from app.explainer import _LlmOutput, _LlmRepoFit, build_prompt, postprocess
from app.schemas import ExplainCandidate

REPO = ExplainCandidate(
    id=1,
    name="docuflow",
    description="Document workflow service",
    languages=["Java"],
    topics=["spring-boot"],
    readme="Built with **Spring Boot** and PostgreSQL. Exposes a REST API for uploads.",
)


def _fit(**kw):
    base = dict(id=1, fit_score=80, reason="Shows Spring Boot REST work.", matched_requirements=["Java"], evidence=[])
    return _LlmRepoFit(**{**base, **kw})


def test_keeps_only_evidence_present_in_repo():
    out = _LlmOutput(results=[_fit(evidence=["Built with Spring Boot and PostgreSQL", "Deployed on AWS Lambda"])])
    [result] = postprocess(out, [REPO])
    assert result.evidence == ["Built with Spring Boot and PostgreSQL"]


def test_drops_unknown_and_duplicate_ids_and_clamps_scores():
    out = _LlmOutput(results=[_fit(fit_score=140), _fit(id=1, fit_score=10), _fit(id=99)])
    results = postprocess(out, [REPO])
    assert [r.id for r in results] == [1]
    assert results[0].fit_score == 100


def test_prompt_marks_missing_readme():
    bare = ExplainCandidate(id=2, name="empty-repo")
    prompt = build_prompt("Need a Java developer", [bare], max_readme_chars=100)
    assert '<repository id="2">' in prompt
    assert "(no README)" in prompt
