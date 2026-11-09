package fun.commons.template4j.utils;

import fun.commons.template4j.utils.UString;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Covers missed branches in UString:
 * - isBesieged: starts with left but does not end with right (and vice versa)
 * - relativeUrl: else branch where rightPaths.get(0) is not blank (enters .. handling)
 * - lambda$relativeUrl$0: "..".equals(s) is true but leftPaths is empty (no removal)
 */
public class UStringBranchTest {

    // ========== isBesieged: partial match branches ==========

    @Test
    public void testIsBesiegedStartsWithLeftButNotEndsWithRight() {
        // s.startsWith(left) is true, s.endsWith(right) is false
        Assertions.assertFalse(UString.isBesieged("<&hello world", "<&", "&>"));
    }

    @Test
    public void testIsBesiegedEndsWithRightButNotStartsWithLeft() {
        // s.startsWith(left) is false, s.endsWith(right) is true
        Assertions.assertFalse(UString.isBesieged("hello&>", "<&", "&>"));
    }

    @Test
    public void testIsBesiegedNeitherStartsNorEnds() {
        // Both false
        Assertions.assertFalse(UString.isBesieged("hello", "<&", "&>"));
    }

    // ========== relativeUrl: rightPaths.get(0) is not blank ==========

    @Test
    public void testRelativeUrlRelativePathWithDotDotAndNonEmptyLeftPaths() {
        // url does NOT start with /, so rightPaths.get(0) is NOT blank
        // This enters the else branch at line 68, which processes ".." entries
        // leftPaths is non-empty, so ".." removes from leftPaths
        String result = UString.relativeUrl("/a/b/c/", "../d.json");
        Assertions.assertEquals(result, "/a/b/d.json");
    }

    @Test
    public void testRelativeUrlRelativePathWithDotDotAndEmptyLeftPaths() {
        // When leftPaths is empty and url has "..", the ".." removal is skipped
        // because !leftPaths.isEmpty() is false
        // Use a rootUrl with no path segments so leftPaths becomes empty
        String result = UString.relativeUrl("classpath:", "../d.json");
        // leftPaths is empty after splitting "classpath:" path portion
        // ".." in rightPaths won't remove anything from empty leftPaths
        Assertions.assertNotNull(result);
        // The ".." and "." entries are removed by the removeIf at line 79
        Assertions.assertTrue(result.contains("d.json"));
    }

    @Test
    public void testRelativeUrlRelativePathNoDotDot() {
        // rightPaths.get(0) is not blank, and no ".." entries
        // The forEach at line 69 does nothing because "sub" != ".."
        String result = UString.relativeUrl("/a/b/", "sub/file.json");
        Assertions.assertTrue(result.endsWith("sub/file.json"));
    }

    @Test
    public void testRelativeUrlMultipleDotDot() {
        // Multiple ".." entries, each removing from leftPaths
        String result = UString.relativeUrl("/a/b/c/d/", "../../e.json");
        // a/b/c/d/ + ../../e.json = a/b/e.json
        Assertions.assertEquals(result, "/a/b/e.json");
    }

    @Test
    public void testRelativeUrlDotDotExceedsLeftPaths() {
        // More ".." than leftPaths — leftPaths becomes empty, extra ".." removed by removeIf
        String result = UString.relativeUrl("/a/", "../../../d.json");
        // /a/ + ../../../d.json: first .. removes "a", leftPaths empty
        // remaining .. are not removed (leftPaths empty) but removed by removeIf
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.contains("d.json"));
    }

    @Test
    public void testRelativeUrlWithDotSegments() {
        // url contains "." segments — removed by removeIf at line 79
        String result = UString.relativeUrl("/a/b/", "./c.json");
        Assertions.assertTrue(result.endsWith("c.json"));
        Assertions.assertFalse(result.contains("./"));
    }
}
