"use strict";
const test = require("node:test");
const assert = require("node:assert");
const { Registry } = require("./index");

test("register, list and deregister", () => {
  const r = new Registry();
  r.register({ name: "orders", instance_id: "a", host: "h", port: 1 });
  assert.strictEqual(r.list("orders").length, 1);
  assert.ok(r.deregister("orders", "a"));
  assert.strictEqual(r.list("orders").length, 0);
});

test("unhealthy instances are hidden unless requested", () => {
  const r = new Registry();
  r.register({ name: "orders", instance_id: "a", host: "h", port: 1 });
  r.items.get("orders/a").healthy = false;
  assert.strictEqual(r.list("orders").length, 0);
  assert.strictEqual(r.list("orders", true).length, 1);
});