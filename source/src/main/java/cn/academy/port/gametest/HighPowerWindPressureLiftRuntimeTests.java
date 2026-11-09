package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.WindPressureLiftRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW443 connected native server contracts. Positive admission uses the private, declared
 * unpublished-integrated test seam. Native packet acceptance, gravity and fall damage are real;
 * the explicit motion fixture is not evidence of LocalPlayer input, physics or rendering. */
@GameTestHolder("academy_wind_pressure_lift")
@EventBusSubscriber(modid="academy")
@PrefixGameTestTemplate(false)
public final class HighPowerWindPressureLiftRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_wind_pressure_lift";
    private static final String ID = "wind_pressure_lift", PAYMENT = "academy:wind_pressure_lift_payment";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_wind_lift_fixture", UUID::compareTo);
    private HighPowerWindPressureLiftRuntimeTests() {}

    @SubscribeEvent(priority=EventPriority.LOWEST) public static void installFocusedTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var original = level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower", TEMPLATE));
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_wind_pressure_lift", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), original.save(new CompoundTag()));
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_public_dedicated_refusal_is_before_any_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); Ledger before = f.ledger();
        h.assertFalse(f.p.server.isSingleplayer(), "this native suite is genuinely dedicated");
        h.assertFalse(WindPressureLift.toggle(f.p, f.wire(1)), "dedicated public input cannot obtain the private integrated bridge");
        h.assertTrue(before.equals(f.ledger()), "unsupported deployment changes no ledger or XP");
        h.assertFalse(WindPressureLift.active(f.p) || f.p.getPersistentData().contains(PAYMENT), "unsupported deployment creates neither context nor paid receipt");
        f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_paid_ground_start_and_one_use_proof_preserve_native_body_and_shared_pools(GameTestHelper h) {
        Fixture f = new Fixture(h); Vec3 velocity = new Vec3(.12, -.0784, -.09); f.p.setDeltaMovement(velocity); f.p.fallDistance = 7;
        Ledger before = f.ledger(); Vec3 position = f.p.position();
        h.assertTrue(f.toggle(1), "real supported ground starts the declared integrated contract");
        WindPressureLift.Grant g = f.publish();
        h.assertTrue(g != null && g.owner().equals(f.p.getUUID()) && g.entity() == f.p.getId(), "immutable paid grant names only the actual connected owner");
        h.assertTrue(WindPressureLift.consumeIntegrated(g, f.p.server), "matching paid proof consumes once");
        h.assertFalse(WindPressureLift.consumeIntegrated(g, f.p.server), "same immutable proof cannot replay");
        near(h, f.cp(), before.cp() - .5, "LV4 activation uses its independent .5 CP anchor");
        h.assertTrue(f.p.position().equals(position) && f.p.getDeltaMovement().equals(velocity), "server publication and consumption never teleport or overwrite native velocity");
        near(h, f.p.fallDistance, 7, "paid lift never resets accumulated native fall distance");
        h.assertTrue(before.shared().equals(f.ledger().shared()), "publication and proof never change exact HP, base S, reserve, growth, XP or overload");
        h.assertTrue(!f.p.isNoGravity() && !f.p.getAbilities().flying && !f.p.getAbilities().mayfly && f.p.getActiveEffects().isEmpty(), "no flight flags, gravity override or potion support");
        h.assertFalse(f.toggle(1), "same authenticated input is retired"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_immutable_proof_rejects_every_changed_identity_and_motion_field(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var g = f.publish(); double paid = f.cp();
        for (int field = 0; field < 16; field++) {
            try {
                var forged = new WindPressureLift.Grant(field == 0 ? g.entity() + 1 : g.entity(), field == 1 ? UUID.randomUUID() : g.owner(),
                        field == 2 ? g.input() + 1 : g.input(), field == 3 ? g.token() + 1 : g.token(), field == 4 ? g.epoch() + 1 : g.epoch(),
                        field == 5 ? Level.NETHER.location() : g.dimension(), field == 6 ? g.sequence() + 1 : g.sequence(),
                        field == 7 ? g.issuedAt() - 1 : g.issuedAt(), field == 8 ? g.expiresAt() + 1 : g.expiresAt(),
                        field == 9 ? g.anchor().add(.01, 0, 0) : g.anchor(), field == 10 ? g.accepted().add(0, .01, 0) : g.accepted(),
                        field == 14 ? Pose.CROUCHING : g.pose(), field == 11 ? WindPressureLiftRules.Mode.SIDE : g.mode(),
                        field == 12 ? new Vec3(1, 0, 0) : g.direction(), field == 13 || g.terminal());
                if (field == 15) h.assertFalse(WindPressureLift.consumeIntegrated(g, null), "null or foreign server has no proof authority");
                else h.assertFalse(WindPressureLift.consumeIntegrated(forged, f.p.server), "changed proof field " + field + " cannot grant motion");
            } catch (IllegalArgumentException malformedWire) {
                h.assertTrue(field == 7 || field == 8, "only structurally malformed time tuples are rejected by the immutable record constructor");
            }
            h.assertTrue(WindPressureLift.integratedFresh(g, f.p.server), "tamper rejection neither consumes nor replaces the genuine proof");
        }
        h.assertTrue(WindPressureLift.consumeIntegrated(g, f.p.server), "original proof still consumes exactly once after forged copies");
        near(h, f.cp(), paid, "acknowledgements and forged receipts never debit, refund or reward"); near(h, AbilityStorage.get(f.p).exp(ID), 0, "no acknowledgement XP"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_ground_pose_effect_and_native_motion_conflicts_are_free(GameTestHelper h) {
        Fixture f = new Fixture(h); Ledger before = f.ledger(); long input = 0;
        f.p.setOnGround(false); h.assertFalse(f.toggle(++input), "airborne begin denied"); f.place();
        f.p.getAbilities().flying = true; h.assertFalse(f.toggle(++input), "native flight denied"); f.p.getAbilities().flying = false;
        f.p.getAbilities().mayfly = true; h.assertFalse(f.toggle(++input), "flight capability denied"); f.p.getAbilities().mayfly = false;
        f.p.setNoGravity(true); h.assertFalse(f.toggle(++input), "foreign gravity denied"); f.p.setNoGravity(false);
        f.p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100)); h.assertFalse(f.toggle(++input), "levitation denied"); f.p.removeEffect(MobEffects.LEVITATION);
        f.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100)); h.assertFalse(f.toggle(++input), "slow falling denied"); f.p.removeEffect(MobEffects.SLOW_FALLING);
        f.p.containerMenu = ChestMenu.threeRows(73, f.p.getInventory()); h.assertFalse(f.toggle(++input), "open native container denied"); f.p.containerMenu = f.p.inventoryMenu;
        f.p.setPose(Pose.SWIMMING); h.assertFalse(f.toggle(++input), "unsupported native body pose denied"); f.p.setPose(Pose.STANDING);
        f.block(7, 2, 7, Blocks.WATER.defaultBlockState()); h.assertFalse(f.toggle(++input), "actual fluid body denied before stale native fluid flags");
        h.assertTrue(before.equals(f.ledger()), "all ineligible admission remains before CP, cooldown and shared-pool writes"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_ceiling_full_body_and_missing_real_support_fail_before_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); Ledger before = f.ledger();
        f.block(7, 3, 7, Blocks.STONE.defaultBlockState()); h.assertFalse(f.toggle(1), "head AABB colliding with native low ceiling denied");
        f.block(7, 3, 7, Blocks.AIR.defaultBlockState()); f.block(7, 1, 7, Blocks.AIR.defaultBlockState());
        h.assertFalse(f.toggle(2), "forged onGround without real collision support denied");
        h.assertTrue(before.equals(f.ledger()), "native geometry refusal is entirely free"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_crouch_selects_frozen_side_direction_and_release_does_not_toggle_off(GameTestHelper h) {
        Fixture f = new Fixture(h); f.p.setShiftKeyDown(true); f.p.setPose(Pose.CROUCHING); f.p.setYRot(-90); f.p.setXRot(-35);
        h.assertTrue(f.toggle(1), "initial supported crouch chooses side assistance"); var first = f.publish();
        h.assertTrue(first.mode() == WindPressureLiftRules.Mode.SIDE && first.direction().x > .999 && Math.abs(first.direction().y) < 1e-9, "frozen first look is horizontal and normalized");
        f.p.setShiftKeyDown(false); f.p.setPose(Pose.STANDING); f.p.setYRot(90); f.p.setXRot(40);
        h.assertTrue(WindPressureLift.release(f.p, f.wire(1)) && WindPressureLift.active(f.p), "release only ends key ownership, retaining toggle session");
        f.afterPosts(1, () -> { var next = f.grant(); h.assertTrue(next != null && next.mode() == first.mode() && next.direction().equals(first.direction()), "later look and crouch cannot retarget side assistance"); f.succeed(); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_duplicate_callbacks_publish_at_most_one_grant_and_late_proofs_do_not_stack(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var first = f.publish(); double paid = f.cp();
        for (int i = 0; i < 20; i++) { WindPressureLift.tick(f.p); f.publish(); }
        h.assertTrue(f.grant().equals(first), "duplicate callbacks in one actual server tick cannot mint new sequence");
        near(h, f.cp(), paid, "same-tick callbacks cannot repay");
        f.afterPosts(1, () -> { var second = f.grant(); h.assertTrue(second != null && second.sequence() == first.sequence() + 1, "one actual server tick permits one replacement sequence");
            h.assertFalse(WindPressureLift.consumeIntegrated(first, f.p.server), "prior unconsumed proof expires when replaced");
            h.assertTrue(WindPressureLift.consumeIntegrated(second, f.p.server), "only latest single proof is usable");
            h.assertFalse(WindPressureLift.consumeIntegrated(second, f.p.server), "latest proof is single use too"); f.succeed(); });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_real_preset_request_and_container_lifecycle_revoke_same_tick_proof(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var first = f.publish();
        h.assertTrue(AcademyGameplay.requestFromClient(f.p, new AcademyNetwork.Request("preset_switch", "1")), "actual authenticated preset request succeeds");
        h.assertFalse(WindPressureLift.consumeIntegrated(first, f.p.server) || WindPressureLift.active(f.p), "real common lifecycle closes and revokes before any later tick");
        Fixture other = new Fixture(h, 22); h.assertTrue(other.toggle(1), "second paid native setup"); var second = other.publish();
        other.p.containerMenu = ChestMenu.threeRows(79, other.p.getInventory()); WindPressureLift.tick(other.p);
        h.assertFalse(WindPressureLift.consumeIntegrated(second, other.p.server) || WindPressureLift.active(other.p), "native menu invalidation revokes pending proof"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_abort_cancel_before_begin_and_reentrant_send_never_resurrect_paid_controller(GameTestHelper h) {
        Fixture f = new Fixture(h);
        h.assertFalse(WindPressureLift.abort(f.p, f.wire(1)), "cancel-before-start records a tombstone without a live session");
        h.assertFalse(f.toggle(1), "canceled input cannot begin late");
        boolean[] sawGrant = {false}; final WindPressureLift.Grant[] intercepted = {null};
        f.channel.pipeline().addFirst("wind_lift_reentry", new ChannelOutboundHandlerAdapter() {
            @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) throws Exception {
                if (!sawGrant[0] && message instanceof ClientboundCustomPayloadPacket packet && packet.payload() instanceof WindPressureLift.Grant g && !g.terminal()) {
                    sawGrant[0] = true; intercepted[0] = g;
                    h.assertFalse(f.toggle(2), "publication callback cannot replay the originating input");
                    WindPressureLift.abort(f.p);
                }
                super.write(ctx, message, promise);
            }
        });
        double cp = f.cp(); f.toggle(2); f.publish(); f.drain();
        h.assertTrue(sawGrant[0], "real native Connection outbound callback observed the paid grant");
        h.assertFalse(WindPressureLift.active(f.p) || WindPressureLift.consumeIntegrated(intercepted[0], f.p.server), "callback abort cannot be undone by returning publication stack");
        near(h, f.cp(), cp - .5, "canceled paid grant remains spent once with no refund");
        h.assertFalse(f.toggle(3), "callback cleanup cannot erase persistent restart fence"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_world_clock_changes_and_fake_posts_cannot_age_physical_debt(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var g = f.publish(); CompoundTag debt = f.payment(); double cp = f.cp();
        var clock = (ServerLevelData) h.getLevel().getLevelData(); long before = h.getLevel().getGameTime();
        try {
            for (long moved : new long[]{before + 1_000_000, 0}) {
                clock.setGameTime(moved);
                for (int i = 0; i < 5; i++) { WindPressureLift.tick(f.p); WindPressureLift.playerTick(new PlayerTickEvent.Post(f.p)); f.publish(); }
                h.assertTrue(f.payment().equals(debt), "world clock jump and duplicate player callbacks cannot advance paid window, restart or cooldown");
                near(h, f.cp(), cp, "clock manipulation cannot cause repeat debit or refill");
            }
        } finally { clock.setGameTime(before); }
        h.assertTrue(WindPressureLift.integratedFresh(g, f.p.server), "world time does not invalidate same-physical-tick immutable proof"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_actual_player_nbt_reload_keeps_debt_and_cannot_carry_free_window(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var old = f.publish();
        AbilityStorage.save(f.p); CompoundTag saved = f.p.saveWithoutId(new CompoundTag()); CompoundTag debt = f.payment(); double cp = f.cp();
        WindPressureLift.remove(f.p); AbilityStorage.remove(f.p); LegacySingleKeyOwnerEpoch.forget(f.p); f.p.load(saved); f.place(); f.epoch();
        h.assertTrue(f.payment().equals(debt), "actual save/load retains physical remaining debt"); near(h, f.cp(), cp, "native save/load cannot refill spent CP");
        h.assertFalse(WindPressureLift.consumeIntegrated(old, f.p.server), "reloaded ownership cannot reuse old proof");
        h.assertFalse(f.toggle(2), "new controller cannot inherit the old paid window or erase cooldown");
        h.assertTrue(f.payment().equals(debt), "refused new controller preserves debt exactly"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_same_uuid_new_connection_cannot_reuse_old_paid_grant(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var old = f.publish();
        WindPressureLift.abort(f.p); AbilityStorage.save(f.p); CompoundTag debt = f.payment(); double cp = f.cp(); UUID owner = f.p.getUUID(); f.close();
        Fixture joined = new Fixture(h, 22, owner, true);
        h.assertTrue(joined.p.getUUID().equals(old.owner()) && joined.p.getId() != old.entity(), "real replacement connection keeps UUID and changes native entity identity");
        h.assertFalse(WindPressureLift.consumeIntegrated(old, joined.p.server), "same UUID alone cannot revive old session proof");
        h.assertTrue(joined.payment().equals(debt), "rejoin retains paid remaining debt"); near(h, joined.cp(), cp, "rejoin keeps depleted shared ledger");
        h.assertFalse(joined.toggle(2), "new connection cannot continue a prepaid old controller for free"); joined.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_malformed_or_replaced_payment_storage_fails_closed_without_normalization(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var g = f.publish(); WindPressureLift.abort(f.p);
        CompoundTag good = f.payment(); double cp = f.cp(); long input = 2;
        List<Consumer<CompoundTag>> corruptions = List.of(t -> t.putString("remaining", "20"), t -> t.putInt("remaining", 21), t -> t.putInt("remaining", -1),
                t -> t.putInt("restart", 9), t -> t.putInt("cooldown", -1), t -> t.putLong("receipt", -1), t -> t.putInt("version", 999), t -> t.putString("foreign", "extra"));
        for (var corrupt : corruptions) { CompoundTag changed = good.copy(); corrupt.accept(changed); f.p.getPersistentData().put(PAYMENT, changed);
            h.assertFalse(f.toggle(input++), "malformed native payment storage cannot authorize a new controller");
            h.assertTrue(f.payment().equals(changed), "refusal never normalizes corrupt debt into permission"); near(h, f.cp(), cp, "malformed storage cannot debit or refill CP"); }
        h.assertFalse(WindPressureLift.consumeIntegrated(g, f.p.server), "tampered storage never restores aborted proof"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void wind_lift_grounded_lift_then_real_packet_landing_keeps_native_fall_math_and_serious_damage(GameTestHelper h) {
        Fixture f = new Fixture(h), control = new Fixture(h, 22);
        int began = f.p.server.getTickCount();
        h.runAfterDelay(61, () -> {
            h.assertTrue(f.p.server.getTickCount() - began == 61 && spawnProtection(f.p) <= 0 && spawnProtection(control.p) <= 0,
                    "real resident server ticks naturally expire both login damage protections");
            f.place(); control.place(); h.assertTrue(f.toggle(1), "native grounded activation before measured lift"); var grant = f.publish();
            h.assertTrue(WindPressureLift.consumeIntegrated(grant, f.p.server), "paid proof authorizes only one declared local-motion fixture");
            Vec3 nativeBefore = f.p.getDeltaMovement();
            // This is a declared client-equivalent input fixture, not an actual LocalPlayer test.
            // The .1 addition is an independent approved bound, not a value read from implementation.
            f.p.setDeltaMovement(nativeBefore.x, Math.min(.16, nativeBefore.y + .1), nativeBefore.z);
            f.p.aiStep();
            h.assertTrue(f.p.getY() > f.start.y && !f.p.onGround(), "real native aiStep lifts the supported actor off the floor with ordinary gravity");
            h.assertTrue(f.p.getDeltaMovement().y < .1 && !f.p.isNoGravity() && !f.p.getAbilities().flying, "native gravity still acts immediately on the assisted impulse");
            WindPressureLift.abort(f.p);
            // Identical accumulated eight-metre falls isolate native landing settlement from
            // the declared lift fixture; this deliberately does not claim an eight-metre flight.
            BigDecimal before = hp(f.p), ordinaryBefore = hp(control.p);
            f.land(8); control.land(8);
            BigDecimal loss = before.subtract(hp(f.p)), ordinaryLoss = ordinaryBefore.subtract(hp(control.p));
            h.assertTrue(loss.compareTo(new BigDecimal("5")) == 0 && loss.compareTo(ordinaryLoss) == 0,
                    "actual accepted supported packet landing keeps vanilla ceil(distance-3)=5 damage, equal to paired control");
            h.assertTrue(f.p.onGround() && control.p.onGround() && f.p.fallDistance == 0 && control.p.fallDistance == 0,
                    "only the actual native landing resets the measured fall distance");
            h.assertTrue(!WindPressureLift.active(f.p) && f.p.getActiveEffects().isEmpty(), "stopped lift has no fall immunity or lingering potion rescue"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_connection_transient_is_not_authority_but_real_accepted_packet_is(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var first = f.publish();
        boolean[] transientTravel = {false};
        f.hook(new Object() { @SubscribeEvent(priority=EventPriority.LOWEST) public void player(PlayerTickEvent.Post e) {
            if (e.getEntity() == f.p && f.p.position().distanceToSqr(f.start) > .01) transientTravel[0] = true;
        } });
        f.p.setDeltaMovement(.4, -.0784, 0);
        f.afterPosts(1, () -> {
            h.assertTrue(transientTravel[0], "actual native connection doTick temporarily traveled horizontally");
            h.assertTrue(f.p.position().equals(f.start), "actual connection tick restored the authoritative accepted coordinates");
            var restored = f.grant(); h.assertTrue(restored != null && restored.accepted().equals(f.start), "published permission uses restored accepted coordinates, never temporary server travel");
            f.p.setDeltaMovement(0, -.0784, 0); f.moveAccepted(f.start.add(.25, 0, 0), true);
        });
        f.afterPosts(2, () -> {
            var moved = f.grant(); h.assertTrue(moved != null && moved.accepted().equals(f.start.add(.25, 0, 0)), "genuinely accepted native movement reaches the next immutable proof");
            h.assertTrue(moved.anchor().equals(first.anchor()), "accepted movement never relocates the fixed field");
            h.assertFalse(WindPressureLift.consumeIntegrated(first, f.p.server), "the first permission cannot be banked across genuine server ticks"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_large_real_accepted_displacement_closes_without_teleporting_back(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var old = f.publish();
        Vec3 wanted = f.start.add(.901, 0, 0); f.moveAccepted(wanted, true);
        f.afterPosts(1, () -> {
            h.assertFalse(WindPressureLift.active(f.p) || WindPressureLift.consumeIntegrated(old, f.p.server), "a real accepted move above .9 revokes assistance");
            h.assertTrue(f.p.position().equals(wanted), "fail closed cleanup preserves the server's accepted position instead of snapping back");
            h.assertTrue(f.p.getActiveEffects().isEmpty() && !f.p.isNoGravity(), "range refusal adds no fallback effect or gravity bypass"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_native_side_wall_packet_cannot_move_body_through_collision(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var old = f.publish();
        f.block(8, 2, 7, Blocks.STONE.defaultBlockState()); f.block(8, 3, 7, Blocks.STONE.defaultBlockState());
        Vec3 wanted = f.start.add(.8, 0, 0);
        f.p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(wanted.x, wanted.y, wanted.z, true));
        h.assertTrue(f.p.getX() < wanted.x && h.getLevel().noCollision(f.p, f.p.getBoundingBox()), "real server movement handling rejects a target body that intersects the stone side wall");
        h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(8, 2, 7))).is(Blocks.STONE), "lift never breaks a wall to satisfy input");
        WindPressureLift.abort(f.p); h.assertFalse(WindPressureLift.consumeIntegrated(old, f.p.server), "wall encounter can always cancel the exact pending grant"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_new_ceiling_over_head_invalidates_complete_body_before_next_grant(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var old = f.publish();
        f.block(7, 3, 7, Blocks.STONE.defaultBlockState()); Vec3 accepted = f.p.position();
        f.afterPosts(1, () -> {
            h.assertFalse(WindPressureLift.active(f.p) || WindPressureLift.consumeIntegrated(old, f.p.server), "new actual ceiling intersects upper body and revokes paid proof");
            h.assertTrue(f.p.position().equals(accepted), "ceiling invalidation never teleports through or below terrain"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_radius_order",timeoutTicks=15)
    public static void wind_lift_full_aabb_crossing_fixed_radius_stops_while_center_is_still_inside(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var first = f.publish();
        for (int step = 1; step <= 5; step++) {
            final double distance = step * .5;
            h.runAfterDelay(step, () -> f.moveAccepted(f.start.add(distance, 0, 0), true));
            f.afterPosts(step, () -> h.assertTrue(WindPressureLift.active(f.p), "entire native body stays within radius after accepted " + distance + " metres"));
        }
        h.runAfterDelay(6, () -> f.moveAccepted(f.start.add(2.8, 0, 0), true));
        f.afterPosts(6, () -> {
            h.assertTrue(f.p.position().distanceTo(f.start) < 3, "accepted feet center remains inside nominal three-metre radius");
            h.assertFalse(WindPressureLift.active(f.p) || WindPressureLift.consumeIntegrated(first, f.p.server), "full standing AABB over the fixed radius refuses assistance even with center inside"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_unloaded_full_body_probe_never_loads_remote_chunks(GameTestHelper h) {
        Fixture f = new Fixture(h); Ledger before = f.ledger(); BlockPos far = new BlockPos(20_000_007, f.p.blockPosition().getY(), 20_000_007);
        h.assertFalse(h.getLevel().hasChunkAt(far), "remote native column is genuinely unloaded before probe");
        AABB body = f.p.getDimensions(Pose.STANDING).makeBoundingBox(new Vec3(far.getX() + .5, far.getY(), far.getZ() + .5));
        h.assertFalse((boolean) invoke("safeVolume", new Class<?>[]{ServerPlayer.class, AABB.class}, f.p, body), "real production full-body probe refuses unloaded area");
        h.assertFalse(h.getLevel().hasChunkAt(far), "production probe has no implicit chunk loading");
        h.assertTrue(before.equals(f.ledger()) && f.p.position().equals(f.start), "unloaded probe neither moves actor nor changes ledgers"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=40)
    public static void wind_lift_twenty_native_ticks_renew_exactly_once_and_duplicate_calls_cannot_catch_up(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); f.publish(); long receipt = f.payment().getLong("receipt");
        h.assertTrue(f.payment().getInt("remaining") == 20, "activation starts twenty complete native player ticks");
        f.afterPosts(19, () -> h.assertTrue(f.payment().getLong("receipt") == receipt && f.payment().getInt("remaining") == 1, "nineteen completed native player ticks cannot renew early"));
        f.afterPosts(20, () -> {
            h.assertTrue(f.payment().getLong("receipt") == receipt + 1 && f.payment().getInt("remaining") == 20, "twentieth actual player tick buys exactly one fresh full window");
            double cp = f.cp(); CompoundTag debt = f.payment();
            for (int i = 0; i < 50; i++) { WindPressureLift.tick(f.p); WindPressureLift.playerTick(new PlayerTickEvent.Post(f.p)); f.publish(); }
            near(h, f.cp(), cp, "duplicate native callbacks cannot catch up or repeatedly charge");
            h.assertTrue(f.payment().equals(debt), "duplicate callbacks never shorten a paid renewal window"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_exhaustion_order",timeoutTicks=35)
    public static void wind_lift_resource_exhaustion_stops_at_next_window_without_refund_or_free_grant(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var old = f.publish();
        f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(ServerTickEvent.Post e) {
            if (e.getServer() == f.p.server) f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 0);
        } });
        f.afterPosts(19, () -> h.assertTrue(WindPressureLift.active(f.p), "the existing paid window remains valid without borrowing another resource"));
        f.afterPosts(20, () -> {
            h.assertFalse(WindPressureLift.active(f.p) || WindPressureLift.consumeIntegrated(old, f.p.server), "unpayable renewal closes before any fresh grant");
            near(h, f.cp(), 0, "exhaustion cannot refill CP"); near(h, AbilityStorage.get(f.p).exp(ID), 0, "paid waiting and expiry grant no mastery"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_lifetime_order",timeoutTicks=265)
    public static void wind_lift_fixed_lifetime_expires_without_runaway_or_following_owner(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var first = f.publish(); int began = f.p.server.getTickCount();
        f.afterPosts(WindPressureLiftRules.LIFETIME - 1, () -> h.assertTrue(WindPressureLift.active(f.p) && f.grant().anchor().equals(first.anchor()), "fixed field remains the same bounded source through its final eligible tick"));
        f.afterPosts(WindPressureLiftRules.LIFETIME, () -> {
            h.assertTrue(f.p.server.getTickCount() - began == WindPressureLiftRules.LIFETIME, "exactly the full lifetime elapsed in genuine server ticks");
            h.assertFalse(WindPressureLift.active(f.p) || WindPressureLift.consumeIntegrated(first, f.p.server), "the finite field and all old permissions expire at the hard boundary");
            h.assertTrue(f.p.position().equals(f.start) && !f.p.isNoGravity() && f.p.getActiveEffects().isEmpty(), "unused permissions cannot create runaway movement or residual flight");
            near(h, AbilityStorage.get(f.p).exp(ID), 0, "repeated publications never reward acknowledgement or waiting"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void wind_lift_toggle_off_is_free_but_restart_cooldown_cannot_be_erased_or_reuse_prepaid_window(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var old = f.publish(); double paid = f.cp();
        h.assertTrue(f.toggle(2) && !WindPressureLift.active(f.p), "fresh Q toggles off even during this session's own one-tick UI guard");
        near(h, f.cp(), paid, "manual off is free and never refunds activation");
        h.assertFalse(WindPressureLift.consumeIntegrated(old, f.p.server), "toggle off immediately revokes old immutable proof");
        h.assertTrue(f.payment().getInt("restart") == 8 && f.payment().getInt("cooldown") == 10, "native LV4 stop records independent eight-tick restart and ten-tick q cooldown");
        AbilityStorage.get(f.p).cooldowns.remove(ID); AbilityStorage.get(f.p).cooldownMaxTicks.remove(ID);
        h.assertFalse(f.toggle(3), "deleting UI cooldown cannot erase paid physical restart debt");
        f.afterPosts(8, () -> {
            h.assertTrue(f.payment().getInt("restart") == 0 && f.payment().getInt("cooldown") == 2, "real restart expiry does not erase remaining independent q cooldown");
            h.assertFalse(f.toggle(4), "restart alone is insufficient while cooldown remains");
        });
        f.afterPosts(10, () -> {
            h.assertTrue(f.payment().getInt("cooldown") == 0 && f.payment().getInt("remaining") > 0, "old session still has unused prepaid time after both restart debts expire");
            double before = f.cp(); h.assertTrue(f.toggle(5), "new session after true cooldown is admissible"); near(h, f.cp(), before - .5, "new controller pays full activation instead of inheriting old free window"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void wind_lift_real_native_death_revokes_proof_and_never_resurrects_owner(GameTestHelper h) {
        Fixture f = new Fixture(h); h.runAfterDelay(61, () -> {
            f.place(); h.assertTrue(spawnProtection(f.p) <= 0 && f.toggle(1), "native damage protection expired before paid setup"); var old = f.publish();
            f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
            h.assertTrue(f.p.hurt(f.p.damageSources().fellOutOfWorld(), 50), "real lethal native source reaches the ordinary exact health path");
            h.assertFalse(f.p.isAlive() || WindPressureLift.active(f.p) || WindPressureLift.consumeIntegrated(old, f.p.server), "ordinary death revokes grant and exact controller without rescue");
            WindPressureLift.tick(f.p); f.publish(); h.assertTrue(hp(f.p).signum() == 0, "later lifecycle callbacks cannot restore dead HP"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_actual_dimension_change_revokes_old_world_proof_and_retains_debt(GameTestHelper h) {
        Fixture f = new Fixture(h); h.assertTrue(f.toggle(1), "paid native setup"); var old = f.publish(); double cp = f.cp();
        var other = f.p.server.getLevel(Level.NETHER); h.assertTrue(other != null, "native Nether destination exists");
        f.p.teleportTo(other, f.start.x, f.start.y, f.start.z, Set.of(), 0, 0);
        h.assertTrue(f.p.serverLevel() == other, "actual native dimension transition completed");
        h.assertFalse(WindPressureLift.active(f.p) || WindPressureLift.consumeIntegrated(old, f.p.server), "changed world revokes old exact grant synchronously");
        h.assertTrue(f.payment().getInt("remaining") == 20 && f.payment().getInt("restart") == 8 && f.payment().getInt("cooldown") == 10, "world transition retains prepaid remainder and incurs real stop debt");
        near(h, f.cp(), cp, "native dimension transition neither refunds nor refills CP"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void wind_lift_successful_native_debit_callback_cancel_records_debt_then_new_start_pays_again(GameTestHelper h) {
        Fixture f = new Fixture(h); double cp = f.cp();
        // Declared adversarial observation at the actual NBT debit. The ordinary
        // shared writer, exact compound identity and returned success remain real.
        DebitCallbackBudget observed = new DebitCallbackBudget(f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY), () -> WindPressureLift.abort(f.p));
        f.p.getPersistentData().put(HighPowerResources.DATA_KEY, observed); observed.armed = true;
        h.assertFalse(f.toggle(1), "cancel during actual successful CP debit cannot activate movement");
        h.assertTrue(observed.fired && !WindPressureLift.active(f.p) && f.publish() == null, "native debit callback ran and no canceled grant survives");
        near(h, f.cp(), cp - .5, "already successful debit remains spent exactly once");
        h.assertTrue(f.payment().getInt("version") == 1 && f.payment().getInt("remaining") == 20
                && f.payment().getInt("restart") == 8 && f.payment().getInt("cooldown") == 10 && f.payment().getLong("receipt") > 0,
                "known successful native write gets valid full payment and stop debt despite callback cancellation");
        f.afterPosts(9, () -> {
            h.assertTrue(f.payment().getInt("remaining") == 11 && f.payment().getInt("restart") == 0 && f.payment().getInt("cooldown") == 1,
                    "known canceled payment debt ages by real native owner ticks without becoming permanently corrupt");
            h.assertFalse(f.toggle(2), "one remaining actual cooldown tick still refuses a new controller");
        });
        f.afterPosts(10, () -> {
            double before = f.cp(); h.assertTrue(f.toggle(3), "fresh activation succeeds after the real canceled-payment stop debt expires");
            near(h, f.cp(), before - .5, "new controller pays full activation instead of carrying old prepaid ticks");
            h.assertTrue(f.payment().getInt("remaining") == 20, "new successful payment receives exactly one fresh window"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_unknown_budget_replacement_after_native_write_remains_closed_and_is_never_repaired(GameTestHelper h) {
        Fixture f = new Fixture(h); double cp = f.cp();
        ReplacingRoot root = new ReplacingRoot(f.p.getPersistentData()); setField(f.p, "persistentData", root); root.armed = true;
        h.assertFalse(f.toggle(1), "unknown ledger replacement after successful native shared write cannot obtain movement");
        h.assertTrue(root.fired && !WindPressureLift.active(f.p) && f.publish() == null, "real root publication callback replaces the budget and no grant is issued");
        CompoundTag replacement = f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); CompoundTag before = replacement.copy();
        h.assertTrue(replacement.getString("foreign_owner").equals("replacement"), "foreign replacement survives without snapshot rollback");
        near(h, f.cp(), cp - .5, "unknown replacement still retains the witnessed successful debit with no refund");
        h.assertFalse(f.toggle(2), "uncertified writer scope cannot be normalized into replay permission");
        h.assertTrue(f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY) == replacement && replacement.equals(before),
                "later refusal preserves unknown budget identity and data exactly"); f.succeed();
    }

    private static final class DebitCallbackBudget extends CompoundTag {
        final Runnable callback; boolean armed, fired;
        DebitCallbackBudget(CompoundTag source, Runnable callback) { this.callback = callback; for (String key : source.getAllKeys()) super.put(key, source.get(key).copy()); }
        @Override public void putDouble(String key, double value) { super.putDouble(key, value); if (armed && !fired && key.equals("cp")) { fired = true; armed = false; callback.run(); } }
    }
    private static final class ReplacingRoot extends CompoundTag {
        boolean armed, fired;
        ReplacingRoot(CompoundTag source) { for (String key : source.getAllKeys()) super.put(key, source.get(key)); }
        @Override public Tag put(String key, Tag value) {
            Tag previous = super.put(key, value);
            if (armed && !fired && key.equals(HighPowerResources.DATA_KEY) && value instanceof CompoundTag budget) {
                armed = false; fired = true; CompoundTag replacement = budget.copy(); replacement.putString("foreign_owner", "replacement"); super.put(key, replacement);
            }
            return previous;
        }
    }
    private static void setField(Object owner, String name, Object value) {
        try { for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(owner, value); return; }
            catch (NoSuchFieldException absent) { /* Continue through the actual native hierarchy. */ }
        } throw new NoSuchFieldException(name); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void wind_lift_actual_native_gravity_trajectory_and_accepted_landing_accumulate_fall_without_reset(GameTestHelper h) {
        Fixture f = new Fixture(h), control = new Fixture(h, 22);
        for (Fixture actor : List.of(f, control)) {
            actor.block(actor.x, 5, 7, Blocks.STONE.defaultBlockState());
            for (int dx = 0; dx <= 7; dx++) for (int dz = -2; dz <= 2; dz++) actor.block(actor.x + dx, 1, 7 + dz, Blocks.STONE.defaultBlockState());
        }
        h.runAfterDelay(61, () -> {
            for (Fixture actor : List.of(f, control)) {
                // Isolate landing damage from saturated FoodData healing, now visible at the smaller pool scale.
                actor.p.getFoodData().setFoodLevel(17); actor.p.getFoodData().setSaturation(0);
                Vec3 platform = actor.start.add(0, 4, 0); actor.p.moveTo(platform.x, platform.y, platform.z, 0, 0);
                actor.p.setOnGround(true); actor.p.setDeltaMovement(0, -.0784, 0); actor.p.move(MoverType.SELF, new Vec3(0, -.0784, 0)); actor.p.connection.resetPosition();
                h.assertTrue(actor.p.onGround() && actor.p.fallDistance == 0 && spawnProtection(actor.p) <= 0, "measured trajectory begins on real elevated support with naturally zero fall distance and no spawn protection");
            }
            BigDecimal before = hp(f.p), ordinaryBefore = hp(control.p);
            h.assertTrue(f.toggle(1), "supported elevated ground pays one lift activation"); var grant = f.publish();
            h.assertTrue(WindPressureLift.consumeIntegrated(grant, f.p.server), "one actual paid proof authorizes the declared input fixture");
            // Both actors receive the same bounded initial motion, so the paired control isolates
            // hidden fall handling from trajectory. Only f owns a paid lift context and proof.
            for (Fixture actor : List.of(f, control)) {
                actor.p.setDeltaMovement(.8, -.0784 + .1, 0);
                actor.trajectory = new NativeTrajectory(actor); actor.hook(actor.trajectory);
            }
            f.afterPosts(1, () -> h.assertTrue(f.trajectory.lifted && control.trajectory.lifted, "real native travel raises both bodies before applying gravity; no fabricated postgravity displacement"));
            f.afterPosts(30, () -> {
                var a = f.trajectory; var b = control.trajectory;
                h.assertTrue(a.landed && b.landed && a.acceptedMoves > 5 && b.acceptedMoves == a.acceptedMoves, "complete paired native gravity paths produce real accepted packet landings");
                h.assertTrue(a.maxFall > 3 && b.maxFall == a.maxFall && a.monotonicFall && b.monotonicFall,
                        "actual accepted downward movement alone accumulated the same damaging fall distance without hidden airborne resets");
                BigDecimal loss = before.subtract(hp(f.p)), controlLoss = ordinaryBefore.subtract(hp(control.p));
                h.assertTrue(loss.signum() > 0 && loss.compareTo(controlLoss) == 0 && loss.compareTo(BigDecimal.valueOf(Math.ceil(a.maxFall - 3))) == 0,
                        "actual accumulated fall reaches ordinary ceil(distance-3) damage exactly and matches the unpaid control: loss="+loss+" control="+controlLoss+" fall="+a.maxFall);
                h.assertTrue(!WindPressureLift.active(f.p) && f.p.fallDistance == 0 && f.p.onGround() && f.p.getActiveEffects().isEmpty(),
                        "leaving the fixed column stops assistance; only native landing clears fall distance and grants no rescue"); f.succeed();
            });
        });
    }

    /** Capture actual native doTick travel, then submit it after Connection.tick's ordinary
     * restoration through the genuine movement handler. This is an explicit transport/input
     * fixture, not a claim that a LocalPlayer JVM was tested. It never writes fallDistance. */
    public static final class NativeTrajectory {
        final Fixture f; final double platformY; Vec3 predicted; boolean grounded, lifted, landed, monotonicFall = true;
        int acceptedMoves; float maxFall, previousFall;
        NativeTrajectory(Fixture f) { this.f = f; platformY = f.p.getY(); }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void capture(PlayerTickEvent.Post event) {
            if (event.getEntity() == f.p && !landed) { predicted = f.p.position(); grounded = f.p.onGround(); lifted |= predicted.y > platformY; }
        }
        void accept() {
            if (predicted == null) throw new AssertionError("Native trajectory did not observe the connection's actual player tick");
            f.moveAccepted(predicted, grounded); acceptedMoves++;
            float distance = f.p.fallDistance; maxFall = Math.max(maxFall, distance);
            if (!f.p.onGround() && distance + 1e-6F < previousFall) monotonicFall = false;
            previousFall = distance; landed = f.p.onGround() && Math.abs(f.p.getY() - f.start.y) < 1e-7;
            predicted = null;
        }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_small_and_rotation_only_native_teleports_revoke_before_post_without_undoing_destination(GameTestHelper h) {
        Fixture f = new Fixture(h), rotation = new Fixture(h, 22);
        h.assertTrue(cn.academy.port.highpower.mixin.NativeScatterPlateMixinPlugin.windPressureLiftTeleportAvailable(),
                "actual post-apply native teleport callback witness is installed");
        for (Fixture actor : List.of(f, rotation)) {
            h.assertTrue(actor.toggle(1), "paid native context before explicit native teleport"); var old = actor.publish(); double cp = actor.cp();
            Vec3 destination = actor == f ? actor.start.add(.1, 0, 0) : actor.start;
            float yaw = actor == f ? actor.p.getYRot() : 87;
            actor.p.connection.teleport(destination.x, destination.y, destination.z, yaw, 0, Set.of());
            h.assertFalse(WindPressureLift.active(actor.p) || WindPressureLift.consumeIntegrated(old, actor.p.server),
                    "explicit native teleport revokes proof synchronously even below movement threshold or with zero positional displacement");
            h.assertTrue(actor.p.position().equals(destination) && actor.p.getYRot() == yaw,
                    "lifecycle cancellation preserves the actual native teleport and current rotation");
            near(h, actor.cp(), cp, "teleport cancellation neither refunds prior payment nor charges a fresh activation");
            h.assertTrue(actor.payment().getInt("restart") == 8 && actor.payment().getInt("cooldown") == 10,
                    "small or rotation-only teleport retains ordinary stop debt");
            actor.drain(); WindPressureLift.tick(actor.p); actor.publish();
            h.assertFalse(WindPressureLift.active(actor.p), "native teleport acknowledgement cannot silently restore the old paid session");
        }
        f.succeed();
    }

    private record Shared(CompoundTag combat, Map<String, Double> mastery, double levelExperience, double overload) {}
    private record Ledger(double cp, CompoundTag resources, Shared shared) {}
    private static BigDecimal hp(ServerPlayer p) { return new BigDecimal(HighPowerCombat.healthText(p)); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " != " + expected); }
    private static Object field(Object owner, String name) {
        try { Class<?> type = owner instanceof Class<?> c ? c : owner.getClass(); while (type != null) {
            try { Field f = type.getDeclaredField(name); f.setAccessible(true); return f.get(owner instanceof Class<?> ? null : owner); }
            catch (NoSuchFieldException absent) { type = type.getSuperclass(); }
        } throw new NoSuchFieldException(name); } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static int spawnProtection(ServerPlayer p) { return (int) field(p, "spawnInvulnerableTime"); }
    private static Object invoke(String method, Class<?>[] signature, Object... args) {
        try { Method m = WindPressureLift.class.getDeclaredMethod(method, signature); m.setAccessible(true); return m.invoke(null, args); }
        catch (InvocationTargetException failure) { throw new AssertionError(failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    /** Real native connection-cycle ordering, once per actual server tick. EmbeddedChannel
     * is only a transport fixture; the actual doTick-and-restore implementation runs. */
    public static final class NativeConnectionPump {
        final Fixture f; int last;
        NativeConnectionPump(Fixture f) { this.f = f; last = f.p.server.getTickCount(); }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(ServerTickEvent.Post event) {
            if (event.getServer() == f.p.server && last != f.p.server.getTickCount() && !f.closed && !f.p.isRemoved() && !f.p.hasDisconnected()) {
                last = f.p.server.getTickCount(); try { f.p.connection.tick(); if (f.trajectory != null && !f.trajectory.landed) f.trajectory.accept(); } catch (Throwable failure) { f.fail(failure); }
            }
        }
    }

    public static final class PostCheck {
        final Fixture f; final int due; final Runnable action; boolean ran;
        PostCheck(Fixture f, int count, Runnable action) { this.f = f; due = f.p.server.getTickCount() + count; this.action = action; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void post(ServerTickEvent.Post event) {
            if (!ran && !f.terminalQueued && !f.h.testInfo.isDone() && event.getServer() == f.p.server && f.p.server.getTickCount() >= due) { ran = true; try { action.run(); } catch (Throwable failure) { f.fail(failure); } }
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerPlayer p; final EmbeddedChannel channel; final Vec3 start; final int x;
        final Map<BlockPos, BlockState> changed = new LinkedHashMap<>(); final Set<ChunkPos> held = new LinkedHashSet<>();
        final List<Object> hooks = new ArrayList<>(); final UUID ticket = UUID.randomUUID(); boolean closed, terminalQueued; Throwable terminalFailure; NativeTrajectory trajectory;
        Fixture(GameTestHelper h) { this(h, 7); }
        Fixture(GameTestHelper h, int x) { this(h, x, UUID.randomUUID(), false); }
        Fixture(GameTestHelper h, int x, UUID uuid, boolean rejoin) {
            this.h = h; this.x = x; h.onEachTick(this::finishQueuedTerminal); start = h.absoluteVec(new Vec3(x + .5, 2, 7.5));
            AABB arena = new AABB(start.add(-4, -1, -4), start.add(4, 6, 4));
            for (int cx = ((int) Math.floor(arena.minX)) >> 4; cx <= ((int) Math.floor(arena.maxX)) >> 4; cx++)
                for (int cz = ((int) Math.floor(arena.minZ)) >> 4; cz <= ((int) Math.floor(arena.maxZ)) >> 4; cz++) {
                    ChunkPos chunk = new ChunkPos(cx, cz); held.add(chunk); h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket); h.getLevel().getChunk(cx, cz);
                }
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) block(x + dx, 1, 7 + dz, Blocks.STONE.defaultBlockState());
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(uuid, "WL" + uuid.toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(); p.setGameMode(GameType.SURVIVAL); place();
            var state = AbilityStorage.get(p);
            if (rejoin) {
                h.assertTrue(HighPowerCombat.enabled(p) && HighPowerResources.effectiveLevel(p) == 4 && state.learned(ID)
                        && state.learned("multi_axis_reflection") && ID.equals(state.presets.currentSkill(0)),
                        "real PlayerList login loaded the saved enrolled contract, learned parent, skill and binding without new enrollment");
            } else {
                h.assertTrue(HighPowerCombat.enroll(p, 4), "explicit connected LV4 enrollment fixture");
                state.selectCategory(HighPowerCategories.VECMANIP); state.setLevel(4); state.learn("multi_axis_reflection"); state.learn(ID);
                state.activated = true; state.overloadFine = true; h.assertTrue(state.presets.edit(0, 0, ID, state::learned), "canonical learned input binding");
                HighPowerResources.initializeBudget(p);
            }
            HighPowerGrowth.values(p); epoch(); drain();
            hook(new NativeConnectionPump(this));
            h.testInfo.addListener(new GameTestListener() { public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); } });
        }
        void succeed() { if (!closed && !terminalQueued) terminalQueued = true; }
        void fail(Throwable failure) { if (!closed && !terminalQueued) { terminalQueued = true; terminalFailure = failure; } }
        void finishQueuedTerminal() { if (!closed && terminalQueued && !h.testInfo.isDone()) { if (terminalFailure == null) h.succeed(); else h.testInfo.fail(terminalFailure); } }
        void place() { p.moveTo(start.x, start.y, start.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setPose(Pose.STANDING); p.setShiftKeyDown(false);
            p.setNoGravity(false); p.setOnGround(true); p.setDeltaMovement(0, -.0784, 0); p.move(MoverType.SELF, new Vec3(0, -.0784, 0)); p.connection.resetPosition(); }
        void block(int px, int py, int pz, BlockState state) { BlockPos at = h.absolutePos(new BlockPos(px, py, pz)); changed.putIfAbsent(at, h.getLevel().getBlockState(at)); h.getLevel().setBlockAndUpdate(at, state); }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(long input) { return new HighPowerInput.Input(0, input, p.getId(), epoch()).wire(); }
        boolean toggle(long input) { return (boolean) invoke("toggle", new Class<?>[]{ServerPlayer.class, String.class, boolean.class}, p, wire(input), true); }
        WindPressureLift.Grant grant() { Object context = ((Map<?, ?>) field(WindPressureLift.class, "CONTEXTS")).get(p.getUUID()); return context == null ? null : (WindPressureLift.Grant) field(context, "grant"); }
        WindPressureLift.Grant publish() { invoke("post", new Class<?>[]{MinecraftServer.class}, p.server); return grant(); }
        CompoundTag payment() { return p.getPersistentData().getCompound(PAYMENT).copy(); }
        double cp() { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp"); }
        Ledger ledger() { var state = AbilityStorage.get(p); return new Ledger(cp(), p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy(),
                new Shared(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy(), Map.copyOf(state.experience), state.levelExperience, state.overload)); }
        void afterPosts(int ticks, Runnable action) { hook(new PostCheck(this, ticks, action)); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void moveAccepted(Vec3 destination, boolean ground) { p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(destination.x, destination.y, destination.z, ground));
            h.assertTrue(p.position().distanceToSqr(destination) < 1e-10, "real native packet accepted the declared destination"); }
        void land(float distance) { p.setNoGravity(false); p.setPos(start.add(0, .25, 0)); p.setOnGround(false); p.fallDistance = distance; p.setDeltaMovement(0, -.25, 0);
            p.connection.resetPosition(); p.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(start.x, start.y, start.z, true));
            h.assertTrue(p.position().distanceToSqr(start) < 1e-10, "actual native connection accepts supported ground contact"); }
        List<Object> drain() { channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); List<Object> result = new ArrayList<>(); Object packet;
            while ((packet = channel.readOutbound()) != null) { if (packet instanceof ClientboundPlayerPositionPacket pos) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(pos.getId())); result.add(packet); ReferenceCountUtil.release(packet); } return result; }
        @Override public void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); WindPressureLift.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p);
            p.server.getPlayerList().remove(p); changed.forEach((at, state) -> h.getLevel().setBlockAndUpdate(at, state)); held.forEach(chunk -> h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket)); channel.finishAndReleaseAll(); }
    }
}
