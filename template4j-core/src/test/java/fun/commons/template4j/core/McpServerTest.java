package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 覆盖 MCP server JSON-RPC 协议: initialize / tools/list / tools/call / 错误处理。
 */
public class McpServerTest {

    @Test
    public void initializeReturnsCapabilities() {
        JSONObject req = JSON.parseObject(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}");
        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertEquals("2.0", r.getString("jsonrpc"));
        Assertions.assertEquals(1, r.getIntValue("id"));
        JSONObject result = r.getJSONObject("result");
        Assertions.assertNotNull(result.get("protocolVersion"));
        Assertions.assertNotNull(result.getJSONObject("capabilities"));
        Assertions.assertNotNull(result.getJSONObject("serverInfo"));
    }

    @Test
    public void toolsListReturnsRenderTemplateTool() {
        JSONObject req = JSON.parseObject(
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}");
        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        JSONObject result = r.getJSONObject("result");
        com.alibaba.fastjson2.JSONArray tools = result.getJSONArray("tools");
        Assertions.assertEquals(1, tools.size());
        JSONObject tool = tools.getJSONObject(0);
        Assertions.assertEquals("render_template", tool.getString("name"));
        // schema 应包含 template / data / format
        JSONObject schema = tool.getJSONObject("inputSchema");
        JSONObject props = schema.getJSONObject("properties");
        Assertions.assertNotNull(props.get("template"));
        Assertions.assertNotNull(props.get("data"));
        Assertions.assertNotNull(props.get("format"));
    }

