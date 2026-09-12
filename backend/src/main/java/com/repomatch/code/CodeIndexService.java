package com.repomatch.code;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.repomatch.ai.AiServiceClient;
import com.repomatch.ai.dto.EmbedRequest;
import com.repomatch.ai.dto.EmbedResponse;
import com.repomatch.config.AppProperties;
import com.repomatch.github.GitHubClient;
import com.repomatch.github.dto.GitHubTree;
import com.repomatch.repo.RepoEntity;
import com.repomatch.repo.RepoRepository;

/**
 * Reads a repository's source from GitHub, slices it into line windows and embeds them, so
 * questions can be answered from the code itself. Optional: nothing here runs during a match.
 */
@Service
public class CodeIndexService {

    private static final Logger log = LoggerFactory.getLogger(CodeIndexService.class);

    /** Caps keep one repository's index (and its embedding cost) predictable. */
    static final int MAX_FILES = 120;
    static final int MAX_CHUNKS = 600;
    private static final int EMBED_BATCH = 96;

    private final GitHubClient gitHub;
    private final RepoRepository repos;
    private final CodeChunkRepository chunks;
    private final RepoIndexRepository indexes;
    private final AiServiceClient ai;
    private final int parallelRequests;

    public CodeIndexService(GitHubClient gitHub, RepoRepository repos, CodeChunkRepository chunks,
                            RepoIndexRepository indexes, AiServiceClient ai, AppProperties props) {
        this.gitHub = gitHub;
        this.repos = repos;
        this.chunks = chunks;
        this.indexes = indexes;
        this.ai = ai;
        this.parallelRequests = props.github().maxParallelRequests();
    }

    public record IndexSummary(long repoId, String repo, String commitSha, int fileCount, int chunkCount,
                               int skippedFiles, String model, Instant indexedAt, boolean rebuilt) {
    }

    public IndexSummary status(long repoId) {
        RepoEntity repo = repo(repoId);
        RepoIndexEntity index = indexes.findById(repoId).orElseThrow(() -> new NotIndexedException(repo.getName()));
        return summary(repo, index, false);
    }

    public IndexSummary index(long repoId, boolean force) {
        RepoEntity repo = repo(repoId);
        String[] parts = repo.getFullName().split("/", 2);
        String owner = parts[0];
        String name = parts.length > 1 ? parts[1] : repo.getName();
        String branch = repo.getDefaultBranch() == null || repo.getDefaultBranch().isBlank() ? "HEAD" : repo.getDefaultBranch();

        GitHubTree tree = gitHub.getTree(owner, name, branch);
        List<GitHubTree.Entry> indexable = tree.tree().stream()
                .filter(GitHubTree.Entry::isFile)
                .filter(e -> CodeFiles.isIndexable(e.path(), e.size() == null ? 0 : e.size()))
                .toList();
        if (indexable.isEmpty()) {
            throw new NothingToIndexException(repo.getName());
        }

        RepoIndexEntity existing = indexes.findById(repoId).orElse(null);
        if (!force && existing != null && tree.sha() != null && Objects.equals(existing.getCommitSha(), tree.sha())) {
            log.debug("Index for {} is current", repo.getFullName());
            return summary(repo, existing, false);
        }

        // Shallow files first: they're usually the entry points, and the cap bites the deep ones.
        List<GitHubTree.Entry> selected = indexable.stream()
                .sorted(Comparator.<GitHubTree.Entry>comparingInt(e -> (int) e.path().chars().filter(c -> c == '/').count())
                        .thenComparing(GitHubTree.Entry::path))
                .limit(MAX_FILES)
                .toList();

        List<CodeFiles.Chunk> sliced = sliceAll(owner, name, branch, selected);
        if (sliced.isEmpty()) {
            throw new NothingToIndexException(repo.getName());
        }

        List<float[]> vectors = new ArrayList<>(sliced.size());
        String model = null;
        for (int from = 0; from < sliced.size(); from += EMBED_BATCH) {
            List<CodeFiles.Chunk> batch = sliced.subList(from, Math.min(from + EMBED_BATCH, sliced.size()));
            EmbedResponse res = ai.embed(EmbedRequest.documents(batch.stream().map(CodeFiles::embeddingText).toList()));
            vectors.addAll(res.vectors());
            model = res.model();
        }

        chunks.deleteByRepoId(repoId);
        List<CodeChunkEntity> rows = new ArrayList<>(sliced.size());
        for (int i = 0; i < sliced.size(); i++) {
            CodeFiles.Chunk c = sliced.get(i);
            CodeChunkEntity row = new CodeChunkEntity();
            row.setRepoId(repoId);
            row.setPath(c.path());
            row.setStartLine(c.startLine());
            row.setEndLine(c.endLine());
            row.setContent(c.text());
            row.setEmbedding(vectors.get(i));
            rows.add(row);
        }
        chunks.saveAll(rows);

        RepoIndexEntity index = existing != null ? existing : new RepoIndexEntity();
        index.setRepoId(repoId);
        index.setCommitSha(tree.sha());
        index.setFileCount(selected.size());
        index.setChunkCount(rows.size());
        index.setSkippedFiles(Math.max(0, tree.tree().size() - indexable.size()));
        index.setModel(model);
        index.setIndexedAt(Instant.now());
        indexes.save(index);

        log.info("Indexed {}: {} files, {} chunks", repo.getFullName(), selected.size(), rows.size());
        return summary(repo, index, true);
    }

    /** Downloads the selected files in parallel and slices them, stopping at the chunk cap. */
    private List<CodeFiles.Chunk> sliceAll(String owner, String name, String branch, List<GitHubTree.Entry> files) {
        Semaphore permits = new Semaphore(parallelRequests);
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<CodeFiles.Chunk>>> futures = files.stream()
                    .map(file -> exec.submit(() -> {
                        permits.acquire();
                        try {
                            return gitHub.getFileContent(owner, name, file.path(), branch)
                                    .map(content -> CodeFiles.chunk(file.path(), content))
                                    .orElse(List.of());
                        } finally {
                            permits.release();
                        }
                    }))
                    .toList();

            List<CodeFiles.Chunk> all = new ArrayList<>();
            for (Future<List<CodeFiles.Chunk>> f : futures) {
                if (all.size() >= MAX_CHUNKS) break;
                for (CodeFiles.Chunk chunk : f.get()) {
                    all.add(chunk);
                    if (all.size() >= MAX_CHUNKS) break;
                }
            }
            return all;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) throw re;
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading repository files", e);
        }
    }

    private RepoEntity repo(long repoId) {
        return repos.findById(repoId).orElseThrow(() -> new RepoNotFoundException(repoId));
    }

    private static IndexSummary summary(RepoEntity repo, RepoIndexEntity index, boolean rebuilt) {
        return new IndexSummary(repo.getId(), repo.getFullName(), index.getCommitSha(), index.getFileCount(),
                index.getChunkCount(), index.getSkippedFiles(), index.getModel(), index.getIndexedAt(), rebuilt);
    }
}
