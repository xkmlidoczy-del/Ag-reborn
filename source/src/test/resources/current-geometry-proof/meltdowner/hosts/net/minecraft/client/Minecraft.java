package net.minecraft.client;
public final class Minecraft {
 private static final Minecraft INSTANCE=new Minecraft();
 public net.minecraft.client.player.LocalPlayer player=new net.minecraft.client.player.LocalPlayer();
 public Options options=new Options();
 public net.minecraft.client.multiplayer.ClientLevel level;
 public net.minecraft.client.multiplayer.ClientPacketListener connection=new net.minecraft.client.multiplayer.ClientPacketListener();
 public net.minecraft.client.multiplayer.ClientPacketListener getConnection(){return connection;}
 public boolean paused;public net.minecraft.world.entity.Entity cameraEntity;
 public final net.minecraft.client.renderer.GameRenderer gameRenderer=new net.minecraft.client.renderer.GameRenderer();
 public boolean isPaused(){return paused;} public net.minecraft.world.entity.Entity getCameraEntity(){return cameraEntity==null?player:cameraEntity;}
 public static Minecraft getInstance(){return INSTANCE;}
}
