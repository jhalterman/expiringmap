package net.jodah.expiringmap;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.Test;

import net.jodah.expiringmap.ExpiringMap.ExpiringEntry;

/**
 * Tests {@link ExpiringEntry}.
 */
public class ExpiringEntryTest {
  /**
   * Tests that entry ordering via {@link ExpiringEntry#compareTo(ExpiringEntry)} is as expected.
   */
  @Test
  public void testEntryOrdering() {
    AtomicReference<ExpirationPolicy> expirationPolicy = new AtomicReference<ExpirationPolicy>(ExpirationPolicy.CREATED);
    NavigableSet<ExpiringEntry<String, String>> set = new TreeSet<ExpiringEntry<String, String>>();
    ExpiringEntry<String, String> entry1 = new ExpiringEntry<String, String>("a", "a", expirationPolicy,
      new AtomicLong(1000000));
    ExpiringEntry<String, String> entry2 = new ExpiringEntry<String, String>("b", "b", expirationPolicy,
      new AtomicLong(1000000));
    ExpiringEntry<String, String> entry3 = new ExpiringEntry<String, String>("c", "c", expirationPolicy,
      new AtomicLong(1000000));
    ExpiringEntry<String, String> entry4 = new ExpiringEntry<String, String>("e", "e", expirationPolicy,
      new AtomicLong(5000));
    ExpiringEntry<String, String> entry5 = new ExpiringEntry<String, String>("f", "f", expirationPolicy,
      new AtomicLong(1100000));
    set.add(entry1);
    set.add(entry2);
    set.add(entry3);
    assertEquals(set.first(), entry1);
    assertEquals(set.last(), entry3);
    set.add(entry4);
    set.add(entry5);
    assertEquals(set.first(), entry4);
    assertEquals(set.last(), entry5);
  }

  /**
   * Tests that replacement of entries in a set is as expected per ordering via
   * {@link ExpiringEntry#compareTo(ExpiringEntry)}.
   */
  @Test
  public void testEntryReplaceSameKey() {
    AtomicReference<ExpirationPolicy> expirationPolicy = new AtomicReference<ExpirationPolicy>(ExpirationPolicy.CREATED);
    Set<ExpiringEntry<String, String>> set = new TreeSet<ExpiringEntry<String, String>>();
    ExpiringEntry<String, String> entry1 = new ExpiringEntry<String, String>("a", "a", expirationPolicy,
      new AtomicLong(1000));
    ExpiringEntry<String, String> entry2 = new ExpiringEntry<String, String>("a", "a", expirationPolicy,
      new AtomicLong(1000));
    ExpiringEntry<String, String> entry3 = new ExpiringEntry<String, String>("a", "a", expirationPolicy,
      new AtomicLong(1500));
    set.add(entry1);
    assertFalse(set.add(entry2));
    assertFalse(set.add(entry3));
  }
}
