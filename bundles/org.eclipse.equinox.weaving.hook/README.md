# Equinox Weaving Hook

This bundle provides aspect-oriented programming (AOP) support for OSGi bundles through bytecode weaving hooks.

## Overview

The Equinox Weaving Hook is an OSGi framework extension that enables load-time weaving of aspect-oriented code into OSGi bundles. It allows aspects (cross-cutting concerns) to be woven into bundle bytecode at load time without modifying the original bundle JARs.

## Features

- **Load-time Weaving** - Weave aspects into bundle classes as they are loaded
- **OSGi Integration** - Seamless integration with the Eclipse OSGi framework
- **Hook Configurators** - Framework hooks for bytecode transformation
- **Weaving Service API** - `org.eclipse.equinox.service.weaving` for programmatic weaving control
- **Bundle Adaptor Support** - Extensible adaptors for different weaving implementations

## Architecture

This bundle is a **fragment** attached to the `org.eclipse.osgi` host bundle, which allows it to extend the OSGi framework with weaving capabilities.

### Key Components
- **WeavingHook** - Framework hook that intercepts bundle class loading
- **BundleAdaptorProvider** - Provides adaptors for different weaving strategies  
- **SupplementerRegistry** - Manages supplemental bundles that provide aspects

## Usage

### Installation
1. Install this fragment bundle alongside org.eclipse.osgi
2. The fragment will automatically attach to the OSGi framework
3. Configure weaving through the `hookconfigurators.properties` file

### Configuration
Weaving behavior is configured via the included `hookconfigurators.properties` file and optional `config.ini` settings.

## Compatibility

- **Fragment Host**: org.eclipse.osgi (version 3.10.0 to 4.0.0)
- **Java Version**: JavaSE-17 or higher
- **Framework**: Eclipse OSGi / Equinox

## Related Projects

This bundle is commonly used with:
- AspectJ weaving implementations
- Equinox Aspects framework
- OSGi weaving implementations

## Technical Details

As an OSGi framework extension, this bundle operates at a low level and requires careful configuration. Improper use may affect framework stability.
