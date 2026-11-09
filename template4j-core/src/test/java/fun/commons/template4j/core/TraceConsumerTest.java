package fun.commons.template4j.core;

import com.sun.net.httpserver.HttpServer;
import fun.commons.template4j.core.RenderEngine.TraceEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 覆盖 engine.withTraceConsumer: 事件捕获、类型字段、零开销默认。
 */
public class TraceConsumerTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    public void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    public void traceEventsFiredForStringRender() throws Exception {
        List<TraceEvent> events = new CopyOnWriteArrayList<>();
        RenderEngine e = RenderEngine.build().withTraceConsumer(events::add);

        e.render("Hello, {@name}!", Map.of("name", "alice"));

        // 至少一条 RENDER_STRING 事件, value 含 "alice"
        boolean found = events.stream()
                .anyMatch(ev -> "RENDER_STRING".equals(ev.type())
                        && String.valueOf(ev.value()).contains("alice"));
        Assertions.assertTrue(found, "missing RENDER_STRING event: " + events);
    }

    @Test
    public void traceEventsFiredForRestCall() throws Exception {
        server.createContext("/t", exchange -> {
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var os = exchange.getResponseBody()) { os.write(body); }
        });

        List<TraceEvent> events = new CopyOnWriteArrayList<>();
        RenderEngine e = RenderEngine.build().withTraceConsumer(events::add);

        e.renderJson("""
                { "@class": "rest", "url": "http://127.0.0.1:%d/t" }
                """.formatted(port), Map.of());

        boolean foundRest = events.stream()
                .anyMatch(ev -> "REST".equals(ev.type()));
        Assertions.assertTrue(foundRest, "missing REST event: " + events);
    }

    @Test
    public void traceEventsFiredForComponent() throws Exception {
        List<TraceEvent> events = new CopyOnWriteArrayList<>();
        RenderEngine e = RenderEngine.build().withTraceConsumer(events::add);

        e.renderJson("""
                { "@class": "if", "select": "true", "then": "yes" }
                """, Map.of());

        boolean foundComp = events.stream()
                .anyMatch(ev -> "COMPONENT".equals(ev.type()));
        Assertions.assertTrue(foundComp, "missing COMPONENT event: " + events);
    }

    @Test
    public void traceEventsFiredForScriptEval() throws Exception {
        List<TraceEvent> events = new CopyOnWriteArrayList<>();
        RenderEngine e = RenderEngine.build().withTraceConsumer(events::add);

        e.render("{= 1 + 2 }", Map.of());

        boolean foundScript = events.stream()
                .anyMatch(ev -> "SCRIPT".equals(ev.type()));
        Assertions.assertTrue(foundScript, "missing SCRIPT event: " + events);
    }

    @Test
    public void traceConsumerNullMeansZeroOverhead() throws Exception {
        // 默认无 consumer, 渲染正常完成
        RenderEngine e = RenderEngine.build();
        Object r = e.render("Hello, {@name}!", Map.of("name", "alice"));
        Assertions.assertEquals("Hello, alice!", r);
    }

    @Test
    public void traceEventFieldsPopulated() throws Exception {
        List<TraceEvent> events = new CopyOnWriteArrayList<>();
        RenderEngine e = RenderEngine.build().withTraceConsumer(events::add);

        e.render("Hello, {@name}!", Map.of("name", "bob"));

        TraceEvent ev = events.stream()
                .filter(x -> "RENDER_STRING".equals(x.type()))
                .findFirst()
                .orElseThrow();
        Assertions.assertNotNull(ev.path());
        Assertions.assertNotNull(ev.message());
        // value 在 RENDER_STRING 事件中是渲染后的字符串
        Assertions.assertTrue(String.valueOf(ev.value()).contains("bob"));
    }

    @Test
    public void traceEventFormatReturnsReadableString() throws Exception {
        List<TraceEvent> events = new CopyOnWriteArrayList<>();
        RenderEngine e = RenderEngine.build().withTraceConsumer(events::add);

        e.render("{= 1 + 1 }", Map.of());

        TraceEvent ev = events.stream()
                .filter(x -> "SCRIPT".equals(x.type()))
                .findFirst()
                .orElseThrow();
        String formatted = ev.format();
        Assertions.assertTrue(formatted.contains("SCRIPT"));
    }

    @Test
    public void multipleConsumersAllInvoked() throws Exception {
        List<TraceEvent> a = new CopyOnWriteArrayList<>();
        List<TraceEvent> b = new CopyOnWriteArrayList<>();
        RenderEngine e = RenderEngine.build()
                .withTraceConsumer(a::add)
                .withTraceConsumer(b::add);

        e.render("Hello, {@name}!", Map.of("name", "x"));

        // 第二次 withTraceConsumer 应该替换或追加 — 我们设计为追加
        Assertions.assertFalse(a.isEmpty());
        Assertions.assertFalse(b.isEmpty());
    }
}
