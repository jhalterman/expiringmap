# ExpiringMap [![Build Status](https://travis-ci.org/jhalterman/expiringmap.png)](https://travis-ci.org/jhalterman/expiringmap)

A high performance, low-overhead, zero dependency, thread-safe [ConcurrentMap](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentMap.html) implementation that expires entries. Features include expiration policies, variable entry settings, expiration listeners, and lazy entry loading.

## Setup

Add ExpiringMap as a Maven dependency:

```xml
<dependency>
  <groupId>net.jodah</groupId>
  <artifactId>expiringmap</artifactId>
  <version>0.4.0</version>
</dependency>
```

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
Map<String, String> map = ExpiringMap.builder()
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

When variable expiration is disabled (default) put/remote operations are constant O(n), whereas when it is enabled put/remove operations impose a cost of O(log n).

#### On Expiration Listeners

Expiration listeners should avoid blocking or synchronizing on shared resources since they are initially invoked from within the context of the ExpiringMap's lone Timer thread. Given this, any expiration listener whose invocation duration exceeds a set threshold will thereafter be invoked from a separate thread pool to prevent entry expirations from stacking up in the Timer thread.

Nevertheless, ExpiringMap is still susceptible to ExpirationListener notifications stacking up internally if they are not processed in a timely manner.

## Docs

JavaDocs are available [here](https://jhalterman.github.com/expiringmap/javadoc).

## License

Copyright 2009-2014 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).