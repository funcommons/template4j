package fun.commons.template4j.core;

import fun.commons.template4j.core.beans.FuncVars;
import lombok.SneakyThrows;

import javax.script.SimpleBindings;

public class RenderBindings extends SimpleBindings {

    private static final String KEEP_GLOBALS_KEY = "#jsr223.groovy.engine.keep.globals";
    private static final String FILENAME_KEY = "javax.script.filename";

    @SneakyThrows
    @Override
    public Object get(Object key)  {

        Object result = super.get(key);

        if (KEEP_GLOBALS_KEY.equals(key)) {
            return result;
        }

        if (result == null && super.containsKey("vars")) {

            String k = String.valueOf(key);

            if (k.startsWith("$")) {
                k = k.substring(1);
            }

            FuncVars vars = (FuncVars) super.get("vars");
            result = vars.get(k);

        }

        return result;
    }


    @Override
    public boolean containsKey(Object key) {

        boolean result = super.containsKey(key);

        if (KEEP_GLOBALS_KEY.equals(key)) {
            return result;
        }
        if (FILENAME_KEY.equals(key)) {
            return result;
        }

        if (!result && super.containsKey("vars")) {
            // Permissive match: 允许 Groovy 通过 get() 路径查找变量。
            // get() 会在 varMap 中按 $name / $name() 形式回溯父级 SelfObject，
            // 不存在的变量最终返回 null（业务模板依赖此宽松语义）。
            result = String.valueOf(key).matches("\\$?[\\._\\w]+(\\(\\))?");
        }

        return result;
    }
}
