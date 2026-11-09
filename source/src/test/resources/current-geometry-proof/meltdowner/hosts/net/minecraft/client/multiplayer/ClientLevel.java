package net.minecraft.client.multiplayer;public class ClientLevel extends net.minecraft.world.level.Level {
 public final java.util.Map<Integer,net.minecraft.world.entity.Entity> entities=new java.util.HashMap<>();
 public net.minecraft.world.entity.Entity getEntity(int id){return entities.get(id);}
}
