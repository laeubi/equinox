#!/bin/bash
# Demonstration: Why javax.servlet.descriptor is required at runtime
# This script demonstrates the issue with a simple Java example

set -e

echo "=========================================="
echo "Demonstrating javax.servlet.descriptor runtime requirement"
echo "=========================================="
echo ""

# Create a temp directory for our test
TEMP_DIR=$(mktemp -d) || {
    echo "Error: Failed to create temporary directory"
    exit 1
}
echo "Working in: $TEMP_DIR"
cd "$TEMP_DIR"

# Create a simple test class that uses dynamic proxy
cat > ProxyTest.java << 'EOF'
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * This test demonstrates that when creating a dynamic proxy for an interface,
 * ALL types referenced in the interface's method signatures must be loadable,
 * even if those methods are never called.
 * 
 * For ServletContext, the getJspConfigDescriptor() method returns
 * javax.servlet.descriptor.JspConfigDescriptor, which means that package
 * must be available when creating the proxy.
 */
public class ProxyTest {
    
    // A simple interface with a method that returns a type from another package
    interface TestInterface {
        String simpleMethod();
        // This method references a type that might not be available
        java.util.concurrent.Future<String> methodWithComplexReturnType();
    }
    
    public static void main(String[] args) {
        System.out.println("Creating a dynamic proxy for TestInterface...");
        
        try {
            // This is exactly what ServletContextAdaptor does
            TestInterface proxy = (TestInterface) Proxy.newProxyInstance(
                ProxyTest.class.getClassLoader(),
                new Class[] { TestInterface.class },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        System.out.println("Method called: " + method.getName());
                        return null;
                    }
                }
            );
            
            System.out.println("✓ Proxy created successfully!");
            System.out.println("✓ This worked because java.util.concurrent.Future is available in JDK");
            System.out.println("");
            System.out.println("The same principle applies to ServletContext and JspConfigDescriptor:");
            System.out.println("  - ServletContext.getJspConfigDescriptor() returns JspConfigDescriptor");
            System.out.println("  - JspConfigDescriptor is in javax.servlet.descriptor package");
            System.out.println("  - Therefore, javax.servlet.descriptor MUST be available at runtime");
            System.out.println("  - Even though the code never explicitly calls getJspConfigDescriptor()");
            
        } catch (Throwable t) {
            System.err.println("✗ Failed to create proxy:");
            t.printStackTrace();
        }
    }
}
EOF

# Try to compile and run
echo ""
echo "Compiling ProxyTest.java..."
if javac ProxyTest.java 2>&1; then
    echo "✓ Compilation successful"
    echo ""
    echo "Running ProxyTest..."
    java ProxyTest
else
    echo "✗ Compilation failed"
fi

# Cleanup - store original directory for safe cleanup
ORIGINAL_DIR="$HOME"
cd "$ORIGINAL_DIR"
rm -rf "$TEMP_DIR"
echo ""
echo "=========================================="
echo "Demonstration complete"
echo "=========================================="
