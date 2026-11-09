package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.VisualTierConfig;
import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.Map;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Float Dial on FX4, our reading of buoyancy (not gravity control): what floats is wrapped in a thin-film bubble (SH4) and carried
 * by rising bubbles (PT5); density slices ride up or down the target only, never on the ground. Ladder: level 3 makes lifted things
 * overshoot and bob (TM2), level 4 adds a swarm of bubbles circling the target (PT3), level 5 gives the caster's view a slow,
 * weightless tint and a gentle field-of-view swell (the planned cinematic camera, CM4, is not built yet). Presentation only.
 */
final class CanonFxFloat {
    private static final float[] BUBBLE={.65f,.91f,1f},PALE={.92f,.96f,1f},LILAC={.91f,.72f,1f};
    private static final String BUBBLES="fd_bubble_atlas",SHELL="fx4:fd_bubble_atlas",SLICE="fx4:fd_slice_ring",COLUMN="fx4:fd_column",POP="fx4:fd_pop_sheet";
    enum Role { LIFT, LOWER, CARRY, FIELD }
    private static final Map<String,Role> ROLES=Map.ofEntries(
            Map.entry("float_descent",Role.LOWER),Map.entry("float_ally_descent",Role.LOWER),Map.entry("float_hostile_descent",Role.LOWER),
            Map.entry("float_item_lower",Role.LOWER),Map.entry("float_air_brake",Role.LOWER),Map.entry("float_item_release",Role.LOWER),
            Map.entry("float_item_pull",Role.CARRY),Map.entry("float_item_push",Role.CARRY),Map.entry("float_item_orbit",Role.CARRY),
            Map.entry("float_field",Role.FIELD),Map.entry("float_hover_field",Role.FIELD),Map.entry("float_item_field",Role.FIELD),
            Map.entry("float_item_shelf_field",Role.FIELD),Map.entry("float_release_field",Role.FIELD),Map.entry("float_rescue_field",Role.FIELD));
    private CanonFxFloat() {}
    static Role role(String skill){return ROLES.getOrDefault(skill,Role.LIFT);}

    static void onReceive(CanonVfx.Visual v,int tier,boolean local,double life){
        if (role(v.skill()) == Role.FIELD) {
            if (v.phase() == CanonVfx.Phase.START && v.origin().distanceToSqr(v.endpoint()) > .25) {
                bubbles(v.endpoint(), 6, false, 0);
            }
            return;
        }
        if(v.phase()==CanonVfx.Phase.END)return;
        Vec3 b=v.endpoint();boolean down=role(v.skill())==Role.LOWER;
        for(int w=0;w<Math.min(10,(int)(life/5));w++){final int wave=w;CanonFxForms.later(wave*5,()->bubbles(b,4,down,wave));}
        if(local&&tier>=5){CanonCamera.tint(BUBBLE,.25,(int)Math.min(40,life));CanonCamera.fovKick(.04,30);}
    }
    /** PT5: bubbles that accelerate upward (or drift down, slowing) and burst. */
    private static void bubbles(Vec3 at,int n,boolean down,int seed){
        for(int k=0;k<n;k++){double ang=rnd(k+seed*7)*6.28,rad=.2+.8*rnd(k+seed+3);
            CanonParticles.spawn(BUBBLES,k+seed,at.add(Math.cos(ang)*rad,down?1.8:-.4,Math.sin(ang)*rad),new Vec3(0,down?-.03:.02,0),down?-.0006:-.003,.01,
                    .075+.065*rnd(k+5),0,30,BUBBLE,.8f,CanonFx4.Mode.ALPHA,CanonParticles.Land.NONE,false,0);}
    }

