package fun.commons.template4j.core;

import fun.commons.template4j.core.beans.FuncCast;
import fun.commons.template4j.core.beans.FuncData;
import fun.commons.template4j.core.beans.FuncFne;
import fun.commons.template4j.core.beans.FuncLoad;
import fun.commons.template4j.core.beans.FuncRender;
import fun.commons.template4j.core.beans.FuncVars;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

/**
 * 脚本环境：聚合 Groovy 脚本执行所需的 imports / beans / 引擎实例。
 * 从 JsonRenderer 中拆分以便集中处理脚本执行副作用。
 */
final class ScriptEnvironment {

    private static final ScriptEngine GROOVY_ENGINE = new ScriptEngineManager().getEngineByName("groovy");

    private final GroovyImports imports = new GroovyImports();
    private final Map<String, Object> beans = new HashMap<>();

    void registerBean(String name, Object bean) {
        beans.put(name, bean);
    }

    boolean hasBean(String name) {
        return beans.containsKey(name);
    }

    void addImport(String className) {
        imports.put(className);
    }

    Object eval(String script, SelfObject self) throws ScriptException {
        Bindings bindings = buildBindings(self);
        return GROOVY_ENGINE.eval(imports.toCode() + script, bindings);
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
