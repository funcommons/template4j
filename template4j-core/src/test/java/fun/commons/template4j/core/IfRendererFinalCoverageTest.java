package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers IfRenderer remaining branches (lines 55, 61, 64, 67).
 * These are partial branch misses in the if-else chain for the "is" predicate.
 */
public class IfRendererFinalCoverageTest {

    // Line 55: null check — select is null, is="null" → test=true
    @Test
    public void testIfIsNullTrue() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"{@nonexistent}","is":"null","then":"yes","else":"no"}
                """, Map.of());
        Assertions.assertEquals(r, "yes");
    }

    // Line 55: null check — select is NOT null, is="null" → test=false
    @Test
    public void testIfIsNullFalse() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"{= 'something' }","is":"null","then":"yes","else":"no"}
                """, Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Line 61: blank check — select is blank, is="!blank" → test=false
    @Test
    public void testIfIsNotBlankWithBlank() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"","is":"!blank","then":"yes","else":"no"}
                """, Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Line 64: blank check — select is blank, is="blank" → test=true
    @Test
    public void testIfIsBlankWithBlank() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":"","is":"blank","then":"yes","else":"no"}
                """, Map.of());
        Assertions.assertEquals(r, "yes");
    }

    // Line 67: empty check — select is empty, is="!empty" → test=false
    @Test
    public void testIfIsNotEmptyWithEmpty() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":[],"is":"!empty","then":"yes","else":"no"}
                """, Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Line 67: empty check — select is NOT empty, is="empty" → test=false
    @Test
    public void testIfIsEmptyWithNonEmpty() throws Exception {
        Object r = RenderEngine.build().renderJson("""
                {"@class":"if","select":[1,2],"is":"empty","then":"yes","else":"no"}
                """, Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Also test all other "is" values with both outcomes for full branch coverage
    @Test
    public void testIfTrueFalseValues() throws Exception {
        // true with true → yes
        Assertions.assertEquals(RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{= true }\",\"is\":\"true\",\"then\":\"yes\",\"else\":\"no\"}", Map.of()), "yes");
        // true with false → no
        Assertions.assertEquals(RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{= false }\",\"is\":\"true\",\"then\":\"yes\",\"else\":\"no\"}", Map.of()), "no");
        // !true with true → no
        Assertions.assertEquals(RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{= true }\",\"is\":\"!true\",\"then\":\"yes\",\"else\":\"no\"}", Map.of()), "no");
        // !true with false → yes
        Assertions.assertEquals(RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{= false }\",\"is\":\"!true\",\"then\":\"yes\",\"else\":\"no\"}", Map.of()), "yes");
        // false with false → yes
        Assertions.assertEquals(RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{= false }\",\"is\":\"false\",\"then\":\"yes\",\"else\":\"no\"}", Map.of()), "yes");
        // false with true → no
        Assertions.assertEquals(RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{= true }\",\"is\":\"false\",\"then\":\"yes\",\"else\":\"no\"}", Map.of()), "no");
        // !false with true → yes
        Assertions.assertEquals(RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{= true }\",\"is\":\"!false\",\"then\":\"yes\",\"else\":\"no\"}", Map.of()), "yes");
        // !null with non-null → yes
        Assertions.assertEquals(RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{= 'x' }\",\"is\":\"!null\",\"then\":\"yes\",\"else\":\"no\"}", Map.of()), "yes");
        // !null with null → no
        Assertions.assertEquals(RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{@nonexistent}\",\"is\":\"!null\",\"then\":\"yes\",\"else\":\"no\"}", Map.of()), "no");
        // !blank with non-blank → yes
        Assertions.assertEquals(RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"text\",\"is\":\"!blank\",\"then\":\"yes\",\"else\":\"no\"}", Map.of()), "yes");
        // !empty with non-empty → yes
        Assertions.assertEquals(RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":[1],\"is\":\"!empty\",\"then\":\"yes\",\"else\":\"no\"}", Map.of()), "yes");
    }
}
