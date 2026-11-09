package cn.academy.port.client.canon;

import static cn.academy.port.client.canon.CanonFxScripts.*;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonFx.Canvas;
import cn.academy.port.client.canon.CanonFx.View;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

/**
 * Paper Psychokinesis on FX4: real sheets of paper, never light. Sheets (pp_sheet_atlas shapes on a fibre surface) fly out of the
 * caster's hand, unfold along their creases with an overshoot (GM2, TM2) and fold back up when retracted; projectiles are folded
 * shapes that leave tumbling scraps (PT3). Ladder: level 3 staggers and overshoots every unfold, level 4 lets scraps tumble and lie
 * on the ground (PT1), level 5 raises a paper storm around the caster with a camera shake (the planned cinematic camera, CM4, is
 * not built yet). All alpha blended. Presentation only.
 */
final class CanonFxPaper {
    private static final float[] PAPER={1f,.97f,.88f},SHADE={.86f,.8f,.68f},RED={.9f,.32f,.26f};
    private static final String SHEETS="fx4:pp_sheet_atlas",SCRAP="pp_scrap_sheet",LINE="fx4:common_speedline";
    /** Cells of pp_sheet_atlas: square, book fold, triangle, kite, crane wing, plane wing, fan, crumpled. */
    private static final int SQUARE=0,BOOK=1,TRIANGLE=2,KITE=3,CRANE=4,PLANE=5,FAN=6;
    enum Role { THROW, BUILD, RETRACT, ARMOR, WING, ARRAY }
    private static final Map<String,Role> ROLES=Map.ofEntries(
            Map.entry("paper_blade",Role.THROW),Map.entry("paper_plane",Role.THROW),Map.entry("paper_retract",Role.RETRACT),
            Map.entry("paper_armor",Role.ARMOR),Map.entry("paper_wing",Role.WING),Map.entry("paper_array",Role.ARRAY));
    private CanonFxPaper() {}
    static Role role(String skill){return ROLES.getOrDefault(skill,Role.BUILD);}
    static double arrive(double life){return Math.min(life*.45,6);}

    // ------------------------------------------------------------------ one-shot work
    static void onReceive(CanonVfx.Visual v,int tier,boolean local,double life){
        if(v.phase()==CanonVfx.Phase.END || CanonReceiptFx.paperPlacement(v.skill())
                || "paper_array".equals(v.skill()) && CanonReceiptFx.replacesLegacy(v.skill())) return;
        Role role=role(v.skill());Vec3 b=v.endpoint();int hit=(int)Math.round(arrive(life));
        switch(role){
            case THROW -> CanonFxForms.later(hit,()->{scraps(b,v.skill().equals("paper_blade")?8:4,tier);
                if(local&&v.skill().equals("paper_blade")){CanonCamera.shake(.2+.08*tier,5);CanonCamera.freeze(20+8*tier);}});
            case BUILD -> CanonFxForms.later(hit+4,()->scraps(b,3+tier,tier));
            case ARRAY -> {for(int w=0;w<4;w++){final int wave=w;CanonFxForms.later(3+wave*5,()->scraps(v.origin().add(0,.5,0),6,tier));}
                if(local){CanonCamera.shake(.45,12);if(tier>=5)CanonCamera.fovKick(.05,16);}}
            case WING -> {if(local)CanonCamera.fovKick(.07,14);}
            default -> { }
        }
    }
    /** PT3/PT1: scraps tumble as they fall; from level 4 they lie on the ground before dissolving. */
    private static void scraps(Vec3 at,int n,int tier){
        for(int k=0;k<n;k++){double ang=rnd(k*7+(int)(at.x*5))*6.28,sp=.04+.05*rnd(k+3);
            CanonParticles.spawn(SCRAP,0,at.add(0,.3,0),new Vec3(Math.cos(ang)*sp,.08+.06*rnd(k+5),Math.sin(ang)*sp),.012,.05,.09+.05*rnd(k+9),0,
                    tier>=4?110:45,PAPER,.95f,CanonFx4.Mode.ALPHA,tier>=4?CanonParticles.Land.STOP:CanonParticles.Land.NONE,false,0);}
    }

