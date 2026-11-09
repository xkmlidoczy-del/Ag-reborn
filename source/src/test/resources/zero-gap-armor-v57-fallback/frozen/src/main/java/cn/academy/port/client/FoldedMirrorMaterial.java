package cn.academy.port.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;
import org.lwjgl.opengl.GL20;

/** NEW221 only. Original PNGs remain intact; the resource manager owns shader lifetime/reloads. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class FoldedMirrorMaterial {
    private static ShaderInstance energy;
    private FoldedMirrorMaterial() {}

    @SubscribeEvent public static void register(RegisterShadersEvent event) {
        // Never retain the previous resource generation if a replacement cannot compile.
        energy = null;
        if (Boolean.getBoolean("academy.highpower.foldedMirrorForceFallback")) {
            LogUtils.getLogger().info("NEW221 shader material forced to safe original-texture fallback");
            return;
        }
        try {
            ShaderInstance candidate = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("academy", "folded_mirror_energy"),
                    DefaultVertexFormat.POSITION_TEX_COLOR);
            // Vanilla logs link failures without throwing from ShaderInstance's constructor.
            if (GL20.glGetProgrami(candidate.getId(), GL20.GL_LINK_STATUS) == 0) {
                LogUtils.getLogger().error("NEW221 shader material did not link; using safe original-texture fallback: {}",
                        GL20.glGetProgramInfoLog(candidate.getId()));
                candidate.close();
                return;
            }
            event.registerShader(candidate, shader -> {
                        energy = shader;
                        LogUtils.getLogger().info("NEW221 shader material loaded for this resource generation");
                    });
        } catch (IOException | RuntimeException exception) {
            LogUtils.getLogger().error("NEW221 shader material failed; using safe original-texture fallback", exception);
        }
    }
    public static ShaderInstance get() { return energy != null ? energy : GameRenderer.getPositionTexColorShader(); }
    public static boolean available() { return energy != null; }

    /** Called only after the preceding batch has drawn; uniforms cannot leak to another owner/age. */
    public static void set(FoldedMirrorMesh.Face face) {
        if (energy == null) return;
        var kind = energy.getUniform("MaterialKind"); if (kind != null) kind.set((float)face.kind());
        var part = energy.getUniform("TexturePart"); if (part != null) part.set((float)face.texture());
        var phase = energy.getUniform("VisualPhase"); if (phase != null) phase.set(face.material().phase());
        var contact = energy.getUniform("Contact");
        if (contact != null) contact.set(face.material().contactU(), face.material().contactV(), face.material().contactPower());
    }
}