    static void draw(Canvas c,View v){
        double e=env(v);if(e<=0)return;double t=time(v);int tier=Math.max(1,Math.min(5,v.tier()));
        switch(role(v.skill())){
            case LIFT,LOWER -> target(c,v,e,t,tier,role(v.skill())==Role.LOWER);
            case CARRY -> carry(c,v,e,t);
            case FIELD -> field(c,v,e,t,tier);
        }
    }
    /** SH4: a thin-film bubble around a box, wobbling slightly; the colour film is in the texture. */
    private static void wrap(Canvas c,AABB box,double e,double t,double grow){
        Vec3 mid=box.getCenter();double r=Math.max(box.getXsize(),box.getZsize())*.75+.25,h=box.getYsize()*.62+.2,w=1+.05*Math.sin(t*.4);
        // Let the thin coloured membrane lead; the supporting density traces
        // remain below it rather than bleaching the target into white streaks.
        float[] film=tint(BUBBLE,LILAC,.3+.18*Math.sin(t*.045));
        c.filmShell(SHELL,CanonFx4.Mode.ALPHA,mid,r*grow*w,h*grow/w,10,16,film,(float)(.88*e),5);
    }
    private static void target(Canvas c,View v,double e,double t,int tier,boolean down){
        // The shared client view binds the server endpoint to one native entity.
        // Never reacquire a nearby item or creature after that target has moved.
        AABB box=v.targetBox();if(box==null)return;
        double grow=tier>=3?easeOutBack(reveal(v,7)):easeOut(reveal(v,7));wrap(c,box,e,t,grow);
        // Density slices travel along the target only: up while it rises, down while it is lowered.
        for(int k=0;k<3;k++){double ph=frac(t*.03+k/3.),y=down?box.maxY-ph*box.getYsize():box.minY+ph*box.getYsize();
            Vec3 mid=new Vec3(box.getCenter().x,y,box.getCenter().z);double r=Math.max(box.getXsize(),box.getZsize())*.8+.2;
            c.cellPlane(SLICE,CanonFx4.Mode.ADD,mid,new Vec3(r,0,0),new Vec3(0,0,r),0,down?LILAC:BUBBLE,(float)(.30*e*Math.sin(Math.PI*ph)));}
        // The buoyant column under the target.
        Vec3 foot=new Vec3(box.getCenter().x,box.minY,box.getCenter().z);
        c.fx4Quad(COLUMN,CanonFx4.Mode.ADD,foot.add(c.right.scale(.35)).add(0,-.2,0),foot.subtract(c.right.scale(.35)).add(0,-.2,0),
                foot.subtract(c.right.scale(.35)).add(0,1.4,0),foot.add(c.right.scale(.35)).add(0,1.4,0),
                new float[]{0,(float)(1+t*(down?-.02:.02)),1,(float)(t*(down?-.02:.02))},BUBBLE[0],BUBBLE[1],BUBBLE[2],(float)(.28*e));
        if(tier>=4)swarm(c,box.getCenter(),Math.max(box.getXsize(),box.getYsize())*.9+.4,e,t,8);
        if(life01(v)>.85){var grid=Fx4Atlas.get("fd_pop_sheet");c.cellSprite(POP,CanonFx4.Mode.ADD,box.getCenter(),Math.max(box.getXsize(),box.getYsize())*.7,0,grid.frame((life01(v)-.85)/.15),PALE,(float)(.8*e));}
    }
    /** PT3: bubbles circling a centre on tilted orbits. */
    private static void swarm(Canvas c,Vec3 centre,double R,double e,double t,int n){
        for(int k=0;k<n;k++){double a=t*.06+k*6.283/n,tilt=rnd(k)*1.2-.6;
            Vec3 p=centre.add(Math.cos(a)*R,Math.sin(a)*R*tilt+.2*Math.sin(t*.1+k),Math.sin(a)*R);
            c.cellSprite("fx4:"+BUBBLES,CanonFx4.Mode.ALPHA,p,.07+.05*rnd(k+3),0,k,BUBBLE,(float)(.85*e));}
    }
    private static void carry(Canvas c,View v,double e,double t){
        AABB box=v.targetBox();Vec3 a=v.origin(),b=box==null?v.endpoint():box.getCenter();
        if(box!=null)wrap(c,box,e,t,easeOut(reveal(v,6)));
        for(int k=0;k<10;k++){double s=frac(t*.04+k/10.);Vec3 p=a.add(b.subtract(a).scale(s)).add(0,.25*Math.sin(Math.PI*s)+.08*Math.sin(t*.3+k),0);
            c.cellSprite("fx4:"+BUBBLES,CanonFx4.Mode.ALPHA,p,.05+.04*rnd(k),0,k,BUBBLE,(float)(.8*e*Math.sin(Math.PI*s)));}
    }
    private static void field(Canvas c,View v,double e,double t,int tier){
        if (!VisualTierConfig.tier().enhanced()) return;
        if (v.phase() == CanonVfx.Phase.END.ordinal()) {
            if ("float_release_field".equals(v.skill())) {
                c.cellSprite(POP, CanonFx4.Mode.ALPHA, v.endpoint().add(0, .22, 0), .5, 0,
                        Fx4Atlas.get("fd_pop_sheet").frame(life01(v)), PALE, (float)(.78 * e));
            }
            return;
        }
        Vec3 centre = v.rangeCentre();
        if (centre == null || v.radius() <= 0) return;
        double radius = v.radius();
        // No target identity accompanies a field packet. Density bands visualize the area;
        // only the separate single-target receipts can wrap a bound entity.
        for (int layer = 0; layer < 3; layer++) {
            double progress = frac(v.age() * .035 + layer / 3.0);
            double height = radius * (.12 + .36 * progress);
            double section = Math.sqrt(radius * radius - height * height) * (.43 + .44 * progress);
            float alpha = (float)(.40 * e * Math.sin(Math.PI * progress));
            c.cellPlane(SLICE, CanonFx4.Mode.ALPHA, centre.add(0, height, 0),
                    new Vec3(section, 0, 0), new Vec3(0, 0, section), 0,
                    layer == 1 ? LILAC : BUBBLE, alpha);
        }
    }
}
