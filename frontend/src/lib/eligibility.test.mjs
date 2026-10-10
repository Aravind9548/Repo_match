// Checks the eligibility reader against real ad wording, including the TerraLogic drive.
import assert from "node:assert/strict";






const mod = await import("./eligibility.ts");

const drive = "New Graduate Hiring Drive. We are hiring 2025 & 2026 passouts for our Bengaluru office. Freshers only.";
assert.deepEqual(mod.wantedGraduationYears(drive).years, [2025, 2026]);

let notes = mod.eligibilityNotes(drive, { graduation: "B.Tech CSE, May 2027" });
assert.equal(notes.length, 1, JSON.stringify(notes));
assert.equal(notes[0].level, "blocked");
assert.match(notes[0].text, /2025 and 2026 graduates.*2027/);
assert.match(notes[0].quote, /passouts/);

// A graduate the ad does want gets no warning at all.
assert.deepEqual(mod.eligibilityNotes(drive, { graduation: "B.Tech, 2026" }), []);

// No saved graduation year: it asks for one rather than guessing.
notes = mod.eligibilityNotes(drive, { graduation: "" });
assert.equal(notes[0].level, "check");

// Senior roles are flagged by the experience they ask for.
const senior = "Software Engineer II. You have 4+ years of professional experience building Android apps.";
notes = mod.eligibilityNotes(senior, { graduation: "B.Tech, 2027" });
assert.equal(notes.some((n) => n.level === "blocked" && /4\+ years/.test(n.text)), true);

// An internship that mentions no years and no batch says nothing.
assert.deepEqual(mod.eligibilityNotes("Summer intern. You will write Java and learn from a mentor.", { graduation: "2027" }), []);

// "1 year of experience" is not a wall for a student.
assert.deepEqual(mod.eligibilityNotes("Internship requiring 1 year of experience with Python.", { graduation: "2027" }), []);

console.log("all eligibility checks pass");
