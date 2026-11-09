package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import fun.commons.template4j.utils.UJson;
import fun.commons.template4j.utils.UString;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class JsonRenderer implements RenderEngine, RenderEngine.Renderer {

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

    /** parseStrTemplate 结果缓存: 同一字符串模板多次渲染时复用 tokenized string + expressionMap。 */
    static final int MAX_PARSE_CACHE_ENTRIES = Integer.getInteger(
            "template4j.parseCache.maxEntries", 1024);
    private final Map<String, ParsedStrTemplate> parseCache = Collections.synchronizedMap(
            new LinkedHashMap<String, ParsedStrTemplate>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ParsedStrTemplate> eldest) {
                    return size() > MAX_PARSE_CACHE_ENTRIES;
                }
            });

    /** parseStrTemplate 不可变结果: tokenized string + expressionMap。 */
    private static final class ParsedStrTemplate {
        final String tokenized;
        final Map<String, String> expressionMap;
        ParsedStrTemplate(String tokenized, Map<String, String> expressionMap) {
            this.tokenized = tokenized;
            this.expressionMap = Collections.unmodifiableMap(expressionMap);
        }
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
    public RenderEngine withTraceConsumer(@NonNull Consumer<RenderEngine.TraceEvent> consumer) {
        traceConsumers.add(consumer);
        return this;
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
    public Object render(Object template, Map<String, Object> data) throws RenderException {
        validate(inputSchema, data, "input", "/");
        Object result = convertSelf(template, new SelfObject(this, data));
        validate(outputSchema, result, "output", "/");
        return result;
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
        ParsedStrTemplate parsed = parseCache.get(strValue);
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
        trace(RenderEngine.TraceEvent.of("RENDER_STRING", self.path(),
                "placeholders=" + keyList.size(), 0, result));
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
            long t0 = System.nanoTime();
            Object componentResult;
            try {
                componentResult = component.doRender(input, self);
            } finally {
                long durMs = (System.nanoTime() - t0) / 1_000_000;
                trace(RenderEngine.TraceEvent.of("COMPONENT", self.path(),
                        "@class=" + componentName, durMs, null));
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
