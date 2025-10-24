# Equinox Common

This bundle provides common runtime utilities and APIs used across the Eclipse platform and Equinox framework.

## Features

### Core Runtime APIs
- **Runtime utilities** - Common runtime functionality and utilities
- **Status and error handling** - Progress monitoring and error reporting APIs
- **Adapter framework** - Dynamic type adaptation support
- **Event services** - Basic event notification infrastructure

### Exported Packages
- `org.eclipse.core.runtime` - Core runtime APIs including IStatus, IProgressMonitor, and adapter framework
- `org.eclipse.core.text` - Text manipulation utilities
- `org.eclipse.equinox.events` - Event notification services

### Split Packages
This bundle provides split packages that are shared with other runtime bundles:
- `org.eclipse.core.runtime` - Shared with the Eclipse Core Runtime bundle
- `org.eclipse.core.internal.runtime` - Internal runtime utilities shared with trusted bundles

## Usage

Add a dependency to this bundle in your MANIFEST.MF:

```
Require-Bundle: org.eclipse.equinox.common;bundle-version="3.0.0"
```

Or import specific packages:

```
Import-Package: org.eclipse.core.runtime,
 org.eclipse.core.text
```

## Requirements

- JavaSE-17 or higher
- org.eclipse.osgi bundle (version 3.17.200 or higher)

## Related Bundles

This bundle works closely with:
- `org.eclipse.core.runtime` - Eclipse Core Runtime
- `org.eclipse.equinox.registry` - Extension Registry
- `org.eclipse.equinox.preferences` - Preference Service
- `org.eclipse.core.jobs` - Job Scheduler
