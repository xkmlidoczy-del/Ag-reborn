package net.minecraft.server.level;
public class ServerPlayer extends net.minecraft.world.entity.player.Player {private final net.minecraft.nbt.CompoundTag data=new net.minecraft.nbt.CompoundTag();public int syncs;public boolean valid=true;public net.minecraft.nbt.CompoundTag getPersistentData(){return data;}}
