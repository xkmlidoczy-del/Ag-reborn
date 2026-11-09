/* Original GuiImagFusor/page_imagfusor/TechUI art and placement. GPLv3. */
package cn.academy.port.client;
import cn.academy.port.fusion.ClassicFusorMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
public final class ClassicFusorScreen extends AbstractContainerScreen<ClassicFusorMenu> {
    private static ResourceLocation texture(String path){return ResourceLocation.fromNamespaceAndPath("academy","textures/guis/"+path+".png");}
    private static final ResourceLocation BG=texture("parent/parent_background"),INV=texture("ui/ui_inventory"),PAGE=texture("ui/ui_imagfusor"),PROGRESS=texture("progress/progress_fusor"),HIST=texture("histogram");
    public ClassicFusorScreen(ClassicFusorMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=187;}
    @Override protected void init(){super.init();leftPos=(width-304)/2;topPos=(height-imageHeight)/2;
        addRenderableWidget(new ClassicWirelessTabButton(leftPos-20,topPos+22,()->ClassicWirelessClient.openReceiver(menu.containerId,menu.sourcePos())));
    }
    @Override protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY){
        RenderSystem.enableBlend();for(var layer:new ResourceLocation[]{BG,INV,PAGE})graphics.blit(layer,leftPos,topPos,176,187,0,0,352,374,352,374);
        if(menu.progress()>0){int width=(int)(61*menu.progress());graphics.blit(PROGRESS,leftPos+58,topPos+47,width,15,0,0,(int)(126*menu.progress()),30,126,30);}
        String requirement=menu.liquidRequired()==0?"IDLE":String.valueOf(menu.liquidRequired());graphics.drawCenteredString(font,requirement,leftPos+90,topPos+14,0xcccccccc);
        int x=leftPos+186;graphics.fill(x-4,topPos-2,x+118,topPos+187,0xb5182732);graphics.fill(x-4,topPos-2,x+118,topPos,0xff25f7ff);graphics.fill(x-4,topPos+185,x+118,topPos+187,0xff25f7ff);
        graphics.drawString(font,title,x,topPos+4,0xffdceeff,false);graphics.blit(HIST,x+14,topPos+18,84,84,0,0,210,210,210,210);
        int energyHeight=(int)Math.round(48*Math.max(.03,menu.energy()/2000));int liquidHeight=(int)Math.round(48*Math.max(.03,menu.liquid()/8000.0));
        graphics.fill(x+32,topPos+97-energyHeight,x+38,topPos+97,0xff25c4ff);graphics.fill(x+52,topPos+97-liquidHeight,x+58,topPos+97,0xff7680de);
        graphics.drawString(font,Component.translatable("ac.gui.common.hist.energy"),x,topPos+108,0xffc8e0ec,false);graphics.drawString(font,String.format(Locale.ROOT,"%.1f/2000 IF",menu.energy()),x,topPos+121,0xff25c4ff,false);
        graphics.drawString(font,Component.translatable("ac.gui.common.hist.liquid"),x,topPos+142,0xffc8e0ec,false);graphics.drawString(font,menu.liquid()+"/8000 mB",x,topPos+155,0xff7680de,false);
    }
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public void render(GuiGraphics graphics,int x,int y,float partialTick){super.render(graphics,x,y,partialTick);renderTooltip(graphics,x,y);}
}
