
# 0.4.0

### New Features

* Added support for entry loaders. Addresses issue #4.

### API Changes

* Added `ExpiringMap.Builder.entryLoader`.
* Added `ExpiringMap.Builder.expirationListeners`.

# 0.3.2

### New Features

* Added implementation of `ConcurrentMap`.
* Replaced internal synchronization with separate read/write locks.
  
# 0.3.1

### Bug Fixes

* Fixed NPE when putting a value over a previously put null value.

# 0.3.0
Initial release