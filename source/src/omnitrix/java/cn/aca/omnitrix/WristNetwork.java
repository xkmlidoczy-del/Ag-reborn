package cn.aca.omnitrix;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

public final class WristNetwork {
    private WristNetwork() {}

    public static final int OPEN = 0, CLOSE = 1, TRANSFORM = 2, UNEQUIP = 3, REVERT = 4,
            PRIMARY = 5, SECONDARY = 6, RESIST = 7, FLICK = 8,
            ASCALON_TOGGLE = 16, ASCALON_SWING = 17, ASCALON_PRIMARY = 18, ASCALON_SECONDARY = 19,
            ASCALON_SYZYGY = 20, ASCALON_CHARGE = 21;
    public static final int FAILSAFE_CUE = 96, RESISTED_CUE = 97, THAW_CUE = 98,
            AEGIS_CUE = 99, SINGULARITY_CUE = 100, EXTINCTION_CUE = 101, FLICK_CUE = 102, REWRITE_CUE = 103,
            CRYSTAL_SHELL_BREAK_CUE = 104,
            ASCALON_ARMOR_CUE = 110, ASCALON_SWING_CUE = 111, ASCALON_PARRY_CUE = 112,
            ASCALON_CLEAVE_HIT_CUE = 113, ASCALON_FISSURE_CUE = 114, ASCALON_BEAM_END_CUE = 115,
            ASCALON_RIFT_CUE = 116, ASCALON_TEMPEST_CUE = 117, ASCALON_DOME_ARC_CUE = 118,
            ASCALON_SYZYGY_CUE = 119, ASCALON_MACH_CUE = 120;
    public static final int FLAG_HIT_ENTITY = 1, FLAG_HIT_GROUND = 2, FLAG_LINKED = 4, FLAG_PROJECTILE = 8,
            FLAG_FALLBACK = 16;
    public static final int STASIS_SCENE = 0, SEAL_SCENE = 1, SYZYGY_SCENE = 2;

    private record ActiveScene(Scene scene, Set<ServerPlayer> viewers) {}
    private static final Map<ServerLevel, List<ActiveScene>> SCENES = new WeakHashMap<>();

