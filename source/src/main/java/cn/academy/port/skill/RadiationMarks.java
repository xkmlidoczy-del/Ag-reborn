/* AcademyCraft 1.0.7 MDDamageHelper/RadiationIntensify adaptation. See NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AbilityDamage;
import cn.academy.port.core.ClassicRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
@EventBusSubscriber(modid="academy")
public final class RadiationMarks {
    private static final String TICKS="academy:md_mark_ticks", RATE="academy:md_mark_rate";
    private RadiationMarks() {}
    public static double displayedMastery(double maxCp,double initLevel5Cp) {return cn.academy.port.core.ClassicPassiveSkills.radiationMastery(maxCp,initLevel5Cp);}
    public static double rate(double mastery) {return cn.academy.port.core.ClassicPassiveSkills.radiationRate(mastery);}
    /** Attack first; apply mark even if the damage hook rejects, exactly as the classic helper. */
    public static void attack(ServerPlayer player,Entity target,double raw) {
        attack(player,"meltdowner.electron_bomb",target,raw);
    }
    /** Preserve the originating canonical skill in shared MD attacks. */
    public static void attack(ServerPlayer player,String skill,Entity target,double raw) {
        AbilityDamage.attack(player,skill,target,raw);
        var state=AbilityStorage.get(player);
        if(state.learned("rad_intensify")) {
            var data=target.getPersistentData();data.putInt(TICKS,Math.max(60,player.getPersistentData().getInt(TICKS)));data.putFloat(RATE,(float)rate(state.exp("rad_intensify")));
            // The source sync deliberately marks the caster on clients, while damage marks the target.
            var tag=new net.minecraft.nbt.CompoundTag();tag.putString("kind","radiation_mark");tag.putInt("entity",player.getId());tag.putUUID("entity_uuid",player.getUUID());tag.putInt("ticks",Math.min(12000,data.getInt(TICKS)));
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(player.serverLevel(),null,player.getX(),player.getY(),player.getZ(),20,new cn.academy.port.AcademyNetwork.ClientData(tag));
        }
    }
    @SubscribeEvent public static void tick(EntityTickEvent.Pre event) {if(event.getEntity() instanceof LivingEntity&&!event.getEntity().level().isClientSide){var data=event.getEntity().getPersistentData();int tick=data.getInt(TICKS);if(tick>0)data.putInt(TICKS,tick-1);}}
    @SubscribeEvent public static void hurt(LivingIncomingDamageEvent event) {if(!event.getEntity().level().isClientSide){var data=event.getEntity().getPersistentData();if(data.getInt(TICKS)>0){float multiplier=data.getFloat(RATE);if(Double.isFinite(multiplier)&&multiplier>=0)event.setAmount((float)(event.getAmount()*multiplier));}}}
}
