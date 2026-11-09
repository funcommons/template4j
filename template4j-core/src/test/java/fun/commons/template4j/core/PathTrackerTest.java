package fun.commons.template4j.core;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Coverage for {@link PathTracker}. The tracker walks the parent chain of
 * SelfObject to build a slash-joined path used in error messages.
 *
 * Edge cases worth pinning down:
 *   - empty / blank self.name is skipped (parent path replaces, no trailing /)
 *   - the top-level SelfObject has no parent — its name is returned alone
 *   - blank path falls back to ""
 */
public class PathTrackerTest {

    private static SelfObject selfWith(String name, SelfObject parent) {
        // Construct via reflection-free path: parent can be null for the root.
        // SelfObject's constructor stores name verbatim; renderer field is unused
        // by PathTracker.
        if (parent == null) {
            return new SelfObject(null, new HashMap<>());
        }
        SelfObject child = new SelfObject(parent, name);
        return child;
    }

    @Test
    public void build_returnsEmptyForUnnamedRoot() {
        SelfObject root = selfWith("", null);
        // root.name is "" by default
        assertEquals(PathTracker.build(root), "");
    }

    @Test
    public void build_returnsNameAlone_whenNoParent() {
        SelfObject root = selfWith("", null);
        root.name = "top";
        assertEquals(PathTracker.build(root), "top");
    }

    @Test
    public void build_joinsParentAndChildWithSlash() {
        SelfObject root = selfWith("", null);
        root.name = "outer";
        SelfObject mid = selfWith("mid", root);
        SelfObject leaf = selfWith("inner", mid);
        assertEquals(PathTracker.build(leaf), "outer/mid/inner");
    }

    @Test
    public void build_skipsBlankNameInChain() {
        SelfObject root = selfWith("", null);
        root.name = "outer";
        SelfObject blankName = selfWith("", root);     // name "" should be skipped
        SelfObject leaf = selfWith("inner", blankName);
        assertEquals(PathTracker.build(leaf), "outer/inner");
    }

    @Test
    public void build_handlesAllBlankChain() {
        SelfObject root = selfWith("", null);
        SelfObject mid = selfWith("   ", root); // whitespace-only
        // StringUtils.isNotBlank("   ") = false, so name is skipped
        // After skipping, path = build(parent) = "" (parent.name also blank)
        String result = PathTracker.build(mid);
        // Either "" (root blank) — confirm via StringUtils defaultIfBlank contract
        assertEquals(StringUtils.defaultIfBlank("", ""), result);
    }

    @Test
    public void rootOf_returnsTopmostSelfObject() {
        SelfObject root = selfWith("", null);
        root.name = "R";
        SelfObject mid = selfWith("M", root);
        SelfObject leaf = selfWith("L", mid);
        assertEquals(root, PathTracker.rootOf(leaf));
        assertEquals(root, PathTracker.rootOf(root));
    }
}
