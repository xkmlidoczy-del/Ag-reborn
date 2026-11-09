package cn.academy.port.client;

import cn.academy.port.highpower.ConstructRuntime;
import cn.academy.port.highpower.core.ConstructPose;
import cn.academy.port.highpower.core.CastPose;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/** Client store of V-ADV-01 cues: the latest cue per owner, stamped with the client game time it arrived. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class ConstructClient {
    record Cue(ConstructRuntime.Visual visual, long received) {}
    private static final Map<UUID, Cue> CUES = new HashMap<>();
    private static final Map<UUID, ConstructPose.Timeline> POSES = new HashMap<>();
    private static final Map<UUID, List<ConstructDebris.Sample>> SAMPLES = new HashMap<>();
    private static Object world;

    private ConstructClient() {}

    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(world!=mc.level) { CUES.clear(); POSES.clear(); SAMPLES.clear(); world=mc.level; }
        if(mc.level!=null) { for(var cue:cues()) if(cue.visual().phase()==ConstructRuntime.DASH) dashDebris(mc,cue.visual()); }
    }

    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) { ConstructRuntime.clientReceiver = ConstructClient::receive; }
    }

    static void receive(ConstructRuntime.Visual v) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (mc.level == null || !mc.level.dimension().location().equals(v.dimension())) return;
            if (world != mc.level) { CUES.clear(); POSES.clear(); SAMPLES.clear(); world = mc.level; }
            if (v.phase() >= ConstructRuntime.SHATTER) { impactDebris(mc, v); return; }
            Cue old = CUES.get(v.owner());
            if (old != null && old.visual().token() > v.token()) return;
            var entity=mc.level.getEntity(v.entityId());
            var pose=POSES.computeIfAbsent(v.owner(), id -> new ConstructPose.Timeline());
            if (!pose.accept(v.token(),v.phase(),mc.level.getGameTime(),entity==null?0:entity.getYRot())) return;
            boolean fresh = old == null || old.visual().token() != v.token() || old.visual().phase() != v.phase();
            CUES.put(v.owner(), new Cue(v, mc.level.getGameTime()));
            if (fresh) phaseDebris(mc, v, entity);
            if (CUES.size() > 64) { var first=CUES.keySet().iterator().next(); CUES.remove(first); POSES.remove(first); }
        });
    }

    /** The thrown structure breaks apart, and pieces of it fly to a struck target to become the blocks that lock it. */
    private static void impactDebris(Minecraft mc, ConstructRuntime.Visual v) {
        var level = mc.level; if (level == null) return;
        var samples = SAMPLES.get(v.owner());
        if (v.phase() == ConstructRuntime.SHATTER) ConstructDebris.shatter(level, samples, v.min(), v.max(), v.aux(), 110);
        else ConstructDebris.gather(level, samples, v.aux(), v.min(), v.max(), cn.academy.port.highpower.core.ConstructRules.ENTOMB_GATHER_TICKS, 40);
    }

    /** One-shot debris at a phase change: the dash starts with a stomp and cracking blocks; the grab heaves the ground and bursts chips off the structure. */
    private static void phaseDebris(Minecraft mc, ConstructRuntime.Visual v, net.minecraft.world.entity.Entity owner) {
        var level = mc.level; if (level == null) return;
        if (v.phase() == ConstructRuntime.PREVIEW || v.phase() == ConstructRuntime.DASH) {
            var samples = ConstructDebris.sample(level, v.min(), v.max());
            if (!samples.isEmpty()) SAMPLES.put(v.owner(), samples);
        }
        if (v.phase() == ConstructRuntime.DASH) {
            if (owner != null) ConstructDebris.feet(level, owner.position(), owner.getLookAngle(), 26, 1.3);
            ConstructDebris.crack(level, SAMPLES.get(v.owner()), v.min(), v.max(), 22, 1);
        } else if (v.phase() == ConstructRuntime.HOLD) {
            ConstructDebris.crack(level, SAMPLES.get(v.owner()), v.min(), v.max(), 44, 1.5);
            ConstructDebris.heave(level, v.min(), v.max(), 54, 1.6);
            if (owner != null) ConstructDebris.feet(level, owner.position(), owner.getLookAngle(), 18, 1.1);
        }
    }

    /** While the caster runs at a locked structure it keeps cracking: a few chips per tick. */
    private static void dashDebris(Minecraft mc, ConstructRuntime.Visual v) {
        var level = mc.level; if (level == null) return;
        var samples = SAMPLES.get(v.owner());
        ConstructDebris.crack(level, samples, v.min(), v.max(), 2, .9);
        if (level.getGameTime() % 3 == 0 && level.getEntity(v.entityId()) instanceof net.minecraft.world.entity.player.Player p && p.getDeltaMovement().horizontalDistanceSqr() > .04)
            ConstructDebris.feet(level, p.position(), p.getDeltaMovement().normalize(), 3, .8);
    }

    /** Crouch and forward lean for the dash, sampled like the arm pose. */
    static ConstructPose.Stance stance(net.minecraft.world.entity.player.Player player, double now) {
        var mc = Minecraft.getInstance();
        if (world != mc.level || mc.level == null || !player.isAlive() || player.isSpectator()) return ConstructPose.Stance.NONE;
        var timeline = POSES.get(player.getUUID());
        return timeline == null ? ConstructPose.Stance.NONE : timeline.stance(now);
    }

    /** True while this owner's structure is held in the air (a HOLD cue arrived within the last few ticks). */
    static boolean holding(UUID owner) {
        Minecraft mc = Minecraft.getInstance();
        Cue cue = CUES.get(owner);
        return cue != null && mc.level != null && world == mc.level && cue.visual().phase() == ConstructRuntime.HOLD
                && mc.level.getGameTime() - cue.received() <= cn.academy.port.client.ConstructDecor.HOLD_STALE;
    }

    static CastPose.Motion motion(net.minecraft.world.entity.player.Player player,double now) {
        var mc=Minecraft.getInstance();
        if (world!=mc.level || mc.level==null) { CUES.clear(); POSES.clear(); return CastPose.Motion.NONE; }
        if(!player.isAlive() || player.isSpectator()) return CastPose.Motion.NONE;
        var timeline=POSES.get(player.getUUID());
        return timeline==null?CastPose.Motion.NONE:timeline.sample(now,player.getYRot(),player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT);
    }
    static double phaseAge(UUID owner,double now) {
        var timeline=POSES.get(owner); return timeline==null?0:timeline.age(now);
    }

    static List<Cue> cues() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || world != mc.level) { CUES.clear(); POSES.clear(); return List.of(); }
        var it=POSES.entrySet().iterator();
        while(it.hasNext()) { var e=it.next(); if(e.getValue().expired(mc.level.getGameTime())) { CUES.remove(e.getKey()); it.remove(); } }
        return List.copyOf(CUES.values());
    }
}
