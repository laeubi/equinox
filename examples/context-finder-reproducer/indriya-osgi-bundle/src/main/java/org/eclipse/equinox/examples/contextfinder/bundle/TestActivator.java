/*******************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.equinox.examples.contextfinder.bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import javax.measure.Unit;

import org.eclipse.equinox.examples.contextfinder.IndriyaAndOSGi;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Bundle activator that tests accessing {@link IndriyaAndOSGi#HOUR} from
 * different threading contexts to reproduce issue 1243.
 * <p>
 * Control which tests to run via system properties:
 * <ul>
 * <li>{@code -Dtest.activator.start=true} — test in activator start method</li>
 * <li>{@code -Dtest.common.pool=true} — test in ForkJoinPool common pool</li>
 * <li>{@code -Dtest.virtual.thread=true} — test in virtual thread</li>
 * <li>{@code -Dtest.parallel.stream=true} — test in parallel stream</li>
 * <li>{@code -Dtest.preload=true} — pre-load class before other tests</li>
 * <li>{@code -Dtest.user.workaround=true} — test with user TCCL workaround in parallel stream</li>
 * <li>{@code -Dtest.all=true} — run all tests (default if no property set)</li>
 * </ul>
 *
 * @see <a href="https://github.com/eclipse-equinox/equinox/issues/1243">Issue
 *      1243</a>
 */
public class TestActivator implements BundleActivator {

	private static final String PREFIX = "[ContextFinder-Reproducer] ";

	private final List<TestResult> results = new ArrayList<>();

	@Override
	public void start(BundleContext context) throws Exception {
		log("=".repeat(80));
		log("REPRODUCER FOR https://github.com/eclipse-equinox/equinox/issues/1243");
		log("=".repeat(80));
		logThreadInfo("Activator.start()", Thread.currentThread());
		log("");

		boolean runAll = getBooleanProperty("test.all", true);
		boolean preload = getBooleanProperty("test.preload", false);

		if (preload) {
			log("--- PRE-LOADING IndriyaAndOSGi class (test.preload=true) ---");
			log("  NOTE: Pre-loading forces class initialization on this thread (main thread).");
			log("  This means the ServiceLoader result gets cached, and subsequent accesses");
			log("  from other threads will succeed even without ContextFinder as TCCL.");
			testInActivatorStart("Pre-load");
			log("");
		}

		// IMPORTANT: Run CommonPool test FIRST (before Activator.start() test)
		// to demonstrate the failure when the first classload happens on a FJP thread.
		// If we ran Activator.start() first, the ServiceLoader result would be cached
		// and the CommonPool test would succeed.
		if (runAll || getBooleanProperty("test.common.pool", false)) {
			log("--- TEST: Access IndriyaAndOSGi.HOUR in ForkJoinPool common pool ---");
			log("  NOTE: This test runs FIRST to ensure the class is NOT already loaded.");
			log("  When IndriyaAndOSGi is first accessed on a FJP thread, ServiceLoader.load()");
			log("  uses the FJP thread's TCCL (system class loader, NOT ContextFinder).");
			log("  Whether this fails depends on whether the ServiceLoader implementation");
			log("  class is visible to the system class loader.");
			testInCommonPool();
			log("");
		}

		if (runAll || getBooleanProperty("test.activator.start", false)) {
			log("--- TEST: Access IndriyaAndOSGi.HOUR in Activator.start() ---");
			testInActivatorStart("Activator.start()");
			log("");
		}

		if (runAll || getBooleanProperty("test.virtual.thread", false)) {
			log("--- TEST: Access IndriyaAndOSGi.HOUR in Virtual Thread ---");
			testInVirtualThread();
			log("");
		}

		if (runAll || getBooleanProperty("test.parallel.stream", false)) {
			log("--- TEST: Access IndriyaAndOSGi.HOUR in Parallel Stream ---");
			testInParallelStream();
			log("");
		}

		if (runAll || getBooleanProperty("test.user.workaround", false)) {
			log("--- TEST: Access IndriyaAndOSGi.HOUR in Parallel Stream with User TCCL Workaround ---");
			log("  NOTE: This test demonstrates the user workaround of explicitly setting");
			log("  the TCCL on each ForkJoinPool worker thread before accessing TCCL-dependent");
			log("  code. This is boilerplate-heavy but works reliably.");
			testInParallelStreamWithUserWorkaround();
			log("");
		}

		// Check if a custom ForkJoinPool thread factory is active
		String threadFactory = System.getProperty("java.util.concurrent.ForkJoinPool.common.threadFactory");
		if (threadFactory != null) {
			log("--- INFO: Custom ForkJoinPool.common.threadFactory is active ---");
			log("  Factory class: " + threadFactory);
			log("  This should cause ForkJoinPool worker threads to inherit the ContextFinder");
			log("  TCCL, fixing the issue without any user workaround code.");
			log("");
		}

		printSummary();
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		log("Bundle stopping.");
	}

