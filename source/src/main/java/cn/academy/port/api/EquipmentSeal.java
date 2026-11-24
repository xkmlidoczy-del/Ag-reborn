package cn.academy.port.api;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ItemCapability;

/**
 * Opt-in defense lease. Equipment consults active() before healing, restocking and death
 * protection.
 */
public interface EquipmentSeal {
    ItemCapability<EquipmentSeal, Void> CAPABILITY =
            ItemCapability.createVoid(
                    ResourceLocation.fromNamespaceAndPath("academy", "sealable_defense"),
                    EquipmentSeal.class);
    String DEADLINE = "academy.equipment_defense_sealed_until";

    boolean permits(ItemStack stack, LivingEntity wearer, ServerPlayer caster);

    static boolean active(LivingEntity wearer) {
        return wearer.getServer() != null
                && !cn.academy.port.highpower.PlayerShieldArmor.ignoresControl(wearer)
                && wearer.getPersistentData().getLong(DEADLINE)
                        > wearer.getServer().overworld().getGameTime();
    }

    static boolean apply(ServerPlayer caster, LivingEntity wearer, int ticks) {
        if (ticks <= 0 || !CombatControl.canAffect(caster, wearer)) return false;
        boolean eligible = false;
        for (var stack : wearer.getAllSlots())
            if (eligible(stack, wearer, caster)) {
                eligible = true;
                break;
            }
        if (!eligible && wearer instanceof Player player) {
            for (var stack : player.getInventory().items)
                if (eligible(stack, wearer, caster)) {
                    eligible = true;
                    break;
                }
        }
        if (!eligible) return false;
        long expires = caster.server.overworld().getGameTime() + ticks;
        var state = wearer.getPersistentData();
        if (expires <= state.getLong(DEADLINE)) return false;
        state.putLong(DEADLINE, expires);
        return true;
    }

    private static boolean eligible(ItemStack stack, LivingEntity wearer, ServerPlayer caster) {
        var provider = stack.isEmpty() ? null : stack.getCapability(CAPABILITY);
        return provider != null && provider.permits(stack, wearer, caster);
    }
}
