/* AcademyCraft1.0.7 five Forge achievement pages, modern Screen adaptation. GPLv3. */
package cn.academy.port.client.achievements;
import cn.academy.port.achievements.ClassicAchievementCatalog;
import cn.academy.port.achievements.ClassicAchievementCatalog.Entry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
/** Original 24-pixel achievement grid, 22px frames, 256x202 frame and five page cycle. */
public final class ClassicAchievementScreen extends Screen {
    public static final int FRAME_WIDTH=256,FRAME_HEIGHT=202,GRID=24,VIEW_WIDTH=224,VIEW_HEIGHT=155;
    private final Screen parent;private int page;private double panX,panY,zoom=1;private Button pageButton;
    public ClassicAchievementScreen(Screen parent){super(Component.translatable("ac.achievement.title"));this.parent=parent;}
    public String page(){return ClassicAchievementCatalog.PAGES.get(page);}
    private Component pageName(){String key=ClassicAchievementCatalog.pageTitle(page());return page==0?Component.literal(key):Component.translatable(key);}
    @Override protected void init(){
        int x=(width-FRAME_WIDTH)/2,y=(height-FRAME_HEIGHT)/2;
        pageButton=addRenderableWidget(Button.builder(pageName(),b->{page=(page+1)%5;panX=panY=0;zoom=1;b.setMessage(pageName());}).bounds(x+16,y+176,164,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x+184,y+176,56,20).build());
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        renderBackground(g,mx,my,partial);int x=(width-FRAME_WIDTH)/2,y=(height-FRAME_HEIGHT)/2,vx=x+16,vy=y+17;
        g.fill(x,y,x+256,y+202,0xff131313);g.fill(x+1,y+1,x+255,y+201,0xffa0a0a0);g.fill(x+3,y+3,x+253,y+199,0xffc6c6c6);
        g.drawString(font,title,x+15,y+6,0xff404040,false);
        g.enableScissor(vx,vy,vx+VIEW_WIDTH,vy+VIEW_HEIGHT);g.fill(vx,vy,vx+VIEW_WIDTH,vy+VIEW_HEIGHT,0xff222222);
        g.pose().pushPose();g.pose().translate(vx+VIEW_WIDTH/2.0,vy+VIEW_HEIGHT/2.0,0);g.pose().scale((float)zoom,(float)zoom,1);g.pose().translate(panX,panY,0);
        background(g);var entries=ClassicAchievementCatalog.page(page());
        for(var e:entries){var p=ClassicAchievementCatalog.get(e.parent());if(p==null)continue;
            int color=ClassicAchievementClient.earned(e.id())?0xffa0a0a0:ClassicAchievementClient.earned(p.id())?0xff00ff00:0xff000000;
            int ax=e.x()*GRID+11,ay=e.y()*GRID+11,bx=p.x()*GRID+11,by=p.y()*GRID+11;
            g.fill(Math.min(ax,bx),ay,Math.max(ax,bx)+1,ay+1,color);g.fill(bx,Math.min(ay,by),bx+1,Math.max(ay,by)+1,color);
            int sign=ay<by?-1:1;g.fill(bx-2,by+sign*9,bx+3,by+sign*9+1,color);g.fill(bx-1,by+sign*8,bx+2,by+sign*8+1,color);
        }
        Entry hovered=null;
        double localX=(mx-vx-VIEW_WIDTH/2.0)/zoom-panX,localY=(my-vy-VIEW_HEIGHT/2.0)/zoom-panY;
        for(var e:entries){int px=e.x()*GRID,py=e.y()*GRID;boolean won=ClassicAchievementClient.earned(e.id()),ready=e.parent()==null||ClassicAchievementClient.earned(e.parent());
            int distance=ClassicAchievementCatalog.distance(e,ClassicAchievementClient::earned);if(distance>5)continue;
            ClassicAchievementVisual.frame(g,px,py,won,ready);ClassicAchievementVisual.icon(g,e,px+3,py+3);
            if(!won&&!ready)g.fill(px+3,py+3,px+19,py+19,distance>3?0xe0000000:0x90000000);
            if(mx>=vx&&mx<vx+VIEW_WIDTH&&my>=vy&&my<vy+VIEW_HEIGHT&&localX>=px&&localX<px+22&&localY>=py&&localY<py+22)hovered=e;
        }
        g.pose().popPose();g.disableScissor();
        // Screen.render in 1.21 redraws/blur-processes its background. This page
        // already drew that background; render its registered buttons once.
        for(var widget:renderables)widget.render(g,mx,my,partial);
        if(hovered!=null){boolean won=ClassicAchievementClient.earned(hovered.id()),ready=hovered.parent()==null||ClassicAchievementClient.earned(hovered.parent());
            var lines=new ArrayList<Component>();int distance=ClassicAchievementCatalog.distance(hovered,ClassicAchievementClient::earned);
            lines.add(Component.translatable(distance<=3||ready||won?hovered.title():"ac.achievement.unknown"));
            if(ready||won)lines.add(Component.translatable(hovered.description()));
            else if(distance<=3)lines.add(Component.translatable("ac.achievement.requires",Component.translatable(ClassicAchievementCatalog.get(hovered.parent()).title())));
            if(won)lines.add(Component.translatable("ac.achievement.taken"));
            g.renderTooltip(font,lines,Optional.empty(),mx,my);
        }
    }
    /** Forge 1.7 pages inherit vanilla's cave motif. Use the host game's block textures without bundling Mojang assets. */
    private void background(GuiGraphics g){
        int minX=(int)Math.floor((-VIEW_WIDTH/2.0/zoom-panX)/16)-1,minY=(int)Math.floor((-VIEW_HEIGHT/2.0/zoom-panY)/16)-1;
        int columns=(int)Math.ceil(VIEW_WIDTH/zoom/16)+3,rows=(int)Math.ceil(VIEW_HEIGHT/zoom/16)+3;
        for(int c=minX;c<minX+columns;c++)for(int r=minY;r<minY+rows;r++){
            var random=new Random(1234L+c+r*31L);int depth=random.nextInt(Math.max(1,r+24))+Math.max(0,r)/2;
            String block=depth>37?"bedrock":depth==35?"diamond_ore":depth==22?"iron_ore":depth==10?"dirt":random.nextInt(2)==0?"stone":"cobblestone";
            var texture=ResourceLocation.fromNamespaceAndPath("minecraft","textures/block/"+block+".png");g.blit(texture,c*16,r*16,0,0,16,16,16,16);g.fill(c*16,r*16,c*16+16,r*16+16,0x70000000);
        }
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        int vx=(width-FRAME_WIDTH)/2+16,vy=(height-FRAME_HEIGHT)/2+17;
        if(button==0&&x>=vx&&x<vx+VIEW_WIDTH&&y>=vy&&y<vy+VIEW_HEIGHT){panX=Math.max(-320,Math.min(320,panX+dx/zoom));panY=Math.max(-240,Math.min(240,panY+dy/zoom));return true;}
        return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){zoom=Math.max(.5,Math.min(2,zoom+vertical*.1));return true;}
}
