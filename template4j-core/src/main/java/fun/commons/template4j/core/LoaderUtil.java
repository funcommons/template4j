package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import fun.commons.template4j.utils.UJson;
import fun.commons.template4j.utils.UString;
import fun.commons.template4j.utils.UType;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.*;


public class LoaderUtil {

    private static final YAMLMapper yamlMapper = new YAMLMapper();
    private static final String INCLUDE_KEY = "&include";


    //返回 JSONObject JSONArray 或 String
    //resourceType可选，为 json|yaml|text 之一
    public static Object loadResource(TemplateLoader templateLoader, String resourceUrl, String resourceType) throws IOException {

        templateLoader = templateLoader == null ? new DefaultTemplateLoader() : templateLoader;

        String string = templateLoader.load(resourceUrl);

        if (string == null) {
            throw new IOException("can't found resource 404 :" + resourceUrl);
        }

        String[] jsonExt = new String[]{".json", ".json5", "jt", ".japi", ".jpage"};
        String[] yamlExt = new String[]{".yml", ".yaml", ".yt", ".yapi", ".ypage"};

        if (StringUtils.isBlank(resourceType)) {

            if (StringUtils.endsWithAny(resourceUrl.toLowerCase(), jsonExt)) {

                resourceType = "json";

            } else if (StringUtils.endsWithAny(resourceUrl.toLowerCase(), yamlExt)) {

                resourceType = "yaml";

            }

        }

        Object result;

        if ("json".equalsIgnoreCase(resourceType)) {

            result = JSON.parse(string);

        } else if ("yaml".equalsIgnoreCase(resourceType)) {

            result = UJson.JSONFromYaml(string);

        } else if ("text".equalsIgnoreCase(resourceType)) {

            result = string;

        } else {


            //尝试json格式转换为JSON
            if (UJson.isJsonObject(string) || UJson.isJsonArray(string)) {

                result = JSON.parse(string);

            } else {

                //尝试yaml格式转换为JSON
                result = UJson.JSONFromYamlSafe(string);

                //转换失败，认为是String
                if (result == null) {
                    result = string;
                }

            }
        }

        return result;

    }


    protected static Object loadTemplate(TemplateLoader templateLoader, String resourceUrl, String resourceType) throws IOException {

        Object result = loadResource(templateLoader, resourceUrl, resourceType);
        return includeValue(templateLoader, resourceUrl, result);

    }

    protected static JSONObject loadJSONObjectTemplate(TemplateLoader templateLoader, String resourceUrl) throws IOException {
        Object result = loadResource(templateLoader, resourceUrl, null);
        return UType.castValue(result, JSONObject.class);
    }
    protected static JSONArray loadJSONArrayTemplate(TemplateLoader templateLoader, String resourceUrl) throws IOException {
        Object result = loadResource(templateLoader, resourceUrl, null);
        return UType.castValue(result, JSONArray.class);
    }

    protected static Object includeValue(TemplateLoader templateLoader, String rootUrl, Object value) throws IOException {


        if (value instanceof JSONObject) {

            return includeObject(templateLoader, rootUrl, (JSONObject) value);

        } else if (value instanceof JSONArray) {

            return includeArray(templateLoader, rootUrl, (JSONArray) value);

        } else if (value instanceof String) {

            return includeString(templateLoader, rootUrl, (String) value);

        } else {
            return value;
        }

    }

    protected static JSONArray includeArray(TemplateLoader resourceLoader, String rootUrl, JSONArray template) throws IOException {

        JSONArray result = new JSONArray();

        for (Object v : template) {
            result.add(includeValue(resourceLoader, rootUrl, v));
        }

        return result;
    }

    protected static Object includeString(TemplateLoader templateLoader, String rootUrl, String template) throws IOException {

        if (StringUtils.isNotBlank(template) && template.startsWith(INCLUDE_KEY + ":")) {

            String url = template.substring(INCLUDE_KEY.length() + 1);
            String resourceUrl = UString.relativeUrl(rootUrl, url);
            Object loaded = loadResource(templateLoader, resourceUrl, null);

            return includeValue(templateLoader, rootUrl, loaded);

        } else {
            return template;
        }

    }

    protected static JSONObject includeObject(TemplateLoader templateLoader, String rootUrl, JSONObject template) throws IOException {
        JSONObject result = new JSONObject();
        for (String key : template.keySet()) {
            if (INCLUDE_KEY.equals(key)) {

                Object obj = template.get(key);
                List<String> includes = new LinkedList<>();

                if (obj instanceof String) {
                    includes.add(String.valueOf(obj));
                } else if (obj instanceof List list) {
                    includes.addAll(list);
                }

                for (String url : includes) {
                    String resourceUrl = UString.relativeUrl(rootUrl, url);
                    JSONObject includeJson = loadJSONObjectTemplate(templateLoader, resourceUrl);
                    result.putAll(includeObject(templateLoader, resourceUrl, includeJson));
                }

            } else {
                result.put(key, includeValue(templateLoader, rootUrl, template.get(key)));
            }
        }
        return result;
    }

}
