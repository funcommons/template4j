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
}
