package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

/**
 * 直接覆盖 ScriptUtil 静态 helper 的字符分支: isPathChar / isWordStartChar / isTerminator。
 * 也覆盖 codeReplaceAt 中 BLOCK_COMMENT 关闭、SQUOTE/DQUOTE escape、rewriteAt deepScan 等分支。
 */
public class ScriptUtilHelpersTest {

    private static boolean invokeIsPathChar(char c) throws Exception {
        Method m = ScriptUtil.class.getDeclaredMethod("isPathChar", char.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, c);
    }

    private static boolean invokeIsWordStartChar(char c) throws Exception {
        Method m = ScriptUtil.class.getDeclaredMethod("isWordStartChar", char.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, c);
    }

    private static boolean invokeIsTerminator(char c) throws Exception {
        Method m = ScriptUtil.class.getDeclaredMethod("isTerminator", char.class);
        m.setAccessible(true);
        return (boolean) m.invoke(null, c);
    }

    private static String invokeCodeReplaceAt(String program) throws Exception {
        Method m = ScriptUtil.class.getDeclaredMethod("codeReplaceAt", String.class);
        m.setAccessible(true);
        return (String) m.invoke(null, program);
    }

    private static int[] invokeRewriteAt(String program, int i, StringBuilder out) throws Exception {
        Method m = ScriptUtil.class.getDeclaredMethod("rewriteAt", String.class, int.class, StringBuilder.class);
        m.setAccessible(true);
        return (int[]) m.invoke(null, program, i, out);
    }

    // ---- isPathChar ----

    @Test
    public void isPathCharLetter() throws Exception {
        Assertions.assertTrue(invokeIsPathChar('a'));
        Assertions.assertTrue(invokeIsPathChar('Z'));
    }

    @Test
    public void isPathCharDigit() throws Exception {
        Assertions.assertTrue(invokeIsPathChar('0'));
        Assertions.assertTrue(invokeIsPathChar('9'));
    }

    @Test
    public void isPathCharUnderscoreAndDot() throws Exception {
        Assertions.assertTrue(invokeIsPathChar('_'));
        Assertions.assertTrue(invokeIsPathChar('.'));
    }

    @Test
    public void isPathCharBrackets() throws Exception {
        Assertions.assertTrue(invokeIsPathChar('['));
        Assertions.assertTrue(invokeIsPathChar(']'));
    }

    @Test
    public void isPathCharQuoteAndDash() throws Exception {
        Assertions.assertTrue(invokeIsPathChar('\''));
        Assertions.assertTrue(invokeIsPathChar('"'));
        Assertions.assertTrue(invokeIsPathChar('-'));
    }

    @Test
    public void isPathCharOtherReturnsFalse() throws Exception {
        Assertions.assertFalse(invokeIsPathChar('@'));
        Assertions.assertFalse(invokeIsPathChar(' '));
        Assertions.assertFalse(invokeIsPathChar('+'));
    }

    // ---- isWordStartChar ----

    @Test
    public void isWordStartCharLetter() throws Exception {
        Assertions.assertTrue(invokeIsWordStartChar('a'));
    }

    @Test
    public void isWordStartCharUnderscore() throws Exception {
        Assertions.assertTrue(invokeIsWordStartChar('_'));
    }

    @Test
    public void isWordStartCharQuote() throws Exception {
        Assertions.assertTrue(invokeIsWordStartChar('\''));
        Assertions.assertTrue(invokeIsWordStartChar('"'));
    }

    @Test
    public void isWordStartCharDollar() throws Exception {
        Assertions.assertTrue(invokeIsWordStartChar('$'));
    }

    @Test
    public void isWordStartCharBackslash() throws Exception {
        Assertions.assertTrue(invokeIsWordStartChar('\\'));
    }

    @Test
    public void isWordStartCharOtherReturnsFalse() throws Exception {
        Assertions.assertFalse(invokeIsWordStartChar(' '));
        Assertions.assertFalse(invokeIsWordStartChar('.'));
        Assertions.assertFalse(invokeIsWordStartChar('['));
    }

    // ---- isTerminator ----

    @Test
    public void isTerminatorWhitespace() throws Exception {
        Assertions.assertTrue(invokeIsTerminator(' '));
        Assertions.assertTrue(invokeIsTerminator('\t'));
        Assertions.assertTrue(invokeIsTerminator('\n'));
    }

