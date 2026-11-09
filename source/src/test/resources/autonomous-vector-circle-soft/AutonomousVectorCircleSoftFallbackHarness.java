package cn.academy.port.client;

import java.nio.file.Path;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;

/** Executes the production optional shader owner against the existing narrow shader/resource API doubles. */
public final class AutonomousVectorCircleSoftFallbackHarness {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        Path root = Path.of(args[0]); String lane = args[1];
        check(!AutonomousVectorCircleSoftMaterial.requested() && !AutonomousVectorCircleSoftMaterial.ready(), "a fresh client process starts OFF and unready");
        AutonomousVectorCircleSoftMaterial.request(true);
        check(AutonomousVectorCircleSoftMaterial.requested(), "ON changes only the local request flag");
        AutonomousVectorCircleSoftMaterial.request(false);
        check(!AutonomousVectorCircleSoftMaterial.requested(), "OFF is immediate without a new shader registration");
        if (lane.equals("forced")) System.setProperty(AutonomousVectorCircleSoftMaterial.FORCE_FALLBACK, "true");
        ShaderInstance.failCompile = lane.equals("compile");
        GL20.linked = !lane.equals("link");
        var event = new RegisterShadersEvent(new ResourceManager(root, lane.equals("missing") ? "missing" : "valid"));
        event.defer = lane.equals("stale");
        AutonomousVectorCircleSoftMaterial.register(event);
        if (lane.equals("valid")) {
            check(AutonomousVectorCircleSoftMaterial.ready() && event.registered == 1, "successful link enables the optional material");
            check(AutonomousVectorCircleSoftMaterial.shader().source.equals("shader=academy:autonomous_vector_circle_soft"), "renderer binds the registered new shader");
            check(!AutonomousVectorCircleSoftMaterial.requested(), "loading resources never turns the visual option ON");
            AutonomousVectorCircleSoftMaterial.request(true);
            check(AutonomousVectorCircleSoftMaterial.requested() && AutonomousVectorCircleSoftMaterial.ready(), "ON can use the already-linked shader");
            AutonomousVectorCircleSoftMaterial.request(false);
            check(!AutonomousVectorCircleSoftMaterial.requested() && AutonomousVectorCircleSoftMaterial.ready(), "OFF leaves valid resources ready for a later ON");
        } else if (lane.equals("stale")) {
            check(!AutonomousVectorCircleSoftMaterial.ready() && event.registered == 1, "deferred callback cannot advertise readiness early");
            AutonomousVectorCircleSoftMaterial.request(true);
            ShaderInstance.failCompile = true;
            AutonomousVectorCircleSoftMaterial.register(new RegisterShadersEvent(new ResourceManager(root, "valid")));
            ShaderInstance.failCompile = false;
            event.finish();
            check(!AutonomousVectorCircleSoftMaterial.ready(), "old success callback cannot undo a later failed reload");
            check(AutonomousVectorCircleSoftMaterial.requested(), "resource reload never invents a new toggle or discards the session request");
            AutonomousVectorCircleSoftMaterial.register(new RegisterShadersEvent(new ResourceManager(root, "valid")));
            check(AutonomousVectorCircleSoftMaterial.ready(), "a fresh valid reload safely recovers");
        } else {
            check(!AutonomousVectorCircleSoftMaterial.ready() && event.registered == 0, "missing, failed, unlinked or forced-off shader cannot enter an enhanced group");
            check(AutonomousVectorCircleSoftMaterial.shader().source.equals("position-tex-color-shader"), "unready callback safely exposes a vanilla shader rather than null");
            if (lane.equals("link")) check(ShaderInstance.closed == 1, "unlinked candidate is closed");
            if (lane.equals("forced")) check(ShaderInstance.attempts == 0, "forced fallback never constructs an optional shader");
            else check(ShaderInstance.attempts == 1, "the real production constructor path was exercised");
        }
        System.clearProperty(AutonomousVectorCircleSoftMaterial.FORCE_FALLBACK);
        System.out.println("PASS " + lane + ": " + checks + " production shader-owner API-double checks; no real GPU result claimed");
    }
}
