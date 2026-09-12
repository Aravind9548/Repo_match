package com.repomatch.code;

import java.util.List;

import org.springframework.stereotype.Service;

import com.repomatch.ai.AiServiceClient;
import com.repomatch.ai.dto.AnswerRequest;
import com.repomatch.ai.dto.AnswerResponse;
import com.repomatch.ai.dto.EmbedRequest;
import com.repomatch.ai.dto.ExplainResponse;
import com.repomatch.analysis.UsageGuard;
import com.repomatch.repo.RepoEntity;
import com.repomatch.repo.RepoRepository;

/** Answers a question about one indexed repository, from its own code. */
@Service
public class AskService {

    /** Enough context for a real answer without flooding the prompt. */
    static final int TOP_CHUNKS = 14;
    static final int MAX_PER_FILE = 3;

    private final RepoRepository repos;
    private final CodeChunkRepository chunks;
    private final RepoIndexRepository indexes;
    private final AiServiceClient ai;
    private final UsageGuard usage;

    public AskService(RepoRepository repos, CodeChunkRepository chunks, RepoIndexRepository indexes,
                      AiServiceClient ai, UsageGuard usage) {
        this.repos = repos;
        this.chunks = chunks;
        this.indexes = indexes;
        this.ai = ai;
        this.usage = usage;
    }

    /** @param snippet the indexed code the citation points at, so the reader can check it. */
    public record Source(String path, int startLine, int endLine, String snippet, double similarity) {
    }

    public record Answer(long repoId, String repo, String question, String answer, boolean answered,
                         List<Source> citations, List<Source> searched, String llmModel, Usage usage) {
    }

    /** Token counts in this API's camelCase shape (the AI service speaks snake_case). */
    public record Usage(int promptTokens, int outputTokens, int thinkingTokens) {

        static Usage of(ExplainResponse.Usage u) {
            return u == null ? null : new Usage(u.promptTokens(), u.outputTokens(), u.thinkingTokens());
        }
    }

    public Answer ask(long repoId, String question, String clientKey) {
        RepoEntity repo = repos.findById(repoId).orElseThrow(() -> new RepoNotFoundException(repoId));
        if (indexes.findById(repoId).isEmpty()) {
            throw new NotIndexedException(repo.getName());
        }

        float[] query = ai.embed(EmbedRequest.query(question.strip())).vectors().getFirst();
        List<CodeSearch.Hit> hits = CodeSearch.topMatches(chunks.findByRepoId(repoId), query, TOP_CHUNKS, MAX_PER_FILE);
        if (hits.isEmpty()) {
            throw new NotIndexedException(repo.getName());
        }

        usage.acquireLlmCall(clientKey);
        AnswerResponse response = ai.answer(new AnswerRequest(repo.getFullName(), question.strip(),
                hits.stream()
                        .map(h -> new AnswerRequest.Chunk(h.chunk().getPath(), h.chunk().getStartLine(),
                                h.chunk().getEndLine(), h.chunk().getContent()))
                        .toList()));

        List<Source> citations = response.citations() == null ? List.of()
                : response.citations().stream().map(c -> toSource(c, hits)).toList();
        List<Source> searched = hits.stream()
                .map(h -> new Source(h.chunk().getPath(), h.chunk().getStartLine(), h.chunk().getEndLine(), null,
                        round(h.similarity())))
                .toList();

        return new Answer(repoId, repo.getFullName(), question.strip(), response.answer(), response.answered(),
                citations, searched, response.llmModel(), Usage.of(response.usage()));
    }

    /** Attaches the indexed text behind a citation so the UI can show it without another fetch. */
    private static Source toSource(AnswerResponse.Citation citation, List<CodeSearch.Hit> hits) {
        return hits.stream()
                .filter(h -> h.chunk().getPath().equals(citation.path()))
                .filter(h -> h.chunk().getStartLine() <= citation.endLine() && h.chunk().getEndLine() >= citation.startLine())
                .findFirst()
                .map(h -> new Source(citation.path(), citation.startLine(), citation.endLine(), h.chunk().getContent(),
                        round(h.similarity())))
                .orElseGet(() -> new Source(citation.path(), citation.startLine(), citation.endLine(), null, 0));
    }

    private static double round(double v) {
        return Math.round(v * 10_000d) / 10_000d;
    }
}
