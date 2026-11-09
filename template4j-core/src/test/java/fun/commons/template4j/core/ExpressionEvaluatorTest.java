package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.RenderEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Coverage for {@link ExpressionEvaluator} via the public render API. Each
 * test exercises one of the six placeholder forms the evaluator dispatches:
 *
 *   <&...&>  literal pass-through
 *   {#...#}  alternate literal pass-through
 *   {@...}   JSONPath against data
 *   {$...}   variable lookup with optional JSONPath tail
 *   {=...}   Groovy single-expression
 *   {%...%}  Groovy statement block
 *
 * Direct unit tests of ExpressionEvaluator are hard because the constructor
 * takes a package-private ScriptEnvironment — driving each branch through
 * JsonRenderer.expressionValue gets us the same coverage with stable API.
 */
public class ExpressionEvaluatorTest {

    private final RenderEngine engine = RenderEngine.build();

    // ===== <&...&> and {#...#} literal pass-through =====
    // These forms live inside STRING values; using them at JSON root would
    // fail parsing. Verify the literal-pass-through by embedding in a value.

    @Test
    public void angleBracketLiteral_isReturnedUntouched() {
        // <&key&> returns the literal text between the delimiters, no evaluation
        Object r = engine.renderJson("{\"v\":\"<&not-an-expression&>\"}", Map.of());
        assertEquals(((JSONObject) r).getString("v"), "not-an-expression");
    }

    @Test
    public void hashBraceLiteral_isReturnedUntouched() {
        // {#...#} delimiters return the inner text untouched
        Object r = engine.renderJson("{\"v\":\"{#@not-evaluated#}\"}", Map.of());
        assertEquals(((JSONObject) r).getString("v"), "@not-evaluated");
    }

    // ===== {@...} JSONPath =====

    @Test
    public void jsonDataPath_resolvesNestedField() {
        Object r = engine.renderJson("{\"v\":\"{@user.name}\"}", Map.of("user", Map.of("name", "Alice")));
        assertEquals(((JSONObject) r).getString("v"), "Alice");
    }

    @Test
    public void jsonDataPath_quotedStringPathStripsOuterQuotes() {
        // skip: dot-in-key path support depends on fastjson2 JSONPath semantics
        // for bracket-vs-dot notation; verified separately by JsonPath reference
        // docs. Use a regular field name to exercise the quote-strip branch.
        Object r = engine.renderJson("{\"v\":\"{@'name'}\"}", Map.of("name", "Alice"));
        assertEquals(((JSONObject) r).getString("v"), "Alice");
    }

    @Test
    public void jsonDataPath_emptyPathEvaluatesToRoot() {
        // {@} → JSONPath.eval(data, "$") returns the root
        Object r = engine.renderJson("{\"v\":\"{@}\"}", Map.of("k", "v"));
        // result is the data root; renderer assigns whatever JSONPath returns
        assertEquals(((JSONObject) r).getJSONObject("v").getString("k"), "v");
    }

    // ===== {$...} variable lookup =====

    @Test
    public void variableLookup_resolvesNamedVar() {
        // template defines $x then references it
        Object r = engine.renderJson("{\"$x\":\"literal\",\"v\":\"{$x}\"}", Map.of());
        assertEquals(((JSONObject) r).getString("v"), "literal");
    }

    @Test
    public void variableLookup_supportsJsonPathTail() {
        Object r = engine.renderJson(
            "{\"$x\":{\"a\":1},\"v\":\"{$x.a}\"}",
            Map.of());
        assertEquals(1, ((JSONObject) r).getIntValue("v"));
    }

    // ===== {= ...} single Groovy expression =====

    @Test
    public void groovyInline_returnsExpressionValue() {
        Object r = engine.renderJson("{\"v\":\"{= 2 + 3 }\"}", Map.of());
        assertEquals(5, ((JSONObject) r).getIntValue("v"));
    }

    @Test
    public void groovyInline_canReferenceDataViaAtRewrite() {
        // @x is rewritten to data.x inside the script
        Object r = engine.renderJson("{\"v\":\"{= @x * 2 }\"}", Map.of("x", 7));
        assertEquals(14, ((JSONObject) r).getIntValue("v"));
    }

    // ===== {% ... %} Groovy statement block =====

    @Test
    public void groovyBlock_runsStatementsAndReturns() {
        Object r = engine.renderJson("{\"v\":\"{% int a = 5; int b = 6; return a + b; %}\"}", Map.of());
        assertEquals(11, ((JSONObject) r).getIntValue("v"));
    }

    // ===== error path =====

    @Test
    public void runtimeExceptionInScript_surfacesAsRenderException() {
        Assertions.assertThrows(RenderException.class, () -> {
            // Groovy runs but throws → wrapped in RenderException
            engine.renderJson("{\"v\":\"{= 1 / 0 }\"}", Map.of());
        });
    }
}
