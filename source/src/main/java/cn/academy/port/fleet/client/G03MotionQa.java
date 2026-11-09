package cn.academy.port.fleet.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import cn.academy.port.fleet.GunItem;
import cn.academy.port.fleet.InteractionLab;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.world.item.ItemStack;

/** Per-render-frame evidence, only enabled by an explicit disposable-world QA command. */
public final class G03MotionQa {
 private static PrintWriter out;
 private static String label;
 public static void register(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> d) {
  if(!Boolean.getBoolean("academy.qa.showcase")||!Boolean.getBoolean("academy.qa.confirmOwnWorld"))return;
  d.register(Commands.literal("aclab_qa_motion")
   .then(Commands.literal("start").then(Commands.argument("label",StringArgumentType.word()).executes(c->{
    stop();label=StringArgumentType.getString(c,"label");if(!label.matches("[A-Za-z0-9_-]{1,60}"))throw new IllegalArgumentException("QA label");
    Path p=Minecraft.getInstance().gameDirectory.toPath().resolve("motion-"+label+".csv");
    try {out=new PrintWriter(Files.newBufferedWriter(p,StandardOpenOption.CREATE_NEW));}
    catch(IOException ex){throw new IllegalStateException(ex);}
    out.println("time,equip,ammo,reloadStart,reloadEnd,lastFire,wx,wy,wz,qx,qy,qz,qw,lx,ly,lz,rx,ry,rz,run,walk");
    InteractionLab.LOGGER.info("C03_MOTION_START {}",label);return 1;
   })))
   .then(Commands.literal("stop").executes(c->{stop();return 1;})));
 }
 private static void stop(){if(out!=null){out.close();out=null;InteractionLab.LOGGER.info("C03_MOTION_STOP {}",label);}}
 public static void sample(ItemStack stack,double time,float equip,float run,float walk,G03Rig.Frame f){
  if(out==null)return;var rig=G03Rig.get(((GunItem)stack.getItem()).variant);var p=f.poses[rig.id("weapon")];var l=rig.point(f,"left_hand");var r=rig.point(f,"right_hand");
  out.printf(Locale.ROOT,"%.8f,%.7f,%d,%d,%d,%d,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.8f,%.6f,%.6f%n",time,equip,GunItem.ammo(stack),GunItem.reloadStart(stack),GunItem.reloadEnd(stack),GunItem.lastFire(stack),p.pos.x,p.pos.y,p.pos.z,p.rot.x,p.rot.y,p.rot.z,p.rot.w,l.x,l.y,l.z,r.x,r.y,r.z,run,walk);
 }
}
