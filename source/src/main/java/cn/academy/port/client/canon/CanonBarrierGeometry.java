package cn.academy.port.client.canon;

import cn.academy.port.canon.CanonExpansionCombatA;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import net.minecraft.world.phys.Vec3;

/** The intercept volume's edges, using its frozen server aim rather than the actor's later body yaw. */
final class CanonBarrierGeometry {
    private CanonBarrierGeometry(){}
    static Vec3 forward(View v){return v.aimAxis()!=null?v.aimAxis():Vec3.directionFromRotation(0,v.yaw());}
    static Vec3 centre(View v){return v.barrierCentre()!=null?v.barrierCentre():v.anchor().add(0,1.62,0).add(forward(v).scale(CanonExpansionCombatA.frontalBarrier(v.skill())?CanonExpansionCombatA.barrierDistance(v.skill()):0));}
    static void boundary(Canvas c,View v,float[] rgb,double e){
        if(!CanonExpansionCombatA.physicalBarrier(v.skill())||v.phase()==2||v.phase()==3)return;
        var box=CanonExpansionCombatA.barrierBox(centre(v),v.skill());
        drawBox(c,box,rgb,e*.55);
        if(v.skill().equals("aero_air_curtain"))drawBox(c,CanonExpansionCombatA.barrierInnerBox(centre(v),v.skill()),rgb,e*.8);
    }
    private static void drawBox(Canvas c,net.minecraft.world.phys.AABB box,float[] rgb,double e){
        Vec3[] p=new Vec3[8];for(int i=0;i<8;i++)p[i]=new Vec3((i&1)==0?box.minX:box.maxX,(i&2)==0?box.minY:box.maxY,(i&4)==0?box.minZ:box.maxZ);
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0)CanonSignatures.streak(c,p[i],p[i|bit],.022,rgb,e);
    }
}
