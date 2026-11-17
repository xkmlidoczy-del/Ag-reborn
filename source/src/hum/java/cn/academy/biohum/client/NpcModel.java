package cn.academy.biohum.client;

import cn.academy.biohum.CastMotion;
import cn.academy.biohum.HumNpc;
import cn.academy.biohum.Skills;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Six vanilla joints, sampled from a separate authored clip for each skill. */
final class NpcModel extends PlayerModel<HumNpc> {
    private final ModelPart[] joints;
    private final boolean slim;
    private float[] activeAngles;

    NpcModel(ModelPart root, boolean slim) {
        super(root, slim);
        this.slim=slim;
        joints = new ModelPart[]{rightArm, leftArm, body, head, rightLeg, leftLeg};
    }

    @Override
    public void setupAnim(HumNpc npc, float walk, float amount, float age, float yaw, float pitch) {
        for (var joint : joints) joint.resetPose();
        hat.visible=!npc.suspended();
        activeAngles=null;
        super.setupAnim(npc, walk, amount, age, yaw, pitch);
        if(npc.suspended()){
            body.xRot=Mth.sin(age*.06f)*.022f;
            head.xRot=.04f+Mth.sin(age*.064f)*.038f;
            head.yRot=Mth.sin(age*.043f)*.072f;
            head.zRot=Mth.cos(age*.052f)*.032f;
            rightArm.xRot=-.04f+Mth.cos(age*.071f)*.07f;
            leftArm.xRot=-.05f+Mth.cos(age*.068f+1)*.07f;
            rightArm.zRot=.095f+Mth.sin(age*.084f)*.038f;
            leftArm.zRot=-.095f+Mth.sin(age*.081f)*.031f;
            rightLeg.xRot=Mth.sin(age*.061f+1.6f)*.045f;
            leftLeg.xRot=Mth.sin(age*.062f)*-.041f;
            rightSleeve.copyFrom(rightArm);leftSleeve.copyFrom(leftArm);
            rightPants.copyFrom(rightLeg);leftPants.copyFrom(leftLeg);jacket.copyFrom(body);hat.copyFrom(head);
            return;
        }
        var skill = Skills.get(npc.technique());
        if (skill == null) return;
        float partial=Mth.clamp(age-npc.tickCount,0,1);
        float tick=npc.signatureAge()+partial;
        boolean aborted=npc.abortAge()>=0;
        float poseAt=aborted?Math.max(0,npc.abortFrame()):tick;
        float blend=CastMotion.blend(skill,poseAt);
        if(aborted)blend*=1-Mth.smoothstep(Mth.clamp((npc.abortAge()+partial)/8f,0,1));
        float[] angles=CastMotion.aimed(npc,skill,poseAt);
        float facing=Mth.wrapDegrees(npc.castYaw()-npc.yBodyRot);
        float lookYaw=Mth.clamp(yaw-facing,-18,18)*Mth.DEG_TO_RAD;
        float lookPitch=Mth.clamp(pitch,-12,12)*Mth.DEG_TO_RAD;
        var anchor=CastMotion.style(skill).anchor();
        boolean facialAnchor=anchor==CastMotion.Anchor.MOUTH || anchor==CastMotion.Anchor.EYES;
        for (int i = 0; i < joints.length; i++) {
            var joint = joints[i];
            // Cast poses carry the hands and shoulders. Navigation can keep driving the feet.
            float weight=i<4?blend:blend*(1-.88f*Mth.clamp(amount*2.3f,0,1));
            joint.xRot=Mth.lerp(weight,joint.xRot,angles[i*3]+(i==3&&!facialAnchor?lookPitch*.25f:0));
            joint.yRot=Mth.lerp(weight,joint.yRot,angles[i*3+1]+(i==3&&!facialAnchor?lookYaw*.3f:0));
            joint.zRot=Mth.lerp(weight,joint.zRot,angles[i*3+2]);
            angles[i*3]=joint.xRot;
            angles[i*3+1]=joint.yRot;
            angles[i*3+2]=joint.zRot;
        }
        activeAngles=angles;
        SkillFx.recordPose(npc,body.zRot);
        move(rightArm,CastMotion.shoulder(angles,slim,true));
        move(leftArm,CastMotion.shoulder(angles,slim,false));
        move(head,CastMotion.upperBody(angles,Vec3.ZERO));
        move(body,CastMotion.upperBody(angles,Vec3.ZERO));
        rightSleeve.copyFrom(rightArm);
        leftSleeve.copyFrom(leftArm);
        rightPants.copyFrom(rightLeg);
        leftPants.copyFrom(leftLeg);
        jacket.copyFrom(body);
        hat.copyFrom(head);
    }

    Vec3 anchor(CastMotion.Anchor anchor) {
        if(activeAngles==null)return Vec3.ZERO;
        return CastMotion.anchor(anchor,activeAngles,slim);
    }

    private static void move(ModelPart part,Vec3 point) {
        part.setPos((float)point.x*16,(float)point.y*16,(float)point.z*16);
    }
}
