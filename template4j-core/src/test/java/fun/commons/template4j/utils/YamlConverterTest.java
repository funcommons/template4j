package fun.commons.template4j.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class YamlConverterTest {

    @Test
    public void jsonObjectFromYaml_parsesMapping() throws IOException {
        JSONObject o = YamlConverter.jsonObjectFromYaml("name: Alice\nage: 30\n");
        assertEquals(o.getString("name"), "Alice");
        assertEquals(30, o.getIntValue("age"));
    }

    @Test
    public void jsonArrayFromYaml_parsesSequence() throws IOException {
        JSONArray a = YamlConverter.jsonArrayFromYaml("- a\n- b\n- c\n");
        assertEquals(3, a.size());
        assertEquals(a.getString(0), "a");
    }

    @Test
    public void jsonObjectFromYaml_handlesNestedStructures() throws IOException {
        JSONObject o = YamlConverter.jsonObjectFromYaml(
            "user:\n  name: Alice\n  tags:\n    - admin\n    - user\n");
        assertEquals(o.getJSONObject("user").getString("name"), "Alice");
        assertEquals(2, o.getJSONObject("user").getJSONArray("tags").size());
    }

    @Test
    public void toYAMLString_roundTripsBasicObject() throws IOException {
        JSONObject o = JSONObject.of("k1", "v1", "k2", 42);
        String yaml = YamlConverter.toYAMLString(o);
        // round-trip back
        Object parsed = YamlConverter.fromYaml(yaml);
        assertTrue(parsed instanceof JSONObject);
        assertEquals(((JSONObject) parsed).getString("k1"), "v1");
        assertEquals(42, ((JSONObject) parsed).getIntValue("k2"));
    }

    @Test
    public void toJsonObjectSafe_returnsNullForBlankInput() {
        // blank / empty inputs are tolerated and return null
        assertNull(YamlConverter.toJsonObjectSafe(""));
        assertNull(YamlConverter.toJsonObjectSafe("   "));
        // null input throws IllegalArgumentException in the underlying YAML
        // parser — that's a contract of the impl, not a bug, so don't test it here.
    }

    @Test
    public void toJsonObjectSafe_returnsParsedForValidInput() {
        Object parsed = YamlConverter.toJsonObjectSafe("k: v");
        // depending on shape, this can be JSONObject or scalar
        assertTrue(parsed instanceof JSONObject);
    }

    @Test
    public void fromYaml_handlesTabFreeIndentedMapping() throws IOException {
        Object parsed = YamlConverter.fromYaml("a:\n  b: 1\n  c: 2\n");
        assertTrue(parsed instanceof JSONObject);
        JSONObject a = ((JSONObject) parsed).getJSONObject("a");
        assertEquals(1, a.getIntValue("b"));
        assertEquals(2, a.getIntValue("c"));
    }
}
