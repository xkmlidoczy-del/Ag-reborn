package net.minecraft.client.renderer;

/** Minimal shader fallback surface; the engine owns the actual implementation in a game process. */
public final class GameRenderer {
    private static final ShaderInstance FALLBACK = new ShaderInstance();
    public static ShaderInstance getPositionTexColorShader() { return FALLBACK; }
}