	private void testInActivatorStart(String label) {
		logThreadInfo(label, Thread.currentThread());
		try {
			Unit<?> hour = IndriyaAndOSGi.getHour();
			String msg = label + " -> SUCCESS: IndriyaAndOSGi.HOUR = " + hour;
			log(msg);
			results.add(new TestResult(label, true, msg));
		} catch (Throwable t) {
			String msg = label + " -> FAILED: " + t;
			log(msg);
			t.printStackTrace(System.err);
			results.add(new TestResult(label, false, msg));
		}
	}

	private void testInCommonPool() {
		CountDownLatch latch = new CountDownLatch(1);
		ForkJoinPool.commonPool().execute(() -> {
			try {
				Thread thread = Thread.currentThread();
				logThreadInfo("CommonPool", thread);
				ClassLoader tccl = thread.getContextClassLoader();
				boolean hasContextFinder = tccl != null
						&& tccl.getClass().getName().contains("ContextFinder");
				log("  TCCL is ContextFinder: " + hasContextFinder);
				if (!hasContextFinder) {
					log("  WARNING: TCCL is NOT ContextFinder on ForkJoinPool thread!");
					log("  TCCL class: " + (tccl != null ? tccl.getClass().getName() : "null"));
					log("  This means ServiceLoader.load() will use the system class loader,");
					log("  which in a real OSGi environment would NOT be able to find bundle");
					log("  classes, causing ClassNotFoundException.");
				}
				try {
					Unit<?> hour = IndriyaAndOSGi.getHour();
					String msg = "CommonPool -> SUCCESS: IndriyaAndOSGi.HOUR = " + hour
							+ (hasContextFinder ? "" : " (but TCCL is WRONG - works here only because "
									+ "jars are on system classpath in this embedded setup)");
					log(msg);
					results.add(new TestResult("CommonPool", hasContextFinder, msg));
				} catch (Throwable t) {
					String msg = "CommonPool -> FAILED: " + t;
					log(msg);
					t.printStackTrace(System.err);
					results.add(new TestResult("CommonPool", false, msg));
				}
			} finally {
				latch.countDown();
			}
		});
		awaitSafely(latch, "CommonPool");
	}

	private void testInVirtualThread() {
		CountDownLatch latch = new CountDownLatch(1);
		Thread.startVirtualThread(() -> {
			try {
				logThreadInfo("VirtualThread", Thread.currentThread());
				try {
					Unit<?> hour = IndriyaAndOSGi.getHour();
					String msg = "VirtualThread -> SUCCESS: IndriyaAndOSGi.HOUR = " + hour;
					log(msg);
					results.add(new TestResult("VirtualThread", true, msg));
				} catch (Throwable t) {
					String msg = "VirtualThread -> FAILED: " + t;
					log(msg);
					t.printStackTrace(System.err);
					results.add(new TestResult("VirtualThread", false, msg));
				}
			} finally {
				latch.countDown();
			}
		});
		awaitSafely(latch, "VirtualThread");
	}

