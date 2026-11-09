package cn.academy.core;
public final class AcademyCraft { public static final Config config=new Config();public static final class Config {public boolean enabled;public boolean getBoolean(String key,String section,boolean fallback,String description){return enabled;}} }
