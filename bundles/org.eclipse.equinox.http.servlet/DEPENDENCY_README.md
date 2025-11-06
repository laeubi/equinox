# Understanding the javax.servlet.descriptor Dependency

This directory contains comprehensive documentation explaining why `javax.servlet.descriptor` must be included in the OSGi bundle's `Import-Package` directive, even though static code analysis shows it's not directly used.

## Quick Start

**Start here:** Read [ANALYSIS_SUMMARY.md](ANALYSIS_SUMMARY.md) for the executive summary and recommendation.

## Complete Documentation

1. **[ANALYSIS_SUMMARY.md](ANALYSIS_SUMMARY.md)** - Executive summary with problem statement, findings, and recommendations
   
2. **[DEPENDENCY_ANALYSIS.md](DEPENDENCY_ANALYSIS.md)** - Detailed technical explanation of:
   - How dynamic proxies work
   - Why ServletContext requires javax.servlet.descriptor
   - OSGi classloading specifics
   - Why compilation succeeds but runtime fails

3. **[demo_proxy_requirement.sh](demo_proxy_requirement.sh)** - Executable demonstration
   ```bash
   bash demo_proxy_requirement.sh
   ```
   This shows a working example of how dynamic proxy creation requires all interface method signature types to be available.

4. **Source Code Documentation** - See JavaDoc in:
   - `src/org/eclipse/equinox/http/servlet/internal/servlet/ServletContextAdaptor.java` (method `createServletContext()`)

## Key Takeaway

The `javax.servlet.descriptor` package in `META-INF/MANIFEST.MF` line 21 is **NOT removable**. It's required for dynamic proxy creation at runtime, which is a fundamental JVM requirement, not a bug or code smell.

```
Import-Package: javax.servlet;version="[3.1.0,5.0.0)",
 javax.servlet.descriptor;version="[3.1.0,5.0.0)",  ← Required for dynamic proxy
 javax.servlet.http;version="[3.1.0,5.0.0)",
```

## Background

This documentation was created in response to:
- **GitHub PR #1083** - Automated code cleanup that removed "unused" imports
- **Review Comment** (ID: r2217621917) - Noted that javax.servlet.descriptor appears unused but causes runtime failures when removed
  - URL: https://github.com/eclipse-equinox/equinox/pull/1083/files#r2217621917
- **Runtime Failure** - Tests failed when javax.servlet.descriptor was removed

## For Maintainers

If automated cleanup tools flag `javax.servlet.descriptor` for removal in the future:

1. Do NOT remove it
2. Configure your tools to recognize this as a legitimate indirect dependency
3. Reference this documentation to explain why it must remain

## Technical Details

**Problem:** ServletContextAdaptor creates dynamic proxy for ServletContext interface
**Challenge:** ServletContext.getJspConfigDescriptor() returns JspConfigDescriptor from javax.servlet.descriptor  
**Requirement:** All types in interface method signatures must be loadable during proxy creation  
**Solution:** Import javax.servlet.descriptor in MANIFEST.MF

---

*For questions or clarifications about this dependency, refer to the detailed documentation in this directory.*
