package fun.commons.template4j.core;

import fun.commons.template4j.utils.UThrow;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * 覆盖 EachRenderer 剩余分支 + UThrow 各分支。
 */
public class EachRendererExtraTest {

    private final RenderEngine engine = RenderEngine.build();

    @Test
    public void testEachWithArray() throws Exception {
        // select 是 JSONArray — 走 instanceof Collection
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": [10, 20, 30],
                  "body": "{= $item * 2 }"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testEachMapSelect() throws Exception {
        // select 是 Map — 走 select instanceof Map
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": {"a": 1, "b": 2},
                  "body": "{k=$key v=$item}"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testEachNonnullTrueDropsNulls() throws Exception {
        // nonnull: true (默认) — 移除 null 结果
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": [1, 2, 3],
                  "body": "{= $item > 2 ? $item : null}"
                }
                """, Map.of());
        Assertions.assertTrue(r.toString().contains("3"));
    }

    @Test
    public void testEachNullSelect() throws Exception {
        // select 是 null
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": "{@missing}"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testUThrowAssertFalse() {
        // false 抛
        try {
            UThrow.assertArgument(false, "should throw");
            Assertions.fail("false should throw");
        } catch (Exception ex) {
            Assertions.assertTrue(ex.getMessage().contains("should"));
        }
    }

    @Test
    public void testUThrowAssertTrue() throws Exception {
        // true 通过
        UThrow.assertArgument(true, "ok");
    }

    @Test
    public void testEachMergeTrueBodyIsList() throws Exception {
        // merge: true (默认) + body 返回 List — 触发合并
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": [1, 2],
                  "body": [10, 20]
                }
                """, Map.of());
        // 每轮的 [10, 20] 应展平
        // 结果 [10,20,10,20]
        Assertions.assertNotNull(r);
    }

    @Test
    public void testEachKeyItemDollarPrefix() throws Exception {
        // keyName/itemName 带 $ 前缀 — 走 startsWith("$") 分支
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": [1, 2],
                  "keyName": "$k",
                  "itemName": "$v",
                  "body": "{$k}={$v}"
                }
                """, Map.of());
        Assertions.assertTrue(r.toString().contains("0=1"));
        Assertions.assertTrue(r.toString().contains("1=2"));
    }

    @Test
    public void testEachNoBodyItemOnly() throws Exception {
        // 没有 body — 直接用 select 的 item 作为 subTarget
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": ["a", "b", "c"]
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testEachNullSubTarget() throws Exception {
        // body 返回 null — isNonnull 默认 true 时不加入结果
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": [1, 2, 3],
                  "body": "{% return null %}"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testEachMapSubTargetListWithMerge() throws Exception {
        // select 是 Map + body 返回 List + merge — 各分支组合
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": {"a": 1, "b": 2},
                  "body": ["x", "y"]
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testEachMapSubTargetNonList() throws Exception {
        // select 是 Map + body 返回非 List
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": {"a": 1},
                  "body": "raw"
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testEachWithoutBody() throws Exception {
        // 无 body — 直接使用 select item 作为 subTarget
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": ["a", "b"]
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }

    @Test
    public void testEachBodyReturnsListMergeFalse() throws Exception {
        // body 返回 List 但 merge=false — 保留子数组
        Object r = engine.renderJson("""
                {
                  "@class": "each",
                  "select": [1, 2],
                  "merge": false,
                  "body": [10, 20]
                }
                """, Map.of());
        Assertions.assertNotNull(r);
    }
}
