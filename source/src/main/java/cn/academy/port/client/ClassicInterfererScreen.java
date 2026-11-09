/* AcademyCraft1.0.7 GuiAbilityInterferer/page_interfere.xml/TechUI. GPLv3; see NOTICE. */
package cn.academy.port.client;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.interferer.*;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
/** No source inventory slots or IF/wireless tab. Source callbacks update controls before20-tick tile sync. */
public final class ClassicInterfererScreen extends AbstractContainerScreen<ClassicAbilityInterfererMenu> {
    private final ClassicHudFont sourceFont=new ClassicHudFont();
    private double authoritativeRange=10,displayRange=10;private boolean displayEnabled;
    private SortedSet<String> authoritativeNames=new TreeSet<>();private List<String> displayNames=new ArrayList<>();
    private int progress;private String selected;private ClassicInterfererEditor editing;private long sequence;
    private static final ClassicHudTimeline.Rgba WHITE=rgba(1,1,1,1);
    public ClassicInterfererScreen(ClassicAbilityInterfererMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=187;}
    @Override protected void init(){super.init();leftPos=(int)Math.round(ClassicInterfererVisualRules.pageX(width));topPos=(int)Math.round(ClassicInterfererVisualRules.pageY(height));sourceFont.use(ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.FONT.get():"Microsoft YaHei");editing=null;sequence=menu.sequence();if(menu.snapshot()!=null)accept(menu.snapshot());}
    public void accept(ClassicInterfererNetwork.Snapshot snapshot){
        if(snapshot.menuId()!=menu.containerId||!snapshot.token().equals(menu.token())||!Double.isFinite(snapshot.range()))return;
        sequence=Math.max(sequence,snapshot.sequence());
        if(snapshot.periodic()){authoritativeRange=snapshot.range();authoritativeNames=new TreeSet<>(snapshot.names());if(displayNames.isEmpty())updateNames(snapshot.names());if(snapshot.sequence()==0){displayRange=snapshot.range();displayEnabled=snapshot.enabled();}}
        else switch(snapshot.action()){case "set_range"->displayRange=snapshot.range();case "set_enabled"->displayEnabled=snapshot.enabled();case "set_whitelist"->updateNames(snapshot.names());default->{}}
    }
    private void updateNames(List<String> names){displayNames=new ArrayList<>(names);progress=0;selected=null;}
    private void request(String action,double range,boolean enabled,Collection<String> names){if(sequence==Long.MAX_VALUE)return;PacketDistributor.sendToServer(new ClassicInterfererNetwork.Request(menu.containerId,menu.token(),++sequence,action,range,enabled,List.copyOf(names)));}
    @Override protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY){
        double x=ClassicInterfererVisualRules.pageX(width),y=ClassicInterfererVisualRules.pageY(height);ClassicHudCanvas canvas=new ClassicHudCanvas(graphics);canvas.begin();
        try{
            canvas.rect(tex("parent/parent_background"),x,y,176,187,WHITE);
            canvas.rect(tex("ui/ui_interfere"),x,y,176,187,rgba(1,1,1,ClassicWirelessVisualRules.breatheAlpha(ClassicWirelessClock.millis())));
            canvas.rect(tex("icons/icon_inv"),ClassicInterfererVisualRules.rootX(width)-20,y,16.8,16.8,WHITE);
            double cx=x+8,cy=y+25;
            text(canvas,"Swtich:",cx,cy+4,10,WHITE);text(canvas,"Range:",cx,cy+20,10,WHITE);
            double lum=displayEnabled?1:.6;canvas.rect(tex("button/button_switch_"+(displayEnabled?"on":"off")),cx+40,cy,16,16,rgba(lum,lum,lum,1));
            button(canvas,"button_arrowlefta",cx+40,cy+16,16,16,mouseX,mouseY);button(canvas,"button_arrowrighta",cx+100,cy+16,16,16,mouseX,mouseY);
            String range=Double.toString(displayRange);text(canvas,range,cx+56+(44-sourceFont.width(range,10,false))/2,cy+20,10,WHITE);
            double lx=x+8,ly=y+78.5;
            button(canvas,"button_add",lx+4,ly+4,12,12,mouseX,mouseY);button(canvas,"button_remove",lx+24,ly+4,12,12,mouseX,mouseY);
            button(canvas,"button_arrowupb",lx+144,ly+20,16,16,mouseX,mouseY);button(canvas,"button_arrowdownb",lx+144,ly+84,16,16,mouseX,mouseY);
            int rows=ClassicInterfererVisualRules.visibleRows(displayNames.size(),progress);
            for(int row=0;row<rows;row++){String name=displayNames.get(progress+row);double rx=lx-5,ry=ly+20+row*16;canvas.rect(tex("element/element_background300x32"),rx,ry,150,16,rgba(1,1,1,name.equals(selected)?1:.7));canvas.rect(tex("icons/icon_whitelist_single"),rx+10,ry+2,12,12,WHITE);text(canvas,ClassicInterfererEditor.emit(name,110,t->sourceFont.width(t,10,false)),rx+30,ry+4,10,WHITE);}
            if(editing!=null){canvas.fill(lx+50,ly+5,40,10,rgba(1,1,1,50/255d),false);String shown=editing.display(t->sourceFont.width(t,10,false));text(canvas,shown,lx+50,ly+5,10,WHITE);if(ClassicWirelessClock.millis()%2000<1000)text(canvas,"|",lx+50+editing.caretX(t->sourceFont.width(t,10,false)),ly+4,10,WHITE);}
        }finally{canvas.end();}
    }
    private void button(ClassicHudCanvas canvas,String name,double x,double y,int w,int h,int mouseX,int mouseY){canvas.rect(tex("button/"+name),x,y,w,h,rgba(1,1,1,within(mouseX,mouseY,x,y,w,h)?1:.7));}
    private void text(ClassicHudCanvas canvas,String value,double x,double y,double size,ClassicHudTimeline.Rgba color){sourceFont.draw(canvas,value,x,y,size,color,false,false);}
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY){}
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(button!=0)return false;double x=ClassicInterfererVisualRules.pageX(width),y=ClassicInterfererVisualRules.pageY(height),cx=x+8,cy=y+25,lx=x+8,ly=y+78.5;
        if(editing!=null){if(within(mouseX,mouseY,lx+50,ly+5,40,10)){editing.click(mouseX-lx-50,t->sourceFont.width(t,10,false));return true;}editing=null;}
        if(within(mouseX,mouseY,cx+40,cy,16,16)){request("set_enabled",0,!displayEnabled,List.of());return true;}
        if(within(mouseX,mouseY,cx+40,cy+16,16,16)||within(mouseX,mouseY,cx+100,cy+16,16,16)){double delta=mouseX<cx+56?-10:10;request("set_range",ClassicInterfererRules.clampRange(authoritativeRange+delta),false,List.of());return true;}
        if(within(mouseX,mouseY,lx+4,ly+4,12,12)){editing=new ClassicInterfererEditor();return true;}
        if(within(mouseX,mouseY,lx+24,ly+4,12,12)){if(selected!=null){var names=new TreeSet<>(authoritativeNames);names.remove(selected);request("set_whitelist",0,false,names);}return true;}
        if(within(mouseX,mouseY,lx+144,ly+20,16,16)){progress=Math.max(0,progress-1);return true;}
        if(within(mouseX,mouseY,lx+144,ly+84,16,16)){progress=Math.min(ClassicInterfererVisualRules.maxProgress(displayNames.size()),progress+1);return true;}
        int rows=ClassicInterfererVisualRules.visibleRows(displayNames.size(),progress);for(int row=0;row<rows;row++)if(within(mouseX,mouseY,lx-5,ly+20+row*16,150,16)){selected=displayNames.get(progress+row);return true;}
        return true;
    }
    @Override public boolean charTyped(char character,int modifiers){if(editing==null)return false;editing.type(character,t->sourceFont.width(t,10,false));return true;}
    @Override public boolean keyPressed(int key,int scanCode,int modifiers){
        if(editing!=null){
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){if(!editing.content().isEmpty()){var names=new TreeSet<>(authoritativeNames);names.add(editing.content());if(names.size()<=ClassicInterfererRules.MAX_NAMES)request("set_whitelist",0,false,names);}editing=null;return true;}
            if(key==GLFW.GLFW_KEY_BACKSPACE){editing.backspace(t->sourceFont.width(t,10,false));return true;}
            if(key==GLFW.GLFW_KEY_DELETE){editing.delete();return true;}
            if(key==GLFW.GLFW_KEY_LEFT){editing.left();return true;}
            if(key==GLFW.GLFW_KEY_RIGHT){editing.right(t->sourceFont.width(t,10,false));return true;}
            if(key==GLFW.GLFW_KEY_C&&hasControlDown()){minecraft.keyboardHandler.setClipboard(editing.content());return true;}
            if(key==GLFW.GLFW_KEY_V&&hasControlDown()){editing.paste(minecraft.keyboardHandler.getClipboard());return true;}
            if(key!=GLFW.GLFW_KEY_ESCAPE)return true;
        }
        return super.keyPressed(key,scanCode,modifiers);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){super.removed();editing=null;sourceFont.release();}
    private static boolean within(double mx,double my,double x,double y,double w,double h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    private static net.minecraft.resources.ResourceLocation tex(String path){return ClassicHudCanvas.texture("guis/"+path);}
    private static ClassicHudTimeline.Rgba rgba(double r,double g,double b,double a){return new ClassicHudTimeline.Rgba(r,g,b,a);}
}
