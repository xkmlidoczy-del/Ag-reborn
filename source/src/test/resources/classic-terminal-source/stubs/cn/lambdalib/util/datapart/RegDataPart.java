package cn.lambdalib.util.datapart;
import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD}) public @interface RegDataPart {Class<?> value();}
