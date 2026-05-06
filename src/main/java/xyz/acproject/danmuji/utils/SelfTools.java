package xyz.acproject.danmuji.utils;
public class SelfTools {

    /**
     * 修正版：按视觉宽度对齐
     * @param sb 目标 StringBuilder
     * @param targetVisualOffset 目标视觉位置（例如 40）
     * @param content 要追加的内容
     */
    public static void appendAt(StringBuilder sb, int targetVisualOffset, String content) {
        if (content == null) content = "";

        // 1. 计算当前内容的“视觉长度” (中文算2，英文算1)
        int currentVisualLen = getVisualLength(sb.toString());

        // 2. 计算还需要补多少“视觉宽度”
        int padding = targetVisualOffset - currentVisualLen;

        // 3. 如果不够，就补空格
        if (padding > 0) {
            sb.append(" ".repeat(padding));
        }

        // 4. 追加内容
        sb.append(content);
    }

    /**
     * 计算字符串的视觉长度
     * 规则：中文字符算2，其他算1
     */
    private static int getVisualLength(String str) {
        int length = 0;
        for (char c : str.toCharArray()) {
            if (isChineseChar(c)) {
                length += 2;
            } else {
                length += 1;
            }
        }
        return length;
    }

    /**
     * 简单的中文字符判断（覆盖大部分常用汉字）
     */
    private static boolean isChineseChar(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
                || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }
}