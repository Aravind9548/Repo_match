package com.repomatch.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CodeSearchTest {

    private static CodeChunkEntity chunk(String path, float... embedding) {
        CodeChunkEntity c = new CodeChunkEntity();
        c.setPath(path);
        c.setEmbedding(embedding);
        c.setStartLine(1);
        c.setEndLine(10);
        c.setContent("code");
        return c;
    }

    @Test
    void cosineHandlesIdenticalOppositeAndEmptyVectors() {
        assertThat(CodeSearch.cosine(new float[] {1, 2, 3}, new float[] {2, 4, 6})).isCloseTo(1, within());
        assertThat(CodeSearch.cosine(new float[] {1, 0}, new float[] {-1, 0})).isCloseTo(-1, within());
        assertThat(CodeSearch.cosine(new float[] {0, 0}, new float[] {1, 1})).isZero();
        assertThat(CodeSearch.cosine(new float[] {1, 0}, null)).isZero();
        assertThat(CodeSearch.cosine(new float[] {1, 0}, new float[] {1, 0, 0})).isZero();
    }

    @Test
    void ranksByCosineAndCapsWindowsPerFile() {
        List<CodeChunkEntity> chunks = List.of(
                chunk("big.py", 1, 0),
                chunk("big.py", 0.99f, 0.01f),
                chunk("big.py", 0.98f, 0.02f),
                chunk("other.py", 0.9f, 0.1f));

        List<CodeSearch.Hit> hits = CodeSearch.topMatches(chunks, new float[] {1, 0}, 3, 2);

        assertThat(hits).hasSize(3);
        assertThat(hits).extracting(h -> h.chunk().getPath()).containsExactly("big.py", "big.py", "other.py");
        assertThat(hits.get(0).similarity()).isGreaterThan(hits.get(2).similarity());
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(1e-6);
    }
}
