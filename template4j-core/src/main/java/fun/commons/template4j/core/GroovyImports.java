package fun.commons.template4j.core;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groovy 脚本编译前缀的 import 集, 按<b>来源</b>分两个集合记录:
 * <ul>
 *   <li><b>默认集</b> ({@link #defaultImports}): 构造时写入的引擎内置导入, 之后只读。</li>
 *   <li><b>用户显式集</b> ({@link #userImports}): {@link #put} / {@link #putAll} / {@link #putCode}
 *       等用户途径写入的条目。仅在用户显式集<b>内部</b>去重 (星号吞同类条目等规则与历史一致),
 *       不再与默认集去重 — 用户显式 {@code java.io.File} 与默认 {@code java.io.*} 允许并存。</li>
 * </ul>
 *
 * <p>拆分两个集合的动机: 旧实现把所有条目放在一个 List 里, {@code put("java.io.File")} 会被
 * {@code !imports.contains("java.io.*")} 判重吞成 no-op, 用户显式导入静默丢失。
 * {@link #toCode()} 输出两集合并 (默认在前、用户在后, 完全相同的字符串只输出一次),
 * 含默认集的 {@code java.io.*} / {@code java.net.*} — 非沙箱编译前缀, 行为与拆分前一致。
 *
 * <p><b>默认导入收敛</b> ({@link #toSandboxCode()}, 包级可见, 仅供同包 {@link ScriptEnvironment}
 * 的沙箱分支使用): 沙箱脚本的编译前缀不再隐式携带 java.io / java.net 导入 — 默认集仅摘除
 * {@code java.io.*} 与 {@code java.net.*} 两条, 其余条目原样保留; 用户显式条目一律保留。
 * 注意这<b>不等于</b>编译期类不可见: Groovy 自身的默认导入
 * ({@code org.codehaus.groovy.control.ResolveVisitor.DEFAULT_IMPORTS} 同样含 java.io / java.net)
 * 与脚本前缀无关, {@code File} / {@code URL} 等名字在沙箱脚本中仍可解析, 显式解析是否放行
 * 依旧受 Groovy 默认导入影响, 强制拦截由 {@link GroovySandbox} 的运行期白名单承担。
 * 收敛的价值在于引擎的导入面不再"承诺"白名单禁用的包 — 导入面与放行面一致。
 */
public class GroovyImports {

    /** {@link #toSandboxCode()} 下不再生成的默认集条目: io / net 与运行期白名单禁区重叠。 */
    private static final Set<String> SANDBOX_DROPPED_DEFAULTS = Set.of("java.io.*", "java.net.*");

    /** 引擎默认导入集: 构造时写入, 之后只读。 */
    private final List<String> defaultImports = new ArrayList<>();

    /** 用户显式导入集 (put / putAll / putCode)。仅在集内去重, 不与 {@link #defaultImports} 去重。 */
    private final List<String> userImports = new ArrayList<>();

    public GroovyImports() {

        defaultImports.add("java.math.*");
        defaultImports.add("java.text.*");
        defaultImports.add("java.io.*");
        defaultImports.add("java.net.*");
        defaultImports.add("java.util.*");
        defaultImports.add("java.util.regex.*");
        defaultImports.add("com.alibaba.fastjson2.*");
        defaultImports.add("org.apache.commons.lang3.*");
        defaultImports.add("org.apache.commons.lang3.time.*");

    }

    public void put(String item) {

        String s = StringUtils.trim(item);
        String cls = StringUtils.substringAfterLast(s, ".");
        String path = StringUtils.substringBeforeLast(s, ".");

        // 判重只看用户显式集: 与默认集同包的显式条目 (如 java.io.File vs 默认 java.io.*) 必须保留
        if (!userImports.contains(path + ".*")) {
            if ("*".equals(cls)) {
                userImports.removeIf(p -> p.matches(path.replaceAll("\\.", "\\.") + "\\.\\w+"));
            }
            userImports.add(s);
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
            while (matcher.find()) {
                userImports.add(matcher.group(1));
            }
        }
    }


    /** 非沙箱脚本编译前缀: 默认集 + 用户显式集合并, 含默认 {@code java.io.*} / {@code java.net.*}。 */
    public String toCode() {
        return code(false);
    }

    /**
     * 沙箱脚本编译前缀 — 默认导入收敛版 (默认集去掉 {@code java.io.*} / {@code java.net.*},
     * 用户显式条目全保留), 条目顺序与 {@link #toCode()} 同构。语义详见类注释。
     */
    String toSandboxCode() {
        return code(true);
    }

    /** 统一拼接: 默认在前用户在后; sandbox=true 时跳过被收敛的默认条目; 同名字符串只输出一次。 */
    private String code(boolean sandbox) {
        StringBuilder sb = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (String item : defaultImports) {
            if (sandbox && SANDBOX_DROPPED_DEFAULTS.contains(item)) {
                continue;
            }
            appendImport(sb, item, seen);
        }
        for (String item : userImports) {
            appendImport(sb, item, seen);
        }
        return sb.toString();
    }

    /** 追加一条 import; 跳过空白条目与完全重复条目 (默认集与用户集可能含同名条目)。 */
    private static void appendImport(StringBuilder sb, String item, Set<String> seen) {
        if (StringUtils.isNotBlank(item) && seen.add(item)) {
            sb.append("import ").append(item).append(";\r\n");
        }
    }

}
