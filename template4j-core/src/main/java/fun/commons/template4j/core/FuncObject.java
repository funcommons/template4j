package fun.commons.template4j.core;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FuncObject {

    public final String name;
    public final Object template;

    @Getter @Setter
    public SelfObject self;


    public FuncObject(String name, Object template) {
        this.name = name;
        this.template = template;
    }

    //如何防止死循环？
    public Object call(Object... args) throws RenderException {

        List<Object> argList = new ArrayList<Object>(){
            @Override
            public Object get(int index) {
                return get(index,null);
            }
            public Object get(int index,Object defaultValue) {
                if (index >= size()) {
                    return defaultValue;
                }else {
                    Object value = super.get(index);
                    return value != null ? value : defaultValue;
                }
            }
            public Object call(int index) {
                return get(index);
            }
            public Object call(int index,Object defaultValue) {
                return get(index,defaultValue);
            }

        };

        argList.addAll(Arrays.asList(args));

        // 如果成功获取名称为name的锁
        if (self.lock(name)) {
            // 将args存储为名为"_args"的变量
            self.putVar("_args", argList);
            // 使用template和name进行渲染，并将结果存储在result变量中
            Object result = self.convertSelf(template, name);
            // 从变量中移除"_args"
            self.removeVar("_args");
            // 释放名称为name的锁
            self.unLock(name);

            return result;

        } else {
            // 如果无法获取名称为name的锁
            return null;
        }
    }



    public Object get(String name) throws Exception {
        return call(name);
    }


}