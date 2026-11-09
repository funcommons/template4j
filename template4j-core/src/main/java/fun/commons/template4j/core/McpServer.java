package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 最小 MCP (Model Context Protocol) server, stdio transport (line-delimited JSON-RPC 2.0)。
 *
 * <p>暴露一个工具 "render_template", 让 AI Agent 调用 template4j 渲染模板。
 *
 * <p>使用方式 (集成进 Claude Desktop / Cursor / 其它 MCP client):
 * <pre>
 * {
 *   "mcpServers": {
 *     "template4j": {
 *       "command": "java",
 *       "args": ["-cp", "template4j-core.jar", "fun.commons.template4j.core.McpServer"]
 *     }
 *   }
 * }
 * </pre>
 *
 * <p>支持的 JSON-RPC 方法:
 * <ul>
 *   <li>{@code initialize} — MCP 协议握手, 返回 capabilities</li>
 *   <li>{@code tools/list} — 列出可用工具 (render_template)</li>
 *   <li>{@code tools/call} — 调用工具, 参数 {@code name="render_template"}, {@code arguments={template, data, format?}}</li>
 * </ul>
 *
 * <p>设计原则: 零依赖 (JDK + fastjson2 + template4j-core)。stdio 适合 IDE 插件 / CLI 工具集成。
 */
public class McpServer {

    private static final String PROTOCOL_VERSION = "2024-11-05";
    private static final String SERVER_NAME = "template4j";
    private static final String SERVER_VERSION = "0.5.0";

