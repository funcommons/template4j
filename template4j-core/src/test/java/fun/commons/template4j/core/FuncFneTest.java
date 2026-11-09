package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.*;

/**
 * 覆盖 {@link FuncFne}：第一个非空值
 */
public class FuncFneTest {

    @Test
    public void testFirstNonNullScalar() throws Exception {
        Assertions.assertEquals(RenderEngine.build().render("{% fne(null,'a','b') %}", new JSONObject()), "a");
    }

    @Test
    public void testBlankStringSkipped() throws Exception {
        Assertions.assertEquals(RenderEngine.build().render("{% fne('','a','b') %}", new JSONObject()), "a");
        Assertions.assertEquals(RenderEngine.build().render("{% fne('   ','a','b') %}", new JSONObject()), "a");
        Assertions.assertEquals(RenderEngine.build().render("{% fne('\\t\\n','a') %}", new JSONObject()), "a");
    }

    @Test
    public void testEmptyCollectionSkipped() throws Exception {
        Assertions.assertEquals(RenderEngine.build().render("{% fne([],'a','b') %}", new JSONObject()), "a");
        Assertions.assertEquals(RenderEngine.build().render("{% fne(new HashMap<>(),'a','b') %}", new JSONObject()), "a");
        Assertions.assertEquals(RenderEngine.build().render("{% fne(new ArrayList<>(),'a') %}", new JSONObject()), "a");
    }

    @Test
    public void testNonEmptyCollectionReturns() throws Exception {
        Object r = RenderEngine.build().render("{% fne(null, [], new HashMap<>(), [1,2], 'x') %}", new JSONObject());
        Assertions.assertTrue(r instanceof List);
        Assertions.assertEquals(2, ((List<?>) r).size());
    }

    @Test
    public void testNonEmptyMapReturns() throws Exception {
        // 嵌套 Map 字段非空时，应作为整体返回
        Map<String, Object> data = new HashMap<>();
        data.put("m", new HashMap<>(Map.of("a", 1)));
        data.put("x", "fallback");
        Object r = RenderEngine.build().render("{% return fne(@m, @x) %}", data);
        Assertions.assertTrue(r instanceof Map);
        Assertions.assertEquals(1, ((Map<?, ?>) r).get("a"));
    }

    @Test
    public void testAllEmptyReturnsNull() throws Exception {
        Assertions.assertNull(RenderEngine.build().render("{% fne(null, '', [], new HashMap<>()) %}", new JSONObject()));
    }

    @Test
    public void testEmptyArgsReturnsNull() throws Exception {
        Assertions.assertNull(RenderEngine.build().render("{% fne() %}", new JSONObject()));
    }

    @Test
    public void testNullArgsReturnsNull() throws Exception {
        Assertions.assertNull(RenderEngine.build().render("{% def x=null; return fne(x) %}", new JSONObject()));
    }

    @Test
    public void testMixedTypes() throws Exception {
        Assertions.assertEquals(0,
                ((Number) RenderEngine.build().render("{% fne(null, '', [], new HashMap<>(), 0) %}", new JSONObject())).intValue());
    }

    @Test
    public void testCharSequenceSubclass() throws Exception {
        Assertions.assertEquals(RenderEngine.build().render("{% fne(new StringBuilder(''), 'a') %}", new JSONObject()), "a");
    }

    @Test
    public void testFneWithJsonPath() throws Exception {
        // 文档示例：fne(@'result.code', @'result.errno', @'result.status', 0)
        Map<String, Object> data = new JSONObject(Map.of("result", Map.of("status", 200)));
        Assertions.assertEquals(200, ((Number) RenderEngine.build().render(
                "{% fne(@'result.code',@'result.errno',@'result.status', 0) %}", data)).intValue());
    }
}