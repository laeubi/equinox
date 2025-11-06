---
layout: post
title: Resolver API Migration Guide
summary: Guide for migrating from the legacy Equinox Resolver API to the OSGi Resolver Specification
---

* The generated Toc will be an ordered list
{:toc}

This guide helps developers migrate from the legacy Equinox resolver API to the modern OSGi Resolver Specification. The legacy API is heavily used in Eclipse PDE and other Eclipse-based applications.

## Overview

### Legacy Equinox Resolver API

The historical Equinox resolver API provides:
- **Package**: `org.eclipse.osgi.service.resolver`
- **API Location**: `bundles/org.eclipse.osgi/container/src/org/eclipse/osgi/service/resolver`
- **Implementation**: `bundles/org.eclipse.osgi.compatibility.state`
- **Main Interfaces**: `PlatformAdmin`, `State`, `Resolver`, `BundleDescription`

### Modern OSGi Resolver API

The successor OSGi Resolver Specification provides:
- **Package**: `org.osgi.service.resolver`
- **API Location**: `bundles/org.eclipse.osgi/osgi/src/org/osgi/service/resolver`
- **Specification**: [OSGi Core Specification 8.0.0 - Resolver Service](https://docs.osgi.org/specification/osgi.core/8.0.0/service.resolver.html)
- **Main Interfaces**: `Resolver`, `ResolveContext`, `ResolutionException`

## Key Differences

### Architecture Changes

| Legacy API | Modern API | Notes |
|------------|------------|-------|
| State-based model with mutable `State` objects | Context-based model with immutable `Resource` objects | Modern API is more functional and thread-safe |
| `PlatformAdmin` service for state management | `Resolver` service with `ResolveContext` | Cleaner separation of concerns |
| Bundle-centric (`BundleDescription`) | Resource-centric (`Resource`, `Capability`, `Requirement`) | More generic and extensible |
| Resolver modifies state in-place | Resolver returns new wires without modifying input | Immutable approach prevents side effects |

### Core Concepts Mapping

| Legacy Concept | Modern Equivalent | Description |
|----------------|-------------------|-------------|
| `State` | `ResolveContext` + `Resource` collection | Represents the system state |
| `BundleDescription` | `Resource` (typically `BundleRevision`) | Represents a bundle |
| `ExportPackageDescription` | `Capability` with `PackageNamespace` | Package export |
| `ImportPackageSpecification` | `Requirement` with `PackageNamespace` | Package import |
| `BundleSpecification` | `Requirement` with `BundleNamespace` | Require-Bundle |
| `HostSpecification` | `Requirement` with `HostNamespace` | Fragment host |
| `GenericDescription` | `Capability` with custom namespace | Generic capability |
| `GenericSpecification` | `Requirement` with custom namespace | Generic requirement |
| `StateWire` | `Wire` | Represents a resolved dependency |
| `Resolver.resolve()` | `Resolver.resolve(ResolveContext)` | Resolution operation |

## Migration Steps

### 1. Obtaining the Resolver Service

#### Legacy API
```java
// Get PlatformAdmin service
ServiceReference<PlatformAdmin> ref = 
    bundleContext.getServiceReference(PlatformAdmin.class);
PlatformAdmin platformAdmin = bundleContext.getService(ref);

// Get a resolver
Resolver resolver = platformAdmin.createResolver();

// Get or create a state
State state = platformAdmin.getState();
```

#### Modern API
```java
// Get Resolver service
ServiceReference<Resolver> ref = 
    bundleContext.getServiceReference(Resolver.class);
Resolver resolver = bundleContext.getService(ref);

// Create a custom ResolveContext (see below)
ResolveContext context = new MyResolveContext();
```

### 2. Building the Resolution Context

#### Legacy API - State Population
```java
// Create a new state
StateObjectFactory factory = platformAdmin.getFactory();
State state = factory.createState(true);

// Add bundles to state
BundleDescription bundleDesc = factory.createBundleDescription(
    stateId, symbolicName, version, location, 
    requiredBundles, host, imports, exports, 
    providedCapabilities, singleton, attachFragments, 
    dynamicImports, platformFilter, execEnvs, genericRequires, 
    nativeCode);
state.addBundle(bundleDesc);

// Set resolver
state.setResolver(resolver);
resolver.setState(state);
```

#### Modern API - ResolveContext Implementation
```java
public class MyResolveContext extends ResolveContext {
    private final Collection<Resource> mandatoryResources;
    private final Collection<Resource> optionalResources;
    private final Map<Resource, Wiring> wirings;
    
    @Override
    public Collection<Resource> getMandatoryResources() {
        return mandatoryResources;
    }
    
    @Override
    public Collection<Resource> getOptionalResources() {
        return optionalResources;
    }
    
    @Override
    public List<Capability> findProviders(Requirement requirement) {
        // Return capabilities that match the requirement
        // This is where you implement your resource lookup logic
        List<Capability> providers = new ArrayList<>();
        for (Resource resource : getAllResources()) {
            for (Capability cap : resource.getCapabilities(
                    requirement.getNamespace())) {
                if (matches(requirement, cap)) {
                    providers.add(cap);
                }
            }
        }
        return providers;
    }
    
    @Override
    public Map<Resource, Wiring> getWirings() {
        // Return existing wirings (already resolved bundles)
        return wirings;
    }
    
    @Override
    public int insertHostedCapability(List<Capability> capabilities,
            HostedCapability hostedCapability) {
        // Insert fragment capabilities into host's capability list
        // Return the index where it was inserted
        capabilities.add(hostedCapability);
        return capabilities.size() - 1;
    }
    
    @Override
    public boolean isEffective(Requirement requirement) {
        // Check if requirement is effective
        // Typically checks the "effective" directive
        String effective = requirement.getDirectives()
            .get(Namespace.REQUIREMENT_EFFECTIVE_DIRECTIVE);
        return effective == null || 
               Namespace.EFFECTIVE_RESOLVE.equals(effective);
    }
}
```

### 3. Performing Resolution

#### Legacy API
```java
// Resolve the state
state.resolve(bundlesToResolve);

// Check resolution results
BundleDescription[] bundles = state.getBundles();
for (BundleDescription bundle : bundles) {
    if (bundle.isResolved()) {
        // Bundle resolved successfully
        BundleDescription[] requiredBundles = 
            bundle.getResolvedRequires();
        ExportPackageDescription[] resolvedImports = 
            bundle.getResolvedImports();
    } else {
        // Bundle failed to resolve
        ResolverError[] errors = state.getResolverErrors(bundle);
        for (ResolverError error : errors) {
            System.out.println("Error: " + error);
        }
    }
}
```

#### Modern API
```java
try {
    // Perform resolution
    Map<Resource, List<Wire>> resolution = resolver.resolve(context);
    
    // Process resolution results
    for (Map.Entry<Resource, List<Wire>> entry : resolution.entrySet()) {
        Resource resource = entry.getKey();
        List<Wire> wires = entry.getValue();
        
        // Wires represent resolved dependencies
        for (Wire wire : wires) {
            Requirement requirement = wire.getRequirement();
            Capability capability = wire.getCapability();
            Resource provider = wire.getProvider();
            
            System.out.println("Resolved: " + 
                requirement.getNamespace() + 
                " provided by " + provider);
        }
    }
} catch (ResolutionException e) {
    // Resolution failed
    Collection<Requirement> unresolvedRequirements = 
        e.getUnresolvedRequirements();
    for (Requirement req : unresolvedRequirements) {
        System.out.println("Unresolved: " + req);
    }
}
```

### 4. Working with Resources

#### Legacy API - BundleDescription
```java
BundleDescription bundle = state.getBundle(bundleId);

// Get symbolic name and version
String symbolicName = bundle.getSymbolicName();
Version version = bundle.getVersion();

// Get dependencies
ImportPackageSpecification[] imports = bundle.getImportPackages();
BundleSpecification[] requiredBundles = bundle.getRequiredBundles();

// Get capabilities
ExportPackageDescription[] exports = bundle.getExportPackages();
GenericDescription[] capabilities = bundle.getGenericCapabilities();

// Check if resolved
boolean resolved = bundle.isResolved();
if (resolved) {
    ExportPackageDescription[] resolvedImports = 
        bundle.getResolvedImports();
}
```

#### Modern API - Resource/BundleRevision
```java
// Assuming resource is a BundleRevision
BundleRevision revision = (BundleRevision) resource;
Bundle bundle = revision.getBundle();

// Get symbolic name and version
String symbolicName = revision.getSymbolicName();
Version version = revision.getVersion();

// Get requirements
List<Requirement> requirements = 
    resource.getRequirements(null); // null = all namespaces

// Get specific package imports
List<Requirement> packageReqs = 
    resource.getRequirements(PackageNamespace.PACKAGE_NAMESPACE);

// Get capabilities
List<Capability> capabilities = 
    resource.getCapabilities(null); // null = all namespaces

// Get specific package exports
List<Capability> packageCaps = 
    resource.getCapabilities(PackageNamespace.PACKAGE_NAMESPACE);

// Check if resolved (via Wiring)
BundleWiring wiring = revision.getWiring();
boolean resolved = (wiring != null);
if (resolved) {
    List<Wire> packageWires = 
        wiring.getRequiredWires(PackageNamespace.PACKAGE_NAMESPACE);
}
```

### 5. Dynamic Imports

#### Legacy API
```java
// Add dynamic imports to a bundle
State state = platformAdmin.getState();
BundleDescription bundle = state.getBundle(bundleId);

StateObjectFactory factory = platformAdmin.getFactory();
ImportPackageSpecification dynamicImport = 
    factory.createImportPackageSpecification(
        "com.example.dynamic", 
        new VersionRange("[1.0.0,2.0.0)"), 
        null, null, null, null, null);

state.addDynamicImportPackages(bundle, 
    new ImportPackageSpecification[] { dynamicImport });

// Resolve dynamic import
state.resolve(new BundleDescription[] { bundle });
```

#### Modern API
```java
// Resolve a dynamic import
BundleWiring hostWiring = bundle.adapt(BundleWiring.class);
Requirement dynamicRequirement = // ... get from wiring

try {
    Map<Resource, List<Wire>> resolution = 
        resolver.resolveDynamic(context, hostWiring, dynamicRequirement);
    
    // Process dynamic resolution result
    List<Wire> newWires = resolution.get(hostWiring.getResource());
    for (Wire wire : newWires) {
        System.out.println("Dynamic import resolved: " + 
            wire.getCapability());
    }
} catch (ResolutionException e) {
    System.out.println("Dynamic import failed: " + e.getMessage());
}
```

## Common Patterns and Best Practices

### Pattern 1: Converting BundleDescription to Resource

```java
// Legacy: BundleDescription
BundleDescription bundleDesc = state.getBundle(symbolicName, version);

// Modern: Get BundleRevision from Bundle
Bundle bundle = // ... obtain bundle
BundleRevision resource = bundle.adapt(BundleRevision.class);
```

### Pattern 2: Requirement Matching

#### Legacy API
```java
ImportPackageSpecification importSpec = // ...
ExportPackageDescription exportDesc = // ...

// Check if export matches import
boolean matches = importSpec.isSatisfiedBy(exportDesc);
```

#### Modern API
```java
Requirement requirement = // ...
Capability capability = // ...

// Use requirement filter
String filterStr = requirement.getDirectives()
    .get(Namespace.REQUIREMENT_FILTER_DIRECTIVE);
if (filterStr != null) {
    Filter filter = FrameworkUtil.createFilter(filterStr);
    boolean matches = filter.matches(capability.getAttributes());
}
```

### Pattern 3: Iterating Over Dependencies

#### Legacy API
```java
BundleDescription bundle = state.getBundle(bundleId);
if (bundle.isResolved()) {
    // Resolved Require-Bundle
    BundleDescription[] requiredBundles = bundle.getResolvedRequires();
    
    // Resolved Import-Package
    ExportPackageDescription[] imports = bundle.getResolvedImports();
}
```

#### Modern API
```java
BundleRevision revision = bundle.adapt(BundleRevision.class);
BundleWiring wiring = revision.getWiring();

if (wiring != null) {
    // All wires (resolved dependencies)
    List<Wire> allWires = wiring.getRequiredWires(null);
    
    // Package import wires
    List<Wire> packageWires = 
        wiring.getRequiredWires(PackageNamespace.PACKAGE_NAMESPACE);
    
    // Require-Bundle wires
    List<Wire> bundleWires = 
        wiring.getRequiredWires(BundleNamespace.BUNDLE_NAMESPACE);
    
    for (Wire wire : allWires) {
        Resource provider = wire.getProvider();
        Capability capability = wire.getCapability();
        // Process wire...
    }
}
```

## Missing Features and Workarounds

### 1. State Snapshots and Comparison

**Legacy API**: The `State` interface provides `compare()` method to get differences between states.

**Modern API**: No direct equivalent. **Workaround**: Maintain your own tracking of resources and wirings before/after resolution.

```java
// Workaround: Track changes manually
Set<Resource> beforeResources = new HashSet<>(context.getMandatoryResources());
Map<Resource, List<Wire>> resolution = resolver.resolve(context);
Set<Resource> afterResources = new HashSet<>(resolution.keySet());
afterResources.addAll(beforeResources);

// Identify newly resolved resources
Set<Resource> newResources = new HashSet<>(resolution.keySet());
newResources.removeAll(beforeResources);
```

### 2. Mutable State Manipulation

**Legacy API**: Direct state modification via `addBundle()`, `removeBundle()`, `updateBundle()`.

**Modern API**: Immutable resource model. **Workaround**: Create new `ResolveContext` instances with updated resource collections.

```java
// Workaround: Create new context with modifications
public class ModifiableResolveContext extends ResolveContext {
    private final Set<Resource> resources = new HashSet<>();
    
    public void addResource(Resource resource) {
        resources.add(resource);
    }
    
    public void removeResource(Resource resource) {
        resources.remove(resource);
    }
    
    @Override
    public Collection<Resource> getMandatoryResources() {
        return Collections.unmodifiableSet(resources);
    }
}
```

### 3. ResolverError Details

**Legacy API**: `ResolverError` provides detailed error information with error types.

**Modern API**: `ResolutionException` with unresolved requirements. **Workaround**: Analyze unresolved requirements to determine error causes.

```java
try {
    resolver.resolve(context);
} catch (ResolutionException e) {
    // Analyze unresolved requirements
    for (Requirement req : e.getUnresolvedRequirements()) {
        Resource resource = req.getResource();
        String namespace = req.getNamespace();
        
        // Determine error type based on namespace and attributes
        if (PackageNamespace.PACKAGE_NAMESPACE.equals(namespace)) {
            System.out.println("Missing package: " + 
                req.getAttributes().get(PackageNamespace.PACKAGE_NAMESPACE));
        } else if (BundleNamespace.BUNDLE_NAMESPACE.equals(namespace)) {
            System.out.println("Missing required bundle: " + 
                req.getAttributes().get(BundleNamespace.BUNDLE_NAMESPACE));
        }
    }
}
```

### 4. StateObjectFactory

**Legacy API**: `StateObjectFactory` for creating bundle descriptions and specifications.

**Modern API**: No factory needed; create `Resource`, `Capability`, and `Requirement` implementations directly.

**Workaround**: Implement your own resource/capability/requirement classes or use existing implementations from OSGi framework.

```java
// Example: Custom Resource implementation
public class CustomResource implements Resource {
    private final List<Capability> capabilities = new ArrayList<>();
    private final List<Requirement> requirements = new ArrayList<>();
    
    public void addCapability(Capability capability) {
        capabilities.add(capability);
    }
    
    public void addRequirement(Requirement requirement) {
        requirements.add(requirement);
    }
    
    @Override
    public List<Capability> getCapabilities(String namespace) {
        if (namespace == null) return capabilities;
        return capabilities.stream()
            .filter(c -> namespace.equals(c.getNamespace()))
            .collect(Collectors.toList());
    }
    
    @Override
    public List<Requirement> getRequirements(String namespace) {
        if (namespace == null) return requirements;
        return requirements.stream()
            .filter(r -> namespace.equals(r.getNamespace()))
            .collect(Collectors.toList());
    }
}
```

### 5. Resolver Hooks

**Legacy API**: Limited hook support through `State` and `Resolver` interaction.

**Modern API**: Uses standard OSGi `ResolverHookFactory` service. Migration is straightforward - register your hook factory service.

```java
// Register resolver hook
bundleContext.registerService(ResolverHookFactory.class, 
    new MyResolverHookFactory(), null);
```

## Additional Considerations

### Thread Safety

- **Legacy API**: `State` objects are not thread-safe by default
- **Modern API**: `ResolveContext` must be thread-safe; resolver may call methods concurrently

### Performance

- **Legacy API**: Optimized for incremental updates to mutable state
- **Modern API**: Optimized for immutable snapshots; may require more memory for large state changes

### Testing

When testing resolution:

```java
// Modern API testing pattern
@Test
public void testResolution() {
    // Create test resources
    Resource resource = createTestResource();
    
    // Create resolve context
    ResolveContext context = new ResolveContext() {
        @Override
        public Collection<Resource> getMandatoryResources() {
            return Collections.singleton(resource);
        }
        
        @Override
        public List<Capability> findProviders(Requirement req) {
            // Return test providers that match the requirement
            List<Capability> testProviders = new ArrayList<>();
            // Add capabilities that match the requirement
            // testProviders.add(someMatchingCapability);
            return testProviders;
        }
    };
    
    // Resolve
    Map<Resource, List<Wire>> result = resolver.resolve(context);
    
    // Assert
    assertNotNull(result.get(resource));
}
```

## Migration Checklist

- [ ] Identify all usages of `PlatformAdmin`, `State`, and `Resolver` in your codebase
- [ ] Replace `State` with `ResolveContext` implementation
- [ ] Convert `BundleDescription` references to `Resource`/`BundleRevision`
- [ ] Replace `ExportPackageDescription`/`ImportPackageSpecification` with `Capability`/`Requirement`
- [ ] Update resolution logic to use `Resolver.resolve(ResolveContext)`
- [ ] Handle `ResolutionException` instead of checking `ResolverError`
- [ ] Update dynamic import logic to use `resolveDynamic()`
- [ ] Implement workarounds for missing features (state comparison, mutable state)
- [ ] Add proper thread synchronization for `ResolveContext` implementations
- [ ] Update tests to use modern API patterns
- [ ] Review performance characteristics for your use case

## Resources

- [OSGi Core Specification - Resolver Service](https://docs.osgi.org/specification/osgi.core/8.0.0/service.resolver.html)
- [OSGi Core Specification - Resource API](https://docs.osgi.org/specification/osgi.core/8.0.0/framework.resource.html)
- [Legacy Resolver API](https://github.com/laeubi/equinox/tree/master/bundles/org.eclipse.osgi/container/src/org/eclipse/osgi/service/resolver)
- [OSGi Resolver API](https://github.com/laeubi/equinox/tree/master/bundles/org.eclipse.osgi/osgi/src/org/osgi/service/resolver)
- [Compatibility State Implementation](https://github.com/laeubi/equinox/tree/master/bundles/org.eclipse.osgi.compatibility.state)

## Getting Help

If you encounter issues during migration:
1. Check if your use case is covered in this guide
2. Review the OSGi specification for detailed API documentation
3. Examine the Equinox implementation for reference
4. Ask questions on the [Eclipse forums](https://www.eclipse.org/forums/)
5. Report issues on [GitHub](https://github.com/eclipse-equinox/equinox/issues)
