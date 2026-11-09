package cn.academy.port.qa;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** QA-only native inventory, held-item and dropped-item gallery. Never invokes auxiliary item behavior. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CanonAuxVisualQa {
    private static final String RUN=System.getProperty("academy.qa.aux.run","");
    private static final Path ROOT=Path.of(System.getProperty("academy.qa.aux.root",".")).toAbsolutePath().normalize();
    private static final boolean ENABLED=Boolean.getBoolean("academy.qa.showcase")&&RUN.matches("[a-f0-9]{32}");
    private static final List<String> IDS=List.of("mental_remote","dark_matter_crystal","nitrogen_canister",
            "aero_pressure_chip","water_flask","buoyancy_float","origami_paper");
    private static final Set<String> SOLID=Set.copyOf(IDS);
    private static final Set<String> VERIFIED=new HashSet<>();
    private static CompletableFuture<?> pending;
    private static String pendingAction,activeItem="",activeLabel="",context="";
    private static int activeDamage,firstFrames,thirdFrames,guiFrames,worldFrames;
    private static volatile UUID dropped;
    private static boolean modelChecked;
    private CanonAuxVisualQa() {}

    static boolean step(Minecraft mc,String action){
        require(ENABLED&&mc.getSingleplayerServer()!=null&&mc.level!=null&&mc.player!=null
                &&mc.gameDirectory.toPath().toAbsolutePath().normalize().startsWith(ROOT),"isolated hidden auxiliary QA required");
        if(action.equals("setup")){
            faceForward(mc.player);
            return server(mc,action,p->{
            p.setGameMode(GameType.SURVIVAL);p.getAbilities().invulnerable=true;p.onUpdateAbilities();
            p.getFoodData().setFoodLevel(20);p.setHealth(p.getMaxHealth());p.getInventory().clearContent();
            for(int x=-8;x<=8;x++)for(int z=-8;z<=10;z++){
                p.serverLevel().setBlock(new BlockPos(x,-61,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                for(int y=-60;y<=-55;y++)p.serverLevel().setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
            }
            p.serverLevel().setBlock(new BlockPos(0,-60,2),Blocks.QUARTZ_BLOCK.defaultBlockState(),3);
            for(int i=0;i<IDS.size();i++)p.getInventory().setItem(i,stack(IDS.get(i),-1));
            p.getInventory().setItem(7,new ItemStack(cn.academy.port.AcademyCraft.COIN.get()));
            p.getInventory().setItem(8,new ItemStack(cn.academy.port.AcademyCraft.NEEDLE.get()));
            for(int damage=0;damage<=4;damage++)p.getInventory().setItem(9+damage,stack("water_flask",damage));
            p.getInventory().selected=0;p.inventoryMenu.broadcastChanges();
            p.teleportTo(p.serverLevel(),.5,-60,-1.5,0,0);p.setDeltaMovement(Vec3.ZERO);faceForward(p);
            record(mc,"setup",null);
            });
        }
        if(action.equals("models")){models(mc);return true;}
        if(action.equals("inventory")){
            reset("inventory");mc.options.hideGui=false;mc.setScreen(new InventoryScreen(mc.player));
            inventoryCursor(mc);return true;
        }
        if(action.equals("close")){mc.setScreen(null);context="";return true;}
        if(action.startsWith("hold:")){
            String id=action.substring(5);require(IDS.contains(id),"known auxiliary item");
            mc.setScreen(null);mc.options.hideGui=false;activeItem=id;activeLabel=id;activeDamage=id.equals("water_flask")?4:0;
            faceForward(mc.player);
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(0,0,mc.player.onGround()));
            var owner=mc.player.getUUID();mc.getSingleplayerServer().execute(()->{
                var player=mc.getSingleplayerServer().getPlayerList().getPlayer(owner);if(player!=null)faceForward(player);
            });
            int slot=IDS.indexOf(id);mc.player.getInventory().selected=slot;
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
            return true;
        }
        if(action.startsWith("flask:")){
            int damage=Integer.parseInt(action.substring(6));require(damage>=0&&damage<=4,"water damage0..4");
            activeItem="water_flask";activeLabel="water_flask_d"+damage;activeDamage=damage;
            mc.player.getInventory().selected=4;
            mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(4));
            return server(mc,action,p->{p.getInventory().setItem(4,stack("water_flask",damage));p.inventoryMenu.broadcastChanges();});
        }
        if(action.startsWith("drop:")){
            String id=action.substring(5);require(IDS.contains(id),"known dropped auxiliary item");
            activeItem=id;activeLabel=id;activeDamage=id.equals("water_flask")?4:0;
            return server(mc,action,p->{
                for(var old:p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(16),e->e.getTags().contains("auxvisual-"+RUN)))old.discard();
                var entity=new ItemEntity(p.serverLevel(),.5,-58.5,2.5,stack(id,-1));
                entity.setDeltaMovement(Vec3.ZERO);entity.setNeverPickUp();entity.addTag("auxvisual-"+RUN);
                require(p.serverLevel().addFreshEntity(entity),"native item entity admitted");dropped=entity.getUUID();
                var data=new JsonObject();data.addProperty("item",id);data.addProperty("entity",dropped.toString());record(mc,"drop-spawn",data);
            });
        }
        if(action.startsWith("drop-angle:")){
            String angle=action.substring(11);require(Set.of("base","quarter","half").contains(angle),"known dropped-item photo angle");
            require(context.equals("drop"),"angle photo uses existing native dropped-item context");
            var entities=mc.level.getEntitiesOfClass(ItemEntity.class,mc.player.getBoundingBox().inflate(16),e->e.getUUID().equals(dropped));
            require(entities.size()==1,"same native item entity exists for angle photo");var entity=entities.getFirst();
            require(!entity.isRemoved()&&entity.getItem().is(stack(activeItem,-1).getItem()),"same real dropped item");
            var renderer=mc.getEntityRenderDispatcher().getRenderer(entity);require(renderer instanceof net.minecraft.client.renderer.entity.ItemEntityRenderer,"angle photo retains vanilla ItemEntityRenderer");
            var data=new JsonObject();data.addProperty("item",activeItem);data.addProperty("angle",angle);data.addProperty("entity",dropped.toString());
            data.addProperty("age",entity.getAge());data.addProperty("spinRadians",entity.getSpin(0));data.addProperty("spinDegrees",Math.toDegrees(entity.getSpin(0)));
            data.addProperty("renderer",renderer.getClass().getName());record(mc,"drop-angle",data);return true;
        }
        if(action.startsWith("context:")){reset(action.substring(8));return true;}
        if(action.startsWith("check:")){check(mc,action.substring(6));return true;}
        if(action.equals("done")){
            require(modelChecked,"models checked");require(VERIFIED.containsAll(Set.of("inventory","hotbar")),"native GUI contexts");
            for(String id:IDS)for(String type:List.of("first","third","drop"))require(VERIFIED.contains(type+":"+id),"rendered "+type+":"+id);
            for(int damage=0;damage<=4;damage++)require(VERIFIED.contains("first:water_flask_d"+damage),"rendered water state "+damage);
            var data=new JsonObject();data.addProperty("verifiedContexts",VERIFIED.size());data.addProperty("auxiliaryItems",IDS.size());
            data.addProperty("waterStates",5);record(mc,"complete",data);context="";return true;
        }
        throw new IllegalArgumentException("unknown auxiliary QA step "+action);
    }

    private static boolean server(Minecraft mc,String action,Consumer<ServerPlayer> work){
        if(pending==null){pendingAction=action;var owner=mc.player.getUUID();pending=mc.getSingleplayerServer().submit(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(owner);require(p!=null,"server owner exists");work.accept(p);return true;
        });return false;}
        require(action.equals(pendingAction),"same pending setup action");if(!pending.isDone())return false;
        pending.join();pending=null;pendingAction=null;return true;
    }
    /** Orient the real fixture player; do not replace model, held transforms or renderer. */
    private static void faceForward(net.minecraft.world.entity.LivingEntity player){
        player.setYRot(0);player.yRotO=0;player.setXRot(0);player.xRotO=0;
        player.setYHeadRot(0);player.yHeadRotO=0;player.setYBodyRot(0);player.yBodyRotO=0;
    }
    /** Place only Minecraft's internal GUI cursor at (4,4), without a GLFW/OS cursor warp. */
    private static void inventoryCursor(Minecraft mc){
        try{
            var x=net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            var y=net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");x.setAccessible(true);y.setAccessible(true);
            x.setDouble(mc.mouseHandler,4d*mc.getWindow().getScreenWidth()/mc.getWindow().getGuiScaledWidth());
            y.setDouble(mc.mouseHandler,4d*mc.getWindow().getScreenHeight()/mc.getWindow().getGuiScaledHeight());
        }catch(ReflectiveOperationException error){throw new IllegalStateException("QA inventory cursor",error);}
        var data=new JsonObject();data.addProperty("guiX",4);data.addProperty("guiY",4);record(mc,"inventory-cursor",data);
    }
    private static ItemStack stack(String id,int damage){
        var item=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("academy",id));
        require(item!=net.minecraft.world.item.Items.AIR,"registered item "+id);var stack=new ItemStack(item);
        if(damage>=0)stack.setDamageValue(damage);return stack;
    }
    private static boolean held(Minecraft mc){return mc.player!=null&&!mc.player.getMainHandItem().isEmpty()
            &&BuiltInRegistries.ITEM.getKey(mc.player.getMainHandItem().getItem()).toString().equals("academy:"+activeItem)
            &&mc.player.getMainHandItem().getDamageValue()==activeDamage;}
    private static void reset(String next){
        require(Set.of("first","third","drop","inventory","hotbar").contains(next),"known render context");
        context=next;firstFrames=thirdFrames=guiFrames=worldFrames=0;
    }
    private static void check(Minecraft mc,String type){
        require(type.equals(context),"active render context");var data=new JsonObject();data.addProperty("context",type);
        if(type.equals("inventory")){
            require(mc.screen instanceof InventoryScreen&&guiFrames>0,"native inventory screen actually rendered");
            for(int i=0;i<IDS.size();i++)require(mc.player.getInventory().getItem(i).is(stack(IDS.get(i),-1).getItem()),"inventory icon "+IDS.get(i));
            for(int damage=0;damage<=4;damage++)require(mc.player.getInventory().getItem(9+damage).getDamageValue()==damage,"water inventory row "+damage);
            VERIFIED.add(type);data.addProperty("frames",guiFrames);
        }else if(type.equals("hotbar")){
            require(mc.screen==null&&!mc.options.hideGui&&guiFrames>0,"native hotbar actually rendered");
            for(int i=0;i<IDS.size();i++)require(mc.player.getInventory().getItem(i).is(stack(IDS.get(i),-1).getItem()),"hotbar icon "+IDS.get(i));
            VERIFIED.add(type);data.addProperty("frames",guiFrames);
        }else{
            require(IDS.contains(activeItem),"current item known");data.addProperty("item",activeItem);data.addProperty("label",activeLabel);data.addProperty("damage",activeDamage);
            if(type.equals("first")){require(mc.options.getCameraType().isFirstPerson()&&held(mc)&&firstFrames>0,"native first-person hand actually rendered");data.addProperty("frames",firstFrames);}
            else if(type.equals("third")){require(!mc.options.getCameraType().isFirstPerson()&&held(mc)&&thirdFrames>0,"native local third-person player actually rendered");data.addProperty("frames",thirdFrames);}
            else{
                var entities=mc.level.getEntitiesOfClass(ItemEntity.class,mc.player.getBoundingBox().inflate(16),e->e.getUUID().equals(dropped));
                require(entities.size()==1&&worldFrames>0,"native dropped item in rendered scene");var entity=entities.getFirst();
                require(!entity.isRemoved()&&entity.getItem().is(stack(activeItem,-1).getItem())&&entity.getItem().getDamageValue()==activeDamage,"same native dropped item");
                var renderer=mc.getEntityRenderDispatcher().getRenderer(entity);require(renderer instanceof net.minecraft.client.renderer.entity.ItemEntityRenderer,"vanilla ItemEntityRenderer retained");
                data.addProperty("renderer",renderer.getClass().getName());data.addProperty("entity",dropped.toString());data.addProperty("frames",worldFrames);
            }
            VERIFIED.add(type+":"+activeLabel);data.add("model",model(mc,stack(activeItem,activeDamage)));
        }
        record(mc,"context",data);
    }

    private static void models(Minecraft mc){
        var data=new JsonObject();var items=new JsonArray();for(String id:IDS){var row=model(mc,stack(id,-1));row.addProperty("item",id);items.add(row);}
        var states=new JsonArray();var fingerprints=new HashSet<String>();
        for(int damage=0;damage<=4;damage++){
            var s=stack("water_flask",damage);require(s.getMaxDamage()==4,"native maximum four charges");
            var property=ItemProperties.getProperty(s,ResourceLocation.withDefaultNamespace("damage"));require(property!=null,"vanilla damage predicate exists");
            float value=property.call(s,mc.level,mc.player,0);require(Math.abs(value-damage/4f)<1e-6,"native damage predicate value");
            var row=model(mc,s);row.addProperty("damage",damage);row.addProperty("charges",4-damage);row.addProperty("predicate",value);
            require(fingerprints.add(row.get("fingerprint").getAsString()),"five distinct resolved water models");states.add(row);
        }
        data.add("items",items);data.add("waterStates",states);record(mc,"models",data);modelChecked=true;
    }
    private static JsonObject model(Minecraft mc,ItemStack stack){
        var baked=mc.getItemRenderer().getModel(stack,mc.level,mc.player,0);require(baked!=mc.getModelManager().getMissingModel(),"not missing model");
        require(!baked.isCustomRenderer(),"resource model uses native baked item renderer");
        String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();require(!SOLID.contains(id)||baked.isGui3d(),"solid item has native 3D geometry "+id);
        boolean fabulous=Minecraft.useShaderTransparency();var nativePasses=baked.getRenderPasses(stack,fabulous);
        require(!nativePasses.isEmpty(),"native item render passes exist");
        require(!id.equals("water_flask")||nativePasses.size()==2,"water flask resolves opaque then glass native passes");
        var passRows=new JsonArray();
        for(int i=0;i<nativePasses.size();i++){
            var pass=nativePasses.get(i);var types=pass.getRenderTypes(stack,fabulous);require(!types.isEmpty(),"native pass has render types");
            var passRow=new JsonObject();passRow.addProperty("index",i);var names=new JsonArray();
            for(var type:types)names.add(type.toString());passRow.add("renderTypes",names);passRows.add(passRow);
        }
        var quads=new ArrayList<BakedQuad>();quads.addAll(baked.getQuads(null,null,RandomSource.create(17)));
        for(var face:Direction.values())quads.addAll(baked.getQuads(null,face,RandomSource.create(17)));
        require(!quads.isEmpty(),"resolved model contains baked quads");var sprites=new java.util.TreeSet<String>();var signature=new StringBuilder();
        for(var q:quads){String sprite=q.getSprite().contents().name().toString();require(!sprite.contains("missingno"),"all baked textures exist");sprites.add(sprite);
            signature.append(q.getDirection()).append(':').append(sprite).append(':').append(Arrays.toString(q.getVertices())).append(';');}
        var row=new JsonObject();row.addProperty("quadCount",quads.size());row.addProperty("gui3d",baked.isGui3d());
        row.addProperty("renderPassCount",nativePasses.size());row.addProperty("fabulous",fabulous);row.add("renderPasses",passRows);
        row.addProperty("fingerprint",hash(signature.toString()));var names=new JsonArray();sprites.forEach(names::add);row.add("sprites",names);return row;
    }
    private static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static synchronized void record(Minecraft mc,String event,JsonObject data){
        var row=new JsonObject();row.addProperty("run",RUN);row.addProperty("event",event);row.addProperty("time",System.currentTimeMillis());
        if(mc.level!=null)row.addProperty("tick",mc.level.getGameTime());if(data!=null)row.add("data",data);
        try{Files.writeString(ROOT.resolve("aux-visual-receipts.jsonl"),row+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}catch(Exception e){throw new IllegalStateException(e);}
        LogUtils.getLogger().info("QA_AUX_VISUAL {}",row);
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_AUX_VISUAL "+why);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hand(RenderHandEvent event){
        if(ENABLED&&context.equals("first")&&event.getHand()==InteractionHand.MAIN_HAND&&!event.isCanceled()&&held(Minecraft.getInstance()))firstFrames++;
    }
    @SubscribeEvent public static void player(RenderPlayerEvent.Post event){
        var mc=Minecraft.getInstance();if(ENABLED&&context.equals("third")&&event.getEntity()==mc.player&&held(mc))thirdFrames++;
    }
    @SubscribeEvent public static void inventory(ScreenEvent.Render.Post event){
        if(ENABLED&&context.equals("inventory")&&event.getScreen() instanceof InventoryScreen)guiFrames++;
    }
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();if(ENABLED&&context.equals("hotbar")&&mc.screen==null&&!mc.options.hideGui)guiFrames++;
    }
    @SubscribeEvent public static void world(RenderLevelStageEvent event){
        if(ENABLED&&context.equals("drop")&&event.getStage()==RenderLevelStageEvent.Stage.AFTER_ENTITIES)worldFrames++;
    }
}
