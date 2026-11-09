package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.client.AcademyClient;
import cn.academy.port.client.CastPoseClient;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CastPose;
import cn.academy.port.highpower.core.CastCameraTimeline;
import cn.academy.port.client.CastSignatureDecor;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/** QA-only setup and read-only assertions. Casts always enter through the normal scripted GLFW keys;
 * this fixture never calls a skill start/release, a visual receiver, or CastPoseClient.force. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
final class LiveInputQa {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean FINAL_VISUAL = Boolean.getBoolean("academy.qa.finalVisual");
    private static final class FinalVisual {
        final String skill,category; final int level,serial;
        boolean firstPerson,actionRequested,releaseRequested; volatile boolean actionSaved,releaseSaved;
        FinalVisual(SkillCatalog.Skill entry,int serial){skill=entry.id();category=entry.category();level=entry.level();this.serial=serial;}
    }
    private record Capture(FinalVisual witness,boolean release,double age,double sinceRelease,cn.academy.port.highpower.core.CastPose.Archetype archetype){}
    private static FinalVisual finalVisual;
    private static Capture pendingCapture;
    private static int visualSerial;
    private static String skill = "";
    private static int hands, arms, signatureFrames, signatureQuads, cameraFrames;
    private static double beforeFov;
    private static Object fovEvent;
    private static Matrix4f beforeHand;
    private static Zombie target;
    private static BigDecimal targetHp;
    private static Vec3 setupPosition;
    private static java.util.UUID tracedBody;
    private static volatile String failure;
    private record LearningProbe(java.util.UUID owner,String category,String id,cn.academy.port.develop.DeveloperType type,double before,double expected,net.minecraft.world.item.ItemStack stack,cn.academy.port.machine.MachineDeveloperBlockEntity machine){}
    private static LearningProbe learningProbe;
    private record StormProbe(java.util.UUID owner,Object state,long began,double cp,double maxCp){}
    private static StormProbe stormProbe;
    private static volatile boolean motionOptIn;
    private static Boolean priorAutoJump;
    private static java.util.UUID tossedItem;
    private static Vec3 tossedPosition;
    private static int ironBeforePull;
    private static Object windowOwnerBefore, windowWorldBefore;
    private record ShieldProbe(java.util.UUID owner,String category,Object world,BigDecimal health,BigDecimal shield,long tick,BigDecimal paidShield){}
    private static ShieldProbe shieldProbe;



    static void assertHealthy() {
        GameplayAcceptanceGroundStep.assertHealthy();
        if (failure != null) throw new IllegalStateException("QA_LIVE_FAILED " + failure);
    }

    static void run(Minecraft mc, String arg) {
        assertHealthy();
        if(arg.startsWith("ground:")){ GameplayAcceptanceGroundStep.run(mc,arg.substring(7));
        } else if(arg.startsWith("prepare-learning:")||arg.startsWith("prepare-machine-learning:")||arg.startsWith("prepare-advanced-machine-learning:")||arg.startsWith("learned:")){
            boolean advancedLearning=arg.startsWith("prepare-advanced-machine-learning:");
            boolean machineLearning=advancedLearning||arg.startsWith("prepare-machine-learning:");
            boolean prepare=arg.startsWith("prepare-learning:")||machineLearning;
            // A category-qualified selector disambiguates the four genuine course records.
            // Existing unqualified portable/normal fixtures keep their original lookup semantics.
            String selector=arg.substring(arg.indexOf(':')+1);int slash=selector.indexOf('/');
            String category=slash<0?null:selector.substring(0,slash),id=slash<0?selector:selector.substring(slash+1);
            var server=mc.getSingleplayerServer();if(server==null)throw new IllegalStateException("integrated server required");
            var owner=mc.player.getUUID();
            server.execute(()->{
                try{
                    var p=server.getPlayerList().getPlayer(owner);
                    if(prepare){
                        var entry=SkillCatalog.REGISTERED.stream().filter(e->e.id().equals(id)&&(category==null||e.category().equals(category))).findFirst().orElseThrow();
                        var state=AbilityStorage.get(p);
                        if(!state.category.equals(entry.category()))state.selectCategory(entry.category());
                        if(state.level<entry.level())state.setLevel(entry.level());
                        state.activated=true;state.overloadFine=true;state.cooldowns.remove(id);
                        preparePrerequisites(state,entry,new java.util.HashSet<>());state.unlearn(id);
                        cn.academy.port.machine.MachineDeveloperSessions.close(p);
                        cn.academy.port.machine.MachineDeveloperBlockEntity machine=null;
                        var type=advancedLearning?cn.academy.port.develop.DeveloperType.ADVANCED:machineLearning?cn.academy.port.develop.DeveloperType.NORMAL:cn.academy.port.develop.DeveloperType.PORTABLE;
                        var stack=machineLearning?net.minecraft.world.item.ItemStack.EMPTY:new net.minecraft.world.item.ItemStack(AcademyCraft.DEVELOPER.get());
                        double before;
                        if(machineLearning){
                            var origin=p.blockPosition().offset(2,0,0);var block=(advancedLearning?cn.academy.port.machine.MachineDevelopers.ADVANCED:cn.academy.port.machine.MachineDevelopers.NORMAL).get();
                            var facing=net.minecraft.core.Direction.NORTH;
                            for(int part=0;part<8;part++)p.serverLevel().setBlock(origin.offset(cn.academy.port.machine.MachineDeveloperBlock.offset(part,facing)),
                                block.defaultBlockState().setValue(cn.academy.port.machine.MachineDeveloperBlock.FACING,facing).setValue(cn.academy.port.machine.MachineDeveloperBlock.PART,part),3);
                            machine=(cn.academy.port.machine.MachineDeveloperBlockEntity)p.serverLevel().getBlockEntity(origin);
                            require(machine!=null&&machine.available()&&machine.developerType()==type,"actual complete eight-cell developer of the selected native tier required");
                            machine.battery().energy(type.energy);before=machine.battery().getEnergy();
                            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,stack);
                        }else{
                            var energy=new cn.academy.port.develop.DeveloperItemEnergy(stack,type);energy.energy(type.energy);before=energy.energy();
                            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,stack);
                        }
                        p.inventoryMenu.broadcastChanges();
                        double expected=type.actualConsumption(cn.academy.port.core.ClassicRules.learningStimulations(entry.level()));
                        require(!state.learned(id),"tested record remains unlearned after prerequisite-only fixture preparation");
                        learningProbe=new LearningProbe(owner,entry.category(),id,type,before,expected,stack,machine);
                        AbilityStorage.save(p);AcademyNetwork.sync(p);
                        boolean eligible=cn.academy.port.develop.DevelopmentActions.canLearn(state,type,entry);
                        if(machineLearning)require(eligible,"actual selected developer tier meets real learning conditions");
                        if(machineLearning)require(machine.use(p),"real reachable machine session opens the actual developer screen");
                        LOG.info("QA_LEARNING_PREPARED id={} category={} learned={} developerType={} beforeIF={} nativeCostIF={} eligible={} realMachine={}",id,entry.category(),state.learned(id),type,before,expected,eligible,machineLearning);
                    }else{
                        var probe=learningProbe;require(probe!=null&&probe.owner().equals(owner)&&probe.id().equals(id)&&(category==null||probe.category().equals(category)),"matching category/record finite developer fixture required");
                        require(AbilityStorage.get(p).category.equals(probe.category())&&AbilityStorage.get(p).learned(id),"real server development must actually learn selected skill");
                        double after;
                        if(probe.machine()!=null){
                            require(probe.machine().available()&&probe.machine().developerType()==probe.type()&&probe.type()!=cn.academy.port.develop.DeveloperType.PORTABLE,"same real machine and native developer tier own finite IF debit");
                            after=probe.machine().battery().getEnergy();
                        }else{
                            require(probe.type()==cn.academy.port.develop.DeveloperType.PORTABLE&&p.getMainHandItem()==probe.stack()&&p.getMainHandItem().is(AcademyCraft.DEVELOPER.get()),"same real portable developer stack and tier own the finite IF debit");
                            after=new cn.academy.port.develop.DeveloperItemEnergy(p.getMainHandItem(),probe.type()).energy();
                        }
                        require(Math.abs(probe.before()-after-probe.expected())<1e-6,"actual finite IF decrease equals production native stimulation cost");
                        LOG.info("QA_LEARNING_PASS id={} beforeIF={} afterIF={} actualSpentIF={} nativeCostIF={} learned=true viaRealConfirm=true category={} developerType={}",id,probe.before(),after,probe.before()-after,probe.expected(),probe.category(),probe.type());
                    }
                }catch(RuntimeException e){failure=e.toString();}
            });
        } else if (arg.startsWith("setup:")) {
            finishFinalVisual("next_setup");
            skill = arg.substring(6); hands = arms = signatureFrames = signatureQuads = cameraFrames = 0;
            var entry = SkillCatalog.REGISTERED.stream().filter(e->e.id().equals(skill)).findFirst().orElseThrow();
            if (FINAL_VISUAL) finalVisual = new FinalVisual(entry,++visualSerial);
            var server = mc.getSingleplayerServer();
            if (server == null) throw new IllegalStateException("integrated server required");
            var id = mc.player.getUUID();
            server.execute(() -> {
                try {
                    var p = server.getPlayerList().getPlayer(id);
                    if (target != null) { target.discard(); target = null; }
                    p.setGameMode(GameType.SURVIVAL);
                    setupPosition = p.position();
                    p.getInventory().clearContent();
                    if (entry.id().equals("railgun")||entry.id().equals("magnetic_item_pull")) p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                            new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, entry.id().equals("magnetic_item_pull")?1:16));
                    if (entry.id().equals(IronSandLance.ID)) p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
                            new net.minecraft.world.item.ItemStack(IronSandMedium.ITEM.get(), 32));
                    p.inventoryMenu.broadcastChanges();
                    var s = AbilityStorage.get(p); s.selectCategory(entry.category()); s.setLevel(5);
                    // A fresh QA enrollment after category selection witnesses actual LV5 output, not just unlock level.
                    p.getPersistentData().remove(HighPowerCombat.DATA_KEY);
                    if(!HighPowerCombat.enroll(p,5)) {
                        LOG.error("QA_LIVE_SETUP_FAILED skill={} isolation={}",entry.id(),isolation(p));
                        throw new IllegalStateException("enrollment refused skill="+entry.id()+" "+isolation(p));
                    }
                    for (var e : SkillCatalog.REGISTERED) if (e.category().equals(entry.category())) s.learn(e.id());
                    s.activated = true; s.overloadFine = true; s.cooldowns.clear();
                    s.presets.switchTo(0);
                    for (int slot = 0; slot < 4; slot++) s.presets.edit(0, slot, slot == 2 ? entry.id() : "", s::learned);
                    HighPowerResources.initializeBudget(p);
                    AbilityStorage.save(p); AcademyNetwork.sync(p);
                    LOG.info("QA_LIVE_SETUP skill={} category={} cp={}", entry.id(), entry.category(), HighPowerResources.cp(p));
                } catch (RuntimeException e) { failure = e.toString(); }
            });
        } else if (arg.startsWith("slot:")) {
            // Rebinds the cast key (slot 2) to another learned skill of the same category without resetting shells, bodies or budget.
            String id = arg.substring(5); skill = id;
            var server = mc.getSingleplayerServer(); var owner = mc.player.getUUID(); require(server != null, "integrated server required");
            server.execute(() -> {
                try {
                    var p = server.getPlayerList().getPlayer(owner); var s = AbilityStorage.get(p);
                    s.presets.edit(0, 2, id, s::learned); s.cooldowns.clear(); AbilityStorage.save(p); AcademyNetwork.sync(p);
                    LOG.info("QA_LIVE_SLOT skill={}", id);
                } catch (RuntimeException e) { failure = e.toString(); }
            });
        } else if (arg.equals("isolation")) {
            var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();require(server!=null,"integrated server required");
            server.execute(()->{
                try{LOG.info("QA_LIVE_ISOLATION skill={} {}",skill,isolation(server.getPlayerList().getPlayer(owner)));}
                catch(RuntimeException e){failure=e.toString();}
            });
        } else if (arg.equals("motion-preflight")) {
            LOG.info("QA_LIVE_MOTION_PREFLIGHT skill={} enabled={} groundBlocker={} beamBlocker={} ground={} sprint={} forward={} jump={} autoJump={} pos={} velocity={}",
                skill,SkillCatalog.enabled(AcademyClient.state.category,skill),cn.academy.port.client.MagneticGroundStepClientMotion.startBlocker(),
                cn.academy.port.client.BeamStepClientMotion.startBlocker(),mc.player.onGround(),mc.player.isSprinting(),mc.player.input.forwardImpulse,
                mc.player.input.jumping,mc.options.autoJump().get(),mc.player.position(),mc.player.getDeltaMovement());
        } else if (arg.startsWith("native-feature:")) {
            boolean optIn=arg.equals("native-feature:optin");
            require(optIn||arg.equals("native-feature:restore"),"known test feature action");
            require(Boolean.getBoolean("academy.qa.nativeFeatureGate"),"explicit academy.qa.nativeFeatureGate=true required");
            if(optIn){require(!motionOptIn,"one scoped opt-in");priorAutoJump=mc.options.autoJump().get();mc.options.autoJump().set(false);}
            else if(priorAutoJump!=null){mc.options.autoJump().set(priorAutoJump);priorAutoJump=null;}
            var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();require(server!=null,"integrated server required");
            server.execute(()->{
                try{
                    if(optIn){cn.academy.port.gametest.HighPowerFeatureGateRuntimeTests.candidateMotionOptIn(server.overworld());motionOptIn=true;}
                    else restoreMotionOptIn(server);
                    var p=server.getPlayerList().getPlayer(owner);AcademyNetwork.sync(p);
                    LOG.info("QA_LIVE_FEATURE_OPTIN property=true optedIn={} enabled={} productionDefault={} reusedNativeFeatureGateFixture=true",motionOptIn,SkillCatalog.enabled("electromaster","magnetic_ground_step"),AcademyConfig.defaultSkillEnabled(cn.academy.port.core.ClassicSkillConfiguration.key("electromaster","magnetic_ground_step")));
                }catch(RuntimeException e){failure=e.toString();}
            });
        } else if (arg.equals("item-dropped")||arg.equals("item-pulled")) {
            var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();require(server!=null,"integrated server required");
            server.execute(()->{
                try{
                    var p=server.getPlayerList().getPlayer(owner);
                    int iron=p.getInventory().countItem(net.minecraft.world.item.Items.IRON_INGOT);
                    if(arg.equals("item-dropped")){
                        var items=p.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(20),e->e.tickCount<100&&e.getItem().is(net.minecraft.world.item.Items.IRON_INGOT)&&MagneticItemProvenance.ownedBy(e,owner));
                        require(items.size()==1,"one actual owner-proven native Q toss required");var item=items.get(0);
                        tossedItem=item.getUUID();tossedPosition=item.position();ironBeforePull=iron;
                        LOG.info("QA_LIVE_ITEM_TOSSED item={} pos={} ownerProven=true viaRealQ=true inventoryIron={}",tossedItem,tossedPosition,iron);
                    }else{
                        require(tossedItem!=null,"actual toss baseline");var item=p.serverLevel().getEntity(tossedItem);
                        require(iron>ironBeforePull||item!=null&&item.position().distanceToSqr(p.position())+.25<tossedPosition.distanceToSqr(p.position()),"actual selected item moved toward owner or was picked up");
                        LOG.info("QA_LIVE_ITEM_PULL_PASS item={} before={} after={} inventoryIronBefore={} inventoryIron={} actualServerItem=true",tossedItem,tossedPosition,item==null?"picked_up":item.position(),ironBeforePull,iron);
                    }
                }catch(RuntimeException e){failure=e.toString();}
            });
        } else if (arg.startsWith("gui:")) {
            require(mc.screen!=null,"GUI operation requires a real open skill screen");
            String op=arg.substring(4);
            if(op.equals("item-pull-drag")) {
                require(cn.academy.port.client.MagneticItemPullSelection.ownScreen(),"real held-slot item selection screen");
                var screen=mc.screen;
                @SuppressWarnings("unchecked") var points=(java.util.List<cn.academy.port.client.MagneticItemPullSelectionGeometry.Point>)read(screen,"points");
                var point=points.stream().filter(p->p.id().equals(tossedItem)).findFirst().orElseThrow(()->new IllegalStateException("real owned item must be projected before dragging"));
                double cx=screen.width/2.0,cy=screen.height/2.0;
                double tx=point.x()+(point.x()>=cx?10:-10),ty=point.y()+(point.y()>=cy?10:-10);
                var rect=cn.academy.port.client.MagneticItemPullSelectionGeometry.rectangle(screen.width,screen.height,tx,ty);
                require(rect.contains(point.x(),point.y()),"visible candidate fits real bounded drag rectangle");
                double rawX=tx*mc.getWindow().getScreenWidth()/screen.width,rawY=ty*mc.getWindow().getScreenHeight()/screen.height;
                // Hidden QA windows cannot receive an OS cursor warp. Deliver the normal native mouse callback,
                // so the next ordinary Screen.render sees the same cursor as the press/drag/release handlers.
                try{
                    var move=mc.mouseHandler.getClass().getDeclaredMethod("onMove",long.class,double.class,double.class);move.setAccessible(true);
                    move.invoke(mc.mouseHandler,mc.getWindow().getWindow(),rawX,rawY);
                }catch(ReflectiveOperationException e){throw new IllegalStateException("native mouse-move callback unavailable",e);}
                require(Math.abs(mc.mouseHandler.xpos()-rawX)<.01&&Math.abs(mc.mouseHandler.ypos()-rawY)<.01,"native mouse callback owns stable render cursor");
                require(screen.mouseClicked(cx,cy,0)&&screen.mouseDragged(tx,ty,0,tx-cx,ty-cy)&&screen.mouseReleased(tx,ty,0),"production mouse press/drag/release handlers");
                LOG.info("QA_LIVE_ITEM_RECT item={} projected={},{} cursor={},{} viaRealMouseHandlers=true submitRequiresRealRUp=true",point.id(),point.x(),point.y(),tx,ty);
            } else if(op.equals("location-add") || op.equals("location-go")) {
                require(mc.screen instanceof cn.academy.port.client.ClassicLocationTeleportScreen,"real location screen");
                var screen=mc.screen;double x0=screen.width/2.0+20*.24,y0=screen.height/2.0-179.166*.24;
                var rows=(java.util.List<?>)read(screen,"locations");int visible=Math.min(5,rows.size());
                if(op.equals("location-add")) {
                    var edit=(net.minecraft.client.gui.components.EditBox)read(screen,"input");edit.setValue("QA_confirmed");
                    require(screen.mouseClicked(x0+390*.24,y0+(18+visible*82+45)*.24,0),"location add via actual mouse handler");
                } else {
                    require(!rows.isEmpty(),"saved destination required");
                    require(screen.mouseClicked(x0+350*.24,y0+(18+45)*.24,0),"location confirm via actual mouse handler");
                }
            } else {
                require(mc.screen instanceof cn.academy.port.client.TacticalCoordinateScreen,"real tactical screen");
                var screen=(cn.academy.port.client.TacticalCoordinateScreen)mc.screen;
                var layout=(cn.academy.port.client.TacticalCoordinateUiState.Layout)read(screen,"layout");
                var rect=layout.button(0,op.equals("tactical-save")?0:1);
                require(op.equals("tactical-save") || op.equals("tactical-confirm"),"known tactical UI action");
                require(screen.mouseClicked(rect.x()+rect.width()/2.0,rect.y()+rect.height()/2.0,0),"tactical action via actual mouse handler");
            }
            LOG.info("QA_LIVE_GUI_ACTION {} viaRealMouseHandler=true",op);
        } else if (arg.equals("confirmed")) {
            require(signatureFrames>0,"authoritative confirmed action produced a signature");
            if(SkillCatalog.find(AcademyClient.state.category,skill).orElseThrow().level()>=4)
                require(cameraFrames>0,"authoritative confirmed advanced action reached FOV event");
            LOG.info("QA_LIVE_CONFIRMED_PASS skill={} signatureFrames={} actualFovFrames={}",skill,signatureFrames,cameraFrames);
        } else if (arg.startsWith("storm:")) {
            var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();
            require(server!=null,"integrated server required");String operation=arg.substring(6);
            server.execute(()->{
                try{
                    var p=server.getPlayerList().getPlayer(owner);var s=AbilityStorage.get(p);
                    require(skill.equals("storm_wing")&&s.level==5&&p.gameMode.getGameModeForPlayer()==GameType.SURVIVAL,"finite LV5 survival storm fixture");
                    require(Double.isFinite(s.cp)&&Double.isFinite(s.maxCp())&&s.maxCp()>0&&s.cp>0&&s.cp<=s.maxCp(),"finite native CP ledger");
                    require(Double.isFinite(s.overload)&&s.overload>=0&&s.overload<=s.maxOverload(),"finite native heat ledger");
                    if(operation.equals("baseline")){
                        require(!cn.academy.port.skill.StormWing.active(p),"baseline precedes real R start");
                        stormProbe=new StormProbe(owner,s,p.serverLevel().getGameTime(),s.cp,s.maxCp());
                        LOG.info("QA_LIVE_STORM_BASELINE cp={} maxCp={} heat={} noRefillAfterBaseline=true",s.cp,s.maxCp(),s.overload);
                    }else{
                        var probe=stormProbe;require(probe!=null&&probe.owner().equals(owner)&&probe.state()==s,"same owner and native progress ledger");
                        long elapsed=p.serverLevel().getGameTime()-probe.began();
                        if(operation.equals("active")||operation.equals("sustain")){
                            require(cn.academy.port.skill.StormWing.active(p)&&cn.academy.port.skill.StormWing.phase(p)==cn.academy.port.skill.StormWingSession.ACTIVE,"actual server flight remains active");
                            require(cn.academy.port.skill.StormWing.input(p)>0,"flight originated from a real input nonce");
                            require(s.cp<probe.cp(),"actual finite native CP debit occurred");
                            if(operation.equals("sustain"))require(cn.academy.port.skill.StormWing.ticks(p)>=800&&elapsed>=850,"at least 40 seconds in actual ACTIVE phase");
                            LOG.info("QA_LIVE_STORM_{}_PASS elapsedTicks={} activeTicks={} cpBefore={} cp={} maxCp={} heat={} maxHeat={} input={} y={} finiteNativeLedger=true",operation.toUpperCase(java.util.Locale.ROOT),elapsed,cn.academy.port.skill.StormWing.ticks(p),probe.cp(),s.cp,s.maxCp(),s.overload,s.maxOverload(),cn.academy.port.skill.StormWing.input(p),p.getY());
                        }else if(operation.equals("stopped")){
                            require(!cn.academy.port.skill.StormWing.active(p),"normal R toggled the actual server context off");
                            require(p.onGround()||p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING),"airborne stop grants actual slow falling");
                            LOG.info("QA_LIVE_STORM_STOP_PASS cp={} heat={} grounded={} slowFalling={} noSkillApiCalled=true",s.cp,s.overload,p.onGround(),p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING));
                        }else throw new IllegalArgumentException(operation);
                    }
                }catch(RuntimeException e){failure=e.toString();}
            });
        } else if (arg.startsWith("shield:")) {
            var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();
            require(server!=null,"integrated server required");String operation=arg.substring(7);
            server.execute(()->{
                try{
                    var p=server.getPlayerList().getPlayer(owner);require(p!=null&&HighPowerCombat.enabled(p),"real enrolled player ledger required");
                    var state=AbilityStorage.get(p);var hp=new BigDecimal(HighPowerCombat.healthText(p));var shield=HighPowerCombat.shield(p);
                    boolean holding=EsperShields.holding(p);long now=p.serverLevel().getGameTime();
                    LOG.info("QA_LIVE_SHIELD_SAMPLE operation={} category={} health={} shield={} holding={} tick={} invulnerableTime={} position={} lastShieldSpend={} source=ordinary_damage_command readOnly=true",
                            operation,state.category,hp,shield,holding,now,p.invulnerableTime,p.position(),p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getLong("lastShieldSpend"));
                    if(operation.equals("baseline")){
                        require(shield.signum()>0&&holding,"baseline has actual positive shield and super armor");
                        shieldProbe=new ShieldProbe(owner,state.category,p.serverLevel(),hp,shield,now,null);
                        LOG.info("QA_LIVE_SHIELD_BASELINE category={} health={} shield={} holding={}",state.category,hp,shield,holding);
                    }else{
                        var probe=shieldProbe;
                        require(probe!=null&&probe.owner().equals(owner)&&probe.category().equals(state.category)&&probe.world()==p.serverLevel(),"same owner/category/world baseline");
                        if(operation.equals("paid")){
                            require(shield.signum()>0&&shield.compareTo(probe.shield())<0,"ordinary damage decreases a real still-positive shield");
                            require(hp.compareTo(probe.health())==0&&holding,"paid shield leaves exact HP unchanged and keeps super armor");
                            shieldProbe=new ShieldProbe(owner,state.category,p.serverLevel(),probe.health(),probe.shield(),probe.tick(),shield);
                            LOG.info("QA_LIVE_SHIELD_PAID_PASS category={} shieldBefore={} shield={} healthUnchanged={} holding={} elapsedTicks={} exactDamageAmountNotAssumed=true",state.category,probe.shield(),shield,hp,holding,now-probe.tick());
                        }else if(operation.equals("broken")){
                            require(probe.paidShield()!=null,"paid shield must be witnessed before break");
                            require(shield.signum()==0,"larger ordinary damage actually empties shield; do not force through phase immunity or recovery");
                            require(hp.signum()>0&&hp.compareTo(probe.health())<0&&!holding,"real overflow reduces HP and ends super armor without killing player");
                            LOG.info("QA_LIVE_SHIELD_BROKEN_PASS category={} shield={} healthBefore={} health={} holding={} elapsedTicks={} exactDamageAmountNotAssumed=true",state.category,shield,probe.health(),hp,holding,now-probe.tick());
                        }else throw new IllegalArgumentException(operation);
                    }
                }catch(RuntimeException e){failure=e.toString();}
            });
        } else if (arg.startsWith("hud-budget:")) {
            String operation=arg.substring(11);require(operation.equals("classic")||operation.equals("expansion"),"known HUD budget expectation");
            boolean expected=operation.equals("expansion");
            try{
                var hud=cn.academy.port.client.HighPowerRecoveryHud.class;
                var field=hud.getDeclaredField("SESSION");field.setAccessible(true);
                var session=(HighPowerRecoveryHudModel.Session)field.get(null);var values=session.current();
                require(values!=null&&values.category().equals(AcademyClient.state.category),"actual fresh same-category HP/shield HUD snapshot");
                require(cn.academy.port.client.HighPowerRecoveryHud.replacesNativeHearts(mc.player),"actual panel has a safe visible layout");
                var ordinaryMethod=hud.getDeclaredMethod("ordinaryBudget",HighPowerRecoveryHudModel.Values.class);ordinaryMethod.setAccessible(true);
                var ordinary=(SkillBudgetHudModel.Values)ordinaryMethod.invoke(null,values);
                var exactMethod=hud.getDeclaredMethod("budget",HighPowerRecoveryHudModel.Values.class);exactMethod.setAccessible(true);
                var exact=(SkillBudgetHudModel.Values)exactMethod.invoke(null,values);
                require(SkillBudgetHudModel.currentPresetUsesBudget(AcademyClient.state)==expected,"current mapped usable preset selects the intended wallet");
                require((ordinary!=null)==expected,"actual ordinaryBudget visibility matches classic-only/expansion preset");
                require(exact!=null,"ALT still has a real fresh budget even when the ordinary row is hidden");
                var rows=HighPowerRecoveryHudModel.displayRows(values,ordinary!=null);
                require(rows.rows().height()<=56&&(rows.budgetY()>=0)==expected,"actual compact painted content and budget row agree");
                LOG.info("QA_LIVE_HUD_BUDGET_PASS expectation={} category={} selectedPreset={} ordinaryBudgetVisible={} exactAltBudgetVisible={} compactPaintHeight={} availableCp={} nativeCp={} nativeMaxCp={} readOnly=true screenshotRequired=true",
                        operation,values.category(),AcademyClient.state.presets.current(),ordinary!=null,exact!=null,rows.rows().height(),ordinary==null?"hidden":ordinary.availableText(),AcademyClient.state.cp,AcademyClient.state.maxCp());
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        } else if (arg.equals("wind-diagnostic")) {
            var p=mc.player;var server=mc.getSingleplayerServer();
            var body=p.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(p.position());
            var column=new net.minecraft.world.phys.AABB(p.getX()-3,p.getY(),p.getZ()-3,p.getX()+3,p.getY()+6,p.getZ()+3);
            LOG.info("QA_LIVE_WIND_PREFLIGHT blocker={} nativeReady={} singleplayer={} published={} pos={} velocity={} ground={} bodyClear={} columnClear={} supportCollision={} vecStarter={} vecCombat={} vecFinal={} teleporter={} jetEngine={} activated={} overloadFine={} interfering={} busy={} learnedPrereq={} screen={}",
                cn.academy.port.client.WindPressureLiftClientMotion.startBlocker(),cn.academy.port.client.WindPressureLiftNativeTravel.ready(mc),
                mc.hasSingleplayerServer(),server!=null&&server.isPublished(),p.position(),p.getDeltaMovement(),p.onGround(),
                mc.level.noCollision(p,body.deflate(1e-7)),mc.level.noCollision(p,column),!mc.level.noCollision(p,body.move(0,-.02,0)),
                cn.academy.port.client.ClassicVectorStarterEffects.anyActive(),cn.academy.port.client.ClassicVectorCombatEffects.anyActive(),
                cn.academy.port.client.ClassicVectorFinalEffects.anyActive(),cn.academy.port.client.ClassicTeleporterFinalEffects.anyActive(),
                cn.academy.port.client.ClassicMeltdownerLateEffects.delegateActive("jet_engine"),AcademyClient.state.isActivated(),AcademyClient.state.overloadFine,
                AcademyClient.state.interfering,AcademyClient.state.consumptionInProgress(),AcademyClient.state.learned("multi_axis_reflection"),mc.screen);
        } else if (arg.equals("visual") || arg.equals("visual-report")) {
            LOG.info("QA_LIVE_VISUAL_SAMPLE skill={} handFrames={} armFrames={} signatureStageFrames={} projectedSignatureQuads={} cameraFovFrames={} scope=renderStageEligible_not_pixel_acceptance",
                    skill, hands, arms, signatureFrames, signatureQuads, cameraFrames);
            if (arg.equals("visual")) {
                require(signatureFrames > 0 && signatureQuads > 0, "normal key's category/action signature reaches AFTER_PARTICLES with drawable projected geometry");
                LOG.info("QA_LIVE_SIGNATURE_PASS skill={} renderStageEligible=true frames={} maxProjectedQuads={} actualPixelsRequireScreenshot=true",skill,signatureFrames,signatureQuads);
            }
        } else if (arg.equals("camera")) {
            require(cameraFrames > 0, "selected advanced skill actually changed the configured FOV event");
            LOG.info("QA_LIVE_CAMERA_PASS skill={} actualFovEventFrames={}",skill,cameraFrames);
        } else if (arg.equals("new-window:dead")) {
            var server = mc.getSingleplayerServer(); var owner = mc.player.getUUID();
            require(server != null, "actual integrated server required");
            server.submit(() -> {
                var p = server.getPlayerList().getPlayer(owner);
                require(p != null && !p.isAlive() && HighPowerCombat.enabled(p) && new BigDecimal(HighPowerCombat.healthText(p)).signum() == 0,
                        "real native damage spent the ledger and caused death");
                var policy = EsperProtectionConfig.policy();
                require(policy.blockKillCommand() && policy.blockForcedDeath(), "kill and forced-death guards remain enabled");
                LOG.info("QA_LIVE_NEW_WINDOW_DEATH_PASS nativeDead=true exactLedgerDead=true blockKill=true blockForcedDeath=true source=actual_out_of_world_damage_command");
                return true;
            }).join();
        } else if (arg.equals("new-window:respawn")) {
            require(mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen, "real death screen is open");
            var screen = mc.screen;
            var button = screen.children().stream().filter(x -> x instanceof net.minecraft.client.gui.components.Button b
                    && b.active && b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("deathScreen.respawn")).map(x -> (net.minecraft.client.gui.components.Button)x).findFirst().orElseThrow();
            double x = button.getX() + button.getWidth() / 2.0, y = button.getY() + button.getHeight() / 2.0;
            require(screen.mouseClicked(x, y, 0), "normal production respawn button click"); screen.mouseReleased(x, y, 0);
            LOG.info("QA_LIVE_NEW_WINDOW_RESPAWN_CLICK productionDeathScreen=true");
        } else if (arg.equals("new-window:armed") || arg.equals("new-window:clean") || arg.equals("new-window:respawned")) {
            try {
                var windows = cn.academy.port.client.NewSkillEffects.class.getDeclaredField("WINDOWS"); windows.setAccessible(true);
                var leases = (java.util.Map<?,?>) read(windows.get(null), "leases");
                var field = cn.academy.port.client.NewSkillEffects.class.getDeclaredField("LIVE"); field.setAccessible(true);
                var live = (java.util.List<?>) field.get(null); int own = 0;
                synchronized (live) {
                    for (var item : live) if (((NewSkillRuntime.Visual)read(item, "visual")).owner().equals(mc.player.getUUID())) own++;
                }
                if (arg.equals("new-window:armed")) {
                    require(!leases.isEmpty() && own > 0, "actual local ARMED cue created its bounded receipt");
                    windowOwnerBefore = mc.player; windowWorldBefore = mc.level;
                } else {
                    require(leases.isEmpty() && own == 0, "invalid/replaced local owner leaves no visual window or local effect");
                    if (arg.equals("new-window:respawned")) require(mc.player != windowOwnerBefore && mc.level == windowWorldBefore && mc.player.isAlive(), "real respawn replaced the owner inside the same ClientLevel");
                }
                LOG.info("QA_LIVE_NEW_WINDOW_LIFECYCLE_PASS phase={} leases={} ownLive={} alive={} spectator={} sameWorld={} replacedPlayer={}",
                        arg, leases.size(), own, mc.player.isAlive(), mc.player.isSpectator(), mc.level == windowWorldBefore, mc.player != windowOwnerBefore);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        } else if (arg.equals("new-window:divert") || arg.equals("new-window:expired")) {
            boolean expectDivert = arg.endsWith(":divert");
            try {
                var field = cn.academy.port.client.NewSkillEffects.class.getDeclaredField("LIVE"); field.setAccessible(true);
                var live = (java.util.List<?>) field.get(null);
                NewSkillRuntime.Visual found = null; int age = -1;
                synchronized (live) {
                    for (var item : live) {
                        var v = (NewSkillRuntime.Visual) read(item, "visual");
                        if (v.owner().equals(mc.player.getUUID()) && NewSkillRuntime.Kind.values()[v.kind()].id.equals("impulse_split") && v.phase() == NewSkillRuntime.DIVERT) {
                            found = v; age = ((Number) read(item, "age")).intValue();
                        }
                    }
                }
                var input = cn.academy.port.client.NewSkillClient.input("impulse_split");
                require(input != null && input.current() == null, "server END closes the released real split input");
                require(expectDivert == (found != null), expectDivert ? "actual owner DIVERT survives immediate END for its own lifetime" : "DIVERT naturally expires without forced clearing");
                if (found != null) require(found.input() > 1 && found.epoch() > 0 && age >= 0 && age < 24, "the real second direction owns the live diversion cue");
                LOG.info("QA_LIVE_NEW_WINDOW_PASS phase={} owner={} input={} epoch={} token={} sequence={} age={} inputEnded=true scope=actual_received_live_not_pixel_acceptance",
                        expectDivert ? "DIVERT" : "EXPIRED", mc.player.getUUID(), found == null ? 0 : found.input(), found == null ? 0 : found.epoch(), found == null ? 0 : found.token(), found == null ? 0 : found.sequence(), age);
            } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        } else if (arg.equals("camera-neutral")) {
            require(!cameraTimeline().active(), "camera timeline retires after cancellation/release");
            require(cameraTimeline().sample(0).equals(CastCameraTimeline.View.NONE), "no FOV/pitch/roll offset remains");
            LOG.info("QA_LIVE_CAMERA_NEUTRAL_PASS skill={}",skill);
        } else if (arg.equals("camera-held")) {
            require(cameraTimeline().holds(skill), "camera belongs to the accepted selected advanced skill");
            LOG.info("QA_LIVE_CAMERA_HELD_PASS skill={}",skill);
        } else if (arg.startsWith("award:") || arg.startsWith("overload:")) {
            // Growth-style HUD fixture: the same server-side award call a cast makes (AbilityProgress.addExperience), or the overload ledger share.
            var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
            if (server == null) throw new IllegalStateException("integrated server required");
            server.execute(() -> {
                try {
                    var s = AbilityStorage.get(server.getPlayerList().getPlayer(id));
                    if (arg.startsWith("award:")) s.addExperience(arg.substring(6), 0.0005f);
                    else s.overload = s.maxOverload() * Double.parseDouble(arg.substring(9));
                    LOG.info("QA_LIVE_GROWTH {}", arg);
                } catch (RuntimeException e) { failure = e.toString(); }
            });
        } else if (arg.equals("pose")) {
            Object cast = localCast(mc);
            LOG.info("QA_LIVE_INPUT skill={} physicalR={} key={} slot={} usable={} activated={} screen={} delegate={}/{} cast={} handFrames={} armFrames={}",
                    skill, org.lwjgl.glfw.GLFW.glfwGetKey(mc.getWindow().getWindow(), 82), AcademyClient.SLOTS[2].isDown(),
                    AcademyClient.state.presets.currentSkill(2), AcademyClient.state.canUse(skill), AcademyClient.state.isActivated(),
                    mc.screen, AcademyClient.delegatePresent(2), AcademyClient.delegateActive(2), cast, hands, arms);
            if (!skill.equals("vector_construct")) {
                require(cast != null && skill.equals(read(cast, "skill")), "normal key must create the selected skill's gesture");
                require(read(cast, "archetype") == CastPose.of(skill), "gesture archetype matches ability");
            } else require(CastPoseClient.motion(mc.player,mc.level.getGameTime()).strength()>0,"construct must have an authoritative phase");
            require(mc.options.getCameraType().isFirstPerson() ? hands > 0 : arms > 0, "pose reached the actual hand/player renderer");
            LOG.info("QA_LIVE_POSE_PASS skill={} archetype={} handFrames={} armFrames={} camera={}", skill, CastPose.of(skill), hands, arms, mc.options.getCameraType());
        } else if (arg.equals("transientstate")) {
            // Read-only dump of in-flight controller state, to check that nothing transient survives a reload.
            var server = mc.getSingleplayerServer();
            if (server == null) throw new IllegalStateException("integrated server required");
            var id = mc.player.getUUID();
            server.execute(() -> {
                try {
                    var p = server.getPlayerList().getPlayer(id);
                    LOG.info("QA_LIVE_TRANSIENT dim={} returnMarked={} returnActive={} groundStep={} beamStep={} lift={} tether={} construct={}",
                            p.level().dimension().location(), ReturnMark.marked(p), ReturnMark.active(p), MagneticGroundStep.active(p),
                            BeamStep.active(p), WindPressureLift.active(p), PolarityTether.active(p), ConstructRuntime.heldBody(p));
                } catch (RuntimeException e) { failure = e.toString(); }
            });
        } else if (arg.equals("savedstate")) {
            // Read-only dump of the server-side ability ledger, used to check what survived a world save/reload.
            var server = mc.getSingleplayerServer();
            if (server == null) throw new IllegalStateException("integrated server required");
            var id = mc.player.getUUID();
            server.execute(() -> {
                try {
                    var p = server.getPlayerList().getPlayer(id);
                    var s = AbilityStorage.get(p);
                    String preset = s.presets.currentSkill(2);
                    LOG.info("QA_LIVE_SAVED_STATE category={} level={} activated={} preset={} presetLearned={} cp={}",
                            s.category, s.level, s.activated, preset, preset.isEmpty() ? null : s.learned(preset),
                            HighPowerResources.cp(p));
                } catch (RuntimeException e) { failure = e.toString(); }
            });
        } else if (arg.equals("construct:trace")) {
            var id=mc.player.getUUID();
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                var link=ConstructLink.get();
                var body=tracedBody==null || link==null?null:link.body(p.serverLevel(),tracedBody);
                LOG.info("QA_LIVE_BODY_TRACE id={} body={} center={} velocity={} min={} max={} target={} hp={} touches={}",tracedBody,body,
                    body==null?null:java.util.Arrays.toString(body.center()),body==null?null:java.util.Arrays.toString(body.velocity()),
                    body==null?null:java.util.Arrays.toString(body.min()),body==null?null:java.util.Arrays.toString(body.max()),
                    target==null?null:target.position(),target==null?null:HighPowerCombat.healthText(target),
                    body==null?java.util.List.of():link.touching(p.serverLevel(),tracedBody,.35).stream().map(e->e.getType().toString()).toList());
            });
        } else if (arg.startsWith("construct:")) {
            String phase=arg.substring(10);
            try { var message=net.minecraft.client.gui.Gui.class.getDeclaredField("overlayMessageString"); message.setAccessible(true); LOG.info("QA_LIVE_CONSTRUCT_OVERLAY {}",message.get(mc.gui)); }
            catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
            try {
                var f=Class.forName("cn.academy.port.client.ConstructClient").getDeclaredField("CUES"); f.setAccessible(true);
                Object cue=((Map<?,?>)f.get(null)).get(mc.player.getUUID());
                int expected=switch(phase) { case "preview" -> ConstructRuntime.PREVIEW; case "dash" -> ConstructRuntime.DASH; case "hold" -> ConstructRuntime.HOLD; case "throw" -> ConstructRuntime.THROWN; case "land" -> ConstructRuntime.LANDED; default -> throw new IllegalArgumentException(phase); };
                require(cue!=null && ((ConstructRuntime.Visual)read(cue,"visual")).phase()==expected,"authoritative phase reached client: "+phase);
            } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
            var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
            server.execute(() -> { try {
                var p=server.getPlayerList().getPlayer(id);
                if(phase.equals("hold")) tracedBody=ConstructRuntime.heldBody(p);
                LOG.info("QA_LIVE_CONSTRUCT_STATE phase={} preview={} body={} q={} usable={} slot={} cp={}",phase,ConstructRuntime.previewing(p),ConstructRuntime.heldBody(p),HighPowerGrowth.values(p).position(),AbilityStorage.get(p).canUse("vector_construct"),AbilityStorage.get(p).presets.currentSkill(2),HighPowerResources.cp(p));
                require(phase.equals("preview")?ConstructRuntime.previewing(p):phase.equals("dash")?ConstructRuntime.dashing(p):phase.equals("hold")?ConstructRuntime.heldBody(p)!=null:ConstructRuntime.heldBody(p)==null,"construct server phase: "+phase);
                LOG.info("QA_LIVE_CONSTRUCT_PASS phase={} held={}",phase,ConstructRuntime.heldBody(p));
            } catch(RuntimeException e) { failure=e.toString(); } });
        } else if (arg.equals("right")) {
            mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.RIGHT); mc.options.broadcastOptions();
        } else if (arg.equals("clearui")) {
            mc.gui.getChat().clearMessages(false);
            mc.getToasts().clear();
        } else if (arg.equals("left")) {
            mc.options.mainHand().set(net.minecraft.world.entity.HumanoidArm.LEFT);
            mc.options.broadcastOptions();
        } else if (arg.equals("neutral")) {
            require(localCast(mc) == null && CastPoseClient.motion(mc.player,mc.level.getGameTime()).strength()==0, "gesture must retire after release/cancellation");
            LOG.info("QA_LIVE_NEUTRAL_PASS skill={}", skill);
        } else if (arg.equals("cleartarget")) {
            mc.getSingleplayerServer().execute(() -> { if (target != null) { target.discard(); target = null; } });
        } else if (arg.equals("target") || arg.equals("target:front") || arg.equals("target:throw")) {
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                var p = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                if (target != null) target.discard();
                target = EntityType.HUSK.create(p.serverLevel());
                target.setNoAi(true); target.setNoGravity(true); target.setSilent(true);
                target.setPos(p.position().add(arg.equals("target:throw") ? new Vec3(0,0,20) : arg.equals("target:front") ? new Vec3(0, 0, 6) : new Vec3(6, 0, 8)));
                p.serverLevel().addFreshEntity(target);
                require(HighPowerCombat.enroll(target, 5), "durable target enrollment");
                targetHp = new BigDecimal(HighPowerCombat.healthText(target));
                LOG.info("QA_LIVE_TARGET at={} hp={}", target.position(), targetHp);
            });
        } else if (arg.equals("placed") || arg.equals("fired") || arg.equals("active") || arg.equals("inactive") || arg.equals("damaged") || arg.equals("moved")) {
            var id = mc.player.getUUID();
            mc.getSingleplayerServer().execute(() -> {
                try {
                    var p = mc.getSingleplayerServer().getPlayerList().getPlayer(id);
                    if (arg.equals("moved")) {
                        require(p.position().distanceToSqr(setupPosition) > 1, "teleport key changes the actual authoritative position");
                        LOG.info("QA_LIVE_MOVEMENT_PASS from={} to={}", setupPosition, p.position());
                    } else if (arg.equals("damaged")) {
                        var damage = targetHp.subtract(new BigDecimal(HighPowerCombat.healthText(target)));
                        require(damage.signum() > 0, "instant skill must damage the actual target ledger");
                        if(skill.equals("vector_construct") || skill.equals("zenith_vector_dominion"))
                            require(damage.compareTo(new BigDecimal("1e12"))>0,"high-power vector output must exceed ordinary physical collision damage");
                        LOG.info("QA_LIVE_DAMAGE_PASS skill={} damage={}", skill, damage);
                    } else if (arg.equals("placed")) {
                        require(FoldedMirrorArray.plates(p).size() == 1 && !FoldedMirrorArray.holding(p), "physical shift+R release placed a paid stationary mirror");
                        LOG.info("QA_LIVE_MIRROR_PLACED plates={} cp={}", FoldedMirrorArray.plates(p), HighPowerResources.cp(p));
                    } else if (arg.equals("fired")) {
                        var r = FoldedMirrorArray.receipt(p);
                        require(r != null && r.crossings() == 1 && r.path().size() == 2 && r.status() == FoldedMirrorArray.DAMAGED,
                                "real key release must produce one reflected, damaging two-leg ray; receipt=" + r);
                        var damage = targetHp.subtract(new BigDecimal(HighPowerCombat.healthText(target)));
                        require(damage.signum() > 0, "reflected ray damages the real target ledger");
                        LOG.info("QA_LIVE_MIRROR_FIRED crossings={} path={} damage={} cp={}", r.crossings(), r.path(), damage, HighPowerResources.cp(p));
                    } else {
                        String name = switch (skill) {
                            case "deflection_strike" -> "DeflectionStrike"; case "dual_point" -> "DualPoint";
                            case "vector_parry" -> "VectorParry"; case "inertial_anchor" -> "InertialAnchor";
                            case "shear_windblade" -> "ShearWindblade"; case "iron_sand_lance" -> "IronSandLance";
                            case "circulation_listening" -> "CirculationListening";
                            case "rail_thunder_array" -> "RailThunderArray"; case "magnetic_domain" -> "MagneticDomain";
                            case "multicore_canopy" -> "MulticoreCanopy"; case "vector_cage" -> "VectorCage";
                            case "wind_pressure_lift" -> "WindPressureLift";
                            case "zenith_vector_dominion", "phase_corridor", "reverse_jet_brake" -> "NewSkillRuntime";
                            case "tactical_coordinate_formation" -> "TacticalCoordinateFormation";
                            case "thunder_clap" -> "skill.ThunderClap"; case "plasma_cannon" -> "skill.PlasmaCannon"; default -> throw new IllegalStateException("no active probe for " + skill);
                        };
                        if(skill.equals("circulation_listening")) {
                            var basic=CirculationListening.class.getDeclaredMethod("basic",net.minecraft.server.level.ServerPlayer.class); basic.setAccessible(true);
                            var pay=CirculationListening.class.getDeclaredMethod("readPayment",net.minecraft.server.level.ServerPlayer.class); pay.setAccessible(true);
                            LOG.info("QA_LIVE_LISTEN_DIAGNOSTIC nativeObserver={} basic={} payment={} ready={} runtimeReason={}",CirculationListeningMotionProof.available(),basic.invoke(null,p),pay.invoke(null,p),HighPowerResources.ready(p),HighPowerCombat.unsupportedReason(p));
                        }
                        boolean active = (boolean) Class.forName(name.startsWith("skill.")?"cn.academy.port."+name:"cn.academy.port.highpower." + name)
                                .getMethod("active", net.minecraft.server.level.ServerPlayer.class).invoke(null, p);
                        require(active != arg.equals("inactive"), "authoritative " + skill + " controller expected " + arg);
                        LOG.info("QA_LIVE_{}_PASS skill={}", arg.toUpperCase(java.util.Locale.ROOT), skill);
                    }
                } catch (ReflectiveOperationException | RuntimeException e) { failure = e.toString(); }
            });
        } else throw new IllegalArgumentException(arg);
    }

    /** Read-only fixture isolation. In particular, legacy toggle contexts are never silently aborted. */
    private static void restoreMotionOptIn(net.minecraft.server.MinecraftServer server){
        if(!motionOptIn)return;
        cn.academy.port.gametest.HighPowerFeatureGateRuntimeTests.candidateMotionRestore(server.overworld());motionOptIn=false;
    }
    @SubscribeEvent public static void serverStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event){
        restoreMotionOptIn(event.getServer());
        if(priorAutoJump!=null){var saved=priorAutoJump;priorAutoJump=null;Minecraft.getInstance().execute(()->Minecraft.getInstance().options.autoJump().set(saved));}
    }

    private static String isolation(net.minecraft.server.level.ServerPlayer p){
        var s=AbilityStorage.get(p);
        return "unsupportedReason="+HighPowerCombat.unsupportedReason(p).orElse("none")
            +" deviation="+cn.academy.port.skill.VecDeviation.active(p)
            +" reflection="+cn.academy.port.skill.VecReflection.active(p)
            +" lightshield="+cn.academy.port.skill.LightShield.active(p)
            +" storm="+cn.academy.port.skill.StormWing.active(p)
            +" hpEnabled="+HighPowerCombat.enabled(p)+" hpData="+p.getPersistentData().contains(HighPowerCombat.DATA_KEY)
            +" alive="+p.isAlive()+" activated="+s.activated+" overloadFine="+s.overloadFine+" cp="+s.cp+" heat="+s.overload;
    }

    /** Fixture-only prerequisite preparation derives the real catalog requirements; it never replaces production canLearn. */
    private static void preparePrerequisites(cn.academy.port.core.AbilityProgress state,SkillCatalog.Skill skill,java.util.Set<String> seen){
        if(!seen.add(skill.id()))return;
        for(var requirement:skill.requirements()){
            var parent=SkillCatalog.find(skill.category(),requirement.id()).orElseThrow();
            preparePrerequisites(state,parent,seen);state.learn(parent.id());
            state.experience.put(parent.id(),Math.max(state.exp(parent.id()),requirement.exp()));
        }
        if(skill.anyLearnedSkillLevel()>0){
            var other=SkillCatalog.ALL.stream().filter(s->s.category().equals(skill.category())&&s.level()==skill.anyLearnedSkillLevel()&&!s.id().equals(skill.id())).findFirst().orElseThrow();
            preparePrerequisites(state,other,seen);state.learn(other.id());
        }
    }

    static void aim(Minecraft mc, String arg) {
        String[] xyz = arg.split(",");
        Vec3 point = new Vec3(Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]), Double.parseDouble(xyz[2]));
        Vec3 d = point.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) -Math.toDegrees(Math.atan2(d.x, d.z)));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
    }

    private static Object localCast(Minecraft mc) {
        try {
            Field f = CastPoseClient.class.getDeclaredField("CASTS"); f.setAccessible(true);
            return ((Map<?, ?>) f.get(null)).get(mc.player.getId());
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static Object read(Object object, String field) {
        try { Field f = object.getClass().getDeclaredField(field); f.setAccessible(true); return f.get(object); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void require(boolean condition, String why) { if (!condition) throw new IllegalStateException(why); }

    /** Called only at an actual setup boundary or quit; never mutates presentation/gameplay. */
    static void finishFinalVisual(String boundary) {
        if (!FINAL_VISUAL || finalVisual == null) return;
        var w=finalVisual;
        LOG.info("QA_FINAL_VISUAL_SAMPLE skill={} category={} level={} signatureFrames={} projectedQuads={} firstPerson={} handMatrixFrames={} thirdPersonArmFrames={} actualCameraFovFrames={} actionSaved={} releaseSaved={} boundary={} scope=render_observation_plus_native_framebuffer_screenshots_not_automatic_pixel_approval",
                w.skill,w.category,w.level,signatureFrames,signatureQuads,w.firstPerson,hands,arms,cameraFrames,w.actionSaved,w.releaseSaved,boundary);
        require(signatureFrames>0&&signatureQuads>0,"final real-input signature must reach actual AFTER_PARTICLES with drawable geometry: "+w.skill);
        require(!w.firstPerson||hands>0,"first-person final cast must actually transform its hand matrix: "+w.skill);
        require(w.level<4||cameraFrames>0,"final advanced cast must change actual configured FOV: "+w.skill);
        require(w.actionSaved&&w.releaseSaved,"both actual full-frame action/release screenshots must have saved: "+w.skill);
        LOG.info("QA_FINAL_VISUAL_PASS skill={} category={} level={} hands={} camera={} firstPerson={} scope=actual_stage_matrix_fov_and_saved_images_pixel_review_separate",
                w.skill,w.category,w.level,hands,cameraFrames,w.firstPerson);
        finalVisual=null;pendingCapture=null;
    }

    private static CastCameraTimeline cameraTimeline() {
        try {
            var f = Class.forName("cn.academy.port.client.CameraMotion").getDeclaredField("TIMELINE"); f.setAccessible(true);
            return (CastCameraTimeline) f.get(null);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeFov(ViewportEvent.ComputeFov event) { beforeFov = event.getFOV(); fovEvent = event; }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterFov(ViewportEvent.ComputeFov event) {
        if (skill.isEmpty() || event != fovEvent || !event.usedConfiguredFov()) return;
        var clock = cameraTimeline();
        if (!clock.active() || !skill.equals(read(clock,"skill"))) return;
        double expected = beforeFov * clock.sample(event.getPartialTick()).fov();
        if (Math.abs(event.getFOV() - beforeFov) > 1e-6 && Math.abs(event.getFOV() - expected) < .01) cameraFrames++;
    }
    /** Observes real cast state at the real render stage; does not call receivers, renderers, or force any cast. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void signatureStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || skill.isEmpty()) return;
        var mc = Minecraft.getInstance(); var tier = VisualTierConfig.tier();
        if (mc.level == null || mc.player == null || !tier.enhanced()) return;
        float partial = mc.isPaused() ? 0 : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var cast = CastPoseClient.signatures(partial).stream().filter(c -> c.local() && c.entity()==mc.player.getId() && skill.equals(c.skill())).findFirst().orElse(null);
        if (cast == null) return;
        String category=CastSignatureDecor.category(cast,AcademyClient.state.category);
        int level=CastSignatureDecor.level(cast,category);
        var pos=mc.player.getPosition(partial); var hand=CastPoseClient.handAnchor(mc.player,mc.level.getGameTime()+partial,partial);
        var look=mc.player.getViewVector(partial); var camera=event.getCamera().getPosition();
        var parts=CastSignatureDecor.fx(cast.skill(),cast.archetype(),category,level,new Point(pos.x,pos.y,pos.z),new Point(hand.x,hand.y,hand.z),
                new Point(look.x,look.y,look.z),cast.age(),cast.sinceRelease(),17,mc.level.getGameTime()+partial,tier);
        try {
            var material=Class.forName("cn.academy.port.client.RailThunderArrayGrandMaterial").getDeclaredMethod("kitReady"); material.setAccessible(true);
            if (!(boolean)material.invoke(null)) return;
            var projector=Class.forName("cn.academy.port.client.VisualKit").getDeclaredMethod("projectParts",cn.academy.port.client.FxLib.Parts.class,
                    Point.class,Point.class,Point.class,VisualTier.class,int.class); projector.setAccessible(true);
            var direction=event.getCamera().getLookVector(); var up=event.getCamera().getUpVector();
            var quads=(java.util.List<?>)projector.invoke(null,parts,new Point(camera.x,camera.y,camera.z),new Point(direction.x(),direction.y(),direction.z()),
                    new Point(up.x(),up.y(),up.z()),tier,CastSignatureDecor.MAX_QUADS);
            if (!quads.isEmpty()) {
                signatureFrames++; signatureQuads=Math.max(signatureQuads,quads.size());
                if (FINAL_VISUAL && finalVisual != null && mc.screen == null) {
                    var w=finalVisual;w.firstPerson|=mc.options.getCameraType().isFirstPerson();
                    if (pendingCapture==null) {
                        // Observe a representative natural frame, never extend or manufacture a cast.
                        // Released short taps remain eligible for action capture while their real geometry lives.
                        if (!w.actionRequested&&cast.age()>=4) pendingCapture=new Capture(w,false,cast.age(),cast.sinceRelease(),cast.archetype());
                        else if (w.actionRequested&&!w.releaseRequested&&cast.sinceRelease()>=2) pendingCapture=new Capture(w,true,cast.age(),cast.sinceRelease(),cast.archetype());
                    }
                }
            }
        } catch (ReflectiveOperationException e) { failure=e.toString(); }
    }

    /** Capture after every GUI listener, including LOWEST-priority replacement health/shield panels. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void finalFrame(net.neoforged.neoforge.client.event.RenderFrameEvent.Post event) {
        if (!FINAL_VISUAL || pendingCapture==null) return;
        var mc=Minecraft.getInstance();var capture=pendingCapture;pendingCapture=null;
        var w=capture.witness();if(w!=finalVisual||mc.screen!=null)return;
        String phase=capture.release()?"release":"action";
        String name="codex_final_"+w.skill+"_"+phase+"_"+w.serial+".png";
        if(capture.release())w.releaseRequested=true;else w.actionRequested=true;
        net.minecraft.client.Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message->{
            boolean saved=message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    && t.getKey().equals("screenshot.success")
                    && new java.io.File(new java.io.File(mc.gameDirectory,"screenshots"),name).isFile();
            if(capture.release())w.releaseSaved=saved;else w.actionSaved=saved;
            LOG.info("QA_FINAL_VISUAL_SCREENSHOT skill={} phase={} file={} saved={} sampledAge={} sampledSinceRelease={} sampledArchetype={} source=native_full_framebuffer message={}",w.skill,phase,name,saved,capture.age(),capture.sinceRelease(),capture.archetype(),message.getString());
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void before(RenderHandEvent e) { beforeHand = new Matrix4f(e.getPoseStack().last().pose()); }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void after(RenderHandEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || beforeHand == null || !mc.options.getCameraType().isFirstPerson()) return;
        // The original dir_shock/dir_blast handlers own real matrices while shared motion() deliberately returns NONE.
        boolean selected=CastPoseClient.signatures(0).stream().anyMatch(c->c.local()&&c.entity()==mc.player.getId()&&skill.equals(c.skill()));
        if(!selected)return;
        if(FINAL_VISUAL&&finalVisual!=null)finalVisual.firstPerson=true;
        if (!beforeHand.equals(e.getPoseStack().last().pose(), 1e-6f)) hands++;
    }
    @SubscribeEvent
    public static void player(RenderPlayerEvent.Post e) {
        var mc = Minecraft.getInstance();
        if (e.getEntity() != mc.player) return;
        double t = mc.level.getGameTime() + mc.getTimer().getGameTimeDeltaPartialTick(false);
        var motion=CastPoseClient.motion(mc.player,t);
        if(motion.strength()<.05) return;
        var expected=motion.arms();
        var m = e.getRenderer().getModel();
        if (Math.abs(m.rightArm.xRot - expected.rx()) < .035 && Math.abs(m.leftArm.xRot - expected.lx()) < .035
                && Math.abs(m.rightArm.yRot - (expected.ry() + motion.body().twist())) < .035 && Math.abs(m.leftArm.yRot - (expected.ly() + motion.body().twist())) < .035) arms++;
    }
}
