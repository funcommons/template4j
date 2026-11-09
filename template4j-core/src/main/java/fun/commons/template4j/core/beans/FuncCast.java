package fun.commons.template4j.core.beans;


import fun.commons.template4j.utils.UType;

public  class FuncCast {

    public Object call(Object value, String type) {
        return UType.castValue(value, type);
    }
    public Object call(Object value, Class<?> type) {
        return UType.castValue(value, type);
    }

}