/* LambdaLib 1.2.3 TrueTypeFont rasterization/metrics adaptation. MIT; see NOTICE. */
package cn.academy.port.client.terminal.media;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Source uses system AWT fonts, not Minecraft's bitmap font. No third-party/proprietary TTF is copied. */
final class MediaFont {
    private static final int FONT_SIZE=24,CHAR_SIZE=(int)(FONT_SIZE*1.4);
    private record Glyph(ResourceLocation texture,int width){}
    private final Map<Integer,Glyph> normalGlyphs=new HashMap<>(),boldGlyphs=new HashMap<>();
    private Font normal,bold;
    private String preference;
    private boolean disabled;
    // TextureManager keys are global; a glyph serial by itself collides with every other font instance.
    private static final AtomicLong NEXT_TEXTURE_OWNER=new AtomicLong();
    private final long textureOwner=NEXT_TEXTURE_OWNER.getAndIncrement();
    private int textureSerial;
    void use(String requested){
        if(requested.equals(preference))return;release();preference=requested;
        try{
            var installed=GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts();String name=null;
            for(String candidate:new String[]{requested,"微软雅黑","Microsoft YaHei","SimHei","Adobe Heiti Std R"}){
                for(Font font:installed)if(font.getName().equalsIgnoreCase(candidate)){name=candidate;break;}
                if(name!=null)break;
            }
            normal=new Font(name,Font.PLAIN,FONT_SIZE);bold=normal.deriveFont(Font.BOLD);disabled=false;
        }catch(RuntimeException|LinkageError exception){disabled=true;LogUtils.getLogger().warn("System font unavailable; classic HUD will use the Minecraft font",exception);}
    }
    double width(String text,double size,boolean bold){
        if(disabled)return Minecraft.getInstance().font.width(text)*size/12;
        return text.codePoints().map(cp->glyph(cp,bold).width()).sum()*size/CHAR_SIZE;
    }
    void draw(MediaCanvas canvas,String text,double x,double y,double size,MediaCanvas.Color color,boolean bold,boolean mono){
        if(text.isEmpty()||color.a()<=0)return;
        // AWT is available in the standard Minecraft Java 21 runtime; fallback is only for a stripped/headless runtime.
        if(disabled){canvas.minecraftText(text,x,y,size,color);return;}
        // LambdaLib TrueTypeFont disables alpha testing; vanilla GUI shader discards alpha < .1.
        ShaderInstance shader=ClassicMediaClient.fontShader;
        for(int cp:text.codePoints().toArray()){
            Glyph glyph=glyph(cp,bold);canvas.rect(glyph.texture(),x,y,size,size,0,0,1,1,color,shader,null);x+=glyph.width()*size/CHAR_SIZE;
        }
    }
    private Glyph glyph(int cp,boolean isBold){
        Map<Integer,Glyph> cache=isBold?boldGlyphs:normalGlyphs;
        if(normalGlyphs.size()+boldGlyphs.size()>=4096&&!cache.containsKey(cp))release();
        return cache.computeIfAbsent(cp,key->{
            BufferedImage image=new BufferedImage(CHAR_SIZE,CHAR_SIZE,BufferedImage.TYPE_INT_ARGB);var g=image.createGraphics();
            g.setFont(isBold?bold:normal);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            var metrics=g.getFontMetrics();int width=metrics.charWidth(key);g.setBackground(new Color(255,255,255,0));g.clearRect(0,0,CHAR_SIZE,CHAR_SIZE);
            g.setColor(Color.WHITE);g.drawString(new String(Character.toChars(key)),3,1+metrics.getAscent());g.dispose();
            NativeImage pixels=new NativeImage(CHAR_SIZE,CHAR_SIZE,false);
            for(int y=0;y<CHAR_SIZE;y++)for(int x=0;x<CHAR_SIZE;x++){
                int argb=image.getRGB(x,y);pixels.setPixelRGBA(x,y,(argb&0xff00ff00)|((argb>>>16)&255)|((argb&255)<<16));
            }
            DynamicTexture texture=new DynamicTexture(pixels);
            com.mojang.blaze3d.platform.TextureUtil.prepareImage(texture.getId(),5,CHAR_SIZE,CHAR_SIZE);
            texture.upload();texture.setFilter(true,true);
            // Modern texture object owns the image/id; GPU mip generation and bias retain LambdaLib filtering.
            org.lwjgl.opengl.GL30.glGenerateMipmap(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
            org.lwjgl.opengl.GL11.glTexParameterf(org.lwjgl.opengl.GL11.GL_TEXTURE_2D,org.lwjgl.opengl.GL14.GL_TEXTURE_LOD_BIAS,-.65f);
            ResourceLocation id=nextTextureLocation();
            Minecraft.getInstance().getTextureManager().register(id,texture);return new Glyph(id,width);
        });
    }
    private ResourceLocation nextTextureLocation(){
        return ResourceLocation.fromNamespaceAndPath("academy","dynamic/classic_media_font_"+textureOwner+"_"+(textureSerial++));
    }
    void release(){releaseGlyphs(Minecraft.getInstance().getTextureManager()::release);}
    private void releaseGlyphs(Consumer<ResourceLocation> release){
        normalGlyphs.values().forEach(g->release.accept(g.texture()));boldGlyphs.values().forEach(g->release.accept(g.texture()));normalGlyphs.clear();boldGlyphs.clear();
    }
    String resolvedName(){return disabled?"Minecraft font fallback":normal==null?"not initialized":normal.getName();}
}
