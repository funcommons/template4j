package fun.commons.template4j.utils;

import com.alibaba.fastjson2.JSONPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * 对象树搜索工具：在 Map/List 树中查找符合谓词条件的 Map 节点。
 */
public final class JsonFinder {

    private JsonFinder() {
    }

    public static List<Map<String, Object>> findMapAll(Object root) {
        return findMap(null, root, (name, map) -> true);
    }

    public static List<Map<String, Object>> findMapWithName(Object root, String... names) {
        return findMap(null, root, (name, map) -> {
            boolean result = false;
            for (String s : names) {
                result = result || s.equals(name);
            }
            return result;
        });
    }

    public static List<Map<String, Object>> findMapContainsPath(Object root, String path) {
        return findMap(null, root, (name, map) -> JSONPath.contains(map, path));
    }

    public static List<Map<String, Object>> findMapContainsKey(Object root, String... keys) {
        return findMap(null, root, (name, map) -> {
            boolean result = true;
            for (String key : keys) {
                result = result && map.containsKey(key);
            }
            return result;
        });
    }

    public static List<Map<String, Object>> findMap(Object root, BiPredicate<String, Map<String, Object>> matcher) {
        return findMap(null, root, matcher);
    }

    private static List<Map<String, Object>> findMap(String index, Object value, BiPredicate<String, Map<String, Object>> matcher) {
        List<Map<String, Object>> result = new ArrayList<>();

        if (value instanceof Map) {
            Map<String, Object> vMap = (Map) value;
            if (matcher.test(index, vMap)) {
                result.add(vMap);
            }
            vMap.forEach((k, v) -> result.addAll(findMap(k, v, matcher)));
        } else if (value instanceof List vList) {
            for (int i = 0; i < vList.size(); i++) {
                Object item = vList.get(i);
                if (item instanceof Map || item instanceof List) {
                    result.addAll(findMap(String.valueOf(i), item, matcher));
                }
            }
        }
        return result;
    }
}
