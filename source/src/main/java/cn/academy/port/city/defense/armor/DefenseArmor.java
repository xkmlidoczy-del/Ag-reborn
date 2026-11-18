package cn.academy.port.city.defense.armor;

import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

/** Issued equipment is real armor, with the source module's materials and models. */
@net.neoforged.fml.common.EventBusSubscriber(modid="academy")
public final class DefenseArmor {
    @net.neoforged.bus.api.SubscribeEvent public static void issueAmbient(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event){
        if(event.getEntity() instanceof cn.academy.port.city.CityNpcEntity npc&&!npc.level().isClientSide&&npc.look()==cn.academy.port.city.CityNpcEntity.Look.GUARD&&!npc.getPersistentData().getBoolean("academy_police_uniform_issued")){
            equip(npc,false);npc.getPersistentData().putBoolean("academy_police_uniform_issued",true);
        }
    }
    public static void register(IEventBus bus){
        DefenseArmorMaterials.MATERIALS.register(bus);DefenseArmorItems.ITEMS.register(bus);
        bus.addListener(DefenseArmor::creative);
    }
    private static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey()==CreativeModeTabs.COMBAT)DefenseArmorItems.ALL.forEach(h->event.accept(h.get()));}
    public static void equip(Mob mob,boolean o5){
        Item[] items=o5?new Item[]{DefenseArmorItems.BLACK_GOLD_HELMET.get(),DefenseArmorItems.BLACK_GOLD_CHESTPLATE.get(),DefenseArmorItems.BLACK_GOLD_LEGGINGS.get(),DefenseArmorItems.BLACK_GOLD_BOOTS.get()}:
            new Item[]{DefenseArmorItems.POLICE_HELMET.get(),DefenseArmorItems.POLICE_CHESTPLATE.get(),DefenseArmorItems.POLICE_LEGGINGS.get(),DefenseArmorItems.POLICE_BOOTS.get()};
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        for(int i=0;i<slots.length;i++){mob.setItemSlot(slots[i],new ItemStack(items[i]));mob.setDropChance(slots[i],0);}
    }
    /** Cancel before native armor, absorption and the Academy shield bridge see the hit. */
    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void protectPlayer(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
                && blocksCombat(player, event.getSource())) event.setCanceled(true);
    }

    private static boolean blocksCombat(LivingEntity wearer, net.minecraft.world.damagesource.DamageSource source) {
        return (source.getEntity() != null || source.getDirectEntity() != null
                || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                || source.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE))
                && !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL)
                && full(wearer, true);
    }

    public static boolean full(LivingEntity entity,boolean o5){
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}){
            if(!(entity.getItemBySlot(slot).getItem() instanceof DefenseArmorItem a)||a.isO5()!=o5||a.getEquipmentSlot()!=slot)return false;
        }return true;
    }
}
