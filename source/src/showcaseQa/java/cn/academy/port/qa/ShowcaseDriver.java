package cn.academy.port.qa;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * QA-only visual driver. Opens a fixed Superflat creative world and runs a '|' separated script:
 * {@code wait:N} (client ticks), {@code cmd:text} (chat/client command without slash), {@code shot:name}, {@code quit}.
 * Compiled only with -PshowcaseQA=true and excluded from the distributable JAR; it never changes gameplay code.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ShowcaseDriver {
    private static final Logger LOG = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("academy.qa.showcase");
    private static final String WORLD = "AcademyShowcase";
    private static final List<String> SCRIPT = new ArrayList<>();
    private static int index, wait, titleTicks, worldTicks;
    private static boolean opening, finished;
    // Video clips: one frame per client tick while a recording is open, and an optional scripted camera orbit.
    private static String recName; private static int recFrame;
    private static double[] orbit; private static int orbitTick;
    // Fixed cinematic camera (vcam:x,y,z,focusX,focusY,focusZ | vcam:off) so a third-person cast can be filmed from the side.
    private static double[] vcam; private static java.lang.reflect.Method cameraSetPosition;
    private static net.minecraft.world.item.ItemStack blockedDeveloperStack;
    private static java.util.UUID blockedDeveloperOwner;
    private static String blockedDeveloperSkill;
    private static int blockedDeveloperTick;
    private static net.minecraft.world.phys.Vec3 liquidStart, liquidDirection, paperStart;

    static {
        if (ENABLED) for (String step : System.getProperty("academy.qa.script", "quit").split("\\|")) if (!step.isBlank()) SCRIPT.add(step.trim());
    }

    private ShowcaseDriver() {}

    @SubscribeEvent
    public static void camera(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
        if (!ENABLED || vcam == null) return;
        var pos = new net.minecraft.world.phys.Vec3(vcam[0], vcam[1], vcam[2]);
        var d = new net.minecraft.world.phys.Vec3(vcam[3], vcam[4], vcam[5]).subtract(pos);
        try {
            if (cameraSetPosition == null) {
                cameraSetPosition = net.minecraft.client.Camera.class.getDeclaredMethod("setPosition", net.minecraft.world.phys.Vec3.class);
                cameraSetPosition.setAccessible(true);
            }
            cameraSetPosition.invoke(event.getCamera(), pos);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        event.setYaw((float) -Math.toDegrees(Math.atan2(d.x, d.z)));
        event.setPitch((float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) return;
        Minecraft mc = Minecraft.getInstance();
        forceWindowActive(mc);
        FxProbeQa.sample(mc);
        if(CanonFormalClientQa.observerTick(mc)) return;
        if(DualPoseQa.observerTick(mc)) return;
        if (mc.level == null || mc.player == null) {
            if (Boolean.getBoolean("academy.qa.confirmOwnWorld")
                    && System.getProperty("academy.qa.world", "").startsWith("AcademyShowcase-")
                    && mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen confirm) {
                String label = net.minecraft.network.chat.Component.translatable("selectWorld.backupJoinSkipButton").getString();
                for (var child : confirm.children()) {
                    if (child instanceof net.minecraft.client.gui.components.Button button
                            && button.getMessage().getString().equals(label)) {
                        LOG.info("QA_CONFIRM_OWN_TEST_WORLD no_backup=true no_cache_erase=true");
                        button.onPress();
                        return;
                    }
                }
            }
            if (++titleTicks % 200 == 0) LOG.info("QA_WAITING screen={} opening={}", mc.screen == null ? "none" : mc.screen.getClass().getName(), opening);
            if (mc.screen instanceof TitleScreen && titleTicks > 40 && !opening) open(mc);
            return;
        }
        forceWindowActive(mc);
        if (++worldTicks < 120) return; // let chunks, player and renderers settle
        orbitCamera(mc); captureFrame(mc);
        if (wait > 0) { wait--; return; }
        if (index >= SCRIPT.size()) { finish(mc); return; }
        String step = SCRIPT.get(index++);
        int colon = step.indexOf(':');
        String op = colon < 0 ? step : step.substring(0, colon), arg = colon < 0 ? "" : step.substring(colon + 1);
        switch (op) {
            case "urban-install" -> {
                var server=mc.getSingleplayerServer(); var id=mc.player.getUUID();
                if(server==null)throw new IllegalStateException("Urban UI fixture needs integrated server");
                server.execute(() -> {var player=server.getPlayerList().getPlayer(id); if(player==null)throw new IllegalStateException("player missing");
                    var terminal=cn.academy.port.terminal.TerminalStorage.get(player);
                    terminal.installTerminal(); terminal.installApp("urban_communication");
                    cn.academy.port.terminal.TerminalStorage.save(player);}); wait=20;
            }
            case "urban-open" -> { cn.academy.port.terminal.TerminalNetwork.requestApp("urban_communication"); wait=30; }
            case "urban-check" -> { if(mc.screen==null || !mc.screen.getClass().getName().equals("cn.academy.port.client.terminal.UrbanStoryScreen"))throw new IllegalStateException("Urban app did not open through network"); LOG.info("QA_URBAN_APP_OPENED"); }
            case "paper-start" -> { paperStart=mc.player.position(); LOG.info("QA_PAPER_START {}",paperStart); }
            case "paper-motion" -> { if(paperStart==null)throw new IllegalStateException("paper start required"); double delta=mc.player.position().y-paperStart.y;LOG.info("QA_PAPER_ASCENT {}",delta);if(delta<0.2)throw new IllegalStateException("paper wing did not lift the player"); }
            case "liquid-body" -> { var bodies=mc.level.getEntitiesOfClass(cn.academy.port.canon.CanonLiquidEntities.Shadow.class,mc.player.getBoundingBox().inflate(24)); LOG.info("QA_LIQUID_BODIES count={} held={} category={}",bodies.size(),mc.player.getMainHandItem(),cn.academy.port.client.AcademyClient.state.category); if(bodies.size()!=1)throw new IllegalStateException("liquid body not spawned"); }
            case "liquid-camera" -> { if(!(mc.getCameraEntity() instanceof cn.academy.port.canon.CanonLiquidEntities.Shadow))throw new IllegalStateException("liquid view did not enter owned shadow"); LOG.info("QA_LIQUID_VIEW_ENTERED"); }
            case "liquid-turn" -> {
                if(!(mc.getCameraEntity() instanceof cn.academy.port.canon.CanonLiquidEntities.Shadow shadow))throw new IllegalStateException("liquid view required");
                liquidStart=shadow.position(); mc.player.setYRot(mc.player.getYRot()+90); mc.player.setYHeadRot(mc.player.getYRot());
                liquidDirection=net.minecraft.world.phys.Vec3.directionFromRotation(0,mc.player.getYRot()).multiply(1,0,1).normalize();
                LOG.info("QA_LIQUID_TURN yaw={} direction={}",mc.player.getYRot(),liquidDirection);
            }
            case "liquid-motion" -> {
                if(!(mc.getCameraEntity() instanceof cn.academy.port.canon.CanonLiquidEntities.Shadow shadow)||liquidStart==null||liquidDirection==null)throw new IllegalStateException("liquid turn required");
                double advance=shadow.position().subtract(liquidStart).dot(liquidDirection);LOG.info("QA_LIQUID_MOVE advance={}",advance);
                if(advance<0.3)throw new IllegalStateException("shadow did not follow client look");
            }
            case "liquid-camera-back" -> { if(mc.getCameraEntity()!=mc.player)throw new IllegalStateException("liquid view did not restore player camera"); LOG.info("QA_LIQUID_VIEW_EXITED"); }
            case "canonwater" -> { if(!CanonWaterTransferClientQa.step(mc,arg))index--; wait=0; }
            case "auxvisual" -> { if(!CanonAuxVisualQa.step(mc,arg))index--; wait=0; }
            case "jei" -> { if(!JeiRecipeQa.step(arg))index--; wait=0; }
            case "fx4qa" -> { if(!Fx4RolloutQa.step(mc,arg))index--; wait=0; }
            case "urbanformal" -> { if(!UrbanStoryFormalQa.step(mc,arg))index--; wait=0; }
            case "canonformal" -> { if(!CanonFormalClientQa.step(mc,arg))index--; wait=0; }
            case "offenseqa" -> { CanonOffenseShowcaseQa.step(mc,arg); wait=6; }
            case "cityqa" -> { if (!StoryCityClientQa.step(mc, arg)) index--; else wait = 30; }
            case "cityshopqa" -> { if (!CityCommerceQa.step(mc, arg)) { index--; wait=0; } else wait=6; }
            case "cityvisual" -> { if (!CityVisualQa.step(mc, arg)) { index--; wait=0; } else wait=6; }
            case "citynpc" -> { if (!CityNpcQa.step(mc, arg)) { index--; wait=0; } else wait=6; }
            case "cityesper" -> { if (!CityEsperNumericQa.step(mc, arg)) { index--; wait=0; } else wait=6; }
            case "mousehold" -> {int button=Integer.parseInt(arg);var k=com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(button);net.minecraft.client.KeyMapping.set(k,true);}
            case "mouserelease" -> {int button=Integer.parseInt(arg);var k=com.mojang.blaze3d.platform.InputConstants.Type.MOUSE.getOrCreate(button);net.minecraft.client.KeyMapping.set(k,false);}
            case "portalqa" -> {AmethystPortalQa.step(mc,arg);wait=6;}
            case "citydefense" -> {CityDefenseQa.step(mc,arg);wait=6;}
            case "responseqa" -> {CityResponseUpgradeQa.step(mc,arg);wait=6;}
            case "citycombat" -> { if (!CityNpcCombatQa.step(mc,arg)) {index--;wait=0;} else wait=6; }
            case "citylife" -> { if(!CityLifeQa.step(mc,arg)){index--;wait=0;}else wait=6; }
            case "wait" -> wait = Integer.parseInt(arg);
            case "lang" -> { mc.options.languageCode=arg; mc.getLanguageManager().setSelected(arg); mc.reloadResourcePacks(); wait=100; }
            case "cmd" -> { LOG.info("QA_CMD {}", arg); mc.player.connection.sendCommand(arg); wait = 6; }
            case "clientcmd" -> { LOG.info("QA_CLIENT_CMD {} handled={}", arg, net.neoforged.neoforge.client.ClientCommandHandler.runCommand(arg)); wait = 1; }
            case "shot" -> shot(mc, arg);
            case "rec" -> { recName = arg; recFrame = 0; new java.io.File(mc.gameDirectory, "screenshots/rec/" + arg).mkdirs(); LOG.info("QA_REC_START {}", arg); }
            case "recend" -> { LOG.info("QA_REC_END {} frames={}", recName, recFrame); recName = null; wait = 20; }
            case "orbit" -> { var f = arg.split(","); orbit = new double[f.length]; for (int i = 0; i < f.length; i++) orbit[i] = Double.parseDouble(f[i]); orbitTick = 0; }
            case "orbitend" -> orbit = null;
            case "vcamrel" -> { var f = arg.split(","); var at = mc.player.position(); vcam = new double[6];   // vcamrel:dx,dy,dz,fx,fy,fz relative to the player
                for (int i = 0; i < 6; i++) vcam[i] = Double.parseDouble(f[i]) + (i % 3 == 0 ? at.x : i % 3 == 1 ? at.y : at.z); }
            case "vcam" -> { if (arg.equals("off")) vcam = null; else { var f = arg.split(","); vcam = new double[6]; for (int i = 0; i < 6; i++) vcam[i] = Double.parseDouble(f[i]); } }
            case "gui" -> mc.options.hideGui = arg.equals("off");
            case "key" -> key(mc, arg);
            case "keydown" -> heldKey(mc, arg, true);
            case "keyup" -> heldKey(mc, arg, false);
            case "escape" -> { if(mc.screen!=null)key(mc,"256"); }
            case "live" -> { LiveInputQa.run(mc, arg); wait = arg.equals("pose") ? 0 : 6; }
            case "gameem" -> { GameplayAcceptanceEmMd.run(mc,arg); wait=arg.equals("construction:path-ready")?0:6; }
            case "passives" -> { GameplayAcceptancePassives.run(mc,arg); wait=6; }
            case "vmextra" -> { GameplayAcceptanceVmItemSplit.run(mc,arg); wait=arg.equals("arrow")||arg.equals("aim-item")||arg.startsWith("direction:")?0:6; }
            case "worldboss" -> { if(!WorldBossQa.step(mc,arg))index--; wait=0; }
            case "gamestorm" -> { if(!GameplayAcceptanceStormCycle.run(mc,arg))index--;wait=0; }
            case "gamevmenv" -> { GameplayAcceptanceVmEnvironment.run(mc,arg); wait=6; }
            case "mark" -> LOG.info("QA_SCRIPT_MARK {}",arg);
            case "gamevm" -> { GameplayAcceptanceVmUtility.run(mc,arg.startsWith("utility:")?arg.substring(8):arg); wait=arg.endsWith(":tossed")||arg.endsWith(":aim-item")||arg.endsWith(":arrow")||arg.contains(":direction:")?0:6; }
            case "gametp" -> { GameplayAcceptanceTpVm.run(mc,arg); wait=6; }
            case "playmode" -> { GameplayAcceptanceTpPlayModes.run(mc,arg); wait=6; }
            case "arrowmode" -> { GameplayAcceptanceNativeArrowModes.run(mc,arg); wait=arg.equals("launch")||arg.equals("arm-phase")||arg.startsWith("toggle-")||arg.equals("restart-ready")?0:6; }
            case "walk" -> { if(!GameplayAcceptanceTpVm.walk(mc,arg)){index--;wait=0;}else wait=12; }
            case "consent" -> { if(!DualConsentQa.run(mc,arg)){index--;wait=5;}else wait=6; }
            case "ground" -> { if(!DualGroundingQa.run(mc,arg))index--;wait=0; }
            case "dual" -> { if(!DualPoseQa.host(mc,arg)) { index--; wait=20; } }
            case "native" -> { if(!SableNativeQa.step(mc,arg)) { index--; wait=20; } }
            case "motif" -> {   // motif:<skill>:<age>:<since> draws that skill's motif at an exact moment (since -1 = held); motif:off clears it
                String[] mp = arg.split(":");
                if (mp[0].equals("off")) cn.academy.port.client.MotifDirector.qaOff();
                else if (mp.length >= 7) cn.academy.port.client.MotifDirector.qaPose(mp[0], Double.parseDouble(mp[1]), Double.parseDouble(mp[2]), Double.parseDouble(mp[3]), Double.parseDouble(mp[4]), Double.parseDouble(mp[5]), Double.parseDouble(mp[6]));
                else if (mp.length >= 6) cn.academy.port.client.MotifDirector.qaPose(mp[0], Double.parseDouble(mp[1]), Double.parseDouble(mp[2]), Double.parseDouble(mp[3]), Double.parseDouble(mp[4]), Double.parseDouble(mp[5]));
                else cn.academy.port.client.MotifDirector.qaPose(mp[0], Double.parseDouble(mp[1]), Double.parseDouble(mp[2]));
                wait = 3;
            }
            case "aim" -> { LiveInputQa.aim(mc, arg); wait = 3; }
            case "railgun" -> { var look = mc.player.getLookAngle(); cn.academy.port.client.ClassicEffects.addRailgun(mc.player.getEyePosition().add(look.scale(1.2)).add(0, -.3, 0), look, Double.parseDouble(arg.isEmpty() ? "60" : arg), mc.player.getId()); wait = 2; }
            case "classiccheck" -> classicCheck(mc);
            case "tree" -> { tree(mc,arg); wait=arg.equals("close")?6:Boolean.getBoolean("academy.qa.learningFast")?16:60; }
            case "fx" -> { fx(mc, arg); wait = 2; }
            case "vec" -> { VectorQaFixture.stage(mc, arg); wait = 6; }
            case "el" -> { ElectroQaFixture.stage(mc, arg); wait = 6; }
            case "tp" -> { TeleportQaFixture.stage(mc, arg); wait = 6; }
            case "md" -> { MeltQaFixture.stage(mc, arg); wait = 6; }
            case "vc" -> { ConstructQaFixture.run(mc, arg); wait = 4; }
            case "ls" -> { ClassicQaFixture.lightShield(mc, arg); wait = 4; }
            case "wind" -> { ClassicQaFixture.wind(mc, arg); wait = 40; }
            case "pose" -> { poseStep(mc, arg); wait = 2; }
            case "posefix" -> { var f = arg.split(":"); cn.academy.port.client.CastPoseClient.forceAt(mc.player.getId(), cn.academy.port.highpower.core.CastPose.Archetype.valueOf(f[0].toUpperCase()), Double.parseDouble(f[1]), Double.parseDouble(f[2]), true); wait = 3; }
            case "canonprep" -> {   // canonprep:<category> sets that category at level 5 with every canon skill learned and ability active
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                server.execute(() -> { var sp = server.getPlayerList().getPlayer(id); if (sp == null) return;
                    var st = cn.academy.port.AbilityStorage.get(sp); st.changeCategoryClassic(arg); st.setLevel(5);
                    for (var sk : cn.academy.port.canon.CanonSkills.ALL) if (sk.category().equals(arg)) st.learn(sk.id());
                    st.setActivateState(true); cn.academy.port.AbilityStorage.save(sp); cn.academy.port.AcademyNetwork.sync(sp);
                    LOG.info("QA_CANON_PREP {}", arg); });
                wait = 10;
            }
            case "canoncast" -> {   // canoncast:<skill> maps the skill to preset slot 1 and presses it through the real request path
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                server.execute(() -> { var sp = server.getPlayerList().getPlayer(id); if (sp == null) return;
                    var st = cn.academy.port.AbilityStorage.get(sp); st.presets.edit(st.presets.current(), 0, arg, st::learned);
                    boolean ok = cn.academy.port.AcademyGameplay.requestFromClient(sp, new cn.academy.port.AcademyNetwork.Request("slot_press", "0"));
                    LOG.info("QA_CANON_CAST {} accepted={}", arg, ok); });
                wait = 2;
            }
            case "classicsig" -> { var q = arg.split(":"); cn.academy.port.client.canon.ClassicSignatures.qa(q[0], Integer.parseInt(q[1]), Double.parseDouble(q[2]), Double.parseDouble(q[3])); wait = 1; }   // classicsig:<skill>:<level>:<held ticks>:<since release, -1 = holding>
            case "sigqa" -> {SignatureRenderQa.run(mc,arg);wait=1;}
            case "slotset" -> { FxProbeQa.slot(mc, arg); wait = 2; }
            case "slotset3" -> { FxProbeQa.slot(mc, arg, 3); wait = 2; }
            case "rot" -> { var f = arg.split(","); mc.player.setYRot(Float.parseFloat(f[0])); mc.player.setYHeadRot(Float.parseFloat(f[0])); mc.player.setXRot(Float.parseFloat(f[1])); wait = 2; }   // aim by turning, as a player would
            case "fxprobe" -> { if (arg.equals("start")) FxProbeQa.start(mc); else if (arg.equals("state")) FxProbeQa.state(mc); else FxProbeQa.end(mc, arg.substring(arg.indexOf(':') + 1)); wait = arg.equals("state") ? 0 : 1; }
            case "castforce" -> { cn.academy.port.client.CastPoseClient.forceCast(mc.player.getId(), arg); wait = 2; }
            case "refuse" -> {
                var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
                server.execute(() -> { var sp = server.getPlayerList().getPlayer(id); if (sp != null) sp.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.academy.highpower." + arg + ".not_ready"), true); });
                wait = 4;
            }
            case "castdump" -> { LOG.info("QA_CAST_STATE {}", cn.academy.port.client.CastPoseClient.stateForTest(mc.player.getId())); wait = 1; }
            case "look" -> { mc.player.setXRot(arg.equals("down") ? 90 : arg.equals("up") ? -90 : arg.isEmpty() ? 0 : Float.parseFloat(arg)); wait = 8; }
            case "use" -> {
                // Right-click with the main hand: on the block under the crosshair, or in the air when asked ("use:item").
                var hit = mc.hitResult;
                if (!arg.equals("item") && hit instanceof net.minecraft.world.phys.BlockHitResult block && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK)
                    mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, block);
                else mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND); wait = 10;
            }
            case "fps" -> { LOG.info("QA_FPS {} {}", arg, mc.getFps()); wait = 1; }
            case "swing" -> { mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND); wait = 1; }
            case "cam" -> { mc.options.setCameraType(arg.equals("third") ? net.minecraft.client.CameraType.THIRD_PERSON_BACK : arg.equals("front") ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT : net.minecraft.client.CameraType.FIRST_PERSON); wait = 6; }
            case "clearchat" -> { mc.gui.getChat().clearMessages(false); wait = 2; }
            case "quit" -> finish(mc);
            default -> LOG.warn("QA_UNKNOWN_STEP {}", step);
        }
    }

    private static void classicCheck(Minecraft mc) {
        cn.academy.port.client.ClassicEffects.addArc(mc.player.getEyePosition(),mc.player.getLookAngle(),12);
        try {
            var type=Class.forName("cn.academy.port.client.ClassicDecorSnapshots");
            for(String kind:new String[]{"beams","arcs"}){
                var getter=type.getDeclaredMethod(kind);getter.setAccessible(true);
                var rows=(java.util.List<?>)getter.invoke(null);
                if(rows.isEmpty())throw new IllegalStateException("Missing live classic snapshot: "+kind);
                var first=(double[])rows.getFirst();double original=first[0];first[0]=Double.NaN;
                var second=(java.util.List<?>)getter.invoke(null);
                if(((double[])second.getFirst())[0]!=original)throw new IllegalStateException("Snapshot mutated frozen classic storage");
                LOG.info("QA_CLASSIC_SNAPSHOT kind={} rows={} columns={} independentCopy=true",kind,rows.size(),first.length);
            }
        }catch(ReflectiveOperationException error){throw new IllegalStateException("Live classic getter access failed",error);}
    }
    private static void tree(Minecraft mc,String arg) {
        if(arg.equals("close")){mc.setScreen(null);return;}
        cn.academy.port.client.ClassicDeveloperScreen screen;
        if(!arg.equals("open")&&!arg.equals("develop")&&mc.screen instanceof cn.academy.port.client.ClassicDeveloperScreen current)screen=current;
        else{screen=new cn.academy.port.client.ClassicDeveloperScreen(!arg.equals("develop"));mc.setScreen(screen);}
        try {
            var nodes=cn.academy.port.client.UnifiedSkillTreeLayout.category(cn.academy.port.client.AcademyClient.state.category);
            var placement=cn.academy.port.client.ClassicDeveloperTimeline.placement(screen.width,screen.height);
            double mx=placement.x()+(128+128.5)*placement.scale(),my=placement.y()+(18+69.5)*placement.scale();
            var scrollField=cn.academy.port.client.ClassicDeveloperScreen.class.getDeclaredField("treeScroll");scrollField.setAccessible(true);
            if(arg.startsWith("detail-")){
                var coverField=cn.academy.port.client.ClassicDeveloperScreen.class.getDeclaredField("cover");coverField.setAccessible(true);Object cover=coverField.get(screen);
                if(cover==null)throw new IllegalStateException("Open a skill detail before detail scrolling");
                var nodeField=cover.getClass().getDeclaredField("node");nodeField.setAccessible(true);
                var node=(cn.academy.port.client.ClassicDeveloperLayout.Node)nodeField.get(cover);
                if(node==null)throw new IllegalStateException("Current cover is not a skill detail");
                int count=(int)node.conditions().stream().filter(c->c.get("shouldDisplay").getAsBoolean()).count();
                var detail=cn.academy.port.client.DeveloperSkillDetailsLayout.placement(screen.width,screen.height,count);
                var description=cn.academy.port.client.DeveloperSkillDetailsLayout.DESCRIPTION;
                mx=detail.screenX(description.x()+description.width()/2);my=detail.screenY(description.y()+description.height()/2);
                if(arg.equals("detail-top")||arg.equals("detail-bottom"))screen.mouseScrolled(mx,my,0,arg.equals("detail-top")?10000:-10000);
                else if(arg.equals("detail-back"))screen.mouseClicked(detail.screenX(detail.back().x()+detail.back().width()/2),detail.screenY(detail.back().y()+detail.back().height()/2),0);
                else if(arg.equals("detail-confirm")){
                    screen.mouseClicked(detail.screenX(detail.confirm().x()+detail.confirm().width()/2),detail.screenY(detail.confirm().y()+detail.confirm().height()/2),0);
                    var requested=cover.getClass().getDeclaredField("requested");requested.setAccessible(true);
                    if(!requested.getBoolean(cover))throw new IllegalStateException("Real skill confirm did not start a development request");
                    LOG.info("QA_TREE_DETAIL_CONFIRM id={} requested=true viaRealClick=true",node.id());
                }
                else if(arg.equals("detail-confirm-blocked")){
                    var visible=screen.getClass().getDeclaredMethod("canConfirmSkill",cover.getClass());visible.setAccessible(true);
                    var developerType=screen.getClass().getDeclaredField("developerType");developerType.setAccessible(true);
                    if(!(boolean)visible.invoke(screen,cover)||((cn.academy.port.develop.DeveloperType)developerType.get(screen)).supportsSkill(node.level()))
                        throw new IllegalStateException("Blocked fixture requires a visible unlearned skill and an insufficient actual developer grade");
                    var skill=cn.academy.port.SkillCatalog.find(node.category(),node.id()).orElseThrow();
                    if(!cn.academy.port.SkillCatalog.canLearn(cn.academy.port.client.AcademyClient.state,skill))throw new IllegalStateException("All non-machine learning prerequisites must be met");
                    var ready=screen.getClass().getDeclaredMethod("canStartSkill",cover.getClass());ready.setAccessible(true);
                    if((boolean)ready.invoke(screen,cover))throw new IllegalStateException("Blocked detail is unexpectedly ready");
                    var requested=cover.getClass().getDeclaredField("requested");requested.setAccessible(true);
                    var disposed=cover.getClass().getDeclaredField("buttonDisposed");disposed.setAccessible(true);
                    if(requested.getBoolean(cover)||disposed.getBoolean(cover))throw new IllegalStateException("Blocked detail must start idle");
                    blockedDeveloperOwner=mc.player.getUUID();blockedDeveloperSkill=node.id();
                    var server=mc.getSingleplayerServer();if(server==null)throw new IllegalStateException("Actual integrated server required");
                    blockedDeveloperStack=server.submit(()->{var p=server.getPlayerList().getPlayer(blockedDeveloperOwner);blockedDeveloperTick=server.getTickCount();return p.getMainHandItem().copy();}).join();
                    screen.mouseClicked(detail.screenX(detail.confirm().x()+detail.confirm().width()/2),detail.screenY(detail.confirm().y()+detail.confirm().height()/2),0);
                    if(requested.getBoolean(cover)||disposed.getBoolean(cover))throw new IllegalStateException("Disabled detail click must not begin or consume a request");
                    LOG.info("QA_TREE_DETAIL_BLOCKED id={} ready=false requested=false buttonDisposed=false viaRealClick=true",node.id());
                }
                else if(arg.equals("detail-blocked-verify")){
                    if(blockedDeveloperStack==null||!node.id().equals(blockedDeveloperSkill))throw new IllegalStateException("Actual blocked click baseline required");
                    var server=mc.getSingleplayerServer();if(server==null)throw new IllegalStateException("Actual integrated server required");
                    server.submit(()->{var p=server.getPlayerList().getPlayer(blockedDeveloperOwner);int elapsed=server.getTickCount()-blockedDeveloperTick;
                        if(elapsed<10||cn.academy.port.AbilityStorage.get(p).learned(node.id())||!net.minecraft.world.item.ItemStack.isSameItemSameComponents(blockedDeveloperStack,p.getMainHandItem())||blockedDeveloperStack.getCount()!=p.getMainHandItem().getCount())
                            throw new IllegalStateException("Blocked click changed the real developer stack/learning state or was not observed after native ticks");
                        LOG.info("QA_TREE_DETAIL_BLOCKED_PASS id={} nativeElapsed={} learned=false realDeveloperStackAndEnergyUnchanged=true",node.id(),elapsed);
                    }).join();
                }
                else throw new IllegalArgumentException("Unknown detail QA operation: "+arg);
                var detailScroll=cover.getClass().getDeclaredField("descriptionScroll");detailScroll.setAccessible(true);
                var detailLines=cover.getClass().getDeclaredField("descriptionLines");detailLines.setAccessible(true);int lineCount=((List<?>)detailLines.get(cover)).size();
                LOG.info("QA_TREE_DETAIL id={} operation={} descriptionScroll={} maxScroll={} lines={} viaRealInput=true",node.id(),arg,detailScroll.getDouble(cover),cn.academy.port.client.DeveloperSkillDetailsLayout.maxScroll(lineCount),lineCount);
            }else if(arg.equals("top")||arg.equals("bottom")){
                if(!screen.mouseScrolled(mx,my,0,arg.equals("top")?10000:-10000))throw new IllegalStateException("Tree wheel event was not consumed");
            }else if(arg.startsWith("select:")){
                String id=arg.substring("select:".length());
                var node=nodes.stream().filter(n->n.id().equals(id)).findFirst().orElseThrow();
                double target=cn.academy.port.client.UnifiedSkillTreeLayout.clampScroll(nodes,node.y()+8-69.5);
                double delta=(scrollField.getDouble(screen)-target)/(cn.academy.port.client.UnifiedSkillTreeLayout.ROW/2);
                screen.mouseScrolled(mx,my,0,delta);
                double scroll=scrollField.getDouble(screen);
                // Invert the production parallax transform iteratively, then use the real click handler.
                mx=placement.x()+(128+node.x()+8)*placement.scale();my=placement.y()+(18+node.y()+8-scroll)*placement.scale();
                for(int i=0;i<12;i++){
                    mx=placement.x()+(128+node.x()+8-cn.academy.port.client.ClassicDeveloperTimeline.parallax(mx,screen.width))*placement.scale();
                    my=placement.y()+(18+node.y()+8-scroll-cn.academy.port.client.ClassicDeveloperTimeline.parallax(my,screen.height))*placement.scale();
                }
                if(!screen.mouseClicked(mx,my,0))throw new IllegalStateException("Tree skill click was not consumed: "+id);
                var field=cn.academy.port.client.ClassicDeveloperScreen.class.getDeclaredField("navigation");field.setAccessible(true);
                var navigation=(cn.academy.port.client.HighPowerDeveloperLayout.Navigation)field.get(screen);
                if(!navigation.selected().equals(id))throw new IllegalStateException("Tree click did not select "+id+"; actual="+navigation.selected());
                LOG.info("QA_TREE_SELECT category={} id={} viaRealClick=true",cn.academy.port.client.AcademyClient.state.category,id);
            }else if(!arg.isEmpty()&&!arg.equals("open")&&!arg.equals("develop"))throw new IllegalArgumentException("Unknown tree QA operation: "+arg);
            long classic=nodes.stream().filter(n->!cn.academy.port.highpower.HighPowerSkills.contains(n.id())).count();
            LOG.info("QA_TREE category={} unified=true nodes={} classic={} extension={} scroll={} maxScroll={}",
                    cn.academy.port.client.AcademyClient.state.category,nodes.size(),classic,nodes.size()-classic,
                    scrollField.getDouble(screen),cn.academy.port.client.UnifiedSkillTreeLayout.maxScroll(nodes));
        }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    private static void open(Minecraft mc) {
        opening = true;
        String fixed = System.getProperty("academy.qa.world", "").trim();
        LOG.info("QA_OPEN_WORLD {}", fixed.isEmpty() ? WORLD : fixed);
        try {
            var rules = new GameRules();
            LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, !Boolean.getBoolean("academy.qa.noCheats"), rules, WorldDataConfiguration.DEFAULT);
            var flows = mc.createWorldOpenFlows();
            if (!fixed.isEmpty() && mc.getLevelSource().levelExists(fixed)) {
                // Reopen an existing save so a second process can observe what survived the previous save.
                LOG.info("QA_REOPEN_WORLD {}", fixed);
                flows.openWorld(fixed, () -> LOG.info("QA_REOPENED_WORLD {}", fixed));
            } else {
                flows.createFreshLevel(fixed.isEmpty() ? WORLD + "-" + System.currentTimeMillis() : fixed, settings, new WorldOptions(Long.getLong("academy.qa.seed",20261017L), Boolean.getBoolean("academy.qa.normalWorld"), false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(Boolean.getBoolean("academy.qa.normalWorld")?WorldPresets.NORMAL:WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
        } catch (RuntimeException failure) {
            LOG.error("QA_OPEN_FAILED", failure);
            finished = true;
            mc.stop();
        }
    }

    /** The hidden window never reports focus; fixtures require an active window, so QA marks it active. */
    private static void forceWindowActive(Minecraft mc) {
        try {
            Field f = Minecraft.class.getDeclaredField("windowActive");
            f.setAccessible(true);
            f.setBoolean(mc, true);
        } catch (ReflectiveOperationException ignored) {
            // Field name differs: fixtures needing focus will simply stay hidden and the log shows it.
        }
    }

    /** {@code key:290} or {@code key:290+shift}: delivers a physical key press/release through the normal input path. */
    private static void key(Minecraft mc, String arg) {
        boolean shift = arg.endsWith("+shift");
        int code = Integer.parseInt(shift ? arg.substring(0, arg.length() - 6) : arg);
        int mods = shift ? org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT : 0;
        long window = mc.getWindow().getWindow();
        mc.keyboardHandler.keyPress(window, code, 0, org.lwjgl.glfw.GLFW.GLFW_PRESS, mods);
        mc.keyboardHandler.keyPress(window, code, 0, org.lwjgl.glfw.GLFW.GLFW_RELEASE, mods);
        wait = 4;
    }

    private static void heldKey(Minecraft mc, String arg, boolean down) {
        int code = Integer.parseInt(arg);
        if (down) System.setProperty("academy.qa.key." + code, "true");
        else System.clearProperty("academy.qa.key." + code);
        mc.keyboardHandler.keyPress(mc.getWindow().getWindow(), code, 0,
                down ? org.lwjgl.glfw.GLFW.GLFW_PRESS : org.lwjgl.glfw.GLFW.GLFW_RELEASE, 0);
        LOG.info("QA_KEY {} {}", code, down ? "DOWN" : "UP");
    }

    /** {@code fx:<kind>:<phase>}: feeds the production receiver a synthetic authenticated visual for the first armor stand. */
    private static void fx(Minecraft mc, String arg) {
        String[] f = arg.split(":");
        int kind = Integer.parseInt(f[0]), phase = Integer.parseInt(f[1]);
        net.minecraft.world.entity.Entity stand = null;
        for (var e : mc.level.entitiesForRendering()) if (e.getType() == net.minecraft.world.entity.EntityType.ARMOR_STAND) { stand = e; break; }
        if (stand == null) { LOG.warn("QA_FX_NO_STAND"); return; }
        var at = stand.position();
        java.util.function.BiFunction<Double, Double, net.minecraft.world.phys.Vec3> rel = (dx, dz) -> new net.minecraft.world.phys.Vec3(at.x + dx, at.y, at.z + dz);
        java.util.List<net.minecraft.world.phys.Vec3> points = new java.util.ArrayList<>();
        net.minecraft.world.phys.Vec3 point = at.add(0, 1, 0), dir = new net.minecraft.world.phys.Vec3(0, 0, -1);
        switch (kind) {
            case 0 -> { for (double[] t : new double[][] {{3, 6}, {-4, 9}, {6, 12}}) points.add(rel.apply(t[0], t[1]).add(0, 1, 0)); }
            case 1 -> { points.add(rel.apply(-8.0, 0.0)); points.add(rel.apply(0.0, 10.0)); points.add(rel.apply(10.0, 18.0)); dir = new net.minecraft.world.phys.Vec3(0, 0, 1); }
            case 2 -> dir = new net.minecraft.world.phys.Vec3(.3, -.8, .2);
            case 3 -> { point = rel.apply(0.0, 4.0).add(0, 1.2, 0); dir = new net.minecraft.world.phys.Vec3(0, 0, -.6); }
            case 4 -> { points.add(at); points.add(rel.apply(0.0, 18.0)); point = rel.apply(0.0, 18.0); }
            case 5 -> { for (double[] t : new double[][] {{4, 8}, {-5, 12}, {8, 16}, {-9, 18}, {0, 22}}) points.add(rel.apply(t[0], t[1]).add(0, 3, 0)); }
            case 6 -> { points.add(at.add(-1.5, 1, 1.5)); points.add(at.add(1.5, 1, 1.5)); point = rel.apply(0.0, 4.0).add(0, 1.2, 0); dir = new net.minecraft.world.phys.Vec3(0, 0, -.6); }
            default -> { }
        }
        cn.academy.port.client.NewSkillEffects.receive(new cn.academy.port.highpower.NewSkillRuntime.Visual(kind, stand.getId(), stand.getUUID(), 1000 + kind, ++qaSequence, 1, 1,
                mc.level.dimension().location(), phase, point, dir, points, mc.level.getGameTime()));
    }
    private static long qaSequence;

    private static void captureFrame(Minecraft mc) {
        if (recName == null) return;
        Screenshot.grab(mc.gameDirectory, String.format("rec/%s/f%05d.png", recName, recFrame++), mc.getMainRenderTarget(), message -> {});
    }

    /** orbit:cx,cy,cz,radius,eyeY,yaw0,yaw1,ticks,targetY - the player (spectator) circles the target, looking at it, authoritatively on the server. */
    private static void orbitCamera(Minecraft mc) {
        if (orbit == null || orbit.length < 9) return;
        double t = Math.min(1, orbitTick++ / Math.max(1.0, orbit[7]));
        double angle = Math.toRadians(orbit[5] + (orbit[6] - orbit[5]) * t);
        double x = orbit[0] + Math.sin(angle) * orbit[3], z = orbit[2] - Math.cos(angle) * orbit[3], eye = orbit[4];
        double dx = orbit[0] - x, dz = orbit[2] - z, flat = Math.hypot(dx, dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) Math.toDegrees(Math.atan2(eye - orbit[8], flat));
        var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        if (server == null) return;
        server.execute(() -> { var sp = server.getPlayerList().getPlayer(id); if (sp != null) sp.teleportTo(sp.serverLevel(), x, eye - 1.62, z, yaw, pitch); });
    }

    private static void shot(Minecraft mc, String name) {
        LOG.info("QA_SHOT {}", name);
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> LOG.info("QA_SHOT_SAVED {}", message.getString()));
        wait = 10;
    }

    private static void finish(Minecraft mc) {
        LiveInputQa.assertHealthy();
        LiveInputQa.finishFinalVisual("quit");
        finished = true;
        LOG.info("QA_DONE");
        mc.stop();
    }

    /** {@code pose:strike} holds a casting gesture on the local player, {@code pose:strike:tap} plays it once, {@code pose:off} clears it. */
    private static void poseStep(Minecraft mc, String arg) {
        if (mc.player == null) return;
        if (arg.equals("off")) { cn.academy.port.client.CastPoseClient.clearForTest(); return; }
        String[] f = arg.split(":");
        cn.academy.port.client.CastPoseClient.force(mc.player.getId(), cn.academy.port.highpower.core.CastPose.Archetype.valueOf(f[0].toUpperCase()), f.length < 2 || !f[1].equals("tap"), true);
    }
}
