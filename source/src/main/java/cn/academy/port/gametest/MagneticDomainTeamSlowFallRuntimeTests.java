package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.gametest.HighPowerMagneticDomainRuntimeTests.Fixture;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.MagneticDomain;
import cn.academy.port.highpower.MagneticDomainTeamSlowFall;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native NEW152 teammate receipts and revocation. Declared server motion fixtures exercise
 * native effects and callbacks, not remote-client movement or two-client synchronization.
 * This batch never clears global domain/consent state owned by other native fixtures. */
@GameTestHolder("academy_magnetic_domain_team")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class MagneticDomainTeamSlowFallRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "magnetic_domain_team_slowfall";
    private MagneticDomainTeamSlowFallRuntimeTests() {}

    @SubscribeEvent public static void install(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(); ListTag size = new ListTag();
        for (int extent : new int[] {32, 24, 32}) size.add(IntTag.valueOf(extent));
        tag.put("size", size); ListTag blocks = new ListTag(); CompoundTag block = new CompoundTag();
        ListTag position = new ListTag(); for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        block.put("pos", position); block.putInt("state", 0); blocks.add(block);
        tag.put("blocks", blocks); tag.put("entities", new ListTag()); ListTag palette = new ListTag();
        CompoundTag air = new CompoundTag(); air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_magnetic_domain_team", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_01", timeoutTicks=110)
    public static void team_membership_without_consent_and_missing_or_different_team_refuse(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); f.falling(); f.tick();
            h.assertFalse(f.effect(), "same actual team alone does not authorize a native effect");
            var board = h.getLevel().getScoreboard();
            board.removePlayerFromTeam(f.recipient.p.getScoreboardName(), f.team);
            h.assertFalse(MagneticDomainTeamSlowFall.consent(f.recipient.p, true), "teamless opt-in refuses");
            PlayerTeam other = f.newTeam(); board.addPlayerToTeam(f.recipient.p.getScoreboardName(), other);
            h.assertTrue(MagneticDomainTeamSlowFall.consent(f.recipient.p, true), "recipient may opt in to their own actual team");
            f.tick(); h.assertFalse(f.effect(), "another team's paid field cannot supply the effect");
            h.assertTrue(MagneticDomain.active(f.caster.p), "refusals leave the paid source domain active");
            f.noRecipientLedger(); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_02", timeoutTicks=110)
    public static void ordinary_unlearned_recipient_gets_caster_attributed_effect_without_resources_or_motion_writes(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); f.falling(); f.recipient.p.fallDistance = 9;
            SourceWitness witness = f.witness(f.recipient.p);
            Resources recipient = Resources.capture(f.recipient.p), caster = Resources.capture(f.caster.p);
            Vec3 position = f.recipient.p.position(), velocity = f.recipient.p.getDeltaMovement();
            boolean grounded = f.recipient.p.onGround();
            f.optIn(); f.tick();
            MobEffectInstance effect = f.requireEffect();
            h.assertTrue(effect.getDuration() == 2 && effect.getAmplifier() == 0
                    && !effect.isAmbient() && !effect.isVisible() && !effect.showIcon(), "bounded native two-tick effect shape");
            h.assertTrue(witness.sources.size() == 1 && witness.sources.getFirst() == f.caster.p,
                    "real native Added event names the paid caster, not the recipient");
            f.tick();
            h.assertTrue(witness.sources.size() == 2 && witness.sources.getLast() == f.caster.p
                    && f.recipient.p.getEffect(MobEffects.SLOW_FALLING) == effect, "refresh keeps the native owned instance and real caster");
            h.assertTrue(f.recipient.p.position().equals(position) && f.recipient.p.getDeltaMovement().equals(velocity)
                    && f.recipient.p.fallDistance == 9 && f.recipient.p.onGround() == grounded,
                    "team grant writes neither position, velocity, grounded state nor fall distance");
            recipient.same(h, f.recipient.p, "ordinary recipient"); caster.same(h, f.caster.p, "already paid caster");
            f.noRecipientLedger(); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_03", timeoutTicks=110)
    public static void horizontal_radius_boundary_admits_but_next_representable_position_refuses(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); Vec3 at = f.caster.p.position().add(0, 0, MagneticDomain.RADIUS);
            f.loadBoundary(at);
            f.recipient.place(at); f.falling(); f.optIn(); f.tick(); f.requireEffect();
            f.offAndExpire();
            Vec3 outside = new Vec3(at.x, at.y, Math.nextUp(at.z));
            h.assertTrue(outside.distanceToSqr(f.caster.p.position()) > MagneticDomain.RADIUS * MagneticDomain.RADIUS, "fixture is strictly outside the radius after world-coordinate rounding");
            f.recipient.place(outside); f.falling(); f.optIn(); f.tick();
            h.assertFalse(f.effect(), "next representable position beyond the radius is outside");
            f.recipient.place(at); f.falling(); f.tick(); f.requireEffect();
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_04", timeoutTicks=110)
    public static void vertical_distance_uses_the_same_spherical_sample_boundary(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); Vec3 at = f.caster.p.position().add(0, MagneticDomain.RADIUS, 0);
            f.loadBoundary(at);
            f.recipient.place(at); f.falling(); f.optIn(); f.tick(); f.requireEffect();
            f.offAndExpire();
            Vec3 outside = new Vec3(at.x, Math.nextUp(at.y), at.z);
            // At negative Y, the one-metre centre offset and subtraction can round
            // the first nextUp foot position back to exactly48. Select the first
            // representable position whose actual native centre sample is outside.
            while(outside.add(0,1,0).distanceToSqr(f.caster.p.position().add(0,1,0))<=MagneticDomain.RADIUS*MagneticDomain.RADIUS)
                outside=new Vec3(outside.x,Math.nextUp(outside.y),outside.z);
            h.assertTrue(outside.add(0, 1, 0).distanceToSqr(f.caster.p.position().add(0, 1, 0)) > MagneticDomain.RADIUS * MagneticDomain.RADIUS,
                    "both sample points remain strictly outside after adding the existing one-metre offset");
            f.recipient.place(outside); f.falling(); f.optIn(); f.tick();
            h.assertFalse(f.effect(), "same horizontal location does not bypass vertical range");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_05", timeoutTicks=110)
    public static void native_collider_wall_blocks_entry_and_new_wall_stops_refresh(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); f.recipient.place(f.caster.p.position().add(0, 0, 6)); f.falling(); f.optIn();
            BlockPos wall = BlockPos.containing(f.caster.p.position().add(0, 1, 3));
            f.block(wall, Blocks.STONE.defaultBlockState()); f.tick();
            h.assertFalse(f.effect(), "native collider clip prevents a grant through the wall");
            f.block(wall, Blocks.AIR.defaultBlockState()); f.tick(); MobEffectInstance old = f.requireEffect();
            f.block(wall, Blocks.STONE.defaultBlockState()); f.tick();
            h.assertTrue(f.recipient.p.getEffect(MobEffects.SLOW_FALLING) == old && old.getDuration() == 2,
                    "new wall stops ownership without deleting the bounded native residue");
            f.expire(); f.falling(); f.tick(); h.assertFalse(f.effect(), "wall prevents renewal after native expiry");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_06", timeoutTicks=110)
    public static void explicit_off_leaves_only_native_residue_and_requires_a_new_opt_in(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); f.falling(); f.optIn(); f.tick(); MobEffectInstance old = f.requireEffect();
            h.assertTrue(MagneticDomainTeamSlowFall.consent(f.recipient.p, false), "recipient explicitly revokes");
            h.assertFalse(MagneticDomainTeamSlowFall.consented(f.recipient.p), "off removes authority immediately");
            f.tick(); h.assertTrue(f.recipient.p.getEffect(MobEffects.SLOW_FALLING) == old, "off does not remove native potion state");
            f.expire(); f.falling(); f.tick(); h.assertFalse(f.effect(), "remaining inside the field does not restore consent");
            f.optIn(); f.tick(); f.requireEffect(); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_07", timeoutTicks=110)
    public static void actual_team_change_revokes_and_rejoining_does_not_restore_old_consent(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); f.falling(); f.optIn(); f.tick(); f.requireEffect();
            var board = h.getLevel().getScoreboard();
            board.removePlayerFromTeam(f.recipient.p.getScoreboardName(), f.team); f.tick();
            h.assertFalse(MagneticDomainTeamSlowFall.consented(f.recipient.p), "leaving the actual team revokes its session consent");
            f.expire(); board.addPlayerToTeam(f.recipient.p.getScoreboardName(), f.team);
            f.falling(); f.tick(); h.assertFalse(f.effect(), "rejoining the same team object cannot recover old consent");
            f.optIn(); f.tick(); f.requireEffect(); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_08", timeoutTicks=110)
    public static void connection_replacement_and_native_disconnect_revoke_recipient_authority(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); f.falling(); f.optIn(); f.tick(); f.requireEffect();
            Actor r = f.recipient;
            r.p.connection = new ServerGamePacketListenerImpl(r.p.server, r.connection, r.p, r.cookie);
            f.tick(); h.assertFalse(MagneticDomainTeamSlowFall.consented(r.p), "replacement connection cannot inherit old consent");
            f.expire(); f.falling(); f.tick(); h.assertFalse(f.effect(), "new connection receives no automatic grant");
            f.optIn(); f.tick(); f.requireEffect();
            r.connection.disconnect(Component.literal("NEW152 native disconnect fixture"));
            r.channel.runPendingTasks();
            h.assertFalse(r.connection.isConnected(), "actual native connection is disconnected");
            f.tick(); h.assertFalse(MagneticDomainTeamSlowFall.consented(r.p), "disconnected recipient loses authority");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_09", timeoutTicks=130)
    public static void real_unaffordable_upkeep_ends_team_benefit_without_free_payment_window(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            // Explicit opening balance supplies exactly one paid window. No fixture pump refills CP.
            f.caster.initialCp(1); f.start();
            h.assertTrue(f.caster.cp() == 0, "opening native payment consumed the sole CP");
            f.falling(); f.optIn(); f.tick(); f.requireEffect();
            long paidUntil = MagneticDomain.paidUntil(f.caster.p);
            h.runAfterDelay(21, () -> {
                h.assertFalse(MagneticDomain.active(f.caster.p), "real server upkeep ends an unaffordable domain");
                h.assertTrue(f.caster.cp() == 0 && MagneticDomain.paidUntil(f.caster.p) == 0,
                        "failed renewal cannot spend negative CP or issue another paid context");
                h.assertTrue(f.caster.p.getPersistentData().getCompound(MagneticDomain.PAYMENT_KEY).getLong("until") == paidUntil,
                        "failed upkeep retains the original persisted payment fence");
                f.expire(); f.falling(); f.tick(); h.assertFalse(f.effect(), "unpaid source cannot renew the recipient");
                f.noRecipientLedger(); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_10", timeoutTicks=110)
    public static void overlapping_casters_keep_one_stable_source_and_handoff_only_after_native_expiry(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); Fixture second = f.secondCaster(); f.ready(() -> {
            f.start(); h.assertTrue(second.toggle(1), "second real paid field starts");
            f.falling(); f.optIn(); SourceWitness witness = f.witness(f.recipient.p); f.tick();
            MobEffectInstance old = f.requireEffect();
            ServerPlayer selected = f.caster.p.getUUID().compareTo(second.p.getUUID()) < 0 ? f.caster.p : second.p;
            ServerPlayer alternate = selected == f.caster.p ? second.p : f.caster.p;
            h.assertTrue(witness.sources.getFirst() == selected, "initial overlap resolves deterministically by UUID");
            for (int n = 0; n < 4; n++) f.tick();
            h.assertTrue(witness.sources.size() == 5 && witness.sources.stream().allMatch(source -> source == selected)
                    && f.recipient.p.getEffect(MobEffects.SLOW_FALLING) == old, "repeated arbitration never swaps an active source or native object");
            MagneticDomain.abort(selected); int before = witness.sources.size(); f.tick();
            h.assertTrue(witness.sources.size() == before && f.recipient.p.getEffect(MobEffects.SLOW_FALLING) == old,
                    "alternate source cannot claim another field's unexpired native effect");
            f.expire(); f.falling(); f.tick();
            h.assertTrue(f.effect() && witness.sources.size() == before + 1 && witness.sources.getLast() == alternate,
                    "still-paid alternate obtains a fresh effect only after ordinary expiry");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_11", timeoutTicks=110)
    public static void foreign_potion_is_preserved_and_in_place_merge_suspends_the_existing_consent(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); f.falling();
            MobEffectInstance foreign = new MobEffectInstance(MobEffects.SLOW_FALLING, 180, 1);
            f.recipient.p.addEffect(foreign); f.optIn(); f.tick();
            h.assertTrue(f.recipient.p.getEffect(MobEffects.SLOW_FALLING) == foreign && foreign.getDuration() == 180
                    && foreign.getAmplifier() == 1, "preexisting foreign potion is neither shortened nor replaced");
            MagneticDomainTeamSlowFall.consent(f.recipient.p, false);
            f.recipient.p.removeEffect(MobEffects.SLOW_FALLING); f.optIn(); f.tick();
            MobEffectInstance owned = f.requireEffect();
            f.recipient.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 220, 0));
            h.assertTrue(f.recipient.p.getEffect(MobEffects.SLOW_FALLING) == owned && owned.getDuration() == 220,
                    "actual native foreign addition merged into the old object");
            f.tick(); h.assertFalse(MagneticDomainTeamSlowFall.consented(f.recipient.p), "foreign merge suspends the affected consent");
            h.assertTrue(owned.getDuration() == 220 && owned.isVisible(), "suspension does not rewrite foreign potion fields");
            f.recipient.p.removeEffect(MobEffects.SLOW_FALLING); f.tick();
            h.assertFalse(f.effect(), "foreign potion removal cannot silently restore the suspended grant");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_12", timeoutTicks=110)
    public static void real_milk_suspends_and_repeated_on_cannot_reacquire_without_explicit_off_on(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); f.falling(); f.optIn(); f.tick(); f.requireEffect();
            Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), f.recipient.p.serverLevel(), f.recipient.p);
            h.assertFalse(f.effect(), "actual native milk cured the effect");
            f.tick(); h.assertFalse(MagneticDomainTeamSlowFall.consented(f.recipient.p), "cure suspends current consent");
            h.assertFalse(MagneticDomainTeamSlowFall.consent(f.recipient.p, true), "idempotent on cannot bypass suspension");
            f.tick(); h.assertFalse(f.effect(), "current field cannot immediately undo the milk cure");
            MagneticDomainTeamSlowFall.consent(f.recipient.p, false); f.optIn(); f.falling(); f.tick();
            f.requireEffect(); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_13", timeoutTicks=110)
    public static void native_applicable_refusal_suspends_instead_of_retrying_the_same_consent(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); f.falling(); f.optIn();
            ApplicableVeto veto = new ApplicableVeto(f.recipient.p); f.caster.hook(veto); f.tick();
            h.assertTrue(veto.calls == 1 && !f.effect(), "real native Applicable veto refuses the first addition");
            veto.enabled = false; f.tick(); f.tick();
            h.assertTrue(veto.calls == 1 && !f.effect() && !MagneticDomainTeamSlowFall.consented(f.recipient.p),
                    "ending a native veto does not replay the refused consent");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_14", timeoutTicks=110)
    public static void added_callback_caster_abort_fences_late_commit_without_refund(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            double before = f.caster.cp(); f.start(); f.falling(); f.optIn();
            AddedAction hook = new AddedAction(f.recipient.p, event -> MagneticDomain.abort(f.caster.p)); f.caster.hook(hook);
            f.tick();
            h.assertTrue(hook.called && !MagneticDomain.active(f.caster.p) && !f.effect(),
                    "ending the actual source inside Added prevents its pending native write");
            h.assertTrue(f.caster.cp() == before - MagneticDomain.CP_COST, "callback abort does not refund its paid opening window");
            f.tick(); h.assertFalse(f.effect(), "ended source cannot produce an orphan benefit"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_15", timeoutTicks=110)
    public static void added_callback_recipient_off_fences_late_commit_and_keeps_caster_paid(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); f.ready(() -> {
            f.start(); f.falling(); f.optIn(); double paid = f.caster.cp();
            AddedAction hook = new AddedAction(f.recipient.p, event -> MagneticDomainTeamSlowFall.consent(f.recipient.p, false));
            f.caster.hook(hook); f.tick();
            h.assertTrue(hook.called && !f.effect() && !MagneticDomainTeamSlowFall.consented(f.recipient.p),
                    "recipient revocation in native Added defeats the pending commit");
            h.assertTrue(MagneticDomain.active(f.caster.p) && f.caster.cp() == paid, "recipient refusal neither ends nor refunds caster payment");
            f.noRecipientLedger(); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_16", timeoutTicks=120)
    public static void newly_started_own_domain_takes_priority_after_old_team_effect_expires(GameTestHelper h) {
        TeamFixture f = new TeamFixture(h); Fixture own = f.secondCaster(); f.ready(() -> {
            f.start(); falling(own.p);
            h.assertTrue(MagneticDomainTeamSlowFall.consent(own.p, true), "future caster first opts in as a teammate");
            SourceWitness witness = f.witness(own.p); MagneticDomainTeamSlowFall.tickRecipient(own.p);
            MobEffectInstance old = own.p.getEffect(MobEffects.SLOW_FALLING);
            h.assertTrue(old != null && witness.sources.getLast() == f.caster.p, "real teammate effect exists before own activation");
            h.assertTrue(own.toggle(1), "recipient explicitly activates their own paid domain");
            MagneticDomain.tick(own.p); MagneticDomainTeamSlowFall.tickRecipient(own.p);
            h.assertTrue(MagneticDomain.active(own.p) && own.p.getEffect(MobEffects.SLOW_FALLING) == old,
                    "own priority preserves the old two-tick native residue without treating it as foreign authority");
            Vec3 position = own.p.position(); expire(h, own.p); own.p.setPos(position); falling(own.p);
            h.runAfterDelay(1, () -> {
                // A genuine next server frame gives the ordinary self-domain tick its next refresh.
                MagneticDomain.tick(own.p); MagneticDomainTeamSlowFall.tickRecipient(own.p);
                h.assertTrue(own.p.hasEffect(MobEffects.SLOW_FALLING) && witness.sources.getLast() == own.p,
                        "own domain obtains its self-attributed native effect after residue expires");
                int count = witness.sources.size(); MagneticDomainTeamSlowFall.tickRecipient(own.p);
                h.assertTrue(witness.sources.size() == count, "team updater cannot overwrite an active self-domain lease");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_17", timeoutTicks=125)
    public static void team_off_before_first_own_domain_tick_cannot_poison_the_self_lease(GameTestHelper h) {
        ownDomainSurvivesTeamOff(h, false);
    }

    @GameTest(template=TEMPLATE, batch=BATCH+"_18", timeoutTicks=125)
    public static void team_off_after_first_own_domain_defer_cannot_poison_the_self_lease(GameTestHelper h) {
        ownDomainSurvivesTeamOff(h, true);
    }

    private static void ownDomainSurvivesTeamOff(GameTestHelper h, boolean afterFirstTick) {
        TeamFixture f = new TeamFixture(h); Fixture own = f.secondCaster(); f.ready(() -> {
            f.start(); falling(own.p);
            h.assertTrue(MagneticDomainTeamSlowFall.consent(own.p, true), "future caster explicitly accepts teammate slow falling");
            SourceWitness witness = f.witness(own.p); MagneticDomainTeamSlowFall.tickRecipient(own.p);
            MobEffectInstance old = own.p.getEffect(MobEffects.SLOW_FALLING);
            h.assertTrue(old != null && old.getDuration() == 2 && witness.sources.size() == 1
                    && witness.sources.getFirst() == f.caster.p, "exact live teammate pulse precedes the new self-domain context");
            h.assertTrue(own.toggle(1), "recipient starts a genuine separately paid own domain");
            double paid = own.cp(); long paidUntil = MagneticDomain.paidUntil(own.p);
            if (afterFirstTick) {
                MagneticDomain.tick(own.p);
                h.assertTrue(own.p.getEffect(MobEffects.SLOW_FALLING) == old && old.getDuration() == 2
                        && witness.sources.size() == 1, "first own-domain tick defers while the exact teammate pulse remains live");
            }
            h.assertTrue(MagneticDomainTeamSlowFall.consent(own.p, false), "recipient explicitly turns teammate consent off");
            h.assertFalse(MagneticDomainTeamSlowFall.consented(own.p), "team consent is gone before the old pulse expires");
            h.assertTrue(MagneticDomain.active(own.p) && own.cp() == paid && MagneticDomain.paidUntil(own.p) == paidUntil,
                    "team off preserves the independently paid own-domain context and ledger");

            Runnable crossLivePulseBoundary = () -> {
                h.assertTrue(own.p.getEffect(MobEffects.SLOW_FALLING) == old && old.getDuration() > 0,
                        "the own refresh boundary is exercised before the inherited native pulse expires");
                MagneticDomain.tick(own.p); MagneticDomainTeamSlowFall.tickRecipient(own.p);
                h.assertTrue(own.p.getEffect(MobEffects.SLOW_FALLING) == old && witness.sources.size() == 1,
                        "no new grant replaces or refreshes the old teammate pulse after off");
                Vec3 position = own.p.position(); expire(h, own.p); own.p.setPos(position); falling(own.p);
                h.runAfterDelay(1, () -> {
                    MagneticDomain.tick(own.p); MagneticDomainTeamSlowFall.tickRecipient(own.p);
                    MobEffectInstance self = own.p.getEffect(MobEffects.SLOW_FALLING);
                    h.assertTrue(MagneticDomain.active(own.p) && self != null && self != old
                            && witness.sources.size() > 1 && witness.sources.getLast() == own.p,
                            "after team off and natural pulse expiry the same own domain must recover self-attributed slow falling");
                    h.assertFalse(MagneticDomainTeamSlowFall.consented(own.p), "self recovery never restores revoked teammate consent");
                    h.assertTrue(MagneticDomain.paidUntil(own.p) == paidUntil,
                            "self recovery retains the original paid window while native player ticks may perform ordinary CP recovery");
                    h.succeed();
                });
            };
            // A second own-domain tick needs a genuine next server frame; its old pulse must
            // still be present so deleting the consent cannot silently evade this regression.
            if (afterFirstTick) h.runAfterDelay(1, crossLivePulseBoundary);
            else crossLivePulseBoundary.run();
        });
    }

    public static final class SourceWitness {
        final ServerPlayer recipient;
        final List<Entity> sources = new ArrayList<>();
        SourceWitness(ServerPlayer recipient) { this.recipient = recipient; }
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void added(MobEffectEvent.Added event) {
            if (event.getEntity() == recipient && event.getEffectInstance().is(MobEffects.SLOW_FALLING)) sources.add(event.getEffectSource());
        }
    }

    public static final class AddedAction {
        final ServerPlayer recipient;
        final Consumer<MobEffectEvent.Added> action;
        boolean called;
        AddedAction(ServerPlayer recipient, Consumer<MobEffectEvent.Added> action) { this.recipient = recipient; this.action = action; }
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void added(MobEffectEvent.Added event) {
            if (!called && event.getEntity() == recipient && event.getEffectInstance().is(MobEffects.SLOW_FALLING)) {
                called = true; action.accept(event);
            }
        }
    }

    public static final class ApplicableVeto {
        final ServerPlayer recipient;
        boolean enabled = true;
        int calls;
        ApplicableVeto(ServerPlayer recipient) { this.recipient = recipient; }
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void applicable(MobEffectEvent.Applicable event) {
            if (event.getEntity() == recipient && event.getEffectInstance().is(MobEffects.SLOW_FALLING)) {
                calls++;
                if (enabled) event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        }
    }

    private record Resources(CompoundTag persistent, AbilityProgress ability, CompoundTag abilityData,
                             float health, float absorption, int experienceLevel, int totalExperience) {
        static Resources capture(ServerPlayer p) {
            AbilityProgress ability = AbilityStorage.peek(p);
            return new Resources(p.getPersistentData().copy(), ability, ability == null ? null : AbilityStorage.encode(ability),
                    p.getHealth(), p.getAbsorptionAmount(), p.experienceLevel, p.totalExperience);
        }
        void same(GameTestHelper h, ServerPlayer p, String who) {
            h.assertTrue(p.getPersistentData().equals(persistent), who + " receives no resource ledger write or refill");
            h.assertTrue(AbilityStorage.peek(p) == ability && (ability == null || AbilityStorage.encode(ability).equals(abilityData)),
                    who + " receives no ability allocation, learning, CP, mastery or preset change");
            h.assertTrue(p.getHealth() == health && p.getAbsorptionAmount() == absorption
                    && p.experienceLevel == experienceLevel && p.totalExperience == totalExperience,
                    who + " receives no native health, absorption or XP grant");
        }
    }

    private static void falling(ServerPlayer p) {
        p.setNoGravity(false); p.setOnGround(false); p.setDeltaMovement(0, -1, 0);
    }
    private static void expire(GameTestHelper h, ServerPlayer p) {
        for (int tick = 0; tick < 3; tick++) p.doTick();
        h.assertFalse(p.hasEffect(MobEffects.SLOW_FALLING), "real native player ticks expire the bounded residue");
    }

    /** A real connected ordinary player: deliberately never enrolled or assigned a skill. */
    private static final class Actor {
        final ServerPlayer p;
        final Connection connection;
        final EmbeddedChannel channel;
        final CommonListenerCookie cookie;
        boolean closed;
        Actor(GameTestHelper h, Vec3 position) {
            var server = h.getLevel().getServer();
            cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "DTR" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            p.setGameMode(GameType.SURVIVAL); p.getAbilities().instabuild = false;
            p.getAbilities().invulnerable = false; p.getAbilities().flying = false; place(position);
        }
        void place(Vec3 position) {
            p.moveTo(position.x, position.y, position.z, 0, 0); p.setNoGravity(true);
            p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition();
        }
        void close() {
            if (closed) return; closed = true;
            MagneticDomainTeamSlowFall.remove(p); MagneticDomain.remove(p);
            AbilityStorage.remove(p); p.server.getPlayerList().remove(p); channel.finishAndReleaseAll();
        }
    }

    private static final class TeamFixture {
        final GameTestHelper h;
        final Fixture caster;
        final Actor recipient;
        final PlayerTeam team;
        final List<PlayerTeam> teams = new ArrayList<>();
        final List<Fixture> extraCasters = new ArrayList<>();
        final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
        boolean closed;
        TeamFixture(GameTestHelper h) {
            this.h = h; caster = new Fixture(h);
            recipient = new Actor(h, caster.p.position().add(0, 0, 3)); team = newTeam();
            h.getLevel().getScoreboard().addPlayerToTeam(caster.p.getScoreboardName(), team);
            h.getLevel().getScoreboard().addPlayerToTeam(recipient.p.getScoreboardName(), team);
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }
        PlayerTeam newTeam() {
            PlayerTeam value = h.getLevel().getScoreboard().addPlayerTeam("dts" + UUID.randomUUID().toString().substring(0, 10));
            teams.add(value); return value;
        }
        Fixture secondCaster() {
            Fixture other = new Fixture(h); extraCasters.add(other);
            h.getLevel().getScoreboard().addPlayerToTeam(other.p.getScoreboardName(), team); return other;
        }
        void ready(Runnable action) {
            caster.ready(() -> {
                recipient.place(caster.p.position().add(0, 0, 3));
                for (int n = 0; n < extraCasters.size(); n++) {
                    Fixture other = extraCasters.get(n); other.p.doTick(); other.place();
                    other.p.setPos(caster.p.position().add(2 + n * 2, 0, 0)); other.p.connection.resetPosition();
                }
                action.run();
            });
        }
        void loadBoundary(Vec3 at) {
            // Test environment only: expanded48m range must already be loaded; the ability never loads it.
            Vec3 from=caster.p.position();
            for(int x=((int)Math.floor(Math.min(from.x,at.x)))>>4;x<=((int)Math.floor(Math.max(from.x,at.x)))>>4;x++)
                for(int z=((int)Math.floor(Math.min(from.z,at.z)))>>4;z<=((int)Math.floor(Math.max(from.z,at.z)))>>4;z++)h.getLevel().getChunk(x,z);
            // The old12m fixture's air volume does not cover the expanded48m line.
            // Neighboring test structures/roof blocks must not turn a radius test into an occlusion test.
            Vec3 delta=at.subtract(from);int steps=(int)Math.ceil(delta.length());
            for(int i=0;i<=steps;i++) {
                BlockPos center=BlockPos.containing(from.add(delta.scale(steps==0?0:(double)i/steps)).add(0,1,0));
                for(BlockPos cell:BlockPos.betweenClosed(center.offset(-1,-1,-1),center.offset(1,1,1)))
                    block(cell.immutable(),Blocks.AIR.defaultBlockState());
            }
        }
        void start() { h.assertTrue(caster.toggle(1) && MagneticDomain.active(caster.p), "genuine authenticated paid domain starts"); }
        void optIn() { h.assertTrue(MagneticDomainTeamSlowFall.consent(recipient.p, true), "recipient explicitly opts in for their session and team"); }
        void falling() { MagneticDomainTeamSlowFallRuntimeTests.falling(recipient.p); }
        void tick() { MagneticDomainTeamSlowFall.tickRecipient(recipient.p); }
        boolean effect() { return recipient.p.hasEffect(MobEffects.SLOW_FALLING); }
        MobEffectInstance requireEffect() {
            MobEffectInstance value = recipient.p.getEffect(MobEffects.SLOW_FALLING);
            if(value==null)try {
                var consentField=MagneticDomainTeamSlowFall.class.getDeclaredField("CONSENTS");consentField.setAccessible(true);
                Object consent=((Map<?,?>)consentField.get(null)).get(recipient.p);
                var keyMethod=MagneticDomain.class.getDeclaredMethod("teamLeaseKey",ServerPlayer.class);keyMethod.setAccessible(true);
                Object key=keyMethod.invoke(null,caster.p);
                var sourceMethod=java.util.Arrays.stream(MagneticDomainTeamSlowFall.class.getDeclaredMethods()).filter(m->m.getName().equals("validSource")).findFirst().orElseThrow();sourceMethod.setAccessible(true);
                var eligible=MagneticDomainTeamSlowFall.class.getDeclaredMethod("eligible",ServerPlayer.class);eligible.setAccessible(true);
                Vec3 from=caster.p.position().add(0,1,0),to=recipient.p.position().add(0,1,0);
                var hit=h.getLevel().clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,caster.p));
                System.out.println("QA_DOMAIN_BOUNDARY caster="+caster.p.position()+" recipient="+recipient.p.position()+" radius="+MagneticDomain.RADIUS+" square="+from.distanceToSqr(to)+" eligible="+eligible.invoke(null,caster.p)+"/"+eligible.invoke(null,recipient.p)+" consent="+(consent!=null)+" sourceKey="+(key!=null)+" validSource="+(consent==null?false:sourceMethod.invoke(null,consent,caster.p,key))+" clip="+hit.getType()+" block="+hit.getBlockPos()+" candidates="+h.getLevel().getEntitiesOfClass(ServerPlayer.class,new net.minecraft.world.phys.AABB(recipient.p.position(),recipient.p.position()).inflate(MagneticDomain.RADIUS)).size());
            }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            h.assertTrue(value != null, "eligible consenting teammate receives the native effect"); return value;
        }
        void expire() {
            Vec3 position = recipient.p.position(); MagneticDomainTeamSlowFallRuntimeTests.expire(h, recipient.p); recipient.place(position);
        }
        void offAndExpire() { MagneticDomainTeamSlowFall.consent(recipient.p, false); expire(); }
        SourceWitness witness(ServerPlayer player) { SourceWitness witness = new SourceWitness(player); caster.hook(witness); return witness; }
        void noRecipientLedger() {
            h.assertFalse(recipient.p.getPersistentData().contains(HighPowerCombat.DATA_KEY)
                    || recipient.p.getPersistentData().contains(HighPowerResources.DATA_KEY)
                    || recipient.p.getPersistentData().contains(MagneticDomain.PAYMENT_KEY),
                    "ordinary recipient has no HP/shield, CP or domain payment enrollment");
            AbilityProgress state = AbilityStorage.peek(recipient.p);
            h.assertTrue(state == null || !state.learned(MagneticDomain.ID), "ordinary recipient never learns the domain");
        }
        void block(BlockPos position, BlockState state) {
            blocks.putIfAbsent(position, h.getLevel().getBlockState(position)); h.getLevel().setBlock(position, state, 3);
        }
        void close() {
            if (closed) return; closed = true;
            recipient.close(); MagneticDomainTeamSlowFall.remove(caster.p);
            for (Fixture other : extraCasters) { MagneticDomainTeamSlowFall.remove(other.p); other.close(); }
            caster.close();
            blocks.forEach((position, state) -> h.getLevel().setBlock(position, state, 3));
            teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);
        }
    }
}
