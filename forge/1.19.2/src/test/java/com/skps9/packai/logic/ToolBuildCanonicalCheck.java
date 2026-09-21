package com.skps9.packai.logic;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MODIFIED Tetra frames get one canonical parts line. STANDARD / UNKNOWN stay unchanged.
 * AskEngine hook must sit after the unique STANDARD anchor and before InfoCompleteness.append.
 */
public final class ToolBuildCanonicalCheck {
    private static final String A1 =
            "if (frameKind == ModularFrameStandard.Kind.STANDARD && frameMatch.recipeIndex() != null) {";
    private static final String A2 = "body = InfoCompleteness.append(";
    private static final String H = "AskJeiHints.ensureToolBuildPartsLine(";

    private ToolBuildCanonicalCheck() {}

    public static void main(String[] args) throws Exception {
        String build = """
                [TOOL_BUILD]
                part head: tetra:netherite material tetra:netherite name 下界合金
                part shaft: tetra:beam material tetra:beam name 再利用梁杆
                """;
        String answer = "plain answer";
        String standard = AskJeiHints.ensureToolBuildPartsLine(
                answer, ModularFrameStandard.Kind.STANDARD, build, "zh_cn");
        assert answer.equals(standard) : standard;
        assert !standard.contains("下界合金") : standard;

        String modified = AskJeiHints.ensureToolBuildPartsLine(
                answer, ModularFrameStandard.Kind.MODIFIED, build, "zh_cn");
        assert modified.contains("下界合金") && modified.contains("再利用梁杆") : modified;
        assert modified.contains(ReplyLang.toolBuildCanonical("zh_cn", "head＝下界合金＋shaft＝再利用梁杆"))
                : modified;

        String unknown = AskJeiHints.ensureToolBuildPartsLine(
                answer, ModularFrameStandard.Kind.UNKNOWN, build, "zh_cn");
        assert answer.equals(unknown) : unknown;
        assert !unknown.contains("下界合金") : unknown;

        String hammer = AskJeiHints.partsNames(
                "part double/head_left: double/basic_hammer_left material basic_hammer/netherite"
                        + " name 下界合金 item tetra:basic_hammer_left");
        assert hammer.contains("下界合金") : hammer;
        assert !hammer.contains("tetra:") : hammer;
        assert !hammer.contains("item ") : hammer;

        String src = Files.readString(engineSource());
        int c1 = count(src, A1);
        int c2 = count(src, A2);
        int ch = count(src, H);
        assert c1 == 1 : "A1 count " + c1;
        assert c2 == 1 : "A2 count " + c2;
        assert ch == 1 : "H count " + ch;
        int i1 = src.indexOf(A1);
        int i2 = src.indexOf(A2);
        int ih = src.indexOf(H);
        assert i1 < ih && ih < i2 : "order A1=" + i1 + " H=" + ih + " A2=" + i2;
        int open = src.indexOf('{', i1);
        assert open > i1 : "A1 must open a block";
        int close = matchingBrace(src, open);
        assert src.charAt(close) == '}' : "close " + close;
        assert ih > close : "H must be AFTER the STANDARD block closes (close=" + close + " H=" + ih + ")";
        assert ih < i2 : "H must be before InfoCompleteness.append";

        System.out.println("close=" + close + " H=" + ih);
        System.out.println("ToolBuildCanonicalCheck OK");
    }

    /** 由 src 內 index openIdx（必須係 '{'）搵對應配對 '}' 嘅 index；唔平衡就 throw。 */
    static int matchingBrace(String src, int openIdx) {
        if (src == null || openIdx < 0 || openIdx >= src.length() || src.charAt(openIdx) != '{') {
            throw new IllegalArgumentException("openIdx must be '{': " + openIdx);
        }
        int depth = 0;
        boolean inString = false;
        boolean inChar = false;
        boolean escape = false;
        for (int i = openIdx; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (inChar) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && i + 1 < src.length() && src.charAt(i + 1) == '/') {
                int nl = src.indexOf('\n', i);
                if (nl < 0) {
                    break;
                }
                i = nl;
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        throw new IllegalStateException("unbalanced brace at " + openIdx);
    }

    static int count(String src, String needle) {
        int n = 0;
        int i = 0;
        while (i < src.length()) {
            int j = src.indexOf(needle, i);
            if (j < 0) {
                return n;
            }
            n++;
            i = j + needle.length();
        }
        return n;
    }

    static Path engineSource() {
        Path[] candidates = {
                Path.of("src/main/java/com/skps9/packai/logic/AskEngine.java"),
                Path.of("forge/1.19.2/src/main/java/com/skps9/packai/logic/AskEngine.java"),
        };
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        throw new AssertionError("AskEngine.java not found from " + Path.of("").toAbsolutePath());
    }
}
