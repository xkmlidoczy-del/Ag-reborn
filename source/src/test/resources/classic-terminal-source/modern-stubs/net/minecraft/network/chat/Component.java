package net.minecraft.network.chat;
public class Component {public final String key;private Component(String k){key=k;}public static Component translatable(String k,Object...args){return new Component(k);}}
