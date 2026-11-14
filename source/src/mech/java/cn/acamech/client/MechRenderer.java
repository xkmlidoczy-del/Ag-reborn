package cn.acamech.client;

import cn.acamech.Chassis;
import cn.acamech.MechEntity;
import cn.acamech.MechMod;
import cn.acamech.RangedWeapon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class MechRenderer extends EntityRenderer<MechEntity> {
    private final BoxRig rig;
    private final LocomotionPose locomotion;
    private final ResourceLocation texture, lightTexture;
    private final RenderType armorLayer;
    private final int energyColor;

    MechRenderer(EntityRendererProvider.Context context, Chassis chassis) {
        super(context);
        rig = new BoxRig(context.getResourceManager(), chassis.id);
        locomotion = new LocomotionPose(rig, chassis);
        texture = MechMod.id("textures/entity/" + chassis.id + ".png");
        lightTexture = MechMod.id("textures/entity/" + chassis.id + "_glow.png");
        armorLayer = MechRenderTypes.alloy(texture, MechMod.id("textures/entity/" + chassis.id + "_finish.png"));
        energyColor = switch (chassis) {
            case ATLAS -> 0x56C8FF;
            case AETHER -> 0x58BCFF;
            case SERAPH -> 0x50FFCC;
        };
        shadowRadius = chassis == Chassis.ATLAS ? 1.22f : .79f;
    }

    @Override
    public void render(MechEntity mech, float yaw, float partial, PoseStack stack, MultiBufferSource buffers, int light) {
        var game = Minecraft.getInstance();
        boolean showBody = game.getCameraEntity() != mech.getFirstPassenger() || !game.options.getCameraType().isFirstPerson();
        var attack = mech.chassis().attack;
        var ranged = mech.chassis().ranged;
        float strikeAge = mech.strikeAge() < 0 ? -1 : mech.strikeAge() + partial;
        float rangedAge = mech.rangedAge() < 0 ? -1 : mech.rangedAge() + partial;
        float strikeSurge = strikeAge < 0 ? 0 : AttackPose.ease(strikeAge / attack.contactTick)
                * (1 - AttackPose.ease((strikeAge - attack.contactTick) / 8));
        float surge = Math.max(strikeSurge, rangedAge < 0 ? 0 : RangedPose.weight(ranged, rangedAge)
                * Mth.clamp(rangedAge / ranged.windup, 0, 1));
        if (showBody || strikeAge >= 0 || rangedAge >= 0) pose(mech, partial, strikeAge, rangedAge);
        if (showBody) rig.articulateHydraulics();
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partial, mech.yRotO, mech.getYRot())));
        stack.scale(-1, -1, 1);
        if (showBody) rig.root().render(stack, buffers.getBuffer(armorLayer), light, OverlayTexture.NO_OVERLAY);
        if (showBody && (mech.charge() > 0 || surge > 0)) {
            rig.bakeGlowPose();
            float activity = mech.auxBlend(partial);
            float charge = Math.min(1f, mech.charge() / 110f);
            int gain = Math.min(255, Math.round(144 + 86 * charge + 25 * activity + 65 * surge));
            int emission = 0xFF000000 | gain << 16 | gain << 8 | gain;
            rig.glow().render(stack, buffers.getBuffer(RenderType.eyes(lightTexture)), LightTexture.FULL_BRIGHT,
                    OverlayTexture.NO_OVERLAY, emission);
            var camera = game.gameRenderer.getMainCamera().getPosition();
            float distance = (float) Math.sqrt(mech.distanceToSqr(camera));
            float distanceFade = Mth.clamp((56 - distance) / 24f, 0, 1);
            if (distanceFade > 0) {
                // Dark ambient light makes the halo stronger, while the full-bright lens is visible in daylight.
                float ambient = Math.max(LightTexture.block(light), LightTexture.sky(light) * game.level.getSkyDarken(1f));
                float strength = (1.17f - .56f * ambient / 15f) * (.55f + .45f * charge) * (1 + .36f * activity);
                rig.renderHalos(stack, buffers.getBuffer(MechRenderTypes.HALO), energyColor,
                        strength * distanceFade * (1 + .67f * surge), mech.tickCount + partial);
            }
        }
        if (showBody && (strikeAge >= 0 || rangedAge >= 0)) {
            ArmorMotion.exhaust(new AttackEffects.Pen(stack.last(), buffers.getBuffer(MechRenderTypes.STRIKE_LIGHT)),
                    rig, mech.chassis(), strikeAge, rangedAge, mech.rangedResult());
        }
        if (strikeAge >= 0) {
            AttackEffects.motion(stack, buffers.getBuffer(MechRenderTypes.STRIKE_LIGHT), rig, attack, strikeAge,
                    age -> pose(mech, partial, (float) age, -1));
        }
        if (rangedAge >= 0 && mech.rangedResult() != RangedWeapon.ABORTED) {
            RangedPose.chargeEffects(new AttackEffects.Pen(stack.last(), buffers.getBuffer(MechRenderTypes.STRIKE_LIGHT)),
                    rig, ranged, rangedAge);
        }
        stack.popPose();
        if (!mech.strikeImpacts().isEmpty()) {
            AttackEffects.impacts(stack, buffers.getBuffer(MechRenderTypes.STRIKE_LIGHT), mech, partial);
        }
        if (showBody) super.render(mech, yaw, partial, stack, buffers, light);
    }

    private void pose(MechEntity mech, float partial, float strikeAge, float rangedAge) {
        rig.reset();
        Chassis chassis = mech.chassis();
        float strikeWeight = strikeAge < 0 ? 0 : AttackPose.ease(strikeAge / 3)
                * AttackPose.ease((chassis.attack.duration - strikeAge) / 5);
        float lock = Math.max(strikeWeight, RangedPose.weight(chassis.ranged, rangedAge));
        float gait = mech.gait(partial) * (1 - .78f * lock);
        float action = mech.auxBlend(partial) * (1 - .92f * lock);
        float brace = chassis == Chassis.ATLAS ? action : 0;
        float flight = chassis == Chassis.SERAPH ? action : 0;
        float speedup = chassis == Chassis.AETHER ? action : 0;
        locomotion.apply(rig, mech, partial, gait, brace, flight, speedup, lock);
        if (strikeAge >= 0) AttackPose.apply(rig, chassis.attack, strikeAge);
        if (rangedAge >= 0) RangedPose.apply(rig, mech, rangedAge);
        ArmorMotion.apply(rig, chassis, strikeAge, rangedAge, mech.strikeResult(), mech.rangedResult());
    }

    @Override public ResourceLocation getTextureLocation(MechEntity mech) { return texture; }
}
