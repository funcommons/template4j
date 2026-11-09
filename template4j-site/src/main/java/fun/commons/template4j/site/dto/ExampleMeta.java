package fun.commons.template4j.demo.dto;

import com.alibaba.fastjson2.annotation.JSONField;

public record ExampleMeta(
    String id,
    String title,
    String description,
    int difficulty,
    @JSONField(name = "template_type") String templateType,
    String source,
    @JSONField(name = "category") String category
) {}
