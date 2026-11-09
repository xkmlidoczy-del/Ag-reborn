package com.google.common.collect;
public interface Multimap<K,V> { void put(K key,V value); java.util.Collection<V> get(K key); }
