package fun.commons.template4j.core.components;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.template4j.core.RenderComponent;
import fun.commons.template4j.core.RenderException;
import org.apache.commons.lang3.BooleanUtils;

/**
 * Try component: 包装子模板执行, 捕获任意 Exception 后切到 fallback / onError 分支。
 *
 * <p>典型用法:
 * <pre>
 * {
 *   "@class": "try",
 *   "select": { "@class": "rest", "url": "/flaky" },
 *   "fallback": { "default": true },
 *   "onError": { "@class": "rest", "url": "/audit", "method": "post", "data": { "msg": "{$error.message}" } },
 *   "rethrow": false
 * }
 * </pre>
 *
 * <p>规则:
 * <ul>
 *   <li>select 抛任何 Exception → 进入降级流程</li>
 *   <li>$error 提供 message / cause / path (不暴露 original Throwable)</li>
 *   <li>先执行 onError (副作用), 再返回 fallback</li>
 *   <li>仅 onError 无 fallback: 执行 onError 后返回 null</li>
 *   <li>仅 fallback 无 onError: 直接返回 fallback</li>
 *   <li>两者都无: 异常正常上抛 (等价于未使用 try)</li>
 *   <li>rethrow=true: fallback 不渲染, onError 仍执行 (用于"先记录再上抛")</li>
 * </ul>
 */
public class TryRenderer implements RenderComponent {

    @Override
    public String name() {
        return "try";
    }

    @Override
    public Object render(RenderContext context) throws Exception {
        context.assertField("select");

        Object selectTemplate = context.map.get("select");
        boolean hasFallback = context.containsField("fallback");
        boolean hasOnError = context.containsField("onError");
        boolean rethrow = parseBooleanField(context, "rethrow", false);

        try {
            return context.render(selectTemplate);
        } catch (Exception ex) {
            Throwable original = unwrap(ex);

            // 副作用先行
            if (hasOnError) {
                try {
                    context.renderField("onError", "error", buildError(original, context));
                } catch (Exception innerEx) {
                    // onError 自身失败: 把原异常作为 suppression, 改抛 inner
                    if (rethrow) {
                        throw new RenderException(context.self.path(),
                                "@class:try",
                                "onError handler failed",
                                innerEx);
                    }
                    if (!hasFallback) {
                        throw new RenderException(context.self.path(),
                                "@class:try",
                                "onError handler failed",
                                innerEx);
                    }
                    // 有 fallback: 容忍 onError 失败, 继续走 fallback
                }
            }

            if (hasFallback && !rethrow) {
                return context.renderField("fallback", "error", buildError(original, context));
            }

            if (rethrow || (!hasFallback && !hasOnError)) {
                if (ex instanceof RenderException re) throw re;
                throw new RenderException(context.self.path(),
                        "@class:try",
                        "select failed: " + original.getMessage(),
                        original);
            }

            // 仅 onError 路径: onError 已执行, 无 fallback 无 rethrow → 返回 null
            return null;
        }
    }

    private static JSONObject buildError(Throwable original, RenderContext context) {
        JSONObject err = new JSONObject();
        err.put("message", original.getMessage() != null
                ? original.getMessage() : original.getClass().getSimpleName());
        err.put("cause", original.getClass().getSimpleName());
        err.put("path", context.self.path());
        // 注: 不暴露 original Throwable 对象, 避免向模板泄露内部堆栈/类路径。
        return err;
    }

    private static Throwable unwrap(Exception ex) {
        Throwable c = ex.getCause();
        return c != null ? c : ex;
    }

    private static boolean parseBooleanField(RenderContext context, String field, boolean fallback) {
        if (!context.containsField(field)) return fallback;
        try {
            Object v = context.renderField(field);
            if (v == null) return fallback;
            Boolean b = BooleanUtils.toBooleanObject(String.valueOf(v));
            return b == null ? fallback : b;
        } catch (Exception e) {
            return fallback;
        }
    }
}
