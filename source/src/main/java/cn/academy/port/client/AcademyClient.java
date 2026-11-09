package cn.academy.port.client;
import cn.academy.port.*;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class AcademyClient {
    public static AbilityProgress state=new AbilityProgress();
    private static final ClassicActivationClientState activationState=new ClassicActivationClientState();
    public static net.minecraft.nbt.CompoundTag development;
    public static final KeyMapping TOGGLE=new KeyMapping("key.academy.toggle",GLFW.GLFW_KEY_V,"key.categories.academy");
    public static final KeyMapping SWITCH_PRESET=new KeyMapping("key.academy.switch_preset",GLFW.GLFW_KEY_C,"key.categories.academy");
    public static final KeyMapping EDIT_PRESET=new KeyMapping("key.academy.edit_preset",GLFW.GLFW_KEY_N,"key.categories.academy");
    public static final KeyMapping[] SLOTS={
        new KeyMapping("key.academy.ability_0",com.mojang.blaze3d.platform.InputConstants.Type.MOUSE,GLFW.GLFW_MOUSE_BUTTON_LEFT,"key.categories.academy"),
        new KeyMapping("key.academy.ability_1",com.mojang.blaze3d.platform.InputConstants.Type.MOUSE,GLFW.GLFW_MOUSE_BUTTON_RIGHT,"key.categories.academy"),
        new KeyMapping("key.academy.ability_2",GLFW.GLFW_KEY_R,"key.categories.academy"),
        new KeyMapping("key.academy.ability_3",GLFW.GLFW_KEY_F,"key.categories.academy")
    };
    private static final ClassicActivationKey activation=new ClassicActivationKey();
    private static final ClassicInputLatch switchLatch=new ClassicInputLatch(),editLatch=new ClassicInputLatch();
    private static final ClientSessionGuard session=new ClientSessionGuard();
    private static Object ownedConnection;
    private static ClassicClientInputBinding binding;
    private static final MenuMouseInputFence menuMouseFence=new MenuMouseInputFence();
    private static int[] overrideKeys=new int[0];
    private static String mappingSignature="";
    private static boolean deathFenced;
    private static long singleInputCounter,singleInputEpoch,serverSingleEpoch;
    private static String groundStepBlocker="";
    private static long groundStepBlockerAt=Long.MIN_VALUE;
    private static final PolarityTetherInputTracker tetherInputs=new PolarityTetherInputTracker(),circuitInputs=new PolarityTetherInputTracker(),groundingInputs=new PolarityTetherInputTracker(),groundStepInputs=new PolarityTetherInputTracker(),itemPullInputs=new PolarityTetherInputTracker();
    private static final IronSandLanceInputState ironLanceInputs=new IronSandLanceInputState();
    private static final ThunderNetInputState thunderNetInputs=new ThunderNetInputState();
    private static final RailThunderArrayInputState railArrayInputs=new RailThunderArrayInputState();
    private static final PotentialBufferInputState potentialBufferInputs=new PotentialBufferInputState();
    private static final ThermalShellInputState thermalShellInputs=new ThermalShellInputState();
    private static final DualPointInputState dualPointInputs=new DualPointInputState();
    private static final ScatterPlateInputState scatterPlateInputs=new ScatterPlateInputState();
    private static final BeamEtchingInputState beamEtchingInputs=new BeamEtchingInputState();
    private static final BeamEtchingInputState pointMeltdownInputs=new BeamEtchingInputState();
    private static final DisintegrationSweepInputState disintegrationSweepInputs=new DisintegrationSweepInputState();
    private static final FoldedMirrorInputState foldedMirrorInputs=new FoldedMirrorInputState();
    private static final RemoteScatterRelayInputState remoteScatterRelayInputs=new RemoteScatterRelayInputState();
    private static final ScatterLocatorInputState scatterLocatorInputs=new ScatterLocatorInputState();
    private static final ParticleLaminateInputState particleLaminateInputs=new ParticleLaminateInputState();
    private static final MulticoreCanopyInputState multicoreCanopyInputs=new MulticoreCanopyInputState();
    private static final DissipationCurtainInputState dissipationCurtainInputs=new DissipationCurtainInputState();
    private static final VectorCageInputState vectorCageInputs=new VectorCageInputState();
    private static final CirculationListeningInputState circulationListeningInputs=new CirculationListeningInputState();
    private static final CirculationReliefInputState circulationReliefInputs=new CirculationReliefInputState();
    private static final AutonomousVectorCircleInputState autonomousVectorCircleInputs=new AutonomousVectorCircleInputState();
    private static final MultiAxisReflectionInputState multiAxisReflectionInputs=new MultiAxisReflectionInputState();
    private static final WindPressureLiftInputState windPressureLiftInputs=new WindPressureLiftInputState();
    private static final InertialAnchorInputState inertialAnchorInputs=new InertialAnchorInputState();
    private static final ZeroGapArmorInputState zeroGapArmorInputs=new ZeroGapArmorInputState();
    private static final SafeLandingProbeInputState safeLandingProbeInputs=new SafeLandingProbeInputState();
    private static final DisplacedProjectionInputState displacedProjectionInputs=new DisplacedProjectionInputState();
    private static final RecoveryMarkInputState recoveryMarkInputs=new RecoveryMarkInputState();
    private static final ShortInterceptionInputState shortInterceptionInputs=new ShortInterceptionInputState();
    private static final MultidimensionalLandingArrayInputState multidimensionalLandingArrayInputs=new MultidimensionalLandingArrayInputState();
    private static final SpatialPincerInputState spatialPincerInputs=new SpatialPincerInputState();
    private static final SpatialRelayInputState spatialRelayInputs=new SpatialRelayInputState();
    private static final EmergencyReturnInputState emergencyReturnInputs=new EmergencyReturnInputState();
    private static final CornerExchangeInputState cornerExchangeInputs=new CornerExchangeInputState();
    private static final CrossSubstitutionInputState crossSubstitutionInputs=new CrossSubstitutionInputState();
    private static final ReturnMarkInputState returnMarkInputs=new ReturnMarkInputState();
    private static final PhaseSidestepInputState phaseSidestepInputs=new PhaseSidestepInputState();
    private static final VectorParryInputState vectorParryInputs=new VectorParryInputState();
    private static final BrakingTouchInputState brakingTouchInputs=new BrakingTouchInputState();
    private static final DeflectionStrikeInputState deflectionStrikeInputs=new DeflectionStrikeInputState();
    private static final YawPalmInputState yawPalmInputs=new YawPalmInputState();
    private static final TrajectoryReturnInputState trajectoryReturnInputs=new TrajectoryReturnInputState();
    private static final ShearWindbladeInputState shearWindbladeInputs=new ShearWindbladeInputState();
    private static final MagneticDomainInputState magneticDomainInputs=new MagneticDomainInputState();
    private static boolean down(KeyMapping mapping){return physicalDown(sourceKey(mapping.getKey()));}
    private static int sourceKey(com.mojang.blaze3d.platform.InputConstants.Key key){return key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.MOUSE?(key.getValue()>=0&&key.getValue()<=GLFW.GLFW_MOUSE_BUTTON_LAST?ClassicPhysicalKeys.mouse(key.getValue()):0):key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM?ClassicPhysicalKeys.keysym(key.getValue()):sourceScancode(key.getValue());}
    private static int glfwForScan(int scan){if(scan<0)return -1;for(int glfw:ClassicPhysicalKeys.supportedKeysyms().keySet())if(GLFW.glfwGetKeyScancode(glfw)==scan)return glfw;for(int glfw=305;glfw<=314;glfw++)if(GLFW.glfwGetKeyScancode(glfw)==scan)return glfw;for(int glfw:new int[]{161,162})if(GLFW.glfwGetKeyScancode(glfw)==scan)return glfw;return -1;}
    private static int sourceScancode(int scan){int glfw=glfwForScan(scan);return glfw<0?ClassicPhysicalKeys.scancode(scan):ClassicPhysicalKeys.keysym(glfw);}
    private static boolean nativeKey(int glfw){return glfw>=GLFW.GLFW_KEY_SPACE&&glfw<=GLFW.GLFW_KEY_LAST&&(ClassicPhysicalKeys.supportedKeysyms().containsKey(glfw)||glfw==GLFW.GLFW_KEY_KP_EQUAL||glfw==GLFW.GLFW_KEY_WORLD_1||glfw==GLFW.GLFW_KEY_WORLD_2||glfw>=GLFW.GLFW_KEY_F16&&glfw<=GLFW.GLFW_KEY_F25);}
    private static boolean physicalDown(int key){var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return false;if(key<0){int button=key+100;return button>=0&&button<=GLFW.GLFW_MOUSE_BUTTON_LAST&&GLFW.glfwGetMouseButton(mc.getWindow().getWindow(),button)==GLFW.GLFW_PRESS;}int glfw=ClassicPhysicalKeys.glfw(key);if(nativeKey(glfw))return com.mojang.blaze3d.platform.InputConstants.isKeyDown(mc.getWindow().getWindow(),glfw);int scan=ClassicPhysicalKeys.scan(key);if(scan>=0){int actual=glfwForScan(scan);return nativeKey(actual)&&GLFW.glfwGetKey(mc.getWindow().getWindow(),actual)==GLFW.GLFW_PRESS;}return false;}
    private static long now(){return net.minecraft.Util.getMillis();}
    public static KeyMapping skillKey(String id){for(int slot=0;slot<SLOTS.length;slot++)if(state.presets.currentSkill(slot).equals(id))return SLOTS[slot];return null;}
    public static boolean delegatePresent(int slot){return binding!=null&&slot>=0&&slot<SLOTS.length&&binding.ownsDefault(slot);}
    public static boolean delegateActive(int slot){return binding!=null&&slot>=0&&slot<SLOTS.length&&binding.activeDefault(slot);}
    public static boolean activationHeld(){return activation.held();}
    public static long activationHeldMillis(){return activation.heldMillis(now());}
    public static boolean displayNumbers(){return activation.displayNumbers(now());}
    private static String mappings(){StringBuilder value=new StringBuilder();for(int slot=0;slot<SLOTS.length;slot++)value.append(state.presets.currentSkill(slot)).append(':').append(sourceKey(SLOTS[slot].getKey())).append(';');return value.toString();}
    private static ClassicClientInputBinding createBinding(Minecraft mc){
        final Object level=mc.level,player=mc.player,connection=mc.getConnection();
        return new ClassicClientInputBinding(new ClassicClientInputBinding.Host(){
            public boolean available(){return mc.level==level&&mc.player==player&&mc.getConnection()==connection&&level!=null&&player!=null;}
            public boolean inGame(){return available()&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();}
            public boolean endTickAllowed(){return inGame()&&!mc.isPaused();}
            public int actorId(){return mc.player==null?-1:mc.player.getId();}public long ownerEpoch(){return serverSingleEpoch;}
            public boolean categoryActivated(){return state.isActivated();}
            public boolean canUseAbility(){return state.activated&&state.overloadFine&&!state.interfering&&(!state.consumptionInProgress()||vectorParryControlValid()||brakingTouchControlValid()||newSkillControlValid());}
            public boolean terminalOpen(){return mc.screen!=null&&!MagneticItemPullSelection.ownScreen();}
            public boolean physicalDown(int key){return AcademyClient.physicalDown(key);}
            public boolean registeredMapping(String skill){return cn.academy.port.preset.PresetSkills.registeredMapping(state,skill);}
            public boolean eligible(ClassicClientInputBinding.Node node){
                if(node.key<0&&fleetOwnsPointer()||menuMouseInputBlocked(node))return false;
                if(node.skill.equals(cn.academy.port.highpower.DualPoint.ID))return dualPointDelegateEligible(mc,node.slot,node.key);
                if(node.skill.equals(cn.academy.port.highpower.CirculationRelief.ID))return circulationReliefInputs.eligible(circulationReliefGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.AutonomousVectorCircle.ID))return autonomousVectorCircleInputs.eligible(autonomousVectorCircleGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.MultiAxisReflection.ID))return multiAxisReflectionInputs.eligible(multiAxisReflectionGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.WindPressureLift.ID))return windPressureLiftInputs.eligible(windPressureLiftGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.VectorCage.ID))return vectorCageInputs.delegateEligible(vectorCageGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.CirculationListening.ID))return circulationListeningInputs.eligible(circulationListeningGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.InertialAnchor.ID))return inertialAnchorInputs.eligible(inertialAnchorGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.ZeroGapArmor.ID))return zeroGapArmorInputs.eligible(zeroGapArmorGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.DisplacedProjection.ID))return displacedProjectionInputs.eligible(displacedProjectionGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.RecoveryMark.ID))return recoveryMarkInputs.delegateEligible(recoveryMarkGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.ReturnMark.ID))return returnMarkInputs.delegateEligible(returnMarkGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.ShortInterception.ID))return shortInterceptionInputs.delegateEligible(shortInterceptionGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.CornerExchange.ID))return cornerExchangeInputs.delegateEligible(cornerExchangeGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.EmergencyReturn.ID))return emergencyReturnInputs.delegateEligible(emergencyReturnGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.MultidimensionalLandingArray.ID))return multidimensionalLandingArrayInputs.delegateEligible(multidimensionalLandingArrayGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.SpatialPincerCorridor.ID))return spatialPincerInputs.delegateEligible(spatialPincerGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.SpatialRelay.ID))return spatialRelayInputs.delegateEligible(spatialRelayGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.CrossSubstitution.ID))return crossSubstitutionInputs.delegateEligible(crossSubstitutionGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.PhaseSidestep.ID))return phaseSidestepInputs.eligible(phaseSidestepGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.VectorParry.ID))return vectorParryInputs.delegateEligible(vectorParryGates(mc,node.slot,node.key));
                if(NewSkillClient.handles(node.skill))return NewSkillClient.input(node.skill).delegateEligible(newSkillGates(mc,node.skill,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.BrakingTouch.ID))return brakingTouchInputs.delegateEligible(brakingTouchGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.DeflectionStrike.ID))return deflectionStrikeInputs.delegateEligible(deflectionStrikeGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.YawPalm.ID))return yawPalmInputs.delegateEligible(yawPalmGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.TrajectoryReturn.ID))return trajectoryReturnInputs.delegateEligible(trajectoryReturnGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.ShearWindblade.ID))return shearWindbladeInputs.delegateEligible(shearWindbladeGates(mc,node.slot,node.key));
                if(node.skill.equals(cn.academy.port.highpower.SafeLandingProbe.ID))return safeLandingProbeInputs.eligible(safeLandingProbeGates(mc,node.slot,node.key));
                boolean usable=state.canUse(node.skill)||node.skill.equals(cn.academy.port.highpower.FoldedMirrorArray.ID)
                        &&state.cooldowns.getOrDefault(node.skill,0)>0&&FoldedMirrorEffects.canRecallLocal()
                        ||node.skill.equals(cn.academy.port.highpower.RemoteScatterRelay.ID)&&state.cooldowns.getOrDefault(node.skill,0)>0&&RemoteScatterRelayEffects.canRecallLocal()
                        ||node.skill.equals(cn.academy.port.highpower.ScatterLocatorPlate.ID)&&ScatterLocatorEffects.canRecallLocal()
                        ||node.skill.equals(cn.academy.port.highpower.ParticleLaminate.ID)&&state.cooldowns.getOrDefault(node.skill,0)>0&&ParticleLaminateEffects.canRecallLocal()
                        ||node.skill.equals(cn.academy.port.highpower.MulticoreCanopy.ID)&&state.cooldowns.getOrDefault(node.skill,0)>0&&MulticoreCanopyEffects.canControlLocal();
                return inGame()&&MagneticItemPullSelection.permits(node.skill,node.slot)&&usable
                        &&cn.academy.port.preset.PresetSkills.mappedUsable(state,node.skill)&&SkillCatalog.enabled(state.category,node.skill);
            }
            public String mappedSkill(int slot){return state.presets.currentSkill(slot);}public int mappedKey(int slot){return sourceKey(SLOTS[slot].getKey());}public int slotCount(){return SLOTS.length;}
            public void rawActivation(boolean desired){if(!desired)cancelPersistentHighPowerInputs();activationState.activate(state,false,()->{},value->request("activate_state",Boolean.toString(value)));}
            public void replaceOverrides(int[] keys){overrideKeys=keys.clone();}
            public long down(ClassicClientInputBinding.Node node){long input=press(node);CastPoseClient.down(node,input);return input;}
            public void up(ClassicClientInputBinding.Node node,long input){CastPoseClient.up(node);terminal(node,input,false);}
            public void abort(ClassicClientInputBinding.Node node,long input){CastPoseClient.abort(node);terminal(node,input,true);}
            public boolean cancelPending(ClassicClientInputBinding.Node node,long input){if(input<=0||cn.academy.port.core.TargetedContextTermination.Skill.find(node.skill)==null)return false;request("pending_context_abort",node.skill+":"+input);if(node.skill.equals("flashing"))ClassicTeleporterFinalEffects.endLocal(node.skill);else if(node.skill.equals("storm_wing"))ClassicVectorCombatEffects.abortLocal(node.skill);else if(node.skill.equals("vec_deviation"))ClassicVectorStarterEffects.endLocal(node.skill);else if(node.skill.equals("vec_reflection"))ClassicVectorFinalEffects.endLocal(node.skill);return true;}
            public boolean railgunCharges(ClassicClientInputBinding.Node node){return node.input()>=0&&(mc.player.getMainHandItem().is(net.minecraft.world.item.Items.IRON_INGOT)||mc.player.getMainHandItem().is(net.minecraft.world.item.Items.IRON_BLOCK));}
            // Source resets charge locally; this scoped modern cancellation protects the independent server timer.
            public void railgunEnd(ClassicClientInputBinding.Node node,boolean abort){request("railgun_abort","");}
            public void direction(ClassicClientInputBinding.ContextIdentity context,int direction,ClassicClientInputBinding.Edge edge,boolean silent){
                if(context.skill.equals("flashing"))ClassicTeleporterFinalEffects.direction(context.input,context.token,direction,edge,silent);
                else if(!silent)request("storm_direction",context.node.slot+":"+context.input+":"+(edge==ClassicClientInputBinding.Edge.DOWN?direction-1:-1));
            }
            public void localContextEnd(ClassicClientInputBinding.ContextIdentity context){if(context.skill.equals("flashing"))ClassicTeleporterFinalEffects.terminateContext(context.input,context.token);else if(context.skill.equals("storm_wing"))ClassicVectorCombatEffects.abortLocal(context.skill);else if(context.skill.equals("vec_deviation"))ClassicVectorStarterEffects.terminateContext(context.input,context.token);else if(context.skill.equals("vec_reflection"))ClassicVectorFinalEffects.terminateContext(context.input,context.token);}
            public void contextAbort(cn.academy.port.core.TargetedContextTermination.Request identity){request(cn.academy.port.core.TargetedContextTermination.ACTION,identity.wire());}
            public void modernCleanup(java.util.Collection<ClassicClientInputBinding.Node> defaults){for(var node:defaults)cleanupModern(node.skill);}
            public void rejectedSingle(ClassicClientInputBinding.Node node,long input,long capturedOwnerEpoch){CastPoseClient.abort(node);if(node.skill.equals("ground_shock"))ClassicGroundShockEffects.abortLocal(input,capturedOwnerEpoch);if(node.skill.equals("charging"))ClassicChargingEffects.endLocal(input,capturedOwnerEpoch);if(node.skill.equals("mag_movement")||node.skill.equals("mag_manip"))ClassicMagneticEffects.abortLocal(node.skill,input,capturedOwnerEpoch);if(ClassicFirstSkillEffects.ownsSingleKey(node.skill))ClassicFirstSkillEffects.retireLocal(node.skill,input,capturedOwnerEpoch);}
        });
    }
    /** Existing prepress close is an explicit modern policy, not a source delegate callback. */
    private static long press(ClassicClientInputBinding.Node node){
        String skill=node.skill;int slot=node.slot;
        if(!skill.equals("flashing")&&ClassicTeleporterFinalEffects.delegateActive("flashing")){var owned=binding==null?null:binding.context("flashing");if(owned!=null)ClassicTeleporterFinalEffects.hideLocal("flashing");}
        if(skill.equals(cn.academy.port.highpower.BeamStep.ID)){
            var mc=Minecraft.getInstance();
            if(persistentInputBlocked(mc)||mc.screen!=null||mc.getCameraEntity()!=mc.player||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            if(!BeamStepClientMotion.canStartLocal()){
                if(mc.player!=null)mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(BeamStepClientMotion.startBlocker()),true);
                return 0;
            }
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            if(!BeamStepClientMotion.captureInput(input,identity.epoch()))return 0;
            request("hp_beam_step_tap",identity.wire());
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.DualPoint.ID)){
            if(persistentInputBlocked(Minecraft.getInstance())||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            var gates=dualPointGates(Minecraft.getInstance(),slot,node.key);
            if(!dualPointInputs.eligible(gates))return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            if(!dualPointInputs.tap(identity,gates))return 0;
            request(down(Minecraft.getInstance().options.keyShift)?"hp_dual_point_mark":"hp_dual_point_tap",identity.wire());
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.BeamEtching.ID)){
            var mc=Minecraft.getInstance();
            if(persistentInputBlocked(mc)||mc.screen!=null||mc.getCameraEntity()!=mc.player||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            sendPersistentCancellation("hp_beam_etching_abort",beamEtchingInputs.cancel(),AcademyClient::cancelBeamEtchingLocally);
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            beamEtchingInputs.begin(identity,node.key);
            request("hp_beam_etching_start",identity.wire());
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.PointMeltdown.ID)){
            var mc=Minecraft.getInstance();
            if(persistentInputBlocked(mc)||mc.screen!=null||mc.getCameraEntity()!=mc.player||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            sendPersistentCancellation("hp_point_meltdown_abort",pointMeltdownInputs.cancel(),AcademyClient::cancelPointMeltdownLocally);
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            pointMeltdownInputs.begin(identity,node.key);
            request("hp_point_meltdown_start",identity.wire());
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.DisintegrationSweep.ID)){
            var mc=Minecraft.getInstance();
            if(disintegrationSweepInputs.holding()||disintegrationSweepInputs.queuedStart()!=null)return 0;
            if(persistentInputBlocked(mc)||mc.screen!=null||mc.getCameraEntity()!=mc.player||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            disintegrationSweepInputs.begin(identity,node.key,down(mc.options.keyShift),disintegrationSweepDispatchContext(mc));
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.FoldedMirrorArray.ID)){
            var mc=Minecraft.getInstance();
            if(foldedMirrorInputs.holding()||foldedMirrorInputs.queuedStart()!=null)return 0;
            if(persistentInputBlocked(mc)||mc.screen!=null||mc.getCameraEntity()!=mc.player||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            foldedMirrorInputs.begin(identity,node.key,down(mc.options.keyShift),foldedMirrorDispatchContext(mc));
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.RemoteScatterRelay.ID)){
            var mc=Minecraft.getInstance();
            if(remoteScatterRelayInputs.holding()||remoteScatterRelayInputs.queuedStart()!=null)return 0;
            if(persistentInputBlocked(mc)||mc.screen!=null||mc.getCameraEntity()!=mc.player||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            remoteScatterRelayInputs.begin(identity,node.key,down(mc.options.keyShift),remoteScatterRelayDispatchContext(mc));
            RemoteScatterRelayEffects.clearResultNote();
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.ScatterLocatorPlate.ID)){
            var mc=Minecraft.getInstance();
            if(scatterLocatorInputs.holding()||scatterLocatorInputs.queuedStart()!=null)return 0;
            if(persistentInputBlocked(mc)||mc.screen!=null||mc.getCameraEntity()!=mc.player||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            scatterLocatorInputs.begin(identity,node.key,down(mc.options.keyShift),scatterLocatorDispatchContext(mc));
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.ParticleLaminate.ID)){
            var mc=Minecraft.getInstance();
            if(particleLaminateInputs.holding()||particleLaminateInputs.queuedStart()!=null)return 0;
            if(persistentInputBlocked(mc)||mc.screen!=null||mc.getCameraEntity()!=mc.player||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            particleLaminateInputs.begin(identity,node.key,down(mc.options.keyShift),particleLaminateDispatchContext(mc));
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.MulticoreCanopy.ID)){
            var mc=Minecraft.getInstance();
            if(multicoreCanopyInputs.holding()||multicoreCanopyInputs.queuedStart()!=null)return 0;
            if(persistentInputBlocked(mc)||mc.screen!=null||mc.getCameraEntity()!=mc.player||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            multicoreCanopyInputs.begin(identity,node.key,multicoreCanopyDispatchContext(mc));
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.DissipationCurtain.ID)){
            var mc=Minecraft.getInstance();
            if(dissipationCurtainInputs.holding()||dissipationCurtainInputs.queuedStart()!=null)return 0;
            if(persistentInputBlocked(mc)||mc.screen!=null||mc.getCameraEntity()!=mc.player||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            dissipationCurtainInputs.begin(identity,node.key,dissipationCurtainDispatchContext(mc));
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.DisplacedProjection.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!displacedProjectionInputs.press(identity,displacedProjectionGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.RecoveryMark.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!recoveryMarkInputs.press(identity,recoveryMarkGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.ReturnMark.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!returnMarkInputs.press(identity,returnMarkGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.ShortInterception.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!shortInterceptionInputs.press(identity,shortInterceptionGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.CornerExchange.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!cornerExchangeInputs.press(identity,cornerExchangeGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.TacticalCoordinateFormation.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!TacticalCoordinateClient.expectOpen(identity))return 0;
            singleInputCounter++;
            request("hp_tactical_coordinate_open",identity.wire());
            return singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.EmergencyReturn.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!emergencyReturnInputs.press(identity,emergencyReturnGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.MultidimensionalLandingArray.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!multidimensionalLandingArrayInputs.press(identity,multidimensionalLandingArrayGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.SpatialPincerCorridor.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!spatialPincerInputs.press(identity,spatialPincerGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.SpatialRelay.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!spatialRelayInputs.press(identity,spatialRelayGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.CrossSubstitution.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!crossSubstitutionInputs.press(identity,crossSubstitutionGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.VectorParry.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!vectorParryInputs.press(identity,vectorParryGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(NewSkillClient.handles(skill)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!NewSkillClient.input(skill).press(identity,newSkillGates(mc,skill,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.BrakingTouch.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!pressBrakingTouchInput(identity,brakingTouchGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.DeflectionStrike.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!deflectionStrikeInputs.press(identity,deflectionStrikeGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.YawPalm.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!yawPalmInputs.press(identity,yawPalmGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.TrajectoryReturn.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!trajectoryReturnInputs.press(identity,trajectoryReturnGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.VectorCage.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!vectorCageInputs.press(identity,vectorCageGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.ShearWindblade.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!shearWindbladeInputs.press(identity,shearWindbladeGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.PhaseSidestep.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            if(!phaseSidestepInputs.press(identity,phaseSidestepGates(mc,slot,node.key)))return 0;
            return ++singleInputCounter;
        }
        if(skill.equals(cn.academy.port.highpower.SafeLandingProbe.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            var press=safeLandingProbeInputs.press(identity,safeLandingProbeGates(mc,slot,node.key));if(press==null)return 0;
            if(press.dispatch()){singleInputCounter++;request("hp_safe_landing_probe",press.identity().wire());}
            return press.identity().input();
        }
        if(skill.equals(cn.academy.port.highpower.CirculationRelief.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            var press=circulationReliefInputs.press(identity,circulationReliefGates(mc,slot,node.key));if(press==null)return 0;
            if(press.dispatch()){singleInputCounter++;request("circulation_relief",press.identity().wire());}
            return press.identity().input();
        }
        if(skill.equals(cn.academy.port.highpower.AutonomousVectorCircle.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            var press=autonomousVectorCircleInputs.press(identity,autonomousVectorCircleGates(mc,slot,node.key));if(press==null)return 0;
            if(press.dispatch()){singleInputCounter++;request("autonomous_vector_circle",press.identity().wire());}
            return press.identity().input();
        }
        if(skill.equals(cn.academy.port.highpower.MultiAxisReflection.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            var press=multiAxisReflectionInputs.press(identity,multiAxisReflectionGates(mc,slot,node.key));if(press==null)return 0;
            if(press.dispatch()){singleInputCounter++;request("multi_axis_reflection_start",press.identity().wire());}
            return press.identity().input();
        }
        if(skill.equals(cn.academy.port.highpower.WindPressureLift.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var prior=windPressureLiftInputs.acceptedIdentity();
            if(prior==null&&windPressureLiftInputs.current()==null){
                String blocker=WindPressureLiftClientMotion.startBlocker();
                if(!blocker.isEmpty()){if(mc.player!=null)mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(blocker),true);return 0;}
            }
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            var press=windPressureLiftInputs.press(identity,windPressureLiftGates(mc,slot,node.key));if(press==null)return 0;
            if(press.dispatch()){
                if(prior!=null)cancelWindPressureLiftLocally(prior);
                else if(!WindPressureLiftClientMotion.captureInput(press.identity().input(),press.identity().epoch())){windPressureLiftInputs.cancel(press.identity());return 0;}
                singleInputCounter++;request("wind_pressure_lift_start",press.identity().wire());
            }
            return press.identity().input();
        }
        if(skill.equals(cn.academy.port.highpower.CirculationListening.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            var press=circulationListeningInputs.press(identity,circulationListeningGates(mc,slot,node.key));if(press==null)return 0;
            if(press.dispatch()){singleInputCounter++;request("circ_listen_start",press.identity().wire());}
            return press.identity().input();
        }
        if(skill.equals(cn.academy.port.highpower.InertialAnchor.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            var press=inertialAnchorInputs.press(identity,inertialAnchorGates(mc,slot,node.key));if(press==null)return 0;
            if(press.dispatch()){singleInputCounter++;request("inertial_anchor",press.identity().wire());}
            return press.identity().input();
        }
        if(skill.equals(cn.academy.port.highpower.ZeroGapArmor.ID)){
            var mc=Minecraft.getInstance();
            if(singleInputCounter==Long.MAX_VALUE||mc.isPaused())return 0;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,singleInputCounter+1,node.actorId(),node.ownerEpoch());
            var press=zeroGapArmorInputs.press(identity,zeroGapArmorGates(mc,slot,node.key));if(press==null)return 0;
            if(press.dispatch()){singleInputCounter++;request("hp_zero_gap_armor_toggle",press.identity().wire());}
            return press.identity().input();
        }
        if(skill.equals(cn.academy.port.highpower.ScatterPlates.ID)){
            if(persistentInputBlocked(Minecraft.getInstance())||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            scatterPlateInputs.tap(identity);
            request("hp_scatter_plates_toggle",identity.wire());
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.MagneticDomain.ID)){
            if(persistentInputBlocked(Minecraft.getInstance())||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            magneticDomainInputs.tap(identity);
            request("hp_magnetic_domain_toggle",identity.wire());
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.RailThunderArray.ID)){
            if(persistentInputBlocked(Minecraft.getInstance())||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            // Marks belong to one server context; another preparatory press must not abort that context.
            railArrayInputs.begin(identity,node.key);
            request("hp_rail_thunder_array_press",identity.wire());
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.PotentialBuffer.ID)){
            if(persistentInputBlocked(Minecraft.getInstance())||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            potentialBufferInputs.tap(identity);
            request("hp_potential_buffer_toggle",identity.wire());
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.ThermalShell.ID)){
            if(persistentInputBlocked(Minecraft.getInstance())||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            thermalShellInputs.tap(identity);
            request("hp_thermal_shell_toggle",identity.wire());
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.HighPowerSkills.THUNDER_NET)){
            if(persistentInputBlocked(Minecraft.getInstance())||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            sendPersistentCancellation("hp_thunder_net_abort",thunderNetInputs.cancel(),AcademyClient::cancelThunderNetLocally);
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            thunderNetInputs.begin(identity,node.key);
            request("hp_thunder_net_begin",identity.wire());
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.HighPowerSkills.IRON_SAND_LANCE)){
            if(persistentInputBlocked(Minecraft.getInstance())||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            sendPersistentCancellation("hp_iron_lance_abort",ironLanceInputs.cancel(),AcademyClient::cancelIronLanceLocally);
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            // Select once from the configured physical crouch key. No classic control is remapped.
            boolean orbit=down(Minecraft.getInstance().options.keyShift);
            ironLanceInputs.begin(identity,orbit);
            Minecraft.getInstance().player.displayClientMessage(net.minecraft.network.chat.Component.translatable(orbit?"academy.highpower.iron_lance.mode_orbit":"academy.highpower.iron_lance.mode_attack"),false);
            request(orbit?"hp_iron_lance_orbit_begin":"hp_iron_lance_begin",identity.wire());
            return input;
        }
        if(skill.equals(cn.academy.port.highpower.HighPowerSkills.MAGNETIC_ITEM_PULL)){
            if(persistentInputBlocked(Minecraft.getInstance())||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            sendPersistentCancellation("hp_item_pull_abort",itemPullInputs.cancel(),AcademyClient::cancelItemPullLocally);
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            // Capture the configured physical crouch key before setScreen clears logical mappings.
            boolean arrows=down(Minecraft.getInstance().options.keyShift);
            itemPullInputs.remember(identity);
            if(arrows){
                request("hp_item_separate_begin",identity.wire());
                if(MagneticItemPullSelection.openArrows(identity))return input;
            }else{
                request("hp_item_pull_begin",identity.wire());
                if(MagneticItemPullSelection.open(identity))return input;
            }
            cancelItemPullInput();return 0;
        }
        if(skill.equals(cn.academy.port.highpower.HighPowerSkills.POLARITY_TETHER)||skill.equals(cn.academy.port.highpower.HighPowerSkills.CONDUCTIVE_CIRCUIT)||skill.equals(cn.academy.port.highpower.HighPowerSkills.GROUNDING_RETURN)||skill.equals(cn.academy.port.highpower.HighPowerSkills.MAGNETIC_GROUND_STEP)){
            if(persistentInputBlocked(Minecraft.getInstance())||singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            if(skill.equals(cn.academy.port.highpower.HighPowerSkills.MAGNETIC_GROUND_STEP)&&!groundStepStartAllowed())return 0;
            long input=++singleInputCounter;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch());
            if(skill.equals(cn.academy.port.highpower.HighPowerSkills.POLARITY_TETHER)){tetherInputs.remember(identity);request("hp_tether_tap",identity.wire());}
            else if(skill.equals(cn.academy.port.highpower.HighPowerSkills.CONDUCTIVE_CIRCUIT)){circuitInputs.remember(identity);request("hp_circuit_tap",identity.wire());}
            else if(skill.equals(cn.academy.port.highpower.HighPowerSkills.GROUNDING_RETURN)){groundingInputs.remember(identity);request("hp_ground_start",identity.wire());}
            else {groundStepInputs.remember(identity);request("hp_ground_step_tap",identity.wire());}
            return input;
        }
        if (skill.equals(cn.academy.port.highpower.HighPowerSkills.MAGNETIC_BRACER)||skill.equals(cn.academy.port.highpower.HighPowerSkills.CHARGE_DECOY)) {
            if(singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            if(skill.equals(cn.academy.port.highpower.HighPowerSkills.MAGNETIC_BRACER))BracerDecorState.pressed(input);
            request(skill.equals(cn.academy.port.highpower.HighPowerSkills.MAGNETIC_BRACER)?"hp_bracer_press":"hp_decoy_press",new cn.academy.port.highpower.HighPowerInput.Input(slot,input,node.actorId(),node.ownerEpoch()).wire());
            return input;
        }
        var legacy=cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(skill);
        if(legacy!=null){
            if(singleInputCounter==Long.MAX_VALUE||serverSingleEpoch<=0||node.actorId()<0)return 0;
            long input=++singleInputCounter;
            if(skill.equals("ground_shock"))ClassicGroundShockEffects.startLocal(input);
            if(ClassicFirstSkillEffects.ownsSingleKey(skill))ClassicFirstSkillEffects.startLocal(skill,input);
            request(cn.academy.port.core.LegacySingleKeyProtocol.PRESS,new cn.academy.port.core.LegacySingleKeyProtocol.StartRequest(slot,legacy,input,node.actorId(),node.ownerEpoch()).wire());
            return input;
        }
        long input=0;
        if(ClassicVectorCombatEffects.owns(skill))input=ClassicVectorCombatEffects.startLocal(skill);
        if(ClassicVectorFinalEffects.owns(skill))input=ClassicVectorFinalEffects.startLocal(skill);
        if(ClassicVectorStarterEffects.owns(skill))input=ClassicVectorStarterEffects.startLocal(skill);
        if(skill.equals("body_intensify"))input=ClassicBodyIntensifyEffects.startLocal();
        if(skill.equals("thunder_clap"))input=ClassicThunderClapEffects.startLocal();
        if(ClassicMeltdownerStarterEffects.owns(skill))input=ClassicMeltdownerStarterEffects.startLocal(skill);
        if(ClassicMeltdownerBeamEffects.owns(skill))input=ClassicMeltdownerBeamEffects.startLocal(skill);
        if(ClassicMeltdownerLateEffects.owns(skill))input=ClassicMeltdownerLateEffects.startLocal(skill);
        if(ClassicTeleporterProgressionEffects.owns(skill))input=ClassicTeleporterProgressionEffects.startLocal(skill);
        if(ClassicTeleporterFinalEffects.owns(skill))input=ClassicTeleporterFinalEffects.startLocal(skill);
        var coin=skill.equals("railgun")?ClassicCoinEffects.attempt():java.util.OptionalLong.empty();
        if(input>0)request("slot_press_token",slot+":"+input);else if(coin.isPresent())request("coin_attempt",Long.toString(coin.getAsLong()));else request("slot_press",Integer.toString(slot));return coin.isPresent()?-1:input;
    }
    private static final java.util.Set<String> TOKEN_TERMINALS=java.util.Set.of("dir_blast","blood_retro","plasma_cannon","vec_accel","jet_engine","electron_missile","penetrate_teleport","mark_teleport","flesh_ripping","shift_tp");
    /** Captured node skill/input choose callbacks; no current-preset identity resolution here. */
    private static void terminal(ClassicClientInputBinding.Node node,long input,boolean abort){
        String skill=node.skill;
        if(skill.equals(cn.academy.port.highpower.BeamStep.ID)){
            if(abort&&input>0)BeamStepClientMotion.abortLocal(input,node.ownerEpoch());
            return;
        }
        if(skill.equals(cn.academy.port.highpower.DualPoint.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_dual_point_abort",dualPointInputs.cancel(identity),AcademyClient::cancelDualPointLocally);
            else dualPointInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.BeamEtching.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_beam_etching_abort",beamEtchingInputs.cancel(identity),AcademyClient::cancelBeamEtchingLocally);
            else releaseBeamEtchingInput(beamEtchingInputs.release(identity));
            return;
        }
        if(skill.equals(cn.academy.port.highpower.PointMeltdown.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_point_meltdown_abort",pointMeltdownInputs.cancel(identity),AcademyClient::cancelPointMeltdownLocally);
            else releasePointMeltdownInput(pointMeltdownInputs.release(identity));
            return;
        }
        if(skill.equals(cn.academy.port.highpower.DisintegrationSweep.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_disintegration_sweep_abort",disintegrationSweepInputs.cancel(identity),AcademyClient::cancelDisintegrationSweepLocally);
            else releaseDisintegrationSweepInput(disintegrationSweepInputs.release(identity));
            return;
        }
        if(skill.equals(cn.academy.port.highpower.FoldedMirrorArray.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_folded_mirror_abort",foldedMirrorInputs.cancel(identity),AcademyClient::cancelFoldedMirrorLocally);
            else releaseFoldedMirrorInput(foldedMirrorInputs.release(identity));
            return;
        }
        if(skill.equals(cn.academy.port.highpower.RemoteScatterRelay.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_remote_relay_abort",remoteScatterRelayInputs.cancel(identity),AcademyClient::cancelRemoteScatterRelayLocally);
            else releaseRemoteScatterRelayInput(remoteScatterRelayInputs.release(identity));
            return;
        }
        if(skill.equals(cn.academy.port.highpower.ScatterLocatorPlate.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_scatter_locator_abort",scatterLocatorInputs.cancel(identity),AcademyClient::cancelScatterLocatorLocally);
            else releaseScatterLocatorInput(scatterLocatorInputs.release(identity));
            return;
        }
        if(skill.equals(cn.academy.port.highpower.ParticleLaminate.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_particle_laminate_abort",particleLaminateInputs.cancel(identity),AcademyClient::cancelParticleLaminateLocally);
            else releaseParticleLaminateInput(particleLaminateInputs.release(identity));
            return;
        }
        if(skill.equals(cn.academy.port.highpower.MulticoreCanopy.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_multicore_canopy_abort",multicoreCanopyInputs.cancel(identity),AcademyClient::cancelMulticoreCanopyLocally);
            else releaseMulticoreCanopyInput(multicoreCanopyInputs.release(identity));
            return;
        }
        if(skill.equals(cn.academy.port.highpower.DissipationCurtain.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_dissipation_curtain_abort",dissipationCurtainInputs.cancel(identity),AcademyClient::cancelDissipationCurtainLocally);
            else releaseDissipationCurtainInput(dissipationCurtainInputs.release(identity));
            return;
        }
        if(skill.equals(cn.academy.port.highpower.DisplacedProjection.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)displacedProjectionInputs.cancel(identity,AcademyClient::sendDisplacedProjectionCancellation);
            else displacedProjectionInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.RecoveryMark.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)recoveryMarkInputs.cancel(identity,AcademyClient::sendRecoveryMarkCancellation);
            else recoveryMarkInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.ReturnMark.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)returnMarkInputs.cancel(identity,AcademyClient::sendReturnMarkCancellation);
            else returnMarkInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.ShortInterception.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)shortInterceptionInputs.cancel(identity,AcademyClient::sendShortInterceptionCancellation);
            else shortInterceptionInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.CornerExchange.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)cornerExchangeInputs.cancel(identity,AcademyClient::sendCornerExchangeCancellation);
            else cornerExchangeInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.TacticalCoordinateFormation.ID))return;
        if(skill.equals(cn.academy.port.highpower.EmergencyReturn.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)emergencyReturnInputs.cancel(identity,AcademyClient::sendEmergencyReturnCancellation);
            else emergencyReturnInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.MultidimensionalLandingArray.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort){multidimensionalLandingArrayInputs.cancel(identity,AcademyClient::sendMultidimensionalLandingArrayCancellation);}
            else multidimensionalLandingArrayInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.SpatialPincerCorridor.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort){if(!SpatialPincerClient.ownsPicker())spatialPincerInputs.cancel(identity,AcademyClient::sendSpatialPincerCancellation);}
            else spatialPincerInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.SpatialRelay.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)spatialRelayInputs.cancel(identity,AcademyClient::sendSpatialRelayCancellation);
            else spatialRelayInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.CrossSubstitution.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)crossSubstitutionInputs.cancel(identity,AcademyClient::sendCrossSubstitutionCancellation);
            else crossSubstitutionInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.VectorParry.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)vectorParryInputs.cancel(identity,AcademyClient::sendVectorParryCancellation);
            else if(vectorParryInputs.release(identity))VectorParryEffects.abortLocal(input,node.ownerEpoch());
            return;
        }
        if(NewSkillClient.handles(skill)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)NewSkillClient.input(skill).cancel(identity,i->sendNewSkillCancellation(skill,i));
            else NewSkillClient.input(skill).release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.BrakingTouch.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)brakingTouchInputs.cancel(identity,AcademyClient::sendBrakingTouchCancellation);
            else if(brakingTouchInputs.release(identity))BrakingTouchEffects.abortLocal(input,node.ownerEpoch());
            return;
        }
        if(skill.equals(cn.academy.port.highpower.DeflectionStrike.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)deflectionStrikeInputs.cancel(identity,AcademyClient::sendDeflectionStrikeCancellation);
            else deflectionStrikeInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.YawPalm.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)yawPalmInputs.cancel(identity,AcademyClient::sendYawPalmCancellation);
            else yawPalmInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.TrajectoryReturn.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)trajectoryReturnInputs.cancel(identity,AcademyClient::sendTrajectoryReturnCancellation);
            else trajectoryReturnInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.VectorCage.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)vectorCageInputs.cancel(identity,AcademyClient::sendVectorCageCancellation);
            else vectorCageInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.ShearWindblade.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)shearWindbladeInputs.cancel(identity,AcademyClient::sendShearWindbladeCancellation);
            else shearWindbladeInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.PhaseSidestep.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)phaseSidestepInputs.cancel(identity,AcademyClient::sendPhaseSidestepCancellation);
            else phaseSidestepInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.SafeLandingProbe.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)safeLandingProbeInputs.cancel(identity,AcademyClient::sendSafeLandingProbeCancellation);
            else safeLandingProbeInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.CirculationRelief.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("circulation_relief_end",circulationReliefInputs.cancel(identity),AcademyClient::cancelCirculationReliefLocally);
            else circulationReliefInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.AutonomousVectorCircle.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("autonomous_vector_circle_end",autonomousVectorCircleInputs.cancel(identity),AcademyClient::cancelAutonomousVectorCircleLocally);
            else autonomousVectorCircleInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.MultiAxisReflection.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("multi_axis_reflection_end",multiAxisReflectionInputs.cancel(identity),AcademyClient::cancelMultiAxisReflectionLocally);
            else multiAxisReflectionInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.WindPressureLift.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("wind_pressure_lift_end",windPressureLiftInputs.cancel(identity),AcademyClient::cancelWindPressureLiftLocally);
            else windPressureLiftInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.CirculationListening.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("circ_listen_abort",circulationListeningInputs.cancel(identity),AcademyClient::cancelCirculationListeningLocally);
            else {circulationListeningInputs.release(identity);if(singleOwnerEpochMatches(identity.epoch()))request("circ_listen_release",identity.wire());}
            return;
        }
        if(skill.equals(cn.academy.port.highpower.InertialAnchor.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("inertial_anchor_end",inertialAnchorInputs.cancel(identity),AcademyClient::cancelInertialAnchorLocally);
            else inertialAnchorInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.ZeroGapArmor.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_zero_gap_armor_abort",zeroGapArmorInputs.cancel(identity),AcademyClient::cancelZeroGapArmorLocally);
            else zeroGapArmorInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.ScatterPlates.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_scatter_plates_abort",scatterPlateInputs.cancel(identity),AcademyClient::cancelScatterPlatesLocally);
            else scatterPlateInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.MagneticDomain.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_magnetic_domain_abort",magneticDomainInputs.cancel(identity),AcademyClient::cancelMagneticDomainLocally);
            else magneticDomainInputs.release(identity);
            return;
        }
        if(skill.equals(cn.academy.port.highpower.RailThunderArray.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_rail_thunder_array_abort",railArrayInputs.cancel(identity),AcademyClient::cancelRailArrayLocally);
            else releaseRailArrayInput(railArrayInputs.release(identity));
            return;
        }
        if(skill.equals(cn.academy.port.highpower.PotentialBuffer.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_potential_buffer_abort",potentialBufferInputs.cancel(identity),AcademyClient::cancelPotentialBufferLocally);
            else potentialBufferInputs.release(identity); // A toggle persists after ordinary key release.
            return;
        }
        if(skill.equals(cn.academy.port.highpower.ThermalShell.ID)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_thermal_shell_abort",thermalShellInputs.cancel(identity),AcademyClient::cancelThermalShellLocally);
            else thermalShellInputs.release(identity); // A toggle persists after ordinary key release.
            return;
        }
        if(skill.equals(cn.academy.port.highpower.HighPowerSkills.THUNDER_NET)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_thunder_net_abort",thunderNetInputs.cancel(identity),AcademyClient::cancelThunderNetLocally);
            else releaseThunderNetInput(thunderNetInputs.release(identity));
            return;
        }
        if(skill.equals(cn.academy.port.highpower.HighPowerSkills.IRON_SAND_LANCE)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(abort)sendPersistentCancellation("hp_iron_lance_abort",ironLanceInputs.cancel(identity),AcademyClient::cancelIronLanceLocally);
            else if(ironLanceInputs.release(identity)!=null&&singleOwnerEpochMatches(identity.epoch()))request("hp_iron_lance_release",identity.wire());
            return;
        }
        if(skill.equals(cn.academy.port.highpower.HighPowerSkills.MAGNETIC_ITEM_PULL)){
            if(input<=0||node.actorId()<0)return;
            var identity=new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch());
            if(MagneticItemPullSelection.arrows(identity)){
                if(MagneticItemPullSelection.releaseArrows(identity,abort)&&singleOwnerEpochMatches(identity.epoch()))request("hp_item_separate_release",identity.wire());
                return;
            }
            var candidates=MagneticItemPullSelection.release(identity,abort);
            if(candidates==null)return;
            if(candidates.isEmpty()){
                cancelItemPullInput();
                var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("academy.highpower.item_pull.empty_selection"),false);
            }else if(singleOwnerEpochMatches(identity.epoch())){
                PacketDistributor.sendToServer(new cn.academy.port.highpower.MagneticItemPull.CandidateRequest(identity.wire(),candidates));
            }
            return;
        }
        if(abort&&skill.equals(cn.academy.port.highpower.HighPowerSkills.CHARGE_DECOY))ElectromasterChargeDecoyEffects.abortLocal(input);
        if(skill.equals(cn.academy.port.highpower.HighPowerSkills.MAGNETIC_BRACER))BracerDecorState.released();
        if (skill.equals(cn.academy.port.highpower.HighPowerSkills.MAGNETIC_BRACER)||skill.equals(cn.academy.port.highpower.HighPowerSkills.CHARGE_DECOY)) {
            if(input>0&&node.actorId()>=0&&singleOwnerEpochMatches(node.ownerEpoch()))
                request(skill.equals(cn.academy.port.highpower.HighPowerSkills.MAGNETIC_BRACER)?"hp_bracer_stop":abort?"hp_decoy_abort":"hp_decoy_release",new cn.academy.port.highpower.HighPowerInput.Input(node.slot,input,node.actorId(),node.ownerEpoch()).wire());
            return;
        }
        if(ClassicVectorCombatEffects.owns(skill)){if(abort)ClassicVectorCombatEffects.abortLocal(skill);else ClassicVectorCombatEffects.releaseLocal(skill);}
        if(ClassicVectorFinalEffects.owns(skill)){if(abort)ClassicVectorFinalEffects.endLocal(skill);else ClassicVectorFinalEffects.releaseLocal(skill);}
        if(ClassicVectorStarterEffects.owns(skill)){if(abort)ClassicVectorStarterEffects.endLocal(skill);else ClassicVectorStarterEffects.releaseLocal(skill);}
        if(ClassicMeltdownerStarterEffects.owns(skill))ClassicMeltdownerStarterEffects.endLocal(skill);
        if(ClassicMeltdownerBeamEffects.owns(skill))ClassicMeltdownerBeamEffects.endLocal(skill);
        if(ClassicMeltdownerLateEffects.owns(skill)){if(abort)ClassicMeltdownerLateEffects.endLocal(skill);else ClassicMeltdownerLateEffects.releaseLocal(skill);}
        if(ClassicTeleporterProgressionEffects.owns(skill))ClassicTeleporterProgressionEffects.endLocal(skill);
        if(ClassicTeleporterFinalEffects.owns(skill))ClassicTeleporterFinalEffects.endLocal(skill);
        if(skill.equals("ground_shock"))ClassicGroundShockEffects.abortLocal(input);
        if(skill.equals("body_intensify")&&abort)ClassicBodyIntensifyEffects.abortLocal();
        if(skill.equals("thunder_clap"))ClassicThunderClapEffects.abortLocal();
        if(skill.equals("mag_movement")||abort&&skill.equals("mag_manip"))ClassicMagneticEffects.abortLocal(skill,input);
        if(ClassicFirstSkillEffects.ownsSingleKey(skill))ClassicFirstSkillEffects.endLocal(skill,input,abort);
        var legacy=cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(skill);
        if(legacy!=null){if(input>0&&!singleInputRetired(skill,input)&&node.actorId()>=0&&singleOwnerEpochMatches(node.ownerEpoch())){long token=node.acceptedToken();if(token>0)request(abort?cn.academy.port.core.LegacySingleKeyProtocol.ABORT:cn.academy.port.core.LegacySingleKeyProtocol.RELEASE,new cn.academy.port.core.LegacySingleKeyProtocol.AcceptedRequest(legacy,input,node.actorId(),node.ownerEpoch(),token).wire());else request(abort?cn.academy.port.core.LegacySingleKeyProtocol.PENDING_ABORT:cn.academy.port.core.LegacySingleKeyProtocol.PENDING_RELEASE,new cn.academy.port.core.LegacySingleKeyProtocol.PendingRequest(legacy,input,node.actorId(),node.ownerEpoch()).wire());}}
        else if(TOKEN_TERMINALS.contains(skill)){if(input>0)request(abort?"slot_abort_token":"slot_release_token",node.slot+":"+input);}
        else request(abort?"slot_abort":"slot_release",Integer.toString(node.slot));
    }
    private static void cleanupModern(String skill){
        if(skill.equals(cn.academy.port.highpower.HighPowerSkills.MAGNETIC_ITEM_PULL))MagneticItemPullSelection.cancel();
        if(skill.equals(cn.academy.port.highpower.HighPowerSkills.CHARGE_DECOY))ElectromasterChargeDecoyEffects.clear();
        if(ClassicVectorCombatEffects.owns(skill)&&!skill.equals("storm_wing"))ClassicVectorCombatEffects.abortLocal(skill);
        if(skill.equals("plasma_cannon"))ClassicVectorFinalEffects.endLocal(skill);
        if(skill.equals("vec_accel"))ClassicVectorStarterEffects.endLocal(skill);
        if(ClassicMeltdownerStarterEffects.owns(skill))ClassicMeltdownerStarterEffects.endLocal(skill);
        if(ClassicMeltdownerBeamEffects.owns(skill))ClassicMeltdownerBeamEffects.endLocal(skill);
        if(ClassicMeltdownerLateEffects.owns(skill))ClassicMeltdownerLateEffects.endLocal(skill);
        if(ClassicTeleporterProgressionEffects.owns(skill))ClassicTeleporterProgressionEffects.endLocal(skill);
        if(ClassicTeleporterFinalEffects.owns(skill))ClassicTeleporterFinalEffects.endLocal(skill);
        if(skill.equals("ground_shock"))ClassicGroundShockEffects.abortLocal();
        if(skill.equals("body_intensify"))ClassicBodyIntensifyEffects.abortLocal();
        if(skill.equals("thunder_clap"))ClassicThunderClapEffects.abortLocal();
        if(java.util.Set.of("mag_movement","mag_manip").contains(skill))ClassicMagneticEffects.abortLocal(skill);
    }
    /** Snapshot fallback keeps accepted context V ownership/order independent from cosmetic visibility. */
    private static void modernSnapshotFallback(){if(!state.activated||!mappingSignature.equals(mappings()))cancelPersistentHighPowerInputs();if(binding!=null)binding.modernSnapshotFallback();mappingSignature=mappings();}
    private static void discardBinding(){singleInputEpoch++;serverSingleEpoch=0;var old=binding;binding=null;if(old!=null)old.discard();menuMouseFence.clear();overrideKeys=new int[0];}
    private static boolean persistentInputBlocked(Minecraft mc){return mc.screen!=null&&!MagneticItemPullSelection.ownScreen()||GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)!=GLFW.GLFW_TRUE;}
    /** Refuse unsupported local prediction before allocating input or asking the server to spend CP. */
    private static boolean groundStepStartAllowed(){
        if(MagneticGroundStepClientMotion.canStartLocal()){groundStepBlocker="";return true;}
        String reason=MagneticGroundStepClientMotion.startBlocker();
        if(reason==null||reason.isEmpty())reason="academy.highpower.ground_step.unsupported";
        long time=now();var mc=Minecraft.getInstance();
        if(mc.player!=null&&(!reason.equals(groundStepBlocker)||groundStepBlockerAt==Long.MIN_VALUE||time-groundStepBlockerAt>=2000)){
            // Keep refusals in ordinary chat; the recurring prototype HUD owns the actionbar.
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable(reason),false);
            groundStepBlocker=reason;groundStepBlockerAt=time;
        }
        return false;
    }
    private static void cancelDualPointLocally(cn.academy.port.highpower.HighPowerInput.Input input){DualPointEffects.abortLocal(input.input(),input.epoch());}
    public static boolean dualPointInputMatches(long input,long epoch){return dualPointInputs.permits(input,epoch);}
    public static void dualPointAcceptedInput(long input,long epoch){dualPointInputs.accepted(input,epoch);}
    public static void dualPointEndedInput(long input,long epoch){dualPointInputs.ended(input,epoch);}
    private static void cancelBeamEtchingLocally(cn.academy.port.highpower.HighPowerInput.Input input){BeamEtchingEffects.abortLocal(input.input(),input.epoch());}
    private static void releaseBeamEtchingInput(cn.academy.port.highpower.HighPowerInput.Input identity){
        if(identity!=null&&singleOwnerEpochMatches(identity.epoch()))request("hp_beam_etching_release",identity.wire());
    }
    public static boolean beamEtchingInputMatches(long input,long epoch){return beamEtchingInputs.permits(input,epoch);}
    public static void beamEtchingAcceptedInput(long input,long epoch){beamEtchingInputs.accepted(input,epoch);}
    public static void beamEtchingEndedInput(long input,long epoch){beamEtchingInputs.ended(input,epoch);}
    private static void cancelPointMeltdownLocally(cn.academy.port.highpower.HighPowerInput.Input input){PointMeltdownEffects.abortLocal(input.input(),input.epoch());}
    private static void releasePointMeltdownInput(cn.academy.port.highpower.HighPowerInput.Input identity){
        if(identity!=null&&singleOwnerEpochMatches(identity.epoch()))request("hp_point_meltdown_release",identity.wire());
    }
    public static boolean pointMeltdownInputMatches(long input,long epoch){return pointMeltdownInputs.permits(input,epoch);}
    public static void pointMeltdownAcceptedInput(long input,long epoch){pointMeltdownInputs.accepted(input,epoch);}
    public static void pointMeltdownEndedInput(long input,long epoch){pointMeltdownInputs.ended(input,epoch);}
    private static void cancelDisintegrationSweepLocally(cn.academy.port.highpower.HighPowerInput.Input input){DisintegrationSweepEffects.abortLocal(input.input(),input.epoch());}
    private static void releaseDisintegrationSweepInput(cn.academy.port.highpower.HighPowerInput.Input identity){
        if(identity!=null&&singleOwnerEpochMatches(identity.epoch()))request("hp_disintegration_sweep_release",identity.wire());
    }
    private static DisintegrationSweepInputState.DispatchContext disintegrationSweepDispatchContext(Minecraft mc){
        return new DisintegrationSweepInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                state.presets.current(),state.presets.revision(),mappings());
    }
    /** Vanilla LocalPlayer has sent its own crouch/movement packets before this Post dispatch. */
    private static void flushDisintegrationSweepStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var queued=disintegrationSweepInputs.queuedStart();if(queued==null)return;
        var identity=queued.identity();String skill=cn.academy.port.highpower.DisintegrationSweep.ID;
        boolean ready=mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&binding!=null&&!mc.isPaused()
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!persistentInputBlocked(mc)&&mc.screen==null&&mc.getCameraEntity()==mc.player
                &&mc.player.getId()==identity.actor()&&singleOwnerEpochMatches(identity.epoch())
                &&disintegrationSweepInputs.queuedFor(disintegrationSweepDispatchContext(mc))
                &&skill.equals(state.presets.currentSkill(identity.slot()))
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill)&&SkillCatalog.enabled(state.category,skill)
                &&state.canUse(skill);
        if(!ready){sendPersistentCancellation("hp_disintegration_sweep_abort",disintegrationSweepInputs.cancel(),AcademyClient::cancelDisintegrationSweepLocally);return;}
        var dispatch=disintegrationSweepInputs.flushQueuedStart();if(dispatch==null)return;
        request("hp_disintegration_sweep_start",dispatch.start().wire());
        releaseDisintegrationSweepInput(dispatch.release());
    }
    public static boolean disintegrationSweepInputMatches(long input,long epoch){return disintegrationSweepInputs.permits(input,epoch);}
    public static void disintegrationSweepAcceptedInput(long input,long epoch){disintegrationSweepInputs.accepted(input,epoch);}
    public static void disintegrationSweepEndedInput(long input,long epoch){disintegrationSweepInputs.ended(input,epoch);}
    private static void cancelFoldedMirrorLocally(cn.academy.port.highpower.HighPowerInput.Input input){FoldedMirrorEffects.abortLocal(input.input(),input.epoch());}
    private static void releaseFoldedMirrorInput(cn.academy.port.highpower.HighPowerInput.Input identity){
        if(identity!=null&&singleOwnerEpochMatches(identity.epoch()))request("hp_folded_mirror_release",identity.wire());
    }
    private static FoldedMirrorInputState.DispatchContext foldedMirrorDispatchContext(Minecraft mc){
        return new FoldedMirrorInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                state.presets.current(),state.presets.revision(),mappings());
    }
    /** Vanilla LocalPlayer has sent its own crouch/movement packets before this Post dispatch. */
    private static void flushFoldedMirrorStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var queued=foldedMirrorInputs.queuedStart();if(queued==null)return;
        var identity=queued.identity();String skill=cn.academy.port.highpower.FoldedMirrorArray.ID;
        boolean ready=mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&binding!=null&&!mc.isPaused()
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!persistentInputBlocked(mc)&&mc.screen==null&&mc.getCameraEntity()==mc.player
                &&mc.player.getId()==identity.actor()&&singleOwnerEpochMatches(identity.epoch())
                &&foldedMirrorInputs.queuedFor(foldedMirrorDispatchContext(mc))
                &&skill.equals(state.presets.currentSkill(identity.slot()))
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill)&&SkillCatalog.enabled(state.category,skill)
                &&(state.canUse(skill)||state.cooldowns.getOrDefault(skill,0)>0&&FoldedMirrorEffects.canRecallLocal());
        if(!ready){sendPersistentCancellation("hp_folded_mirror_abort",foldedMirrorInputs.cancel(),AcademyClient::cancelFoldedMirrorLocally);return;}
        var dispatch=foldedMirrorInputs.flushQueuedStart();if(dispatch==null)return;
        request("hp_folded_mirror_start",dispatch.start().wire());
        releaseFoldedMirrorInput(dispatch.release());
    }
    public static boolean foldedMirrorInputMatches(long input,long epoch){return foldedMirrorInputs.permits(input,epoch);}
    public static void foldedMirrorAcceptedInput(long input,long epoch){foldedMirrorInputs.accepted(input,epoch);}
    public static void foldedMirrorEndedInput(long input,long epoch){foldedMirrorInputs.ended(input,epoch);}
    private static void cancelRemoteScatterRelayLocally(cn.academy.port.highpower.HighPowerInput.Input input){RemoteScatterRelayEffects.abortLocal(input.input(),input.epoch());}
    private static void releaseRemoteScatterRelayInput(cn.academy.port.highpower.HighPowerInput.Input identity){
        if(identity!=null&&singleOwnerEpochMatches(identity.epoch()))
            remoteScatterRelayInputs.deferRelease(identity,remoteScatterRelayDispatchContext(Minecraft.getInstance()));
    }
    /** Final native aim must reach the server before selecting an endpoint or committing a relay shot. */
    private static void flushRemoteScatterRelayRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);
        var identity=remoteScatterRelayInputs.flushDeferredRelease(remoteScatterRelayDispatchContext(mc));
        if(identity==null)return;
        if(mc.player==null||mc.level==null||mc.getConnection()==null||mc.isPaused()
                ||!mc.player.isAlive()||mc.player.isRemoved()||mc.player.isSpectator()||mc.player.getId()!=identity.actor()
                ||!RemoteScatterRelayEffects.stateEligible()||persistentInputBlocked(mc)||mc.screen!=null
                ||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering
                ||!"meltdowner".equals(state.category)||!singleOwnerEpochMatches(identity.epoch())
                ||!cn.academy.port.highpower.RemoteScatterRelay.ID.equals(state.presets.currentSkill(identity.slot()))){
            sendPersistentCancellation("hp_remote_relay_abort",remoteScatterRelayInputs.cancel(),AcademyClient::cancelRemoteScatterRelayLocally);
            return;
        }
        request("hp_remote_relay_release",identity.wire());
    }
    private static RemoteScatterRelayInputState.DispatchContext remoteScatterRelayDispatchContext(Minecraft mc){
        return new RemoteScatterRelayInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                state.presets.current(),state.presets.revision(),mappings());
    }
    /** Vanilla LocalPlayer has sent its own crouch/movement packets before this Post dispatch. */
    private static void flushRemoteScatterRelayStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var queued=remoteScatterRelayInputs.queuedStart();if(queued==null)return;
        var identity=queued.identity();String skill=cn.academy.port.highpower.RemoteScatterRelay.ID;
        boolean ready=mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&binding!=null&&!mc.isPaused()
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!persistentInputBlocked(mc)&&mc.screen==null&&mc.getCameraEntity()==mc.player
                &&mc.player.getId()==identity.actor()&&singleOwnerEpochMatches(identity.epoch())
                &&remoteScatterRelayInputs.queuedFor(remoteScatterRelayDispatchContext(mc))
                &&skill.equals(state.presets.currentSkill(identity.slot()))
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill)&&SkillCatalog.enabled(state.category,skill)
                &&(state.canUse(skill)||state.cooldowns.getOrDefault(skill,0)>0&&RemoteScatterRelayEffects.canRecallLocal());
        if(!ready){sendPersistentCancellation("hp_remote_relay_abort",remoteScatterRelayInputs.cancel(),AcademyClient::cancelRemoteScatterRelayLocally);return;}
        var dispatch=remoteScatterRelayInputs.flushQueuedStart();if(dispatch==null)return;
        request("hp_remote_relay_start",dispatch.start().wire());
        releaseRemoteScatterRelayInput(dispatch.release());
    }
    public static boolean remoteScatterRelayInputMatches(long input,long epoch){return remoteScatterRelayInputs.permits(input,epoch);}
    public static void remoteScatterRelayAcceptedInput(long input,long epoch){remoteScatterRelayInputs.accepted(input,epoch);}
    public static void remoteScatterRelayEndedInput(long input,long epoch){remoteScatterRelayInputs.ended(input,epoch);}
    private static void cancelScatterLocatorLocally(cn.academy.port.highpower.HighPowerInput.Input input){ScatterLocatorEffects.abortLocal(input.input(),input.epoch());}
    private static void releaseScatterLocatorInput(cn.academy.port.highpower.HighPowerInput.Input identity){
        if(identity!=null&&singleOwnerEpochMatches(identity.epoch()))
            scatterLocatorInputs.deferRelease(identity,scatterLocatorDispatchContext(Minecraft.getInstance()));
    }
    /** Final native aim must reach the server before committing a locator orientation or preview. */
    private static void flushScatterLocatorRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);
        var identity=scatterLocatorInputs.flushDeferredRelease(scatterLocatorDispatchContext(mc));
        if(identity==null)return;
        if(mc.player==null||mc.level==null||mc.getConnection()==null||mc.isPaused()
                ||!mc.player.isAlive()||mc.player.isRemoved()||mc.player.isSpectator()||mc.player.getId()!=identity.actor()
                ||!ScatterLocatorEffects.stateEligible()||persistentInputBlocked(mc)||mc.screen!=null
                ||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering
                ||!"meltdowner".equals(state.category)||!singleOwnerEpochMatches(identity.epoch())
                ||!cn.academy.port.highpower.ScatterLocatorPlate.ID.equals(state.presets.currentSkill(identity.slot()))){
            sendPersistentCancellation("hp_scatter_locator_abort",scatterLocatorInputs.cancel(),AcademyClient::cancelScatterLocatorLocally);
            return;
        }
        request("hp_scatter_locator_release",identity.wire());
    }
    private static ScatterLocatorInputState.DispatchContext scatterLocatorDispatchContext(Minecraft mc){
        return new ScatterLocatorInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                state.presets.current(),state.presets.revision(),mappings());
    }
    /** Vanilla LocalPlayer has sent its own crouch/movement packets before this Post dispatch. */
    private static void flushScatterLocatorStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var queued=scatterLocatorInputs.queuedStart();if(queued==null)return;
        var identity=queued.identity();String skill=cn.academy.port.highpower.ScatterLocatorPlate.ID;
        boolean ready=mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&binding!=null&&!mc.isPaused()
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!persistentInputBlocked(mc)&&mc.screen==null&&mc.getCameraEntity()==mc.player
                &&mc.player.getId()==identity.actor()&&singleOwnerEpochMatches(identity.epoch())
                &&scatterLocatorInputs.queuedFor(scatterLocatorDispatchContext(mc))
                &&skill.equals(state.presets.currentSkill(identity.slot()))
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill)&&SkillCatalog.enabled(state.category,skill)
                &&(state.canUse(skill)||ScatterLocatorEffects.canRecallLocal());
        if(!ready){sendPersistentCancellation("hp_scatter_locator_abort",scatterLocatorInputs.cancel(),AcademyClient::cancelScatterLocatorLocally);return;}
        var dispatch=scatterLocatorInputs.flushQueuedStart();if(dispatch==null)return;
        request("hp_scatter_locator_start",dispatch.start().wire());
        releaseScatterLocatorInput(dispatch.release());
    }
    public static boolean scatterLocatorInputMatches(long input,long epoch){return scatterLocatorInputs.permits(input,epoch);}
    public static void scatterLocatorAcceptedInput(long input,long epoch){scatterLocatorInputs.accepted(input,epoch);}
    public static void scatterLocatorEndedInput(long input,long epoch){scatterLocatorInputs.ended(input,epoch);}
    private static void cancelParticleLaminateLocally(cn.academy.port.highpower.HighPowerInput.Input input){ParticleLaminateEffects.abortLocal(input.input(),input.epoch());}
    private static void releaseParticleLaminateInput(cn.academy.port.highpower.HighPowerInput.Input identity){
        if(identity!=null&&singleOwnerEpochMatches(identity.epoch()))
            particleLaminateInputs.deferRelease(identity,particleLaminateDispatchContext(Minecraft.getInstance()));
    }
    /** Final aim and crouch must reach vanilla before the server commits the second panel. */
    private static void flushParticleLaminateRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);
        var identity=particleLaminateInputs.flushDeferredRelease(particleLaminateDispatchContext(mc));
        if(identity==null)return;
        if(mc.player==null||mc.level==null||mc.getConnection()==null||persistentInputBlocked(mc)||mc.screen!=null
                ||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering
                ||!"meltdowner".equals(state.category)||!singleOwnerEpochMatches(identity.epoch())
                ||!cn.academy.port.highpower.ParticleLaminate.ID.equals(state.presets.currentSkill(identity.slot()))){
            sendPersistentCancellation("hp_particle_laminate_abort",particleLaminateInputs.cancel(),AcademyClient::cancelParticleLaminateLocally);
            return;
        }
        request("hp_particle_laminate_release",identity.wire());
    }
    private static ParticleLaminateInputState.DispatchContext particleLaminateDispatchContext(Minecraft mc){
        return new ParticleLaminateInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                state.presets.current(),state.presets.revision(),mappings());
    }
    /** Vanilla LocalPlayer has sent its own crouch/movement packets before this Post dispatch. */
    private static void flushParticleLaminateStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var queued=particleLaminateInputs.queuedStart();if(queued==null)return;
        var identity=queued.identity();String skill=cn.academy.port.highpower.ParticleLaminate.ID;
        boolean ready=mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&binding!=null&&!mc.isPaused()
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!persistentInputBlocked(mc)&&mc.screen==null&&mc.getCameraEntity()==mc.player
                &&mc.player.getId()==identity.actor()&&singleOwnerEpochMatches(identity.epoch())
                &&particleLaminateInputs.queuedFor(particleLaminateDispatchContext(mc))
                &&skill.equals(state.presets.currentSkill(identity.slot()))
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill)&&SkillCatalog.enabled(state.category,skill)
                &&(state.canUse(skill)||state.cooldowns.getOrDefault(skill,0)>0&&ParticleLaminateEffects.canRecallLocal());
        if(!ready){sendPersistentCancellation("hp_particle_laminate_abort",particleLaminateInputs.cancel(),AcademyClient::cancelParticleLaminateLocally);return;}
        var dispatch=particleLaminateInputs.flushQueuedStart();if(dispatch==null)return;
        request("hp_particle_laminate_start",dispatch.start().wire());
        releaseParticleLaminateInput(dispatch.release());
    }
    public static boolean particleLaminateInputMatches(long input,long epoch){return particleLaminateInputs.permits(input,epoch);}
    public static void particleLaminateAcceptedInput(long input,long epoch){particleLaminateInputs.accepted(input,epoch);}
    public static void particleLaminateEndedInput(long input,long epoch){particleLaminateInputs.ended(input,epoch);}
    private static void cancelMulticoreCanopyLocally(cn.academy.port.highpower.HighPowerInput.Input input){MulticoreCanopyEffects.abortLocal(input.input(),input.epoch());}
    private static void releaseMulticoreCanopyInput(cn.academy.port.highpower.HighPowerInput.Input identity){
        if(identity!=null&&singleOwnerEpochMatches(identity.epoch()))
            multicoreCanopyInputs.deferRelease(identity,multicoreCanopyDispatchContext(Minecraft.getInstance()));
    }
    /** Final native aim reaches the server before deployment or firing release. */
    private static void flushMulticoreCanopyRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);
        var identity=multicoreCanopyInputs.flushDeferredRelease(multicoreCanopyDispatchContext(mc));
        if(identity==null)return;
        if(mc.player==null||mc.level==null||mc.getConnection()==null||persistentInputBlocked(mc)||mc.screen!=null
                ||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering
                ||!"meltdowner".equals(state.category)||!singleOwnerEpochMatches(identity.epoch())
                ||!cn.academy.port.highpower.MulticoreCanopy.ID.equals(state.presets.currentSkill(identity.slot()))){
            sendPersistentCancellation("hp_multicore_canopy_abort",multicoreCanopyInputs.cancel(),AcademyClient::cancelMulticoreCanopyLocally);
            return;
        }
        request("hp_multicore_canopy_release",identity.wire());
    }
    private static MulticoreCanopyInputState.DispatchContext multicoreCanopyDispatchContext(Minecraft mc){
        return new MulticoreCanopyInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                state.presets.current(),state.presets.revision(),mappings());
    }
    /** Vanilla LocalPlayer has sent its own crouch/movement packets before this Post dispatch. */
    private static void flushMulticoreCanopyStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var queued=multicoreCanopyInputs.queuedStart();if(queued==null)return;
        var identity=queued.identity();String skill=cn.academy.port.highpower.MulticoreCanopy.ID;
        boolean ready=mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&binding!=null&&!mc.isPaused()
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!persistentInputBlocked(mc)&&mc.screen==null&&mc.getCameraEntity()==mc.player
                &&mc.player.getId()==identity.actor()&&singleOwnerEpochMatches(identity.epoch())
                &&multicoreCanopyInputs.queuedFor(multicoreCanopyDispatchContext(mc))
                &&skill.equals(state.presets.currentSkill(identity.slot()))
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill)&&SkillCatalog.enabled(state.category,skill)
                &&(state.canUse(skill)||state.cooldowns.getOrDefault(skill,0)>0&&MulticoreCanopyEffects.canControlLocal());
        if(!ready){sendPersistentCancellation("hp_multicore_canopy_abort",multicoreCanopyInputs.cancel(),AcademyClient::cancelMulticoreCanopyLocally);return;}
        var dispatch=multicoreCanopyInputs.flushQueuedStart();if(dispatch==null)return;
        request("hp_multicore_canopy_start",dispatch.start().wire());
        releaseMulticoreCanopyInput(dispatch.release());
    }
    public static boolean multicoreCanopyInputMatches(long input,long epoch){return multicoreCanopyInputs.permits(input,epoch);}
    public static void multicoreCanopyAcceptedInput(long input,long epoch){multicoreCanopyInputs.accepted(input,epoch);}
    public static void multicoreCanopyEndedInput(long input,long epoch){multicoreCanopyInputs.ended(input,epoch);}
    private static void cancelDissipationCurtainLocally(cn.academy.port.highpower.HighPowerInput.Input input){DissipationCurtainEffects.abortLocal(input.input(),input.epoch());}
    private static void releaseDissipationCurtainInput(cn.academy.port.highpower.HighPowerInput.Input identity){
        if(identity!=null&&singleOwnerEpochMatches(identity.epoch()))
            dissipationCurtainInputs.deferRelease(identity,dissipationCurtainDispatchContext(Minecraft.getInstance()));
    }
    /** Release follows the matching queued start and the final native movement update. */
    private static void flushDissipationCurtainRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);
        var identity=dissipationCurtainInputs.flushDeferredRelease(dissipationCurtainDispatchContext(mc));
        if(identity==null)return;
        if(mc.player==null||mc.level==null||mc.getConnection()==null||persistentInputBlocked(mc)||mc.screen!=null
                ||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering
                ||!"meltdowner".equals(state.category)||!singleOwnerEpochMatches(identity.epoch())
                ||!cn.academy.port.highpower.DissipationCurtain.ID.equals(state.presets.currentSkill(identity.slot()))){
            sendPersistentCancellation("hp_dissipation_curtain_abort",dissipationCurtainInputs.cancel(),AcademyClient::cancelDissipationCurtainLocally);
            return;
        }
        request("hp_dissipation_curtain_release",identity.wire());
    }
    private static DissipationCurtainInputState.DispatchContext dissipationCurtainDispatchContext(Minecraft mc){
        return new DissipationCurtainInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                state.presets.current(),state.presets.revision(),mappings());
    }
    /** Vanilla LocalPlayer has sent its own crouch/movement packets before this Post dispatch. */
    private static void flushDissipationCurtainStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var queued=dissipationCurtainInputs.queuedStart();if(queued==null)return;
        var identity=queued.identity();String skill=cn.academy.port.highpower.DissipationCurtain.ID;
        boolean ready=mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&binding!=null&&!mc.isPaused()
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!persistentInputBlocked(mc)&&mc.screen==null&&mc.getCameraEntity()==mc.player
                &&mc.player.getId()==identity.actor()&&singleOwnerEpochMatches(identity.epoch())
                &&dissipationCurtainInputs.queuedFor(dissipationCurtainDispatchContext(mc))
                &&skill.equals(state.presets.currentSkill(identity.slot()))
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill)&&SkillCatalog.enabled(state.category,skill)
                &&state.canUse(skill);
        if(!ready){sendPersistentCancellation("hp_dissipation_curtain_abort",dissipationCurtainInputs.cancel(),AcademyClient::cancelDissipationCurtainLocally);return;}
        var dispatch=dissipationCurtainInputs.flushQueuedStart();if(dispatch==null)return;
        request("hp_dissipation_curtain_start",dispatch.start().wire());
        releaseDissipationCurtainInput(dispatch.release());
    }
    public static boolean dissipationCurtainInputMatches(long input,long epoch){return dissipationCurtainInputs.permits(input,epoch);}
    public static void dissipationCurtainAcceptedInput(long input,long epoch){dissipationCurtainInputs.accepted(input,epoch);}
    public static void dissipationCurtainEndedInput(long input,long epoch){dissipationCurtainInputs.ended(input,epoch);}
    private static DisplacedProjectionInputState.Gates displacedProjectionGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.DisplacedProjection.ID;
        var context=new DisplacedProjectionInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics;
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)
                &&state.level>=1&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new DisplacedProjectionInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0);
    }
    private static void tickDisplacedProjectionInput(Minecraft mc){
        var identity=displacedProjectionInputs.current();if(identity==null)return;
        displacedProjectionInputs.releaseWhenUp(physicalDown(displacedProjectionInputs.physicalKey()));
        displacedProjectionInputs.tick(displacedProjectionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendDisplacedProjectionCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native crouch and movement. */
    private static void flushDisplacedProjectionStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=displacedProjectionInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused()){displacedProjectionCancelInput();return;}
        displacedProjectionInputs.flushPost(displacedProjectionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendDisplacedProjectionStart,AcademyClient::sendDisplacedProjectionCancellation);
    }
    private static void sendDisplacedProjectionStart(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_displaced_projection_start",input.wire());}
    public static boolean displacedProjectionInputMatches(long input,long epoch){return displacedProjectionInputs.permits(input,epoch);}
    public static boolean displacedProjectionControlValid(){
        var identity=displacedProjectionInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&displacedProjectionInputs.ownsControl(displacedProjectionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void displacedProjectionEndedInput(long input,long epoch){displacedProjectionInputs.ended(input,epoch);}
    public static void displacedProjectionCancelInput(){displacedProjectionInputs.cancelBeforeClear(AcademyClient::sendDisplacedProjectionCancellation);}
    private static void sendDisplacedProjectionCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&displacedProjectionInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_displaced_projection_abort",input.wire());
        DisplacedProjectionEffects.abortLocal(input.input(),input.epoch());
    }
    private static RecoveryMarkInputState.Gates recoveryMarkGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.RecoveryMark.ID;
        var context=new RecoveryMarkInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.onGround()
                &&!mc.player.isFallFlying()&&!mc.player.isSwimming()&&!mc.player.isInWater()&&!mc.player.isInLava();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)
                &&state.level>=2&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new RecoveryMarkInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,mc.player!=null&&mc.player.isShiftKeyDown());
    }
    private static void tickRecoveryMarkInput(Minecraft mc){
        var identity=recoveryMarkInputs.current();if(identity==null)return;
        recoveryMarkInputs.releaseWhenUp(physicalDown(recoveryMarkInputs.physicalKey()));
        recoveryMarkInputs.tick(recoveryMarkGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendRecoveryMarkCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native crouch and movement. */
    private static void flushRecoveryMarkStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=recoveryMarkInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused()){recoveryMarkCancelInput();return;}
        recoveryMarkInputs.flushPost(recoveryMarkGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendRecoveryMarkStart,AcademyClient::sendRecoveryMarkCancellation);
    }
    private static void sendRecoveryMarkStart(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_recovery_mark_start",input.wire());}
    public static boolean recoveryMarkInputMatches(long input,long epoch){return recoveryMarkInputs.permits(input,epoch);}
    public static boolean recoveryMarkControlValid(){
        var identity=recoveryMarkInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&recoveryMarkInputs.ownsControl(recoveryMarkGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void recoveryMarkAcceptedInput(long input,long epoch,int remaining){recoveryMarkInputs.accepted(input,epoch,remaining);}
    public static void recoveryMarkEndedInput(long input,long epoch){recoveryMarkInputs.ended(input,epoch);}
    public static void recoveryMarkCancelInput(){recoveryMarkInputs.cancelBeforeClear(AcademyClient::sendRecoveryMarkCancellation);}
    private static void sendRecoveryMarkCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&recoveryMarkInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_recovery_mark_abort",input.wire());
        RecoveryMarkEffects.abortLocal(input.input(),input.epoch());
    }
    private static ReturnMarkInputState.Gates returnMarkGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.ReturnMark.ID;
        var context=new ReturnMarkInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.onGround()
                &&!mc.player.isFallFlying()&&!mc.player.isSwimming()&&!mc.player.isInWater()&&!mc.player.isInLava();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)
                &&state.level>=2&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new ReturnMarkInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,mc.player!=null&&mc.player.isShiftKeyDown());
    }
    private static void tickReturnMarkInput(Minecraft mc){
        var identity=returnMarkInputs.current();if(identity==null)return;
        returnMarkInputs.releaseWhenUp(physicalDown(returnMarkInputs.physicalKey()));
        returnMarkInputs.tick(returnMarkGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendReturnMarkCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native crouch and movement. */
    private static void flushReturnMarkStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=returnMarkInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused()){returnMarkCancelInput();return;}
        returnMarkInputs.flushPost(returnMarkGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendReturnMarkStart,AcademyClient::sendReturnMarkCancellation);
    }
    private static void sendReturnMarkStart(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_return_mark_start",input.wire());}
    public static boolean returnMarkInputMatches(long input,long epoch){return returnMarkInputs.permits(input,epoch);}
    public static boolean returnMarkControlValid(){
        var identity=returnMarkInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&returnMarkInputs.ownsControl(returnMarkGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void returnMarkAcceptedInput(long input,long epoch,int remaining){returnMarkInputs.accepted(input,epoch,remaining);}
    public static void returnMarkEndedInput(long input,long epoch){returnMarkInputs.ended(input,epoch);}
    public static void returnMarkCancelInput(){returnMarkInputs.cancelBeforeClear(AcademyClient::sendReturnMarkCancellation);}
    private static void sendReturnMarkCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&returnMarkInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_return_mark_abort",input.wire());
        ReturnMarkEffects.abortLocal(input.input(),input.epoch());
    }
    private static ShortInterceptionInputState.Gates shortInterceptionGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.ShortInterception.ID;
        var context=new ShortInterceptionInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.onGround()
                &&!mc.player.isFallFlying()&&!mc.player.isSwimming()&&!mc.player.isInWater()&&!mc.player.isInLava();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)
                &&state.level>=3&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new ShortInterceptionInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,mc.player!=null&&mc.player.isShiftKeyDown());
    }
    private static void tickShortInterceptionInput(Minecraft mc){
        var identity=shortInterceptionInputs.current();if(identity==null)return;
        shortInterceptionInputs.releaseWhenUp(physicalDown(shortInterceptionInputs.physicalKey()));
        shortInterceptionInputs.tick(shortInterceptionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendShortInterceptionCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native look and movement. */
    private static void flushShortInterceptionStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=shortInterceptionInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused())return;
        shortInterceptionInputs.flushStartPost(shortInterceptionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendShortInterceptionStart,AcademyClient::sendShortInterceptionCancellation);
    }
    /** Release is queued by the genuine key-up and dispatched after native look/movement. */
    private static void flushShortInterceptionRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=shortInterceptionInputs.queuedRelease();if(identity==null||mc.isPaused())return;
        shortInterceptionInputs.flushReleasePost(shortInterceptionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendShortInterceptionRelease,AcademyClient::sendShortInterceptionCancellation);
    }
    private static void sendShortInterceptionRelease(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_short_interception_release",input.wire());}
    private static void sendShortInterceptionStart(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_short_interception_start",input.wire());}
    public static boolean shortInterceptionInputMatches(long input,long epoch){return shortInterceptionInputs.permits(input,epoch);}
    public static boolean shortInterceptionReceiptMatches(long input,long epoch,boolean terminal){return shortInterceptionInputs.permitsReceipt(input,epoch,terminal);}
    public static boolean shortInterceptionControlValid(){
        var identity=shortInterceptionInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&shortInterceptionInputs.ownsControl(shortInterceptionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void shortInterceptionAcceptedInput(long input,long epoch,int remaining,boolean retained){shortInterceptionInputs.accepted(input,epoch,remaining,retained);}
    public static void shortInterceptionEndedInput(long input,long epoch){shortInterceptionInputs.ended(input,epoch);}
    public static void shortInterceptionCancelInput(){shortInterceptionInputs.cancelBeforeClear(AcademyClient::sendShortInterceptionCancellation);}
    private static void sendShortInterceptionCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&shortInterceptionInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_short_interception_abort",input.wire());
        ShortInterceptionEffects.abortLocal(input.input(),input.epoch());
    }
    private static MultidimensionalLandingArrayInputState.Gates multidimensionalLandingArrayGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.MultidimensionalLandingArray.ID;
        var context=new MultidimensionalLandingArrayInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.onGround()
                &&!mc.player.isFallFlying()&&!mc.player.isSwimming()&&!mc.player.isInWater()&&!mc.player.isInLava();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)
                &&state.level>=5&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new MultidimensionalLandingArrayInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,false,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,mc.player!=null&&mc.player.isShiftKeyDown());
    }
    private static void tickMultidimensionalLandingArrayInput(Minecraft mc){
        var identity=multidimensionalLandingArrayInputs.current();if(identity==null)return;
        multidimensionalLandingArrayInputs.releaseWhenUp(physicalDown(multidimensionalLandingArrayInputs.physicalKey()));
        multidimensionalLandingArrayInputs.tick(multidimensionalLandingArrayGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendMultidimensionalLandingArrayCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native look and movement. */
    private static void flushMultidimensionalLandingArrayStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=multidimensionalLandingArrayInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused())return;
        multidimensionalLandingArrayInputs.flushStartPost(multidimensionalLandingArrayGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendMultidimensionalLandingArrayStart,AcademyClient::sendMultidimensionalLandingArrayCancellation);
    }
    /** Release is queued by the genuine key-up and dispatched after native look/movement. */
    private static void flushMultidimensionalLandingArrayRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=multidimensionalLandingArrayInputs.queuedRelease();if(identity==null||mc.isPaused())return;
        multidimensionalLandingArrayInputs.flushReleasePost(multidimensionalLandingArrayGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendMultidimensionalLandingArrayRelease,AcademyClient::sendMultidimensionalLandingArrayCancellation);
    }
    private static void sendMultidimensionalLandingArrayRelease(cn.academy.port.highpower.HighPowerInput.Input input){cn.academy.port.highpower.MultidimensionalLandingArray.diagnostic("client release input="+input.input());request("hp_mla_release",input.wire());}
    private static void sendMultidimensionalLandingArrayStart(cn.academy.port.highpower.HighPowerInput.Input input){cn.academy.port.highpower.MultidimensionalLandingArray.diagnostic("client start input="+input.input());request("hp_mla_start",input.wire());}
    public static boolean multidimensionalLandingArrayInputMatches(long input,long epoch){return multidimensionalLandingArrayInputs.permits(input,epoch);}
    public static boolean multidimensionalLandingArrayReceiptMatches(long input,long epoch,int phase){return multidimensionalLandingArrayInputs.permitsReceipt(input,epoch,phase);}
    public static boolean multidimensionalLandingArrayControlValid(){
        var identity=multidimensionalLandingArrayInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&multidimensionalLandingArrayInputs.ownsControl(multidimensionalLandingArrayGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void multidimensionalLandingArrayAcceptedInput(long input,long epoch,int remaining,int phase,boolean freeOrigin){multidimensionalLandingArrayInputs.accepted(input,epoch,remaining,phase,freeOrigin);}
    public static void multidimensionalLandingArrayEndedInput(long input,long epoch){multidimensionalLandingArrayInputs.ended(input,epoch);}
    public static void multidimensionalLandingArrayRejectedInput(long input,long epoch){multidimensionalLandingArrayInputs.rejectedGesture(input,epoch);}
    public static void multidimensionalLandingArrayCancelInput(){multidimensionalLandingArrayInputs.cancelBeforeClear(AcademyClient::sendMultidimensionalLandingArrayCancellation);}
    private static void sendMultidimensionalLandingArrayCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&multidimensionalLandingArrayInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection())){
            cn.academy.port.highpower.MultidimensionalLandingArray.diagnostic("client abort input="+input.input());
            request("hp_mla_abort",input.wire());
        }
        MultidimensionalLandingArrayEffects.abortLocal(input.input(),input.epoch());
    }
    private static SpatialPincerInputState.Gates spatialPincerGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.SpatialPincerCorridor.ID;
        var context=new SpatialPincerInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.onGround()
                &&!mc.player.isFallFlying()&&!mc.player.isSwimming()&&!mc.player.isInWater()&&!mc.player.isInLava();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)
                &&state.level>=4&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new SpatialPincerInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,SpatialPincerClient.ownsPicker(),GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,mc.player!=null&&mc.player.isShiftKeyDown());
    }
    private static void tickSpatialPincerInput(Minecraft mc){
        var identity=spatialPincerInputs.current();if(identity==null)return;
        spatialPincerInputs.releaseWhenUp(physicalDown(spatialPincerInputs.physicalKey()));
        spatialPincerInputs.tick(spatialPincerGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendSpatialPincerCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native look and movement. */
    private static void flushSpatialPincerStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=spatialPincerInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused())return;
        spatialPincerInputs.flushStartPost(spatialPincerGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendSpatialPincerStart,AcademyClient::sendSpatialPincerCancellation);
    }
    /** Release is queued by the genuine key-up and dispatched after native look/movement. */
    private static void flushSpatialPincerRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=spatialPincerInputs.queuedRelease();if(identity==null||mc.isPaused())return;
        spatialPincerInputs.flushReleasePost(spatialPincerGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendSpatialPincerRelease,AcademyClient::sendSpatialPincerCancellation);
    }
    private static void sendSpatialPincerRelease(cn.academy.port.highpower.HighPowerInput.Input input){var mc=Minecraft.getInstance();if(mc.player!=null&&mc.player.isShiftKeyDown()&&SpatialPincerEffects.preparedPoints()==3&&!SpatialPincerEffects.targetsFrozen())SpatialPincerClient.expectOpen(input);request("hp_spatial_pincer_release",input.wire());}
    private static void sendSpatialPincerStart(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_spatial_pincer_start",input.wire());}
    public static boolean spatialPincerInputMatches(long input,long epoch){return spatialPincerInputs.permits(input,epoch);}
    public static boolean spatialPincerReceiptMatches(long input,long epoch,boolean terminal){return spatialPincerInputs.permitsReceipt(input,epoch,terminal);}
    public static boolean spatialPincerControlValid(){
        var identity=spatialPincerInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&spatialPincerInputs.ownsControl(spatialPincerGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void spatialPincerAcceptedInput(long input,long epoch,int remaining,boolean queued){spatialPincerInputs.accepted(input,epoch,remaining,queued);}
    public static void spatialPincerEndedInput(long input,long epoch){spatialPincerInputs.ended(input,epoch);}
    public static void spatialPincerRejectedGesture(long input,long epoch){spatialPincerInputs.rejectedGesture(input,epoch);}
    public static void spatialPincerCancelInput(){spatialPincerInputs.cancelBeforeClear(AcademyClient::sendSpatialPincerCancellation);}
    private static void sendSpatialPincerCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&spatialPincerInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_spatial_pincer_abort",input.wire());
        SpatialPincerEffects.abortLocal(input.input(),input.epoch());
    }
    private static SpatialRelayInputState.Gates spatialRelayGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.SpatialRelay.ID;
        var context=new SpatialRelayInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.onGround()
                &&!mc.player.isFallFlying()&&!mc.player.isSwimming()&&!mc.player.isInWater()&&!mc.player.isInLava();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)
                &&state.level>=4&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new SpatialRelayInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,mc.player!=null&&mc.player.isShiftKeyDown());
    }
    private static void tickSpatialRelayInput(Minecraft mc){
        var identity=spatialRelayInputs.current();if(identity==null)return;
        spatialRelayInputs.releaseWhenUp(physicalDown(spatialRelayInputs.physicalKey()));
        spatialRelayInputs.tick(spatialRelayGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendSpatialRelayCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native look and movement. */
    private static void flushSpatialRelayStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=spatialRelayInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused())return;
        spatialRelayInputs.flushStartPost(spatialRelayGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendSpatialRelayStart,AcademyClient::sendSpatialRelayCancellation);
    }
    /** Release is queued by the genuine key-up and dispatched after native look/movement. */
    private static void flushSpatialRelayRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=spatialRelayInputs.queuedRelease();if(identity==null||mc.isPaused())return;
        spatialRelayInputs.flushReleasePost(spatialRelayGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendSpatialRelayRelease,AcademyClient::sendSpatialRelayCancellation);
    }
    private static void sendSpatialRelayRelease(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_spatial_relay_release",input.wire());}
    private static void sendSpatialRelayStart(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_spatial_relay_start",input.wire());}
    public static boolean spatialRelayInputMatches(long input,long epoch){return spatialRelayInputs.permits(input,epoch);}
    public static boolean spatialRelayReceiptMatches(long input,long epoch,boolean terminal){return spatialRelayInputs.permitsReceipt(input,epoch,terminal);}
    public static boolean spatialRelayControlValid(){
        var identity=spatialRelayInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&spatialRelayInputs.ownsControl(spatialRelayGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void spatialRelayAcceptedInput(long input,long epoch,int remaining,boolean queued){spatialRelayInputs.accepted(input,epoch,remaining,queued);}
    public static void spatialRelayEndedInput(long input,long epoch){spatialRelayInputs.ended(input,epoch);}
    public static void spatialRelayRejectedGesture(long input,long epoch){spatialRelayInputs.rejectedGesture(input,epoch);}
    public static void spatialRelayCancelInput(){spatialRelayInputs.cancelBeforeClear(AcademyClient::sendSpatialRelayCancellation);}
    private static void sendSpatialRelayCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&spatialRelayInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_spatial_relay_abort",input.wire());
        SpatialRelayEffects.abortLocal(input.input(),input.epoch());
    }
    private static CornerExchangeInputState.Gates cornerExchangeGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.CornerExchange.ID;
        var context=new CornerExchangeInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.onGround()
                &&!mc.player.isFallFlying()&&!mc.player.isSwimming()&&!mc.player.isInWater()&&!mc.player.isInLava();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)
                &&state.level>=3&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new CornerExchangeInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,mc.player!=null&&mc.player.isShiftKeyDown());
    }
    private static void tickCornerExchangeInput(Minecraft mc){
        var identity=cornerExchangeInputs.current();if(identity==null)return;
        cornerExchangeInputs.releaseWhenUp(physicalDown(cornerExchangeInputs.physicalKey()));
        cornerExchangeInputs.tick(cornerExchangeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendCornerExchangeCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native crouch and movement. */
    private static void flushCornerExchangeStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=cornerExchangeInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused())return;
        cornerExchangeInputs.flushStartPost(cornerExchangeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendCornerExchangeStart,AcademyClient::sendCornerExchangeCancellation);
    }
    /** Release is queued by the genuine key-up and dispatched after native crouch/movement. */
    private static void flushCornerExchangeRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=cornerExchangeInputs.queuedRelease();if(identity==null||mc.isPaused())return;
        cornerExchangeInputs.flushReleasePost(cornerExchangeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendCornerExchangeRelease,AcademyClient::sendCornerExchangeCancellation);
    }
    private static void sendCornerExchangeRelease(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_corner_exchange_release",input.wire());}
    private static void sendCornerExchangeStart(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_corner_exchange_start",input.wire());}
    public static boolean cornerExchangeInputMatches(long input,long epoch){return cornerExchangeInputs.permits(input,epoch);}
    public static boolean cornerExchangeReceiptMatches(long input,long epoch,boolean terminal){return cornerExchangeInputs.permitsReceipt(input,epoch,terminal);}
    public static boolean cornerExchangeReversedPreview(boolean liveCrouch){return cornerExchangeInputs.previewReversed(liveCrouch);}
    public static boolean cornerExchangeControlValid(){
        var identity=cornerExchangeInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&cornerExchangeInputs.ownsControl(cornerExchangeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void cornerExchangeAcceptedInput(long input,long epoch,int remaining,boolean warning){cornerExchangeInputs.accepted(input,epoch,remaining,warning);}
    public static void cornerExchangeEndedInput(long input,long epoch){cornerExchangeInputs.ended(input,epoch);}
    public static void cornerExchangeCancelInput(){cornerExchangeInputs.cancelBeforeClear(AcademyClient::sendCornerExchangeCancellation);}
    private static void sendCornerExchangeCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&cornerExchangeInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_corner_exchange_abort",input.wire());
        CornerExchangeEffects.abortLocal(input.input(),input.epoch());
    }
    private static EmergencyReturnInputState.Gates emergencyReturnGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.EmergencyReturn.ID;
        var context=new EmergencyReturnInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.onGround()
                &&!mc.player.isFallFlying()&&!mc.player.isSwimming()&&!mc.player.isInWater()&&!mc.player.isInLava();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)
                &&state.level>=4&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new EmergencyReturnInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,mc.player!=null&&mc.player.isShiftKeyDown());
    }
    private static void tickEmergencyReturnInput(Minecraft mc){
        var identity=emergencyReturnInputs.current();if(identity==null)return;
        emergencyReturnInputs.releaseWhenUp(physicalDown(emergencyReturnInputs.physicalKey()));
        emergencyReturnInputs.tick(emergencyReturnGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendEmergencyReturnCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native crouch and movement. */
    private static void flushEmergencyReturnStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=emergencyReturnInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused())return;
        emergencyReturnInputs.flushStartPost(emergencyReturnGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendEmergencyReturnStart,AcademyClient::sendEmergencyReturnCancellation);
    }
    /** Release is queued by the genuine key-up and dispatched after native crouch/movement. */
    private static void flushEmergencyReturnRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=emergencyReturnInputs.queuedRelease();if(identity==null||mc.isPaused())return;
        emergencyReturnInputs.flushReleasePost(emergencyReturnGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendEmergencyReturnRelease,AcademyClient::sendEmergencyReturnCancellation);
    }
    private static void sendEmergencyReturnRelease(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_emergency_return_release",input.wire());}
    private static void sendEmergencyReturnStart(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_emergency_return_start",input.wire());}
    public static boolean emergencyReturnInputMatches(long input,long epoch){return emergencyReturnInputs.permits(input,epoch);}
    public static boolean emergencyReturnReceiptMatches(long input,long epoch,boolean allowUnreleasedTerminal){return emergencyReturnInputs.permitsReceipt(input,epoch,allowUnreleasedTerminal);}
    public static boolean emergencyReturnControlValid(){
        var identity=emergencyReturnInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&emergencyReturnInputs.ownsControl(emergencyReturnGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void emergencyReturnAcceptedInput(long input,long epoch,int remaining,boolean warning){emergencyReturnInputs.accepted(input,epoch,remaining,warning);}
    public static void emergencyReturnEndedInput(long input,long epoch){emergencyReturnInputs.ended(input,epoch);}
    public static void emergencyReturnCancelInput(){emergencyReturnInputs.cancelBeforeClear(AcademyClient::sendEmergencyReturnCancellation);}
    private static void sendEmergencyReturnCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&emergencyReturnInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_emergency_return_abort",input.wire());
        EmergencyReturnEffects.abortLocal(input.input(),input.epoch());
    }
    private static CrossSubstitutionInputState.Gates crossSubstitutionGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.CrossSubstitution.ID;
        var context=new CrossSubstitutionInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.onGround()
                &&!mc.player.isFallFlying()&&!mc.player.isSwimming()&&!mc.player.isInWater()&&!mc.player.isInLava();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)
                &&state.level>=2&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new CrossSubstitutionInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,mc.player!=null&&mc.player.isShiftKeyDown());
    }
    private static void tickCrossSubstitutionInput(Minecraft mc){
        var identity=crossSubstitutionInputs.current();if(identity==null)return;
        crossSubstitutionInputs.releaseWhenUp(physicalDown(crossSubstitutionInputs.physicalKey()));
        crossSubstitutionInputs.tick(crossSubstitutionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendCrossSubstitutionCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native crouch and movement. */
    private static void flushCrossSubstitutionStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=crossSubstitutionInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused()){crossSubstitutionCancelInput();return;}
        crossSubstitutionInputs.flushPost(crossSubstitutionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendCrossSubstitutionStart,AcademyClient::sendCrossSubstitutionCancellation);
    }
    private static void sendCrossSubstitutionStart(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_cross_substitution_start",input.wire());}
    public static boolean crossSubstitutionInputMatches(long input,long epoch){return crossSubstitutionInputs.permits(input,epoch);}
    public static boolean crossSubstitutionControlValid(){
        var identity=crossSubstitutionInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&crossSubstitutionInputs.ownsControl(crossSubstitutionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void crossSubstitutionAcceptedInput(long input,long epoch,int remaining,boolean projected){crossSubstitutionInputs.accepted(input,epoch,remaining,projected);}
    public static void crossSubstitutionEndedInput(long input,long epoch){crossSubstitutionInputs.ended(input,epoch);}
    public static void crossSubstitutionCancelInput(){crossSubstitutionInputs.cancelBeforeClear(AcademyClient::sendCrossSubstitutionCancellation);}
    private static void sendCrossSubstitutionCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&crossSubstitutionInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_cross_substitution_abort",input.wire());
        CrossSubstitutionEffects.abortLocal(input.input(),input.epoch());
    }
    private static VectorParryInputState.Gates vectorParryGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.VectorParry.ID;
        var context=new VectorParryInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.containerMenu==mc.player.inventoryMenu;
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)
                &&state.level>=1&&state.level<=5&&state.learned(skill)&&state.learned(cn.academy.port.highpower.DeflectionStrike.ID)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new VectorParryInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering,mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),mc.screen==null,
                GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,!state.consumptionInProgress());
    }
    private static void tickVectorParryInput(Minecraft mc){
        var identity=vectorParryInputs.current();if(identity==null)return;
        var released=vectorParryInputs.releaseWhenUp(physicalDown(vectorParryInputs.physicalKey()));
        if(released!=null)VectorParryEffects.abortLocal(released.input(),released.epoch());
        vectorParryInputs.tick(vectorParryGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendVectorParryCancellation);
    }
    /** Both held edges follow LocalPlayer's native sneak, look and movement packets. */
    private static void flushVectorParryStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=vectorParryInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused()){vectorParryCancelInput();return;}
        vectorParryInputs.flushStartPost(vectorParryGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                input->request("hp_vector_parry_start",input.wire()),AcademyClient::sendVectorParryCancellation);
    }
    private static void flushVectorParryRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=vectorParryInputs.queuedRelease();if(identity==null)return;
        vectorParryInputs.flushReleasePost(vectorParryGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                input->request("hp_vector_parry_release",input.wire()),AcademyClient::sendVectorParryCancellation);
    }
    public static boolean vectorParryInputMatches(long input,long epoch){return vectorParryInputs.permits(input,epoch);}
    public static void vectorParryAcceptedInput(long input,long epoch){vectorParryInputs.accepted(input,epoch);}
    public static void vectorParryEndedInput(long input,long epoch){vectorParryInputs.ended(input,epoch);}
    public static void vectorParryLocalVisualLost(long input,long epoch){vectorParryInputs.visualLost(input,epoch);}
    public static boolean vectorParryControlValid(){
        var identity=vectorParryInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&vectorParryInputs.ownsControl(vectorParryGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void vectorParryCancelInput(){vectorParryInputs.cancelBeforeClear(AcademyClient::sendVectorParryCancellation);}
    public static void cancelVectorParryForReload(){vectorParryCancelInput();}
    private static void sendVectorParryCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&vectorParryInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_vector_parry_abort",input.wire());
        VectorParryEffects.abortLocal(input.input(),input.epoch());
    }
    /** Fence an old completed receipt before an effect tick can mistake replacement for control loss. */
    private static boolean pressBrakingTouchInput(cn.academy.port.highpower.HighPowerInput.Input input,BrakingTouchInputState.Gates gates){
        var previous=brakingTouchInputs.current();
        if(!brakingTouchInputs.press(input,gates))return false;
        if(previous!=null)BrakingTouchEffects.abortLocal(previous.input(),previous.epoch());
        return true;
    }
    private static BrakingTouchInputState.Gates brakingTouchGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.BrakingTouch.ID;
        var context=new BrakingTouchInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isVehicle()&&!mc.player.isSleeping()&&!mc.player.isFallFlying()
                &&!mc.player.getAbilities().flying&&!mc.player.isInWaterOrBubble()&&!mc.player.isInLava()&&!mc.player.onClimbable()
                &&mc.player.containerMenu==mc.player.inventoryMenu;
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)
                &&state.level>=1&&state.level<=5&&state.learned(skill)&&state.learned(cn.academy.port.highpower.DeflectionStrike.ID)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new BrakingTouchInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering,mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),mc.screen==null,
                GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,!state.consumptionInProgress());
    }
    // ---- the seven post-0.81.2 skills share one authenticated input machine per skill ------------------------------
    private static BrakingTouchInputState.Gates newSkillGates(Minecraft mc,String skill,int slot,int physicalKey){
        var kind=cn.academy.port.highpower.NewSkillRuntime.Kind.of(skill).orElseThrow();
        var context=new BrakingTouchInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isVehicle()&&!mc.player.isSleeping()&&!mc.player.isFallFlying()
                &&mc.player.containerMenu==mc.player.inventoryMenu;
        boolean mapped=kind.category.equals(state.category)&&state.level>=kind.level&&state.level<=5&&state.learned(skill)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new BrakingTouchInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering,mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),mc.screen==null,
                GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible,!state.consumptionInProgress());
    }
    private static void tickNewSkillInputs(Minecraft mc){
        for(var entry:NewSkillClient.all()){
            var inputs=entry.getValue();var identity=inputs.current();if(identity==null)continue;
            String skill=entry.getKey();
            inputs.releaseWhenUp(physicalDown(inputs.physicalKey()));
            inputs.tick(newSkillGates(mc,skill,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),mc.isPaused(),i->sendNewSkillCancellation(skill,i));
        }
    }
    private static void flushNewSkillStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);
        for(var entry:NewSkillClient.all()){
            var inputs=entry.getValue();var identity=inputs.queuedStart();if(identity==null)continue;
            String skill=entry.getKey();
            if(mc.isPaused()){inputs.cancelBeforeClear(i->sendNewSkillCancellation(skill,i));continue;}
            inputs.flushStartPost(newSkillGates(mc,skill,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),mc.player!=null&&mc.player.isShiftKeyDown(),
                    input->request("hp_new_start",input.wire()),i->sendNewSkillCancellation(skill,i));
        }
    }
    private static void flushNewSkillRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);
        for(var entry:NewSkillClient.all()){
            var inputs=entry.getValue();var identity=inputs.queuedRelease();if(identity==null)continue;
            String skill=entry.getKey();
            inputs.flushReleasePost(newSkillGates(mc,skill,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                    input->request("hp_new_end",input.wire()),i->sendNewSkillCancellation(skill,i));
        }
    }
    public static boolean newSkillInputMatches(String skill,long input,long epoch){var s=NewSkillClient.input(skill);return s!=null&&s.permits(input,epoch);}
    public static void newSkillAcceptedInput(String skill,long input,long epoch,int phase){var s=NewSkillClient.input(skill);if(s!=null)s.accepted(input,epoch,BrakingTouchState.BRAKED);}
    public static void newSkillEndedInput(String skill,long input,long epoch){var s=NewSkillClient.input(skill);if(s!=null)s.ended(input,epoch);}
    public static boolean newSkillControlValid(){
        var mc=Minecraft.getInstance();
        for(var entry:NewSkillClient.all()){
            var identity=entry.getValue().current();
            if(identity!=null&&entry.getValue().ownsControl(newSkillGates(mc,entry.getKey(),identity.slot(),sourceKey(SLOTS[identity.slot()].getKey()))))return true;
        }
        return false;
    }
    public static void newSkillCancelAll(){for(var entry:NewSkillClient.all()){String skill=entry.getKey();entry.getValue().cancelBeforeClear(i->sendNewSkillCancellation(skill,i));}}
    private static void sendNewSkillCancellation(String skill,cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();var inputs=NewSkillClient.input(skill);
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&inputs!=null&&inputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_new_abort",input.wire());
    }
    private static void tickBrakingTouchInput(Minecraft mc){
        var identity=brakingTouchInputs.current();if(identity==null)return;
        var released=brakingTouchInputs.releaseWhenUp(physicalDown(brakingTouchInputs.physicalKey()));
        if(released!=null)BrakingTouchEffects.abortLocal(released.input(),released.epoch());
        brakingTouchInputs.tick(brakingTouchGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendBrakingTouchCancellation);
    }
    /** Both held edges follow LocalPlayer's native sneak, look and movement packets. */
    private static void flushBrakingTouchStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=brakingTouchInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused()){brakingTouchCancelInput();newSkillCancelAll();return;}
        brakingTouchInputs.flushStartPost(brakingTouchGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.player!=null&&mc.player.isShiftKeyDown(),input->request("brake_touch_start",input.wire()),AcademyClient::sendBrakingTouchCancellation);
    }
    private static void flushBrakingTouchRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=brakingTouchInputs.queuedRelease();if(identity==null)return;
        brakingTouchInputs.flushReleasePost(brakingTouchGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                input->request("brake_touch_end",input.wire()),AcademyClient::sendBrakingTouchCancellation);
    }
    public static boolean brakingTouchInputMatches(long input,long epoch){return brakingTouchInputs.permits(input,epoch);}
    public static void brakingTouchAcceptedInput(long input,long epoch,int phase){brakingTouchInputs.accepted(input,epoch,phase);}
    public static void brakingTouchEndedInput(long input,long epoch){brakingTouchInputs.ended(input,epoch);}
    public static boolean brakingTouchControlValid(){
        var identity=brakingTouchInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&brakingTouchInputs.ownsControl(brakingTouchGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void brakingTouchCancelInput(){brakingTouchInputs.cancelBeforeClear(AcademyClient::sendBrakingTouchCancellation);}
    public static void cancelBrakingTouchForReload(){brakingTouchCancelInput();newSkillCancelAll();}
    private static void sendBrakingTouchCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&brakingTouchInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("brake_touch_abort",input.wire());
        BrakingTouchEffects.abortLocal(input.input(),input.epoch());
    }
    private static DeflectionStrikeInputState.Gates deflectionStrikeGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.DeflectionStrike.ID;
        var context=new DeflectionStrikeInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)
                &&state.level>=1&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new DeflectionStrikeInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible);
    }
    private static void tickDeflectionStrikeInput(Minecraft mc){
        var identity=deflectionStrikeInputs.current();if(identity==null)return;
        deflectionStrikeInputs.releaseWhenUp(physicalDown(deflectionStrikeInputs.physicalKey()));
        deflectionStrikeInputs.tick(deflectionStrikeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendDeflectionStrikeCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native crouch and movement. */
    private static void flushDeflectionStrikeStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=deflectionStrikeInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused()){deflectionStrikeCancelInput();return;}
        deflectionStrikeInputs.flushPost(deflectionStrikeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendDeflectionStrikeStart,AcademyClient::sendDeflectionStrikeCancellation);
    }
    private static void sendDeflectionStrikeStart(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_deflect_tap",input.wire());}
    public static boolean deflectionStrikeInputMatches(long input,long epoch){return deflectionStrikeInputs.permits(input,epoch);}
    public static boolean deflectionStrikeControlValid(){
        var identity=deflectionStrikeInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&deflectionStrikeInputs.ownsControl(deflectionStrikeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void deflectionStrikeEndedInput(long input,long epoch){deflectionStrikeInputs.ended(input,epoch);}
    public static void deflectionStrikeCancelInput(){deflectionStrikeInputs.cancelBeforeClear(AcademyClient::sendDeflectionStrikeCancellation);}
    private static void sendDeflectionStrikeCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&deflectionStrikeInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_deflect_abort",input.wire());
        DeflectionStrikeEffects.abortLocal(input.input(),input.epoch());
    }
    private static DualPointInputState.Gates dualPointGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.DualPoint.ID;
        var context=new DualPointInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping();
        boolean mapped="meltdowner".equals(state.category)&&state.level>=1&&state.level<=5&&state.learned(skill)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill)
                &&slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot));
        return new DualPointInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),mapped,
                SkillCatalog.enabled(state.category,skill),mc.screen==null,
                GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,sourceEligible,state.cooldowns.getOrDefault(skill,0)==0);
    }
    private static boolean dualPointDelegateEligible(Minecraft mc,int slot,int physicalKey){
        return dualPointInputs.delegateEligible(dualPointGates(mc,slot,physicalKey));
    }
    private static YawPalmInputState.Gates yawPalmGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.YawPalm.ID;
        var context=new YawPalmInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)
                &&state.level>=2&&state.level<=5&&state.learned(skill)&&state.learned(cn.academy.port.highpower.BrakingTouch.ID)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new YawPalmInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible);
    }
    private static void tickYawPalmInput(Minecraft mc){
        var identity=yawPalmInputs.current();if(identity==null)return;
        yawPalmInputs.releaseWhenUp(physicalDown(yawPalmInputs.physicalKey()));
        yawPalmInputs.tick(yawPalmGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendYawPalmCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native crouch and movement. */
    private static void flushYawPalmStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=yawPalmInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused()){yawPalmCancelInput();return;}
        yawPalmInputs.flushPost(yawPalmGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.player!=null&&mc.player.isShiftKeyDown(),AcademyClient::sendYawPalmStart,AcademyClient::sendYawPalmCancellation);
    }
    private static void flushYawPalmRelease(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=yawPalmInputs.queuedRelease();if(identity==null)return;
        yawPalmInputs.flushReleasePost(yawPalmGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendYawPalmRelease,AcademyClient::sendYawPalmCancellation);
    }
    private static void sendYawPalmRelease(cn.academy.port.highpower.HighPowerInput.Input input){request("yaw_palm_release",input.wire());}
    private static void sendYawPalmStart(cn.academy.port.highpower.HighPowerInput.Input input){request("yaw_palm_tap",input.wire());}
    public static boolean yawPalmInputMatches(long input,long epoch,boolean crouch){return yawPalmInputs.permits(input,epoch,crouch);}
    public static boolean yawPalmControlValid(){
        var identity=yawPalmInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&yawPalmInputs.ownsControl(yawPalmGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void yawPalmEndedInput(long input,long epoch){yawPalmInputs.ended(input,epoch);}
    public static void yawPalmCancelInput(){yawPalmInputs.cancelBeforeClear(AcademyClient::sendYawPalmCancellation);}
    private static void sendYawPalmCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&yawPalmInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("yaw_palm_abort",input.wire());
        YawPalmEffects.abortLocal(input.input(),input.epoch());
    }
    private static TrajectoryReturnInputState.Gates trajectoryReturnGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.TrajectoryReturn.ID;
        var context=new TrajectoryReturnInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)
                &&state.level>=3&&state.level<=5&&state.learned(skill)&&state.learned(cn.academy.port.highpower.YawPalm.ID)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new TrajectoryReturnInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible);
    }
    private static void tickTrajectoryReturnInput(Minecraft mc){
        var identity=trajectoryReturnInputs.current();if(identity==null)return;
        trajectoryReturnInputs.releaseWhenUp(physicalDown(trajectoryReturnInputs.physicalKey()));
        trajectoryReturnInputs.tick(trajectoryReturnGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendTrajectoryReturnCancellation);
    }
    /** Called once from Post after native movement; no hold or crouch branch. */
    private static void flushTrajectoryReturnStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=trajectoryReturnInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused()){trajectoryReturnCancelInput();return;}
        trajectoryReturnInputs.flushPost(trajectoryReturnGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendTrajectoryReturnStart,AcademyClient::sendTrajectoryReturnCancellation);
    }
    private static void sendTrajectoryReturnStart(cn.academy.port.highpower.HighPowerInput.Input input){request("trajectory_return_tap",input.wire());}
    public static boolean trajectoryReturnInputMatches(long input,long epoch){return trajectoryReturnInputs.permits(input,epoch);}
    public static boolean trajectoryReturnControlValid(){
        var identity=trajectoryReturnInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&trajectoryReturnInputs.ownsControl(trajectoryReturnGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void trajectoryReturnEndedInput(long input,long epoch){trajectoryReturnInputs.ended(input,epoch);}
    public static void trajectoryReturnCancelInput(){trajectoryReturnInputs.cancelBeforeClear(AcademyClient::sendTrajectoryReturnCancellation);}
    private static void sendTrajectoryReturnCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&trajectoryReturnInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("trajectory_return_abort",input.wire());
        TrajectoryReturnEffects.abortLocal(input.input(),input.epoch());
    }
    private static ShearWindbladeInputState.Gates shearWindbladeGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.ShearWindblade.ID;
        var context=new ShearWindbladeInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.containerMenu==mc.player.inventoryMenu;
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)
                &&state.level>=2&&state.level<=5&&state.learned(skill)&&state.learned(cn.academy.port.highpower.DeflectionStrike.ID)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new ShearWindbladeInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible);
    }
    private static void tickShearWindbladeInput(Minecraft mc){
        var identity=shearWindbladeInputs.current();if(identity==null)return;
        shearWindbladeInputs.releaseWhenUp(physicalDown(shearWindbladeInputs.physicalKey()));
        shearWindbladeInputs.tick(shearWindbladeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendShearWindbladeCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native crouch and movement. */
    private static void flushShearWindbladeStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=shearWindbladeInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused()){shearWindbladeCancelInput();return;}
        shearWindbladeInputs.flushPost(shearWindbladeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendShearWindbladeStart,AcademyClient::sendShearWindbladeCancellation);
    }
    private static void sendShearWindbladeStart(cn.academy.port.highpower.HighPowerInput.Input input){request("shear_windblade_tap",input.wire());}
    public static boolean shearWindbladeInputMatches(long input,long epoch){return shearWindbladeInputs.permits(input,epoch);}
    public static boolean shearWindbladeControlValid(){
        var identity=shearWindbladeInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&shearWindbladeInputs.ownsControl(shearWindbladeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void shearWindbladeEndedInput(long input,long epoch){shearWindbladeInputs.ended(input,epoch);}
    public static void shearWindbladeCancelInput(){shearWindbladeInputs.cancelBeforeClear(AcademyClient::sendShearWindbladeCancellation);}
    private static void sendShearWindbladeCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&shearWindbladeInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("shear_windblade_abort",input.wire());
        ShearWindbladeEffects.abortLocal(input.input(),input.epoch());
    }
    private static PhaseSidestepInputState.Gates phaseSidestepGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.PhaseSidestep.ID;
        var context=new PhaseSidestepInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=mc.player!=null&&mc.level!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator();
        boolean sourceEligible=inGame&&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable&&!mc.player.noPhysics
                &&!mc.player.isPassenger()&&!mc.player.isSleeping()&&mc.player.onGround()
                &&!mc.player.isFallFlying()&&!mc.player.isSwimming()&&!mc.player.isInWater()&&!mc.player.isInLava();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)
                &&state.level>=1&&state.learned(skill)&&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new PhaseSidestepInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0,sourceEligible);
    }
    private static void tickPhaseSidestepInput(Minecraft mc){
        var identity=phaseSidestepInputs.current();if(identity==null)return;
        phaseSidestepInputs.releaseWhenUp(physicalDown(phaseSidestepInputs.physicalKey()));
        phaseSidestepInputs.tick(phaseSidestepGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendPhaseSidestepCancellation);
    }
    /** Called only from ClientTickEvent.Post, after LocalPlayer sent native crouch and movement. */
    private static void flushPhaseSidestepStart(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var identity=phaseSidestepInputs.queuedStart();if(identity==null)return;
        if(mc.isPaused()){phaseSidestepCancelInput();return;}
        phaseSidestepInputs.flushPost(phaseSidestepGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                AcademyClient::sendPhaseSidestepStart,AcademyClient::sendPhaseSidestepCancellation);
    }
    private static void sendPhaseSidestepStart(cn.academy.port.highpower.HighPowerInput.Input input){request("hp_phase_sidestep_start",input.wire());}
    public static boolean phaseSidestepInputMatches(long input,long epoch){return phaseSidestepInputs.permits(input,epoch);}
    public static boolean phaseSidestepControlValid(){
        var identity=phaseSidestepInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&phaseSidestepInputs.ownsControl(phaseSidestepGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void phaseSidestepEndedInput(long input,long epoch){phaseSidestepInputs.ended(input,epoch);}
    public static void phaseSidestepCancelInput(){phaseSidestepInputs.cancelBeforeClear(AcademyClient::sendPhaseSidestepCancellation);}
    private static void sendPhaseSidestepCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&phaseSidestepInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_phase_sidestep_abort",input.wire());
        PhaseSidestepEffects.abortLocal(input.input(),input.epoch());
    }
    private static SafeLandingProbeInputState.Gates safeLandingProbeGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.SafeLandingProbe.ID;
        var context=new SafeLandingProbeInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings(),
                mc.player==null?null:mc.player.getPose(),mc.player==null?0:mc.player.getX(),
                mc.player==null?0:mc.player.getY(),mc.player==null?0:mc.player.getZ());
        boolean inGame=binding!=null&&mc.player!=null&&mc.level!=null&&mc.getConnection()!=null
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable
                &&!mc.player.noPhysics&&!mc.player.isPassenger()&&!mc.player.isSleeping()
                &&mc.player.onGround()&&!mc.player.isFallFlying()&&!mc.player.isSwimming()
                &&!mc.player.isInWater()&&!mc.player.isInLava();
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(state.category)&&state.level>=1
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new SafeLandingProbeInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0);
    }
    private static void tickSafeLandingProbeInput(Minecraft mc){
        var identity=safeLandingProbeInputs.current();if(identity==null)return;
        safeLandingProbeInputs.tick(safeLandingProbeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),
                mc.isPaused(),AcademyClient::sendSafeLandingProbeCancellation);
    }
    public static boolean safeLandingProbeInputMatches(long input,long epoch){return safeLandingProbeInputs.permits(input,epoch);}
    public static boolean safeLandingProbeControlValid(){
        var identity=safeLandingProbeInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&safeLandingProbeInputs.ownsControl(safeLandingProbeGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void safeLandingProbePreviewInput(long input,long epoch,int remaining){safeLandingProbeInputs.preview(input,epoch,remaining);}
    public static void safeLandingProbeEndedInput(long input,long epoch){safeLandingProbeInputs.ended(input,epoch);}
    public static void safeLandingProbeLocalVisualLost(long input,long epoch){safeLandingProbeInputs.cancelVisual(input,epoch,AcademyClient::sendSafeLandingProbeCancellation);}
    private static void sendSafeLandingProbeCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&safeLandingProbeInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("hp_safe_landing_probe_abort",input.wire());
        SafeLandingProbeEffects.abortLocal(input.input(),input.epoch());
    }
    private static void cancelSafeLandingProbeBeforeDiscard(){safeLandingProbeInputs.cancelBeforeClear(AcademyClient::sendSafeLandingProbeCancellation);}
    private static CirculationReliefInputState.Gates circulationReliefGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.CirculationRelief.ID;
        var context=new CirculationReliefInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=binding!=null&&mc.player!=null&&mc.level!=null&&mc.getConnection()!=null
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable
                &&!mc.player.noPhysics&&!mc.player.isPassenger()&&!mc.player.isVehicle()&&!mc.player.isSleeping()
                &&!mc.player.isFallFlying()&&!mc.player.getAbilities().flying
                &&mc.player.containerMenu==mc.player.inventoryMenu;
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)&&state.level>=3&&state.level<=5
                &&state.learned(cn.academy.port.highpower.InertialAnchor.ID)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new CirculationReliefInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0);
    }
    private static void tickCirculationReliefInput(Minecraft mc){
        var identity=circulationReliefInputs.current();if(identity==null)return;
        sendPersistentCancellation("circulation_relief_end",circulationReliefInputs.tick(
                circulationReliefGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),mc.isPaused()),AcademyClient::cancelCirculationReliefLocally);
    }
    private static void cancelCirculationReliefLocally(cn.academy.port.highpower.HighPowerInput.Input input){CirculationReliefEffects.abortLocal(input.input(),input.epoch());}
    public static boolean circulationReliefInputMatches(long input,long epoch){return circulationReliefInputs.permits(input,epoch);}
    public static void circulationReliefAcceptedInput(long input,long epoch){circulationReliefInputs.accepted(input,epoch);}
    public static void circulationReliefRejectedInput(long input,long epoch){circulationReliefInputs.rejected(input,epoch);}
    /** Only a matching server ENDED receipt is a no-wire terminal. */
    public static void circulationReliefEndedInput(long input,long epoch){circulationReliefInputs.ended(input,epoch);}
    /** Explicit visual owner invalidity cancels; quiet heartbeat hiding never invokes this method. */
    public static void circulationReliefLocalVisualLost(long input,long epoch){circulationReliefInputs.cancelVisual(input,epoch,AcademyClient::sendCirculationReliefCancellation);}
    private static void sendCirculationReliefCancellation(cn.academy.port.highpower.HighPowerInput.Input input){sendPersistentCancellation("circulation_relief_end",input,AcademyClient::cancelCirculationReliefLocally);}
    private static void cancelCirculationReliefBeforeDiscard(){circulationReliefInputs.cancelBeforeClear(AcademyClient::sendCirculationReliefCancellation);}
    public static boolean circulationReliefControlValid(){
        var identity=circulationReliefInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&circulationReliefInputs.ownsControl(circulationReliefGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void cancelCirculationReliefForReload(){cancelCirculationReliefBeforeDiscard();}
    private static AutonomousVectorCircleInputState.Gates autonomousVectorCircleGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.AutonomousVectorCircle.ID;
        var context=new AutonomousVectorCircleInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=binding!=null&&mc.player!=null&&mc.level!=null&&mc.getConnection()!=null
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable
                &&!mc.player.noPhysics&&!mc.player.isPassenger()&&!mc.player.isVehicle()&&!mc.player.isSleeping()
                &&!mc.player.isFallFlying()&&!mc.player.getAbilities().flying
                &&mc.player.containerMenu==mc.player.inventoryMenu;
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)&&state.level==5
                &&state.learned(cn.academy.port.highpower.MultiAxisReflection.ID)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new AutonomousVectorCircleInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0);
    }
    private static void tickAutonomousVectorCircleInput(Minecraft mc){
        var identity=autonomousVectorCircleInputs.current();if(identity==null)return;
        sendPersistentCancellation("autonomous_vector_circle_end",autonomousVectorCircleInputs.tick(
                autonomousVectorCircleGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),mc.isPaused()),AcademyClient::cancelAutonomousVectorCircleLocally);
    }
    private static void cancelAutonomousVectorCircleLocally(cn.academy.port.highpower.HighPowerInput.Input input){AutonomousVectorCircleEffects.abortLocal(input.input(),input.epoch());}
    public static boolean autonomousVectorCircleInputMatches(long input,long epoch){return autonomousVectorCircleInputs.permits(input,epoch);}
    public static void autonomousVectorCircleAcceptedInput(long input,long epoch){autonomousVectorCircleInputs.accepted(input,epoch);}
    public static void autonomousVectorCircleRejectedInput(long input,long epoch){autonomousVectorCircleInputs.rejected(input,epoch);}
    /** Only a matching server ENDED receipt is a no-wire terminal. */
    public static void autonomousVectorCircleEndedInput(long input,long epoch){autonomousVectorCircleInputs.ended(input,epoch);}
    /** Explicit visual owner invalidity cancels; quiet heartbeat hiding never invokes this method. */
    public static void autonomousVectorCircleLocalVisualLost(long input,long epoch){autonomousVectorCircleInputs.cancelVisual(input,epoch,AcademyClient::sendAutonomousVectorCircleCancellation);}
    private static void sendAutonomousVectorCircleCancellation(cn.academy.port.highpower.HighPowerInput.Input input){sendPersistentCancellation("autonomous_vector_circle_end",input,AcademyClient::cancelAutonomousVectorCircleLocally);}
    private static void cancelAutonomousVectorCircleBeforeDiscard(){autonomousVectorCircleInputs.cancelBeforeClear(AcademyClient::sendAutonomousVectorCircleCancellation);}
    public static boolean autonomousVectorCircleControlValid(){
        var identity=autonomousVectorCircleInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&autonomousVectorCircleInputs.ownsControl(autonomousVectorCircleGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void cancelAutonomousVectorCircleForReload(){cancelAutonomousVectorCircleBeforeDiscard();}
    private static MultiAxisReflectionInputState.Gates multiAxisReflectionGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.MultiAxisReflection.ID;
        var context=new MultiAxisReflectionInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=binding!=null&&mc.player!=null&&mc.level!=null&&mc.getConnection()!=null
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable
                &&!mc.player.noPhysics&&!mc.player.isPassenger()&&!mc.player.isVehicle()&&!mc.player.isSleeping()
                &&!mc.player.isFallFlying()&&!mc.player.getAbilities().flying
                &&mc.player.containerMenu==mc.player.inventoryMenu;
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)&&state.level>=4&&state.level<=5
                &&state.learned(cn.academy.port.highpower.VectorCage.ID)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new MultiAxisReflectionInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0);
    }
    private static void tickMultiAxisReflectionInput(Minecraft mc){
        var identity=multiAxisReflectionInputs.current();if(identity==null)return;
        sendPersistentCancellation("multi_axis_reflection_end",multiAxisReflectionInputs.tick(
                multiAxisReflectionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),mc.isPaused()),AcademyClient::cancelMultiAxisReflectionLocally);
    }
    private static void cancelMultiAxisReflectionLocally(cn.academy.port.highpower.HighPowerInput.Input input){MultiAxisReflectionEffects.abortLocal(input.input(),input.epoch());}
    public static boolean multiAxisReflectionInputMatches(long input,long epoch){return multiAxisReflectionInputs.permits(input,epoch);}
    public static void multiAxisReflectionAcceptedInput(long input,long epoch){multiAxisReflectionInputs.accepted(input,epoch);}
    public static void multiAxisReflectionRejectedInput(long input,long epoch){multiAxisReflectionInputs.rejected(input,epoch);}
    /** Only a matching server ENDED receipt is a no-wire terminal. */
    public static void multiAxisReflectionEndedInput(long input,long epoch){multiAxisReflectionInputs.ended(input,epoch);}
    /** Explicit visual owner invalidity cancels; quiet heartbeat hiding never invokes this method. */
    public static void multiAxisReflectionLocalVisualLost(long input,long epoch){multiAxisReflectionInputs.cancelVisual(input,epoch,AcademyClient::sendMultiAxisReflectionCancellation);}
    private static void sendMultiAxisReflectionCancellation(cn.academy.port.highpower.HighPowerInput.Input input){sendPersistentCancellation("multi_axis_reflection_end",input,AcademyClient::cancelMultiAxisReflectionLocally);}
    private static void cancelMultiAxisReflectionBeforeDiscard(){multiAxisReflectionInputs.cancelBeforeClear(AcademyClient::sendMultiAxisReflectionCancellation);}
    public static boolean multiAxisReflectionControlValid(){
        var identity=multiAxisReflectionInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&multiAxisReflectionInputs.ownsControl(multiAxisReflectionGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void cancelMultiAxisReflectionForReload(){cancelMultiAxisReflectionBeforeDiscard();}
    private static WindPressureLiftInputState.Gates windPressureLiftGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.WindPressureLift.ID;
        var context=new WindPressureLiftInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=binding!=null&&mc.player!=null&&mc.level!=null&&mc.getConnection()!=null
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable
                &&!mc.player.noPhysics&&!mc.player.isPassenger()&&!mc.player.isVehicle()&&!mc.player.isSleeping()
                &&!mc.player.isFallFlying()&&!mc.player.getAbilities().flying
                &&mc.player.containerMenu==mc.player.inventoryMenu;
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)&&state.level>=4&&state.level<=5
                &&state.learned(cn.academy.port.highpower.MultiAxisReflection.ID)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new WindPressureLiftInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0);
    }
    private static void tickWindPressureLiftInput(Minecraft mc){
        var identity=windPressureLiftInputs.current();if(identity==null)return;
        sendPersistentCancellation("wind_pressure_lift_end",windPressureLiftInputs.tick(
                windPressureLiftGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),mc.isPaused()),AcademyClient::cancelWindPressureLiftLocally);
    }
    private static void cancelWindPressureLiftLocally(cn.academy.port.highpower.HighPowerInput.Input input){WindPressureLiftClientMotion.abortLocal(input.input(),input.epoch());WindPressureLiftEffects.abortLocal(input.input(),input.epoch());}
    public static boolean windPressureLiftInputMatches(long input,long epoch){return windPressureLiftInputs.permits(input,epoch);}
    public static void windPressureLiftAcceptedInput(long input,long epoch){windPressureLiftInputs.accepted(input,epoch);}
    public static void windPressureLiftRejectedInput(long input,long epoch){windPressureLiftInputs.rejected(input,epoch);}
    /** Only a matching server ENDED receipt is a no-wire terminal. */
    public static void windPressureLiftEndedInput(long input,long epoch){windPressureLiftInputs.ended(input,epoch);}
    /** Explicit visual owner invalidity cancels; quiet heartbeat hiding never invokes this method. */
    public static void windPressureLiftLocalVisualLost(long input,long epoch){windPressureLiftInputs.cancelVisual(input,epoch,AcademyClient::sendWindPressureLiftCancellation);}
    private static void sendWindPressureLiftCancellation(cn.academy.port.highpower.HighPowerInput.Input input){sendPersistentCancellation("wind_pressure_lift_end",input,AcademyClient::cancelWindPressureLiftLocally);}
    private static void cancelWindPressureLiftBeforeDiscard(){windPressureLiftInputs.cancelBeforeClear(AcademyClient::sendWindPressureLiftCancellation);}
    public static boolean windPressureLiftControlValid(){
        var identity=windPressureLiftInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&windPressureLiftInputs.ownsControl(windPressureLiftGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void cancelWindPressureLiftForReload(){cancelWindPressureLiftBeforeDiscard();}
    private static VectorCageInputState.Gates vectorCageGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.VectorCage.ID;
        var context=new VectorCageInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=binding!=null&&mc.player!=null&&mc.level!=null&&mc.getConnection()!=null
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable
                &&!mc.player.noPhysics&&!mc.player.isPassenger()&&!mc.player.isVehicle()&&!mc.player.isSleeping()
                &&!mc.player.isFallFlying()&&!mc.player.getAbilities().flying
                &&mc.player.containerMenu==mc.player.inventoryMenu;
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)&&state.level>=4&&state.level<=5
                &&state.learned(skill)&&state.learned(cn.academy.port.highpower.TrajectoryReturn.ID)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new VectorCageInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0);
    }
    private static void tickVectorCageInput(Minecraft mc){
        var id=vectorCageInputs.current();if(id==null)return;
        vectorCageInputs.releaseWhenUp(physicalDown(vectorCageInputs.physicalKey()));
        vectorCageInputs.tick(vectorCageGates(mc,id.slot(),sourceKey(SLOTS[id.slot()].getKey())),mc.isPaused(),AcademyClient::sendVectorCageCancellation);
    }
    private static void flushVectorCageInput(){
        var mc=Minecraft.getInstance();synchronizeSession(mc);var id=vectorCageInputs.current();if(id==null)return;
        if(mc.isPaused())return;
        vectorCageInputs.flushPost(vectorCageGates(mc,id.slot(),sourceKey(SLOTS[id.slot()].getKey())),
                input->request("vector_cage_start",input.wire()),input->request("vector_cage_release",input.wire()),AcademyClient::sendVectorCageCancellation);
    }
    public static boolean vectorCageInputMatches(long input,long epoch){return vectorCageInputs.permits(input,epoch);}
    public static void vectorCageAcceptedInput(long input,long epoch,int phase){vectorCageInputs.accepted(input,epoch,phase);}
    public static void vectorCageRejectedInput(long input,long epoch){vectorCageInputs.rejected(input,epoch);}
    public static void vectorCageEndedInput(long input,long epoch){vectorCageInputs.ended(input,epoch);}
    public static void vectorCageCancelInput(){vectorCageInputs.cancelBeforeClear(AcademyClient::sendVectorCageCancellation);}
    public static boolean vectorCageControlValid(){
        var id=vectorCageInputs.current();var mc=Minecraft.getInstance();
        return id!=null&&vectorCageInputs.ownsControl(vectorCageGates(mc,id.slot(),sourceKey(SLOTS[id.slot()].getKey())));
    }
    private static void sendVectorCageCancellation(cn.academy.port.highpower.HighPowerInput.Input input){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.getConnection()!=null&&mc.player.getId()==input.actor()
                &&singleOwnerEpochMatches(input.epoch())&&vectorCageInputs.ownsEnvironment(mc.level,mc.player,mc.getConnection()))
            request("vector_cage_abort",input.wire());
        VectorCageEffects.abortLocal(input.input(),input.epoch());
    }
    private static CirculationListeningInputState.Gates circulationListeningGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.CirculationListening.ID;
        var context=new CirculationListeningInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=binding!=null&&mc.player!=null&&mc.level!=null&&mc.getConnection()!=null
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable
                &&!mc.player.noPhysics&&!mc.player.isPassenger()&&!mc.player.isVehicle()&&!mc.player.isSleeping()
                &&!mc.player.isFallFlying()&&!mc.player.getAbilities().flying
                &&mc.player.containerMenu==mc.player.inventoryMenu;
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)&&state.level>=3&&state.level<=5
                &&state.learned(cn.academy.port.highpower.CirculationRelief.ID)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new CirculationListeningInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0);
    }
    private static void tickCirculationListeningInput(Minecraft mc){
        var identity=circulationListeningInputs.current();if(identity==null)return;
        sendPersistentCancellation("circ_listen_abort",circulationListeningInputs.tick(
                circulationListeningGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),mc.isPaused()),AcademyClient::cancelCirculationListeningLocally);
    }
    private static void cancelCirculationListeningLocally(cn.academy.port.highpower.HighPowerInput.Input input){CirculationListeningEffects.abortLocal(input.input(),input.epoch());}
    public static boolean circulationListeningInputMatches(long input,long epoch){return circulationListeningInputs.permits(input,epoch);}
    public static void circulationListeningAcceptedInput(long input,long epoch){circulationListeningInputs.accepted(input,epoch);}
    public static void circulationListeningRejectedInput(long input,long epoch){circulationListeningInputs.rejected(input,epoch);}
    /** Only a matching server ENDED receipt is a no-wire terminal. */
    public static void circulationListeningEndedInput(long input,long epoch){circulationListeningInputs.ended(input,epoch);}
    /** Explicit visual owner invalidity cancels; quiet heartbeat hiding never invokes this method. */
    public static void circulationListeningLocalVisualLost(long input,long epoch){circulationListeningInputs.cancelVisual(input,epoch,AcademyClient::sendCirculationListeningCancellation);}
    private static void sendCirculationListeningCancellation(cn.academy.port.highpower.HighPowerInput.Input input){sendPersistentCancellation("circ_listen_abort",input,AcademyClient::cancelCirculationListeningLocally);}
    private static void cancelCirculationListeningBeforeDiscard(){circulationListeningInputs.cancelBeforeClear(AcademyClient::sendCirculationListeningCancellation);}
    public static boolean circulationListeningControlValid(){
        var identity=circulationListeningInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&circulationListeningInputs.ownsControl(circulationListeningGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void cancelCirculationListeningForReload(){cancelCirculationListeningBeforeDiscard();vectorCageCancelInput();}
    private static InertialAnchorInputState.Gates inertialAnchorGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.InertialAnchor.ID;
        var context=new InertialAnchorInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=binding!=null&&mc.player!=null&&mc.level!=null&&mc.getConnection()!=null
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable
                &&!mc.player.noPhysics&&!mc.player.isPassenger()&&!mc.player.isVehicle()&&!mc.player.isSleeping()
                &&!mc.player.isFallFlying()&&!mc.player.getAbilities().flying
                &&mc.player.containerMenu==mc.player.inventoryMenu;
        boolean mapped=cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(state.category)&&state.level>=2&&state.level<=5
                &&state.learned(cn.academy.port.highpower.DeflectionStrike.ID)
                &&state.learned(cn.academy.port.highpower.VectorParry.ID)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new InertialAnchorInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0);
    }
    private static void tickInertialAnchorInput(Minecraft mc){
        var identity=inertialAnchorInputs.current();if(identity==null)return;
        sendPersistentCancellation("inertial_anchor_end",inertialAnchorInputs.tick(
                inertialAnchorGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),mc.isPaused()),AcademyClient::cancelInertialAnchorLocally);
    }
    private static void cancelInertialAnchorLocally(cn.academy.port.highpower.HighPowerInput.Input input){InertialAnchorEffects.abortLocal(input.input(),input.epoch());}
    public static boolean inertialAnchorInputMatches(long input,long epoch){return inertialAnchorInputs.permits(input,epoch);}
    public static void inertialAnchorAcceptedInput(long input,long epoch){inertialAnchorInputs.accepted(input,epoch);}
    /** Only a matching server ENDED receipt is a no-wire terminal. */
    public static void inertialAnchorEndedInput(long input,long epoch){inertialAnchorInputs.ended(input,epoch);}
    /** Visual eligibility/lease loss must close the server controller while its input is still owned. */
    public static void inertialAnchorLocalVisualLost(long input,long epoch){inertialAnchorInputs.cancelVisual(input,epoch,AcademyClient::sendInertialAnchorCancellation);}
    private static void sendInertialAnchorCancellation(cn.academy.port.highpower.HighPowerInput.Input input){sendPersistentCancellation("inertial_anchor_end",input,AcademyClient::cancelInertialAnchorLocally);}
    private static void cancelInertialAnchorBeforeDiscard(){inertialAnchorInputs.cancelBeforeClear(AcademyClient::sendInertialAnchorCancellation);}
    public static boolean inertialAnchorControlValid(){
        var identity=inertialAnchorInputs.current();var mc=Minecraft.getInstance();
        return identity!=null&&inertialAnchorInputs.ownsControl(inertialAnchorGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())));
    }
    public static void cancelInertialAnchorForReload(){cancelInertialAnchorBeforeDiscard();}
    private static ZeroGapArmorInputState.Gates zeroGapArmorGates(Minecraft mc,int slot,int physicalKey){
        String skill=cn.academy.port.highpower.ZeroGapArmor.ID;
        var context=new ZeroGapArmorInputState.DispatchContext(mc.level,mc.player,mc.getConnection(),inputSessionEpoch(),
                mc.player==null?-1:mc.player.getId(),serverSingleEpoch,state.presets.current(),state.presets.revision(),mappings());
        boolean inGame=binding!=null&&mc.player!=null&&mc.level!=null&&mc.getConnection()!=null
                &&mc.player.isAlive()&&!mc.player.isRemoved()&&!mc.player.isSpectator()
                &&!mc.player.getAbilities().instabuild&&!mc.player.getAbilities().invulnerable
                &&!mc.player.noPhysics&&!mc.player.isPassenger()&&!mc.player.isSleeping();
        boolean mapped="meltdowner".equals(state.category)&&state.level==5
                &&state.learned(cn.academy.port.highpower.HighPowerSkills.DUAL_POINT)
                &&state.learned(cn.academy.port.highpower.HighPowerSkills.SCATTER_PLATES)
                &&state.learned(cn.academy.port.highpower.HighPowerSkills.DISSIPATION_CURTAIN)
                &&cn.academy.port.preset.PresetSkills.mappedUsable(state,skill);
        return new ZeroGapArmorInputState.Gates(context,slot,physicalKey,inGame,state.isActivated(),
                state.activated&&state.overloadFine&&!state.interfering&&!state.consumptionInProgress(),
                mapped,SkillCatalog.enabled(state.category,skill),
                slot>=0&&slot<SLOTS.length&&skill.equals(state.presets.currentSkill(slot)),
                mc.screen==null,GLFW.glfwGetWindowAttrib(mc.getWindow().getWindow(),GLFW.GLFW_FOCUSED)==GLFW.GLFW_TRUE,
                mc.getCameraEntity()==mc.player,state.cooldowns.getOrDefault(skill,0)==0);
    }
    private static void tickZeroGapArmorInput(Minecraft mc){
        var identity=zeroGapArmorInputs.current();if(identity==null)return;
        sendPersistentCancellation("hp_zero_gap_armor_abort",zeroGapArmorInputs.tick(
                zeroGapArmorGates(mc,identity.slot(),sourceKey(SLOTS[identity.slot()].getKey())),mc.isPaused()),AcademyClient::cancelZeroGapArmorLocally);
    }
    private static void cancelZeroGapArmorLocally(cn.academy.port.highpower.HighPowerInput.Input input){ZeroGapArmorEffects.abortLocal(input.input(),input.epoch());}
    public static boolean zeroGapArmorInputMatches(long input,long epoch){return zeroGapArmorInputs.permits(input,epoch);}
    public static void zeroGapArmorAcceptedInput(long input,long epoch){zeroGapArmorInputs.accepted(input,epoch);}
    /** Only a matching server ENDED receipt is a no-wire terminal. */
    public static void zeroGapArmorEndedInput(long input,long epoch){zeroGapArmorInputs.ended(input,epoch);}
    /** Visual eligibility/lease loss must close the server controller while its input is still owned. */
    public static void zeroGapArmorLocalVisualLost(long input,long epoch){zeroGapArmorInputs.cancelVisual(input,epoch,AcademyClient::sendZeroGapArmorCancellation);}
    private static void sendZeroGapArmorCancellation(cn.academy.port.highpower.HighPowerInput.Input input){sendPersistentCancellation("hp_zero_gap_armor_abort",input,AcademyClient::cancelZeroGapArmorLocally);}
    private static void cancelZeroGapArmorBeforeDiscard(){zeroGapArmorInputs.cancelBeforeClear(AcademyClient::sendZeroGapArmorCancellation);}
    private static void cancelScatterPlatesLocally(cn.academy.port.highpower.HighPowerInput.Input input){ScatterPlateEffects.abortLocal(input.input(),input.epoch());}
    public static boolean scatterPlateInputMatches(long input,long epoch){return scatterPlateInputs.permits(input,epoch);}
    public static void scatterPlateAcceptedInput(long input,long epoch){scatterPlateInputs.accepted(input,epoch);}
    public static void scatterPlateEndedInput(long input,long epoch){scatterPlateInputs.ended(input,epoch);}
    private static void cancelMagneticDomainLocally(cn.academy.port.highpower.HighPowerInput.Input input){MagneticDomainEffects.abortLocal(input.input(),input.epoch());}
    public static boolean magneticDomainInputMatches(long input,long epoch){return magneticDomainInputs.permits(input,epoch);}
    public static void magneticDomainAcceptedInput(long input,long epoch){magneticDomainInputs.accepted(input,epoch);}
    public static void magneticDomainEndedInput(long input,long epoch){magneticDomainInputs.ended(input,epoch);}
    private static void cancelRailArrayLocally(cn.academy.port.highpower.HighPowerInput.Input input){RailThunderArrayEffects.abortLocal(input.input(),input.epoch());}
    private static void releaseRailArrayInput(cn.academy.port.highpower.HighPowerInput.Input identity){
        if(identity!=null&&singleOwnerEpochMatches(identity.epoch()))request("hp_rail_thunder_array_release",identity.wire());
    }
    public static boolean railArrayInputMatches(long input,long epoch){return railArrayInputs.permits(input,epoch);}
    public static void railArrayAcceptedInput(long input,long epoch){railArrayInputs.accepted(input,epoch);}
    public static void railArrayEndedInput(long input,long epoch){railArrayInputs.ended(input,epoch);}
    private static void cancelPotentialBufferLocally(cn.academy.port.highpower.HighPowerInput.Input input){PotentialBufferEffects.abortLocal(input.input(),input.epoch());}
    private static void cancelThermalShellLocally(cn.academy.port.highpower.HighPowerInput.Input input){ThermalShellEffects.abortLocal(input.input(),input.epoch());}
    public static boolean potentialBufferInputMatches(long input,long epoch){return potentialBufferInputs.permits(input,epoch);}
    public static boolean thermalShellInputMatches(long input,long epoch){return thermalShellInputs.permits(input,epoch);}
    public static void potentialBufferAcceptedInput(long input,long epoch){potentialBufferInputs.accepted(input,epoch);}
    public static void thermalShellAcceptedInput(long input,long epoch){thermalShellInputs.accepted(input,epoch);}
    public static void potentialBufferEndedInput(long input,long epoch){potentialBufferInputs.ended(input,epoch);}
    public static void thermalShellEndedInput(long input,long epoch){thermalShellInputs.ended(input,epoch);}
    private static void cancelThunderNetLocally(cn.academy.port.highpower.HighPowerInput.Input input){ThunderNetEffects.abortLocal(input.input(),input.epoch());}
    private static void releaseThunderNetInput(cn.academy.port.highpower.HighPowerInput.Input identity){
        if(identity!=null&&singleOwnerEpochMatches(identity.epoch()))request("hp_thunder_net_release",identity.wire());
    }
    public static boolean thunderNetInputMatches(long input,long epoch){
        var held=thunderNetInputs.current();return held!=null&&held.identity().input()==input&&held.identity().epoch()==epoch;
    }
    private static void cancelIronLanceLocally(cn.academy.port.highpower.HighPowerInput.Input input){
        IronSandLanceEffects.abortLocal(input.input(),input.epoch());
    }
    private static void cancelGroundStepLocally(cn.academy.port.highpower.HighPowerInput.Input input){
        MagneticGroundStepClientMotion.abortLocal(input.input(),input.epoch());
        MagneticGroundStepEffects.abortLocal(input.input(),input.epoch());
    }
    private static void cancelItemPullLocally(cn.academy.port.highpower.HighPowerInput.Input input){
        MagneticItemPullEffects.abortLocal(input.input(),input.epoch());
        MagneticArrowSeparationEffects.abortLocal(input.input(),input.epoch());
    }
    public static void cancelItemPullInput(){
        sendPersistentCancellation("hp_item_pull_abort",itemPullInputs.cancel(),AcademyClient::cancelItemPullLocally);
    }
    private static void cancelPersistentHighPowerInputs(){
        multidimensionalLandingArrayCancelInput();
        SpatialPincerClient.cancelPending();
        spatialPincerCancelInput();
        TacticalCoordinateClient.cancelPending();
        displacedProjectionCancelInput();
        recoveryMarkCancelInput();
        returnMarkCancelInput();
        spatialRelayCancelInput();
        shortInterceptionCancelInput();
        cornerExchangeCancelInput();
        emergencyReturnCancelInput();
        crossSubstitutionCancelInput();
        vectorParryCancelInput();
        brakingTouchCancelInput();newSkillCancelAll();
        deflectionStrikeCancelInput();yawPalmCancelInput();trajectoryReturnCancelInput();
        shearWindbladeCancelInput();
        phaseSidestepCancelInput();
        cancelSafeLandingProbeBeforeDiscard();
        cancelCirculationReliefBeforeDiscard();cancelAutonomousVectorCircleBeforeDiscard();cancelMultiAxisReflectionBeforeDiscard();cancelWindPressureLiftBeforeDiscard();cancelCirculationListeningBeforeDiscard();vectorCageCancelInput();
        cancelInertialAnchorBeforeDiscard();
        sendPersistentCancellation("hp_zero_gap_armor_abort",zeroGapArmorInputs.cancel(),AcademyClient::cancelZeroGapArmorLocally);
        sendPersistentCancellation("hp_disintegration_sweep_abort",disintegrationSweepInputs.cancel(),AcademyClient::cancelDisintegrationSweepLocally);
        sendPersistentCancellation("hp_folded_mirror_abort",foldedMirrorInputs.cancel(),AcademyClient::cancelFoldedMirrorLocally);
        sendPersistentCancellation("hp_remote_relay_abort",remoteScatterRelayInputs.cancel(),AcademyClient::cancelRemoteScatterRelayLocally);
        sendPersistentCancellation("hp_scatter_locator_abort",scatterLocatorInputs.cancel(),AcademyClient::cancelScatterLocatorLocally);
        sendPersistentCancellation("hp_particle_laminate_abort",particleLaminateInputs.cancel(),AcademyClient::cancelParticleLaminateLocally);
        sendPersistentCancellation("hp_multicore_canopy_abort",multicoreCanopyInputs.cancel(),AcademyClient::cancelMulticoreCanopyLocally);
        sendPersistentCancellation("hp_dissipation_curtain_abort",dissipationCurtainInputs.cancel(),AcademyClient::cancelDissipationCurtainLocally);
        sendPersistentCancellation("hp_beam_etching_abort",beamEtchingInputs.cancel(),AcademyClient::cancelBeamEtchingLocally);
        sendPersistentCancellation("hp_point_meltdown_abort",pointMeltdownInputs.cancel(),AcademyClient::cancelPointMeltdownLocally);
        sendPersistentCancellation("hp_dual_point_abort",dualPointInputs.cancel(),AcademyClient::cancelDualPointLocally);
        sendPersistentCancellation("hp_scatter_plates_abort",scatterPlateInputs.cancel(),AcademyClient::cancelScatterPlatesLocally);
        sendPersistentCancellation("hp_magnetic_domain_abort",magneticDomainInputs.cancel(),AcademyClient::cancelMagneticDomainLocally);
        sendPersistentCancellation("hp_rail_thunder_array_abort",railArrayInputs.cancel(),AcademyClient::cancelRailArrayLocally);
        sendPersistentCancellation("hp_potential_buffer_abort",potentialBufferInputs.cancel(),AcademyClient::cancelPotentialBufferLocally);
        sendPersistentCancellation("hp_thermal_shell_abort",thermalShellInputs.cancel(),AcademyClient::cancelThermalShellLocally);
        sendPersistentCancellation("hp_thunder_net_abort",thunderNetInputs.cancel(),AcademyClient::cancelThunderNetLocally);
        sendPersistentCancellation("hp_iron_lance_abort",ironLanceInputs.cancel(),AcademyClient::cancelIronLanceLocally);
        MagneticItemPullSelection.cancel();
        sendPersistentCancellation("hp_item_pull_abort",itemPullInputs.cancel(),AcademyClient::cancelItemPullLocally);
        sendPersistentCancellation("hp_tether_abort",tetherInputs.cancel(),ignored->PolarityTetherEffects.abortLocal());
        sendPersistentCancellation("hp_circuit_abort",circuitInputs.cancel(),ignored->ConductiveCircuitEffects.abortLocal());
        sendPersistentCancellation("hp_ground_abort",groundingInputs.cancel(),input->GroundingReturnEffects.abortLocal(input.input(),input.epoch()));
        sendPersistentCancellation("hp_ground_step_abort",groundStepInputs.cancel(),AcademyClient::cancelGroundStepLocally);
    }
    private static void sendPersistentCancellation(String action,cn.academy.port.highpower.HighPowerInput.Input input,java.util.function.Consumer<cn.academy.port.highpower.HighPowerInput.Input> hideLocal){
        if(input==null)return;hideLocal.accept(input);var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null&&mc.player.getId()==input.actor()&&singleOwnerEpochMatches(input.epoch()))request(action,input.wire());
    }
    private static void clearEffects(){NewSkillClient.resetAll();NewSkillEffects.clear();vectorCageInputs.reset();VectorCageEffects.clear();circulationListeningInputs.reset();CirculationListeningEffects.clear();circulationReliefInputs.reset();CirculationReliefEffects.clear();autonomousVectorCircleInputs.reset();AutonomousVectorCircleEffects.clear();multiAxisReflectionInputs.reset();MultiAxisReflectionEffects.clear();windPressureLiftInputs.reset();WindPressureLiftClientMotion.clear();WindPressureLiftEffects.clear();inertialAnchorInputs.reset();InertialAnchorEffects.clear();brakingTouchInputs.reset();BrakingTouchEffects.clear();vectorParryInputs.reset();VectorParryEffects.clear();deflectionStrikeInputs.reset();DeflectionStrikeEffects.clear();yawPalmInputs.reset();YawPalmEffects.clear();trajectoryReturnInputs.reset();TrajectoryReturnEffects.clear();shearWindbladeInputs.reset();ShearWindbladeEffects.clear();multidimensionalLandingArrayInputs.reset();MultidimensionalLandingArrayEffects.clear();SpatialPincerClient.clear();spatialPincerInputs.reset();SpatialPincerEffects.clear();TacticalCoordinateClient.cancelPending();spatialRelayInputs.reset();SpatialRelayEffects.clear();shortInterceptionInputs.reset();ShortInterceptionEffects.clear();cornerExchangeInputs.reset();CornerExchangeEffects.clear();emergencyReturnInputs.reset();EmergencyReturnEffects.clear();crossSubstitutionInputs.reset();CrossSubstitutionEffects.clear();recoveryMarkInputs.reset();RecoveryMarkEffects.clear();returnMarkInputs.reset();ReturnMarkEffects.clear();displacedProjectionInputs.reset();DisplacedProjectionEffects.clear();phaseSidestepInputs.reset();PhaseSidestepEffects.clear();safeLandingProbeInputs.reset();SafeLandingProbeEffects.clear();zeroGapArmorInputs.reset();ZeroGapArmorEffects.clear();multicoreCanopyInputs.reset();MulticoreCanopyEffects.clear();dissipationCurtainInputs.reset();DissipationCurtainEffects.clear();BeamStepClientMotion.clear();BeamStepEffects.clear();disintegrationSweepInputs.reset();DisintegrationSweepEffects.clear();foldedMirrorInputs.reset();FoldedMirrorEffects.clear();remoteScatterRelayInputs.reset();RemoteScatterRelayEffects.clear();scatterLocatorInputs.reset();ScatterLocatorEffects.clear();particleLaminateInputs.reset();ParticleLaminateEffects.clear();thermalShellInputs.reset();ThermalShellEffects.clear();beamEtchingInputs.reset();BeamEtchingEffects.clear();pointMeltdownInputs.reset();PointMeltdownEffects.clear();scatterPlateInputs.reset();ScatterPlateEffects.clear();dualPointInputs.reset();DualPointEffects.clear();magneticDomainInputs.reset();MagneticDomainEffects.clear();railArrayInputs.reset();RailThunderArrayEffects.clear();potentialBufferInputs.reset();PotentialBufferEffects.clear();thunderNetInputs.reset();ThunderNetEffects.clear();ironLanceInputs.reset();IronSandLanceEffects.clear();MagneticItemPullSelection.clear();itemPullInputs.reset();MagneticItemPullEffects.clear();MagneticArrowSeparationEffects.clear();tetherInputs.reset();circuitInputs.reset();groundingInputs.reset();groundStepInputs.reset();groundStepBlocker="";groundStepBlockerAt=Long.MIN_VALUE;MagneticGroundStepClientMotion.clear();MagneticGroundStepEffects.clear();PolarityTetherEffects.clear();ConductiveCircuitEffects.clear();GroundingReturnEffects.clear();ClassicFirstSkillEffects.clearSingleKeyContexts();ClassicCoinEffects.clear();ClassicGroundShockEffects.clear();ClassicMagneticEffects.clear();ClassicThunderBoltEffects.clear();ClassicMineDetectEffects.clear();ClassicBodyIntensifyEffects.clear();ClassicThunderClapEffects.clear();ClassicMeltdownerStarterEffects.clear();ClassicMeltdownerBeamEffects.clear();ClassicMeltdownerLateEffects.clear();ClassicTeleporterProgressionEffects.clear();ClassicTeleporterFinalEffects.clear();ClassicVectorStarterEffects.clear();ClassicVectorCombatEffects.clear();ClassicVectorFinalEffects.clear();}
    private static void synchronizeSession(Minecraft mc){boolean changed=session.synchronize(mc.level,mc.player)||ownedConnection!=mc.getConnection();if(changed){spatialPincerCancelInput();spatialRelayCancelInput();shortInterceptionCancelInput();cornerExchangeCancelInput();emergencyReturnCancelInput();crossSubstitutionCancelInput();recoveryMarkCancelInput();returnMarkCancelInput();displacedProjectionCancelInput();deflectionStrikeCancelInput();yawPalmCancelInput();trajectoryReturnCancelInput();shearWindbladeCancelInput();vectorParryCancelInput();brakingTouchCancelInput();newSkillCancelAll();phaseSidestepCancelInput();cancelSafeLandingProbeBeforeDiscard();cancelCirculationReliefBeforeDiscard();cancelAutonomousVectorCircleBeforeDiscard();cancelMultiAxisReflectionBeforeDiscard();cancelWindPressureLiftBeforeDiscard();cancelCirculationListeningBeforeDiscard();vectorCageCancelInput();cancelInertialAnchorBeforeDiscard();discardBinding();ownedConnection=mc.getConnection();cn.academy.port.client.achievements.ClassicAchievementClient.clear();cn.academy.port.client.tutorial.TutorialClient.clear();cn.academy.port.client.terminal.TerminalClient.clear();state=new AbilityProgress();activationState.resetSession(state);development=null;clearEffects();activation.replaceSession(down(TOGGLE),now());switchLatch.replaceSession(down(SWITCH_PRESET));editLatch.replaceSession(down(EDIT_PRESET));deathFenced=false;}if(binding==null&&mc.player!=null&&mc.level!=null&&mc.player.isAlive()){binding=createBinding(mc);binding.runtime().updateDefaultGroup();binding.establishSessionFence();mappingSignature=mappings();}}
    public static void editPreset(int preset,int slot,String skill){if(cn.academy.port.preset.PresetSkills.edit(state,preset,slot,skill)){cancelPersistentHighPowerInputs();if(binding!=null)binding.presetEdit();mappingSignature=mappings();request("preset_edit",preset+":"+slot+":"+skill);}}
    /** These hooks are invoked after the existing effect receiver has accepted its local start/end. */
    public static void acceptedContextStart(String skill,long input,long token,boolean active){if(binding!=null)binding.acceptedStart(skill,input,token,active);}
    public static void acceptedContextActive(String skill,long input,long token){if(binding!=null)binding.acceptedActive(skill,input,token);}
    public static boolean acceptedContextEnd(String skill,long input,long token){return binding!=null&&binding.acceptedEnd(skill,input,token);}
    public static boolean contextStartCancelled(String skill,long input,long token){return binding==null||binding.contextStartCancelled(skill,input,token);}
    public static void acceptedSingleStart(String skill,long input,long token){if(binding!=null)binding.acceptedSingleStart(skill,input,token);}
    public static void acceptedSingleEnd(String skill,long input,long token){if(binding!=null)binding.acceptedSingleEnd(skill,input,token);}
    public static boolean singleStartAllowed(String skill,long input,long token){return binding!=null&&binding.singleStartAllowed(skill,input,token);}
    public static boolean singleTerminalAllowed(String skill,long input,long token){return binding!=null&&binding.singleTerminalAllowed(skill,input,token);}
    public static boolean singleInputRetired(String skill,long input){return binding==null||binding.singleInputRetired(skill,input);}
    public static boolean legacySingleStartAllowed(String skill){return binding!=null&&!binding.discarded()&&!binding.hasPositiveSingleOwnership(skill);}
    public static boolean singleTokenConflicts(String skill,long input,long token){return binding==null||binding.singleTokenConflicts(skill,input,token);}
    public static long inputSessionEpoch(){return singleInputEpoch;}
    public static long serverSingleEpoch(){return serverSingleEpoch;}
    public static boolean singleOwnerEpochMatches(long epoch){return epoch>0&&epoch==serverSingleEpoch;}
    public static boolean sameInputSessionEpoch(long epoch){var mc=Minecraft.getInstance();return epoch==singleInputEpoch&&binding!=null&&!binding.discarded()&&mc.level!=null&&mc.player!=null&&mc.player.isAlive()&&!mc.player.isRemoved()&&mc.getConnection()==ownedConnection;}
    private static void receiveSingleRejection(net.minecraft.nbt.CompoundTag data){
        if(!data.contains("input",net.minecraft.nbt.Tag.TAG_LONG)||!data.contains("skill",net.minecraft.nbt.Tag.TAG_STRING)||!data.hasUUID("entity_uuid")||!data.contains("entity",net.minecraft.nbt.Tag.TAG_INT)||!data.contains("owner_epoch",net.minecraft.nbt.Tag.TAG_LONG)||!singleOwnerEpochMatches(data.getLong("owner_epoch")))return;
        var identity=cn.academy.port.core.LegacySingleKeyProtocol.Skill.find(data.getString("skill"));long input=data.getLong("input");
        var mc=Minecraft.getInstance();if(identity==null||input<=0||mc.player==null||mc.player.getId()!=data.getInt("entity")||!mc.player.getUUID().equals(data.getUUID("entity_uuid")))return;
        var tag=data.copy();final Object level=mc.level,player=mc.player,connection=mc.getConnection();long epoch=inputSessionEpoch();long ownerEpoch=data.getLong("owner_epoch");
        mc.execute(()->{if(mc.level==level&&mc.player==player&&mc.getConnection()==connection&&sameInputSessionEpoch(epoch)&&singleOwnerEpochMatches(ownerEpoch)&&binding!=null)binding.rejectedSingle(identity.id(),tag.getLong("input"));});
    }
    public static void acceptedRailgunCoin(){if(binding!=null)binding.railgunCoinAccepted();}
    @SubscribeEvent public static void activationObserved(cn.academy.port.api.AbilityActivateEvent event){var mc=Minecraft.getInstance();if(binding!=null&&mc.level!=null&&mc.level.isClientSide&&event.getEntity()==mc.player&&event.state==state)binding.observerActivate();}
    @SubscribeEvent public static void deactivationObserved(cn.academy.port.api.AbilityDeactivateEvent event){var mc=Minecraft.getInstance();if(binding!=null&&mc.level!=null&&mc.level.isClientSide&&event.getEntity()==mc.player&&event.state==state)binding.observerDeactivate();}
    public static void request(String action,String value) {PacketDistributor.sendToServer(new AcademyNetwork.Request(action,value));}
    public static void receive(AcademyNetwork.ClientData packet) {
        var minecraft=Minecraft.getInstance();synchronizeSession(minecraft);var t=packet.data();if(t==null)return;
        CastPoseClient.acceptedLegacyVisual(t);
        switch(t.getString("kind")) {
            case "single_key_rejected" -> receiveSingleRejection(t);
            case "terminal_state","terminal_installed","terminal_app_installed","terminal_install_effect","terminal_open","terminal_app","terminal_frequency","terminal_urban_location" -> cn.academy.port.client.terminal.TerminalClient.receive(t);
            case "tutorial_open" -> cn.academy.port.client.tutorial.TutorialClient.open(t);
            case "tutorial_state","tutorial_activate" -> cn.academy.port.client.tutorial.TutorialClient.receive(t);
            case "classic_achievements" -> cn.academy.port.client.achievements.ClassicAchievementClient.receive(t);
            case "development" -> development=t.copy();
            case "highpower_learning_result" -> {if(minecraft.screen instanceof ClassicDeveloperScreen screen)screen.highPowerLearningResult(t);}
            case "activation_event" -> {if(minecraft.player!=null&&minecraft.level!=null){Boolean active=AcademyNetwork.activationEventState(t,minecraft.player.getId());if(active!=null)cn.academy.port.api.AbilityActivationLifecycle.post(minecraft.player,state,active);}}
            case "state" -> {long epoch=t.contains("single_key_owner_epoch",net.minecraft.nbt.Tag.TAG_LONG)?t.getLong("single_key_owner_epoch"):0;if(epoch>0){if(serverSingleEpoch>0&&epoch<serverSingleEpoch)return;if(serverSingleEpoch>0&&epoch!=serverSingleEpoch&&binding!=null)binding.retireSingleOwnerEpoch();serverSingleEpoch=epoch;}if(minecraft.getSingleplayerServer()==null)SkillCatalog.applyConfigurationSnapshot(t.getCompound("skill_configuration"));activationState.receive(state,t,next->state=next,AcademyClient::modernSnapshotFallback);}
            case "developer" -> minecraft.setScreen(new DeveloperScreen());
            case "developer_machine" -> minecraft.setScreen(new DeveloperScreen(t));
            case "developer_machine_update" -> {if(minecraft.screen instanceof DeveloperScreen screen)screen.machineUpdate(t);}
            case "developer_machine_close" -> {if(minecraft.screen instanceof DeveloperScreen screen&&screen.machineSession(t.getString("session")))minecraft.setScreen(null);}
            case "charge" -> {
                ClassicEffects.addCharge(t.getInt("entity"));
                RailgunDecorClient.charge(t.getInt("entity"));
            }
            case "thunder_bolt" -> ClassicThunderBoltEffects.receive(t);
            case "mine_detect" -> ClassicMineDetectEffects.receive(t);
            case "body_intensify_start","body_intensify_end" -> ClassicBodyIntensifyEffects.receive(t);
            case "thunder_clap_start","thunder_clap_end" -> ClassicThunderClapEffects.receive(t);
            case "scatter_bomb_start","scatter_bomb_ball","scatter_bomb_ray","scatter_bomb_end","light_shield_start","light_shield_end" -> ClassicMeltdownerStarterEffects.receive(t);
            case "meltdowner_start","meltdowner_end","meltdowner_ray","meltdowner_reflection","mine_ray_basic_start","mine_ray_basic_end","mine_ray_basic_particles","mine_ray_expert_start","mine_ray_expert_end","mine_ray_expert_particles","mine_ray_luck_start","mine_ray_luck_end","mine_ray_luck_particles" -> ClassicMeltdownerBeamEffects.receive(t);
            case "ray_barrage_start","ray_barrage_scatter","ray_barrage_preray","ray_barrage_end","jet_engine_start","jet_engine_mark_end","jet_engine_trigger","jet_engine_step","jet_engine_end","electron_missile_start","electron_missile_ball","electron_missile_ray","electron_missile_update","electron_missile_end" -> ClassicMeltdownerLateEffects.receive(t);
            case "mag_movement_start","mag_movement_update","mag_movement_end","mag_manip_start","mag_manip_end","mag_manip_perform" -> ClassicMagneticEffects.receive(t);
            case "charging_start","charging_end" -> ClassicChargingEffects.receive(t);
            case "dir_blast_prepare","dir_blast_abort","dir_blast_perform","storm_wing_start","storm_wing_state","storm_wing_end","blood_retro_start","blood_retro_end","blood_retro_perform" -> ClassicVectorCombatEffects.receive(t);
            case "vec_reflection_start","vec_reflection_end","vec_reflection_entity","vec_reflection_wave","plasma_cannon_start","plasma_cannon_end","plasma_cannon_go","plasma_cannon_position","plasma_cannon_ready" -> ClassicVectorFinalEffects.receive(t);
            case "vec_accel_start","vec_accel_end","vec_accel_perform","vec_deviation_start","vec_deviation_end","vec_deviation_stop","vec_deviation_sound" -> ClassicVectorStarterEffects.receive(t);
            case "ground_shock_start","ground_shock_abort","ground_shock" -> ClassicGroundShockEffects.receive(t);
            case "location_teleport_arrival" -> ConfirmedTeleportArrivalEffects.receive(t);
            case "location_teleport_list","location_teleport_end","shift_tp_start","shift_tp_end","shift_tp_placed","shift_tp_critical","flashing_start","flashing_perform","flashing_end" -> ClassicTeleporterFinalEffects.receive(t);
            case "penetrate_teleport_start","penetrate_teleport_end","mark_teleport_start","mark_teleport_end","flesh_ripping_start","flesh_ripping_end" -> ClassicTeleporterProgressionEffects.receive(t);
            case "radiation_mark","dir_shock_prepare","dir_shock_abort","dir_shock","electron_bomb_charge","electron_bomb","threatening_teleport_start","threatening_teleport_abort","threatening_teleport" -> ClassicFirstSkillEffects.receive(t);
            case "coin_toss","coin_end" -> ClassicCoinEffects.receive(t);
            case "railgun","arc" -> {
                var origin=new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"));var direction=new Vec3(t.getDouble("dx"),t.getDouble("dy"),t.getDouble("dz"));
                boolean rail=t.getString("kind").equals("railgun");
                if(rail) {
                    if(!RailgunDecorClient.sameDimension(minecraft.level,t))return;
                    ClassicEffects.addRailgun(origin,direction,t.getDouble("length"),t.contains("entity",net.minecraft.nbt.Tag.TAG_INT)?t.getInt("entity"):-1);
                    RailgunDecorClient.shot(t,origin,direction);
                } else {
                    // The native WeakArc renderer caps each stroke at 20 blocks. Chain its original material
                    // without truncating the server-confirmed contact or changing the frozen renderer.
                    double length=Math.min(64,t.getDouble("length"));
                    if(Double.isFinite(length)&&length>0&&Double.isFinite(direction.lengthSqr())&&direction.lengthSqr()>1e-12){
                        Vec3 axis=direction.normalize();
                        for(double offset=0;offset<length;offset+=20)ClassicEffects.addArc(origin.add(axis.scale(offset)),axis,Math.min(20,length-offset));
                        if(t.contains("entity",net.minecraft.nbt.Tag.TAG_INT))MotifDirector.acceptedArc(t.getInt("entity"),origin.add(axis.scale(length)));
                    }
                }
                if(minecraft.level!=null)minecraft.level.playLocalSound(origin.x,origin.y,origin.z,SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy",rail?"em.railgun":"em.arc_weak")),SoundSource.PLAYERS,.5f,1,false);
            }
            default -> {}
        }
    }
    /** Sole ability-key poll/dispatch authority, at source START. Auxiliary V/C/N timing stays separate. */
    @SubscribeEvent public static void tick(ClientTickEvent.Pre event) {
        var mc=Minecraft.getInstance();synchronizeSession(mc);if(mc.player==null||mc.level==null)return;
        if(!mc.player.isAlive()){if(!deathFenced){spatialPincerCancelInput();spatialRelayCancelInput();shortInterceptionCancelInput();cornerExchangeCancelInput();emergencyReturnCancelInput();crossSubstitutionCancelInput();displacedProjectionCancelInput();deflectionStrikeCancelInput();yawPalmCancelInput();trajectoryReturnCancelInput();shearWindbladeCancelInput();vectorParryCancelInput();brakingTouchCancelInput();newSkillCancelAll();phaseSidestepCancelInput();cancelCirculationReliefBeforeDiscard();cancelAutonomousVectorCircleBeforeDiscard();cancelMultiAxisReflectionBeforeDiscard();cancelWindPressureLiftBeforeDiscard();cancelCirculationListeningBeforeDiscard();vectorCageCancelInput();cancelInertialAnchorBeforeDiscard();cancelSafeLandingProbeBeforeDiscard();cancelZeroGapArmorBeforeDiscard();discardBinding();clearEffects();activation.replaceSession(down(TOGGLE),now());switchLatch.replaceSession(down(SWITCH_PRESET));editLatch.replaceSession(down(EDIT_PRESET));deathFenced=true;}return;}
        deathFenced=false;MagneticItemPullSelection.tick();boolean inputBlocked=persistentInputBlocked(mc);
        tickCirculationReliefInput(mc);
        tickAutonomousVectorCircleInput(mc);
        tickMultiAxisReflectionInput(mc);
        tickWindPressureLiftInput(mc);
        tickCirculationListeningInput(mc);
        tickVectorCageInput(mc);
        tickInertialAnchorInput(mc);
        tickZeroGapArmorInput(mc);
        tickSafeLandingProbeInput(mc);
        tickDisplacedProjectionInput(mc);
        tickRecoveryMarkInput(mc);
        tickReturnMarkInput(mc);
        tickShortInterceptionInput(mc);
        tickCornerExchangeInput(mc);
        tickEmergencyReturnInput(mc);
        tickMultidimensionalLandingArrayInput(mc);
        tickSpatialPincerInput(mc);
        tickSpatialRelayInput(mc);
        tickCrossSubstitutionInput(mc);
        tickPhaseSidestepInput(mc);
        tickVectorParryInput(mc);
        tickBrakingTouchInput(mc);
        tickNewSkillInputs(mc);
        tickDeflectionStrikeInput(mc);
        tickYawPalmInput(mc);
        tickTrajectoryReturnInput(mc);
        tickShearWindbladeInput(mc);
        sendPersistentCancellation("hp_dual_point_abort",dualPointInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null),AcademyClient::cancelDualPointLocally);
        sendPersistentCancellation("hp_scatter_plates_abort",scatterPlateInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player),AcademyClient::cancelScatterPlatesLocally);
        sendPersistentCancellation("hp_beam_etching_abort",beamEtchingInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering||!"meltdowner".equals(state.category)||!state.presets.currentContains(cn.academy.port.highpower.BeamEtching.ID)),AcademyClient::cancelBeamEtchingLocally);
        sendPersistentCancellation("hp_point_meltdown_abort",pointMeltdownInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering||!"meltdowner".equals(state.category)||!state.presets.currentContains(cn.academy.port.highpower.PointMeltdown.ID)),AcademyClient::cancelPointMeltdownLocally);
        sendPersistentCancellation("hp_disintegration_sweep_abort",disintegrationSweepInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering||!"meltdowner".equals(state.category)||!state.presets.currentContains(cn.academy.port.highpower.DisintegrationSweep.ID)),AcademyClient::cancelDisintegrationSweepLocally);
        sendPersistentCancellation("hp_folded_mirror_abort",foldedMirrorInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering||!"meltdowner".equals(state.category)||!state.presets.currentContains(cn.academy.port.highpower.FoldedMirrorArray.ID)),AcademyClient::cancelFoldedMirrorLocally);
        sendPersistentCancellation("hp_remote_relay_abort",remoteScatterRelayInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering||!"meltdowner".equals(state.category)||!state.presets.currentContains(cn.academy.port.highpower.RemoteScatterRelay.ID)),AcademyClient::cancelRemoteScatterRelayLocally);
        sendPersistentCancellation("hp_scatter_locator_abort",scatterLocatorInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering||!"meltdowner".equals(state.category)||!state.presets.currentContains(cn.academy.port.highpower.ScatterLocatorPlate.ID)),AcademyClient::cancelScatterLocatorLocally);
        sendPersistentCancellation("hp_particle_laminate_abort",particleLaminateInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering||!"meltdowner".equals(state.category)||!state.presets.currentContains(cn.academy.port.highpower.ParticleLaminate.ID)),AcademyClient::cancelParticleLaminateLocally);
        sendPersistentCancellation("hp_multicore_canopy_abort",multicoreCanopyInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering||!"meltdowner".equals(state.category)||!state.presets.currentContains(cn.academy.port.highpower.MulticoreCanopy.ID)),AcademyClient::cancelMulticoreCanopyLocally);
        sendPersistentCancellation("hp_dissipation_curtain_abort",dissipationCurtainInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||mc.getCameraEntity()!=mc.player||!state.activated||!state.overloadFine||state.interfering||!"meltdowner".equals(state.category)||!state.presets.currentContains(cn.academy.port.highpower.DissipationCurtain.ID)),AcademyClient::cancelDissipationCurtainLocally);
        var etchingHeld=beamEtchingInputs.current();
        var pointHeld=pointMeltdownInputs.current();
        if(!inputBlocked&&mc.screen==null&&etchingHeld!=null)releaseBeamEtchingInput(beamEtchingInputs.releaseWhenUp(physicalDown(etchingHeld.physicalKey())));
        if(!inputBlocked&&mc.screen==null&&pointHeld!=null)releasePointMeltdownInput(pointMeltdownInputs.releaseWhenUp(physicalDown(pointHeld.physicalKey())));
        var sweepHeld=disintegrationSweepInputs.current();
        if(!inputBlocked&&mc.screen==null&&sweepHeld!=null)releaseDisintegrationSweepInput(disintegrationSweepInputs.releaseWhenUp(physicalDown(sweepHeld.physicalKey())));
        var mirrorHeld=foldedMirrorInputs.current();
        if(!inputBlocked&&mc.screen==null&&mirrorHeld!=null)releaseFoldedMirrorInput(foldedMirrorInputs.releaseWhenUp(physicalDown(mirrorHeld.physicalKey())));
        var relayHeld=remoteScatterRelayInputs.current();
        if(!inputBlocked&&mc.screen==null&&relayHeld!=null)releaseRemoteScatterRelayInput(remoteScatterRelayInputs.releaseWhenUp(physicalDown(relayHeld.physicalKey())));
        var locatorHeld=scatterLocatorInputs.current();
        if(!inputBlocked&&mc.screen==null&&locatorHeld!=null)releaseScatterLocatorInput(scatterLocatorInputs.releaseWhenUp(physicalDown(locatorHeld.physicalKey())));
        var laminateHeld=particleLaminateInputs.current();
        if(!inputBlocked&&mc.screen==null&&laminateHeld!=null)releaseParticleLaminateInput(particleLaminateInputs.releaseWhenUp(physicalDown(laminateHeld.physicalKey())));
        var canopyHeld=multicoreCanopyInputs.current();
        if(!inputBlocked&&mc.screen==null&&canopyHeld!=null)releaseMulticoreCanopyInput(multicoreCanopyInputs.releaseWhenUp(physicalDown(canopyHeld.physicalKey())));
        var curtainHeld=dissipationCurtainInputs.current();
        if(!inputBlocked&&mc.screen==null&&curtainHeld!=null)releaseDissipationCurtainInput(dissipationCurtainInputs.releaseWhenUp(physicalDown(curtainHeld.physicalKey())));
        sendPersistentCancellation("hp_magnetic_domain_abort",magneticDomainInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null),AcademyClient::cancelMagneticDomainLocally);
        sendPersistentCancellation("hp_rail_thunder_array_abort",railArrayInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null),AcademyClient::cancelRailArrayLocally);
        var arrayHeld=railArrayInputs.current();
        if(!inputBlocked&&mc.screen==null&&arrayHeld!=null)releaseRailArrayInput(railArrayInputs.releaseWhenUp(physicalDown(arrayHeld.physicalKey())));
        sendPersistentCancellation("hp_potential_buffer_abort",potentialBufferInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null),AcademyClient::cancelPotentialBufferLocally);
        sendPersistentCancellation("hp_thermal_shell_abort",thermalShellInputs.cancelWhenBlocked(inputBlocked||mc.screen!=null||!"meltdowner".equals(state.category)||!state.activated||!state.overloadFine||state.interfering||!state.presets.currentContains(cn.academy.port.highpower.ThermalShell.ID)),AcademyClient::cancelThermalShellLocally);
        sendPersistentCancellation("hp_iron_lance_abort",ironLanceInputs.cancelWhenBlocked(inputBlocked),AcademyClient::cancelIronLanceLocally);
        sendPersistentCancellation("hp_item_pull_abort",itemPullInputs.cancelWhenBlocked(inputBlocked),AcademyClient::cancelItemPullLocally);
        sendPersistentCancellation("hp_tether_abort",tetherInputs.cancelWhenBlocked(inputBlocked),ignored->PolarityTetherEffects.abortLocal());
        sendPersistentCancellation("hp_circuit_abort",circuitInputs.cancelWhenBlocked(inputBlocked),ignored->ConductiveCircuitEffects.abortLocal());
        sendPersistentCancellation("hp_ground_abort",groundingInputs.cancelWhenBlocked(inputBlocked),input->GroundingReturnEffects.abortLocal(input.input(),input.epoch()));
        sendPersistentCancellation("hp_ground_step_abort",groundStepInputs.cancelWhenBlocked(inputBlocked),AcademyClient::cancelGroundStepLocally);
        sendPersistentCancellation("hp_thunder_net_abort",thunderNetInputs.cancelWhenBlocked(inputBlocked),AcademyClient::cancelThunderNetLocally);
        var netHeld=thunderNetInputs.current();
        if(!inputBlocked&&netHeld!=null)releaseThunderNetInput(thunderNetInputs.releaseWhenUp(physicalDown(netHeld.physicalKey())));
        state.tickCooldowns();if(binding==null)return;
        String current=mappings();if(!current.equals(mappingSignature)){cancelPersistentHighPowerInputs();mappingSignature=current;binding.requestFlush();}
        pollMenuMouseFence();binding.tickStart();for(var key:SLOTS)while(key.consumeClick()){};
        var switchTransition=switchLatch.update(down(SWITCH_PRESET),mc.screen==null&&state.activated);
        var editTransition=editLatch.update(down(EDIT_PRESET),mc.screen==null&&state.hasCategory());
        while(SWITCH_PRESET.consumeClick()){};while(EDIT_PRESET.consumeClick()){};while(TOGGLE.consumeClick()){};
        boolean activate=activation.update(down(TOGGLE),mc.screen==null,now());if(mc.screen!=null)return;
        if(activate&&state.hasCategory()){binding.activateKeyDown();return;}
        if(editTransition.press()){binding.presetEdit();mc.setScreen(new PresetEditScreen());return;}
        if(switchTransition.press()){int next=(state.presets.current()+1)%cn.academy.port.core.SkillPresets.MAX_PRESETS;state.presets.switchTo(next);cancelPersistentHighPowerInputs();binding.presetSwitch();mappingSignature=mappings();request("preset_switch",Integer.toString(next));}
    }
    /** Modern END adapter runs after all ordinary effect/context Post ticks. */
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void finishContextDisposals(ClientTickEvent.Post event){if(binding!=null)binding.finishLocalDisposals();flushFoldedMirrorStart();flushDisintegrationSweepStart();flushParticleLaminateRelease();flushParticleLaminateStart();flushParticleLaminateRelease();flushDissipationCurtainRelease();flushDissipationCurtainStart();flushDissipationCurtainRelease();flushMulticoreCanopyRelease();flushMulticoreCanopyStart();flushMulticoreCanopyRelease();flushRecoveryMarkStart();flushReturnMarkStart();flushShortInterceptionStart();flushShortInterceptionRelease();flushCornerExchangeStart();flushCornerExchangeRelease();flushEmergencyReturnStart();flushEmergencyReturnRelease();flushMultidimensionalLandingArrayStart();flushMultidimensionalLandingArrayRelease();flushSpatialPincerStart();flushSpatialPincerRelease();flushSpatialRelayStart();flushSpatialRelayRelease();flushCrossSubstitutionStart();flushPhaseSidestepStart();flushDeflectionStrikeStart();flushYawPalmStart();flushYawPalmRelease();flushTrajectoryReturnStart();flushShearWindbladeStart();flushVectorCageInput();flushVectorParryRelease();flushVectorParryStart();flushVectorParryRelease();flushBrakingTouchRelease();flushBrakingTouchStart();flushBrakingTouchRelease();flushNewSkillRelease();flushNewSkillStart();flushNewSkillRelease();flushScatterLocatorRelease();flushScatterLocatorStart();flushScatterLocatorRelease();flushDisplacedProjectionStart();flushRemoteScatterRelayRelease();flushRemoteScatterRelayStart();flushRemoteScatterRelayRelease();}
    private static boolean fleetOwnsPointer(){var p=Minecraft.getInstance().player;return p!=null&&(p.getMainHandItem().getItem() instanceof cn.academy.port.fleet.GunItem||p.getVehicle() instanceof cn.academy.port.fleet.LabVehicle);}
    private static boolean overrides(KeyMapping vanilla){int key=sourceKey(vanilla.getKey());if(key<0&&fleetOwnsPointer())return false;for(int own:overrideKeys)if(own==key)return true;return false;}
    /** Source ControlOverrider suppresses vanilla mappings for bound delegates, even on cooldown. */
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOW) public static void suppressVanillaKeys(ClientTickEvent.Pre event){
        var mc=Minecraft.getInstance();synchronizeSession(mc);if(mc.player==null||mc.screen!=null)return;
        for(var key:mc.options.keyMappings){
            boolean own=key==TOGGLE||key==SWITCH_PRESET||key==EDIT_PRESET;for(var slot:SLOTS)own|=key==slot;
            if(!own&&overrides(key)){key.setDown(false);while(key.consumeClick()){};}
        }
    }
    /** Observe menu-owned mouse edges before a native button can close its screen. */
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST,receiveCanceled=true)
    public static void menuMouseButton(InputEvent.MouseButton.Pre event){
        var mc=Minecraft.getInstance();
        if(event.getAction()==GLFW.GLFW_RELEASE)menuMouseFence.release(event.getButton());
        else if(event.getAction()==GLFW.GLFW_PRESS)menuMouseFence.press(event.getButton(),mc.screen!=null);
    }
    /** The native Resume button closes its screen during mouse-down, before the next START poll. */
    @SubscribeEvent public static void menuScreenClosing(ScreenEvent.Closing event){
        menuMouseFence.screenClosing(button->physicalDown(ClassicPhysicalKeys.mouse(button)));
    }
    private static void pollMenuMouseFence(){menuMouseFence.poll(button->physicalDown(ClassicPhysicalKeys.mouse(button)));}
    private static boolean menuMouseInputBlocked(ClassicClientInputBinding.Node node){
        if(!cn.academy.port.highpower.HighPowerSkills.IDS.contains(node.skill)||!menuMouseFence.blocks(node.key))return false;
        // The fence denies new starts only; an exact live selector delegate keeps its existing hold/release contract.
        return binding==null||binding.runtime().keyRegistrationSnapshot().get(node.key)!=node||!binding.activeDefault(node.slot);
    }
    /** Callback-level mouse suppression also blocks vanilla before its click queue is populated. */
    @SubscribeEvent public static void suppressVanillaMouse(InputEvent.MouseButton.Pre event){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null||!state.activated||fleetOwnsPointer())return;
        int source=ClassicPhysicalKeys.mouse(event.getButton());for(int key:overrideKeys)if(key==source){event.setCanceled(true);return;}
    }
    @SubscribeEvent public static void suppressVanillaInteraction(InputEvent.InteractionKeyMappingTriggered event){
        if(Minecraft.getInstance().screen==null&&overrides(event.getKeyMapping())){event.setCanceled(true);event.setSwingHand(false);}
    }
    // Replace AcademyClient.hud's placeholder implementation after merging the presets bridge.
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(mc.player==null)return;
        var keys=new java.util.ArrayList<ClassicAbilityHud.KeyHint>();
        for(int slot=0;slot<SLOTS.length;slot++){
            if(!delegatePresent(slot))continue;
            String skill=state.presets.currentSkill(slot);
            var key=SLOTS[slot].getKey();
            int mouse=key.getType()==com.mojang.blaze3d.platform.InputConstants.Type.MOUSE?key.getValue():-1;
            var visual=delegateActive(slot)?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(skill.equals("railgun")){
                if(ClassicCoinEffects.hasPendingAttempt())visual=ClassicCoinEffects.ready()?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.CHARGE;
                else visual=binding!=null&&binding.railgunTicks(slot)!=-1?ClassicAbilityHud.DelegateVisual.CHARGE:ClassicAbilityHud.DelegateVisual.IDLE;
            }
            if(skill.equals("ground_shock")){
                int preparing=ClassicGroundShockEffects.localPrepareTicks();
                visual=preparing<0?ClassicAbilityHud.DelegateVisual.IDLE:preparing<5?ClassicAbilityHud.DelegateVisual.CHARGE:ClassicAbilityHud.DelegateVisual.ACTIVE;
            }
            if(skill.equals(cn.academy.port.highpower.MagneticDomain.ID))visual=MagneticDomainEffects.activeLocal()?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(skill.equals(cn.academy.port.highpower.DualPoint.ID))visual=DualPointEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.BeamEtching.ID))visual=BeamEtchingEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.PointMeltdown.ID))visual=PointMeltdownEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.DisintegrationSweep.ID))visual=DisintegrationSweepEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.FoldedMirrorArray.ID))visual=FoldedMirrorEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.RemoteScatterRelay.ID))visual=RemoteScatterRelayEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.ScatterLocatorPlate.ID))visual=ScatterLocatorEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.ParticleLaminate.ID))visual=ParticleLaminateEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.MulticoreCanopy.ID))visual=MulticoreCanopyEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.DissipationCurtain.ID))visual=DissipationCurtainEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.CirculationRelief.ID))visual=CirculationReliefEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.AutonomousVectorCircle.ID))visual=AutonomousVectorCircleEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.MultiAxisReflection.ID))visual=MultiAxisReflectionEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.WindPressureLift.ID))visual=WindPressureLiftEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.VectorCage.ID))visual=VectorCageEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.CirculationListening.ID))visual=CirculationListeningEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.InertialAnchor.ID))visual=InertialAnchorEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.ZeroGapArmor.ID))visual=ZeroGapArmorEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.DisplacedProjection.ID))visual=DisplacedProjectionEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.RecoveryMark.ID))visual=RecoveryMarkEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.ReturnMark.ID))visual=ReturnMarkEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.ShortInterception.ID))visual=ShortInterceptionEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.CornerExchange.ID))visual=CornerExchangeEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.TacticalCoordinateFormation.ID))visual=TacticalCoordinateClient.localVisual();
            if(skill.equals(cn.academy.port.highpower.EmergencyReturn.ID))visual=EmergencyReturnEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.MultidimensionalLandingArray.ID))visual=MultidimensionalLandingArrayEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.SpatialPincerCorridor.ID))visual=SpatialPincerEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.SpatialRelay.ID))visual=SpatialRelayEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.CrossSubstitution.ID))visual=CrossSubstitutionEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.PhaseSidestep.ID))visual=PhaseSidestepEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.VectorParry.ID))visual=VectorParryEffects.localVisual();
            if(NewSkillClient.handles(skill))visual=NewSkillEffects.localVisual(skill);
            if(skill.equals(cn.academy.port.highpower.BrakingTouch.ID))visual=BrakingTouchEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.DeflectionStrike.ID))visual=DeflectionStrikeEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.YawPalm.ID))visual=YawPalmEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.TrajectoryReturn.ID))visual=TrajectoryReturnEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.ShearWindblade.ID))visual=ShearWindbladeEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.SafeLandingProbe.ID))visual=SafeLandingProbeEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.ScatterPlates.ID))visual=ScatterPlateEffects.activeLocal()?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(skill.equals(cn.academy.port.highpower.RailThunderArray.ID))visual=RailThunderArrayEffects.localVisual();
            if(skill.equals(cn.academy.port.highpower.PotentialBuffer.ID))visual=PotentialBufferEffects.activeLocal()?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(skill.equals(cn.academy.port.highpower.ThermalShell.ID))visual=ThermalShellEffects.activeLocal()?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(ClassicMeltdownerStarterEffects.owns(skill))visual=ClassicMeltdownerStarterEffects.delegateActive(skill)?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(ClassicMeltdownerBeamEffects.owns(skill))visual=ClassicMeltdownerBeamEffects.delegateActive(skill)?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(ClassicVectorStarterEffects.owns(skill))visual=ClassicVectorStarterEffects.active(skill)?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            if(ClassicVectorCombatEffects.owns(skill))visual=ClassicVectorCombatEffects.active(skill)?(skill.equals("storm_wing")&&!ClassicVectorCombatEffects.stormActive()?ClassicAbilityHud.DelegateVisual.CHARGE:ClassicAbilityHud.DelegateVisual.ACTIVE):ClassicAbilityHud.DelegateVisual.IDLE;
            if(ClassicVectorFinalEffects.owns(skill))visual=ClassicVectorFinalEffects.charging()&&skill.equals("plasma_cannon")?ClassicAbilityHud.DelegateVisual.CHARGE:ClassicVectorFinalEffects.active(skill)?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE;
            keys.add(new ClassicAbilityHud.KeyHint("def",slot,skill,SLOTS[slot].getTranslatedKeyMessage(),mouse,
                    visual,state.cooldowns.getOrDefault(skill,0),state.cooldownMaximum(skill)));
        }
        if(binding!=null)for(var node:binding.winningNodes())if(node.kind==ClassicClientInputBinding.Kind.DIRECTION){String letter=node.key==ClassicPhysicalKeys.W?"W":node.key==ClassicPhysicalKeys.A?"A":node.key==ClassicPhysicalKeys.S?"S":"D";keys.add(new ClassicAbilityHud.KeyHint(node.group,node.direction,node.group.equals(ClassicClientInputBinding.FLASH_GROUP)?letter.toLowerCase(java.util.Locale.ROOT):node.skill,net.minecraft.network.chat.Component.literal(letter),-1,node.context.applying()==node.direction?ClassicAbilityHud.DelegateVisual.ACTIVE:ClassicAbilityHud.DelegateVisual.IDLE,0,1));}
        String hint=binding==null?"":binding.runtime().activationHintTranslated(TOGGLE.getTranslatedKeyMessage().getString(),key->net.minecraft.network.chat.Component.translatable(key).getString()).orElse("");
        if(hint.isEmpty()){
            hint=CirculationReliefEffects.localHint();
            String listening=CirculationListeningEffects.localHint();
            if(!listening.isEmpty())hint=hint.isEmpty()?listening:hint+" | "+listening;
        }
        if(hint.isEmpty())hint=AutonomousVectorCircleEffects.localHint();
        if(hint.isEmpty())hint=MultiAxisReflectionEffects.localHint();
        if(hint.isEmpty())hint=WindPressureLiftEffects.localHint();
        if(hint.isEmpty())hint=VectorCageEffects.localHint();
        if(hint.isEmpty())hint=InertialAnchorEffects.localHint();
        // Source GroundShock context provides captured predicted CP cost.
        ClassicAbilityHud.render(event.getGuiGraphics(),state,new ClassicAbilityHud.Inputs(
                activationHeld(),ClassicGroundShockEffects.consumptionHint()+ClassicMagneticEffects.consumptionHint()+ClassicTeleporterFinalEffects.consumptionHint()+ClassicVectorStarterEffects.consumptionHint()+ClassicVectorCombatEffects.consumptionHint()+ClassicVectorFinalEffects.consumptionHint(),state.presets.current(),hint,keys));
    }

    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {AcademyNetwork.clientReceiver=AcademyClient::receive;event.enqueueWork(()->{
            ClassicEnergyUnitProperties.register();
            net.minecraft.client.renderer.item.ItemProperties.register(AcademyCraft.DEVELOPER.get(),ResourceLocation.fromNamespaceAndPath("academy","charge_icon"),(stack,level,entity,seed)->{int damage=(int)Math.round(13*(1-new cn.academy.port.develop.DeveloperItemEnergy(stack,cn.academy.port.develop.DeveloperType.PORTABLE).energy()/10000));return damage<3?2f:damage>10?0f:1f;});
            net.minecraft.client.renderer.item.ItemProperties.register(AcademyCraft.INDUCTION_FACTOR.get(),ResourceLocation.fromNamespaceAndPath("academy","category"),(stack,level,entity,seed)->cn.academy.port.develop.InductionFactors.category(stack).map(cat->(float)cn.academy.port.develop.DevelopmentActions.CATEGORIES.indexOf(cat)).orElse(0f));
        });}
        @SubscribeEvent public static void developerRenderers(EntityRenderersEvent.RegisterRenderers event){ClassicDeveloperRendering.registerRenderers(event);event.registerEntityRenderer(cn.academy.port.highpower.TeleporterNeedleEntities.DISPLACED_NEEDLE.get(),DisplacedNeedleRenderer::new);event.registerEntityRenderer(cn.academy.port.highpower.TeleporterNeedleEntities.CROSS_NEEDLE.get(),DisplacedNeedleRenderer::new);event.registerEntityRenderer(cn.academy.port.highpower.TeleporterNeedleEntities.INTERCEPTION_NEEDLE.get(),DisplacedNeedleRenderer::new);event.registerEntityRenderer(cn.academy.port.highpower.TeleporterNeedleEntities.CORRIDOR_NEEDLE.get(),DisplacedNeedleRenderer::new);event.registerEntityRenderer(cn.academy.port.highpower.TeleporterNeedleEntities.BORROWED_NEEDLE.get(),BorrowedNeedleRenderer::new);}
        @SubscribeEvent public static void developerModels(RegisterClientReloadListenersEvent event){ClassicDeveloperRendering.registerReloadListeners(event);}
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {cn.academy.port.client.terminal.TerminalClient.registerKeys(event);event.register(TOGGLE);event.register(SWITCH_PRESET);event.register(EDIT_PRESET);for(var key:SLOTS)event.register(key);}
    }
}