	private void testInParallelStream() {
		List<TestResult> parallelResults = new java.util.concurrent.CopyOnWriteArrayList<>();
		int parallelism = ForkJoinPool.getCommonPoolParallelism();
		log("  ForkJoinPool parallelism: " + parallelism);

		IntStream.range(0, parallelism * 4).parallel().forEach(i -> {
			Thread t = Thread.currentThread();
			String label = "ParallelStream[" + i + "] on " + t.getName();
			ClassLoader tccl = t.getContextClassLoader();
			boolean hasContextFinder = tccl != null
					&& tccl.getClass().getName().contains("ContextFinder");
			if (i == 0 || !t.getName().equals("main")) {
				logThreadInfo(label, t);
			}
			try {
				Unit<?> hour = IndriyaAndOSGi.getHour();
				if (!t.getName().equals("main")) {
					log("  " + label + " -> SUCCESS: " + hour
							+ (hasContextFinder ? "" : " (TCCL is WRONG)"));
				}
				parallelResults.add(new TestResult(label, hasContextFinder, "SUCCESS"));
			} catch (Throwable ex) {
				log("  " + label + " -> FAILED: " + ex);
				ex.printStackTrace(System.err);
				parallelResults.add(new TestResult(label, false, ex.toString()));
			}
		});

		long correctTCCL = parallelResults.stream().filter(r -> r.success).count();
		long wrongTCCL = parallelResults.stream().filter(r -> !r.success).count();
		String msg = "ParallelStream -> " + correctTCCL + " with correct TCCL, " + wrongTCCL
				+ " with WRONG TCCL out of " + parallelResults.size() + " tasks";
		log(msg);
		results.add(new TestResult("ParallelStream", wrongTCCL == 0, msg));
	}

	/**
	 * Tests the "user workaround" for the TCCL issue: explicitly capturing the
	 * TCCL from the calling thread and setting it on each ForkJoinPool worker
	 * thread before accessing TCCL-dependent code.
	 * <p>
	 * This is the workaround described in the README:
	 * <pre>
	 * ClassLoader contextFinder = Thread.currentThread().getContextClassLoader();
	 * myList.parallelStream().forEach(item -> {
	 *     Thread.currentThread().setContextClassLoader(contextFinder);
	 *     // ... use ServiceLoader-dependent code ...
	 * });
	 * </pre>
	 * <p>
	 * While this works, it is boilerplate-heavy and error-prone: every parallel
	 * stream operation that might trigger TCCL-dependent code needs this wrapper.
	 */
	private void testInParallelStreamWithUserWorkaround() {
		List<TestResult> parallelResults = new java.util.concurrent.CopyOnWriteArrayList<>();
		int parallelism = ForkJoinPool.getCommonPoolParallelism();
		log("  ForkJoinPool parallelism: " + parallelism);

		// Capture the TCCL from the current (main) thread — this is the ContextFinder
		ClassLoader callerTccl = Thread.currentThread().getContextClassLoader();
		log("  Captured caller TCCL: " + (callerTccl != null
				? callerTccl.getClass().getName() + "@" + Integer.toHexString(callerTccl.hashCode())
				: "null"));

		IntStream.range(0, parallelism * 4).parallel().forEach(i -> {
			Thread t = Thread.currentThread();
			String label = "UserWorkaround[" + i + "] on " + t.getName();

			// === USER WORKAROUND: set the captured TCCL on this worker thread ===
			ClassLoader originalTccl = t.getContextClassLoader();
			t.setContextClassLoader(callerTccl);
			try {
				ClassLoader tccl = t.getContextClassLoader();
				boolean hasContextFinder = tccl != null
						&& tccl.getClass().getName().contains("ContextFinder");
				if (i == 0 || !t.getName().equals("main")) {
					logThreadInfo(label, t);
				}
				try {
					Unit<?> hour = IndriyaAndOSGi.getHour();
					if (!t.getName().equals("main")) {
						log("  " + label + " -> SUCCESS: " + hour
								+ (hasContextFinder ? " (TCCL is correct)" : " (TCCL was set by workaround)"));
					}
					parallelResults.add(new TestResult(label, true, "SUCCESS"));
				} catch (Throwable ex) {
					log("  " + label + " -> FAILED: " + ex);
					ex.printStackTrace(System.err);
					parallelResults.add(new TestResult(label, false, ex.toString()));
				}
			} finally {
				// Restore original TCCL to avoid leaking into other tasks
				t.setContextClassLoader(originalTccl);
			}
		});

		long successes = parallelResults.stream().filter(r -> r.success).count();
		long failures = parallelResults.stream().filter(r -> !r.success).count();
		String msg = "UserWorkaround -> " + successes + " successes, " + failures
				+ " failures out of " + parallelResults.size() + " tasks";
		log(msg);
		results.add(new TestResult("UserWorkaround", failures == 0, msg));
	}

