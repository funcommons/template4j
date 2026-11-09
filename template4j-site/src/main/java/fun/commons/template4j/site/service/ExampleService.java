package fun.commons.template4j.site.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.site.dto.ExampleDetail;
import fun.commons.template4j.site.dto.ExampleMeta;
import fun.commons.template4j.site.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ExampleService {

    private static final String EXAMPLES_PATH = "examples/index.json";
    private static final String ID_PATTERN = "^[a-z0-9-]+$";

    private volatile List<ExampleMeta> cachedIndex;
    private final Map<String, ExampleDetail> cachedDetails = new ConcurrentHashMap<>();

    public List<ExampleMeta> list() {
        if (cachedIndex == null) {
            synchronized (this) {
                if (cachedIndex == null) {
                    cachedIndex = loadIndex();
                }
            }
        }
        return cachedIndex;
    }

    public ExampleDetail load(String id) {
        if (!id.matches(ID_PATTERN)) {
            throw BizException.exampleNotFound(id);
        }
        return cachedDetails.computeIfAbsent(id, k -> {
            List<ExampleMeta> index = list();
            ExampleMeta meta = index.stream().filter(m -> m.id().equals(k)).findFirst()
                .orElseThrow(() -> BizException.exampleNotFound(k));
            return buildDetail(meta, index);
        });
    }

    private List<ExampleMeta> loadIndex() {
        try {
            ClassPathResource resource = new ClassPathResource(EXAMPLES_PATH);
            String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return JSON.parseArray(json, ExampleMeta.class);
        } catch (Exception e) {
            log.error("Failed to load examples index", e);
            return Collections.emptyList();
        }
    }

    private ExampleDetail buildDetail(ExampleMeta meta, List<ExampleMeta> index) {
        try {
            ParsedExample parsed = parseWebDoc(meta.source());
            if (parsed == null) {
                throw BizException.exampleNotFound(meta.id());
            }

            int idx = -1;
            for (int i = 0; i < index.size(); i++) {
                if (index.get(i).id().equals(meta.id())) {
                    idx = i;
                    break;
                }
            }

            return new ExampleDetail(
                meta.id(),
                meta.title(),
                meta.description(),
                meta.difficulty(),
                meta.templateType(),
                parsed.template,
                parsed.data,
                parsed.expected,
                parsed.sections,
                meta.source(),
                idx > 0 ? index.get(idx - 1).id() : null,
                idx >= 0 && idx < index.size() - 1 ? index.get(idx + 1).id() : null
            );
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to load example detail: " + meta.id(), e);
            throw BizException.exampleNotFound(meta.id());
        }
    }

    @SuppressWarnings("unchecked")
    private ParsedExample parseWebDoc(String sourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(sourcePath);
            if (!resource.exists()) {
                log.warn("Web doc resource not found: {}", sourcePath);
                return fallback(sourcePath);
            }
            String yamlContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Yaml yaml = new Yaml();
            List<Map<String, Object>> rawSections = yaml.load(yamlContent);

            if (rawSections == null || rawSections.isEmpty()) {
                return fallback(sourcePath);
            }

            // Make every section JSON-serializable so the frontend can render
            // markdown + json-renderer sections in the same order as web's docs.
            List<Map<String, Object>> sections = new ArrayList<>();
            String template = null;
            Object data = null;
            Object expected = null;

            for (Map<String, Object> raw : rawSections) {
                if (raw == null) continue;
                String type = (String) raw.get("type");
                Object content = raw.get("content");
                if (type == null) continue;

                Map<String, Object> serialized = new LinkedHashMap<>();
                serialized.put("type", type);

                if ("markdown".equals(type)) {
                    serialized.put("content", content == null ? "" : content.toString());
                } else if ("json-renderer".equals(type)) {
                    serialized.put("content", jsonRendererContent(content));
                    Map<String, Object> jrContent = (Map<String, Object>) content;
                    if (jrContent != null && !Boolean.FALSE.equals(jrContent.get("doc"))) {
                        String[] extracted = extractFirstJsonRenderer((Map<String, Object>) content);
                        if (template == null && extracted[0] != null) template = extracted[0];
                        if (data == null && extracted[1] != null) data = serializedData(extracted[1]);
                        if (expected == null && extracted[2] != null) expected = parsedJson(extracted[2]);
                    }
                } else {
                    serialized.put("content", content);
                }
                sections.add(serialized);
            }

            if (expected == null) expected = Map.of("rendered", "see live output");
            if (sections.isEmpty()) return fallback(sourcePath);
            // when no usable template/data extracted, use legacy fallback semantics
            if (template == null && data == null) {
                ParsedExample fb = fallback(sourcePath);
                return new ParsedExample(fb.template, fb.data, fb.expected, sections, fb.markdown);
            }
            return new ParsedExample(template, data, expected, sections, null);
        } catch (Exception e) {
            log.warn("Failed to parse web doc: {}", sourcePath, e);
        }
        return fallback(sourcePath);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonRendererContent(Object content) {
        if (!(content instanceof Map<?,?> m)) return Map.of();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }

    // returns [template, data, expected] from the first json-renderer section's variables
    @SuppressWarnings("unchecked")
    private String[] extractFirstJsonRenderer(Map<String, Object> content) {
        if (content == null) return new String[]{null, null, null};
        List<Map<String, Object>> variables = (List<Map<String, Object>>) content.get("variables");
        if (variables == null) return new String[]{null, null, null};
        String template = null;
        String dataStr = null;
        String expected = null;
        for (Map<String, Object> var : variables) {
            String name = (String) var.get("name");
            Object value = var.get("value");
            if (value == null || name == null) continue;
            if ("template".equals(name)) {
                template = value.toString().trim();
            } else if ("templateUrl".equals(name)) {
                String tpl = loadTemplateUrl(value.toString().trim());
                if (tpl != null) template = tpl;
            } else if ("data".equals(name)) {
                dataStr = value.toString();
            }
        }
        Object resultObj = content.get("result");
        if (resultObj != null) expected = resultObj.toString();
        return new String[]{template, dataStr, expected};
    }

    private Object serializedData(String dataStr) {
        try { return JSON.parse(dataStr); }
        catch (Exception e) { return Map.of(); }
    }

    private Object parsedJson(String s) {
        try { return JSON.parse(s); }
        catch (Exception e) { return s; }
    }

    private String loadTemplateUrl(String templateUrl) {
        try {
            ClassPathResource resource = new ClassPathResource(templateUrl);
            if (resource.exists()) {
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            }
            // try with /docs prefix
            resource = new ClassPathResource("docs/" + templateUrl);
            if (resource.exists()) {
                return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            log.warn("Failed to load templateUrl: {}", templateUrl, e);
        }
        return null;
    }

    private ParsedExample fallback(String sourcePath) {
        log.info("Using fallback example for: {}", sourcePath);
        return FALLBACKS.getOrDefault(sourcePath,
            new ParsedExample(
                "{\"msg\":\"hello template4j\"}",
                Map.of(),
                Map.of("msg", "hello template4j"),
                List.of(),
                null
            ));
    }

    private static final Map<String, ParsedExample> FALLBACKS = Map.of(
        "docs/documents/home.yml", new ParsedExample(
            "{\"status\":0,\"message\":\"success\",\"data\":{\"system\":\"{@OS}\",\"username\":\"{@USERNAME}\",\"home\":\"{@HOMEDRIVE}{@HOMEPATH}\"}}",
            Map.of("OS", "Windows_NT", "USERNAME", "Administrator", "HOMEDRIVE", "C:", "HOMEPATH", "\\Users\\Administrator"),
            Map.of("status", 0, "message", "success", "data", Map.of("system", "Windows_NT", "username", "Administrator", "home", "C:\\Users\\Administrator")),
            List.of(),
            null
        ),
        "docs/documents/quick-start.yml", new ParsedExample(
            "{\"info\":\"{@name}同学{@age}岁,是{= @age > 18 ? '成':'未成' }年人\"}",
            Map.of("name", "小红", "age", 19, "likes", List.of("电影", "音乐", "小说", "帅哥")),
            Map.of("info", "小红同学19岁,是成年人"),
            List.of(),
            null
        )
    );

    private record ParsedExample(
        String template,
        Object data,
        Object expected,
        List<Map<String, Object>> sections,
        String markdown  // reserved for future use when a doc carries only prose
    ) {}
}
