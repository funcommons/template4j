package fun.commons.template4j.core.components;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import fun.commons.template4j.core.RenderEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3 注入语义钉死测试 (round2 §S3): <b>rest 组件的响应数据是"数据", 不是"模板"</b>。
 *
 * <p>上游响应体经 {@code UJson.smartCast} 解析后, 以防御性 copy / 原样引用的形式作为
 * {@code responseData} 变量注入 success / error 模板渲染。本测试用 loopback HttpServer
 * 返回<b>恶意形态的响应体</b> (携带 {@code @class} / {@code $enter{}} / {@code $return{}} /
 * {@code {$item}} 等模板语义键), 钉死这些键在 rest 组件自身的所有渲染路径中
 * <b>永不被执行</b> (不被当作组件调用 / 脚本):
 * <ul>
 *   <li>无 success 块的直返路径 (响应对象原样作为组件结果)</li>
 *   <li>success 块单占位直返 {@code {$responseData}} (singleReturn 返回原对象)</li>
 *   <li>字符串拼接 (JSON 序列化后是纯文本, 不再求值)</li>
 *   <li>嵌套字段引用 {@code {$responseData.a.b}}</li>
 *   <li>error 分支 (status/message 为引擎自构, message 是攻击者可控文本但只是字符串)</li>
 *   <li>响应缓存命中路径 (defensiveCopy 之后语义不变)</li>
 *   <li>{@code *} 合并键 (数据侧条目 verbatim 并入父级, 不再过 renderMap)</li>
 *   <li>{@code each} 迭代响应数据 (body 是模板, item 值是数据)</li>
 *   <li>Groovy {@code {% return vars.responseData %}} 直接返回原对象</li>
 * </ul>
 *
 * <p><b>唯一的边界 (设计语义, 非漏洞)</b>: 模板作者<b>显式</b>使用 {@code @class: render}
 * 组件把数据提升为模板重新渲染 —— 见 {@link #renderComponent_explicitLateBindingExecutesByDesign()}。
 * 若其余任何用例出现"响应数据被执行"的迹象 (探针翻转 / 注入端点被调用), 即为安全回归。
 *
 * <p>执行探测手段: 恶意 payload 内嵌指向 mock {@code /pwned} 端点的 rest 组件调用
 * ({@code injectionHits} 计数) 与 Groovy {@code probe.hit()} 探针 bean (由
 * {@code engine.withBean} 注册)。surefire 注入的
 * {@code template4j.rest.allowed-hosts=127.0.0.1,localhost,0.0.0.0} 放行 loopback,
 * 本测试继承该属性、<b>不得 clear</b> —— 这样 {@code injectionHits==0} 证明的是
 * "组件未被执行" 而非 "被 SSRF 守卫拦截"。
 */
public class RestResponseInjectionTest {

    // ===== 恶意响应体样例 =====

    /**
     * 样例 A: 组件注入形态 ({@code @class: rest})。若被执行会向 mock 的 /pwned 端点
     * 再次发起 HTTP 调用 —— injectionHits 计数即执行证据。
     */
    private String restComponentPayload() {
        return "{\"@class\":\"rest\",\"url\":\"http://127.0.0.1:" + port + "/pwned\",\"method\":\"GET\"}";
    }

    /**
     * 样例 B: {@code @class: each} 组件注入形态。若被执行会渲染 body,
     * 产出 {@code [{"n":1},{"n":2},{"n":3}]}; 原样数据则保留 select 原始数组与 body 原始模板文本。
     */
    private static final String EACH_PAYLOAD =
            "{\"@class\":\"each\",\"select\":[1,2,3],\"body\":{\"n\":\"{$item}\"}}";

    /**
     * 样例 C: {@code $enter{}} / {@code $return{}} 脚本注入形态。若被执行会翻转 probe 探针
     * (probe 是真实注册的 bean —— 一旦执行必然成功翻转, 不存在"执行报错所以没执行"的误判)。
     */
    private static final String ENTER_RETURN_PAYLOAD =
            "{\"a\":{\"$enter{}\":\"probe.hit()\"},\"$return{}\":\"probe.hit()\"}";

    /** /pwned 端点响应体: 只有用例 7 (设计语义) 应该拿到它。 */
    private static final String PWNED_BODY = "{\"pwned\":true,\"executed\":true}";

    // ===== mock server fixture =====

    private HttpServer server;
    private int port;
    private final InjectionProbe probe = new InjectionProbe();
    /** 合法的外层 rest 调用 (指向 /data) 的命中数。 */
    private final AtomicInteger outerHits = new AtomicInteger();
    /** 恶意 payload 内嵌的 /pwned 调用命中数 — 除用例 7 外必须恒为 0。 */
    private final AtomicInteger injectionHits = new AtomicInteger();
    private volatile String outerBody = "{}";
    private volatile int outerStatus = 200;

    /** Groovy 可调用的执行探针: 脚本 payload 里 {@code probe.hit()} 一旦执行即翻转。 */
    public static final class InjectionProbe {
        private final AtomicInteger hits = new AtomicInteger();

        public void hit() {
            hits.incrementAndGet();
        }

        public int hits() {
            return hits.get();
        }
    }

    @BeforeEach
    void startMockServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        // 注意: 不 clear template4j.rest.allowed-hosts —— 继承 surefire 注入值 (见类注释)
        server.createContext("/pwned", ex -> {
            injectionHits.incrementAndGet();
            writeResponse(ex, PWNED_BODY, 200);
        });
        server.createContext("/", ex -> {
            outerHits.incrementAndGet();
            writeResponse(ex, outerBody, outerStatus);
        });
        server.start();
    }

    @AfterEach
    void stopMockServer() {
        if (server != null) server.stop(0);
    }

    private static void writeResponse(HttpExchange ex, String body, int status) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private RenderEngine newEngine() throws Exception {
        return RenderEngine.build().withBean("probe", probe);
    }

    private String outerUrl() {
        return "http://127.0.0.1:" + port + "/data";
    }

    // ===== 1. 无 success 块: 响应对象原样直返 =====

    @Test
    void noSuccessBlock_responseObjectReturnedAsRawData() throws Exception {
        outerBody = ENTER_RETURN_PAYLOAD;
        Object out = newEngine().renderJson(
                "{\"@class\":\"rest\",\"url\":\"" + outerUrl() + "\"}", Map.of());

        JSONObject result = assertInstanceOf(JSONObject.class, out);
        // $enter{} / $return{} 键作为数据键 verbatim 保留在结果里
        assertEquals("probe.hit()", result.getJSONObject("a").getString("$enter{}"));
        assertEquals("probe.hit()", result.getString("$return{}"));
        assertEquals(0, probe.hits(), "$enter{}/$return{} 键不得被执行");
    }

    // ===== 2. success 单占位直返: 原样对象, 组件不触发 =====

    @Test
    void singlePlaceholder_returnsResponseDataVerbatimWithoutExecuting() throws Exception {
        outerBody = restComponentPayload();
        JSONObject out = assertInstanceOf(JSONObject.class, newEngine().renderJson("""
                {"@class":"rest","url":"%s","success":{"r":"{$responseData}"}}
                """.formatted(outerUrl()), Map.of()));

        JSONObject r = out.getJSONObject("r");
        assertEquals("rest", r.getString("@class"), "@class 键必须原样存在");
        assertEquals("http://127.0.0.1:" + port + "/pwned", r.getString("url"));
        assertEquals("GET", r.getString("method"));
        assertEquals(3, r.size(), "响应数据键无增删");
        assertEquals(1, outerHits.get(), "外层 rest 调用恰好一次");
        assertEquals(0, injectionHits.get(), "注入的 rest 组件不得被执行 (端点零调用)");
    }

    // ===== 3. 字符串拼接: JSON 序列化为纯文本, 不再求值 =====

    @Test
    void stringConcat_responseDataSerializedAsPlainTextNotReevaluated() throws Exception {
        outerBody = "{\"status\":" + EACH_PAYLOAD + ",\"url\":\"http://127.0.0.1:" + port + "/pwned\"}";
        JSONObject out = assertInstanceOf(JSONObject.class, newEngine().renderJson("""
                {"@class":"rest","url":"%s","success":{
                   "rText":"code={$responseData.url}",
                   "rJson":"json={$responseData.status}"}}
                """.formatted(outerUrl()), Map.of()));

        // 字符串值: 纯拼接
        assertEquals("code=http://127.0.0.1:" + port + "/pwned", out.getString("rText"));
        // 对象值: 以 JSON 序列化文本形式拼接 (键序无关, 回读比对内容)
        String rJson = out.getString("rJson");
        assertTrue(rJson.startsWith("json={"), "对象占位符应被 JSON 序列化拼接, 实际: " + rJson);
        assertEquals(JSON.parseObject(EACH_PAYLOAD), JSON.parseObject(rJson.substring("json=".length())),
                "拼接文本必须是响应数据的 JSON 序列化, 内容原样");
        // 若 each 组件被执行, 会出现 [{"n":1},...] 形态; 原样数据保留 body 模板文本
        assertFalse(rJson.contains("\"n\":1"), "each 组件不得被执行");
        assertTrue(rJson.contains("{$item}"), "body 的模板文本应作为数据原样出现");
        assertEquals(0, injectionHits.get());
        assertEquals(0, probe.hits());
    }

    // ===== 4. 嵌套字段引用: 原样值 =====

    @Test
    void nestedFieldReference_returnsRawValueVerbatim() throws Exception {
        outerBody = "{\"a\":{\"b\":" + EACH_PAYLOAD + "}}";
        JSONObject out = assertInstanceOf(JSONObject.class, newEngine().renderJson("""
                {"@class":"rest","url":"%s","success":{"r":"{$responseData.a.b}"}}
                """.formatted(outerUrl()), Map.of()));

        JSONObject r = out.getJSONObject("r");
        assertEquals("each", r.getString("@class"), "嵌套引用返回原样对象, 不触发组件");
        assertEquals(3, r.getJSONArray("select").size(), "select 仍是原始数据 [1,2,3]");
        assertEquals("{$item}", r.getJSONObject("body").getString("n"), "body 仍是原始模板文本");
        assertEquals(0, injectionHits.get());
    }

    // ===== 5. success 分支: $enter{}/$return{} 恶意键保持惰性 =====

    @Test
    void successBlock_enterReturnKeysInResponseStayInert() throws Exception {
        outerBody = ENTER_RETURN_PAYLOAD;
        JSONObject out = assertInstanceOf(JSONObject.class, newEngine().renderJson("""
                {"@class":"rest","url":"%s","success":{"raw":"{$responseData}","keep":1}}
                """.formatted(outerUrl()), Map.of()));

        JSONObject raw = out.getJSONObject("raw");
        assertEquals("probe.hit()", raw.getJSONObject("a").getString("$enter{}"));
        assertEquals("probe.hit()", raw.getString("$return{}"));
        assertEquals(1, out.getIntValue("keep"));
        assertEquals(0, probe.hits(), "success 分支注入的 $enter{}/$return{} 不得被执行");
    }

    // ===== 6. error 分支: 引擎自构 responseData, message 攻击者可控但只是字符串 =====

    @Test
    void errorBranch_engineBuiltResponseDataIsInert() throws Exception {
        outerStatus = 500;
        outerBody = EACH_PAYLOAD; // 500 响应体文本原样成为 responseData.message
        JSONObject out = assertInstanceOf(JSONObject.class, newEngine().renderJson("""
                {"@class":"rest","url":"%s","error":{
                   "status":"{$responseData.status}",
                   "rawMsg":"{$responseData.message}",
                   "concatMsg":"upstream={$responseData.message}"}}
                """.formatted(outerUrl()), Map.of()));

        assertEquals(500, out.getIntValue("status"), "status 是引擎自构的整数");
        assertEquals(EACH_PAYLOAD, out.getString("rawMsg"),
                "message 是原样字符串 — 未解析、未执行");
        assertEquals("upstream=" + EACH_PAYLOAD, out.getString("concatMsg"),
                "拼接路径同样只是纯文本");
        assertEquals(0, injectionHits.get());
        assertEquals(0, probe.hits());
    }

    // ===== 7. 设计语义边界: render 组件显式 late-binding 会执行 (非漏洞, 文档用例) =====

    /**
     * <b>设计语义, 非漏洞</b>: {@code @class: render} 组件的契约就是"显式 late-binding" ——
     * 模板作者主动把 select 的求值结果<b>当作模板</b>再次渲染。这条路径与 rest 响应数据的
     * <b>隐式</b>注入不同: 上游响应永远不会在 rest 组件内部被这样二次渲染 (其余用例已钉死)。
     * 换言之, "数据 → 模板" 只能经由模板作者显式的 render 组件发生; 把攻击者可控数据喂给
     * render 组件等同于作者亲手执行它, 属于模板作者的责任边界, 不算 rest 组件的注入漏洞。
     * 本用例把这条边界写进测试当文档。
     */
    @Test
    void renderComponent_explicitLateBindingExecutesByDesign() throws Exception {
        Map<String, Object> data = Map.of("resp", JSON.parseObject(restComponentPayload()));
        Object out = newEngine().renderJson(
                "{\"@class\":\"render\",\"select\":\"{@resp}\"}", data);

        // 内嵌 rest 组件被执行 → /pwned 端点恰好被调用一次, 并拿到其响应
        assertEquals(1, injectionHits.get(), "render 显式提升数据为模板 → 组件按设计执行");
        JSONObject result = assertInstanceOf(JSONObject.class, out);
        assertEquals(Boolean.TRUE, result.getBoolean("executed"));
        assertEquals(0, outerHits.get());
    }

    // ===== 8. Groovy vars 注入路径: 直接返回原对象 =====

    @Test
    void groovyScript_varsResponseData_returnedRaw() throws Exception {
        outerBody = EACH_PAYLOAD;
        JSONObject out = assertInstanceOf(JSONObject.class, newEngine().renderJson("""
                {"@class":"rest","url":"%s","success":{"r":"{%% return vars.responseData %%}"}}
                """.formatted(outerUrl()), Map.of()));

        JSONObject r = assertInstanceOf(JSONObject.class, out.get("r"),
                "脚本返回值原样透传 (singleReturn), 不再渲染");
        assertEquals("each", r.getString("@class"));
        assertEquals("{$item}", r.getJSONObject("body").getString("n"));
        assertEquals(0, injectionHits.get());
        assertEquals(0, probe.hits());
    }

    // ===== 9. each 迭代响应数据: body 是模板, item 值是数据 =====

    @Test
    void each_overResponseData_bodyIsTemplate_itemValueIsData() throws Exception {
        outerBody = "{\"items\":[" + EACH_PAYLOAD + "]}";
        JSONObject out = assertInstanceOf(JSONObject.class, newEngine().renderJson("""
                {"@class":"rest","url":"%s","success":{
                   "list":"{$responseData.items}",
                   "mapped":{"@class":"each","select":"{$responseData.items}","body":{"item":"{$item}"}}}}
                """.formatted(outerUrl()), Map.of()));

        // list: 原样数组
        JSONArray list = out.getJSONArray("list");
        assertEquals(1, list.size());
        assertEquals("each", list.getJSONObject(0).getString("@class"));
        // mapped: each 渲染的是模板侧 body; item 值作为数据 verbatim 塞入, 不触发组件
        JSONArray mapped = out.getJSONArray("mapped");
        assertEquals(1, mapped.size());
        JSONObject item = mapped.getJSONObject(0).getJSONObject("item");
        assertEquals("each", item.getString("@class"));
        assertEquals("{$item}", item.getJSONObject("body").getString("n"));
        assertEquals(0, injectionHits.get());
    }

    // ===== 10. * 合并键: 数据侧条目 verbatim 并入, 不再过 renderMap =====

    @Test
    void starMerge_responseDataEntriesMergedVerbatimWithoutExecution() throws Exception {
        outerBody = ENTER_RETURN_PAYLOAD;
        JSONObject out = assertInstanceOf(JSONObject.class, newEngine().renderJson("""
                {"@class":"rest","url":"%s","success":{"keep":"x","*":"{$responseData}"}}
                """.formatted(outerUrl()), Map.of()));

        assertEquals("x", out.getString("keep"));
        assertEquals("probe.hit()", out.getJSONObject("a").getString("$enter{}"),
                "$enter{} 条目合并后原样保留");
        assertEquals("probe.hit()", out.getString("$return{}"),
                "$return{} 条目合并后原样保留");
        assertEquals(3, out.size());
        assertEquals(0, probe.hits(), "合并进结果的数据键不得被当作模板键执行");
    }

    // ===== 11. 响应缓存命中路径: defensiveCopy 之后语义不变 =====

    @Test
    void cachedResponse_stillDataNotTemplate() throws Exception {
        outerBody = restComponentPayload();
        String template = """
                {"@class":"rest","url":"%s","cache":{"key":"s3-injection-audit-cache","ttl":60000},
                 "success":{"r":"{$responseData}"}}
                """.formatted(outerUrl());
        RenderEngine engine = newEngine();

        JSONObject first = assertInstanceOf(JSONObject.class, engine.renderJson(template, Map.of()));
        JSONObject second = assertInstanceOf(JSONObject.class, engine.renderJson(template, Map.of()));

        // 第二次来自缓存: 外层端点仍只被调用一次
        assertEquals(1, outerHits.get(), "第二次渲染应命中响应缓存");
        assertEquals(first, second, "缓存命中路径结果与首次一致");
        for (JSONObject out : new JSONObject[]{first, second}) {
            JSONObject r = out.getJSONObject("r");
            assertEquals("rest", r.getString("@class"), "defensiveCopy 保留 @class 原样键");
            assertEquals("http://127.0.0.1:" + port + "/pwned", r.getString("url"));
        }
        assertEquals(0, injectionHits.get(), "缓存路径注入的组件同样不得执行");
    }
}
