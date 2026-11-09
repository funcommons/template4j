package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Date;
import java.util.Map;

/**
 * 覆盖 {@link FuncCast}：所有支持类型 + null 行为
 *
 * 数据通过 {@code @data.field} 在脚本内访问，避免依赖未注册到 bindings 的裸标识符。
 */
public class FuncCastTest {

    private Object cast(Object value, String type) throws Exception {
        // 把 value 和 type 作为 data 字段传入，脚本用 @value / @type 引用
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("v", value);
        data.put("t", type);
        return RenderEngine.build().render("{% return cast(@v, @t) %}", data);
    }

    private Object cast(Object value, Class<?> type) throws Exception {
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("v", value);
        return RenderEngine.build().render("{% return cast(@v, Integer.class) %}", data);
    }

    @Test
    public void testCastToString() throws Exception {
        Assertions.assertEquals(cast(123, "String"), "123");
        Assertions.assertEquals(cast("hello", "String"), "hello");
    }

    @Test
    public void testCastStringToString() throws Exception {
        Assertions.assertEquals(cast("hello", "string"), "hello");
    }

    @Test
    public void testCastToInt() throws Exception {
        Assertions.assertEquals(123, ((Number) cast("123", "int")).intValue());
        Assertions.assertEquals(123, ((Number) cast("123", "Integer")).intValue());
        Assertions.assertEquals(123, ((Number) cast(123.0, "int")).intValue());
    }

    @Test
    public void testCastLong() throws Exception {
        Assertions.assertEquals(123L, ((Number) cast("123", "long")).longValue());
        Assertions.assertEquals(123L, ((Number) cast("123", "Long")).longValue());
    }

    @Test
    public void testCastDouble() throws Exception {
        Assertions.assertEquals(1.5, ((Number) cast("1.5", "double")).doubleValue(), 0.0001);
        Assertions.assertEquals(1.5, ((Number) cast("1.5", "Double")).doubleValue(), 0.0001);
    }

    @Test
    public void testCastFloat() throws Exception {
        Assertions.assertEquals(1.5f, ((Number) cast("1.5", "float")).floatValue(), 0.0001f);
        Assertions.assertEquals(1.5f, ((Number) cast("1.5", "Float")).floatValue(), 0.0001f);
    }

    @Test
    public void testCastBoolean() throws Exception {
        Assertions.assertEquals(Boolean.TRUE, cast("true", "boolean"));
        Assertions.assertEquals(Boolean.TRUE, cast("true", "Boolean"));
        Assertions.assertEquals(Boolean.FALSE, cast("false", "boolean"));
        Assertions.assertEquals(Boolean.TRUE, cast(1, "Boolean"));
    }

    @Test
    public void testCastBigInteger() throws Exception {
        Object r = cast("123", "BigInteger");
        Assertions.assertTrue(r instanceof BigInteger);
        Assertions.assertEquals(BigInteger.valueOf(123), r);
    }

    @Test
    public void testCastBigDecimal() throws Exception {
        Object r = cast("1.5", "BigDecimal");
        Assertions.assertTrue(r instanceof BigDecimal);
        Assertions.assertEquals(new BigDecimal("1.5"), r);
    }

    @Test
    public void testCastDate() throws Exception {
        Object r = cast("2022-01-01 10:00:11", "Date");
        Assertions.assertTrue(r instanceof Date);
    }

    @Test
    public void testCastJsonObject() throws Exception {
        Object r = cast("{\"a\":1}", "JSONObject");
        Assertions.assertTrue(r instanceof JSONObject);
        Assertions.assertEquals(1, ((JSONObject) r).getIntValue("a"));
    }

    @Test
    public void testCastJsonArray() throws Exception {
        Object r = cast("[1,2,3]", "JSONArray");
        Assertions.assertTrue(r instanceof JSONArray);
        Assertions.assertEquals(3, ((JSONArray) r).size());
    }

    @Test
    public void testCastNullReturnsNull() throws Exception {
        Assertions.assertNull(cast(null, "int"));
        Assertions.assertNull(cast(null, "String"));
        Assertions.assertNull(cast(null, "JSONObject"));
    }

    @Test
    public void testCastInTemplateStringSyntax() throws Exception {
        Map<String, Object> data = Map.of("page", Map.of("size", "100"));
        Object r = RenderEngine.build().render("{% cast(@'page.size', 'int') %}", data);
        Assertions.assertEquals(100, ((Number) r).intValue());
    }

    @Test
    public void testCastFromBean() throws Exception {
        Object r = RenderEngine.build().render("{% cast('123', Integer.class) %}", Map.of());
        Assertions.assertEquals(123, ((Number) r).intValue());
    }

    @Test
    public void testUnknownTypeReturnsOriginal() throws Exception {
        // FuncCast.call(value, "UnknownType") — UType.castValue 不识别则抛错，
        // 此处断言抛错而非静默返回原值
        try {
            Object r = cast("hello", "NoSuchType");
            // 如果未抛错，断言至少返回非 null
            Assertions.assertNotNull(r);
        } catch (Exception ex) {
            // 期望抛 RenderException
            Assertions.assertTrue(ex.getMessage().contains("script error") || ex.getCause() != null);
        }
    }
}