    public record Request(int action, int value) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(Omnitrix.id("request"));
        static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buf, data) -> { buf.writeByte(data.action); buf.writeVarInt(data.value); },
                buf -> new Request(buf.readUnsignedByte(), buf.readVarInt()));
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Performance(int action, int style, long start, long impact, long end, Vec3 origin, Vec3 focus) {
        private void write(FriendlyByteBuf buf) {
            buf.writeByte(action);
            buf.writeByte(style);
            buf.writeVarLong(start);
            buf.writeVarLong(impact);
            buf.writeVarLong(end);
            Cue.write(buf, origin);
            Cue.write(buf, focus);
        }

        private static Performance read(FriendlyByteBuf buf) {
            return new Performance(buf.readUnsignedByte(), buf.readUnsignedByte(), buf.readVarLong(), buf.readVarLong(),
                    buf.readVarLong(), Cue.read(buf), Cue.read(buf));
        }
    }

    public record Snapshot(UUID actor, int kind, int stage, int form, int target, boolean lifted, boolean bonded,
                           boolean misfire, int resistance,
                           int revision, long now, long began, long until, long primaryAt, long secondaryAt,
                           long shiftPrimaryAt, long shiftSecondaryAt, long flickAt, long crystalShellUntil,
                           Performance performance, long linkUntil, long skillHintUntil,
                           double barrier, double maxBarrier)
            implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(Omnitrix.id("wrist"));
        static final StreamCodec<FriendlyByteBuf, Snapshot> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeUUID(s.actor);
            buf.writeByte(s.kind);
            buf.writeByte(s.stage);
            buf.writeByte(s.form);
            buf.writeVarInt(s.target);
            buf.writeBoolean(s.lifted);
            buf.writeBoolean(s.bonded);
            buf.writeBoolean(s.misfire);
            buf.writeByte(s.resistance);
            buf.writeVarInt(s.revision);
            buf.writeVarLong(s.now);
            buf.writeVarLong(s.began);
            buf.writeVarLong(s.until);
            buf.writeVarLong(s.primaryAt);
            buf.writeVarLong(s.secondaryAt);
            buf.writeVarLong(s.shiftPrimaryAt);
            buf.writeVarLong(s.shiftSecondaryAt);
            buf.writeVarLong(s.flickAt);
            buf.writeVarLong(s.crystalShellUntil);
            buf.writeBoolean(s.performance != null);
            if (s.performance != null) s.performance.write(buf);
            buf.writeVarLong(s.linkUntil);
            buf.writeVarLong(s.skillHintUntil);
            buf.writeDouble(s.barrier);
            buf.writeDouble(s.maxBarrier);
        }, buf -> new Snapshot(buf.readUUID(), buf.readByte(), buf.readByte(), buf.readByte(), buf.readVarInt(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readUnsignedByte(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readBoolean() ? Performance.read(buf) : null, buf.readVarLong(), buf.readVarLong(),
                buf.readDouble(), buf.readDouble()));
        @Override public Type<Snapshot> type() { return TYPE; }

        public Kind watch() { return Kind.at(kind); }
        public Form chosen() { return Form.at(form); }
        public Wrist.Stage phase() { return Wrist.Stage.values()[stage]; }
        public boolean morphed() { return chosen() != null && (phase() == Wrist.Stage.ACTIVE || phase() == Wrist.Stage.REVERTING); }
        public boolean controlled() {
            return watch() == Kind.CARNITRIX && (morphed() || phase() == Wrist.Stage.ARMING);
        }

        static Snapshot of(ServerPlayer player) {
            var wrist = Omnitrix.wrist(player);
            Kind kind = wrist.kind();
            return new Snapshot(player.getUUID(), kind == null ? -1 : kind.ordinal(), wrist.stage.ordinal(),
                    wrist.form == null ? -1 : wrist.form.ordinal(), wrist.targetId, wrist.panelUp, wrist.carniLocked,
                    wrist.misfire, wrist.resistance,
                    wrist.revision, WristService.time(player), wrist.began, wrist.until, wrist.primaryAt, wrist.secondaryAt,
                    wrist.shiftPrimaryAt, wrist.shiftSecondaryAt, wrist.flickAt, wrist.crystalShellUntil,
                    wrist.skill == null ? null : wrist.skill.motion, Math.max(wrist.linkUntil, wrist.circuitUntil), wrist.skillHintUntil,
                    wrist.barrier, wrist.maxBarrier);
        }
    }

    public record AscalonSnapshot(UUID actor, boolean armored, float charge, float flightBoost, long now,
                                  long transitionTick, long slashAt, long riftAt, long tempestAt, long syzygyAt,
                                  long skySyzygyUntil, long parryCounterUntil, long iaidoChargeSince,
                                  long riftRecallUntil, long phantomArrayUntil, int comboStep, int revision)
            implements CustomPacketPayload {
        public static final Type<AscalonSnapshot> TYPE = new Type<>(Omnitrix.id("ascalon"));
        static final StreamCodec<FriendlyByteBuf, AscalonSnapshot> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeUUID(s.actor);
            buf.writeBoolean(s.armored);
            buf.writeFloat(s.charge);
            buf.writeFloat(s.flightBoost);
            buf.writeVarLong(s.now);
            buf.writeVarLong(s.transitionTick);
            buf.writeVarLong(s.slashAt);
            buf.writeVarLong(s.riftAt);
            buf.writeVarLong(s.tempestAt);
            buf.writeVarLong(s.syzygyAt);
            buf.writeVarLong(s.skySyzygyUntil);
            buf.writeVarLong(s.parryCounterUntil);
            buf.writeVarLong(s.iaidoChargeSince);
            buf.writeVarLong(s.riftRecallUntil);
            buf.writeVarLong(s.phantomArrayUntil);
            buf.writeVarInt(s.comboStep);
            buf.writeVarInt(s.revision);
        }, buf -> new AscalonSnapshot(buf.readUUID(), buf.readBoolean(), buf.readFloat(), buf.readFloat(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                buf.readVarLong(), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<AscalonSnapshot> type() { return TYPE; }

        static AscalonSnapshot of(ServerPlayer p) {
            var a = Omnitrix.ascalon(p);
            return new AscalonSnapshot(p.getUUID(), a.armored, a.siphonCharge, a.flightBoost, WristService.time(p),
                    a.transitionTick, a.slashAt, a.riftAt, a.tempestAt, a.syzygyAt, a.skySyzygyUntil,
                    a.parryCounterUntil, a.iaidoChargeSince, a.riftRecallUntil, a.phantomArrayUntil,
                    a.comboStep, a.revision);
        }
    }

    public record Cue(UUID actor, int kind, int action, int beat, int flags, long tick, Vec3 from, Vec3 to) implements CustomPacketPayload {
        public static final Type<Cue> TYPE = new Type<>(Omnitrix.id("cue"));
        static final StreamCodec<FriendlyByteBuf, Cue> CODEC = StreamCodec.of((buf, v) -> {
            buf.writeUUID(v.actor);
            buf.writeByte(v.kind);
            buf.writeByte(v.action);
            buf.writeByte(v.beat);
            buf.writeByte(v.flags);
            buf.writeVarLong(v.tick);
            write(buf, v.from);
            write(buf, v.to);
        }, buf -> new Cue(buf.readUUID(), buf.readByte(), buf.readUnsignedByte(), buf.readUnsignedByte(),
                buf.readUnsignedByte(), buf.readVarLong(), read(buf), read(buf)));
        private static void write(FriendlyByteBuf buf, Vec3 p) {
            buf.writeDouble(p.x);
            buf.writeDouble(p.y);
            buf.writeDouble(p.z);
        }
        private static Vec3 read(FriendlyByteBuf buf) { return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()); }
        public Kind watch() { return Kind.at(kind); }
        public boolean hitEntity() { return (flags & FLAG_HIT_ENTITY) != 0; }
        public boolean hitGround() { return (flags & FLAG_HIT_GROUND) != 0; }
        public boolean linked() { return (flags & FLAG_LINKED) != 0; }
        public boolean projectile() { return (flags & FLAG_PROJECTILE) != 0; }
        public boolean fallback() { return (flags & FLAG_FALLBACK) != 0; }
        @Override public Type<Cue> type() { return TYPE; }
    }

    public record Scene(UUID actor, int effect, int variant, long began, long until, long now, Vec3 center)
            implements CustomPacketPayload {
        public static final Type<Scene> TYPE = new Type<>(Omnitrix.id("scene"));
        static final StreamCodec<FriendlyByteBuf, Scene> CODEC = StreamCodec.of((buf, scene) -> {
            buf.writeUUID(scene.actor);
            buf.writeByte(scene.effect);
            buf.writeByte(scene.variant);
            buf.writeVarLong(scene.began);
            buf.writeVarLong(scene.until);
            buf.writeVarLong(scene.now);
            Cue.write(buf, scene.center);
        }, buf -> new Scene(buf.readUUID(), buf.readUnsignedByte(), buf.readUnsignedByte(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), Cue.read(buf)));

        @Override public Type<Scene> type() { return TYPE; }
    }

    public record Stasis(int entity, int ticks) implements CustomPacketPayload {
        public static final Type<Stasis> TYPE = new Type<>(Omnitrix.id("stasis"));
        static final StreamCodec<FriendlyByteBuf, Stasis> CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeVarInt(value.entity); buf.writeVarInt(value.ticks); },
                buf -> new Stasis(buf.readVarInt(), buf.readVarInt()));
        @Override public Type<Stasis> type() { return TYPE; }
    }

    public interface Receiver {
        void snapshot(Snapshot snapshot, Player viewer);
        default void ascalon(AscalonSnapshot snapshot, Player viewer) {}
        default void scene(Scene scene, Player viewer) {}
        void cue(Cue cue, Player viewer);
        void stasis(Stasis stasis, Player viewer);
        boolean isClassic(Player player, Form form);
        default boolean isViltrumPaladin(Player player) { return false; }
        default boolean reducedMotion() { return false; }
        default void appendAscalonTooltip(List<Component> tooltip) {}
    }
    public static Receiver client;

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("8").playToServer(Request.TYPE, Request.CODEC, (msg, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                if (msg.action() >= ASCALON_TOGGLE) AscalonService.handleRequest(player, msg);
                else WristService.request(player, msg);
            }
        }).playToClient(Snapshot.TYPE, Snapshot.CODEC, (msg, ctx) -> {
            if (client != null) client.snapshot(msg, ctx.player());
        }).playToClient(AscalonSnapshot.TYPE, AscalonSnapshot.CODEC, (msg, ctx) -> {
            if (client != null) client.ascalon(msg, ctx.player());
        }).playToClient(Cue.TYPE, Cue.CODEC, (msg, ctx) -> {
            if (client != null) client.cue(msg, ctx.player());
        }).playToClient(Scene.TYPE, Scene.CODEC, (msg, ctx) -> {
            if (client != null) client.scene(msg, ctx.player());
        }).playToClient(Stasis.TYPE, Stasis.CODEC, (msg, ctx) -> {
            if (client != null) client.stasis(msg, ctx.player());
        });
    }

    static void sync(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, Snapshot.of(player));
    }

    static void syncAscalon(ServerPlayer player) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, AscalonSnapshot.of(player));
    }

    static void tracking(ServerPlayer viewer, ServerPlayer actor) {
        PacketDistributor.sendToPlayer(viewer, Snapshot.of(actor));
    }

    static void trackingAscalon(ServerPlayer viewer, ServerPlayer actor) {
        PacketDistributor.sendToPlayer(viewer, AscalonSnapshot.of(actor));
    }

    static void beginScene(ServerPlayer actor, int effect, int variant, Vec3 center, int duration) {
        long now = WristService.time(actor);
        var level = actor.serverLevel();
        var scenes = SCENES.computeIfAbsent(level, ignored -> new ArrayList<>());
        if (effect != SEAL_SCENE) {
            scenes.removeIf(active -> active.scene.effect == effect && active.scene.actor.equals(actor.getUUID()));
        }
        var active = new ActiveScene(new Scene(actor.getUUID(), effect, variant, now, now + duration, now, center),
                new HashSet<>());
        scenes.add(active);
        sendScene(level, active, now);
    }

    static void tickScenes(ServerTickEvent.Post event) {
        long now = event.getServer().overworld().getGameTime();
        for (ServerLevel level : event.getServer().getAllLevels()) {
            var scenes = SCENES.get(level);
            if (scenes == null) continue;
            scenes.removeIf(active -> active.scene.until <= now);
            if (now % 5 == 0) for (var active : scenes) sendScene(level, active, now);
            if (scenes.isEmpty()) SCENES.remove(level);
        }
    }

    static void stopScenes(ServerStoppedEvent event) {
        SCENES.keySet().removeIf(level -> level.getServer() == event.getServer());
    }

    private static void sendScene(ServerLevel level, ActiveScene active, long now) {
        Scene scene = active.scene;
        active.viewers.removeIf(viewer -> viewer.isRemoved() || viewer.level() != level
                || viewer.distanceToSqr(scene.center) > 128 * 128);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(scene.center) <= 128 * 128 && active.viewers.add(viewer)) {
                PacketDistributor.sendToPlayer(viewer,
                        new Scene(scene.actor, scene.effect, scene.variant, scene.began, scene.until, now, scene.center));
            }
        }
    }

    public static void cue(ServerPlayer player, int action, Vec3 from, Vec3 to) {
        cue(player, action, 0, 0, from, to);
    }

    public static void cue(ServerPlayer player, int action, int beat, int flags, Vec3 from, Vec3 to) {
        Kind kind = player.hasData(Omnitrix.WRIST) ? Omnitrix.wrist(player).kind() : null;
        int kindId = kind == null ? -1 : kind.ordinal();
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new Cue(player.getUUID(), kindId, action, beat, flags, WristService.time(player), from, to));
    }

    static void cueNear(net.minecraft.server.level.ServerLevel level, UUID actor, int action, Vec3 at, double range) {
        cueNear(level, actor, Kind.CELESTIAL.ordinal(), action, 0, at, at, range);
    }

    static void cueNear(net.minecraft.server.level.ServerLevel level, UUID actor, int kind, int action, int flags,
                        Vec3 from, Vec3 to, double range) {
        long tick = level.getServer().overworld().getGameTime();
        PacketDistributor.sendToPlayersNear(level, null, to.x, to.y, to.z, range,
                new Cue(actor, kind, action, 0, flags, tick, from, to));
    }

    static void freeze(net.minecraft.world.entity.Entity entity, int ticks) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, new Stasis(entity.getId(), ticks));
    }

    static void freezeFor(ServerPlayer viewer, net.minecraft.world.entity.Entity entity, int ticks) {
        PacketDistributor.sendToPlayer(viewer, new Stasis(entity.getId(), ticks));
    }

    public static void ask(int action, int value) { PacketDistributor.sendToServer(new Request(action, value)); }
}
