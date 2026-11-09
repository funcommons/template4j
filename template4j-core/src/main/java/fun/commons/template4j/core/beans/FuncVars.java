package fun.commons.template4j.core.beans;

import fun.commons.template4j.core.RenderException;
import fun.commons.template4j.core.SelfObject;

public class FuncVars {

    public final SelfObject self;

    public FuncVars(SelfObject self) {
        this.self = self;
    }

    public Object get(String name) throws Exception {
        return self.vars(name);
    }

    public Object call(String name) throws Exception {
        return self.vars(name);
    }

    public void put(String name, Object value) throws RenderException {
        self.putVar(name, value);
    }

    public Object putAt(String name, Object value) throws RenderException {
        self.putVar(name, value);
        return value;
    }

}