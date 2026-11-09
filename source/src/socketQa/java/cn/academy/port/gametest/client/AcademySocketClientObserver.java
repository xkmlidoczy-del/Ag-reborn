package cn.academy.port.gametest.client;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.client.AcademyClient;
import cn.academy.port.client.PresetEditScreen;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.socket.SocketChannel;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/** Read-only observer of physical controls, native socket payloads and normal AcademyClient state. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class AcademySocketClientObserver {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String OBSERVER = "academy_socket_client_observer";
    private static final ConcurrentLinkedQueue<CompoundTag> RECEIVED = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<AcademyNetwork.Request> SENT = new ConcurrentLinkedQueue<>();
    private static final List<String> CYCLE = new ArrayList<>();
    private static Connection connection;
    private static volatile boolean installed;
    private static boolean done, failed, sawPhysicalR, sawReleaseR, editorSeen, receivedFixture, receivedEditedWhileZero, receivedFinal, wireEdit;
    private static boolean oldR, oldC, oldN, oldLmb;
    private static int rEdges, cEdges, nEdges, lmbEdges, states, starts, ends, arcs, wireRPress, wireRRelease, wireArcPress, maximum;
    private static long admittedAt;
    private static double previousCp = Double.NaN;
    private static boolean receivedCpDrop, receivedProgress;
    private AcademySocketClientObserver() {}

    private static Path directory() {
        Path dir = Path.of("").toAbsolutePath().normalize();
        if (!dir.getFileName().toString().matches("run-socket-client-[A-Za-z0-9_-]+"))
            throw new IllegalStateException("Read-only socket client QA requires disposable run-socket-client-* working directory");
        return dir;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("academy.socket.clientqa") || done || failed) return;
        try {
            directory();
            Minecraft mc = Minecraft.getInstance();
            if (connection != null && (!connection.isConnected() || System.nanoTime() - admittedAt > TimeUnit.MINUTES.toNanos(15)))
                throw new IllegalStateException("Real client observation ended before complete physical/received proof; no socket pass claimed");
            if (mc.player == null || mc.getConnection() == null || mc.level == null) return;
            Connection actual = mc.getConnection().getConnection();
            if (connection == null) install(actual);
            if (connection != actual) throw new IllegalStateException("Socket observer never follows a replacement connection automatically");
            physical(mc);
            AcademyNetwork.Request sent;
            while ((sent = SENT.poll()) != null) {
                if (sent.action().equals("slot_press") && sent.value().equals("2")) wireRPress++;
                if (sent.action().equals("slot_release") && sent.value().equals("2")) wireRRelease++;
                if (sent.action().equals("slot_press") && sent.value().equals("0")) wireArcPress++;
                if (sent.action().equals("preset_switch")) CYCLE.add(sent.value());
                if (sent.action().equals("preset_edit") && sent.value().equals("1:0:arc_gen")) wireEdit = true;
                LOG.info("ACADEMY_SOCKET_CLIENT_WIRE action={} value={}", sent.action(), sent.value());
            }
            CompoundTag incoming;
            while ((incoming = RECEIVED.poll()) != null) {
                switch (incoming.getString("kind")) {
                    case "state" -> {
                        states++;
                        var state = AbilityStorage.decode(incoming);
                        receivedFixture |= state.category.equals("electromaster") && state.level == 5 && state.activated
                                && state.exp("charging") == 1 && state.exp("arc_gen") == 1 && state.exp("railgun") == 1
                                && state.presets.current() == 0 && state.presets.skill(0, 0).equals("arc_gen")
                                && state.presets.skill(0, 1).isEmpty() && state.presets.skill(0, 2).equals("charging") && state.presets.skill(0, 3).equals("railgun");
                        receivedEditedWhileZero |= state.presets.current() == 0 && state.presets.skill(1, 0).equals("arc_gen");
                        receivedFinal |= state.presets.current() == 1 && state.presets.currentSkill(0).equals("arc_gen");
                        if (Double.isFinite(previousCp) && state.cp < previousCp) receivedCpDrop = true;
                        previousCp = state.cp; receivedProgress |= state.levelExperience > 0;
                        maximum = Math.max(maximum, state.cooldownMaxTicks.getOrDefault("arc_gen", 0));
                    }
                    case "charging_start" -> starts++;
                    case "charging_end" -> ends++;
                    case "arc" -> arcs++;
                    default -> {}
                }
            }
            var normal = AcademyClient.state;
            if (installed && receivedFixture && receivedEditedWhileZero && receivedFinal && receivedCpDrop && receivedProgress
                    && starts == 1 && ends == 1 && arcs == 2 && maximum == 5
                    && wireRPress == 1 && wireRRelease == 1 && wireArcPress == 2 && wireEdit
                    && CYCLE.equals(List.of("1", "2", "3", "0", "1"))
                    && sawPhysicalR && sawReleaseR && rEdges >= 1 && cEdges >= 5 && nEdges >= 1 && lmbEdges >= 2 && editorSeen
                    && normal.presets.current() == 1 && normal.presets.currentSkill(0).equals("arc_gen") && normal.levelExperience > 0) {
                Properties proof = properties("passed");
                proof.setProperty("normalClientStateFinal", "current=1,LMB=arc_gen,levelExperience>0");
                proof.setProperty("claimBoundary", "Read-only local GLFW edges, decoded native TCP payloads and normal AcademyClient state observed. Server gameplay and visual pixels require server certificate/CUA screenshots.");
                write("socket-client-proof.properties", proof); done = true;
                LOG.info("ACADEMY_SOCKET_CLIENT_PROOF_PASS physicalR=true physicalRelease=true Cedges={} Nedges={} LMBedges={} decodedStates={} arcEffects=2 cooldownMaximum=5", cEdges, nEdges, lmbEdges, states);
            }
        } catch (RuntimeException e) {
            failed = true;
            Properties proof = properties("failed"); proof.setProperty("reason", e.toString());
            write("socket-client-failure.properties", proof);
            LOG.error("ACADEMY_SOCKET_CLIENT_PROOF_FAIL", e);
        }
    }
    private static void install(Connection actual) {
        if (actual.isMemoryConnection() || !(actual.channel() instanceof SocketChannel)
                || !(actual.getRemoteAddress() instanceof InetSocketAddress remote) || !remote.getAddress().isLoopbackAddress() || remote.getPort() != 25569)
            throw new IllegalStateException("Expected the explicit real loopback TCP endpoint on port 25569");
        var setup = ChannelAttributes.getPayloadSetup(actual);
        if (ChannelAttributes.getConnectionType(actual) != ConnectionType.NEOFORGE || setup == null
                || setup.getChannel(ConnectionProtocol.PLAY, AcademyNetwork.Request.TYPE.id()) == null
                || setup.getChannel(ConnectionProtocol.PLAY, AcademyNetwork.ClientData.TYPE.id()) == null
                || !setup.getChannel(ConnectionProtocol.PLAY, AcademyNetwork.Request.TYPE.id()).chosenVersion().equals("1")
                || !setup.getChannel(ConnectionProtocol.PLAY, AcademyNetwork.ClientData.TYPE.id()).chosenVersion().equals("1"))
            throw new IllegalStateException("Both required Academy channels must be negotiated by NeoForge at version 1");
        connection = actual; admittedAt = System.nanoTime();
        actual.channel().eventLoop().execute(() -> {
            actual.channel().pipeline().addBefore("packet_handler", OBSERVER, new ChannelDuplexHandler() {
                @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                    if (message instanceof ClientboundCustomPayloadPacket packet && packet.payload() instanceof AcademyNetwork.ClientData payload && payload.data() != null)
                        RECEIVED.add(payload.data().copy());
                    super.channelRead(context, message);
                }
                @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    if (message instanceof ServerboundCustomPayloadPacket packet && packet.payload() instanceof AcademyNetwork.Request request) SENT.add(request);
                    super.write(context, message, promise);
                }
            });
            installed = true;
        });
        LOG.info("ACADEMY_SOCKET_CLIENT_ADMITTED nativeTCP=true memoryConnection=false negotiatedVersion=1");
    }
    private static boolean down(Minecraft mc, KeyMapping mapping) {
        var key = mapping.getKey(); long window = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) return key.getValue() >= 0 && GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        if (key.getType() == InputConstants.Type.KEYSYM) return key.getValue() >= 0 && InputConstants.isKeyDown(window, key.getValue());
        return mapping.isDown();
    }
    private static void physical(Minecraft mc) {
        boolean r = down(mc, AcademyClient.SLOTS[2]), c = down(mc, AcademyClient.SWITCH_PRESET), n = down(mc, AcademyClient.EDIT_PRESET), lmb = down(mc, AcademyClient.SLOTS[0]);
        if (r && !oldR && mc.screen == null) { rEdges++; sawPhysicalR = true; }
        if (!r && oldR && sawPhysicalR) sawReleaseR = true;
        if (c && !oldC && mc.screen == null) cEdges++;
        // AcademyClient may already open the editor earlier in this same ClientTickEvent.Post.
        if (n && !oldN && (mc.screen == null || mc.screen instanceof PresetEditScreen)) nEdges++;
        if (lmb && !oldLmb && mc.screen == null) lmbEdges++;
        editorSeen |= mc.screen instanceof PresetEditScreen;
        oldR = r; oldC = c; oldN = n; oldLmb = lmb;
    }
    private static Properties properties(String status) {
        Properties proof = new Properties(); proof.setProperty("status", status);
        proof.setProperty("transport", connection == null ? "not-observed" : connection.channel().getClass().getName());
        proof.setProperty("nativeReceivedStates", Integer.toString(states)); proof.setProperty("receivedChargingStart", Integer.toString(starts));
        proof.setProperty("receivedChargingEnd", Integer.toString(ends)); proof.setProperty("receivedArcEffects", Integer.toString(arcs));
        proof.setProperty("receivedCooldownMaximum", Integer.toString(maximum)); proof.setProperty("physicalREdges", Integer.toString(rEdges));
        proof.setProperty("physicalRRelease", Boolean.toString(sawReleaseR)); proof.setProperty("physicalCEdges", Integer.toString(cEdges));
        proof.setProperty("physicalNEdges", Integer.toString(nEdges)); proof.setProperty("physicalLMBEdges", Integer.toString(lmbEdges));
        proof.setProperty("presetEditorObserved", Boolean.toString(editorSeen)); proof.setProperty("outboundCycle", String.join(",", CYCLE));
        proof.setProperty("outboundNonselectedEdit", Boolean.toString(wireEdit)); return proof;
    }
    private static void write(String name, Properties proof) {
        try (OutputStream output = Files.newOutputStream(directory().resolve(name))) { proof.store(output, "Independent read-only native client proof"); }
        catch (IOException e) { throw new IllegalStateException("Cannot write client socket proof", e); }
    }
}
