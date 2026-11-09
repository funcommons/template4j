package fun.commons.template4j.demo.dto;

public record TryRequest(
    String templateType,
    String template,
    Object data,
    Options options
) {
    public TryRequest {
        if (templateType == null || templateType.isEmpty()) templateType = "json";
        if (options == null) options = new Options(false);
    }

    public record Options(boolean withTrace) {
        public Options {
            // compact constructor, withTrace defaults to false from caller
        }
    }
}
