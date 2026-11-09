package cn.lambdalib.util.generic;
import java.lang.reflect.*;public class RegistryUtils {public static Field getObfField(Class<?> c,String a,String b){try{return c.getDeclaredField(a);}catch(Exception e){throw new RuntimeException(e);}}}
