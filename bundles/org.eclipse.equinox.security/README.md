# Equinox Security

This bundle provides security infrastructure for the Eclipse Equinox platform, including:

- **Java Authentication and Authorization Service (JAAS)** support
- **Secure Storage** API for storing and retrieving sensitive data
- **Password Management** with encryption capabilities
- **Authentication and Authorization** services

## Features

### JAAS Integration
- Login configuration providers
- Custom LoginModule implementations
- CallbackHandler support and mapping
- Integration with Eclipse Platform authentication

### Secure Storage
- Encrypted storage for sensitive data (passwords, credentials, keys)
- Multiple password provider support
- Pluggable encryption algorithms
- Platform-specific secure storage backends

### APIs
- `org.eclipse.equinox.security.auth` - Authentication services
- `org.eclipse.equinox.security.auth.credentials` - Credential management
- `org.eclipse.equinox.security.auth.module` - Custom login modules
- `org.eclipse.equinox.security.storage` - Secure storage API
- `org.eclipse.equinox.security.storage.provider` - Storage provider extensions

## Usage

When referring to classes from this bundle by name (such as including them in login configuration files), add a dependency on `org.eclipse.equinox.security` to your bundle's MANIFEST.MF:

```
Require-Bundle: org.eclipse.equinox.security
```

Or use Import-Package for specific packages:

```
Import-Package: org.eclipse.equinox.security.storage,
 org.eclipse.equinox.security.auth
```

## Requirements

- JavaSE-17 or higher
- Eclipse OSGi framework
