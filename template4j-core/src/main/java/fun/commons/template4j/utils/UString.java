package fun.commons.template4j.utils;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.substringAfter;
import static org.apache.commons.lang3.StringUtils.substringBeforeLast;

public class UString {

    private static final Pattern SCHEME_HEAD = Pattern.compile("^\\w+:[\\s/\\\\]*");
    private static final Pattern LEADING_SLASH = Pattern.compile("^[/\\\\]+");



    public static boolean isBesieged(String source,String left,String right) {
        if (source == null) {
            return false;
        }

        String s = source.trim();
        return s.startsWith(left) && s.endsWith(right);
    }


    public static String extractBesieged(String source,String left,String right) {
        String substring = substringAfter(source,left);
        String result = substringBeforeLast(substring, right);
        return result;
    }

    /**
     * 相对路径转换成绝对路径
     * @param rootUrl
     * @param url
     * @return
     */
    public static String relativeUrl(String rootUrl, String url) {
        String result = url;
        if (StringUtils.isNotBlank(rootUrl) && !url.matches("^\\w+:[/\\\\]*[.[^.]]+")) {
            String head = "";
            String leftUrl = rootUrl;
            if (rootUrl.matches("^(https?|ftp|file)://.*$")){
                try {
                    URI rootURI = new URI(rootUrl);
                    leftUrl = rootURI.getPath();
                    head = new URI(rootURI.getScheme(),  rootURI.getUserInfo(), rootURI.getHost(), rootURI.getPort(), null, null, null).toString();
                    head = head + "/";
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            } else if (rootUrl.matches("^\\w+:.*$")){
                head = extractHead(rootUrl, SCHEME_HEAD);
                leftUrl = StringUtils.substringAfter(rootUrl, head);
            } else if (rootUrl.matches("^[/\\\\]+.*$")) {
                head = extractHead(rootUrl, LEADING_SLASH);
                leftUrl = StringUtils.substringAfter(rootUrl, head);
            }

            List<String> leftPaths = new LinkedList<>(Arrays.asList((leftUrl + "-").split("[/\\\\]+")));
            leftPaths.remove(leftPaths.size() - 1);

            List<String> rightPaths = new LinkedList<>(Arrays.asList(url.split("[/\\\\]+")));
            // 处理相对路径
            if (StringUtils.isBlank(rightPaths.get(0))) {
                leftPaths.clear();
            }else{
                rightPaths.forEach(s -> {
                    if ("..".equals(s) && !leftPaths.isEmpty()) {
                        leftPaths.remove(leftPaths.size() - 1);
                    }
                });
            }
            // 合并 并 输出为 string
            LinkedList<String> fullPaths = new LinkedList<>(leftPaths);
            fullPaths.addAll(rightPaths);
            //删除所有为.或空的项
            fullPaths.removeIf(s -> "..".equals(s) || ".".equals(s) || StringUtils.isEmpty(s));
            result = head + String.join("/", fullPaths);
        }
        return result;
    }

    private static String extractHead(String source, Pattern pattern) {
        Matcher m = pattern.matcher(source);
        return m.find() ? m.group() : "";
    }

}
