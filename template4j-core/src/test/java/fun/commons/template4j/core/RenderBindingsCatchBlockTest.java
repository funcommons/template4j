package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.beans.FuncVars;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Covers RenderBindings.get() @SneakyThrows synthetic catch block
 * by making FuncVars.get() throw via a corrupted SelfObject.
 */
public class RenderBindingsCatchBlockTest {

    // Trigger the @SneakyThrows catch block by making vars.get() throw
    // FuncVars.get() calls self.vars(name) which calls self.getVar(name)
    // getVar() can throw RenderException under certain conditions
    // One way: use a $ key with invalid type in putVarDefine

    @Test
    public void testRenderBindingsGetWithThrowingFuncVars() throws Exception {
        // We need to make FuncVars.get() throw an Exception
        // FuncVars.get() calls self.vars(name) which calls getVar(name)
        // getVar() looks up in varMap — it shouldn't throw normally

        // Alternative: use reflection to replace FuncVars with a mock that throws
        // But we can't easily mock FuncVars in the bindings

        // Let me try: create a custom SelfObject where vars() throws
        // Actually, the simplest approach: test via Groovy script where
        // the get() method is called with a key that causes FuncVars to throw

        // FuncVars.get() throws Exception (declared), and @SneakyThrows wraps it
        // We need to trigger an actual Exception from FuncVars.get()

        // SelfObject.vars() can throw if it walks up the chain and encounters
        // a RenderException — but this is unlikely in normal usage

        // Let me try another approach: directly test RenderBindings.get() with
        // a FuncVars that throws
        JsonRenderer renderer = new JsonRenderer();
        SelfObject self = new SelfObject(renderer, new java.util.HashMap<>());

        // Create a FuncVars that throws
        FuncVars throwingVars = new FuncVars(self) {
            @Override
            public Object get(String name) throws Exception {
                throw new Exception("test exception from FuncVars");
            }
        };

        RenderBindings bindings = new RenderBindings();
        bindings.put("vars", throwingVars);

        // Call get() with a key that triggers the vars fallback
        // This should hit the @SneakyThrows catch block
        try {
            Object result = bindings.get("mykey");
            // The catch block wraps the Exception and rethrows as SneakyThrow
            Assertions.fail("Should have thrown");
        } catch (Exception e) {
            // The @SneakyThrows catch block catches the Exception and rethrows it
            // This is the synthetic catch block that JaCoCo marks as missed
            Assertions.assertTrue(e.getMessage().contains("test exception") || e.getCause() != null);
        }
    }
}
