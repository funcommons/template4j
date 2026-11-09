package fun.commons.template4j.core.components;

import fun.commons.template4j.core.RenderComponent;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class SwitchRenderer implements RenderComponent {

    /** Keys at the switch template root that are reserved, not treated as cases. */
    private static final Set<String> RESERVED = Set.of("@class", "select", "cases", "default");

    @Override
    public String name() {
        return "switch";
    }

    /**
     * <pre>
     *---
     *"@class": switch
     *"select": "{$color}"
     *"cases":
     *   "red":    { code: 0, result: "#0000ff" }
     *   "blue":   { code: 0, result: "#00ff00" }
     *   "green":  { code: 0, result: "#ff0000" }
     *"default":
     *   code: 404
     *   msg: 不支持{$color}
     *
     * Case values may also be written as direct sibling keys (no `cases` map):
     *   {
     *     "@class": "switch",
     *     "select": "{@color}",
     *     "red":   { "code": 0, "result": "#0000ff" },
     *     "blue":  { "code": 0, "result": "#00ff00" },
     *     "green": { "code": 0, "result": "#ff0000" },
     *     "default": { "code": -1, "result": "..." }
     *   }
     * </pre>
     */
    @Override
    public Object render(RenderContext context) throws Exception
    {
        //必需包含select属性
        context.assertField("select");
        Object select = context.renderField("select");

        Map<String, Object> cases = context.getValue("cases");
        if (cases == null) {
            // 兼容写法:cases 作为兄弟 key 平铺
            cases = collectSiblingCases(context);
        }
        if (cases != null && select != null) {
            String field = String.valueOf(select);
            if (StringUtils.isNotBlank(field) && cases.containsKey(field)) {
                return context.render(cases.get(field));
            }
            // select 为 Number 时,尝试去掉小数部分作为 key (兼容 Math.floor 等返回 Double 的场景)
            if (select instanceof Number) {
                String intStr = String.valueOf(((Number) select).intValue());
                if (cases.containsKey(intStr)) {
                    return context.render(cases.get(intStr));
                }
            }
            // select 为 "9.0" 这类可解析为整数的字符串时,尝试用整数形式匹配
            try {
                String intStr = String.valueOf(new java.math.BigDecimal(field).intValue());
                if (!intStr.equals(field) && cases.containsKey(intStr)) {
                    return context.render(cases.get(intStr));
                }
            } catch (NumberFormatException ignore) {
            }
        }

        if (context.containsField("default")) {
            return context.renderField("default");
        }
        return null;
    }

    private static Map<String, Object> collectSiblingCases(RenderContext context) {
        Map<String, Object> src = context.getMap();
        if (src == null || src.isEmpty()) return null;
        Map<String, Object> out = null;
        for (Map.Entry<String, Object> e : src.entrySet()) {
            if (RESERVED.contains(e.getKey())) continue;
            if (out == null) out = new LinkedHashMap<>();
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }
}
