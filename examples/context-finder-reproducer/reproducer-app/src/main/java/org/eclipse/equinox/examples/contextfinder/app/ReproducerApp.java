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
package org.eclipse.equinox.examples.contextfinder.app;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ForkJoinPool;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

/**
 * Standalone application that launches an embedded Equinox OSGi framework and
 * reproduces issue 1243.
 * <p>
 * The application:
 * <ol>
 * <li>Creates a temporary directory for the OSGi framework storage</li>
 * <li>Launches Equinox with the ContextFinder as TCCL</li>
 * <li>Installs the indriya-osgi-api and indriya-osgi-bundle bundles</li>
 * <li>Starts the test bundle which runs the reproducer tests</li>
 * </ol>
 * <p>
 * The test bundle's activator ({@code TestActivator}) attempts to access
 * {@code IndriyaAndOSGi.HOUR} from different threading contexts (activator
 * start, common pool, virtual thread, parallel stream) and reports
 * success/failure for each.
 *
 * @see <a href="https://github.com/eclipse-equinox/equinox/issues/1243">Issue
 *      1243</a>
 */
public class ReproducerApp {

	public static void main(String[] args) throws Exception {
		System.out.println("=".repeat(80));
		System.out.println("Issue 1243 Reproducer - ContextFinder TCCL on ForkJoinPool/Virtual Threads");
		System.out.println("=".repeat(80));
		System.out.println("Java version: " + System.getProperty("java.version"));
		System.out.println("Java vendor:  " + System.getProperty("java.vendor"));
		System.out.println("Java home:    " + System.getProperty("java.home"));
		System.out.println();

		// Check if custom thread factory should be activated
		// NOTE: The ForkJoinPool.common.threadFactory system property must be set
		// BEFORE the ForkJoinPool class is first used, because the common pool is
		// lazily initialized on first access. Setting it here (before any FJP use)
		// ensures it takes effect.
		if (Boolean.getBoolean("test.thread.factory")) {
			String factoryClass = ContextFinderForkJoinWorkerThreadFactory.class.getName();
			System.out.println("MITIGATION: Setting java.util.concurrent.ForkJoinPool.common.threadFactory");
			System.out.println("  to: " + factoryClass);
			System.out.println("  This will cause ForkJoinPool worker threads to inherit the TCCL");
			System.out.println("  from the thread that triggers common pool initialization.");
			System.setProperty("java.util.concurrent.ForkJoinPool.common.threadFactory", factoryClass);
			System.out.println();
		}

		String activeFactory = System.getProperty("java.util.concurrent.ForkJoinPool.common.threadFactory");
		if (activeFactory != null) {
			System.out.println("ForkJoinPool.common.threadFactory: " + activeFactory);
		} else {
			System.out.println("ForkJoinPool.common.threadFactory: <default JDK factory>");
		}
		System.out.println();

		// Create temp directory for OSGi storage
		Path storageDir = Files.createTempDirectory("equinox-reproducer-");
		System.out.println("OSGi storage: " + storageDir);

		// Configure framework
		Map<String, String> config = new HashMap<>();
		config.put(Constants.FRAMEWORK_STORAGE, storageDir.toAbsolutePath().toString());
		config.put(Constants.FRAMEWORK_STORAGE_CLEAN, Constants.FRAMEWORK_STORAGE_CLEAN_ONFIRSTINIT);
		// Propagate system properties for test control
		propagateProperty(config, "test.all");
		propagateProperty(config, "test.activator.start");
		propagateProperty(config, "test.common.pool");
		propagateProperty(config, "test.virtual.thread");
		propagateProperty(config, "test.parallel.stream");
		propagateProperty(config, "test.preload");
		propagateProperty(config, "test.user.workaround");
		propagateProperty(config, "test.thread.factory");

		// Find and create framework
		ServiceLoader<FrameworkFactory> loader = ServiceLoader.load(FrameworkFactory.class);
		FrameworkFactory factory = loader.findFirst()
				.orElseThrow(() -> new RuntimeException("No OSGi FrameworkFactory found on classpath"));

		System.out.println("Framework factory: " + factory.getClass().getName());
		Framework framework = factory.newFramework(config);

		try {
			// Initialize and start framework
			framework.init();
			framework.start();
			System.out.println("Framework started: " + framework.getSymbolicName() + " "
					+ framework.getVersion());

			// After framework start, the ContextFinder is set as TCCL on this thread.
			// If we have a custom thread factory, update it with the ContextFinder TCCL
			// so that new ForkJoinPool worker threads will inherit it.
			if (Boolean.getBoolean("test.thread.factory")) {
				ClassLoader contextFinderTccl = Thread.currentThread().getContextClassLoader();
				System.out.println("Updating ForkJoinPool thread factory with ContextFinder TCCL: "
						+ (contextFinderTccl != null ? contextFinderTccl.getClass().getName() : "null"));
				// Access the common pool to trigger initialization and get the factory
				ForkJoinPool commonPool = ForkJoinPool.commonPool();
				java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory fjpFactory = commonPool.getFactory();
				if (fjpFactory instanceof ContextFinderForkJoinWorkerThreadFactory ctxFactory) {
					ctxFactory.setContextClassLoader(contextFinderTccl);
				} else {
					System.out.println("WARNING: Common pool factory is not ContextFinderForkJoinWorkerThreadFactory: "
							+ fjpFactory.getClass().getName());
				}
			}

			System.out.println();

			BundleContext bc = framework.getBundleContext();

			// Install bundles from the target/bundles directory (populated by
			// copy-dependencies)
			System.out.println("Installing bundles...");
			Path bundlesDir = findBundlesDir();
			if (bundlesDir != null && Files.isDirectory(bundlesDir)) {
				try (var stream = Files.list(bundlesDir)) {
					stream.filter(p -> p.toString().endsWith(".jar")).sorted().forEach(jar -> {
						try {
							Bundle b = bc.installBundle(jar.toUri().toString());
							System.out.println("  Installed: " + b.getSymbolicName() + " "
									+ b.getVersion() + " from " + jar.getFileName());
						} catch (BundleException e) {
							System.out.println("  SKIP: " + jar.getFileName() + " - " + e.getMessage());
						}
					});
				}
			}

			// Also install our own sibling module JARs
			Path projectDir = findProjectDir();
			if (projectDir != null && projectDir.getParent() != null) {
				try (var dirs = Files.list(projectDir.getParent())) {
					dirs.filter(Files::isDirectory)
							.filter(d -> !d.getFileName().toString().equals("reproducer-app"))
							.forEach(dir -> {
								Path targetDir = dir.resolve("target");
								if (Files.isDirectory(targetDir)) {
									try (var jars = Files.list(targetDir)) {
										jars.filter(p -> p.toString().endsWith(".jar")
												&& !p.toString().contains("sources"))
												.forEach(jar -> {
													try {
														Bundle b = bc.installBundle(jar.toUri().toString());
														System.out.println("  Installed: " + b.getSymbolicName()
																+ " " + b.getVersion() + " from "
																+ jar.getFileName());
													} catch (BundleException e) {
														System.out.println("  SKIP: " + jar.getFileName()
																+ " - " + e.getMessage());
													}
												});
									} catch (IOException e) {
										// ignore
									}
								}
							});
				}
			}

			// Start all installed bundles
			System.out.println();
			System.out.println("Starting bundles...");
			for (Bundle b : bc.getBundles()) {
				if (b.getBundleId() != 0 && b.getState() != Bundle.ACTIVE) {
					try {
						b.start();
						System.out.println("  Started: " + b.getSymbolicName() + " " + b.getVersion());
					} catch (BundleException e) {
						System.out.println("  FAILED to start: " + b.getSymbolicName() + " - " + e.getMessage());
					}
				}
			}

			System.out.println();

			// Wait a bit for activator tests to complete
			Thread.sleep(2000);

		} finally {
			framework.stop();
			framework.waitForStop(10000);
			System.out.println("Framework stopped.");

			// Cleanup
			deleteRecursively(storageDir.toFile());
		}
	}

	private static void propagateProperty(Map<String, String> config, String key) {
		String value = System.getProperty(key);
		if (value != null) {
			config.put(key, value);
		}
	}

	private static Path findBundlesDir() {
		Path projectDir = findProjectDir();
		if (projectDir != null) {
			Path bundlesDir = projectDir.resolve("target/bundles");
			if (Files.isDirectory(bundlesDir)) {
				return bundlesDir;
			}
		}
		return null;
	}

	private static Path findProjectDir() {
		// Try to find the project directory from the classpath
		URL resource = ReproducerApp.class.getResource(
				"/" + ReproducerApp.class.getName().replace('.', '/') + ".class");
		if (resource != null) {
			String path = resource.getPath();
			// Path typically looks like: .../target/classes/org/...
			int targetIdx = path.indexOf("/target/");
			if (targetIdx > 0) {
				return Path.of(path.substring(0, targetIdx));
			}
		}
		// Fallback: use working directory
		return Path.of(System.getProperty("user.dir"));
	}

	private static void deleteRecursively(File file) {
		if (file.isDirectory()) {
			File[] children = file.listFiles();
			if (children != null) {
				for (File child : children) {
					deleteRecursively(child);
				}
			}
		}
		file.delete();
	}
}
