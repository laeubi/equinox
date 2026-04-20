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
package org.eclipse.equinox.examples.contextfinder;

import javax.measure.Unit;
import javax.measure.quantity.Time;

import tech.units.indriya.unit.Units;

/**
 * Simulates the real-world scenario from issue 1243: a class with a static
 * field that triggers ServiceLoader-based class loading via indriya's
 * {@code Units.HOUR}.
 * <p>
 * When {@code Units.HOUR} is first accessed, indriya uses
 * {@link java.util.ServiceLoader} to discover
 * {@code javax.measure.spi.SystemOfUnitsService} implementations. ServiceLoader
 * uses the Thread Context Class Loader (TCCL) to find service implementations.
 * <p>
 * In an OSGi environment, if this first access happens on a ForkJoinPool common
 * pool thread (e.g. inside a parallel stream), the TCCL is the system class
 * loader instead of OSGi's ContextFinder, causing a
 * {@code ClassNotFoundException} for the service implementation class.
 *
 * @see <a href="https://github.com/eclipse-equinox/equinox/issues/1243">Issue
 *      1243</a>
 */
public class IndriyaAndOSGi {

	/**
	 * Static field that triggers indriya ServiceLoader class loading on first
	 * access. The class loading happens via:
	 * <ol>
	 * <li>{@code Units.HOUR} → static initializer of {@code Units}</li>
	 * <li>{@code Units} → {@code ServiceLoader.load(SystemOfUnitsService.class)}
	 * </li>
	 * <li>ServiceLoader uses TCCL → fails if TCCL is not ContextFinder</li>
	 * </ol>
	 */
	public static final Unit<Time> HOUR = Units.HOUR;

	private IndriyaAndOSGi() {
		// Utility class
	}

	/**
	 * Explicitly access HOUR to trigger class loading. Useful for testing when
	 * the static field access alone isn't sufficient to demonstrate the issue.
	 *
	 * @return the HOUR unit, or throws if ServiceLoader fails
	 */
	public static Unit<Time> getHour() {
		return HOUR;
	}
}
