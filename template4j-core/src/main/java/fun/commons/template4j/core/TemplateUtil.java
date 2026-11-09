package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.utils.UString;
import fun.commons.template4j.utils.UType;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringSubstitutor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TemplateUtil {

    private static String dataStr(String key, String dataType) {

        if (StringUtils.isBlank(dataType)) {
            return "{% data(\"['" + key + "']\") %}";
        } else {
            return "{% cast(data(\"['" + key + "']\"),\"" + dataType + "\") %}";
        }
    }

    private static String varsStr(String key, String dataType) {

        if (StringUtils.isBlank(dataType)) {
            return "{% $" + key + " %}";
        } else {
            return "{% cast($" + key + ",'" + dataType + "') %}";
        }
    }

    protected static void simplify(Map<String, Object> map) {

        // value的@和$简写
        for (Map.Entry<String, Object> entry : map.entrySet()) {

            Object o = entry.getValue();
            String k = entry.getKey();

            // 处理非空字符串
            if (o instanceof String v && StringUtils.isNotBlank(o.toString())) {
                //如果数据表达式不嵌入字符串中，且与模板的key同名则可以简写为@。
                if ("@".equals(v)) {
                    entry.setValue(dataStr(k, null));
                }
                //数据简写并要类型转换,则用`@|`开头，后跟数型类型。
                else if (v.matches("^@\\|\\w+$")) {
                    entry.setValue(dataStr(k, v.substring(2)));
                }
                //如果变量表达式不嵌入字符串中，且与模板的key同名则可以简写为$。
                else if ("$".equals(v)) {
                    entry.setValue(varsStr(k, null));
                }
                //变量简写并要类型转换,则用`$|`开头，后跟数型类型。
                else if (v.matches("^\\$\\|\\w+$")) {
                    entry.setValue(varsStr(k, v.substring(2)));
                }

                //简写@class。

                else if (v.matches("^@\\w+(\\|[\\w!_\\.-]+)?:[\\w\\W]*")) {

                    String classStr = v.split(":")[0];
                    String selectStr = StringUtils.removeStart(v, classStr + ":");

                    String[] tempList = classStr.split("\\|");
                    String className = StringUtils.removeStart(tempList[0],"@");
                    Map<String, Object> subMap = new LinkedHashMap<>();
                    subMap.put("@class",className);
                    subMap.put("select",selectStr);
                    if (tempList.length > 1){
                        subMap.put("method",tempList[1]);
                    }
                    entry.setValue(subMap);
                }

            }
        }

        //处理@class 简写
        for (Map.Entry<String, Object> entry : map.entrySet()) {

            String k = entry.getKey();

            if ("@class".equals(k)){
                //非@class 简写，不用处理
                return;
            }

            if (isAtClassShorthand(k)){

                String[] tempList = k.split("\\|");

                String className = tempList[0].substring(1);
                map.put("@class",className);
                map.put("select",entry.getValue());
                if (tempList.length > 1){
                    map.put("method",tempList[1]);
                }
                map.remove(k);
                return;
            }
        }
    }

    /**
    * <pre>
    * ## <b>key转义 "{&...&}": ... </b>
    * 如果key符合定义语法 如：以# 开头，然而又不希望被解析为注释，可以使用 {&#...&} 包裹起来
    * </pre>
    * */
    protected static JSONObject escapeKey(JSONObject objectValue) {
        JSONObject modifiedMap = new JSONObject();
        for (Map.Entry<String, Object> entry : objectValue.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();
            if (UString.isBesieged(k,"<&","&>")){
                k = UString.extractBesieged(k,"<&","&>");
            }
            modifiedMap.put(k, v);
        }
        return  modifiedMap;
    }
    /**
     * <pre>
     * ## <b> 转写 #... 、 ...& 、...% </b>
     * </pre>
     * */
    protected static JSONObject convertKey(JSONObject objectValue) {

        JSONObject modifiedMap = new JSONObject();

        for (Map.Entry<String, Object> entry : objectValue.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();

            //把所有的键#开头的，都作为注释处理
            if (k.startsWith("#")) {
                continue;
            }

            if (k.length() > 1) {

                //把所有的键尾%结束的，都作为脚本处理
                // "key%": "return @name" 转为： "key": "{% return @name %}"
                if (k.endsWith("%")) {
                    k = StringUtils.removeEnd(k, "%");
                    v = ScriptUtil.getProgramBesieged(v);
                }

                //把所有的键尾&结束的，都不渲染处理
                // "key&": "{% return @name %}" 转为： "key": { "@class":"const", "select": "{% return @name %}" }"
                else if (k.endsWith("&")) {
                    k = StringUtils.removeEnd(k, "&");
                    JSONObject newVar = new JSONObject();
                    newVar.put("@class", "const");
                    newVar.put("select", v);
                    v = newVar;
                }

                //把所有的键尾#结束的，都不渲染处理
                // "key#": "{@name}" 转为： "key": { "@class":"const", "select": "{@name}" }"
                else if (k.endsWith("#")) {
                    k = StringUtils.removeEnd(k, "#");
                    JSONObject newVar = new JSONObject();
                    newVar.put("@class", "const");
                    newVar.put("select", v);
                    v = newVar;
                }

            }
            modifiedMap.put(k, v);
        }

        return modifiedMap;

    }

    protected static JSONObject convertVars(JSONObject objectValue, SelfObject subSelf) throws RenderException {

        JSONObject modifiedMap = new JSONObject();
        //遍历所有键值对
        //如果是$...的，则认为是变量，
        //如果是$...()的，则认为是方法，
        for (Map.Entry<String, Object> entry : objectValue.entrySet()) {
            String k = entry.getKey();
            Object v = entry.getValue();

            //如果有@为键，则用 @下的数据取代 原来的 参数数据
            //注意：* 只支持定义在@前的变量, 不支持定义在@前，但变量内引用了@后的变量的变量。
            if ( k.equals("@")) {
               subSelf.setData(UType.castValue(subSelf.convertSelf(v,  "@"), JSONObject.class));
            }
            //@@ 局部数据：与 @ 类似，但 value 不走模板渲染（字面 Map）
            else if ( k.equals("@@")) {
                subSelf.setData(UType.castValue(v, JSONObject.class));
            }
            else if ( k.equals("$")) {
                subSelf.putVarDefine(v);
            }
            //如果有$$为键，则用 $$下的 (渲染后数据) 作为变量
            //注意：只支持定义在$$前的变量,和@局部数据。
            else if ( k.equals("$$")) {
                subSelf.putVars(subSelf.convertSelf(v,  "$$"));
            }
            else if (isVarKeyHash(k)) {
                // $xxx# 变量原样（不渲染），与 docs 中 "$abc#" 文档一致
                String cleanKey = k.substring(0, k.length() - 1);  // 去掉 #
                JSONObject constWrap = new JSONObject();
                constWrap.put("@class", "const");
                constWrap.put("select", v);
                subSelf.putVar(cleanKey, constWrap);
            }
            else if (isVarKey(k)) {
                subSelf.putVar(k, v);
            } else {
                modifiedMap.put(k,v);
            }
        }

        return modifiedMap;
    }

    // ===== key-classification helpers (replace per-call Pattern.compile) =====

    /** matches: ^@\w+(\|[\w!_\.-]+)?$  →  @ClassName or @ClassName|method */
    private static boolean isAtClassShorthand(String k) {
        if (k.length() < 2 || k.charAt(0) != '@') return false;
        int n = k.length();
        int i = 1;
        // mandatory at least one word char after @
        if (!isWordChar(k.charAt(i))) return false;
        while (i < n && isWordChar(k.charAt(i))) i++;
        // optional |method
        if (i < n && k.charAt(i) == '|') {
            i++; // skip |
            if (i >= n) return false; // | alone invalid
            while (i < n && isMethodChar(k.charAt(i))) i++;
        }
        return i == n;
    }

    /** matches: \$[\._\w]+#(\(\))?  →  $xxx# or $xxx#() */
    private static boolean isVarKeyHash(String k) {
        if (k.length() < 3 || k.charAt(0) != '$') return false;
        int n = k.length();
        int end = n;
        if (n >= 3 && k.charAt(n - 1) == ')' && k.charAt(n - 2) == '(') end = n - 2;
        if (end < 3 || k.charAt(end - 1) != '#') return false;
        // need at least one body char between $ and #
        for (int i = 1; i < end - 1; i++) {
            if (!isVarBodyChar(k.charAt(i))) return false;
        }
        return true;
    }

    /** matches: \$[\._\w]+(\(\))?  →  $xxx or $xxx() */
    private static boolean isVarKey(String k) {
        if (k.length() < 2 || k.charAt(0) != '$') return false;
        int n = k.length();
        int end = n;
        if (n >= 3 && k.charAt(n - 1) == ')' && k.charAt(n - 2) == '(') end = n - 2;
        if (end < 2) return false;
        for (int i = 1; i < end; i++) {
            if (!isVarBodyChar(k.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isWordChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    private static boolean isMethodChar(char c) {
        return isWordChar(c) || c == '!' || c == '.' || c == '-';
    }

    private static boolean isVarBodyChar(char c) {
        return isWordChar(c) || c == '.' || c == '_';
    }

    protected static String parseStrTemplate(String strValue, Map<String, String> expressionMap) {
        // 用 StringSubstitutor 把占位符替换为 %expr:N% token,延迟到 evaluate 阶段解析。
        // 6 种定界对各自跑一遍: <&...&> / {#...#} / {%...%} / {@...} / {$...} / {=...}
        // 每次匹配把完整占位符存入 expressionMap, value 改写为 %expr:N%。
        // escape 设为 \0 (NULL) 是 StringSubstitutor 的 magic value 表示禁用转义;
        // 否则默认 '$' 会在模板含 ${...} 时被误当 escape。
        final int[] counter = {0};
        String result = strValue;
        result = tokenizePlaceholders(result, "<&", "&>", expressionMap, counter);
        result = tokenizePlaceholders(result, "{#", "#}", expressionMap, counter);
        result = tokenizePlaceholders(result, "{%", "%}", expressionMap, counter);
        result = tokenizePlaceholders(result, "{@", "}", expressionMap, counter);
        result = tokenizePlaceholders(result, "{$", "}", expressionMap, counter);
        result = tokenizePlaceholders(result, "{=", "}", expressionMap, counter);
        return result;
    }

    private static String tokenizePlaceholders(String input, String prefix, String suffix,
                                                Map<String, String> expressionMap, int[] counter) {
        StringSubstitutor ss = new StringSubstitutor(
                key -> {
                    String full = prefix + key + suffix;
                    String token = "%expr:" + (counter[0]++) + "%";
                    expressionMap.put(token, full);
                    return token;
                },
                prefix, suffix, ' ');
        ss.setEnableSubstitutionInVariables(false);
        return ss.replace(input);
    }

    // 合并val中的MAP，如果val是数组或多级数级。则递归合并所有MAP
    public static Map<String,Object> mergeAllMap(Object val) {
        Map<String,Object> result = new LinkedHashMap<>();
        if (val instanceof Map) {
            result.putAll((Map) val);
        } else if ((val) instanceof List) {
            for (Object objTemp : (List) val) {
                result.putAll(mergeAllMap(objTemp));
            }
        }
        return result;
    }
}