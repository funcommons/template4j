package fun.commons.template4j.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * 直接构造 SelfObject 来覆盖 getRoot/path 各分支。
 */
public class SelfObjectUnitTest {

    @Test
    public void testGetRootAtTop() {
        // 顶层 — parent == null → returns this
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        Assertions.assertSame(root, root.getRoot());
    }

    @Test
    public void testGetRootDeepChain() {
        // 多级嵌套 — parent != null → recurses
        JsonRenderer renderer = new JsonRenderer();
        SelfObject level1 = new SelfObject(renderer, new HashMap<>());
        SelfObject level2 = new SelfObject(level1, "a");
        SelfObject level3 = new SelfObject(level2, "b");

        // getRoot() 应从 level3 跳到 level1（顶级 parent=null）
        Assertions.assertSame(level1, level3.getRoot());
        Assertions.assertSame(level1, level2.getRoot());
        Assertions.assertSame(level1, level1.getRoot());
    }

    @Test
    public void testPathTopLevel() {
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        // 顶层 path 为空字符串
        Assertions.assertEquals(root.path(), "");
    }

    @Test
    public void testPathNested() {
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        SelfObject level1 = new SelfObject(root, "outer");
        SelfObject level2 = new SelfObject(level1, "inner");
        // 嵌套 path 应是 "/outer/inner"
        Assertions.assertEquals(level2.path(), "/outer/inner");
    }

    @Test
    public void testPathMiddleWithBlank() {
        // 中间层 name 为空时，path 不拼接 "/"
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        SelfObject blank = new SelfObject(root, "");
        SelfObject child = new SelfObject(blank, "x");
        // parent.name 为空时 child.path() == parent.path() + "/x"
        Assertions.assertEquals(child.path(), "/x");
    }

    @Test
    public void testGetResourceUrlSelfOnly() {
        // 自身有 resourceUrl
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        root.setResourceUrl("/a.json");
        Assertions.assertEquals(root.getResourceUrl(), "/a.json");
    }

    @Test
    public void testGetResourceUrlInheritFromParent() {
        // parent 有，自己没有 → 继承
        JsonRenderer renderer = new JsonRenderer();
        SelfObject root = new SelfObject(renderer, new HashMap<>());
        root.setResourceUrl("/root.json");
        SelfObject child = new SelfObject(root, "x");
        Assertions.assertEquals(child.getResourceUrl(), "/root.json");
    }
}
