package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScriptUtilTest {




    @Test
    public  void TestXX(){
        String input = "This is a {#sample \r\n xx #} string with {#wrapped#} parts.";

        // 定义正则表达式
        String regex = "\\{#([\\s\\S]*?)#\\}";

        // 编译正则表达式
        Pattern pattern = Pattern.compile(regex);

        // 创建Matcher对象
        Matcher matcher = pattern.matcher(input);

        // 查找匹配项并输出结果
        while (matcher.find()) {
            String matchedString = matcher.group(1);
        }
    }
    @Test
    public  void TestCodeReplaceAt(){

        // 测试parseAndReplace方法
        {
            String src = """
                    // 这是一个测试脚本 @
                    /*
                    * 这是一个多行注释 @parameters.id
                    * 这是一个多行注释 @parameters.name
                    */
                    int id = 1 ; // @id
                    String name1 = "中国龙" ; // @name
                    String name2 = "//@name" + @id;
                    String name3 = @name + // @name
                           /* " */ @.id ; // @id "
                    String d = @+do(@,@)+@;
                    if (@&&@||@){return @}
                    do(@[0],@."['中文 2']",@'name.id')
                    """;

            String des = """
                    // 这是一个测试脚本 @
                    /*
                    * 这是一个多行注释 @parameters.id
                    * 这是一个多行注释 @parameters.name
                    */
                    int id = 1 ; // @id
                    String name1 = "中国龙" ; // @name
                    String name2 = "//@name" + data.id;
                    String name3 = data.name + // @name
                           /* " */ data.id ; // @id "
                    String d = data('$')+do(data('$'),data('$'))+data('$');
                    if (data('$')&&data('$')||data('$')){return data('$')}
                    do(data[0],data."['中文 2']",data.'name.id')
                    """;

            String result = ScriptUtil.codeReplaceAt(src);
            Assertions.assertEquals(des, result);
        }
        // 测试//注释
        {
            String src = "String a = 1 ; // @name ";
            String des = src;
            Assertions.assertEquals(des, ScriptUtil.codeReplaceAt(src));
        }
        // 测试//注释
        {
            String src = "String name = \"//@name\" + @id ";
            String des = "String name = \"//@name\" + data.id ";
            Assertions.assertEquals(des, ScriptUtil.codeReplaceAt(src));
        }
        {
            String src = "String name = '//@name' + @id ";
            String des = "String name = '//@name' + data.id ";
            Assertions.assertEquals(des, ScriptUtil.codeReplaceAt(src));
        }
        // 新增：@..x JSONPath deepScan 改写为 data("..x") — 避免 Groovy spread 算子
        {
            String src = "return @..name";
            String des = "return data(\"..name\")";
            Assertions.assertEquals(des, ScriptUtil.codeReplaceAt(src));
        }
        // 新增：@.x 与 @..x 不混淆
        {
            String src = "return @.a.@..b";
            String des = "return data.a.data(\"..b\")";
            Assertions.assertEquals(des, ScriptUtil.codeReplaceAt(src));
        }


    }
}
