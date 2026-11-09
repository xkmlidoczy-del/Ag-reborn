package org.apache.commons.lang3.reflect;
import java.lang.reflect.*;import java.util.*;public class FieldUtils {public static List<Field> getAllFieldsList(Class<?> c){List<Field> r=new ArrayList<>();for(;c!=null;c=c.getSuperclass())r.addAll(Arrays.asList(c.getDeclaredFields()));return r;}}
