package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Map;

/**
 * TDD 契约测试: round2 §S2 — 沙箱模式默认导入收敛 ({@link GroovyImports#toSandboxCode()}).
 *
 * <p>契约 (由业务实现方保证):
 * <ul>
 *   <li>{@code GroovyImports} 新增包级方法 {@code toSandboxCode()}: 与 {@code toCode()} 唯一区别 —
 *       <b>默认集</b>中的 {@code java.io.*} 与 {@code java.net.*} 两条不生成; <b>用户显式</b>条目
 *       (经 {@code ScriptEnvironment.addImport} 委托 {@link GroovyImports#put(String)}) 一律保留,
 *       含 {@code java.io.File} 这类与被删默认星号同包的显式导入。</li>
 *   <li>{@code ScriptEnvironment} 沙箱分支编译改用 {@code toSandboxCode()}; 非沙箱分支
 *       {@code toCode()} 不变 (显式 {@code withImport} 在两种模式下照常生成)。</li>
 *   <li>沙箱缓存 key 机制不变 ({@code importsVersion + script}); addImport 后同文本重编译、
 *       新导入生效已由 {@link SandboxCacheTest#addImport_bumpsImportsVersion_recompilesSameTextAndExposesNewClass}
 *       完全覆盖 (§S2 计划测试点 6 与其同构, 按计划省略, 不在此重复)。</li>
 * </ul>
 *
 * <p>机制说明 (测试点 1 计划里的"编译期类不可见"): Groovy 自身的默认导入
 * ({@code org.codehaus.groovy.control.ResolveVisitor.DEFAULT_IMPORTS} = java.lang / java.util /
 * java.io / java.net / groovy.lang / groovy.util) 与脚本前缀无关 — 移除模板引擎默认集的
 * java.io/net 后 {@code File} / {@code URL} 在沙箱脚本中仍可解析, 拦截表现为运行期白名单
 * (message 形如 {@code sandbox: blocked constructor call java.io.File}); 若实现另做编译期拒绝则为
 * {@code sandbox: script rejected: ...}。两种机制的观测契约一致 — {@link RenderException} 且
 * message 含 "sandbox" — 本类只钉观测契约, 不钉具体拦截层。
 *
 * <p>分工: 非沙箱良性脚本回归归 {@link SandboxRenderTest}; 缓存语义归 {@link SandboxCacheTest}。
 * 本类不改任何系统属性 (surefire 注入的 {@code template4j.rest.allowed-hosts} 等保持原样,
 * 无 save/restore 需求); 每个测试自建引擎 ({@code JsonRenderer} 每实例持有独立
 * {@code ScriptEnvironment}), 实例间脚本缓存互不污染。
 *
 * <p>注意: 本类引用的 {@code GroovyImports#toSandboxCode()} 为 §S2 待落地的生产 API,
 * 在其落地前本测试类无法编译 (契约先行的预期状态)。
 */
public class SandboxImportsTest {

    private static final Map<String, Object> DATA_N = Map.of("n", 21);

    /** 当前测试用引擎, 由各测试指定沙箱开关 / 显式导入。 */
    private RenderEngine engine;

    /** 渲染 JSON 模板并返回 "v" 字段值 (单占位符 {=} 脚本返回原始对象, 见 A4 语义)。 */
    private Object renderOne(String template, Object data) throws RenderException {
        JSONObject result = (JSONObject) engine.renderJson(template, data);
        return result.get("v");
    }

    /** 断言沙箱拒绝给定 Groovy 片段: 抛 RenderException 且 message 含 "sandbox" (大小写不敏感)。 */
    private RenderException assertSandboxRejects(String script) {
        engine = RenderEngine.build().withSandbox(true);
        RenderException ex = Assertions.assertThrows(RenderException.class,
                () -> renderOne("{\"v\":\"" + script + "\"}", DATA_N));
        assertSandboxMessage(ex);
        return ex;
    }

    private static void assertSandboxMessage(RenderException ex) {
        Assertions.assertNotNull(ex.getMessage(), "RenderException message should not be null");
        Assertions.assertTrue(ex.getMessage().toLowerCase().contains("sandbox"),
                "message should mention sandbox: " + ex.getMessage());
    }

    // ========== 1. 沙箱 + 默认导入: File 构造被拒 (默认集不再承诺 java.io) ==========

    @Test
    public void sandboxOn_defaultImportSet_fileConstructorRejected() {
        // §S2 测试点 1: 收敛后默认前缀不再携带 java.io.* / java.net.*; 拦截层见类注释机制说明,
        // 观测契约 (RenderException + "sandbox") 在运行期白名单 / 编译期拒绝两种机制下均成立
        assertSandboxRejects("{= new File('x') }");
    }

    // ========== 2. 非沙箱路径不变: toCode() 默认集原样, new File 正常求值 ==========

    @Test
    public void sandboxOff_fileConstructorStillEvaluates() {
        // §S2 测试点 2: 非沙箱分支 toCode() 不变 — java.io.* 仍在默认前缀, new File 照常求值。
        // 只断言类型 (new File 不做任何 IO, 不触碰文件系统)
        engine = RenderEngine.build();
        Object v = renderOne("{\"v\":\"{= new File('x') }\"}", DATA_N);
        Assertions.assertTrue(v instanceof File, "expected raw File object, got: " + v);
    }

    // ========== 3. 用户显式导入优先: withImport(java.io.File) 保留, 运行期白名单仍兜底 ==========

