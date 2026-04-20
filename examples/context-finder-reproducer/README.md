# Issue 1243 Reproducer - ContextFinder TCCL on ForkJoinPool/Virtual Threads

This is a standalone Maven project that reproduces
[eclipse-equinox/equinox#1243](https://github.com/eclipse-equinox/equinox/issues/1243).

## Problem

In an OSGi environment (Equinox), the `ContextFinder` is set as the Thread Context Class
Loader (TCCL) on the main thread. However, ForkJoinPool common pool threads (used by
`parallelStream()`) do **not** inherit this TCCL — they use the system class loader instead.

This causes `ClassNotFoundException` when TCCL-dependent mechanisms like
`ServiceLoader.load()` are used inside parallel streams. The indriya library
(Units of Measurement) triggers this because `Units.HOUR` internally uses ServiceLoader
during static initialization.

### Java 25 makes this worse

In Java 25, ForkJoinPool common pool threads became `InnocuousForkJoinWorkerThread`
(previously only used when a SecurityManager was installed). These threads explicitly
set the TCCL to the system class loader, making the problem more prominent.

## Structure

```
context-finder-reproducer/
├── pom.xml                    # Parent POM
├── indriya-osgi-api/          # Contains IndriyaAndOSGi class with static field HOUR
├── indriya-osgi-bundle/       # OSGi bundle with TestActivator
└── reproducer-app/            # Main app that launches Equinox and runs tests
```

## How to build and run

```bash
# Build and run with default Java
mvn clean verify

# Run with a specific JDK
JAVA_HOME=/path/to/jdk-25 mvn clean verify
```

## System properties

Control which tests to run:

| Property | Default | Description |
|----------|---------|-------------|
| `test.all` | `true` | Run all tests |
| `test.activator.start` | `false` | Test in BundleActivator.start() |
| `test.common.pool` | `false` | Test in ForkJoinPool common pool thread |
| `test.virtual.thread` | `false` | Test in virtual thread |
| `test.parallel.stream` | `false` | Test in parallel stream |
| `test.preload` | `false` | Pre-load IndriyaAndOSGi class before other tests |
| `test.user.workaround` | `false` | Test with explicit TCCL workaround in parallel stream |
| `test.thread.factory` | `false` | Activate custom ForkJoinPool.common.threadFactory |

Example:
```bash
mvn clean verify -Dtest.preload=true -Dtest.common.pool=true
```

## Expected results

| Test | Java 21 | Java 25 | Notes |
|------|---------|---------|-------|
| Activator.start() | ✅ PASS | ✅ PASS | Runs on main thread with ContextFinder TCCL |
| CommonPool | ❌ FAIL | ❌ FAIL | FJP threads have system CL as TCCL |
| VirtualThread | ✅ PASS | ✅ PASS | Inherits TCCL from creating thread |
| ParallelStream | ❌ FAIL | ❌ FAIL | Uses FJP common pool threads |
| UserWorkaround | ✅ PASS | ✅ PASS | Explicitly sets TCCL per task |
| Preload + CommonPool | ✅ PASS | ✅ PASS | Class already loaded, ServiceLoader cached |

### With `-Dtest.thread.factory=true`

| Test | Java 21 | Java 25 | Notes |
|------|---------|---------|-------|
| CommonPool | ✅ PASS | ✅ PASS | Custom factory propagates ContextFinder TCCL |
| ParallelStream | ✅ PASS | ✅ PASS | Worker threads now have correct TCCL |

## Workarounds and mitigations

### 1. Custom ForkJoinPool thread factory (framework-level fix)

The most complete solution: set a custom `ForkJoinPool.common.threadFactory` that propagates
the ContextFinder TCCL to worker threads. This reproducer includes an example implementation:
`ContextFinderForkJoinWorkerThreadFactory`.

```bash
# Activate in this reproducer:
mvn clean verify -Dtest.thread.factory=true

# Or set the system property directly:
java -Djava.util.concurrent.ForkJoinPool.common.threadFactory=\
  org.eclipse.equinox.examples.contextfinder.app.ContextFinderForkJoinWorkerThreadFactory \
  -jar app.jar
```

**Caveat:** The factory must be on the system classpath and available before the ForkJoinPool
common pool is initialized. The factory captures the TCCL of the thread that triggers pool
initialization, so if the ContextFinder isn't set yet at that point, the factory won't help.

**Important finding (Java 25):** On Java 25, the default ForkJoinPool worker thread class
`InnocuousForkJoinWorkerThread` forcibly resets the TCCL to the system class loader after
creation, making a simple `setContextClassLoader()` call in the factory ineffective. The
factory must create its own `ForkJoinWorkerThread` subclass that does **not** override the
TCCL. This reproducer demonstrates this approach with `ContextFinderForkJoinWorkerThread`.

### 2. User TCCL workaround (per-usage fix)

Explicitly capture and set the TCCL on each parallel stream task:

```java
ClassLoader contextFinder = Thread.currentThread().getContextClassLoader();
myList.parallelStream().forEach(item -> {
    ClassLoader original = Thread.currentThread().getContextClassLoader();
    Thread.currentThread().setContextClassLoader(contextFinder);
    try {
        // ... use ServiceLoader-dependent code ...
    } finally {
        Thread.currentThread().setContextClassLoader(original);
    }
});
```

This works reliably but is **boilerplate-heavy** and error-prone: every parallel stream
operation that might trigger TCCL-dependent code needs this wrapper. Activate in the
reproducer with `-Dtest.user.workaround=true`.

### 3. Pre-load classes (library-specific workaround)

Pre-load the affected class on the main thread before using it in parallel streams:

```java
// In your Activator.start() or initialization code:
IndriyaAndOSGi.getHour(); // Forces class loading on main thread
// Now safe to use in parallel streams since the result is cached
```

This only works if the library (indriya) caches the ServiceLoader result internally.

### 4. Fix in indriya itself

Libraries like indriya could cache the ServiceLoader result after first successful load
to avoid repeated TCCL-dependent lookups. This would make the library more resilient to
environments where the TCCL varies across threads.
