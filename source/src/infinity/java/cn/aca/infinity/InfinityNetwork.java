package cn.aca.infinity;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class InfinityNetwork {
    private InfinityNetwork() {}

    // Client -> Server actions
    public static final int ACT_OPEN_WHEEL = 0;
    public static final int ACT_CLOSE_WHEEL = 1;
    public static final int ACT_SELECT_STONE = 2;
    public static final int ACT_UNEQUIP = 3;
    public static final int ACT_TOGGLE_SOCKET = 4;
    public static final int ACT_CAST_PRIMARY = 5;
    public static final int ACT_CAST_SECONDARY = 6;
    public static final int ACT_CAST_SNAP = 7;
    public static final int ACT_SHIP_CONTROL = 8;
    public static final int ACT_TOGGLE_GAUNTLET = 9;

    // VFX Cue IDs
    public static final int CUE_SPACE_WARP = 1;
    public static final int CUE_SPACE_ESCAPE = 2;
    public static final int CUE_REALITY_WAVE = 3;
    public static final int CUE_REALITY_CONFISCATE = 4;
    public static final int CUE_POWER_PUNCH = 5;
    public static final int CUE_POWER_WRATH = 6;
    public static final int CUE_MIND_BEAM = 7;
    public static final int CUE_MIND_ROLLCALL = 8;
    public static final int CUE_TIME_DOME = 9;
    public static final int CUE_TIME_REWIND = 10;
    public static final int CUE_SOUL_HUKOU = 11;
    public static final int CUE_SOUL_SAVE = 12;
    public static final int CUE_SNAP = 13;
    public static final int CUE_SHIP_CANNON = 14;
    public static final int CUE_SHIP_MISSILE = 15;
    public static final int CUE_SHIP_SNAP_COLLAPSE = 16;
    public static final int CUE_COMBO_PUBLIC_EXEC = 17;
    public static final int CUE_COMBO_SINGULARITY = 18;
    public static final int CUE_COMBO_TEMPORAL_RECKONING = 19;
    public static final int CUE_SHIP_RAIN_FIRE = 20;
    public static final int CUE_SHIP_WARP = 21;
    public static final int CUE_SPACE_DECOY_BURST = 22;
    public static final int CUE_REALITY_BUTTERFLY_HEAL = 23;

    public record ActionPayload(int action, int arg0, int arg1) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(AcaInfinity.id("action"));
        public static final StreamCodec<FriendlyByteBuf, ActionPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeByte(p.action);
                    buf.writeVarInt(p.arg0);
                    buf.writeVarInt(p.arg1);
                },
                buf -> new ActionPayload(buf.readUnsignedByte(), buf.readVarInt(), buf.readVarInt())
        );

        @Override
        public Type<ActionPayload> type() {
            return TYPE;
        }
    }

    public record WearerSnapshot(
            UUID actor,
            boolean equipped,
            int stonesMask,
            int selectedOrdinal,
            boolean wheelOpen,
            boolean socketMode,
            long now,
            long[] primaryReady,
            long[] secondaryReady,
            long snapReadyAt,
            long soulSaveReadyAt,
            long snapAnimAt,
            long castAnimAt,
            int castAnimStone,
            int revision
    ) implements CustomPacketPayload {
        public static final Type<WearerSnapshot> TYPE = new Type<>(AcaInfinity.id("wearer"));
        public static final StreamCodec<FriendlyByteBuf, WearerSnapshot> CODEC = StreamCodec.of(
                (buf, s) -> {
                    buf.writeUUID(s.actor);
                    buf.writeBoolean(s.equipped);
                    buf.writeVarInt(s.stonesMask);
                    buf.writeVarInt(s.selectedOrdinal);
                    buf.writeBoolean(s.wheelOpen);
                    buf.writeBoolean(s.socketMode);
                    buf.writeVarLong(s.now);
                    for (int i = 0; i < Stone.values().length; i++) buf.writeVarLong(s.primaryReady[i]);
                    for (int i = 0; i < Stone.values().length; i++) buf.writeVarLong(s.secondaryReady[i]);
                    buf.writeVarLong(s.snapReadyAt);
                    buf.writeVarLong(s.soulSaveReadyAt);
                    buf.writeVarLong(s.snapAnimAt);
                    buf.writeVarLong(s.castAnimAt);
                    buf.writeVarInt(s.castAnimStone);
                    buf.writeVarInt(s.revision);
                },
                buf -> {
                    UUID actor = buf.readUUID();
                    boolean equipped = buf.readBoolean();
                    int mask = buf.readVarInt();
                    int sel = buf.readVarInt();
                    boolean open = buf.readBoolean();
                    boolean sock = buf.readBoolean();
                    long now = buf.readVarLong();
                    long[] prim = new long[Stone.values().length];
                    long[] sec = new long[Stone.values().length];
                    for (int i = 0; i < prim.length; i++) prim[i] = buf.readVarLong();
                    for (int i = 0; i < sec.length; i++) sec[i] = buf.readVarLong();
                    return new WearerSnapshot(actor, equipped, mask, sel, open, sock, now, prim, sec,
                            buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                            buf.readVarInt(), buf.readVarInt());
                }
        );

        @Override
        public Type<WearerSnapshot> type() {
            return TYPE;
        }

        public GauntletData gauntletData() {
            return new GauntletData(stonesMask, selectedOrdinal);
        }

        public static WearerSnapshot of(ServerPlayer player) {
            WearerState w = AcaInfinity.wearer(player);
            GauntletData d = w.data();
            long now = player.level().getGameTime();
            long[] prim = w.primaryReadyAt.clone();
            long[] sec = w.secondaryReadyAt.clone();
            return new WearerSnapshot(
                    player.getUUID(),
                    w.equipped(),
                    d.stonesMask(),
                    d.selectedOrdinal(),
                    w.wheelOpen,
                    w.socketMode,
                    now,
                    prim,
                    sec,
                    w.snapReadyAt,
                    w.soulSaveReadyAt,
                    w.snapAnimAt,
                    w.castAnimAt,
                    w.castAnimStone,
                    w.revision
            );
        }
    }

    public record EntityMarkPayload(
            int entityId,
            String fatherName,
            String motherName,
            long hukouUntil,
            long shameUntil,
            long parentSlowUntil,
            int gawkTargetId,
            long gawkUntil,
            long spaceTetherUntil,
            long timeFrozenUntil,
            float frozenDamagePool,
            long mindStandUntil,
            long mindControlUntil,
            long crystalSnareUntil,
            long disarmedUntil,
            long shatteredUntil,
            long now
    ) implements CustomPacketPayload {
        public static final Type<EntityMarkPayload> TYPE = new Type<>(AcaInfinity.id("entity_mark"));
        public static final StreamCodec<FriendlyByteBuf, EntityMarkPayload> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.entityId);
                    buf.writeUtf(p.fatherName, 64);
                    buf.writeUtf(p.motherName, 64);
                    buf.writeVarLong(p.hukouUntil);
                    buf.writeVarLong(p.shameUntil);
                    buf.writeVarLong(p.parentSlowUntil);
                    buf.writeVarInt(p.gawkTargetId);
                    buf.writeVarLong(p.gawkUntil);
                    buf.writeVarLong(p.spaceTetherUntil);
                    buf.writeVarLong(p.timeFrozenUntil);
                    buf.writeFloat(p.frozenDamagePool);
                    buf.writeVarLong(p.mindStandUntil);
                    buf.writeVarLong(p.mindControlUntil);
                    buf.writeVarLong(p.crystalSnareUntil);
                    buf.writeVarLong(p.disarmedUntil);
                    buf.writeVarLong(p.shatteredUntil);
                    buf.writeVarLong(p.now);
                },
                buf -> new EntityMarkPayload(
                        buf.readVarInt(),
                        buf.readUtf(64),
                        buf.readUtf(64),
                        buf.readVarLong(),
                        buf.readVarLong(),
                        buf.readVarLong(),
                        buf.readVarInt(),
                        buf.readVarLong(),
                        buf.readVarLong(),
                        buf.readVarLong(),
                        buf.readFloat(),
                        buf.readVarLong(),
                        buf.readVarLong(),
                        buf.readVarLong(),
                        buf.readVarLong(),
                        buf.readVarLong(),
                        buf.readVarLong()
                )
        );

        @Override
        public Type<EntityMarkPayload> type() {
            return TYPE;
        }

        public static EntityMarkPayload of(Entity entity) {
            EntityStatusData s = AcaInfinity.status(entity);
            return new EntityMarkPayload(
                    entity.getId(),
                    s.fatherName,
                    s.motherName,
                    s.hukouUntil,
                    s.shameUntil,
                    s.parentSlowUntil,
                    s.gawkTargetId,
                    s.gawkUntil,
                    s.spaceTetherUntil,
                    s.timeFrozenUntil,
                    s.frozenDamagePool,
                    s.mindStandUntil,
                    s.mindControlUntil,
                    s.crystalSnareUntil,
                    s.disarmedUntil,
                    s.shatteredUntil,
                    entity.level().getGameTime()
            );
        }
    }

    public record VfxCuePayload(UUID actor, int cueId, int extra, long tick, Vec3 from, Vec3 to)
            implements CustomPacketPayload {
        public static final Type<VfxCuePayload> TYPE = new Type<>(AcaInfinity.id("vfx_cue"));
        public static final StreamCodec<FriendlyByteBuf, VfxCuePayload> CODEC = StreamCodec.of(
                (buf, c) -> {
                    buf.writeUUID(c.actor);
                    buf.writeVarInt(c.cueId);
                    buf.writeVarInt(c.extra);
                    buf.writeVarLong(c.tick);
                    buf.writeDouble(c.from.x);
                    buf.writeDouble(c.from.y);
                    buf.writeDouble(c.from.z);
                    buf.writeDouble(c.to.x);
                    buf.writeDouble(c.to.y);
                    buf.writeDouble(c.to.z);
                },
                buf -> new VfxCuePayload(
                        buf.readUUID(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarLong(),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                        new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
                )
        );

        @Override
        public Type<VfxCuePayload> type() {
            return TYPE;
        }
    }

    public interface ClientReceiver {
        void onWearer(WearerSnapshot snapshot, Player viewer);
        void onEntityMark(EntityMarkPayload mark, Player viewer);
        void onVfxCue(VfxCuePayload cue, Player viewer);
    }

    public static ClientReceiver clientReceiver;

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3").optional()
                .playToServer(ActionPayload.TYPE, ActionPayload.CODEC, (msg, ctx) -> {
                    if (ctx.player() instanceof ServerPlayer sp) {
                        GauntletService.handleAction(sp, msg);
                    }
                })
                .playToClient(WearerSnapshot.TYPE, WearerSnapshot.CODEC, (msg, ctx) -> {
                    if (clientReceiver != null) clientReceiver.onWearer(msg, ctx.player());
                })
                .playToClient(EntityMarkPayload.TYPE, EntityMarkPayload.CODEC, (msg, ctx) -> {
                    if (clientReceiver != null) clientReceiver.onEntityMark(msg, ctx.player());
                })
                .playToClient(VfxCuePayload.TYPE, VfxCuePayload.CODEC, (msg, ctx) -> {
                    if (clientReceiver != null) clientReceiver.onVfxCue(msg, ctx.player());
                });
    }

    public static void sendToServer(int action, int arg0, int arg1) {
        PacketDistributor.sendToServer(new ActionPayload(action, arg0, arg1));
    }

    private static void safeSendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        if (player != null && player.connection != null && player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    public static void syncWearer(ServerPlayer player) {
        if (player.level() instanceof ServerLevel sl) {
            WearerSnapshot snap = WearerSnapshot.of(player);
            safeSendToPlayer(player, snap);
            for (ServerPlayer other : sl.players()) {
                if (other != player && other.distanceToSqr(player) <= 128 * 128) {
                    safeSendToPlayer(other, snap);
                }
            }
        }
    }

    public static void syncWearerTo(ServerPlayer viewer, ServerPlayer actor) {
        safeSendToPlayer(viewer, WearerSnapshot.of(actor));
    }

    public static void syncEntityMark(Entity entity) {
        if (!(entity.level() instanceof ServerLevel sl)) return;
        EntityMarkPayload payload = EntityMarkPayload.of(entity);
        for (ServerPlayer viewer : sl.players()) {
            if (viewer.distanceToSqr(entity) <= 128 * 128) {
                safeSendToPlayer(viewer, payload);
            }
        }
    }

    public static void syncEntityMarkTo(ServerPlayer viewer, Entity entity) {
        safeSendToPlayer(viewer, EntityMarkPayload.of(entity));
    }

    public static void broadcastCue(ServerLevel level, UUID actor, int cueId, int extra, Vec3 from, Vec3 to, double range) {
        long now = level.getGameTime();
        VfxCuePayload cue = new VfxCuePayload(actor, cueId, extra, now, from, to);
        double r2 = range * range;
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(from) <= r2) {
                safeSendToPlayer(viewer, cue);
            }
        }
    }
}
