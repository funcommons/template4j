package fun.commons.template4j.core;

import org.apache.commons.lang3.StringUtils;

/**
 * 路径追踪器：在父子 SelfObject 链上构建可读的模板渲染路径（如 {@code /out/% script error}）。
 * 从 SelfObject 中拆分以隔离 debug-path 构建逻辑。
 */
final class PathTracker {

    private PathTracker() {
    }

    static String build(SelfObject self) {
        String path = self.name;
        SelfObject parent = self.parent;
        if (parent != null) {
            if (StringUtils.isNotBlank(self.name)) {
                path = build(parent) + "/" + path;
            } else {
                path = build(parent);
            }
        }
        return StringUtils.defaultIfBlank(path, "");
    }

    static SelfObject rootOf(SelfObject self) {
        if (self.parent == null) {
            return self;
        }
        return rootOf(self.parent);
    }
}
