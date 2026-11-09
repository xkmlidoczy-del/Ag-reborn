package com.google.common.base;
public class Throwables {public static RuntimeException propagate(Throwable t){return t instanceof RuntimeException?(RuntimeException)t:new RuntimeException(t);}}
