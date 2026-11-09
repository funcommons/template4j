package fun.commons.template4j.core.components;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.RenderComponent;
import fun.commons.template4j.core.RenderException;
import org.apache.commons.lang3.BooleanUtils;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Parallel component: 并发执行 tasks 映射中的子模板, 聚合结果供 body 渲染。
 *
 * <p>典型用于 BFF 场景下的多接口聚合: 多个 @class:rest 同时发起,
 * 整体超时后走 error 分支, 任一失败默认会让整张模板走 error (除非 continueOnError=true)。
 *
 * <p>线程池: 共享一个 daemon FixedThreadPool(16)。每个 task 在子 SelfObject 下执行,
 * 与 parent 数据隔离。RestRenderer 的 HttpClient 也是共享的, 真正并发由其内部线程池实现。
 */
public class ParallelRenderer implements RenderComponent {

    private static final int POOL_SIZE = Integer.getInteger("template4j.parallel.poolSize", 16);
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            POOL_SIZE,
            r -> {
                Thread t = new Thread(r, "template4j-parallel");
                t.setDaemon(true);
                return t;
            });

    @Override
    public String name() {
        return "parallel";
    }

    @Override
    public Object render(RenderContext context) throws Exception {
        context.assertField("tasks");

        Object tasksRaw = context.map.get("tasks");
        if (!(tasksRaw instanceof Map)) {
            if (context.containsField("body")) {
                return context.renderField("body");
            }
            return new JSONObject();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> tasksMap = (Map<String, Object>) tasksRaw;
        if (tasksMap.isEmpty()) {
            if (context.containsField("body")) {
                return context.renderField("body");
            }
            return new JSONObject();
        }

        long timeoutMs = parseLong(context.getValue("timeout", 30000), 30000L);
        boolean continueOnError = parseBooleanField(context, "continueOnError", false);
        long deadline = System.currentTimeMillis() + timeoutMs;

        Map<String, CompletableFuture<Object>> futures = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : tasksMap.entrySet()) {
            String taskName = entry.getKey();
            Object taskTemplate = entry.getValue();
            CompletableFuture<Object> f = CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return context.render(taskTemplate, new HashMap<>());
                        } catch (Exception ex) {
                            throw new CompletionException(ex);
                        }
                    },
                    EXECUTOR);
            futures.put(taskName, f);
        }

        Map<String, Object> results = new LinkedHashMap<>();
        Throwable firstError = null;
        String firstErrorTask = null;

        for (Map.Entry<String, CompletableFuture<Object>> entry : futures.entrySet()) {
            String taskName = entry.getKey();
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                // 整体超时已过, 不再等待后续任务
                entry.getValue().cancel(true);
                if (continueOnError) {
                    results.put(taskName, null);
                } else if (firstError == null) {
                    firstError = new java.util.concurrent.TimeoutException(
                            "parallel timeout after " + timeoutMs + "ms");
                    firstErrorTask = taskName;
                }
                continue;
            }
            try {
                Object result = entry.getValue().get(remaining, TimeUnit.MILLISECONDS);
                results.put(taskName, result);
            } catch (Exception ex) {
                Throwable cause = unwrap(ex);
                entry.getValue().cancel(true);
                if (continueOnError) {
                    results.put(taskName, null);
                } else if (firstError == null) {
                    firstError = cause;
                    firstErrorTask = taskName;
                }
            }
        }

        if (firstError != null) {
            if (context.containsField("error")) {
                JSONObject errData = new JSONObject();
                errData.put("task", firstErrorTask);
                errData.put("message", firstError.getMessage() != null
                        ? firstError.getMessage() : firstError.getClass().getSimpleName());
                errData.put("cause", firstError.getClass().getSimpleName());
                errData.put("path", context.self.path());
                return context.renderField("error", "error", errData);
            }
            throw new RenderException(context.self.path(), "@class:parallel",
                    "task '" + firstErrorTask + "' failed", firstError);
        }

        if (context.containsField("body")) {
            return context.renderField("body", results);
        }

        JSONObject out = new JSONObject();
        results.forEach(out::put);
        return out;
    }

    private static Throwable unwrap(Exception ex) {
        Throwable c = ex.getCause();
        if (c instanceof CompletionException) c = c.getCause();
        return c != null ? c : ex;
    }

    private static long parseLong(Object v, long fallback) {
        if (v instanceof Number) return ((Number) v).longValue();
        if (v == null) return fallback;
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBooleanField(RenderContext context, String field, boolean fallback) {
        if (!context.containsField(field)) return fallback;
        try {
            Object v = context.renderField(field);
            if (v == null) return fallback;
            Boolean b = BooleanUtils.toBooleanObject(String.valueOf(v));
            return b == null ? fallback : b;
        } catch (Exception e) {
            return fallback;
        }
    }
}
