package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.GroundingReturn;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Isolated real-player Grounding duel. Setup supplies legal PvP/loadout only.
 * All damage is produced by the second client's ordinary forked_arc key input.
 * Native visual callbacks are forwarded unchanged; damage/context/ledgers are read only.
 */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonGroundingDuelQa {
    private static final String ROLE=System.getProperty("academy.qa.canon.role","");
    private static final String RUN=System.getProperty("academy.qa.canon.run","");
    private static final Path ROOT=Path.of(System.getProperty("academy.qa.canon.root",".")).toAbsolutePath().normalize();
    private static final boolean ENABLED=Boolean.getBoolean("academy.qa.showcase")&&Boolean.getBoolean("academy.qa.canon.groundingDuel")&&(ROLE.equals("host")||ROLE.equals("observer"));
    private static CompletableFuture<JsonObject> pending;
    private static String pendingOp;
    private static Consumer<GroundingReturn.Visual> wrapper;
    private static boolean armed,fired,collecting;
    private static int releaseTicks,rDown,rUp,shiftDown,incomingCount,postCount;
    private static UUID hostId,guestId;
    private static volatile BigDecimal maximumSpent=BigDecimal.ZERO;
    private static volatile boolean precisionObserved;
    private static JsonObject before,latest;
    private static final JsonArray samples=new JsonArray(),incoming=new JsonArray(),posts=new JsonArray();
    private CanonGroundingDuelQa() {}

    static boolean step(Minecraft mc,String op){
        require(ENABLED&&mc.player!=null&&mc.level!=null&&RUN.matches("[a-zA-Z0-9_-]{8,64}")&&mc.gameDirectory.toPath().toAbsolutePath().normalize().startsWith(ROOT),"isolated enabled Grounding duel required");
        if(op.equals("arm")){
            require(ROLE.equals("observer"),"attacker arm on second client only");var state=cn.academy.port.client.AcademyClient.state;
            require(state.category.equals("electromaster")&&state.canUse("forked_arc")&&state.presets.currentSkill(2).equals("forked_arc"),"actual attacker skill sync ready");
            require(mc.screen==null&&mc.getCameraEntity()==mc.player,"actual attacker in normal gameplay view");armed=true;receipt("attacker-armed",null);return true;
        }
        if(op.equals("await-commit"))return maximumSpent.signum()>0&&precisionObserved;
        if(op.equals("await-attack"))return fired&&releaseTicks==0;
        if(op.equals("summary")){var j=new JsonObject();j.addProperty("fired",fired);receipt("summary",j);return true;}
        require(ROLE.equals("host")&&mc.getSingleplayerServer()!=null,"host server diagnostic operation required");
        if(pending==null){
            pendingOp=op;var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();
            pending=server.submit(()->{
                if(op.equals("setup")){
                    var host=server.getPlayerList().getPlayer(owner);var guest=server.getPlayerList().getPlayers().stream().filter(p->p!=host).findFirst().orElseThrow();
                    require(server.getPlayerList().getPlayerCount()==2&&AcademyConfig.ATTACK_PLAYERS.get(),"fresh two-player profile with attackPlayers enabled");
                    server.setPvpAllowed(true);require(host.getTeam()==null&&guest.getTeam()==null,"fresh opponents are not on a shared team");
                    for(int x=-2;x<=2;x++)for(int z=-2;z<=6;z++){server.overworld().setBlock(new BlockPos(x,-61,z),Blocks.STONE.defaultBlockState(),3);for(int y=-60;y<=-56;y++)server.overworld().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);}
                    prepare(host,5,GroundingReturn.ID);prepare(guest,1,"forked_arc");host.teleportTo(.5,-60,.5);guest.teleportTo(.5,-60,3.5);
                    hostId=host.getUUID();guestId=guest.getUUID();require(host.canHarmPlayer(guest)&&guest.canHarmPlayer(host),"actual server PvP policy allows both opponents");return snapshot(server);
                }
                if(op.equals("begin")){before=snapshot(server);collecting=true;maximumSpent=BigDecimal.ZERO;precisionObserved=false;return before;}
                if(op.equals("check")){
                    latest=snapshot(server);var out=new JsonObject();out.add("before",before);out.add("after",latest);out.add("incoming",incoming.deepCopy());out.add("posts",posts.deepCopy());out.add("serverTickSamples",samples.deepCopy());
                    out.addProperty("maximumSpent",maximumSpent.toPlainString());out.addProperty("precisionUsed",precisionObserved);out.addProperty("incomingCount",incomingCount);out.addProperty("postCount",postCount);
                    out.addProperty("host",hostId.toString());out.addProperty("attacker",guestId.toString());out.addProperty("attackSkill","electromaster.forked_arc");
                    var host=server.getPlayerList().getPlayer(hostId);boolean hpSame=new BigDecimal(HighPowerCombat.healthText(host)).compareTo(new BigDecimal(before.getAsJsonObject("host").get("health").getAsString()))==0;
                    out.addProperty("passed",maximumSpent.signum()>0&&precisionObserved&&incomingCount==1&&postCount==1&&hpSame);
                    out.addProperty("hostHealthUnchanged",hpSame);
                    collecting=false;return out;
                }
                if(op.equals("post"))return snapshot(server);
                throw new IllegalArgumentException(op);
            });return false;
        }
        require(op.equals(pendingOp),"diagnostic operation ordering");if(!pending.isDone())return false;
        JsonObject value=pending.join();pending=null;pendingOp=null;receipt(op,value);return true;
    }
    private static void prepare(ServerPlayer p,int level,String skill){
        p.setGameMode(GameType.SURVIVAL);p.getInventory().clearContent();p.removeAllEffects();
        var state=AbilityStorage.get(p);state.selectCategory("electromaster");state.setLevel(level);state.learn("arc_gen");state.learn(skill);state.activated=true;state.overloadFine=true;
        state.presets.switchTo(0);for(int i=0;i<4;i++)state.presets.edit(0,i,i==2?skill:"",state::learned);
        p.getPersistentData().remove(HighPowerCombat.DATA_KEY);require(HighPowerCombat.enroll(p,level),"real QA enrollment");HighPowerResources.initializeBudget(p);AbilityStorage.save(p);AcademyNetwork.sync(p);
    }
    private static JsonObject player(ServerPlayer p){
        var s=AbilityStorage.get(p);var j=new JsonObject();j.addProperty("uuid",p.getUUID().toString());j.addProperty("name",p.getGameProfile().getName());j.addProperty("health",HighPowerCombat.healthText(p));j.addProperty("shield",HighPowerCombat.shieldText(p));j.addProperty("maxShield",HighPowerCombat.maxShieldText(p));j.addProperty("cp",HighPowerResources.cp(p));j.addProperty("nativeCp",s.cp);j.addProperty("slot2",s.presets.currentSkill(2));j.addProperty("sneaking",p.isShiftKeyDown());j.addProperty("onGround",p.onGround());j.addProperty("position",p.position().toString());j.addProperty("experience",s.exp(GroundingReturn.ID));return j;
    }
    private static JsonObject snapshot(net.minecraft.server.MinecraftServer server){
        var host=server.getPlayerList().getPlayer(hostId==null?Minecraft.getInstance().player.getUUID():hostId);var guest=server.getPlayerList().getPlayers().stream().filter(p->p!=host).findFirst().orElseThrow();
        var j=new JsonObject();j.addProperty("serverTick",server.getTickCount());j.addProperty("worldTick",server.overworld().getGameTime());j.addProperty("pvp",server.isPvpAllowed());j.addProperty("attackPlayers",AcademyConfig.ATTACK_PLAYERS.get());j.addProperty("serverPlayers",server.getPlayerList().getPlayerCount());j.add("host",player(host));j.add("attacker",player(guest));
        Object context=context(hostId==null?host.getUUID():hostId);j.addProperty("contextPresent",context!=null);
        if(context!=null){
            var ctx=new JsonObject();for(String name:List.of("capacity","spent","precisionUsed","started","sequence")){
                Object value=read(context,name);if(value instanceof Boolean b)ctx.addProperty(name,b);else if(value instanceof Number n&&!(value instanceof BigDecimal))ctx.addProperty(name,n);else ctx.addProperty(name,value.toString());
            }j.add("context",ctx);
        }
        return j;
    }
    private static Object context(UUID id){try{var f=GroundingReturn.class.getDeclaredField("CONTEXTS");f.setAccessible(true);return ((Map<?,?>)f.get(null)).get(id);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static Object read(Object value,String name){try{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        if(!ENABLED)return;var mc=Minecraft.getInstance();if(GroundingReturn.clientReceiver!=wrapper){var downstream=GroundingReturn.clientReceiver;wrapper=v->{downstream.accept(v);mc.execute(()->{var j=new JsonObject();j.addProperty("owner",v.owner().toString());j.addProperty("entity",v.entity());j.addProperty("input",v.input());j.addProperty("epoch",v.epoch());j.addProperty("sequence",v.sequence());j.addProperty("phase",v.phase());j.addProperty("remaining",v.remaining());j.addProperty("point",v.point().toString());receipt("native-visual",j);
                    if(ROLE.equals("observer")&&armed&&!fired&&mc.level!=null&&mc.level.players().stream().anyMatch(p->p.getUUID().equals(v.owner())&&p.getGameProfile().getName().equals(System.getProperty("academy.qa.canon.hostName","CanonHost")))&&v.phase()==0&&v.remaining()>=55){armed=false;fired=true;System.setProperty("academy.qa.key.82","true");mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),82,0,1,0);releaseTicks=2;receipt("actual-attacker-r-dispatched",j);}
                });};GroundingReturn.clientReceiver=wrapper;}
        if(releaseTicks>0&&--releaseTicks==0){System.clearProperty("academy.qa.key.82");mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),82,0,0,0);receipt("actual-attacker-r-released",null);}
    }
    @SubscribeEvent public static void input(InputEvent.Key e){if(ENABLED){if(e.getKey()==82&&e.getAction()==1)rDown++;if(e.getKey()==82&&e.getAction()==0)rUp++;if(e.getKey()==340&&e.getAction()==1)shiftDown++;}}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e){if(!ENABLED||!ROLE.equals("host")||!collecting||hostId==null)return;var j=snapshot(e.getServer());samples.add(j);if(j.has("context")){var c=j.getAsJsonObject("context");maximumSpent=maximumSpent.max(new BigDecimal(c.get("spent").getAsString()));precisionObserved|=c.get("precisionUsed").getAsBoolean();}}
    private static boolean duel(net.minecraft.world.entity.LivingEntity target,net.minecraft.world.damagesource.DamageSource source){return ENABLED&&ROLE.equals("host")&&collecting&&target.getUUID().equals(hostId)&&source.getEntity()!=null&&source.getEntity().getUUID().equals(guestId);}
    private static JsonObject damage(net.minecraft.world.entity.LivingEntity target,net.minecraft.world.damagesource.DamageSource source){
        var j=new JsonObject();j.addProperty("worldTick",target.level().getGameTime());j.addProperty("target",target.getUUID().toString());j.addProperty("owner",source.getEntity().getUUID().toString());j.addProperty("sourceClass",source.getClass().getName());j.addProperty("sourceType",source.getMsgId());j.addProperty("sourceSkill",String.valueOf(read(source,"skill")));j.addProperty("sourceExactRaw",String.valueOf(read(source,"raw")));j.addProperty("hostHealth",HighPowerCombat.healthText(target));j.addProperty("hostShield",HighPowerCombat.shieldText(target));j.addProperty("attackerCp",HighPowerResources.cp((ServerPlayer)source.getEntity()));return j;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public static void incoming(LivingIncomingDamageEvent e){if(duel(e.getEntity(),e.getSource())){incomingCount++;var j=damage(e.getEntity(),e.getSource());j.addProperty("canceledAtObservedPriority",e.isCanceled());incoming.add(j);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void post(LivingDamageEvent.Post e){if(duel(e.getEntity(),e.getSource())){postCount++;posts.add(damage(e.getEntity(),e.getSource()));}}
    private static synchronized void receipt(String event,JsonObject data){
        if(!ENABLED)return;var j=new JsonObject();j.addProperty("run",RUN);j.addProperty("role",ROLE);j.addProperty("event",event);j.addProperty("time",System.currentTimeMillis());j.addProperty("physicalRDown",rDown);j.addProperty("physicalRUp",rUp);j.addProperty("physicalShiftDown",shiftDown);if(data!=null)j.add("data",data);
        try{Files.writeString(ROOT.resolve(ROLE+"-grounding-receipts.jsonl"),j+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(java.io.IOException e){throw new IllegalStateException(e);}com.mojang.logging.LogUtils.getLogger().info("QA_GROUNDING_DUEL {}",j);
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException(why);}
}
