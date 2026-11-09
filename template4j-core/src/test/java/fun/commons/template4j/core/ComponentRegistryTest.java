package fun.commons.template4j.core;

import fun.commons.template4j.core.RenderComponent;
import fun.commons.template4j.core.components.IfRenderer;
import fun.commons.template4j.core.components.SwitchRenderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Coverage for {@link ComponentRegistry} — the package-private registry that
 * holds the set of registered RenderComponents. Tests verify register/find,
 * null/unknown lookup behavior, and that registerAllDefaults wires up the
 * built-in components.
 */
public class ComponentRegistryTest {

    @Test
    public void find_returnsNullForUnknownComponent() {
        ComponentRegistry r = new ComponentRegistry();
        assertNull(r.find("does-not-exist"));
    }

    @Test
    public void register_makesComponentFindableByName() {
        ComponentRegistry r = new ComponentRegistry();
        RenderComponent c = new SwitchRenderer();
        r.register(c);
        assertSame(c, r.find("switch"));
    }

    @Test
    public void register_overwritesPreviousSameName() {
        ComponentRegistry r = new ComponentRegistry();
        RenderComponent a = new SwitchRenderer();
        RenderComponent b = new SwitchRenderer();
        r.register(a);
        r.register(b);
        assertSame(b, r.find("switch"));
        assertEquals(1, r.all().size());
    }

    @Test
    public void registerAllDefaults_wiresUpBuiltinComponents() {
        ComponentRegistry r = new ComponentRegistry();
        r.registerAllDefaults(null); // returns the renderer arg, but null is OK since we test registry directly
        // 9 built-ins per the registerAllDefaults implementation
        assertEquals(9, r.all().size());
        // spot-check a couple by name
        assertSame(IfRenderer.class, r.find("if").getClass());
        assertSame(SwitchRenderer.class, r.find("switch").getClass());
    }

    @Test
    public void all_returnsLiveMapReflectingFutureRegistrations() {
        ComponentRegistry r = new ComponentRegistry();
        java.util.Map<String, RenderComponent> view = r.all();
        assertEquals(0, view.size());
        r.register(new SwitchRenderer());
        assertEquals(1, view.size()); // live view, not a snapshot
    }
}
