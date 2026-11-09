package fun.commons.template4j.core.components;

import fun.commons.template4j.core.RenderComponent;

public class ConstRenderer implements RenderComponent {
    @Override
    public String name() {
        return "const";
    }

    /**
     * <pre>
     *---
     *"@class": const
     *"select": {...}
     * </pre>
     */
    @Override
    public Object render(RenderContext context) throws Exception
    {
        //必需包含select属性
        context.assertField("select");
        //原样返回，不做渲染。
        return context.getValue("select");

    }

}
