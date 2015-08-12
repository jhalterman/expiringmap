# ExpiringMap
[![Build Status](https://travis-ci.org/jhalterman/expiringmap.svg)](https://travis-ci.org/jhalterman/expiringmap)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.jodah/expiringmap/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.jodah/expiringmap/)
[![License](http://img.shields.io/:license-apache-brightgreen.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

A high performance, low-overhead, zero dependency, thread-safe [ConcurrentMap](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentMap.html) implementation that expires entries. Features include:

* Expiration policies
* Variable entry expiration
* Expiration listeners
* Lazy entry loading
* Support for Java 6+

## Usage

By default, ExpiringMap expires entries 60 seconds from creation:

```java
Map<String, Integer> map = ExpiringMap.create();

// Expires after 60 seconds
map.put("foo", 5);
```
    
The expiration time can be varied as needed:

```java
Map<String, Connection> map = ExpiringMap.builder()
  .expiration(30, TimeUnit.SECONDS)
  .build();
```

Expiration can also occur based on an entry's last access time:

```java
Map<String, Connection> map = ExpiringMap.builder()
  .expirationPolicy(ExpirationPolicy.ACCESSED)
  .expiration(5, TimeUnit.MINUTES)
  .build(); 
```

#### Variable Expiration
        
Entries can have individually variable expiration durations and policies:

```java
ExpiringMap<String, String> map = ExpiringMap.builder()
  .variableExpiration()
  .build();

map.put("foo", "bar", ExpirationPolicy.ACCESSED, 5, TimeUnit.SECONDS);
```

Expiration durations and policies can also be set and reset on the fly:

```java
map.setExpiration("foo", 5, TimeUnit.SECONDS);
map.setExpirationPolicy("foo", ExpirationPolicy.ACCESSED);
map.resetExpiration("foo");
```

#### Lazy Entry Loading

Entries can be lazily loaded via an `EntryLoader` when `ExpiringMap.get` is called:

```java
Map<String, Connection> connections = ExpiringMap.builder()
  .expiration(10, TimeUnit.MINUTES)
  .entryLoader(new EntryLoader<String, Connection>() {
    public Connection load(String address) {
      return new Connection(address);
    }
  })
  .build();
  
// Loads a new connection into the map via the EntryLoader
connections.get("http://jodah.net");
```

Lazily loaded entries can also be made to expire at varying times:

```java
Map<String, Connection> connections = ExpiringMap.builder()
  .expiringEntry(new ExpiringEntryLoader<String, Connection>() {
    public ExpiringValue<Connection> load(String address) {
      return new ExpiringValue(new Connection(address), 5, TimeUnit.MINUTES);
    }
  })
  .build();
```

#### Expiration Listeners

Finally, expiration listeners can be notified when an entry expires:

```java
Map<String, Connection> map = ExpiringMap.builder()
  .expirationListener(new ExpirationListener<String, Connection>() { 
    public void expired(String key, Connection connection) { 
      connection.close(); 
    })
  .build();
```

## Additional Notes

#### On Variable Expiration

When variable expiration is disabled (default), `put` and `remove` operations have a constant O(n) cost. When variable expiration is enabled `put` and `remove` operations have a cost of O(log n).

#### On Expiration Listeners

Expiration listeners should perform work quickly and avoid blocking since they are invoked by default by the ExpiringMap's Timer thread which is also used to expire entries. If an Expration listener blocks or fails to return quickly, ExpiringMap may not be able to expire entries on time. To handle this, any expiration listener whose invocation duration exceeds a set threshold will thereafter be invoked from a separate thread pool to prevent entry expirations from stacking up in the Timer thread.

## Docs

JavaDocs are available [here](https://jhalterman.github.com/expiringmap/javadoc).

## License

Copyright 2009-2015 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).