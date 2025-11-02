/*******************************************************************************
 * Copyright (c) 2015, 2021 IBM Corporation and others.
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
 *     Lars Vogel <Lars.Vogel@vogella.com> - Bug 478685, 478864, 479849
 *     Christoph Läubrich - Bug 577645 - [Adapters] provide a method that returns an Optional for an adapted type
 *******************************************************************************/
package org.eclipse.core.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import org.eclipse.core.internal.runtime.*;
import org.eclipse.osgi.util.NLS;

/**
 * Provides a standard way to request adapters from adaptable objects
 *
 * @see IAdaptable
 * @see IAdapterManager
 * @since 3.8
 */
public class Adapters {
	/**
	 * Maximum depth for conversion path search to prevent excessive computation.
	 */
	private static final int MAX_CONVERSION_DEPTH = 10;

	/**
	 * If it is possible to adapt the given object to the given type, this returns
	 * the adapter. Performs the following checks:
	 *
	 * <ol>
	 * <li>Returns <code>sourceObject</code> if it is an instance of the adapter
	 * type.</li>
	 * <li>If sourceObject implements IAdaptable, it is queried for adapters.</li>
	 * <li>Finally, the adapter manager is consulted for adapters</li>
	 * </ol>
	 *
	 * Otherwise returns null.
	 *
	 * @param <T>             class type to adapt to
	 * @param sourceObject    object to adapt, can be null
	 * @param adapter         type to adapt to
	 * @param allowActivation if true, plug-ins may be activated if necessary to
	 *                        provide the requested adapter. if false, the method
	 *                        will return null if an adapter cannot be provided from
	 *                        activated plug-ins.
	 * @return a representation of sourceObject that is assignable to the adapter
	 *         type, or null if no such representation exists
	 */
	@SuppressWarnings("unchecked")
	public static <T> T adapt(Object sourceObject, Class<T> adapter, boolean allowActivation) {
		if (sourceObject == null) {
			return null;
		}
		if (adapter.isInstance(sourceObject)) {
			return (T) sourceObject;
		}

		if (sourceObject instanceof IAdaptable adaptable) {
			Object result = adaptable.getAdapter(adapter);
			if (result != null) {
				// Sanity-check
				if (!adapter.isInstance(result)) {
					throw new AssertionFailedException(
							adaptable.getClass().getName() + ".getAdapter(" + adapter.getName() + ".class) returned " //$NON-NLS-1$//$NON-NLS-2$
									+ result.getClass().getName() + " that is not an instance the requested type"); //$NON-NLS-1$
				}
				return (T) result;
			}
		}

		// If the source object is a platform object then it's already tried calling
		// AdapterManager.getAdapter,
		// so there's no need to try it again.
		if ((sourceObject instanceof PlatformObject) && !allowActivation) {
			return null;
		}

		String adapterId = adapter.getName();
		Object result = queryAdapterManager(sourceObject, adapterId, allowActivation);
		if (result != null) {
			// Sanity-check
			if (!adapter.isInstance(result)) {
				throw new AssertionFailedException("An adapter factory for " //$NON-NLS-1$
						+ sourceObject.getClass().getName() + " returned " + result.getClass().getName() //$NON-NLS-1$
						+ " that is not an instance of " + adapter.getName()); //$NON-NLS-1$
			}
			return (T) result;
		}

		return null;
	}

	/**
	 * If it is possible to adapt the given object to the given type, this returns
	 * the adapter.
	 * <p>
	 * Convenience method for calling <code>adapt(Object, Class, true)</code>.
	 * <p>
	 * See {@link #adapt(Object, Class, boolean)}.
	 *
	 * @param <T>          class type to adapt to
	 * @param sourceObject object to adapt, can be null
	 * @param adapter      type to adapt to
	 * @return a representation of sourceObject that is assignable to the adapter
	 *         type, or null if no such representation exists
	 */
	public static <T> T adapt(Object sourceObject, Class<T> adapter) {
		return adapt(sourceObject, adapter, true);
	}

	/**
	 * If it is possible to adapt the given object to the given type, this returns
	 * an optional holding the adapter, in all other cases it returns an empty
	 * optional.
	 *
	 * @param sourceObject object to adapt, if <code>null</code> then
	 *                     {@link Optional#empty()} is returned
	 * @param adapter      type to adapt to, must not be <code>null</code>
	 * @param <T>          type to adapt to
	 * @return an Optional representation of sourceObject that is assignable to the
	 *         adapter type, or an empty Optional otherwise
	 * @since 3.16
	 */
	public static <T> Optional<T> of(Object sourceObject, Class<T> adapter) {
		if (sourceObject == null) {
			return Optional.empty();
		}
		Objects.requireNonNull(adapter);
		try {
			return Optional.ofNullable(adapt(sourceObject, adapter));
		} catch (AssertionFailedException e) {
			RuntimeLog.log(Status.error(
					NLS.bind(CommonMessages.adapters_internal_error_of, sourceObject.getClass().getName(), adapter.getClass().getName(), e.getLocalizedMessage()),
					e));
			return Optional.empty();
		}
	}

