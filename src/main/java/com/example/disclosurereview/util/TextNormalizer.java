package com.example.disclosurereview.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * 文本规范化工具：清理不可见字符、多余空白、统一中英文标点。
 */
public final class TextNormalizer {

    private static final Pattern INVISIBLE = Pattern.compile("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u00AD\\u200B\\u200C\\u200D\\uFEFF]");
    private static final Pattern MULTI_BLANK_LINE = Pattern.compile("(\\R[ \\t]*){3,}");

    private TextNormalizer() {
    }

    /**
     * 页面文本规范化：去除不可见字符、合并空格、压缩连续空行。
     */
    public static String normalizePage(String text) {
        if (text == null) {
            return "";
        }
        String t = INVISIBLE.matcher(text).replaceAll("");
        t = t.replace('　', ' ');
        // 行内多余空格合并
        t = t.replaceAll("[ \\t]+", " ");
        // 去掉行尾空格
        t = t.replaceAll("[ \\t]+(\\R)", "$1");
        // 连续空行压缩为一个空行
        t = MULTI_BLANK_LINE.matcher(t).replaceAll("\n\n");
        return t.strip();
    }

    /**
     * 证据回查用的宽松规范化：忽略全部空白，并统一中英文标点，用于子串匹配。
     */
    public static String normalizeForMatch(String text) {
        if (text == null) {
            return "";
        }
        String t = Normalizer.normalize(text, Normalizer.Form.NFKC);
        t = INVISIBLE.matcher(t).replaceAll("");
        t = unifyPunctuation(t);
        // 忽略所有空白字符
        t = t.replaceAll("\\s+", "");
        return t;
    }

    /**
     * 统一中英文标点（冒号、括号、引号、逗号、句号、分号等）。
     */
    public static String unifyPunctuation(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace('：', ':')
                .replace('（', '(').replace('）', ')')
                .replace('，', ',')
                .replace('。', '.')
                .replace('；', ';')
                .replace('！', '!')
                .replace('？', '?')
                .replace('“', '"').replace('”', '"')
                .replace('‘', '\'').replace('’', '\'')
                .replace('、', ',')
                .replace('—', '-').replace('–', '-');
    }
}
