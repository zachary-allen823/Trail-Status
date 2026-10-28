// Variables used by Scriptable.
// These must be at the very top of the file. Do not edit.
// icon-color: deep-green; icon-glyph: bicycle;

// DFW Trail Status widget for Scriptable (iOS).
//
// Status comes from the Trailforks table that dorba.org embeds on its home page.
// Widget parameter: comma-separated trail names, matched loosely
// (e.g. "North Shore, Erwin Park"). Leave empty for the defaults below.

const DEFAULT_TRAILS = ["North Shore", "Erwin Park"];
const SOURCE_URL =
  "https://www.trailforks.com/widgets/region_list/?rids=35629,3719,13783,3726,5016,22007,13746,14710,22005,22065,5025,5022,3723,19612,24208,33774,33812,25637,19031,5018,13750,13751,34284,5020,24408,5021,17590,23481,3717,13827,13829,28103,24406,45471,38370,50089,54704,61494,71523,73024,31507&rows=50&cols=status,title,last_report_ts";
const USER_AGENT =
  "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";
const CACHE_FILE = "dfw-trail-status-cache.json";
const REFRESH_MINUTES = 30;

const LEVELS = {
  1: { label: "Open", color: "#2E7D32" },
  2: { label: "Minor Issues", color: "#F9A825" },
  3: { label: "Caution", color: "#EF6C00" },
  4: { label: "Closed", color: "#C62828" },
  0: { label: "Unknown", color: "#757575" },
};
const COLOR_TO_LEVEL = { green: 1, yellow: 2, amber: 3, orange: 3, red: 4 };

// ---------- parsing (pure, tested in Node) ----------

function decodeEntities(s) {
  return s
    .replace(/&amp;/g, "&")
    .replace(/&#0?39;|&apos;/g, "'")
    .replace(/&quot;/g, '"')
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">");
}

function attr(tag, name) {
  const m = tag.match(new RegExp(`\\b${name}="([^"]*)"`));
  return m ? m[1] : null;
}

function parseTrails(html) {
  const trails = [];
  const rowRe = /<tr\b[^>]*>([\s\S]*?)<\/tr>/g;
  let m;
  while ((m = rowRe.exec(html))) {
    const row = m[1];
    const iconTag = (row.match(/<span\b[^>]*\bsicon_small\b[^>]*>/) || [])[0];
    const link = row.match(/<a\b([^>]*)>([^<]+)<\/a>/);
    if (!iconTag || !link) continue;

    const colorClass = ((attr(iconTag, "class") || "").match(/\bs(green|yellow|amber|orange|red)\b/) || [])[1];
    let level = parseInt(attr(iconTag, "data-sort"), 10);
    if (!(level >= 1 && level <= 4)) level = COLOR_TO_LEVEL[colorClass] || 0;

    const timeTag = (row.match(/<div\b[^>]*\bfullTime\b[^>]*>/) || [])[0];
    const epoch = timeTag ? parseInt(attr(timeTag, "data-sort"), 10) : NaN;

    trails.push({
      name: decodeEntities(link[2].trim()),
      url: attr(link[1], "href"),
      level,
      statusText: decodeEntities(attr(iconTag, "title") || LEVELS[level].label),
      updated: Number.isFinite(epoch) ? epoch * 1000 : null,
    });
  }
  return trails;
}

const normalize = (s) => s.toLowerCase().replace(/[^a-z0-9]/g, "");

// Loose match: "North Shore" -> "Northshore Trails @ Grapevine Lake".
// When several trails match ("Erwin Park" vs "Erwin Park Skill Park"), the shortest name wins.
function findTrail(trails, query) {
  const q = normalize(query);
  if (!q) return null;
  const hits = trails.filter((t) => normalize(t.name).includes(q));
  hits.sort((a, b) => a.name.length - b.name.length);
  return hits[0] || null;
}

function shortName(name) {
  const s = name
    .replace(/\s*@.*$/, "")
    .replace(/\s+Nature Preserve.*$/i, "")
    .replace(/\s+(DORBA\s+)?Trails?$/i, "")
    .trim();
  return s || name;
}

// ---------- fetching & cache (Scriptable only) ----------

async function fetchHtml() {
  try {
    const req = new Request(SOURCE_URL);
    req.headers = { "User-Agent": USER_AGENT, Accept: "text/html" };
    req.timeoutInterval = 20;
    const html = await req.loadString();
    if (req.response.statusCode === 200 && html.includes("sicon_small")) return html;
  } catch (e) {
    console.warn(`Direct request failed: ${e}`);
  }
  // Cloudflare sometimes blocks plain requests; a real WebKit view usually gets through.
  const wv = new WebView();
  await wv.loadURL(SOURCE_URL);
  return await wv.getHTML();
}

