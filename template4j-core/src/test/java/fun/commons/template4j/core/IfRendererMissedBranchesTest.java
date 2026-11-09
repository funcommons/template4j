package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers IfRenderer missed branches: all 'is' method variants.
 */
public class IfRendererMissedBranchesTest {

    @Test
    public void testIfIsTrue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{= true }",
                  "is": "true",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testIfIsNotTrue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{= true }",
                  "is": "!true",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "no");
    }

    @Test
    public void testIfIsFalse() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{= false }",
                  "is": "false",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testIfIsNotFalse() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{= true }",
                  "is": "!false",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testIfIsNull() throws Exception {
        // select must be rendered; use {@nonexistent} which returns null
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{@nonexistent_field}",
                  "is": "null",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testIfIsNotNull() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{@name}",
                  "is": "!null",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of("name", "test"));
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testIfIsBlank() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "",
                  "is": "blank",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testIfIsNotBlank() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "text",
                  "is": "!blank",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testIfIsEmpty() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": [],
                  "is": "empty",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testIfIsNotEmpty() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": [1],
                  "is": "!empty",
                  "then": "yes",
                  "else": "no"
                }
                """, Map.of());
        Assertions.assertEquals(r, "yes");
    }

    @Test
    public void testIfTrueFalseBranches() throws Exception {
        // Using "true"/"false" keys instead of "then"/"else"
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{= true }",
                  "true": "positive",
                  "false": "negative"
                }
                """, Map.of());
        Assertions.assertEquals(r, "positive");
    }

    @Test
    public void testIfFalseTrueKeys() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {
                  "@class": "if",
                  "select": "{= false }",
                  "true": "positive",
                  "false": "negative"
                }
                """, Map.of());
        Assertions.assertEquals(r, "negative");
    }
}
