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

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;

/**
 * A custom {@link ForkJoinWorkerThreadFactory} that propagates the Thread
 * Context Class Loader (TCCL) from the thread that created the pool to each new
 * worker thread.
 * <p>
 * This factory is designed to be used as the
 * {@code java.util.concurrent.ForkJoinPool.common.threadFactory} system property
 * value. When set, the ForkJoinPool common pool will use this factory to create
 * worker threads that inherit the TCCL (e.g. OSGi's ContextFinder) instead of
 * using the system class loader.
 * <p>
 * <b>Usage:</b>
 * <pre>
 * java -Djava.util.concurrent.ForkJoinPool.common.threadFactory=\
 *   org.eclipse.equinox.examples.contextfinder.app.ContextFinderForkJoinWorkerThreadFactory \
 *   ...
 * </pre>
 * <p>
 * <b>Important:</b> This class must be on the system classpath (not inside an
 * OSGi bundle) because the ForkJoinPool common pool is initialized very early
 * by the JVM, before any OSGi framework is started. The class is loaded by the
 * system class loader when the {@code ForkJoinPool} class is first used.
 * <p>
 * <b>How it works:</b>
 * <ol>
 * <li>When the ForkJoinPool common pool is initialized, it reads the
 *     {@code java.util.concurrent.ForkJoinPool.common.threadFactory} system
 *     property and instantiates this factory.</li>
 * <li>At factory creation time, the TCCL of the current thread (typically the
 *     main thread, which has the ContextFinder set by Equinox) is captured.</li>
 * <li>Each new worker thread created by this factory gets the captured TCCL
 *     set as its context class loader.</li>
 * </ol>
 * <p>
 * <b>Caveat:</b> The common pool may be initialized before the OSGi framework
 * sets the ContextFinder as TCCL. In that case, the captured TCCL will be the
 * system class loader, and this factory will have no effect. To handle this,
 * a more sophisticated implementation could lazily look up the ContextFinder
 * from the framework or use a dynamic proxy class loader.
 *
 * @see <a href="https://github.com/eclipse-equinox/equinox/issues/1243">Issue
 *      1243</a>
 */
public class ContextFinderForkJoinWorkerThreadFactory implements ForkJoinWorkerThreadFactory {

	/**
	 * The TCCL captured at the time this factory was created. If the factory is
	 * created after the OSGi framework has set the ContextFinder as TCCL on the
	 * main thread, this will be the ContextFinder. Otherwise, it will be the
	 * system class loader and we fall back to dynamic lookup.
	 */
	private volatile ClassLoader capturedTccl;

	/**
	 * Creates a new factory, capturing the TCCL of the current thread.
	 * <p>
	 * This constructor is called by the JVM when it reads the
	 * {@code java.util.concurrent.ForkJoinPool.common.threadFactory} system
	 * property. It must have a public no-arg constructor.
	 */
	public ContextFinderForkJoinWorkerThreadFactory() {
		this.capturedTccl = Thread.currentThread().getContextClassLoader();
		System.out.println("[ContextFinderFJPFactory] Created. Captured TCCL: "
				+ (capturedTccl != null ? capturedTccl.getClass().getName()
						+ "@" + Integer.toHexString(capturedTccl.hashCode())
						: "null")
				+ " from thread: " + Thread.currentThread().getName());
		if (capturedTccl == null || !capturedTccl.getClass().getName().contains("ContextFinder")) {
			System.out.println("[ContextFinderFJPFactory] WARNING: Captured TCCL is NOT a ContextFinder.");
			System.out.println("[ContextFinderFJPFactory] Will dynamically resolve ContextFinder when creating worker threads.");
		}
	}

	/**
	 * Allows updating the TCCL to propagate after the factory has been created.
	 * This is useful when the factory is created before the OSGi framework
	 * sets the ContextFinder as TCCL.
	 *
	 * @param tccl the class loader to set on new worker threads
	 */
	public void setContextClassLoader(ClassLoader tccl) {
		this.capturedTccl = tccl;
		System.out.println("[ContextFinderFJPFactory] Updated TCCL to: "
				+ (tccl != null ? tccl.getClass().getName()
						+ "@" + Integer.toHexString(tccl.hashCode())
						: "null"));
	}

	@Override
	public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
		// Create our own ForkJoinWorkerThread subclass that properly sets the TCCL.
		// We cannot use defaultForkJoinWorkerThreadFactory because on Java 25 it creates
		// InnocuousForkJoinWorkerThread which forcibly sets TCCL to the system class loader.
		ContextFinderForkJoinWorkerThread thread = new ContextFinderForkJoinWorkerThread(pool);
		if (capturedTccl != null) {
			thread.setContextClassLoader(capturedTccl);
		}
		return thread;
	}

	/**
	 * A ForkJoinWorkerThread subclass that allows setting a custom TCCL.
	 * Unlike InnocuousForkJoinWorkerThread (used by default in Java 25),
	 * this thread does not forcibly reset the TCCL to the system class loader.
	 */
	static class ContextFinderForkJoinWorkerThread extends ForkJoinWorkerThread {
		ContextFinderForkJoinWorkerThread(ForkJoinPool pool) {
			super(pool);
		}
	}
}
