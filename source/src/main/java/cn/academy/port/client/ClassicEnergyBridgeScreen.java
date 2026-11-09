/* AcademyCraft1.0.7 BlockConverterBase.displayGui/WirelessPage.userPage. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import cn.academy.port.wireless.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** The source converter opens only the centered wireless page: no inventory, tabs or machine histogram. */
public final class ClassicEnergyBridgeScreen extends AbstractContainerScreen<ClassicWirelessMenu> {
    private final ClassicHudFont sourceFont=new ClassicHudFont();
    private final Map<Long,EditBox> passwords=new LinkedHashMap<>();
    private CompoundTag data=new CompoundTag();
    private int scroll;
    private String signature="";
    public ClassicEnergyBridgeScreen(ClassicWirelessMenu menu,Inventory player,Component title){super(menu,player,title);imageWidth=176;imageHeight=187;}
    @Override protected void init(){super.init();sourceFont.release();sourceFont.use(ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.FONT.get():"Microsoft YaHei");signature="";update();}
    @Override protected void containerTick(){super.containerTick();update();}
    private List<CompoundTag> candidates(){var result=new ArrayList<CompoundTag>();var list=data.getList("candidates",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++)result.add(list.getCompound(i));return result;}
    private void update(){
        data=menu.snapshot();var candidates=candidates();scroll=Math.max(0,Math.min(scroll,ClassicEnergyBridgeLayout.maxProgress(candidates.size())));String next=""+scroll;
        for(var candidate:candidates)next+=":"+candidate.getLong("pos")+":"+candidate.getBoolean("encrypted");if(next.equals(signature))return;signature=next;
        Map<Long,String> values=new HashMap<>();Long focused=null;for(var entry:passwords.entrySet()){values.put(entry.getKey(),entry.getValue().getValue());if(entry.getValue().isFocused())focused=entry.getKey();removeWidget(entry.getValue());}passwords.clear();setFocused(null);
        for(int i=0;i<Math.min(7,candidates.size()-scroll);i++){var target=candidates.get(scroll+i);if(!target.getBoolean("encrypted"))continue;long key=target.getLong("pos");var field=new SourceEditBox(leftPos+80,topPos+63+i*16,48,Component.translatable("ac.gui.common.prop.password"));field.setMaxLength(128);field.setBordered(false);field.setValue(values.getOrDefault(key,""));addRenderableWidget(field);passwords.put(key,field);if(Objects.equals(focused,key)){setFocused(field);field.setFocused(true);}}
    }
    private void request(String action,BlockPos target,String pass){PacketDistributor.sendToServer(new ClassicWirelessProtocol.Request(menu.containerId,menu.token(),action,target,"",pass));}
    private void connect(CompoundTag target){var field=passwords.get(target.getLong("pos"));request("link",BlockPos.of(target.getLong("pos")),field==null?"":field.getValue());if(field!=null)field.setValue("");}
    private static ClassicHudTimeline.Rgba rgba(double r,double g,double b,double a){return new ClassicHudTimeline.Rgba(r,g,b,a);}
    private static final ClassicHudTimeline.Rgba WHITE=rgba(1,1,1,1);
    private static net.minecraft.resources.ResourceLocation tex(String path){return ClassicHudCanvas.texture("guis/"+path);}
    private static String tr(String key){return Component.translatable(key).getString();}
    private void text(ClassicHudCanvas canvas,String value,double x,double y,double size,ClassicHudTimeline.Rgba tint){sourceFont.draw(canvas,value,x,y,size,tint,false,false);}
    private String fit(String value,double width){if(sourceFont.width(value,9,false)<=width)return value;String result=value;while(!result.isEmpty()&&sourceFont.width(result+"…",9,false)>width)result=result.substring(0,result.offsetByCodePoints(result.length(),-1));return result+"…";}
    private boolean hit(double mx,double my,double x,double y,double width,double height){return mx>=leftPos+x&&mx<leftPos+x+width&&my>=topPos+y&&my<topPos+y+height;}
    private void row(ClassicHudCanvas c,CompoundTag target,double y,boolean linked,boolean present,int mx,int my){
        double x=leftPos+(linked?ClassicEnergyBridgeLayout.CONNECTED_X:ClassicEnergyBridgeLayout.ROW_X);double centered=(linked?18:16)/2d;
        c.rect(tex("element/element_background300x32"),x,y,150,linked?18:16,WHITE);double alpha=present?1:.6;
        c.rect(tex("icons/icon_matrix"),x+8,y+centered-6,12,12,rgba(1,1,1,alpha));String name=present?target.getString("name"):tr("ac.gui.common.pg_wireless.not_connected");
        text(c,fit(name,!linked&&target.getBoolean("encrypted")?37:99),x+20,y+centered-4,9,linked?WHITE:rgba(.8667,.8667,.8667,1));
        if(!linked&&target.getBoolean("encrypted")){c.rect(tex("icons/icon_key"),leftPos+68,y+2,12,12,rgba(1,1,1,.7));c.fill(leftPos+80,y+3.5,48,9,rgba(.133,.133,.133,.667),false);}
        c.rect(tex("icons/icon_"+(linked&&present?"connected":"unconnected")),x+125,y+centered-6,12,12,rgba(1,1,1,hit(mx,my,x-leftPos+125,y-topPos+centered-6,12,12)?1:present?.8:.6));
    }
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mx,int my){ClassicHudCanvas c=new ClassicHudCanvas(graphics);c.begin();try{
        c.rect(tex("parent/parent_background"),leftPos,topPos,176,187,WHITE);double breathe=ClassicWirelessVisualRules.breatheAlpha(ClassicWirelessClock.millis());
        c.rect(tex("icons/icon_tonode"),leftPos+10,topPos+10,16,16,rgba(1,1,1,breathe));text(c,tr("screen.academy.wireless.connected"),leftPos+13,topPos+28,9,rgba(1,1,1,.8));
        row(c,data.getCompound("linked"),topPos+ClassicEnergyBridgeLayout.CONNECTED_Y,true,data.contains("linked",Tag.TAG_COMPOUND),mx,my);text(c,tr("screen.academy.wireless.available"),leftPos+13,topPos+52,9,rgba(1,1,1,.8));
        var targets=candidates();for(int i=0;i<Math.min(7,targets.size()-scroll);i++)row(c,targets.get(scroll+i),topPos+60+i*16,false,true,mx,my);
        c.rect(tex("button/button_arrowupb"),leftPos+ClassicEnergyBridgeLayout.ARROW_X,topPos+ClassicEnergyBridgeLayout.UP_Y,16,16,rgba(1,1,1,hit(mx,my,ClassicEnergyBridgeLayout.ARROW_X,ClassicEnergyBridgeLayout.UP_Y,16,16)?1:.8));c.rect(tex("button/button_arrowdownb"),leftPos+ClassicEnergyBridgeLayout.ARROW_X,topPos+ClassicEnergyBridgeLayout.DOWN_Y,16,16,rgba(1,1,1,hit(mx,my,ClassicEnergyBridgeLayout.ARROW_X,ClassicEnergyBridgeLayout.DOWN_Y,16,16)?1:.8));
    }finally{c.end();}}
    @Override protected void renderLabels(GuiGraphics graphics,int mx,int my){}
    @Override public void render(GuiGraphics graphics,int mx,int my,float partial){super.render(graphics,mx,my,partial);}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0){for(var field:passwords.values())if(field.isMouseOver(mx,my)){for(var other:passwords.values())other.setFocused(false);setFocused(field);field.setFocused(true);return field.mouseClicked(mx,my,button);}setFocused(null);for(var field:passwords.values())field.setFocused(false);
        if(hit(mx,my,ClassicEnergyBridgeLayout.ARROW_X,ClassicEnergyBridgeLayout.UP_Y,16,16)){scroll=Math.max(0,scroll-1);signature="";update();return true;}if(hit(mx,my,ClassicEnergyBridgeLayout.ARROW_X,ClassicEnergyBridgeLayout.DOWN_Y,16,16)){scroll=Math.min(ClassicEnergyBridgeLayout.maxProgress(candidates().size()),scroll+1);signature="";update();return true;}
        if(hit(mx,my,ClassicEnergyBridgeLayout.CONNECTED_X+125,ClassicEnergyBridgeLayout.CONNECTED_Y+3,12,12)&&data.contains("linked",Tag.TAG_COMPOUND)){request("unlink",menu.sourcePos(),"");return true;}var targets=candidates();for(int i=0;i<Math.min(7,targets.size()-scroll);i++)if(hit(mx,my,133,62+i*16,12,12)){connect(targets.get(scroll+i));return true;}}
        return true;}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(hit(mx,my,0,48,176,122)){scroll=Math.max(0,Math.min(ClassicEnergyBridgeLayout.maxProgress(candidates().size()),scroll+(vertical<0?1:vertical>0?-1:0)));signature="";update();}return true;}
    @Override public boolean mouseReleased(double mx,double my,int button){return true;}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true;}for(var entry:passwords.entrySet())if(entry.getValue().isFocused()){if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){request("link",BlockPos.of(entry.getKey()),entry.getValue().getValue());entry.getValue().setValue("");return true;}entry.getValue().keyPressed(key,scan,modifiers);return true;}if(minecraft.options.keyInventory.matches(key,scan))onClose();return true;}
    @Override public boolean charTyped(char value,int modifiers){for(var field:passwords.values())if(field.isFocused())return field.charTyped(value,modifiers);return true;}
    @Override public boolean isPauseScreen(){return false;}
    private final class SourceEditBox extends EditBox {
        private final boolean secret=true,target=true;
        private int selection,visibleStart;
        SourceEditBox(int x,int y,int width,Component label){super(ClassicEnergyBridgeScreen.this.font,x,y,width,9,label);}
        @Override public void setHighlightPos(int position){super.setHighlightPos(position);selection=Math.max(0,Math.min(position,getValue().length()));}
        private String shown(String value){return secret?"*".repeat(value.codePointCount(0,value.length())):value;}
        @Override public void onClick(double x,double y){
            double size=target?10:8;String value=getValue();double local=Math.max(0,x-getX());int position=visibleStart;
            while(position<value.length()){int next=value.offsetByCodePoints(position,1);if(sourceFont.width(shown(value.substring(visibleStart,next)),size,false)>local)break;position=next;}
            moveCursorTo(position,hasShiftDown());
        }
        @Override public void renderWidget(GuiGraphics graphics,int mx,int my,float partial){
            if(!isVisible())return;
            String value=getValue();int cursor=getCursorPosition();double size=target?10:8;
            visibleStart=Math.min(visibleStart,cursor);
            while(visibleStart<cursor&&sourceFont.width(shown(value.substring(visibleStart,cursor)),size,false)>getWidth()-2)visibleStart=value.offsetByCodePoints(visibleStart,1);
            int end=visibleStart;while(end<value.length()){int next=value.offsetByCodePoints(end,1);if(sourceFont.width(shown(value.substring(visibleStart,next)),size,false)>getWidth())break;end=next;}
            graphics.enableScissor(getX(),getY(),getX()+getWidth(),getY()+getHeight());
            ClassicHudCanvas c=new ClassicHudCanvas(graphics);c.begin();
            try{
                double alpha=1;
                int lo=Math.max(visibleStart,Math.min(cursor,selection)),hi=Math.min(end,Math.max(cursor,selection));
                if(isFocused()&&hi>lo){double a=sourceFont.width(shown(value.substring(visibleStart,lo)),size,false),b=sourceFont.width(shown(value.substring(visibleStart,hi)),size,false);c.fill(getX()+a,getY(),b-a,9,rgba(.13,.5,.85,.45*alpha),false);}
                sourceFont.draw(c,shown(value.substring(visibleStart,end)),getX(),getY(),size,rgba(1,1,1,alpha),false,false);
                if(isFocused()&&(ClassicWirelessClock.millis()/300)%2==0){double cx=sourceFont.width(shown(value.substring(visibleStart,cursor)),size,false);c.fill(getX()+cx,getY(),.6,8,rgba(1,1,1,alpha),false);}
            }finally{c.end();graphics.disableScissor();}
        }
    }
    @Override public void removed(){super.removed();sourceFont.release();for(var field:passwords.values())field.setValue("");passwords.clear();data=new CompoundTag();signature="";}

}
