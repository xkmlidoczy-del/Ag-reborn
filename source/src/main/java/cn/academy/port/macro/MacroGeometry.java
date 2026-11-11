package cn.academy.port.macro;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Read-only truncation: no world block query is allowed to discover an unloaded chunk by loading it. */
public final class MacroGeometry {
    private MacroGeometry(){}
    public static Vec3 loadedEnd(ServerLevel world,Vec3 start,Vec3 end){
        if(!MacroVfx.finite(start)||!MacroVfx.finite(end))return start;
        double length=start.distanceTo(end);int steps=Math.max(1,(int)Math.ceil(length*2));Vec3 previous=start;
        for(int i=0;i<=steps;i++){var at=start.lerp(end,i/(double)steps);if(world.getChunkSource().getChunkNow(((int)Math.floor(at.x))>>4,((int)Math.floor(at.z))>>4)==null)return previous;previous=at;}
        return end;
    }
    public static boolean sphereLoaded(ServerLevel world,Vec3 centre,double radius){
        for(int x=((int)Math.floor(centre.x-radius))>>4;x<=((int)Math.floor(centre.x+radius))>>4;x++)
            for(int z=((int)Math.floor(centre.z-radius))>>4;z<=((int)Math.floor(centre.z+radius))>>4;z++)if(world.getChunkSource().getChunkNow(x,z)==null)return false;
        return true;
    }
}
