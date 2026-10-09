const DEFAULT_BASE = "https://repomatch-backend-317668915507.us-central1.run.app";

const el = (id) => document.getElementById(id);
const status = (text, warn) => {
  el("status").textContent = text || "";
  el("status").className = warn ? "warn" : "muted";
};

let settings = { key: "", base: DEFAULT_BASE };
let kit = null;

async function api(path) {
  const res = await fetch(`${settings.base.replace(/\/+$/, "")}${path}`, {
    headers: { "X-Tracker-Key": settings.key },
  });
  if (!res.ok) {
    const body = await res.text();
    let detail = `HTTP ${res.status}`;
    try {
      const json = JSON.parse(body);
      detail = json.detail || json.title || detail;
    } catch {
      // not a problem document; the status says enough
    }
    throw new Error(detail);
  }
  return res.json();
}

/** Same application, same form: a posting's apply URL and the tab can differ in query string and trailing slash. */
function sameForm(applyUrl, tabUrl) {
  try {
    const a = new URL(applyUrl);
    const b = new URL(tabUrl);
    if (a.host !== b.host) return false;
    const trim = (p) => p.replace(/\/+$/, "");
    return trim(b.pathname).startsWith(trim(a.pathname)) || trim(a.pathname).startsWith(trim(b.pathname));
  } catch {
    return false;
  }
}

async function activeTab() {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  return tab;
}

async function load() {
  const tab = await activeTab();
  if (!tab || !/^https?:/.test(tab.url || "")) {
    el("match").textContent = "Open the employer's application form, then click this again.";
    return;
  }

  status("Looking for the application…");
  let applications;
  try {
    applications = await api("/api/tracker/applications");
  } catch (err) {
    status(err.message, true);
    el("match").textContent = "Couldn't reach your tracker. Check the key.";
    return;
  }

  const app = applications.find((a) => a.applyUrl && sameForm(a.applyUrl, tab.url));
  if (!app) {
    el("match").innerHTML =
      "<p class='label'>No match</p><p>This page isn't an application you're tracking. Approve the posting in RepoMatch first.</p>";
    status("");
    return;
  }

  try {
    kit = await api(`/api/apply/kits/${app.id}`);
  } catch (err) {
    status(err.message, true);
    return;
  }

  const answers = (kit.answers || []).length;
  el("match").innerHTML = `
    <p class="label">${escape(app.company || "")}</p>
    <p>${escape(app.role || "")}</p>
    <p class="muted">${kit.profile ? "Your details ready" : "No saved details — fill them in on the My details page"}${
      answers ? ` · ${answers} drafted answer${answers === 1 ? "" : "s"}` : " · no drafted answers yet"
    }</p>`;
  el("fill").disabled = !kit.profile && !answers;
  status("");
}

function escape(s) {
  return String(s).replace(/[<>&]/g, (c) => ({ "<": "&lt;", ">": "&gt;", "&": "&amp;" }[c]));
}

async function fill() {
  const tab = await activeTab();
  el("fill").disabled = true;
  status("Filling…");
  try {
    await chrome.scripting.executeScript({ target: { tabId: tab.id }, files: ["fill.js"] });
    const [{ result }] = await chrome.scripting.executeScript({
      target: { tabId: tab.id },
      func: (k) => repomatchFill(k),
      args: [kit],
    });

    const parts = [`<div class="card"><p class="label">Filled ${result.filled.length} field${
      result.filled.length === 1 ? "" : "s"
    }</p>`];
    if (result.leftOver.length) {
      parts.push(
        `<p class="muted">No field matched these answers — copy them from RepoMatch:</p><ul>${result.leftOver
          .map((q) => `<li>${escape(q)}</li>`)
          .join("")}</ul>`,
      );
    }
    if (result.skipped.length) {
      parts.push(
        `<p class="muted">Left for you: ${result.skipped.map(escape).join(", ")}</p>`,
      );
    }
    parts.push("<p class='warn'>Read every field before you submit. Uploads and tick-boxes are yours to do.</p></div>");
    el("result").innerHTML = parts.join("");
    status("");
  } catch (err) {
    status(err.message, true);
  } finally {
    el("fill").disabled = false;
  }
}

async function start() {
  const stored = await chrome.storage.local.get(["key", "base"]);
  settings = { key: stored.key || "", base: stored.base || DEFAULT_BASE };
  el("base").value = settings.base;
  if (!settings.key) {
    el("setup").hidden = false;
    return;
  }
  el("main").hidden = false;
  await load();
}

el("save").addEventListener("click", async () => {
  const key = el("key").value.trim();
  if (!key) {
    status("Paste your tracker key first.", true);
    return;
  }
  settings = { key, base: el("base").value.trim() || DEFAULT_BASE };
  await chrome.storage.local.set(settings);
  el("setup").hidden = true;
  el("main").hidden = false;
  await load();
});

el("forget").addEventListener("click", async () => {
  await chrome.storage.local.remove(["key"]);
  el("main").hidden = true;
  el("result").innerHTML = "";
  el("key").value = "";
  el("setup").hidden = false;
});

el("fill").addEventListener("click", fill);
start();
