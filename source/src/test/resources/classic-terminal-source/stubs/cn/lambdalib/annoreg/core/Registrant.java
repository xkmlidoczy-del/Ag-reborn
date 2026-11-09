package cn.lambdalib.annoreg.core;
import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD}) public @interface Registrant {}
