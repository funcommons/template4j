package fun.commons.template4j.core;

import fun.commons.template4j.core.components.ConstRenderer;
import fun.commons.template4j.core.components.EachRenderer;
import fun.commons.template4j.core.components.IfRenderer;
import fun.commons.template4j.core.components.InvokeRenderer;
import fun.commons.template4j.core.components.ParallelRenderer;
import fun.commons.template4j.core.components.RenderRenderer;
import fun.commons.template4j.core.components.RestRenderer;
import fun.commons.template4j.core.components.SwitchRenderer;
import fun.commons.template4j.core.components.TryRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * 组件注册表：负责 {@link RenderComponent} 的注册、查询、迭代。
 * 从 JsonRenderer 中拆分以隔离组件管理的可变状态。
 */
final class ComponentRegistry {

    private final Map<String, RenderComponent> components = new HashMap<>();

    void register(RenderComponent component) {
        components.put(component.name(), component);
    }

    RenderComponent find(String name) {
        return components.get(name);
    }

    java.util.Map<String, RenderComponent> all() {
        return components;
    }

    JsonRenderer registerAllDefaults(JsonRenderer renderer) {
        register(new RestRenderer());
        register(new InvokeRenderer());
        register(new IfRenderer());
        register(new EachRenderer());
        register(new SwitchRenderer());
        register(new ConstRenderer());
        register(new RenderRenderer());
        register(new ParallelRenderer());
        register(new TryRenderer());
        return renderer;
    }
}
