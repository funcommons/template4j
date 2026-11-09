package fun.commons.template4j.core.beans;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.Map;

public class FuncFne {

    //返回第一个非空值
    public Object call(Object... values) {

        if (values != null) {

            for (int i = 0 ; i < values.length ; i ++) {

                final Object val = values[i];

                if (val == null){
                    continue;
                }

                if (val instanceof CharSequence) {

                    if (StringUtils.isNotBlank((CharSequence) val)) {
                        return val;
                    }

                }else if (val instanceof Collection) {

                    if (!((Collection) val).isEmpty()) {
                        return val;
                    }
                }else if (val instanceof Map) {

                    if (!((Map) val).isEmpty()) {
                        return val;
                    }
                }else {
                    return val;
                }
            }
        }

        return null;
    }
}