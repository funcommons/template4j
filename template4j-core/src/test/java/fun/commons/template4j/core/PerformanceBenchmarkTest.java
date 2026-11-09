package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 性能基准：冷启动 vs 热路径 + 不同复杂度模板。
 * <p>
 * 默认 surefire 在 template4j-core/pom.xml 的 excludes 里跳过本文件，启用方式：
 * <pre>mvn -pl template4j-core -Pbenchmark test</pre>
 * <p>
 * 每条 benchmark 都断言"在合理阈值内"，超阈值会失败，作为 perf regression 网关。
 */
public class PerformanceBenchmarkTest {

    private final RenderEngine engine = RenderEngine.build();

    @Test
    public void benchmarkColdVsWarmSimpleTemplate() throws Exception {
        // 简单字符串模板
        String template = "Hello, {@name}! You are {@age} years old.";

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice");
        data.put("age", 25);

        // 冷启动
        long coldStart = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            engine.render(template, data);
        }
        long coldMs = (System.nanoTime() - coldStart) / 1_000_000;

        // 热路径（重复执行同一模板，JIT 优化）
        long warmStart = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            engine.render(template, data);
        }
        long warmMs = (System.nanoTime() - warmStart) / 1_000_000;

        long coldPerCall = coldMs * 1_000_000L / 10_000;     // ns
        long warmPerCall = warmMs * 1_000_000L / 100_000;   // ns

        System.out.println("=== Simple template benchmark ===");
        System.out.printf("Cold  (10,000 calls): %d ms  (avg %,d ns/call)%n", coldMs, coldPerCall);
        System.out.printf("Warm  (100,000 calls): %d ms  (avg %,d ns/call)%n", warmMs, warmPerCall);
        System.out.printf("Speedup: %.1fx%n", (double) coldPerCall / warmPerCall);

        // 简单字符串模板应 < 100us/call
        Assertions.assertTrue(warmPerCall < 100_000, "warm too slow: " + warmPerCall + " ns/call");
    }

    @Test
    public void benchmarkJsonTemplate() throws Exception {
        // JSON 模板 — 注意 {@age} 必须放在字符串值内,否则裸 {@age} 不是合法 JSON
        String template = """
                {
                    "name": "{@name}",
                    "age": "{@age}",
                    "address": {
                        "city": "{@city}",
                        "zip": "{@zip}"
                    },
                    "likes": "{@likes}"
                }
                """;

        Map<String, Object> data = new HashMap<>();
        data.put("name", "Alice");
        data.put("age", 25);
        data.put("city", "Beijing");
        data.put("zip", "100000");
        data.put("likes", "music");

        long warmStart = System.nanoTime();
        for (int i = 0; i < 50_000; i++) {
            engine.renderJson(template, data);
        }
        long warmMs = (System.nanoTime() - warmStart) / 1_000_000;
        long warmPerCall = warmMs * 1_000_000L / 50_000;

        System.out.println("=== JSON template benchmark ===");
        System.out.printf("Warm  (50,000 calls): %d ms  (avg %,d ns/call)%n", warmMs, warmPerCall);

        Assertions.assertTrue(warmPerCall < 500_000, "warm too slow: " + warmPerCall + " ns/call");
    }

    @Test
    public void benchmarkScriptTemplate() throws Exception {
        // 含 Groovy 脚本的模板
        String template = """
                {
                    "status": "{= @code + 200 }",
                    "msg": "{= @code > 0 ? 'ok' : 'fail' }"
                }
                """;

        Map<String, Object> data = new HashMap<>();
        data.put("code", 200);

        // 预热
        for (int i = 0; i < 100; i++) {
            engine.renderJson(template, data);
        }

        long warmStart = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            engine.renderJson(template, data);
        }
        long warmMs = (System.nanoTime() - warmStart) / 1_000_000;
        long warmPerCall = warmMs * 1_000_000L / 10_000;

        System.out.println("=== Script template benchmark ===");
        System.out.printf("Warm  (10,000 calls): %d ms  (avg %,d ns/call)%n", warmMs, warmPerCall);

        // 脚本慢一些，但应 < 5ms/call
        Assertions.assertTrue(warmPerCall < 5_000_000, "warm too slow: " + warmPerCall + " ns/call");
    }

    @Test
    public void benchmarkEachComponent() throws Exception {
        // 循环组件
        String template = """
                {
                    "@class": "each",
                    "select": "{@items}",
                    "body": {
                        "id": "{$item.id}",
                        "name": "{$item.name}",
                        "price": "{= $item.price * 1.1 }"
                    }
                }
                """;

        // 100 个 item
        java.util.List<JSONObject> items = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(JSONObject.parse("{\"id\":" + i + ",\"name\":\"item" + i + "\",\"price\":" + (i * 10) + "}"));
        }
        Map<String, Object> data = Map.of("items", items);

        for (int i = 0; i < 10; i++) {
            engine.renderJson(template, data);
        }

        long warmStart = System.nanoTime();
        for (int i = 0; i < 1_000; i++) {
            engine.renderJson(template, data);
        }
        long warmMs = (System.nanoTime() - warmStart) / 1_000_000;
        long warmPerCall = warmMs * 1_000_000L / 1_000;

        System.out.println("=== Each component (100 items) benchmark ===");
        System.out.printf("Warm  (1,000 calls): %d ms  (avg %,d ns/call)%n", warmMs, warmPerCall);

        Assertions.assertTrue(warmPerCall < 50_000_000, "warm too slow: " + warmPerCall + " ns/call");
    }
}