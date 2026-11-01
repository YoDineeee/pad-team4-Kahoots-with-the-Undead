package md.utm.faf.pad.player.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs around EVERY request and writes two log lines:
 *
 *   INFO [9f1c-...] --> GET /api/zombies/types
 *   INFO [9f1c-...] <-- GET /api/zombies/types 200 (12 ms)
 *
 * The id in brackets is the X-Request-Id the gateway gave the request. It is put in the MDC
 * (the "logging context"), so EVERY log line written while handling this request shows it,
 * and you can follow one request from the gateway's log into this service's log.
 *
 * It also counts the requests (shown on GET /status).
 */
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final InstanceInfo instance;
    private final AtomicLong requestsTotal = new AtomicLong(); // Atomic: many requests run at the same time

    public RequestLoggingFilter(InstanceInfo instance) {
        this.instance = instance;
    }

    public long requestsTotal() {
        return requestsTotal.get();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString(); // called directly, not through the gateway
        }
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);
        response.setHeader("X-Instance-Id", instance.instanceId());

        String path = request.getRequestURI();
        boolean isStatusCheck = path.equals("/status") || path.startsWith("/actuator");
        long start = System.currentTimeMillis();

        if (!isStatusCheck) {
            requestsTotal.incrementAndGet();
            log.info("--> {} {}", request.getMethod(), path);
        }
        try {
            chain.doFilter(request, response); // the controller runs here
        } finally {
            if (!isStatusCheck) {
                log.info("<-- {} {} {} ({} ms)", request.getMethod(), path, response.getStatus(),
                        System.currentTimeMillis() - start);
            }
            MDC.remove("requestId");
        }
    }
}