    public static void main(String[] args) {
        // stdin / stdout line-delimited JSON-RPC
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintStream out = System.out;
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                JSONObject request;
                try {
                    request = JSON.parseObject(line);
                } catch (Exception e) {
                    writeLine(out, errorResponse(null, -32700, "parse error: " + e.getMessage()));
                    continue;
                }
                handle(request, out);
            }
        } catch (Exception e) {
            System.err.println("mcp server fatal: " + e.getMessage());
            System.exit(1);
        }
    }

    /** 测试入口 / 跨模块入口: 处理单个请求并返回响应 (JSON 字符串)。 */
    public static String handle(String requestJson) {
        JSONObject request = JSON.parseObject(requestJson);
        // 测试时把响应收集到 buffer
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        PrintStream ps = new PrintStream(buf, true, StandardCharsets.UTF_8);
        handle(request, ps);
        return buf.toString(StandardCharsets.UTF_8).trim();
    }

    private static void handle(JSONObject request, PrintStream out) {
        String method = request.getString("method");
        Object id = request.get("id");
        // notification (no id) → no response
        boolean wantsResponse = request.containsKey("id");

        try {
            switch (method == null ? "" : method) {
                case "initialize":
                    if (wantsResponse) writeLine(out, okResponse(id, initializeResult()));
                    break;
                case "initialized":
                case "notifications/initialized":
                    // no-op
                    break;
                case "tools/list":
                    if (wantsResponse) writeLine(out, okResponse(id, toolsListResult()));
                    break;
                case "tools/call":
                    if (wantsResponse) {
                        JSONObject result = toolsCall(request.getJSONObject("params"));
                        writeLine(out, okResponse(id, result));
                    }
                    break;
                case "ping":
                    if (wantsResponse) writeLine(out, okResponse(id, new JSONObject()));
                    break;
                default:
                    if (wantsResponse) {
                        writeLine(out, errorResponse(id, -32601, "method not found: " + method));
                    }
            }
        } catch (McpToolException e) {
            if (wantsResponse) writeLine(out, toolErrorResponse(id, e.getMessage()));
        } catch (Exception e) {
            if (wantsResponse) writeLine(out, errorResponse(id, -32603, "internal: " + e.getMessage()));
        }
    }

    // ===== MCP protocol responses =====

    private static JSONObject initializeResult() {
        JSONObject result = new JSONObject();
        result.put("protocolVersion", PROTOCOL_VERSION);
        JSONObject caps = new JSONObject();
        JSONObject toolsCap = new JSONObject();
        toolsCap.put("listChanged", false);
        caps.put("tools", toolsCap);
        result.put("capabilities", caps);
        JSONObject serverInfo = new JSONObject();
        serverInfo.put("name", SERVER_NAME);
        serverInfo.put("version", SERVER_VERSION);
        result.put("serverInfo", serverInfo);
        return result;
    }

    private static JSONObject toolsListResult() {
        JSONObject result = new JSONObject();
        JSONArray tools = new JSONArray();
        tools.add(renderTemplateTool());
        result.put("tools", tools);
        return result;
    }

    private static JSONObject renderTemplateTool() {
        JSONObject tool = new JSONObject();
        tool.put("name", "render_template");
        tool.put("description",
                "Render a template4j template (JSON or YAML string) with the given data. "
                + "Placeholders: {@jsonPath} / {$var} / {=groovyExpr} / {%groovyBlock%}.");
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject props = new JSONObject();
        JSONObject templateProp = new JSONObject();
        templateProp.put("type", "string");
        templateProp.put("description", "Template string (JSON or YAML)");
        props.put("template", templateProp);
        JSONObject dataProp = new JSONObject();
        dataProp.put("type", "object");
        dataProp.put("description", "Data context as JSON object");
        props.put("data", dataProp);
        JSONObject formatProp = new JSONObject();
        formatProp.put("type", "string");
        formatProp.put("enum", new JSONArray().fluentAdd("json").fluentAdd("yaml"));
        formatProp.put("default", "json");
        formatProp.put("description", "Template format: 'json' (default) or 'yaml'");
        props.put("format", formatProp);
        schema.put("properties", props);
        JSONArray required = new JSONArray();
        required.add("template");
        required.add("data");
        schema.put("required", required);
        tool.put("inputSchema", schema);
        return tool;
    }

    private static JSONObject toolsCall(JSONObject params) throws McpToolException {
        if (params == null) throw new McpToolException("missing params");
        String toolName = params.getString("name");
        if (!"render_template".equals(toolName)) {
            throw new McpToolException("unknown tool: " + toolName);
        }
        JSONObject args = params.getJSONObject("arguments");
        if (args == null) throw new McpToolException("missing arguments");
        String template = args.getString("template");
        if (template == null) throw new McpToolException("missing argument: template");
        JSONObject data = args.getJSONObject("data");
        if (data == null) data = new JSONObject();
        String format = args.getString("format");
        if (format == null) format = "json";

        RenderEngine engine = RenderEngine.build();
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = new LinkedHashMap<>(data);
        Object rendered;
        try {
            if ("yaml".equalsIgnoreCase(format)) {
                rendered = engine.renderYaml(template, dataMap);
            } else {
                rendered = engine.renderJson(template, dataMap);
            }
        } catch (Exception e) {
            throw new McpToolException("render failed: " + e.getMessage());
        }

        JSONObject result = new JSONObject();
        JSONArray content = new JSONArray();
        JSONObject item = new JSONObject();
        item.put("type", "text");
        item.put("text", JSON.toJSONString(rendered));
        content.add(item);
        result.put("content", content);
        result.put("isError", false);
        return result;
    }

    // ===== JSON-RPC framing =====

    private static JSONObject okResponse(Object id, Object result) {
        JSONObject resp = new JSONObject();
        resp.put("jsonrpc", "2.0");
        if (id != null) resp.put("id", id);
        resp.put("result", result);
        return resp;
    }

    private static JSONObject errorResponse(Object id, int code, String message) {
        JSONObject resp = new JSONObject();
        resp.put("jsonrpc", "2.0");
        if (id != null) resp.put("id", id);
        JSONObject error = new JSONObject();
        error.put("code", code);
        error.put("message", message);
        resp.put("error", error);
        return resp;
    }

    private static JSONObject toolErrorResponse(Object id, String toolMessage) {
        // MCP: tool error returns OK JSON-RPC with isError=true in result
        JSONObject resp = new JSONObject();
        resp.put("jsonrpc", "2.0");
        if (id != null) resp.put("id", id);
        JSONObject result = new JSONObject();
        JSONArray content = new JSONArray();
        JSONObject item = new JSONObject();
        item.put("type", "text");
        item.put("text", toolMessage);
        content.add(item);
        result.put("content", content);
        result.put("isError", true);
        resp.put("result", result);
        return resp;
    }

    private static void writeLine(PrintStream out, JSONObject obj) {
        out.println(JSON.toJSONString(obj));
        out.flush();
    }

    private static final class McpToolException extends Exception {
        McpToolException(String msg) { super(msg); }
    }
}
