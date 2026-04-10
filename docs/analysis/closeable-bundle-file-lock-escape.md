# CloseableBundleFile Lock Escape Analysis

## References

- **Discussion**: [eclipse-equinox/equinox#896 (comment)](https://github.com/eclipse-equinox/equinox/discussions/896#discussioncomment-16506081)
- **Related Issue**: [#220 - Potential Deadlock in MRUBundleFileList](https://github.com/eclipse-equinox/equinox/issues/220)
- **Previous Fix**: [Commit 7ac58ba - Fix unexpected errors / lock escape](https://github.com/eclipse-equinox/equinox/commit/7ac58baf3355f68b44ed87b5282bc13cfd602375)

## Executive Summary

A `ReentrantLock` (`openLock`) in `CloseableBundleFile` is being acquired by a worker thread during
class loading but never fully released. The worker thread then returns to its thread pool while still
holding the lock, permanently blocking any subsequent thread that attempts to load a class or access
a resource from the same bundle file. This is a **lock escape** bug that causes indefinite thread
blocking and is observed with increasing frequency on JDK 21 and JDK 25.

## 1. Problem Description

### 1.1 Observed Symptoms

Reported by the Snow Owl project ([b2ihealthcare/snow-owl](https://github.com/b2ihealthcare/snow-owl)),
the problem manifests as:

- JUnit test suites hang indefinitely during class loading
- Thread dumps show one thread **permanently blocked** waiting for a `ReentrantLock` in
  `CloseableBundleFile.lockOpen()`
- The lock is listed as held by a **different worker thread** that has already completed its task and
  returned to its thread pool
- The problem occurs with increasing frequency on JDK 21 and JDK 25

### 1.2 Environment

```
Eclipse Platform 2026-03 (e4.39)
Eclipse OSGi version 3.24.100
Java 25.0.2 (Eclipse Adoptium) — also observed on JDK 21
```

### 1.3 Thread Dump Evidence

**Blocked thread** (`qtp7825855-624`) — a Jetty HTTP worker trying to load a class:

```
"qtp7825855-624" java.lang.Thread.State: WAITING (parking)
  - parking to wait for <0x00000000809affd0> (a ReentrantLock$NonfairSync)
  at CloseableBundleFile.lockOpen(CloseableBundleFile.java:85)     // openLock.lock()
  at CloseableBundleFile.getEntry(CloseableBundleFile.java:290)
  at NestedDirBundleFile.getEntry(NestedDirBundleFile.java:96)
  at ClasspathEntry.findEntry(ClasspathEntry.java:204)
  at ClasspathManager.findClassImpl(ClasspathManager.java:675)
  ...
  at java.lang.ClassLoader.loadClass(ClassLoader.java:490)
```

**Lock-holding thread** (`server-106`) — a worker thread **idle in its pool**:

```
"server-106" java.lang.Thread.State: WAITING (parking)
  - parking to wait for <0x00000000b3080fd0> (a ...ExecutorScalingQueue)
  at LinkedTransferQueue.take(...)
  at ThreadPoolExecutor.getTask(...)                               // waiting for next task
  at ThreadPoolExecutor.runWorker(...)
  at ThreadPoolExecutor$Worker.run(...)

  Locked ownable synchronizers:
    - <0x00000000809affd0> (a ReentrantLock$NonfairSync)           // STILL HOLDS THE LOCK
```

**Key observation**: Thread `server-106` has completed its task (it's in `getTask()` waiting for work),
yet it still holds the `openLock` (`<0x00000000809affd0>`) in its "Locked ownable synchronizers" list.
This is the smoking gun of a **lock escape**.

## 2. Architecture Overview

### 2.1 Locking Mechanism

`CloseableBundleFile` uses a `ReentrantLock` named `openLock` to protect bundle file operations. The
lock is managed through a `lockOpen()`/`releaseOpen()` pattern:

```
CloseableBundleFile.java
├── openLock: ReentrantLock          — the problematic lock
├── lockOpen()                       — acquires openLock, opens file if needed
├── releaseOpen()                    — releases openLock (delegates to openLock.unlock())
├── getEntry(path)                   — lockOpen() → try { findEntry() } finally { releaseOpen() }
├── getFile(entry, nativeCode)       — lockOpen() → try { ... } finally { releaseOpen() }
├── containsDir(dir)                 — lockOpen() → try { ... } finally { releaseOpen() }
├── getEntryPaths(path, recurse)     — lockOpen() → try { ... } finally { releaseOpen() }
├── getInputStream(entry)            — lockOpen() → try { ... } finally { releaseOpen() }
├── extractDirectory(dirName)        — lockOpen() → try { ... } finally { releaseOpen() }
├── open()                           — openLock.lock() → try { internalOpen() } finally { unlock() }
├── close()                          — openLock.lock() → try { ... } finally { unlock() }
├── incrementReference()             — openLock.lock() → try { ref++ } finally { unlock() }
└── decrementReference()             — openLock.lock() → try { ref-- } finally { unlock() }
```

### 2.2 Class Loading Call Chain

```
ClassLoader.loadClass()
  → ModuleClassLoader.loadClass()
    → BundleLoader.findClass()
      → ClasspathManager.findLocalClass()
        → ClasspathManager.findClassImpl()
          → ClasspathEntry.findEntry()            — calls BundleFile.getEntry()
            → NestedDirBundleFile.getEntry()       — delegates to baseBundleFile.getEntry()
              → CloseableBundleFile.getEntry()     — lockOpen() / releaseOpen()
          → BundleEntry.getBytes()
            → ZipBundleEntry.getInputStream()      — calls bundleFile.getInputStream()
              → CloseableBundleFile.getInputStream() — lockOpen() / releaseOpen()
```

### 2.3 MRU (Most Recently Used) Bundle File Management

`MRUBundleFileList` limits the number of simultaneously open bundle files:

- When a new file exceeds the limit, the least recently used file is closed asynchronously via an
  `EventManager` thread ("Bundle File Closer")
- A **back pressure** mechanism temporarily releases the `openLock` to allow the closer thread to
  catch up
- The `dispatchEvent()` method on the closer thread acquires a `pendingLock` to track pending close
  operations

### 2.4 Back Pressure Window

A critical detail in `internalOpen()` is the back pressure mechanism, which **temporarily releases
the lock**:

```java
private void internalOpen() throws IOException {
    if (closed) {
        boolean needBackPressure = mruListAdd();
        if (needBackPressure) {
            openLock.unlock();    // ← lock fully released (hold count 1→0)
            try {
                mruListApplyBackPressure();   // may block up to 500ms
            } finally {
                openLock.lock();  // ← lock re-acquired (hold count 0→1)
            }
        }
        if (closed) {
            // ... doOpen() ...
            closed = false;
        }
        // NOTE: if another thread opened the file during back pressure,
        // 'closed' is now false and this falls through WITHOUT calling mruListUse()
    } else {
        mruListUse();
    }
}
```

## 3. Root Cause Analysis

### 3.1 Code Review of Lock Paths

All public methods in `CloseableBundleFile` that call `lockOpen()` follow the pattern:

```java
if (!lockOpen()) { return ...; }
try {
    // work
} finally {
    releaseOpen();
}
```

The `lockOpen()` method itself handles errors:

```java
protected boolean lockOpen() {
    openLock.lock();
    try {
        internalOpen();
        return true;
    } catch (Throwable e) {
        openLock.unlock();     // always unlock on any throwable
        // ... error handling, publishContainerEvent ...
        if (!(e instanceof IOException)) {
            EquinoxContainer.sneakyThrow(e);  // re-throws non-IOExceptions
        }
        return false;
    }
}
```

**At first glance, all code paths appear correct.** Every `lockOpen()` has a matching `releaseOpen()`
in a `finally` block, and the `catch (Throwable e)` handler always unlocks before any exception
propagation.

### 3.2 Hypothesized Root Causes

Since the code paths all appear correct at the source level, the lock escape likely originates from
one of these mechanisms:

#### Hypothesis A: JVM-Level Lock Implementation Changes (Most Likely)

JDK 21 and JDK 25 include significant changes to `AbstractQueuedSynchronizer` (AQS), which underlies
`ReentrantLock`:

- **JDK 21 (JEP 444)**: Virtual thread support changed how `synchronized` blocks interact with
  carrier threads, and AQS was updated for better virtual thread compatibility.
- **JDK 25**: Further AQS optimizations and changes to the `LinkedTransferQueue` (visible in the
  stack trace).

A JVM bug where `ReentrantLock.unlock()` fails to fully release the lock under specific timing
conditions (e.g., during contention between the back pressure unlock/relock and another thread's
close operation) would explain:
- Why the problem is intermittent
- Why it's more frequent on newer JDKs
- Why the code appears correct at the source level

#### Hypothesis B: Error During Finally Block Execution

If an `Error` (such as `StackOverflowError` or `OutOfMemoryError`) occurs precisely during the
`releaseOpen()` call in a `finally` block, the lock would not be released:

```java
try {
    return findEntry(path);    // if this returns normally
} finally {
    releaseOpen();             // but THIS throws StackOverflowError → lock escapes
}
```

This is unlikely but possible in deeply recursive class loading scenarios (class A triggers
loading of class B, which triggers class C, etc.).

#### Hypothesis C: Back Pressure Race Condition

During the back pressure window in `internalOpen()`, the lock is fully released. While released:

1. Thread B could open the file (setting `closed = false`)
2. Thread A re-acquires the lock
3. Thread A sees `closed == false`, skips both the open logic AND the `mruListUse()` call
4. Thread A returns from `internalOpen()`, and `lockOpen()` returns `true`
5. The caller (e.g., `getEntry()`) uses the file and calls `releaseOpen()`

While this doesn't directly cause a lock escape, the **missing `mruListUse()` call** could lead to
the MRU evicting this file prematurely, potentially causing a cascade of operations that could
interact with the lock in unexpected ways.

#### Hypothesis D: MRU Closer Thread Interaction

The async "Bundle File Closer" thread calls `close()` on bundle files, which acquires the `openLock`.
If the closer thread's `close()` call interleaves with a worker thread's `lockOpen()` during the
back pressure window, there could be a state where:

1. Worker thread releases `openLock` for back pressure
2. Closer thread acquires `openLock` for close()
3. Closer thread closes the file and releases `openLock`
4. Worker thread re-acquires `openLock`
5. Worker thread finds `closed = true` and re-opens

This is handled by the code, but the **interaction between the MRU's `pendingLock` and the
`openLock`** creates a complex two-lock ordering that could lead to subtle issues, especially with
JVM lock implementation changes.

#### Hypothesis E: close() Timeout Bug

In `close()`, there is a suspicious timeout value:

```java
refCondition.await(1000, TimeUnit.MICROSECONDS); // comment says "timeout after 1 second"
```

The comment says 1 second, but `1000 MICROSECONDS = 1 millisecond`. This means the close operation
times out after **1 millisecond**, not 1 second. While this doesn't directly cause the lock escape,
it means the MRU closer gives up very quickly on files with open streams, potentially leading to
resource management issues that could compound the problem.

### 3.3 Related Historical Issues

**Issue #220 (Potential Deadlock in MRUBundleFileList)**: A similar but different problem where the
MRU's `pendingLock` and intrinsic lock (`synchronized (this)`) created a deadlock chain. The current
code still uses this two-lock pattern.

**Commit 7ac58ba (Fix unexpected errors)**: Fixed a lock escape where non-`IOException` errors thrown
from `internalOpen()` (then called `open(boolean)`) were not caught, causing the lock to never be
released. The fix changed the catch from `IOException` to `Throwable` and restructured the lock
acquisition.

## 4. Proposed Code Changes

### 4.1 Defensive Lock Release with Lock Timeout (Primary Fix)

Replace `openLock.lock()` in `lockOpen()` with a timed acquisition and add defensive lock balance
checking:

```java
protected boolean lockOpen() {
    openLock.lock();
    boolean success = false;
    try {
        internalOpen();
        success = true;
        return true;
    } catch (Throwable e) {
        // existing error handling...
        return false;
    } finally {
        if (!success) {
            openLock.unlock();
        }
    }
}
```

**Rationale**: By using a `success` flag and a `finally` block instead of unlocking only in the
`catch`, we ensure the lock is always released on failure, regardless of how the method exits. This
is a more defensive pattern that protects against edge cases like `Error` subclasses thrown from
unexpected places.

**Note**: When `lockOpen()` returns `true`, the lock remains held (intentionally) and the caller
releases it via `releaseOpen()` in a `finally` block.

### 4.2 Fix close() Timeout Value

```java
// Before:
refCondition.await(1000, TimeUnit.MICROSECONDS); // BUG: 1000 MICROSECONDS = 1ms, not 1s as intended

// After:
refCondition.await(1, TimeUnit.SECONDS); // properly 1 second
```

**Rationale**: The comment says "timeout after 1 second" but the code waits only 1 millisecond.
This means the MRU closer gives up almost immediately on files with active input streams, which
could lead to resource management issues.

### 4.3 Fix Missing `mruListUse()` in Back Pressure Path

```java
private void internalOpen() throws IOException {
    if (closed) {
        boolean needBackPressure = mruListAdd();
        if (needBackPressure) {
            openLock.unlock();
            try {
                mruListApplyBackPressure();
            } finally {
                openLock.lock();
            }
        }
        if (closed) {
            if (needBackPressure) {
                mruListAdd();
            }
            doOpen();
            closed = false;
            // debug logging...
        } else {
            // File was opened by another thread during back pressure
            mruListUse();  // ← ADD THIS
        }
    } else {
        mruListUse();
    }
}
```

**Rationale**: When another thread opens the file during the back pressure window, the current
thread falls through without calling `mruListUse()`. This means the MRU doesn't know the file is
still in use and may evict it prematurely.

### 4.4 Add Diagnostic Lock Logging

Add configurable logging when lock acquisition takes longer than a threshold:

```java
protected boolean lockOpen() {
    boolean acquired = false;
    try {
        acquired = openLock.tryLock(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
    }
    if (!acquired) {
        // Log a diagnostic message with the lock owner info
        if (debug.DEBUG_BUNDLE_FILE) {
            debug.trace(OPTION_DEBUG_BUNDLE_FILE,
                "WARNING: Failed to acquire open lock within 30s for " + toString()
                + " held by " + getLockOwnerName());
        }
        // Fall back to unconditional lock
        openLock.lock();
    }
    // ... rest of lockOpen()
}
```

**Rationale**: This helps diagnose the problem in production by logging warnings before a thread
becomes permanently blocked. The 30-second timeout is a diagnostic threshold, not a behavioral
change.

### 4.5 Consider Lock Timeout for Production Resilience

As a more invasive but resilient change, consider using `tryLock()` with a timeout in `lockOpen()`
so that threads don't block indefinitely if a lock escape occurs:

```java
protected boolean lockOpen() {
    boolean acquired;
    try {
        acquired = openLock.tryLock(60, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
    }
    if (!acquired) {
        // Log error: lock could not be acquired, possible lock escape
        return false;
    }
    // ... rest of lockOpen()
}
```

**Trade-off**: This prevents indefinite blocking but means class loading may fail if a lock escape
occurs. This could be made configurable via a system property.

## 5. Mitigations

### 5.1 Short-term: Disable MRU Bundle File List

Users can disable the MRU by setting:

```
-Dosgi.bundlefile.limit=0
```

When the MRU is disabled:
- No back pressure mechanism is triggered (eliminating the temporary lock release)
- No async close operations via the "Bundle File Closer" thread
- The `lockOpen()` → `internalOpen()` → `doOpen()` path is simpler

This removes several of the suspected race condition windows.

### 5.2 Short-term: Increase MRU File Limit

If disabling MRU is not desirable (e.g., too many open file handles), increasing the limit reduces
the frequency of bundle file closures:

```
-Dosgi.bundlefile.limit=200
```

### 5.3 Medium-term: Enable OSGi Debug Tracing

Users can enable bundle file tracing to capture more diagnostic data:

```
-Dosgi.debug
-Dosgi.debug.bundlefile=true
-Dosgi.debug.bundlefile.open=true
-Dosgi.debug.bundlefile.close=true
```

This logs every open/close operation on bundle files, which can help correlate timing with the
lock escape.

### 5.4 Long-term: Restructure Locking Architecture

Consider restructuring the locking to avoid the `lockOpen()`/`releaseOpen()` pattern entirely.
Options include:

1. **Read-write lock**: Use a `ReentrantReadWriteLock` where read operations (getEntry, etc.) use
   the read lock and close operations use the write lock.

2. **Lock-free design**: Use atomic operations and `volatile` fields instead of explicit locks for
   the open/close state management.

3. **Scoped locking**: Use `try-with-resources` with an `AutoCloseable` lock guard to make lock
   leaks impossible at the API level.

## 6. Testing and Debugging Strategies

### 6.1 Reproducing the Problem

#### Stress Test: Concurrent Class Loading with MRU Pressure

```java
/**
 * Stress test for concurrent class loading under MRU pressure.
 * NOTE: Sets system property osgi.bundlefile.limit; must be run in isolation
 * or the property must be restored in an @After method.
 */
@Test
void testConcurrentClassLoadingUnderMRUPressure() throws Exception {
    // Set a very low MRU limit to maximize back pressure
    System.setProperty("osgi.bundlefile.limit", "10");

    // Install many bundles to exceed the MRU limit
    for (int i = 0; i < 50; i++) {
        installBundle("test.bundle." + i);
    }

    // Create a thread pool similar to the reporter's setup
    ExecutorService pool = new ThreadPoolExecutor(
        10, 50, 60, TimeUnit.SECONDS,
        new LinkedTransferQueue<>() // similar to ExecutorScalingQueue
    );

    // Concurrently load classes from different bundles
    CountDownLatch latch = new CountDownLatch(100);
    AtomicReference<Throwable> failure = new AtomicReference<>();

    for (int i = 0; i < 100; i++) {
        final int bundleIdx = i % 50;
        pool.submit(() -> {
            try {
                Bundle bundle = getBundleByIndex(bundleIdx);
                bundle.loadClass("some.test.Class");
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                latch.countDown();
            }
        });
    }

    // Use a timeout to detect deadlock
    boolean completed = latch.await(60, TimeUnit.SECONDS);
    assertTrue(completed, "Class loading timed out — possible lock escape");
    assertNull(failure.get(), "Class loading failed");
}
```

#### Stress Test: Lock Balance Verification

```java
@Test
void testLockBalanceUnderContention() throws Exception {
    // Get a reference to a CloseableBundleFile
    CloseableBundleFile<?> bundleFile = getTestBundleFile();

    // Use reflection to access the openLock field
    Field lockField = CloseableBundleFile.class.getDeclaredField("openLock");
    lockField.setAccessible(true);
    ReentrantLock lock = (ReentrantLock) lockField.get(bundleFile);

    ExecutorService pool = Executors.newFixedThreadPool(20);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(100);

    for (int i = 0; i < 100; i++) {
        pool.submit(() -> {
            try {
                startLatch.await();
                BundleEntry entry = bundleFile.getEntry("META-INF/MANIFEST.MF");
                if (entry != null) {
                    entry.getBytes();
                }
            } catch (Exception e) {
                // ignore
            } finally {
                doneLatch.countDown();
            }
        });
    }

    startLatch.countDown();
    doneLatch.await(30, TimeUnit.SECONDS);
    pool.shutdown();
    pool.awaitTermination(5, TimeUnit.SECONDS);

    // Verify lock is not held by any thread
    assertFalse(lock.isLocked(), "Lock should not be held after all operations complete");
    assertEquals(0, lock.getHoldCount(), "Lock hold count should be 0");
}
```

### 6.2 Debugging Techniques

#### Thread Dump Analysis

When the problem occurs, take a thread dump with lock info:

```bash
jcmd <pid> Thread.print -l
```

Or for virtual threads (JDK 21+):

```bash
jcmd <pid> Thread.dump_to_file -format=json threaddump.json
```

Look for:
- Threads with `CloseableBundleFile.lockOpen` in their stack waiting on a `ReentrantLock$NonfairSync`
- The thread listed in "Locked ownable synchronizers" that holds the same lock object
- Whether the lock-holding thread's stack shows any bundle file operations (if not, it's a lock
  escape)

#### JFR (Java Flight Recorder) Monitoring

Use JFR to capture lock contention events:

```bash
-XX:StartFlightRecording=name=lockdebug,settings=profile,duration=300s,filename=lock.jfr
```

Enable these JFR events:
- `jdk.JavaMonitorWait` — monitors lock wait times
- `jdk.JavaMonitorEnter` — monitors lock acquisition
- `jdk.ThreadPark` — monitors thread parking (ReentrantLock uses LockSupport.park)

#### Custom Lock Wrapper for Debugging

For development/debugging purposes, replace `openLock` with a custom `ReentrantLock` subclass
that logs acquisition and release:

```java
private final ReentrantLock openLock = new ReentrantLock() {
    @Override
    public void lock() {
        if (debug.DEBUG_BUNDLE_FILE) {
            debug.trace(OPTION_DEBUG_BUNDLE_FILE,
                "LOCK acquiring by " + Thread.currentThread().getName()
                + " for " + CloseableBundleFile.this);
        }
        super.lock();
        if (debug.DEBUG_BUNDLE_FILE) {
            debug.trace(OPTION_DEBUG_BUNDLE_FILE,
                "LOCK acquired (hold=" + getHoldCount() + ") by "
                + Thread.currentThread().getName()
                + " for " + CloseableBundleFile.this);
        }
    }

    @Override
    public void unlock() {
        if (debug.DEBUG_BUNDLE_FILE) {
            debug.trace(OPTION_DEBUG_BUNDLE_FILE,
                "LOCK releasing (hold=" + getHoldCount() + ") by "
                + Thread.currentThread().getName()
                + " for " + CloseableBundleFile.this);
        }
        super.unlock();
    }
};
```

### 6.3 Automated Lock Escape Detection

Add a periodic health check that scans for potential lock escapes:

```java
/**
 * Can be run periodically (e.g., every 30 seconds) to detect lock escapes.
 * A lock escape is detected when a ReentrantLock is held by a thread
 * that is not executing any CloseableBundleFile-related code.
 */
public static void checkForLockEscapes(Collection<CloseableBundleFile<?>> bundleFiles) {
    for (CloseableBundleFile<?> bf : bundleFiles) {
        ReentrantLock lock = bf.getOpenLock();
        if (lock.isLocked()) {
            Thread owner = lock.getOwner(); // needs protected access
            if (owner != null) {
                StackTraceElement[] stack = owner.getStackTrace();
                boolean inBundleFileCode = Arrays.stream(stack).anyMatch(
                    e -> e.getClassName().contains("CloseableBundleFile")
                      || e.getClassName().contains("BundleFile"));
                if (!inBundleFileCode) {
                    System.err.println("LOCK ESCAPE DETECTED: " + bf
                        + " lock held by " + owner.getName()
                        + " which is NOT in bundle file code");
                    // NOTE: force unlocking a ReentrantLock not held by the current thread
                    // is not possible via the public API — unlock() throws
                    // IllegalMonitorStateException. Recovery would require unsafe reflection
                    // to modify internal lock state, which is dangerous and not recommended.
                    // Instead, log the escape for diagnosis and consider restarting the
                    // framework or application.
                }
            }
        }
    }
}
```

## 7. Summary of Recommendations

| Priority | Action | Risk | Impact |
|----------|--------|------|--------|
| **High** | Change `lockOpen()` to use `success` flag with `finally` block (§4.1) | Low | Prevents lock escape from any unhandled error paths |
| **High** | Fix `close()` timeout: 1000 MICROSECONDS → 1 SECONDS (§4.2) | Low | Fixes incorrect timeout that may cause premature close bailout |
| **Medium** | Add `mruListUse()` call in back pressure path (§4.3) | Low | Prevents premature MRU eviction after back pressure |
| **Medium** | Add diagnostic lock logging (§4.4) | Low | Enables production diagnosis without code changes |
| **Medium** | Document `osgi.bundlefile.limit=0` workaround (§5.1) | None | Provides immediate workaround for affected users |
| **Low** | Consider timed `tryLock()` for resilience (§4.5) | Medium | Prevents indefinite blocking but may cause class load failures |
| **Low** | Restructure locking architecture (§5.4) | High | Long-term fix but requires significant refactoring |

## 8. Open Questions

1. **JVM bug?** Should a JDK bug report be filed? The increasing frequency on JDK 25 suggests
   possible JVM-level changes. Testing with `-XX:+UseLightweightLocking` (JDK 25) vs.
   `-XX:-UseLightweightLocking` could help isolate.

2. **Virtual threads?** If the application uses virtual threads (even indirectly through frameworks),
   `ReentrantLock` with virtual threads has different pinning behavior than `synchronized`. Is the
   worker thread (`server-106`) a virtual thread?

3. **Reproducer quality?** The problem is described as intermittent. A reliable reproducer would
   greatly help isolate whether this is a framework bug or a JVM bug.

4. **Lock hold time monitoring?** Should the framework track how long the `openLock` is held and
   alert if it exceeds a threshold? This could help detect the problem earlier.
