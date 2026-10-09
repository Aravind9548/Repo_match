# RepoMatch Autofill

A small Chrome extension that fills an employer's application form from your RepoMatch apply kit.

It fills fields and stops there. It does not click submit, does not tick consent boxes, does not upload files, and
does not touch a field you have already typed in. You read the form and send it yourself.

## Install it

1. Open `chrome://extensions` and switch on **Developer mode**.
2. Click **Load unpacked** and pick this `extension/` folder.
3. Click the extension, paste your tracker key (the Alerts page shows it under "Use on another device"), and save.
   The key is kept in the extension's own storage on this computer, and goes to your backend only.

## Use it

1. Approve a posting in RepoMatch so it is in your tracker, and draft its apply kit on the application page.
2. Open the employer's form and click the extension. It matches the page against your tracked applications by apply
   URL, and shows the company, role and how many answers it has.
3. Click **Fill this form**. It reports what it filled, which drafted answers found no field (copy those over
   yourself), and which fields it left for you.
4. Attach the tailored PDF, answer anything left, read it all, and submit.

## How a field is matched

`fill.js` reads each visible, empty input and textarea and builds its question from the `<label for>`, a wrapping
label, `aria-label`, `aria-labelledby`, the placeholder, the field name, and a sibling heading — which is where
Workday and Ashby keep the question. Short fields match your standing details by phrasing ("notice period",
"right to work", "expected ctc"); long fields match a drafted answer by word overlap with its question, at 50% or
better, and each answer is used at most once.

## Tests

`fill.test.mjs` runs the filler against markup shaped like Greenhouse, Lever and Workday forms, and checks it
refuses to overwrite typed values or to guess at a question it has no answer for.

```bash
npm install jsdom          # in this folder, or anywhere on the path
node fill.test.mjs
```

## If you change backend URLs

`DEFAULT_BASE` in `popup.js` and `host_permissions` in `manifest.json` both name the Cloud Run backend. The popup
also lets you type a different one, but the permission has to list it.
