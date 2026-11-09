package fun.commons.template4j.utils;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.util.Map;

/**
 * 深度合并两个 JSON 对象（左覆盖右 / 右覆盖左 由参数控制）。
 * 提取自 UJson 的 mergeJson / mergeJson(from, to, mustExist, arrayFill) 实现。
 */
public final class JsonMerger {

    private JsonMerger() {
    }

    public static JSONObject mergeJson(JSONObject fromObj, JSONObject toObj) {
        return mergeJson(fromObj, toObj, false, true);
    }

    public static JSONObject mergeJson(JSONObject fromObj, JSONObject toObj, boolean mustExist, boolean arrayFill) {
        JSONObject parameters = UJson.copy(fromObj);
        JSONObject target = UJson.copy(toObj);

        parameters.keySet().forEach(k -> {
            Object fromValue = parameters.get(k);

            if (target.containsKey(k)) {
                Object toValue = target.get(k);

                if (toValue instanceof JSONObject) {
                    JSONObject toObjValue = (JSONObject) toValue;
                    if (fromValue instanceof JSONObject fromObjValue) {
                        target.put(k, mergeJson(fromObjValue, toObjValue, mustExist, arrayFill));
                    } else {
                        target.put(k, fromValue);
                    }
                } else if (toValue instanceof JSONArray) {
                    JSONArray toObjValue = (JSONArray) toValue;
                    if (fromValue instanceof JSONArray fromObjValue) {
                        if (arrayFill) {
                            fromObjValue.removeAll(toObjValue);
                            toObjValue.addAll(fromObjValue);
                        } else {
                            target.put(k, fromValue);
                        }
                    } else {
                        if (arrayFill) {
                            if (!toObjValue.contains(fromValue)) {
                                toObjValue.add(fromValue);
                            }
                        } else {
                            target.put(k, fromValue);
                        }
                    }
                } else {
                    target.put(k, fromValue);
                }
            } else {
                if (!mustExist) {
                    target.put(k, fromValue);
                }
            }
        });

        return target;
    }
}
