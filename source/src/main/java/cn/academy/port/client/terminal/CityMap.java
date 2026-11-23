package cn.academy.port.client.terminal;

import cn.academy.port.client.ClassicTerminalDrawing;
import cn.academy.port.client.HighPowerRecoveryHud;
import cn.academy.port.client.TerminalSkin;
import com.mojang.math.Axis;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Both views draw one north-up, client-owned surface texture. Coordinates are aligned to two-block cells. */
final class CityMap {
    private static final int N=48, STEP=2, BUDGET=192, UNKNOWN=0xff292d30, UNSEEN=0;
    private static int[] cells=new int[N*N], scratch=new int[N*N];
    private static final int[] pending=new int[N*N];
    private static final BlockPos.MutableBlockPos probe=new BlockPos.MutableBlockPos();
    private static int originX,originZ,cursor,pendingCount;
    private static Level world;
    private static DynamicTexture texture;
    private static ResourceLocation id;
    private static boolean enabled,dirty;
    private CityMap() {}

    static void open() { enabled=true; Minecraft.getInstance().setScreen(new MapScreen()); }
    static void clear() {
        enabled=false; world=null;
        if (id!=null) Minecraft.getInstance().getTextureManager().release(id);
        texture=null; id=null; pendingCount=0;
    }
    private static void center(int px,int pz,boolean reset) {
        int x=Math.floorDiv(px,STEP)*STEP-N,z=Math.floorDiv(pz,STEP)*STEP-N;
        int dx=(x-originX)/STEP,dz=(z-originZ)/STEP;
        Arrays.fill(scratch,UNSEEN);
        if(!reset && Math.abs(dx)<N && Math.abs(dz)<N) {
            int from=Math.max(0,-dx), span=N-Math.abs(dx);
            for(int row=Math.max(0,-dz);row<Math.min(N,N-dz);row++)
                System.arraycopy(cells,(row+dz)*N+from+dx,scratch,row*N+from,span);
        }
        int[] swap=cells;cells=scratch;scratch=swap;
        originX=x;originZ=z;cursor=0;pendingCount=0;dirty=true;
        for(int i=cells.length-1;i>=0;i--) if(cells[i]==UNSEEN) pending[pendingCount++]=i;
    }
    static void tick(Minecraft mc) {
        if(!enabled || mc.level==null || mc.player==null || mc.isPaused()) return;
        int px=mc.player.getBlockX(),pz=mc.player.getBlockZ();
        if(world!=mc.level || Math.abs(px-(originX+N))>20 || Math.abs(pz-(originZ+N))>20) {
            center(px,pz,world!=mc.level); world=mc.level;
        }
        for(int i=0;i<BUDGET;i++) {
            int cell;
            if(pendingCount>0) cell=pending[--pendingCount];
            else {cell=cursor;cursor=(cursor+1)%cells.length;}
            int color=sample(originX+cell%N*STEP,originZ+cell/N*STEP);
            if(cells[cell]!=color){cells[cell]=color;dirty=true;}
        }
    }
    private static int sample(int x,int z) {
        if(!world.hasChunk(x>>4,z>>4)) return UNKNOWN;
        int y=world.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
        if(y<world.getMinBuildHeight()) return UNKNOWN;
        probe.set(x,y,z);
        var state=world.getBlockState(probe);
        var key=BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String name=key.getPath();
        if(!state.getFluidState().isEmpty()) return 0xff587e8d;
        if(name.contains("line") && key.getNamespace().equals("academy")) return 0xffd0c9aa;
        if(name.contains("road")||name.contains("asphalt")) return 0xff505259;
        if(name.contains("grass")||name.contains("leaves")||name.contains("moss")) return 0xff859579;
        if(key.getNamespace().equals("academy")) {
            if(name.contains("floor")||name.contains("plaza")||name.contains("pavement")) return 0xffa8a69c;
            if(name.contains("glass")||name.contains("window")) return 0xff8fa8ab;
            return 0xff95918c;
        }
        int raw=state.getMapColor(world,probe).col;
        if(raw==0) return state.isAir()?UNKNOWN:0xff777c7d;
        int gray=((raw>>16&255)*3+(raw>>8&255)*6+(raw&255))/10;
        return 0xff000000|(((raw>>16&255)+gray+72)/3<<16)|(((raw>>8&255)+gray+76)/3<<8)|((raw&255)+gray+77)/3;
    }
    private static void drawMap(GuiGraphics g,Minecraft mc,int x,int y,int size,float alpha) {
        if(world!=mc.level || alpha<=0)return;
        if(texture==null) {
            texture=new DynamicTexture(N,N,false);
            id=mc.getTextureManager().register("academy_city_map",texture);
            dirty=true;
        }
        if(dirty) {
            var pixels=texture.getPixels();
            if(pixels==null)return;
            for(int i=0;i<cells.length;i++) {
                int argb=cells[i]==UNSEEN?UNKNOWN:cells[i];
                pixels.setPixelRGBA(i%N,i/N,0xff000000|((argb&255)<<16)|(argb&0xff00)|((argb>>16)&255));
            }
            texture.upload();dirty=false;
        }
        g.setColor(1,1,1,alpha);
        g.blit(id,x,y,size,size,0,0,N,N,N,N);
        g.setColor(1,1,1,1);
        float mx=(float)((mc.player.getX()-originX)*size/(N*STEP)),mz=(float)((mc.player.getZ()-originZ)*size/(N*STEP));
        int n=size>64?12:8;
        if(mx<n/2f||mx>size-n/2f||mz<n/2f||mz>size-n/2f)return;
        g.pose().pushPose();
        g.pose().translate(x+mx,y+mz,1);
        g.pose().mulPose(Axis.ZP.rotationDegrees(mc.player.getYRot()+180));
        TerminalSkin.draw(g,TerminalSkin.ARROW,-n/2,-n/2,n,n,(Math.round(255*alpha)<<24)|0xffffff);
        g.pose().popPose();
    }
    static void renderHud(GuiGraphics g,Minecraft mc) {
        if(!enabled||mc.player==null||mc.level!=world||mc.options.hideGui||mc.screen!=null||mc.getDebugOverlay().showDebugScreen())return;
        String xText="X "+mc.player.getBlockX(),zText="Z "+mc.player.getBlockZ();
        int w=Math.max(58,10+Math.max(mc.font.width(xText),mc.font.width(zText))),x=6,y=55,h=85;
        if(g.guiWidth()<w+12||g.guiHeight()<y+h+27)return;
        var box=HighPowerRecoveryHud.relayReservedBox(g.guiWidth(),g.guiHeight());
        if(box!=null && x<box.x()+box.width() && x+w>box.x() && y<box.y()+box.height() && y+h>box.y())return;
        TerminalSkin.draw(g,TerminalSkin.FRAME,x,y,w,h,-1);
        g.drawString(mc.font,"N",x+(w-mc.font.width("N"))/2,y+3,0xffebece8,false);
        drawMap(g,mc,x+(w-N)/2,y+13,N,1);
        g.drawString(mc.font,xText,x+5,y+64,0xffe1e3df,false);
        g.drawString(mc.font,zText,x+5,y+74,0xffc0c4c4,false);
    }
    private static final class MapScreen extends Screen {
        private final ClassicTerminalDrawing drawing=TerminalSkin.FONT;
        private final long opened=TerminalClient.gameTime();
        private static final int W=255,H=181;
        MapScreen(){super(Component.translatable("ac.app.city_map.name"));}
        private float fit(){return Math.min(1.5f,Math.min((width-10f)/W,(height-10f)/H));}
        private int left(){return Math.round((width-W*fit())/2);}
        private int top(){return Math.round((height-H*fit())/2);}
        @Override protected void init() {
            float s=fit();
            addRenderableWidget(new Button(left()+Math.round(166*s),top()+Math.round(143*s),Math.round(79*s),Math.round(23*s),
                    Component.translatable("academy.city_map.hide"),b->{enabled=false;onClose();},message -> message.get()){
                @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt) {
                    float a=(float)TerminalTimeline.appAlpha(TerminalClient.gameTime()-opened,2);
                    if(a<=0)return;
                    int color=(Math.round(a*255)<<24)|0xffffff;
                    TerminalSkin.draw(g,isHoveredOrFocused()?TerminalSkin.SELECTED:TerminalSkin.FRAME,getX(),getY(),getWidth(),getHeight(),color);
                    drawing.begin(g);
                    try {drawing.text(getMessage().getString(),getX()+getWidth()*.5,getY()+getHeight()*.24,9*fit(),0xf1f1ec,a,1);}
                    finally {drawing.end();}
                }
            });
        }
        @Override public void render(GuiGraphics g,int mx,int my,float dt) {
            renderBackground(g,mx,my,dt);
            var mc=Minecraft.getInstance();if(mc.player==null)return;
            long elapsed=TerminalClient.gameTime()-opened;
            float frame=(float)TerminalTimeline.appAlpha(elapsed,0),map=(float)TerminalTimeline.appAlpha(elapsed,1),info=(float)TerminalTimeline.appAlpha(elapsed,2);
            g.pose().pushPose();g.pose().translate(left(),top(),0);g.pose().scale(fit(),fit(),1);
            TerminalSkin.draw(g,TerminalSkin.FRAME,0,0,W,H,(Math.round(255*frame)<<24)|0xffffff);
            drawMap(g,mc,9,23,147,map);
            drawing.begin(g);
            try {
                drawing.text(title.getString(),9,4,11,0xf0efeb,frame,0);
                drawing.text("N",151,7,8,0xc8cccb,map,2);
                drawing.text(Component.translatable("academy.city_map.position").getString(),166,26,9,0xb5bab9,info,0);
                drawing.text("X  "+mc.player.getBlockX(),166,44,10,0xf2f2ed,info,0);
                drawing.text("Y  "+mc.player.getBlockY(),166,59,10,0xf2f2ed,info,0);
                drawing.text("Z  "+mc.player.getBlockZ(),166,74,10,0xf2f2ed,info,0);
                String facing=Component.translatable("academy.city_map."+mc.player.getDirection().getName()+"_heading").getString();
                drawing.text(facing,166,96,10,0xd6d9d7,info,0);
                drawing.fill(166,125,24.5,1,0xd5d7d3,info);drawing.fill(166,122,1,3,0xd5d7d3,info);
                drawing.fill(190,122,1,3,0xd5d7d3,info);
                drawing.text(Component.translatable("academy.city_map.scale").getString(),198,119,8,0xd5d7d3,info,0);
            } finally {drawing.end();}
            g.pose().popPose();
            for(var widget:renderables) widget.render(g,mx,my,dt);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
