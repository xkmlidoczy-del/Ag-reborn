package cn.academy.port.client.terminal.media;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Classic source screen geometry and controls, with local user-media input and modern keyboard access. */
public final class ClassicMediaScreen extends Screen {
    private final MediaBackend backend=MediaBackend.instance();private final MediaFont mediaFont=new MediaFont();private final MediaLayout layout=MediaLayout.main();
    private final Map<MediaFiles.Track,ResourceLocation>covers=new HashMap<>();private int firstRow;private double scale,left,top;private boolean volumeDragging,scrollDragging;
    private EditBox editor;private MediaFiles.Track editing;private boolean editName;private List<MediaFiles.Track>displayed=List.of();
    public ClassicMediaScreen(){super(Component.literal("Media Player"));}
    @Override protected void init(){commitEdit();releaseCovers();mediaFont.use(cn.academy.port.ClassicHudConfig.FONT.get());var back=layout.element("back").box();scale=Math.min(back.scale(),Math.min((width-8)/back.width(),(height-32)/back.height()));scale=Math.max(.05,scale);left=(width-back.width()*scale)/2;top=(height-back.height()*scale)/2;displayed=backend.tracks();firstRow=Math.min(firstRow,MediaLayout.maxScroll(displayed.size()));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){if(displayed!=backend.tracks()){commitEdit();releaseCovers();displayed=backend.tracks();firstRow=Math.min(firstRow,MediaLayout.maxScroll(displayed.size()));}}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
        var canvas=new MediaCanvas(graphics);canvas.begin();graphics.pose().pushPose();graphics.pose().translate(left,top,0);graphics.pose().scale((float)scale,(float)scale,1);
        canvas.rect(MediaCanvas.texture("guis/apps/media_player/back"),0,0,650,504,MediaCanvas.WHITE);
        double mx=(mouseX-left)/scale,my=(mouseY-top)/scale;var info=backend.current();
        var pop=box("pop");var stop=box("stop");canvas.rect(MediaCanvas.texture("guis/apps/media_player/"+(info!=null&&!info.paused()?"pause":"play")),pop.x(),pop.y(),pop.width(),pop.height(),white(pop.contains(mx,my)?1:200/255d));
        canvas.rect(MediaCanvas.texture("guis/apps/media_player/stop"),stop.x(),stop.y(),stop.width(),stop.height(),white(stop.contains(mx,my)?1:200/255d));
        var vb=box("volume_back");canvas.rect(MediaCanvas.texture("guis/icons/volume_overlay"),vb.x(),vb.y(),vb.width()*vb.scale(),vb.height()*vb.scale(),MediaCanvas.WHITE);
        var volume=box("volume_bar");canvas.fill(186+112*backend.volume(),volume.y(),volume.width(),volume.height(),white(.6));
        text(canvas,"back/title",info==null?"":backend.name(info.media()),650,504);text(canvas,"back/play_time",info==null?"00:00":info.displayTime(),650,504);
        var progress=box("progress");canvas.fill(progress.x(),progress.y(),progress.width()*(info==null?0:info.progress()),progress.height(),MediaCanvas.WHITE);
        var area=box("area");
        if(displayed.isEmpty()){
            String empty=backend.scanning()?"Scanning local media…":"Add your own .ogg files to acmedia/source";
            mediaFont.draw(canvas,empty,area.x()+8,area.y()+30,24,white(.8),false,false);
            mediaFont.draw(canvas,"Optional covers: acmedia/cover/<id>.png",area.x()+8,area.y()+65,22,white(.65),false,false);
            mediaFont.draw(canvas,"Internal tracks are excluded from this port",area.x()+8,area.y()+105,22,white(.65),false,false);
        }
        for(int i=0;i<MediaLayout.visibleRows()&&firstRow+i<displayed.size();i++){
            MediaFiles.Track track=displayed.get(firstRow+i);double rowY=area.y()+i*60;
            canvas.fill(area.x(),rowY,554,60,white(mx>=area.x()&&mx<area.x()+554&&my>=rowY&&my<rowY+60?60/255d:20/255d));
            var icon=layout.box("back/t_one/icon",554,60);canvas.rect(cover(track),area.x()+icon.x(),rowY+icon.y(),icon.width(),icon.height(),MediaCanvas.WHITE);
            graphics.pose().pushPose();graphics.pose().translate(area.x(),rowY,0);
            if(!(editor!=null&&editing==track&&editName))text(canvas,"back/t_one/title",backend.name(track),554,60);
            if(!(editor!=null&&editing==track&&!editName))text(canvas,"back/t_one/desc",backend.description(track),554,60);
            text(canvas,"back/t_one/time",MediaPlayback.displayTime(track.lengthSeconds()),554,60);
            for(String edit:List.of("btn_edit_name","btn_edit_desc")){var b=layout.box("back/t_one/"+edit,554,60);canvas.rect(MediaCanvas.texture("guis/icons/edit"),b.x(),b.y(),b.width(),b.height(),white(b.contains(mx-area.x(),my-rowY)?1:.2));}
            graphics.pose().popPose();
        }
        var scroll=box("scroll_bar");int max=MediaLayout.maxScroll(displayed.size());double fraction=max==0?0:(double)firstRow/max;canvas.fill(scroll.x(),169+246*fraction,scroll.width(),scroll.height(),white(130/255d));
        graphics.pose().popPose();canvas.end();
        if(editor!=null)editor.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawString(font,"F5: refresh   Space: play/pause   Esc: close",(int)left,(int)(top+504*scale+4),0xffb9b9b9,false);
        String status=!backend.error().isEmpty()?backend.error():!backend.warnings().isEmpty()?backend.warnings().getFirst():backend.loading()?"Preparing native audio…":"";
        if(!status.isEmpty())graphics.drawString(font,font.plainSubstrByWidth(status,Math.max(1,(int)(650*scale))),(int)left,(int)(top+504*scale+15),backend.error().isEmpty()?0xffd3b478:0xffe5a0a0,false);
        if(backend.warnings().size()>1&&my>=504)graphics.renderTooltip(font,backend.warnings().stream().<Component>map(Component::literal).toList(),Optional.empty(),mouseX,mouseY);
    }
    private MediaLayout.Box box(String name){return layout.box("back/"+name,650,504);}
    private static MediaCanvas.Color white(double alpha){return new MediaCanvas.Color(1,1,1,alpha);}
    private void text(MediaCanvas canvas,String path,String value,double pw,double ph){var e=layout.element(path);var b=e.box().resolved(pw,ph);String text=fit(value,b.width(),e.fontSize());double textWidth=mediaFont.width(text,e.fontSize(),false);double x=b.x()+switch(e.textAlign()){case"RIGHT"->b.width()-textWidth;case"CENTER"->(b.width()-textWidth)/2;default->0;};double y=b.y()+switch(e.heightAlign()){case"BOTTOM"->b.height()-e.fontSize();case"CENTER"->(b.height()-e.fontSize())/2;default->0;};mediaFont.draw(canvas,text,x,y,e.fontSize(),white(e.alpha()),false,false);}
    private String fit(String text,double width,double size){if(mediaFont.width(text,size,false)<=width)return text;StringBuilder out=new StringBuilder();for(int cp:text.codePoints().toArray()){String next=out.toString()+new String(Character.toChars(cp));if(mediaFont.width(next+"…",size,false)>width)break;out.appendCodePoint(cp);}return out+"…";}
    private ResourceLocation cover(MediaFiles.Track track){return covers.computeIfAbsent(track,t->{
        if(t.cover()!=null&&MediaFiles.safeCover(t.cover()))try(var input=Files.newInputStream(t.cover())){NativeImage image=NativeImage.read(input);DynamicTexture texture=new DynamicTexture(image);texture.setFilter(true,false);return Minecraft.getInstance().getTextureManager().register("academy_media_cover",texture);}catch(IOException|RuntimeException ignored){}
        return MediaCanvas.texture("guis/icons/icon_nomedia");
    });}
    private void releaseCovers(){var missing=MediaCanvas.texture("guis/icons/icon_nomedia");covers.values().stream().filter(id->!id.equals(missing)).forEach(id->Minecraft.getInstance().getTextureManager().release(id));covers.clear();}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0)return super.mouseClicked(x,y,button);if(editor!=null&&editor.isMouseOver(x,y))return super.mouseClicked(x,y,button);commitEdit();double mx=(x-left)/scale,my=(y-top)/scale;
        if(box("pop").contains(mx,my)){backend.togglePlayPause();return true;}if(box("stop").contains(mx,my)){backend.stopCurrent();return true;}
        if(mx>=179.6875&&mx<307.6875&&my>=77&&my<109){volumeDragging=true;dragVolume(mx);return true;}
        if(mx>=599&&mx<620&&my>=169&&my<=471){scrollDragging=true;dragScroll(my);return true;}
        var area=box("area");if(mx>=area.x()&&mx<area.x()+554&&my>=area.y()&&my<area.y()+MediaLayout.visibleRows()*60){int row=(int)((my-area.y())/60),index=firstRow+row;if(index>=displayed.size())return true;var track=displayed.get(index);double rx=mx-area.x(),ry=my-area.y()-row*60;
            for(boolean name:new boolean[]{true,false}){var b=layout.box("back/t_one/btn_edit_"+(name?"name":"desc"),554,60);if(b.contains(rx,ry)){beginEdit(track,name,row);return true;}}
            backend.play(track);return true;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&volumeDragging){dragVolume((x-left)/scale);return true;}if(button==0&&scrollDragging){dragScroll((y-top)/scale);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0){volumeDragging=scrollDragging=false;}return super.mouseReleased(x,y,button);}
    private void dragVolume(double mx){backend.volume((float)((mx-186)/112));}
    private void dragScroll(double my){firstRow=(int)(MediaLayout.maxScroll(displayed.size())*Math.max(0,Math.min(1,(my-169)/246)));}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){commitEdit();firstRow=Math.max(0,Math.min(MediaLayout.maxScroll(displayed.size()),firstRow-(int)Math.signum(vertical)));return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(editor!=null){if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER){commitEdit();return true;}if(key==GLFW.GLFW_KEY_ESCAPE){commitEdit();return true;}return super.keyPressed(key,scan,modifiers);}if(key==GLFW.GLFW_KEY_SPACE){backend.togglePlayPause();return true;}if(key==GLFW.GLFW_KEY_F5){backend.refresh();return true;}if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){firstRow=Math.max(0,Math.min(MediaLayout.maxScroll(displayed.size()),firstRow+(key==GLFW.GLFW_KEY_UP?-1:1)));return true;}return super.keyPressed(key,scan,modifiers);}
    private void beginEdit(MediaFiles.Track track,boolean name,int row){editing=track;editName=name;var area=box("area");var b=layout.box("back/t_one/"+(name?"title":"desc"),554,60);editor=new EditBox(font,(int)(left+(area.x()+b.x())*scale),(int)(top+(area.y()+row*60+b.y())*scale),Math.max(40,(int)(b.width()*scale)),Math.max(12,(int)(b.height()*scale)),Component.literal(name?"Media name":"Media description"));editor.setMaxLength(2048);editor.setBordered(false);editor.setValue(name?backend.name(track):backend.description(track));addWidget(editor);setFocused(editor);editor.setFocused(true);}
    private void commitEdit(){if(editor!=null){backend.edit(editing,editName,editor.getValue());removeWidget(editor);editor=null;editing=null;setFocused(null);}}
    @Override public void removed(){commitEdit();releaseCovers();mediaFont.release();}
}
