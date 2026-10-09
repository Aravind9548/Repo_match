// Runs fill.js against form markup shaped like Greenhouse, Lever and Workday pages.
import { readFileSync } from "node:fs";
import assert from "node:assert/strict";
import { JSDOM } from "jsdom";

const src = readFileSync(new URL("./fill.js", import.meta.url), "utf8");

const kit = {
  profile: {
    fullName: "Aravind Reddy",
    email: "a@example.com",
    phone: "+91 90000 00000",
    location: "Hyderabad, India",
    githubUrl: "https://github.com/Aravind9548",
    linkedinUrl: "https://linkedin.com/in/aravind",
    portfolioUrl: "",
    workAuthorization: "Indian citizen, no sponsorship needed",
    noticePeriod: "Can start June 2026",
    expectedSalary: "As per company norms",
    graduation: "B.Tech CSE, May 2027",
    extras: [{ label: "How did you hear about us?", value: "A friend who works here" }],
  },
  answers: [
    { question: "Why do you want to work at Acme?", answer: "Because of the systems work." },
    { question: "Describe your strongest project.", answer: "RepoMatch, a job matcher." },
    { question: "Anything else we should know?", answer: "Available full-time from June." },
  ],
};

function run(html) {
  const dom = new JSDOM(`<!doctype html><body>${html}</body>`, { runScripts: "outside-only" });
  const { window } = dom;
  // jsdom lays nothing out, so the visibility check needs a stand-in for offsetParent.
  Object.defineProperty(window.HTMLElement.prototype, "offsetParent", { get() { return this.ownerDocument.body; } });
  window.eval(src);
  return window.eval("repomatchFill")(kit);
}

// Greenhouse: <label for>, long answers in textareas.
const greenhouse = run(`
  <label for="first_name">First Name *</label><input id="first_name" />
  <label for="last_name">Last Name *</label><input id="last_name" />
  <label for="email">Email *</label><input id="email" type="email" />
  <label for="phone">Phone</label><input id="phone" />
  <label for="q1">Why do you want to work at Acme? *</label><textarea id="q1"></textarea>
  <label for="q2">Describe your strongest project. *</label><textarea id="q2"></textarea>
  <label for="gh">GitHub Profile</label><input id="gh" />
  <label for="hear">How did you hear about us?</label><input id="hear" />
`);
assert.equal(greenhouse.filled.length, 8, `greenhouse filled ${greenhouse.filled.length}: ${greenhouse.filled}`);

// Lever: aria-label and wrapping labels, no ids.
const lever = run(`
  <label><span>Full name</span><input name="name" /></label>
  <label><span>Email</span><input name="email" /></label>
  <label><span>Current location</span><input name="location" /></label>
  <input aria-label="LinkedIn URL" name="urls[LinkedIn]" />
  <label><span>Are you legally authorized to work? Visa status</span><input name="cards[x][field0]" /></label>
`);
assert.equal(lever.filled.length, 5, `lever filled ${lever.filled.length}: ${lever.filled}`);

// Workday: the question sits in a sibling heading, not a label.
const workday = run(`
  <div data-automation-id="legalNameSection"><label id="h1">Legal First Name</label><input aria-labelledby="h1" /></div>
  <div data-automation-id="phone"><label id="h2">Phone Number</label><input aria-labelledby="h2" /></div>
  <div data-automation-id="q"><h3>Anything else we should know?</h3><textarea></textarea></div>
`);
assert.equal(workday.filled.length, 3, `workday filled ${workday.filled.length}: ${workday.filled}`);

// It must not overwrite typed values, touch password/file/checkbox fields, or guess at unknown questions.
const careful = run(`
  <label for="e">Email</label><input id="e" value="typed@me.com" />
  <label for="p">Password</label><input id="p" type="password" />
  <label for="f">Resume</label><input id="f" type="file" />
  <label for="c">I agree to the privacy policy</label><input id="c" type="checkbox" />
  <label for="u">What is your favourite compiler flag?</label><textarea id="u"></textarea>
`);
assert.equal(careful.filled.length, 0, `careful filled: ${careful.filled}`);
assert.ok(careful.leftOver.length === 3, `left over: ${careful.leftOver}`);

// Each drafted answer is used once, not pasted into every textarea.
const repeats = run(`
  <label for="a">Why do you want to work at Acme?</label><textarea id="a"></textarea>
  <label for="b">Why do you want to work at Acme?</label><textarea id="b"></textarea>
`);
assert.equal(repeats.filled.length, 1, `repeats filled: ${repeats.filled}`);

console.log("all fill.js checks pass");
