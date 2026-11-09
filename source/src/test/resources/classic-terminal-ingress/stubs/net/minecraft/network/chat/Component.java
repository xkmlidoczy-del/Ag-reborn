package net.minecraft.network.chat;
public record Component(String key){public static Component translatable(String key,Object...args){return new Component(key);}}
