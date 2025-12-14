# Investigation Notes: PR #1217 Review Comments

## Task
Address review comments from https://github.com/eclipse-equinox/equinox/pull/1217#pullrequestreview-3575713467

## Repository Memory Context
Review comment: "Tests for bundle URLs should verify both URL parsing AND openConnection + reading content to ensure full functionality."

## Current Status of PR #1217

### Tests in BundleURLConnectionTest.java
- Lines 110-178: Tests that ONLY verify URL parsing (getQuery(), getPath(), getRef())
- Lines 180-225: Tests that verify BOTH parsing AND openConnection + content reading

### Core Problem Discovered
**All tests related to query parameter parsing are FAILING**, including those in the original PR #1217.

When running tests on the PR branch:
```
Tests run: 14, Failures: 8, Errors: 0, Skipped: 0
```

All failures are: `Query parameter should be preserved expected:<param=value> but was:<null>`

## Root Cause Analysis

The query parameter parsing implementation in `BundleResourceHandler.parseURL()` does not work correctly. The issue appears to be related to how Java's URL class calls custom protocol handlers:

1. When `new URL("bundleentry://1.fwk123/path?query=value")` is called
2. Java's URL class parses the protocol and finds the registered handler  
3. The handler's `parseURL(URL url, String str, int start, int end)` method is called
4. The `spec` extracted from `str.substring(start, end)` should contain the query string
5. However, the query is not being properly extracted and set via `setURL()`

### Attempts to Fix
I tried multiple approaches to fix the parsing:
1. Parsing from `url.getQuery()` - returns null
2. Parsing from the `spec` string - doesn't find the '?' character
3. Parsing from the original `str` parameter - still doesn't work
4. Combination of all approaches - still fails

The complexity of the OSGi URL handling system (PlurlStreamHandler, PlurlSetter, etc.) makes it difficult to debug without access to runtime debugging tools.

## Recommendation

The review comment asks for tests to verify both parsing AND openConnection. However:

1. **The underlying feature doesn't work** - query parameters are not being parsed at all
2. Adding openConnection verification to tests that already fail parsing verification doesn't add value
3. The parsing bug needs to be fixed FIRST before the review comment can be properly addressed

### Suggested Actions
1. Fix the query/fragment parsing implementation in `BundleResourceHandler.parseURL()`
2. Ensure all existing tests pass (lines 110-178)  
3. THEN add openConnection verification to those tests (as the review requests)
4. Verify the two tests that already have openConnection (lines 180-225) also pass

## Technical Details

### BundleResourceHandler.parseURL() (lines 100-118)
The code attempts to extract query and fragment from the spec:
```java
String query = url.getQuery();  // Returns null for new URLs
String ref = url.getRef();       // Returns null for new URLs
int queryIdx = spec.indexOf('?', pathIdx);  // Should find '?' but doesn't?
int fragmentIdx = spec.indexOf('#', pathIdx);
```

The extraction logic seems correct in theory, but in practice the query remains null after `setURL()` is called.

### Potential Issues
- The `spec` parameter might not include the query/fragment portion
- The `PlurlSetter` mechanism might not be properly setting values
- Java's URL class might pre-parse custom protocol URLs differently than expected
- There may be missing configuration or registration of the handler

## Files Modified in This Investigation
- `bundles/org.eclipse.osgi/container/src/org/eclipse/osgi/storage/url/BundleResourceHandler.java` (attempted fixes, all reverted)
- `bundles/org.eclipse.osgi.tests/src/org/eclipse/osgi/tests/url/BundleURLConnectionTest.java` (added openConnection tests, reverted)

## Conclusion

To properly address the review comments, the query parsing bug must be fixed first. Without a working implementation, adding openConnection tests only creates more failing tests without providing additional value.

The PR #1217 needs significant debugging and potentially redesign of the query parameter parsing logic before it can be merged.
