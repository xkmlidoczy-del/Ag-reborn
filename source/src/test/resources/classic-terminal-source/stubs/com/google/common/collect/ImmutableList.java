package com.google.common.collect;
import java.util.*; public class ImmutableList {public static <T> List<T> copyOf(List<T> v){return Collections.unmodifiableList(new ArrayList<>(v));}}