    // ------------------------------------------------------------------ per frame
    static void draw(Canvas c,View v){
        Role role=role(v.skill());
        // Flight remains materially deployed until the server ends it. The
        // shared envelope fades START during its last 30%; applying it here
        // made the wings vanish before an END packet reopened their fold.
        // A missing END still retires at the finite lease, with a short tail.
        double e=role==Role.WING&&v.phase()!=3
                ?easeOut(reveal(v,4))*clamp01((v.life()-v.age())/.5):env(v);
        if(e<=0)return;double t=time(v);int tier=Math.max(1,Math.min(5,v.tier()));
        if (CanonReceiptFx.paperPlacement(v.skill())) {
            if (v.phase() == CanonVfx.Phase.START.ordinal()) placed(c, v, e);
            return;
        }
        if (role == Role.ARRAY && CanonReceiptFx.replacesLegacy(v.skill())) {
            if (v.phase() == CanonVfx.Phase.HIT.ordinal()) arrayHit(c, v, e);
            return;
        }
        switch(role){
            case THROW -> fly(c,v,e,t);
            case BUILD -> build(c,v,e,t,tier,false);
            case RETRACT -> build(c,v,e,t,tier,true);
            case ARMOR -> armor(c,v,e,t);
            case WING -> wing(c,v,e,t);
            case ARRAY -> array(c,v,e,t,tier);
        }
    }
    /** GM2: one sheet swung about a hinge line; angle 0 lies along {@code dir}, positive angles lift it about {@code axis}. */
    private static void sheet(Canvas c,int cell,Vec3 hinge,Vec3 axis,Vec3 dir,double width,double length,double angle,float[] rgb,float al){
        Vec3 ax=axis.normalize(),d0=dir.normalize();
        Vec3 d=d0.scale(Math.cos(angle)).add(ax.cross(d0).scale(Math.sin(angle))).add(ax.scale(ax.dot(d0)*(1-Math.cos(angle))));
        Vec3 w=ax.scale(width*.5),far=d.scale(length);float[] uv=Fx4Atlas.get("pp_sheet_atlas").cell(cell);
        c.fx4Quad(SHEETS,CanonFx4.Mode.ALPHA,hinge.subtract(w).add(far),hinge.add(w).add(far),hinge.add(w),hinge.subtract(w),uv,rgb[0],rgb[1],rgb[2],al);
    }
    private static void fly(Canvas c,View v,double e,double t){
        Vec3 a=v.origin(),b=v.endpoint(),d=b.subtract(a);boolean plane=v.skill().equals("paper_plane");
        double arr=arrive(v.life()),p=clamp01(v.age()/Math.max(1,arr));if(p>=1)return;
        double s=plane?p:easeIn(p);Vec3 side=rightOf(d.normalize());
        Vec3 at=a.add(d.scale(s)).add(0,(plane?.6:.25)*Math.sin(Math.PI*s),0).add(side.scale((plane?.3:.1)*Math.sin(2*Math.PI*s)));
        Vec3 dir=d.normalize(),up=upOf(dir,side);double roll=plane?Math.sin(t*.3)*.4:t*.8;
        Vec3 wing=side.scale(Math.cos(roll)).add(up.scale(Math.sin(roll)));
        // A plane is two wings on a keel; a blade is a single folded edge spinning on its axis.
        if(plane){sheet(c,PLANE,at,dir,wing,.5,.28,.15,PAPER,(float)e);sheet(c,PLANE,at,dir,wing.scale(-1),.5,.28,-.15,SHADE,(float)e);}
        else sheet(c,TRIANGLE,at.subtract(dir.scale(.2)),dir,wing,.42,.16,0,PAPER,(float)e);
        c.ribbon4(LINE,CanonFx4.Mode.ALPHA,line(at.subtract(d.normalize().scale(.9)),at,3),x->.04*x,x->.35*e*x,SHADE,1,0);
    }
    private static void build(Canvas c,View v,double e,double t,int tier,boolean retract){
        // Sheets fly from the hand to the construction point, then unfold flat along their creases one after another.
        Vec3 hand=v.anchor().add(0,1.2,0),site=v.endpoint();if(site.distanceToSqr(hand)<1)site=hand.add(flat(Vec3.directionFromRotation(0,v.yaw())).scale(1.5)).add(0,-1,0);
        int n=Math.min(10,4+tier*2);double stagger=tier>=3?1.2:.6;
        for(int k=0;k<n;k++){
            double p=clamp01((v.age()-k*stagger)/Math.max(4,arrive(v.life())));if(retract)p=1-p;if(p<=0)continue;
            double flyP=clamp01(p/.55),open=tier>=3?easeOutBack(clamp01((p-.55)/.45)):easeOut(clamp01((p-.55)/.45));
            double ang=k*2.399,rad=.35+.12*(k%4);Vec3 home=site.add(Math.cos(ang)*rad,.05+.02*k,Math.sin(ang)*rad);
            Vec3 at=hand.add(home.subtract(hand).scale(easeOut(flyP))).add(0,.8*Math.sin(Math.PI*flyP),0);
            Vec3 axis=new Vec3(Math.cos(ang+1.57),0,Math.sin(ang+1.57)),dir=new Vec3(Math.cos(ang),0,Math.sin(ang));
            double lift=(1-open)*1.5+Math.sin(t*.2+k)*.04*(1-open);
            int cell=k%3==0?SQUARE:k%3==1?BOOK:KITE;
            sheet(c,cell,at,axis,dir,.42,.42,lift,PAPER,(float)e);sheet(c,cell,at,axis,dir.scale(-1),.42,.42,-lift,SHADE,(float)(.9*e));
        }
    }
    private static void armor(Canvas c,View v,double e,double t){
        Vec3 body=v.anchor().add(0,1,0);double lock=easeOutBack(reveal(v,8));
        for(int k=0;k<10;k++){double ang=k*.628+t*.04*(1-lock),y=(k%3-1)*.45;
            Vec3 out=new Vec3(Math.cos(ang),0,Math.sin(ang)),home=body.add(out.scale(.55)).add(0,y,0),at=body.add(out.scale(2.2)).add(0,y+1,0);
            at=at.add(home.subtract(at).scale(lock));
            sheet(c,k%2==0?SQUARE:FAN,at.subtract(0,.2,0),rightOf(out),new Vec3(0,1,0),.36,.4,.1*Math.sin(t*.3+k),PAPER,(float)e);}
    }
    private static void wing(Canvas c,View v,double e,double t){
        Vec3 f=flat(Vec3.directionFromRotation(0,v.yaw())),back=f.scale(-1),r=rightOf(f);
        Vec3 root=v.anchor().add(0,1.35,0).add(back.scale(.28));
        // An actual folded forewing carries the longer layered paper primaries.
        // Keep the elbows outside the torso so the opposite wing is not reduced
        // to a stack of tiny sheets directly behind the player's shoulders.
        for(int side=-1;side<=1;side+=2){
            double shoulder=v.phase()==3?e:easeOutBack(reveal(v,6));
            if(shoulder<=0)continue;
            Vec3 outward=r.scale(side),shoulderAt=root.add(outward.scale(.20));
            double flap=Math.sin(t*.20+side*.12)*.055;
            sheet(c,BOOK,shoulderAt,f,outward.add(0,.18,0),.56,.88*shoulder,
                    side*(flap+(1-shoulder)*1.15),PAPER,(float)e);
            Vec3 elbow=shoulderAt.add(outward.scale(.70*shoulder)).add(0,.12*shoulder,0).add(back.scale(.10));
            for(int k=0;k<6;k++){
                double unfold=v.phase()==3?e:easeOutBack(clamp01((v.age()-k*.9)/5));
                if(unfold<=0)continue;
                Vec3 hinge=elbow.add(outward.scale(.045*k)).add(back.scale(.075*k)).add(0,-.09*k,0);
                Vec3 dir=outward.add(0,.38-.13*k,0).add(back.scale(.16+.035*k));
                sheet(c,k%3==0?PLANE:CRANE,hinge,f,dir,.52+.025*k,(1.35-.065*k)*unfold,
                        side*(flap+(1-unfold)*1.10+.025*Math.sin(t*.17+k)),k%2==0?PAPER:SHADE,(float)e);
            }
        }
        c.ribbon4(LINE,CanonFx4.Mode.ALPHA,line(root,root.add(f.scale(-1.6)),3),x->.05,x->.25*e*(1-x),RED,1,0);
    }
    /** First/last cells only. The ordinary world renderer owns the full build. */
    private static void placed(Canvas c, View v, double e) {
        var level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) return;
        var first = net.minecraft.core.BlockPos.containing(v.origin());
        var last = net.minecraft.core.BlockPos.containing(v.endpoint());
        for (var cell : first.equals(last) ? java.util.List.of(first) : java.util.List.of(first, last)) {
            if (!level.hasChunkAt(cell)
                    || !(level.getBlockEntity(cell) instanceof cn.academy.port.canon.CanonPaperContent.PaperEntity)) continue;
            var shape = level.getBlockState(cell).getShape(level, cell);
            if (shape.isEmpty()) continue;
            var bounds = shape.bounds();
            double fade = e * (1 - clamp01((v.age() - 8) / 8));
            if (fade <= .01) continue;
            Vec3 axis = new Vec3(1, 0, 0), forward = new Vec3(0, 0, 1);
            double width = Math.min(.34, bounds.getXsize() * .72);
            double length = Math.min(.22, bounds.getZsize() * .43);
            Vec3 hinge = new Vec3(cell.getX() + (bounds.minX + bounds.maxX) * .5,
                    cell.getY() + bounds.maxY + .018, cell.getZ() + (bounds.minZ + bounds.maxZ) * .5);
            double fold = (1 - easeOut(clamp01(v.age() / 7))) * 1.2;
            sheet(c, BOOK, hinge, axis, forward, width, length, -fold, PAPER, (float) fade);
            sheet(c, BOOK, hinge, axis, forward.scale(-1), width, length, fold, SHADE, (float) fade);
        }
    }

    /** A few folded sheets peel down the accepted origin-to-contact lane and fold back at impact. */
    private static void arrayHit(Canvas c, View v, double e) {
        Vec3 from = v.origin(), to = v.endpoint(), delta = to.subtract(from);
        double distance = delta.length();
        if (distance < .02) return;
        Vec3 axis = delta.scale(1 / distance), side = rightOf(axis);
        double travel = Math.max(1, Math.min(4, v.life() * .4));
        for (int k = 0; k < 3; k++) {
            double age = v.age() - k * .52;
            if (age < 0) continue;
            double t = easeIn(clamp01(age / travel));
            double f = Math.sin(Math.PI * t);
            Vec3 hinge = from.add(delta.scale(t)).add(side.scale((k - 1) * .28 * f));
            double size = Math.min(.33, distance * .16);
            double tail = Math.min(size, distance * t);
            if (tail > .001) {
                float alpha = (float) (e * (1 - .55 * clamp01((age - travel) / 5)));
                sheet(c, PLANE, hinge, side, axis.scale(-1), size, tail, .46 * f, PAPER, alpha);
                sheet(c, TRIANGLE, hinge, side, axis.scale(-1), size * .55, tail * .8, -.46 * f, SHADE, alpha);
            }
        }
    }

    private static void array(Canvas c,View v,double e,double t,int tier){
        // A storm of sheets circles the caster, then peels off toward the targets.
        Vec3 centre=v.anchor().add(0,1,0),b=v.endpoint();int n=tier>=5?28:18;double strike=clamp01((life01(v)-.45)/.4);
        for(int k=0;k<n;k++){double ang=t*.12+k*6.283/n,rad=1.3+.4*Math.sin(k*1.7),y=-.6+1.6*rnd(k);
            Vec3 orbit=centre.add(Math.cos(ang)*rad,y+.2*Math.sin(t*.2+k),Math.sin(ang)*rad);
            double go=k%2==0?easeIn(clamp01(strike*1.3-rnd(k+3)*.3)):0;Vec3 at=orbit.add(b.subtract(orbit).scale(go));
            Vec3 tang=new Vec3(-Math.sin(ang),0,Math.cos(ang));
            sheet(c,k%4==0?PLANE:k%4==1?TRIANGLE:k%4==2?SQUARE:FAN,at,tang,new Vec3(0,1,0),.24,.22,t*.3+k,PAPER,(float)e);}
    }
}
