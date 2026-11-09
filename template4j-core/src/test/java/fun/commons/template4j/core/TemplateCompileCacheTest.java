package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import fun.commons.template4j.core.RenderEngine.Template;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 覆盖 engine.compile(template) 显式预编译 API + 内部 parseStrTemplate 缓存。
 */
public class TemplateCompileCacheTest {

    @Test
    public void compiledTemplateProducesSameResultAsDirectRender() throws Exception {
        RenderEngine e = RenderEngine.build();
        String template = """
                {
                  "name": "{@name}",
                  "age": "{@age}",
                  "summary": "{= @name + ' is ' + @age }"
                }
                """;
        Map<String, Object> data = new HashMap<>();
        data.put("name", "alice");
        data.put("age", 30);

        Template compiled = e.compile(template);
        Object r1 = compiled.render(data);
        Object r2 = e.render(template, data);
        Assertions.assertEquals(r2, r1);
    }

    @Test
    public void compiledTemplateReusedAcrossRenders() throws Exception {
        RenderEngine e = RenderEngine.build();
        String template = "{\"v\":\"{@x}\"}";
        Template compiled = e.compile(template);

        Assertions.assertEquals("{\"v\":\"a\"}", compiled.render(Map.of("x", "a")).toString().replace(" ", ""));
        Assertions.assertEquals("{\"v\":\"b\"}", compiled.render(Map.of("x", "b")).toString().replace(" ", ""));
        Assertions.assertEquals("{\"v\":\"c\"}", compiled.render(Map.of("x", "c")).toString().replace(" ", ""));
    }

    @Test
    public void internalCacheHitsForSameStringTemplate() throws Exception {
        RenderEngine e = RenderEngine.build();
        String template = "Hello, {@name}! You are {@age}.";

        // 第一次渲染: cache miss
        Object r1 = e.render(template, Map.of("name", "alice", "age", 30));
        // 第二次渲染: cache hit
        Object r2 = e.render(template, Map.of("name", "bob", "age", 25));

        Assertions.assertEquals("Hello, alice! You are 30.", r1);
        Assertions.assertEquals("Hello, bob! You are 25.", r2);
    }

    @Test
    public void compiledTemplateIsThreadSafe() throws Exception {
        RenderEngine e = RenderEngine.build();
        String template = "{\"v\":\"{@x}\"}";
        Template compiled = e.compile(template);

        int threads = 16;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            final int idx = i;
            new Thread(() -> {
                try {
                    Object r = compiled.render(Map.of("x", "v" + idx));
                    String s = r.toString().replace(" ", "");
                    if (!("{\"v\":\"v" + idx + "\"}").equals(s)) {
                        error.set(new AssertionError("got " + s + " for idx " + idx));
                    }
                } catch (Throwable t) {
                    error.set(t);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        latch.await();
        Assertions.assertNull(error.get(), "thread error: " + error.get());
    }

    @Test
    public void compileYamlTemplate() throws Exception {
        RenderEngine e = RenderEngine.build();
        // YAML 中 {@x} 必须加引号 (裸 {@ 会触发 flow mapping 解析错误)
        String yaml = "name: \"{@name}\"\nage: \"{@age}\"\n";
        Template compiled = e.compileYaml(yaml);
        Object r = compiled.render(Map.of("name", "alice", "age", 30));
        com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) r;
        Assertions.assertEquals("alice", obj.getString("name"));
        Assertions.assertEquals(30, obj.getIntValue("age"));
    }
}
