package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
        // JDK 17 兼容: HttpClient 在 JDK 21 才有 close()。不用 try-with-resources。
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/t.jt?v=hello"))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("hello"));
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

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/missing.jt"))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(404, resp.statusCode());
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

    // ---- Cli 边界覆盖 ----

    @Test
    public void renderPrettyFlag() throws Exception {
        Path tpl = Files.writeString(tmp.resolve("t.jt"),
                "{\"v\":\"{@v}\"}");
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{"render", tpl.toString(),
                    "--data", "{\"v\":42}", "--pretty"});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("v"), "pretty output: " + out);
    }

    @Test
    public void renderUnknownArgThrows() throws Exception {
        Path tpl = Files.writeString(tmp.resolve("t.jt"), "{}");
        // Cli.run 内部 try/catch 捕获 IllegalArgumentException, 走 main 路径会 System.exit
        // 走 run() 路径会抛出。用 assertThrows 验证
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                Cli.run(new String[]{"render", tpl.toString(), "--data", "{}", "--bogus-flag"}));
    }

    @Test
    public void renderYamlDataFile() throws Exception {
        Path tpl = Files.writeString(tmp.resolve("t.jt"),
                "{\"v\":\"{@v}\"}");
        Path data = Files.writeString(tmp.resolve("d.yaml"),
                "v: hello-from-yaml\n");
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{"render", tpl.toString(),
                    "--data-file", data.toString()});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("hello-from-yaml"));
    }

    @Test
    public void watchInvalidDirReturns1() throws Exception {
        String err = captureErr(() -> {
            int code = Cli.run(new String[]{"watch", "--dir", "/nonexistent-path-xyz"});
            Assertions.assertEquals(1, code);
        });
        Assertions.assertTrue(err.contains("must be a directory"));
    }

    @Test
    public void watchWithInlineData() throws Exception {
        Path tpl = Files.writeString(tmp.resolve("t.jt"), "{\"v\":\"{@v}\"}");
        int port = findFreePort();
        Thread server = new Thread(() -> {
            try {
                Cli.run(new String[]{"watch", "--dir", tmp.toString(),
                        "--port", String.valueOf(port),
                        "--data", "{\"v\":\"preset\"}"});
            } catch (Exception ignored) {}
        });
        server.setDaemon(true);
        server.start();
        Thread.sleep(500);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/t.jt"))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("preset"));
    }

    @Test
    public void watchServesYamlTemplate() throws Exception {
        // .yt 后缀 → isYaml=true → renderYaml
        Files.writeString(tmp.resolve("a.yt"), "v: \"{@v}\"\n");
        int port = findFreePort();
        Thread server = new Thread(() -> {
            try {
                Cli.run(new String[]{"watch", "--dir", tmp.toString(),
                        "--port", String.valueOf(port)});
            } catch (Exception ignored) {}
        });
        server.setDaemon(true);
        server.start();
        Thread.sleep(500);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/a.yt?v=yamldata"))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("yamldata"));
    }

    @Test
    public void watchIndexFallbackPath() throws Exception {
        // GET / → 默认 index.jt
        Files.writeString(tmp.resolve("index.jt"), "{\"v\":\"home\"}");
        int port = findFreePort();
        Thread server = new Thread(() -> {
            try {
                Cli.run(new String[]{"watch", "--dir", tmp.toString(),
                        "--port", String.valueOf(port)});
            } catch (Exception ignored) {}
        });
        server.setDaemon(true);
        server.start();
        Thread.sleep(500);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/"))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("home"));
    }

    @Test
    public void watchCacheHitOnSecondRequest() throws Exception {
        // 二次访问相同文件 → 走 cache hit 分支 (lastModified 不变)
        Files.writeString(tmp.resolve("cached.jt"), "{\"v\":\"x\"}");
        int port = findFreePort();
        Thread server = new Thread(() -> {
            try {
                Cli.run(new String[]{"watch", "--dir", tmp.toString(),
                        "--port", String.valueOf(port)});
            } catch (Exception ignored) {}
        });
        server.setDaemon(true);
        server.start();
        Thread.sleep(500);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        // 第一次 → cache miss, 读文件
        java.net.http.HttpResponse<String> resp1 = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/cached.jt"))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp1.statusCode());
        // 第二次 → cache hit
        java.net.http.HttpResponse<String> resp2 = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/cached.jt"))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp2.statusCode());
    }

    @Test
    public void watchQueryWithoutEqualsIsIgnored() throws Exception {
        // query 中没有 `=` → 跳过该对 (if eq > 0 分支)
        Files.writeString(tmp.resolve("q.jt"), "{\"v\":\"ok\"}");
        int port = findFreePort();
        Thread server = new Thread(() -> {
            try {
                Cli.run(new String[]{"watch", "--dir", tmp.toString(),
                        "--port", String.valueOf(port)});
            } catch (Exception ignored) {}
        });
        server.setDaemon(true);
        server.start();
        Thread.sleep(500);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/q.jt?noequals"))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode());
    }

    @Test
    public void renderYamlDataFileNotMappingThrows() throws Exception {
        // YAML data 是 sequence 不是 mapping → 走 IllegalArgumentException 分支
        Path tpl = Files.writeString(tmp.resolve("t.jt"), "{}");
        Path data = Files.writeString(tmp.resolve("seq.yaml"),
                "- a\n- b\n");
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                Cli.run(new String[]{"render", tpl.toString(),
                        "--data-file", data.toString()}));
    }

    @Test
    public void watchRenderErrorReturns500() throws Exception {
        // 模板语法错误 → 500 with "render error"
        Files.writeString(tmp.resolve("bad.jt"), "{\"v\":\"{@v\""); // 漏掉 }
        int port = findFreePort();
        Thread server = new Thread(() -> {
            try {
                Cli.run(new String[]{"watch", "--dir", tmp.toString(),
                        "--port", String.valueOf(port)});
            } catch (Exception ignored) {}
        });
        server.setDaemon(true);
        server.start();
        Thread.sleep(500);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/bad.jt"))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(500, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("render error"));
    }

    @Test
    public void watchUnknownArgThrows() throws Exception {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                Cli.run(new String[]{"watch", "--bogus"}));
    }

    // ---- alias coverage ----

    @Test
    public void runDashV() throws Exception {
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{"-v"});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("template4j"));
    }

    @Test
    public void runDoubleDashVersion() throws Exception {
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{"--version"});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("template4j"));
    }

    @Test
    public void runDashH() throws Exception {
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{"-h"});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("template4j"));
        Assertions.assertTrue(out.contains("Usage"));
    }

    @Test
    public void runDoubleDashHelp() throws Exception {
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{"--help"});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("Usage"));
    }

    @Test
    public void runMcpForwardsToMcpServer() throws Exception {
        // 用 mock stdin 提供一个有效 JSON-RPC ping 请求
        String in = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}\n";
        java.io.InputStream oldIn = System.in;
        java.io.PrintStream oldOut = System.out;
        try {
            System.setIn(new java.io.ByteArrayInputStream(in.getBytes(StandardCharsets.UTF_8)));
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            System.setOut(new java.io.PrintStream(baos));
            // McpServer.main() 读到 EOF 退出; 此处我们用 Cli.run("mcp")
            int code = Cli.run(new String[]{"mcp"});
            Assertions.assertEquals(0, code);
            // 输出应包含 ping 响应
            String out = baos.toString();
            Assertions.assertTrue(out.contains("result") || out.contains("ping"));
        } finally {
            System.setIn(oldIn);
            System.setOut(oldOut);
        }
    }

    @Test
    public void runShiftArgsEmpty() throws Exception {
        // shiftArgs 在 args.length == 1 时返回空数组
        // mcp 命令无后续参数时, shiftArgs 返回 [], McpServer.main 立即 EOF 退出
        java.io.InputStream oldIn = System.in;
        try {
            System.setIn(new java.io.ByteArrayInputStream(new byte[0]));
            int code = Cli.run(new String[]{"mcp"});
            Assertions.assertEquals(0, code);
        } finally {
            System.setIn(oldIn);
        }
    }

    // ---- runRender 边缘 ----

    @Test
    public void renderTwoNonFlagArgsThrows() throws Exception {
        // 第二个非 flag 参数 (templatePath 已设置) → 抛 IllegalArgumentException
        Path tpl = Files.writeString(tmp.resolve("t.jt"), "{}");
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                Cli.run(new String[]{"render", tpl.toString(), "extra-arg", "--data", "{}"}));
    }

    // ---- loadData 边缘 ----

    @Test
    public void loadDataYamlNotMappingThrows() throws Exception {
        // yaml data 是 sequence → parseMap 失败 → 抛 IllegalArgumentException
        Path tpl = Files.writeString(tmp.resolve("t.jt"), "{}");
        Path data = Files.writeString(tmp.resolve("seq.yaml"), "- a\n- b\n");
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                Cli.run(new String[]{"render", tpl.toString(), "--data-file", data.toString()}));
    }

    @Test
    public void loadDataJsonFileSucceeds() throws Exception {
        Path tpl = Files.writeString(tmp.resolve("t.jt"), "{\"v\":\"{@v}\"}");
        Path data = Files.writeString(tmp.resolve("d.json"), "{\"v\":\"hello\"}");
        String out = captureOut(() -> {
            int code = Cli.run(new String[]{"render", tpl.toString(), "--data-file", data.toString()});
            Assertions.assertEquals(0, code);
        });
        Assertions.assertTrue(out.contains("hello"));
    }

    // ---- printVersion ----

    @Test
    public void printVersionOutputContainsTemplate4j() throws Exception {
        String out = captureOut(() -> {
            java.lang.reflect.Method m = Cli.class.getDeclaredMethod("printVersion");
            m.setAccessible(true);
            m.invoke(null);
        });
        Assertions.assertTrue(out.contains("template4j"));
    }

    @Test
    public void printVersionFromSystemProperty() throws Exception {
        String oldVal = System.getProperty("template4j.version");
        System.setProperty("template4j.version", "9.9.9-systemprop");
        try {
            String out = captureOut(() -> {
                java.lang.reflect.Method m = Cli.class.getDeclaredMethod("printVersion");
                m.setAccessible(true);
                m.invoke(null);
            });
            Assertions.assertTrue(out.contains("9.9.9-systemprop"),
                    "should use system property, got: " + out);
        } finally {
            if (oldVal == null) System.clearProperty("template4j.version");
            else System.setProperty("template4j.version", oldVal);
        }
    }

    @Test
    public void readVersionFromJarManifestReturnsNullOrVersion() {
        // 真实 classpath 下,可能拿到 null (classpath dir) 或 version (jar)
        String v = Cli.readVersionFromJarManifest();
        // 不抛异常即可;值是 null 或字符串
        if (v != null) {
            Assertions.assertFalse(v.isEmpty());
        }
    }

    @Test
    public void readVersionFromJarManifestWithNullUrl() {
        Assertions.assertNull(Cli.readVersionFromJarManifest((java.net.URL) null));
    }

    @Test
    public void readVersionFromJarManifestWithBadUrl() {
        // 不存在的 file:// URL → openStream throws IOException → catch → return null
        try {
            java.net.URL bad = new java.net.URL("file:///nonexistent/path/xyz.jar");
            Assertions.assertNull(Cli.readVersionFromJarManifest(bad));
        } catch (java.net.MalformedURLException e) {
            // unreachable
        }
    }

    @Test
    public void readVersionFromJarManifestWithPlainDirectory() throws Exception {
        // 普通目录 URL (如 target/classes) → JarInputStream 会失败 → return null
        java.net.URL dirUrl = tmp.toUri().toURL();
        Assertions.assertNull(Cli.readVersionFromJarManifest(dirUrl));
    }

    @Test
    public void readVersionFromJarManifestWithRealJar() throws Exception {
        // 构造一个临时 jar 包含 manifest Implementation-Version → 应该读到
        java.io.File tmpJar = tmp.resolve("test.jar").toFile();
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
                new java.io.FileOutputStream(tmpJar))) {
            java.util.jar.Manifest mf = new java.util.jar.Manifest();
            mf.getMainAttributes().putValue("Implementation-Version", "9.9.9-test");
            jos.putNextEntry(new java.util.jar.JarEntry("META-INF/MANIFEST.MF"));
            jos.write("Manifest-Version: 1.0\r\nImplementation-Version: 9.9.9-test\r\n".getBytes());
            jos.closeEntry();
        }
        String v = Cli.readVersionFromJarManifest(tmpJar.toURI().toURL());
        Assertions.assertEquals("9.9.9-test", v);
    }

    @Test
    public void readVersionFromPackageReturnsNullOrVersion() {
        String v = Cli.readVersionFromPackage();
        if (v != null) {
            Assertions.assertFalse(v.isEmpty());
        }
    }

    @Test
    public void printVersionHandlesAllPaths() throws Exception {
        // 取消 system property → 走 fallback 链
        String oldVal = System.getProperty("template4j.version");
        System.clearProperty("template4j.version");
        try {
            String out = captureOut(() -> {
                java.lang.reflect.Method m = Cli.class.getDeclaredMethod("printVersion");
                m.setAccessible(true);
                m.invoke(null);
            });
            Assertions.assertTrue(out.contains("template4j"));
        } finally {
            if (oldVal != null) System.setProperty("template4j.version", oldVal);
        }
    }

    // ---- handleWatchRequest edges ----

    @Test
    public void watchYamlTemplateViaWatchRequest() throws Exception {
        // .yml 模板 → entry.isYaml=true → renderYaml 分支
        Files.writeString(tmp.resolve("a.yml"), "v: \"{@v}\"\n");
        int port = findFreePort();
        Thread server = new Thread(() -> {
            try {
                Cli.run(new String[]{"watch", "--dir", tmp.toString(),
                        "--port", String.valueOf(port)});
            } catch (Exception ignored) {}
        });
        server.setDaemon(true);
        server.start();
        Thread.sleep(500);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/a.yml?v=ok"))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(200, resp.statusCode());
        Assertions.assertTrue(resp.body().contains("ok"));
    }

    @Test
    public void watchPathTraversalReturns404() throws Exception {
        // 用 ../ 试图逃出 dir → file.startsWith(dir) == false → 404
        Files.writeString(tmp.resolve("safe.jt"), "{}");
        int port = findFreePort();
        Thread server = new Thread(() -> {
            try {
                Cli.run(new String[]{"watch", "--dir", tmp.toString(),
                        "--port", String.valueOf(port)});
            } catch (Exception ignored) {}
        });
        server.setDaemon(true);
        server.start();
        Thread.sleep(500);

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpResponse<String> resp = client.send(
                java.net.http.HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/../etc/passwd"))
                        .build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());
        Assertions.assertEquals(404, resp.statusCode());
    }

    // ---- 选项缺值防护 (flag 收尾 → missing value + usage, exit 1) ----

    @Test
    public void renderMissingDataValueReturns1() throws Exception {
        // --data 是最后一个参数 → 缺值, 输出 missing value + render usage, exit 1
        Path tpl = Files.writeString(tmp.resolve("t.jt"), "{}");
        String err = captureErr(() -> {
            int code = Cli.run(new String[]{"render", tpl.toString(), "--data"});
            Assertions.assertEquals(1, code);
        });
        Assertions.assertTrue(err.contains("missing value for --data"));
        Assertions.assertTrue(err.contains("usage: template4j render"));
    }

    @Test
    public void renderMissingDataFileValueReturns1() throws Exception {
        // --data-file 是最后一个参数 → 缺值
        Path tpl = Files.writeString(tmp.resolve("t.jt"), "{}");
        String err = captureErr(() -> {
            int code = Cli.run(new String[]{"render", tpl.toString(), "--data-file"});
            Assertions.assertEquals(1, code);
        });
        Assertions.assertTrue(err.contains("missing value for --data-file"));
        Assertions.assertTrue(err.contains("usage: template4j render"));
    }

    @Test
    public void watchMissingDirValueReturns1() throws Exception {
        // --dir / -d 是最后一个参数 → 缺值, 输出 missing value + watch usage, exit 1
        for (String flag : new String[]{"--dir", "-d"}) {
            String err = captureErr(() -> {
                int code = Cli.run(new String[]{"watch", flag});
                Assertions.assertEquals(1, code);
            });
            Assertions.assertTrue(err.contains("missing value for " + flag));
            Assertions.assertTrue(err.contains("usage: template4j watch"));
        }
    }

    @Test
    public void watchMissingPortValueReturns1() throws Exception {
        // --port / -p 是最后一个参数 → 缺值
        for (String flag : new String[]{"--port", "-p"}) {
            String err = captureErr(() -> {
                int code = Cli.run(new String[]{"watch", flag});
                Assertions.assertEquals(1, code);
            });
            Assertions.assertTrue(err.contains("missing value for " + flag));
            Assertions.assertTrue(err.contains("usage: template4j watch"));
        }
    }

    @Test
    public void watchMissingDataValueReturns1() throws Exception {
        // --data 是最后一个参数 → 缺值
        String err = captureErr(() -> {
            int code = Cli.run(new String[]{"watch", "--data"});
            Assertions.assertEquals(1, code);
        });
        Assertions.assertTrue(err.contains("missing value for --data"));
        Assertions.assertTrue(err.contains("usage: template4j watch"));
    }
}
