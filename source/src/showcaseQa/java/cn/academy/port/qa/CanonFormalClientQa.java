package cn.academy.port.qa;

import cn.academy.port.canon.CanonVfx;
import cn.academy.port.client.canon.CanonVfxClient;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in, isolated two-client evidence collector. No gameplay or VFX is synthesized. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonFormalClientQa {
    private static final String ROLE=System.getProperty("academy.qa.canon.role","");
    private static final boolean ENABLED=Boolean.getBoolean("academy.qa.showcase")&&(ROLE.equals("host")||ROLE.equals("observer"));
    private static final String RUN=System.getProperty("academy.qa.canon.run","");
    private static final Path ROOT=Path.of(System.getProperty("academy.qa.canon.root",".")).toAbsolutePath().normalize();
    private static final Path REPORT=ROOT.resolve(ROLE+"-receipts.jsonl");
    private static final int PORT=Integer.getInteger("academy.qa.canon.port",25589);
    private static final Set<CanonVfx.Visual> SEEN=Collections.newSetFromMap(new WeakHashMap<>());
    private static Consumer<CanonVfx.Visual> wrapper;
    private static boolean connecting,everJoined,reconnecting,disconnectPending,dimensionPending,visualSeen,departureActive;
    private static int titleTicks,joins,previousActive;
    private static Object lastWorld,lastLiveWorld,lastLiveConnection;
    private static String lastLiveDimension="";
    private static volatile List<Identity> serverPlayers=List.of();
    private static java.util.concurrent.CompletableFuture<JsonObject> waterDiagnostic;
    private static java.util.concurrent.CompletableFuture<JsonObject> liquidDiagnostic;
    private static String liquidStage;
    private static boolean observeShadow;
    private static int liquidKeyPresses, checkedLiquidKeyPresses;
    private static String waterDiagnosticId;
    private record Identity(String name,UUID uuid) {}
    private CanonFormalClientQa() {}

    /** Invoke before ShowcaseDriver's ordinary-world creation branch. */
    public static boolean observerTick(Minecraft mc){
        if(!ENABLED||!ROLE.equals("observer"))return false;
        if(mc.level!=null&&mc.player!=null){
            if(!everJoined||reconnecting){joins++;everJoined=true;connecting=false;reconnecting=false;
                receipt(joins==1?"joined":"rejoined",null,mc);}
            String host=System.getProperty("academy.qa.canon.hostName","CanonHost");
            mc.level.players().stream().filter(p->p.getGameProfile().getName().equals(host)).findFirst().ifPresent(p->{
                var target=p.getEyePosition();
                if(observeShadow){var bodies=mc.level.getEntitiesOfClass(cn.academy.port.canon.CanonLiquidEntities.Shadow.class,p.getBoundingBox().inflate(24),b->b.controlOwner().filter(p.getUUID()::equals).isPresent());
                    if(bodies.size()==1)target=bodies.getFirst().getEyePosition();}
                var d=target.subtract(mc.player.getEyePosition());
                mc.player.setYRot((float)-Math.toDegrees(Math.atan2(d.x,d.z)));
                mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
            });
            return false;
        }
        if(disconnectPending){if(activeCount()!=0)return true;receipt("disconnect-clean",null,mc);disconnectPending=false;}
        if(mc.screen instanceof TitleScreen&&++titleTicks>40&&!connecting){
            connecting=true;String address="127.0.0.1:"+PORT;
            ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),new ServerData("Canon QA",address,ServerData.Type.OTHER),false,null);
            receipt("connect-attempt",null,mc);
        }
        return true;
    }

    /** Driver repeats the same action until true. Scripts supply actual skill actions separately. */
    public static boolean step(Minecraft mc,String action){
        if(!ENABLED)throw new IllegalStateException("canon QA not enabled");
        requireIsolated(mc);
        if(action.equals("publish")){
            if(!ROLE.equals("host"))throw new IllegalStateException("host only");
            MinecraftServer server=mc.getSingleplayerServer();if(server==null)return false;
            if(!server.isPublished()){
                server.setUsesAuthentication(false);
                if(!server.publishServer(GameType.SURVIVAL,false,PORT))throw new IllegalStateException("LAN publish failed");
                receipt("published",null,mc);
            }
            return true;
        }
        if(action.equals("ready")){
            if(mc.level==null||mc.player==null)return false;
            if(ROLE.equals("host")){
                var players=serverPlayers;
                if(players.size()!=2||players.get(0).uuid.equals(players.get(1).uuid))return false;
                JsonObject data=new JsonObject();for(var p:players)data.addProperty(p.name,p.uuid.toString());
                receipt("server-two-players",data,mc);
            }else if(mc.level.players().stream().noneMatch(p->!p.getUUID().equals(mc.player.getUUID())))return false;
            if(System.getProperty("academy.canon.experimental")!=null||!cn.academy.port.canon.CanonSkills.EXPERIMENTAL)
                throw new IllegalStateException("formal QA requires default-on canon without an experimental property");
            JsonObject defaults=new JsonObject();defaults.addProperty("propertyAbsent",true);defaults.addProperty("enabled",true);
            receipt("canon-default",defaults,mc);receipt("ready",null,mc);return true;
        }
        if(action.startsWith("signal:")){String name=token(action.substring(7));try{Files.writeString(ROOT.resolve("signal-"+name),RUN,StandardOpenOption.CREATE_NEW);}catch(IOException e){throw new IllegalStateException(e);}return true;}
        if(action.equals("track-shadow")){
            if(!ROLE.equals("observer"))throw new IllegalStateException("observer camera only");
            observeShadow=true;return true;
        }
        if(action.startsWith("await:")){String name=token(action.substring(6));Path p=ROOT.resolve("signal-"+name);try{return Files.isRegularFile(p)&&Files.readString(p).equals(RUN);}catch(IOException e){throw new IllegalStateException(e);}}
        if(action.startsWith("mark:")){JsonObject data=new JsonObject();data.addProperty("label",token(action.substring(5)));receipt("mark",data,mc);return true;}
        if(action.equals("railgun-visible")){
            try{
                var f=Class.forName("cn.academy.port.client.ClassicEffects").getDeclaredField("BEAMS");f.setAccessible(true);
                for(Object beam:(java.util.List<?>)f.get(null)){
                    var c=beam.getClass();java.lang.reflect.Field len=c.getDeclaredField("length"),created=c.getDeclaredField("created"),frame=c.getDeclaredField("lastFrame"),origin=c.getDeclaredField("origin");
                    for(var field:new java.lang.reflect.Field[]{len,created,frame,origin})field.setAccessible(true);
                    if((double)len.get(beam)==64&&(long)frame.get(beam)>(long)created.get(beam)){
                        JsonObject data=new JsonObject();data.addProperty("length",64);data.addProperty("actualRenderVisited",true);data.addProperty("observerDistance",mc.player.position().distanceTo((net.minecraft.world.phys.Vec3)origin.get(beam)));receipt("classic-railgun-64-rendered",data,mc);return true;
                    }
                }
                return false;
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }
        if(action.equals("state")){
            var state=cn.academy.port.client.AcademyClient.state;String skill=state.presets.currentSkill(2);
            JsonObject data=new JsonObject();data.addProperty("category",state.category);data.addProperty("skill",skill);
            data.addProperty("canUse",state.canUse(skill));data.addProperty("active",state.isActivated());
            data.addProperty("screen",mc.screen==null?"none":mc.screen.getClass().getName());
            receipt("input-state",data,mc);return true;
        }
        if(action.startsWith("water-diagnostic:")){
            if(!ROLE.equals("host")||mc.player==null||mc.getSingleplayerServer()==null)throw new IllegalStateException("host water diagnostic only");
            String id=token(action.substring("water-diagnostic:".length()));var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();
            if(waterDiagnostic==null){waterDiagnosticId=id;waterDiagnostic=server.submit(()->{
                var player=server.getPlayerList().getPlayer(owner);if(player==null)throw new IllegalStateException("server player missing");
                return CanonFormalWaterDiagnostics.snapshot(player,id);
            });}
            if(!id.equals(waterDiagnosticId))throw new IllegalStateException("another water diagnostic is active");
            if(!waterDiagnostic.isDone())return false;
            JsonObject result=waterDiagnostic.join();waterDiagnostic=null;waterDiagnosticId=null;
            receipt("server-water-diagnostic",result,mc);return true;
        }
        if(action.startsWith("liquid-server:")){
            if(!ROLE.equals("host")||mc.player==null||mc.getSingleplayerServer()==null)throw new IllegalStateException("host liquid diagnostic only");
            String stage=token(action.substring("liquid-server:".length()));var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();
            if(liquidDiagnostic==null){liquidStage=stage;liquidDiagnostic=server.submit(()->{
                var p=server.getPlayerList().getPlayer(owner);if(p==null)throw new IllegalStateException("server player missing");
                var bodies=p.serverLevel().getEntitiesOfClass(cn.academy.port.canon.CanonLiquidEntities.Shadow.class,p.getBoundingBox().inflate(24),b->b.controlOwner().filter(owner::equals).isPresent());
                if(bodies.size()!=1||p.getAbilities().instabuild||!p.getMainHandItem().isEmpty())throw new IllegalStateException("expected owned body and actual medium consumption in survival");
                boolean installed=stage.equals("module");
                if(!installed&&!stage.equals("none")&&!stage.equals("spyglass"))throw new IllegalArgumentException("unknown liquid scenario");
                if(bodies.getFirst().hasObservationModule()!=installed)throw new IllegalStateException("incorrect installed observation module");
                if(stage.equals("spyglass")?!p.getOffhandItem().is(net.minecraft.world.item.Items.SPYGLASS):!p.getOffhandItem().isEmpty())throw new IllegalStateException("unexpected offhand or module not consumed");
                var data=new JsonObject();data.addProperty("stage",stage);data.addProperty("owner",owner.toString());data.addProperty("body",bodies.getFirst().getUUID().toString());
                data.addProperty("module",installed);data.addProperty("mediumConsumed",true);data.addProperty("offhand",p.getOffhandItem().toString());return data;
            });}
            if(!stage.equals(liquidStage))throw new IllegalStateException("another liquid diagnostic is active");
            if(!liquidDiagnostic.isDone())return false;
            var data=liquidDiagnostic.join();liquidDiagnostic=null;liquidStage=null;receipt("liquid-server",data,mc);return true;
        }
        if(action.startsWith("liquid-client:")){
            if(!ROLE.equals("host")||mc.player==null||mc.level==null)throw new IllegalStateException("host liquid camera diagnostic only");
            String stage=token(action.substring("liquid-client:".length()));
            var bodies=mc.level.getEntitiesOfClass(cn.academy.port.canon.CanonLiquidEntities.Shadow.class,mc.player.getBoundingBox().inflate(24),b->b.controlOwner().filter(mc.player.getUUID()::equals).isPresent());
            if(bodies.size()!=1)return false;
            var body=bodies.getFirst();boolean viewing=mc.getCameraEntity()==body;
            if(liquidKeyPresses!=checkedLiquidKeyPresses+1)throw new IllegalStateException("camera check requires one actual B key press since previous check");
            if(stage.equals("view")){if(!body.hasObservationModule()||!viewing)return false;}
            else if(stage.equals("none")||stage.equals("spyglass")){if(body.hasObservationModule()||mc.getCameraEntity()!=mc.player)throw new IllegalStateException("camera entered without module");}
            else if(stage.equals("exit")){if(mc.getCameraEntity()!=mc.player)throw new IllegalStateException("camera did not return to owner");}
            else throw new IllegalArgumentException("unknown liquid camera stage");
            checkedLiquidKeyPresses=liquidKeyPresses;
            JsonObject data=new JsonObject();data.addProperty("stage",stage);data.addProperty("body",body.getUUID().toString());data.addProperty("viewing",viewing);data.addProperty("keyPresses",liquidKeyPresses);
            receipt("liquid-client",data,mc);return true;
        }
        if(action.equals("liquid-observe")){
            if(!ROLE.equals("observer")||mc.level==null)throw new IllegalStateException("observer only");
            String host=System.getProperty("academy.qa.canon.hostName","CanonHost");
            var owner=mc.level.players().stream().filter(p->p.getGameProfile().getName().equals(host)).findFirst().orElse(null);if(owner==null)return false;
            var bodies=mc.level.getEntitiesOfClass(cn.academy.port.canon.CanonLiquidEntities.Shadow.class,owner.getBoundingBox().inflate(24),b->b.controlOwner().filter(owner.getUUID()::equals).isPresent());
            if(bodies.size()!=1||!bodies.getFirst().hasObservationModule())return false;
            var body=bodies.getFirst();observeShadow=true;
            JsonObject data=new JsonObject();data.addProperty("owner",owner.getUUID().toString());data.addProperty("body",body.getUUID().toString());
            data.addProperty("module",true);data.addProperty("renderer",mc.getEntityRenderDispatcher().getRenderer(body).getClass().getName());
            receipt("liquid-observer",data,mc);return true;
        }
        if(action.startsWith("grounding:"))return CanonGroundingDuelQa.step(mc,action.substring(10));
        if(action.startsWith("fixture:"))return CanonCompleteFixturesQa.step(mc,action.substring(8));
        if(action.startsWith("soak:"))return CanonMultiplayerSoakQa.step(mc,token(action.substring(5)),data->receipt("soak-checkpoint",data,mc));
        if(action.startsWith("await-vfx:"))return activeVisual(token(action.substring(10)));
        if(action.equals("rejoin")){
            if(!ROLE.equals("observer")||mc.level==null||!visualSeen||activeCount()==0)throw new IllegalStateException("rejoin requires an active real received VFX");
            receipt("disconnect-request",null,mc);disconnectPending=true;reconnecting=true;connecting=false;titleTicks=0;
            mc.disconnect(new TitleScreen());return true;
        }
        if(action.equals("wait-clean"))return !disconnectPending&&!dimensionPending&&activeCount()==0;
        if(action.equals("done")){receipt("done",null,mc);return true;}
        throw new IllegalArgumentException("unknown canon QA action "+action);
    }

    @SubscribeEvent public static void input(net.neoforged.neoforge.client.event.InputEvent.Key event){
        if(ENABLED&&ROLE.equals("host")&&event.getKey()==org.lwjgl.glfw.GLFW.GLFW_KEY_B
                &&event.getAction()==org.lwjgl.glfw.GLFW.GLFW_PRESS&&Minecraft.getInstance().screen==null)liquidKeyPresses++;
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post event){
        if(!ENABLED||!ROLE.equals("host"))return;
        CanonMultiplayerSoakQa.serverTick(event.getServer());
        serverPlayers=event.getServer().getPlayerList().getPlayers().stream()
                .map(p->new Identity(p.getGameProfile().getName(),p.getUUID())).toList();
    }
    @SubscribeEvent public static void clientTick(ClientTickEvent.Post event){
        if(!ENABLED)return;Minecraft mc=Minecraft.getInstance();installReceiver();
        Object next=mc.level;String dimension=mc.level==null?"":mc.level.dimension().location().toString();
        if(lastWorld!=null&&next==null&&previousActive>0&&visualSeen)departureActive=true;
        if(lastLiveWorld!=null&&next!=null&&lastLiveWorld!=next&&lastLiveConnection==mc.getConnection()
                && mc.getConnection()!=null&&!lastLiveDimension.equals(dimension)){
            if((previousActive>0||departureActive)&&visualSeen)dimensionPending=true;
            receipt("dimension-change",null,mc);
        }
        if(dimensionPending&&activeCount()==0){dimensionPending=false;receipt("dimension-clean",null,mc);}
        if(next!=null){lastLiveWorld=next;lastLiveConnection=mc.getConnection();lastLiveDimension=dimension;departureActive=false;}
        lastWorld=next;previousActive=activeCount();
    }
    private static void installReceiver(){
        if(CanonVfx.clientReceiver==wrapper)return;
        Consumer<CanonVfx.Visual> downstream=CanonVfx.clientReceiver;
        wrapper=v->{downstream.accept(v);Minecraft mc=Minecraft.getInstance();
            if(mc.level==null||mc.player==null||v==null||!SEEN.add(v))return;
            boolean registered=containsVisual(v);
            if(!registered&&!Boolean.getBoolean("academy.qa.canon.captureAllServerVfx"))return;
            var actor=mc.level.getEntity(v.entity());if(actor!=null&&!actor.getUUID().equals(v.owner()))return;
            visualSeen=true;JsonObject data=new JsonObject();data.addProperty("owner",v.owner().toString());data.addProperty("entity",v.entity());
            data.addProperty("skill",v.skill());data.addProperty("phase",v.phase().name());data.addProperty("local",v.owner().equals(mc.player.getUUID()));
            data.addProperty("registeredForRender",registered);
            data.addProperty("dimension",v.dimension().toString());data.addProperty("origin",v.origin().toString());data.addProperty("endpoint",v.endpoint().toString());
            data.addProperty("serverAimAxis",v.axis().toString());data.addProperty("serverRadius",v.radius());
            receipt("vfx-received",data,mc);
            CanonFloatBindingQa.receive(v,mc);
            if(registered)CanonFormalPoseQa.captureVfx(v,v.owner().equals(mc.player.getUUID()));
        };CanonVfx.clientReceiver=wrapper;
    }
    private static boolean containsVisual(CanonVfx.Visual visual){for(Object e:effects())try{
        Method m=e.getClass().getDeclaredMethod("visual");m.setAccessible(true);if(m.invoke(e)==visual)return true;
    }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}return false;}
    private static boolean activeVisual(String skill){for(Object e:effects())try{
        Method m=e.getClass().getDeclaredMethod("visual");m.setAccessible(true);
        if(m.invoke(e) instanceof CanonVfx.Visual v && skill.equals(v.skill()))return true;
    }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}return false;}
    private static int activeCount(){return effects().size();}
    private static List<?> effects(){try{Field f=CanonVfxClient.class.getDeclaredField("EFFECTS");f.setAccessible(true);return List.copyOf((List<?>)f.get(null));}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}}
    private static void requireIsolated(Minecraft mc){
        if(!RUN.matches("[a-zA-Z0-9_-]{8,64}")||!mc.gameDirectory.toPath().toAbsolutePath().normalize().startsWith(ROOT)
                ||PORT<1024||PORT>65535)throw new IllegalStateException("requires isolated QA run directory/token/port");
    }
    private static String token(String s){if(!s.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("invalid QA token");return s;}
    private static void receipt(String kind,JsonObject data,Minecraft mc){
        requireIsolated(mc);var out=new JsonObject();out.addProperty("run",RUN);out.addProperty("role",ROLE);out.addProperty("event",kind);
        out.addProperty("time",System.currentTimeMillis());out.addProperty("active",activeCount());
        if(mc.player!=null){out.addProperty("self",mc.player.getUUID().toString());out.addProperty("name",mc.player.getGameProfile().getName());}
        if(mc.level!=null){out.addProperty("worldTick",mc.level.getGameTime());out.addProperty("world",mc.level.dimension().location().toString());}
        if(data!=null)out.add("data",data);
        try{Files.createDirectories(ROOT);Files.writeString(REPORT,out+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(IOException e){throw new IllegalStateException(e);}LogUtils.getLogger().info("QA_CANON_FORMAL {} {}",ROLE,out);
    }
}
