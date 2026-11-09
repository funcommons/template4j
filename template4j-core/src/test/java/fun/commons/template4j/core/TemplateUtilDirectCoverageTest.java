package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.TemplateUtil;
import fun.commons.template4j.core.SelfObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Covers TemplateUtil lines 207-214 ($xxx# regex) by calling convertVars directly,
 * bypassing convertKey which would normally strip the # suffix first.
 * Also covers the private constructor (line 13).
 */
public class TemplateUtilDirectCoverageTest {

    @Test
    public void testTemplateUtilPrivateConstructor() throws Exception {
        Constructor<TemplateUtil> c = TemplateUtil.class.getDeclaredConstructor();
        c.setAccessible(true);
        TemplateUtil instance = c.newInstance();
        Assertions.assertNotNull(instance);
    }

    @Test
    public void testConvertVarsDollarHashKey() throws Exception {
        // Call convertVars directly with a $val# key to hit line 207-214
        // This bypasses convertKey which would normally strip the #
        Method convertVars = TemplateUtil.class.getDeclaredMethod("convertVars", JSONObject.class, SelfObject.class);
        convertVars.setAccessible(true);

        JSONObject input = new JSONObject();
        input.put("$val#", "{@name}");
        input.put("out", "{$val}");

        SelfObject self = new SelfObject(new JsonRenderer(), new java.util.HashMap<>());

        JSONObject result = (JSONObject) convertVars.invoke(null, input, self);

        // $val# should be processed: cleanKey = $val, const-wrap created, putVar called
        // The "out" key should remain in modifiedMap
        Assertions.assertTrue(result.containsKey("out"));
        // The var should have been put into self.varMap
        Assertions.assertTrue(self.getVarMap().containsKey("$val"));
    }

    @Test
    public void testConvertVarsDollarFuncHashKey() throws Exception {
        // $func# — variable with no-render (regex: $xxx# with optional trailing ())
        Method convertVars = TemplateUtil.class.getDeclaredMethod("convertVars", JSONObject.class, SelfObject.class);
        convertVars.setAccessible(true);

        JSONObject input = new JSONObject();
        input.put("$func#", "{@name}");

        SelfObject self = new SelfObject(new JsonRenderer(), new java.util.HashMap<>());

        JSONObject result = (JSONObject) convertVars.invoke(null, input, self);

        // $func# should be processed: cleanKey = $func (strip trailing #), const-wrap created
        Assertions.assertTrue(self.getVarMap().containsKey("$func"));
        Assertions.assertTrue(result.isEmpty()); // key consumed by convertVars
    }

    @Test
    public void testConvertVarsDollarHashWithUnderscore() throws Exception {
        // $_val# — underscore in name
        Method convertVars = TemplateUtil.class.getDeclaredMethod("convertVars", JSONObject.class, SelfObject.class);
        convertVars.setAccessible(true);

        JSONObject input = new JSONObject();
        input.put("$_val#", "test");

        SelfObject self = new SelfObject(new JsonRenderer(), new java.util.HashMap<>());

        JSONObject result = (JSONObject) convertVars.invoke(null, input, self);
        Assertions.assertTrue(self.getVarMap().containsKey("$_val"));
    }
}