	/**
	 * Attempts to convert the given object to the given type by finding and
	 * executing an adaptation path through intermediate types. This method performs
	 * multi-hop conversions when a direct adaptation is not available.
	 * <p>
	 * The conversion process:
	 * <ol>
	 * <li>First tries direct adaptation from sourceObject to adapter type (shortcut)</li>
	 * <li>If direct adaptation fails, searches for a conversion path through
	 * intermediate types using a breadth-first search to find the shortest path</li>
	 * <li>Executes the found conversion path and returns the first successful
	 * non-null result</li>
	 * </ol>
	 * <p>
	 * Note that this method may activate plug-ins if necessary to provide the
	 * requested adapters.
	 * </p>
	 *
	 * @param <T>          class type to convert to
	 * @param sourceObject object to convert, can be null
	 * @param adapter      type to convert to
	 * @return a representation of sourceObject that is assignable to the adapter
	 *         type, or null if no conversion path exists or all paths fail
	 * @since 3.17
	 */
	public static <T> T convert(Object sourceObject, Class<T> adapter) {
		if (sourceObject == null || adapter == null) {
			return null;
		}

		// Shortcut: try direct adaptation first
		T directResult = adapt(sourceObject, adapter, true);
		if (directResult != null) {
			return directResult;
		}

		// Find conversion paths using BFS
		List<List<String>> paths = findConversionPaths(sourceObject.getClass(), adapter);
		
		// Try each path in order (shortest first due to BFS)
		for (List<String> path : paths) {
			T result = tryConversionPath(sourceObject, adapter, path);
			if (result != null) {
				return result;
			}
		}

		return null;
	}

	/**
	 * Finds conversion paths from source class to target class using breadth-first
	 * search. Returns paths sorted by length (shortest first).
	 * Each path contains intermediate type names (not including source or target).
	 */
	private static List<List<String>> findConversionPaths(Class<?> sourceClass, Class<?> targetClass) {
		List<List<String>> allPaths = new ArrayList<>();
		IAdapterManager manager = AdapterManager.getDefault();
		String targetName = targetClass.getName();

		// BFS to find paths - search from source to target
		Queue<PathNode> queue = new LinkedList<>();
		Set<String> visited = new HashSet<>();
		
		// Start from the source type
		String sourceName = sourceClass.getName();
		queue.add(new PathNode(sourceName, new ArrayList<>()));
		visited.add(sourceName);

		int currentDepth = 0;

		while (!queue.isEmpty() && allPaths.isEmpty() && currentDepth < MAX_CONVERSION_DEPTH) {
			int levelSize = queue.size();
			List<List<String>> currentLevelPaths = new ArrayList<>();

			for (int i = 0; i < levelSize; i++) {
				PathNode current = queue.poll();
				
				// Get all types we can adapt to from current type
				Class<?> currentClass;
				try {
					currentClass = Class.forName(current.typeName);
				} catch (ClassNotFoundException e) {
					// Log at debug level to aid troubleshooting
					RuntimeLog.log(Status.info("Could not load class " + current.typeName //$NON-NLS-1$
							+ " during conversion path search: " + e.getMessage())); //$NON-NLS-1$
					continue;
				}
				
				String[] adapterTypes = manager.computeAdapterTypes(currentClass);
				for (String adapterType : adapterTypes) {
					if (adapterType.equals(targetName)) {
						// Found a path to target!
						currentLevelPaths.add(new ArrayList<>(current.path));
					} else if (!visited.contains(adapterType)) {
						// Add to queue for further exploration
						visited.add(adapterType);
						List<String> newPath = new ArrayList<>(current.path);
						newPath.add(adapterType);
						queue.add(new PathNode(adapterType, newPath));
					}
				}
			}

			// If we found paths at this level, use them (shortest paths)
			if (!currentLevelPaths.isEmpty()) {
				allPaths.addAll(currentLevelPaths);
			}

			currentDepth++;
		}

		return allPaths;
	}

	/**
	 * Tries to execute a conversion path.
	 */
	@SuppressWarnings("unchecked")
	private static <T> T tryConversionPath(Object sourceObject, Class<T> targetClass, List<String> path) {
		Object current = sourceObject;
		
		// Apply each intermediate conversion
		for (String typeName : path) {
			try {
				Class<?> intermediateClass = Class.forName(typeName);
				current = adapt(current, intermediateClass, true);
				if (current == null) {
					return null;
				}
			} catch (ClassNotFoundException e) {
				// Log at debug level to aid troubleshooting
				RuntimeLog.log(Status.info("Could not load class " + typeName //$NON-NLS-1$
						+ " during conversion path execution: " + e.getMessage())); //$NON-NLS-1$
				return null;
			}
		}
		
		// Final conversion to target type
		T result = adapt(current, targetClass, true);
		return result;
	}

	/**
	 * Helper class for BFS path finding.
	 */
	private static class PathNode {
		final String typeName;
		final List<String> path;

		PathNode(String typeName, List<String> path) {
			this.typeName = typeName;
			this.path = path;
		}
	}

	private static Object queryAdapterManager(Object sourceObject, String adapterId, boolean allowActivation) {
		Object result;
		if (allowActivation) {
			result = AdapterManager.getDefault().loadAdapter(sourceObject, adapterId);
		} else {
			result = AdapterManager.getDefault().getAdapter(sourceObject, adapterId);
		}
		return result;
	}
}
