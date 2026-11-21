package qa.seal;

import cn.academy.port.api.EquipmentSeal;
import cn.academy.port.api.NativeCombatDamage;

import com.mojang.authlib.GameProfile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Two independent dedicated JVMs run this opt-in test defense against a single copied QA world. */
@Mod("academy_equipment_probe")
public final class EquipmentRestartProbe {
    private Husk wearer;
    private LivingEntity protectedBody;
    private int ticks;
    private long deadline;

    public EquipmentRestartProbe(IEventBus bus) {
        bus.addListener(this::register);
        NeoForge.EVENT_BUS.addListener(this::death);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }

    private void register(RegisterCapabilitiesEvent e) {
        e.registerItem(
                EquipmentSeal.CAPABILITY,
                (stack, ctx) -> (actual, body, caster) -> true,
                Items.WOODEN_SWORD);
    }

    private void death(LivingDeathEvent e) {
        if (e.getEntity() == protectedBody && !EquipmentSeal.active(protectedBody)) {
            e.setCanceled(true);
            protectedBody.setHealth(4);
        }
    }

    private void maintain(Husk body) {
        if (EquipmentSeal.active(body)) return;
        body.setHealth(body.getMaxHealth());
        if (body.getOffhandItem().isEmpty())
            body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.STICK));
    }

    private void check(boolean condition, String label) {
        if (!condition) throw new IllegalStateException("EQUIPMENT_QA " + label);
    }

    private void tick(ServerTickEvent.Post event) {
        try {
            verify(event);
        } catch (java.io.IOException failed) {
            throw new java.io.UncheckedIOException(failed);
        }
    }

    private void verify(ServerTickEvent.Post e) throws java.io.IOException {
        if (!e.getServer().isDedicatedServer()) return;
        var level = e.getServer().overworld();
        String phase = System.getProperty("aca.qa.seal.phase");
        if (phase == null || ++ticks < 8) return;
        Path save = Path.of("probe-id.txt");
        if (phase.equals("write") && ticks == 8) {
            level.setChunkForced(0, 0, true);
            level.getChunk(0, 0);
            wearer = EntityType.HUSK.create(level);
            wearer.setNoAi(true);
            wearer.setNoGravity(true);
            wearer.setPersistenceRequired();
            wearer.moveTo(4, 110, 4);
            wearer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            var sword = new ItemStack(Items.WOODEN_SWORD);
            sword.set(
                    net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                    Component.literal("同一把测试剑"));
            wearer.setItemSlot(EquipmentSlot.MAINHAND, sword);
            level.addFreshEntity(wearer);
            protectedBody = wearer;
            maintain(wearer);
            var normal =
                    NativeCombatDamage.hurtDetailed(wearer, level.damageSources().magic(), 2000);
            check(normal.rescued() && wearer.getHealth() == 4, "unsealed rescue");
            wearer.setHealth(30);
            wearer.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            var caster = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "SealProbe"));
            check(EquipmentSeal.apply(caster, wearer, 100), "apply");
            maintain(wearer);
            deadline = wearer.getPersistentData().getLong(EquipmentSeal.DEADLINE);
            check(
                    wearer.getHealth() == 30 && wearer.getOffhandItem().isEmpty(),
                    "pre-restart suppression");
            Files.writeString(save, wearer.getUUID() + "\n" + deadline);
            System.out.println(
                    "SEAL_WRITE " + wearer.getUUID() + " " + level.getGameTime() + " " + deadline);
        }
        if (!phase.equals("read")) return;
        if (wearer == null) {
            var lines = Files.readAllLines(save);
            level.getChunk(0, 0);
            if (!(level.getEntity(UUID.fromString(lines.get(0))) instanceof Husk restored)) {
                check(ticks < 60, "saved entity missing");
                return;
            }
            wearer = restored;
            protectedBody = wearer;
            deadline = Long.parseLong(lines.get(1));
            check(
                    EquipmentSeal.active(wearer)
                            && wearer.getPersistentData().getLong(EquipmentSeal.DEADLINE)
                                    == deadline,
                    "same deadline after restart");
            maintain(wearer);
            check(
                    wearer.getHealth() == 30
                            && wearer.getOffhandItem().isEmpty()
                            && wearer.getMainHandItem().getCount() == 1
                            && wearer.getMainHandItem().getHoverName().getString().equals("同一把测试剑"),
                    "same item and balances");
            var copy = EntityType.HUSK.create(level);
            var tag = new CompoundTag();
            wearer.saveWithoutId(tag);
            copy.load(tag);
            copy.setUUID(UUID.randomUUID());
            copy.moveTo(5, 110, 4);
            level.addFreshEntity(copy);
            protectedBody = copy;
            var hit = NativeCombatDamage.hurtDetailed(copy, level.damageSources().magic(), 2000);
            check(
                    !hit.rescued() && copy.getHealth() == 0,
                    "equipped defense did not rescue during seal");
            protectedBody = wearer;
            System.out.println(
                    "SEAL_RESTORED "
                            + wearer.getUUID()
                            + " "
                            + level.getGameTime()
                            + " "
                            + deadline);
        }
        if (level.getGameTime() == deadline) {
            maintain(wearer);
            check(
                    wearer.getHealth() == 100
                            && wearer.getOffhandItem().is(Items.STICK)
                            && wearer.getOffhandItem().getCount() == 1,
                    "restore supply once at deadline");
            wearer.invulnerableTime = 0;
            var rescued =
                    NativeCombatDamage.hurtDetailed(wearer, level.damageSources().magic(), 2000);
            check(
                    rescued.rescued()
                            && wearer.getHealth() == 4
                            && wearer.getMainHandItem().getCount() == 1,
                    "rescue resumes; original sword retained");
            System.out.println(
                    "SEAL_FINISHED "
                            + wearer.getUUID()
                            + " "
                            + level.getGameTime()
                            + " hp="
                            + wearer.getHealth());
        }
    }
}
