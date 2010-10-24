package com.jhalt.expiringmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.jhalt.expiringmap.ExpiringMap.ExpirationPolicy;
import com.jhalt.expiringmap.ExpiringMap.ExpiringEntry;

/**
 * Tests {@link ExpiringEntry}.
 */
public class ExpiringMapEntryTest {
    /**
     * Tests that entry ordering via {@link ExpiringEntry#compareTo(Entry)} is as expected.
     */
    @Test
    public void testEntryOrdering() {
        AtomicReference<ExpirationPolicy> expirationPolicy = new AtomicReference<ExpirationPolicy>(
                ExpirationPolicy.CREATED);

        NavigableSet<ExpiringEntry<String, String>> set = new TreeSet<ExpiringEntry<String, String>>();
        ExpiringEntry<String, String> entry1 = new ExpiringEntry<String, String>("a", "a",
                expirationPolicy, new AtomicLong(1000));
        ExpiringEntry<String, String> entry2 = new ExpiringEntry<String, String>("b", "b",
                expirationPolicy, new AtomicLong(1000));
        ExpiringEntry<String, String> entry3 = new ExpiringEntry<String, String>("c", "c",
                expirationPolicy, new AtomicLong(1000));
        ExpiringEntry<String, String> entry4 = new ExpiringEntry<String, String>("e", "e",
                expirationPolicy, new AtomicLong(500));
        ExpiringEntry<String, String> entry5 = new ExpiringEntry<String, String>("f", "f",
                expirationPolicy, new AtomicLong(1100));
        set.add(entry1);
        set.add(entry2);
        set.add(entry3);
        assertEquals(entry1, set.first());
        assertEquals(entry3, set.last());
        set.add(entry4);
        set.add(entry5);
        assertEquals(entry4, set.first());
        assertEquals(entry5, set.last());
    }

    /**
     * Tests that replacement of entries in a set is as expected per ordering via
     * {@link ExpiringEntry#compareTo(Entry)}.
     */
    @Test
    public void testEntryReplaceSameKey() {
        AtomicReference<ExpirationPolicy> expirationPolicy = new AtomicReference<ExpirationPolicy>(
                ExpirationPolicy.CREATED);
        Set<ExpiringEntry<String, String>> set = new TreeSet<ExpiringEntry<String, String>>();
        ExpiringEntry<String, String> entry1 = new ExpiringEntry<String, String>("a", "a",
                expirationPolicy, new AtomicLong(1000));
        ExpiringEntry<String, String> entry2 = new ExpiringEntry<String, String>("a", "a",
                expirationPolicy, new AtomicLong(1000));
        ExpiringEntry<String, String> entry3 = new ExpiringEntry<String, String>("a", "a",
                expirationPolicy, new AtomicLong(1500));
        set.add(entry1);
        assertFalse(set.add(entry2));
        assertFalse(set.add(entry3));
    }
}
