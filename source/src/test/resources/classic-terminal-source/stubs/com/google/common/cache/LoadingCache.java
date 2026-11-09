package com.google.common.cache;
import java.util.concurrent.ExecutionException; public class LoadingCache<K,V>{private final CacheLoader<K,V> loader;LoadingCache(CacheLoader<K,V> l){loader=l;}public V get(K k)throws ExecutionException{try{return loader.load(k);}catch(Exception e){throw new ExecutionException(e);}}}