    @Test
    public void toolsCallRenderJson() {
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        JSONObject args = new JSONObject();
        args.put("template", "{\"v\":\"{@v}\"}");
        args.put("data", JSON.parseObject("{\"v\":42}"));
        params.put("arguments", args);

        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 3);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertEquals(3, r.getIntValue("id"));
        JSONObject result = r.getJSONObject("result");
        Assertions.assertFalse(result.getBooleanValue("isError"));
        com.alibaba.fastjson2.JSONArray content = result.getJSONArray("content");
        Assertions.assertEquals(1, content.size());
        JSONObject item = content.getJSONObject(0);
        Assertions.assertEquals("text", item.getString("type"));
        Assertions.assertTrue(item.getString("text").contains("42"));
    }

    @Test
    public void toolsCallRenderYaml() {
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        JSONObject args = new JSONObject();
        args.put("template", "v: \"{@v}\"\n");
        args.put("data", JSON.parseObject("{\"v\":\"hello\"}"));
        args.put("format", "yaml");
        params.put("arguments", args);

        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 4);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertFalse(r.getJSONObject("result").getBooleanValue("isError"));
        Assertions.assertTrue(r.getJSONObject("result").getJSONArray("content")
                .getJSONObject(0).getString("text").contains("hello"));
    }

    @Test
    public void unknownToolReturnsError() {
        JSONObject params = new JSONObject();
        params.put("name", "bogus");
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 5);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertTrue(r.getJSONObject("result").getBooleanValue("isError"));
    }

    @Test
    public void renderErrorReportsMessage() {
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        JSONObject args = new JSONObject();
        // 故意给坏的模板, 触发 RenderException
        args.put("template", "not valid json {{{");
        args.put("data", new JSONObject());
        params.put("arguments", args);

        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 6);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertTrue(r.getJSONObject("result").getBooleanValue("isError"));
        String text = r.getJSONObject("result").getJSONArray("content")
                .getJSONObject(0).getString("text");
        Assertions.assertTrue(text.contains("render failed"));
    }

    @Test
    public void unknownMethodReturnsMethodNotFound() {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 7);
        req.put("method", "totally/bogus");

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertNotNull(r.getJSONObject("error"));
        Assertions.assertEquals(-32601, r.getJSONObject("error").getIntValue("code"));
    }

    @Test
    public void notificationDoesNotReturnResponse() {
        // 没有 id 的请求是 notification, server 不应回复
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("method", "notifications/initialized");

        // handle 在测试模式下总返回 string; 但 notification 应返回空
        String resp = McpServer.handle(req.toJSONString());
        Assertions.assertTrue(resp == null || resp.isEmpty(),
                "notification should produce no response, got: " + resp);
    }

    @Test
    public void pingReturnsEmpty() {
        JSONObject req = JSON.parseObject(
                "{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"ping\"}");
        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertEquals(8, r.getIntValue("id"));
        Assertions.assertNotNull(r.get("result"));
    }

    // ---- 边界覆盖 ----

    @Test
    public void toolsCallAsNotificationProducesNoResponse() {
        // 没有 id 的 tools/call 是 notification → 无响应
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        JSONObject args = new JSONObject();
        args.put("template", "{\"v\":\"{@v}\"}");
        args.put("data", JSON.parseObject("{\"v\":1}"));
        params.put("arguments", args);
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("method", "tools/call");
        req.put("params", params);
        // 没有 id

        String resp = McpServer.handle(req.toJSONString());
        Assertions.assertTrue(resp == null || resp.isEmpty(),
                "notification should produce no response, got: " + resp);
    }

    @Test
    public void toolsCallWithBogusToolReturnsIsError() {
        JSONObject params = new JSONObject();
        params.put("name", "totally-bogus");
        params.put("arguments", new JSONObject());
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 99);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertTrue(r.getJSONObject("result").getBooleanValue("isError"));
    }

    @Test
    public void toolsCallMissingNameReturnsError() {
        JSONObject params = new JSONObject();
        // 不设 name
        params.put("arguments", new JSONObject());
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 100);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        // unknown tool: null name 不等于 "render_template" → 抛 McpToolException
        Assertions.assertTrue(r.getJSONObject("result").getBooleanValue("isError"));
    }

    @Test
    public void toolsCallMissingArgumentsReturnsError() {
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        // 不设 arguments
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 101);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertTrue(r.getJSONObject("result").getBooleanValue("isError"));
    }

    @Test
    public void toolsCallMissingTemplateReturnsError() {
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        JSONObject args = new JSONObject();
        args.put("data", new JSONObject());  // 不设 template
        params.put("arguments", args);
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 102);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertTrue(r.getJSONObject("result").getBooleanValue("isError"));
    }

    @Test
    public void initializeAsNotificationNoResponse() {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("method", "initialize");
        // 无 id
        String resp = McpServer.handle(req.toJSONString());
        Assertions.assertTrue(resp == null || resp.isEmpty());
    }

    @Test
    public void toolsListAsNotificationNoResponse() {
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("method", "tools/list");
        String resp = McpServer.handle(req.toJSONString());
        Assertions.assertTrue(resp == null || resp.isEmpty());
    }

    @Test
    public void nullMethodReturnsMethodNotFound() {
        // method 字段缺失 → null → 走 default 分支
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 200);
        // 不设 method
        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertEquals(-32601, r.getJSONObject("error").getIntValue("code"));
    }

    @Test
    public void formatYamlUpperCaseRendersYaml() {
        // format="YAML" 大小写不敏感, 走 yaml 分支
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        JSONObject args = new JSONObject();
        args.put("template", "v: \"{@v}\"\n");
        args.put("data", JSON.parseObject("{\"v\":\"x\"}"));
        args.put("format", "YAML");
        params.put("arguments", args);
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 201);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertFalse(r.getJSONObject("result").getBooleanValue("isError"));
    }

    @Test
    public void formatMissingDefaultsToJson() {
        // 没传 format → 默认 json
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        JSONObject args = new JSONObject();
        args.put("template", "{\"v\":\"{@v}\"}");
        args.put("data", JSON.parseObject("{\"v\":7}"));
        // 不传 format
        params.put("arguments", args);
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 202);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertFalse(r.getJSONObject("result").getBooleanValue("isError"));
        String text = r.getJSONObject("result").getJSONArray("content")
                .getJSONObject(0).getString("text");
        Assertions.assertTrue(text.contains("7"));
    }

    @Test
    public void dataMissingDefaultsToEmpty() {
        // 没传 data → 默认空 JSONObject
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        JSONObject args = new JSONObject();
        args.put("template", "{\"v\":\"static\"}");
        // 不传 data
        params.put("arguments", args);
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 203);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertFalse(r.getJSONObject("result").getBooleanValue("isError"));
    }

    @Test
    public void initializedAsNotification() {
        // "initialized" 走 notification no-op 分支
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("method", "initialized");
        // 没 id
        String resp = McpServer.handle(req.toJSONString());
        Assertions.assertTrue(resp == null || resp.isEmpty());
    }

    @Test
    public void initializedWithIdAlsoNoResponseBecauseNotification() {
        // "initialized" 是 notification, 即使带 id 也不响应 (wantsResponse=true 但 case 内不响应)
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 204);
        req.put("method", "initialized");
        String resp = McpServer.handle(req.toJSONString());
        Assertions.assertTrue(resp == null || resp.isEmpty());
    }

    @Test
    public void unknownMethodAsNotificationNoResponse() {
        // unknown method 但无 id → 不响应
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("method", "totally/bogus");
        String resp = McpServer.handle(req.toJSONString());
        Assertions.assertTrue(resp == null || resp.isEmpty());
    }

    @Test
    public void nullIdNotificationHasNoResponse() {
        // fastjson2 默认不序列化 null, 所以 id=null 在 wire 上等同 notification
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", null);
        req.put("method", "ping");
        String resp = McpServer.handle(req.toJSONString());
        Assertions.assertTrue(resp == null || resp.isEmpty());
    }

    @Test
    public void pingAsNotificationHasNoResponse() {
        // ping 也走 wantsResponse=true/false 分支
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("method", "ping");
        String resp = McpServer.handle(req.toJSONString());
        Assertions.assertTrue(resp == null || resp.isEmpty());
    }

    @Test
    public void idExplicitlyNullInJsonStringResponseOmitsId() {
        // 用 JSON 字符串显式传 id:null, fastjson2 parse 后 containsKey("id")=true 但 id=null
        // → okResponse 的 if(id!=null) 分支走 false
        String resp = McpServer.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"ping\"}");
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertFalse(r.containsKey("id"));
        Assertions.assertNotNull(r.get("result"));
    }

    @Test
    public void toolsCallWithYamlTemplateMissingDataDefaultsEmpty() {
        // 故意只传 template 不传 data → data 走 null 分支
        JSONObject params = new JSONObject();
        params.put("name", "render_template");
        JSONObject args = new JSONObject();
        args.put("template", "{\"x\":1}");
        args.put("format", "yaml");
        // 不传 data
        params.put("arguments", args);
        JSONObject req = new JSONObject();
        req.put("jsonrpc", "2.0");
        req.put("id", 300);
        req.put("method", "tools/call");
        req.put("params", params);

        String resp = McpServer.handle(req.toJSONString());
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertFalse(r.getJSONObject("result").getBooleanValue("isError"));
    }

    @Test
    public void errorResponseWithNullIdOmitsIdField() {
        // id 显式 null → errorResponse 的 if (id != null) 走 false → 不带 id 字段
        String resp = McpServer.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"totally-bogus\"}");
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertFalse(r.containsKey("id"));
        Assertions.assertNotNull(r.getJSONObject("error"));
    }

    @Test
    public void toolErrorResponseWithNullIdOmitsIdField() {
        // 触发 tools/call 失败路径 → toolErrorResponse → id=null 时不带 id
        String resp = McpServer.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"tools/call\",\"params\":{\"name\":\"bogus\"}}");
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertFalse(r.containsKey("id"));
        Assertions.assertNotNull(r.getJSONObject("result"));
    }

    @Test
    public void initializeWithNullIdReturnsNoIdField() {
        // initialize + null id → okResponse if(id!=null) 走 false
        String resp = McpServer.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"initialize\"}");
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertFalse(r.containsKey("id"));
        Assertions.assertNotNull(r.getJSONObject("result").get("protocolVersion"));
    }

    @Test
    public void toolsListWithNullIdOmitsIdField() {
        String resp = McpServer.handle(
                "{\"jsonrpc\":\"2.0\",\"id\":null,\"method\":\"tools/list\"}");
        JSONObject r = JSON.parseObject(resp);
        Assertions.assertFalse(r.containsKey("id"));
        Assertions.assertNotNull(r.getJSONObject("result").getJSONArray("tools"));
    }

    @Test
    public void mainHandlesSingleRequest() throws Exception {
        // 测试 main 通过 handle(String) 入口: 单请求验证
        java.lang.reflect.Method m = McpServer.class.getDeclaredMethod("handle", String.class);
        m.setAccessible(true);
        String r = m.invoke(null,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}")
                .toString().trim();
        Assertions.assertTrue(r.contains("\"id\":1"));
        Assertions.assertTrue(r.contains("\"result\""));
    }
}
