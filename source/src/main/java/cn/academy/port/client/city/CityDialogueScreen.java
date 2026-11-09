package cn.academy.port.client.city;

import cn.academy.port.city.CityDialogueNetwork;
import cn.academy.port.city.CityEspers;
import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.client.ClassicTerminalDrawing;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Original terminal typography and assets, live NPC portrait, authored topic textures and responsive transitions. */
public final class CityDialogueScreen extends Screen {
    private static final int W=900,H=470,INK=0xe4edf0,MUTED=0x8daab8,CYAN=0x9addea,GOLD=0xd8bd80;
    private static final int CHOICE_X=562,CHOICE_Y=104,CHOICE_W=310,CHOICE_H=42,ROWS=6;
    private final ClassicTerminalDrawing drawing=new ClassicTerminalDrawing();
    private final List<TopicButton> topics=new ArrayList<>();
    private CityDialogueNetwork.View view;
    private List<String> lines=List.of();
    private double scale,left,top;
    private int page,textScroll,heartbeat;
    private boolean serverClosing;
    private boolean hudSaved,previousHideGui;
    private final long opened=Util.getMillis();
    private long bodyChanged=opened,lastFrame=opened;
    public CityDialogueScreen(CityDialogueNetwork.View view) {super(view.title());this.view=view;}
    public CityDialogueNetwork.View view() {return view;}
    public void accept(CityDialogueNetwork.View next) {
        view=next;page=0;textScroll=0;bodyChanged=Util.getMillis();rebuildWidgets();
    }
    public void select(String id) {
        if(view.options().stream().anyMatch(o -> o.id().equals(id)&&o.enabled())) send(id);
    }
    private void send(String action) {PacketDistributor.sendToServer(new CityDialogueNetwork.Choice(view.npc(),view.token(),action));}
    private static String tr(String key,Object... args) {return Component.translatable("academy.city."+key,args).getString();}
    private int x(double value) {return (int)Math.round(left+value*scale);}
    private int y(double value) {return (int)Math.round(top+value*scale);}
    @Override protected void init() {
        if(!hudSaved) {previousHideGui=minecraft.options.hideGui;hudSaved=true;}
        minecraft.options.hideGui=true;
        drawing.prepare();scale=Math.min((width-16d)/W,(height-16d)/H);
        left=(width-W*scale)/2;top=(height-H*scale)/2;
        lines=wrap(view.body().getString(),296,21);
        topics.clear();page=Math.min(page,Math.max(0,(view.options().size()-1)/ROWS));
        for(int row=0;row<ROWS;row++) {
            int index=page*ROWS+row;if(index>=view.options().size()) break;
            var o=view.options().get(index);
            addTopic(o.label(),o.id(),CHOICE_X,CHOICE_Y+row*49,CHOICE_W,CHOICE_H,o.enabled(),b -> select(o.id()));
        }
        int pages=Math.max(1,(view.options().size()+ROWS-1)/ROWS);
        if(pages>1) {
            addTopic(Component.literal("‹"),"previous",CHOICE_X,403,34,25,page>0,b -> {page--;rebuildWidgets();});
            addTopic(Component.literal("›"),"next",CHOICE_X+CHOICE_W-34,403,34,25,page<pages-1,b -> {page++;rebuildWidgets();});
        }
        addTopic(Component.translatable("academy.city.dialogue.leave"),"leave",CHOICE_X,435,CHOICE_W,25,true,b -> onClose());
    }
    private void addTopic(Component text,String id,int vx,int vy,int vw,int vh,boolean enabled,Button.OnPress action) {
        var b=new TopicButton(text,id,vx,vy,vw,vh,action);b.active=enabled;topics.add(b);addRenderableWidget(b);
    }
    private final class TopicButton extends Button {
        final String id;final int vx,vy,vw,vh;double hover;
        TopicButton(Component text,String id,int vx,int vy,int vw,int vh,OnPress action) {
            super(x(vx),y(vy),Math.max(1,x(vx+vw)-x(vx)),Math.max(1,y(vy+vh)-y(vy)),text,action,DEFAULT_NARRATION);
            this.id=id;this.vx=vx;this.vy=vy;this.vw=vw;this.vh=vh;
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) {
            isHovered=mx>=getX()&&mx<getX()+getWidth()&&my>=getY()&&my<getY()+getHeight();
        }
    }
    @Override public void tick() {
        if(++heartbeat%40==0) send("keep");
        if(minecraft.player==null||minecraft.level==null) {onClose();return;}
        var npc=minecraft.level.getEntity(view.entity());
        if(npc==null||!npc.getUUID().equals(view.npc())||npc.distanceToSqr(minecraft.player)>64) onClose();
    }
    private void start(GuiGraphics g) {
        g.pose().pushPose();g.pose().translate(left,top,0);g.pose().scale((float)scale,(float)scale,1);drawing.begin(g);
    }
    private void end(GuiGraphics g) {drawing.end();g.pose().popPose();}
    @Override public void render(GuiGraphics g,int mx,int my,float partial) {
        long now=Util.getMillis();double dt=Math.min(80,Math.max(0,now-lastFrame));lastFrame=now;
        double reveal=1-Math.pow(1-Math.min(1,(now-opened)/220d),3);
        double mouseX=(mx-left)/scale,mouseY=(my-top)/scale;
        g.fill(0,0,width,height,0x3502080d);
        start(g);
        try {
            drawing.fill(0,0,W,H,0x0b141c,.90*reveal);
            drawing.fill(0,0,190,H,0x14232e,.58*reveal);
            drawing.texture("guis/city_dialogue/glass",0,0,W,H,.22*reveal);
            drawing.fill(210,98,330,315,0x070e14,.42);
            rule(0,0,W*reveal,1,CYAN,.65);rule(0,H-1,W,1,MUTED,.32);rule(190,0,1,H,MUTED,.22);
            rule(0,0,5,38,CYAN,.76);rule(W-38,H-2,38,2,CYAN,.48);
            drawing.texture("guis/data_terminal/logo",23,22,31,31,.85);
            drawing.text(tr("dialogue.channel"),64,24,22,INK,.9,0);
            drawing.text(view.title().getString(),221,20,28,INK,1,0);
            drawing.text(topic(),222,58,16,MUTED,1,0);
            drawing.text(tr("dialogue.wallet",view.coins()),872,22,22,GOLD,1,2);
            drawing.text(tr("dialogue.district",view.district()),872,58,16,MUTED,1,2);
            rule(210,86,662,1,MUTED,.30);
            corner(20,88,150,244);
            drawing.texture("guis/city_dialogue/reticle",24,92,142,236,.28);
            drawing.text(tr("dialogue.face_to_face"),95,69,15,MUTED,.9,1);
            drawing.text(tr("dialogue.profile"),24,352,15,MUTED,1,0);
            drawing.text(tr("dialogue.topics"),CHOICE_X,82,15,MUTED,1,0);
            int visible=10;textScroll=Math.min(textScroll,Math.max(0,lines.size()-visible));
            g.enableScissor(x(222),y(108),x(529),y(408));
            for(int i=0;i<visible&&i+textScroll<lines.size();i++) {
                double alpha=Math.max(0,Math.min(1,(now-bodyChanged-i*12)/130d));
                drawing.text(lines.get(i+textScroll),223,112+i*28,21,INK,alpha,0);
            }
            g.disableScissor();
            if(lines.size()>visible) {
                drawing.fill(532,112,2,280,MUTED,.15);
                double handle=280d*visible/lines.size();
                drawing.fill(532,112+(280-handle)*textScroll/Math.max(1,lines.size()-visible),2,handle,CYAN,.65);
                drawing.text(tr("dialogue.scroll"),222,431,15,MUTED,.85,0);
            }
            int pages=Math.max(1,(view.options().size()+ROWS-1)/ROWS);
            if(pages>1) drawing.text((page+1)+" / "+pages,CHOICE_X+CHOICE_W/2d,406,17,MUTED,1,1);
            for(var b:topics) renderTopic(b,mouseX,mouseY,dt);
        } finally {end(g);}
        if(minecraft.level!=null&&minecraft.level.getEntity(view.entity()) instanceof CityNpcEntity npc) {
            InventoryScreen.renderEntityInInventoryFollowsAngle(g,x(22),y(91),x(167),y(327),Math.max(1,(int)(103*scale)),.02f,-.08f,0,npc);
            start(g);
            try {
                if(npc.esper()!=CityEspers.Profile.NONE) {
                    drawing.texture(abilityIcon(npc.esper()),24,376,28,28,.8);
                    drawing.text("Lv "+npc.esperLevel(),63,378,20,CYAN,1,0);
                    var label=wrap(npc.esper().label().getString(),143,17);
                    for(int i=0;i<Math.min(2,label.size());i++) drawing.text(label.get(i),24,407+i*22,17,INK,.9,0);
                } else {
                    drawing.text(tr("dialogue.citizen"),24,380,21,INK,.95,0);
                    drawing.text(tr("dialogue.service_status"),24,412,16,MUTED,.95,0);
                }
            } finally {end(g);}
        }
        super.render(g,mx,my,partial);
    }
    private void rule(double x,double y,double w,double h,int color,double alpha) {drawing.fill(x,y,w,h,color,alpha);}
    private void corner(int x,int y,int w,int h) {
        for(int side=0;side<2;side++)for(int edge=0;edge<2;edge++) {
            drawing.fill(x+side*(w-15),y+edge*(h-1),15,1,CYAN,.47);
            drawing.fill(x+side*(w-1),y+edge*(h-15),1,15,CYAN,.47);
        }
    }
    private void renderTopic(TopicButton b,double mx,double my,double dt) {
        boolean over=b.active&&(b.isFocused()||mx>=b.vx&&mx<b.vx+b.vw&&my>=b.vy&&my<b.vy+b.vh);
        b.hover+=( (over?1:0)-b.hover)*Math.min(1,dt/95d);
        boolean utility=b.id.equals("leave")||b.id.equals("previous")||b.id.equals("next");
        int accent=b.id.equals("heal")?0xa5dbca:b.id.equals("repair")||b.id.equals("trade")?GOLD:CYAN;
        double alpha=b.active?1:.36;
        drawing.fill(b.vx,b.vy,b.vw,b.vh,0x1b2b36,(utility?.22:.50)*alpha);
        drawing.fill(b.vx,b.vy,b.vw,b.vh,0x335566,b.hover*.66*alpha);
        drawing.fill(b.vx,b.vy+b.vh-1,b.vw,1,MUTED,.26*alpha);
        if(b.hover>.01) {
            drawing.fill(b.vx,b.vy,2,b.vh,accent,.95*b.hover);
            drawing.fill(b.vx,b.vy+b.vh-1,b.vw*b.hover,1,accent,.82*b.hover);
            drawing.glow(b.vx,b.vy,b.vw,b.vh,3,accent,.10*b.hover);
        }
        if(utility) {
            drawing.text(b.getMessage().getString(),b.vx+b.vw/2d,b.vy+2,18,over?INK:MUTED,alpha,1);return;
        }
        drawing.texture(topicIcon(b.id),b.vx+11,b.vy+8,25,25,.78*alpha);
        String text=b.getMessage().getString();double size=19;
        while(size>15&&drawing.width(text,size)>b.vw-56) size--;
        drawing.text(text,b.vx+45+2*b.hover,b.vy+(b.vh-size)/2-1,size,over?INK:0xc8d8df,alpha,0);
    }
    private String topic() {
        if(view.options().stream().anyMatch(o -> o.id().equals("demo"))) return tr("dialogue.esper");
        if(view.options().stream().anyMatch(o -> o.id().equals("guide_awaken"))) return tr("dialogue.guide");
        if(view.options().stream().anyMatch(o -> o.id().startsWith("accept.")||o.id().startsWith("finish."))) return tr("dialogue.task_details");
        if(view.body().getContents() instanceof TranslatableContents t&&t.getKey().equals("academy.city.quest.journal")) return tr("dialogue.journal");
        if(view.options().stream().anyMatch(o -> o.id().startsWith("detail."))) return tr("dialogue.tasks");
        return tr("dialogue.face_to_face");
    }
    private String topicIcon(String id) {
        if(id.equals("esper")||id.equals("demo")) {
            if(minecraft.level!=null&&minecraft.level.getEntity(view.entity()) instanceof CityNpcEntity npc) return abilityIcon(npc.esper());
            return "guis/apps/skill_tree/icon";
        }
        String icon=id.equals("heal")?"heal":id.equals("repair")?"repair":id.equals("trade")?"trade":
                id.equals("local")?"local":id.equals("talk")?"talk":id.equals("main")?"back":
                id.equals("guide")||id.startsWith("guide_")?"guide":id.equals("journal")?"journal":
                id.startsWith("accept.")||id.startsWith("finish.")?"check":"tasks";
        return "guis/city_dialogue/topic_"+icon;
    }
    private static String abilityIcon(CityEspers.Profile p) {
        return "guis/icons/icon_"+switch(p) {
            case ELECTRO -> "electromaster";case MELT -> "meltdowner";case TELEPORT -> "teleporter";
            case VECTOR -> "vecmanip";case MENTAL -> "mental_out";case AIR -> "aero_hand";
            case FLOAT -> "float_dial";case NITROGEN -> "nitrogen_armor";case DARK -> "dark_matter";
            case WATER -> "hydro_hand";case PAPER -> "paper_psychokinesis";case SHADOW -> "liquid_shadow";
            case NONE -> "location_on";
        };
    }
    private List<String> wrap(String text,double width,double size) {
        var result=new ArrayList<String>();
        for(String paragraph:text.split("\\n",-1)) {
            var iterator=BreakIterator.getLineInstance(Locale.SIMPLIFIED_CHINESE);iterator.setText(paragraph);
            var line=new StringBuilder();int begin=iterator.first();
            for(int end=iterator.next();end!=BreakIterator.DONE;begin=end,end=iterator.next()) {
                String word=paragraph.substring(begin,end);
                if(drawing.width(line+word,size)>width&&!line.isEmpty()) {result.add(line.toString().stripTrailing());line.setLength(0);}
                if(drawing.width(word,size)>width) {
                    for(int cp:word.codePoints().toArray()) {
                        String letter=new String(Character.toChars(cp));
                        if(drawing.width(line+letter,size)>width&&!line.isEmpty()) {result.add(line.toString());line.setLength(0);}
                        line.append(letter);
                    }
                } else line.append(line.isEmpty()?word.stripLeading():word);
            }
            result.add(line.toString().stripTrailing());
        }
        return List.copyOf(result);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy) {
        if((x-left)/scale<CHOICE_X) {textScroll=Math.max(0,Math.min(Math.max(0,lines.size()-10),textScroll-(int)Math.signum(dy)*3));return true;}
        int pages=Math.max(1,(view.options().size()+ROWS-1)/ROWS);
        page=Math.max(0,Math.min(pages-1,page-(int)Math.signum(dy)));rebuildWidgets();return true;
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial) {}
    @Override public boolean isPauseScreen() {return false;}
    @Override public void removed() {
        drawing.release();if(hudSaved) minecraft.options.hideGui=previousHideGui;
        if(!serverClosing) send("close");
    }
    private void closeFromServer() {serverClosing=true;Minecraft.getInstance().setScreen(null);}
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Client {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
            CityDialogueNetwork.clientReceiver=packet -> {
                var mc=Minecraft.getInstance();
                if(packet.entity()<0) {
                    if(mc.screen instanceof CityDialogueScreen s&&s.view.token()==packet.token()) s.closeFromServer();
                } else if(mc.player!=null) {
                    if(mc.screen instanceof CityDialogueScreen s&&s.view.token()==packet.token()) s.accept(packet);
                    else mc.setScreen(new CityDialogueScreen(packet));
                }
            };
        }
    }
}
