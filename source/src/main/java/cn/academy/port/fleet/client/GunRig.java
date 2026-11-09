package cn.academy.port.fleet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Skin-aware first-person arm adapter, driven by the exported gun anchors.
 * Left-handed callers mirror the complete gun pose, not just its translation.
 * This is a procedural runtime rig; the Blender clips are not decoded here.
 */
public final class GunRig {
    private GunRig() {}
    public static float gripY(int variant) { return GunAnchors.forVariant(variant).grip().y(); }
    public static float gripZ(int variant) { return GunAnchors.forVariant(variant).grip().z(); }
    /** Backward-compatible normalised value, now shared with LabMesh. */
    public static float cellDrop(float progress) { return ReloadTimeline.sample(progress, false).magazineDrop(); }

    public static void draw(PoseStack ps, MultiBufferSource buffers, int light,
                            Matrix4f basePose, int variant, float reload) {
        draw(ps, buffers, light, basePose, variant, reload, false);
    }

    public static void draw(PoseStack ps, MultiBufferSource buffers, int light,
                            Matrix4f basePose, int variant, float reload, boolean emptyReload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isInvisible()) return;
        GunAnchors.Profile profile = GunAnchors.forVariant(variant);
        ReloadTimeline.Pose motion = ReloadTimeline.sample(reload, emptyReload);
        boolean mainRight = mc.player.getMainArm() == HumanoidArm.RIGHT;
        boolean slim = mc.player.getSkin().model().toString().equalsIgnoreCase("slim");
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(mc.player.getSkin().texture()));
        // Current gun coordinates -> eye coordinates is base^-1 * current.
        // Its inverse brings fixed eye-space shoulder endpoints into the rig.
        Matrix4f viewToGun = new Matrix4f(ps.last().pose()).invert().mul(basePose);
        float side = mainRight ? 1 : -1;
        Vector3f mainShoulder = viewToGun.transformPosition(side * .36f, -.51f, -.20f, new Vector3f());
        Vector3f otherShoulder = viewToGun.transformPosition(-side * .36f, -.51f, -.20f, new Vector3f());
        Vector3f mainPole = viewToGun.transformDirection(side * .25f, -.85f, .20f, new Vector3f());
        Vector3f otherPole = viewToGun.transformDirection(-side * .25f, -.85f, .20f, new Vector3f());

        // These are authored contact targets, already offset from the centre
        // plane. Adding the old rig's extra offsets would move hands away from
        // the repaired grips and disagree with the Blender reference targets.
        Vector3f mainPalm = vector(profile.grip());
        Vector3f otherPalm = vector(profile.support());
        Vector3f magPalm = magazineContact(profile, motion);
        otherPalm.lerp(magPalm, motion.handToMagazine());
        Vector3f chargePalm = vector(profile.charge()).add(0, 0, -profile.slideTravel() * motion.charge());
        otherPalm.lerp(chargePalm, motion.handToCharge());
        float magAngle = profile.magazineTiltDegrees() * motion.magazineTilt();
        float mainTilt = -12;
        float otherTilt = lerp(65, magAngle, motion.handToMagazine());
        otherTilt = lerp(otherTilt, 10, motion.handToCharge());
        Vector3f mainWrist = wrist(mainPalm, mainTilt);
        Vector3f otherWrist = wrist(otherPalm, otherTilt);
        arm(ps, out, light, mainShoulder, mainWrist, mainPole, mainRight, slim);
        arm(ps, out, light, otherShoulder, otherWrist, otherPole, !mainRight, slim);
        palm(ps, out, light, mainPalm, 1, mainRight, slim, mainTilt, 0, true);
        palm(ps, out, light, otherPalm, -1, !mainRight, slim, otherTilt, 0, false);
    }

    /** G03 hand targets and orientations are sampled from the authored Blender hierarchy. */
    public static void drawContactAuthored(PoseStack ps,MultiBufferSource buffers,int light,Matrix4f basePose,G03Rig rig,G03Rig.Frame frame){
        drawContactAuthored(ps,buffers,light,basePose,rig,frame,false);
    }
    public static void drawDrivingAuthored(PoseStack ps,MultiBufferSource buffers,int light,Matrix4f basePose,G03Rig rig,G03Rig.Frame frame){
        drawContactAuthored(ps,buffers,light,basePose,rig,frame,true);
    }
    private static void drawContactAuthored(PoseStack ps,MultiBufferSource buffers,int light,Matrix4f basePose,G03Rig rig,G03Rig.Frame frame,boolean driving){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.player.isInvisible())return;
        boolean right=driving||mc.player.getMainArm()==HumanoidArm.RIGHT;
        boolean slim=QaView.forceSlim>=0?QaView.forceSlim==1:mc.player.getSkin().model().toString().equalsIgnoreCase("slim");
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(mc.player.getSkin().texture()));
        Matrix4f inverse=new Matrix4f(ps.last().pose()).invert().mul(basePose);float side=right?1:-1;
        for(String hand:new String[]{"right","left"}){
            boolean main=hand.equals("right"),skinRight=main?right:!right;float shoulderSide=main?side:-side;
            Vector3f palm=rig.point(frame,hand+"_hand");var rotation=rig.rotation(frame,hand+"_hand");
            Vector3f shoulder=inverse.transformPosition(shoulderSide*.24f,-.38f,-.22f,new Vector3f());
            Vector3f target=new Vector3f(0,-.035f,0).rotate(rotation).add(palm);
            Vector3f pole=inverse.transformDirection(shoulderSide*.2f,-.9f,.05f,new Vector3f()).add(rig.point(frame,hand+"_elbow"));
            authoredArm(ps,out,light,shoulder,target,pole,skinRight,slim);
            contactPalm(ps,out,light,palm,rotation,skinRight,slim);
            contactDigits(ps,out,light,rig,frame,hand,skinRight,slim);
        }
    }
    public static void drawDriverThirdArms(PoseStack ps,MultiBufferSource buffers,int light,net.minecraft.client.player.AbstractClientPlayer player,G03Rig rig,G03Rig.Frame f,Matrix4f modelToRig){
        boolean slim=player.getSkin().model().toString().equalsIgnoreCase("slim");var out=buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkin().texture()));
        for(String hand:new String[]{"right","left"}){
            boolean right=hand.equals("right");float side=right?-1:1;
            Vector3f shoulder=modelToRig.transformPosition(side*.3125f,.125f,0,new Vector3f());
            Vector3f palm=rig.point(f,hand+"_hand");var rotation=rig.rotation(f,hand+"_hand");var wrist=new Vector3f(0,-.035f,0).rotate(rotation).add(palm);
            Vector3f pole=modelToRig.transformDirection(side*.25f,.8f,.10f,new Vector3f());var solved=TwoBoneIk.solve(point(shoulder),point(wrist),point(pole),.37f,.37f);int row=right?20:52;
            rod(ps,out,light,vector(solved.shoulder()),vector(solved.elbow()),slim?.055f:.070f,right,slim,row,row+6);rod(ps,out,light,vector(solved.elbow()),vector(solved.wrist()),slim?.045f:.058f,right,slim,row+6,row+11);
            contactPalm(ps,out,light,palm,rotation,right,slim);contactDigits(ps,out,light,rig,f,hand,right,slim);
        }
    }
    public static void drawDriverLegs(PoseStack ps,MultiBufferSource buffers,int light,net.minecraft.client.player.AbstractClientPlayer player,Matrix4f modelToVehicle,cn.academy.port.fleet.VehicleConfig cfg){
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkin().texture()));
        for(int i=0;i<2;i++){
            float side=i==0?-1:1;var hip=modelToVehicle.transformPosition(side*.11875f,.75f,0,new Vector3f());var pedal=cfg.pedals.get(Math.min(i,cfg.pedals.size()-1));
            var ankle=new Vector3f((float)pedal.x,(float)pedal.y+.065f,(float)pedal.z-.10f);var solved=TwoBoneIk.solve(point(hip),point(ankle),new TwoBoneIk.Point(0,.1f,1),.39f,.39f);
            legRod(ps,out,light,vector(solved.shoulder()),vector(solved.elbow()),.085f,i==0);legRod(ps,out,light,vector(solved.elbow()),vector(solved.wrist()),.075f,i==0);
            float u=(i==0?4:20)/64f,v=(i==0?30:62)/64f;box(ps,out,light,ankle.x-.065f,(float)pedal.y,ankle.z-.03f,ankle.x+.065f,(float)pedal.y+.08f,ankle.z+.16f,u,v,u+4/64f,v+2/64f);
        }
    }
    private static void legRod(PoseStack ps,VertexConsumer out,int light,Vector3f a,Vector3f b,float radius,boolean right){
        var dir=new Vector3f(b).sub(a).normalize();var ref=Math.abs(dir.y)>.9f?new Vector3f(0,0,1):new Vector3f(0,1,0);var x=new Vector3f(dir).cross(ref).normalize().mul(radius);var y=new Vector3f(dir).cross(x).normalize().mul(radius);
        Vector3f[] av={new Vector3f(a).add(x).add(y),new Vector3f(a).sub(x).add(y),new Vector3f(a).sub(x).sub(y),new Vector3f(a).add(x).sub(y)};
        Vector3f[] bv={new Vector3f(b).add(x).add(y),new Vector3f(b).sub(x).add(y),new Vector3f(b).sub(x).sub(y),new Vector3f(b).add(x).sub(y)};int base=right?0:16,row=right?20:52;
        for(int i=0;i<4;i++){int next=(i+1)%4;quad(ps,out,light,av[i],av[next],bv[next],bv[i],(base+4)/64f,(base+8)/64f,row/64f,(row+6)/64f);}
    }
    public static void drawThirdContact(PoseStack ps,MultiBufferSource buffers,int light,net.minecraft.world.entity.player.Player player,
            Vector3f shoulder,Vector3f pole,G03Rig rig,G03Rig.Frame frame){
        if(!(player instanceof net.minecraft.client.player.AbstractClientPlayer cp)||player.isInvisible())return;
        boolean right=player.getMainArm()==HumanoidArm.LEFT,slim=cp.getSkin().model().toString().equalsIgnoreCase("slim");
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(cp.getSkin().texture()));
        drawThirdContactSkin(ps,buffers,light,cp.getSkin().texture(),right,slim,shoulder,pole,rig,frame);
    }
    public static void drawThirdContactSkin(PoseStack ps,MultiBufferSource buffers,int light,net.minecraft.resources.ResourceLocation skin,
            boolean right,boolean slim,Vector3f shoulder,Vector3f pole,G03Rig rig,G03Rig.Frame frame){
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(skin));
        Vector3f palm=rig.point(frame,"left_hand");var rotation=rig.rotation(frame,"left_hand");
        Vector3f wrist=new Vector3f(0,-.035f,0).rotate(rotation).add(palm);
        var solved=TwoBoneIk.solve(point(shoulder),point(wrist),point(pole),.34f,.31f);int row=right?20:52;
        rod(ps,out,light,vector(solved.shoulder()),vector(solved.elbow()),slim?.086f:.112f,right,slim,row,row+6);
        rod(ps,out,light,vector(solved.elbow()),vector(solved.wrist()),slim?.052f:.060f,right,slim,row+6,row+10);
        contactPalm(ps,out,light,palm,rotation,right,slim);contactDigits(ps,out,light,rig,frame,"left",right,slim);
    }
    private static void contactPalm(PoseStack ps,VertexConsumer out,int light,Vector3f palm,org.joml.Quaternionf rotation,boolean right,boolean slim){
        float u=(right?44:36)/64f,v=(right?29:61)/64f,uw=(slim?3:4)/64f;
        ps.pushPose();ps.translate(palm.x,palm.y,palm.z);ps.mulPose(rotation);
        box(ps,out,light,-.0115f,-.034f,-.020f,.0115f,.030f,.022f,u,v,u+uw,v+3/64f);
        ps.popPose();
    }
    private static void contactDigits(PoseStack ps,VertexConsumer out,int light,G03Rig rig,G03Rig.Frame f,String hand,boolean right,boolean slim){
        String[] names=G03FingerPose.DIGITS;float[] radii=G03FingerPose.RADII;
        float u=(right?44:36)/64f,v=(right?29:61)/64f,uw=(slim?3:4)/64f;
        for(int digit=0;digit<names.length;digit++){
            Vector3f[] joints=G03FingerPose.points(rig,f,hand,digit);
            for(int j=0;j<3;j++){
                float radius=radii[digit]*(1-.09f*j);digitRod(ps,out,light,joints[j],joints[j+1],radius,u,v,uw);
                digitJoint(ps,out,light,joints[j],radius,u,v,uw);
            }
            digitJoint(ps,out,light,joints[3],radii[digit]*.82f,u,v,uw);
        }
    }
    private static void digitRod(PoseStack ps,VertexConsumer out,int light,Vector3f a,Vector3f b,float radius,float u,float v,float uw){
        Vector3f axis=new Vector3f(b).sub(a).normalize();Vector3f ref=Math.abs(axis.y)<.95f?new Vector3f(0,1,0):new Vector3f(1,0,0);
        Vector3f x=new Vector3f(axis).cross(ref).normalize().mul(radius),y=new Vector3f(axis).cross(x).normalize().mul(radius);
        for(int i=0;i<8;i++){
            float angle=(float)(i*Math.PI/4),next=(float)((i+1)*Math.PI/4);
            Vector3f r=new Vector3f(x).mul((float)Math.cos(angle)).add(new Vector3f(y).mul((float)Math.sin(angle)));
            Vector3f t=new Vector3f(x).mul((float)Math.cos(next)).add(new Vector3f(y).mul((float)Math.sin(next)));
            quad(ps,out,light,new Vector3f(a).add(r),new Vector3f(a).add(t),new Vector3f(b).add(t),new Vector3f(b).add(r),u,u+uw,v,v+2/64f);
        }
    }
    private static void digitJoint(PoseStack ps,VertexConsumer out,int light,Vector3f c,float r,float u,float v,float uw){
        Vector3f[] ring={new Vector3f(c).add(r,0,0),new Vector3f(c).add(0,0,r),new Vector3f(c).add(-r,0,0),new Vector3f(c).add(0,0,-r)};
        Vector3f top=new Vector3f(c).add(0,r,0),bottom=new Vector3f(c).add(0,-r,0);
        for(int i=0;i<4;i++){
            quad(ps,out,light,ring[i],top,ring[(i+1)%4],ring[(i+1)%4],u,u+uw,v,v+1/64f);
            quad(ps,out,light,ring[(i+1)%4],bottom,ring[i],ring[i],u,u+uw,v,v+1/64f);
        }
    }
    public static void drawAuthored(PoseStack ps, MultiBufferSource buffers, int light, Matrix4f basePose,
            Vector3f mainPalm, org.joml.Quaternionf mainRotation, Vector3f otherPalm, org.joml.Quaternionf otherRotation,
            float mainCurl, float otherCurl, float trigger) {
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.player.isInvisible())return;
        boolean right=mc.player.getMainArm()==HumanoidArm.RIGHT;
        boolean slim=QaView.forceSlim>=0?QaView.forceSlim==1:mc.player.getSkin().model().toString().equalsIgnoreCase("slim");
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(mc.player.getSkin().texture()));
        Matrix4f inverse=new Matrix4f(ps.last().pose()).invert().mul(basePose);float side=right?1:-1;
        Vector3f mainShoulder=inverse.transformPosition(side*.24f,-.38f,-.22f,new Vector3f());
        Vector3f otherShoulder=inverse.transformPosition(-side*.24f,-.38f,-.22f,new Vector3f());
        Vector3f mainWrist=new Vector3f(0,-.035f,0).rotate(mainRotation).add(mainPalm);
        Vector3f otherWrist=new Vector3f(0,-.035f,0).rotate(otherRotation).add(otherPalm);
        authoredArm(ps,out,light,mainShoulder,mainWrist,inverse.transformDirection(side*.2f,-.9f,.05f,new Vector3f()),right,slim);
        authoredArm(ps,out,light,otherShoulder,otherWrist,inverse.transformDirection(-side*.2f,-.9f,.05f,new Vector3f()),!right,slim);
        authoredPalm(ps,out,light,mainPalm,mainRotation,1,right,slim,mainCurl,trigger);
        authoredPalm(ps,out,light,otherPalm,otherRotation,-1,!right,slim,otherCurl,1);
    }
    /** Third-person support arm keeps Minecraft dimensions and follows the same module-contact track. */
    public static void drawThirdSupport(PoseStack ps, MultiBufferSource buffers, int light,
            net.minecraft.world.entity.player.Player player, Vector3f shoulder, Vector3f pole,
            Vector3f palm, org.joml.Quaternionf rotation) {
        if(!(player instanceof net.minecraft.client.player.AbstractClientPlayer cp)||player.isInvisible())return;
        boolean right=player.getMainArm()==HumanoidArm.LEFT;
        boolean slim=cp.getSkin().model().toString().equalsIgnoreCase("slim");
        VertexConsumer out=buffers.getBuffer(RenderType.entityCutoutNoCull(cp.getSkin().texture()));
        Vector3f wrist=new Vector3f(0,-.065f,0).rotate(rotation).add(palm);
        TwoBoneIk.Arm solved=TwoBoneIk.solve(point(shoulder),point(wrist),point(pole),.34f,.31f);
        float radius=slim?.086f:.112f;int row=right?20:52;
        rod(ps,out,light,vector(solved.shoulder()),vector(solved.elbow()),radius,right,slim,row,row+6);
        rod(ps,out,light,vector(solved.elbow()),vector(solved.wrist()),radius*.88f,right,slim,row+6,row+10);
        ps.pushPose();ps.translate(palm.x,palm.y,palm.z);ps.mulPose(rotation);
        float u=(right?44:36)/64f,v=(right?29:61)/64f;
        box(ps,out,light,-.066f,-.065f,-.052f,.066f,.055f,.052f,u,v,u+(slim?3:4)/64f,v+3/64f);
        ps.popPose();
    }
    private static void authoredArm(PoseStack ps,VertexConsumer out,int light,Vector3f shoulder,Vector3f target,Vector3f pole,boolean right,boolean slim){
        TwoBoneIk.Arm a=TwoBoneIk.solve(point(shoulder),point(target),point(pole),.36f,.37f);
        float r=slim?.031f:.036f;int row=right?20:52;
        rod(ps,out,light,vector(a.shoulder()),vector(a.elbow()),r,right,slim,row,row+6);
        rod(ps,out,light,vector(a.elbow()),vector(a.wrist()),r*.86f,right,slim,row+6,row+11);
    }
    private static void authoredPalm(PoseStack ps,VertexConsumer out,int light,Vector3f centre,org.joml.Quaternionf rotation,
            int inward,boolean right,boolean slim,float curl,float trigger){
        float u=(right?44:36)/64f,v=(right?29:61)/64f,uw=(slim?3:4)/64f;
        ps.pushPose();ps.translate(centre.x,centre.y,centre.z);ps.mulPose(rotation);
        box(ps,out,light,-.012f,-.032f,-.014f,.012f,.030f,.013f,u,v,u+uw,v+3/64f);
        float c=Math.max(0,Math.min(1,curl));
        for(int finger=0;finger<3;finger++){
            float y=.012f-finger*.018f;
            fingerBox(ps,out,light,inward,.010f,.032f,y-.0068f,y+.0068f,-.021f,-.009f,u,v,uw);
            fingerBox(ps,out,light,inward,.025f,.038f,y-.0068f,y+.0068f,-.012f,-.010f+.027f*c,u,v,uw);
        }
        // Independently keyed index and a thumb on the opposite side of the grip.
        float k=Math.max(0,Math.min(1,trigger));
        fingerBox(ps,out,light,inward,.010f,.025f,.022f,.034f,-.005f,.048f-.023f*k,u,v,uw);
        if(k>.1f)fingerBox(ps,out,light,inward,.022f,.037f,.022f,.034f,.014f,.027f,u,v,uw);
        fingerBox(ps,out,light,-inward,.008f,.021f,.008f,.028f,.010f,.037f,u,v,uw);
        ps.popPose();
    }

    /** Same pivot -> translation -> rotation as LabMesh's energy-cell branch. */
    private static Vector3f magazineContact(GunAnchors.Profile profile, ReloadTimeline.Pose motion) {
        Vector3f pivot = vector(profile.mag());
        return vector(profile.magGrip()).sub(pivot)
                .rotateX((float) Math.toRadians(profile.magazineTiltDegrees() * motion.magazineTilt()))
                .add(pivot).add(0, -profile.magazineDropDistance() * motion.magazineDrop(), 0);
    }

    public static void drawWheelHands(PoseStack ps, MultiBufferSource buffers, int light, Matrix4f basePose) {
        drawDrivingHands(ps, buffers, light, basePose, .19f, -.06f);
    }

    public static void drawHandlebarHands(PoseStack ps, MultiBufferSource buffers, int light, Matrix4f basePose) {
        drawDrivingHands(ps, buffers, light, basePose, .32f, 0);
    }

    /** Contacts stay in the wheel/handlebar frame, so steer rotation moves both. */
    private static void drawDrivingHands(PoseStack ps, MultiBufferSource buffers, int light,
                                         Matrix4f basePose, float width, float height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isInvisible()) return;
        boolean slim = mc.player.getSkin().model().toString().equalsIgnoreCase("slim");
        VertexConsumer out = buffers.getBuffer(RenderType.entityCutoutNoCull(mc.player.getSkin().texture()));
        Matrix4f viewToWheel = new Matrix4f(ps.last().pose()).invert().mul(basePose);
        for (int side : new int[]{-1, 1}) {
            boolean right = side > 0;
            Vector3f shoulder = viewToWheel.transformPosition(side * .36f, -.51f, -.18f, new Vector3f());
            Vector3f pole = viewToWheel.transformDirection(side * .25f, -.85f, .20f, new Vector3f());
            Vector3f hand = new Vector3f(side * width, height, .012f);
            arm(ps, out, light, shoulder, wrist(hand, 10), pole, right, slim);
            palm(ps, out, light, hand, -side, right, slim, 10, 0, false);
        }
    }

    private static Vector3f vector(GunAnchors.Point point) { return new Vector3f(point.x(), point.y(), point.z()); }
    private static TwoBoneIk.Point point(Vector3f v) { return new TwoBoneIk.Point(v.x, v.y, v.z); }
    private static Vector3f vector(TwoBoneIk.Point point) { return new Vector3f(point.x(), point.y(), point.z()); }
    private static float lerp(float a, float b, float amount) { return a + (b - a) * amount; }
    private static Vector3f wrist(Vector3f palm, float tilt) {
        return new Vector3f(0, -.038f, 0).rotateX((float) Math.toRadians(tilt)).add(palm);
    }

    private static void arm(PoseStack ps, VertexConsumer out, int light, Vector3f shoulder,
                             Vector3f hand, Vector3f pole, boolean right, boolean slim) {
        TwoBoneIk.Arm solved = TwoBoneIk.solve(point(shoulder), point(hand), point(pole), .31f, .29f);
        Vector3f a = vector(solved.shoulder()), elbow = vector(solved.elbow()), wrist = vector(solved.wrist());
        float radius = slim ? .038f : .045f;
        int row = right ? 20 : 52;
        rod(ps, out, light, a, elbow, radius, right, slim, row, row + 6);
        rod(ps, out, light, elbow, wrist, radius * .90f, right, slim, row + 6, row + 11);
    }

    private static void palm(PoseStack ps, VertexConsumer out, int light, Vector3f centre,
                              int inward, boolean right, boolean slim, float tilt, float roll, boolean triggerHand) {
        float u = (right ? 44 : 36) / 64f, v = (right ? 29 : 61) / 64f;
        float uw = (slim ? 3 : 4) / 64f;
        ps.pushPose();
        ps.translate(centre.x, centre.y, centre.z);
        ps.mulPose(Axis.XP.rotationDegrees(tilt));
        ps.mulPose(Axis.ZP.rotationDegrees(roll));
        box(ps, out, light, -.014f, -.038f, -.024f, .014f, .038f, .024f, u, v, u + uw, v + 3 / 64f);
        // Three curled fingers. A small second phalanx turns around the grip,
        // unlike the handover's single large intersecting finger blocks.
        for (int finger = 0; finger < 3; finger++) {
            float y = .018f - finger * .021f;
            fingerBox(ps, out, light, inward, .010f, .037f, y - .008f, y + .008f,
                    -.025f, -.010f, u, v, uw);
            fingerBox(ps, out, light, inward, .030f, .045f, y - .008f, y + .008f,
                    -.012f, .013f, u, v, uw);
        }
        // The resting index is extended along the receiver; it does not clip
        // through the trigger. A firing index clip can be added to the arm asset.
        fingerBox(ps, out, light, inward, .010f, .026f, .027f, .042f,
                triggerHand ? -.006f : -.024f, triggerHand ? .057f : .023f, u, v, uw);
        fingerBox(ps, out, light, -inward, .008f, .023f, .013f, .035f,
                .016f, .046f, u, v, uw);
        ps.popPose();
    }

    private static void fingerBox(PoseStack ps, VertexConsumer out, int light, int side,
                                   float xa, float xb, float y0, float y1, float z0, float z1,
                                   float u, float v, float width) {
        float x0 = side > 0 ? xa : -xb, x1 = side > 0 ? xb : -xa;
        box(ps, out, light, x0, y0, z0, x1, y1, z1, u, v, u + width, v + 2 / 64f);
    }

    private static void rod(PoseStack ps, VertexConsumer out, int light, Vector3f a, Vector3f b,
                              float radius, boolean right, boolean slim, int rowStart, int rowEnd) {
        Vector3f direction = new Vector3f(b).sub(a);
        if (direction.lengthSquared() < 1e-10f) return;
        direction.normalize();
        Vector3f reference = Math.abs(direction.y) > .9f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f x = new Vector3f(direction).cross(reference).normalize().mul(radius);
        Vector3f y = new Vector3f(direction).cross(x).normalize().mul(radius);
        Vector3f[] a4 = {new Vector3f(a).add(x).add(y), new Vector3f(a).sub(x).add(y),
                new Vector3f(a).sub(x).sub(y), new Vector3f(a).add(x).sub(y)};
        Vector3f[] b4 = {new Vector3f(b).add(x).add(y), new Vector3f(b).sub(x).add(y),
                new Vector3f(b).sub(x).sub(y), new Vector3f(b).add(x).sub(y)};
        int base = right ? 40 : 32, width = slim ? 3 : 4;
        int[] starts = {base + 4, base + 4 + width, base + 8 + width, base};
        int[] widths = {width, 4, width, 4};
        for (int face = 0; face < 4; face++) {
            int next = (face + 1) % 4;
            quad(ps, out, light, a4[face], a4[next], b4[next], b4[face],
                    starts[face] / 64f, (starts[face] + widths[face]) / 64f, rowStart / 64f, rowEnd / 64f);
        }
        quad(ps, out, light, a4[3], a4[2], a4[1], a4[0],
                (base + 4) / 64f, (base + 4 + width) / 64f, rowStart / 64f, (rowStart + 1) / 64f);
        quad(ps, out, light, b4[0], b4[1], b4[2], b4[3],
                (base + 4) / 64f, (base + 4 + width) / 64f, (rowEnd - 1) / 64f, rowEnd / 64f);
    }

    private static void box(PoseStack ps, VertexConsumer out, int light, float x0, float y0, float z0,
                              float x1, float y1, float z1, float u0, float v0, float u1, float v1) {
        Vector3f a = new Vector3f(x0, y0, z0), b = new Vector3f(x1, y0, z0);
        Vector3f c = new Vector3f(x1, y1, z0), d = new Vector3f(x0, y1, z0);
        Vector3f e = new Vector3f(x0, y0, z1), f = new Vector3f(x1, y0, z1);
        Vector3f g = new Vector3f(x1, y1, z1), h = new Vector3f(x0, y1, z1);
        Vector3f[] corners = {a, b, c, d, e, f, g, h};
        // The handover's six box faces all pointed inward. No-cull avoids
        // disappearing geometry, but cannot correct those inverted normals.
        for (int face = 0; face < RigGeometry.BOX_FACE_COUNT; face++) {
            quad(ps, out, light, corners[RigGeometry.boxCorner(face, 0)], corners[RigGeometry.boxCorner(face, 1)],
                    corners[RigGeometry.boxCorner(face, 2)], corners[RigGeometry.boxCorner(face, 3)], u0, u1, v0, v1);
        }
    }

    private static void quad(PoseStack ps, VertexConsumer out, int light, Vector3f a, Vector3f b,
                               Vector3f c, Vector3f d, float u0, float u1, float v0, float v1) {
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        if (normal.lengthSquared() < 1e-12f) return;
        normal.normalize();
        var p = ps.last();
        vertex(out, p, a, u0, v0, normal, light); vertex(out, p, b, u1, v0, normal, light);
        vertex(out, p, c, u1, v1, normal, light); vertex(out, p, d, u0, v1, normal, light);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vector3f point,
                                 float u, float v, Vector3f normal, int light) {
        out.addVertex(pose, point.x, point.y, point.z).setColor(255, 255, 255, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, normal.x, normal.y, normal.z);
    }
}
