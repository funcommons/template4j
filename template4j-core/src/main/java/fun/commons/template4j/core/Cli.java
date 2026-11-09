package fun.commons.template4j.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令行入口: 用模板 + 数据 → 渲染结果。
 *
 * <pre>
 *   java -cp template4j-core.jar fun.commons.template4j.core.Cli render template.jt --data '{"id":123}'
 *   java -cp template4j-core.jar fun.commons.template4j.core.Cli render template.jt --data-file data.json
 *   java -cp template4j-core.jar fun.commons.template4j.core.Cli render template.jt --data-file data.yaml --yaml
 *   java -cp template4j-core.jar fun.commons.template4j.core.Cli version
 *   java -cp template4j-core.jar fun.commons.template4j.core.Cli help
 * </pre>
 *
 * <p>零依赖 (只用 JDK + template4j-core 内部 API)。复杂参数解析留给 picocli 等三方库, 这里手写以保持 jar 精简。
 */
public class Cli {

    public static void main(String[] args) {
        try {
            int exit = run(args);
            System.exit(exit);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(2);
        }
    }

    /** 测试入口: 返回 exit code, 不调 System.exit。 */
    static int run(String[] args) throws Exception {
        if (args.length == 0) {
            printHelp();
            return 1;
        }
        String cmd = args[0];
        switch (cmd) {
            case "version":
            case "-v":
            case "--version":
                printVersion();
                return 0;
            case "help":
            case "-h":
            case "--help":
                printHelp();
                return 0;
            case "render":
                return runRender(args);
            case "watch":
                return runWatch(args);
            case "mcp":
                // 转发到 MCP server (stdio JSON-RPC)
                McpServer.main(shiftArgs(args));
                return 0;
            default:
                System.err.println("unknown command: " + cmd);
                printHelp();
                return 1;
        }
    }

