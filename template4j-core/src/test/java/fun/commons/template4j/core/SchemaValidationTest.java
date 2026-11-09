package fun.commons.template4j.core;

import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 覆盖 engine.withInputSchema / withOutputSchema: 数据校验、fail-fast、错误信息。
 */
public class SchemaValidationTest {

    private static final String USER_SCHEMA = """
            {
              "$schema": "http://json-schema.org/draft-07/schema#",
              "type": "object",
              "properties": {
                "id": { "type": "integer", "minimum": 1 },
                "name": { "type": "string" }
              },
              "required": ["id", "name"]
            }
            """;

    @Test
    public void inputValidPasses() throws Exception {
        RenderEngine e = RenderEngine.build().withInputSchema(USER_SCHEMA);
        Object r = e.renderJson("""
                { "id": "{@id}", "name": "{@name}" }
                """, Map.of("id", 123, "name", "alice"));
        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals(123, obj.getIntValue("id"));
        Assertions.assertEquals("alice", obj.getString("name"));
    }

    @Test
    public void inputMissingRequiredFailsFast() {
        RenderEngine e = RenderEngine.build().withInputSchema(USER_SCHEMA);
        // data 缺 name, schema 要求 required
        RenderException ex = Assertions.assertThrows(RenderException.class, () ->
                e.renderJson("{\"id\":\"{@id}\"}", Map.of("id", 123)));
        Assertions.assertNotNull(ex.getMessage());
    }

    @Test
    public void inputWrongTypeFails() {
        RenderEngine e = RenderEngine.build().withInputSchema(USER_SCHEMA);
        // id 是字符串, schema 要求 integer
        Assertions.assertThrows(Exception.class, () ->
                e.renderJson("{\"id\":\"{@id}\"}", Map.of("id", "not-a-number")));
    }

    @Test
    public void outputValidPasses() throws Exception {
        String outputSchema = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": { "v": { "type": "string" } },
                  "required": ["v"]
                }
                """;
        RenderEngine e = RenderEngine.build().withOutputSchema(outputSchema);
        Object r = e.renderJson("{\"v\":\"{@name}\"}", Map.of("name", "alice"));
        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals("alice", obj.getString("v"));
    }

    @Test
    public void outputInvalidThrows() {
        String outputSchema = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "type": "object",
                  "properties": { "v": { "type": "integer" } },
                  "required": ["v"]
                }
                """;
        RenderEngine e = RenderEngine.build().withOutputSchema(outputSchema);
        // 渲染后 v 是字符串, schema 要求 integer
        Assertions.assertThrows(Exception.class, () ->
                e.renderJson("{\"v\":\"{@name}\"}", Map.of("name", "alice")));
    }

    @Test
    public void noSchemaIsZeroOverhead() throws Exception {
        // 默认无 schema, 行为不变
        RenderEngine e = RenderEngine.build();
        Object r = e.render("Hello, {@name}!", Map.of("name", "world"));
        Assertions.assertEquals("Hello, world!", r);
    }

    @Test
    public void schemaAsMapObject() throws Exception {
        // schema 也可以传 Map (而非 JSON 字符串)
        java.util.LinkedHashMap<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("id", Map.of("type", "integer")));
        schema.put("required", java.util.List.of("id"));

        RenderEngine e = RenderEngine.build().withInputSchema(schema);
        Object r = e.renderJson("{\"id\":\"{@id}\"}", Map.of("id", 42));
        Assertions.assertEquals(42, ((com.alibaba.fastjson2.JSONObject) r).getIntValue("id"));
        Assertions.assertThrows(Exception.class, () ->
                e.renderJson("{\"id\":\"{@id}\"}", Map.of("id", "bad")));
    }

    @Test
    public void schemaValidationMessageContainsPath() {
        RenderEngine e = RenderEngine.build().withInputSchema(USER_SCHEMA);
        try {
            e.renderJson("{\"id\":\"{@id}\"}", Map.of("id", -1));
            Assertions.fail("should have thrown");
        } catch (Exception ex) {
            // 错误信息或 cause 应指明哪个字段
            String msg = ex.getMessage() + (ex.getCause() != null ? " " + ex.getCause().getMessage() : "");
            Assertions.assertTrue(msg.contains("id") || msg.contains("schema") || msg.contains("minimum"),
                    "msg should mention field/issue: " + msg);
        }
    }
}
