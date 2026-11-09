/* Source-layout PresetEditUI adaptation for Minecraft GuiGraphics. See NOTICE and docs/PRESET_PORT.md. */
package cn.academy.port.client;

import cn.academy.port.core.SkillPresets;
import cn.academy.port.preset.PresetSkills;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

/** Source page dimensions, 350ms slide/fade, four-wide selector, immediate authoritative edits. */
public final class PresetEditScreen extends Screen {
    private static final double PAGE_WIDTH = 116.25, PAGE_HEIGHT = 141.5, ROW_HEIGHT = 34.75;
    private static final double ICON_SIZE = 15, SELECTOR_MARGIN = 2.5, SELECTOR_STEP = 18;
    private static final ResourceLocation BACK = texture("guis/preset_settings/back"), SELECTED = texture("guis/preset_settings/selected"), REMOVE = texture("guis/preset_settings/cancel");
    private final ClassicPresetEditor editor = new ClassicPresetEditor();
    private double selectorX, selectorY;
    private String category;
    private record Choice(String id, ResourceLocation icon, Component hint) {}

    public PresetEditScreen() { super(Component.translatable("ac.gui.preset_edit.name")); category = AcademyClient.state.category; }
    private static ResourceLocation texture(String path) { return ResourceLocation.fromNamespaceAndPath("academy", "textures/" + path + ".png"); }
    private static ResourceLocation skillIcon(String id) { return SkillIconAtlas.texture(AcademyClient.state.category,id); }
    private double left(int page,long now) { return (width-PAGE_WIDTH)/2 + editor.x(page,now); }
    private double top() { return (height-PAGE_HEIGHT)/2; }
    private List<Choice> choices() {
        var result = new ArrayList<Choice>();
        result.add(new Choice("",REMOVE,Component.translatable("ac.gui.preset_edit.skill_remove")));
        for (var skill : PresetSkills.available(AcademyClient.state,editor.browsing()))
            result.add(new Choice(skill.id(),skillIcon(skill.id()),Component.translatable("ac.ability."+skill.category()+"."+skill.id()+".name")));
        return result;
    }
    private static void image(GuiGraphics g, ResourceLocation texture,int x,int y,int w,int h,int originalW,int originalH) {
        g.blit(texture,x,y,w,h,0,0,originalW,originalH,originalW,originalH);
    }
    @Override public void tick() {
        super.tick();if(!AcademyClient.state.hasCategory()||!category.equals(AcademyClient.state.category))onClose();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        long now=Util.getMillis();boolean transiting=editor.transiting(now);
        g.fill(0,0,width,height,0xA6000000);
        g.drawString(font,title,5,5,0xFFFFFFFF,false);
        for(int page=0;page<SkillPresets.MAX_PRESETS;page++){
            double scale=editor.scale(page,now),alpha=editor.alpha(page,now),x=left(page,now),y=top();
            g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale((float)scale,(float)scale,1);
            g.setColor(1,1,1,(float)alpha);image(g,BACK,0,0,116,142,465,566);
            int color=((int)(alpha*255)<<24)|0xFFFFFF;
            g.drawCenteredString(font,Component.translatable("ac.gui.preset_edit.tag").append(Integer.toString(page+1)),58,-14,color);
            for(int slot=0;slot<SkillPresets.MAX_KEYS;slot++){
                int rowY=(int)Math.round(1.5+slot*ROW_HEIGHT);
                double lx=(mx-x)/scale,ly=(my-y)/scale;
                if(!transiting&&page==editor.browsing()&&lx>=3&&lx<113&&ly>=rowY&&ly<rowY+ROW_HEIGHT)
                    image(g,SELECTED,3,rowY,110,35,440,149);
                String id=AcademyClient.state.presets.skill(page,slot);
                if(!id.isEmpty()){
                    image(g,skillIcon(id),5,rowY+4,27,27,32,32);
                    g.pose().pushPose();g.pose().translate(36.25,rowY+12,0);g.pose().scale(.75f,.75f,1);
                    String text=Component.translatable("ac.ability."+AcademyClient.state.category+"."+id+".name").getString();
                    if(font.width(text)>91)text=font.plainSubstrByWidth(text,82)+"…";
                    g.drawString(font,text,0,0,color,false);g.pose().popPose();
                }
            }
            g.setColor(1,1,1,1);g.pose().popPose();
        }
        if(!transiting&&editor.selectorSlot()!=-1)drawSelector(g,mx,my);
    }
    private void drawSelector(GuiGraphics g,int mx,int my){
        var choices=choices();int cols=Math.min(4,choices.size()),rows=(choices.size()+3)/4;
        int w=(int)Math.ceil(2*SELECTOR_MARGIN+ICON_SIZE+(cols-1)*SELECTOR_STEP),h=(int)Math.ceil(2*SELECTOR_MARGIN+ICON_SIZE+(rows-1)*SELECTOR_STEP);
        int x=(int)selectorX,y=(int)selectorY;
        g.fill(x-1,y-1,x+w+1,y+h+1,0x66FFFFFF);g.fill(x,y,x+w,y+h,0xC8313131);
        Component hint=Component.translatable("ac.gui.preset_edit.skill_select");
        for(int choice=0;choice<choices.size();choice++){
            int bx=(int)(selectorX+SELECTOR_MARGIN+(choice%4)*SELECTOR_STEP),by=(int)(selectorY+SELECTOR_MARGIN+(choice/4)*SELECTOR_STEP);
            var entry=choices.get(choice);image(g,entry.icon,bx,by,15,15,entry.id.isEmpty()?23:32,entry.id.isEmpty()?23:32);
            if(mx>=bx&&mx<bx+ICON_SIZE&&my>=by&&my<by+ICON_SIZE){g.fill(bx,by,bx+15,by+15,0x33FFFFFF);hint=entry.hint;}
        }
        int hintWidth=(int)Math.ceil(font.width(hint)*.75)+6;
        g.fill(x-1,y-15,x+hintWidth+1,y-2,0x33FFFFFF);g.fill(x,y-14,x+hintWidth,y-3,0xC8313131);
        g.pose().pushPose();g.pose().translate(x+3,y-12,0);g.pose().scale(.75f,.75f,1);g.drawString(font,hint,0,0,0xFFBBBBBB,false);g.pose().popPose();
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0)return super.mouseClicked(mx,my,button);
        long now=Util.getMillis();if(editor.transiting(now))return true;
        if(editor.selectorSlot()!=-1){
            var choices=choices();
            for(int choice=0;choice<choices.size();choice++){
                double bx=selectorX+SELECTOR_MARGIN+(choice%4)*SELECTOR_STEP,by=selectorY+SELECTOR_MARGIN+(choice/4)*SELECTOR_STEP;
                if(mx>=bx&&mx<bx+ICON_SIZE&&my>=by&&my<by+ICON_SIZE){
                    AcademyClient.editPreset(editor.browsing(),editor.selectorSlot(),choices.get(choice).id);editor.closeSelector();return true;
                }
            }
        }
        for(int page=0;page<SkillPresets.MAX_PRESETS;page++){
            double scale=editor.scale(page,now),lx=(mx-left(page,now))/scale,ly=(my-top())/scale;
            if(lx>=3&&lx<113&&ly>=1.5&&ly<1.5+4*ROW_HEIGHT){
                int slot=(int)((ly-1.5)/ROW_HEIGHT);boolean wasSelector=editor.selectorSlot()!=-1;
                editor.click(page,slot,now);
                if(!wasSelector&&editor.selectorSlot()!=-1){selectorX=mx;selectorY=my;}
                return true;
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public void onClose(){editor.close();super.onClose();}
    @Override public void removed(){editor.close();super.removed();}
}
