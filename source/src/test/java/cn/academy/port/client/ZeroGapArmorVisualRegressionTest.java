package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
import cn.academy.port.client.ZeroGapArmorState.Frame;
import cn.academy.port.client.ZeroGapArmorState.Identity;
import static cn.academy.port.client.ZeroGapArmorState.*;
import static cn.academy.port.client.FoldedMirrorMesh.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Engine-free tests of exact six-pane presentation, authoritative gaps and replay-safe local contacts. */
public final class ZeroGapArmorVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER=new UUID(252,1);
    private static final Identity ID=new Identity(8,OWNER,2,3,10);
    private static final Vector FEET=new Vector(0,64,0),LOOK=new Vector(0,0,1);
    private static void check(boolean condition,String message) { checks++;if(!condition)throw new AssertionError(message); }
    private static int selected(Vector feet,double yaw,int phaseTick) { return ZeroGapArmorGeometry.capture(feet,yaw,phaseTick).activeMask(); }
    private static Frame frame(long sequence,int phase,int physical,int gap) { return frame(ID,sequence,phase,FEET,0,physical,gap,-1,FEET); }
    private static Frame frame(Identity id,long sequence,int phase,Vector feet,double yaw,int physical,int gap,int plate,Vector point) {
        int selected=selected(feet,yaw,physical);
        int live=phase==STARTUP||phase==ENDED||gap>0?0:selected;
        return new Frame(id,sequence,phase,feet,yaw,physical,selected,live,gap,plate,point);
    }
    private static Frame mask(Frame f,int selected,int live) {
        return new Frame(f.id(),f.sequence(),f.phase(),f.feet(),f.yaw(),f.phaseTick(),selected,live,f.breakTicks(),f.plate(),f.point());
    }
    private static Frame hit(long sequence,int phaseTick,int gap,int plate,Vector point) { return frame(ID,sequence,HIT,FEET,0,phaseTick,gap,plate,point); }
    public static void main(String[] args) {
        validity();replay();limits();leases();meshes();contacts();nearClipAndBudget();
        System.out.println("PASS ZeroGapArmorVisualRegressionTest: "+checks+" checks");
    }
    private static void validity() {
        Frame f=frame(1,ACTIVE,0,0);
        check(valid(f),"native active frame accepted");
        for(int physical=0;physical<120;physical++)for(double yaw:new double[]{0,29.99,30,90,180,270,359.999}) {
            Frame oriented=frame(ID,1,ACTIVE,FEET,yaw,physical,0,-1,FEET);
            check(valid(oriented),"all native phases/facing masks validate");
            for(int mask=0;mask<64;mask++)check(valid(mask(oriented,oriented.selectedMask(),mask))==((mask&~oriented.selectedMask())==0),"only funded subset of facing selection accepted");
        }
        check(!valid(mask(f,7,7)),"three arbitrary bits cannot replace geometry-selected plates");
        check(!valid(mask(f,63,63)),"all-six protection is refused");
        check(!valid(mask(frame(1,STARTUP,0,0),f.selectedMask(),f.selectedMask())),"startup cannot claim any live plate");
        check(!valid(mask(frame(1,ACTIVE,0,4),f.selectedMask(),f.selectedMask())),"gap cannot claim any live plate");
        check(!valid(mask(frame(1,ENDED,0,0),f.selectedMask(),f.selectedMask())),"terminal cannot claim live plate");
        check(!valid(new Frame(ID,1,ACTIVE,FEET,Double.NaN,0,f.selectedMask(),0,0,-1,FEET)),"NaN yaw refused");
        check(!valid(new Frame(ID,1,ACTIVE,FEET,360,0,f.selectedMask(),0,0,-1,FEET)),"noncanonical yaw refused");
        check(!valid(new Frame(ID,1,ACTIVE,new Vector(30_000_000,64,0),0,0,f.selectedMask(),0,0,-1,new Vector(30_000_000,64,0))),"world-edge slab overflow returns false without throwing");
        check(!valid(new Frame(ID,1,ACTIVE,FEET,0,120,f.selectedMask(),0,0,-1,FEET)),"counter cannot wrap malformed packet locally");
        check(!valid(new Frame(ID,1,ACTIVE,FEET,0,0,f.selectedMask(),0,5,-1,FEET)),"out-of-range break debt refused");
        check(!valid(new Frame(ID,1,ACTIVE,FEET,0,0,f.selectedMask(),0,0,0,FEET)),"ordinary keepalive cannot invent hit");
        check(!valid(new Frame(ID,1,HIT,FEET,0,0,f.selectedMask(),0,0,6,FEET)),"seventh plate refused");
        check(!valid(new Frame(ID,1,HIT,FEET,0,0,f.selectedMask(),0,0,0,new Vector(Double.NaN,64,0))),"NaN contact refused");
        check(valid(hit(2,0,4,0,new Vector(20,64,20))),"historical finite crossing remains valid even after current plate moves");
        check(eligible(5,true,true)&&!eligible(4,true,true)&&!eligible(6,true,true)&&!eligible(5,false,true),"LV5 and learned parent gates");
    }
    private static void replay() {
        var state=new ZeroGapArmorState();var startup=frame(1,STARTUP,119,0);
        check(state.accept(startup,true),"warning receipt opens context");
        check(!state.accept(startup,true),"duplicate sequence refused");
        check(state.accept(frame(2,ACTIVE,0,0),true),"native wrap from119to0 accepted");
        check(!state.accept(frame(3,STARTUP,1,0),true),"one activation cannot regress to startup");
        check(state.accept(frame(4,ACTIVE,1,4),true),"global break receipt accepted");
        check(state.current(OWNER).liveMask()==0,"post-break body mask zero immediately");
        check(state.accept(frame(5,ENDED,1,4),true)&&state.size()==0,"ended receipt retires context");
        check(!state.accept(frame(6,ACTIVE,2,0),true),"terminal watermark blocks delayed revival");
        Identity next=new Identity(8,OWNER,2,4,11);
        Frame newer=frame(next,1,STARTUP,FEET,0,2,3,-1,FEET);
        check(state.accept(newer,true),"new authenticated edge does not reset persisted phase/debt");
        check(!state.accept(frame(new Identity(9,OWNER,2,4,11),2,ACTIVE,FEET,0,3,0,-1,FEET),true),"entity-id replacement cannot borrow activation identity");
        check(!state.accept(frame(new Identity(8,OWNER,2,5,11),2,ACTIVE,FEET,0,3,0,-1,FEET),true),"new input cannot reuse old token");
        state.clear();check(!state.accept(frame(next,2,ACTIVE,FEET,0,3,0,-1,FEET),true),"world clear retains same-session replay tombstone");
        state.resetSession();check(state.accept(startup,true),"fresh connection resets old-session fences");
        state.resetSession();state.cancel(2,3);check(!state.accept(startup,true),"local abort fences delayed startup");
        check(state.accept(newer,true),"later held edge survives older cancellation");
    }
    private static void limits() {
        var state=new ZeroGapArmorState();
        for(int i=1;i<=MAX_CONTEXTS;i++)check(state.accept(frame(new Identity(i,new UUID(252,i),2,3,10),1,ACTIVE,FEET,0,0,0,-1,FEET),false),"bounded owner accepted");
        check(!state.accept(frame(new Identity(65,new UUID(252,65),2,3,10),1,ACTIVE,FEET,0,0,0,-1,FEET),false),"65th owner refused");
        state.resetSession();
        for(int i=1;i<=MAX_WATERMARKS;i++) {
            Frame f=frame(new Identity(i,new UUID(252,i),2,3,10),1,ACTIVE,FEET,0,0,0,-1,FEET);
            check(state.accept(f,false),"bounded watermark accepted");state.retire(f.id());
        }
        check(state.watermarkCount()==MAX_WATERMARKS&&!state.accept(frame(new Identity(2048,new UUID(252,2048),2,3,10),1,ACTIVE,FEET,0,0,0,-1,FEET),false),"full replay fence refuses instead of evicting retired identity");
    }
    private static void leases() {
        var lease=new Lease();Frame gap=frame(1,ACTIVE,47,4);lease.received(gap);
        for(int i=0;i<100;i++)check(lease.tick(true,true)&&lease.quiet()==0,"pause freezes cosmetics");
        for(int i=0;i<QUIET_TICKS;i++)lease.tick(false,true);
        check(lease.ending()&&!lease.live(),"lost native receipts expire protection");
        check(gap.phaseTick()==47&&gap.breakTicks()==4&&gap.liveMask()==0,"cosmetic clock cannot heal debt or rotate physical phase");
        lease.received(frame(2,ACTIVE,48,0));check(lease.ending(),"expired marker cannot renew in place");
        for(int i=1;i<FADE_TICKS;i++)check(lease.tick(false,true),"bounded cosmetic fade persists");
        check(!lease.tick(false,true),"fourth fade tick removes marker");
        lease=new Lease();lease.received(frame(1,STARTUP,0,0));check(!lease.tick(true,false),"owner invalidation removes marker while paused");
    }
    private static void meshes() {
        for(int phaseTick=0;phaseTick<120;phaseTick+=3)for(double yaw:new double[]{0,91,179.9,270}) {
            Frame f=frame(ID,1,ACTIVE,FEET,yaw,phaseTick,0,-1,FEET);var snapshot=ZeroGapArmorMesh.snapshot(f);
            int fills=0,sides=0;
            for(int index=0;index<6;index++) {
                var faces=ZeroGapArmorMesh.plate(f,index,-1,7);
                boolean live=(f.liveMask()&1<<index)!=0;
                check(faces.stream().filter(face->face.kind()==FILL).count()==(live?1:0),"only selected live rectangle receives artwork");
                if(!live)check(faces.stream().allMatch(face->face.kind()==GHOST&&face.alpha()<=35),"inactive three remain unmistakably dim unfilled outlines");
                for(var face:faces) {
                    if(face.kind()==FILL){fills++;check(face.texture()==SHIELD_TEXTURE&&face.color()==0xFFFFFF&&face.alpha()==210,"original shield source modulation preserved");}
                    if(face.kind()==SIDE)sides++;
                    for(Vector vertex:face.vertices()) {
                        check(ZeroGapArmorMesh.contains(snapshot.plates().get(index),vertex),"all visible geometry remains within physical .9x1.3x.04 slab");
                        check(vertex.subtract(FEET).length()<=3,"entire effect remains within three meters of feet");
                    }
                }
                var laterMaterial=ZeroGapArmorMesh.plate(f,index,-1,41);
                check(faces.size()==laterMaterial.size(),"cosmetic shimmer cannot add physical geometry");
                for(int j=0;j<faces.size();j++)check(faces.get(j).vertices().equals(laterMaterial.get(j).vertices()),"material time cannot extrapolate rotation");
            }
            check(fills==3&&sides==12,"exactly three textured finite volumes among six numbered panes");
        }
        Frame active=frame(1,ACTIVE,0,0),startup=frame(1,STARTUP,0,0),gap=frame(1,ACTIVE,0,4);
        var start=ZeroGapArmorMesh.plate(startup,0,-1,0);var broken=ZeroGapArmorMesh.plate(gap,0,-1,0);
        check(start.stream().allMatch(f->f.kind()==GHOST)&&broken.stream().allMatch(f->f.kind()==GHOST),"startup and gap never produce filled wall or side volumes");
        check(!start.equals(broken)&&start.getFirst().color()!=broken.getFirst().color(),"startup dashes and break brackets are visibly distinct");
        for(int i=0;i<6;i++) {
            check(ZeroGapArmorMesh.plate(active,i,0,0).stream().allMatch(f->f.kind()==GHOST&&f.alpha()<=32),"retirement is faint outlines only");
            check(ZeroGapArmorMesh.plate(active,i,FADE_TICKS,0).isEmpty(),"expired pane emits no geometry");
        }
        check(ZeroGapArmorMesh.SPAWNED_PARTICLES==0,"hard zero particle budget");
    }
    private static void contacts() {
        var contacts=new Contacts();var plate=ZeroGapArmorGeometry.capture(FEET,0,0).plates().get(0);
        Vector point=ZeroGapArmorGeometry.point(plate,.20,.25);Frame receipt=hit(3,0,0,0,point),current=frame(4,ACTIVE,0,0);
        contacts.received(frame(1,STARTUP,0,0));check(contacts.frame(0)==null,"startup cannot seed absorption");
        contacts.received(receipt);contacts.tick(false);contacts.received(receipt);check(contacts.age(0)==1,"duplicate contact cannot renew cosmetic age");
        contacts.received(current);check(contacts.frame(0)==receipt&&contacts.age(0)==1,"keepalive cannot move or renew contact");
        for(int age=0;age<HIT_TICKS;age++)for(var f:ZeroGapArmorMesh.glint(current,receipt,age,age))for(var p:f.vertices()) {
            check(ZeroGapArmorMesh.contains(plate,p),"glint clips to its actual current rectangle");
            check(p.subtract(point).length()<=ZeroGapArmorMesh.MAX_GLINT_RADIUS,"glint stays localized and never expands into full shield flash");
        }
        check(!ZeroGapArmorMesh.glint(current,receipt,0,0).isEmpty(),"actual current contact is visible");
        Frame breaking=hit(5,0,4,0,point);
        check(ZeroGapArmorMesh.plate(breaking,0,-1,0,breaking,0).stream().noneMatch(f->f.kind()==FILL),"breaking HIT removes body immediately");
        check(!ZeroGapArmorMesh.glint(breaking,breaking,0,0).isEmpty(),"real breaking receipt may leave only local small glint");
        Frame moved=frame(ID,6,ACTIVE,FEET.add(new Vector(0,0,2)),0,0,0,-1,FEET.add(new Vector(0,0,2)));
        check(ZeroGapArmorMesh.glint(moved,receipt,1,1).isEmpty(),"moving away never drags old hit to new pane");
        check(ZeroGapArmorMesh.glint(frame(6,ACTIVE,20,0),receipt,1,1).isEmpty(),"rotated-away numbered pane cannot flash old world crossing");
        check(ZeroGapArmorMesh.glint(current,hit(3,0,0,0,new Vector(20,64,20)),0,0).isEmpty(),"finite remote historical receipt cannot create remote glint");
        check(ZeroGapArmorMesh.glint(frame(6,STARTUP,0,0),receipt,0,0).isEmpty(),"startup never flashes absorption");
        check(ZeroGapArmorMesh.glint(current,receipt,HIT_TICKS,0).isEmpty(),"glint expires independent of body renewals");
        var corner=hit(3,0,0,0,ZeroGapArmorGeometry.point(plate,.45,.65));
        check(!ZeroGapArmorMesh.glint(current,corner,0,0).isEmpty(),"exact corner hit keeps clipped local segment");
        for(var f:ZeroGapArmorMesh.glint(current,corner,0,0))for(var p:f.vertices())check(ZeroGapArmorMesh.contains(plate,p),"corner glint never spans physical gap");
        for(int i=1;i<HIT_TICKS;i++)contacts.tick(false);check(contacts.frame(0)==null,"bounded contact storage expires");
    }
    private static void nearClipAndBudget() {
        Frame frame=frame(1,ACTIVE,0,0);var all=new ArrayList<Face>();
        for(int owner=0;owner<32;owner++)for(int index=0;index<6;index++)all.addAll(ZeroGapArmorMesh.plate(frame,index,-1,0));
        Vector eye=FEET.add(new Vector(0,1.1,0));var budget=new ZeroGapArmorMesh.Budget();
        var sorted=ZeroGapArmorMesh.ordered(all,eye,LOOK,budget);
        check(sorted.size()==ZeroGapArmorMesh.MAX_MESH_QUADS&&budget.used()==sorted.size(),"one post-clipping budget caps all owners/materials");
        int previous=-1;for(var f:sorted){check(layer(f)>=previous,"world layers render volume before contact accent");previous=layer(f);}
        var plate=ZeroGapArmorGeometry.capture(FEET,0,0).plates().get(0);
        Vector nearEye=plate.center().add(new Vector(0,0,-.01));Vector angledLook=new Vector(.6,0,.8);
        int parts=0;
        for(var f:ZeroGapArmorMesh.plate(frame,0,-1,0))for(var clipped:FoldedMirrorMesh.nearClip(f,nearEye,angledLook)) {
            parts++;for(var p:clipped.vertices()) {
                check(p.subtract(nearEye).dot(angledLook)>=NEAR_DEPTH-1e-8,"near clip cannot create camera wedge");
                check(ZeroGapArmorMesh.contains(plate,p),"camera clipping does not resize world slab");
            }
        }
        check(parts>0&&ZeroGapArmorMesh.MAX_VISIBLE_CONTEXTS==4,"grazing clipping exercised and owner cap bounded");
    }
}
