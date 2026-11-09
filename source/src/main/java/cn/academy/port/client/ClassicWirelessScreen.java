/* AcademyCraft1.0.7 GuiNode/GuiMatrix2/TechUI WirelessPage; GPLv3. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import cn.academy.port.wireless.ClassicWirelessMenu;
import cn.academy.port.wireless.ClassicWirelessProtocol;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Native inventory and token-bound wireless actions over original source art. Passwords are local inputs only. */
public final class ClassicWirelessScreen extends AbstractContainerScreen<ClassicWirelessMenu> {
    private static final ClassicHudTimeline.Rgba WHITE = rgba(1,1,1,1);
    private final ClassicHudFont sourceFont = new ClassicHudFont();
    private final ClassicWirelessNodeAnimation nodeAnimation=new ClassicWirelessNodeAnimation();
    private final Map<String, Field> fields = new LinkedHashMap<>();
    private boolean wireless;
    private int scroll;
    private String fieldSignature = "";
    private CompoundTag data = new CompoundTag();
    private long linkedChanged, sentAt = -1;
    private boolean lastLinked;
    private String submitted = "",lastActionStatus="";
    private long openedAt,lastInfoTime;
    private double infoHeight,infoAlpha=1,textAlpha=1;
    private record Field(EditBox box, String action, BlockPos target, boolean secret) {}
    public ClassicWirelessScreen(ClassicWirelessMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title); imageWidth=176; imageHeight=187;
        wireless=menu.kind()==ClassicWirelessMenu.Kind.USER;
    }
    @Override protected void init() {
        super.init(); leftPos=(width-304)/2; topPos=(height-imageHeight)/2;
        sourceFont.use(ClassicHudConfig.SPEC.isLoaded()?ClassicHudConfig.FONT.get():"Microsoft YaHei");
        fields.clear();fieldSignature=""; linkedChanged=ClassicWirelessClock.millis();openedAt=lastInfoTime=linkedChanged;infoHeight=0; updateData();
    }
    @Override protected void containerTick() { super.containerTick();updateData(); }
    private void updateData() {
        data=menu.snapshot(); boolean linked=data.contains("linked",Tag.TAG_COMPOUND);
        if(linked!=lastLinked){lastLinked=linked;linkedChanged=ClassicWirelessClock.millis();}
        String signature=menu.kind()+":"+data.getBoolean("configure")+":"+data.getBoolean("initialized")+":"+wireless+":"+scroll;
        for(var target:candidates())signature+=":"+target.getLong("pos")+":"+target.getBoolean("encrypted");
        if(!signature.equals(fieldSignature)){rebuildFields();fieldSignature=signature;}
    }
    private void rebuildFields() {
        Map<String,String> saved=new LinkedHashMap<>();String focused="";
        for(var entry:fields.entrySet()){saved.put(entry.getKey(),entry.getValue().box().getValue());if(entry.getValue().box().isFocused())focused=entry.getKey();removeWidget(entry.getValue().box());}
        setFocused(null);fields.clear();int infoX=leftPos+179,infoY=topPos+5;
        if(data.getBoolean("configure")){
            if(menu.kind()==ClassicWirelessMenu.Kind.NODE){
                field("name",infoX+46,infoY+107,46,false,"node_name",menu.sourcePos(),data.getString("node_name"));
                field("password",infoX+46,infoY+115,46,true,"node_password",menu.sourcePos(),"");
            }else if(menu.kind()==ClassicWirelessMenu.Kind.MATRIX){
                field("ssid",infoX+46,infoY+118,46,false,data.getBoolean("initialized")?"matrix_ssid":"",menu.sourcePos(),data.getString("ssid"));
                field("password",infoX+46,infoY+(data.getBoolean("initialized")?137:126),46,true,data.getBoolean("initialized")?"matrix_password":"",menu.sourcePos(),"");
            }
        }
        if(wireless){
            var targets=candidates();int count=Math.min(7,Math.max(0,targets.size()-scroll));
            for(int i=0;i<count;i++){var target=targets.get(scroll+i);if(target.getBoolean("encrypted"))
                field("target:"+target.getLong("pos"),leftPos+80,topPos+63+i*16,48,true,"link",BlockPos.of(target.getLong("pos")),"");}
        }
        for(var entry:fields.entrySet()){if(saved.containsKey(entry.getKey()))entry.getValue().box().setValue(saved.get(entry.getKey()));if(entry.getKey().equals(focused)){setFocused(entry.getValue().box());entry.getValue().box().setFocused(true);}}
    }
    private void field(String key,int x,int y,int w,boolean secret,String action,BlockPos target,String value){
        EditBox box=new SourceEditBox(x,y,w,secret,key.startsWith("target:"),Component.translatable(secret?"ac.gui.common.prop.password":"ac.gui.common.prop."+(key.equals("name")?"node_name":"ssid")));
        box.setMaxLength(128);box.setBordered(false);box.setTextColor(0xffdceeff);box.setValue(value);
        if(secret)box.setFormatter((text,position)->Component.literal("*".repeat(text.codePointCount(0,text.length()))).getVisualOrderText());
        addRenderableWidget(box);fields.put(key,new Field(box,action,target,secret));
    }
    private final class SourceEditBox extends EditBox {
        private final boolean secret,target;
        private int selection,visibleStart;
        SourceEditBox(int x,int y,int width,boolean secret,boolean target,Component label){super(ClassicWirelessScreen.this.font,x,y,width,9,label);this.secret=secret;this.target=target;}
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
                double alpha=target?1:infoAlpha;
                int lo=Math.max(visibleStart,Math.min(cursor,selection)),hi=Math.min(end,Math.max(cursor,selection));
                if(isFocused()&&hi>lo){double a=sourceFont.width(shown(value.substring(visibleStart,lo)),size,false),b=sourceFont.width(shown(value.substring(visibleStart,hi)),size,false);c.fill(getX()+a,getY(),b-a,9,rgba(.13,.5,.85,.45*alpha),false);}
                sourceFont.draw(c,shown(value.substring(visibleStart,end)),getX(),getY(),size,rgba(1,1,1,alpha),false,false);
                if(isFocused()&&(ClassicWirelessClock.millis()/300)%2==0){double cx=sourceFont.width(shown(value.substring(visibleStart,cursor)),size,false);c.fill(getX()+cx,getY(),.6,8,rgba(1,1,1,alpha),false);}
            }finally{c.end();graphics.disableScissor();}
        }
    }
    private List<CompoundTag> candidates(){var result=new ArrayList<CompoundTag>();var list=data.getList("candidates",Tag.TAG_COMPOUND);for(int i=0;i<list.size();i++)result.add(list.getCompound(i));return result;}
    public void serverSnapshot(CompoundTag snapshot){
        String status=snapshot.getString("status");if(!status.isEmpty()){sentAt=-1;lastActionStatus=status;}
    }
    private void request(String action,BlockPos target,String value,String password){
        PacketDistributor.sendToServer(new ClassicWirelessProtocol.Request(menu.containerId,menu.token(),action,target,value,password));
        sentAt=ClassicWirelessClock.millis();submitted=action;lastActionStatus="";
    }
    private void submit(Field field){
        if(field.action().isEmpty())return;
        String value=field.secret()?"":field.box().getValue(),pass=field.secret()?field.box().getValue():"";
        request(field.action(),field.target(),value,pass);
        if(field.secret())field.box().setValue("");
    }
    @Override protected void renderBg(GuiGraphics graphics,float partialTick,int mouseX,int mouseY){
        ClassicHudCanvas canvas=new ClassicHudCanvas(graphics);canvas.begin();
        try{
            canvas.rect(tex("parent/parent_background"),leftPos,topPos,176,187,WHITE);
            double alpha=ClassicWirelessVisualRules.breatheAlpha(ClassicWirelessClock.millis());
            if(!wireless){
                canvas.rect(tex("ui/ui_inventory"),leftPos,topPos,176,187,rgba(1,1,1,alpha));
                if(menu.kind()!=ClassicWirelessMenu.Kind.USER)canvas.rect(tex("ui/ui_"+(menu.kind()==ClassicWirelessMenu.Kind.NODE?"node":"matrix")),leftPos,topPos,176,187,rgba(1,1,1,alpha));
                if(menu.kind()==ClassicWirelessMenu.Kind.NODE){
                    int frame=nodeAnimation.update(lastLinked,ClassicWirelessClock.millis());
                    canvas.rect(tex("effect/effect_node"),leftPos+42,topPos+35.5,93,37.5,0,frame/10d,1,(frame+1)/10d,rgba(1,1,1,alpha),null,null);
                }
            }else drawWireless(canvas,mouseX,mouseY,alpha);
            drawTabs(canvas,mouseX,mouseY);drawInfo(canvas,mouseX,mouseY);
        }finally{canvas.end();}
    }
    private void drawTabs(ClassicHudCanvas c,int mx,int my){
        double invTint=wireless?.8:1;
        c.rect(tex("icons/icon_inv"),leftPos-20,topPos,16.8,16.8,rgba(invTint,invTint,invTint,!wireless||hit(mx,my,-20,0,17,17)?1:.8));
        if(menu.kind()!=ClassicWirelessMenu.Kind.MATRIX){double tint=wireless?1:.8;c.rect(tex("icons/icon_wireless"),leftPos-20,topPos+22,16.8,16.8,rgba(tint,tint,tint,wireless||hit(mx,my,-20,22,17,17)?1:.8));}
    }
    private void drawWireless(ClassicHudCanvas c,int mx,int my,double alpha){
        c.rect(tex("icons/icon_"+(menu.kind()==ClassicWirelessMenu.Kind.NODE?"tomatrix":"tonode")),leftPos+10,topPos+10,16,16,rgba(1,1,1,alpha));
        text(c,tr("screen.academy.wireless.connected"),leftPos+13,topPos+28,9,rgba(1,1,1,.8));
        var linked=data.getCompound("linked");boolean has=data.contains("linked",Tag.TAG_COMPOUND);
        row(c,linked,topPos+35,has,true,mx,my);
        text(c,tr("screen.academy.wireless.available"),leftPos+13,topPos+52,9,rgba(1,1,1,.8));
        var targets=candidates();scroll=Math.max(0,Math.min(scroll,Math.max(0,targets.size()-7)));
        for(int i=0;i<Math.min(7,targets.size()-scroll);i++)row(c,targets.get(scroll+i),topPos+60+i*16,true,false,mx,my);
        c.rect(tex("button/button_arrowupb"),leftPos+154,topPos+52,16,16,rgba(1,1,1,hit(mx,my,154,52,16,16)?1:.8));
        c.rect(tex("button/button_arrowdownb"),leftPos+154,topPos+161,16,16,rgba(1,1,1,hit(mx,my,154,161,16,16)?1:.8));
        text(c,tr("screen.academy.wireless.refresh"),leftPos+9,topPos+174,8,WHITE);
        if(menu.kind()==ClassicWirelessMenu.Kind.USER)text(c,tr("screen.academy.wireless.return"),leftPos+119,topPos+174,8,WHITE);
    }
    private void row(ClassicHudCanvas c,CompoundTag target,double y,boolean present,boolean linked,int mx,int my){
        c.rect(tex("element/element_background300x32"),leftPos+8,y,150,linked?18:16,WHITE);
        double a=present?1:.6;c.rect(tex("icons/icon_matrix"),leftPos+16,y+2,12,12,rgba(1,1,1,a));
        String name=present?target.getString("name"):tr("ac.gui.common.pg_wireless.not_connected");
        double limit=linked?99:target.getBoolean("encrypted")?37:99;
        name=ellipsize(name,limit,9);
        text(c,name,leftPos+28,y+4,9,rgba(.87,.87,.87,1));
        if(!linked&&target.getBoolean("encrypted")){
            c.rect(tex("icons/icon_key"),leftPos+68,y+2,12,12,rgba(1,1,1,.7));
            c.fill(leftPos+80,y+3.5,48,9,rgba(.133,.133,.133,.667),false);
        }
        c.rect(tex("icons/icon_"+(linked&&present?"connected":"unconnected")),leftPos+133,y+2,12,12,rgba(1,1,1,hit(mx,my,133,y-topPos,12,12)?1:present?.8:.6));
    }
    private void drawInfo(ClassicHudCanvas c,int mx,int my){
        double x=leftPos+179,y=topPos+5;int kind=menu.kind()==ClassicWirelessMenu.Kind.NODE?2:1;
        double height=menu.kind()==ClassicWirelessMenu.Kind.MATRIX?(data.getBoolean("configure")?(data.getBoolean("initialized")?171:151):(data.getBoolean("initialized")?142:126)):menu.kind()==ClassicWirelessMenu.Kind.NODE?(data.getBoolean("configure")?131:123):119;
        long now=ClassicWirelessClock.millis();double step=Math.min(500,Math.max(0,now-lastInfoTime))*.5;
        infoHeight+=Math.signum(height-infoHeight)*Math.min(step,Math.abs(height-infoHeight));lastInfoTime=now;
        infoAlpha=Math.max(0,Math.min(1,(now-openedAt-300)/300d));
        blend(c,x,y,100,infoHeight);textAlpha=infoAlpha;
        c.rect(tex("histogram"),x,y-20,84,84,rgba(1,1,1,infoAlpha));
        double amount=data.getDouble("energy"),maximum=data.getDouble("max_energy");
        if(menu.kind()==ClassicWirelessMenu.Kind.NODE||menu.kind()==ClassicWirelessMenu.Kind.USER){
            double fraction=ClassicWirelessVisualRules.histogramFraction(amount,maximum);
            c.fill(x+22.4,y+59.2-48*fraction,6.4,48*fraction,rgba(.145,.769,1,infoAlpha),false);
            property(c,x,y+64,tr("ac.gui.common.hist.energy"),String.format(Locale.ROOT,"%.0f IF",amount),rgba(.145,.769,1,1));
        }
        if(menu.kind()!=ClassicWirelessMenu.Kind.USER){
            double fraction=ClassicWirelessVisualRules.histogramFraction(data.getInt("load"),data.getInt("capacity"));
            double barX=menu.kind()==ClassicWirelessMenu.Kind.NODE?x+38.4:x+22.4;
            c.fill(barX,y+59.2-48*fraction,6.4,48*fraction,rgba(1,.424,0,infoAlpha),false);
            property(c,x,y+(kind==2?72:64),tr("ac.gui.common.hist.capacity"),data.getInt("load")+"/"+data.getInt("capacity"),rgba(1,.424,0,1));
        }
        text(c,tr("ac.gui.common.sep.info"),x+3,y+(kind==2?83:75),6,rgba(1,1,1,.6));
        if(menu.kind()==ClassicWirelessMenu.Kind.NODE){
            property(c,x,y+91,tr("ac.gui.common.prop.range"),format(data.getDouble("range")),WHITE);
            property(c,x,y+99,tr("ac.gui.common.prop.owner"),data.getString("owner"),WHITE);
            property(c,x,y+107,tr("ac.gui.common.prop.node_name"),data.getBoolean("configure")?"":data.getString("node_name"),WHITE);
            if(data.getBoolean("configure"))property(c,x,y+115,tr("ac.gui.common.prop.password"),"",WHITE);
        }else if(menu.kind()==ClassicWirelessMenu.Kind.MATRIX){
            property(c,x,y+83,tr("ac.gui.common.prop.owner"),data.getString("owner"),WHITE);
            property(c,x,y+91,tr("ac.gui.common.prop.range"),format(data.getDouble("range")),WHITE);
            property(c,x,y+99,tr("ac.gui.common.prop.bandwidth"),format(data.getDouble("bandwidth"))+" IF/T",WHITE);
            text(c,tr("ac.gui.common.sep."+(data.getBoolean("initialized")?"wireless_info":data.getBoolean("configure")?"wireless_init":"wireless_noinit")),x+3,y+110,6,rgba(1,1,1,.6));
            if(data.getBoolean("initialized")||data.getBoolean("configure"))property(c,x,y+118,tr("ac.gui.common.prop.ssid"),data.getBoolean("configure")?"":data.getString("ssid"),WHITE);
            if(data.getBoolean("configure")){
                if(data.getBoolean("initialized"))text(c,tr("ac.gui.common.sep.change_pass"),x+3,y+129,6,rgba(1,1,1,.6));
                property(c,x,y+(data.getBoolean("initialized")?137:126),tr("ac.gui.common.prop.password"),"",WHITE);
                text(c,tr(data.getBoolean("initialized")?"screen.academy.wireless.reset":"screen.academy.wireless.init"),x+28,y+(data.getBoolean("initialized")?155:135),9,WHITE);
            }
        }else{
            property(c,x,y+83,tr("ac.gui.common.prop.bandwidth"),format(data.getDouble("bandwidth"))+" IF/T",WHITE);
            String hostTitle=data.contains("title_key",Tag.TAG_STRING)?tr(data.getString("title_key")):data.getString("title");
            text(c,ellipsize(hostTitle,88,8),x+6,y+101,8,WHITE);
        }
        for(var entry:fields.entrySet())if(!entry.getKey().startsWith("target:")){var box=entry.getValue().box();text(c,"[",box.getX()-4,box.getY(),8,WHITE);text(c,"]",box.getX()+box.getWidth()+2,box.getY(),8,WHITE);}
        textAlpha=1;
        String status=lastActionStatus.isEmpty()?data.getString("status"):lastActionStatus;
        if(sentAt>=0&&ClassicWirelessClock.millis()-sentAt>3000&&status.isEmpty())status="waiting";
        if(!status.isEmpty()){
            String key="screen.academy.wireless.status."+status;String text=tr(key);if(text.equals(key))text=status.replace('_',' ');
            text(c,text,leftPos+8,topPos+198,8,rgba(1,.8,.5,1));
        }
    }
    private void property(ClassicHudCanvas c,double x,double y,String key,String value,ClassicHudTimeline.Rgba color){
        text(c,key,x+6,y,8,WHITE);text(c,ellipsize(value,48,8),x+46,y,8,color);
    }
    private void blend(ClassicHudCanvas c,double x,double y,double w,double h){
        double[] xs={x-4,x,x+w,x+w+4},ys={y-4,y,y+h,y+h+4};
        for(int col=0;col<3;col++)for(int row=0;row<3;row++)c.rect(tex("blend_quad"),xs[col],ys[row],xs[col+1]-xs[col],ys[row+1]-ys[row],col/3d,row/3d,(col+1)/3d,(row+1)/3d,rgba(0,0,0,.5),null,null);
        c.rect(tex("line"),x-3.2,y-8.6,w+6.4,12,WHITE);c.rect(tex("line"),x-3.2,y+h-2,w+6.4,8,WHITE);
    }
    private String ellipsize(String value,double width,double size){
        if(sourceFont.width(value,size,false)<=width)return value;
        String shortened=value;while(!shortened.isEmpty()&&sourceFont.width(shortened+"…",size,false)>width)shortened=shortened.substring(0,shortened.offsetByCodePoints(shortened.length(),-1));
        return shortened+"…";
    }
    private void text(ClassicHudCanvas c,String value,double x,double y,double size,ClassicHudTimeline.Rgba color){sourceFont.draw(c,value,x,y,size,rgba(color.r(),color.g(),color.b(),color.a()*textAlpha),false,false);}
    private static String tr(String key){return Component.translatable(key).getString();}
    private static String format(double value){return String.format(Locale.ROOT,"%.0f",Double.isFinite(value)?value:0);}
    private static net.minecraft.resources.ResourceLocation tex(String path){return ClassicHudCanvas.texture("guis/"+path);}
    private static ClassicHudTimeline.Rgba rgba(double r,double g,double b,double a){return new ClassicHudTimeline.Rgba(r,g,b,a);}
    private boolean hit(double mx,double my,double x,double y,double w,double h){return mx>=leftPos+x&&mx<leftPos+x+w&&my>=topPos+y&&my<topPos+y+h;}
    @Override protected void renderLabels(GuiGraphics graphics,int mx,int my) {}
    @Override protected void renderSlot(GuiGraphics graphics,Slot slot){if(!wireless)super.renderSlot(graphics,slot);}
    @Override protected boolean isHovering(int x,int y,int w,int h,double mx,double my){return !wireless&&super.isHovering(x,y,w,h,mx,my);}
    @Override protected boolean hasClickedOutside(double mx,double my,int left,int top,int button){return !hit(mx,my,-23,-25,310,220);}
    @Override public void render(GuiGraphics graphics,int mx,int my,float partial){super.render(graphics,mx,my,partial);if(!wireless)renderTooltip(graphics,mx,my);}
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button==0){
            if(hit(mx,my,-20,0,17,17)){if(menu.kind()==ClassicWirelessMenu.Kind.USER){request("return",menu.sourcePos(),"","");return true;}wireless=false;fieldSignature="";updateData();return true;}
            if(menu.kind()!=ClassicWirelessMenu.Kind.MATRIX&&hit(mx,my,-20,22,17,17)){wireless=true;fieldSignature="";updateData();request("refresh",menu.sourcePos(),"","");return true;}
            for(var field:fields.values())if(field.box().isMouseOver(mx,my)){for(var other:fields.values())other.box().setFocused(false);setFocused(field.box());field.box().setFocused(true);return field.box().mouseClicked(mx,my,button);}
            for(var field:fields.values())field.box().setFocused(false);setFocused(null);
            if(menu.kind()==ClassicWirelessMenu.Kind.MATRIX&&data.getBoolean("configure")&&hit(mx,my,207,data.getBoolean("initialized")?160:140,66,15)){
                if(data.getBoolean("initialized"))request("matrix_reset",menu.sourcePos(),"","");
                else{Field ssid=fields.get("ssid"),password=fields.get("password");if(ssid!=null&&password!=null){request("matrix_init",menu.sourcePos(),ssid.box().getValue(),password.box().getValue());password.box().setValue("");}}
                return true;
            }
            if(wireless){
                if(hit(mx,my,154,52,16,16)){scroll=Math.max(0,scroll-1);fieldSignature="";updateData();return true;}
                if(hit(mx,my,154,161,16,16)){scroll=Math.min(Math.max(0,candidates().size()-7),scroll+1);fieldSignature="";updateData();return true;}
                if(hit(mx,my,133,37,12,12)&&data.contains("linked",Tag.TAG_COMPOUND)){request("unlink",menu.sourcePos(),"","");return true;}
                var targets=candidates();for(int i=0;i<Math.min(7,targets.size()-scroll);i++)if(hit(mx,my,133,62+i*16,12,12)){
                    var target=targets.get(scroll+i);var field=fields.get("target:"+target.getLong("pos"));
                    request("link",BlockPos.of(target.getLong("pos")),"",field==null?"":field.box().getValue());if(field!=null)field.box().setValue("");return true;
                }
                if(hit(mx,my,8,171,61,15)){request("refresh",menu.sourcePos(),"","");return true;}
                if(menu.kind()==ClassicWirelessMenu.Kind.USER&&hit(mx,my,115,171,60,15)){request("return",menu.sourcePos(),"","");return true;}
                return true;
            }
            // Clicking the info sidebar never drops or shift-moves a carried stack.
            if(hit(mx,my,176,-10,110,210))return true;
        }
        return wireless||super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseReleased(double mx,double my,int button){return wireless||super.mouseReleased(mx,my,button);}
    @Override public boolean mouseDragged(double mx,double my,int button,double dx,double dy){return wireless||super.mouseDragged(mx,my,button,dx,dy);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(wireless&&hit(mx,my,0,48,176,122)){scroll=Math.max(0,Math.min(Math.max(0,candidates().size()-7),scroll+(vertical<0?1:vertical>0?-1:0)));fieldSignature="";updateData();return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE){if(wireless&&menu.kind()==ClassicWirelessMenu.Kind.USER){if(submitted.equals("return")){onClose();}else request("return",menu.sourcePos(),"","");return true;}if(wireless){wireless=false;fieldSignature="";updateData();return true;}onClose();return true;}
        for(var field:fields.values())if(field.box().isFocused()){
            if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){submit(field);return true;}
            if(field.box().keyPressed(key,scan,modifiers))return true;
            // Inventory/drop/hotbar shortcuts must not leak while typing a name or password.
            return true;
        }
        if(wireless&&minecraft.options.keyInventory.matches(key,scan)){onClose();return true;}
        return wireless||super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean charTyped(char value,int modifiers){for(var field:fields.values())if(field.box().isFocused())return field.box().charTyped(value,modifiers);return wireless||super.charTyped(value,modifiers);}
    @Override public void removed(){super.removed();sourceFont.release();fields.values().stream().filter(Field::secret).forEach(f->f.box().setValue(""));fields.clear();}
}
