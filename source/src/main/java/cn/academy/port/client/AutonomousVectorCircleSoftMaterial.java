package cn.academy.port.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;

/** Optional soft-alpha stroke material only. No authority, receipts, clocks or resource balances. */
final class AutonomousVectorCircleSoftMaterial {
    static final String FORCE_FALLBACK = "academy.autonomousVectorCircleSoft.forceFallback";
    private static volatile ShaderInstance softShader;
    private static volatile boolean requested; // Deliberately session-only: a new client process starts OFF.
    private static int generation;
    private AutonomousVectorCircleSoftMaterial() {}
    static boolean requested() { return requested; }
    static void request(boolean on) { requested = on; }
    static boolean ready() { return softShader != null && !Boolean.getBoolean(FORCE_FALLBACK); }
    static ShaderInstance shader() { return ready() ? softShader : GameRenderer.getPositionTexColorShader(); }
    static void register(RegisterShadersEvent event) {
        int revision = ++generation;
        softShader = null;
        if (Boolean.getBoolean(FORCE_FALLBACK)) return;
        ShaderInstance candidate = null;
        try {
            candidate = new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("academy", "autonomous_vector_circle_soft"), DefaultVertexFormat.POSITION_TEX_COLOR);
            if (GL20.glGetProgrami(candidate.getId(), GL20.GL_LINK_STATUS) == 0) { candidate.close(); return; }
            event.registerShader(candidate, loaded -> { if (revision == generation) softShader = loaded; });
        } catch (Exception | LinkageError unavailable) {
            if (candidate != null) try { candidate.close(); } catch (RuntimeException ignored) {}
            softShader = null;
        }
    }
}
