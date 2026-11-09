package fun.commons.template4j.core.components;

import com.alibaba.fastjson2.JSONArray;
import fun.commons.template4j.core.RenderComponent;
import org.apache.commons.lang3.BooleanUtils;


import java.util.*;

public class EachRenderer implements RenderComponent {
    @Override
    public String name() {
        return "each";
    }
    /**
     * <pre>
     *---
     *"@class": each
     *"select": ["red", "blue", "green"]
     *"body":
     *   "key": "{$key}"
     *   "item": "{$item}"
     *#可选项
     *"keyName": "key"
     *"itemName": "item"
     *#如果subTarget是数组，merge为true, 则合并数组
     *"merge": true
     *"nonnull": true
     * 输出：
     * [
     *   {"key": "0", "item": "red"},
     *   {"key": "1", "item": "blue"},
     *   {"key": "2", "item": "green"}
     * ]
     *
     * </pre>
     */
    @Override
    public Object render(RenderContext context) throws Exception
    {
        //必需包含select属性
        context.assertField("select");

        String keyName = context.getValue("keyName", "key");
        String itemName = context.getValue("itemName", "item");
        Object merge = context.renderField("merge");

        boolean isBody  = context.containsField("body");

        boolean isMerge  = Boolean.FALSE != BooleanUtils.toBooleanObject(String.valueOf(merge));
        Object nonnull = context.renderField("nonnull");
        boolean isNonnull = Boolean.FALSE !=BooleanUtils.toBooleanObject(String.valueOf(nonnull));

        keyName = keyName.startsWith("$") ? keyName.substring(1) : keyName ;
        itemName = itemName.startsWith("$") ? itemName.substring(1) : itemName ;

        Object select = context.renderField("select");
        JSONArray result = new JSONArray();

        if (select != null) {

            if ( select.getClass().isArray()) {
                select = Arrays.asList((Object[]) select);
            }

            if (select instanceof Collection<?>) {
                int index = 0;
                for (Object obj : (Collection<?>) select) {

                    Map<String,Object>  subMap = new  HashMap<>();
                    subMap.put(keyName, index++);
                    subMap.put(itemName, obj);

                    Object subTarget = isBody ? context.renderField("body",subMap) : obj;

                    if ( subTarget!= null) {
                        //如果subTarget是数组，merge为true, 则合并数组
                        if (subTarget instanceof List && isMerge) {
                            result.addAll((List) subTarget);
                        } else {
                            result.add(subTarget);
                        }
                    }

                }

            }
            else if (select instanceof Map selectMap) {

                for (Object key : selectMap.keySet()) {

                    Object obj = selectMap.get(key);

                    Map<String,Object>  subMap = new  HashMap<>();
                    subMap.put(keyName, key);
                    subMap.put(itemName, obj );

                    Object subTarget = isBody ? context.renderField("body",subMap) : obj ;

                    if ( subTarget!= null) {
                        //如果subTarget是数组，merge为true, 则合并数组
                        if (subTarget instanceof List && isMerge) {
                            result.addAll((List) subTarget);
                        } else {
                            result.add(subTarget);
                        }
                    }
                }
            }
            else {
                // select 不是 Collection / Map / array：保持向前兼容的静默跳过行为。
                // 不抛异常以兼容既有的模板（testEachSelectNotIterable 锁定此行为）。
            }
        }

        if (isNonnull){
            result.removeIf(o -> o == null);
        }
        return result;

    }


}
