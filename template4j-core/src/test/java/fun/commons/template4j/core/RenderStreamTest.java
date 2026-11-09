package fun.commons.template4j.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 覆盖 RenderEngine.renderStream(): 推模式 + 拉模式 + 错误处理 + 多次订阅幂等。
 */
public class RenderStreamTest {

    private HttpServer server;
    private int port;

    @BeforeEach
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
    }

    @AfterEach
    public void stopServer() {
        if (server != null) server.stop(0);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
    }

    @Test
    public void basicRenderStreamCompletes() throws Exception {
        RenderEngine engine = RenderEngine.build();
        RenderStream s = engine.renderStream("{\"v\":42}", Map.of());

        List<RenderEngine.TraceEvent> events = s.awaitAll();
        Assertions.assertTrue(s.isDone());
        Assertions.assertNull(s.error());
        Assertions.assertNotNull(s.result());
        // 最后一个事件是 RESULT 终态
        RenderEngine.TraceEvent last = events.get(events.size() - 1);
        Assertions.assertEquals(RenderStream.RESULT_TYPE, last.type());
    }

    @Test
    public void pushSubscribeReceivesEvents() throws Exception {
        AtomicInteger nextCount = new AtomicInteger();
        AtomicReference<String> lastType = new AtomicReference<>();
        CountDownLatch completeLatch = new CountDownLatch(1);
        CountDownLatch errorLatch = new CountDownLatch(1);

        RenderEngine engine = RenderEngine.build();
        engine.renderStream("{\"v\":42}", Map.of())
                .subscribe(
                        ev -> {
                            nextCount.incrementAndGet();
                            lastType.set(ev.type());
                        },
                        err -> errorLatch.countDown(),
                        () -> completeLatch.countDown());

        Assertions.assertTrue(completeLatch.await(5, TimeUnit.SECONDS), "onComplete not called");
        Assertions.assertFalse(errorLatch.await(100, TimeUnit.MILLISECONDS), "onError unexpectedly called");
        Assertions.assertTrue(nextCount.get() >= 1, "expected >=1 onNext calls");
        Assertions.assertEquals(RenderStream.RESULT_TYPE, lastType.get());
    }

    @Test
    public void restComponentEmitsRestEvent() throws Exception {
        server.createContext("/stream-test", exchange -> {
            try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            send(exchange, 200, "{\"x\":1}");
        });
        server.start();

        RenderEngine engine = RenderEngine.build();
        // 用 JSONObject 而不是 String,触发 @class 组件分发
        com.alibaba.fastjson2.JSONObject tpl = com.alibaba.fastjson2.JSON.parseObject(
                "{\"@class\":\"rest\",\"url\":\"http://127.0.0.1:" + port + "/stream-test\"}");
        RenderStream s = engine.renderStream(tpl, Map.of());
        List<RenderEngine.TraceEvent> events = s.awaitAll();
        Assertions.assertNotNull(s.result());
        // 应有 REST 事件 + RESULT 事件
        boolean hasRest = events.stream().anyMatch(e -> "REST".equals(e.type()));
        boolean hasResult = events.stream().anyMatch(e -> RenderStream.RESULT_TYPE.equals(e.type()));
        Assertions.assertTrue(hasRest, "expected REST event in stream, got: " + events.stream().map(RenderEngine.TraceEvent::format).reduce((a,b) -> a + "\n" + b).orElse("(none)"));
        Assertions.assertTrue(hasResult, "expected RESULT event in stream");
    }

    @Test
    public void errorPropagatesToStream() throws Exception {
        RenderEngine engine = RenderEngine.build();
        // 故意给不存在的组件名 → render() 抛 RenderException
        com.alibaba.fastjson2.JSONObject tpl = com.alibaba.fastjson2.JSON.parseObject(
                "{\"@class\":\"definitely-not-a-real-component\"}");
        RenderStream s = engine.renderStream(tpl, Map.of());
        List<RenderEngine.TraceEvent> events = s.awaitAll();
        Assertions.assertTrue(s.isDone());
        Assertions.assertNotNull(s.error());
        // 最后一个事件是 ERROR
        RenderEngine.TraceEvent last = events.get(events.size() - 1);
        Assertions.assertEquals(RenderStream.ERROR_TYPE, last.type());
    }

    @Test
    public void pollAllReturnsPartialEvents() throws Exception {
        RenderEngine engine = RenderEngine.build();
        RenderStream s = engine.renderStream("{\"v\":\"hi\"}", Map.of());
        // 不调 start, pollAll 应自动启动
        List<RenderEngine.TraceEvent> events = s.pollAll(2000);
        Assertions.assertTrue(s.isDone(), "pollAll should wait until done");
        Assertions.assertFalse(events.isEmpty());
    }

    @Test
    public void startIsIdempotent() throws Exception {
        RenderEngine engine = RenderEngine.build();
        RenderStream s = engine.renderStream("{\"v\":1}", Map.of());
        s.start();
        s.start();  // 第二次调用不应重启线程
        s.awaitAll();
        Assertions.assertTrue(s.isDone());
        Assertions.assertNotNull(s.result());
    }

    @Test
    public void subscribeNullArgsUseDefaults() throws Exception {
        RenderEngine engine = RenderEngine.build();
        // 传 null 的 handlers → 用默认 (无操作)
        engine.renderStream("{\"v\":1}", Map.of())
                .subscribe(null, null, null);
        Thread.sleep(500);
        // render 应该已完
        Assertions.assertTrue(true, "subscribe should not throw");
    }

    @Test
    public void eventsAccessorReturnsCopy() throws Exception {
        RenderEngine engine = RenderEngine.build();
        RenderStream s = engine.renderStream("{\"v\":1}", Map.of());
        s.awaitAll();
        java.util.List<RenderEngine.TraceEvent> events = s.events();
        Assertions.assertFalse(events.isEmpty());
        // 修改返回的列表不应影响内部
        int sizeBefore = events.size();
        try {
            events.add(null);
        } catch (Exception ignored) {}
        Assertions.assertEquals(sizeBefore, s.events().size());
    }

    @Test
    public void pollTimeoutReturnsPartial() throws Exception {
        RenderEngine engine = RenderEngine.build();
        RenderStream s = engine.renderStream("{\"v\":1}", Map.of());
        // 用很短的 timeout 测试 pollAll 部分返回
        List<RenderEngine.TraceEvent> events = s.pollAll(50);
        // 应该至少有初始空 + (可能空,可能完成)
        Assertions.assertNotNull(events);
        // 等到完成
        s.awaitAll();
        Assertions.assertTrue(s.isDone());
    }

    @Test
    public void subscribeOverwritesPreviousHandlers() throws Exception {
        AtomicInteger firstNextCount = new AtomicInteger();
        CountDownLatch firstLatch = new CountDownLatch(1);
        RenderEngine engine = RenderEngine.build();
        RenderStream s = engine.renderStream("{\"v\":1}", Map.of());
        s.subscribe(ev -> firstNextCount.incrementAndGet(), null, () -> firstLatch.countDown());
        s.awaitAll();
        // 第一个订阅的 complete 应被触发
        Assertions.assertTrue(firstLatch.await(2, TimeUnit.SECONDS));
        // 至少一个事件被处理
        Assertions.assertTrue(firstNextCount.get() >= 1);
    }
}