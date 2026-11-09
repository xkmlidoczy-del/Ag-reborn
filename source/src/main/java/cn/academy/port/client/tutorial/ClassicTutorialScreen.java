/* AcademyCraft 1.0.7 GuiTutorial/tutorial.xml modern client adaptation. GPLv3. */
package cn.academy.port.client.tutorial;

import cn.academy.port.client.AcademyClient;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.tutorial.ClassicTutorials;
import cn.academy.port.tutorial.TutorialState;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Source research guide: learned-first navigation, locked briefs, scrolling markdown and preview groups. */
public final class ClassicTutorialScreen extends Screen implements TutorialMarkdown.Context {
    private final TutorialFont classicFont=new TutorialFont();
    private TutorialState state;
    private final boolean firstOpen;
    private final long created=System.nanoTime();
    private long selectedAt=-1;
    private final Map<String,TutorialDocument> documents=new HashMap<>();
    private final Map<String,TutorialMarkdown> content=new HashMap<>(),briefs=new HashMap<>();
    private final Map<String,double[]> imageSizes=new HashMap<>();
    private List<ClassicTutorials.Page> pages=List.of();
    private ClassicTutorials.Page selected;
    private TutorialPreviews previews;
    private List<TutorialPreviews.Group> groups=List.of();
    private final TutorialNavigation.Views previewViews=new TutorialNavigation.Views();
    private double progress,dragOffset;
    private boolean dragging;
    private String language;
    private String hoverText;
    private TutorialMarkdown referenceNotice;
    public ClassicTutorialScreen(TutorialState state){super(Component.translatable("item.academy.tutorial"));this.state=state;firstOpen=!state.firstOpened();}
    @Override protected void init(){
        classicFont.use(ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.FONT.get():"Microsoft YaHei");
        String next=TutorialDocument.language(minecraft.getLanguageManager().getSelected());
        if(!next.equals(language)){language=next;documents.clear();briefs.clear();content.clear();referenceNotice=null;}
        readImageSizes();previews=new TutorialPreviews(classicFont);rebuildPages();if(selected!=null){groups=previews.groups(selected);previewViews.update(groupSizes());}
    }
    public void receive(TutorialState updated){
        boolean nameChanged=updated.misakaID()!=state.misakaID();state=updated;if(nameChanged){briefs.clear();content.clear();}
        rebuildPages();if(previews!=null&&selected!=null){groups=previews.groups(selected);previewViews.update(groupSizes());}
    }
    private void rebuildPages(){
        pages=TutorialNavigation.researchOrder(ClassicTutorials.pages(),page->state.visible(page.id()));
    }
    private TutorialDocument document(String id){return documents.computeIfAbsent(id,k->{
        String raw=read("tutorials/"+language+"/"+id+".md");if(raw==null)raw=read("tutorials/en_us/"+id+".md");
        try{return TutorialDocument.parse(raw==null?TutorialDocument.UNKNOWN:raw);}catch(IllegalArgumentException malformed){com.mojang.logging.LogUtils.getLogger().warn("Malformed tutorial {}",id,malformed);return TutorialDocument.parse(TutorialDocument.UNKNOWN);}
    });}
    private String read(String path){try(var stream=minecraft.getResourceManager().open(ResourceLocation.fromNamespaceAndPath("academy",path))){return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}catch(IOException exception){return null;}}
    private void readImageSizes(){
        imageSizes.clear();String raw=read("tutorials/image_sizes.json");if(raw==null)return;
        try{JsonParser.parseString(raw).getAsJsonObject().entrySet().forEach(e->{var a=e.getValue().getAsJsonArray();imageSizes.put(e.getKey(),new double[]{a.get(0).getAsDouble(),a.get(1).getAsDouble()});});}catch(RuntimeException ignored){}
    }
    @Override public double width(String text,double size,TutorialMarkdown.Style style){return classicFont.width(text,size,style);}
    @Override public String key(String id){return switch(id){
        case "ability_activation"->AcademyClient.TOGGLE.getTranslatedKeyMessage().getString();
        case "edit_preset"->AcademyClient.EDIT_PRESET.getTranslatedKeyMessage().getString();
        case "switch_preset"->AcademyClient.SWITCH_PRESET.getTranslatedKeyMessage().getString();
        default->"???";
    };}
    @Override public String misakaName(){return Component.translatable("ac.tutorial.misaka",state.misakaID()).getString();}
    @Override public double[] imageSize(String resource){return imageSizes.getOrDefault(resource,new double[]{16,16});}
    private TutorialMarkdown content(){return content.computeIfAbsent(selected.id(),id->new TutorialMarkdown(document(id).content(),150,this));}
    private TutorialMarkdown brief(){return briefs.computeIfAbsent(selected.id(),id->new TutorialMarkdown(document(id).brief(),130,this));}
    @Override public void removed(){super.removed();classicFont.release();}
    // GuiTutorial inherits GuiScreen's singleplayer pause default; its logo timer remains absolute.
    @Override public boolean isPauseScreen(){return true;}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        graphics.fillGradient(0,0,width,height,0xC0101010,0xD0101010);graphics.flush();
        TutorialCanvas canvas=new TutorialCanvas(graphics);canvas.begin();var placement=TutorialLayout.placement(width,height);hoverText=null;
        graphics.pose().pushPose();
        try{
            graphics.pose().translate(placement.x(),placement.y(),0);graphics.pose().scale((float)placement.scale(),(float)placement.scale(),1);
            double mx=placement.localX(mouseX),my=placement.localY(mouseY),elapsed=elapsed();
            double leftAlpha=firstOpen?TutorialLayout.blend(elapsed,1.75,.3):1;
            rect(canvas,"guis/window_tutorialleft",TutorialLayout.LEFT,new TutorialCanvas.Color(1,1,1,leftAlpha));
            if(TutorialLayout.listVisible(firstOpen,elapsed))drawList(canvas,mx,my,placement);
            drawLogos(canvas,elapsed);
            if(selected!=null){
                if(state.visible(selected.id()))drawContent(canvas,mx,my,placement);
                drawBrief(canvas,placement);drawPreview(canvas,mx,my);
            }
        }finally{graphics.pose().popPose();canvas.end();}
        if(hoverText!=null&&!hoverText.isBlank())graphics.renderTooltip(font,Component.literal(hoverText),mouseX,mouseY);
    }
    private void drawList(TutorialCanvas canvas,double mx,double my,TutorialLayout.Placement placement){
        for(int i=0;i<pages.size();i++){
            var page=pages.get(i);double y=TutorialLayout.LIST.y()+i*TutorialLayout.ROW_HEIGHT;boolean over=new TutorialLayout.Rect(TutorialLayout.LIST.x(),y,72,12).contains(mx,my);
            if(over)canvas.fill(TutorialLayout.LIST.x(),y,72,12,new TutorialCanvas.Color(1,1,1,.3));
            clip(canvas.graphics,placement,new TutorialLayout.Rect(TutorialLayout.LIST.x(),y,72,12));
            try{text(canvas,document(page.id()).title().strip(),TutorialLayout.LIST.x()+3,y+1,10,state.visible(page.id())?TutorialCanvas.WHITE:new TutorialCanvas.Color(.6,.6,.6,1),TutorialMarkdown.Style.NORMAL);}
            finally{canvas.graphics.disableScissor();}
            if(over)hoverText=document(page.id()).title().strip();
        }
    }
    private void drawContent(TutorialCanvas canvas,double mx,double my,TutorialLayout.Placement placement){
        TutorialMarkdown markdown=content();double delta=TutorialLayout.clamp(progress)*TutorialLayout.scrollRange(markdown.height());
        clip(canvas.graphics,placement,TutorialLayout.CONTENT);
        try{drawMarkdown(canvas,markdown,97,17.75-delta,mx,my);}finally{canvas.graphics.disableScissor();}
        rect(canvas,"guis/button/widget_scroll_1",TutorialLayout.TRACK,TutorialCanvas.WHITE);
        TutorialLayout.Rect handle=new TutorialLayout.Rect(TutorialLayout.TRACK.x(),11.75+TutorialLayout.SCROLL_TRAVEL*progress,9.5,53);
        rect(canvas,"guis/button/widget_scroll_2",handle,new TutorialCanvas.Color(1,1,1,handle.contains(mx,my)?1:.8));
    }
    private void drawBrief(TutorialCanvas canvas,TutorialLayout.Placement placement){
        rect(canvas,"guis/window_tutorialright",TutorialLayout.RIGHT,TutorialCanvas.WHITE);
        TutorialLayout.Rect textArea=new TutorialLayout.Rect(271.75,154.5,146,69);clip(canvas.graphics,placement,textArea);
        try{text(canvas,document(selected.id()).title().strip(),274.75,157.5,10,TutorialCanvas.WHITE,TutorialMarkdown.Style.NORMAL);drawMarkdown(canvas,brief(),274.75,169.5,-1,-1);}finally{canvas.graphics.disableScissor();}
    }
    private void drawMarkdown(TutorialCanvas canvas,TutorialMarkdown markdown,double x,double y,double mx,double my){
        for(var instruction:markdown.draws()){
            if(instruction instanceof TutorialMarkdown.Text t)text(canvas,t.value(),x+t.x(),y+t.y(),t.size(),t.reference()?TutorialCanvas.REFERENCE:TutorialCanvas.WHITE,t.style());
            else if(instruction instanceof TutorialMarkdown.Dot dot)canvas.fill(x+dot.x(),y+dot.y(),dot.size(),dot.size(),TutorialCanvas.WHITE);
            else if(instruction instanceof TutorialMarkdown.Image image){
                ResourceLocation resource=ResourceLocation.tryParse(image.resource());if(resource!=null)canvas.rect(resource,x+image.x(),y+image.y(),image.width(),image.height(),TutorialCanvas.WHITE);
                if(new TutorialLayout.Rect(x+image.x(),y+image.y(),image.width(),image.height()).contains(mx,my)&&TutorialLayout.CONTENT.contains(mx,my))hoverText=image.hover();
            }
        }
    }
    private void drawPreview(TutorialCanvas canvas,double mx,double my){
        if(groups.isEmpty()){
            if(hasUnavailableReferences()){
                if(referenceNotice==null){String notice="> "+Component.translatable("screen.academy.tutorial_source_reference").getString()+"\n\n"+Component.translatable("screen.academy.tutorial_source_details").getString();referenceNotice=new TutorialMarkdown(notice,130,this);}
                drawMarkdown(canvas,referenceNotice,279.75,42,-1,-1);
            }
            return;
        }
        var current=groups.get(previewViews.group());previews.draw(canvas,current,previewViews.view(),System.currentTimeMillis(),mx,my);
        if(previews.tooltip!=null)hoverText=previews.tooltip;
        if(current.cards().size()>1){rect(canvas,"guis/button/button_left_2",TutorialLayout.PREVIOUS,new TutorialCanvas.Color(1,1,1,TutorialLayout.PREVIOUS.contains(mx,my)?1:.8));rect(canvas,"guis/button/button_right_2",TutorialLayout.NEXT,new TutorialCanvas.Color(1,1,1,TutorialLayout.NEXT.contains(mx,my)?1:.8));}
        for(int i=0;i<groups.size();i++){
            var group=groups.get(i);double x=TutorialLayout.TAGS.x()+i*17;boolean over=new TutorialLayout.Rect(x,TutorialLayout.TAGS.y(),18,18).contains(mx,my);
            canvas.rect(TutorialCanvas.texture("guis/icons/icon_"+(group.preview().kind().equals("recipe")?"craft":"view")),x,TutorialLayout.TAGS.y(),18,18,new TutorialCanvas.Color(1,1,1,over?1:.7));
            if(over)text(canvas,previews.groupName(group),TutorialLayout.TAGS.x(),TutorialLayout.TAGS.y()-8,10,TutorialCanvas.WHITE,TutorialMarkdown.Style.NORMAL);
        }
    }
    private boolean hasUnavailableReferences(){
        // Keep original author text intact while making unavailable contextual features explicit.
        List<String> targets=switch(selected.id()){
            case "misc"->List.of("academy:cat_engine","academy:ability_interferer");
            case "ability_basis"->List.of("academy:app_skill_tree");
            case "wireless_network"->List.of("academy:app_freq_transmitter");
            case "develop_ability"->List.of("academy:phase_gen","academy:windgen_base");
            default->List.of();
        };
        return targets.stream().anyMatch(target->TutorialPreviews.registeredItem(target)==null)
            ||selected.id().equals("misc")&&(minecraft.getConnection()==null||minecraft.getConnection().getCommands().getRoot().getChild("aim")==null);
    }
    private void drawLogos(TutorialCanvas canvas,double elapsed){
        double out=selectedAt<0?1:1-TutorialLayout.blend((System.nanoTime()-selectedAt)/1e9,0,.3);if(out<=0)return;
        logo(canvas,"logo1",145.625,149.5,224.75,59,out*(firstOpen?TutorialLayout.blend(elapsed,1.3,.3):1));
        double[] glow=TutorialLayout.glowSegments(firstOpen,elapsed);double start=glow[0]*.25,end=glow[1]*.25;
        for(int direction:new int[]{-1,1}){
            double x=direction<0?258-end:258+start;double w=end-start;TutorialCanvas.Color c=new TutorialCanvas.Color(1,1,1,out);
            canvas.glow(x,182.5,w,.75,1.25,c);canvas.fill(x,182.125,w,1.25,c);
        }
        logo(canvas,"logo0",145.625,19,224.75,137,out*(firstOpen?TutorialLayout.blend(elapsed,1.75,.3):1));
        logo(canvas,"logo3",239.375,firstOpen?TutorialLayout.logo3Y(elapsed):65.375,37.25,37.25,out*(firstOpen?TutorialLayout.blend(elapsed,.1,.3):1));
        logo(canvas,"logo2",145.625,149.5,224.75,59,out*(firstOpen?TutorialLayout.blend(elapsed,.65,.3):1));
    }
    private static void logo(TutorialCanvas canvas,String name,double x,double y,double width,double height,double alpha){canvas.rect(TutorialCanvas.texture("guis/tutorial/"+name),x,y,width,height,new TutorialCanvas.Color(1,1,1,alpha));}
    private static void rect(TutorialCanvas canvas,String texture,TutorialLayout.Rect rect,TutorialCanvas.Color color){canvas.rect(TutorialCanvas.texture(texture),rect.x(),rect.y(),rect.width(),rect.height(),color);}
    private void text(TutorialCanvas canvas,String text,double x,double y,double size,TutorialCanvas.Color color,TutorialMarkdown.Style style){classicFont.draw(canvas,text,x,y,size,color,style);}
    private static void clip(GuiGraphics graphics,TutorialLayout.Placement placement,TutorialLayout.Rect rect){
        // Scissor coordinates are framebuffer-independent GUI screen coordinates, not pose-local.
        graphics.enableScissor((int)Math.floor(placement.x()+rect.x()*placement.scale()),(int)Math.floor(placement.y()+rect.y()*placement.scale()),(int)Math.ceil(placement.x()+(rect.x()+rect.width())*placement.scale()),(int)Math.ceil(placement.y()+(rect.y()+rect.height())*placement.scale()));
    }
    private double elapsed(){return (System.nanoTime()-created)/1e9;}
    private int[] groupSizes(){return groups.stream().mapToInt(group->group.cards().size()).toArray();}
    private void select(ClassicTutorials.Page page){if(selected!=page){if(selected==null)selectedAt=System.nanoTime();selected=page;progress=0;groups=previews.groups(page);previewViews.reset(groupSizes());}}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return super.mouseClicked(x,y,button);var placement=TutorialLayout.placement(width,height);double mx=placement.localX(x),my=placement.localY(y);
        if(TutorialLayout.listVisible(firstOpen,elapsed()))for(int i=0;i<pages.size();i++)if(new TutorialLayout.Rect(TutorialLayout.LIST.x(),TutorialLayout.LIST.y()+12*i,72,12).contains(mx,my)){select(pages.get(i));return true;}
        if(selected!=null&&state.visible(selected.id())&&TutorialLayout.TRACK.contains(mx,my)){
            dragging=true;double handleY=11.75+progress*163;dragOffset=my>=handleY&&my<handleY+53?my-handleY:26.5;drag(my);return true;
        }
        if(selected!=null&&!groups.isEmpty()){
            for(int i=groups.size()-1;i>=0;i--)if(new TutorialLayout.Rect(TutorialLayout.TAGS.x()+17*i,TutorialLayout.TAGS.y(),18,18).contains(mx,my)){previewViews.select(i);return true;}
            int count=groups.get(previewViews.group()).cards().size();if(count>1&&TutorialLayout.PREVIOUS.contains(mx,my)){previewViews.shift(-1);return true;}if(count>1&&TutorialLayout.NEXT.contains(mx,my)){previewViews.shift(1);return true;}
        }
        return super.mouseClicked(x,y,button);
    }
    private void drag(double localY){progress=TutorialLayout.clamp((localY-dragOffset-11.75)/163);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(dragging&&button==0){drag(TutorialLayout.placement(width,height).localY(y));return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&dragging){dragging=false;return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(selected!=null&&state.visible(selected.id())){var placement=TutorialLayout.placement(width,height);if(new TutorialLayout.Rect(92,9.75,172,220.5).contains(placement.localX(x),placement.localY(y))){double range=TutorialLayout.scrollRange(content().height());if(range>0)progress=TutorialLayout.clamp(progress-vertical*24/range);return true;}}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(TutorialLayout.listVisible(firstOpen,elapsed())&&(key==GLFW.GLFW_KEY_DOWN||key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_HOME||key==GLFW.GLFW_KEY_END)){
            int current=selected==null?-1:pages.indexOf(selected);int next=key==GLFW.GLFW_KEY_HOME?0:key==GLFW.GLFW_KEY_END?pages.size()-1:Math.floorMod(current+(key==GLFW.GLFW_KEY_DOWN?1:-1),pages.size());select(pages.get(next));return true;
        }
        if(selected!=null&&state.visible(selected.id())&&(key==GLFW.GLFW_KEY_PAGE_DOWN||key==GLFW.GLFW_KEY_PAGE_UP)){double range=TutorialLayout.scrollRange(content().height());if(range>0)progress=TutorialLayout.clamp(progress+(key==GLFW.GLFW_KEY_PAGE_DOWN?1:-1)*190/range);return true;}
        return super.keyPressed(key,scan,modifiers);
    }
}
