package cn.academy.port.client;

import cn.academy.port.client.VectorCageState.Point;
import cn.academy.port.highpower.core.VectorCageGeometry;
import cn.academy.port.highpower.core.VectorCageRules;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import java.nio.file.Path;

/** Executable receipt, native-only hit, exact geometry, bounded timing and complete-group budget checks. */
public final class VectorCageVisualRegressionTest {
    private static int checks;
    private static void check(boolean c,String m){checks++;if(!c)throw new AssertionError(m);}
    private static final Point CENTER=new Point(20,64,-30), ZERO=VectorCageState.ZERO;
    private static VectorCageState.Identity id(int owner,long input){return new VectorCageState.Identity(owner,new UUID(19,owner+1),71,input,input+100);}
    private static VectorCageState.Frame frame(VectorCageState.Identity id,long seq,int phase,int age,boolean low){
        return new VectorCageState.Frame(id,seq,phase,age,low,CENTER,phase==0?4:VectorCageState.halfWidthAt(Math.min(79,age)),
                phase==0?-1:VectorCageState.pulseAt(age),-1,ZERO,0);
    }
    private static VectorCageState.Frame hit(VectorCageState.Identity id,long seq,int age,int status){
        return new VectorCageState.Frame(id,seq,2,age,false,CENTER,VectorCageState.halfWidthAt(age),VectorCageState.pulseAt(age),0,new Point(16.5,65,-30),status);
    }
    private static VectorCageState.Controller controller(int age,boolean low){return new VectorCageState.Controller(frame(id(1,1),1,1,age,low),true,0,List.of());}
    private static long roles(VectorCageMesh.Shape s,VectorCageMesh.Role r){return s.strokes().stream().filter(p->p.role()==r).count();}
    public static void main(String[] args)throws Exception{
        VectorCageState s=new VectorCageState();var id=id(1,1);
        check(s.accept(frame(id,1,0,0,false),true),"server mark accepted");
        check(!s.accept(frame(id,1,0,0,false),true),"duplicate preview rejected");
        check(s.accept(frame(id,2,0,150,false),true),"preview heartbeat retains immutable mark");
        var changed=new VectorCageState.Frame(id,3,0,151,true,CENTER,4,-1,-1,ZERO,0);
        check(!s.accept(changed,true),"mark-time mode cannot change");
        changed=new VectorCageState.Frame(id,3,0,151,false,new Point(21,64,-30),4,-1,-1,ZERO,0);
        check(!s.accept(changed,true),"mark cannot follow moving view");
        check(s.accept(frame(id,3,1,0,false),true),"confirmation preserves mark identity and resets authoritative phase age");
        check(!s.accept(frame(id,4,0,151,false),true),"active cannot regress to preview");
        check(!s.accept(hit(id,4,6,0),true),"REFUSED cannot fabricate impact");
        check(s.accept(frame(id,4,1,6,false),true),"first actual compression position accepted");
        check(s.accept(hit(id,5,6,1),true),"native DAMAGED receipt produces contact");
        check(!s.accept(hit(id,5,6,1),true),"contact replay rejected");
        check(s.accept(hit(id,6,6,2),true),"native ABSORBED receipt produces distinct contact");
        check(s.controllers().getFirst().contacts().size()==2,"only actual receipts decorate hit points");
        for(int i=0;i<5;i++)s.tick(false,c->true);
        check(s.controllers().getFirst().contacts().isEmpty(),"hit contraction expires without replay from old frame");
        check(s.controllers().getFirst().frame().age()==6,"client clock never advances authoritative pressure age");
        check(s.accept(frame(id,7,3,7,false),true)&&s.controllers().isEmpty(),"explicit end clears immediately");
        check(!s.accept(frame(id,8,1,26,false),true),"late wave cannot revive end");
        check(s.accept(frame(id(1,2),1,0,0,false),true),"fresh token may mark");s.clear();
        check(!s.accept(frame(id(1,2),2,0,1,false),true),"reload retains terminal replay fence");
        s.cancel(71,3);check(!s.accept(frame(id(1,3),1,0,0,false),true),"canceled local identity cannot arrive late");
        check(s.accept(frame(id(2,1),1,1,79,false),false),"remote owner is independent of local input watermark");
        for(int i=0;i<100;i++)s.tick(true,c->true);check(s.controllers().size()==1,"pause freezes bounded visual clock");
        s.tick(false,c->true);check(s.controllers().isEmpty(),"last active tick expires exactly once");
        check(s.accept(frame(id(2,1),2,3,80,false),false),"authoritative end resolves input after cosmetic expiration");
        VectorCageState preview=new VectorCageState();preview.accept(frame(id(3,1),1,0,0,false),false);
        for (int i = 0; i < VectorCageRules.PREVIEW_TICKS - 1; i++) preview.tick(false, c -> true);
        check(preview.controllers().size() == 1, "immutable preview survives through the last configured live tick");
        preview.tick(false, c -> true);
        check(preview.controllers().isEmpty(), "preview expires at the configured tick boundary");
        VectorCageState pool=new VectorCageState();
        for(int i=10;i<14;i++)check(pool.accept(frame(id(i,1),1,1,0,false),false),"four remote controllers fit");
        check(!pool.accept(frame(id(15,1),1,1,0,false),false),"fifth remote refused");
        check(pool.accept(frame(id(16,1),1,1,0,false),true),"local remains visible at capacity");
        check(pool.controllers().size()==4&&pool.controllers().stream().anyMatch(VectorCageState.Controller::local),"hard controller cap preserves local");
        pool.tick(false,c->false);check(pool.controllers().isEmpty(),"death/body/world invalidity clears whole pool");
        VectorCageState orphan=new VectorCageState();check(!orphan.accept(hit(id(20,1),1,6,1),false),"orphan hit cannot conjure controller");
        check(!VectorCageState.valid(hit(id,1,12,1)),"gap cannot claim hit");
        var impossible=new VectorCageState.Frame(id,1,1,6,false,CENTER,4,0,-1,ZERO,0);
        check(!VectorCageState.valid(impossible),"packet cannot claim invented geometry for age");
        impossible=new VectorCageState.Frame(id,1,2,6,false,CENTER,3.375,0,0,new Point(25,65,-30),1);
        check(!VectorCageState.valid(impossible),"hit outside finite field refused");
        int waves=0;VectorCageMesh.Phase previous=VectorCageMesh.Phase.END;Set<Integer> pulses=new HashSet<>();
        for(int age=0;age<80;age++)for(boolean low:new boolean[]{false,true}){
            var c=controller(age,low);var mesh=VectorCageMesh.shape(c);
            check(mesh.vertexCount()<=512&&mesh.vertexCount()*4<=2048,"whole world2048andpercontroller512hard vertex budget");
            check(mesh.strokes().stream().noneMatch(v->v.role()==VectorCageMesh.Role.DAMAGE||v.role()==VectorCageMesh.Role.ABSORBED),"no invented future hit marks");
            check(VectorCageState.halfWidthAt(age)==VectorCageRules.halfWidth(age),"server and client timeline share actual geometry");
            var bounds=VectorCageMesh.bounds(c);
            for(var stroke:mesh.strokes()){
                check(bounds.contains(stroke.from())&&bounds.contains(stroke.to()),"bounds contain all decoration");
                var core=VectorCageMesh.profile(stroke,true);var shoulder=VectorCageMesh.profile(stroke,false);
                check(core.gray()>shoulder.gray()&&core.alpha()>shoulder.alpha()&&shoulder.width()>core.width(),"white core and translucent gray shoulder");
            }
            for(int wall=0;wall<4;wall++){
                var actual=VectorCageGeometry.faceVertices(VectorCageGeometry.frame(new VectorCageGeometry.Point(CENTER.x(),CENTER.y(),CENTER.z())),wall,c.frame().halfWidth())
                        .stream().map(p->new Point(p.x(),p.y(),p.z())).collect(java.util.stream.Collectors.toSet());
                check(actual.equals(Set.copyOf(VectorCageMesh.face(CENTER,wall,c.frame().halfWidth()))),"render face equals actual finite pressure plane");
            }
            check(mesh.panels().isEmpty()==(low||mesh.phase()!=VectorCageMesh.Phase.COMPRESS),"optional transparent planes only compress normal mode");
            for(var p:mesh.panels())check(p.alpha()<=9,"never opaque white pressure wall");
            check(roles(mesh,VectorCageMesh.Role.PRESSURE)==(mesh.phase()==VectorCageMesh.Phase.COMPRESS?12:0),"one complete sparse inward arrow per actual wall only during confirmed compression");
            check(roles(mesh,VectorCageMesh.Role.SPINE)==(!low&&mesh.phase()==VectorCageMesh.Phase.COMPRESS?4:0),"one finite spine per confirmed normal pressure plane; low mode remains open");
            for(var stroke:mesh.strokes())if(stroke.role()==VectorCageMesh.Role.SPINE){
                double x=Math.abs(stroke.from().x()-CENTER.x()),z=Math.abs(stroke.from().z()-CENTER.z());
                check(x==c.frame().halfWidth()&&z==0||z==c.frame().halfWidth()&&x==0,"spine lies on an actual current finite face");
                check(stroke.to().x()==stroke.from().x()&&stroke.to().z()==stroke.from().z()
                        &&stroke.from().y()>=CENTER.y()+.05&&stroke.to().y()<=CENTER.y()+3.05,"spine does not expand wall geometry");
            }
            if(mesh.phase()==VectorCageMesh.Phase.COMPRESS)for(var stroke:mesh.strokes())
                if(stroke.role()==VectorCageMesh.Role.EDGE||stroke.role()==VectorCageMesh.Role.SPINE||stroke.role()==VectorCageMesh.Role.PRESSURE){
                    var material=VectorCageMesh.profile(stroke,true);
                    double pixelsAt12m=material.width()*812/(2*Math.tan(Math.toRadians(35))*12);
                    check(pixelsAt12m>=2.1,"normal-distance bright contour stays above a single pixel at812px height/70degree FOV");
                    check(material.minV()==13f/32&&material.maxV()==18f/32,"bright spine samples actual high-alpha glow center");
                    var shoulder=VectorCageMesh.profile(stroke,false);
                    check(shoulder.width()>=material.width()*3.3&&shoulder.gray()==136&&shoulder.alpha()>=material.alpha()*.59,"gray shoulder creates bounded contrast instead of white filled wall");
                }
            if(!low){if(mesh.phase()==VectorCageMesh.Phase.COMPRESS&&previous!=mesh.phase())waves++;previous=mesh.phase();if(c.frame().pulse()>=0)pulses.add(c.frame().pulse());}
            var fallback=VectorCageMesh.shape(c,low?64:96);
            check(fallback.vertexCount()==(low?64:96)&&fallback.panels().isEmpty(),"complete four-plane core retained with exhausted decoration budget");
            check(VectorCageMesh.shape(c,low?63:95).vertexCount()==0,"never partial false geometry below entire core budget");
        }
        check(waves==4&&pulses.equals(Set.of(0,1,2,3)),"four distinct sweeps with warmup and rests");
        var mark=new VectorCageState.Controller(frame(id,1,0,0,false),true,0,List.of());
        check(VectorCageMesh.shape(mark).phase()==VectorCageMesh.Phase.PREVIEW&&VectorCageMesh.shape(mark).panels().isEmpty(),"preview is immutable footprint only");
        check(VectorCageMesh.shape(new VectorCageState.Controller(frame(id,1,3,79,false),true,0,List.of())).vertexCount()==0,"end removes all decoration immediately");
        for(int status:new int[]{1,2}){
            var receipts=List.of(new VectorCageState.Contact(new Point(17,65,-30),0,status,0),new VectorCageState.Contact(new Point(23,65,-30),1,status,1),
                    new VectorCageState.Contact(new Point(20,65,-33),2,status,2),new VectorCageState.Contact(new Point(20,65,-27),3,status,3));
            var mesh=VectorCageMesh.shape(new VectorCageState.Controller(frame(id,1,1,6,false),true,0,receipts));
            check((roles(mesh,VectorCageMesh.Role.DAMAGE)>0)==(status==1),"damage marks only native damaged");
            check((roles(mesh,VectorCageMesh.Role.ABSORBED)>0)==(status==2),"absorbed ticks only native absorbed");
            check(mesh.vertexCount()<=368&&mesh.vertexCount()*4<=1472,"new plane spines keep maximum native receipt count below unchanged512/2048hard budgets");
        }
        var texture=ImageIO.read(Path.of("src/main/resources/assets/academy/textures/effects/glow_line.png").toFile());
        check(texture.getWidth()==32&&texture.getHeight()==32,"actual verified classic glow texture dimensions");
        for(int y=13;y<18;y++)for(int x=0;x<32;x++)
            check((texture.getRGB(x,y)>>>24)>=232,"new core UV interval has actual bright alpha coverage across every texel");
        check(VectorCageMesh.MAX_PARTICLES==0,"zero particles");
        var camera=new Point(29_999_990,90,-29_999_990);var p=new Point(29_999_990.125,90.5,-29_999_989.75);
        check(VectorCageMesh.relative(p,camera).equals(new Point(.125,.5,.25)),"double subtraction precedes float at world border");
        var line=new VectorCageMesh.Stroke(new Point(0,0,-1),new Point(0,0,2),.1,100,VectorCageMesh.Role.EDGE);
        var clipped=VectorCageMesh.clipNear(line,new Point(0,0,1),.05);
        check(clipped!=null&&clipped.from().z()>=.1299999,"near clipping includes decorative halfwidth");
        check(VectorCageMesh.nearEyeFade(.2)==0&&VectorCageMesh.nearEyeFade(.85)==1,"near-eye fade leaves central world readable");
        System.out.println("VectorCageVisualRegressionTest: "+checks+" checks passed");
    }
}
