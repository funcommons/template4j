package fun.commons.template4j.core;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GroovyImports {


    private final List<String> imports = new ArrayList<>();


    public GroovyImports() {

        put("java.math.*");
        put("java.text.*");
        put("java.io.*");
        put("java.net.*");
        put("java.util.*");
        put("java.util.regex.*");
        put("com.alibaba.fastjson2.*");
        put("org.apache.commons.lang3.*");
        put("org.apache.commons.lang3.time.*");

    }

    public void put(String item) {

        String s = StringUtils.trim(item);
        String cls = StringUtils.substringAfterLast(s, ".");
        String path = StringUtils.substringBeforeLast(s, ".");

        if (!imports.contains(path + ".*")) {
            if ("*".equals(cls)) {
                imports.removeIf(p -> p.matches(path.replaceAll("\\.", "\\.") + "\\.\\w+"));
            }
            imports.add(s);
        }

    }


    public void putAll(List<String> imports) {
        if (imports != null) {
            for (String item : imports) {
                put(item);
            }
        }
    }

    public void putCode(String importsCode) {
        if (StringUtils.isNotBlank(importsCode)) {
            Pattern pattern = Pattern.compile("\\s*import\\s*([a-z0-9A-Z.]+)\\s*;?\\s*(//.+)?[\r\n]+");
            Matcher matcher = pattern.matcher(importsCode);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                imports.add(matcher.group(1));
            }
        }
    }


    public String toCode() {
        StringBuilder sb = new StringBuilder();
        for (String item : imports) {
            if (StringUtils.isNotBlank(item)) {
                sb.append("import ").append(item).append(";\r\n");
            }
        }
        return sb.toString();
    }

}
