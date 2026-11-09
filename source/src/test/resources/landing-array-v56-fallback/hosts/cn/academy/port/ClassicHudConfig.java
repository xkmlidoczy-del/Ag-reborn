package cn.academy.port;
/** Compile-only GUI configuration; unsupported GUI execution fails closed. */
public final class ClassicHudConfig {public static final Spec SPEC=new Spec();public static final Value CP_X=new Value(),CP_Y=new Value(),KEY_X=new Value(),KEY_Y=new Value(),NOTIFICATION_X=new Value(),NOTIFICATION_Y=new Value();public static class Spec{public boolean isLoaded(){throw new UnsupportedOperationException("GUI outside fallback proof");}}public static class Value{public double get(){throw new UnsupportedOperationException("GUI outside fallback proof");}}}
