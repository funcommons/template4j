package fun.commons.template4j.site.dto;

import java.util.List;

public record TryResponse(
    Object rendered,
    long timingMs,
    List<String> trace
) {}
