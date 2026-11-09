package fun.commons.template4j.core.components;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.RenderComponent;
import fun.commons.template4j.core.SelfObject;
import fun.commons.template4j.utils.UType;

public class RenderRenderer implements RenderComponent {

    @Override
    public String name() {
        return "render";
    }


    /**
     * <pre>
     *---
     *"@class": "render"
     *"select":
     *  "name":   "{@name}",
     *"data":
     *  "name": "自定义数据"
     * </pre>
     */


    @Override
    public Object render(RenderContext context) throws Exception
    {
        //必需包含select属性
        context.assertField("select");

        if (context.containsField("data")) {
            Object data = context.renderField("data");
            SelfObject subSelf = new SelfObject(context.getSelf(), name());
            if (data instanceof JSONObject) {
                subSelf.setData((JSONObject) data);
            } else {
                subSelf.setData(UType.castValue(data, JSONObject.class));
            }
            return subSelf.convertSelf(context.getValue("select"));
        }

        Object template = context.renderField("select");
        return context.render(template);

    }

}
