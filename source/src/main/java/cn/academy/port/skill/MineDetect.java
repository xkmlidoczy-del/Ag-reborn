/* AcademyCraft 1.0.7 MineDetect server adapter. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Immediate accepted keydown; ore discovery/effects stay local to the casting player. */
public final class MineDetect {
    public static final String ID=MineDetectRules.ID;
    private static final Set<UUID> PERFORMING=new HashSet<>();
    private MineDetect() {}
    public static boolean perform(ServerPlayer player) {
        if(player==null||!player.serverLevel().getServer().isSameThread()||player.isRemoved()
                ||!player.isAlive()||player.isSpectator()||!Double.isFinite(player.getX())
                ||!Double.isFinite(player.getY())||!Double.isFinite(player.getZ()))return false;
        if(!PERFORMING.add(player.getUUID()))return false;
        try {
            var state=AbilityStorage.get(player);
            var plan=MineDetectRules.prepare(state,player.getAbilities().instabuild);
            if(plan==null)return false;
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,MineDetectRules.TIME));
            MineDetectRules.awardExperience(state,plan);
            cn.academy.port.achievements.ClassicAchievements.trigger(player,"electromaster.mine_detect");
            var data=effectData(player,plan);
            PacketDistributor.sendToPlayer(player,new AcademyNetwork.ClientData(data));
            state.setCooldown(ID,MineDetectRules.cooldown(state.exp(ID)));
            return true;
        } finally {PERFORMING.remove(player.getUUID());}
    }
    /** Trusted native fixture seam; ingress accepts only authenticated slot presses. */
    public static CompoundTag effectData(ServerPlayer player,MineDetectRules.Plan plan) {
        var data=new CompoundTag();data.putString("kind",ID);data.putInt("entity",player.getId());
        data.putFloat("range",plan.range());data.putBoolean("advanced",plan.advanced());return data;
    }
}
