package com.repomatch.code;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class CodeFilesTest {

    @Test
    void indexesSourceAndDocsButNotBuildOutputOrLockfiles() {
        assertThat(CodeFiles.isIndexable("src/main/java/App.java", 900)).isTrue();
        assertThat(CodeFiles.isIndexable("README.md", 400)).isTrue();
        assertThat(CodeFiles.isIndexable("Dockerfile", 300)).isTrue();

        assertThat(CodeFiles.isIndexable("frontend/node_modules/react/index.js", 900)).isFalse();
        assertThat(CodeFiles.isIndexable("backend/target/classes/App.class", 900)).isFalse();
        assertThat(CodeFiles.isIndexable("package-lock.json", 900)).isFalse();
        assertThat(CodeFiles.isIndexable("static/app.min.js", 900)).isFalse();
        assertThat(CodeFiles.isIndexable("docs/diagram.png", 900)).isFalse();
        assertThat(CodeFiles.isIndexable("huge.py", 500_000)).isFalse();
    }

    @Test
    void chunksCarryOverlappingLineRangesThatAlwaysAdvance() {
        String content = IntStream.rangeClosed(1, 150).mapToObj(i -> "line " + i).collect(Collectors.joining("\n"));

        List<CodeFiles.Chunk> chunks = CodeFiles.chunk("a/b.py", content);

        assertThat(chunks).hasSizeGreaterThan(2);
        assertThat(chunks.get(0).startLine()).isEqualTo(1);
        assertThat(chunks.get(0).endLine()).isEqualTo(CodeFiles.MAX_LINES);
        // Each chunk starts before the previous one ended (overlap) but strictly after its start.
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).startLine()).isGreaterThan(chunks.get(i - 1).startLine());
            assertThat(chunks.get(i).startLine()).isLessThanOrEqualTo(chunks.get(i - 1).endLine());
        }
        assertThat(chunks.get(chunks.size() - 1).endLine()).isEqualTo(150);
        assertThat(chunks.get(0).text()).startsWith("line 1\nline 2");
    }

    @Test
    void skipsBlankContentAndKeepsThePathInEmbeddingText() {
        assertThat(CodeFiles.chunk("x.py", "   \n\n")).isEmpty();
        assertThat(CodeFiles.chunk("x.py", null)).isEmpty();

        CodeFiles.Chunk chunk = CodeFiles.chunk("pkg/mod.py", "def a():\n    return 1\n").get(0);
        assertThat(CodeFiles.embeddingText(chunk)).startsWith("pkg/mod.py (lines 1-2)");
    }
}
