package fun.commons.template4j.core;

import com.alibaba.fastjson2.JSONPath;
import fun.commons.template4j.utils.UString;
import org.apache.commons.lang3.StringUtils;

import javax.script.ScriptException;

/**
 * 表达式求值器：负责解析 {@code <&...&>}, {@code {#...#}}, {@code {@...}}, {@code {$...}}, {@code {=...}}, {@code {%...%}} 等占位符语法。
 * 从 JsonRenderer 中拆分以集中管理语法分发逻辑。
 */
final class ExpressionEvaluator {

    private final ScriptEnvironment scripts;
    private final RenderEngine.Renderer renderer;

    ExpressionEvaluator(ScriptEnvironment scripts, RenderEngine.Renderer renderer) {
        this.scripts = scripts;
        this.renderer = renderer;
    }

    Object evaluate(String expression, SelfObject self, String name) throws RenderException {
        try {
            if (UString.isBesieged(expression, "<&", "&>")) {
                return UString.extractBesieged(expression, "<&", "&>");
            }
            if (UString.isBesieged(expression, "{#", "#}")) {
                return UString.extractBesieged(expression, "{#", "#}");
            }
            if (UString.isBesieged(expression, "{@", "}")) {
                String expr = UString.extractBesieged(expression, "{@", "}");
                expr = StringUtils.isBlank(expr) ? "$" : expr;
                if (UString.isBesieged(expr, "'", "'")) expr = UString.extractBesieged(expr, "'", "'");
                if (UString.isBesieged(expr, "\"", "\"")) expr = UString.extractBesieged(expr, "\"", "\"");
                return JSONPath.eval(self.getData(), expr);
            }
            if (UString.isBesieged(expression, "{$", "}")) {
                String expr = UString.extractBesieged(expression, "{$", "}");
                String varName = extractVarName(expr);
                String varPath = StringUtils.substringAfter(expr, varName);
                if (UString.isBesieged(varPath, "'", "'")) varPath = UString.extractBesieged(varPath, "'", "'");
                if (UString.isBesieged(varPath, "\"", "\"")) varPath = UString.extractBesieged(varPath, "\"", "\"");
                Object result = self.vars(varName);
                if (result != null && StringUtils.isNotBlank(varPath)) {
                    result = JSONPath.eval(result, varPath);
                }
                return result;
            }

            String script = expression;
            boolean isScriptExpr = UString.isBesieged(expression, "{=", "}")
                    || UString.isBesieged(expression, "{%", "%}");
            if (UString.isBesieged(expression, "{=", "}")) {
                script = UString.extractBesieged(expression, "{=", "}");
            } else if (UString.isBesieged(expression, "{%", "%}")) {
                script = UString.extractBesieged(expression, "{%", "%}");
            }
            while (UString.isBesieged(script, "(", ")")) {
                script = UString.extractBesieged(script, "(", ")");
            }
            script = ScriptUtil.codeReplaceAt(script);

            long t0 = isScriptExpr ? System.nanoTime() : 0L;
            Object result = scripts.eval(script, self);
            if (isScriptExpr && renderer != null) {
                long durMs = (System.nanoTime() - t0) / 1_000_000;
                renderer.trace(RenderEngine.TraceEvent.of("SCRIPT", self.path(),
                        expression, durMs, result));
            }
            if (result instanceof Exception e) {
                throw new RenderException(self.path(), name, "script return exception : " + expression, e);
            }
            return result;
        } catch (ScriptException se) {
            throw new RenderException(self.path(), name, "script error by : " + expression, se);
        } catch (RenderException re) {
            throw re;
        } catch (Exception e) {
            throw new RenderException(self.path(), name, "script error by : " + expression, e);
        }
    }

    /**
     * 从 `{$varName.path}` 的 expr 中提取变量名(不含 path 部分)。
     *
     * <p>规则:首字符必须是字母或下划线,后续是字母/数字/下划线。遇到第一个非
     * word 字符(通常是 `.`、`[`、`'`、`"` 等)就停。
     */
    private static String extractVarName(String expr) {
        if (expr == null || expr.isEmpty()) return "";
        char first = expr.charAt(0);
        if (!(Character.isLetter(first) || first == '_')) return "";
        int i = 1;
        while (i < expr.length()) {
            char c = expr.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_')) break;
            i++;
        }
        return expr.substring(0, i);
    }
}
