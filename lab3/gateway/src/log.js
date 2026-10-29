// One log line = time, level, who, what happened:
//   2026-10-28T15:04:05.123Z INFO  [gateway] GET /api/zombies/types -> 200 (12 ms) by zombie-2 [request 9f1c...]
// The tests set QUIET_LOGS=1 so their output stays readable.

export function log(level, message) {
  if (process.env.QUIET_LOGS) return;
  console.log(`${new Date().toISOString()} ${level.padEnd(5)} [gateway] ${message}`);
}
