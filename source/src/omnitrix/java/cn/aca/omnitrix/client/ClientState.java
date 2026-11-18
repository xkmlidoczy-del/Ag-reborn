package cn.aca.omnitrix.client;

import cn.aca.omnitrix.AscalonService;
import cn.aca.omnitrix.Form;
import cn.aca.omnitrix.Kind;
import cn.aca.omnitrix.Wrist.Stage;
import cn.aca.omnitrix.WristNetwork;
import cn.aca.omnitrix.WristNetwork.AscalonSnapshot;
import cn.aca.omnitrix.WristNetwork.Snapshot;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

final class ClientState implements WristNetwork.Receiver {
    record View(Snapshot s, long localReceived) {
        double now(float partial) {
            var level = Minecraft.getInstance().level;
            return (double) s.now() + (level == null ? 0 : level.getGameTime() - localReceived) + partial;
        }
        float age(float partial) { return (float) Math.max(0, now(partial) - s.began()); }
        float progress(float partial) {
            return s.until() <= s.began() ? 0 : Math.min(1, age(partial) / (s.until() - s.began()));
        }
        int ticksLeft() { return Math.max(0, (int) (s.until() - now(0))); }
        WristNetwork.Performance performance(float partial) {
            var playing = s.performance();
            double now = now(partial);
            return playing != null && now >= playing.start() && now < playing.end() ? playing : null;
        }
    }

    record AscalonView(AscalonSnapshot s, long localReceived) {
        double now(float partial) {
            var level = Minecraft.getInstance().level;
            return (double) s.now() + (level == null ? 0 : level.getGameTime() - localReceived) + partial;
        }
        float transitionFactor(float partial) {
            if (s.transitionTick() == Long.MIN_VALUE) return s.armored() ? 1f : 0f;
            float elapsed = (float) Math.max(0, now(partial) - s.transitionTick());
            float f = Math.min(1f, elapsed / 12f);
            return s.armored() ? f : (1f - f);
        }
        boolean skySyzygyActive(float partial) {
            return now(partial) < s.skySyzygyUntil();
        }
        float skySyzygyIntensity(float partial) {
            double rem = s.skySyzygyUntil() - now(partial);
            if (rem <= 0) return 0f;
            float fadeIn = (float) Math.min(1.0, (AscalonService.SYZYGY_SKY_TICKS - rem + 4) / 14.0);
            float fadeOut = (float) Math.min(1.0, rem / 30.0);
            return fadeIn * fadeOut;
        }
        boolean parryCounterReady(float partial) {
            return now(partial) < s.parryCounterUntil();
        }
        float iaidoChargeProgress(float partial) {
            if (s.iaidoChargeSince() == Long.MIN_VALUE) return 0f;
            return (float) Math.min(1.0, Math.max(0.0, (now(partial) - s.iaidoChargeSince()) / 12.0));
        }
        boolean riftRecallReady(float partial) {
            return now(partial) < s.riftRecallUntil();
        }
        boolean phantomArrayActive(float partial) {
            return now(partial) < s.phantomArrayUntil();
        }
    }

    record Effect(WristNetwork.Cue cue, long tick) {}
    record SceneView(WristNetwork.Scene scene, long localReceived) {
        long began() { return localReceived - (scene.now() - scene.began()); }
        long until() { return localReceived + (scene.until() - scene.now()); }
    }
    private record SceneKey(UUID actor, int effect, long began) {}

    private static final int MAX_CUE_EVENTS = 192;
    private static final Map<UUID, View> WRISTS = new HashMap<>();
    private static final Map<UUID, AscalonView> ASCALONS = new HashMap<>();
    private static final Map<UUID, Effect> LATEST = new HashMap<>();
    private static final ArrayDeque<Effect> EVENTS = new ArrayDeque<>();
    private static final Map<SceneKey, SceneView> SCENES = new HashMap<>();
    private static final Map<Integer, Long> FROZEN = new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel visualLevel;

    @Override
    public void snapshot(Snapshot s, Player viewer) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        levelChanged(level);
        if (s.watch() == null) WRISTS.remove(s.actor());
        else WRISTS.put(s.actor(), new View(s, level.getGameTime()));
        var actor = level.getPlayerByUUID(s.actor());
        if (actor != null) actor.refreshDimensions();
        if (s.actor().equals(viewer.getUUID()) && (s.controlled() || s.phase() == Stage.LOCKING) && Minecraft.getInstance().screen != null
                && !(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.PauseScreen)
                && !(Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            viewer.closeContainer();
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public void scene(WristNetwork.Scene scene, Player viewer) {
        var level = Minecraft.getInstance().level;
        if (level == null || scene.until() <= scene.now()) return;
        levelChanged(level);
        if (scene.effect() != WristNetwork.SEAL_SCENE) {
            SCENES.keySet().removeIf(key -> key.actor.equals(scene.actor()) && key.effect == scene.effect()
                    && key.began < scene.began());
        }
        SCENES.put(new SceneKey(scene.actor(), scene.effect(), scene.began()), new SceneView(scene, level.getGameTime()));
    }

    @Override
    public void ascalon(AscalonSnapshot s, Player viewer) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        levelChanged(level);
        ASCALONS.put(s.actor(), new AscalonView(s, level.getGameTime()));
    }

    @Override
    public void stasis(WristNetwork.Stasis packet, Player viewer) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        levelChanged(level);
        if (packet.ticks() <= 0) FROZEN.remove(packet.entity());
        else FROZEN.put(packet.entity(), level.getGameTime() + packet.ticks());
    }

    static void hold(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        var entity = event.getEntity();
        if (entity.level() == visualLevel && FROZEN.getOrDefault(entity.getId(), Long.MIN_VALUE) > entity.level().getGameTime()) {
            event.setCanceled(true);
        }
    }

