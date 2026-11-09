package fun.commons.template4j.core.components;

import fun.commons.template4j.core.RenderComponent;

import java.util.*;

public class InvokeRenderer implements RenderComponent {
    @Override
    public String name() {
        return "invoke";
    }

    /**
     * <pre>
     * 形式1: 完整调用
     *  "@class": invoke
     *  "select": "Math.abs(-10)"
     *
     * 形式2: 对象+方法+参数
     *  "@class": invoke
     *  "select": "{= StringUtils }"
     *  "method": "firstNonBlank"
     *  "args": [" ", null, "abc"]
     * </pre>
     */
    @Override
    public Object render(RenderContext context) throws Exception
    {
        context.assertField("select");

        Object select = context.renderField("select");
        if (select == null) return null;

        // 形式2: select + method + args
        if (context.containsField("method")) {
            String method = context.renderField("method");
            List<Object> args = new ArrayList<>();

            if (context.containsField("args")) {
                Object argsObj = context.getValue("args");
                if (argsObj instanceof Collection<?>) {
                    for (Object arg : (Collection) argsObj) {
                        args.add(context.render(arg));
                    }
                } else {
                    Object argsValue = context.render(argsObj);
                    if (argsValue instanceof Collection) {
                        args.addAll((Collection) argsValue);
                    } else {
                        args.add(argsValue);
                    }
                }
            }

            Map<String, Object> vars = new HashMap<>();
            vars.put("_funcObj", select);
            vars.put("_funcArgs", args);

            StringBuilder program = new StringBuilder("_funcObj.").append(method).append("(");
            for (int i = 0; i < args.size(); i++) {
                if (i > 0) program.append(",");
                program.append("_funcArgs[").append(i).append("]");
            }
            program.append(")");

            return context.render("{%" + program + "%}", vars);
        }

        // 形式1: select 已经是渲染过的字符串,直接作为脚本执行
        return context.render("{%" + select + "%}");
    }

}
