package fun.commons.template4j.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link LockManager} — the named-lock that prevents FuncObject
 * from recursively invoking itself.
 *
 * Three guarantees under test:
 *   1. acquire returns true the first time, false on re-entry with same name
 *   2. release is symmetric (acquire-release-acquire round-trips)
 *   3. shareWithChild produces a manager that shares the underlying lock list
 *      with its parent (so the recursion guard propagates down the call tree)
 *   4. concurrent acquire from N threads only one returns true per name
 */
public class LockManagerTest {

    @Test
    public void acquireReturnsTrueOncePerName() {
        LockManager m = new LockManager();
        assertTrue(m.acquire("alpha"));
        assertFalse(m.acquire("alpha")); // re-entry blocked
        assertTrue(m.acquire("beta"));   // different name OK
        assertFalse(m.acquire("beta"));
    }

    @Test
    public void releaseAllowsReacquire() {
        LockManager m = new LockManager();
        assertTrue(m.acquire("x"));
        assertFalse(m.acquire("x"));
        assertTrue(m.release("x"));
        assertFalse(m.release("x")); // double release is no-op
        assertTrue(m.acquire("x"));  // re-acquire works
    }

    @Test
    public void releaseUnknownNameReturnsFalse() {
        LockManager m = new LockManager();
        assertFalse(m.release("never-acquired"));
    }

    @Test
    public void shareWithChild_propagatesLocksAcrossParentAndChild() {
        LockManager parent = new LockManager();
        LockManager child = parent.shareWithChild();

        // Parent acquires "lock-x" → both parent AND child see it
        assertTrue(parent.acquire("lock-x"));
        assertFalse(child.acquire("lock-x"));
        assertFalse(parent.acquire("lock-x"));

        // release from either side frees the name everywhere
        assertTrue(child.release("lock-x"));
        assertTrue(parent.acquire("lock-x"));
    }

    @Test
    public void snapshot_isLiveViewOfBackingList() {
        LockManager m = new LockManager();
        assertEquals(0, m.snapshot().size());

        m.acquire("first");
        // snapshot() returns a fresh copy each call — re-fetch reflects the new lock
        List<String> snap = m.snapshot();
        assertEquals(1, snap.size());
        assertEquals("first", snap.get(0));
    }

    @Test
    public void concurrentAcquire_onlyOneThreadWinsPerName() throws InterruptedException {
        final LockManager m = new LockManager();
        final int N = 16;
        final ExecutorService pool = Executors.newFixedThreadPool(N);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicInteger winners = new AtomicInteger(0);
        final List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < N; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (m.acquire("contended")) winners.incrementAndGet();
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "threads did not finish in time");

        assertEquals(0, errors.size(), "errors in worker threads: " + errors);
        assertEquals(1, winners.get(), "exactly one thread should win the contended lock");
    }
}
