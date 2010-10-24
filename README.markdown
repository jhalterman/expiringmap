# ExpiringMap 0.0.1

## Introduction

ExpiringMap is a high performance low-overhead thread-safe map that expires entries. Optional features include expiration policies, variable entry settings, and expiration listeners.

## Motivation

In early 2009, my team came across the need for a simple map capable of expiring entries. After surveying the options available at the time and running some tests against them, we were surprised to find that none of the candidates provided what we were looking for: thread-safety, fast expiration, and low overhead. 

While my project was utilizing Google Collections, its expirable map entry implementation was still primitive and not suitable for heavy use. So I decided to create ExpiringMap.

The expiring map implementations I surveyed invariably utilized polling, numerous threads, or one TimerTask per entry. Thus, ExpiringMap was designed to perform accurate expiration while utilizing single Timer thread and TimerTask.

Since the initial creation of ExpiringMap, Kevin Bourrillion at Google has rewritten Guava's MapMaker expiration implementation to use an approach similar to that of ExpiringMap and enhanced it to include some of the same features. 

## Usage

Creates an expiring map with a default entry duration of 60 seconds from creation:

    Map<String, Integer> map = ExpiringMap.create();
    
Creates an expiring map with an entry duration of 30 seconds from creation:

    Map<String, Connection> map = ExpiringMap.builder()
        .expiration(30, TimeUnit.SECONDS)
        .build();

Creates an expiring map with an entry duration of 5 minutes from each entry's last access:

    Map<String, Connection> map = ExpiringMap.builder()
        .expirationPolicy(ExpirationPolicy.ACCESSED)
        .expiration(5, TimeUnit.MINUTES)
        .build(); 

Creates an expiring map that invokes the given expiration listener for each entry as it expires:

    Map<String, Connection> map = ExpiringMap.builder()
        .expirationListener(new ExpirationListener<String, Connection>() { 
            public void expired(String key, Connection connection) { 
                connection.close(); 
            })
        .build();
        
Creates an expiring map that supports variable expiration, where the expiration duration and policy can vary for each entry.

    Map<String, String> map = ExpiringMap.builder();
        .variableExpiration()
        .build();
    map.put("foo", "bar");
    map.setExpiration("foo", 5, TimeUnit.SECONDS);
    map.setExpirationPolicy("foo", ExpirationPolicy.ACCESSED);
    
## Variable Expiration Considerations

When variable expiration is disabled (default) put/remote operations are constant, whereas when it is enabled put/remove operations impose a cost of log(n).

## Expiration Listener Considerations

Expiration listeners should avoid blocking or synchronizing on shared resources since they are initially invoked from within the context of the ExpiringMap's lone Timer thread. Given this vulnerability, any expiration listener whose invocation duration exceeds a set threshold will thereafter be invoked from a separate thread pool to prevent entry expirations from stacking up in the Timer thread.

Nevertheless, ExpiringMap is still susceptible to ExpirationListener notifications stacking up if they are not processed in a timely manner.

## Future Enhancements

TODO Implement ConcurrentMap interface
TODO Consider strategies for dealing with long running expiration listeners
