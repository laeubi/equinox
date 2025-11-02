package org.eclipse.equinox.common.tests.adaptable;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.eclipse.core.internal.runtime.AdapterManager;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.IAdapterFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class AdaptersTest {

	private AdapterManager manager;
	private java.util.List<IAdapterFactory> registeredFactories;

	@Before
	public void setUp() {
		manager = AdapterManager.getDefault();
		registeredFactories = new java.util.ArrayList<>();
	}

	@After
	public void tearDown() {
		// Clean up only the factories registered in this test
		for (IAdapterFactory factory : registeredFactories) {
			manager.unregisterAdapters(factory);
		}
		registeredFactories.clear();
	}

	private void registerFactory(IAdapterFactory factory, Class<?> adaptable) {
		manager.registerAdapters(factory, adaptable);
		registeredFactories.add(factory);
	}

	@Test
	public void testOptionalObjectIsNull() {
		Optional<?> optional = Adapters.of(null, Object.class);
		assertTrue(optional.isEmpty());
	}

	@Test(expected = NullPointerException.class)
	public void testOptionalAdapterTypeIsNull() {
		Adapters.of(new Object(), null);
	}

	@Test
	public void testOptionalOfNotAdaptableIsEmpty() {
		Optional<?> optional = Adapters.of(new ThisWillNotAdapt(), Runnable.class);
		assertTrue(optional.isEmpty());
	}

	@Test
	public void testOptionalOfAdaptable() {
		Optional<?> optional = Adapters.of(new ThisWillAdaptToRunnable(), Runnable.class);
		assertTrue(optional.isPresent());
	}

	@Test
	public void testConvertWithNullSource() {
		String result = Adapters.convert(null, String.class);
		assertNull("Convert should return null for null source", result);
	}

	@Test
	public void testConvertWithNullTarget() {
		Object result = Adapters.convert(new Object(), null);
		assertNull("Convert should return null for null target", result);
	}

	@Test
	public void testConvertDirectAdaptation() {
		// Register a simple adapter factory
		IAdapterFactory factory = new IAdapterFactory() {
			@Override
			public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
				if (adaptableObject instanceof TypeA && adapterType == TypeB.class) {
					return adapterType.cast(new TypeB());
				}
				return null;
			}

			@Override
			public Class<?>[] getAdapterList() {
				return new Class[] { TypeB.class };
			}
		};

		registerFactory(factory, TypeA.class);

		TypeA source = new TypeA();
		TypeB result = Adapters.convert(source, TypeB.class);
		assertNotNull("Direct conversion should succeed", result);
	}

	@Test
	public void testConvertSingleHopConversion() {
		// Register factories for A -> B and B -> C
		IAdapterFactory factoryAB = new IAdapterFactory() {
			@Override
			public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
				if (adaptableObject instanceof TypeA && adapterType == TypeB.class) {
					return adapterType.cast(new TypeB());
				}
				return null;
			}

			@Override
			public Class<?>[] getAdapterList() {
				return new Class[] { TypeB.class };
			}
		};

		IAdapterFactory factoryBC = new IAdapterFactory() {
			@Override
			public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
				if (adaptableObject instanceof TypeB && adapterType == TypeC.class) {
					return adapterType.cast(new TypeC());
				}
				return null;
			}

			@Override
			public Class<?>[] getAdapterList() {
				return new Class[] { TypeC.class };
			}
		};

		registerFactory(factoryAB, TypeA.class);
		registerFactory(factoryBC, TypeB.class);

		TypeA source = new TypeA();
		TypeC result = Adapters.convert(source, TypeC.class);
		assertNotNull("Single-hop conversion (A->B->C) should succeed", result);
	}

	@Test
	public void testConvertMultiHopConversion() {
		// Register factories for A -> B, B -> C, and C -> D
		IAdapterFactory factoryAB = new IAdapterFactory() {
			@Override
			public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
				if (adaptableObject instanceof TypeA && adapterType == TypeB.class) {
					return adapterType.cast(new TypeB());
				}
				return null;
			}

			@Override
			public Class<?>[] getAdapterList() {
				return new Class[] { TypeB.class };
			}
		};

		IAdapterFactory factoryBC = new IAdapterFactory() {
			@Override
			public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
				if (adaptableObject instanceof TypeB && adapterType == TypeC.class) {
					return adapterType.cast(new TypeC());
				}
				return null;
			}

			@Override
			public Class<?>[] getAdapterList() {
				return new Class[] { TypeC.class };
			}
		};

		IAdapterFactory factoryCD = new IAdapterFactory() {
			@Override
			public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
				if (adaptableObject instanceof TypeC && adapterType == TypeD.class) {
					return adapterType.cast(new TypeD());
				}
				return null;
			}

			@Override
			public Class<?>[] getAdapterList() {
				return new Class[] { TypeD.class };
			}
		};

		registerFactory(factoryAB, TypeA.class);
		registerFactory(factoryBC, TypeB.class);
		registerFactory(factoryCD, TypeC.class);

		TypeA source = new TypeA();
		TypeD result = Adapters.convert(source, TypeD.class);
		assertNotNull("Multi-hop conversion (A->B->C->D) should succeed", result);
	}

	@Test
	public void testConvertNoPathExists() {
		// Register only A -> B factory
		IAdapterFactory factory = new IAdapterFactory() {
			@Override
			public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
				if (adaptableObject instanceof TypeA && adapterType == TypeB.class) {
					return adapterType.cast(new TypeB());
				}
				return null;
			}

			@Override
			public Class<?>[] getAdapterList() {
				return new Class[] { TypeB.class };
			}
		};

		registerFactory(factory, TypeA.class);

		TypeA source = new TypeA();
		TypeD result = Adapters.convert(source, TypeD.class);
		assertNull("Conversion should fail when no path exists", result);
	}

	@Test
	public void testConvertFactoryReturnsNull() {
		// Register a factory that returns null
		IAdapterFactory factory = new IAdapterFactory() {
			@Override
			public <T> T getAdapter(Object adaptableObject, Class<T> adapterType) {
				return null; // Always returns null
			}

			@Override
			public Class<?>[] getAdapterList() {
				return new Class[] { TypeB.class };
			}
		};

		registerFactory(factory, TypeA.class);

		TypeA source = new TypeA();
		TypeB result = Adapters.convert(source, TypeB.class);
		assertNull("Conversion should fail when factory returns null", result);
	}

	// Test helper classes
	private static final class ThisWillNotAdapt {
	}

	private static final class ThisWillAdaptToRunnable implements Runnable {
		@Override
		public void run() {
		}
	}

	private static class TypeA {
	}

	private static class TypeB {
	}

	private static class TypeC {
	}

	private static class TypeD {
	}
}
