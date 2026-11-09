package fun.commons.template4j.core.beans;

import fun.commons.template4j.core.SelfObject;

public class FuncRender {

    //返回第一个非空值

    final SelfObject self;

    public FuncRender(SelfObject self) {
        this.self = self;
    }

    public Object call(Object template) throws Exception {
        return self.convertSelf(template,"render()") ;
    }

}