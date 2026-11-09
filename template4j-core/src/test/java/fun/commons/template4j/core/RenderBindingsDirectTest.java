package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers remaining RenderBindings method entry lines (10, 37).
 * These are the first statements of get() and containsKey().
 * JaCoCo marks them as missed because they're the method entry point,
 * but they ARE covered when Groovy calls these methods during script execution.
 * Let's try direct unit testing to ensure coverage.
 */
public class RenderBindingsDirectTest {

    @Test
    public void testGetAndContainsKeyDirectly() {
        RenderBindings b = new RenderBindings();

        // Test get() with existing key
        b.put("test_key", "test_value");
        Assertions.assertEquals(b.get("test_key"), "test_value");

        // Test get() with non-existing key
        Assertions.assertNull(b.get("nonexistent"));

        // Test containsKey() with existing key
        Assertions.assertTrue(b.containsKey("test_key"));

        // Test containsKey() with non-existing key
        Assertions.assertFalse(b.containsKey("nonexistent"));

        // Test special keys
        b.put("#jsr223.groovy.engine.keep.globals", "keep");
        Assertions.assertEquals(b.get("#jsr223.groovy.engine.keep.globals"), "keep");
        Assertions.assertTrue(b.containsKey("#jsr223.groovy.engine.keep.globals"));

        // Test with vars bean
        fun.commons.template4j.core.beans.FuncVars vars =
            new fun.commons.template4j.core.beans.FuncVars(
                new SelfObject(new JsonRenderer(), Map.of()));
        b.put("vars", vars);
        // Now get() with $ prefix should delegate to vars
        // And containsKey() with valid var name should return true
        Assertions.assertTrue(b.containsKey("$myvar"));
        Assertions.assertTrue(b.containsKey("myvar"));
    }

    @Test
    public void testGetWithVarsBeanAndDollarPrefix() {
        RenderBindings b = new RenderBindings();
        fun.commons.template4j.core.beans.FuncVars vars =
            new fun.commons.template4j.core.beans.FuncVars(
                new SelfObject(new JsonRenderer(), Map.of()));
        b.put("vars", vars);

        // $foo should delegate to vars.get("foo")
        // Since no variable is set, returns null
        Object result = b.get("$foo");
        // vars.get("foo") calls self.vars("foo") which walks up parent chain
        // Since no variable, returns null
        Assertions.assertNull(result);
    }

    @Test
    public void testContainsKeyWithVarsBean() {
        RenderBindings b = new RenderBindings();
        fun.commons.template4j.core.beans.FuncVars vars =
            new fun.commons.template4j.core.beans.FuncVars(
                new SelfObject(new JsonRenderer(), Map.of()));
        b.put("vars", vars);

        // Valid var name pattern → true
        Assertions.assertTrue(b.containsKey("myVar"));
        Assertions.assertTrue(b.containsKey("$myVar"));
        Assertions.assertTrue(b.containsKey("myFunc()"));
        Assertions.assertTrue(b.containsKey("_name"));

        // Invalid var name → false (contains hyphen)
        Assertions.assertFalse(b.containsKey("my-var"));
    }
}
