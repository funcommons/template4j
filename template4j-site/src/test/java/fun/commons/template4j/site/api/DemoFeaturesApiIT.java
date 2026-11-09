package fun.commons.template4j.site.api;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.site.SiteApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = SiteApplication.class)
@AutoConfigureMockMvc
class DemoFeaturesApiIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void parallelDemoAggregatesThreeMockEndpoints() throws Exception {
        mockMvc.perform(get("/api/demo/parallel").param("userId", "u-42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.result.userId").value("u-42"))
                .andExpect(jsonPath("$.data.result.userName").value("alice"))
                .andExpect(jsonPath("$.data.result.vip").value(true))
                .andExpect(jsonPath("$.data.result.orderCount").value(2))
                .andExpect(jsonPath("$.data.result.totalAmount").value(300))
                .andExpect(jsonPath("$.data.result.payment.status").value("paid"))
                .andExpect(jsonPath("$.data.result.audit.logged").value(false))
                .andExpect(jsonPath("$.data.elapsedMs").isNumber());
    }

    @Test
    void compileDemoComparesBareVsCompiled() throws Exception {
        mockMvc.perform(get("/api/demo/compile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.iterations").value(5000))
                .andExpect(jsonPath("$.data.bareRenderTotalMs").isNumber())
                .andExpect(jsonPath("$.data.compiledRenderTotalMs").isNumber())
                .andExpect(jsonPath("$.data.speedup").isString());
    }

    @Test
    void schemaDemoGetWithValidData() throws Exception {
        mockMvc.perform(get("/api/demo/schema"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.userId").value(42))
                .andExpect(jsonPath("$.data.greeting").value("Hello, alice!"));
    }

    @Test
    void schemaDemoPostWithInvalidData() throws Exception {
        // 缺 name 字段 → schema 校验失败
        mockMvc.perform(post("/api/demo/schema")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(Map.of("id", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.phase").value("schema-validation"))
                .andExpect(jsonPath("$.data.message").isString());
    }

    @Test
    void traceDemoCollectsEvents() throws Exception {
        mockMvc.perform(get("/api/demo/trace").param("v", "42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.result.v").value(42))
                .andExpect(jsonPath("$.data.result.doubled").value(84))
                .andExpect(jsonPath("$.data.events").isArray())
                .andExpect(jsonPath("$.data.events[0].type").isString());
    }

    @Test
    void mcpDemoHttpCall() throws Exception {
        // tools/list
        JSONObject rpc = new JSONObject();
        rpc.put("jsonrpc", "2.0");
        rpc.put("id", 1);
        rpc.put("method", "tools/list");
        mockMvc.perform(post("/api/demo/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(rpc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.result.tools[0].name").value("render_template"));
    }

    @Test
    void mcpDemoRenderTemplate() throws Exception {
        // tools/call render_template
        JSONObject args = new JSONObject();
        args.put("template", "{\"v\":\"{@v}\"}");
        args.put("data", Map.of("v", 42));
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        params.put("arguments", args);
        JSONObject rpc = new JSONObject();
        rpc.put("jsonrpc", "2.0");
        rpc.put("id", 2);
        rpc.put("method", "tools/call");
        rpc.put("params", params);
        mockMvc.perform(post("/api/demo/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(rpc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.content[0].text").value("{\"v\":42}"));
    }

    @Test
    void mcpDemoTryEndpoint() throws Exception {
        // /api/demo/mcp/try 便利入口
        JSONObject args = new JSONObject();
        args.put("template", "{\"v\":\"{@x}\"}");
        args.put("data", Map.of("x", 99));
        mockMvc.perform(post("/api/demo/mcp/try")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(args)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.result.content[0].text").value("{\"v\":99}"));
    }

    // ---- McpController 边界覆盖 ----

    @Test
    void mcpDemoParseError() throws Exception {
        // 故意传非 JSON
        mockMvc.perform(post("/api/demo/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not valid json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jsonrpc").value("2.0"))
                .andExpect(jsonPath("$.error.code").value(-32700));
    }

    @Test
    void mcpDemoBatch() throws Exception {
        // batch: 多个 JSON-RPC 请求
        com.alibaba.fastjson2.JSONArray batch = new com.alibaba.fastjson2.JSONArray();
        JSONObject r1 = new JSONObject();
        r1.put("jsonrpc", "2.0"); r1.put("id", 1); r1.put("method", "ping");
        JSONObject r2 = new JSONObject();
        r2.put("jsonrpc", "2.0"); r2.put("id", 2); r2.put("method", "tools/list");
        batch.add(r1); batch.add(r2);
        mockMvc.perform(post("/api/demo/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(batch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[1].result.tools").isArray());
    }

    @Test
    void mcpDemoInvalidRequestBody() throws Exception {
        // 传 JSON 但不是对象也不是数组 (e.g. 字符串/数字)
        mockMvc.perform(post("/api/demo/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"just a string\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.error.code").value(-32600));
    }

    @Test
    void mcpDemoUnknownTool() throws Exception {
        // tools/call 一个不存在的工具 → isError=true
        JSONObject params = new JSONObject();
        params.put("name", "bogus_tool");
        params.put("arguments", new JSONObject());
        JSONObject rpc = new JSONObject();
        rpc.put("jsonrpc", "2.0"); rpc.put("id", 5); rpc.put("method", "tools/call");
        rpc.put("params", params);
        mockMvc.perform(post("/api/demo/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(rpc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true));
    }

    @Test
    void mcpDemoMissingArguments() throws Exception {
        // tools/call 缺 arguments
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        JSONObject rpc = new JSONObject();
        rpc.put("jsonrpc", "2.0"); rpc.put("id", 6); rpc.put("method", "tools/call");
        rpc.put("params", params);
        mockMvc.perform(post("/api/demo/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(rpc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(true));
    }

    @Test
    void mcpDemoBatchWithNonObjectItem() throws Exception {
        // batch 中含非对象项 → 该项被跳过
        com.alibaba.fastjson2.JSONArray batch = new com.alibaba.fastjson2.JSONArray();
        batch.add("not-an-object");  // 非 JSONObject, 应被跳过
        JSONObject r2 = new JSONObject();
        r2.put("jsonrpc", "2.0"); r2.put("id", 2); r2.put("method", "ping");
        batch.add(r2);
        mockMvc.perform(post("/api/demo/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JSON.toJSONString(batch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));
    }
}
