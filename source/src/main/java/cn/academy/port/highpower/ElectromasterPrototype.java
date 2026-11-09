package cn.academy.port.highpower;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.highpower.core.HighPowerLevel;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import org.joml.Vector3f;
import java.math.BigDecimal;
import java.util.Comparator;

/** Bounded, opt-in vertical slice. Commands explicitly mark test setup, never survival completion. */
@EventBusSubscriber(modid="academy")
public final class ElectromasterPrototype {
    public static final DeferredItem<Item> CONTROL=AcademyCraft.ITEMS.register("electromaster_prototype",()->new ElectromasterPrototypeItem(new Item.Properties().stacksTo(1)));
    private static final String KEY=HighPowerResources.DATA_KEY;
    private static final String BRACER_LEASE="academy:highpower_bracer_steering_until";
    private static final int BRACER_LEASE_TICKS=20;
    private static final TagKey<EntityType<?>> METAL=TagKey.create(Registries.ENTITY_TYPE,ResourceLocation.fromNamespaceAndPath("academy","magnetic_projectiles"));
    private static final DustParticleOptions BLUE=new DustParticleOptions(new Vector3f(.4f,.75f,1),.5f);
    private ElectromasterPrototype() {}
    public static void register() { /* Forces deferred item registration before ITEMS.register(bus). */ }
    /** The control item takes precedence over villagers/chests so close targets cannot swallow casts. */
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void entityInput(PlayerInteractEvent.EntityInteractSpecific event) {
        if(!event.isCanceled()&&controlInput(event.getEntity(),event.getHand())){event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void generalEntityInput(PlayerInteractEvent.EntityInteract event) {
        if(!event.isCanceled()&&controlInput(event.getEntity(),event.getHand())){event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void blockInput(PlayerInteractEvent.RightClickBlock event) {
        if(!event.isCanceled()&&controlInput(event.getEntity(),event.getHand())){event.setCancellationResult(InteractionResult.SUCCESS);event.setCanceled(true);}
    }
    private static boolean controlInput(Player player,InteractionHand hand) {
        if(hand!=InteractionHand.MAIN_HAND||!player.getItemInHand(hand).is(CONTROL.get()))return false;
        if(player instanceof ServerPlayer server) {
            if(ready(server)){if(player.isShiftKeyDown())player.startUsingItem(hand);else cast(server);}
        } else if(player.isShiftKeyDown())player.startUsingItem(hand);
        return true;
    }
    /** Compatibility wrappers remain EM-only; shared category accounting lives in HighPowerResources. */
    public static boolean ready(ServerPlayer p) {
        return p!=null&&HighPowerCategories.isElectromaster(AbilityStorage.get(p).category)&&HighPowerResources.ready(p);
    }
    public static int effectiveLevel(ServerPlayer p) {
        return p!=null&&HighPowerCategories.isElectromaster(AbilityStorage.get(p).category)?HighPowerResources.effectiveLevel(p):0;
    }
    /** One initial budget at genuine enrollment. Relearning, rebinding and promotion never call a refill. */
    public static void initializeBudget(ServerPlayer p) {
        if(effectiveLevel(p)>0)HighPowerResources.initializeBudget(p);
    }
    public static double prototypeCp(ServerPlayer p) {return effectiveLevel(p)>0?HighPowerResources.cp(p):0;}
    public static boolean castLearned(ServerPlayer p) {
        return ready(p)&&HighPowerSkills.eligible(AbilityStorage.get(p),HighPowerSkills.FORKED_ARC)&&cast(p);
    }
    /** Existing EM controllers retain their closed category gate and persistent cooldowns. */
    public static boolean trySpend(ServerPlayer p,String skill,double cpCost,int cooldownTicks) {
        return ready(p)&&!refuseUnsupported(p,p)&&HighPowerResources.trySpend(p,skill,cpCost,cooldownTicks);
    }
    public static boolean cast(ServerPlayer p) {
        if(!ready(p)||HighPowerResources.hasReservation(p))return false;
        if(refuseUnsupported(p,p))return false;
        int level=effectiveLevel(p);var tag=p.getPersistentData().getCompound(KEY);long now=p.serverLevel().getGameTime();
        var growth=HighPowerGrowth.values(p);if(now<tag.getLong("next_cast")||HighPowerResources.availableCp(p)<growth.arcCost())return false;
        var start=p.getEyePosition();var end=start.add(p.getLookAngle().scale(SkillTuning.number("forked_arc", "reach")));
        var block=p.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(block.getType()!=HitResult.Type.MISS)end=block.getLocation();
        LivingEntity primary=null;Vec3 impact=end;double nearest=start.distanceToSqr(end);
        for(var candidate:p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(start,end).inflate(.3),e->e!=p&&e.isAlive()&&!e.isSpectator())) {
            var hit=candidate.getBoundingBox().inflate(.12).clip(start,end);
            if(hit.isPresent()&&start.distanceToSqr(hit.get())<nearest){nearest=start.distanceToSqr(hit.get());primary=candidate;impact=hit.get();}
        }
        if(primary!=null&&refuseUnsupported(p,primary))return false;
        if(!HighPowerResources.trySpendPrototypeArc(p,growth.arcCost(),growth.arcCooldown()))return false;
        HighPowerNaturalRecovery.offensiveAction(p);
        arc(p,start,impact);
        var progress=AbilityStorage.get(p);
        if(progress.learned(HighPowerSkills.FORKED_ARC))progress.setCooldown(HighPowerSkills.FORKED_ARC,growth.arcCooldown());
        if(primary==null){if(block.getType()!=HitResult.Type.MISS)award(p,HighPowerSkills.FORKED_ARC,.0018);return true;}
        var amount=growth.damage();
        var conductiveReceipt=ConductiveMarkReceipts.capture(p,primary);
        if(!HighPowerCombat.attack(p,"electromaster.forked_arc",primary,amount))return true;
        ConductiveMarkReceipts.acceptedArc(conductiveReceipt);
        award(p,HighPowerSkills.FORKED_ARC,.005);
        primary.getPersistentData().putLong("academy:conductive_until",now+100);
        if(p.isShiftKeyDown())return true;
        final LivingEntity root=primary;var from=impact;
        var branches=p.serverLevel().getEntitiesOfClass(LivingEntity.class,primary.getBoundingBox().inflate(SkillTuning.number("forked_arc", "branch_radius")),e->e!=p&&e!=root&&e.isAlive()&&!e.isSpectator()&&conductive(e,now));
        branches.sort(Comparator.comparingDouble(root::distanceToSqr));int accepted=0;
        for(var target:branches) {
            if(accepted>=SkillTuning.integer("forked_arc", "max_branches"))break;Vec3 to=target.getBoundingBox().getCenter();
            if(p.serverLevel().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()!=HitResult.Type.MISS)continue;
            if(HighPowerCombat.attack(p,"electromaster.forked_arc",target,amount.multiply(new BigDecimal("0.35")))){arc(p,from,to);accepted++;}
        }
        return true;
    }
    private static void award(ServerPlayer p,String skill,double amount) {
        var state=AbilityStorage.get(p);
        if(state.category.equals(HighPowerSkills.CATEGORY)&&state.learned(skill)){state.addExperience(skill,amount);AbilityStorage.save(p);}
    }
    private static boolean refuseUnsupported(ServerPlayer player,LivingEntity target) {
        var reason=HighPowerCombat.unsupportedReason(target);
        if(reason.isEmpty())return false;
        var notice=player.getPersistentData().getCompound(KEY);long now=player.serverLevel().getGameTime();
        if(!reason.get().equals(notice.getString("lastUnsupportedReason"))||!notice.contains("lastUnsupportedNotice")||now-notice.getLong("lastUnsupportedNotice")>=40){
            player.displayClientMessage(Component.literal("ACA prototype unavailable: "+reason.get()),false);
            notice.putString("lastUnsupportedReason",reason.get());notice.putLong("lastUnsupportedNotice",now);
            player.getPersistentData().put(KEY,notice);
        }
        return true;
    }
    private static boolean conductive(LivingEntity e,long now) {return e.isInWaterOrRain()||e.getPersistentData().getLong("academy:conductive_until")>now;}
    private static void arc(ServerPlayer p,Vec3 start,Vec3 end) {
        var delta=end.subtract(start);if(delta.lengthSqr()<=1e-8)return;
        var direction=delta.normalize();double length=Math.min(SkillTuning.number("forked_arc", "reach"),delta.length());
        // The immutable classic WeakArc renderer caps one event at 20 blocks.
        // Split this new 24-block skill instead of silently hiding its final four blocks
        // or changing legacy geometry. At most two primary segments, plus two branches.
        for(double offset=0;offset<length;offset+=20)
            AcademyNetwork.effect(p,"arc",start.add(direction.scale(offset)),direction,Math.min(20,length-offset));
    }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        boolean resourceTick=HighPowerResources.tick(p);
        if(!ready(p)){HighPowerBracerSession.abort(p);HighPowerCombat.shieldActive(p,false);return;}
        if(!resourceTick)return;
        int level=effectiveLevel(p);var growth=HighPowerGrowth.values(p);long now=p.serverLevel().getGameTime();var tag=p.getPersistentData().getCompound(KEY);
        boolean defending=(p.isUsingItem()&&p.getUseItem().is(CONTROL.get())&&p.isShiftKeyDown())||HighPowerBracerSession.active(p);
        if(defending&&refuseUnsupported(p,p))defending=false;
        HighPowerCombat.shieldActive(p,defending);HighPowerCombat.tickShield(p);
        if(defending&&HighPowerResources.trySpendPrototypeDefense(p,growth.arcCost()/20)) {
            var eye=p.getEyePosition();var look=p.getLookAngle();int inspected=0;AbstractArrow chosen=null;double nearest=Double.MAX_VALUE;
            // Exclude stuck, receding and rear arrows before spending the threat budget.
            // Otherwise old arrows could starve a genuine incoming projectile forever.
            for(var arrow:p.serverLevel().getEntitiesOfClass(AbstractArrow.class,p.getBoundingBox().inflate(SkillTuning.number("magnetic_bracer", "radius")),a->eligibleArrow(a,p,eye,look))) {
                if(inspected++>=16)break;double distance=arrow.position().distanceToSqr(eye);
                if(distance<nearest){nearest=distance;chosen=arrow;}
            }
            if(chosen!=null&&HighPowerCombat.spendShield(p,growth.damage().multiply(new BigDecimal("0.1")))) {
                var to=chosen.position().subtract(eye);var velocity=chosen.getDeltaMovement();
                var away=to.normalize().add(look).normalize().scale(Math.min(3,Math.max(.35,velocity.length())));
                chosen.setDeltaMovement(away);chosen.hasImpulse=true;chosen.hurtMarked=true;
                chosen.getPersistentData().putLong(BRACER_LEASE,p.server.overworld().getGameTime()+BRACER_LEASE_TICKS);
                award(p,HighPowerSkills.MAGNETIC_BRACER,.005);
                arc(p,eye.add(look.scale(.45)).add(0,-.4,0),chosen.position());
            }
            // Two short wrist-local marks, never a sphere or opaque screen overlay.
            if(now%4==0){var wrist=eye.add(look.scale(.45)).add(0,-.4,0);p.serverLevel().sendParticles(BLUE,wrist.x,wrist.y,wrist.z,2,.07,.025,.07,0);}
        }
        if(now%10==0) {
            var numbers=HighPowerRecoveryHudModel.hudNumberLocale(p.clientInformation().language());
            String hp=HighPowerRecoveryHudModel.compact(HighPowerCombat.healthText(p),numbers);
            String shield=HighPowerRecoveryHudModel.compact(HighPowerCombat.shield(p).stripTrailingZeros().toPlainString(),numbers);
            String cp=HighPowerRecoveryHudModel.compact(BigDecimal.valueOf(tag.getDouble("cp")).stripTrailingZeros().toPlainString(),numbers);
            String percent=growth.position().subtract(BigDecimal.valueOf(level)).max(BigDecimal.ZERO).multiply(BigDecimal.valueOf(100)).setScale(2,java.math.RoundingMode.DOWN).stripTrailingZeros().toPlainString();
            // Full compact fallback is always transmitted. Only an actually rendered client snapshot may omit duplicate pools.
            p.displayClientMessage(Component.translatableWithFallback("hud.academy.highpower.summary.v1",
                    "EM LV%s · %s%% | HP %s | S %s | CP %s", level,percent,hp,shield,cp),true);
        }
    }
    /** Shared native-arrow steering lease; does not affect ordinary projectile motion or hits. */
    public static boolean leased(AbstractArrow arrow) {
        if(arrow==null||!(arrow.level() instanceof net.minecraft.server.level.ServerLevel world)||!world.getServer().isSameThread())return true;
        long now=world.getServer().overworld().getGameTime();
        if(now<0||now>Long.MAX_VALUE-BRACER_LEASE_TICKS)return true;
        var data=arrow.getPersistentData();if(!data.contains(BRACER_LEASE))return false;
        if(!data.contains(BRACER_LEASE,net.minecraft.nbt.Tag.TAG_LONG))return true;
        long until=data.getLong(BRACER_LEASE);
        return until<0||until>now&&until-now>BRACER_LEASE_TICKS||now<until;
    }
    private static double bracerRadiusSquared() {
        double radius = SkillTuning.number("magnetic_bracer", "radius");
        return radius * radius;
    }
    private static boolean eligibleArrow(AbstractArrow arrow,ServerPlayer player,Vec3 eye,Vec3 look) {
        if(leased(arrow)||MultiAxisReflectionArrowBridge.leased(arrow) || TrajectoryReturnArrowBridge.leased(arrow) || ShortInterceptionArrowBridge.leased(arrow)||IronSandLance.leased(arrow)||MagneticArrowSeparation.leased(arrow)||MagneticDomainArrows.leased(arrow)||MagneticGroundStep.leased(arrow)||ElectromasterChargeDecoy.leased(arrow)||arrow.inGround||!arrow.getType().is(METAL)||arrow.getOwner()==player)return false;
        var to=arrow.position().subtract(eye);var velocity=arrow.getDeltaMovement();double distance=to.lengthSqr();
        return distance>1e-8&&distance<=bracerRadiusSquared()&&to.normalize().dot(look)>=.5&&velocity.lengthSqr()>=.001&&velocity.dot(to)<0;
    }
    private static int approveContract(net.minecraft.commands.CommandSourceStack source,LivingEntity target) {
        if(!HighPowerCombat.enabled(target)){source.sendFailure(Component.literal("Only an existing enrolled ledger can be approved."));return 0;}
        if(!HighPowerCombat.needsContractApproval(target)){source.sendFailure(Component.literal("This ledger already uses the reviewed vanilla-only contract."));return 0;}
        if(!HighPowerCombat.approveVanillaContract(target)){
            source.sendFailure(Component.literal("Vanilla-only contract refused: unsupported environment or entity state. Approval cannot override compatibility checks."));return 0;
        }
        source.sendSuccess(()->Component.literal("Reviewed vanilla-only damage contract approved. Exact HP and shield are unchanged; third-party hooks and unsupported enchantments remain disabled."),false);
        return 1;
    }
    private static int growthStatus(net.minecraft.commands.CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p=source.getPlayerOrException();
        if(!HighPowerCombat.enabled(p)){source.sendFailure(Component.literal("No enrolled high-power ledger."));return 0;}
        var q=HighPowerCombat.earnedGrowthPosition(p);
        if(!HighPowerCategories.active(AbilityStorage.get(p))){
            source.sendSuccess(()->Component.literal("High-power category inactive; retained achieved HP capacity and injury: "+HighPowerCombat.healthText(p)+" / "+HighPowerCombat.maxHealthText(p)+"; earned growth "+q),false);return 1;
        }
        var g=HighPowerGrowth.values(p);
        source.sendSuccess(()->Component.literal("Effective q="+g.position()+", earned capacity Q="+g.earnedPosition()+", native "+AbilityStorage.get(p).category+" LV"+AbilityStorage.get(p).level+" progress="+AbilityStorage.get(p).levelProgress(cn.academy.port.SkillCatalog.levelSkillCount(AbilityStorage.get(p)))+", damage="+g.damage().toPlainString()+", HP="+HighPowerCombat.healthText(p)+" / "+HighPowerCombat.maxHealthText(p)+", shield="+HighPowerCombat.shieldText(p)+", prototype CP="+HighPowerResources.cp(p)+" / "+g.cpMaximum()+", arc CP="+g.arcCost()+", arc cooldown="+g.arcCooldown()+" native ticks. Category switches rebase observation without refill; earned capacity growth never heals. Paid levels preserve q; native LV5 can still need genuine XP to finish q=5."),false);return 1;
    }
    private static int resetGrowth(net.minecraft.commands.CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var p=source.getPlayerOrException();var state=AbilityStorage.get(p);
        if(!HighPowerCombat.enabled(p)||!HighPowerCategories.active(state))return 0;
        var q=cn.academy.port.highpower.core.ContinuousHighPowerGrowth.position(state.level,state.levelProgress(cn.academy.port.SkillCatalog.levelSkillCount(state)));
        double stored=p.getPersistentData().getCompound(KEY).getDouble("cp");
        if(!Double.isFinite(stored)||stored<0||stored>HighPowerGrowth.cpMaximumAt(q)||!HighPowerCombat.resetGrowthContract(p)){
            source.sendFailure(Component.literal("Reset refused: existing HP/shield/CP does not fit the requested capacity, or the contract is busy/unsupported. No balance was changed."));return 0;
        }
        cn.academy.port.AcademyGameplay.cancelForAdministrativeMutation(p);HighPowerGrowth.invalidate(p);
        source.sendSuccess(()->Component.literal("ADMIN growth contract explicitly rebaselined. Current HP/shield/CP were preserved exactly; no refill. This is a test/admin override, not earned progression."),false);return 1;
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("academyhp").requires(s->s.hasPermission(2))
            .then(Commands.literal("growth").executes(c->growthStatus(c.getSource())))
            .then(Commands.literal("reset_growth").executes(c->{c.getSource().sendFailure(Component.literal("ADMIN WARNING: rebaseline the earned-capacity contract to current level/progress. No healing/refill; refuses if current balances exceed the new capacity. Use /academyhp reset_growth confirm only for an intentional admin reset."));return 0;})
                    .then(Commands.literal("confirm").executes(c->resetGrowth(c.getSource()))))
            .then(Commands.literal("approve_self").executes(c->approveContract(c.getSource(),c.getSource().getPlayerOrException())))
            .then(Commands.literal("approve").executes(c->{
                var p=c.getSource().getPlayerOrException();var from=p.getEyePosition();var to=from.add(p.getLookAngle().scale(24));
                var target=p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(1),e->e!=p&&e.getBoundingBox().clip(from,to).isPresent()).stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
                if(target==null){c.getSource().sendFailure(Component.literal("Aim at an enrolled target."));return 0;}
                return approveContract(c.getSource(),target);
            }))
            .then(Commands.literal("inspect").executes(c->{
                var p=c.getSource().getPlayerOrException();var from=p.getEyePosition();var to=from.add(p.getLookAngle().scale(24));
                var target=p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(1),e->e!=p&&e.getBoundingBox().clip(from,to).isPresent()).stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
                if(target==null||!HighPowerCombat.enabled(target)){c.getSource().sendFailure(Component.literal("Aim at an enrolled target."));return 0;}
                c.getSource().sendSuccess(()->Component.literal("Exact target HP: "+HighPowerCombat.healthText(target)+" / "+HighPowerCombat.maxHealthText(target)+"; shield: "+HighPowerCombat.shieldText(target)),false);return 1;
            }))
            .then(Commands.literal("equip").then(Commands.argument("level",IntegerArgumentType.integer(1,5)).executes(c->{
                var p=c.getSource().getPlayerOrException();int level=IntegerArgumentType.getInteger(c,"level");
                if(HighPowerCombat.enabled(p)){c.getSource().sendFailure(Component.literal("Already enrolled: equip cannot refill HP or shield. Use a fresh test player/world for another preset."));return 0;}
                var reason=HighPowerCombat.unsupportedReason(p);
                if(reason.isPresent()){c.getSource().sendFailure(Component.literal(reason.get()));return 0;}
                if(!HighPowerCombat.enroll(p,level))return 0;cn.academy.port.AcademyGameplay.cancelForAdministrativeMutation(p);var s=AbilityStorage.get(p);s.changeCategoryClassic("electromaster");s.setLevel(level);s.learn("arc_gen");AbilityStorage.save(p);AcademyNetwork.sync(p);
                var t=p.getPersistentData().getCompound(KEY);t.putDouble("cp",HighPowerGrowth.values(p).cpMaximum());p.getPersistentData().put(KEY,t);p.getInventory().add(new ItemStack(CONTROL.get()));
                c.getSource().sendSuccess(()->Component.literal("Prototype fixture enabled. Right-click: forked arc. Sneak + hold right-click: front magnetic bracer. New world only; no survival acceptance."),false);return 1;
            })))
            .then(Commands.literal("target").then(Commands.argument("level",IntegerArgumentType.integer(1,5)).executes(c->{
                var p=c.getSource().getPlayerOrException();var from=p.getEyePosition();var to=from.add(p.getLookAngle().scale(24));
                var target=p.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(1),e->e!=p&&e.getBoundingBox().clip(from,to).isPresent()).stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
                if(target==null||HighPowerCombat.enabled(target))return 0;
                var reason=HighPowerCombat.unsupportedReason(target);
                if(reason.isPresent()){c.getSource().sendFailure(Component.literal(reason.get()));return 0;}
                if(!HighPowerCombat.enroll(target,IntegerArgumentType.getInteger(c,"level"))){c.getSource().sendFailure(Component.literal("Unsupported target: bosses and third-party entities need a tested adapter."));return 0;}target.getPersistentData().putLong("academy:conductive_until",p.serverLevel().getGameTime()+1200);
                c.getSource().sendSuccess(()->Component.literal("Target ledger enrolled: "+HighPowerCombat.healthText(target)),false);return 1;
            }))));
    }
}
