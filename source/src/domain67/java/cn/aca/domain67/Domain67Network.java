package cn.aca.domain67;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class Domain67Network {
    public static final int ACT_EXPAND_DOMAIN = 1;
    public static final int ACT_CIRCUS_BROADCAST = 2;
    public static final int ACT_LOG_FINISHER = 3;
    public static final int ACT_BENTO_CAMEO = 4;

    public static final int CUE_DOMAIN_EXPAND = 1;
    public static final int CUE_DOMAIN_CLOSE = 2;
    public static final int CUE_CIRCUS_BROADCAST = 3;
    public static final int CUE_GUARD_LOG = 4;
    public static final int CUE_LOG_BARRAGE = 5;
    public static final int CUE_MEGA_LOG_IMPACT = 6;
    public static final int CUE_BENTO_CAMEO = 7;
    public static final int CUE_FINISHED_NO_REWARD = 8;

    public interface ClientReceiver {
        void onDomainSync(DomainSyncPayload payload);
        void onVfxCue(VfxCuePayload payload);
    }

    public static volatile ClientReceiver clientReceiver;

    private Domain67Network() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar(AcaDomain67.ID).versioned("1");
        reg.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, Domain67Network::handleAction);
        reg.playToClient(DomainSyncPayload.TYPE, DomainSyncPayload.STREAM_CODEC, Domain67Network::handleDomainSync);
        reg.playToClient(VfxCuePayload.TYPE, VfxCuePayload.STREAM_CODEC, Domain67Network::handleVfxCue);
    }

    public static void sendAction(int actionId, int arg0) {
        PacketDistributor.sendToServer(new ActionPayload(actionId, arg0));
    }

    public static void broadcastCue(ServerLevel level, Vec3 pos, VfxCuePayload payload) {
        sendToNearbyClients(level, pos, payload);
    }

    public static void sendSync(ServerLevel level, Vec3 pos, DomainSyncPayload payload) {
        sendToNearbyClients(level, pos, payload);
    }

    private static void sendToNearbyClients(ServerLevel level, Vec3 pos, CustomPacketPayload payload) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos) < 156.0 * 156.0
                    && player.connection != null && player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    private static void handleAction(ActionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) {
                Domain67Manager.handlePlayerAction(sp, payload.actionId(), payload.arg0());
            }
        });
    }

    private static void handleDomainSync(DomainSyncPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var rx = clientReceiver;
            if (rx != null) rx.onDomainSync(payload);
        });
    }

    private static void handleVfxCue(VfxCuePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var rx = clientReceiver;
            if (rx != null) rx.onVfxCue(payload);
        });
    }

    public record ActionPayload(int actionId, int arg0) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(AcaDomain67.id("action"));
        public static final StreamCodec<FriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.actionId);
                    buf.writeVarInt(p.arg0);
                },
                buf -> new ActionPayload(buf.readVarInt(), buf.readVarInt())
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DinerSnapshot(int entityId, float x, float y, float z, float progress, boolean finished) {}

    public record DomainSyncPayload(
            UUID ownerId,
            boolean active,
            double cx, double cy, double cz,
            float radius,
            int ticksElapsed,
            int ticksRemaining,
            int totalDuration,
            int burnoutTicks,
            List<DinerSnapshot> diners
    ) implements CustomPacketPayload {
        public static final Type<DomainSyncPayload> TYPE = new Type<>(AcaDomain67.id("domain_sync"));
        public static final StreamCodec<FriendlyByteBuf, DomainSyncPayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeUUID(p.ownerId);
                    buf.writeBoolean(p.active);
                    buf.writeDouble(p.cx);
                    buf.writeDouble(p.cy);
                    buf.writeDouble(p.cz);
                    buf.writeFloat(p.radius);
                    buf.writeVarInt(p.ticksElapsed);
                    buf.writeVarInt(p.ticksRemaining);
                    buf.writeVarInt(p.totalDuration);
                    buf.writeVarInt(p.burnoutTicks);
                    buf.writeVarInt(p.diners.size());
                    for (var d : p.diners) {
                        buf.writeVarInt(d.entityId);
                        buf.writeFloat(d.x);
                        buf.writeFloat(d.y);
                        buf.writeFloat(d.z);
                        buf.writeFloat(d.progress);
                        buf.writeBoolean(d.finished);
                    }
                },
                buf -> {
                    UUID owner = buf.readUUID();
                    boolean active = buf.readBoolean();
                    double cx = buf.readDouble(), cy = buf.readDouble(), cz = buf.readDouble();
                    float rad = buf.readFloat();
                    int el = buf.readVarInt(), rem = buf.readVarInt(), tot = buf.readVarInt(), burn = buf.readVarInt();
                    int n = buf.readVarInt();
                    List<DinerSnapshot> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        list.add(new DinerSnapshot(
                                buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                                buf.readFloat(), buf.readBoolean()));
                    }
                    return new DomainSyncPayload(owner, active, cx, cy, cz, rad, el, rem, tot, burn, list);
                }
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record VfxCuePayload(
            int cueId,
            UUID actor,
            Vec3 from,
            Vec3 to,
            int extra
    ) implements CustomPacketPayload {
        public static final Type<VfxCuePayload> TYPE = new Type<>(AcaDomain67.id("vfx_cue"));
        public static final StreamCodec<FriendlyByteBuf, VfxCuePayload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.cueId);
                    buf.writeUUID(p.actor);
                    buf.writeDouble(p.from.x);
                    buf.writeDouble(p.from.y);
                    buf.writeDouble(p.from.z);
                    buf.writeDouble(p.to.x);
                    buf.writeDouble(p.to.y);
                    buf.writeDouble(p.to.z);
                    buf.writeVarInt(p.extra);
                },
                buf -> new VfxCuePayload(
                        buf.readVarInt(),
                        buf.readUUID(),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        buf.readVarInt()
                )
        );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
