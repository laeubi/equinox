# Quick Reference: Regression Fix

## Problem
- **Issue**: NullPointerException during Eclipse restart after update
- **Error Location**: `BundleContextImpl.removeServiceListener()` line 315
- **Root Cause**: Race condition between framework shutdown and ServiceTracker cleanup
- **Trigger**: PR #1170 changed logging timing, exposing pre-existing race condition

## Solution Applied
**File**: `bundles/org.eclipse.osgi/container/src/org/eclipse/osgi/internal/framework/BundleContextImpl.java`

**Change**: Added defensive null check in `removeServiceListener()` method

```java
@Override
public void removeServiceListener(ServiceListener listener) {
    if (listener == null) {
        throw new IllegalArgumentException();
    }
    ServiceRegistry registry = container.getServiceRegistry();
    if (registry == null) {
        // Framework is shutting down, safe to ignore service listener removal
        return;
    }
    registry.removeServiceListener(this, listener);
}
```

## Why This Fix Works
1. During framework shutdown, `ServiceRegistry` is cleared (set to null)
2. ServiceTrackers may still be closing and trying to remove listeners
3. The null check prevents NPE by returning early when registry is null
4. This is safe per OSGi spec: removing an unregistered listener is a no-op
5. During shutdown, listeners are being cleaned up anyway

## Impact
- **Risk**: Very low - minimal change, defensive programming
- **Performance**: No impact - only affects shutdown path
- **Compatibility**: Full - maintains existing behavior
- **Testing**: Should test restart-after-update scenarios

## References
- Full Analysis: `REGRESSION_ANALYSIS_PR1170.md`
- Related PR: https://github.com/eclipse-equinox/equinox/pull/1170
- Issue: https://github.com/eclipse-platform/.github/issues/257

## Verification
Run: `/tmp/validate_fix.sh` to verify the fix is in place
