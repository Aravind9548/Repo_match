/**
 * Runs inside the application page. Reads every field, works out what each one is asking, and fills the ones it
 * recognises from the kit. It fills, reports, and stops — submitting is the person's job, and nothing here clicks a
 * submit button or a consent box.
 */
function repomatchFill(kit) {
  const profile = kit.profile || {};
  const answers = (kit.answers || []).filter((a) => a.answer && a.answer.trim());

  /** Label text for a field: what a person reads above or beside it. */
  function labelOf(el) {
    const bits = [];
    if (el.id) {
      // Matched by iterating rather than by selector: ids on these forms contain brackets and dots.
      const forLabel = Array.from(document.getElementsByTagName("label")).find((l) => l.htmlFor === el.id);
      if (forLabel) bits.push(forLabel.textContent);
    }
    const wrapping = el.closest("label");
    if (wrapping) bits.push(wrapping.textContent);
    bits.push(el.getAttribute("aria-label") || "");
    bits.push(el.getAttribute("placeholder") || "");
    bits.push(el.name || "");
    const labelled = el.getAttribute("aria-labelledby");
    if (labelled) {
      labelled.split(/\s+/).forEach((id) => {
        const node = document.getElementById(id);
        if (node) bits.push(node.textContent);
      });
    }
    // Workday and Ashby put the question in a sibling heading rather than a <label>.
    const group = el.closest("[data-automation-id], fieldset, div");
    if (group) {
      const heading = group.querySelector("legend, h2, h3, h4, label, span[id]");
      if (heading && !heading.contains(el)) bits.push(heading.textContent);
    }
    return bits.join(" ").replace(/\s+/g, " ").trim().toLowerCase();
  }

  /** Short answers, matched on how forms actually word them. */
  const RULES = [
    { value: profile.fullName, any: ["full name", "your name"], not: ["company", "referr", "user"] },
    { value: firstName(profile.fullName), any: ["first name", "given name", "legal first"] },
    { value: lastName(profile.fullName), any: ["last name", "family name", "surname", "legal last"] },
    { value: profile.email, any: ["email", "e-mail"], not: ["confirm"] },
    { value: profile.email, any: ["confirm email", "verify email"] },
    { value: profile.phone, any: ["phone", "mobile", "contact number"] },
    { value: profile.location, any: ["location", "city", "where are you based", "current address", "address"] },
    { value: profile.githubUrl, any: ["github"] },
    { value: profile.linkedinUrl, any: ["linkedin"] },
    { value: profile.portfolioUrl, any: ["portfolio", "website", "personal site", "other url"] },
    { value: profile.workAuthorization, any: ["work authorization", "work authorisation", "right to work", "visa", "sponsorship"] },
    { value: profile.noticePeriod, any: ["notice period", "available", "start date", "earliest start"] },
    { value: profile.expectedSalary, any: ["salary", "compensation", "expected ctc", "expected pay"] },
    { value: profile.graduation, any: ["graduation", "degree", "university", "college", "school", "major"] },
  ];
  for (const extra of profile.extras || []) {
    if (extra.label && extra.value) {
      RULES.push({ value: extra.value, any: [extra.label.toLowerCase()] });
    }
  }

  function firstName(name) {
    return name ? name.trim().split(/\s+/)[0] : "";
  }
  function lastName(name) {
    const parts = name ? name.trim().split(/\s+/) : [];
    return parts.length > 1 ? parts.slice(1).join(" ") : "";
  }

  /** Word overlap, so "Why do you want to work here?" finds the drafted "Why this company?". */
  function overlap(a, b) {
    const stop = new Set(["the", "a", "an", "to", "of", "in", "for", "you", "your", "do", "is", "are", "and", "at",
      "with", "about", "this", "that", "what", "why", "how", "us", "we", "our", "would", "please", "tell", "describe"]);
    const words = (s) => new Set(s.toLowerCase().match(/[a-z]{3,}/g)?.filter((w) => !stop.has(w)) ?? []);
    const x = words(a);
    const y = words(b);
    if (!x.size || !y.size) return 0;
    let shared = 0;
    x.forEach((w) => y.has(w) && shared++);
    return shared / Math.min(x.size, y.size);
  }

  function set(el, value) {
    const native = Object.getOwnPropertyDescriptor(
      el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype,
      "value",
    );
    // React keeps its own copy of the value; the native setter is what it listens for.
    if (native && native.set) native.set.call(el, value);
    else el.value = value;
    el.dispatchEvent(new Event("input", { bubbles: true }));
    el.dispatchEvent(new Event("change", { bubbles: true }));
  }

  const filled = [];
  const skipped = [];
  const used = new Set();

  const fields = Array.from(document.querySelectorAll("input, textarea")).filter((el) => {
    if (el.disabled || el.readOnly) return false;
    if (["hidden", "submit", "button", "checkbox", "radio", "password", "file"].includes(el.type)) return false;
    return el.offsetParent !== null;
  });

  for (const el of fields) {
    if (el.value && el.value.trim()) continue; // never overwrite something already typed
    const label = labelOf(el);
    if (!label) {
      skipped.push("an unlabelled field");
      continue;
    }

    const long = el instanceof HTMLTextAreaElement || (el.maxLength > 300 || el.maxLength === -1) && label.length > 40;
    if (long) {
      let best = null;
      let bestScore = 0;
      answers.forEach((a, i) => {
        if (used.has(i)) return;
        const score = overlap(label, a.question);
        if (score > bestScore) {
          bestScore = score;
          best = i;
        }
      });
      if (best !== null && bestScore >= 0.5) {
        set(el, answers[best].answer);
        used.add(best);
        filled.push(answers[best].question);
        continue;
      }
    }

    const rule = RULES.find(
      (r) => r.value && r.value.trim() && r.any.some((k) => label.includes(k)) && !(r.not || []).some((k) => label.includes(k)),
    );
    if (rule) {
      set(el, rule.value.trim());
      filled.push(label.slice(0, 60));
    } else {
      skipped.push(label.slice(0, 60));
    }
  }

  const leftOver = answers.filter((_, i) => !used.has(i)).map((a) => a.question);
  return { filled, skipped: skipped.slice(0, 12), leftOver, url: location.href };
}