    private static int runRender(String[] args) throws Exception {
        String templatePath = null;
        String dataJson = null;
        String dataFile = null;
        boolean templateIsYaml = false;
        boolean pretty = false;

        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--data":
                    if (i + 1 >= args.length) {
                        // 选项缺值 (已是最后一个参数): 提示用法而不是越界崩溃
                        System.err.println("missing value for " + a);
                        printRenderUsage();
                        return 1;
                    }
                    dataJson = args[++i];
                    break;
                case "--data-file":
                    if (i + 1 >= args.length) {
                        System.err.println("missing value for " + a);
                        printRenderUsage();
                        return 1;
                    }
                    dataFile = args[++i];
                    break;
                case "--yaml":
                    templateIsYaml = true;
                    break;
                case "--pretty":
                    pretty = true;
                    break;
                default:
                    if (templatePath == null && !a.startsWith("--")) {
                        templatePath = a;
                    } else {
                        throw new IllegalArgumentException("unknown arg: " + a);
                    }
            }
        }

        if (templatePath == null) {
            System.err.println("missing template path");
            return 1;
        }
        if (dataJson == null && dataFile == null) {
            System.err.println("missing --data or --data-file");
            return 1;
        }

        String templateStr = readFile(templatePath);
        Map<String, Object> data = loadData(dataJson, dataFile);

        RenderEngine engine = RenderEngine.build();
        Object result = templateIsYaml
                ? engine.renderYaml(templateStr, data)
                : engine.renderJson(templateStr, data);

        if (pretty) {
            System.out.println(com.alibaba.fastjson2.JSON.toJSONString(result,
                    com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat));
        } else {
            System.out.println(com.alibaba.fastjson2.JSON.toJSONString(result));
        }
        return 0;
    }

    private static Map<String, Object> loadData(String json, String file) throws IOException {
        if (json != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = com.alibaba.fastjson2.JSON.parseObject(json, Map.class);
            return m;
        }
        String content = readFile(file);
        // 简单按扩展名推断
        if (file.endsWith(".yml") || file.endsWith(".yaml")) {
            Object parsed = fun.commons.template4j.utils.UJson.JSONFromYaml(content);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) parsed);
                return m;
            }
            throw new IllegalArgumentException("yaml data must be a mapping, got " + parsed.getClass());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = com.alibaba.fastjson2.JSON.parseObject(content, Map.class);
        return m;
    }

    private static String readFile(String path) throws IOException {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8);
    }

    /** 去掉 args[0], 返回剩余参数。供 mcp 子命令转发用。 */
    private static String[] shiftArgs(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] out = new String[args.length - 1];
        System.arraycopy(args, 1, out, 0, out.length);
        return out;
    }

    private static void printVersion() {
        String v = System.getProperty("template4j.version");
        if (v == null) {
            v = readVersionFromJarManifest();
        }
        if (v == null) {
            v = readVersionFromPackage();
        }
        System.out.println("template4j " + (v != null ? v : "dev"));
    }

    /** 从 JAR manifest 读取 Implementation-Version。shaded fat jar 场景。返回 null 表示读不到。 */
    static String readVersionFromJarManifest() {
        java.net.URL url;
        try {
            url = Cli.class.getProtectionDomain().getCodeSource().getLocation();
        } catch (Exception e) {
            return null;
        }
        return readVersionFromJarManifest(url);
    }

    /** 从给定 URL 读 manifest Implementation-Version(测试用 overload)。 */
    static String readVersionFromJarManifest(java.net.URL url) {
        if (url == null) return null;
        try (java.io.InputStream is = url.openStream();
             java.util.jar.JarInputStream jis = new java.util.jar.JarInputStream(is)) {
            java.util.jar.Manifest mf = jis.getManifest();
            if (mf == null) return null;
            return mf.getMainAttributes().getValue("Implementation-Version");
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 Package metadata 读取 Implementation-Version。返回 null 表示读不到。 */
    static String readVersionFromPackage() {
        Package p = Cli.class.getPackage();
        return p != null ? p.getImplementationVersion() : null;
    }

    /** watch 命令: 启动 dev server, 监听模板目录变化热重载。 */
    static int runWatch(String[] args) throws Exception {
        String dir = ".";
        int port = 8080;
        String dataJson = "{}";
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if ("--dir".equals(a) || "-d".equals(a)) {
                if (i + 1 >= args.length) {
                    // 选项缺值 (已是最后一个参数): 提示用法而不是越界崩溃
                    System.err.println("missing value for " + a);
                    printWatchUsage();
                    return 1;
                }
                dir = args[++i];
            } else if ("--port".equals(a) || "-p".equals(a)) {
                if (i + 1 >= args.length) {
                    System.err.println("missing value for " + a);
                    printWatchUsage();
                    return 1;
                }
                port = Integer.parseInt(args[++i]);
            } else if ("--data".equals(a)) {
                if (i + 1 >= args.length) {
                    System.err.println("missing value for " + a);
                    printWatchUsage();
                    return 1;
                }
                dataJson = args[++i];
            } else {
                throw new IllegalArgumentException("unknown arg: " + a);
            }
        }

        Path dirPath = Paths.get(dir).toAbsolutePath();
        if (!Files.isDirectory(dirPath)) {
            System.err.println("--dir must be a directory: " + dirPath);
            return 1;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> baseData = com.alibaba.fastjson2.JSON.parseObject(dataJson, Map.class);

        // 模板缓存: key=template 文件相对路径, value={lastModified, content, isYaml}
        ConcurrentHashMap<String, TemplateEntry> cache = new ConcurrentHashMap<>();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> handleWatchRequest(exchange, dirPath, baseData, cache));
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("template4j dev server");
        System.out.println("  dir:  " + dirPath);
        System.out.println("  port: " + port);
        System.out.println("  open: http://localhost:" + port + "/template.jt");
        System.out.println("  (Ctrl+C to stop)");

        // 阻塞主线程 (server 在独立线程)
        Thread.currentThread().join();
        return 0;
    }

    private static final class TemplateEntry {
        final long lastModified;
        final String content;
        final boolean isYaml;
        TemplateEntry(long m, String c, boolean y) {
            this.lastModified = m;
            this.content = c;
            this.isYaml = y;
        }
    }

    private static void handleWatchRequest(HttpExchange exchange, Path dir, Map<String, Object> baseData,
                                            ConcurrentHashMap<String, TemplateEntry> cache) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/") || path.isEmpty()) path = "/index.jt";

        Path file = dir.resolve(path.substring(1)).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            sendText(exchange, 404, "not found: " + path);
            return;
        }

        String key = file.toString();
        long mtime = Files.getLastModifiedTime(file).toMillis();
        TemplateEntry entry = cache.get(key);
        if (entry == null || entry.lastModified != mtime) {
            // 读文件 + 刷新缓存 (热重载)
            String content = Files.readString(file, StandardCharsets.UTF_8);
            boolean yaml = path.endsWith(".yml") || path.endsWith(".yaml") || path.endsWith(".yt");
            entry = new TemplateEntry(mtime, content, yaml);
            cache.put(key, entry);
        }

        // 合并 baseData + query 参数作为数据
        Map<String, Object> data = new LinkedHashMap<>(baseData);
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    data.put(pair.substring(0, eq),
                            java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
                }
            }
        }

        try {
            RenderEngine engine = RenderEngine.build();
            Object result = entry.isYaml
                    ? engine.renderYaml(entry.content, data)
                    : engine.renderJson(entry.content, data);
            String body = com.alibaba.fastjson2.JSON.toJSONString(result,
                    com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat);
            sendText(exchange, 200, body);
        } catch (Exception e) {
            sendText(exchange, 500, "render error: " + e.getMessage());
        }
    }

    private static void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void printHelp() {
        System.out.println("""
                template4j — JSON/YAML template engine CLI

                Usage:
                  template4j render <template-file> --data '<json>'
                  template4j render <template-file> --data-file <data-file> [--yaml]
                  template4j watch [--dir <path>] [--port <port>] [--data '<json>']
                  template4j mcp            (start MCP server, stdio JSON-RPC)
                  template4j version
                  template4j help

                Options:
                  --data '<json>'       data as inline JSON string
                  --data-file <path>    data file (.json / .yml / .yaml)
                  --yaml                template is YAML (default: JSON)
                  --pretty              pretty-print result
                  --dir/-d <path>       watch: template directory (default: .)
                  --port/-p <number>    watch: HTTP port (default: 8080)

                Commands:
                  render    render template + data → stdout
                  watch     dev server, hot-reload templates on file change
                  mcp       start MCP server (for Claude Desktop / Cursor / etc.)
                  version   print version
                  help      this message
                """);
    }

    /** render 子命令 usage: 选项缺值时输出到 stderr。 */
    private static void printRenderUsage() {
        System.err.println("usage: template4j render <template-file> --data '<json>'");
        System.err.println("   or: template4j render <template-file> --data-file <data-file> [--yaml] [--pretty]");
    }

    /** watch 子命令 usage: 选项缺值时输出到 stderr。 */
    private static void printWatchUsage() {
        System.err.println("usage: template4j watch [--dir <path>] [--port <port>] [--data '<json>']");
    }
}
