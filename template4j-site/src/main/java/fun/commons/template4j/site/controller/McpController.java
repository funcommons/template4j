package fun.commons.template4j.site.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.McpServer;
import fun.commons.template4j.site.common.ApiResponse;
import fun.commons.template4j.site.config.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 演示 MCP (Model Context Protocol) over HTTP: 把 stdio JSON-RPC 适配成 HTTP POST。
 *
 * <p>POST 一个 JSON-RPC 请求体, 返回 McpServer 处理后的 JSON-RPC 响应。
 * 用于 web 端直接调用 render_template 工具。
 */
@RestController
@RequestMapping("/api/demo/mcp")
@RequiredArgsConstructor
public class McpController {

    @PostMapping
    public Object handle(@RequestBody String body, HttpServletRequest httpReq) {
        // body 可以是单请求或 batch (JSONArray)。McpServer.handle 接受单请求; batch 我们循环。
        Object parsed;
        try {
            parsed = JSON.parse(body);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("jsonrpc", "2.0");
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", -32700);
            error.put("message", "parse error: " + e.getMessage());
            err.put("error", error);
            return err;
        }

        if (parsed instanceof com.alibaba.fastjson2.JSONArray batch) {
            // batch 模式
            com.alibaba.fastjson2.JSONArray out = new com.alibaba.fastjson2.JSONArray();
            for (Object item : batch) {
                if (item instanceof JSONObject req) {
                    out.add(JSON.parse(McpServer.handle(req.toJSONString())));
                }
            }
            return out;
        }

        if (parsed instanceof JSONObject req) {
            // 直接返回原始 JSON-RPC 响应 (不走 ApiResponse 包装, 协议要求)
            String resp = McpServer.handle(req.toJSONString());
            return JSON.parse(resp);
        }

        // 不支持的请求体
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("jsonrpc", "2.0");
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", -32600);
        error.put("message", "invalid request");
        err.put("error", error);
        return err;
    }

    @PostMapping("/try")
    public ApiResponse<Object> tryDemo(@RequestBody Map<String, Object> args,
                                        HttpServletRequest httpReq) {
        // 便利入口: 直接传 render_template 参数, 不用包 JSON-RPC
        JSONObject rpcParams = new JSONObject();
        rpcParams.put("name", "render_template");
        rpcParams.put("arguments", args);

        JSONObject rpc = new JSONObject();
        rpc.put("jsonrpc", "2.0");
        rpc.put("id", 1);
        rpc.put("method", "tools/call");
        rpc.put("params", rpcParams);

        String resp = McpServer.handle(rpc.toJSONString());
        return ApiResponse.success(JSON.parse(resp),
                (String) httpReq.getAttribute(TraceIdFilter.TRACE_ID_KEY));
    }
}
