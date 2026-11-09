package cn.academy.port.client.canon;

import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * Ground ring at the radius the server actually checks for an area skill. Motif art is sized for looks; this ring is the
 * honest reach, so what players see matches what the skill can touch. Presentation only.
 */
final class CanonRangeRing {
    private static final Map<String,float[]> COLOURS=Map.of(
            "mental_out",new float[]{.96f,.57f,.8f},"aero_hand",new float[]{.49f,.93f,.8f},"nitrogen_armor",new float[]{.51f,.71f,1f},
            "dark_matter",new float[]{.88f,.85f,1f},"hydro_hand",new float[]{.37f,.82f,1f},"paper_psychokinesis",new float[]{1f,.88f,.64f},
            "liquid_shadow",new float[]{.65f,.68f,.78f},"float_dial",new float[]{.68f,.91f,.71f});
    private CanonRangeRing() {}

    static void draw(Canvas c,View v){
        if(v.radius()<=0||v.rangeCentre()==null||v.phase()==3||cn.academy.port.canon.CanonExpansionCombatA.physicalBarrier(v.skill()))return;
        double e=CanonFxScripts.env(v);if(e<=.01)return;
        // A control packet's radius admits targets at cast time. The actual
        // marks continue with their own lease; the selection boundary does not.
        if (CanonFxMental.targetPointField(v.skill()) && CanonReceiptFx.replacesLegacy(v.skill()))
            e *= 1 - CanonFxScripts.clamp01((v.age() - 6) / 6);
        if (e <= .01) return;
        float[] rgb=COLOURS.getOrDefault(v.category(),new float[]{1,1,1});
        double grow=Math.min(1,v.age()/4.0),R=v.radius();
        Vec3 at=v.rangeCentre().add(0,.06,0);
        // A dashed range cue has its centreline at the authoritative radius from its first frame.
        // Texture padding and an expanding shockwave are not reliable measurements of reach.
        for(int i=0;i<32;i++){
            double a=i*Math.PI/16,b=a+Math.PI/24;
            Vec3 p=at.add(Math.cos(a)*R,0,Math.sin(a)*R),q=at.add(Math.cos(b)*R,0,Math.sin(b)*R);
            c.cellStrip("fx4:common_speedline",CanonFx4.Mode.ALPHA,p,q,.07,0,CanonSignatures.deep(rgb),(float)(.7*e*grow));
            c.cellStrip("fx4:common_speedline",CanonFx4.Mode.ADD,p,q,.035,0,rgb,(float)(.65*e*grow));
        }
    }
}
