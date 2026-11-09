package fun.commons.template4j.core.beans;

import com.alibaba.fastjson2.JSONPath;

import java.util.Map;

public class FuncData {

    final Map<String,Object> data;

    public FuncData(Map<String,Object> data) {
        this.data = data;
    }

    public Object call(String path) {
        return get(path);
    }

    public Object call(Map<String,Object> data) {
        return new FuncData(data);
    }

    public Object get(String path) {
        return JSONPath.eval(data, path);
    }

}