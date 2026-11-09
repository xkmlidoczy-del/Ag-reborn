package cn.lambdalib.annoreg.mc;
import java.lang.annotation.*; @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD}) public @interface RegItem {@Retention(RetentionPolicy.RUNTIME) public @interface Render {} @Retention(RetentionPolicy.RUNTIME) public @interface HasRender {}}
