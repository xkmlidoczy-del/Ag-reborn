package cn.academy.port.qa;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonPoseClient;
import cn.academy.port.client.canon.CanonPoseMath;
import com.google.gson.JsonObject;
import com.mojang.math.Axis;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Matrix4f;

/** QA-only render-stage receipts; local previews never establish the server pose snapshot. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonFormalPoseQa {
    private static final boolean OFFENSE=Boolean.getBoolean("academy.qa.offense.pose");
    private static final String ROLE=System.getProperty("academy.qa.canon.role",OFFENSE?"offense":"");
    private static final String RUN=System.getProperty("academy.qa.canon.run","");
    private static final boolean ENABLED=Boolean.getBoolean("academy.qa.showcase")&&(ROLE.equals("host")||ROLE.equals("observer")||OFFENSE);
    private static final Path ROOT=Path.of(System.getProperty("academy.qa.canon.root",".")).toAbsolutePath().normalize();
    private static final Set<String> SEEN=new HashSet<>();
    private static final ArrayDeque<String> SHOTS=new ArrayDeque<>();
    private static Matrix4f handBefore;
    private static CanonPoseClient.Snapshot handSnapshot;
    private static boolean right;
    private CanonFormalPoseQa() {}
    static void captureVfx(CanonVfx.Visual v,boolean local){
        if(!ENABLED||v==null)return;String key="vfx_"+v.skill()+"_"+v.phase().name().toLowerCase()+"_"+(local?"local":"remote");
        if(SEEN.add(key)&&!OFFENSE)SHOTS.add("canon_"+ROLE+"_"+key+".png");
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void handBefore(RenderHandEvent e){
        handBefore=null;handSnapshot=null;if(!ENABLED||e.getHand()!=InteractionHand.MAIN_HAND)return;
        var mc=Minecraft.getInstance();if(mc.player==null)return;
        var snap=CanonPoseClient.snapshot(mc.player,e.getPartialTick());if(snap==null)return;
        handBefore=new Matrix4f(e.getPoseStack().last().pose());handSnapshot=snap;right=mc.player.getMainArm()==HumanoidArm.RIGHT;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void handAfter(RenderHandEvent e){
        if(!ENABLED||e.getHand()!=InteractionHand.MAIN_HAND||handBefore==null||handSnapshot==null)return;
        var pose=CanonPoseMath.sample(handSnapshot.category(),handSnapshot.skill(),handSnapshot.age(),handSnapshot.duration(),right,handSnapshot.impact());
        var expected=new Matrix4f(handBefore).translate((float)pose.handX(),(float)pose.handY(),(float)pose.handZ())
                .rotate(Axis.XP.rotationDegrees((float)((right?pose.rightX():pose.leftX())*14)))
                .rotate(Axis.YP.rotationDegrees((float)((right?pose.rightY():pose.leftY())*12)))
                .rotate(Axis.ZP.rotationDegrees((float)((right?pose.rightZ():pose.leftZ())*12)));
        float[] a=expected.get(new float[16]),b=e.getPoseStack().last().pose().get(new float[16]);
        for(int i=0;i<16;i++)if(!Float.isFinite(b[i])||Math.abs(a[i]-b[i])>1e-4)return;
        var mc=Minecraft.getInstance();if(mc.player!=null)record("first-person-hand",handSnapshot,mc.player.getUUID().toString(),true);
    }
    @SubscribeEvent public static void player(RenderPlayerEvent.Post e){
        if(!ENABLED||e.getRenderer().getModel().attackTime>0)return;
        var mc=Minecraft.getInstance();var actor=e.getEntity();var snap=CanonPoseClient.snapshot(actor,e.getPartialTick());
        if(snap==null||CanonPoseMath.envelope(snap.age(),snap.duration())<.99)return;
        var pose=CanonPoseMath.sample(snap.category(),snap.skill(),snap.age(),snap.duration(),actor.getMainArm()==HumanoidArm.RIGHT,snap.impact());
        var model=e.getRenderer().getModel();
        if(Math.abs(model.rightArm.xRot-pose.rightX())>.025||Math.abs(model.rightArm.yRot-pose.rightY())>.025
                ||Math.abs(model.rightArm.zRot-pose.rightZ())>.025||Math.abs(model.leftArm.xRot-pose.leftX())>.025
                ||Math.abs(model.leftArm.yRot-pose.leftY())>.025||Math.abs(model.leftArm.zRot-pose.leftZ())>.025)return;
        record("third-person-model",snap,actor.getUUID().toString(),actor==mc.player);
    }
    private static void record(String kind,CanonPoseClient.Snapshot snap,String owner,boolean local){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||!RUN.matches("[a-zA-Z0-9_-]{8,64}")
                ||!mc.gameDirectory.toPath().toAbsolutePath().normalize().startsWith(ROOT))return;
        String key=kind+":"+snap.skill()+":"+owner+(OFFENSE?":"+snap.impact():"");if(!SEEN.add(key))return;
        var data=new JsonObject();data.addProperty("run",RUN);data.addProperty("role",ROLE);data.addProperty("event",kind);
        data.addProperty("skill",snap.skill());data.addProperty("owner",owner);data.addProperty("local",local);
        data.addProperty("age",snap.age());data.addProperty("tick",mc.level.getGameTime());
        data.addProperty("impact",snap.impact());
        try{Files.createDirectories(ROOT);Files.writeString(ROOT.resolve(ROLE+"-pose-receipts.jsonl"),data+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(java.io.IOException ex){throw new IllegalStateException(ex);}
        if(!OFFENSE)SHOTS.add("canon_"+ROLE+"_"+kind+"_"+snap.skill()+"_"+(local?"local":"remote")+".png");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!ENABLED||SHOTS.isEmpty())return;var mc=Minecraft.getInstance();if(mc.level==null)return;
        String name=SHOTS.remove();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),ignored->{});
    }
}
