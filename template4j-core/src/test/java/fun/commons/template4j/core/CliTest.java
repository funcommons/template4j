package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 覆盖 Cli.main / run: 命令分发、render、version、help。
 * 用 System.out 捕获 stdout 做断言。
 */
public class CliTest {

    @TempDir
    Path tmp;

    @Test
    public void helpReturns0() throws Exception {
        // 捕获 stdout
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{"help"});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("template4j"));
        Assertions.assertTrue(out.contains("Usage"));
    }

    @Test
    public void versionReturns0() throws Exception {
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{"version"});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("template4j"));
    }

    @Test
    public void noArgsPrintsHelpReturns1() throws Exception {
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{});
            Assertions.assertEquals(1, code);
        });
        Assertions.assertTrue(out.contains("Usage"));
    }

    @Test
    public void unknownCommandReturns1() throws Exception {
        String err = captureErr(() -> {
            int code = Cli.run(new String[]{"bogus"});
            Assertions.assertEquals(1, code);
        });
        Assertions.assertTrue(err.contains("unknown command"));
    }

    @Test
    public void renderJsonInline() throws Exception {
        Path tpl = Files.writeString(tmp.resolve("t.jt"),
                "{\"v\":\"{@v}\"}");
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{"render", tpl.toString(), "--data", "{\"v\":42}"});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("\"v\":42"));
    }

    @Test
    public void renderJsonDataFile() throws Exception {
        Path tpl = Files.writeString(tmp.resolve("t.jt"),
                "{\"v\":\"{@v}\"}");
        Path data = Files.writeString(tmp.resolve("d.json"),
                "{\"v\":\"hello\"}");
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{"render", tpl.toString(), "--data-file", data.toString()});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("hello"));
    }

    @Test
    public void renderYamlTemplate() throws Exception {
        Path tpl = Files.writeString(tmp.resolve("t.jt"),
                "v: \"{@v}\"\n");
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{
                    "render", tpl.toString(),
                    "--data", "{\"v\":\"x\"}",
                    "--yaml"});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("x"));
    }

    @Test
    public void renderMissingTemplateReturns1() throws Exception {
        String err = captureErr(() -> {
            int code = Cli.run(new String[]{"render", "--data", "{}"});
            Assertions.assertEquals(1, code);
        });
        Assertions.assertTrue(err.contains("missing template"));
    }

    @Test
    public void renderMissingDataReturns1() throws Exception {
        Path tpl = Files.writeString(tmp.resolve("t.jt"), "{}");
        String err = captureErr(() -> {
            int code = Cli.run(new String[]{"render", tpl.toString()});
            Assertions.assertEquals(1, code);
        });
        Assertions.assertTrue(err.contains("missing --data"));
    }

    @Test
    public void watchStartsAndServesTemplate() throws Exception {
        Path tpl = Files.writeString(tmp.resolve("t.jt"), "{\"v\":\"{@v}\"}");

        // 在独立线程启动 watch server
        int port = findFreePort();
        Thread server = new Thread(() -> {
            try {
                Cli.run(new String[]{"watch", "--dir", tmp.toString(), "--port", String.valueOf(port)});
            } catch (Exception ignored) {
            }
        });
        server.setDaemon(true);
        server.start();

        // 等 server 起来
        Thread.sleep(500);

        // GET /t.jt?v=hello
        try (java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient()) {
            java.net.http.HttpResponse<String> resp = client.send(
                    java.net.http.HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/t.jt?v=hello"))
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            Assertions.assertEquals(200, resp.statusCode());
            Assertions.assertTrue(resp.body().contains("hello"));
        }
    }

    @Test
    public void watch404ForUnknownFile() throws Exception {
        int port = findFreePort();
        Thread server = new Thread(() -> {
            try {
                Cli.run(new String[]{"watch", "--dir", tmp.toString(), "--port", String.valueOf(port)});
            } catch (Exception ignored) {
            }
        });
        server.setDaemon(true);
        server.start();
        Thread.sleep(500);

        try (java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient()) {
            java.net.http.HttpResponse<String> resp = client.send(
                    java.net.http.HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + port + "/missing.jt"))
                            .build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            Assertions.assertEquals(404, resp.statusCode());
        }
    }

    private static int findFreePort() throws IOException {
        try (java.net.ServerSocket s = new java.net.ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    @Test
    public void mcpCommandForwardsToMcpServer() throws Exception {
        // Cli mcp 子命令转发到 McpServer.main
        // 用 stdin 提供 JSON-RPC 请求, 检查 stdout 输出
        String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}\n"
                + "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}\n";
        Process p = new ProcessBuilder(
                System.getProperty("java.home") + "/bin/java",
                "-cp", System.getProperty("java.class.path"),
                "fun.commons.template4j.core.Cli",
                "mcp")
                .redirectErrorStream(false)
                .start();
        try (var os = p.getOutputStream(); var isr = new java.io.InputStreamReader(p.getInputStream()); var br = new java.io.BufferedReader(isr)) {
            os.write(request.getBytes());
            os.flush();
            os.close();
            String line1 = br.readLine();
            String line2 = br.readLine();
            Assertions.assertNotNull(line1);
            Assertions.assertTrue(line1.contains("initialize") || line1.contains("protocolVersion"),
                    "first response: " + line1);
            Assertions.assertTrue(line2.contains("tools"));
        } finally {
            p.destroyForcibly();
        }
    }

    // ===== helpers =====

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static String captureOut(ThrowingRunnable r) throws Exception {
        PrintStream old = System.out;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setOut(new PrintStream(baos));
        try {
            r.run();
        } finally {
            System.setOut(old);
        }
        return baos.toString();
    }

    private static String captureErr(ThrowingRunnable r) throws Exception {
        PrintStream old = System.err;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        System.setErr(new PrintStream(baos));
        try {
            r.run();
        } finally {
            System.setErr(old);
        }
        return baos.toString();
    }
}
