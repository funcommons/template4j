package fun.commons.template4j.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fun.commons.template4j.core.beans.FuncCast;
import fun.commons.template4j.core.beans.FuncData;
import fun.commons.template4j.core.beans.FuncFne;
import fun.commons.template4j.core.beans.FuncLoad;
import fun.commons.template4j.core.beans.FuncRender;
import fun.commons.template4j.core.beans.FuncVars;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

/**
 * 脚本环境：聚合 Groovy 脚本执行所需的 imports / beans / 引擎实例。
 * 从 JsonRenderer 中拆分以便集中处理脚本执行副作用。
 *
 * <p>编译缓存：以 {@code imports版本号 + '\0' + script} 为 key 缓存 {@link CompiledScript}。
 * addImport 递增版本号使旧条目按 key 自然失效（不必主动 clear）。缓存的是编译产物而非求值结果，
 * bindings 每次 eval 重建后传入，故跨实例 / 跨 SelfObject 复用同一编译脚本无状态问题。
 * 缓存配置在实例构造时读取（非 static），同 JVM 内改属性后新建实例即生效。
 */
final class ScriptEnvironment {

    private static final ScriptEngine GROOVY_ENGINE = new ScriptEngineManager().getEngineByName("groovy");

    private static final String PROP_MAX_ENTRIES = "template4j.scriptCache.maxEntries";
    private static final String PROP_ENABLED = "template4j.scriptCache.enabled";
    private static final int DEFAULT_MAX_ENTRIES = 2000;

    private final GroovyImports imports = new GroovyImports();
    private final Map<String, Object> beans = new HashMap<>();

    private final boolean scriptCacheEnabled;
    private final Compilable compilable;
    private final Cache<String, CompiledScript> scriptCache;
    /** 沙箱环境; null = 关闭 (默认), 走 jsr223 + 编译缓存路径。开启后走 {@link GroovySandbox} 独立 eval。 */
    private volatile GroovySandbox sandbox;
    /** imports 内容版本号，addImport 递增，作为缓存 key 前缀。 */
    private long importsVersion;

    ScriptEnvironment() {
        this.scriptCacheEnabled = Boolean.parseBoolean(System.getProperty(PROP_ENABLED, "true"));
        this.compilable = GROOVY_ENGINE instanceof Compilable engine ? engine : null;
        this.scriptCache = Caffeine.newBuilder().maximumSize(maxEntries()).build();
    }

    void registerBean(String name, Object bean) {
        beans.put(name, bean);
    }

    boolean hasBean(String name) {
        return beans.containsKey(name);
    }

    void addImport(String className) {
        importsVersion++;
        imports.put(className);
    }

    /** 开启 / 关闭 Groovy 沙箱。开启后脚本编译 / 执行经 {@link GroovySandbox} 审查, 关闭恢复默认路径。 */
    void setSandboxEnabled(boolean enabled) {
        sandbox = enabled ? new GroovySandbox() : null;
    }

    Object eval(String script, SelfObject self) throws ScriptException {
        GroovySandbox sandbox = this.sandbox;
        if (sandbox != null) {
            return sandbox.eval(imports.toCode() + script, buildBindings(self));
        }
        Bindings bindings = buildBindings(self);
        if (!scriptCacheEnabled || compilable == null) {
            return GROOVY_ENGINE.eval(imports.toCode() + script, bindings);
        }
        String key = importsVersion + "\0" + script;
        CompiledScript compiled = scriptCache.getIfPresent(key);
        if (compiled == null) {
            compiled = compilable.compile(imports.toCode() + script);
            scriptCache.put(key, compiled);
        }
        return compiled.eval(bindings);
    }

    int scriptCacheSize() {
        scriptCache.cleanUp();
        return (int) scriptCache.estimatedSize();
    }

    void clearScriptCache() {
        scriptCache.invalidateAll();
    }

    private static int maxEntries() {
        String raw = System.getProperty(PROP_MAX_ENTRIES);
        if (raw == null) {
            return DEFAULT_MAX_ENTRIES;
        }
        try {
            int max = Integer.parseInt(raw.trim());
            return max > 0 ? max : DEFAULT_MAX_ENTRIES;
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_ENTRIES;
        }
    }

    private Bindings buildBindings(SelfObject self) {
        Bindings bindings = new RenderBindings();
        bindings.putAll(beans);
        bindings.put("data", new FuncData(self.getData()));
        bindings.put("vars", new FuncVars(self));
        bindings.put("render", new FuncRender(self));
        bindings.put("load", new FuncLoad(self));
        bindings.put("cast", new FuncCast());
        bindings.put("fne", new FuncFne());
        return bindings;
    }
}
