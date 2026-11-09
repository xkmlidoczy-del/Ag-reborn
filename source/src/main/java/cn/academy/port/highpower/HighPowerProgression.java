package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.DevelopmentProcess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Explicit learning opt-in, after real finite developer work. Never enrolls from a cast packet. */
public final class HighPowerProgression {
    private HighPowerProgression() {}
    /** Capacity follows genuine ability progress; neither this hook nor the core API refills balances. */
    public static boolean promote(ServerPlayer player) {
        if(player==null||!player.server.isSameThread())return false;
        var state=AbilityStorage.get(player);
        if(!HighPowerCategories.active(state)||!java.util.stream.Stream.concat(HighPowerSkills.ALL.stream(),cn.academy.port.canon.CanonSkills.ALL.stream()).anyMatch(skill->skill.category().equals(state.category)&&state.learned(skill.id()))
                ||!HighPowerCombat.promoteLevel(player,state.level))return false;
        player.displayClientMessage(Component.translatable("screen.academy.highpower.promoted",state.level),false);
        return true;
    }
    // AbilityProgressLifecycle binds immutable native before/after XP receipts to earned growth.
    // SkillExpAddedEvent alone cannot prove an actual delta after reentrant classic callbacks.
    /** Only finite developer completion can certify an integer-level transition. */
    public static DevelopmentProcess.Action leveling(ServerPlayer player,DevelopmentProcess.Action original){
        return new DevelopmentProcess.Action(){
            public String id(){return original.id();}
            public int stimulations(){return original.stimulations();}
            public boolean validate(DevelopmentProcess.Developer developer){return original.validate(developer);}
            public void complete(){HighPowerCombat.completeEarnedLevel(player,original::complete);}
        };
    }

    /**
     * Canon categories have no high-power purchase, so a learned canon skill is the server-owned opt-in.
     * Without it every canon cast failed HighPowerResources.ready. Driven by stored state, never by a cast packet.
     * The same holds for an advanced classic skill learned outside the development UI (`/aim learn_hp`, `/aim maxhp`):
     * it was learned but never enrolled, so every cast was refused as "not ready".
     */
    public static boolean certifyCanon(ServerPlayer player) {
        if (player == null || !player.server.isSameThread() || HighPowerCombat.enabled(player)) return false;
        var state = AbilityStorage.get(player);
        boolean canon = cn.academy.port.canon.CanonSkills.activeCategory(state.category)
                && cn.academy.port.canon.CanonSkills.ALL.stream().anyMatch(s -> s.category().equals(state.category) && state.learned(s.id()));
        boolean classic = HighPowerSkills.ALL.stream().anyMatch(s -> s.category().equals(state.category) && state.learned(s.id()));
        if (!canon && !classic || !HighPowerCategories.active(state)
                || HighPowerCombat.unsupportedReason(player).isPresent() || !HighPowerCombat.enroll(player, state.level)) return false;
        HighPowerResources.initializeBudget(player);
        if (state.learned(HighPowerSkills.SCATTER_PLATES)) ScatterPlateStorage.ensure(player);
        if (state.learned("zero_gap_armor")) ZeroGapArmorStorage.ensureLearned(player);
        HighPowerCombat.synchronizeEarnedProgress(player);
        return true;
    }

    /** UI-specific ingress: reject before work and return the precise compatibility reason to its modal. */
    public static boolean requestLearning(ServerPlayer player,DevelopmentProcess.Developer developer,String wire) {
        var request=HighPowerLearningProtocol.parse(wire);
        if(player==null||request==null||!player.server.isSameThread()||!player.isAlive()||player.isRemoved()||player.isSpectator())return false;
        var reason=HighPowerCombat.unsupportedReason(player);
        boolean accepted=reason.isEmpty()&&(developer==null
                ?cn.academy.port.develop.DevelopmentController.startSkill(player,request.skill())
                :cn.academy.port.develop.DevelopmentController.startSkill(player,developer,request.skill()));
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,new cn.academy.port.AcademyNetwork.ClientData(
                HighPowerLearningProtocol.result(request,accepted,reason.orElse(""))));
        return accepted;
    }

    public static DevelopmentProcess.Action learning(ServerPlayer player, AbilityProgress state,
                                                     DevelopmentProcess.Action original) {
        // The original native action retains its own category/prerequisite/tier checks. Capture its
        // registered identity as well, so another admitted category cannot finish an old action.
        var skill = original.id().startsWith("skill:") ? HighPowerSkills.find(original.id().substring(6)).orElse(null) : null;
        return new DevelopmentProcess.Action() {
            public String id() { return original.id(); }
            public int stimulations() { return original.stimulations(); }
            public boolean validate(DevelopmentProcess.Developer developer) {
                if (AbilityStorage.get(player) != state || skill == null || !skill.category().equals(state.category)
                        || !HighPowerCategories.active(state) || !original.validate(developer)) return false;
                var reason = HighPowerCombat.unsupportedReason(player);
                if (reason.isPresent()) {
                    player.displayClientMessage(Component.literal("ACA high-power learning unavailable: " + reason.get()), false);
                    return false;
                }
                return true;
            }
            public void complete() {
                // validate() has just checked this same server-owned state. Existing ledgers are never reset.
                boolean fresh = !HighPowerCombat.enabled(player);
                if (fresh) {
                    if (!HighPowerCombat.enroll(player, state.level)) throw new IllegalStateException("Validated high-power enrollment failed");
                    HighPowerResources.initializeBudget(player);
                }
                original.complete();
                // Only the first genuine purchase may partition the existing base shield.
                // The storage seam restores exact prior shares, and refuses missing/corrupt
                // initialized ledgers; neither relearning nor category changes refill them.
                if (skill.id().equals(HighPowerSkills.SCATTER_PLATES)) ScatterPlateStorage.ensure(player);
                if (skill.id().equals("zero_gap_armor")) ZeroGapArmorStorage.ensureLearned(player);
                // Native category changes retain XP but can change its denominator. Relearning
                // awards no XP and therefore cannot certify a newly interpreted growth coordinate.
                if (fresh) HighPowerCombat.synchronizeEarnedProgress(player);
            }
        };
    }
}
