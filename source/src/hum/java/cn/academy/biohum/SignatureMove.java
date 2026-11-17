package cn.academy.biohum;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** The second batch's server-authoritative skills and their shared pose clock. */
public enum SignatureMove implements NpcMove {
    SISTERS_ARC("sisters_arc", "欠陷电气·定向电弧", "观察报告", 9, 9, 58, 14, 0x63cfff),
    NETWORK_ALERT("network_alert", "御坂网络·预警", "检查御坂网络", 12, 12, 95, 18, 0x87e6ff),
    WORST_DISCHARGE("worst_discharge", "分叉放电", "打个招呼", 16, 12, 90, 13, 0xa66cfb),
    ONMYO_SEAL("onmyo_seal", "阴阳术·符咒牵制", "交换情报", 16, 10, 95, 12, 0xffbd68),
    COORDINATE_SHIFT("coordinate_shift", "坐标移动", "检查落点", 10, 9, 68, 8, 0xe187ac),
    VENUS_REFLECTION("venus_reflection", "金星反光术", "检查术式条件", 14, 12, 86, 16, 0x9ce5b6),
    MELTDOWNER("meltdowner", "原子崩坏", "ITEM 点名", 15, 15, 108, 26, 0x77fc60),
    NITROGEN_ARMOR("nitrogen_armor", "氮气装甲·架防", "查看装甲", 7, 13, 85, 3, 0xe5edf3),
    TOY_CHARGE("toy_charge", "布置玩偶陷阱", "回收玩偶", 11, 11, 105, 9, 0xffb463),
    DARK_MATTER_WINGS("dark_matter_wings", "未元物质·白翼", "估算交战距离", 19, 20, 112, 19, 0xe7e1ff),
    AMAZING_PUNCH("amazing_punch", "超厉害拳", "根性声援", 12, 12, 76, 15, 0xf49a53),
    AIM_SHELTER("aim_shelter", "AIM 护持", "确认护持范围", 12, 14, 100, 6, 0x8fe1df);

