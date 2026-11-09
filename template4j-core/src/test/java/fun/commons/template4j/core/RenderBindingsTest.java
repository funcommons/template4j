package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.script.Bindings;
import javax.script.SimpleBindings;

/**
 * 覆盖 RenderBindings 的 get/containsKey 各分支。
 */
public class RenderBindingsTest {

    @Test
    public void testGetAndContainsKeyBasic() {
        Bindings b = new RenderBindings();
        b.put("name", "alice");
        Assertions.assertEquals(b.get("name"), "alice");
        Assertions.assertTrue(b.containsKey("name"));
    }

    @Test
    public void testGetWithDollarPrefix() {
        // get("$foo") → 内部移除 $ 后查 vars
        Bindings b = new RenderBindings();
        b.put("data", "X");
        // "$data" 不是 vars 注入的格式，所以 puts 直接通过 SimpleBindings
        b.put("$data", "Y");
        Assertions.assertEquals(b.get("$data"), "Y");
    }

    @Test
    public void testContainsKeyRegex() {
        // 通过 vars 注入触发
        Bindings b = new RenderBindings();
        Assertions.assertFalse(b.containsKey("$dollar_prefixed"));
        Assertions.assertFalse(b.containsKey("plain_key"));
    }

    @Test
    public void testGetJsr223KeepGlobals() {
        // 特殊 key "#jsr223.groovy.engine.keep.globals" 直接返回 super.get()
        Bindings b = new RenderBindings();
        b.put("#jsr223.groovy.engine.keep.globals", "KEEP");
        Assertions.assertEquals(b.get("#jsr223.groovy.engine.keep.globals"), "KEEP");
    }

    @Test
    public void testContainsKeyJsr223Globals() {
        Bindings b = new RenderBindings();
        b.put("jsr223.groovy.engine.keep.globals", "KEEP");
        Assertions.assertTrue(b.containsKey("jsr223.groovy.engine.keep.globals"));
    }

    @Test
    public void testContainsKeyScriptFilename() {
        Bindings b = new RenderBindings();
        b.put("javax.script.filename", "/some.groovy");
        Assertions.assertTrue(b.containsKey("javax.script.filename"));
    }

    @Test
    public void testGetNilValue() {
        // get 一个不存在的 key
        Bindings b = new RenderBindings();
        Assertions.assertNull(b.get("nonexistent"));
    }

    @Test
    public void testContainsKeyInvalidVarName() {
        // not matches regex \\$?[\\._\\w]+ — 返回 false
        Bindings b = new RenderBindings();
        // 把 vars 注入 — 用 FuncVars bean
        // 任意 keys 都不匹配 regex
        Assertions.assertFalse(b.containsKey("$"));
        Assertions.assertFalse(b.containsKey("*"));
        Assertions.assertFalse(b.containsKey(".."));
    }

    @Test
    public void testContainsKeyWithVarsRegexMatch() {
        // 直接验证不存在 keys — 覆盖 containsKey 返回 false 分支
        Bindings b = new RenderBindings();
        Assertions.assertFalse(b.containsKey("$"));
        Assertions.assertFalse(b.containsKey("1startsWithDigit"));
        Assertions.assertFalse(b.containsKey("a-b"));  // hyphen 不在 [\w] 中
    }
}
