// Run with: node --test ios-scriptable/test/parse.test.js
const { test } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");
const path = require("node:path");

// The Scriptable script uses top-level await, so evaluate it inside an async function.
async function loadScript() {
  const src = fs.readFileSync(path.join(__dirname, "..", "TrailStatus.js"), "utf8");
  const mod = { exports: {} };
  const AsyncFunction = (async () => {}).constructor;
  await new AsyncFunction("module", src)(mod);
  return mod.exports;
}

const html = fs.readFileSync(path.join(__dirname, "..", "..", "fixtures", "region_list.html"), "utf8");

test("parses every trail row with status and update time", async () => {
  const { parseTrails } = await loadScript();
  const trails = parseTrails(html);
  assert.strictEqual(trails.length, 7);

  const ns = trails.find((t) => t.name.startsWith("Northshore"));
  assert.deepStrictEqual(ns, {
    name: "Northshore Trails @ Grapevine Lake",
    url: "https://www.trailforks.com/region/northshore-trails--grapevine-lake/",
    level: 1,
    statusText: "All Clear / Green",
    updated: 1792857000 * 1000,
  });

  const yellow = trails.find((t) => t.level === 2);
  assert.strictEqual(yellow.name, "Example Yellow Trail & Park");
});

test("matches user-friendly names to the right trail", async () => {
  const { parseTrails, findTrail } = await loadScript();
  const trails = parseTrails(html);
  assert.strictEqual(findTrail(trails, "North Shore").name, "Northshore Trails @ Grapevine Lake");
  assert.strictEqual(findTrail(trails, "erwin park").name, "Erwin Park");
  assert.strictEqual(findTrail(trails, "Erwin Park Skill").name, "Erwin Park Skill Park");
  assert.strictEqual(findTrail(trails, "Erwin Park").level, 4);
  assert.strictEqual(findTrail(trails, "Nonexistent"), null);
});

test("shortens long names for the widget", async () => {
  const { shortName } = await loadScript();
  assert.strictEqual(shortName("Northshore Trails @ Grapevine Lake"), "Northshore");
  assert.strictEqual(shortName("Big Cedar Wilderness Trails"), "Big Cedar Wilderness");
  assert.strictEqual(shortName("Erwin Park"), "Erwin Park");
});
