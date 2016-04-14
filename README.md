# ExpiringMap
[![Build Status](https://travis-ci.org/jhalterman/expiringmap.svg)](https://travis-ci.org/jhalterman/expiringmap)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.jodah/expiringmap/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.jodah/expiringmap/)
[![License](http://img.shields.io/:license-apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

A high performance, low-overhead, zero dependency, thread-safe [ConcurrentMap](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentMap.html) implementation that expires entries. Features include:

* [Expiration policies](#expiration-policies)
* [Variable entry expiration](#variable-expiration)
* [Expiration listeners](#expiration-listeners)
* [Lazy entry loading](#lazy-entry-loading)

Supports Java 6+ though the documentation uses lambdas for simplicity.

## Usage

ExpiringMap allows you to create a map that expires entries after a certain time period:

```java
Map<String, Connection> map = ExpiringMap.builder()
  .expiration(30, TimeUnit.SECONDS)
  .build();
  
// Expires after 30 seconds
map.put("foo", 5);
```

#### Expiration Policies

Expiration can occur based on an entry's creation time or last access time:

```java
Map<String, Connection> map = ExpiringMap.builder()
  .expirationPolicy(ExpirationPolicy.ACCESSED)
  .build(); 
```

We can also specify an expiration policy for individual entries:

```java
map.put("foo", "bar", ExpirationPolicy.CREATED);
```

And we can change policies on the fly:

```java
map.setExpirationPolicy("foo", ExpirationPolicy.ACCESSED);
```

#### Variable Expiration
        
Entries can have individually variable expiration times and policies:

```java
ExpiringMap<String, String> map = ExpiringMap.builder()
  .variableExpiration()
  .build();

map.put("foo", "bar", ExpirationPolicy.ACCESSED, 5, TimeUnit.SECONDS);
```

Expiration times can also be changed on the fly:

```java
map.setExpiration("foo", 5, TimeUnit.SECONDS);
```

#### Expiration Listeners

Expiration listeners can be notified when an entry expires:

```java
Map<String, Connection> map = ExpiringMap.builder()
  .expirationListener((key, connection) -> connection.close())
  .build();
```

Expiration listeners are called synchronously as entries are expires and block write operations to the map until they completed. Asynchronous expiration listeners can also be configured. These are called on a separate thread pool and do not block map operations:

```java
Map<String, Connection> map = ExpiringMap.builder()
  .asyncExpirationListener((key, connection) -> connection.close())
  .build();
```

#### Lazy Entry Loading

Entries can be lazily loaded via an `EntryLoader` when `ExpiringMap.get` is called:

```java
Map<String, Connection> connections = ExpiringMap.builder()
  .expiration(10, TimeUnit.MINUTES)
  .entryLoader(address -> new Connection(address))
  .build();
  
// Loads a new connection into the map via the EntryLoader
connections.get("http://jodah.net");
```

Lazily loaded entries can also be made to expire at varying times:

```java
Map<String, Connection> connections = ExpiringMap.builder()
  .expiringEntry(address -> new ExpiringValue(new Connection(address), 5, TimeUnit.MINUTES))
  .build();
```

#### Expiration Introspection

ExpiringMap allows you to learn when an entry is expected to expire:

```java
long expiration = map.getExpectedExpiration("foo");
```

We can also reset the internal expiration timer for an entry:

```java
map.resetExpiration("foo");
```

## Additional Notes

#### On Variable Expiration

When variable expiration is disabled (default), `put` and `remove` operations have a constant O(1) cost. When variable expiration is enabled `put` and `remove` operations have a cost of O(log n).

#### On Google App Engine Integration

Google App Engine users must specify a `ThreadFactory` prior to constructing an `ExpiringMap` instance in order to avoid runtime permission errors:

```java

ExpiringMap.setThreadFactory(com.google.appengine.api.ThreadManager.currentRequestThreadFactory());
ExpiringMap.builder().build();
```

See the [GAE docs on threads](https://cloud.google.com/appengine/docs/java/runtime#threads) for more info.

## Docs

JavaDocs are available [here](https://jhalterman.github.com/expiringmap/javadoc).

## License

Copyright 2009-2016 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).
