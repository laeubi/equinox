/*******************************************************************************
 * Copyright (c) 2013, 2020 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.tests.hooks.framework;

import static org.eclipse.osgi.tests.bundles.AbstractBundleTests.stopQuietly;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.eclipse.osgi.tests.OSGiTestsActivator;
import org.junit.Test;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.launch.Framework;

public class ContextFinderTests extends AbstractFrameworkHookTests {

	private Map<String, String> configuration;
	private Framework framework;

	@Override
	public void setUp() throws Exception {
		super.setUp();
		File file = OSGiTestsActivator.getContext().getDataFile(testName.getMethodName());
		configuration = new HashMap<>();
		configuration.put(Constants.FRAMEWORK_STORAGE, file.getAbsolutePath());
		framework = createFramework(configuration);
		initAndStart(framework);
	}

	@Override
	public void tearDown() throws Exception {
		stopQuietly(framework);
		super.tearDown();
	}

	private ClassLoader getContextFinderService() throws InvalidSyntaxException {
		BundleContext bc = framework.getBundleContext();
		ClassLoader contextFinder = bc
				.getService(bc.getServiceReferences(ClassLoader.class, "(equinox.classloader.type=contextClassLoader)")
						.iterator().next());
		assertNotNull("ContextFinder service not found", contextFinder);
		assertTrue("Expected ContextFinder instance, got: " + contextFinder.getClass().getName(),
				isContextFinder(contextFinder));
		return contextFinder;
	}

	/**
	 * Starts a virtual thread using reflection (since the test bundle targets
	 * JavaSE-17 but virtual threads require Java 21+). Returns null if virtual
	 * threads are not available.
	 */
	private static Thread startVirtualThread(Runnable task) {
		try {
			Method m = Thread.class.getMethod("startVirtualThread", Runnable.class);
			return (Thread) m.invoke(null, task);
		} catch (NoSuchMethodException e) {
			return null; // Virtual threads not available
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Failed to start virtual thread", e);
		}
	}

	/**
	 * Returns true if virtual threads are available (Java 21+).
	 */
	private static boolean isVirtualThreadSupported() {
		try {
			Thread.class.getMethod("startVirtualThread", Runnable.class);
			return true;
		} catch (NoSuchMethodException e) {
			return false;
		}
	}

	private static final String CONTEXT_FINDER_CLASS_NAME = "org.eclipse.osgi.internal.framework.ContextFinder";

	/**
	 * Checks if the given classloader is a ContextFinder instance. Uses class name
	 * comparison because the test framework creates a child framework with its own
	 * classloader, so {@code instanceof} would fail across classloader boundaries.
	 */
	private static boolean isContextFinder(ClassLoader cl) {
		return cl != null && cl.getClass().getName().equals(CONTEXT_FINDER_CLASS_NAME);
	}

	@Test
	public void testContextClassLoaderNullLocal() throws InvalidSyntaxException, IOException {
		BundleContext bc = framework.getBundleContext();
		ClassLoader contextFinder = bc
				.getService(bc.getServiceReferences(ClassLoader.class, "(equinox.classloader.type=contextClassLoader)")
						.iterator().next());
		Enumeration<URL> result = contextFinder.getResources("does/not/exist.txt");
		assertNotNull("Null result.", result);
		assertFalse("Found unexpected result", result.hasMoreElements());
	}

	/**
	 * Reproducer for issue 1243: Verifies that the ContextFinder is NOT available
	 * as the thread context class loader on ForkJoinPool common pool worker threads,
	 * which are used by parallel streams.
	 * <p>
	 * In Java 25, ForkJoinPool common pool threads became
	 * {@code InnocuousForkJoinWorkerThread} (previously only used when a
	 * SecurityManager was installed). These threads explicitly set the TCCL to the
	 * system class loader, preventing OSGi's ContextFinder from being inherited.
	 * This causes {@code ClassNotFoundException} when classloading (e.g. via
	 * {@code ServiceLoader.load()}) happens inside parallel stream pipelines in
	 * OSGi bundles.
	 * <p>
	 * The ForkJoinPool common pool uses a factory that sets the system class loader
	 * as the TCCL, so the ContextFinder is never available on these threads unless
	 * Equinox provides a custom ForkJoinPool thread factory via the
	 * {@code java.util.concurrent.ForkJoinPool.common.threadFactory} system
	 * property.
	 *
	 * @see <a href=
	 *      "https://github.com/eclipse-equinox/equinox/issues/1243">Issue
	 *      1243</a>
	 */
	@Test
	public void testContextFinderOnForkJoinPoolThreads() throws Exception {
		ClassLoader contextFinder = getContextFinderService();

		Thread currentThread = Thread.currentThread();
		ClassLoader originalTCCL = currentThread.getContextClassLoader();
		try {
			// Set the ContextFinder as TCCL, which is what EquinoxContainer does
			// during framework initialization
			currentThread.setContextClassLoader(contextFinder);

			// Collect the TCCLs seen on ForkJoinPool worker threads
			List<String> workerThreadsWithoutContextFinder = new CopyOnWriteArrayList<>();

			// Use parallel stream which uses ForkJoinPool.commonPool()
			// Use enough elements to force ForkJoinPool worker thread usage
			IntStream.range(0, ForkJoinPool.getCommonPoolParallelism() * 4).parallel().forEach(i -> {
				Thread t = Thread.currentThread();
				ClassLoader tccl = t.getContextClassLoader();
				// Only check non-main threads (ForkJoinPool workers)
				if (!t.getName().equals("main") && !isContextFinder(tccl)) {
					String info = t.getName() + " (class=" + t.getClass().getName() + ", TCCL="
							+ (tccl != null ? tccl.getClass().getName() : "null") + ")";
					workerThreadsWithoutContextFinder.add(info);
				}
			});

			// This test documents the current issue: ForkJoinPool common pool threads
			// do NOT have the ContextFinder as their TCCL. This assertion is expected
			// to fail until the issue is fixed (e.g. by providing a custom thread factory).
			assertTrue(
					"ForkJoinPool common pool worker threads do not have ContextFinder as TCCL. "
							+ "This causes classloading failures (ClassNotFoundException) when "
							+ "ServiceLoader.load() or other TCCL-dependent code runs in parallel "
							+ "streams within OSGi bundles. In Java 25, ForkJoinPool common pool "
							+ "threads are InnocuousForkJoinWorkerThread instances that always use "
							+ "the system class loader as TCCL. "
							+ "Affected threads: " + workerThreadsWithoutContextFinder
							+ " (see https://github.com/eclipse-equinox/equinox/issues/1243)",
					workerThreadsWithoutContextFinder.isEmpty());
		} finally {
			currentThread.setContextClassLoader(originalTCCL);
		}
	}

	/**
	 * Verifies that the ContextFinder IS correctly inherited as the thread context
	 * class loader on virtual threads when created from a thread that has the
	 * ContextFinder set.
	 * <p>
	 * Virtual threads inherit the TCCL from the creating thread. If the creating
	 * thread has the ContextFinder as TCCL (which is what Equinox sets up), then
	 * virtual threads should also have it. However, if the ContextFinder is not set
	 * as the TCCL when a virtual thread is created (e.g. created from a
	 * ForkJoinPool common pool thread), then the virtual thread will also NOT have
	 * the ContextFinder.
	 *
	 * @see <a href=
	 *      "https://github.com/eclipse-equinox/equinox/issues/1243">Issue
	 *      1243</a>
	 */
	@Test
	public void testContextFinderOnVirtualThreads() throws Exception {
		assumeTrue("Virtual threads require Java 21+", isVirtualThreadSupported());
		ClassLoader contextFinder = getContextFinderService();

		Thread currentThread = Thread.currentThread();
		ClassLoader originalTCCL = currentThread.getContextClassLoader();
		try {
			// Set the ContextFinder as TCCL
			currentThread.setContextClassLoader(contextFinder);

			// Launch virtual threads directly from the current thread and check their TCCL
			List<String> virtualThreadsWithoutContextFinder = new CopyOnWriteArrayList<>();
			int threadCount = 10;
			CountDownLatch latch = new CountDownLatch(threadCount);

			for (int i = 0; i < threadCount; i++) {
				Thread vt = startVirtualThread(() -> {
					try {
						Thread t = Thread.currentThread();
						ClassLoader tccl = t.getContextClassLoader();
						if (!isContextFinder(tccl)) {
							virtualThreadsWithoutContextFinder.add(t.getName() + " (TCCL="
									+ (tccl != null ? tccl.getClass().getName() : "null") + ")");
						}
					} finally {
						latch.countDown();
					}
				});
				assertNotNull("Failed to start virtual thread", vt);
			}

			assertTrue("Virtual threads did not complete in time", latch.await(30, TimeUnit.SECONDS));

			// Virtual threads started from a thread with ContextFinder as TCCL should
			// inherit it
			assertTrue(
					"Virtual threads created from a thread with ContextFinder TCCL should "
							+ "inherit the ContextFinder. Affected threads: "
							+ virtualThreadsWithoutContextFinder
							+ " (see https://github.com/eclipse-equinox/equinox/issues/1243)",
					virtualThreadsWithoutContextFinder.isEmpty());
		} finally {
			currentThread.setContextClassLoader(originalTCCL);
		}
	}

	/**
	 * Tests that virtual threads created from ForkJoinPool common pool threads also
	 * lack the ContextFinder as TCCL, since ForkJoinPool workers don't have it.
	 * This demonstrates a cascading issue: if a parallel stream creates virtual
	 * threads internally, those virtual threads also won't have the ContextFinder.
	 *
	 * @see <a href=
	 *      "https://github.com/eclipse-equinox/equinox/issues/1243">Issue
	 *      1243</a>
	 */
	@Test
	public void testContextFinderOnVirtualThreadsFromForkJoinPool() throws Exception {
		assumeTrue("Virtual threads require Java 21+", isVirtualThreadSupported());
		ClassLoader contextFinder = getContextFinderService();

		Thread currentThread = Thread.currentThread();
		ClassLoader originalTCCL = currentThread.getContextClassLoader();
		try {
			currentThread.setContextClassLoader(contextFinder);

			// Create virtual threads from within ForkJoinPool common pool threads
			List<String> virtualThreadsWithoutContextFinder = new CopyOnWriteArrayList<>();
			int iterations = ForkJoinPool.getCommonPoolParallelism() * 2;
			CountDownLatch allDone = new CountDownLatch(iterations);

			IntStream.range(0, iterations).parallel().forEach(i -> {
				Thread fjpThread = Thread.currentThread();
				// Only test from actual FJP worker threads, not the main thread
				if (!fjpThread.getName().equals("main")) {
					CountDownLatch vtDone = new CountDownLatch(1);
					Thread vt = startVirtualThread(() -> {
						try {
							Thread t = Thread.currentThread();
							ClassLoader tccl = t.getContextClassLoader();
							if (!isContextFinder(tccl)) {
								virtualThreadsWithoutContextFinder
										.add("VirtualThread created from " + fjpThread.getName() + " (TCCL="
												+ (tccl != null ? tccl.getClass().getName() : "null") + ")");
							}
						} finally {
							vtDone.countDown();
						}
					});
					if (vt != null) {
						try {
							vtDone.await(10, TimeUnit.SECONDS);
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
					}
				}
				allDone.countDown();
			});

			assertTrue("Tasks did not complete in time", allDone.await(30, TimeUnit.SECONDS));

			// Virtual threads created from FJP worker threads inherit FJP's TCCL
			// (system classloader), not the ContextFinder
			assertTrue(
					"Virtual threads created from ForkJoinPool common pool threads do not have "
							+ "ContextFinder as TCCL. Since ForkJoinPool workers use the system "
							+ "class loader as TCCL, virtual threads spawned from them inherit "
							+ "the system class loader instead of the ContextFinder. "
							+ "Affected threads: " + virtualThreadsWithoutContextFinder
							+ " (see https://github.com/eclipse-equinox/equinox/issues/1243)",
					virtualThreadsWithoutContextFinder.isEmpty());
		} finally {
			currentThread.setContextClassLoader(originalTCCL);
		}
	}

	/**
	 * Reproducer for issue 1243: Demonstrates that classloading via the thread
	 * context class loader does NOT work correctly on ForkJoinPool common pool
	 * threads. This simulates what happens when {@code ServiceLoader.load()} or
	 * other TCCL-dependent mechanisms are used inside a parallel stream in an OSGi
	 * bundle.
	 * <p>
	 * In the scenario described in issue 1243, the indriya library's
	 * {@code DefaultSystemOfUnitsService} is loaded via {@code ServiceLoader} from
	 * a parallel stream. Since the ForkJoinPool common pool thread's TCCL is the
	 * system class loader (not the ContextFinder), the ServiceLoader cannot find
	 * the service implementation class, resulting in a
	 * {@code ClassNotFoundException}.
	 *
	 * @see <a href=
	 *      "https://github.com/eclipse-equinox/equinox/issues/1243">Issue
	 *      1243</a>
	 */
	@Test
	public void testClassLoadingViaContextClassLoaderInParallelStream() throws Exception {
		ClassLoader contextFinder = getContextFinderService();

		Thread currentThread = Thread.currentThread();
		ClassLoader originalTCCL = currentThread.getContextClassLoader();
		try {
			currentThread.setContextClassLoader(contextFinder);

			// Simulate what ServiceLoader.load() does: uses TCCL for classloading
			List<String> failedThreads = new CopyOnWriteArrayList<>();

			IntStream.range(0, ForkJoinPool.getCommonPoolParallelism() * 4).parallel().forEach(i -> {
				Thread t = Thread.currentThread();
				ClassLoader tccl = t.getContextClassLoader();
				// ServiceLoader.load(SomeService.class) internally does:
				// Thread.currentThread().getContextClassLoader()
				// If TCCL is not ContextFinder, it will use system classloader
				// which cannot find OSGi bundle classes
				if (!isContextFinder(tccl) && !t.getName().equals("main")) {
					failedThreads.add(t.getName() + " (class=" + t.getClass().getName() + ")");
				}
			});

			assertTrue(
					"ServiceLoader.load() would fail on these ForkJoinPool threads because "
							+ "the TCCL is the system class loader instead of the ContextFinder: "
							+ failedThreads + ". "
							+ "This is the root cause of ClassNotFoundException when using "
							+ "parallel streams with ServiceLoader in OSGi bundles "
							+ "(see https://github.com/eclipse-equinox/equinox/issues/1243)",
					failedThreads.isEmpty());
		} finally {
			currentThread.setContextClassLoader(originalTCCL);
		}
	}

}
