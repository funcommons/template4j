package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSON;
import fun.commons.template4j.utils.UJson;

import java.io.IOException;

public interface TemplateLoader {


    /**
     * 加载模板
     *
     * @param url 模板地址
     * @return 模板内容 String
     * @throws Exception 加载异常
     */
    String load(String url) throws IOException;

    /**
     * 加载模板
     *
     * @param url 模板地址 json格式
     * @return 模板内容 JSONObject or JSONArray
     * @throws Exception 加载异常
     */
    default Object loadJson(String url) throws IOException {
        return JSON.parse(load(url));
    }

    /**
     * 加载模板
     *
     * @param url 模板地址 yaml格式
     * @return 模板内容 JSONObject or JSONArray
     * @throws Exception 加载异常
     */
    default Object loadYaml(String url) throws IOException {
        return UJson.JSONFromYaml(load(url));
    }

}
