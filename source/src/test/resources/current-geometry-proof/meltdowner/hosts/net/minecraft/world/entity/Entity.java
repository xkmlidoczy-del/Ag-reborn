package net.minecraft.world.entity;
public class Entity {
 public net.minecraft.world.phys.Vec3 at=net.minecraft.world.phys.Vec3.ZERO;
 public float yaw=23.5F,pitch=-17.25F;public java.util.UUID uuid=new java.util.UUID(1,2);
 public net.minecraft.world.phys.Vec3 position(){return at;}
 public float getYHeadRot(){return yaw;}public float getXRot(){return pitch;}
 public float getEyeHeight(){return 1.62F;}
 public int id=7;public int getId(){return id;}public java.util.UUID getUUID(){return uuid;}
 public net.minecraft.world.level.Level world;public boolean alive=true,removed,spectator;
 public net.minecraft.world.level.Level level(){return world;}public boolean isAlive(){return alive;}public boolean isRemoved(){return removed;}public boolean isSpectator(){return spectator;}
 public double getX(){return at.x;}public double getY(){return at.y;}public double getZ(){return at.z;}
}
