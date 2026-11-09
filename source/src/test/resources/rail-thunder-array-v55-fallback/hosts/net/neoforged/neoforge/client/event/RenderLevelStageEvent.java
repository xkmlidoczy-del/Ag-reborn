package net.neoforged.neoforge.client.event;
import net.minecraft.client.Camera;import org.joml.Matrix4f;import com.mojang.blaze3d.vertex.PoseStack;
public record RenderLevelStageEvent(Stage getStage,Camera getCamera,Matrix4f getModelViewMatrix,PoseStack getPoseStack){public PartialTick getPartialTick(){return new PartialTick();}public static class PartialTick{public float getGameTimeDeltaPartialTick(boolean ignored){return .375f;}}public enum Stage{AFTER_PARTICLES,AFTER_ENTITIES}}
