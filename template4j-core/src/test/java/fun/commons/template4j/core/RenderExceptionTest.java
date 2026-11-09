package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 覆盖 RenderException.removeUnused 各分支 + formatMessage。
 */
public class RenderExceptionTest {

    @Test
    public void testRemoveUnusedNoMatch() {
        // 普通 exception message — 不含 "javax.script.ScriptException: " 或 "java.lang.Exception: "
        // 触发 if (result.equals(message)) yes 分支
        RenderException e = new RenderException("/", "test",
                "plain error message",
                new IllegalArgumentException("some error"));
        Assertions.assertNotNull(e.getMessage());
    }

    @Test
    public void testRemoveUnusedWithJavaLangPrefix() {
        // cause 的 message 含 "java.lang.Exception: " — 触发 removeStart 分支
        RenderException e = new RenderException("/", "test",
                "script error",
                new Exception("java.lang.Exception: nested cause"));
        String msg = e.getMessage();
        Assertions.assertFalse(msg.contains("java.lang.Exception:"));
    }

    @Test
    public void testRenderExceptionDirectConstructor() {
        // 单参构造器 — 直接看 formatMessage
        RenderException e = new RenderException("/path", "name", "message");
        String msg = e.getMessage();
        Assertions.assertTrue(msg.contains("/path"));
        Assertions.assertTrue(msg.contains("message"));
    }

    @Test
    public void testRenderExceptionFormatMessageBlankName() {
        // name 空白时用 ""
        RenderException e = new RenderException("/p", "  ", "msg");
        Assertions.assertTrue(e.getMessage().contains("msg"));
    }

    @Test
    public void testRenderExceptionGetLocalizedMessage() {
        RenderException e = new RenderException("/p", "n", "m");
        Assertions.assertEquals(e.getMessage(), e.getLocalizedMessage());
    }
}
