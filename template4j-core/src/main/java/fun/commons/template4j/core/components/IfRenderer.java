package fun.commons.template4j.core.components;

import fun.commons.template4j.utils.UType;
import fun.commons.template4j.core.RenderComponent;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

public class IfRenderer implements RenderComponent {
    @Override
    public String name() {
        return "if";
    }

    /**
     * <pre>
     *---
     *"@class": if
     *"select": "{= $status == 200 }"
     *"then":
     *   "code": 0
     *   "message": "成功"
     *"else":
     *   "code": "{$status}"
     *   "message": "失败"
     * </pre>
     */
    @Override
    public Object render(RenderContext context) throws Exception
    {
        //必需包含select属性
        context.assertField("select");

        //必需包含"then","else", "true", "false"属性 之一
        context.assertFieldOne("then","else", "true", "false" );

        Object select = context.renderField("select");
        //is优先级高于method
        String is = context.getValue(context.firstNonField("is","method"),"true").toLowerCase();


        boolean test = false;
        if ("true".equals(is)){
             test = UType.isTrue(select);
        }
        else if ("!true".equals(is)) {
            test = !UType.isTrue(select);
        }
        else if ("false".equals(is)) {
            test = UType.isFalse(select);
        }
        else if ("!false".equals(is)) {
            test = !UType.isFalse(select);
        }
        else if ("null".equals(is)) {
            test = select == null;
        }
        else if ("!null".equals(is)) {
            test = select != null;
        }
        else if ("blank".equals(is)) {
            test =  select == null || StringUtils.isBlank(String.valueOf(select));
        }
        else if ("!blank".equals(is)) {
            test =  select != null && StringUtils.isNotBlank(String.valueOf(select));
        }
        else if ("empty".equals(is)) {
            test =  select == null || ObjectUtils.isEmpty(select);
        }
        else if ("!empty".equals(is)) {
            test =  !ObjectUtils.isEmpty(select);
        }

        String field = test? context.firstNonField("then", "true") : context.firstNonField("else","false");
        return context.renderField(field);

    }

}
