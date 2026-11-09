package cn.academy.port.develop;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.SkillCatalog;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server integration seam, called by the gameplay request, tick and lifecycle handlers. */
public final class DevelopmentController {
    private static final Map<UUID, DevelopmentProcess> PROCESSES = new HashMap<>();
    private DevelopmentController() {}

    public static DevelopmentProcess process(ServerPlayer player) {
        return PROCESSES.computeIfAbsent(player.getUUID(), ignored -> new DevelopmentProcess());
    }

    /** Classic PortableDevData re-reads the CURRENT MAIN HAND on every energy access. */
    public static DevelopmentProcess.Developer portable(ServerPlayer player) {
        return new DevelopmentProcess.Developer() {
            private ItemStack stack() {
                ItemStack current = player.getMainHandItem();
                return current.is(AcademyCraft.DEVELOPER.get()) ? current : ItemStack.EMPTY;
            }
            public DeveloperType type() { return DeveloperType.PORTABLE; }
            public boolean tryPullEnergy(double amount) { return new DeveloperItemEnergy(stack(), type()).tryPull(amount); }
            public double energy() { return new DeveloperItemEnergy(stack(), type()).energy(); }
            public double maxEnergy() { return stack().isEmpty() ? 0 : type().energy; }
        };
    }

    public static boolean holdingPortable(ServerPlayer player) { return player.getMainHandItem().is(AcademyCraft.DEVELOPER.get()); }

    public static boolean startSkill(ServerPlayer player, String id) {
        if (!holdingPortable(player)) return false;
        return startSkill(player, portable(player), id);
    }

    /** Machine sessions select the server-owned developer; the client cannot provide a tier or battery. */
    public static boolean startSkill(ServerPlayer player, DevelopmentProcess.Developer developer, String id) {
        var state = AbilityStorage.get(player);
        var found = SkillCatalog.find(state.category, id);
        if (found.isEmpty() || state.learned(id)) return false;
        var action = DevelopmentActions.skill(state, found.get());
        if (cn.academy.port.highpower.HighPowerSkills.contains(id)) action = cn.academy.port.highpower.HighPowerProgression.learning(player, state, action);
        return start(player, developer, action);
    }

    /** No arbitrary category selector: source acquisition chooses an induction factor or a random category. */
    public static boolean startLevel(ServerPlayer player) {
        return startLevel(player, DevelopmentActions.CategoryItems.NONE);
    }

    public static boolean startLevel(ServerPlayer player, DevelopmentActions.CategoryItems items) {
        if (!holdingPortable(player)) return false;
        return startLevel(player, portable(player), items);
    }

    public static boolean startLevel(ServerPlayer player, DevelopmentProcess.Developer developer, DevelopmentActions.CategoryItems items) {
        return start(player, developer, cn.academy.port.highpower.HighPowerProgression.leveling(player, DevelopmentActions.level(AbilityStorage.get(player), items, player.getRandom()::nextInt)));
    }

    /** Trusted machine adapters may use this when real normal/advanced developer devices exist. */
    public static boolean start(ServerPlayer player, DevelopmentProcess.Developer developer, DevelopmentProcess.Action action) {
        if (player==null||player.isRemoved()||!player.isAlive()||player.isSpectator()||!player.serverLevel().getServer().isSameThread()||!action.validate(developer))return false;
        process(player).start(developer, action);
        return true;
    }

    public static void tick(ServerPlayer player, Consumer<DevelopmentProcess.Snapshot> sync) {
        var process = PROCESSES.get(player.getUUID());
        if (process == null) return;
        if(player.isRemoved()||!player.isAlive()||player.isSpectator())process.abort();
        boolean wasDeveloping = process.isDeveloping();
        process.tick(sync);
        if (wasDeveloping && process.state() == DevelopmentProcess.State.DONE) AbilityStorage.save(player);
    }

    public static void abort(ServerPlayer player) {
        var process = PROCESSES.get(player.getUUID());
        if (process != null) process.abort();
    }

    /** Logout/death/clone discard active progress: original DevelopData never enabled NBT storage. */
    public static void remove(ServerPlayer player) { cn.academy.port.machine.MachineDeveloperSessions.close(player); PROCESSES.remove(player.getUUID()); }
    public static void clear() { cn.academy.port.machine.MachineDeveloperSessions.clear(); PROCESSES.clear(); }

    public static CompoundTag encode(DevelopmentProcess.Snapshot snapshot) {
        var tag = new CompoundTag();
        tag.putString("state", snapshot.state().name());
        tag.putString("type", snapshot.developerType() == null ? "" : snapshot.developerType().name());
        tag.putString("action", snapshot.action());
        tag.putInt("stimulation", snapshot.stimulation());
        tag.putInt("max_stimulations", snapshot.maxStimulations());
        tag.putInt("tick_this_stimulation", snapshot.tickThisStimulation());
        tag.putDouble("energy", snapshot.energy());
        tag.putDouble("max_energy", snapshot.maxEnergy());
        tag.putDouble("source_progress", snapshot.sourceProgress());
        tag.putDouble("progress", snapshot.normalizedProgress());
        return tag;
    }
}
