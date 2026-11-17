package cn.academy.biohum;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** A staged action with a server clock shared by all client pose renderers. */
public interface NpcMove {
    String id();
    String label();
    String interaction();
    int windup();
    int recovery();

    boolean start(HumNpc npc, LivingEntity target);
    boolean finish(HumNpc npc, LivingEntity target);
    void interact(HumNpc npc, ServerPlayer player);

    default void defend(HumNpc npc, ServerLevel level) {}

    static NpcMove find(String id) {
        if (id.isEmpty()) return null;
        var original = SignatureMove.find(id);
        return original != null ? original : RosterMove.find(id);
    }

    static NpcMove of(HumNpc npc) {
        return find(npc.profile().abilities().getFirst());
    }
}
