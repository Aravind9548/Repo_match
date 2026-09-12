package com.repomatch.code;

import java.util.List;

import org.springframework.stereotype.Service;

import com.repomatch.ai.AiServiceClient;
import com.repomatch.ai.dto.AnswerRequest;
import com.repomatch.ai.dto.EmbedRequest;
import com.repomatch.ai.dto.ReadmeRequest;
import com.repomatch.ai.dto.ReadmeResponse;
import com.repomatch.analysis.UsageGuard;
import com.repomatch.repo.RepoEntity;
import com.repomatch.repo.RepoRepository;

/**
 * Drafts a README for an indexed repository from its own code.
 *
 * <p>Five of a typical account's repos have no README, and a repo without one reads as unfinished
 * to a recruiter and ranks worse here — there is simply less for the matcher to read.
 */
@Service
public class ReadmeService {

    /**
     * What a README has to answer. Embedding this as a query pulls the entry points, configuration
     * and route definitions to the top instead of whatever file happens to be alphabetically first.
     */
    private static final String SHAPE_QUERY = """
            What does this project do, what is its entry point, how is it configured and run, \
            what are its main modules, dependencies, commands and endpoints?""";

    /** Wider than the ask path: a README summarises the whole repo, not one question. */
    static final int TOP_CHUNKS = 24;
    static final int MAX_PER_FILE = 2;

    private final RepoRepository repos;
    private final CodeChunkRepository chunks;
    private final RepoIndexRepository indexes;
    private final AiServiceClient ai;
    private final UsageGuard usage;

    public ReadmeService(RepoRepository repos, CodeChunkRepository chunks, RepoIndexRepository indexes,
                         AiServiceClient ai, UsageGuard usage) {
        this.repos = repos;
        this.chunks = chunks;
        this.indexes = indexes;
        this.ai = ai;
        this.usage = usage;
    }

    /** @param hadReadme so the UI can warn before anyone overwrites an existing one. */
    public record Draft(long repoId, String repo, String markdown, boolean hadReadme, int filesUsed,
                        List<String> sources, String llmModel, AskService.Usage usage) {
    }

    public Draft draft(long repoId, String clientKey) {
        RepoEntity repo = repos.findById(repoId).orElseThrow(() -> new RepoNotFoundException(repoId));
        if (indexes.findById(repoId).isEmpty()) {
            throw new NotIndexedException(repo.getName());
        }

        float[] query = ai.embed(EmbedRequest.query(SHAPE_QUERY)).vectors().getFirst();
        List<CodeSearch.Hit> hits = CodeSearch.topMatches(chunks.findByRepoId(repoId), query, TOP_CHUNKS, MAX_PER_FILE);
        if (hits.isEmpty()) {
            throw new NotIndexedException(repo.getName());
        }

        usage.acquireLlmCall(clientKey);
        ReadmeResponse response = ai.readme(new ReadmeRequest(repo.getFullName(), repo.getDescription(),
                topLanguages(repo),
                hits.stream()
                        .map(h -> new AnswerRequest.Chunk(h.chunk().getPath(), h.chunk().getStartLine(),
                                h.chunk().getEndLine(), h.chunk().getContent()))
                        .toList()));

        List<String> sources = hits.stream().map(h -> h.chunk().getPath()).distinct().sorted().toList();
        boolean hadReadme = repo.getReadme() != null && !repo.getReadme().isBlank();
        return new Draft(repoId, repo.getFullName(), response.markdown(), hadReadme, response.filesUsed(), sources,
                response.llmModel(), AskService.Usage.of(response.usage()));
    }

    /** GitHub reports languages as bytes per language; biggest first is the order a human would write. */
    private static List<String> topLanguages(RepoEntity repo) {
        if (repo.getLanguages() == null) {
            return List.of();
        }
        return repo.getLanguages().entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue().reversed())
                .map(java.util.Map.Entry::getKey)
                .limit(6)
                .toList();
    }
}
