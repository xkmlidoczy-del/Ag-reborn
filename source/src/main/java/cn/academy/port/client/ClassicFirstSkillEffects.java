/* AcademyCraft 1.0.7 first-skill render adaptation. Copyright Lambda Innovation, GPLv3. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.skill.ClassicRaytrace;
import cn.academy.port.skill.ThreateningTeleport;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * Client-only visuals. Forward recognized effect tags to receive from the client packet switch.
 * This class does not damage entities, consume resources, change inventory, or send aim packets.
 * Modern mesh/pose hooks replace legacy OpenGL and reflection; see the fidelity document.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClassicFirstSkillEffects {
    private static final ResourceLocation WHITE = texture("port/white");
    private static final ResourceLocation GLOW = texture("effects/mdball/glow");
    private static final ResourceLocation RAY_IN = texture("effects/mdray_small/blend_in");
    private static final ResourceLocation RAY_TILE = texture("effects/mdray_small/tile");
    private static final ResourceLocation RAY_OUT = texture("effects/mdray_small/blend_out");
    private static final ResourceLocation MD_PARTICLE = texture("effects/md_particle");
    private static final ResourceLocation TP_PARTICLE = texture("effects/tp_particle");
    private static final ResourceLocation[] ORB = sequence("effects/mdball/", 5);
    private static final ResourceLocation[] FORMULA = sequence("effects/formula/", 10);
    private static final Map<Integer, Shock> SHOCKS = new HashMap<>();
    private static final Map<Integer, SingleVisual> SHOCK_CONTEXTS = new HashMap<>();
    private static final Map<Integer, SingleVisual> TELEPORT_CONTEXTS = new HashMap<>();
    private static final SingleKeyTimeline SHOCK_HISTORY = new SingleKeyTimeline();
    private static final SingleKeyTimeline TELEPORT_HISTORY = new SingleKeyTimeline();
    private static final Map<String, LocalInput> SINGLE_INPUTS = new HashMap<>();
    private static Entity singlePlayer;
    private static Object singleConnection;
    private static long singleEpoch = -1;
    private static final List<Orb> ORBS = new ArrayList<>();
    private static final List<SmallRay> RAYS = new ArrayList<>();
    private static final List<ClassicParticle> PARTICLES = new ArrayList<>();
    private record RadiationMark(java.util.UUID uuid,int ticks) {}
    private static final Map<Integer,RadiationMark> RADIATION_MARKS=new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static ClientLevel activeLevel;
    private static Marker marker;
    private static MultiBufferSource.BufferSource buffers;
    private static long clockMillis, lastWallMillis = Util.getMillis();
    private static PoseStack lastHandStack;

    private ClassicFirstSkillEffects() {}

    /** Packet data is copied before crossing threads; stale-world callbacks are discarded. */
    public static void receive(CompoundTag data) {
        if (data == null) return;
        CompoundTag tag = data.copy();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel target = minecraft.level;
        Entity localPlayer = minecraft.player;
        Object connection = minecraft.getConnection();
        long epoch = AcademyClient.inputSessionEpoch();
        minecraft.execute(() -> {
            if (target == null || minecraft.level != target) return;
            String kind = tag.getString("kind");
            boolean singleKey = isSingleKind(kind);
            if (singleKey && (minecraft.player != localPlayer || minecraft.getConnection() != connection
                    || !AcademyClient.sameInputSessionEpoch(epoch))) return;
            synchronizeWorld(minecraft);
            updateClock(minecraft);
            int entityId = tag.getInt("entity");
            Entity caster = target.getEntity(entityId);
            Vec3 origin = vector(tag, "x", "y", "z");
            if (singleKey) {
                receiveSingleKey(tag, minecraft, target, caster, origin);
                return;
            }
            switch (tag.getString("kind")) {
                case "radiation_mark" -> {
                    int ticks=tag.getInt("ticks");
                    if(caster instanceof LivingEntity&&tag.hasUUID("entity_uuid")&&caster.getUUID().equals(tag.getUUID("entity_uuid"))&&ticks>0&&ticks<=12000&&(RADIATION_MARKS.size()<128||RADIATION_MARKS.containsKey(entityId)))
                        RADIATION_MARKS.put(entityId,new RadiationMark(caster.getUUID(),ticks));
                }
                case "electron_bomb_charge" -> {
                    int life = tag.getInt("life");
                    Vec3 offset = vector(tag, "offset_x", "offset_y", "offset_z");
                    if (finite(origin) && finite(offset) && (life == 5 || life == 20)
                            && offset.lengthSqr() <= 16 && ORBS.size() < 128) {
                        if(caster instanceof Player player)ClassicMeltdownerBeamEffects.endMainRay(player);
                        ORBS.add(new Orb(entityId, origin, offset, life, clockMillis));
                    }
                }
                case "electron_bomb" -> {
                    Vec3 direction = vector(tag, "dx", "dy", "dz");
                    double length = tag.getDouble("length");
                    if (finite(origin) && finite(direction) && direction.lengthSqr() > 1.0E-12
                            && Double.isFinite(length) && length > 0 && length <= 64 && RAYS.size() < 128) {
                        if(caster instanceof LivingEntity living)ClassicMeltdownerBeamEffects.endMainRay(living);
                        RAYS.add(new SmallRay(origin, direction.normalize(), length, clockMillis));
                        target.playLocalSound(origin.x, origin.y, origin.z, sound("md.ray_small"),
                                SoundSource.MASTER, .5F, 1, false);
                    }
                }
                default -> { }
            }
        });
    }

    public static boolean ownsSingleKey(String skill) {
        return "dir_shock".equals(skill) || "threatening_teleport".equals(skill);
    }

    /** Shared Host.down owns allocation; this records the one input without sending or predicting. */
    public static void startLocal(String skill, long input) {
        if (!ownsSingleKey(skill) || input <= 0) return;
        synchronizeWorld(Minecraft.getInstance());
        SINGLE_INPUTS.put(skill, new LocalInput(input, AcademyClient.serverSingleEpoch()));
    }

    /** Physical key-up retains pending ownership until its exact authoritative terminal/rejection. */
    public static void endLocal(String skill, long input, boolean abort) {
        // These two families have no predicted loop to close; a key-up is not a server terminal.
    }

    /** Rejection in the current owner epoch; epoch retirement uses the captured-epoch overload. */
    public static void retireLocal(String skill, long input) {
        retireLocal(skill, input, AcademyClient.serverSingleEpoch());
    }

    /** Deferred END cleanup never clears a new epoch's same-input gesture or independent result. */
    public static void retireLocal(String skill, long input, long ownerEpoch) {
        if (!ownsSingleKey(skill) || input <= 0 || ownerEpoch <= 0) return;
        LocalInput pending = SINGLE_INPUTS.get(skill);
        if (pending != null && pending.input == input && pending.epoch == ownerEpoch) SINGLE_INPUTS.remove(skill);
        Entity player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean shock = skill.equals("dir_shock");
        Map<Integer, SingleVisual> contexts = shock ? SHOCK_CONTEXTS : TELEPORT_CONTEXTS;
        SingleVisual current = contexts.get(player.getId());
        if (current == null || current.input != input || current.epoch != ownerEpoch
                || !current.uuid.equals(player.getUUID())) return;
        contexts.remove(player.getId());
        if (shock) {
            Shock visible = SHOCKS.get(player.getId());
            if (visible != null && !visible.punch && current.equals(visible.identity)) SHOCKS.remove(player.getId());
        } else if (marker != null && current.equals(marker.identity)) marker = null;
    }

    private record LocalInput(long input, long epoch) {}

    /** Only DS/TT state belongs to this session reset. Other first-skill effects keep their lifecycle. */
    public static void clearSingleKeyContexts() {
        SHOCK_CONTEXTS.clear(); TELEPORT_CONTEXTS.clear();
        SHOCK_HISTORY.clear(); TELEPORT_HISTORY.clear(); SINGLE_INPUTS.clear();
        SHOCKS.clear(); marker = null; lastHandStack = null;
    }

    private static boolean isSingleKind(String kind) {
        return kind.equals("dir_shock_prepare") || kind.equals("dir_shock_abort") || kind.equals("dir_shock")
                || kind.equals("threatening_teleport_start") || kind.equals("threatening_teleport_abort")
                || kind.equals("threatening_teleport");
    }

    private static SingleVisual identity(CompoundTag tag, Entity caster) {
        if (!tag.contains("entity", Tag.TAG_INT) || !tag.contains("input", Tag.TAG_LONG)
                || !tag.contains("token", Tag.TAG_LONG) || !tag.contains("tick", Tag.TAG_LONG)
                || !tag.contains("owner_epoch", Tag.TAG_LONG)
                || !tag.hasUUID("entity_uuid") || caster == null || caster.isRemoved()
                || !caster.getUUID().equals(tag.getUUID("entity_uuid"))) return null;
        long input = tag.getLong("input"), token = tag.getLong("token"), tick = tag.getLong("tick");
        long ownerEpoch = tag.getLong("owner_epoch");
        return input >= 0 && token > 0 && tick >= 0 && ownerEpoch >= 0 && (input == 0 || ownerEpoch > 0)
                ? new SingleVisual(caster.getId(), caster.getUUID(), input, token, ownerEpoch) : null;
    }

    /** Observer caches retire absent/replaced actors without inventing a held-context timeout. */
    private static void pruneSingleKeyContexts(ClientLevel level) {
        pruneSingleKeyContexts(level, SHOCK_CONTEXTS, true);
        pruneSingleKeyContexts(level, TELEPORT_CONTEXTS, false);
    }

    private static void pruneSingleKeyContexts(ClientLevel level, Map<Integer, SingleVisual> contexts,
                                               boolean shock) {
        var iterator = contexts.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            SingleVisual identity = entry.getValue();
            Entity caster = level.getEntity(entry.getKey());
            if (caster != null && caster.getUUID().equals(identity.uuid)) continue;
            iterator.remove();
            if (shock) {
                Shock visible = SHOCKS.get(entry.getKey());
                if (visible != null && identity.equals(visible.identity)) SHOCKS.remove(entry.getKey());
            } else if (marker != null && identity.equals(marker.identity)) marker = null;
        }
    }

    private static void receiveSingleKey(CompoundTag tag, Minecraft minecraft, ClientLevel target,
                                         Entity caster, Vec3 origin) {
        pruneSingleKeyContexts(target);
        SingleVisual identity = identity(tag, caster);
        if (identity == null) return;
        String kind = tag.getString("kind");
        boolean shock = kind.startsWith("dir_shock");
        String skill = shock ? "dir_shock" : "threatening_teleport";
        SingleKeyTimeline history = shock ? SHOCK_HISTORY : TELEPORT_HISTORY;
        Map<Integer, SingleVisual> contexts = shock ? SHOCK_CONTEXTS : TELEPORT_CONTEXTS;
        int entityId = identity.entity;
        long input = identity.input, token = identity.token, tick = tag.getLong("tick");
        boolean local = caster == minecraft.player;
        if (local && input > 0 && (!AcademyClient.singleOwnerEpochMatches(identity.epoch)
                || AcademyClient.singleTokenConflicts(skill, input, token))) return;
        SingleVisual current = contexts.get(entityId);
        if (current != null && current.token == token
                && (current.input != input || !current.uuid.equals(identity.uuid))) return;
        boolean start = kind.equals("dir_shock_prepare") || kind.equals("threatening_teleport_start");
        if (start) {
            double range = tag.contains("range") ? tag.getDouble("range") : tag.getDouble("length");
            if (!caster.isAlive() || !shock && (!Double.isFinite(range) || range < 8 || range > 15)
                    || contexts.size() >= 128 && !contexts.containsKey(entityId)
                    || shock && SHOCKS.size() >= 128 && !SHOCKS.containsKey(entityId)) return;
            // This query never mutates the binding. Reject stale local input before replacing visuals/history.
            if (local && (input > 0 && !AcademyClient.singleStartAllowed(skill, input, token)
                    || input == 0 && !AcademyClient.legacySingleStartAllowed(skill))) return;
            if (!history.acceptStart(identity.uuid, input, token, tick)) return;
            contexts.put(entityId, identity);
            if (shock) SHOCKS.put(entityId, new Shock(clockMillis, false, identity));
            else if (local) marker = new Marker(entityId, range, caster.position(), identity);
            if (local) AcademyClient.acceptedSingleStart(skill, input, token);
            return;
        }
        boolean perform = kind.equals(skill);
        // Validate the independent result before committing its terminal tombstone.
        if (perform && !shock && (!finite(origin) || !tag.contains("success", Tag.TAG_BYTE)
                || !tag.getBoolean("success"))) return;
        boolean terminalAllowed = !local || AcademyClient.singleTerminalAllowed(skill, input, token);
        if (!history.acceptTerminal(identity.uuid, input, token, tick)) return;
        if (identity.equals(current)) contexts.remove(entityId);
        if (local && terminalAllowed) {
            AcademyClient.acceptedSingleEnd(skill, input, token);
            LocalInput pending = SINGLE_INPUTS.get(skill);
            if (pending != null && pending.input == input && pending.epoch == identity.epoch) SINGLE_INPUTS.remove(skill);
        }
        if (shock) {
            Shock visible = SHOCKS.get(entityId);
            if (!perform) {
                if (visible != null && !visible.punch && identity.equals(visible.identity)) SHOCKS.remove(entityId);
                return;
            }
            // A valid old hit remains audible; its punch cannot replace a newer prepared gesture.
            if ((visible == null || identity.equals(visible.identity) || visible.punch && token > visible.identity.token)
                    && (SHOCKS.size() < 128 || visible != null))
                SHOCKS.put(entityId, new Shock(clockMillis, true, identity));
            followingSound(caster, "vecmanip.directed_shock");
            return;
        }
        if (marker != null && identity.equals(marker.identity)) marker = null;
        if (!perform) return;
        // No direction-only fallback: the exact drop endpoint is necessary for this effect.
        followingSound(caster, "tp.tp");
        teleportTrail(caster, origin);
        int tier = tag.contains("critical_tier") ? tag.getInt("critical_tier") : -1;
        Entity hit = tag.contains("target") ? target.getEntity(tag.getInt("target")) : null;
        if (tier >= 0 && tier <= 2 && hit != null && local)
            NeoForge.EVENT_BUS.post(new TeleporterCriticalHitEvent(caster, hit, tier));
    }

    private record SingleVisual(int entity, UUID uuid, long input, long token, long epoch) {}

    /** Packet order is separate from visibility and the source binding's immutable node epoch. */
    public static final class SingleKeyTimeline {
        private static final int MAX_HISTORY = 1024;
        private record Key(UUID owner, long token) {}
        private record Entry(long input, long tick, boolean terminal) {}
        private final Map<UUID, Long> latest = new LinkedHashMap<>();
        private final Map<Key, Entry> events = new LinkedHashMap<>();
        private final Map<UUID, Long> retired = new LinkedHashMap<>();
        public boolean acceptStart(UUID owner, long input, long token, long tick) {
            if (!valid(owner, input, token, tick) || token <= latest.getOrDefault(owner, 0L)
                    || events.containsKey(new Key(owner, token))) return false;
            remember(owner, token, new Entry(input, tick, false));
            return true;
        }
        public boolean acceptTerminal(UUID owner, long input, long token, long tick) {
            if (!valid(owner, input, token, tick)) return false;
            Entry previous = events.get(new Key(owner, token));
            if (previous != null && (previous.terminal || previous.input != input || tick < previous.tick)
                    || previous == null && token <= retired.getOrDefault(owner, 0L)) return false;
            // An observer may first receive the result. Older results cannot change the newer held identity.
            remember(owner, token, new Entry(input, tick, true));
            return true;
        }
        private static boolean valid(UUID owner, long input, long token, long tick) {
            return owner != null && input >= 0 && token > 0 && tick >= 0;
        }
        private void remember(UUID owner, long token, Entry entry) {
            long highest = Math.max(token, latest.getOrDefault(owner, 0L));
            latest.remove(owner); latest.put(owner, highest);
            Key key = new Key(owner, token);
            events.remove(key); events.put(key, entry);
            while (latest.size() > MAX_HISTORY) latest.remove(latest.keySet().iterator().next());
            while (events.size() > MAX_HISTORY) {
                Key oldest = events.keySet().iterator().next();
                events.remove(oldest);
                long floor = Math.max(oldest.token, retired.getOrDefault(oldest.owner, 0L));
                retired.remove(oldest.owner); retired.put(oldest.owner, floor);
            }
            while (retired.size() > MAX_HISTORY) retired.remove(retired.keySet().iterator().next());
        }
        public int historySize() { return events.size(); }
        public void clear() { latest.clear(); events.clear(); retired.clear(); }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeWorld(minecraft);
        updateClock(minecraft);
        if (minecraft.level == null || minecraft.isPaused()) return;
        SHOCKS.entrySet().removeIf(entry -> minecraft.level.getEntity(entry.getKey()) == null
                || clockMillis - entry.getValue().created >= (entry.getValue().punch
                ? ClassicFirstSkillTimeline.PUNCH_CONTEXT_MS : ClassicFirstSkillTimeline.MAX_HOLD_MS));
        ORBS.removeIf(orb -> clockMillis - orb.created >= orb.life * 50L
                || minecraft.level.getEntity(orb.caster) == null);
        RAYS.removeIf(ray -> clockMillis - ray.created >= ClassicFirstSkillTimeline.SMALL_RAY_MS);
        // Advance previously emitted particles before adding this tick's ray particles.
        PARTICLES.removeIf(particle -> !particle.tick());
        for (SmallRay ray : RAYS) {
            Vec3 position = ray.origin.add(ray.direction.scale(random(0, 10)));
            addParticle(new ClassicParticle(MD_PARTICLE, position, randomVector(-.015, .015),
                    random(.05, .07), random(.3, .6), 255, RANDOM.nextInt(30) + 25, 20, 5));
        }
        RADIATION_MARKS.entrySet().removeIf(entry->{
            Entity caster=minecraft.level.getEntity(entry.getKey());RadiationMark mark=entry.getValue();
            if(!(caster instanceof LivingEntity)||!caster.getUUID().equals(mark.uuid())||mark.ticks()<=1)return true;
            entry.setValue(new RadiationMark(mark.uuid(),mark.ticks()-1));
            for(int times=RANDOM.nextInt(3);times>0;times--){
                double radius=random(.6,.7)*caster.getBbWidth(),angle=random(0,Math.PI*2);
                Vec3 at=caster.position().add(radius*Math.sin(angle),random(0,caster.getBbHeight()),radius*Math.cos(angle));
                addParticle(new ClassicParticle(MD_PARTICLE,at,randomVector(-.02,.02),.05f+RANDOM.nextFloat()*(.07f-.05f),random(.3,.6),255,RANDOM.nextInt(30)+25,20,5));
            }
            return false;
        });
        if (marker != null) updateMarker(minecraft);
    }

    /**
     * RenderHandEvent's stack is the shared first-person stack, not a per-arm push/pop.
     * Apply once per fresh GameRenderer stack and let vanilla render its hand/item geometry.
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeWorld(minecraft);
        updateClock(minecraft);
        if (minecraft.player == null) return;
        Shock shock = SHOCKS.get(minecraft.player.getId());
        if (shock == null || lastHandStack == event.getPoseStack()) return;
        long age = clockMillis - shock.created;
        if (age >= (shock.punch ? ClassicFirstSkillTimeline.PUNCH_CONTEXT_MS : ClassicFirstSkillTimeline.MAX_HOLD_MS)) return;
        lastHandStack = event.getPoseStack();
        var transform = shock.punch ? ClassicFirstSkillTimeline.punch(age) : ClassicFirstSkillTimeline.prepare(age);
        PoseStack poses = event.getPoseStack();
        poses.translate(transform.x(), transform.y(), transform.z());
        poses.mulPose(Axis.XP.rotationDegrees((float) transform.rotationX()));
        poses.mulPose(Axis.YP.rotationDegrees((float) transform.rotationY()));
        // Keep vanilla FOV, modern arm models, hand selection and item animations. No reflection.
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft minecraft = Minecraft.getInstance();
        synchronizeWorld(minecraft);
        updateClock(minecraft);
        if (minecraft.level == null || ORBS.isEmpty() && RAYS.isEmpty() && PARTICLES.isEmpty() && marker == null) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        Set<RenderType> used = new LinkedHashSet<>();
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        poses.pushPose();
        try {
            poses.translate(-camera.x, -camera.y, -camera.z);
            for (Orb orb : ORBS) renderOrb(orb, minecraft, event, poses, partial, used);
            for (SmallRay ray : RAYS) renderRay(ray, camera, poses.last().pose(), used);
            for (ClassicParticle particle : PARTICLES) {
                poses.pushPose();
                Vec3 position = particle.previous.lerp(particle.position, partial);
                poses.translate(position.x, position.y, position.z);
                poses.mulPose(event.getCamera().rotation());
                double alpha = particle.alpha * ClassicFirstSkillTimeline.particleAlpha(
                        particle.age, particle.life, particle.fade, particle.fadeIn);
                billboard(buffer(particle.texture, used), poses.last().pose(), particle.size, .5,
                        particle.color, particle.color, particle.color, alpha);
                poses.popPose();
            }
            if (marker != null) renderMarker(marker, minecraft, poses, partial, used);
            for (RenderType type : used) buffers.endBatch(type);
        } finally {
            poses.popPose();
        }
    }

    private static void renderOrb(Orb orb, Minecraft minecraft, RenderLevelStageEvent event,
                                  PoseStack poses, float partial, Set<RenderType> used) {
        long age = clockMillis - orb.created;
        if (age >= orb.life * 50L) return;
        Entity caster = minecraft.level.getEntity(orb.caster);
        if (caster == null) return;
        orb.wiggle(clockMillis);
        Vec3 position = interpolatedPosition(caster, partial).add(orb.offset);
        double phase = age / 300.0;
        position = position.add(.03 * Math.sin(phase), .04 * Math.cos(phase * 1.4 + Math.PI / 3.5),
                .03 * Math.cos(phase));
        double alpha = ClassicFirstSkillTimeline.orbAlpha(orb.life, age);
        double size = ClassicFirstSkillTimeline.orbSize(orb.life, age);
        poses.pushPose();
        poses.translate(position.x, position.y, position.z);
        poses.mulPose(event.getCamera().rotation());
        // RenderIcon has asymmetric vertical coordinates [-.25, .75], rather than a centered quad.
        billboard(buffer(GLOW, used), poses.last().pose(), .7 * size, .25,
                255, 255, 255, alpha * (.3 + orb.alphaWiggle * .7));
        billboard(buffer(ORB[orb.texture], used), poses.last().pose(), .5 * size, .25,
                255, 255, 255, alpha * (.8 + .2 * orb.alphaWiggle));
        poses.popPose();
    }

    private static void renderRay(SmallRay ray, Vec3 camera, Matrix4f matrix, Set<RenderType> used) {
        long age = clockMillis - ray.created;
        if (age >= ClassicFirstSkillTimeline.SMALL_RAY_MS) return;
        ray.wiggle(clockMillis);
        double alpha = ClassicFirstSkillTimeline.rayAlpha(age);
        double width = ClassicFirstSkillTimeline.rayWidthScale(age);
        double length = ray.length * ClassicFirstSkillTimeline.rayLengthScale(age);
        if (length <= 0 || width <= 0 || alpha <= 0) return;
        if(cn.academy.port.client.canon.AgNativeBeams.draw(buffers,matrix,camera,ray.origin,ray.origin.add(ray.direction.scale(length)),.045*width,alpha,new float[]{.32f,1,.23f}))return;
        Vec3 u = perpendicular(ray.direction), v = ray.direction.cross(u).normalize();
        Vec3 up = ray.origin.subtract(camera).cross(ray.direction);
        up = up.lengthSqr() < 1.0E-12 ? u : up.normalize();
        Vec3 end = ray.origin.add(ray.direction.scale(length));
        Vec3 mid1 = ray.origin.add(ray.direction.scale(.3)), mid2 = end.add(ray.direction.scale(-.3));
        // RendererRayGlow multiplies alpha twice via getGlowAlpha, including its inherited wiggle.
        double glowAlpha = .5 * alpha * (.9 + ray.glowWiggle) * alpha;
        board(buffer(RAY_IN, used), matrix, ray.origin, mid1, up, .3 * width, glowAlpha);
        board(buffer(RAY_TILE, used), matrix, mid1, mid2, up, .3 * width, glowAlpha);
        board(buffer(RAY_OUT, used), matrix, mid2, end, up, .3 * width, glowAlpha);
        VertexConsumer cylinder = buffer(WHITE, used);
        cylinder(cylinder, matrix, ray, u, v, length, .03 * width, .98, 216, 248, 216, 230 * alpha);
        cylinder(cylinder, matrix, ray, u, v, length, .045 * width, 1, 106, 242, 106, 50 * alpha);
    }

    /** Original 12 angular divisions, four sqrt-radius head slices, and both tapered caps. */
    private static void cylinder(VertexConsumer out, Matrix4f matrix, SmallRay ray, Vec3 u, Vec3 v,
                                 double length, double radius, double headFix, int r, int g, int b, double alpha) {
        double offset = radius * (1 - headFix);
        for (int side = 0; side < 12; side++) {
            double a = side * Math.PI * 2 / 12, next = (side + 1) * Math.PI * 2 / 12;
            ringQuad(out, matrix, ray, u, v, radius, radius, radius, length, a, next, r, g, b, alpha);
            for (int slice = 0; slice < 4; slice++) {
                double s0 = slice / 4.0, s1 = (slice + 1) / 4.0;
                double radius0 = radius * Math.sqrt(s0), radius1 = radius * Math.sqrt(s1);
                ringQuad(out, matrix, ray, u, v, radius0, radius1,
                        offset + radius * headFix * s0, offset + radius * headFix * s1,
                        a, next, r, g, b, alpha);
                ringQuad(out, matrix, ray, u, v, radius0, radius1,
                        length + radius - offset - radius * headFix * s0,
                        length + radius - offset - radius * headFix * s1, a, next, r, g, b, alpha);
            }
        }
    }

    private static void ringQuad(VertexConsumer out, Matrix4f matrix, SmallRay ray, Vec3 u, Vec3 v,
                                 double radius0, double radius1, double x0, double x1,
                                 double angle0, double angle1, int r, int g, int b, double alpha) {
        vertex(out, matrix, ring(ray, u, v, x0, radius0, angle0), 0, 0, r, g, b, alpha);
        vertex(out, matrix, ring(ray, u, v, x1, radius1, angle0), 1, 0, r, g, b, alpha);
        vertex(out, matrix, ring(ray, u, v, x1, radius1, angle1), 1, 1, r, g, b, alpha);
        vertex(out, matrix, ring(ray, u, v, x0, radius0, angle1), 0, 1, r, g, b, alpha);
    }

    private static Vec3 ring(SmallRay ray, Vec3 u, Vec3 v, double x, double radius, double angle) {
        return ray.origin.add(ray.direction.scale(x)).add(u.scale(radius * Math.sin(angle)))
                .add(v.scale(radius * Math.cos(angle)));
    }

    private static void updateMarker(Minecraft minecraft) {
        Entity caster = minecraft.level.getEntity(marker.caster);
        if (caster != minecraft.player || !caster.isAlive() || minecraft.player.getMainHandItem().isEmpty()) {
            marker = null;
            return;
        }
        Vec3 from = caster.getEyePosition(), end = from.add(ClassicRaytrace.direction(caster).scale(marker.range));
        Entity nearest = null;
        double closest = 0;
        // Original filEverything rejects blocks in the living-target trace, permitting wall targets.
        for (Entity candidate : minecraft.level.getEntities(caster, new AABB(from, end).inflate(1),
                entity -> (entity instanceof LivingEntity || entity instanceof EnderDragonPart)
                        && entity.isAlive() && !entity.isSpectator() && entity.isPickable())) {
            double distance = ThreateningTeleport.classicEntityDistanceSquared(candidate.getBoundingBox(), from, end);
            if (Double.isFinite(distance) && (distance < closest || closest == 0)) {
                nearest = candidate;
                closest = distance;
            }
        }
        marker.target = nearest;
        if (nearest != null) marker.position = nearest.position();
        else {
            var block = minecraft.level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, caster));
            marker.position = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        }
    }

    private static void renderMarker(Marker mark, Minecraft minecraft, PoseStack poses, float partial,
                                     Set<RenderType> used) {
        Entity target = mark.target;
        Vec3 position = target == null ? mark.position : interpolatedPosition(target, partial);
        double width = target == null ? .5 : target.getBbWidth(), height = target == null ? .5 : target.getBbHeight();
        position = position.add(-width / 2, ClassicFirstSkillTimeline.markerBob(Util.getMillis()), -width / 2);
        int color = target == null ? 0xbabababa : 0xbab2232a;
        double segment = .2 * width;
        used.add(MarkerRenderType.TYPE);
        VertexConsumer out = buffers.getBuffer(MarkerRenderType.TYPE);
        for (int i = 0; i < 8; i++) {
            int corner = i % 4;
            double x = corner == 1 || corner == 2 ? width : 0;
            double z = corner >= 2 ? width : 0;
            double y = i >= 4 ? height : 0;
            Vec3 start = position.add(x, y, z);
            double yaw = Math.toRadians(-90 * corner);
            line(out, poses.last(), start, start.add(0, i < 4 ? segment : -segment, 0), color);
            line(out, poses.last(), start, start.add(segment * Math.cos(yaw), 0, -segment * Math.sin(yaw)), color);
            line(out, poses.last(), start, start.add(segment * Math.sin(yaw), 0, segment * Math.cos(yaw)), color);
        }
    }

    private static void line(VertexConsumer out, PoseStack.Pose pose, Vec3 from, Vec3 to, int color) {
        Vec3 normal = to.subtract(from).normalize();
        out.addVertex(pose.pose(), (float) from.x, (float) from.y, (float) from.z).setColor(color)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
        out.addVertex(pose.pose(), (float) to.x, (float) to.y, (float) to.z).setColor(color)
                .setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void teleportTrail(Entity caster, Vec3 endpoint) {
        Vec3 start = caster.position().add(0, -.5, 0);
        Vec3 delta = endpoint.add(.5, .5, .5).subtract(start);
        double distance = delta.length();
        if (!Double.isFinite(distance) || distance > 64 || distance <= 0) return;
        Vec3 direction = delta.normalize(), cursor = start;
        double step = 1, travelled = 1;
        while (travelled <= distance) {
            cursor = cursor.add(direction.scale(step));
            addParticle(new ClassicParticle(TP_PARTICLE, cursor,
                    new Vec3(random(-.02, .02), random(-.02, .05), random(-.02, .02)),
                    random(.1, .2), random(.6, .8), 255, 20, 20, 5));
            step = random(1, 2);
            travelled += step;
        }
    }

    /** Client counterpart of the legacy TPSkillHelper event; tier does not alter particle count. */
    public static final class TeleporterCriticalHitEvent extends Event {
        private final Entity caster, target;
        private final int tier;
        public TeleporterCriticalHitEvent(Entity caster, Entity target, int tier) {
            if (caster == null || target == null || tier < 0 || tier > 2) throw new IllegalArgumentException("Invalid critical event");
            this.caster = caster;
            this.target = target;
            this.tier = tier;
        }
        public Entity getCaster() { return caster; }
        public Entity getTarget() { return target; }
        public int getTier() { return tier; }
    }

    @SubscribeEvent
    public static void onTeleporterCritical(TeleporterCriticalHitEvent event) {
        if (!event.target.level().isClientSide()) return;
        Entity target = event.target;
        // LambdaLib rangei(from,to) has an exclusive upper bound, unlike a closed range.
        int count = RANDOM.nextInt(3) + 5;
        for (int i = 0; i < count; i++) {
            double angle = random(0, Math.PI * 2), radius = random(target.getBbWidth() * .5, target.getBbWidth() * .7);
            Vec3 position = target.position().add(radius * Math.sin(angle), random(0, target.getBbHeight()), radius * Math.cos(angle));
            addParticle(new ClassicParticle(FORMULA[RANDOM.nextInt(10)], position, randomVector(-.03, .03),
                    random(1, 1.7), random(.6, 1.5), 220, RANDOM.nextInt(5) + 10, 20, 2));
        }
    }

    private static void followingSound(Entity entity, String path) {
        Minecraft.getInstance().getSoundManager().play(new FollowingSound(entity, sound(path)));
    }

    private static final class FollowingSound extends AbstractTickableSoundInstance {
        private final Entity entity;
        FollowingSound(Entity entity, SoundEvent sound) {
            super(sound, SoundSource.MASTER, RandomSource.create());
            this.entity = entity;
            volume = .5F;
            tick();
        }
        @Override public void tick() {
            if (entity.isRemoved()) { stop(); return; }
            x = entity.getX(); y = entity.getY(); z = entity.getZ();
        }
    }

    private record Shock(long created, boolean punch, SingleVisual identity) {}
    private static final class Marker {
        final int caster;
        final double range;
        final SingleVisual identity;
        Vec3 position;
        Entity target;
        Marker(int caster, double range, Vec3 position, SingleVisual identity) {
            this.caster = caster; this.range = range; this.position = position; this.identity = identity;
        }
    }
    private static final class Orb {
        final int caster, life;
        final Vec3 origin, offset;
        final long created;
        long lastFrame;
        int texture;
        double alphaWiggle = .8, acceleration;
        Orb(int caster, Vec3 origin, Vec3 offset, int life, long created) {
            this.caster = caster; this.origin = origin; this.offset = offset; this.life = life; this.created = created;
        }
        void wiggle(long now) {
            if (lastFrame != 0 && now != lastFrame) {
                if (RANDOM.nextInt(8) < 3) acceleration = random(-4, 4);
                alphaWiggle = Mth.clamp(alphaWiggle + acceleration * (now - lastFrame) / 1000, 0, 1);
            }
            if (now != lastFrame && RANDOM.nextInt(8) < 2) texture = RANDOM.nextInt(5);
            lastFrame = now;
        }
    }
    private static final class SmallRay {
        final Vec3 origin, direction;
        final double length;
        final long created;
        long lastFrame;
        double glowWiggle;
        SmallRay(Vec3 origin, Vec3 direction, double length, long created) {
            this.origin = origin; this.direction = direction; this.length = length; this.created = created;
        }
        void wiggle(long now) {
            if (lastFrame != 0 && now != lastFrame)
                glowWiggle = Mth.clamp(glowWiggle + (now - lastFrame) * random(-.4, .4) / 1000, 0, .1);
            lastFrame = now;
        }
    }
    private static final class ClassicParticle {
        final ResourceLocation texture;
        final Vec3 velocity;
        final double size, alpha;
        final int color, life, fade, fadeIn;
        Vec3 position, previous;
        int age;
        ClassicParticle(ResourceLocation texture, Vec3 position, Vec3 velocity, double size, double alpha,
                        int color, int life, int fade, int fadeIn) {
            this.texture = texture; this.position = position; this.previous = position; this.velocity = velocity;
            this.size = size; this.alpha = alpha; this.color = color; this.life = life; this.fade = fade; this.fadeIn = fadeIn;
        }
        boolean tick() { age++; previous = position; position = position.add(velocity); return age <= life + fade; }
    }

    private static final class MarkerRenderType extends RenderType {
        private static final RenderType TYPE = create("academy_classic_threatening_marker", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 2048, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(GameRenderer::getRendertypeLinesShader))
                        .setLineState(new LineStateShard(OptionalDouble.of(3)))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false));
        private MarkerRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size,
                                 boolean crumbling, boolean sort, Runnable setup, Runnable clear) {
            super(name, format, mode, size, crumbling, sort, setup, clear);
        }
    }

    private static VertexConsumer buffer(ResourceLocation texture, Set<RenderType> used) {
        RenderType type = ClassicRenderTypes.world(texture);
        used.add(type);
        return buffers.getBuffer(type);
    }
    private static void billboard(VertexConsumer out, Matrix4f matrix, double size, double below,
                                  int r, int g, int b, double alpha) {
        vertex(out, matrix, new Vec3(-size / 2, -size * below, 0), 0, 1, r, g, b, 255 * alpha);
        vertex(out, matrix, new Vec3(size / 2, -size * below, 0), 1, 1, r, g, b, 255 * alpha);
        vertex(out, matrix, new Vec3(size / 2, size * (1 - below), 0), 1, 0, r, g, b, 255 * alpha);
        vertex(out, matrix, new Vec3(-size / 2, size * (1 - below), 0), 0, 0, r, g, b, 255 * alpha);
    }
    private static void board(VertexConsumer out, Matrix4f matrix, Vec3 start, Vec3 end, Vec3 up, double width, double alpha) {
        Vec3 offset = up.scale(width / 2);
        vertex(out, matrix, start.add(offset), 0, 1, 255, 255, 255, 255 * alpha);
        vertex(out, matrix, start.subtract(offset), 0, 0, 255, 255, 255, 255 * alpha);
        vertex(out, matrix, end.subtract(offset), 1, 0, 255, 255, 255, 255 * alpha);
        vertex(out, matrix, end.add(offset), 1, 1, 255, 255, 255, 255 * alpha);
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 point, float u, float v,
                               int r, int g, int b, double alpha) {
        out.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z).setUv(u, v)
                .setColor(r, g, b, Mth.clamp((int) Math.round(alpha), 0, 255));
    }
    private static Vec3 interpolatedPosition(Entity entity, float partial) {
        return new Vec3(Mth.lerp(partial, entity.xo, entity.getX()), Mth.lerp(partial, entity.yo, entity.getY()),
                Mth.lerp(partial, entity.zo, entity.getZ()));
    }
    private static Vec3 perpendicular(Vec3 direction) {
        return direction.cross(Math.abs(direction.y) < .9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
    }
    private static void addParticle(ClassicParticle particle) { if (PARTICLES.size() < 2048) PARTICLES.add(particle); }
    private static double random(double low, double high) { return low + RANDOM.nextDouble() * (high - low); }
    private static Vec3 randomVector(double low, double high) { return new Vec3(random(low, high), random(low, high), random(low, high)); }
    private static boolean finite(Vec3 vector) { return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z); }
    private static Vec3 vector(CompoundTag tag, String x, String y, String z) { return new Vec3(tag.getDouble(x), tag.getDouble(y), tag.getDouble(z)); }
    private static SoundEvent sound(String path) { return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", path)); }
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    private static ResourceLocation[] sequence(String path, int count) {
        ResourceLocation[] result = new ResourceLocation[count];
        for (int i = 0; i < count; i++) result[i] = texture(path + i);
        return result;
    }
    private static void synchronizeWorld(Minecraft minecraft) {
        long epoch = AcademyClient.inputSessionEpoch();
        if (singlePlayer != minecraft.player || singleConnection != minecraft.getConnection() || singleEpoch != epoch) {
            clearSingleKeyContexts();
            singlePlayer = minecraft.player; singleConnection = minecraft.getConnection(); singleEpoch = epoch;
        }
        if (activeLevel == minecraft.level) return;
        activeLevel = minecraft.level;
        clearSingleKeyContexts(); ORBS.clear(); RAYS.clear(); PARTICLES.clear(); RADIATION_MARKS.clear();
        clockMillis = 0;
        lastWallMillis = Util.getMillis();
    }
    private static void updateClock(Minecraft minecraft) {
        long now = Util.getMillis();
        if (minecraft.level != null && !minecraft.isPaused()) clockMillis += Math.max(0, now - lastWallMillis);
        lastWallMillis = now;
    }
}
