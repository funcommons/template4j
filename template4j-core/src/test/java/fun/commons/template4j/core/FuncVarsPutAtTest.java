package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 覆盖 FuncVars.putAt (返回值的 putAt)。
 */
public class FuncVarsPutAtTest {

    @Test
    public void testVarsPutAt() throws Exception {
        // vars.putAt("name", value) 返回 value (Spring-style)
        Object r = RenderEngine.build().render(
                "{% def result = vars['myvar'] = 'X'; return result %}", java.util.Map.of());
        Assertions.assertEquals(r, "X");
    }
}
