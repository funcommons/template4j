package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers remaining IfRenderer branches by testing all 'is' values
 * with both true and false conditions.
 */
public class IfRendererCompleteTest {

    // Line 46: !true branch — test with false value
    @Test
    public void testIsTrueFalse() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"{= false }","is":"true","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "f");
    }

    // Line 55: !true with false value → test=true
    @Test
    public void testIsNotTrueFalse() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"{= false }","is":"!true","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "t");
    }

    // Line 58: false with false value → test=true
    @Test
    public void testIsFalseFalse() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"{= false }","is":"false","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "t");
    }

    // Line 61: !null with non-null → test=true
    @Test
    public void testIsNotNullNonNull() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"{= 'x' }","is":"!null","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "t");
    }

    // Line 64: !blank with non-blank → test=true
    @Test
    public void testIsNotBlankNonBlank() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"text","is":"!blank","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "t");
    }

    // Line 67: !empty with non-empty → test=true
    @Test
    public void testIsNotEmptyNonEmpty() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":[1],"is":"!empty","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "t");
    }

    // Line 70: empty with empty → test=true
    @Test
    public void testIsEmptyEmpty() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":[],"is":"empty","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "t");
    }

    // null with null → test=true
    @Test
    public void testIsNullNull() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"{@missing}","is":"null","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "t");
    }

    // blank with blank → test=true
    @Test
    public void testIsBlankBlank() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"","is":"blank","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "t");
    }

    // true with true → test=true
    @Test
    public void testIsTrueTrue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"{= true }","is":"true","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "t");
    }

    // !false with false → !isFalse(false)=!true=false → else
    @Test
    public void testIsNotFalseFalse() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"{= false }","is":"!false","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "f");
    }

    // !false with true → !isFalse(true)=!false=true → then
    @Test
    public void testIsNotFalseTrue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"{= true }","is":"!false","then":"t","else":"f"}
                """, Map.of());
        Assertions.assertEquals(r, "t");
    }
}
