package com.google.common.cache;
public class CacheBuilder {public static CacheBuilder newBuilder(){return new CacheBuilder();}public CacheBuilder maximumSize(int n){return this;}public <K,V> LoadingCache<K,V> build(CacheLoader<K,V> l){return new LoadingCache<>(l);}}
