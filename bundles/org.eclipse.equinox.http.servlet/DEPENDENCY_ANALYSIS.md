# Runtime Dependency Analysis: javax.servlet.descriptor

## Issue
The `javax.servlet.descriptor` package appears unused in the codebase (no direct imports or explicit usage), yet removing it from the `Import-Package` directive in `META-INF/MANIFEST.MF` causes runtime failures.

## Root Cause
The dependency on `javax.servlet.descriptor` is **indirect and required at runtime** due to Java's dynamic proxy mechanism.

### Technical Explanation

1. **ServletContextAdaptor creates a dynamic proxy** (see `ServletContextAdaptor.createServletContext()` method):
   ```java
   public ServletContext createServletContext() {
       Class<?> clazz = getClass();
       ClassLoader curClassLoader = clazz.getClassLoader();
       Class<?>[] interfaces = new Class[] { ServletContext.class };
       return (ServletContext) Proxy.newProxyInstance(curClassLoader, interfaces, 
                                                       new AdaptorInvocationHandler());
   }
   ```

2. **ServletContext interface contains methods with return types from javax.servlet.descriptor**:
   - `JspConfigDescriptor getJspConfigDescriptor()` - Returns `javax.servlet.descriptor.JspConfigDescriptor`
   - Other methods may also reference descriptor types through their signatures

3. **JVM Requirement for Dynamic Proxies**:
   When creating a dynamic proxy using `Proxy.newProxyInstance()`, the JVM must:
   - Load the interface class (`ServletContext`)
   - Verify all method signatures in the interface
   - Load all types referenced in those method signatures (parameters and return types)
   
   This happens **even if those methods are never actually invoked**.

4. **OSGi ClassLoading**:
   In an OSGi environment, classes can only be loaded if their packages are:
   - Exported by a bundle and imported by the consuming bundle, OR
   - Part of the consuming bundle itself

   Since `javax.servlet.descriptor` classes are:
   - Defined in the servlet API bundle
   - Referenced by `ServletContext` interface methods
   - Required for proxy creation at runtime
   
   The package **must** be declared in the `Import-Package` directive.

## Why Compilation Succeeds But Runtime Fails

- **Compile time**: The Java compiler only checks that the code written is valid. Since the code never explicitly uses `JspConfigDescriptor` or other descriptor classes, no compilation errors occur.

- **Runtime**: When `Proxy.newProxyInstance()` is called, the JVM needs to create a proxy class that implements `ServletContext`. This requires loading the complete interface definition, including all referenced types. Without access to `javax.servlet.descriptor`, a `NoClassDefFoundError` or `ClassNotFoundException` occurs.

## Conclusion

The `javax.servlet.descriptor` package import is **mandatory** for this bundle to function correctly at runtime, despite having no direct source code references. This is a legitimate and necessary dependency caused by:
1. The use of dynamic proxies for `ServletContext`
2. The `ServletContext` interface containing methods with descriptor return types
3. JVM requirements for dynamic proxy creation

## Related Files
- `bundles/org.eclipse.equinox.http.servlet/src/org/eclipse/equinox/http/servlet/internal/servlet/ServletContextAdaptor.java` - Creates the dynamic proxy
- `bundles/org.eclipse.equinox.http.servlet/META-INF/MANIFEST.MF` - Contains the Import-Package directive
- `bundles/org.eclipse.equinox.http.servlet/demo_proxy_requirement.sh` - Demonstration script showing the proxy requirement

## Demonstration

You can run the demonstration script to see a working example of this issue:

```bash
cd bundles/org.eclipse.equinox.http.servlet
bash demo_proxy_requirement.sh
```

This script creates a simple Java program that demonstrates how dynamic proxy creation requires all types referenced in interface method signatures to be available, even if those methods are never invoked.

## References
- [Java Dynamic Proxy Documentation](https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/Proxy.html)
- [Servlet 3.1 API Specification](https://javaee.github.io/servlet-spec/)
- GitHub PR: https://github.com/eclipse-equinox/equinox/pull/1083
- GitHub Comment: https://github.com/eclipse-equinox/equinox/pull/1083/files#r2217621917
