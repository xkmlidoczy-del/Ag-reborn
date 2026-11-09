package cn.academy.port.gametest;

import cn.academy.port.*;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.*;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.HighPowerLevel;
import cn.academy.port.highpower.core.PolarityTetherRules;
import cn.academy.port.machine.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real finite developer and authenticated input ingress. Materials, pose and accelerated clocks are fixtures. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerProgressionRuntimeTests {
    private static final String TEMPLATE="highpower_empty";
    private HighPowerProgressionRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch="highpower_progression",timeoutTicks=15)
    public static void finite_acquisition_learning_binding_precision_and_shared_item_budget(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();var state=AbilityStorage.get(p);var energy=f.portable(p,10000);
        p.getInventory().items.set(1,InductionFactors.stack("electromaster"));
        h.assertTrue(wire(p,"develop_level",""),"real acquisition request accepted");f.develop(p,130);
        h.assertTrue(state.category.equals("electromaster")&&state.level==1,"factor consumed by real finite acquisition");
        h.assertTrue(p.getInventory().items.get(1).isEmpty(),"factor inventory slot actually consumed");
        h.assertFalse(HighPowerCombat.enabled(p),"category acquisition alone does not opt into high power");
        h.assertTrue(wire(p,"learn",HighPowerSkills.FORKED_ARC),"portable learning ingress accepted");f.develop(p,77);
        h.assertFalse(state.learned(HighPowerSkills.FORKED_ARC)||HighPowerCombat.enabled(p),"no early skill or health grant");
        f.develop(p,1);h.assertTrue(state.learned(HighPowerSkills.FORKED_ARC)&&HighPowerCombat.enabled(p),"real completion enrolls and learns once");
        h.assertTrue(wire(p,"learn",HighPowerSkills.MAGNETIC_BRACER),"second actual learning request");f.develop(p,78);
        h.assertTrue(state.learned(HighPowerSkills.MAGNETIC_BRACER),"bracer learned through its prerequisite");
        near(h,energy.energy(),1420,"five acquisition plus six learning stimulations use exactly 8580 IF");
        h.assertTrue(state.presets.currentSkill(0).isEmpty(),"learning never silently assigns a key");
        h.assertTrue(wire(p,"preset_edit","0:0:forked_arc")&&wire(p,"preset_edit","0:1:magnetic_bracer"),"ordinary preset editor ingress binds separate skills");
        wire(p,"activate_state","true");
        var primary=f.target(p.position().add(0,0,5));var branch=f.target(p.position().add(2,0,5));
        h.assertTrue(HighPowerCombat.enroll(primary,5)&&HighPowerCombat.enroll(branch,5),"declared supported target fixtures");
        branch.getPersistentData().putLong("academy:conductive_until",p.serverLevel().getGameTime()+100);
        p.setShiftKeyDown(true);double classicCp=state.cp,budget=ElectromasterPrototype.prototypeCp(p);
        h.assertTrue(wire(p,"slot_press","0"),"learned crouch-tap uses the slot without a test item");
        exact(h,primary,HighPowerLevel.LV5.maxHealth().subtract(HighPowerLevel.LV1.damage()),"precision primary exact damage");
        exact(h,branch,HighPowerLevel.LV5.maxHealth(),"crouch precision omits conductive branch");
        near(h,ElectromasterPrototype.prototypeCp(p),budget-10,"one prototype CP debit");near(h,state.cp,classicCp,"no second classic CP debit");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(ElectromasterPrototype.CONTROL.get()));
        h.assertFalse(ElectromasterPrototype.cast(p),"test item shares the exact same attack cooldown");
        near(h,ElectromasterPrototype.prototypeCp(p),budget-10,"blocked duplicate never debits again");
        h.assertTrue(state.exp(HighPowerSkills.FORKED_ARC)>0&&state.levelExperience>0,"successful new skill earns actual mastery and level progress");
        var restored=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(state),state.cpDataConfig());
        h.assertTrue(restored.learned(HighPowerSkills.FORKED_ARC)&&restored.presets.currentSkill(1).equals(HighPowerSkills.MAGNETIC_BRACER),"persisted learning and bindings restore");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_progression",timeoutTicks=15)
    public static void learning_failures_revalidate_category_and_never_enroll_or_refill(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);var energy=f.portable(p,10000);
        s.changeCategoryClassic("electromaster");s.setLevel(1);
        h.assertFalse(DevelopmentController.startSkill(p,HighPowerSkills.MAGNETIC_BRACER),"unlearned prerequisite refused");
        h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.FORKED_ARC),"valid finite root starts");
        f.develop(p,77);s.changeCategoryClassic("teleporter");f.develop(p,1);
        h.assertFalse(HighPowerCombat.enabled(p)||s.learned(HighPowerSkills.FORKED_ARC),"category change cannot complete old learning/enrollment");
        near(h,energy.energy(),7660,"failed completion retains actual spent energy");
        s.changeCategoryClassic("electromaster");s.setLevel(1);energy.energy(100);
        h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.FORKED_ARC),"short battery starts only its actual work");f.develop(p,4);
        h.assertFalse(HighPowerCombat.enabled(p)||s.learned(HighPowerSkills.FORKED_ARC),"depleted developer grants nothing");
        h.assertTrue(DevelopmentController.process(p).state()==DevelopmentProcess.State.FAILED&&energy.energy()==0,"native partial energy drain and failure");
        energy.energy(10000);h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.FORKED_ARC),"genuine retry after explicit fixture charge");f.develop(p,78);
        HighPowerCombat.spendShield(p,cn.academy.port.highpower.core.HighPowerLevel.LV1.damage().multiply(new BigDecimal(".1")));var shield=HighPowerCombat.shield(p);var hp=HighPowerCombat.healthText(p);
        var budgetTag=p.getPersistentData().getCompound("academy:electromaster_prototype");budgetTag.putDouble("cp",37);p.getPersistentData().put("academy:electromaster_prototype",budgetTag);
        s.unlearn(HighPowerSkills.FORKED_ARC);h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.FORKED_ARC),"relearning still pays normal development cost");f.develop(p,78);
        h.assertTrue(HighPowerCombat.shield(p).equals(shield)&&HighPowerCombat.healthText(p).equals(hp),"relearning cannot refill exact pools");
        near(h,ElectromasterPrototype.prototypeCp(p),37,"relearning does not refill shared CP");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_progression",timeoutTicks=15)
    public static void bound_bracer_authenticates_owner_and_stale_release_and_cancels_on_remap(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();f.learn(p);var s=AbilityStorage.get(p);
        wire(p,"preset_edit","0:1:magnetic_bracer");wire(p,"activate_state","true");
        var first=f.input(p,1,1);h.assertTrue(wire(p,"hp_bracer_press",first),"real held-key bracer accepted");
        h.assertTrue(HighPowerBracerSession.active(p),"bound guard active without control item or crouch");
        var eye=p.getEyePosition();var incoming=f.arrow(eye.add(0,0,2),new Vec3(0,0,-1));var before=HighPowerCombat.shield(p);
        ElectromasterPrototype.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(incoming.getDeltaMovement().z>0,"bound hold redirects actual front arrow momentum");
        h.assertTrue(HighPowerCombat.shield(p).compareTo(before.subtract(cn.academy.port.highpower.core.HighPowerLevel.LV1.damage().multiply(new BigDecimal(".1"))))==0,"bound and item guard use shared exact shield cost");
        h.assertTrue(wire(p,"hp_bracer_stop",first)&&!HighPowerBracerSession.active(p),"captured key release ends guard");
        var second=f.input(p,1,2);h.assertTrue(wire(p,"hp_bracer_press",second),"newer hold accepted");
        h.assertFalse(wire(p,"hp_bracer_stop",first),"stale release cannot stop newer hold");
        h.assertTrue(HighPowerBracerSession.active(p),"new hold survives stale terminal");
        h.assertFalse(wire(p,"hp_bracer_press",second),"replayed press refused");
        h.assertFalse(wire(p,"hp_bracer_press",new HighPowerInput.Input(1,3,p.getId()+1,LegacySingleKeyOwnerEpoch.peek(p,p.connection,p.serverLevel(),s)).wire()),"forged actor refused");
        h.assertTrue(wire(p,"preset_edit","0:1:"),"ordinary preset removal accepted");
        h.assertFalse(HighPowerBracerSession.active(p)||HighPowerCombat.shieldActive(p),"removing binding cancels guard immediately");
        wire(p,"preset_edit","0:1:magnetic_bracer");h.assertTrue(wire(p,"hp_bracer_press",f.input(p,1,4)),"rebound fresh hold");wire(p,"activate_state","false");
        h.assertFalse(HighPowerBracerSession.active(p),"V deactivation disposes held guard");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_progression",timeoutTicks=15)
    public static void raw_name_unlearned_wrong_category_and_low_level_cannot_bypass_bound_skills(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();var s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");s.setLevel(5);s.setActivateState(true);
        h.assertFalse(wire(p,"cast",HighPowerSkills.FORKED_ARC),"wire ingress rejects raw skill-name casts");
        h.assertFalse(wire(p,"preset_edit","0:0:forked_arc"),"unlearned editor entry refused");
        h.assertTrue(HighPowerCombat.enroll(p,5),"explicit high-water ledger fixture");ElectromasterPrototype.initializeBudget(p);
        s.learn(HighPowerSkills.FORKED_ARC);wire(p,"preset_edit","0:0:forked_arc");
        double retainedCp=ElectromasterPrototype.prototypeCp(p);s.setLevel(1);
        near(h,ElectromasterPrototype.prototypeCp(p),retainedCp,"rollback retains earned CP balance and high-water capacity");
        var target=f.target(p.position().add(0,0,5));h.assertTrue(HighPowerCombat.enroll(target,5),"target fixture");
        h.assertTrue(wire(p,"slot_press","0"),"reset level uses lower legal preset");
        exact(h,target,HighPowerLevel.LV5.maxHealth().subtract(HighPowerLevel.LV1.damage()),"old LV5 ledger cannot retain LV5 attack after level downgrade");
        near(h,ElectromasterPrototype.prototypeCp(p),retainedCp-10,"lower actual level charges LV1 cost without clamping or refilling earned CP");
        s.changeCategoryClassic("teleporter");h.assertFalse(ElectromasterPrototype.cast(p),"test item cannot use old ledger in another category");
        s.changeCategoryClassic("electromaster");s.setLevel(0);h.assertFalse(ElectromasterPrototype.cast(p),"test item cannot bypass real level-zero gate");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_promotion",timeoutTicks=85)
    public static void real_finite_level_development_promotes_capacity_without_refilling_one_point_deficits(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();f.learn(p);var state=AbilityStorage.get(p);
        h.runAtTickTime(61,()->{
            p.doTick();
            h.assertTrue(p.hurt(p.damageSources().magic(),1),"ordinary native one-point damage after actual spawn protection");
            var expectedHp=HighPowerLevel.LV1.maxHealth().subtract(BigDecimal.ONE);
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(p)).compareTo(expectedHp)==0,"exact one-point HP deficit is retained before promotion");
            h.assertTrue(HighPowerCombat.spendShield(p,BigDecimal.ONE),"explicit one-point shield expenditure fixture");
            var expectedShield=HighPowerLevel.LV1.maxShield().subtract(BigDecimal.ONE);
            var control=p.getPersistentData().getCompound("academy:electromaster_prototype");control.putDouble("cp",37);p.getPersistentData().put("academy:electromaster_prototype",control);
            var energy=f.portable(p,10000);
            // Real XP reaches the next numeric anchor; integer unlock still needs paid development.
            state.addExperience(HighPowerSkills.FORKED_ARC,cn.academy.port.core.ClassicRules.levelThreshold(1,SkillCatalog.levelSkillCount(state)));
            h.assertTrue(new BigDecimal(HighPowerCombat.maxHealthText(p)).compareTo(HighPowerLevel.LV2.maxHealth())==0,"full LV1 progress already reaches continuous LV2 capacity");
            h.assertTrue(DevelopmentController.startLevel(p),"earned-threshold fixture enters real level development");f.develop(p,259);
            h.assertTrue(state.level==1&&HighPowerCombat.level(p)==1,"integer unlock cannot increase before final paid stimulation tick");
            f.develop(p,1);
            h.assertTrue(state.level==2&&HighPowerCombat.level(p)==2,"real paid developer completion certifies the next integer level");
            near(h,energy.energy(),2200,"ten portable level-development stimulations debit exactly 7800 IF");
            h.assertTrue(new BigDecimal(HighPowerCombat.maxHealthText(p)).compareTo(HighPowerLevel.LV2.maxHealth())==0,"LV2 exact health capacity");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(p)).compareTo(expectedHp)==0&&HighPowerCombat.shield(p).compareTo(expectedShield)==0,"promotion preserves absolute one-point deficits and does not proportionally heal");
            near(h,ElectromasterPrototype.prototypeCp(p),37,"promotion does not refill Prototype CP");
            h.assertFalse(HighPowerCombat.promoteLevel(p,2)||HighPowerProgression.promote(p),"repeated promotion cannot mint a refill");
            state.setLevel(1);h.assertFalse(HighPowerCombat.promoteLevel(p,1),"downgrade cannot reset ledger capacity");
            h.assertTrue(ElectromasterPrototype.effectiveLevel(p)==1,"damage and CP still honor the downgraded actual level");
            state.changeCategoryClassic("teleporter");state.setLevel(5);h.assertFalse(HighPowerCombat.promoteLevel(p,5),"raw TP level observation cannot certify shared capacity without an earned receipt");
            state.changeCategoryClassic("electromaster");state.setLevel(1);
            h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.FORKED_ARC),"category-return relearning uses actual developer work");
            // The old level-development battery is short of this new learning cost: give this declared fixture a new finite battery.
            var relearnEnergy=f.portable(p,10000);f.develop(p,78);
            h.assertTrue(state.learned(HighPowerSkills.FORKED_ARC)&&HighPowerCombat.level(p)==2,"relearning lower-level ability preserves the high-water ledger");
            h.assertTrue(new BigDecimal(HighPowerCombat.healthText(p)).compareTo(expectedHp)==0&&HighPowerCombat.shield(p).compareTo(expectedShield)==0,"category-return relearning cannot refill exact balances");
            near(h,ElectromasterPrototype.prototypeCp(p),37,"category-return relearning cannot refill Prototype CP");
            near(h,relearnEnergy.energy(),7660,"category-return learning pays 2340 IF");h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch="highpower_progression",timeoutTicks=15)
    public static void early_legacy_contract_learning_rejection_returns_exact_correlated_feedback_before_any_IF(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();var state=AbilityStorage.get(p);state.changeCategoryClassic("electromaster");state.setLevel(1);
        var energy=f.portable(p,10000);h.assertTrue(HighPowerCombat.enroll(p,1),"declared old-player ledger fixture");
        p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).remove("adapterContract");
        String hp=HighPowerCombat.healthText(p);var shield=HighPowerCombat.shield(p);f.payloads();
        var request=new HighPowerLearningProtocol.Request(HighPowerSkills.FORKED_ARC,1);
        h.assertFalse(wire(p,"hp_learn",request.wire()),"server rejects the unapproved legacy ledger before starting");
        h.assertFalse(DevelopmentController.process(p).isDeveloping()||state.learned(HighPowerSkills.FORKED_ARC),"no false process or skill on early rejection");
        near(h,energy.energy(),10000,"early rejection spends zero finite IF");
        h.assertTrue(HighPowerCombat.healthText(p).equals(hp)&&HighPowerCombat.shield(p).equals(shield),"early rejection leaves exact pools unchanged");
        var messages=f.payloads().stream().filter(tag->tag.getString("kind").equals(HighPowerLearningProtocol.KIND)).toList();
        h.assertTrue(messages.size()==1,"one immediate result emitted on the actual owned connection");
        var pending=new HighPowerLearningProtocol.Pending();pending.begin(request);
        h.assertTrue(pending.receive(messages.getFirst())&&!pending.accepted()&&!pending.waiting()&&pending.reason().contains("approval"),"actual server feedback clears modal wait with precise approval reason");
        h.assertTrue(HighPowerCombat.approveVanillaContract(p),"explicit fixture approval preserves rather than resets ledger");
        var retry=new HighPowerLearningProtocol.Request(HighPowerSkills.FORKED_ARC,2);pending.begin(retry);
        h.assertTrue(wire(p,"hp_learn",retry.wire()),"new correlated request starts after approval");
        var started=f.payloads().stream().filter(tag->tag.getString("kind").equals(HighPowerLearningProtocol.KIND)).findFirst().orElseThrow();
        h.assertTrue(!pending.receive(messages.getFirst())&&pending.receive(started)&&pending.accepted(),"old rejection cannot dismiss successful retry");
        f.develop(p,78);h.assertTrue(state.learned(HighPowerSkills.FORKED_ARC),"approved retry completes actual finite learning");
        near(h,energy.energy(),7660,"only successful attempt spends 2340 IF");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_progression",timeoutTicks=15)
    public static void polarity_tether_finite_learning_fourth_slot_authenticated_taps_and_motion_lifecycle(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();f.learn(p);var state=AbilityStorage.get(p);
        var energy=new DeveloperItemEnergy(p.getMainHandItem(),DeveloperType.PORTABLE);double before=energy.energy();
        h.assertFalse(wire(p,"preset_edit","0:3:polarity_tether"),"unlearned NEW113 cannot occupy the unchanged fourth slot");
        var learning=new HighPowerLearningProtocol.Request(HighPowerSkills.POLARITY_TETHER,113);
        h.assertTrue(wire(p,"hp_learn",learning.wire()),"NEW113 uses genuine correlated portable learning");f.develop(p,77);
        h.assertFalse(state.learned(HighPowerSkills.POLARITY_TETHER),"NEW113 has no early learned bit");f.develop(p,1);
        h.assertTrue(state.level==1&&state.learned(HighPowerSkills.POLARITY_TETHER),"NEW113 is earned at actual LV1");
        near(h,energy.energy(),before-2340,"NEW113 pays three portable stimulations/2340 IF");
        h.assertTrue(SkillCatalog.levelSkillCount(state)==2,"NEW113 does not inflate the classic level-XP denominator");
        // Declared LV2/old-skill fixture only to probe the competing original movement controller.
        state.setLevel(2);state.learn("mag_movement");
        h.assertTrue(wire(p,"preset_edit","0:0:forked_arc")&&wire(p,"preset_edit","0:1:magnetic_bracer")
                &&wire(p,"preset_edit","0:2:mag_movement")&&wire(p,"preset_edit","0:3:polarity_tether"),"new tether reuses the original four slots without replacing existing IDs");
        wire(p,"activate_state","true");h.setBlock(5,3,10,net.minecraft.world.level.block.Blocks.IRON_BLOCK);
        h.assertFalse(wire(p,"slot_press","3"),"generic slot messages cannot bypass tether input identity");
        h.assertFalse(wire(p,"hp_tether_tap",f.input(p,3,8)+":target"),"no client target or extra velocity field is accepted");
        long epoch=LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),state);
        h.assertFalse(wire(p,"hp_tether_tap",new HighPowerInput.Input(3,9,p.getId()+1,epoch).wire()),"wrong actor cannot create tether");
        String tap=f.input(p,3,10);h.assertTrue(wire(p,"hp_tether_tap",tap)&&PolarityTether.active(p),"authenticated first tap creates one pending context");
        h.assertFalse(wire(p,"hp_tether_tap",tap),"same physical input cannot be replayed");
        var movement=new cn.academy.port.core.LegacySingleKeyProtocol.StartRequest(2,
                cn.academy.port.core.LegacySingleKeyProtocol.Skill.MAG_MOVEMENT,100,p.getId(),epoch);
        double oldCp=state.cp,oldOverload=state.overload;
        h.assertFalse(wire(p,cn.academy.port.core.LegacySingleKeyProtocol.PRESS,movement.wire())||cn.academy.port.skill.MagMovement.active(p),"active tether blocks the competing authenticated classic movement start");
        near(h,state.cp,oldCp,"blocked old movement cannot charge classic CP");near(h,state.overload,oldOverload,"blocked old movement cannot charge overload");
        p.setDeltaMovement(.1,0,.05);var inertia=p.getDeltaMovement();
        h.assertTrue(wire(p,"hp_tether_abort",tap)&&!PolarityTether.active(p),"screen/focus abort route closes only its authenticated bounded input");
        h.assertTrue(p.getDeltaMovement().equals(inertia),"screen/focus abort preserves true inertia");
        h.assertTrue(wire(p,"hp_tether_tap",f.input(p,3,11)),"later input can establish a new pending context");
        h.assertTrue(wire(p,"preset_edit","0:3:polarity_tether"),"ordinary preset revision accepted");
        h.assertFalse(PolarityTether.active(p),"preset revision cancels the tether context");
        h.assertTrue(p.getDeltaMovement().equals(inertia),"preset cancellation preserves existing physical inertia");
        h.assertTrue(wire(p,"hp_tether_tap",f.input(p,3,12)),"fresh press after revision starts a fresh context");
        h.assertFalse(wire(p,"hp_tether_abort",tap),"late abort for the older input cannot end the new context");
        h.assertTrue(PolarityTether.active(p),"newer context survives the stale screen-close packet");
        h.runAtTickTime(1,()->{
            PolarityTether.tick(p);h.assertTrue(PolarityTether.phase(p)==PolarityTetherRules.Phase.ANCHORED,"first tap commits against the real native clock");
            h.assertTrue(wire(p,"hp_tether_tap",f.input(p,3,13))&&PolarityTether.phase(p)==PolarityTetherRules.Phase.PULLING,"second authenticated press transitions the same tether to pull");
            h.runAtTickTime(2,()->{
                PolarityTether.tick(p);var beforeCancel=p.getDeltaMovement();wire(p,"activate_state","false");
                h.assertFalse(PolarityTether.active(p),"V deactivation ends the active context");
                h.assertTrue(p.getDeltaMovement().equals(beforeCancel),"V cancellation does not invent zero velocity or braking");
                var saved=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(state),state.cpDataConfig());
                h.assertTrue(saved.learned(HighPowerSkills.POLARITY_TETHER)&&saved.presets.currentSkill(3).equals(HighPowerSkills.POLARITY_TETHER)
                        &&saved.presets.currentSkill(0).equals(HighPowerSkills.FORKED_ARC)&&saved.presets.currentSkill(1).equals(HighPowerSkills.MAGNETIC_BRACER),"fourth extension and old slot IDs survive the same save schema");h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch="highpower_progression",timeoutTicks=15)
    public static void circuit_and_grounding_use_real_lv2_learning_existing_slots_and_scoped_input_cancellation(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();f.learn(p);var state=AbilityStorage.get(p);
        h.assertFalse(DevelopmentController.startSkill(p,HighPowerSkills.CONDUCTIVE_CIRCUIT)
                ||DevelopmentController.startSkill(p,HighPowerSkills.GROUNDING_RETURN),"both new skills reject actual LV1 despite learned parents");
        h.assertFalse(wire(p,"preset_edit","0:2:conductive_circuit")||wire(p,"preset_edit","0:3:grounding_return"),"unlearned new skills cannot be bound");
        state.addExperience(HighPowerSkills.FORKED_ARC,cn.academy.port.core.ClassicRules.levelThreshold(1,SkillCatalog.levelSkillCount(state)));
        var levelEnergy=f.portable(p,10000);
        h.assertTrue(DevelopmentController.startLevel(p),"real earned LV1 threshold starts finite LV2 unlock");f.develop(p,259);
        h.assertTrue(state.level==1&&HighPowerCombat.earnedLevel(p)==1,"LV2 certification does not precede final paid stimulation");f.develop(p,1);
        h.assertTrue(state.level==2&&HighPowerCombat.earnedLevel(p)==2,"actual completed developer work certifies LV2 before learning its skills");
        near(h,levelEnergy.energy(),2200,"LV2 unlock consumes7800IF");
        var energy=f.portable(p,10000); // New finite skill-learning battery, independent of the paid level work.
        int sequence=121;
        for(String skill:List.of(HighPowerSkills.CONDUCTIVE_CIRCUIT,HighPowerSkills.GROUNDING_RETURN)){
            h.assertTrue(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(skill,sequence++).wire()),"real correlated portable LV2 learning starts "+skill);
            f.develop(p,129);h.assertFalse(state.learned(skill),"no early LV2 grant "+skill);f.develop(p,1);
            h.assertTrue(state.learned(skill),"five real paid stimulation cycles learn "+skill);
        }
        near(h,energy.energy(),2200,"both LV2 additions consume3900IF each, no free learning");
        h.assertTrue(SkillCatalog.levelSkillCount(state)==2,"six high-power extensions do not change classic LV2 XP threshold");
        h.assertTrue(wire(p,"preset_edit","0:0:forked_arc")&&wire(p,"preset_edit","0:1:magnetic_bracer")
                &&wire(p,"preset_edit","0:2:conductive_circuit")&&wire(p,"preset_edit","0:3:grounding_return"),"NEW121/122 reuse existing R/F positions without new keys");
        wire(p,"activate_state","true");h.setBlock(5,1,4,net.minecraft.world.level.block.Blocks.STONE);
        h.setBlock(5,1,6,net.minecraft.world.level.block.Blocks.STONE);p.setXRot(45);p.setYRot(0);p.setYHeadRot(0);p.setDeltaMovement(Vec3.ZERO);
        h.assertFalse(wire(p,"slot_press","2")||wire(p,"slot_press","3"),"generic unscoped slot requests cannot start persistent additions");
        String circuit=f.input(p,2,121);
        h.assertFalse(wire(p,"hp_circuit_tap",circuit+":target"),"circuit never accepts a client-supplied anchor or damage field");
        h.assertTrue(wire(p,"hp_circuit_tap",circuit)&&ConductiveCircuit.active(p),"authenticated first tap places a server-raytraced ground anchor");
        h.assertFalse(wire(p,"hp_circuit_tap",circuit),"circuit nonce replay rejected");
        h.assertTrue(wire(p,"hp_circuit_abort",circuit)&&!ConductiveCircuit.active(p),"captured screen/focus cancel removes the matching circuit");
        p.setXRot(0);p.setShiftKeyDown(true);p.setOnGround(true); // Real STONE support beneath the declared standing fixture.
        String grounding=f.input(p,3,122);long epoch=LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),state);
        h.assertFalse(wire(p,"hp_ground_start",new HighPowerInput.Input(3,121,p.getId()+1,epoch).wire()),"forged grounding actor rejected");
        double cp=ElectromasterPrototype.prototypeCp(p);String hp=HighPowerCombat.healthText(p);var shield=HighPowerCombat.shield(p);
        h.assertTrue(wire(p,"hp_ground_start",grounding)&&GroundingReturn.active(p),"crouched authenticated tap creates a supported-ground stance");
        near(h,ElectromasterPrototype.prototypeCp(p),cp,"zero-upfront-CP stance does not invent a CP debit");
        h.assertFalse(wire(p,"hp_ground_abort",f.input(p,3,121)),"old grounding cancel cannot target the new context");
        h.assertTrue(GroundingReturn.active(p),"grounding survives stale cancellation");
        h.assertFalse(wire(p,"hp_ground_start",f.input(p,3,123)),"repeated fresh tap cannot bypass active stance/cooldown");
        h.assertTrue(wire(p,"hp_ground_abort",f.input(p,3,123))&&!GroundingReturn.active(p),"focus cancellation through last emitted input also cancels a prior active start after a rejected retry");
        h.assertTrue(HighPowerCombat.healthText(p).equals(hp)&&HighPowerCombat.shield(p).equals(shield),"starting or canceling without a real hit neither heals nor refills shield");
        var saved=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(state),state.cpDataConfig());
        h.assertTrue(saved.presets.currentSkill(0).equals(HighPowerSkills.FORKED_ARC)&&saved.presets.currentSkill(1).equals(HighPowerSkills.MAGNETIC_BRACER)
                &&saved.presets.currentSkill(2).equals(HighPowerSkills.CONDUCTIVE_CIRCUIT)&&saved.presets.currentSkill(3).equals(HighPowerSkills.GROUNDING_RETURN),"old and new IDs retain exact existing slot/save schema");
        h.assertTrue(saved.learned(HighPowerSkills.CONDUCTIVE_CIRCUIT)&&saved.learned(HighPowerSkills.GROUNDING_RETURN),"both actually earned LV2 learned bits persist");h.succeed();
    }

    @GameTest(template=TEMPLATE,batch="highpower_progression",timeoutTicks=15)
    public static void magnetic_ground_step_finite_normal_learning_integer_lv3_four_slots_and_bound_tap(GameTestHelper h) {
        var f=new Fixture(h);var p=f.player();
        // The embedded client must acknowledge PlayerList's first native teleport before
        // later movement packets can establish a stable connected-player ground fixture.
        p.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(1));
        f.learn(p);var state=AbilityStorage.get(p);
        h.assertTrue(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(HighPowerSkills.POLARITY_TETHER,113).wire()),"real prerequisite learning starts");f.develop(p,78);
        h.assertTrue(state.learned(HighPowerSkills.POLARITY_TETHER),"tether prerequisite genuinely earned through finite portable work");
        h.assertFalse(wire(p,"preset_edit","0:3:magnetic_ground_step"),"unlearned NEW132 cannot enter fourth slot");
        var item=MachineDevelopers.NORMAL_ITEM.get();var stack=new ItemStack(item);p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        var base=h.absolutePos(new BlockPos(12,1,14));
        h.assertTrue(item.place(new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(base),Direction.UP,base,false))).consumesAction(),"physical eight-cell Normal developer placed");
        var machine=(MachineDeveloperBlockEntity)h.getLevel().getBlockEntity(base);machine.battery().load(50000);
        p.moveTo(base.getX()+.5,base.getY(),base.getZ()-1.5,0,0);
        h.assertTrue(machine.use(p),"real sender-bound Normal developer session opens");
        String token=MachineDeveloperSessions.activeToken(p).orElseThrow().toString();
        for(int nextLevel=2;nextLevel<=3;nextLevel++){
            state.addExperience(HighPowerSkills.FORKED_ARC,cn.academy.port.core.ClassicRules.levelThreshold(state.level,SkillCatalog.levelSkillCount(state)));
            h.assertFalse(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_hp_learn",token+":"+new HighPowerLearningProtocol.Request(HighPowerSkills.MAGNETIC_GROUND_STEP,132).wire())),"continuous progress at integer LV"+state.level+" cannot unlock NEW132");
            h.assertTrue(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_level",token)),"real finite Normal level development starts");
            int ticks=5*nextLevel*DeveloperType.NORMAL.ticksPerStimulation();f.develop(p,ticks-1);
            h.assertTrue(state.level==nextLevel-1&&HighPowerCombat.earnedLevel(p)==nextLevel-1,"integer unlock waits for final finite stimulation");f.develop(p,1);
            h.assertTrue(state.level==nextLevel&&HighPowerCombat.earnedLevel(p)==nextLevel,"paid level work certifies LV"+nextLevel);
        }
        near(h,machine.battery().getEnergy(),31625,"LV2 and LV3 certification use7350+11025 actual Normal IF");
        f.portable(p,10000);
        h.assertFalse(wire(p,"hp_learn",new HighPowerLearningProtocol.Request(HighPowerSkills.MAGNETIC_GROUND_STEP,132).wire()),"Portable cannot learn LV3 even after certification");
        var request=new HighPowerLearningProtocol.Request(HighPowerSkills.MAGNETIC_GROUND_STEP,Integer.MAX_VALUE);
        String full=token+":"+request.wire();h.assertTrue(full.length()<=64&&request.wire().startsWith("mgs:~"),"wire-only NEW132 alias keeps full UUID plus max correlation within unchanged64-character bound");
        var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),h.getLevel().registryAccess());
        AcademyNetwork.Request decoded;
        try{AcademyNetwork.Request.CODEC.encode(buffer,new AcademyNetwork.Request("machine_hp_learn",full));decoded=AcademyNetwork.Request.CODEC.decode(buffer);}finally{buffer.release();}
        h.assertTrue(decoded.value().equals(full),"actual original network codec accepts longest complete machine learning payload");
        h.assertFalse(MachineDeveloperSessions.request(p,new AcademyNetwork.Request("machine_hp_learn",UUID.randomUUID()+":"+request.wire())),"forged machine token cannot learn NEW132");
        h.assertTrue(MachineDeveloperSessions.request(p,decoded),"real correlated Normal learning ingress accepts NEW132");
        f.develop(p,146);h.assertFalse(state.learned(HighPowerSkills.MAGNETIC_GROUND_STEP),"no early NEW132 learned bit");f.develop(p,1);
        h.assertTrue(state.learned(HighPowerSkills.MAGNETIC_GROUND_STEP)&&state.exp(HighPowerSkills.MAGNETIC_GROUND_STEP)==0,"seven finite Normal stimulations genuinely learn NEW132 at zero mastery");
        near(h,machine.battery().getEnergy(),26480,"NEW132 debits5145 IF without free learning");
        h.assertTrue(state.presets.currentSkill(3).isEmpty(),"learning never silently assigns a key");
        h.assertTrue(SkillCatalog.ALL.size()==50&&cn.academy.port.core.SkillPresets.MAX_KEYS==4&&cn.academy.port.core.SkillPresets.MAX_PRESETS==4,"fifty canonical skills and existing four-by-four preset schema preserved");
        h.assertTrue(SkillCatalog.levelSkillCount(state)==SkillCatalog.ALL.stream().filter(skill->skill.category().equals(state.category)&&skill.level()==3&&skill.controllable()&&SkillCatalog.enabled(skill.category(),skill.id())).count(),"NEW132 does not inflate classic LV3 progression denominator");
        MachineDeveloperSessions.close(p);
        h.assertTrue(wire(p,"preset_edit","0:0:forked_arc")&&wire(p,"preset_edit","0:1:magnetic_bracer")
                &&wire(p,"preset_edit","0:2:polarity_tether")&&wire(p,"preset_edit","0:3:magnetic_ground_step"),"NEW132 shares the same four slots with previously earned skills");
        wire(p,"activate_state","true");
        for(int x=4;x<=6;x++)for(int z=3;z<=14;z++)h.setBlock(x,1,z,net.minecraft.world.level.block.Blocks.STONE);
        var at=h.absoluteVec(new Vec3(5.5,2,4.5));p.moveTo(at.x,at.y,at.z,0,45);p.setYHeadRot(0);p.setOnGround(true);p.setSprinting(true);p.setDeltaMovement(Vec3.ZERO);
        p.connection.resetPosition();
        p.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(at.x,at.y,at.z,true));
        h.assertTrue(p.position().distanceToSqr(at)<1e-10&&p.onGround(),"ordinary acknowledged movement packet establishes the supported runway pose");
        h.assertFalse(wire(p,"slot_press","3"),"generic slot route cannot bypass authenticated NEW132 input");
        long epoch=LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),state);
        h.assertFalse(wire(p,"hp_ground_step_tap",new HighPowerInput.Input(3,131,p.getId()+1,epoch).wire()),"wrong actor cannot create dash");
        String tap=f.input(p,3,132);h.assertFalse(wire(p,"hp_ground_step_tap",tap+":8:0:0"),"client target or velocity fields cannot enter NEW132 protocol");
        double classicCp=state.cp,cp=ElectromasterPrototype.prototypeCp(p);f.payloads();
        h.assertTrue(wire(p,"hp_ground_step_tap",tap)&&MagneticGroundStep.active(p),"genuinely earned fourth-slot sprint tap starts one server ground controller");
        h.assertTrue(ElectromasterPrototype.prototypeCp(p)<cp,"NEW132 pays shared prototype CP");near(h,state.cp,classicCp,"NEW132 does not double-debit classic CP");
        double paid=ElectromasterPrototype.prototypeCp(p);
        h.assertFalse(wire(p,"hp_ground_step_tap",tap),"replayed NEW132 press is rejected");near(h,ElectromasterPrototype.prototypeCp(p),paid,"replay cannot spend twice");
        h.assertFalse(wire(p,"slot_release","3")||wire(p,"slot_abort","3"),"generic key release or unscoped abort cannot alter fixed tap mode");
        h.assertTrue(MagneticGroundStep.active(p),"key-up retains automatic-duration ground step");
        h.assertFalse(wire(p,"hp_ground_step_abort",f.input(p,3,131)),"older captured cancel cannot terminate current dash");
        long launchedAt=p.serverLevel().getServer().overworld().getGameTime();
        h.runAfterDelay(1,()->{
            h.assertTrue(p.serverLevel().getServer().overworld().getGameTime()>launchedAt,"relative continuation reaches a genuinely later stable server tick");
            // An ordinary grounded client sends this authoritative native position packet;
            // the fixture has no LocalPlayer or predictor that would otherwise emit it.
            var stationary=p.position();p.connection.resetPosition();
            p.connection.handleMovePlayer(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos(stationary.x,stationary.y,stationary.z,true));
            h.assertTrue(p.position().distanceToSqr(at)<1e-10&&p.onGround(),"native packet keeps the earned stationary authorization fixture grounded");
            MagneticGroundStep.tick(p);
            var assists=f.groundStepAssists();
            h.assertTrue(MagneticGroundStep.active(p)&&assists.stream().anyMatch(packet->!packet.terminal()
                    &&packet.entity()==p.getId()&&packet.owner().equals(p.getUUID())&&packet.input()==132&&packet.epoch()==epoch
                    &&packet.token()>0&&packet.sequence()>0&&packet.remaining()>0&&packet.remaining()<=cn.academy.port.highpower.core.MagneticGroundStepRules.range(HighPowerGrowth.values(p).position())+1e-9
                    &&packet.remainingTicks()>0&&packet.expiresAt()-packet.issuedAt()==2),"stable server tick issues bounded owner-only movement authorization for the earned input; active="+MagneticGroundStep.active(p)+", ground="+p.onGround()+", sprint="+p.isSprinting()+", epoch="+epoch+", packets="+assists);
            var velocity=p.getDeltaMovement();float fall=p.fallDistance;
            h.assertTrue(wire(p,"preset_edit","0:3:magnetic_ground_step")&&!MagneticGroundStep.active(p),"ordinary authenticated preset revision cancels ground step immediately");
            h.assertTrue(p.getDeltaMovement().equals(velocity)&&p.fallDistance==fall,"remap cleanup invents neither braking nor fall reset");
            var saved=AbilityStorage.decodeSaved(AbilityStorage.encodeSaved(state),state.cpDataConfig());
            h.assertTrue(saved.learned(HighPowerSkills.MAGNETIC_GROUND_STEP)&&saved.presets.currentSkill(3).equals(HighPowerSkills.MAGNETIC_GROUND_STEP)
                    &&saved.presets.currentSkill(0).equals(HighPowerSkills.FORKED_ARC)&&saved.presets.currentSkill(2).equals(HighPowerSkills.POLARITY_TETHER),"NEW132 and existing learned IDs/bindings roundtrip unchanged schema");
            state.setLevel(2);h.assertFalse(wire(p,"hp_ground_step_tap",f.input(p,3,133)),"saved learned NEW132 cannot bypass later integer-level downgrade");h.succeed();
        });
    }

    private static boolean wire(ServerPlayer p,String action,String value){return AcademyGameplay.requestFromClient(p,new AcademyNetwork.Request(action,value));}
    private static void exact(GameTestHelper h,Villager target,BigDecimal expected,String reason){h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(expected)==0,reason+"; actual="+HighPowerCombat.healthText(target));}
    private static void near(GameTestHelper h,double actual,double expected,String reason){h.assertTrue(Math.abs(actual-expected)<1e-7,reason+"; actual="+actual+" expected="+expected);}
    private static final class Fixture {
        final GameTestHelper h;final List<ServerPlayer> players=new ArrayList<>();final List<EmbeddedChannel> channels=new ArrayList<>();final List<Entity> entities=new ArrayList<>();boolean closed;
        Fixture(GameTestHelper h){this.h=h;h.testInfo.addListener(new GameTestListener(){public void testStructureLoaded(GameTestInfo i){}public void testPassed(GameTestInfo i,GameTestRunner r){close();}public void testFailed(GameTestInfo i,GameTestRunner r){close();}public void testAddedForRerun(GameTestInfo i,GameTestInfo n,GameTestRunner r){close();}});}
        ServerPlayer player(){
            var server=h.getLevel().getServer();var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"HPProgress"),false);
            var p=new ServerPlayer(server,h.getLevel(),cookie.gameProfile(),cookie.clientInformation());players.add(p);
            var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);channels.add(channel);NetworkRegistry.configureMockConnection(connection);server.getPlayerList().placeNewPlayer(connection,p,cookie);
            var pos=h.absoluteVec(new Vec3(5.5,2,4.5));p.moveTo(pos.x,pos.y,pos.z,0,0);p.setYHeadRot(0);p.setNoGravity(true);p.setGameMode(GameType.SURVIVAL);return p;
        }
        DeveloperItemEnergy portable(ServerPlayer p,double amount){var stack=new ItemStack(AcademyCraft.DEVELOPER.get());p.setItemInHand(InteractionHand.MAIN_HAND,stack);var energy=new DeveloperItemEnergy(stack,DeveloperType.PORTABLE);energy.energy(amount);return energy;}
        void develop(ServerPlayer p,int ticks){for(int i=0;i<ticks;i++)DevelopmentController.tick(p,ignored->{});}
        void learn(ServerPlayer p){var s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");s.setLevel(1);portable(p,10000);h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.FORKED_ARC),"fixture starts real learning");develop(p,78);h.assertTrue(DevelopmentController.startSkill(p,HighPowerSkills.MAGNETIC_BRACER),"fixture starts real bracer learning");develop(p,78);}
        String input(ServerPlayer p,int slot,long input){var state=AbilityStorage.get(p);return new HighPowerInput.Input(slot,input,p.getId(),LegacySingleKeyOwnerEpoch.current(p,p.connection,p.serverLevel(),state)).wire();}
        Villager target(Vec3 at){var target=new Villager(EntityType.VILLAGER,h.getLevel());target.setPos(at);target.setNoAi(true);target.setNoGravity(true);h.getLevel().addFreshEntity(target);entities.add(target);return target;}
        Arrow arrow(Vec3 at,Vec3 velocity){var arrow=new Arrow(EntityType.ARROW,h.getLevel());arrow.setPos(at);arrow.setDeltaMovement(velocity);arrow.setNoGravity(true);h.getLevel().addFreshEntity(arrow);entities.add(arrow);return arrow;}
        List<MagneticGroundStep.Assist> groundStepAssists(){
            var result=new ArrayList<MagneticGroundStep.Assist>();
            // GameTestTicker executes inside tickChildren while native player connections
            // suspend flushing. Pending channel.write packets are not readable outbound
            // until the real transport flush boundary, so explicitly flush this embedded
            // transport before inspecting the actual packets already issued by the server.
            for(var channel:channels){channel.runPendingTasks();channel.flushOutbound();channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof MagneticGroundStep.Assist assist)result.add(assist);
                io.netty.util.ReferenceCountUtil.release(packet);
            }}return result;
        }
        List<net.minecraft.nbt.CompoundTag> payloads(){
            var result=new ArrayList<net.minecraft.nbt.CompoundTag>();
            for(var channel:channels){channel.runPendingTasks();Object packet;while((packet=channel.readOutbound())!=null){
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom&&custom.payload() instanceof AcademyNetwork.ClientData data)result.add(data.data().copy());
                io.netty.util.ReferenceCountUtil.release(packet);
            }}return result;
        }
        void close(){if(closed)return;closed=true;for(var p:players){HighPowerBracerSession.remove(p);ElectromasterChargeDecoy.remove(p);PolarityTether.remove(p);ConductiveCircuit.remove(p);GroundingReturn.remove(p);MagneticGroundStep.remove(p);cn.academy.port.skill.MagMovement.remove(p);DevelopmentController.remove(p);AbilityStorage.remove(p);h.getLevel().getServer().getPlayerList().remove(p);}entities.forEach(Entity::discard);channels.forEach(EmbeddedChannel::finishAndReleaseAll);}
    }
}
