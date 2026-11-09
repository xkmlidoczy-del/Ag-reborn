package cn.academy.port.tutorial;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Portable promoted-layout check reads real binding files, never a cached earlier build or staging tree. */
public final class TutorialIntegrationRegressionTest {
    private static int checks;
    private static void check(boolean pass,String why){checks++;if(!pass)throw new AssertionError(why);}
    private static Path root;
    private static String file(String rel)throws Exception{return Files.readString(root.resolve(rel));}
    public static void main(String[] args)throws Exception {
        root=Path.of(System.getProperty("academy.tutorial.root",System.getProperty("user.dir")));
        check(file("src/main/java/cn/academy/port/AcademyCraft.java").contains("cn.academy.port.tutorial.TutorialModule.register(bus,container);"),"real module registration call");
        check(file("src/main/java/cn/academy/port/AcademyNetwork.java").contains("cn.academy.port.tutorial.TutorialNetwork.request(player,request)"),"real network dispatch");
        var client=file("src/main/java/cn/academy/port/client/AcademyClient.java");
        check(client.contains("case \"tutorial_open\" -> cn.academy.port.client.tutorial.TutorialClient.open(t)"),"actual received-item-screen ingress");
        check(client.contains("case \"tutorial_state\",\"tutorial_activate\" -> cn.academy.port.client.tutorial.TutorialClient.receive(t)"),"actual state/activation consumer");
        check(client.contains("cn.academy.port.client.tutorial.TutorialClient.clear();"),"client world/player session reset");
        var events=file("src/main/java/cn/academy/port/tutorial/TutorialEvents.java");
        check(events.contains("ItemEntityPickupEvent.Post")&&events.contains("event.getOriginalStack()"),"successful pickup remembered using pre-consumption stack");
        check(events.contains("PlayerEvent.ItemCraftedEvent")&&events.contains("PlayerEvent.ItemSmeltedEvent"),"actual crafted and furnace output event paths");
        check(events.contains("new ItemEntity(player.serverLevel(),player.getX(),player.getY()+1,player.getZ(),new ItemStack(TutorialModule.ITEM.get()))"),"source Y+1 real world guide drop");
        check(!events.contains("getInventory()")&&!events.contains("inventory.contains"),"no invented inventory-inspection unlock");
        for(String hook:List.of("PlayerLoggedInEvent","PlayerLoggedOutEvent","PlayerRespawnEvent","PlayerChangedDimensionEvent","PlayerEvent.Clone","ServerStoppingEvent","ServerStoppedEvent"))check(events.contains(hook),"real persistence/session hook "+hook);
        var module=file("src/main/java/cn/academy/port/tutorial/TutorialModule.java");
        check(module.contains("ITEMS.register(\"tutorial\",()->new TutorialItem(new Item.Properties()))"),"source stack64 tutorial registry");
        check(module.contains("NeoForge.EVENT_BUS.register(TutorialEvents.class)"),"actual gameplay subscriber registration");
        check(module.contains("ModConfig.Type.SERVER,AcademyTutorialConfig.SPEC"),"actual source config binding");
        check(file("src/main/java/cn/academy/port/tutorial/AcademyTutorialConfig.java").contains(".define(\"giveCloudTerminal\",true)"),"counterintuitive original default setting retained");
        var recipe=JsonParser.parseString(file("src/main/resources/data/academy/recipe/classic/tutorial_48.json")).getAsJsonObject();
        check(recipe.get("type").getAsString().equals("minecraft:crafting_shapeless"),"source recipe48 shape");
        check(recipe.getAsJsonArray("ingredients").size()==2,"source recipe48 input count");
        check(recipe.getAsJsonArray("ingredients").get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:book"),"source recipe48 book");
        check(recipe.getAsJsonArray("ingredients").get(1).getAsJsonObject().get("item").getAsString().equals("academy:crystal_low"),"source recipe48 low crystal");
        check(recipe.getAsJsonObject("result").get("id").getAsString().equals("academy:tutorial")&&recipe.getAsJsonObject("result").get("count").getAsInt()==1,"source recipe48 exact item/yield");
        var model=JsonParser.parseString(file("src/main/resources/assets/academy/models/item/tutorial.json")).getAsJsonObject();
        check(model.getAsJsonObject("textures").get("layer0").getAsString().equals("academy:items/tutorial"),"actual source item texture bound");
        check(Files.size(root.resolve("src/main/resources/assets/academy/textures/items/tutorial.png"))>0,"actual tutorial item texture present");
        for(var locale:List.of("en_us","zh_cn","zh_tw","ja_jp")) {
            var lang=JsonParser.parseString(file("src/main/resources/assets/academy/lang/"+locale+".json")).getAsJsonObject();
            check(lang.get("item.academy.tutorial").getAsString().equals(lang.get("item.ac_tutorial.name").getAsString()),"original localized item name "+locale);
            for(var key:List.of("source_reference","no_recipe","not_ported","recipe_reference","source_details"))check(lang.has("screen.academy.tutorial_"+key),"honest conditional preview localization "+locale+key);
        }
        check(!file("NOTICE").contains("Third-party media restored"),"media exclusions retained");
        System.out.println("PASS "+checks+" tutorial actual promoted bindings/recipe/event/language/media checks; no game startup");
    }
}