	private void printSummary() {
		log("=".repeat(80));
		log("SUMMARY");
		log("=".repeat(80));
		log("Java version: " + System.getProperty("java.version"));
		log("Java vendor:  " + System.getProperty("java.vendor"));
		log("ForkJoinPool parallelism: " + ForkJoinPool.getCommonPoolParallelism());
		log("");

		int passed = 0;
		int failed = 0;
		for (TestResult result : results) {
			String status = result.success ? "PASS" : "FAIL";
			log(String.format("  [%s] %s: %s", status, result.testName, result.message));
			if (result.success) {
				passed++;
			} else {
				failed++;
			}
		}
		log("");
		log(String.format("Results: %d passed, %d failed out of %d tests", passed, failed, results.size()));

		if (failed > 0) {
			log("");
			log("ANALYSIS / POSSIBLE MITIGATIONS:");
			log("  1. The root cause is that ForkJoinPool common pool threads use the system");
			log("     class loader as TCCL, not OSGi's ContextFinder.");
			log("  2. In Java 25, common pool threads are InnocuousForkJoinWorkerThread which");
			log("     always sets TCCL to the system class loader.");
			log("  3. Pre-loading the class in the activator or a 'real' thread before using it");
			log("     in parallel streams may work as a workaround (if the class caches the result).");
			log("  4. Setting java.util.concurrent.ForkJoinPool.common.threadFactory to a custom");
			log("     factory that sets the ContextFinder as TCCL could fix this in Equinox.");
			log("  5. Libraries like indriya could cache the ServiceLoader result to avoid");
			log("     repeated TCCL-dependent lookups (mitigation in the library itself).");
			log("  6. Users could wrap parallel stream code to explicitly set the TCCL:");
			log("     ClassLoader original = Thread.currentThread().getContextClassLoader();");
			log("     Thread.currentThread().setContextClassLoader(correctLoader);");
			log("     try { ... parallel stream ... } finally { Thread.currentThread().setContextClassLoader(original); }");
		}

		log("=".repeat(80));
	}

	private static void logThreadInfo(String context, Thread t) {
		ClassLoader tccl = t.getContextClassLoader();
		log(String.format("  [%s] Thread: name=%s, class=%s, virtual=%s, group=%s, TCCL=%s",
				context,
				t.getName(),
				t.getClass().getName(),
				t.isVirtual(),
				t.getThreadGroup() != null ? t.getThreadGroup().getName() : "null",
				tccl != null ? tccl.getClass().getName() + "@" + Integer.toHexString(tccl.hashCode()) : "null"));
	}

	private static void log(String message) {
		System.out.println(PREFIX + message);
	}

	private static boolean getBooleanProperty(String name, boolean defaultValue) {
		String value = System.getProperty(name);
		if (value == null) {
			return defaultValue;
		}
		return Boolean.parseBoolean(value);
	}

	private static void awaitSafely(CountDownLatch latch, String label) {
		try {
			if (!latch.await(30, TimeUnit.SECONDS)) {
				log("  WARNING: " + label + " did not complete within 30 seconds");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log("  WARNING: " + label + " was interrupted");
		}
	}

	private record TestResult(String testName, boolean success, String message) {
	}
}
