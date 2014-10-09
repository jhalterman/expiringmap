# ExpiringMap [![Build Status](https://travis-ci.org/jhalterman/expiringmap.png)](https://travis-ci.org/jhalterman/expiringmap)

A high performance, low-overhead, thread-safe map that expires entries. Features include expiration policies, variable entry settings, and expiration listeners.

## Motivation

In 2009 my team came across the need for a simple map capable of expiring entries. After surveying the options available at the time and running some tests against them, we were surprised to find that none of the candidates provided what we were looking for: thread-safety, fast expiration, and low overhead. So I created ExpiringMap.

While the implementations I tested invariably utilized polling, numerous threads, or one TimerTask per entry, ExpiringMap was designed to utilize a single Timer thread and TimerTask. This approach keeps memory consumption at a minimum, and expirations fast.

Though ExpiringMap was created when no comparable alternatives existed, [Guava](http://code.google.com/p/guava-libraries/)'s MapMaker was later re-written to use an entry expiration approach similar to that of ExpiringMap, additionally enhancing it to include some of the same features. Going forward, either implementation is suitable for high performance use.

## Setup

Add ExpiringMap as a Maven dependency:

```xml
<dependency>
  <groupId>net.jodah</groupId>
  <artifactId>expiringmap</artifactId>
  <version>0.3.1</version>
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
        
Create an expiring map that supports variable expiration, where the expiration duration and policy can vary for each entry.

```java
Map<String, String> map = ExpiringMap.builder();
  .variableExpiration()
  .build();

map.put("foo", "bar");
map.setExpiration("foo", 5, TimeUnit.SECONDS);
map.setExpirationPolicy("foo", ExpirationPolicy.ACCESSED);
```

### Variable Expiration Considerations

When variable expiration is disabled (default) put/remote operations are constant, whereas when it is enabled put/remove operations impose a cost of log(n).

### Expiration Listener Considerations

Expiration listeners should avoid blocking or synchronizing on shared resources since they are initially invoked from within the context of the ExpiringMap's lone Timer thread. Given this vulnerability, any expiration listener whose invocation duration exceeds a set threshold will thereafter be invoked from a separate thread pool to prevent entry expirations from stacking up in the Timer thread.

Nevertheless, ExpiringMap is still susceptible to ExpirationListener notifications stacking up if they are not processed in a timely manner.

## Implementation Notes

The current implementation uses synchronization instead of read/write locks since I initially wrote this for use on single core servers (yes, unfortunately). Read/write locks can easily be installed though - feel free to fork and have at it if you need to.

## Future Enhancements

* Implement ConcurrentMap interface
* Consider strategies for dealing with long running expiration listeners

## License

Copyright 2009-2014 Jonathan Halterman - Released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).