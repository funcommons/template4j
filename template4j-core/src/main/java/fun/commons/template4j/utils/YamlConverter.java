package fun.commons.template4j.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * YAML ↔ JSON 转换器：基于 Jackson YAMLMapper 实现。
 * YAMLMapper 通过 builder 一次性配置，避免运行时 configure() 触发的并发竞争（C-6）。
 */
public final class YamlConverter {

    private static final YAMLMapper YAML_MAPPER = buildYamlMapper();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private YamlConverter() {
    }

    private static YAMLMapper buildYamlMapper() {
        return (YAMLMapper) YAMLMapper.builder()
                .enable(YAMLGenerator.Feature.SPLIT_LINES)
                .disable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE)
                .build();
    }

    public static Object toJsonObjectSafe(String str) {
        try {
            JsonNode node = YAML_MAPPER.readTree(str);
            if (node.isObject()) {
                return JSONObject.from(OBJECT_MAPPER.convertValue(node, LinkedHashMap.class));
            } else if (node.isArray()) {
                return JSONArray.from(OBJECT_MAPPER.convertValue(node, ArrayList.class));
            } else {
                return null;
            }
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public static String toYAMLString(Object object) throws IOException {
        return YAML_MAPPER.writeValueAsString(object);
    }

    public static Object fromYaml(String yamlString) throws IOException {
        JsonNode node = YAML_MAPPER.readTree(yamlString);
        if (node.getNodeType() == JsonNodeType.OBJECT) {
            return JSONObject.from(OBJECT_MAPPER.convertValue(node, LinkedHashMap.class));
        } else if (node.getNodeType() == JsonNodeType.ARRAY) {
            return JSONArray.from(OBJECT_MAPPER.convertValue(node, ArrayList.class));
        } else {
            throw new IOException("invalid yaml object or array string");
        }
    }

    public static JSONObject jsonObjectFromYaml(String yamlString) throws IOException {
        return JSONObject.from(YAML_MAPPER.readValue(yamlString, LinkedHashMap.class));
    }

    public static JSONArray jsonArrayFromYaml(String yamlString) throws IOException {
        return JSONArray.from(YAML_MAPPER.readValue(yamlString, ArrayList.class));
    }
}
