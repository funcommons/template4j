package fun.commons.template4j.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 递归锁管理器:通过命名锁防止 FuncObject 递归调用自身导致死循环。
 *
 * <p>锁集合在父子 SelfObject 之间共享同一实例 — 父 acquire 的锁名,子也 sees,
 * 反之亦然。{@link ConcurrentHashMap#newKeySet()} 提供 atomic add-when-absent
 * 语义(返回 true 当且仅当 key 之前不存在),无需 synchronized 块。
 *
 * <p>历史 API: {@link SelfObject#locks} 字段曾经是 live {@code List<String>} 视图,
 * 现改为每次 {@link #snapshot()} 返回 fresh copy — 更安全(无并发修改异常)。
 */
final class LockManager {

    private final Set<String> locks;

    LockManager() {
        this.locks = ConcurrentHashMap.newKeySet();
    }

    private LockManager(Set<String> shared) {
        this.locks = shared;
    }

    LockManager shareWithChild() {
        return new LockManager(locks);
    }

    boolean acquire(String name) {
        // Set.add 返回 true 当且仅当 key 之前不存在;ConcurrentHashMap 的 add 是原子的
        return locks.add(name);
    }

    boolean release(String name) {
        return locks.remove(name);
    }

    /**
     * 返回内部锁集合的 fresh copy。调用方迭代时不会被并发修改影响。
     */
    List<String> snapshot() {
        return new ArrayList<>(locks);
    }
}
