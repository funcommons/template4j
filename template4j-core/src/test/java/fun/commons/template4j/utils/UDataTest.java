package fun.commons.template4j.utils;

import com.alibaba.fastjson2.*;
import fun.commons.template4j.utils.UJson;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;

public class UDataTest {



    @Test
    public void test1() throws IOException {
        String yaml = """
                ---
                num1: 1.1
                num2: 2.2
                date1: 2022-01-01 10:00:11
                date2: 1707809032
                bool2: true
                bool1: false
                str1: "hello"
                str2: 'hello'
                url1: https://www.baidu.com/s?wd=%E4%B8%AD%E5%9B%BD%E4%BA%BA
                url2: 'https://www.baidu.com/s?wd=%E4%B8%AD%E5%9B%BD%E4%BA%BA'
                url3: "https://www.baidu.com/s?wd=%E4%B8%AD%E5%9B%BD%E4%BA%BA"
                "array1":
                  - 1
                  - 2
                  - 3
                'array2': [1,2,3]
                "array3": ["1","2","3"]
                中文1: "中国人"
                "中文 2": "\\u4e2d\\u56fd\\u4eba"
                object1:
                  a: 1
                  b: 2
                  c: 1,2,3
                object2: {a: 1, b: 2, c:[1,2,3]}
                """;

        // 将YAML字符串转换为JSON对象
        Object json = UJson.JSONFromYaml(yaml);
        System.out.println(UJson.toPrettyJSONString(json));
    }

    @Test
    public void TestJsonOrder() throws IOException {
        String json = """
             {
                "num1": 1.1,
                "num2": 2.2,
                "date1": "2022-01-01 10:00:11",
                "date2": 1707809032,
                "bool2": true,
                "1":1,
                "3":3,
                "2":2                
             }
             """;

        JSONObject jsonObject = JSON.parseObject(json);
        System.out.println(JSON.toJSONString(jsonObject, JSONWriter.Feature.PrettyFormat));

    }
    @Test
   public void TestYamlObject() throws IOException {
        String yaml = """
                ---
                num1: 1.1
                num2: 2.2
                date1: 2022-01-01 10:00:11
                date2: 1707809032
                bool2: true
                bool1: false
                str1: "hello"
                str2: 'hello'
                url1: https://www.baidu.com/s?wd=%E4%B8%AD%E5%9B%BD%E4%BA%BA
                url2: 'https://www.baidu.com/s?wd=%E4%B8%AD%E5%9B%BD%E4%BA%BA'
                url3: "https://www.baidu.com/s?wd=%E4%B8%AD%E5%9B%BD%E4%BA%BA"
                "array1": 
                  - 1
                  - 2
                  - 3
                'array2': [1,2,3]
                "array3": ["1","2","3"]
                中文1: "中国人"
                "中文 2": "\\u4e2d\\u56fd\\u4eba"
                object1:
                  a: 1
                  b: 2
                  c: 1,2,3
                object2: {a: 1, b: 2, c:[1,2,3]}                                                
                """;

        // 将YAML字符串转换为JSON对象
        Object json = UJson.string2json(yaml);

        Assertions.assertTrue(json instanceof JSONObject);
        JSONObject jsonObject = (JSONObject) json;

        // 打印JSON对象
        System.out.println(JSON.toJSONString(jsonObject, JSONWriter.Feature.PrettyFormat));

        Assertions.assertEquals(jsonObject.getJSONArray("array1").toString(),jsonObject.getJSONArray("array2").toString());
        Assertions.assertEquals(jsonObject.getString("中文1"),jsonObject.getString("中文 2"));
        Assertions.assertEquals(JSONPath.eval(jsonObject,"$['中文1']"),JSONPath.eval(jsonObject,"['中文 2']"));
        Assertions.assertEquals(3,JSONPath.eval(jsonObject,"$.object2.c[2]"));

    }

    @Test
    public void TestYamlArray() throws IOException {
        String yaml = """
                ---
                  - name: 小明
                    age: 18
                  - name: 小红
                    age: 19 
                    likes:
                      - 电影
                      - 音乐
                  - name: 小军
                    age: 20
                    likes:
                      - 篮球
                      - 足球                        
                """;

        // 将YAML字符串转换为JSON对象
        Object json = UJson.string2json(yaml);
        Assertions.assertTrue(json instanceof JSONArray);
        // 打印JSON对象
        System.out.println(JSON.toJSONString(json, JSONWriter.Feature.PrettyFormat));

        Assertions.assertEquals(18,JSONPath.eval(json, "[0].age"));
        Assertions.assertEquals(JSONPath.eval(json, "$[?(@.name=='小军')].likes[1]"), "足球");

    }

    @Test
    public void TestJsonArray() throws IOException {
        String yaml = """
                [
                    {
                        "name":"小明",
                        "age":18
                    },
                    {
                        "name":"小红",
                        "age":19,
                        "likes":[
                            "电影",
                            "音乐"
                        ]
                    },
                    {
                        "name":"小军",
                        "age":20,
                        "likes":[
                            "篮球",
                            "足球"
                        ]
                    }
                ]                       
                """;

        // 将YAML字符串转换为JSON对象
        Object json = UJson.string2json(yaml);

        Assertions.assertTrue(json instanceof JSONArray);

        // 打印JSON对象
        System.out.println( UJson.toYAMLString(json));

        Assertions.assertEquals(18,JSONPath.eval(json, "[0].age"));
        Assertions.assertEquals(JSONPath.eval(json, "$[?(@.name=='小军')].likes[1]"), "足球");


    }
}
