package net.minecraft.world;
public final class World { public boolean isRemote;public final java.util.List<net.minecraft.entity.item.EntityItem> spawned=new java.util.ArrayList<>();public boolean spawnEntityInWorld(net.minecraft.entity.item.EntityItem entity){spawned.add(entity);cn.academy.misc.tutorial.OracleSupport.logs.add("drop");return true;} }
