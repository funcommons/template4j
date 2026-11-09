package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.beans.FuncCast;
import fun.commons.template4j.core.beans.FuncData;
import fun.commons.template4j.core.beans.FuncFne;
import fun.commons.template4j.core.beans.FuncLoad;
import fun.commons.template4j.core.beans.FuncRender;
import fun.commons.template4j.core.beans.FuncVars;
import groovy.lang.Binding;
import groovy.lang.GString;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.ast.stmt.DoWhileStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;
import org.kohsuke.groovy.sandbox.SandboxTransformer;

import javax.script.Bindings;
import javax.script.ScriptException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Groovy 沙箱执行环境：{@code withSandbox(true)} 后替换默认 jsr223 eval 路径。两层防线：
 * 编译期 {@link SandboxTransformer} 把方法 / 构造 / 属性 / 数组访问改写为 {@code Checker.checked*}
 * 调用点（运行期必经拦截器审查），{@link SecureASTCustomizer} 语句黑名单禁全部循环
 * （循环自增走局部变量字节码，运行期拦不住，必须在解析阶段拒绝）；运行期 {@link Whitelist}
 * 默认全拒，违规 {@link SecurityException} 在 {@link #eval} 包装为 {@link RenderException}
 * （message 含 "sandbox"）。
 *
 * <p>注意：groovy-sandbox 1.x 的拦截器注册表是 ThreadLocal（每线程独立），只在构建线程注册一次
 * 会让并行渲染线程完全绕过拦截，因此 {@link #eval} 每次 register / unregister；
 * 嵌套 eval 对同一线程重复注册，List.remove 成对抵消，安全。
 *
 * <p>编译产物可安全缓存复用：{@link SandboxTransformer} 是<b>编译期</b> AST 重写，
 * {@code Checker.checked*} 调用点已编进产物 Script 类；运行期检查全部发生在 {@link Whitelist}
 * 拦截器上，而拦截器是每次 {@code eval} 注册 / 注销的 —— 与脚本对象是否来自缓存无关，
 * 故缓存命中路径的违规调用依旧被拦截（{@link ScriptEnvironment#sandboxCacheSize()} 所见条目
 * 只省编译成本，不弱化白名单）。
 */
final class GroovySandbox {

    private static final String SANDBOX_TAG = "sandbox";

    private final GroovyShell shell = createShell();
    private final GroovyInterceptor interceptor = new Whitelist();

    /**
     * 仅编译不执行。{@link SandboxTransformer} 重写与 {@link SecureASTCustomizer} 循环禁令都在本阶段生效;
     * 产物 {@link Script} 交给 {@link #eval(Script, Bindings)} 克隆执行（违规脚本如 {@code new File(...)}
     * 编译可通过，拦截发生在运行期）。script 已含 imports 前缀（与默认路径一致的 {@code imports.toCode() + script}）。
     */
    Script compile(String script) throws ScriptException {
        try {
            // shell.parse 每次重新编译（内部 caching=false）; 缓存方以 Script 实例为缓存项反复克隆产物类
            return shell.parse(script, "template4jSandboxScript.groovy");
        } catch (CompilationFailedException e) {
            throw new RenderException("/", "{script}", SANDBOX_TAG + ": script rejected: " + e.getMessage(), e);
        }
    }

    /**
     * 执行已编译的 {@link Script}。Script 类线程安全复用、实例不共享：每次经
     * {@link InvokerHelper#createScript} 新建实例并绑定本调用自己的 Binding（该入口即
     * {@code GroovyShell.parse} 内部用的同一实例化方式; Groovy 4 的 {@code Script} 没有
     * {@code newInstance(Binding)} API），因此缓存命中也不会串 bindings。
     */
    Object eval(Script compiled, Bindings bindings) throws ScriptException {
        // fne(...) / data('$') 这类 binding 变量的函数式调用以 onMethodCall(script, "名字") 到达,
        // 拦截器需按名放行值为 Func* 实例的 binding; 保存旧值以支持嵌套 eval 恢复
        Set<String> prevCallable = Whitelist.CALLABLE_BINDINGS.get();
        Whitelist.CALLABLE_BINDINGS.set(collectCallableBindings(bindings));
        interceptor.register();
        try {
            Script instance = InvokerHelper.createScript(compiled.getClass(), new Binding(bindings));
            return instance.run();
        } catch (SecurityException se) {
            String message = StringUtils.containsIgnoreCase(se.getMessage(), SANDBOX_TAG)
                    ? se.getMessage() : SANDBOX_TAG + ": " + se.getMessage();
            throw new RenderException("/", "{script}", message, se);
        } catch (Exception e) {
            // ScriptException 无 (String, Throwable) 构造器, 补挂 cause 保留诊断链
            ScriptException wrapped = new ScriptException(SANDBOX_TAG + " eval failed: " + e.getMessage());
            wrapped.initCause(e);
            throw wrapped;
        } finally {
            interceptor.unregister();
            Whitelist.CALLABLE_BINDINGS.set(prevCallable);
        }
    }

    /** 编译并执行脚本（无缓存直跑路径）。script 已含 imports 前缀（与默认路径一致的 {@code imports.toCode() + script}）。 */
    Object eval(String script, Bindings bindings) throws ScriptException {
        return eval(compile(script), bindings);
    }

    /** bindings 中值为 Func* 实例的名字（data / vars / render / load / cast / fne）。 */
    private static Set<String> collectCallableBindings(Bindings bindings) {
        Set<String> names = new HashSet<>();
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            if (Whitelist.isFuncBean(entry.getValue())) {
                names.add(entry.getKey());
            }
        }
        return names;
    }

    private static GroovyShell createShell() {
        SecureASTCustomizer secureAst = new SecureASTCustomizer();
        secureAst.setDisallowedStatements(List.of(WhileStatement.class, DoWhileStatement.class, ForStatement.class));
        CompilerConfiguration config = new CompilerConfiguration();
        config.addCompilationCustomizers(secureAst, new SandboxTransformer());
        return new GroovyShell(config);
    }

    /** 默认全拒的调用拦截器：白名单外的所有调用点抛 SecurityException。 */
    private static final class Whitelist extends GroovyInterceptor {

        /** a+b / a*b / a&lt;b 等二元运算经 SandboxTransformer 改写为值类型上的运算符方法分派。 */
        private static final Set<String> OPERATOR_METHODS = Set.of(
                "plus", "minus", "multiply", "div", "mod", "power",
                "and", "or", "xor", "negate", "next", "previous", "compareTo");

        /** 当前线程 eval 中可按函数式调用的 binding 名（值为 Func* 实例），由 eval 前后设置 / 恢复。 */
        private static final ThreadLocal<Set<String>> CALLABLE_BINDINGS =
                ThreadLocal.withInitial(Collections::emptySet);

        @Override
        public Object onMethodCall(Invoker invoker, Object receiver, String method, Object... args) throws Throwable {
            // fne(null,'a') 等函数式调用落在 script 对象上 (this.名字), Groovy 经 methodMissing
            // 再路由到 Func* bean 的 call(...) — 值为 Func* 的 binding 名放行
            if (isAllowed(receiver) || isOperatorDispatch(receiver, method)
                    || (receiver instanceof Script && CALLABLE_BINDINGS.get().contains(method))) {
                return invoker.call(receiver, method, args);
            }
            throw blocked("method call " + typeName(receiver) + "." + method);
        }

        @Override
        public Object onStaticCall(Invoker invoker, Class owner, String method, Object... args) throws Throwable {
            if (owner == Math.class || owner == StringUtils.class) {
                return invoker.call(owner, method, args);
            }
            throw blocked("static call " + owner.getName() + "." + method);
        }

        @Override
        public Object onNewInstance(Invoker invoker, Class clazz, Object... args) throws Throwable {
            if (clazz == JSONObject.class || clazz == JSONArray.class) {
                // Checker 的构造 Invoker 用 receiver 携带目标类型, method 位恒 null
                return invoker.call(clazz, null, args);
            }
            throw blocked("constructor call " + clazz.getName());
        }

        @Override
        public Object onGetProperty(Invoker invoker, Object receiver, String property) throws Throwable {
            // Script receiver: binding 变量读取（data / vars / fne 等）以 this.data 属性访问形式出现
            if (receiver instanceof Script || isAllowed(receiver)) {
                return invoker.call(receiver, property);
            }
            throw blocked("property access " + typeName(receiver) + "." + property);
        }

        @Override public Object onSetProperty(Invoker invoker, Object receiver, String property, Object value) throws Throwable { throw blocked("property assignment " + typeName(receiver) + "." + property); }
        @Override public Object onGetAttribute(Invoker invoker, Object receiver, String attribute) throws Throwable { throw blocked("attribute access " + typeName(receiver) + "." + attribute); }
        @Override public Object onSetAttribute(Invoker invoker, Object receiver, String attribute, Object value) throws Throwable { throw blocked("attribute assignment " + typeName(receiver) + "." + attribute); }
        @Override public Object onGetArray(Invoker invoker, Object receiver, Object index) throws Throwable { throw blocked("array access " + typeName(receiver)); }
        @Override public Object onSetArray(Invoker invoker, Object receiver, Object index, Object value) throws Throwable { throw blocked("array assignment " + typeName(receiver)); }
        @Override public Object onSuperCall(Invoker invoker, Class owner, Object receiver, String method, Object... args) throws Throwable { throw blocked("super call " + owner.getName() + "." + method); }
        @Override public void onSuperConstructor(Invoker invoker, Class owner, Object... args) throws Throwable { throw blocked("super constructor call " + owner.getName()); }

        /** Func* beans + fastjson2 容器 + 普通 Map / List 数据容器（与 fastjson2 同信任级别）。 */
        private static boolean isAllowed(Object receiver) {
            return isFuncBean(receiver)
                    || receiver instanceof JSONObject || receiver instanceof JSONArray
                    || receiver instanceof Map || receiver instanceof List;
        }

        static boolean isFuncBean(Object receiver) {
            return receiver instanceof FuncData || receiver instanceof FuncVars
                    || receiver instanceof FuncRender || receiver instanceof FuncLoad
                    || receiver instanceof FuncCast || receiver instanceof FuncFne;
        }

        /** 值类型（Number / Boolean / Character / String / GString / null）上的运算符分派放行。 */
        private static boolean isOperatorDispatch(Object receiver, String method) {
            return (receiver instanceof Number || receiver instanceof Boolean
                    || receiver instanceof Character || receiver instanceof String
                    || receiver instanceof GString || receiver == null)
                    && OPERATOR_METHODS.contains(method);
        }

        private static String typeName(Object receiver) {
            return receiver == null ? "null" : receiver.getClass().getName();
        }

        private static SecurityException blocked(String what) {
            return new SecurityException("sandbox: blocked " + what);
        }
    }
}
