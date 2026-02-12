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
| Preload + CommonPool | ✅ PASS | ✅ PASS | Class already loaded, ServiceLoader cached |

## Possible mitigations

1. **In Equinox**: Set `java.util.concurrent.ForkJoinPool.common.threadFactory` to a
   custom factory that sets the ContextFinder as TCCL on FJP worker threads.

2. **In indriya**: Cache the ServiceLoader result after first successful load to avoid
   repeated TCCL-dependent lookups.

3. **User workaround**: Pre-load the affected class on the main thread before using it
   in parallel streams:
   ```java
   // In your Activator.start() or initialization code:
   IndriyaAndOSGi.getHour(); // Forces class loading on main thread
   // Now safe to use in parallel streams since the result is cached
   ```

4. **User workaround**: Explicitly set TCCL before parallel stream operations:
   ```java
   ClassLoader contextFinder = Thread.currentThread().getContextClassLoader();
   myList.parallelStream().forEach(item -> {
       Thread.currentThread().setContextClassLoader(contextFinder);
       // ... use ServiceLoader-dependent code ...
   });
   ```
