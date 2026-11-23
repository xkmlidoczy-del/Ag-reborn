package qa.combat;

import cn.aca.omnitrix.*;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.city.defense.CityDefenseContent;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import cn.academy.port.titan.TitanProfiles;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.item.ItemStack;

/** Invoked in disposable single-player and loopback worlds, on their owning server thread. */
public final class CapacityProfileProbe {
    private static final List<String> CATEGORIES=List.of("electromaster","meltdowner","teleporter","vecmanip",
            "mental_out","aero_hand","float_dial","nitrogen_armor","dark_matter","hydro_hand","paper_psychokinesis","liquid_shadow");
    private static void eq(double actual,double expected,String where) {
        if(Math.abs(actual-expected)>Math.max(Math.ulp((float)expected),1e-7))throw new AssertionError(where+" expected="+expected+" actual="+actual);
    }
    private static void state(ServerPlayer p,double hp,double max,double shield,double maxShield) {
        if(!HighPowerCombat.enabled(p))throw new AssertionError("missing enrollment");
        eq(p.getHealth(),hp,"native health");eq(p.getMaxHealth(),max,"native maximum");
        eq(Double.parseDouble(HighPowerCombat.shieldText(p)),shield,"shield");eq(Double.parseDouble(HighPowerCombat.maxShieldText(p)),maxShield,"shield capacity");
    }
    public static void run(ServerPlayer p) {
        var world=p.serverLevel();
        for(int tier=1;tier<=5;tier++) {
            var target=EntityType.VILLAGER.create(world);
            if(!HighPowerCombat.enroll(target,tier))throw new AssertionError("enroll "+tier);
            eq(target.getMaxHealth(),new int[]{24,36,52,72,100}[tier-1],"tier "+tier);
            eq(Double.parseDouble(HighPowerCombat.maxShieldText(target)),new int[]{6,12,22,35,50}[tier-1],"tier shield");
            for(String cat:CATEGORIES){
                var ability=AbilityStorage.get(p);ability.changeCategoryClassic(cat);ability.setLevel(tier);ability.setActivateState(true);
                p.getPersistentData().remove(HighPowerCombat.DATA_KEY);
                HighPowerGrowth.invalidate(p);
                if(!HighPowerCombat.enroll(p,tier))throw new AssertionError("player enrollment");
                var power=HighPowerGrowth.values(p).damage();
                eq(power.doubleValue(),new int[]{5,8,12,18,24}[tier-1]*(cat.equals("meltdowner")?1.5:1),"tier power "+cat);
                eq(p.getAttributeValue(CombatAttributes.ABILITY_POWER),power.doubleValue(),"real attribute "+cat);
                int guns=0;
                for(double shot:new double[]{cn.academy.port.fleet.FleetBallistics.damage(p,0),cn.academy.port.fleet.FleetBallistics.damage(p,1),
                        cn.academy.port.fleet.FleetBallistics.damage(p,2),cn.academy.port.fleet.FleetBallistics.damage(p,3),
                        cn.academy.port.fleet.FleetBallistics.damage(p,4),cn.academy.port.fleet.FleetBallistics.damage(p,5)})
                    eq(shot,cn.academy.port.fleet.InteractionLab.DAMAGE[guns++]*new double[]{1,1.1,1.2,1.35,1.5}[tier-1],"gun");
            }
            target.discard();
        }
        var ability=AbilityStorage.get(p);ability.changeCategoryClassic("electromaster");ability.setLevel(5);ability.setActivateState(true);
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);
        var tag=p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20_000_000);
        p.setHealth(10_000_000);tag.putString("shield","5000000");tag.putString("maxShield","10000000");tag.putInt("growthCurve",2);tag.remove("capacityProfileVersion");
        state(p,50,100,25,50);
        var sword=new ItemStack(Omnitrix.ASCALON.get());
        for(int i=0;i<100;i++){
            p.setItemInHand(InteractionHand.OFF_HAND,sword);state(p,10_000_000,20_000_000,5_000_000,10_000_000);
            p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);state(p,50,100,25,50);
        }
        // A shared reserve is partitioned. Its lower half, scatter plates and armor sections are all views of the same parents.
        if (ScatterPlateStorage.ensure(p).isEmpty() || !ZeroGapArmorStorage.ensureLearned(p) || ZeroGapArmorStorage.read(p).isEmpty())
            throw new AssertionError("Native-health player cannot initialize and read zero-gap armor");
        var base=new ExactCombatState(BigDecimal.valueOf(50),BigDecimal.valueOf(100),BigDecimal.valueOf(25),BigDecimal.valueOf(50),false);
        var plates=ScatterPlateShares.restore(base.shield(),base.maxShield(),List.of(BigDecimal.ZERO,BigDecimal.TEN,BigDecimal.valueOf(15)));
        var shared=new SharedShieldReserveShares(BigDecimal.valueOf(7),BigDecimal.valueOf(13));
        var plateTag=new CompoundTag();plates.toDecimalMap().forEach(plateTag::putString);tag.put(ScatterPlateStorage.KEY,plateTag);
        tag.putBoolean(ScatterPlateStorage.INITIALIZED_KEY,true);
        var reserveTag=new CompoundTag();shared.toDecimalMap(base).forEach(reserveTag::putString);reserveTag.putInt("version",2);tag.put(MagneticDomainReserve.KEY,reserveTag);
        var armorTag=new CompoundTag();ZeroGapArmorShares.initialize(base,plates,shared).toDecimalMap().forEach(armorTag::putString);tag.put(ZeroGapArmorStorage.KEY,armorTag);
        tag.putBoolean(ZeroGapArmorStorage.INITIALIZED_KEY,true);
        p.setItemInHand(InteractionHand.OFF_HAND,sword);state(p,10_000_000,20_000_000,5_000_000,10_000_000);
        eq(Double.parseDouble(tag.getCompound(MagneticDomainReserve.KEY).getString("amount")),4_000_000,"shared total");
        eq(Double.parseDouble(tag.getCompound(ScatterPlateStorage.KEY).getString("plate0")),0,"broken plate");
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);state(p,50,100,25,50);
        var restored=tag.getCompound(ZeroGapArmorStorage.KEY);
        for(String k:armorTag.getAllKeys())if(!k.equals("version"))
            eq(Double.parseDouble(restored.getString(k)),Double.parseDouble(armorTag.getString(k)),"restored plate "+k);
        for(String key:List.of(ScatterPlateStorage.KEY,ScatterPlateStorage.INITIALIZED_KEY,MagneticDomainReserve.KEY,
                               ZeroGapArmorStorage.KEY,ZeroGapArmorStorage.TIMING_KEY,ZeroGapArmorStorage.INITIALIZED_KEY))tag.remove(key);
        tag.putString("shield","0");p.setHealth(1);
        p.setItemInHand(InteractionHand.MAIN_HAND,sword);state(p,200000,20_000_000,0,10_000_000);
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);state(p,1,100,0,50);
        var watch=Omnitrix.wrist(p);
        for(Kind k:Kind.values())for(Form form:Form.values())if(form.canUse(k)) {
            var n=new CompoundTag();n.putInt("version",3);n.put("watch",new ItemStack(Omnitrix.watch(k)).saveOptional(p.registryAccess()));
            n.putString("stage","ACTIVE");n.putInt("form",form.ordinal());n.putDouble("dnaShield",5);n.putDouble("dnaCapacity",10);
            watch.deserializeNBT(p.registryAccess(),n);
            boolean prot=k==Kind.CELESTIAL||form==Form.ALIEN_X;
            state(p,prot?200000:1,prot?20_000_000:100,0,prot?10_000_000:50);
            eq(CapacityProfile.wristShield(p).current(),form.maxBarrier(k)/2,"DNA "+k+" "+form);
            if(!PlayerShieldArmor.holding(p))throw new AssertionError("DNA shield does not armor");
            if(prot){p.setItemInHand(InteractionHand.OFF_HAND,sword);state(p,200000,20_000_000,0,10_000_000);p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);}
        }
        watch.deserializeNBT(p.registryAccess(),new CompoundTag());state(p,1,100,0,50);
        var external=new AttributeModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("qa","bonus"),20,AttributeModifier.Operation.ADD_VALUE);
        p.getAttribute(Attributes.MAX_HEALTH).addTransientModifier(external);p.setHealth(60);
        p.setItemInHand(InteractionHand.OFF_HAND,sword);state(p,10_000_010,20_000_020,0,10_000_000);
        p.setItemInHand(InteractionHand.OFF_HAND,ItemStack.EMPTY);state(p,60,120,0,50);p.getAttribute(Attributes.MAX_HEALTH).removeModifier(external.id());
        for(var profile:TitanProfiles.all().values())eq(profile.damageUnit().doubleValue(),profile.tier()==3?3162:56234,"titan "+profile.id());
        var operative=CityDefenseContent.O5.get().create(world);
        if(!HighPowerCombat.enroll(operative,5))throw new AssertionError("O5 enrollment");
        eq(operative.getMaxHealth(),20_000_000,"O5 health");eq(Double.parseDouble(HighPowerCombat.maxShieldText(operative)),10_000_000,"O5 shield");
        operative.discard();
        var witness=cn.academy.port.highpower.core.ClassicDamageScale.scaled(110,BigDecimal.valueOf(24),50);
        eq(witness,52.8,"mastered railgun raw");eq(CanonEmission.levelFactor(5).doubleValue()*24,40.8,"LV5 raw");
        p.setHealth(100);tag.putString("shield","50");AbilityStorage.save(p);
        System.out.println("QA_A382_CAPACITY_PASS levels=5 schools=12 guns=6 switches=100 titans=12 nativeSave=true");
    }
}