    @Override
    public void cue(WristNetwork.Cue cue, Player viewer) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            levelChanged(level);
            long now = level.getGameTime();
            var wrist = WRISTS.get(cue.actor());
            var sword = ASCALONS.get(cue.actor());
            double serverNow = cue.action() >= WristNetwork.ASCALON_ARMOR_CUE && sword != null
                    ? sword.now(0) : wrist != null ? wrist.now(0) : cue.tick();
            long playedAt = now - Math.clamp((long) serverNow - cue.tick(), 0, 56);
            Effect effect = new Effect(cue, playedAt);
            if (!cue.projectile()) {
                LATEST.put(cue.actor(), effect);
            }
            EVENTS.addLast(effect);
            while (EVENTS.size() > MAX_CUE_EVENTS) {
                EVENTS.removeFirst();
            }
            WristPost.effect(cue, viewer, playedAt);
            AscalonRenderer.onCue(cue, playedAt);
        }
    }

    @Override
    public boolean isClassic(Player player, Form form) {
        var view = get(player);
        return view != null && view.s().watch() == Kind.CLASSIC && view.s().phase() == Stage.ACTIVE && view.s().chosen() == form;
    }

    @Override
    public boolean isViltrumPaladin(Player player) {
        var view = get(player);
        if (view != null && view.s().morphed()) return false;
        var asc = ascalon(player);
        return asc != null && asc.s().armored();
    }

    @Override
    public boolean reducedMotion() {
        return reduced();
    }

    @Override
    public void appendAscalonTooltip(List<Component> tooltip) {
        var self = selfAscalon();
        boolean armored = self != null && self.s().armored();
        int pct = self == null ? 100 : Math.round(self.s().charge());
        String armorKey = OmnitrixClient.ASCALON_ARMOR.getTranslatedKeyMessage().getString();
        String uKey = OmnitrixClient.PRIMARY.getTranslatedKeyMessage().getString();
        String iKey = OmnitrixClient.SECONDARY.getTranslatedKeyMessage().getString();
        String oKey = OmnitrixClient.FLICK.getTranslatedKeyMessage().getString();
        tooltip.add(Component.translatable(armored ? "ascalon.aca_omnitrix.armor_state_on" : "ascalon.aca_omnitrix.armor_state_off", armorKey)
                .withStyle(Style.EMPTY.withColor(armored ? 0xdce8f8 : 0x9fb8d8)));
        tooltip.add(Component.translatable("ascalon.aca_omnitrix.charge", pct)
                .withStyle(Style.EMPTY.withColor(pct >= 100 ? 0xebd39c : 0xbcd4f2)));
        tooltip.add(Component.translatable("ascalon.aca_omnitrix.flight_hint")
                .withStyle(Style.EMPTY.withColor(0xaec6e8)));
        tooltip.add(Component.translatable("ascalon.aca_omnitrix.controls_1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("ascalon.aca_omnitrix.controls_2", uKey, iKey, uKey, oKey)
                .withStyle(Style.EMPTY.withColor(0x88a6cc)));
    }

    static View get(Entity entity) { return entity == null ? null : WRISTS.get(entity.getUUID()); }
    static View self() { return get(Minecraft.getInstance().player); }
    static AscalonView ascalon(Entity entity) { return entity == null ? null : ASCALONS.get(entity.getUUID()); }
    static AscalonView selfAscalon() { return ascalon(Minecraft.getInstance().player); }
    static Iterable<Map.Entry<UUID, AscalonView>> allAscalons() { return ASCALONS.entrySet(); }

    static float bearing(Player actor, View view, float partial) {
        var target = actor.level().getEntity(view.s().target());
        if (target == null) return 0;
        var offset = target.getPosition(partial).subtract(actor.getPosition(partial));
        float yaw = (float) Math.toDegrees(Math.atan2(-offset.x, offset.z));
        return net.minecraft.util.Mth.wrapDegrees(yaw - actor.getViewYRot(partial));
    }

    static Effect effect(Entity p) { return p == null ? null : LATEST.get(p.getUUID()); }
    static Iterable<Effect> effects() { return EVENTS; }
    static Iterable<SceneView> scenes() { return SCENES.values(); }
    static boolean reduced() { return Minecraft.getInstance().options.screenEffectScale().get() <= .25; }

    static void clear() {
        WRISTS.clear();
        ASCALONS.clear();
        SCENES.clear();
        LATEST.clear();
        EVENTS.clear();
        FROZEN.clear();
        visualLevel = null;
        WristPost.reset();
        WristVisuals.clear();
        AscalonRenderer.clear();
    }

    static void tick() {
        var level = Minecraft.getInstance().level;
        levelChanged(level);
        if (level != null) {
            long now = level.getGameTime();
            LATEST.entrySet().removeIf(e -> now - e.getValue().tick > 44);
            EVENTS.removeIf(e -> now - e.tick > 56);
            SCENES.values().removeIf(scene -> scene.until() <= now);
            FROZEN.values().removeIf(until -> until <= now);
            AscalonRenderer.tick(now);
        }
    }

    private static void levelChanged(net.minecraft.client.multiplayer.ClientLevel level) {
        if (visualLevel == level) return;
        visualLevel = level;
        WRISTS.clear();
        ASCALONS.clear();
        SCENES.clear();
        LATEST.clear();
        EVENTS.clear();
        FROZEN.clear();
        WristPost.reset();
        WristVisuals.clear();
        AscalonRenderer.clear();
    }

    static float attack(Entity entity, float partial) {
        var cue = effect(entity);
        var level = Minecraft.getInstance().level;
        if (cue == null || level == null) return 0;
        float progress = (level.getGameTime() - cue.tick + partial) / 17;
        return progress > 1 ? 0 : progress;
    }
}
