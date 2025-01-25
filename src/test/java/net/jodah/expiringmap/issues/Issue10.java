package net.jodah.expiringmap.issues;

import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Do not throw ConcurrentModificationException when using an Iterator
 */
@Test
public class Issue10 {
    public void testEntrySet() {
        Map<Integer, Integer> map = ExpiringMap.builder().expiration(30, TimeUnit.SECONDS).expirationPolicy(ExpirationPolicy.ACCESSED).build();
        map.put(1, 1);
        map.put(2, 2);
        Iterator<Map.Entry<Integer, Integer>> iterator = map.entrySet().iterator();
        Assert.assertEquals((Integer) 1, iterator.next().getKey());
        map.put(3, 3);
        Assert.assertEquals((Integer) 2, iterator.next().getKey());
        // we don't require the iterator to be updated with the new '3' entry and we neither expect a ConcurrentModificationException
        if (iterator.hasNext()) {
            Assert.assertEquals((Integer) 3, iterator.next().getKey());
        }
    }

    public void testValues() {
        Map<Integer, Integer> map = ExpiringMap.builder().expiration(30, TimeUnit.SECONDS).expirationPolicy(ExpirationPolicy.ACCESSED).build();
        map.put(1, 1);
        map.put(2, 2);
        Iterator<Integer> iterator = map.values().iterator();
        Assert.assertEquals((Integer) 1, iterator.next());
        map.put(3, 3);
        Assert.assertEquals((Integer) 2, iterator.next());
        // we don't require the iterator to be updated with the new '3' entry and we neither expect a ConcurrentModificationException
        if (iterator.hasNext()) {
            Assert.assertEquals((Integer) 3, iterator.next());
        }
    }

    public void testKeySet() {
        Map<Integer, Integer> map = ExpiringMap.builder().expiration(30, TimeUnit.SECONDS).expirationPolicy(ExpirationPolicy.ACCESSED).build();
        map.put(1, 1);
        map.put(2, 2);
        Iterator<Integer> iterator = map.keySet().iterator();
        Assert.assertEquals((Integer) 1, iterator.next());
        map.put(3, 3);
        Assert.assertEquals((Integer) 2, iterator.next());
        // we don't require the iterator to be updated with the new '3' entry and we neither expect a ConcurrentModificationException
        if (iterator.hasNext()) {
            Assert.assertEquals((Integer) 3, iterator.next());
        }
    }

    public void testParallelPutRemove() {
        ExpiringMap<Integer, String> testee = ExpiringMap.create();
        ExecutorService service = Executors.newFixedThreadPool(2);

        Runnable adder = () -> {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            AtomicInteger counter = new AtomicInteger(0);
            for (int j = 0; j < 5; j++) {
                for (int i = 0; i < 100_000; i++) {
                    if (rnd.nextBoolean()) {
                        testee.put(counter.incrementAndGet(), "bar");
                    }
                }
            }
        };

        Runnable remover = () -> {
            while (!service.isTerminated()) {
                List<Integer> entriesToDelete = new ArrayList<>();
                for (Map.Entry<Integer, String> e : testee.entrySet()) {
                    entriesToDelete.add(e.getKey());
                }
                entriesToDelete.forEach(testee.keySet()::remove);
            }
        };
        service.submit(adder);
        service.shutdown(); // schedule shutdown, let the running tasks finish
        remover.run(); // run synchronous, waits for the adder threads to finish
    }
}
