package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import fun.commons.template4j.utils.UJson;
import fun.commons.template4j.utils.UString;
import lombok.NonNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class JsonRenderer implements RenderEngine, RenderEngine.Renderer, RenderEngine.ScriptCacheStats {

    private final ComponentRegistry components = new ComponentRegistry();
    private final ScriptEnvironment scripts = new ScriptEnvironment();
    private final ExpressionEvaluator evaluator = new ExpressionEvaluator(scripts, this);

    private TemplateLoader templateLoader = new DefaultTemplateLoader();

    /** trace consumers: 默认空 (零开销)。注册后每次事件回调一次。 */
    private final List<Consumer<RenderEngine.TraceEvent>> traceConsumers = new CopyOnWriteArrayList<>();

    /** 输入/输出 schema 校验器。默认 null, 不校验 (零开销)。 */
    private JsonSchema inputSchema;
    private JsonSchema outputSchema;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 引擎级注入 executor (parallel 组件并发提交子任务用)。null = 使用内置默认池。
     * 实例字段 (非静态) 保证引擎间隔离; volatile 保证渲染期间切换立即可见。
     */
    private volatile ExecutorService injectedExecutor;

    /** parseStrTemplate 结果缓存: 同一字符串模板多次渲染时复用 tokenized string + expressionMap。 */
    static final int MAX_PARSE_CACHE_ENTRIES = Integer.getInteger(
            "template4j.parseCache.maxEntries", 1024);
    private final Cache<String, ParsedStrTemplate> parseCache = Caffeine.newBuilder()
            .maximumSize(MAX_PARSE_CACHE_ENTRIES)
            .build();

    /** parseStrTemplate 不可变结果: tokenized string + expressionMap。 */
    private static final class ParsedStrTemplate {
        final String tokenized;
        final Map<String, String> expressionMap;
        ParsedStrTemplate(String tokenized, Map<String, String> expressionMap) {
            this.tokenized = tokenized;
            this.expressionMap = Collections.unmodifiableMap(expressionMap);
        }
    }

    /** 当前缓存条目数 (估算): 先触发清理, 再取估值。 */
    int parseCacheSize() {
        parseCache.cleanUp();
        return (int) parseCache.estimatedSize();
    }

    /** 清空解析缓存。 */
    void clearParseCache() {
        parseCache.invalidateAll();
    }

    public TemplateLoader getTemplateLoader() {
        return templateLoader;
    }

    void setTemplateLoader(TemplateLoader templateLoader) {
        this.templateLoader = templateLoader;
    }


    protected JsonRenderer() {
        components.registerAllDefaults(this);
    }

    @Override
    public JsonRenderer withComponent(RenderComponent component) {
        components.register(component);
        return this;
    }

    @Override
    public JsonRenderer withBean(@NonNull String name, Object bean) {
        if (scripts.hasBean(name)) {
            throw new IllegalStateException(String.format("bean '%s' already exists.", name));
        }
        scripts.registerBean(name, bean);
        return this;
    }

    @Override
    public RenderEngine withLoader(@NonNull TemplateLoader templateLoader) {
        setTemplateLoader(templateLoader);
        return this;
    }

    @Override
    public JsonRenderer withImport(@NonNull String className) {
        scripts.addImport(className);
        return this;
    }

    @Override
    public RenderEngine.TraceHandle addTraceConsumer(@NonNull Consumer<RenderEngine.TraceEvent> consumer) {
        traceConsumers.add(consumer);
        return () -> traceConsumers.remove(consumer);
    }

    @Override
    public RenderEngine withInputSchema(Object schemaContent) {
        this.inputSchema = compileSchema(schemaContent);
        return this;
    }

    @Override
    public RenderEngine withOutputSchema(Object schemaContent) {
        this.outputSchema = compileSchema(schemaContent);
        return this;
    }

    @Override
    public JsonRenderer withSandbox(boolean enabled) {
        scripts.setSandboxEnabled(enabled);
        return this;
    }

    /** {@link RenderEngine.ScriptCacheStats} 真值实现: 委托本引擎的 ScriptEnvironment 编译脚本缓存。 */
    @Override
    public int scriptCacheSize() {
        return scripts.scriptCacheSize();
    }

    @Override
    public void clearScriptCache() {
        scripts.clearScriptCache();
    }

    @Override
    public RenderEngine withExecutor(ExecutorService executor) {
        this.injectedExecutor = executor;
        return this;
    }

    /**
     * 当前引擎注入的 executor, 供 parallel 组件提交任务; null 表示回退内置默认池。
     * public 而非 package-private: components 子包的 ParallelRenderer 需跨包读取。
     */
    public ExecutorService executorForParallel() {
        return injectedExecutor;
    }

    @Override
    public RenderEngine withRestOptions(fun.commons.template4j.core.components.RestOptions options) {
        // rest 组件每引擎注册一个实例 (ComponentRegistry by name); null 同样转发 = 清除, 回退静态默认
        if (components.find("rest") instanceof fun.commons.template4j.core.components.RestRenderer rest) {
            rest.setRestOptions(options);
        }
        return this;
    }

    /** 把 JSON 字符串或 Map 编译为 networknt JsonSchema。null 则清除。 */
    private static JsonSchema compileSchema(Object content) {
        if (content == null) return null;
        try {
            JsonNode node;
            if (content instanceof String s) {
                node = MAPPER.readTree(s);
            } else if (content instanceof Map || content instanceof List) {
                node = MAPPER.valueToTree(content);
            } else if (content instanceof JsonNode) {
                node = (JsonNode) content;
            } else {
                throw new IllegalArgumentException(
                        "schema must be String / Map / JsonNode, got " + content.getClass());
            }
            return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid schema: " + e.getMessage(), e);
        }
    }

    private static void validate(JsonSchema schema, Object data, String phase, String path)
            throws RenderException {
        if (schema == null) return;
        try {
            JsonNode node = MAPPER.valueToTree(data);
            Set<ValidationMessage> errors = schema.validate(node);
            if (!errors.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (ValidationMessage err : errors) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append(err.toString());
                }
                throw new RenderException(path, "{schema:" + phase + '}',
                        "schema validation failed: " + sb, null);
            }
        } catch (RenderException re) {
            throw re;
        } catch (Exception e) {
            throw new RenderException(path, "{schema:" + phase + '}',
                    "schema validation error: " + e.getMessage(), e);
        }
    }

    @Override
    public void trace(RenderEngine.TraceEvent event) {
        // 热路径: 无 consumer 时直接返回 (JIT 可消除)
        if (traceConsumers.isEmpty()) return;
        for (Consumer<RenderEngine.TraceEvent> c : traceConsumers) {
            try { c.accept(event); } catch (Throwable ignored) { /* consumer 异常不影响渲染 */ }
        }
    }

    @Override
    public boolean traceEnabled() {
        // 精确判断: CopyOnWriteArrayList.isEmpty() 零成本。调用点先判此门再构造
        // TraceEvent.of(...), 无 consumer 时事件对象一个都不分配 (trace() 内部的
        // isEmpty 兜底保留, 语义不变)。
        return !traceConsumers.isEmpty();
    }

    @Override
    public Object render(Object template, Map<String, Object> data) throws RenderException {
        validate(inputSchema, data, "input", "/");
        Object result = convertSelf(template, new SelfObject(this, data));
        validate(outputSchema, result, "output", "/");
        return result;
    }

    @Override
    public Object render(Object template, Map<String, Object> data, RenderOptions options) throws RenderException {
        if (options == null) {
            return render(template, data);
        }
        // 请求级 trace 订阅: 渲染结束后立即反注册, 引擎零残留
        RenderEngine.TraceHandle handle = options.traceConsumer() == null
                ? null : addTraceConsumer(options.traceConsumer());
        try {
            if (options.timeoutMs() <= 0) {
                return render(template, data);
            }
            // 限时渲染: 内部渲染跑在 worker 线程 (JDK 21+ 虚拟线程 / 17 共享 daemon 池,
            // 见 startTimeoutWorker), 到点 future.cancel(true) 中断 worker 并抛 RenderException
            FutureTask<Object> future = new FutureTask<>(() -> render(template, data));
            startTimeoutWorker(future);
            try {
                return future.get(options.timeoutMs(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RenderException("/", "render",
                        "render timed out after " + options.timeoutMs() + "ms");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof RenderException re) throw re;
                if (cause instanceof RuntimeException rt) throw rt;
                if (cause instanceof Error err) throw err;
                throw new RenderException("/", "render", "render failed: " + cause.getMessage(), cause);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RenderException("/", "render", "render interrupted", e);
            }
        } finally {
            if (handle != null) handle.remove();
        }
    }

    // ------------------------------------------------------------------
    // 限时渲染 worker 线程策略 (round2 §P3: 替换原来每调用一次 new Thread 的固定平台线程)
    // ------------------------------------------------------------------

    /** 运行时 JDK feature 版本 (决定限时渲染 worker 用虚拟线程还是共享 daemon 池), 静态解析一次。 */
    private static final int JDK_FEATURE = Runtime.version().feature();

    /** 限时渲染 worker 名前缀: 两种策略保持同一命名格式, 日志/诊断口径一致。 */
    private static final String TIMEOUT_WORKER_NAME_PREFIX = "template4j-render-timeout-";

    /**
     * 为限时渲染 ({@code timeoutMs > 0}) 启动 worker 线程执行内部渲染。
     * 线程策略按运行时 JDK 自适应:
     * <ul>
     *   <li>JDK &ge; 21 &rarr; 每次一个虚拟线程 (短阻塞任务的专用形态, 无平台线程创建/栈内存开销);</li>
     *   <li>JDK 17 &rarr; JVM 级共享 daemon 缓存线程池 (见 {@link LegacyTimeoutWorker})。</li>
     * </ul>
     *
     * <p>对调用方的可观察语义与旧实现 (每调用 {@code new Thread(future, ...)}) 完全一致:
     * 照旧 {@code future.get(timeout)} / {@code future.cancel(true)}, 中断落在真正执行
     * {@code future.run()} 的线程上。
     *
     * <p><b>JDK 21-only API 的隔离依据</b>: {@code Thread.ofVirtual()} 及相关符号在 JDK 17
     * 的类库与 javac 里根本不存在 (本仓库 source/target 17, 17 工具链下字面量调用连编译都
     * 过不了, {@code --release 17} 同样拒绝)。因此 21 分支的全部 21-only 调用收敛在
     * {@link V21TimeoutWorker} 单独嵌套类里、以反射完成, {@code Method} 句柄在该类初始化时
     * 一次解析。JVM 类懒加载保证: JDK 17 运行时走 else 分支, {@code V21TimeoutWorker}
     * 永不被加载/初始化, 反射解析 (以及任何对 21-only 符号的触碰) 都不会发生; 若未来在
     * 21+ 工具链下把反射改回字面量调用, 该依据同样成立 —— 不加载则不校验, HotSpot 分拆
     * 校验器对方法体内未解析类型的可赋值性检查也推迟到指令执行时。
     */
    private static void startTimeoutWorker(FutureTask<Object> future) {
        if (JDK_FEATURE >= 21) {
            V21TimeoutWorker.start(future);
        } else {
            LegacyTimeoutWorker.start(future);
        }
    }

    /**
     * JDK &ge; 21 路径: 每次限时渲染一个虚拟线程, 即
     * {@code Thread.ofVirtual().name(prefix, n).unstarted(future).start()} 的反射形态
     * (21-only 符号在 17 的 javac 下不存在, 只能反射; 另见 {@link #startTimeoutWorker} 的隔离依据)。
     *
     * <p>本类只在 {@code Runtime.version().feature() >= 21} 时被加载/初始化, 反射解析在 17 上
     * 绝不发生。每次调用新建一个 builder —— builder 非线程安全, 不跨线程复用; 3 次反射调用
     * 合计微秒级, 远低于旧实现平台线程创建的 0.1-0.5ms。解析/调用失败 (21+ 上不应发生) 时
     * 回退共享 daemon 池, 限时渲染本身绝不因线程策略失败。
     */
    private static final class V21TimeoutWorker {
        private static final AtomicInteger SEQ = new AtomicInteger();
        private static final Method OF_VIRTUAL = find(Thread.class, "ofVirtual");
        private static final Method NAME = find(
                forNameOrNull("java.lang.Thread$Builder"), "name", String.class, long.class);
        private static final Method UNSTARTED = find(
                forNameOrNull("java.lang.Thread$Builder"), "unstarted", Runnable.class);

        private V21TimeoutWorker() { }

        private static Class<?> forNameOrNull(String className) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e) {
                return null;
            }
        }

        private static Method find(Class<?> declaring, String name, Class<?>... params) {
            if (declaring == null) return null;
            try {
                return declaring.getMethod(name, params);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        static void start(FutureTask<Object> future) {
            if (UNSTARTED == null) {
                LegacyTimeoutWorker.start(future);
                return;
            }
            try {
                Object builder = NAME.invoke(OF_VIRTUAL.invoke(null),
                        TIMEOUT_WORKER_NAME_PREFIX, (long) SEQ.getAndIncrement());
                // FutureTask 是 Runnable; unstarted 产物 start() 由本线程触发, 与旧版 worker.start() 同点
                Thread worker = (Thread) UNSTARTED.invoke(builder, future);
                worker.start();
            } catch (Throwable failure) {
                LegacyTimeoutWorker.start(future);
            }
        }
    }

    /**
     * JDK 17 回退路径: JVM 级共享 daemon 缓存线程池 (cached, 空闲线程默认 60s 后自行终止)。
     *
     * <p>static final 直接初始化; 本嵌套类只在 17 分支首次命中时加载 —— 类加载语义本身就是
     * 懒初始化, 无需额外 holder。池为 JVM 级共享、<b>引擎不负责关闭</b>: daemon 线程不阻塞
     * JVM 退出; 空闲线程按 keep-alive 消亡后, 对容器热部署场景的 classloader 钉住效应也随之
     * 释放。代价记录: 与引擎实例无关, 多引擎共享同一池;
     * {@code future.cancel(true)} 打断的仍是正在执行 {@code future.run()} 的 worker 线程,
     * 与旧独占线程版语义一致。
     */
    private static final class LegacyTimeoutWorker {
        private static final AtomicInteger SEQ = new AtomicInteger();
        private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, TIMEOUT_WORKER_NAME_PREFIX + SEQ.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });

        private LegacyTimeoutWorker() { }

        static void start(FutureTask<Object> future) {
            // future.run() 后清掉本线程可能残留的中断标志: cancel(true) 若打断的是渲染中的
            // 非阻塞段, 标志会留在池线程上并被"下一个复用此线程的任务"继承 (旧独占线程版随
            // 线程消亡自然消失, 共享池必须显式清理), 否则后续渲染里任何 park/awaitDone 类
            // 等待会被陈旧标志误伤。对调用方可见语义零影响 (标志只存在于 worker 线程上)。
            POOL.execute(() -> {
                try {
                    future.run();
                } finally {
                    Thread.interrupted(); // 只清标志, 不抛异常
                }
            });
        }
    }

    @Override
    public Template compile(Object template) throws RenderException {
        // 触发一次预渲染以填充 parseStrCache, 同时验证模板语法。
        // 即便忽略此 warmup, 后续 render 也会自动命中缓存。
        try {
            convertSelf(template, new SelfObject(this, new LinkedHashMap<>()));
        } catch (RenderException ignored) {
            // 预渲染可能因数据缺失而失败, 不影响 compile 句柄的可用性 (实际 render 时再抛)
        }
        return new CompiledTemplate(this, template);
    }

    /** 编译后的模板句柄: 线程安全, 可复用。 */
    private static final class CompiledTemplate implements Template {
        private final JsonRenderer engine;
        private final Object template;
        CompiledTemplate(JsonRenderer engine, Object template) {
            this.engine = engine;
            this.template = template;
        }
        @Override
        public Object render(Map<String, Object> data) throws RenderException {
            return engine.render(template, data);
        }
    }

    @Override
    public Object convertSelf(Object value, SelfObject self) throws RenderException {

        if (value == null) {
            return null;
        }

        if (value instanceof String strValue) {
            return renderString(strValue, self);
        }
        if (value instanceof Map) {
            return renderMap(value, self);
        }
        if (value instanceof Collection arrayValue) {
            return renderCollection(arrayValue, self);
        }
        return value;
    }

    private Object renderString(String strValue, SelfObject self) throws RenderException {
        // 命中缓存: 复用 tokenized string + expressionMap, 避免重做 6 次 StringSubstitutor 扫描
        ParsedStrTemplate parsed = parseCache.getIfPresent(strValue);
        if (parsed == null) {
            Map<String, String> exprMap = new LinkedHashMap<>();
            String tokenized = TemplateUtil.parseStrTemplate(strValue, exprMap);
            parsed = new ParsedStrTemplate(tokenized, exprMap);
            parseCache.put(strValue, parsed);
        }

        String strResult = parsed.tokenized;
        Map<String, String> expressionMap = parsed.expressionMap;

        List<String> keyList = new ArrayList<>(expressionMap.keySet());

        String trimmed = strResult.trim();
        boolean jsonShape =
                (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"));

        Object finalResult = null;
        boolean singleReturn = false;
        for (int i = keyList.size() - 1; i >= 0; i--) {
            String key = keyList.get(i);
            String expr = expressionMap.get(key);
            Object ret = evaluator.evaluate(expr, self, "%");

            if (keyList.size() == 1 && strResult.trim().equals(key)) {
                finalResult = ret;
                singleReturn = true;
                break;
            }

            if (ret instanceof JSONObject || ret instanceof JSONArray) {
                strResult = strResult.replace(key, JSON.toJSONString(ret));
            } else if (ret == null) {
                String quotedKey = "\"" + key + "\"";
                if (jsonShape && strResult.contains(quotedKey)) {
                    strResult = strResult.replace(quotedKey, "null");
                } else {
                    strResult = strResult.replace(key, "");
                }
            } else {
                strResult = strResult.replace(key, String.valueOf(ret));
            }
        }

        Object result = singleReturn ? finalResult : strResult;
        if (traceEnabled()) {
            trace(RenderEngine.TraceEvent.of("RENDER_STRING", self.path(),
                    "placeholders=" + keyList.size(), 0, result));
        }
        return result;
    }

    private Object renderMap(Object value, SelfObject self) throws RenderException {
        JSONObject objectValue = new JSONObject((Map) value);

        objectValue = TemplateUtil.convertKey(objectValue);

        Object enterProgram = objectValue.remove("$enter{}");
        Object returnProgram = objectValue.remove("$return{}");

        objectValue = TemplateUtil.convertVars(objectValue, self);
        objectValue = TemplateUtil.escapeKey(objectValue);
        TemplateUtil.simplify(objectValue);

        if (enterProgram != null) {
            self.putVar("_template", objectValue);
            String program = ScriptUtil.getProgramBesieged(enterProgram);
            evaluator.evaluate(program, self, "$enter{}");
            self.getVarMap().remove("_template");
        }

        Object result;
        if (objectValue.containsKey("@class")) {
            String componentName = objectValue.getString("@class");
            RenderComponent component = components.find(componentName);
            if (component == null) {
                throw new RenderException(self.path(), "@class:" + componentName, "can not find component:" + componentName);
            }
            JSONObject input = UJson.copy(objectValue);
            input.remove("@class");
            // 组件计时门: 无 consumer 时跳过 t0 / finally 计时, 零分配零 nanoTime
            boolean traceComponent = traceEnabled();
            long t0 = traceComponent ? System.nanoTime() : 0L;
            Object componentResult;
            try {
                componentResult = component.doRender(input, self);
            } finally {
                if (traceComponent) {
                    long durMs = (System.nanoTime() - t0) / 1_000_000;
                    trace(RenderEngine.TraceEvent.of("COMPONENT", self.path(),
                            "@class=" + componentName, durMs, null));
                }
            }
            result = componentResult;
        } else {
            JSONObject jsonTarget = new JSONObject();
            for (String key : objectValue.keySet()) {
                Object itemValue = convertSelf(objectValue.get(key), new SelfObject(self, key));
                if ("*".equals(key)) {
                    jsonTarget.putAll(TemplateUtil.mergeAllMap(itemValue));
                } else {
                    jsonTarget.put(key, itemValue);
                }
            }
            result = jsonTarget;
        }

        if (returnProgram != null) {
            self.putVar("_result", result);
            String program = ScriptUtil.getProgramBesieged(returnProgram);
            evaluator.evaluate(program, self, "$return{}");
            result = self.getVarMap().remove("_result");
        }

        return result;
    }

    private Object renderCollection(Collection arrayValue, SelfObject self) throws RenderException {
        JSONArray target = new JSONArray();
        int index = 0;
        for (Object v : arrayValue) {
            Object v1 = convertSelf(v, new SelfObject(self, "[" + index++ + "]"));
            if ((v1 instanceof Map) && ((Map) v1).containsKey("*")) {
                v1 = ((Map) v1).get("*");
            }
            if (!(v instanceof List) && v1 instanceof List) {
                target.addAll((List) v1);
            } else {
                target.add(v1);
            }
        }
        return target;
    }


    protected Object expressionValue(String expression, SelfObject self, String name) throws RenderException {
        return evaluator.evaluate(expression, self, name);
    }


    @Override
    public Object renderUrl(String resourceUrl, String resourceType, Map<String, Object> data) throws RenderException {
        Object templateObj;
        try {
            templateObj = LoaderUtil.loadTemplate(templateLoader, resourceUrl, resourceType);
        } catch (Exception e) {
            throw new RenderException("/", "{template}", "load template fail", e);
        }
        SelfObject rootSelf = new SelfObject(this, data);
        rootSelf.setResourceUrl(resourceUrl);
        return convertSelf(templateObj, rootSelf);
    }
}
