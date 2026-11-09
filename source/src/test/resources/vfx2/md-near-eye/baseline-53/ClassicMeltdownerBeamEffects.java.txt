/* Original AcademyCraft1.0.7 / LambdaLib1.2.3 client adaptation. See project NOTICE
 * and staged MELTDOWNER_BEAM_VISUALS.md. AcademyCraft GPLv3; LambdaLib MIT. */
package cn.academy.port.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static cn.academy.port.client.ClassicMeltdownerBeamTimeline.*;

/**
 * Cosmetic client adapter. Only source local abilities walking speed is temporarily changed;
 * no server movement attribute, ability-resource, damage, block, cooldown or packet mutation.
 * UUID/session/token/input fences authorize no speculative beam or sound. GPU/audio unverified.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ClassicMeltdownerBeamEffects {
    private static final ResourceLocation LUCK_PARTICLE = texture("effects/md_particle_luck");
    private static final ResourceLocation MD_PARTICLE = texture("effects/md_particle"), WHITE = texture("port/white");
    private static final ResourceLocation[] MD_TEXTURES = rayTextures("mdray"), MINE_TEXTURES = rayTextures("mdray_small");
    private static final ResourceLocation[] EXPERT_TEXTURES = rayTextures("mdray_expert"), LUCK_TEXTURES = rayTextures("mdray_luck");
    private static final Map<Integer, Context> EXPERT_MINERS = new HashMap<>(), LUCK_MINERS = new HashMap<>();
    private static final Tokens EXPERT_TOKENS = new Tokens(), LUCK_TOKENS = new Tokens();
    private static final ClassicMeltdownerStarterTimeline.Input EXPERT_INPUT = new ClassicMeltdownerStarterTimeline.Input(), LUCK_INPUT = new ClassicMeltdownerStarterTimeline.Input();
    private static final Map<Integer, Context> CHARGES = new HashMap<>(), MINERS = new HashMap<>();
    private static final Tokens CHARGE_TOKENS = new Tokens(), MINE_TOKENS = new Tokens();
    private static final ClassicMeltdownerStarterTimeline.Input CHARGE_INPUT = new ClassicMeltdownerStarterTimeline.Input(), MINE_INPUT = new ClassicMeltdownerStarterTimeline.Input();
    private static final ClassicMeltdownerStarterTimeline.PauseClock CLOCK = new ClassicMeltdownerStarterTimeline.PauseClock();
    private static final List<Ray> RAYS = new ArrayList<>();
    private static final List<MdParticle> PARTICLES = new ArrayList<>();
    private static final List<FollowingSound> ONE_SHOTS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static ClientLevel activeLevel;
    private static Player activePlayer;
    private static Object activeConnection;
    private static volatile long callbackEpoch;
    private static MultiBufferSource.BufferSource buffers;
    private ClassicMeltdownerBeamEffects() {}

    public static boolean owns(String skill) { return MELTDOWNER.equals(skill) || MINE.equals(skill) || ClassicAdvancedMineRayTimeline.owns(skill); }
    private static Map<Integer, Context> contexts(String skill) { return switch(skill) {case "mine_ray_expert" -> EXPERT_MINERS;case "mine_ray_luck" -> LUCK_MINERS;default -> MELTDOWNER.equals(skill) ? CHARGES : MINERS;}; }
    private static Tokens tokens(String skill) { return switch(skill) {case "mine_ray_expert" -> EXPERT_TOKENS;case "mine_ray_luck" -> LUCK_TOKENS;default -> MELTDOWNER.equals(skill) ? CHARGE_TOKENS : MINE_TOKENS;}; }
    private static ClassicMeltdownerStarterTimeline.Input input(String skill) { return switch(skill) {case "mine_ray_expert" -> EXPERT_INPUT;case "mine_ray_luck" -> LUCK_INPUT;default -> MELTDOWNER.equals(skill) ? CHARGE_INPUT : MINE_INPUT;}; }
    /** Returns slot_press_token correlation. Starts no unacknowledged particles/audio/slowdown. */
    public static long startLocal(String skill) {
        if (!owns(skill)) return 0;
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        Context old = mc.player == null ? null : contexts(skill).remove(mc.player.getId());
        if (old != null) { tokens(skill).end(old.key, old.token); old.hide(mc); }
        return input(skill).press();
    }
    /** Release/abort hides held effects immediately. Keep correlation for a late terminal beam. */
    public static void endLocal(String skill) {
        if (!owns(skill)) return;
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); input(skill).end();
        Context current = mc.player == null ? null : contexts(skill).get(mc.player.getId());
        if (current != null) current.hide(mc);
    }
    public static int localTicks(String skill) {
        if (!owns(skill)) return -1;
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc);
        Context current = mc.player == null ? null : contexts(skill).get(mc.player.getId());
        return current == null || !current.shown || !current.valid(mc) ? -1 : current.ticks;
    }
    public static boolean delegateActive(String skill) {
        if (!owns(skill)) return false;
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc);
        if (mc.player == null || !mc.player.isAlive()) return false;
        Context current = contexts(skill).get(mc.player.getId());
        return input(skill).heldPending() || current != null && current.shown && current.valid(mc);
    }

    /** Copy primitive NBT before scheduling. Every kind requires caster UUID, token AND input. */
    public static void receive(CompoundTag data) {
        if (data == null) return;
        CompoundTag tag = data.copy(); String kind = tag.getString("kind");
        if (!List.of("meltdowner_start", "meltdowner_end", "meltdowner_ray", "meltdowner_reflection",
                "mine_ray_basic_start", "mine_ray_basic_end", "mine_ray_basic_particles",
                "mine_ray_expert_start", "mine_ray_expert_end", "mine_ray_expert_particles",
                "mine_ray_luck_start", "mine_ray_luck_end", "mine_ray_luck_particles").contains(kind)
                || !tag.contains("entity", Tag.TAG_INT) || !tag.hasUUID("entity_uuid")
                || !tag.contains("token", Tag.TAG_LONG) || tag.getLong("token") <= 0
                || !tag.contains("input", Tag.TAG_LONG) || tag.getLong("input") < 0) return;
        boolean start = kind.endsWith("_start"), end = kind.endsWith("_end"), particles = kind.endsWith("_particles");
        boolean ray = kind.equals("meltdowner_ray"), reflection = kind.equals("meltdowner_reflection");
        if (ray || reflection) {
            if (!tag.contains("index", Tag.TAG_INT) || tag.getInt("index") != (ray ? 0 : 1)
                    || !vectorTags(tag, "x", "y", "z") || !vectorTags(tag, "dx", "dy", "dz")
                    || !Double.isFinite(vector(tag, "dx", "dy", "dz").lengthSqr())
                    || vector(tag, "dx", "dy", "dz").lengthSqr() < 1E-12
                    || !tag.contains("length", Tag.TAG_DOUBLE) || !Double.isFinite(tag.getDouble("length"))
                    || tag.getDouble("length") < 0 || tag.getDouble("length") > (ray ? 30 : 10)
                    || reflection && tag.getDouble("length") != 10) return;
            if (ray && (!tag.contains("charge", Tag.TAG_INT) || tag.getInt("charge") < MIN_CHARGE || tag.getInt("charge") > MAX_CHARGE)) return;
        }
        if (particles && (!tag.contains("index", Tag.TAG_INT) || tag.getInt("index") < 0
                || !tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT) || !tag.contains("z", Tag.TAG_INT))) return;
        UUID uuid = tag.getUUID("entity_uuid");
        Minecraft mc = Minecraft.getInstance(); ClientLevel level = mc.level; Player local = mc.player;
        Object connection = mc.getConnection(); long epoch = callbackEpoch;
        mc.execute(() -> {
            if (level == null || local == null || mc.level != level || mc.player != local
                    || mc.getConnection() != connection || callbackEpoch != epoch) return;
            synchronizeSession(mc); updateClock(mc);
            String skill = kind.startsWith("meltdowner_") ? MELTDOWNER : ClassicAdvancedMineRayTimeline.KINDS.contains(kind) ? ClassicAdvancedMineRayTimeline.skill(kind) : MINE;
            int id = tag.getInt("entity"); long token = tag.getLong("token"), nonce = tag.getLong("input");
            Caster key = new Caster(id, uuid); boolean localPacket = id == local.getId();
            // A stale nonce/UUID cannot alter a newer local context or its replay history.
            if (localPacket && (!uuid.equals(local.getUUID()) || !input(skill).accepts(nonce))) return;
            Map<Integer, Context> all = contexts(skill); Tokens history = tokens(skill); Context current = all.get(id);
            Entity entity = level.getEntity(id);
            if (current != null && !current.key.equals(key)) {
                if (current.valid(mc)) return;
                all.remove(id); history.end(current.key, current.token); current.hide(mc); current = null;
            }
            if (end) {
                if ((current == null && (!validCaster(mc, entity) || !entity.getUUID().equals(uuid))) || !history.end(key, token)) return;
                if (localPacket) AcademyClient.acceptedSingleEnd(skill, nonce, token);
                if (current != null && current.token <= token) { all.remove(id); current.hide(mc); }
                if (localPacket) input(skill).complete(nonce);
                return;
            }
            if (start) {
                if (!validCaster(mc, entity) || !entity.getUUID().equals(uuid) || all.size() >= 128 && current == null || !history.start(key, token)) return;
                if (current != null) current.hide(mc);
                Context next = new Context((Player)entity, level, connection, skill, token, nonce, !localPacket || input(skill).mayShowStart(nonce), localPacket);
                all.put(id, next);
                if (localPacket) AcademyClient.acceptedSingleStart(skill, nonce, token);
                if (next.shown) {
                    if (MELTDOWNER.equals(skill)) {
                        next.sound = new FollowingSound(next.caster, level, connection, CHARGE_SOUND, CHARGE_VOLUME, false, next);
                    } else {
                        next.ray = new Ray(next.caster, level, connection, ClassicAdvancedMineRayTimeline.spec(skill), Vec3.ZERO, new Vec3(0, 0, 1), 15, CLOCK.elapsed());
                        next.updateMineRay(local);
                        if (MINE.equals(skill)) playOneShot(next.caster, level, connection, MINE_START_SOUND, MINE_START_VOLUME);
                        else playOneShot(next.caster, level, connection, ClassicAdvancedMineRayTimeline.startup(skill), MINE_START_VOLUME);
                        next.sound = new FollowingSound(next.caster, level, connection, MINE_LOOP_SOUND, MINE_LOOP_VOLUME, true, next);
                    }
                    mc.getSoundManager().play(next.sound);
                }
                return;
            }
            if (current == null || current.token != token || current.nonce != nonce || !history.active(key, token) || !current.valid(mc)) return;
            int index = tag.getInt("index");
            if (particles) {
                if (!current.shown || !current.particleSequence.accept(index)) return;
                int count = mineParticleCount(RANDOM);
                for (int i = 0; i < count; i++) {
                    Vec3 at = vec(mineParticlePosition(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"), random(-.2, 1.2), random(-.2, 1.2), random(-.2, 1.2)));
                    addParticle(at, new Vec3(random(-.06, .06), random(-.06, .06), random(-.06, .06)), true);
                }
            } else if (ray || reflection) {
                if (current.rayIndices.get(index) || RAYS.size() >= 256) return;
                current.rayIndices.set(index);
                Vec3 origin = vector(tag, "x", "y", "z");
                if (ray) origin = vec(mainOrigin(point(origin), localPacket));
                RAYS.add(new Ray(current.caster, level, connection, MD, origin, vector(tag, "dx", "dy", "dz").normalize(), tag.getDouble("length"), CLOCK.elapsed()));
                if (ray) playOneShot(current.caster, level, connection, PERFORM_SOUND, PERFORM_VOLUME);
                // Source reflected ray has no additional meltdowner sound and no observer+X origin fix.
            }
        });
    }
    /** Called on leave/death/player replacement as well as self-checked on all tick/render events. */
    public static void clear() { callbackEpoch++; clearOwned(); }
    private static void clearOwned() {
        Minecraft mc = Minecraft.getInstance();
        for (Context context : CHARGES.values()) context.hide(mc);
        for (String skill : List.of(MINE, ClassicAdvancedMineRayTimeline.EXPERT_ID, ClassicAdvancedMineRayTimeline.LUCK_ID))
            for (Context context : contexts(skill).values()) context.hide(mc);
        for (FollowingSound sound : ONE_SHOTS) { sound.finish(); mc.getSoundManager().stop(sound); }
        CHARGES.clear(); MINERS.clear(); CHARGE_TOKENS.clear(); MINE_TOKENS.clear();
        EXPERT_MINERS.clear(); LUCK_MINERS.clear(); EXPERT_TOKENS.clear(); LUCK_TOKENS.clear(); EXPERT_INPUT.clear(); LUCK_INPUT.clear();
        CHARGE_INPUT.clear(); MINE_INPUT.clear(); RAYS.clear(); PARTICLES.clear(); ONE_SHOTS.clear(); CLOCK.clear();
    }
    private static void synchronizeSession(Minecraft mc) {
        if (activeLevel == mc.level && activePlayer == mc.player && activeConnection == mc.getConnection()) return;
        callbackEpoch++; clearOwned(); activeLevel = mc.level; activePlayer = mc.player; activeConnection = mc.getConnection();
    }
    private static void updateClock(Minecraft mc) { CLOCK.update(Util.getMillis(), mc.level != null && !mc.isPaused()); }
    private static boolean validCaster(Minecraft mc, Entity entity) {
        return entity instanceof Player && entity.level() == mc.level && !entity.isRemoved() && entity.isAlive()
                && !entity.isSpectator() && mc.level.getEntity(entity.getId()) == entity;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        if (mc.level == null) return;
        if (mc.player == null || !mc.player.isAlive()) { clear(); return; }
        ONE_SHOTS.removeIf(sound -> !mc.getSoundManager().isActive(sound));
        if (!mc.isPaused()) PARTICLES.removeIf(p -> !p.tick());
        for (String skill : List.of(MELTDOWNER, MINE, ClassicAdvancedMineRayTimeline.EXPERT_ID, ClassicAdvancedMineRayTimeline.LUCK_ID)) {
            var iterator = contexts(skill).values().iterator();
            while (iterator.hasNext()) {
                Context c = iterator.next();
                if (!c.valid(mc)) { iterator.remove(); tokens(skill).end(c.key, c.token); c.hide(mc); }
                else if (!mc.isPaused() && c.shown) {
                    c.ticks++;
                    if (MELTDOWNER.equals(skill)) {
                        if (c.local) c.caster.getAbilities().setWalkingSpeed(walkSpeed(c.ticks));
                        // Literal source dormant range still consumes rangei RNG, never invents particles.
                        int count = chargeParticleCount(RANDOM);
                        for (int i = 0; i < count; i++) {
                            double radius = random(.7, 1), theta = random(0, Math.PI * 2), height = random(-1.2, 0);
                            addParticle(vec(chargeParticlePosition(point(c.caster.position()), c.local, radius, theta, height)),
                                    new Vec3(random(-.03, .03), random(.01, .05), random(-.03, .03)), false);
                        }
                    } else if (c.ray != null) {
                        c.updateMineRay(mc.player);
                        if (!tickRay(c.ray)) c.ray = null;
                    }
                }
            }
        }
        if (mc.isPaused()) return;
        RAYS.removeIf(ray -> !ray.valid(mc) || !tickRay(ray));
    }
    private static boolean tickRay(Ray ray) {
        ray.ticks++;
        if (RANDOM.nextDouble() < ray.spec.particleChance()) {
            // Motion3D(ray,true) uses default non-player entity eye height, then moves[0,10).
            Vec3 at = ray.origin.add(0, RAY_EYE_HEIGHT, 0).add(vec(entityDirection(point(ray.direction))).scale(random(0, 10)));
            addParticle(at, new Vec3(random(-.03, .03), random(-.03, .03), random(-.03, .03)), false,
                    ray.spec == ClassicAdvancedMineRayTimeline.LUCK ? LUCK_PARTICLE : MD_PARTICLE);
        }
        // Source executeAfter(life) is scheduled at first update; callback on tick life+1.
        return ray.ticks <= ray.spec.lifeTicks();
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance(); synchronizeSession(mc); updateClock(mc);
        if (mc.level == null || mc.player == null || !mc.player.isAlive()) return;
        if (buffers == null) buffers = MultiBufferSource.immediate(new ByteBufferBuilder(65536));
        PoseStack poses = event.getPoseStack(); Vec3 camera = event.getCamera().getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false), priorColor[] = RenderSystem.getShaderColor().clone();
        poses.pushPose();
        try {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            for (Ray ray : RAYS) if (ray.valid(mc)) renderRay(ray, mc, camera, poses.last().pose());
            for (String skill : List.of(MINE, ClassicAdvancedMineRayTimeline.EXPERT_ID, ClassicAdvancedMineRayTimeline.LUCK_ID))
                for (Context c : contexts(skill).values()) if (c.shown && c.valid(mc) && c.ray != null) renderRay(c.ray, mc, camera, poses.last().pose());
            for (MdParticle particle : PARTICLES) {
                poses.pushPose(); Vec3 position = particle.previous.lerp(particle.position, partial);
                poses.translate(position.x - camera.x, position.y - camera.y, position.z - camera.z); poses.mulPose(event.getCamera().rotation());
                drawQuad(Material.type(particle.texture, false), poses.last().pose(), ClassicMeltdownerStarterTimeline.particleQuad(particle.size),
                        255, 255, 255, particle.alpha * ClassicMeltdownerStarterTimeline.particleAlpha(particle.age, particle.life));
                poses.popPose();
            }
        } finally { poses.popPose(); RenderSystem.setShaderColor(priorColor[0], priorColor[1], priorColor[2], priorColor[3]); }
    }
    private static void renderRay(Ray ray, Minecraft mc, Vec3 camera, Matrix4f matrix) {
        long age = CLOCK.elapsed() - ray.created; ray.wiggle.frame(CLOCK.elapsed() + 1, RANDOM);
        double width = width(ray.spec, age, ray.wiggle.width), length = ray.length * lengthScale(ray.spec, age);
        double alpha = alpha(ray.spec, age), glowAlpha = glowAlpha(ray.spec, age, ray.wiggle.glow);
        boolean firstPerson = ray.caster == mc.player && mc.options.getCameraType().isFirstPerson();
        Point origin = point(ray.origin.subtract(camera));
        Glow geometry = glow(origin, point(ray.direction), length, point(Vec3.ZERO), firstPerson, ray.spec.glowWidth());
        ResourceLocation[] textures = ray.spec == MD ? MD_TEXTURES : ray.spec == ClassicAdvancedMineRayTimeline.EXPERT ? EXPERT_TEXTURES : ray.spec == ClassicAdvancedMineRayTimeline.LUCK ? LUCK_TEXTURES : MINE_TEXTURES;
        board(Material.type(textures[0], false), matrix, geometry.from(), geometry.mid1(), geometry.up(), ray.spec.glowWidth() * width, glowAlpha);
        board(Material.type(textures[1], false), matrix, geometry.mid1(), geometry.mid2(), geometry.up(), ray.spec.glowWidth() * width, glowAlpha);
        board(Material.type(textures[2], false), matrix, geometry.mid2(), geometry.to(), geometry.up(), ray.spec.glowWidth() * width, glowAlpha);
        CylinderFrame frame = cylinderFrame(origin, point(ray.direction), length, firstPerson);
        if (ray.spec == ClassicAdvancedMineRayTimeline.LUCK) {
            var inner = ClassicAdvancedMineRayTimeline.LUCK_INNER; var outer = ClassicAdvancedMineRayTimeline.LUCK_OUTER;
            cylinder(frame, matrix, length, ray.spec.inner() * width, .98, inner.r(), inner.g(), inner.b(), inner.a() / 255.0 * alpha);
            cylinder(frame, matrix, length, ray.spec.outer() * width, 1, outer.r(), outer.g(), outer.b(), outer.a() / 255.0 * alpha);
        } else {
            // Expert.doRender overrides constructor alpha230 to180 each rendered frame.
            if (ray.spec == ClassicAdvancedMineRayTimeline.EXPERT)
                cylinder(frame, matrix, length, ray.spec.inner() * width, .98, 216, 248, 216, 180 / 255.0 * alpha);
            else cylinder(frame, matrix, length, ray.spec.inner() * width, .98, 216, 248, 216, 230 / 255.0 * alpha);
            cylinder(frame, matrix, length, ray.spec.outer() * width, 1, 106, 242, 106, 50 / 255.0 * alpha);
        }
    }
    private static void cylinder(CylinderFrame frame, Matrix4f matrix, double length, double radius, double headFix, int r, int g, int b, double alpha) {
        RenderType type = Material.type(WHITE, true); VertexConsumer out = buffers.getBuffer(type);
        for (var quad : ClassicMeltdownerBeamTimeline.cylinder(length, radius, headFix)) for (var vertex : List.of(quad.a(), quad.b(), quad.c(), quad.d()))
            vertex(out, matrix, vec(frame.apply(vertex.point())), vertex.u(), vertex.v(), r, g, b, alpha);
        buffers.endBatch(type);
    }
    private static void board(RenderType type, Matrix4f matrix, Point from, Point to, Point up, double width, double alpha) {
        Point half = up.scale(width / 2); VertexConsumer out = buffers.getBuffer(type);
        vertex(out, matrix, vec(from.add(half)), 0, 1, 255, 255, 255, alpha); vertex(out, matrix, vec(from.subtract(half)), 0, 0, 255, 255, 255, alpha);
        vertex(out, matrix, vec(to.subtract(half)), 1, 0, 255, 255, 255, alpha); vertex(out, matrix, vec(to.add(half)), 1, 1, 255, 255, 255, alpha); buffers.endBatch(type);
    }
    private static void drawQuad(RenderType type, Matrix4f matrix, ClassicMeltdownerStarterTimeline.Quad quad, int r, int g, int b, double alpha) {
        VertexConsumer out = buffers.getBuffer(type);
        for (var vertex : List.of(quad.a(), quad.b(), quad.c(), quad.d())) {
            var p = vertex.point(); vertex(out, matrix, new Vec3(p.x(), p.y(), p.z()), vertex.u(), vertex.v(), r, g, b, alpha);
        }
        buffers.endBatch(type); // Source glow/inner/outer order, no global transparent-layer reordering.
    }
    private static void vertex(VertexConsumer out, Matrix4f matrix, Vec3 p, float u, float v, int r, int g, int b, double alpha) {
        out.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setUv(u, v).setColor(r, g, b, Mth.clamp((int)Math.round(alpha * 255), 0, 255));
    }
    private static final class Context {
        final Player caster; final ClientLevel level; final Object connection; final String skill; final long token, nonce;
        final Caster key; final boolean local; final BitSet rayIndices = new BitSet(2); final ParticleSequence particleSequence = new ParticleSequence();
        boolean shown; int ticks; Ray ray; FollowingSound sound;
        Context(Player caster, ClientLevel level, Object connection, String skill, long token, long nonce, boolean shown, boolean local) {
            this.caster = caster; this.level = level; this.connection = connection; this.skill = skill; this.token = token;
            this.nonce = nonce; this.shown = shown; this.local = local; key = new Caster(caster.getId(), caster.getUUID());
        }
        boolean valid(Minecraft mc) { return level == mc.level && connection == mc.getConnection() && validCaster(mc, caster) && key.uuid().equals(caster.getUUID()); }
        void hide(Minecraft mc) {
            if (shown && local && MELTDOWNER.equals(skill)) caster.getAbilities().setWalkingSpeed(.1F);
            shown = false; ray = null; // MRContextC.setDead: immediate termination, no new release fade.
            if (sound != null) { sound.finish(); mc.getSoundManager().stop(sound); sound = null; }
        }
        void updateMineRay(Player localPlayer) {
            Endpoints p = mineEndpoints(point(caster.position()), caster.getEyeHeight(), caster.getYHeadRot(), caster.getXRot(), caster == localPlayer);
            ray.origin = vec(p.from()); Point delta = p.to().subtract(p.from()); ray.length = delta.length(); ray.direction = vec(delta.normalize());
        }
    }
    private static final class Ray {
        final Player caster; final UUID uuid; final ClientLevel level; final Object connection; final Spec spec; final long created;
        Vec3 origin, direction; double length; int ticks; final ClassicMeltdownerStarterTimeline.RayWiggle wiggle = new ClassicMeltdownerStarterTimeline.RayWiggle();
        Ray(Player caster, ClientLevel level, Object connection, Spec spec, Vec3 origin, Vec3 direction, double length, long created) {
            this.caster = caster; uuid = caster.getUUID(); this.level = level; this.connection = connection; this.spec = spec;
            this.origin = origin; this.direction = length == 0 ? new Vec3(0, 0, 1) : direction; this.length = length; this.created = created;
        }
        boolean valid(Minecraft mc) { return level == mc.level && connection == mc.getConnection() && validCaster(mc, caster) && caster.getUUID().equals(uuid); }
    }
    private static final class MdParticle {
        Vec3 position, previous, velocity; final boolean mining; final ResourceLocation texture; final int life; final double alpha; final float size; int age;
        MdParticle(Vec3 position, Vec3 velocity, boolean mining, ResourceLocation texture) {
            this.position = previous = position; this.velocity = velocity; this.mining = mining; this.texture = texture;
            life = 25 + RANDOM.nextInt(30); alpha = random(.3, .6); size = .05F + RANDOM.nextFloat() * (.07F - .05F);
        }
        boolean tick() {
            age++; previous = position;
            position = vec(particleStep(point(position), point(velocity), mining));
            if (mining) velocity = velocity.add(0, -.01, 0); // Rigidbody.gravity BEFORE displacement, no collision/filter.
            return ClassicMeltdownerStarterTimeline.particleAlive(age, life);
        }
    }
    private static final class FollowingSound extends AbstractTickableSoundInstance {
        final Entity caster; final ClientLevel level; final Object connection; final Context owner; boolean finished;
        FollowingSound(Entity caster, ClientLevel level, Object connection, String path, float volume, boolean loop, Context owner) {
            super(sound(path), SoundSource.MASTER, RandomSource.create()); this.caster = caster; this.level = level; this.connection = connection; this.owner = owner;
            this.volume = volume; pitch = 1F; looping = loop; delay = 0; tick();
        }
        void finish() { finished = true; stop(); }
        @Override public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (finished || level != mc.level || connection != mc.getConnection() || !validCaster(mc, caster) || owner != null && !owner.shown) { finish(); return; }
            x = caster.getX(); y = caster.getY(); z = caster.getZ();
        }
    }
    private static void playOneShot(Player caster, ClientLevel level, Object connection, String path, float volume) {
        if (ONE_SHOTS.size() >= 128) return;
        FollowingSound sound = new FollowingSound(caster, level, connection, path, volume, false, null);
        ONE_SHOTS.add(sound); Minecraft.getInstance().getSoundManager().play(sound);
    }
    private static void addParticle(Vec3 position, Vec3 velocity, boolean mining) { addParticle(position, velocity, mining, MD_PARTICLE); }
    private static void addParticle(Vec3 position, Vec3 velocity, boolean mining, ResourceLocation texture) { if (PARTICLES.size() < 4096) PARTICLES.add(new MdParticle(position, velocity, mining, texture)); }
    private static Point point(Vec3 p) { return new Point(p.x, p.y, p.z); }
    private static Vec3 vec(Point p) { return new Vec3(p.x(), p.y(), p.z()); }
    private static double random(double from, double to) { return ClassicMeltdownerStarterTimeline.ranged(RANDOM, from, to); }
    private static Vec3 vector(CompoundTag tag, String x, String y, String z) { return new Vec3(tag.getDouble(x), tag.getDouble(y), tag.getDouble(z)); }
    private static boolean vectorTags(CompoundTag tag, String x, String y, String z) {
        return tag.contains(x, Tag.TAG_DOUBLE) && tag.contains(y, Tag.TAG_DOUBLE) && tag.contains(z, Tag.TAG_DOUBLE)
                && Double.isFinite(tag.getDouble(x)) && Double.isFinite(tag.getDouble(y)) && Double.isFinite(tag.getDouble(z));
    }
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    private static ResourceLocation[] rayTextures(String name) { return new ResourceLocation[] {texture("effects/" + name + "/blend_in"), texture("effects/" + name + "/tile"), texture("effects/" + name + "/blend_out")}; }
    private static SoundEvent sound(String path) { return SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy", path)); }
    private static final class Material extends RenderType {
        private record Key(ResourceLocation texture, boolean cull) {}
        private static final Map<Key, RenderType> TYPES = new HashMap<>();
        private Material(String n, VertexFormat f, VertexFormat.Mode m, int s, boolean cr, boolean sort, Runnable setup, Runnable clear) { super(n, f, m, s, cr, sort, setup, clear); }
        static RenderType type(ResourceLocation texture, boolean cull) {
            return TYPES.computeIfAbsent(new Key(texture, cull), key -> create("academy_classic_beam_" + key.texture.getPath() + "_" + cull,
                    DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, false, false, CompositeState.builder()
                            .setShaderState(new ShaderStateShard(cull ? GameRenderer::getPositionTexColorShader : ClassicMeltdownerStarterEffects.CutoffShader::get))
                            .setTextureState(new TextureStateShard(texture, false, false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                            .setCullState(cull ? CULL : NO_CULL).setLightmapState(NO_LIGHTMAP).setOverlayState(NO_OVERLAY)
                            .setWriteMaskState(COLOR_DEPTH_WRITE).setOutputState(PARTICLES_TARGET).createCompositeState(false)));
        }
    }
}