function cachePath() {
  const fm = FileManager.local();
  return fm.joinPath(fm.documentsDirectory(), CACHE_FILE);
}

function readCache() {
  const fm = FileManager.local();
  const p = cachePath();
  if (!fm.fileExists(p)) return null;
  try {
    return JSON.parse(fm.readString(p));
  } catch (e) {
    return null;
  }
}

async function loadTrails() {
  try {
    const trails = parseTrails(await fetchHtml());
    if (trails.length === 0) throw new Error("No trails found in page");
    const data = { fetchedAt: Date.now(), trails };
    FileManager.local().writeString(cachePath(), JSON.stringify(data));
    return { ...data, stale: false };
  } catch (e) {
    console.error(`Fetch failed: ${e}`);
    const cached = readCache();
    if (cached) return { ...cached, stale: true };
    return { fetchedAt: null, trails: [], stale: true, error: String(e) };
  }
}

// ---------- widget UI ----------

function fmtDate(ms) {
  const df = new DateFormatter();
  df.dateFormat = "MMM d";
  return df.string(new Date(ms));
}

function fmtTime(ms) {
  const df = new DateFormatter();
  df.useNoDateStyle();
  df.useShortTimeStyle();
  return df.string(new Date(ms));
}

function addTrailRow(stack, query, trail, compact) {
  const row = stack.addStack();
  row.layoutHorizontally();
  row.centerAlignContent();

  const level = trail ? trail.level : 0;
  const dot = row.addText("●");
  dot.font = Font.boldSystemFont(compact ? 18 : 22);
  dot.textColor = new Color(LEVELS[level].color);
  row.addSpacer(6);

  const col = row.addStack();
  col.layoutVertically();
  const name = col.addText(trail ? shortName(trail.name) : query);
  name.font = Font.semiboldSystemFont(compact ? 13 : 15);
  name.lineLimit = 1;
  name.minimumScaleFactor = 0.7;

  const detail = trail
    ? `${LEVELS[level].label}${trail.updated ? " · " + fmtDate(trail.updated) : ""}`
    : "Not found";
  const sub = col.addText(detail);
  sub.font = Font.systemFont(compact ? 11 : 12);
  sub.textColor = new Color(LEVELS[level].color);
  sub.lineLimit = 1;
}

function buildWidget(data, queries) {
  const w = new ListWidget();
  w.backgroundColor = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E"));
  w.setPadding(12, 12, 12, 12);
  w.refreshAfterDate = new Date(Date.now() + REFRESH_MINUTES * 60 * 1000);

  const family = config.widgetFamily || "medium";
  const maxRows = { small: 2, medium: 3, large: 7, accessoryRectangular: 2 }[family] || 3;
  const compact = family === "small" || family.startsWith("accessory");

  const title = w.addText("DFW Trails");
  title.font = Font.boldSystemFont(compact ? 11 : 13);
  title.textColor = Color.gray();
  w.addSpacer(6);

  const shown = queries.slice(0, maxRows);
  shown.forEach((q, i) => {
    if (i > 0) w.addSpacer(compact ? 4 : 6);
    addTrailRow(w, q, findTrail(data.trails, q), compact);
  });

  w.addSpacer();
  const footer = w.addText(
    data.fetchedAt ? `${data.stale ? "⚠︎ cached " : ""}${fmtTime(data.fetchedAt)}` : "⚠︎ unable to load"
  );
  footer.font = Font.systemFont(9);
  footer.textColor = Color.gray();

  if (shown.length === 1) {
    const t = findTrail(data.trails, shown[0]);
    if (t && t.url) w.url = t.url;
  }
  return w;
}

async function main() {
  const param = (args.widgetParameter || "").trim();
  const queries = param ? param.split(",").map((s) => s.trim()).filter(Boolean) : DEFAULT_TRAILS;
  const data = await loadTrails();
  const widget = buildWidget(data, queries);

  if (config.runsInWidget) {
    Script.setWidget(widget);
  } else {
    // Running in the app: list every trail name to help with picking widget parameters.
    console.log(data.trails.map((t) => `${LEVELS[t.level].label.padEnd(12)} ${t.name}`).join("\n"));
    await widget.presentMedium();
  }
  Script.complete();
}

if (typeof module !== "undefined") module.exports = { parseTrails, findTrail, shortName };
if (typeof Script !== "undefined") await main();
