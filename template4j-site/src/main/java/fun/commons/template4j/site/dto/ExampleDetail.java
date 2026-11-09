package fun.commons.template4j.site.dto;

import java.util.List;
import java.util.Map;

public record ExampleDetail(
    String id,
    String title,
    String description,
    int difficulty,
    String templateType,
    String template,
    Object data,
    Object expected,
    List<Map<String, Object>> sections,
    String source,
    String prevId,
    String nextId
) {}
