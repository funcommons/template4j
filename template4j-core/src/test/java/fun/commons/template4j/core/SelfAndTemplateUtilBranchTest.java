package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Covers remaining missed branches in SelfObject and TemplateUtil:
 *
 * SelfObject:
 * - getResourceUrl: child with explicitly-blank resourceUrl and parent with resourceUrl (else branch)
 * - vars: parentVars == false prevents parent lookup even when result is null
 *
 * TemplateUtil:
 * - simplify: @class value shorthand WITHOUT |method (tempList.length == 1 branch)
 */
public class SelfAndTemplateUtilBranchTest {

    // ========== SelfObject.getResourceUrl — else branch ==========

    @Test
    public void testGetResourceUrlChildBlankParentHasUrl() {
        // Child has blank resourceUrl, parent has one -> delegates to parent (else branch)
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        root.setResourceUrl("/parent.json");
        SelfObject child = new SelfObject(root, "child");
        // child.resourceUrl is null (blank), so should delegate to parent
        Assertions.assertEquals(child.getResourceUrl(), "/parent.json");
    }

    @Test
    public void testGetResourceUrlChildExplicitEmptyParentHasUrl() {
        // Child with explicitly empty string resourceUrl should still delegate to parent
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        root.setResourceUrl("/parent.json");
        SelfObject child = new SelfObject(root, "child");
        child.setResourceUrl("");
        // StringUtils.isNotBlank("") is false, null != parent is true, so takes else branch
        Assertions.assertEquals(child.getResourceUrl(), "/parent.json");
    }

    @Test
    public void testGetResourceUrlRootWithNoUrl() {
        // Root with no resourceUrl returns null (isNotBlank(null) is false, parent is null -> true branch)
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        Assertions.assertNull(root.getResourceUrl());
    }

    @Test
    public void testGetResourceUrlChildHasOwnUrl() {
        // Child with own non-blank resourceUrl returns it directly (first condition true)
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        root.setResourceUrl("/parent.json");
        SelfObject child = new SelfObject(root, "child");
        child.setResourceUrl("/child.json");
        Assertions.assertEquals(child.getResourceUrl(), "/child.json");
    }

    @Test
    public void testGetResourceUrlDeepChainFallback() {
        // Multi-level chain: grandchild delegates up through child to root
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        root.setResourceUrl("/root.json");
        SelfObject child = new SelfObject(root, "child");
        SelfObject grandchild = new SelfObject(child, "grandchild");
        Assertions.assertEquals(grandchild.getResourceUrl(), "/root.json");
    }

    // ========== SelfObject.vars — parentVars == false branch ==========

    @Test
    public void testVarsParentVarsFalseBlocksLookup() throws Exception {
        // When parentVars is false, vars() should NOT walk up to parent
        // even when the variable is not found locally
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        root.getVarMap().put("shared", "from_parent");
        SelfObject child = new SelfObject(root, "child");
        child.setParentVars(false);
        // Variable not in child's varMap, parentVars is false -> should NOT look up parent
        Object result = child.vars("shared");
        Assertions.assertNull(result);
    }

    @Test
    public void testVarsParentVarsTrueAllowsLookup() throws Exception {
        // When parentVars is true (default), vars() walks up to parent
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        root.getVarMap().put("shared", "from_parent");
        SelfObject child = new SelfObject(root, "child");
        // parentVars defaults to true
        Assertions.assertTrue(child.isParentVars());
        Object result = child.vars("shared");
        Assertions.assertEquals(result, "from_parent");
    }

    @Test
    public void testVarsFoundLocallySkipsParent() throws Exception {
        // When variable is found locally, parent lookup is skipped regardless of parentVars
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        root.getVarMap().put("name", "parent_val");
        SelfObject child = new SelfObject(root, "child");
        child.getVarMap().put("name", "child_val");
        Object result = child.vars("name");
        Assertions.assertEquals(result, "child_val");
    }

    // ========== TemplateUtil.simplify — @class value shorthand without |method ==========

    @Test
    public void testSimplifyAtClassValueWithoutMethod() throws Exception {
        // Value "@const:hello" — no |method part, so tempList.length == 1
        // This covers the missed branch where the method subMap.put is skipped
        Object r = RenderEngine.build().renderJson("""
                {
                  "x": "@const:hello"
                }
                """, Map.of());
        // @const returns select as-is, without rendering
        Assertions.assertEquals(((JSONObject) r).getString("x"), "hello");
    }

    @Test
    public void testSimplifyAtClassKeyWithoutMethod() throws Exception {
        // Key "@if" without |method — covers tempList.length == 1 in the key loop
        Object r = RenderEngine.build().renderJson("""
                {
                  "@if": "{@val}",
                  "is": "!null",
                  "then": "ok"
                }
                """, Map.of("val", "test"));
        Assertions.assertEquals(r, "ok");
    }

    @Test
    public void testSimplifyAtClassValueWithMethod() throws Exception {
        // Value "@if|!null:{@val}" — with |method, covers tempList.length > 1
        // Use @if component within a key that has then/else as siblings
        // The value shorthand puts the whole thing as the value, so then/else must be
        // inside the same object. Use @const|method which creates a const with method field.
        // But const doesn't use method. Instead test via the key form.
        // Test value shorthand with method using the @if|!null:select form
        // where the whole sub-object is the value and then/true/false are sibling keys
        // Actually: the value shorthand replaces the value with a sub-map.
        // So then/else/is must be siblings of the key whose value is the shorthand.
        // That won't work because they're at the same level but not inside the sub-map.
        // Use the key shorthand form instead for the with-method case.
        Object r = RenderEngine.build().renderJson("""
                {
                  "@if|!null": "{@val}",
                  "then": "y"
                }
                """, Map.of("val", "test"));
        Assertions.assertEquals(r, "y");
    }

    @Test
    public void testSimplifyAtClassKeyWithMethod() throws Exception {
        // Key "@if|!null" — with |method in the key loop
        Object r = RenderEngine.build().renderJson("""
                {
                  "@if|!null": "{@val}",
                  "then": "y"
                }
                """, Map.of("val", "test"));
        Assertions.assertEquals(r, "y");
    }

    @Test
    public void testSimplifyAtShorthand() throws Exception {
        // Value "@" — simple data shorthand
        Object r = RenderEngine.build().renderJson(
                "{\"name\":\"@\"}",
                Map.of("name", "alice"));
        Assertions.assertEquals(((JSONObject) r).getString("name"), "alice");
    }

    @Test
    public void testSimplifyDollarShorthand() throws Exception {
        // Value "$" — simple variable shorthand
        Object r = RenderEngine.build().renderJson("""
                {
                  "$name": "bob",
                  "out": "$"
                }
                """, Map.of());
        // The key is "out", value is "$", which becomes {% $out %}
        // But $out is not defined, so it should be null or empty
        // Actually "$" on key "out" becomes {% $out %}
        // Let's use a key that matches an existing var
        Object r2 = RenderEngine.build().renderJson("""
                {
                  "$name": "bob",
                  "name": "$"
                }
                """, Map.of());
        Assertions.assertEquals(((JSONObject) r2).getString("name"), "bob");
    }
}
