package cn.academy.port.highpower;

import cn.academy.port.city.defense.O5OperativeEntity;
import cn.academy.port.highpower.core.*;
import cn.academy.port.titan.TitanEntity;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/** Adjusts Academy's own base and shield allocations at a profile boundary on the server thread. */
public final class CapacityProfile {
    private static final String VERSION = "capacityProfileVersion", PROTECTED = "protectedCapacity";
    private static final int CURRENT = 1;
    private static final ThreadLocal<LivingEntity> WRITING = new ThreadLocal<>();
    public static boolean writing(LivingEntity entity) { return WRITING.get() == entity; }
    private static Predicate<ServerPlayer> protectedGear = p -> false;
    private static Function<ServerPlayer, WristShield> wrist = p -> WristShield.EMPTY;
    public record WristShield(double current, double maximum) {
        public static final WristShield EMPTY = new WristShield(0, 0);
    }
    private CapacityProfile() {}
    public static void registerEquipment(Predicate<ServerPlayer> protection, Function<ServerPlayer, WristShield> reader) {
        protectedGear = Objects.requireNonNull(protection);
        wrist = Objects.requireNonNull(reader);
    }
    public static WristShield wristShield(ServerPlayer player) { return wrist.apply(player); }
    static boolean protectedFor(LivingEntity entity) {
        return entity instanceof O5OperativeEntity || entity instanceof ServerPlayer p && protectedGear.test(p);
    }
    static ContinuousHighPowerGrowth.Snapshot sample(LivingEntity entity, BigDecimal q) {
        return protectedFor(entity) ? ContinuousHighPowerGrowth.protectedCapacity(q) : ContinuousHighPowerGrowth.sample(q);
    }
    static void mark(CompoundTag tag, boolean protectedNow) {
        tag.putInt(VERSION, CURRENT);
        tag.putBoolean(PROTECTED, protectedNow);
    }
    static boolean needsChange(LivingEntity entity, CompoundTag tag) {
        if (entity instanceof TitanEntity) return false;
        if (tag.getInt(VERSION) != CURRENT) return true;
        return entity instanceof ServerPlayer && tag.getBoolean(PROTECTED) != protectedFor(entity);
    }
    static void synchronize(LivingEntity entity, CompoundTag tag, float oldHealth, float oldMaximum) {
        if (entity instanceof TitanEntity || !tag.contains("maxShield", Tag.TAG_STRING)) return;
        boolean protectedNow = protectedFor(entity);
        if (tag.getInt(VERSION) == CURRENT && tag.getBoolean(PROTECTED) == protectedNow) return;
        BigDecimal position = tag.contains("growthPosition", Tag.TAG_STRING)
                ? ContinuousHighPowerGrowth.requirePosition(new BigDecimal(tag.getString("growthPosition")))
                : BigDecimal.valueOf(HighPowerLevel.forLevel(tag.getInt("level")).level());
        var next = protectedNow ? ContinuousHighPowerGrowth.protectedCapacity(position) : ContinuousHighPowerGrowth.sample(position);
        BigDecimal previous = CombatAmounts.parse(tag.getString("maxShield"), "previous shield capacity");
        BigDecimal current = CombatAmounts.parse(tag.getString("shield"), "previous shield");
        CompoundTag updates = new CompoundTag();
        if (previous.compareTo(next.maxShield()) != 0) resizeShields(entity, tag, updates, current, previous, next.maxShield());
        var attribute = entity.getAttribute(Attributes.MAX_HEALTH);
        if (oldMaximum <= 0 || attribute == null) return;
        double fraction = Math.clamp((double)oldHealth / oldMaximum, 0, 1);
        attribute.setBaseValue(next.maxHealth().doubleValue());
        float health = (float)(fraction * entity.getMaxHealth());
        if (!(entity instanceof ServerPlayer player && LOADING.containsKey(player))) setFractionalHealth(entity, health);
        for (String key : updates.getAllKeys()) tag.put(key, updates.get(key));
        if (tag.contains("growthCurve")) tag.putInt("growthCurve", ContinuousHighPowerGrowth.CURVE_VERSION);
        mark(tag, protectedNow);
    }
    // a3.81 wrote native Health but omitted transient wrist/armor max-health modifiers from attributes.
    // Retain exactly one vanilla read until the login handlers have restored the worn equipment.
    private record LoadedVitals(float health, double base) {}
    private static final Map<ServerPlayer, LoadedVitals> LOADING = Collections.synchronizedMap(new WeakHashMap<>());

    public static void captureLoadedVitals(ServerPlayer player, CompoundTag save) {
        LOADING.remove(player);
        var combat = save.getCompound("NeoForgeData").getCompound(HighPowerCombat.DATA_KEY);
        if (!combat.getBoolean("nativeVitals") || combat.getInt(VERSION) >= CURRENT || !save.contains("Health", Tag.TAG_ANY_NUMERIC)) return;
        float health = save.getFloat("Health");
        if (!Float.isFinite(health) || health <= 0) return;
        for (var entry : save.getList("attributes", Tag.TAG_COMPOUND)) {
            var attr = (CompoundTag)entry;
            if (attr.getString("id").equals("minecraft:generic.max_health")) {
                double base = attr.getDouble("base");
                if (Double.isFinite(base) && base > 0) LOADING.put(player, new LoadedVitals(health, base));
                break;
            }
        }
    }
    static void finishLoadedVitals(ServerPlayer player) {
        var saved = LOADING.remove(player);
        if (saved == null || !player.isAlive() || !HighPowerCombat.enabled(player)) return;
        var maximum = player.getAttribute(Attributes.MAX_HEALTH);
        if (maximum == null) return;
        var previous = new AttributeInstance(Attributes.MAX_HEALTH, unused -> {});
        previous.replaceFrom(maximum);
        previous.setBaseValue(saved.base());
        double oldEffective = previous.getValue();
        if (!Double.isFinite(oldEffective) || oldEffective <= 0) return;
        float health = (float)(player.getMaxHealth() * Math.clamp(saved.health() / oldEffective, 0, 1));
        setFractionalHealth(player, health);
    }
    private static void setFractionalHealth(LivingEntity entity, float health) {
        if (Float.compare(entity.getHealth(), health) == 0) return;
        LivingEntity outer = WRITING.get();
        WRITING.set(entity);
        try { entity.setHealth(health); }
        finally { if (outer == null) WRITING.remove(); else WRITING.set(outer); }
    }

