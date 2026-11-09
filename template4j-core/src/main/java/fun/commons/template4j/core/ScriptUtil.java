package fun.commons.template4j.core;

import fun.commons.template4j.utils.UString;

import java.util.List;


public class ScriptUtil {

    /**
     * 把 Groovy 脚本里的 {@code @xxx} 重写成对 {@code data} 的访问,以便在脚本里
     * 直接读取模板数据。规则(按优先级匹配):
     *
     * <pre>
     *   @..path        →  data("..path")     // JSONPath deepScan,例如 @..name
     *   @( / @[ / @.   →  data( / data[ / data.   // 函数调用 / 数组下标 / 字段访问
     *   @wordChar      →  data.wordChar      // 字段访问(@name → data.name)
     *   @terminator    →  data('$')          // 终结符场景(@ 单独出现 → 整个 data 根)
     * </pre>
     *
     * <p>字符串字面量、行注释、块注释内的 {@code @} 不重写 — 用单遍字符扫描状态机
     * 替代原来的 4 次 encode + 4 次 regex replace 的两阶段做法。
     */
    protected static String codeReplaceAt(String program) {
        StringBuilder out = new StringBuilder(program.length() + 16);
        int i = 0;
        int n = program.length();
        int state = NORMAL;
        while (i < n) {
            char c = program.charAt(i);
            char next = (i + 1 < n) ? program.charAt(i + 1) : '\0';
            switch (state) {
                case NORMAL:
                    if (c == '/' && next == '*') { out.append(c); i++; state = BLOCK_COMMENT; continue; }
                    if (c == '/' && next == '/') { out.append(c); i++; state = LINE_COMMENT; continue; }
                    if (c == '\'') { out.append(c); i++; state = SQUOTE; continue; }
                    if (c == '"')  { out.append(c); i++; state = DQUOTE; continue; }
                    if (c == '@') {
                        int[] consumed = rewriteAt(program, i, out);
                        if (consumed != null) { i = consumed[0]; continue; }
                        // no rule matched — preserve @ as literal
                    }
                    out.append(c); i++; break;
                case BLOCK_COMMENT:
                    out.append(c);
                    if (c == '*' && next == '/') { out.append(next); i += 2; state = NORMAL; continue; }
                    i++; break;
                case LINE_COMMENT:
                    out.append(c);
                    if (c == '\n') { state = NORMAL; }
                    i++; break;
                case SQUOTE:
                    out.append(c);
                    if (c == '\\' && next != '\0') { out.append(next); i += 2; continue; }
                    if (c == '\'') { state = NORMAL; }
                    i++; break;
                case DQUOTE:
                    out.append(c);
                    if (c == '\\' && next != '\0') { out.append(next); i += 2; continue; }
                    if (c == '"') { state = NORMAL; }
                    i++; break;
            }
        }
        return out.toString();
    }

    /**
     * 把 program 中位置 i 的 {@code @...} 重写后追加到 out。
     * 返回 [nextIndex],null 表示没有规则匹配(保留字面 @)。
     */
    private static int[] rewriteAt(String program, int i, StringBuilder out) {
        int n = program.length();
        char next = (i + 1 < n) ? program.charAt(i + 1) : '\0';
        char next2 = (i + 2 < n) ? program.charAt(i + 2) : '\0';

        // Rule 1: @..path → data("..path")  (JSONPath deepScan)
        if (next == '.' && next2 == '.') {
            int j = i + 3;
            while (j < n && isPathChar(program.charAt(j))) j++;
            if (j > i + 3) {  // 至少一个 path 字符
                String path = program.substring(i + 3, j);
                out.append("data(\"..").append(path).append("\")");
                return new int[]{ j };
            }
        }

        // Rule 2: @( / @[ / @. (单点,排除 @..)
        if (next == '(' || next == '[' || (next == '.' && next2 != '.')) {
            out.append("data");
            return new int[]{ i + 1 };
        }

        // Rule 3: @<wordStartChar> → data.
        if (isWordStartChar(next)) {
            out.append("data.");
            return new int[]{ i + 1 };
        }

        // Rule 4: @<terminator or end-of-string> → data('$')
        if (next == '\0' || isTerminator(next)) {
            out.append("data('$')");
            return new int[]{ i + 1 };
        }

        return null;
    }

    /** deepScan 路径允许的字符:[\w.\[\]'"\d] 等 */
    private static boolean isPathChar(char c) {
        return Character.isLetterOrDigit(c)
            || c == '_' || c == '.'
            || c == '[' || c == ']'
            || c == '\'' || c == '"' || c == '-';
    }

    /** Rule 3 的字符类:[\\_w'"$] */
    private static boolean isWordStartChar(char c) {
        return Character.isLetterOrDigit(c)
            || c == '_' || c == '\'' || c == '"' || c == '$' || c == '\\';
    }

    /** Rule 4 的终结符:[\s)}+|&;,] */
    private static boolean isTerminator(char c) {
        return Character.isWhitespace(c)
            || c == ')' || c == '}' || c == '+'
            || c == '|' || c == '&' || c == ';' || c == ',';
    }

    // 状态机状态常量
    private static final int NORMAL = 0;
    private static final int BLOCK_COMMENT = 1;
    private static final int LINE_COMMENT = 2;
    private static final int SQUOTE = 3;
    private static final int DQUOTE = 4;


    protected static String getProgramBesieged(Object obj) {

        String program = null;

        if (obj instanceof List) {
            program = String.join("\r\n", (List) obj);
        } else {
            program = String.valueOf(obj);
        }

        while (UString.isBesieged(program, "{%", "%}")) {
            program = UString.extractBesieged(program, "{%", "%}");
        }

        return "{%" + program + "%}";
    }

}
