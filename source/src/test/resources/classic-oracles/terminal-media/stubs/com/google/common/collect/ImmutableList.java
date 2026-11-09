package com.google.common.collect;import java.util.*;public final class ImmutableList {public static<T>List<T>copyOf(Collection<T> c){return Collections.unmodifiableList(new ArrayList<>(c));}}
