package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers ScriptUtil missed lines and codeReplaceAt paths.
 * Tests use {= ... } and {% ... %} expressions which invoke ScriptUtil internally.
 */
public class ScriptUtilMissedTest {

    @Test
    public void testAtWithParenthesesInScript() throws Exception {
        // @(...) → data(...) inside a script expression
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{= @(['key']) }"
                }
                """, Map.of("key", "paren_val"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "paren_val");
    }

    @Test
    public void testAtWithBracketInScript() throws Exception {
        // @[...] → data[...] inside a script expression
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{= @['key'] }"
                }
                """, Map.of("key", "bracket_val"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "bracket_val");
    }

    @Test
    public void testAtWithDotInScript() throws Exception {
        // @.field → data.field inside a script expression
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{= @.key }"
                }
                """, Map.of("key", "dot_val"));
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "dot_val");
    }

    @Test
    public void testCodeAtTerminator() throws Exception {
        // @ at end of expression / followed by whitespace → data('$')
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{= @ }"
                }
                """, Map.of());
        // @ followed by whitespace → data('$') which returns the root data map
        Assertions.assertNotNull(r);
    }

    @Test
    public void testCodeAtClosingBrace() throws Exception {
        // @ followed by } — covered by terminator pattern
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{= @}"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testCodeAtSemicolon() throws Exception {
        // @ followed by ;
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{= @; 1 }"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testAtPreservedInSingleQuotedString() throws Exception {
        // @ inside '...' should not be replaced
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{= '@not_replaced' }"
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "@not_replaced");
    }

    @Test
    public void testAtPreservedInDoubleQuotedString() throws Exception {
        // @ inside "..." should not be replaced
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": '{= "@not_replaced" }'
                }
                """, Map.of());
        Assertions.assertEquals(((com.alibaba.fastjson2.JSONObject) r).getString("out"), "@not_replaced");
    }

    @Test
    public void testAtPreservedInLineComment() throws Exception {
        // @ inside // comment should not be replaced
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{% // @comment \\n return 42 %}"
                }
                """, Map.of());
        Assertions.assertEquals(42, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("out"));
    }

    @Test
    public void testAtPreservedInBlockComment() throws Exception {
        // @ inside /* */ comment should not be replaced
        Object r = RenderEngine.build().renderJson("""
                {
                  "out": "{% /* @comment */ return 99 %}"
                }
                """, Map.of());
        Assertions.assertEquals(99, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("out"));
    }
}
