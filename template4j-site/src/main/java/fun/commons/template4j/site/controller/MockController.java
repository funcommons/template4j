package fun.commons.template4j.site.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 演示用 mock 端点: 供首页 Hero / Playground 预设 / 文档示例的 rest 组件调用,
 * 让模板可以"开箱即渲染"。数据确定性, 无外部依赖。
 */
@RestController
public class MockController {

    @GetMapping({"/users/{id}", "/api/user"})
    public Map<String, Object> user(@PathVariable(required = false) String id,
                                    @RequestParam(required = false) String idParam) {
        String uid = id != null ? id : idParam;
        return Map.of(
                "id", uid != null ? uid : "u-1",
                "login", "alice",
                "name", "alice",
                "vip", true,
                "perks", List.of("free-shipping", "discount"));
    }

    @GetMapping({"/users/{id}/orders", "/api/orders"})
    public List<Map<String, Object>> orders(@PathVariable(required = false) String id,
                                            @RequestParam(required = false) String idParam) {
        String uid = id != null ? id : (idParam != null ? idParam : "u-1");
        return List.of(
                Map.of("orderId", "o-1001", "userId", uid, "amount", 300, "status", "paid"),
                Map.of("orderId", "o-1002", "userId", uid, "amount", 129, "status", "shipped"));
    }

    /** 恒定失败, 演示 try 组件的 fallback 能力。 */
    @GetMapping("/flaky")
    public Map<String, Object> flaky() {
        throw new IllegalStateException("flaky upstream is down (by design)");
    }
}
