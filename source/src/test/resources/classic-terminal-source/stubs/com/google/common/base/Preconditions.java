package com.google.common.base;
public class Preconditions {public static <T> T checkNotNull(T v){if(v==null)throw new NullPointerException();return v;}public static void checkArgument(boolean v){if(!v)throw new IllegalArgumentException();}}
