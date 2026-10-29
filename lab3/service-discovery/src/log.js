// One log line = time, level, who, what happened:
//   2026-10-28T15:04:05.123Z INFO  [service-discovery] Registered zombie-service/zombie-1 at http://172.18.0.7:8081
// The tests set QUIET_LOGS=1 so their output stays readable.

export function log(level, message) {
  if (process.env.QUIET_LOGS) return;
  console.log(`${new Date().toISOString()} ${level.padEnd(5)} [service-discovery] ${message}`);
}
