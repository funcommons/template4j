package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.RenderEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Coverage for {@link ScriptEnvironment} via the render pipeline. Tests verify
 * bean registration, imports, and the built-in `data/vars/render/load/cast/fne`
 * bindings are exposed to Groovy scripts.
 */
public class ScriptEnvironmentTest {

    @Test
    public void beanRegistration_makesBeanVisibleToScript() throws Exception {
        RenderEngine engine = RenderEngine.build().withBean("greeting", "hello");
        Object r = engine.renderJson("{\"v\":\"{= greeting }\"}", Map.of());
        assertEquals(((JSONObject) r).getString("v"), "hello");
    }

    @Test
    public void beanRegistration_rejectsDuplicateName() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            // The engine explicitly forbids re-registration under the same name
            // to avoid silent shadowing. Trying twice throws.
            RenderEngine.build()
                .withBean("greeting", "v1")
                .withBean("greeting", "v2");
        });
    }

    @Test
    public void importRegistration_letsScriptReferenceClassBySimpleName() throws Exception {
        RenderEngine engine = RenderEngine.build().withImport("org.apache.commons.lang3.StringUtils");
        // StringUtils is now resolvable as `StringUtils` (no FQN)
        Object r = engine.renderJson("{\"v\":\"{= StringUtils.upperCase('abc') }\"}", Map.of());
        assertEquals(((JSONObject) r).getString("v"), "ABC");
    }

    @Test
    public void builtInBindings_exposeDataObjectToScript() {
        // `data` binding wraps the data map in FuncData → supports get/JSONPath
        RenderEngine engine = RenderEngine.build();
        Object r = engine.renderJson("{\"v\":\"{= data.get('name') }\"}", Map.of("name", "Alice"));
        assertEquals(((JSONObject) r).getString("v"), "Alice");
    }

    @Test
    public void castBinding_convertsValueToTargetType() {
        RenderEngine engine = RenderEngine.build();
        Object r = engine.renderJson("{\"v\":\"{= cast('42','Integer') }\"}", Map.of());
        assertEquals(42, ((JSONObject) r).getIntValue("v"));
    }

    @Test
    public void fneBinding_returnsFirstNonNull() {
        RenderEngine engine = RenderEngine.build();
        Object r = engine.renderJson("{\"v\":\"{= fne(null, null, 'hit') }\"}", Map.of());
        assertEquals(((JSONObject) r).getString("v"), "hit");
    }

    @Test
    public void varsBinding_readsLocallyDefinedVariable() {
        // $x defined at top of template, vars.x readable in script
        RenderEngine engine = RenderEngine.build();
        Object r = engine.renderJson("{\"$x\":\"hello\",\"v\":\"{= vars.x }\"}", Map.of());
        assertEquals(((JSONObject) r).getString("v"), "hello");
    }

    @Test
    public void scriptCaching_evaluatesSameTemplateManyTimesWithoutError() {
        // The Groovy engine is expected to cache compiled scripts by source
        // text — exercising the same template repeatedly shouldn't blow up.
        RenderEngine engine = RenderEngine.build();
        Map<String, Object> data = new HashMap<>();
        data.put("n", 10);
        for (int i = 0; i < 50; i++) {
            Object r = engine.renderJson("{\"v\":\"{= @n * 2 }\"}", data);
            assertEquals(20, ((JSONObject) r).getIntValue("v"));
        }
    }
}
