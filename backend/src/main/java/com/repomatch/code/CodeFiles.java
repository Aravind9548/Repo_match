package com.repomatch.code;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Which files in a repository are worth indexing, and how they are sliced for retrieval. */
public final class CodeFiles {

    /** Source and docs we can answer questions from. */
    private static final Set<String> EXTENSIONS = Set.of(
            "java", "kt", "scala", "groovy", "py", "rb", "php", "go", "rs", "c", "h", "cc", "cpp", "hpp", "cs",
            "ts", "tsx", "js", "jsx", "mjs", "cjs", "vue", "svelte", "swift", "m", "sql", "sh", "bash", "ps1",
            "yml", "yaml", "toml", "ini", "cfg", "conf", "gradle", "tf", "dockerfile", "md", "rst", "txt");

    /** Directories and artefacts that are never the author's own work. */
    private static final List<String> SKIP_PARTS = List.of(
            "node_modules/", "/dist/", "/build/", "/target/", "/vendor/", "/.venv/", "venv/", "site-packages/",
            "/__pycache__/", "/.git/", "/coverage/", "/.next/", "/out/", "migrations/node", "/third_party/");

    private static final List<String> SKIP_NAMES = List.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "poetry.lock", "gemfile.lock", "cargo.lock",
            "go.sum", "composer.lock");

    /** A single file is rarely useful past this; huge generated files are usually noise. */
    static final int MAX_FILE_CHARS = 80_000;
    /** Retrieval windows: ~60 lines with a small overlap so a function isn't split away from its head. */
    static final int MAX_LINES = 60;
    static final int OVERLAP_LINES = 8;
    static final int MAX_CHUNK_CHARS = 4_000;

    private CodeFiles() {
    }

    public record Chunk(String path, int startLine, int endLine, String text) {
    }

    public static boolean isIndexable(String path, long sizeBytes) {
        String lower = path.toLowerCase(Locale.ROOT);
        String slashed = "/" + lower;
        if (SKIP_PARTS.stream().anyMatch(slashed::contains)) return false;
        String name = lower.substring(lower.lastIndexOf('/') + 1);
        if (SKIP_NAMES.contains(name)) return false;
        if (lower.contains(".min.")) return false;
        if (sizeBytes > MAX_FILE_CHARS) return false;
        if (name.equals("dockerfile") || name.equals("makefile")) return true;
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }

    /** Slices one file into overlapping line windows, carrying the line range for citations. */
    public static List<Chunk> chunk(String path, String content) {
        if (content == null || content.isBlank()) return List.of();
        String[] lines = content.replace("\r\n", "\n").split("\n", -1);
        // A trailing newline isn't a line; counting it would shift every citation by one.
        if (lines.length > 1 && lines[lines.length - 1].isEmpty()) {
            lines = java.util.Arrays.copyOf(lines, lines.length - 1);
        }
        List<Chunk> chunks = new java.util.ArrayList<>();

        int start = 0;
        while (start < lines.length) {
            StringBuilder text = new StringBuilder();
            int end = start;
            while (end < lines.length && end - start < MAX_LINES && text.length() + lines[end].length() + 1 <= MAX_CHUNK_CHARS) {
                text.append(lines[end]).append('\n');
                end++;
            }
            if (end == start) {
                // One line longer than the whole budget: take it truncated so we always advance.
                text.append(lines[start], 0, Math.min(lines[start].length(), MAX_CHUNK_CHARS));
                end = start + 1;
            }
            if (!text.toString().isBlank()) {
                chunks.add(new Chunk(path, start + 1, end, text.toString().stripTrailing()));
            }
            if (end >= lines.length) break;
            start = Math.max(start + 1, end - OVERLAP_LINES);
        }
        return chunks;
    }

    /** What the embedding model sees: the path plus the code, so file names are searchable too. */
    public static String embeddingText(Chunk chunk) {
        return chunk.path() + " (lines " + chunk.startLine() + "-" + chunk.endLine() + ")\n" + chunk.text();
    }
}
