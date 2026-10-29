const express = require('express');

const PORT              = parseInt(process.env.PORT || '8761');
const POLL_MS           = parseInt(process.env.POLL_MS || '5000');
const HEALTH_TIMEOUT_MS = parseInt(process.env.HEALTH_TIMEOUT_MS || '2000');
const CRITICAL_LOAD     = parseFloat(process.env.CRITICAL_LOAD || '0.8');
const QUARANTINE_FAILS  = 3;
const UNQUARANTINE_RUNS = 3;

const app = express();
app.use(express.json());

const registry = new Map();             // name -> Map<"host:port", instance>
const getMap = n => { if (!registry.has(n)) registry.set(n, new Map()); return registry.get(n); };
const K = (h, p) => `${h}:${p}`;

const log = (level, msg, meta = {}) =>
  console.log(JSON.stringify({ ts: new Date().toISOString(), level, svc: 'discovery', msg, ...meta }));

/* ────────── Registration ────────── */
app.post('/register', (req, res) => {
  const { name, host, port } = req.body || {};
  if (!name || !host || !port) return res.status(400).json({ error: 'name,host,port required' });
  const m = getMap(name), k = K(host, port);
  const inst = m.get(k);
  if (inst) {
    inst.lastSeen = Date.now();
    if (inst.quarantined) {
      inst.quarantined = false; inst.failures = 0; inst.consecutiveHealthy = 0;
      log('warn', 'instance re-registered — un-quarantined', { name, host, port });
    } else log('info', 're-registered', { name, host, port });
  } else {
    m.set(k, {
      name, host, port, quarantined: false, failures: 0, consecutiveHealthy: 0,
      active: 0, capacity: 10, critical: false, lastSeen: Date.now(),
    });
    log('info', 'registered', { name, host, port });
  }
  res.json({ ok: true });
});

app.post('/deregister', (req, res) => {
  const { name, host, port } = req.body || {};
  const m = registry.get(name);
  if (m && m.delete(K(host, port))) log('warn', 'deregistered', { name, host, port });
  res.json({ ok: true });
});

app.post('/quarantine', (req, res) => {
  const { name, host, port, reason } = req.body || {};
  const inst = registry.get(name)?.get(K(host, port));
  if (inst && !inst.quarantined) {
    inst.quarantined = true; inst.consecutiveHealthy = 0;
    log('warn', 'quarantined', { name, host, port, reason });
  }
  res.json({ ok: true });
});

/* ────────── Discovery ────────── */
app.get('/services/:name', (req, res) => {
  const m = registry.get(req.params.name);
  if (!m) return res.json([]);
  res.json([...m.values()].filter(i => !i.quarantined).map(({ host, port }) => ({ host, port })));
});

app.get('/services', (_q, res) => {
  const out = {};
  for (const [n, m] of registry)
    out[n] = [...m.values()].filter(i => !i.quarantined).map(({ host, port }) => ({ host, port }));
  res.json(out);
});

app.get('/debug', (_q, res) => {
  const out = {};
  for (const [n, m] of registry) out[n] = [...m.values()];
  res.json(out);
});

app.get('/health', (_q, res) => res.json({ status: 'ok', services: [...registry.keys()] }));

/* ────────── Notifications (Grade 8) ────────── */
async function notify(msg) {
  const tasks = [];
  if (process.env.DISCORD_WEBHOOK_URL)
    tasks.push(fetch(process.env.DISCORD_WEBHOOK_URL, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ content: `[PAD] ${msg}` }),
    }).catch(e => log('error', 'discord notify failed', { e: e.message })));

  if (process.env.TELEGRAM_BOT_TOKEN && process.env.TELEGRAM_CHAT_ID)
    tasks.push(fetch(`https://api.telegram.org/bot${process.env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ chat_id: process.env.TELEGRAM_CHAT_ID, text: `[PAD] ${msg}` }),
    }).catch(e => log('error', 'telegram notify failed', { e: e.message })));

  if (process.env.ALERT_EMAIL_WEBHOOK)
    tasks.push(fetch(process.env.ALERT_EMAIL_WEBHOOK, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ subject: 'PAD alert', text: msg }),
    }).catch(e => log('error', 'email notify failed', { e: e.message })));

  await Promise.allSettled(tasks);
}

/* ────────── Health polling (Grade 7 second bullet) ────────── */
async function pollOne(inst) {
  const ctrl = new AbortController();
  const t = setTimeout(() => ctrl.abort(), HEALTH_TIMEOUT_MS);
  try {
    const r = await fetch(`http://${inst.host}:${inst.port}/health`, { signal: ctrl.signal });
    if (!r.ok) throw new Error(`HTTP ${r.status}`);
    const d = await r.json();

    inst.lastSeen = Date.now();
    inst.active   = d.active   ?? 0;
    inst.capacity = d.capacity ?? 10;
    inst.failures = 0;
    inst.consecutiveHealthy += 1;

    if (inst.quarantined && inst.consecutiveHealthy >= UNQUARANTINE_RUNS) {
      inst.quarantined = false;
      log('info', 'instance recovered — un-quarantined', { name: inst.name, host: inst.host, port: inst.port });
      notify(`RECOVERED: ${inst.name} ${inst.host}:${inst.port}`);
    }

    const load = inst.capacity > 0 ? inst.active / inst.capacity : 0;
    if (load >= CRITICAL_LOAD && !inst.critical) {
      inst.critical = true;
      log('warn', 'CRITICAL LOAD', {
        name: inst.name, host: inst.host, port: inst.port,
        active: inst.active, capacity: inst.capacity, loadPct: Math.round(load * 100),
      });
      notify(`Critical load on ${inst.name} ${inst.host}:${inst.port} — ${inst.active}/${inst.capacity}`);
    } else if (load < CRITICAL_LOAD) inst.critical = false;
  } catch (e) {
    inst.failures += 1;
    inst.consecutiveHealthy = 0;
    if (inst.failures >= QUARANTINE_FAILS && !inst.quarantined) {
      inst.quarantined = true;
      log('error', 'instance DOWN — quarantined', {
        name: inst.name, host: inst.host, port: inst.port, failures: inst.failures,
      });
      notify(`DOWN: ${inst.name} ${inst.host}:${inst.port} (${inst.failures} consecutive failures)`);
    }
  } finally { clearTimeout(t); }
}

async function pollAll() {
  for (const m of registry.values())
    for (const i of m.values()) await pollOne(i);
}
setInterval(pollAll, POLL_MS);
log('info', 'health polling started', { intervalMs: POLL_MS, criticalLoad: CRITICAL_LOAD });

app.listen(PORT, () => log('info', 'service-discovery listening', { port: PORT }));

