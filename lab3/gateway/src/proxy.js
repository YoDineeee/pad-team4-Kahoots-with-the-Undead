// ============================================================================
// THE PROXY: forwards one request to a service and sends the answer back.
//
// It does 5 things, in this order:
//   1. pick an instance of the service    (round robin)
//   2. build the instance URL             (same path + query string, different host)
//   3. copy the request headers           (Authorization, Content-Type, ...)
//   4. call the instance with fetch() and wait for the answer
//   5. copy the answer back to the client (status + headers + body)
//
// No instance -> 503.  Instance down -> 502.  Too slow -> 504.
// 502, 504 and 5xx answers are reported to the circuit breaker.
// ============================================================================

// Headers that describe the connection itself, not the request.
// They must not be copied to the next connection; fetch/Express set their own.
const HEADERS_NOT_TO_COPY = ['host', 'connection', 'content-length', 'transfer-encoding', 'keep-alive', 'expect', 'upgrade', 'content-encoding'];

function copyHeaders(headersObject) {
  const result = {};
  for (const [name, value] of Object.entries(headersObject)) {
    if (!HEADERS_NOT_TO_COPY.includes(name.toLowerCase())) {
      result[name] = value;
    }
  }
  return result;
}

export function createProxy(route, { registry, balancer, breaker, timeoutMs }) {
  // Express calls this function for every request that matches route.prefix.
  return async function forwardRequest(req, res) {
    // 1. Which instance gets this request?
    const instance = balancer.pick(route.service, registry.instancesOf(route.service));
    if (!instance) {
      res.status(503).json({ error: 'NO_INSTANCE_AVAILABLE', message: `No running instance of ${route.service}` });
      return;
    }
    res.set('x-served-by', route.service);
    res.set('x-instance-id', instance.id); // so you can see the round robin in Postman / curl -i

    // 2. Same path and query, but on the instance's address.
    //    req.originalUrl = "/api/zombies/types?x=1"  ->  "http://172.18.0.7:8081/api/zombies/types?x=1"
    const instanceUrl = instance.url + req.originalUrl;

    // 3. Copy the client's headers and add some information about the original request.
    const headers = copyHeaders(req.headers);
    headers['x-request-id'] = req.requestId;
    headers['x-forwarded-for'] = req.ip;

    // GET and HEAD requests have no body. For the others, express.raw() put the bytes in req.body.
    const hasBody = !['GET', 'HEAD'].includes(req.method) && Buffer.isBuffer(req.body) && req.body.length > 0;

    try {
      // 4. Call the instance. AbortSignal.timeout cancels the call if it takes too long.
      const answer = await fetch(instanceUrl, {
        method: req.method,
        headers,
        body: hasBody ? req.body : undefined,
        signal: AbortSignal.timeout(timeoutMs),
      });
      if (answer.status >= 500) {
        breaker.recordFailure(route.service, instance.id, `answered ${answer.status}`);
      }

      // 5. Send the instance's answer back: same status, same headers, same body.
      res.status(answer.status);
      res.set(copyHeaders(Object.fromEntries(answer.headers)));
      res.set('x-instance-id', instance.id);
      res.send(Buffer.from(await answer.arrayBuffer()));
    } catch (error) {
      if (error.name === 'TimeoutError') {
        breaker.recordFailure(route.service, instance.id, 'timeout');
        res.status(504).json({ error: 'GATEWAY_TIMEOUT', message: `${route.service} did not answer within ${timeoutMs} ms` });
      } else {
        breaker.recordFailure(route.service, instance.id, 'unreachable');
        res.status(502).json({ error: 'SERVICE_UNAVAILABLE', message: `${route.service} is unreachable` });
      }
    }
  };
}
