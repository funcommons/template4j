package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import fun.commons.template4j.utils.UJson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JsonRendererTest {

    JsonRenderer converter = new JsonRenderer();

    JSONObject data = JSONObject.parseObject("""
            {
                "name":"小红",
                "age":19,
                "likes":["电影","音乐","小说","帅哥"],
                "不 喜 欢":["足球","游戏"],
                "friends":{
                    "tom":{
                        "name":"小明",
                        "age":22
                    },
                    "leo":{
                        "name":"李傲天",
                        "age":26
                    }
                }
            }
            """);
    @Test
    public void testData() throws Exception {
        String template = """
                {                
                    "test01":"{@name}同学{@age}岁,是{= @age > 18 ? '成':'未成' }年人",
                    "test03":"{=@name}喜欢{@likes[0]}、{=@likes[1]}、{=@'likes[2]'}、{=@.'likes[3]'}",
                    "test02":"{=data.name}讨厌{@['不 喜 欢'][0]}、{=@\\"['不 喜 欢'][1]\\"}",
                    "test04":"朋友{@friends.leo.name},{@friends[*][?(@.name=='李傲天')].age[0]}岁",
                      "1": 1,
                      "3": 3,
                      "2": 2,
                    "test05":{
                        "@": {
                            "name": "小红红",
                            "age": 20
                        },
                        "test": "{@name}同学{@age}岁",
                        "list1": [
                            {    
                              "s1": "{@name}同学{@age}岁",
                            },{
                              "s1": "{% //1/0 %}",
                            }
                        ]
                     }
                }
                """;

        //Object result = JSONPath.eval(data,"$.friends[*][?(@.name=='李傲天')]");

       // Object result = converter.convert("{@friends[?(@.name=='李傲天')].age}",data);
       Object result = converter.render(JSONObject.parseObject(template),data);
       Assertions.assertEquals(JSONPath.eval(result,"test01"), "小红同学19岁,是成年人");
    }

}
