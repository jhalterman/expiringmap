# ExpiringMap [![Build Status](https://travis-ci.org/jhalterman/expiringmap.png)](https://travis-ci.org/jhalterman/expiringmap)

A high performance, low-overhead, zero dependency, thread-safe [ConcurrentMap](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ConcurrentMap.html) implementation that expires entries. Features include expiration policies, variable entry settings, and expiration listeners.

## Setup

Add ExpiringMap as a Maven dependency:

```xml
<dependency>
  <groupId>net.jodah</groupId>
  <artifactId>expiringmap</artifactId>
  <version>0.3.2</version>
</dependency>
```

## Usage

Create an expiring map with a default entry duration of 60 seconds from creation:

```java
Map<String, Integer> map = ExpiringMap.create();
```
    
Create an expiring map with an entry duration of 30 seconds from creation:

```java
Map<String, Connection> map = ExpiringMap.builder()
  .expiration(30, TimeUnit.SECONDS)
  .build();
```

Create an expiring map with an entry duration of 5 minutes from each entry's last access:

```java
Map<String, Connection> map = ExpiringMap.builder()
  .expirationPolicy(ExpirationPolicy.ACCESSED)
  .expiration(5, TimeUnit.MINUTES)
  .build(); 
```

Create an expiring map that invokes the given expiration listener for each entry as it expires:

```java
Map<String, Connection> map = ExpiringMap.builder()
  .expirationListener(new ExpirationListener<String, Connection>() { 
    public void expired(String key, Connection connection) { 
      connection.close(); 
    })
  .build();
```
        
Create an expiring map that supports variable expiration, where the expiration duration and policy can vary for each entry:

```java
Map<String, String> map = ExpiringMap.builder()
  .variableExpiration()
  .build();

map.put("foo", "bar", ExpirationPolicy.ACCESSED, 5, TimeUnit.SECONDS);
```

Expirations can also be set and reset on the fly:

```java
map.setExpiration("foo", 5, TimeUnit.SECONDS);
map.setExpirationPolicy("foo", ExpirationPolicy.ACCESSED);
map.resetExpiration("foo");
```

## Additional Notes

#### On Variable Expiration

When variable expiration is disabled (default) put/remote operations are constant O(n), whereas when it is enabled put/remove operations impose a cost of O(log n).

#### On Expiration Listeners

Expiration listeners should avoid blocking or synchronizing on shared resources since they are initially invoked from within the context of the ExpiringMap's lone Timer thread. Given this vulnerability, any expiration listener whose invocation duration exceeds a set threshold will thereafter be invoked from a separate thread pool to prevent entry expirations from stacking up in the Timer thread.

Nevertheless, ExpiringMap is still susceptible to ExpirationListener notifications stacking up if they are not processed in a timely manner.

## Docs

JavaDocs are available [here](https://jhalterman.github.com/expiringmap/javadoc).

## Future Enhancements

* Consider strategies for dealing with long running expiration listeners

## License

Copyright 2009-2014 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).