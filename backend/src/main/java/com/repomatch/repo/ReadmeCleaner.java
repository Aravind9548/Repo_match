package com.repomatch.repo;

import java.util.regex.Pattern;

/**
 * Strips README noise that carries no signal about what a project does
 * (badges, images, HTML, link URLs) so embeddings and the LLM see prose and code.
 */
public final class ReadmeCleaner {

    private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern REF_IMAGE = Pattern.compile("!\\[[^\\]]*]\\[[^\\]]*]");
    private static final Pattern EMPTY_LINK = Pattern.compile("\\[\\s*]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\([^)]*\\)");
    private static final Pattern LINK_DEF = Pattern.compile("(?m)^\\s*\\[[^\\]]+]:\\s*\\S+.*$");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern TRAILING_SPACE = Pattern.compile("(?m)[ \\t]+$");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private ReadmeCleaner() {
    }

    public static String clean(String markdown, int maxChars) {
        if (markdown == null) {
            return null;
        }
        String s = markdown.replace("\r\n", "\n");
        s = HTML_COMMENT.matcher(s).replaceAll("");
        s = IMAGE.matcher(s).replaceAll("");
        s = REF_IMAGE.matcher(s).replaceAll("");
        s = EMPTY_LINK.matcher(s).replaceAll("");
        s = LINK.matcher(s).replaceAll("$1");
        s = LINK_DEF.matcher(s).replaceAll("");
        s = HTML_TAG.matcher(s).replaceAll("");
        s = TRAILING_SPACE.matcher(s).replaceAll("");
        s = BLANK_LINES.matcher(s).replaceAll("\n\n").strip();
        if (s.isEmpty()) {
            return null;
        }
        return s.length() > maxChars ? s.substring(0, maxChars) : s;
    }
}
