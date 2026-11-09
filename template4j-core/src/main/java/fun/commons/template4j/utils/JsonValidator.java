package fun.commons.template4j.utils;

import com.alibaba.fastjson2.JSONValidator;
import org.apache.commons.lang3.StringUtils;

/**
 * JSON 字符串结构验证工具：判断字符串是否为合法的 JSON Object / Array。
 */
public final class JsonValidator {

    private JsonValidator() {
    }

    public static boolean isJsonObject(String str) {
        if (StringUtils.isBlank(str)) {
            return false;
        }
        String s = str.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            return JSONValidator.from(s).validate();
        }
        return false;
    }

    public static boolean isJsonArray(String str) {
        if (StringUtils.isBlank(str)) {
            return false;
        }
        String s = str.trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            return JSONValidator.from(s).validate();
        }
        return false;
    }

    public static boolean isJson(String str) {
        return isJsonArray(str) || isJsonObject(str);
    }
}