    @Test
    public void sandboxOn_explicitIoFileImport_keptYetInstantiationStillBlocked() throws Exception {
        // §S2 测试点 3: 显式 withImport 用户意图优先 — import 被生成 (字符串层直测见
        // toSandboxCode_explicitImportsAlwaysKept), 但运行期实例化仍被白名单拦截 (兜底不放松)
        engine = RenderEngine.build().withImport("java.io.File").withSandbox(true);
        RenderException ex = Assertions.assertThrows(RenderException.class,
                () -> renderOne("{\"v\":\"{= new File('x') }\"}", DATA_N));
        assertSandboxMessage(ex);
    }

    // ========== 4a. toSandboxCode 语义直测: 默认集只摘除两条, 其余原样 ==========

    @Test
    public void toSandboxCode_defaultSetDropsIoNet_keepsRest_toCodeUnchanged() {
        GroovyImports imports = new GroovyImports();
        String sandboxCode = imports.toSandboxCode();
        // 默认集的 java.io.* / java.net.* 两条不再生成 (子串断言同时覆盖星号与显式两种形态)
        Assertions.assertFalse(sandboxCode.contains("java.io"),
                "sandbox imports must not promise java.io: " + sandboxCode);
        Assertions.assertFalse(sandboxCode.contains("java.net"),
                "sandbox imports must not promise java.net: " + sandboxCode);
        // 收敛是外科手术式的: 默认集其余条目原样保留 (不得整段丢弃)
        Assertions.assertTrue(sandboxCode.contains("import java.util.*"),
                "sandbox imports must keep java.util.*: " + sandboxCode);
        Assertions.assertTrue(sandboxCode.contains("import java.math.*"),
                "sandbox imports must keep java.math.*: " + sandboxCode);
        Assertions.assertTrue(sandboxCode.contains("import com.alibaba.fastjson2.*"),
                "sandbox imports must keep fastjson2: " + sandboxCode);
        Assertions.assertTrue(sandboxCode.contains("import org.apache.commons.lang3.*"),
                "sandbox imports must keep commons-lang3: " + sandboxCode);
        // 非沙箱 toCode() 不变: 默认集两条照常生成
        String code = imports.toCode();
        Assertions.assertTrue(code.contains("import java.io.*"),
                "non-sandbox toCode must keep java.io.*: " + code);
        Assertions.assertTrue(code.contains("import java.net.*"),
                "non-sandbox toCode must keep java.net.*: " + code);
    }

    // ========== 4b. toSandboxCode 语义直测: 用户显式条目一律保留 ==========

    @Test
    public void toSandboxCode_explicitImportsAlwaysKept_includingSamePackageAsDroppedDefault() {
        // 与被删默认星号同包的显式导入必须保留 — 这要求 put 对"用户显式"与"默认集"分开记录,
        // 否则现有去重 (imports.contains("java.io.*")) 会吞掉显式条目, 沙箱下类将不可见
        GroovyImports imports = new GroovyImports();
        imports.put("java.io.File");
        String sandboxCode = imports.toSandboxCode();
        Assertions.assertTrue(sandboxCode.contains("import java.io.File"),
                "explicit java.io.File must survive convergence: " + sandboxCode);
        Assertions.assertFalse(sandboxCode.contains("java.io.*"),
                "default java.io.* must stay dropped even with explicit entry: " + sandboxCode);
        // 与默认集不冲突的显式导入同样保留 (addImport 路径的字符串层等价物)
        GroovyImports other = new GroovyImports();
        other.put("java.time.LocalTime");
        Assertions.assertTrue(other.toSandboxCode().contains("import java.time.LocalTime"),
                "explicit non-default import must be kept: " + other.toSandboxCode());
    }

    // ========== 5a. 白名单放行的静态调用不受收敛影响 ==========

    @Test
    public void sandboxOn_benignStaticCalls_unaffectedByConvergence() {
        // §S2 测试点 5: 默认集收敛不误伤 Math / StringUtils (onStaticCall 按 owner 放行, 与 import 前缀无关)
        engine = RenderEngine.build().withSandbox(true);
        Assertions.assertEquals(2, ((Number) renderOne("{\"v\":\"{= Math.max(1, 2) }\"}", DATA_N)).intValue());
        Assertions.assertEquals(Boolean.TRUE,
                renderOne("{\"v\":\"{= StringUtils.isNotBlank('x') }\"}", DATA_N));
    }

    // ========== 5b/7. 默认导入可用性回归: fastjson2 构造与 String 运算照常 ==========

    @Test
    public void sandboxOn_defaultImportRegression_fastjsonAndStringUsable() {
        // §S2 测试点 5b/7: 收敛后 fastjson2 (默认 import com.alibaba.fastjson2.*) 构造 / 方法调用
        // 与隐式 java.lang 的 String 运算符分派在沙箱下照常可用
        engine = RenderEngine.build().withSandbox(true);
        Assertions.assertEquals(Boolean.TRUE,
                renderOne("{\"v\":\"{= new JSONObject().isEmpty() }\"}", DATA_N));
        Assertions.assertEquals("v", renderOne(
                "{\"v\":\"{% def j = new JSONObject(); j.put('k', 'v'); return j.getString('k') %}\"}", DATA_N));
        Assertions.assertEquals(0, ((Number) renderOne("{\"v\":\"{= new JSONArray().size() }\"}", DATA_N)).intValue());
        Assertions.assertEquals("ab", renderOne("{\"v\":\"{= 'a' + 'b' }\"}", DATA_N));
    }
}