    @Test
    public void isTerminatorPunctuation() throws Exception {
        Assertions.assertTrue(invokeIsTerminator(')'));
        Assertions.assertTrue(invokeIsTerminator('}'));
        Assertions.assertTrue(invokeIsTerminator('+'));
        Assertions.assertTrue(invokeIsTerminator('|'));
        Assertions.assertTrue(invokeIsTerminator('&'));
        Assertions.assertTrue(invokeIsTerminator(';'));
        Assertions.assertTrue(invokeIsTerminator(','));
    }

    @Test
    public void isTerminatorOtherReturnsFalse() throws Exception {
        Assertions.assertFalse(invokeIsTerminator('a'));
        Assertions.assertFalse(invokeIsTerminator('('));
        Assertions.assertFalse(invokeIsTerminator('='));
    }

    // ---- codeReplaceAt 各 state ----

    @Test
    public void codeReplaceAtBlockCommentClosed() throws Exception {
        // /* ... */ 应该完整保留
        String r = invokeCodeReplaceAt("/* @comment */");
        Assertions.assertEquals("/* @comment */", r);
    }

    @Test
    public void codeReplaceAtSquoteEscape() throws Exception {
        // 单引号内的转义字符 \\' 应该保留
        String r = invokeCodeReplaceAt("'\\\\a'");
        Assertions.assertEquals("'\\\\a'", r);
    }

    @Test
    public void codeReplaceAtDquoteEscape() throws Exception {
        // 双引号内的转义字符 \\" 应该保留
        String r = invokeCodeReplaceAt("\"\\\\a\"");
        Assertions.assertEquals("\"\\\\a\"", r);
    }

    @Test
    public void codeReplaceAtAtNoRuleMatchPreserved() throws Exception {
        // @ 后面是字母但非 word-start... 实际 a 是 word-start, 用其他字符测试
        // 例如 @# → 不匹配任何 rule → 保留 @
        String r = invokeCodeReplaceAt("@#");
        Assertions.assertEquals("@#", r);
    }

    // ---- rewriteAt deepScan (Rule 1) ----

    @Test
    public void rewriteAtDeepScan() throws Exception {
        // @..name → data("..name")
        StringBuilder out = new StringBuilder();
        int[] next = invokeRewriteAt("@..name", 0, out);
        Assertions.assertNotNull(next);
        Assertions.assertEquals(7, next[0]);  // 跳过 @..name 7 字符 (@ + . + . + n + a + m + e)
        Assertions.assertEquals("data(\"..name\")", out.toString());
    }

    @Test
    public void rewriteAtDeepScanWithSpecialChars() throws Exception {
        // @..a.b['x'] → data("..a.b['x']")
        StringBuilder out = new StringBuilder();
        int[] next = invokeRewriteAt("@..a.b['x']", 0, out);
        Assertions.assertNotNull(next);
        Assertions.assertTrue(out.toString().contains("data"));
    }

    @Test
    public void rewriteAtDeepScanNoPathCharReturnsNull() throws Exception {
        // @..<non-path> → 不进入 deepScan, 后续 rule 可能命中
        // @..<space> → next='.', next2='.' → 但 j=i+3 之后是空格 → isPathChar(' ')=false → j=i+3 → not entered
        // 然后看 rule 2: next='.', next2='.' → (next == '(' || next == '[' || (next == '.' && next2 != '.'))
        //   'next2 !='.' → false → 不进入 rule 2
        // 然后 rule 3: next='.' → isWordStartChar('.') → false
        // 然后 rule 4: next='.' → isTerminator('.') → false, '\0' check → false
        // → return null
        StringBuilder out = new StringBuilder();
        int[] next = invokeRewriteAt("@.. ", 0, out);
        Assertions.assertNull(next);
    }

    @Test
    public void rewriteAtDotNotDoubleDot() throws Exception {
        // @.field → 单点 rule 2 (next='.', next2='f' != '.')
        StringBuilder out = new StringBuilder();
        int[] next = invokeRewriteAt("@.field", 0, out);
        Assertions.assertNotNull(next);
        Assertions.assertEquals(1, next[0]);  // 跳过 @
        Assertions.assertEquals("data", out.toString());
    }

    @Test
    public void rewriteAtEndOfString() throws Exception {
        // @ 后面是 EOF (next='\0') → rule 4
        StringBuilder out = new StringBuilder();
        int[] next = invokeRewriteAt("@", 0, out);
        Assertions.assertNotNull(next);
        Assertions.assertEquals(1, next[0]);
        Assertions.assertEquals("data('$')", out.toString());
    }
}