    /** Called across one owned attribute mutation so form and armor bonuses enter both endpoints exactly once. */
    public static void equipmentChanged(ServerPlayer player, float oldHealth, float oldMaximum) {
        var tag = player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        if (tag.getBoolean("nativeVitals") && needsChange(player, tag)) synchronize(player, tag, oldHealth, oldMaximum);
    }
    private static BigDecimal scale(BigDecimal value, BigDecimal oldMax, BigDecimal newMax) {
        if (value.signum() == 0) return BigDecimal.ZERO;
        if (value.compareTo(oldMax) == 0) return newMax;
        return value.multiply(newMax).divide(oldMax, MathContext.DECIMAL128).min(newMax);
    }
    /** Children sum exactly to their scaled parent. Preserve empty and full plates across decimal partition rounding. */
    private static List<BigDecimal> distribute(List<BigDecimal> old, List<BigDecimal> caps,
                                                BigDecimal oldMax, BigDecimal newMax, BigDecimal total) {
        List<BigDecimal> out = new ArrayList<>();
        BigDecimal left = total;
        for (int i = 0; i < old.size(); i++) {
            BigDecimal amount = scale(old.get(i), oldMax, newMax).min(caps.get(i)).min(left);
            out.add(amount);
            left = left.subtract(amount);
        }
        for (int i = old.size() - 1; i >= 0 && left.signum() > 0; i--) {
            if (old.get(i).signum() == 0) continue;
            BigDecimal add = caps.get(i).subtract(out.get(i)).min(left);
            out.set(i, out.get(i).add(add));
            left = left.subtract(add);
        }
        if (left.signum() != 0) throw new IllegalArgumentException("Unrepresentable shield partition");
        return out;
    }
    private static BigDecimal field(CompoundTag tag, String key) { return CombatAmounts.parse(tag.getString(key), key); }
    static void resizeShields(LivingEntity entity, CompoundTag old, CompoundTag updates,
                                      BigDecimal current, BigDecimal previous, BigDecimal maximum) {
        BigDecimal shield = scale(current, previous, maximum);
        updates.putString("shield", CombatAmounts.decimalString(shield));
        updates.putString("maxShield", CombatAmounts.decimalString(maximum));
        var before = new ExactCombatState(BigDecimal.ONE, BigDecimal.ONE, current, previous, false);
        var after = new ExactCombatState(BigDecimal.ONE, BigDecimal.ONE, shield, maximum, false);
        var reserve = SharedShieldReserveShares.ZERO;
        if (old.contains(MagneticDomainReserve.KEY)) {
            var saved = MagneticDomainReserve.savedShares(entity, before).orElseThrow(() -> new IllegalArgumentException("Invalid reserve"));
            reserve = new SharedShieldReserveShares(scale(saved.lower(), previous, maximum), scale(saved.upper(), previous, maximum)).validate(after);
            updates.put(MagneticDomainReserve.KEY, MagneticDomainReserve.serialize(after, reserve));
        }
        if (!old.contains(ScatterPlateStorage.KEY)) return;
        var plates = old.getCompound(ScatterPlateStorage.KEY);
        var capacity = ScatterPlateShares.initialize(maximum, maximum);
        var amounts = distribute(List.of(field(plates,"plate0"),field(plates,"plate1"),field(plates,"plate2")),
                capacity.plates().stream().map(ScatterPlateShares.Plate::capacity).toList(), previous, maximum, shield);
        var scatter = ScatterPlateShares.restore(shield,maximum,amounts);
        updates.put(ScatterPlateStorage.KEY, ScatterPlateStorage.serialize(scatter));
        if (!old.contains(ZeroGapArmorStorage.KEY)) return;
        var armor = old.getCompound(ZeroGapArmorStorage.KEY);
        var parents = ZeroGapArmorShares.initialize(after,scatter,reserve);
        var bases = new ArrayList<BigDecimal>();
        var reserves = new BigDecimal[6];
        for (int i=0; i<3; i++) {
            int a=i*2, b=a+1;
            bases.addAll(distribute(List.of(field(armor,"base"+a),field(armor,"base"+b)),
                    List.of(parents.plate(a).baseCapacity(),parents.plate(b).baseCapacity()),previous,maximum,amounts.get(i)));
        }
        for (int i=0; i<2; i++) {
            var indices=List.of(i,i+2,i+4);
            var part=distribute(indices.stream().map(n->field(armor,"reserve"+n)).toList(),
                    indices.stream().map(n->parents.plate(n).reserveCapacity()).toList(),previous,maximum,i==0?reserve.lower():reserve.upper());
            for(int j=0;j<3;j++) reserves[indices.get(j)]=part.get(j);
        }
        var shares=ZeroGapArmorShares.restore(after,scatter,reserve,bases,Arrays.asList(reserves));
        CompoundTag result=new CompoundTag();
        shares.toDecimalMap().forEach(result::putString);
        updates.put(ZeroGapArmorStorage.KEY,result);
    }
}
