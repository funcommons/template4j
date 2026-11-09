package fun.commons.template4j.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonValidatorTest {

    @Test
    public void isJsonObject_acceptsObjectLiteral() {
        assertTrue(JsonValidator.isJsonObject("{\"k\":\"v\"}"));
        assertTrue(JsonValidator.isJsonObject("{}"));
    }

    @Test
    public void isJsonObject_rejectsArraysAndScalarsAndBlank() {
        assertFalse(JsonValidator.isJsonObject("[1,2,3]"));
        assertFalse(JsonValidator.isJsonObject("42"));
        assertFalse(JsonValidator.isJsonObject("\"just a string\""));
        assertFalse(JsonValidator.isJsonObject(""));
        assertFalse(JsonValidator.isJsonObject(null));
        assertFalse(JsonValidator.isJsonObject("   "));
    }

    @Test
    public void isJsonObject_rejectsMalformedJson() {
        assertFalse(JsonValidator.isJsonObject("{unquoted: value}"));
        assertFalse(JsonValidator.isJsonObject("{\"missing-close\":"));
    }

    @Test
    public void isJsonArray_acceptsArraysOnly() {
        assertTrue(JsonValidator.isJsonArray("[]"));
        assertTrue(JsonValidator.isJsonArray("[1,2,3]"));
        assertFalse(JsonValidator.isJsonArray("{\"k\":1}"));
        assertFalse(JsonValidator.isJsonArray(""));
        assertFalse(JsonValidator.isJsonArray(null));
    }

    @Test
    public void isJson_acceptsObjectOrArrayOnly() {
        // JsonValidator.isJson is "is object OR is array" — scalars are NOT
        // accepted (the helper exists to gate JSON-template parsing, where
        // root must be a container).
        assertTrue(JsonValidator.isJson("{}"));
        assertTrue(JsonValidator.isJson("[]"));
        assertFalse(JsonValidator.isJson("\"str\""));
        assertFalse(JsonValidator.isJson("42"));
        assertFalse(JsonValidator.isJson("null"));
        assertFalse(JsonValidator.isJson(""));
        assertFalse(JsonValidator.isJson(null));
        assertFalse(JsonValidator.isJson("{broken"));
    }
}
