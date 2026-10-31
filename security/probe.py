"""Black-box security probe for the RepoMatch API.

There are no accounts here: whoever holds a tracker key owns that key's data, and every query is supposed to be
constrained by the key's SHA-256. That one rule is the whole security model, so this probe spends itself on it —
can one key reach another's applications, resumes, tailoring runs or apply kits, by any route?

It works the way an attacker would: two disposable accounts are created, one is treated as the attacker, and every
id-bearing endpoint is tried against the other's data, including the ones that change and delete. Nothing it touches
belongs to a real person, and it deletes both accounts' applications when it finishes.

No model, no subscription, no network beyond the API under test.

    python security/probe.py                        # against the deployed backend
    python security/probe.py http://localhost:8081  # against a local one
"""

from __future__ import annotations

import io
import json
import sys
import ssl
import urllib.error
import urllib.request

# Windows consoles default to a codepage that cannot print an arrow.
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")

BASE = (sys.argv[1] if len(sys.argv) > 1 else
        "https://repomatch-backend-317668915507.us-central1.run.app").rstrip("/")
TIMEOUT = 60

# Some Python builds ship without a CA bundle; certifi's is used when it is installed.
try:
    import certifi

    SSL_CONTEXT: ssl.SSLContext | None = ssl.create_default_context(cafile=certifi.where())
except ImportError:
    SSL_CONTEXT = None

PASS, FAIL, INFO = "PASS", "FAIL", "INFO"
results: list[tuple[str, str, str]] = []


def record(status: str, name: str, detail: str = "") -> None:
    results.append((status, name, detail))
    mark = {PASS: "ok  ", FAIL: "FAIL", INFO: "note"}[status]
    print(f"  {mark}  {name}" + (f" — {detail}" if detail else ""))


def call(method: str, path: str, key: str | None = None, body: dict | None = None,
         headers: dict | None = None) -> tuple[int, str]:
    """Returns (status, body). A refusal is an answer, so HTTP errors are returned rather than raised."""
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(f"{BASE}{path}", data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if key:
        req.add_header("X-Tracker-Key", key)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT, context=SSL_CONTEXT) as r:
            return r.status, r.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001 - a transport failure is a result too
        return 0, str(e)


def new_key() -> str:
    import base64
    import secrets

    return base64.urlsafe_b64encode(secrets.token_bytes(24)).decode().rstrip("=")


def seed(key: str) -> tuple[int, str] | None:
    """Gives an account one application to own, so there is something to try to steal."""
    status, body = call("GET", "/api/jobs?size=5", key)
    if status != 200:
        return None
    items = json.loads(body).get("items", [])
    for posting in items:
        status, body = call("POST", f"/api/tracker/postings/{posting['id']}/approve", key, {})
        if status == 200:
            app = json.loads(body)
            return app["id"], app.get("company") or ""
    return None


def main() -> int:
    print(f"RepoMatch security probe → {BASE}\n")

    print("Setting up two disposable accounts")
    attacker, victim = new_key(), new_key()
    mine = seed(attacker)
    theirs = seed(victim)
    if not mine or not theirs:
        print("  could not create test data — is the API up?")
        return 2
    my_app, _ = mine
    their_app, their_company = theirs
    record(INFO, "attacker owns application", str(my_app))
    record(INFO, "victim owns application", f"{their_app} ({their_company})")

    print("\nAuthentication")
    status, _ = call("GET", f"/api/tracker/applications/{my_app}")
    record(PASS if status in (400, 401) else FAIL, "no key is refused", f"HTTP {status}")

    status, body = call("GET", f"/api/tracker/applications/{my_app}", "not-a-real-key")
    leaked = status == 200
    record(FAIL if leaked else PASS, "a made-up key gets nothing", f"HTTP {status}")

    status, _ = call("GET", f"/api/tracker/applications/{my_app}", attacker)
    record(PASS if status == 200 else FAIL, "the real key still works", f"HTTP {status}")

    print("\nReading across accounts (the one that matters)")
    for name, path in [
        ("application", f"/api/tracker/applications/{their_app}"),
        ("apply kit", f"/api/apply/kits/{their_app}"),
        ("tailoring runs", f"/api/tailor/applications/{their_app}/runs"),
    ]:
        status, body = call("GET", path, attacker)
        # A leak is any of the victim's content coming back. An empty 200 is not a leak, but it is worth seeing:
        # it tells an attacker the id exists, where a 404 tells them nothing.
        exposed = status == 200 and their_company and their_company.lower() in body.lower()
        empty_ok = status == 200 and body.strip() in ("[]", "{}", "")
        record(FAIL if exposed else PASS, f"cannot read another account's {name}",
               f"HTTP {status}" + (" — LEAKED" if exposed else " (empty list, not a 404)" if empty_ok else ""))

    print("\nWalking the ids")
    seen = []
    for app_id in range(1, 26):
        status, body = call("GET", f"/api/tracker/applications/{app_id}", attacker)
        if status == 200:
            seen.append(app_id)
    only_own = seen == [my_app]
    record(PASS if only_own else FAIL, "id enumeration returns only its own",
           f"reachable: {seen or 'none'}")

    print("\nChanging and deleting across accounts")
    status, _ = call("PATCH", f"/api/tracker/applications/{their_app}", attacker, {"notes": "probe"})
    record(PASS if status != 200 else FAIL, "cannot edit another account's application", f"HTTP {status}")

    status, _ = call("DELETE", f"/api/tracker/applications/{their_app}", attacker)
    gone = call("GET", f"/api/tracker/applications/{their_app}", victim)[0] != 200
    record(FAIL if gone else PASS, "cannot delete another account's application",
           "DESTROYED" if gone else f"HTTP {status}, victim's data intact")

    print("\nSigned email links")
    forged = "s.0000000000000000000000000000000000000000000000000000000000000000.deadbeef"
    status, _ = call("GET", "/api/tracker/applications", forged)
    record(PASS if status != 200 else FAIL, "a forged signed link is rejected", f"HTTP {status}")

    print("\nThe scheduled run")
    status, _ = call("POST", "/api/jobs/cron", None, {})
    record(PASS if status in (401, 403) else FAIL, "cron needs its secret", f"HTTP {status}")

    status, _ = call("POST", "/api/jobs/cron", None, {}, {"X-Cron-Secret": "guess"})
    record(PASS if status in (401, 403) else FAIL, "a guessed cron secret is refused", f"HTTP {status}")

    print("\nBrowser rules")
    status, body = call("OPTIONS", "/api/tracker/applications", None, None,
                        {"Origin": "https://evil.example", "Access-Control-Request-Method": "GET"})
    record(PASS if status >= 400 or "evil.example" not in body else FAIL,
           "an unknown origin is not allowed", f"HTTP {status}")

    print("\nCleaning up")
    for key, app_id in ((attacker, my_app), (victim, their_app)):
        call("DELETE", f"/api/tracker/applications/{app_id}", key)
    record(INFO, "disposable accounts removed", "")

    failed = [r for r in results if r[0] == FAIL]
    print(f"\n{len(results) - len(failed)} checks passed, {len(failed)} failed")
    for _, name, detail in failed:
        print(f"  FAILED: {name} — {detail}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