    private static final Map<String, SignatureMove> BY_ID = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(move -> move.id, move -> move));

    public final String id;
    public final String label;
    public final String interaction;
    public final int windup;
    public final int recovery;
    private final int cooldown;
    private final double range;
    final int color;

    SignatureMove(String id, String label, String interaction, int windup, int recovery,
                  int cooldown, double range, int color) {
        this.id = id;
        this.label = label;
        this.interaction = interaction;
        this.windup = windup;
        this.recovery = recovery;
        this.cooldown = cooldown;
        this.range = range;
        this.color = color;
    }

    public static SignatureMove find(String id) {
        return BY_ID.get(id);
    }

    @Override public String id() { return id; }
    @Override public String label() { return label; }
    @Override public String interaction() { return interaction; }
    @Override public int windup() { return windup; }
    @Override public int recovery() { return recovery; }

    @Override
    public boolean start(HumNpc npc, LivingEntity target) {
        if (!canApply(npc, target) || !npc.windup(id, target, windup)) return false;
        var level = (ServerLevel) npc.level();
        npc.setReady(id, level.getGameTime() + cooldown);
        npc.expression(this == NETWORK_ALERT || this == AIM_SHELTER ? 1 : 2);
        npc.getNavigation().stop();
        if (target != null) npc.getLookControl().setLookAt(target, 45, 45);
        npc.beginSignature(this);
        if(target!=null)npc.faceCast(target.getBoundingBox().getCenter());
        SignatureEffects.ring(level, npc.position().add(0, .11, 0), .44 * npc.profile().scale(), color, 10);
        return true;
    }

    private boolean visibleTarget(HumNpc npc, LivingEntity target) {
        return Abilities.target(npc, target, range) && npc.validTarget(target);
    }

    private boolean canApply(HumNpc npc, LivingEntity target) {
        return switch (this) {
            case NITROGEN_ARMOR, AIM_SHELTER -> true;
            case TOY_CHARGE -> chargePosition(npc, target) != null;
            case VENUS_REFLECTION -> visibleTarget(npc, target) && outdoors(npc);
            case ONMYO_SEAL -> visibleTarget(npc, target) && npc.getHealth() > 4;
            case COORDINATE_SHIFT -> visibleTarget(npc, target) && !(target instanceof Player)
                    && !(target instanceof FlameFamiliar) && !target.isVehicle() && !target.isPassenger()
                    && target.getBbWidth() <= .9 && target.getBbHeight() <= 2.2
                    && Abilities.safeDestination(target, destination(npc, target));
            default -> visibleTarget(npc, target);
        };
    }

    @Override
    public boolean finish(HumNpc npc, LivingEntity target) {
        if (!canApply(npc, target)) return false;
        ServerLevel level = (ServerLevel) npc.level();
        Vec3 at = target != null ? target.getBoundingBox().getCenter() : npc.getBoundingBox().getCenter();
        Vec3 hand = target != null ? handToward(npc, at) : npc.rightHand();
        switch (this) {
            case SISTERS_ARC -> {
                SignatureEffects.arc(level, hand, at, color, .20);
                hit(npc, target, 5);
            }
            case NETWORK_ALERT -> {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 90, 0), npc);
                SignatureEffects.ring(level, npc.position().add(0, .45, 0), .78, color, 22);
                SignatureEffects.corners(level, target.position(), color);
                var player = npc.audience();
                if (player != null) {
                    npc.tell(player, "御坂御坂发现了 " + target.getName().getString() + "，距离 "
                            + Math.round(npc.distanceTo(target)) + " 格！");
                    npc.getNavigation().moveTo(player, 1.08);
                }
                npc.setTarget(null);
            }
            case WORST_DISCHARGE -> {
                List<LivingEntity> branches = level.getEntitiesOfClass(LivingEntity.class,
                        target.getBoundingBox().inflate(3.6), candidate -> candidate != target
                                && candidate instanceof Monster && npc.validTarget(candidate)
                                && target.hasLineOfSight(candidate));
                SignatureEffects.arc(level, hand, at, color, .39);
                hit(npc, target, 8);
                branches.stream().sorted(Comparator.comparingDouble(target::distanceToSqr)).forEach(branch -> {
                    SignatureEffects.arc(level, at, branch.getBoundingBox().getCenter(), 0xd383ff, .33);
                    hit(npc, branch, 3);
                });
            }
            case ONMYO_SEAL -> {
                npc.setHealth(npc.getHealth() - 2);
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 70, 1), npc);
                SignatureEffects.ring(level, target.position().add(0, .12, 0), 1.23, color, 26);
                SignatureEffects.corners(level, target.position(), 0xffda91);
                Abilities.particles(level, at, ParticleTypes.ENCHANT, 18);
            }
            case COORDINATE_SHIFT -> {
                Vec3 from = target.position();
                Vec3 destination = destination(npc, target);
                if (!Abilities.teleport(target, destination)) return false;
                SignatureEffects.corners(level, from, color);
                SignatureEffects.corners(level, destination, color);
                level.playSound(null, BlockPos.containing(destination), SoundEvents.ENDERMAN_TELEPORT,
                        SoundSource.NEUTRAL, .55f, 1.29f);
            }
            case VENUS_REFLECTION -> {
                SignatureEffects.beam(level, hand, at, color);
                Abilities.particles(level, hand, ParticleTypes.ENCHANT, 12);
                target.hurt(npc.damageSources().indirectMagic(null, npc), 6);
            }
            case MELTDOWNER -> {
                SignatureEffects.beam(level, hand, at, color);
                Abilities.particles(level, at, ParticleTypes.END_ROD, 13);
                hit(npc, target, 12);
            }
            case NITROGEN_ARMOR -> {
                npc.guardUntil = level.getGameTime() + 68;
                SignatureEffects.ring(level, npc.position().add(0, npc.getBbHeight() * .60, 0), .77, color, 24);
                Abilities.particles(level, npc.getBoundingBox().getCenter(), ParticleTypes.CLOUD, 15);
                if (visibleTarget(npc, target)) {
                    hit(npc, target, 5);
                    push(npc, target, .46);
                }
            }
            case TOY_CHARGE -> {
                Vec3 position = chargePosition(npc, target);
                var toy = BioHum.TOY_CHARGE.get().create(level);
                toy.arm(npc, position);
                level.addFreshEntity(toy);
                SignatureEffects.ring(level, position.add(0, .06, 0), ToyCharge.RADIUS, color, 24);
            }
            case DARK_MATTER_WINGS -> {
                SignatureEffects.wings(level, npc.getEyePosition().add(0, -.27, 0), at);
                Abilities.particles(level, at, ParticleTypes.END_ROD, 12);
                hit(npc, target, 9);
                push(npc, target, .68);
            }
            case AMAZING_PUNCH -> {
                SignatureEffects.line(level, hand, at, 0xffe5bb, 1.10f, 21);
                SignatureEffects.ring(level, target.position().add(0, .36, 0), 1.28, color, 26);
                SignatureEffects.ring(level, target.position().add(0, .69, 0), .85, 0xffd678, 20);
                Abilities.particles(level, at, ParticleTypes.POOF, 12);
                hit(npc, target, 8);
                push(npc, target, .90);
            }
            case AIM_SHELTER -> {
                for (LivingEntity ally : sheltered(npc)) {
                    ally.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 76, 0), npc);
                    SignatureEffects.ring(level, ally.position().add(0, .11, 0), .48, color, 12);
                }
                SignatureEffects.ring(level, npc.position().add(0, .16, 0), range, color, 36);
            }
        }
        return true;
    }

    @Override
    public void interact(HumNpc npc, ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        LivingEntity observed = Abilities.aimed(npc, player);
        String message = switch (this) {
            case SISTERS_ARC -> observation(npc, observed, "御坂报告，");
            case NETWORK_ALERT -> "御坂御坂看到附近有 " + near(npc, "misaka_10032", "misaka_worst").size() + " 位网络成员。";
            case WORST_DISCHARGE -> near(npc, "accelerator").isEmpty()
                    ? "今天倒是挺安静。你希望我配合哪一步？" : "那位白发的也在啊。真是热闹。";
            case ONMYO_SEAL -> observation(npc, observed, "情报给你，");
            case COORDINATE_SHIFT -> canApply(npc, observed) ? "落点空间检查通过。" : "这个目标或落点无法安全移动。";
            case VENUS_REFLECTION -> outdoors(npc) ? "天空可见，可以准备反光术。" : "上方有遮挡，先换到露天处。";
            case MELTDOWNER -> "附近的 ITEM 成员有 " + near(npc, "kinuhata", "frenda", "takitsubo").size() + " 人。";
            case NITROGEN_ARMOR -> "氮气装甲剩余 " + Math.max(0, (npc.guardUntil - level.getGameTime() + 19) / 20) + " 秒。";
            case TOY_CHARGE -> {
                var toys = level.getEntitiesOfClass(ToyCharge.class, npc.getBoundingBox().inflate(16), toy -> toy.belongsTo(npc));
                toys.forEach(Entity::discard);
                yield "收回了 " + toys.size() + " 个玩偶。";
            }
            case DARK_MATTER_WINGS -> observation(npc, observed, "白翼可在 " + (int) range + " 格内发动，");
            case AMAZING_PUNCH -> player.getHealth() < player.getMaxHealth() / 2
                    ? "先站稳，等你恢复力气再上！" : "好，气势十足。一起过去吧！";
            case AIM_SHELTER -> "目前有 " + sheltered(npc).size() + " 位同行者在护持范围内（含我）。";
        };
        npc.expression(1);
        npc.tell(player, message);
    }

    private static List<HumNpc> near(HumNpc npc, String... ids) {
        var names = List.of(ids);
        return npc.level().getEntitiesOfClass(HumNpc.class, npc.getBoundingBox().inflate(14),
                other -> other.isAlive() && names.contains(other.characterId));
    }

    private static String observation(HumNpc npc, LivingEntity target, String intro) {
        return intro + (target == null ? "视野内没有选中的目标。" : target.getName().getString() + " 距离 "
                + Math.round(npc.distanceTo(target)) + " 格。");
    }

    private List<LivingEntity> sheltered(HumNpc npc) {
        return npc.level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(range),
                ally -> ally.isAlive() && npc.ally(ally) && npc.distanceToSqr(ally) <= range * range);
    }

    private Vec3 chargePosition(HumNpc npc, LivingEntity target) {
        if (target != null && !visibleTarget(npc, target)) return null;
        Vec3 at = target != null ? target.position() : npc.position()
                .add(Vec3.directionFromRotation(0, npc.getYRot()).scale(3.0));
        var level = npc.level();
        BlockPos pos = BlockPos.containing(at);
        return level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos) && level.isEmptyBlock(pos)
                && level.getBlockState(pos.below()).isSolid() ? at : null;
    }

    private static Vec3 destination(HumNpc npc, LivingEntity target) {
        Vec3 away = target.position().subtract(npc.position()).multiply(1, 0, 1).normalize();
        return target.position().add(away.scale(3.75));
    }

    private static boolean outdoors(HumNpc npc) {
        BlockPos eyes = BlockPos.containing(npc.getEyePosition());
        return npc.level().getHeight(Heightmap.Types.MOTION_BLOCKING, eyes.getX(), eyes.getZ()) <= eyes.getY();
    }

    private static Vec3 handToward(HumNpc npc, Vec3 at) {
        Vec3 origin = npc.position().add(0, npc.getBbHeight() * .67, 0);
        Vec3 aim = at.subtract(origin).normalize();
        Vec3 side = aim.cross(new Vec3(0, 1, 0));
        return origin.add(aim.scale(.47)).add(side.scale(.20 * npc.profile().scale()));
    }

    private static void hit(HumNpc npc, LivingEntity target, float damage) {
        target.hurt(npc.damageSources().mobAttack(npc), damage);
    }

    private static void push(HumNpc npc, LivingEntity target, double force) {
        Vec3 direction = target.position().subtract(npc.position()).multiply(1, 0, 1).normalize();
        target.push(direction.x * force, .19, direction.z * force);
    }

    @Override
    public void defend(HumNpc npc, ServerLevel level) {
        if ((this != NETWORK_ALERT && this != AIM_SHELTER) || npc.isNoAi() || npc.mode() != 3
                || npc.owner() == null || level.getGameTime() % 10 != 0 || npc.readyAt(id) > level.getGameTime()) return;
        LivingEntity threat = npc.getTarget();
        if (!visibleTarget(npc, threat)) {
            threat = level.getEntitiesOfClass(Monster.class, npc.getBoundingBox().inflate(range),
                    monster -> visibleTarget(npc, monster))
                    .stream().min(Comparator.comparingDouble(npc::distanceToSqr)).orElse(null);
        }
        if (threat != null) Abilities.cast(npc, 0, threat);
    }
}
