from app.websearch import parse_jobs


def test_parse_jobs_tolerates_fences_and_drops_bad_items():
    text = """Here you go:
```json
[{"title": "SDE Intern", "company": "Acme", "location": "Pune", "url": "https://acme.com/jobs/1"},
 {"title": "", "url": "https://acme.com/jobs/2"},
 {"title": "No link", "url": "acme.com/jobs/3"},
 {"title": "SDE Intern", "url": "https://acme.com/jobs/1"}]
```"""
    jobs = parse_jobs(text)
    assert [j.url for j in jobs] == ["https://acme.com/jobs/1"]
    assert jobs[0].location == "Pune"


def test_parse_jobs_returns_empty_on_prose():
    assert parse_jobs("I could not find anything.") == []
