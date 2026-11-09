package fun.commons.template4j.demo.dto;

import java.util.List;

public record TryResponse(
    Object rendered,
    long timingMs,
    List<String> trace
) {}
