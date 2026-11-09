package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * TDD 契约测试: Groovy 沙箱 {@code RenderEngine#withSandbox(boolean)}。
 *
 * <p>契约 (由业务实现方保证):
 * <ul>
 *   <li>{@code withSandbox(true)} 开启沙箱后, 模板中 Groovy 片段 ({@code {= }} / {@code {% %}})
 *       内的危险调用 (File / Runtime / System.exit / Class.forName / Eval / URL) 抛
 *       {@link RenderException}, 且 {@code getMessage()} 包含 "sandbox" (大小写不敏感)。</li>
 *   <li>while 循环被禁; 良性脚本 ({@code @} 重写、{@code data}、{@code fne}、fastjson2、
 *       commons-lang3) 仍可用。</li>
 *   <li>默认 (沙箱关闭) 行为与现状一致, 此处仅断言良性脚本 (危险脚本不在关闭态执行)。</li>
 * </ul>
 */
public class SandboxRenderTest {

    private static final Map<String, Object> DATA_N = Map.of("n", 21);

    /** 当前测试用引擎, 由各测试 (或 assertSandboxRejects) 指定沙箱开关。 */
    private RenderEngine engine;

    /** 渲染 JSON 模板并返回 "v" 字段值 (engine.render 的 JSONObject 结果取字段)。 */
    private Object renderOne(String template, Object data) throws RenderException {
        JSONObject result = (JSONObject) engine.renderJson(template, data);
        return result.get("v");
    }

    /** 断言沙箱拒绝给定 Groovy 片段: 抛 RenderException 且 message 含 "sandbox" (大小写不敏感)。 */
    private RenderException assertSandboxRejects(String script) {
        engine = RenderEngine.build().withSandbox(true);
        RenderException ex = Assertions.assertThrows(RenderException.class,
                () -> renderOne("{\"v\":\"" + script + "\"}", DATA_N));
        Assertions.assertNotNull(ex.getMessage(), "RenderException message should not be null");
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("sandbox"),
                "message should mention sandbox: " + ex.getMessage());
        return ex;
    }

    // ========== 沙箱开启: 危险脚本被拦截 ==========

    @Test
    public void sandboxBlocksNewFile() {
        assertSandboxRejects("{= new File('.').exists() }");
    }

    @Test
    public void sandboxBlocksRuntime() {
        assertSandboxRejects("{= Runtime.getRuntime() }");
    }

    @Test
    public void sandboxBlocksSystemExit() {
        // 该脚本绝不能在默认 (无沙箱) 引擎上执行, 否则会退出测试 JVM
        assertSandboxRejects("{% System.exit(0) %}");
    }

    @Test
    public void sandboxBlocksClassForName() {
        assertSandboxRejects("{= Class.forName('java.io.File') }");
    }

    @Test
    public void sandboxBlocksEvalMe() {
        assertSandboxRejects("{= Eval.me('1') }");
    }

    @Test
    public void sandboxBlocksNewUrl() {
        assertSandboxRejects("{= new URL('http://x') }");
    }

    @Test
    public void sandboxBlocksWhileLoop() {
        assertSandboxRejects("{% def i=0; while(i<1000000){ i++ }; return i %}");
    }

    // ========== 沙箱开启: 良性脚本仍可用 ==========

    @Test
    public void sandboxAllowsArithmeticWithAtRewrite() {
        engine = RenderEngine.build().withSandbox(true);
        Assertions.assertEquals(42, ((Number) renderOne("{\"v\":\"{= @n * 2 }\"}", DATA_N)).intValue());
    }

    @Test
    public void sandboxAllowsScriptBlockWithData() {
        engine = RenderEngine.build().withSandbox(true);
        Assertions.assertEquals(22,
                ((Number) renderOne("{\"v\":\"{% return data.n + 1 %}\"}", DATA_N)).intValue());
    }

    @Test
    public void sandboxAllowsFne() {
        engine = RenderEngine.build().withSandbox(true);
        Assertions.assertEquals("a", renderOne("{\"v\":\"{= fne(null, 'a') }\"}", DATA_N));
    }

    @Test
    public void sandboxAllowsFastjsonAndCommonsLang() {
        engine = RenderEngine.build().withSandbox(true);
        Assertions.assertEquals(Boolean.TRUE,
                renderOne("{\"v\":\"{= StringUtils.isBlank('') }\"}", DATA_N));
        Assertions.assertEquals(Boolean.TRUE,
                renderOne("{\"v\":\"{= new JSONObject().isEmpty() }\"}", DATA_N));
    }

    // ========== 沙箱关闭 (默认): 行为与现状一致, 仅断言良性脚本 ==========

    @Test
    public void defaultEngineAllowsBenignScripts() {
        engine = RenderEngine.build();
        Assertions.assertEquals(42, ((Number) renderOne("{\"v\":\"{= @n * 2 }\"}", DATA_N)).intValue());
        Assertions.assertEquals(22,
                ((Number) renderOne("{\"v\":\"{% return data.n + 1 %}\"}", DATA_N)).intValue());
        Assertions.assertEquals("a", renderOne("{\"v\":\"{= fne(null, 'a') }\"}", DATA_N));
        Assertions.assertEquals(Boolean.TRUE,
                renderOne("{\"v\":\"{= StringUtils.isBlank('') }\"}", DATA_N));
    }

    @Test
    public void withSandboxFalseExplicitBehavesAsDefault() {
        engine = RenderEngine.build().withSandbox(false);
        Assertions.assertEquals("a", renderOne("{\"v\":\"{= fne(null, 'a') }\"}", DATA_N));
    }

    // ========== data 传参: 沙箱开/关结果一致 ==========

    @Test
    public void dataParamRendersSameWithSandboxOnAndOff() {
        String template = "{\"v\": \"{= @n * 2 }\"}";
        engine = RenderEngine.build().withSandbox(true);
        Object on = renderOne(template, DATA_N);
        engine = RenderEngine.build();
        Object off = renderOne(template, DATA_N);
        Assertions.assertEquals(42, ((Number) on).intValue());
        Assertions.assertEquals(42, ((Number) off).intValue());
    }
}
