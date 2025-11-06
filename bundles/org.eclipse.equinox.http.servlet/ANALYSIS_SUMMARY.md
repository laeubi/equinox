# Analysis Summary: javax.servlet.descriptor Runtime Dependency

## Problem Statement
PR #1083 attempted to remove `javax.servlet.descriptor` from the `Import-Package` directive in `META-INF/MANIFEST.MF` as part of automated cleanup, because static code analysis showed it was not directly used in the source code. However, runtime tests failed with class loading errors.

## Investigation Results

### Root Cause Identified
The `javax.servlet.descriptor` package is **indirectly required at runtime** due to the use of Java dynamic proxies.

**Location:** `ServletContextAdaptor.createServletContext()` method
```java
return (ServletContext) Proxy.newProxyInstance(curClassLoader, interfaces, 
                                                new AdaptorInvocationHandler());
```

### Why It's Required

1. **Dynamic Proxy Creation**: `ServletContextAdaptor` creates a dynamic proxy that implements the `ServletContext` interface

2. **Interface Method Signatures**: The `ServletContext` interface contains method(s) with return types from `javax.servlet.descriptor`:
   - `JspConfigDescriptor getJspConfigDescriptor()`

3. **JVM Requirement**: When creating a dynamic proxy via `Proxy.newProxyInstance()`, the JVM must:
   - Load the interface class (`ServletContext`)
   - Verify all method signatures
   - **Load all types referenced in those signatures** (parameters and return types)
   
   This happens even if those methods are never actually invoked.

4. **OSGi ClassLoading**: In OSGi, classes can only be loaded if their packages are explicitly imported. Therefore, `javax.servlet.descriptor` must be in the `Import-Package` directive.

### Why Compilation Succeeds But Runtime Fails

- **Compile time**: The Java compiler only validates the code written. Since no code explicitly imports or uses `javax.servlet.descriptor` classes, compilation succeeds.

- **Runtime**: When `Proxy.newProxyInstance()` is invoked, the JVM needs to create the proxy class. This requires loading the complete `ServletContext` interface definition, including all referenced types. Without `javax.servlet.descriptor` available, a `NoClassDefFoundError` or `ClassNotFoundException` occurs.

## Conclusion

**The `javax.servlet.descriptor` import is mandatory and cannot be removed.** This is not a bug or unnecessary dependency—it's a fundamental requirement of how Java dynamic proxies work in an OSGi environment.

## Recommendation for PR #1083

The line removing `javax.servlet.descriptor` should be **reverted**:

```diff
 Import-Package: javax.servlet;version="[3.1.0,5.0.0)",
+ javax.servlet.descriptor;version="[3.1.0,5.0.0)",
  javax.servlet.http;version="[3.1.0,5.0.0)",
```

The manifest cleanup tool should be configured to recognize this as a legitimate indirect dependency and not flag it for removal in future automated cleanups.

## Documentation Added

To prevent this confusion in the future, the following documentation has been added to the repository:

1. **DEPENDENCY_ANALYSIS.md** - Comprehensive technical explanation
2. **JavaDoc in ServletContextAdaptor.createServletContext()** - In-line explanation
3. **demo_proxy_requirement.sh** - Working demonstration script

## References

- GitHub PR: https://github.com/eclipse-equinox/equinox/pull/1083
- GitHub Comment: https://github.com/eclipse-equinox/equinox/pull/1083/files#r2217621917
- Java Dynamic Proxy: https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Proxy.html
