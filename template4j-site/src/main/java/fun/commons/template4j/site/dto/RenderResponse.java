package fun.commons.template4j.site.dto;

public record RenderResponse(
    Object rendered,
    String templateType,
    long timingMs
) {
    public static RenderResponse of(Object rendered, String templateType, long timingMs) {
        return new RenderResponse(rendered, templateType, timingMs);
    }
}
