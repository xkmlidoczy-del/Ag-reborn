/* AcademyCraft 1.0.7 ViewGroups and RecipeHandler client adaptation. GPLv3. */
package cn.academy.port.client.tutorial;

import cn.academy.port.tutorial.ClassicTutorials;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Missing devices are visual source references, never registered stand-ins or AIR aliases. */
final class TutorialPreviews {
    record Slot(String id,int count,List<ItemStack> alternatives){
        ItemStack current(long now){return alternatives.isEmpty()?ItemStack.EMPTY:alternatives.get((int)((now/2000)%alternatives.size()));}
    }
    record Card(String type,int width,List<Slot> slots,Slot output,boolean reference,String mode){}
    record Group(ClassicTutorials.Preview preview,List<Card> cards){}
    private final Map<String,List<Card>> references=new HashMap<>();
    private final Map<String,String> pictures=new HashMap<>();
    private final TutorialFont font;
    TutorialPreviews(TutorialFont font){this.font=font;loadReferences();}
    private void loadReferences(){
        try{
            var resources=Minecraft.getInstance().getResourceManager();
            try(var reader=new InputStreamReader(resources.open(id("academy:tutorials/source_item_pictures.json")),StandardCharsets.UTF_8)){
                JsonParser.parseReader(reader).getAsJsonObject().entrySet().forEach(e->pictures.put(e.getKey(),e.getValue().getAsString()));
            }
            try(var reader=new InputStreamReader(resources.open(id("academy:tutorials/source_recipes.json")),StandardCharsets.UTF_8)){
                for(var entry:JsonParser.parseReader(reader).getAsJsonArray()){
                    var object=entry.getAsJsonObject();Slot output=slot(object.getAsJsonObject("output"));var slots=new ArrayList<Slot>();
                    for(var input:object.getAsJsonArray("slots"))slots.add(slot(input.getAsJsonObject()));
                    references.computeIfAbsent(output.id(),k->new ArrayList<>()).add(new Card(object.get("type").getAsString(),object.get("width").getAsInt(),List.copyOf(slots),output,true,object.has("mode")?object.get("mode").getAsString():""));
                }
            }
        }catch(java.io.IOException|RuntimeException exception){com.mojang.logging.LogUtils.getLogger().warn("Classic tutorial reference assets unavailable",exception);}
    }
    List<Group> groups(ClassicTutorials.Page page){
        var groups=new ArrayList<Group>();
        for(var preview:page.previews())groups.add(new Group(preview,preview.kind().equals("recipe")?recipes(preview.target()):List.of()));
        return List.copyOf(groups);
    }
    private List<Card> recipes(String target){
        var mc=Minecraft.getInstance();var cards=new ArrayList<Card>();Item item=registeredItem(target);
        if(item!=null&&mc.level!=null){
            for(var holder:mc.level.getRecipeManager().getRecipes()){
                Recipe<?> recipe=holder.value();ItemStack result=recipe.getResultItem(mc.level.registryAccess());
                if(!result.is(item)||!(recipe instanceof CraftingRecipe||recipe.getType()==RecipeType.SMELTING))continue;
                int width=recipe instanceof ShapedRecipe shaped?shaped.getWidth():3;
                String type=recipe instanceof AbstractCookingRecipe?"smelting":recipe instanceof ShapedRecipe?"shaped":"shapeless";
                var slots=new ArrayList<Slot>();for(var ingredient:recipe.getIngredients()){
                    ItemStack[] choices=ingredient.getItems();String slotId=choices.length==0?"":BuiltInRegistries.ITEM.getKey(choices[0].getItem()).toString();
                    slots.add(new Slot(slotId,1,List.of(choices)));
                }
                cards.add(new Card(type,width,List.copyOf(slots),new Slot(target,result.getCount(),List.of(result.copy())),false,""));
            }
        }
        // A source recipe remains inspectable when its device/output is absent. Machine recipe cards are
        // labeled references because their execution is outside the vanilla synced RecipeManager.
        boolean noLiveRecipes=cards.isEmpty();
        for(Card card:references.getOrDefault(target,List.of()))if(noLiveRecipes||card.type().equals("metal_former"))cards.add(card);
        return List.copyOf(cards);
    }
    private static Slot slot(JsonObject object){String name=object.get("item").getAsString();int count=object.get("count").getAsInt();Item item=registeredItem(name);return new Slot(name,count,item==null?List.of():List.of(new ItemStack(item,count)));}
    static Item registeredItem(String name){
        if(name.isEmpty())return null;ResourceLocation id=ResourceLocation.tryParse(name);
        return id!=null&&BuiltInRegistries.ITEM.containsKey(id)?BuiltInRegistries.ITEM.get(id):null;
    }
    String tooltip;
    void draw(TutorialCanvas canvas,Group group,int subView,long now,double mx,double my){
        tooltip=null;var preview=group.preview();
        if(preview.kind().equals("recipe")){
            if(group.cards().isEmpty()){label(canvas,Component.translatable("screen.academy.tutorial_no_recipe").getString(),340,60,7);return;}
            card(canvas,group.cards().get(Math.floorMod(subView,group.cards().size())),now,mx,my);return;
        }
        if(preview.kind().equals("icon")){canvas.rect(id(preview.target()),304,35,72,72,TutorialCanvas.WHITE);return;}
        Item item=registeredItem(preview.target());
        if(item!=null)model(canvas,new ItemStack(item),now,preview.kind().equals("block"));
        else {picture(canvas,preview.target(),307,35,64);referenceLabel(canvas,true);}
    }
    private void model(TutorialCanvas canvas,ItemStack stack,long now,boolean block){
        var mc=Minecraft.getInstance();var graphics=canvas.graphics;graphics.flush();graphics.pose().pushPose();
        try{
            graphics.pose().translate(344.75,70,150);graphics.pose().scale(64,-64,64);graphics.pose().mulPose(Axis.XP.rotationDegrees(-20));
            if(block){graphics.pose().mulPose(Axis.YP.rotationDegrees((float)((now/80d)%360)));graphics.pose().scale(.8f,.8f,.8f);}
            com.mojang.blaze3d.platform.Lighting.setupFor3DItems();
            mc.getItemRenderer().renderStatic(stack,ItemDisplayContext.NONE,LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,graphics.pose(),graphics.bufferSource(),mc.level,0);
            graphics.flush();
        }finally{graphics.pose().popPose();com.mojang.blaze3d.platform.Lighting.setupForFlatItems();canvas.begin();}
    }
    private void card(TutorialCanvas canvas,Card card,long now,double mx,double my){
        var graphics=canvas.graphics;double x,y,scale;
        if(card.type().equals("metal_former")){
            x=296.75;y=27.75;scale=.5;canvas.rect(TutorialCanvas.texture("guis/tutorial_metalformer"),x,y,96,96,TutorialCanvas.WHITE);
            drawSlot(canvas,card.slots().getFirst(),x+11.333333*scale,y+88.5*scale,25*scale,now,mx,my);
            drawSlot(canvas,card.output(),x+155.333333*scale,y+88.5*scale,25*scale,now,mx,my);
            String mode=card.mode();canvas.rect(TutorialCanvas.texture("guis/icons/icon_former_"+mode),x+82.666667*scale,y+22.7*scale,25*scale,25*scale,TutorialCanvas.WHITE);
        }else if(card.type().equals("smelting")){
            x=287.15;y=37.35;scale=.6;canvas.rect(TutorialCanvas.texture("guis/tutorial_smelting"),x,y,115.2,76.8,TutorialCanvas.WHITE);
            if(!card.slots().isEmpty())drawSlot(canvas,card.slots().getFirst(),x+30*scale,y+43.166667*scale,32*scale,now,mx,my);
            drawSlot(canvas,card.output(),x+123.333333*scale,y+43.166667*scale,32*scale,now,mx,my);
        }else{
            x=285.95;y=37.35;scale=.6;canvas.rect(TutorialCanvas.texture("guis/tutorial/crafting_grid"),x,y,117.6,76.8,TutorialCanvas.WHITE);
            for(int i=0;i<card.slots().size();i++){
                int col=card.type().equals("shaped")?i%card.width():i%3,row=card.type().equals("shaped")?i/card.width():i/3;
                drawSlot(canvas,card.slots().get(i),x+(5+col*43)*scale,y+(5+row*43)*scale,32*scale,now,mx,my);
            }
            drawSlot(canvas,card.output(),x+153*scale,y+49*scale,32*scale,now,mx,my);
            label(canvas,Component.translatable("ac.gui.crafttype."+card.type()).getString(),x+(98-30)*scale,y-28*scale,24*scale);
        }
        if(card.reference())referenceLabel(canvas,registeredItem(card.output().id())==null);
    }
    private void drawSlot(TutorialCanvas canvas,Slot slot,double x,double y,double size,long now,double mx,double my){
        if(slot.id().isEmpty())return;ItemStack stack=slot.current(now);boolean over=new TutorialLayout.Rect(x,y,size,size).contains(mx,my);
        if(over){canvas.fill(x,y,size,size,new TutorialCanvas.Color(1,1,1,.15));tooltip=stack.isEmpty()?sourceName(slot.id()):stack.getHoverName().getString();}
        if(stack.isEmpty()){picture(canvas,slot.id(),x,y,size);if(slot.count()>1)font.draw(canvas,Integer.toString(slot.count()),x+size-5,y+size-5,6,TutorialCanvas.WHITE,TutorialMarkdown.Style.NORMAL);return;}
        var graphics=canvas.graphics;graphics.flush();graphics.pose().pushPose();
        try{graphics.pose().translate(x,y,0);graphics.pose().scale((float)(size/16),(float)(size/16),1);graphics.renderItem(stack,0,0);graphics.renderItemDecorations(Minecraft.getInstance().font,stack,0,0);graphics.flush();}
        finally{graphics.pose().popPose();canvas.begin();}
    }
    private void picture(TutorialCanvas canvas,String target,double x,double y,double size){String picture=pictures.get(target);if(picture!=null)canvas.rect(id(picture),x,y,size,size,TutorialCanvas.WHITE);}
    private void referenceLabel(TutorialCanvas canvas,boolean unavailable){
        String key=unavailable?"screen.academy.tutorial_source_reference":"screen.academy.tutorial_recipe_reference";
        label(canvas,Component.translatable(key).getString(),344.75,118,6.5);
    }
    private void label(TutorialCanvas canvas,String text,double x,double y,double size){font.draw(canvas,text,x-font.width(text,size,TutorialMarkdown.Style.NORMAL)/2,y,size,TutorialCanvas.REFERENCE,TutorialMarkdown.Style.NORMAL);}
    String groupName(Group group){Item item=registeredItem(group.preview().target());String name=item==null?sourceName(group.preview().target()):new ItemStack(item).getHoverName().getString();return group.preview().kind().equals("recipe")?Component.translatable("ac.tutorial.crafting",name).getString():"";}
    private static String sourceName(String id){String path=id.substring(id.indexOf(':')+1);String name=path.equals("portable_developer")?"developer_portable":path.equals("phase_gen")?"phase_generator":path;
        for(String prefix:List.of("item.ac_","tile.ac_")){String key=prefix+name+".name";String result=Component.translatable(key).getString();if(!result.equals(key))return result;}return path.replace('_',' ');
    }
    private static ResourceLocation id(String value){return ResourceLocation.parse(value);}
}
