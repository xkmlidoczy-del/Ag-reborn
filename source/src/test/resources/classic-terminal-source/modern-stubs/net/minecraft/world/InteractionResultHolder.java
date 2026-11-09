package net.minecraft.world;
public class InteractionResultHolder<T>{public T value; public static <T> InteractionResultHolder<T> sidedSuccess(T t,boolean client){var r=new InteractionResultHolder<T>();r.value=t;return r;}}
