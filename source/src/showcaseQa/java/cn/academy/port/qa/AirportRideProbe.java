package cn.academy.port.qa;

import cn.academy.port.train.TrainCarEntity;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Opt-in per-render camera/seat comparison; absent from distributable JARs. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class AirportRideProbe {
    private static final String RING_FILE = System.getProperty("academy.qa.ringRideCsv");
    private static final String FILE = RING_FILE != null ? RING_FILE : System.getProperty("academy.qa.airportRideCsv");
    private static PrintWriter out;
    private static long count;
    private AirportRideProbe() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (FILE == null || event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getVehicle() instanceof TrainCarEntity car)
                || (RING_FILE != null ? !car.ring() : !car.airport())
                || car.role() != 1 || !mc.options.getCameraType().isFirstPerson()) return;
        try {
            if (out == null) {
                out = new PrintWriter(Files.newBufferedWriter(Path.of(FILE)));
                out.println("ns,tick,partial,car_x,car_z,old_x,old_z,yaw,old_yaw,camera_x,camera_z,expected_x,expected_z,error_x,error_z");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> out.close()));
            }
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            var player = mc.player; var camera = mc.gameRenderer.getMainCamera().getPosition();
            var attachment = player.getVehicleAttachmentPoint(car);
            var mounted = car.getPassengerRidingPosition(player);
            double rx = mounted.x - car.getX() - attachment.x, rz = mounted.z - car.getZ() - attachment.z;
            double now = Math.toRadians(-car.getYRot()), old = Math.toRadians(-car.yRotO);
            double lx = rx * Math.cos(now) - rz * Math.sin(now);
            double lz = rx * Math.sin(now) + rz * Math.cos(now);
            double oldX = lx * Math.cos(old) + lz * Math.sin(old);
            double oldZ = -lx * Math.sin(old) + lz * Math.cos(old);
            double expectedX = Mth.lerp(partial, car.xOld, car.getX()) + Mth.lerp(partial, oldX, rx);
            double expectedZ = Mth.lerp(partial, car.zOld, car.getZ()) + Mth.lerp(partial, oldZ, rz);
            out.printf(Locale.ROOT,"%d,%d,%.6f,%.9f,%.9f,%.9f,%.9f,%.6f,%.6f,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f%n",
                    System.nanoTime(),player.tickCount,partial,car.getX(),car.getZ(),car.xOld,car.zOld,
                    car.getYRot(),car.yRotO,camera.x,camera.z,expectedX,expectedZ,camera.x-expectedX,camera.z-expectedZ);
            if (++count % 100 == 0) out.flush();
        } catch (Exception ex) { throw new IllegalStateException("airport ride probe failed", ex); }
    }
}
