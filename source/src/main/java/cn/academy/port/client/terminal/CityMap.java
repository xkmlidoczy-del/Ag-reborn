package cn.academy.port.client.terminal;

import cn.academy.port.client.HighPowerRecoveryHud;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

/** A bounded, client-only surface survey. Never requests or loads a chunk. North is up. */
final class CityMap {
    private static final int CELLS=48, STEP=2, UNKNOWN=0xff1b2930, BUDGET=192;
    private static final int[] colors=new int[CELLS*CELLS];
    private static int originX, originZ, cursor;
    private static boolean enabled, initialized;
    private static Object world;
    private CityMap() {}
    static void open() { enabled=true; Minecraft.getInstance().setScreen(new MapScreen()); }
    static void clear() { enabled=false; initialized=false; world=null; Arrays.fill(colors,UNKNOWN); }
    static void tick(Minecraft mc) {
        if(!enabled || mc.level==null || mc.player==null || mc.isPaused()) return;
        int px=mc.player.blockPosition().getX(), pz=mc.player.blockPosition().getZ();
        if(world!=mc.level || !initialized || Math.abs(px-(originX+CELLS))>24 || Math.abs(pz-(originZ+CELLS))>24) {
            world=mc.level; initialized=true;
            originX=Math.floorDiv(px,STEP)*STEP-CELLS; originZ=Math.floorDiv(pz,STEP)*STEP-CELLS;
            cursor=0; Arrays.fill(colors,UNKNOWN);
        }
        var pos=new BlockPos.MutableBlockPos();
        for(int i=0;i<BUDGET;i++) {
            int index=cursor; cursor=(cursor+1)%colors.length;
            int x=originX+(index%CELLS)*STEP, z=originZ+(index/CELLS)*STEP;
            if(!mc.level.hasChunk(x>>4,z>>4)) { colors[index]=UNKNOWN; continue; }
            int y=mc.level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)-1;
            if(y<mc.level.getMinBuildHeight()) { colors[index]=UNKNOWN; continue; }
            pos.set(x,y,z);
            var state=mc.level.getBlockState(pos);
            int rgb=state.getMapColor(mc.level,pos).col;
            // City materials deliberately have no vanilla map color. Give their visible surfaces
            // a restrained palette so pavements and roofs don't disappear as unexplored space.
            if(rgb==0 && !state.isAir()) {
                var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
                String name=id.getPath();
                if(id.getNamespace().equals("academy")) {
                    if(name.contains("road") || name.contains("asphalt")) rgb=0x515e63;
                    else if(name.contains("glass") || name.contains("window")) rgb=0x69949f;
                    else if(name.contains("floor") || name.contains("plaza") || name.contains("pavement")) rgb=0xa3adb0;
                    else rgb=0x7b8e94;
                } else rgb=0x75838a;
            }
            colors[index]=rgb==0?UNKNOWN:0xff000000|rgb;
        }
    }
    static void renderHud(GuiGraphics g, Minecraft mc) {
        if(!enabled||!initialized||mc.player==null||mc.level!=world||mc.options.hideGui||mc.screen!=null||mc.getDebugOverlay().showDebugScreen())return;
        int x=8,y=64,w=56,h=71;
        if(g.guiWidth()<340||g.guiHeight()<210)return;
        var box=HighPowerRecoveryHud.relayReservedBox(g.guiWidth(),g.guiHeight());
        if(box!=null&&x<box.x()+box.width()&&x+w>box.x()&&y<box.y()+box.height()&&y+h>box.y())return;
        g.fill(x,y,x+w,y+h,0xd00b151c);g.fill(x,y,x+w,y+1,0xff7199a8);
        g.drawString(mc.font,"N",x+25,y+3,0xffe8f5f7,false);
        drawMap(g,mc,x+4,y+15,1);
        String coords=mc.player.getBlockX()+", "+mc.player.getBlockZ();
        g.drawString(mc.font,mc.font.plainSubstrByWidth(coords,w-8),x+4,y+63,0xffe8f5f7,false);
    }
    private static void drawMap(GuiGraphics g,Minecraft mc,int x,int y,int scale) {
        for(int row=0;row<CELLS;row++) {
            int start=0;
            while(start<CELLS) {
                int color=colors[row*CELLS+start],end=start+1;
                while(end<CELLS&&colors[row*CELLS+end]==color)end++;
                g.fill(x+start*scale,y+row*scale,x+end*scale,y+(row+1)*scale,color); start=end;
            }
        }
        int mx=x+Mth.floor((mc.player.getX()-originX)/STEP*scale), mz=y+Mth.floor((mc.player.getZ()-originZ)/STEP*scale);
        if(mx>=x+3&&mx<x+CELLS*scale-3&&mz>=y+3&&mz<y+CELLS*scale-3) {
            int radius=scale==1?1:2;
            g.fill(mx-radius-1,mz-radius-1,mx+radius+2,mz+radius+2,0xff10212a);
            g.fill(mx-radius,mz-radius,mx+radius+1,mz+radius+1,0xffeafcff);
            double angle=Math.toRadians(mc.player.getYRot());
            int dx=(int)Math.round(-Math.sin(angle)*(scale==1?2:4)), dz=(int)Math.round(Math.cos(angle)*(scale==1?2:4));
            g.fill(mx+dx-1,mz+dz-1,mx+dx+2,mz+dz+2,0xffffc466);
        }
    }
    private static final class MapScreen extends Screen {
        MapScreen(){super(Component.translatable("ac.app.city_map.name"));}
        private float scale() { return Math.min(1f, Math.min((width-12)/232f,(height-12)/194f)); }
        @Override protected void init() {
            float s=scale(); int w=Math.max(40,(int)(200*s)), h=Math.max(12,(int)(20*s));
            addRenderableWidget(Button.builder(Component.translatable("academy.city_map.hide"),b->{enabled=false;onClose();})
                    .bounds((width-w)/2,(int)((height-194*s)/2+170*s),w,h).build());
        }
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
            renderBackground(g,mouseX,mouseY,partialTick);
            Minecraft mc=Minecraft.getInstance(); if(mc.player==null)return;
            float s=scale(); int x=0,y=0;
            g.pose().pushPose();
            g.pose().translate((width-232*s)/2,(height-194*s)/2,0); g.pose().scale(s,s,1);
            g.fill(x,y,x+232,y+194,0xe80b151c);g.fill(x,y,x+232,y+2,0xff7199a8);
            g.drawString(font,title,x+12,y+10,0xffe8f5f7,false);
            g.drawString(font,Component.translatable("academy.city_map.north"),x+12,y+26,0xffaac3cd,false);
            drawMap(g,mc,x+12,y+42,2);
            int tx=x+120;
            g.drawString(font,Component.translatable("academy.city_map.position"),tx,y+45,0xffa8bdc5,false);
            g.drawString(font,"X " + mc.player.getBlockX(),tx,y+59,0xffeff8fa,false);
            g.drawString(font,"Y " + mc.player.getBlockY(),tx,y+71,0xffeff8fa,false);
            g.drawString(font,"Z " + mc.player.getBlockZ(),tx,y+83,0xffeff8fa,false);
            g.drawWordWrap(font,Component.translatable("academy.city_map.surface"),tx,y+103,100,0xffaac3cd);
            g.pose().popPose();
            for (var widget : renderables) widget.render(g,mouseX,mouseY,partialTick);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
