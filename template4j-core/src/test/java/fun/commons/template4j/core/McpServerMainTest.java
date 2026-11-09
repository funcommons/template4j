package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 覆盖 McpServer.main() stdio 主循环, 包括 parse-error 路径与正常请求处理。
 * 通过 ProcessBuilder 启动子进程, stdin 喂 JSON-RPC, stdout 检查响应。
 */
public class McpServerMainTest {

    @Test
    public void mainReadsStdinAndWritesJsonRpcResponses() throws Exception {
        String[] reqs = {
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}",
                "not-valid-json",  // 触发 parse error 路径
                "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",
                "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                        + "\"params\":{\"name\":\"render_template\","
                        + "\"arguments\":{\"template\":\"{\\\"v\\\":\\\"{@v}\\\"}\",\"data\":{\"v\":42}}}}",
                "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"totally-unknown\"}",  // method not found
                "",  // 空行, 应被跳过
        };
        StringBuilder stdin = new StringBuilder();
        for (String r : reqs) {
            if (!r.isEmpty()) stdin.append(r).append("\n");
            else stdin.append("\n");
        }
        // 关闭 stdin 让 server 自然退出 (EOF)
        byte[] input = stdin.toString().getBytes(StandardCharsets.UTF_8);

        Process p = new ProcessBuilder(
                System.getProperty("java.home") + "/bin/java",
                "-cp", System.getProperty("java.class.path"),
                "fun.commons.template4j.core.McpServer")
                .redirectErrorStream(false)
                .start();
        try {
            p.getOutputStream().write(input);
            p.getOutputStream().flush();
            p.getOutputStream().close();

            // 等待最多 5s 让 server 处理完
            long deadline = System.currentTimeMillis() + 8000;
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder collected = new StringBuilder();
            String line;
            int lines = 0;
            while (System.currentTimeMillis() < deadline && lines < 5
                    && (line = reader.readLine()) != null) {
                collected.append(line).append("\n");
                lines++;
            }
            String out = collected.toString();
            // 至少有 initialize 响应
            Assertions.assertTrue(out.contains("protocolVersion") || out.contains("serverInfo"),
                    "initialize response: " + out);
            // 也应含 parse-error 或 method-not-found (证明错误路径走了)
            Assertions.assertTrue(out.contains("-32700") || out.contains("-32601"),
                    "error paths: " + out);
        } finally {
            p.destroyForcibly();
            p.waitFor();
        }
    }
}
