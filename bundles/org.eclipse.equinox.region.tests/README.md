# Equinox Region Tests

This test bundle contains tests for the Eclipse Equinox Region functionality.

## Test Stubs

The tests use custom stub implementations located in `org.eclipse.equinox.region.tests.stubs` package:

- **StubBundle** - Mock implementation of OSGi Bundle that fires BundleEvents
- **StubBundleContext** - Mock implementation of OSGi BundleContext that manages listeners and fires events
- **StubServiceRegistration** - Mock implementation of OSGi ServiceRegistration that fires ServiceEvents
- **StubServiceReference** - Mock implementation of OSGi ServiceReference
- **StubFilter** - Simple filter implementation for testing

These stubs replace the previous Virgo test stubs that required AspectJ. The new stubs explicitly fire OSGi events without using aspect-oriented programming, eliminating the dependency on AspectJ.

## History

Prior to the removal of the AspectJ dependency, these tests used the Virgo test stubs (`org.eclipse.virgo.teststubs.osgi.jar`) which relied on AspectJ aspects to automatically fire OSGi events. The tests were disabled due to this AspectJ dependency (see bug 470000).

The stubs have been reimplemented to explicitly fire events in the appropriate methods, maintaining the same behavior while removing the AspectJ requirement.
