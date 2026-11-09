package com.google.common.base;
public final class Preconditions { public static <T>T checkNotNull(T value){if(value==null)throw new NullPointerException();return value;} }
