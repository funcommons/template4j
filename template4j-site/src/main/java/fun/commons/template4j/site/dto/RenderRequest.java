package fun.commons.template4j.demo.dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record RenderRequest(
    String templateType,
    String template,
    Object data
) {
    public RenderRequest {
        if (templateType == null || templateType.isEmpty()) templateType = "json";
    }

    public static RenderRequest of(String templateType, String template, Object data) {
        return new RenderRequest(templateType, template, data);
    }

    /** Render-safe data view. Array-shaped data is converted to a singleton
     *  list for the engine, since some component templates expect the root to
     *  be a Map (e.g. {@code {@class:each}}) and would fail otherwise. */
    public Object normalizedData() {
        if (data == null) return new HashMap<String, Object>();
        if (data instanceof Map) return data;
        if (data instanceof Iterable) {
            List<Object> list = new ArrayList<>();
            for (Object o : (Iterable<?>) data) list.add(o);
            return list;
        }
        return data;
    }
}
