package fun.commons.template4j.utils;

import fun.commons.template4j.utils.UString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 覆盖本地 UString 各方法。
 */
public class UStringTest {

    @Test
    public void testIsBesieged() {
        Assertions.assertTrue(UString.isBesieged("<&hello&>", "<&", "&>"));
        Assertions.assertFalse(UString.isBesieged("hello", "<&", "&>"));
        Assertions.assertFalse(UString.isBesieged(null, "<&", "&>"));
        Assertions.assertFalse(UString.isBesieged("", "<&", "&>"));
    }

    @Test
    public void testExtractBesieged() {
        Assertions.assertEquals(UString.extractBesieged("<&hello&>", "<&", "&>"), "hello");
        Assertions.assertEquals(UString.extractBesieged("<&abc&def&>", "<&", "&>"), "abc&def");
    }

    @Test
    public void testRelativeUrlAbsolute() {
        // 已经是绝对 URL，直接返回
        Assertions.assertEquals(UString.relativeUrl("https://base.com", "https://example.com/path"), "https://example.com/path");
    }

    @Test
    public void testRelativeUrlProtocolRelative() {
        // 协议相对 URL（// 开头）会被合并到 base 的协议下
        Assertions.assertEquals(UString.relativeUrl("https://base.com", "//example.com/path"), "https://base.com/example.com/path");
    }

    @Test
    public void testRelativeUrlRelativePath() {
        // 相对路径解析
        String result = UString.relativeUrl("/base/dir/", "sub/file.json");
        Assertions.assertTrue(result.endsWith("sub/file.json"));
    }

    @Test
    public void testRelativeUrlDotDot() {
        // ../ 处理
        String result = UString.relativeUrl("/base/dir/sub/", "../file.json");
        Assertions.assertTrue(result.endsWith("base/dir/file.json"));
    }

    @Test
    public void testRelativeUrlBlankRoot() {
        // 空 root 时直接返回 url
        Assertions.assertEquals(UString.relativeUrl("", "data.json"), "data.json");
        Assertions.assertEquals(UString.relativeUrl(null, "data.json"), "data.json");
    }

    @Test
    public void testRelativeUrlCustomProtocol() {
        // rootUrl 是 classpath:/path 形式 — 触发 \w+: 分支
        String result = UString.relativeUrl("classpath:/a/b/", "c.json");
        // classpath:/a/b/ + c.json
        Assertions.assertTrue(result.contains("c.json"));
    }

    @Test
    public void testRelativeUrlAbsolutePath() {
        // rootUrl 是 /a/b/ 形式 — 触发 [/\\]+ 分支
        String result = UString.relativeUrl("/a/b/", "c.json");
        Assertions.assertTrue(result.endsWith("c.json"));
        Assertions.assertTrue(result.startsWith("/"));
    }

    @Test
    public void testRelativeUrlWithDotDot() throws Exception {
        // url 里有 ../ 时 — 触发 .. 移除 leftPaths
        String result = UString.relativeUrl("/a/b/c/", "../d.json");
        // a/b/c/ + ../d.json = a/b/d.json
        Assertions.assertEquals(result, "/a/b/d.json");
    }

    @Test
    public void testRelativeUrlEmptyUrlPath() {
        // url 是绝对 URL：直接返回
        Assertions.assertEquals(UString.relativeUrl("/a/b/", "https://other.com/x.json"), "https://other.com/x.json");
    }

    @Test
    public void testRelativeUrlBlankLeftPath() throws Exception {
        // url 是 "/x.json"（以斜杠开头）— rightPaths.get(0) is blank
        // 触发 if (StringUtils.isBlank(rightPaths.get(0))) { leftPaths.clear(); }
        String result = UString.relativeUrl("/base/dir/", "/x.json");
        Assertions.assertTrue(result.endsWith("/x.json"));
    }

    @Test
    public void testRelativeUrlInvalidRootThrows() {
        // rootUrl 是 HTTPS 但格式无效 — 触发 URISyntaxException
        try {
            UString.relativeUrl("http://[invalid", "x.json");
        Assertions.fail("expected Exception");
    } catch (Exception ex) {
            // expected RuntimeException wrapping URISyntaxException
        }
    }

    @Test
    public void testRelativeUrlHttpProtocol() {
        // http:// 协议
        String result = UString.relativeUrl("http://example.com/base/", "sub.json");
        Assertions.assertTrue(result.endsWith("sub.json"));
        Assertions.assertTrue(result.contains("example.com"));
    }

    @Test
    public void testRelativeUrlFtpProtocol() {
        // ftp:// 协议
        String result = UString.relativeUrl("ftp://server/base/", "file.json");
        Assertions.assertTrue(result.endsWith("file.json"));
    }
}
