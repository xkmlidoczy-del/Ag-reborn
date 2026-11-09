package cn.lambdalib.s11n.nbt;
public final class NBTS11n {
 private static Object copy(Object value){if(value instanceof java.util.BitSet b)return b.clone();if(value instanceof java.util.HashSet<?> s)return new java.util.HashSet<>(s);return value;}
 public static void write(net.minecraft.nbt.NBTTagCompound tag,Object object){try{for(var field:object.getClass().getDeclaredFields())if(field.isAnnotationPresent(cn.lambdalib.s11n.SerializeIncluded.class)){field.setAccessible(true);tag.values.put(field.getName(),copy(field.get(object)));}}catch(Exception e){throw new IllegalStateException(e);}}
 public static void read(net.minecraft.nbt.NBTTagCompound tag,Object object){try{for(var field:object.getClass().getDeclaredFields())if(field.isAnnotationPresent(cn.lambdalib.s11n.SerializeIncluded.class)&&tag.values.containsKey(field.getName())){field.setAccessible(true);field.set(object,copy(tag.values.get(field.getName())));}}catch(Exception e){throw new IllegalStateException(e);}}
}
