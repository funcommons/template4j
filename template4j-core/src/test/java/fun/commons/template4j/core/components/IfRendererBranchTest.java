package fun.commons.template4j.core.components;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

import fun.commons.template4j.core.RenderEngine;

/**
 * Covers the 4 missed branches in IfRenderer.render():
 * 1. is="blank" with null select (short-circuit via select==null)
 * 2. is="blank" with non-blank select (isBlank returns false)
 * 3. is="!blank" with null select (short-circuit via select==null)
 * 4. is="empty" with null select (short-circuit via select==null)
 */
public class IfRendererBranchTest {

    // Branch 1: is="blank" with null select -> test=true (select==null short-circuits)
    @Test
    public void testIsBlankWithNullSelect() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{@nonexistent}\",\"is\":\"blank\",\"then\":\"yes\",\"else\":\"no\"}",
                Map.of());
        Assertions.assertEquals(r, "yes");
    }

    // Branch 2: is="blank" with non-blank select -> test=false (isBlank returns false)
    @Test
    public void testIsBlankWithNonBlankSelect() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"hello\",\"is\":\"blank\",\"then\":\"yes\",\"else\":\"no\"}",
                Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Branch 3: is="!blank" with null select -> test=false (select==null short-circuits)
    @Test
    public void testIsNotBlankWithNullSelect() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{@nonexistent}\",\"is\":\"!blank\",\"then\":\"yes\",\"else\":\"no\"}",
                Map.of());
        Assertions.assertEquals(r, "no");
    }

    // Branch 4: is="empty" with null select -> test=true (select==null short-circuits)
    @Test
    public void testIsEmptyWithNullSelect() throws Exception {
        Object r = RenderEngine.build().renderJson(
                "{\"@class\":\"if\",\"select\":\"{@nonexistent}\",\"is\":\"empty\",\"then\":\"yes\",\"else\":\"no\"}",
                Map.of());
        Assertions.assertEquals(r, "yes");
    }
}
