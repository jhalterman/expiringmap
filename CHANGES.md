# 0.5.8

### New Features

* Added `maxSize`

# 0.5.7

### Bug Fixes

* Fixed #36 - Entry/value iterators may show duplicates when variable expiration enabled.

# 0.5.6

### Bug Fixes

* Fixed #31 - `setExpiration(K, long, TimeUnit)` throws NPE for non-existent keys
* Fixed #33 - Entry loader may be called twice for same key under heavy load

# 0.5.5

### Bug Fixes

* Fixed #29 - `remove(Object)` releases write lock prematurely

# 0.5.4

### New Features

* Added `getExpirationPolicy(key)`
* Added separate asynchronous expiration listener APIs

# 0.5.3

### Improvements

* Added Google App Engine support via `ExpiringMap.setThreadFactory(ThreadFactory)`.

### Bug Fixes

* Fixed #28 - Problems with `map.containsValue()` and `values().containsAll()`.

# 0.5.2

### Improvements

* Added OSGI bundle.

# 0.5.1

### Bug Fixes

* Fixed #20 - Daemonize pooled threads

# 0.5.0

### New Features

* Added support for variable expiration lazily loaded entries.

### Bug Fixes

* Fixed #15 - Catch synchronous expirationlistener invocation exceptions.

### API Changes

* Added `ExpiringMap.Builder.expiringEntryLoader`.
* `ExpirationPolicy`, `ExpirationListener`, and `EntryLoader` are no longer inner types on `ExpiringMap` and are now top level types on the `net.jodah.expiringmap` package.

# 0.4.3

### Bug Fixes

* Fixed #12 - Idempotent puts should reset expiration when `ExpirationPolicy` is `ACCESSSED`.

# 0.4.2

### New Features

* Added `getExpectedExpiration` to learn the remaining time left before an entry is expected to expire. Merged from pull request #9.

# 0.4.1

### New Features

* Added proper support for `entrySet()`, `keyset()` and `values()`.

### API Changes

* Removed `valuesIterator()`. Instead use `values().iterator()`.

# 0.4.0

### New Features

* Added support for entry loaders. Addresses issue #4.
* Replaced internal `Timer` with `ScheduledThreadPoolExecutor`. Addresses issue #5.
* Added `toString()` implementation for map and entries.

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