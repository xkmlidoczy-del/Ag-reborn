package cn.academy.port.client;

import cn.academy.port.VisualTierConfig;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.highpower.NewSkillRuntime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Receives the authenticated server visuals of the seven new skills and draws them; local ownership acks go to AcademyClient. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class NewSkillEffects {
    private static final int MAX_LIVE = 32;
    private static final class Live {
        final NewSkillRuntime.Visual visual; int age;
        Live(NewSkillRuntime.Visual visual) { this.visual = visual; }
    }
    private static final List<Live> LIVE = new ArrayList<>();
    private record DominionReceipt(long token, long sequence, boolean terminal) {}
    private static final LinkedHashMap<UUID, DominionReceipt> DOMINION_RECEIPTS = new LinkedHashMap<>();
    private static final NewSkillWindowVisualLease WINDOWS = new NewSkillWindowVisualLease();
    private static Object world, player, connection;

    private NewSkillEffects() {}

    private static boolean sync(Minecraft mc) {
        if (world != mc.level || player != mc.player || connection != mc.getConnection()) {
            clear(); world = mc.level; player = mc.player; connection = mc.getConnection();
        }
        if (mc.level == null || mc.player == null || mc.getConnection() == null) { clear(); return false; }
        if (!mc.player.isAlive() || mc.player.isSpectator()) {
            WINDOWS.clear();
            // An invalid local caster must not carry its window across respawn. Other players' VFX remain visible.
            synchronized (LIVE) { LIVE.removeIf(l -> l.visual.owner().equals(mc.player.getUUID())); }
        }
        return true;
    }

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { NewSkillRuntime.clientReceiver = NewSkillEffects::receive; }
    }

    public static void receive(NewSkillRuntime.Visual v) {
        if (v == null) return;
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (!sync(mc) || !mc.level.dimension().location().equals(v.dimension())) return;
            Entity owner = mc.level.getEntity(v.entityId());
            if (owner == null || !owner.getUUID().equals(v.owner())) return;
            var kind = NewSkillRuntime.Kind.values()[v.kind()];
            String skill = kind.id;
            boolean local = owner == mc.player;
            if (local && (!mc.player.isAlive() || mc.player.isSpectator())) return;
            boolean inputMatches = !local || AcademyClient.newSkillInputMatches(skill, v.input(), v.epoch());
            var identity = new NewSkillWindowVisualLease.Identity(v.kind(), v.owner(), v.token(), v.input(), v.epoch());
            long now = mc.level.getGameTime();
            boolean windowReceipt = local && WINDOWS.accept(identity, v.sequence(), now,
                    v.phase() == NewSkillRuntime.DIVERT, v.phase() == NewSkillRuntime.END);
            if (!inputMatches && !windowReceipt) return;
            boolean dominion = kind == NewSkillRuntime.Kind.ZENITH_DOMINION;
            if (dominion && !acceptDominion(v)) return;
            if (local && v.phase() == NewSkillRuntime.ARMED) {
                if (kind == NewSkillRuntime.Kind.IMPULSE_SPLIT || kind == NewSkillRuntime.Kind.INCIDENT_BYPASS)
                    WINDOWS.arm(identity, v.sequence(), now, kind == NewSkillRuntime.Kind.IMPULSE_SPLIT ? 120 : 40);
            }
            if (v.phase() == NewSkillRuntime.END) { if (local) AcademyClient.newSkillEndedInput(skill, v.input(), v.epoch()); return; }
            if (local) AcademyClient.newSkillAcceptedInput(skill, v.input(), v.epoch(), v.phase());
            synchronized (LIVE) {
                if (dominion || kind == NewSkillRuntime.Kind.PULSE_SHIELD) {
                    LIVE.removeIf(l -> l.visual.kind() == v.kind() && l.visual.owner().equals(v.owner())
                            && (dominion || l.visual.token() == v.token()));
                }
                if (LIVE.size() >= MAX_LIVE) LIVE.remove(0);
                LIVE.add(new Live(v));
            }
        });
    }

    private static boolean acceptDominion(NewSkillRuntime.Visual v) {
        DominionReceipt previous = DOMINION_RECEIPTS.get(v.owner());
        if (previous != null && (v.token() < previous.token()
                || v.token() == previous.token() && (v.sequence() <= previous.sequence()
                || previous.terminal() && v.phase() != NewSkillRuntime.END))) return false;
        DOMINION_RECEIPTS.remove(v.owner());
        if (DOMINION_RECEIPTS.size() >= 2 * MAX_LIVE) DOMINION_RECEIPTS.pollFirstEntry();
        DOMINION_RECEIPTS.put(v.owner(), new DominionReceipt(v.token(), v.sequence(),
                v.phase() == NewSkillRuntime.RELEASE || v.phase() == NewSkillRuntime.END));
        return true;
    }

    public static ClassicAbilityHud.DelegateVisual localVisual(String skill) {
        var mc = Minecraft.getInstance();
        if (!sync(mc) || !mc.player.isAlive() || mc.player.isSpectator()) return ClassicAbilityHud.DelegateVisual.IDLE;
        synchronized (LIVE) {
            for (Live l : LIVE)
                if (l.visual.owner().equals(mc.player.getUUID()) && NewSkillRuntime.Kind.values()[l.visual.kind()].id.equals(skill) && l.age < NewSkillFx.LIFE)
                    return ClassicAbilityHud.DelegateVisual.ACTIVE;
        }
        return ClassicAbilityHud.DelegateVisual.IDLE;
    }

    public static void clear() {
        synchronized (LIVE) { LIVE.clear(); }
        DOMINION_RECEIPTS.clear();
        WINDOWS.clear();
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!sync(mc)) return;
        if (mc.isPaused()) return;
        if (mc.level != null) WINDOWS.expire(mc.level.getGameTime());
        synchronized (LIVE) { LIVE.removeIf(l -> ++l.age >= NewSkillFx.LIFE); }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (!sync(mc)) return;
        List<Live> snapshot;
        synchronized (LIVE) { if (LIVE.isEmpty()) return; snapshot = new ArrayList<>(LIVE); }
        float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = mc.level.getGameTime() + partial;
        var tier = VisualTierConfig.tier();
        var parts = new FxLib.Builder();
        for (Live l : snapshot) {
            var v = l.visual;
            Entity owner = mc.level.getEntity(v.entityId());
            var pos = owner != null ? owner.getPosition(partial) : v.point();
            var points = new ArrayList<Point>(v.points().size());
            for (var p : v.points()) points.add(new Point(p.x, p.y, p.z));
            var cue = new NewSkillFx.Cue(v.kind(), v.phase(), l.age + partial, new Point(v.point().x, v.point().y, v.point().z), new Point(v.dir().x, v.dir().y, v.dir().z), points, v.token());
            parts.add(NewSkillFx.build(cue, new Point(pos.x, pos.y, pos.z), time, tier));
        }
        VisualKit.render(event, parts.build(), NewSkillFx.effective(tier), NewSkillFx.MAX_QUADS);
    }
}
