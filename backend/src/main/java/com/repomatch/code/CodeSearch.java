package com.repomatch.code;

import java.util.Comparator;
import java.util.List;

/** Retrieval over indexed code chunks: cosine similarity, best first. */
public final class CodeSearch {

    private CodeSearch() {
    }

    public record Hit(CodeChunkEntity chunk, double similarity) {
    }

    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /**
     * Top matches for the question, at most {@code perFile} windows from any one file so a single
     * long file can't crowd out the rest of the repository.
     */
    public static List<Hit> topMatches(List<CodeChunkEntity> chunks, float[] query, int limit, int perFile) {
        java.util.Map<String, Integer> used = new java.util.HashMap<>();
        return chunks.stream()
                .map(c -> new Hit(c, cosine(c.getEmbedding(), query)))
                .sorted(Comparator.comparingDouble(Hit::similarity).reversed())
                .filter(hit -> used.merge(hit.chunk().getPath(), 1, Integer::sum) <= perFile)
                .limit(limit)
                .toList();
    }
}
