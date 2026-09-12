from app.answerer import _LlmCitation, build_prompt, verified_citations
from app.schemas import CodeChunk

CHUNKS = [
    CodeChunk(path="watcher/monitor.py", start_line=40, end_line=80, text="def check(): ..."),
    CodeChunk(path="agent/diagnose.py", start_line=1, end_line=30, text="logs = docker.logs(...)"),
]


def test_drops_citations_for_files_we_never_sent():
    cites = [_LlmCitation(path="watcher/monitor.py", start_line=45, end_line=50), _LlmCitation(path="made/up.py", start_line=1, end_line=9)]
    assert [c.path for c in verified_citations(cites, CHUNKS)] == ["watcher/monitor.py"]


def test_clamps_line_numbers_to_the_excerpt_we_sent():
    [cite] = verified_citations([_LlmCitation(path="agent/diagnose.py", start_line=1, end_line=900)], CHUNKS)
    assert (cite.start_line, cite.end_line) == (1, 30)


def test_deduplicates_and_caps():
    same = [_LlmCitation(path="agent/diagnose.py", start_line=2, end_line=5)] * 12
    assert len(verified_citations(same, CHUNKS)) == 1


def test_prompt_carries_paths_and_line_ranges():
    prompt = build_prompt("u/repo", "how does it work?", CHUNKS)
    assert '<excerpt path="watcher/monitor.py" lines="40-80">' in prompt
    assert "how does it work?" in prompt
