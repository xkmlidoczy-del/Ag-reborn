package net.minecraft.world.level;
import net.minecraft.world.phys.Vec3;public class ClipContext {public enum Block{OUTLINE}public enum Fluid{NONE}public final Vec3 from,to;public ClipContext(Vec3 f,Vec3 t,Block b,Fluid u,Object entity){from=f;to=t;}}
