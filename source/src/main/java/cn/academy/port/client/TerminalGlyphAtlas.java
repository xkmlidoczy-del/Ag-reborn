package cn.academy.port.client;

import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Original terminal glyph bitmaps and advances, drawn from a shared texture page. Render thread only. */
final class TerminalGlyphAtlas {
    private static final int SIDE = 512, CELL = 40, PAD = 3, ACROSS = SIDE / CELL;
    private record Tile(Page page, int x, int y, int advance) {}
    private static final class Page {
        final ResourceLocation id;
        final DynamicTexture texture = new DynamicTexture(SIDE, SIDE, true);
        int count;
        boolean dirty;
        Page(ResourceLocation id) {
            this.id = id;
            Objects.requireNonNull(texture.getPixels()).fillRect(0,0,SIDE,SIDE,0x00ffffff);
            Minecraft.getInstance().getTextureManager().register(id, texture);
            TextureUtil.prepareImage(texture.getId(), 3, SIDE, SIDE);
        }
        void bind() {
            if (dirty) {
                texture.upload();
                texture.setFilter(true,true);
                GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
                GL11.glTexParameterf(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_LOD_BIAS, -.65f);
                dirty = false;
            }
            RenderSystem.setShaderTexture(0, id);
        }
    }
    private final ClassicHudFont font;
    private final Supplier<ResourceLocation> ids;
    private final Map<Integer, Tile> tiles = new HashMap<>();
    private final List<Page> pages = new ArrayList<>();
    TerminalGlyphAtlas(ClassicHudFont font, Supplier<ResourceLocation> ids) { this.font=font; this.ids=ids; }

    private Tile tile(int cp, boolean bold) {
        // The Unicode range occupies 21 bits, including supplementary characters.
        int key = bold ? cp | (1 << 21) : cp;
        Tile cached = tiles.get(key);
        if (cached != null) return cached;
        Page page = pages.isEmpty() ? null : pages.getLast();
        if (page == null || page.count == ACROSS * ACROSS) {
            page = new Page(ids.get());
            pages.add(page);
        }
        int col = (page.count % ACROSS)*CELL+PAD, row = (page.count / ACROSS)*CELL+PAD;
        var raster = font.rasterize(cp, bold);
        var pixels = Objects.requireNonNull(page.texture.getPixels());
        for (int y=0; y<ClassicHudFont.CHAR_SIZE; y++) for (int x=0; x<ClassicHudFont.CHAR_SIZE; x++) {
            int argb=raster.image().getRGB(x,y);
            pixels.setPixelRGBA(col+x,row+y,(argb&0xff00ff00)|(argb>>>16&255)|(argb&255)<<16);
        }
        page.count++; page.dirty=true;
        Tile result=new Tile(page,col,row,raster.advance());
        tiles.put(key,result);
        return result;
    }
    void preload(String text) {
        int previousTexture = RenderSystem.getShaderTexture(0);
        width(text, ClassicHudFont.CHAR_SIZE, false);
        for (Page page : pages) if (page.dirty) page.bind();
        RenderSystem.setShaderTexture(0, previousTexture);
    }
    double width(String text, double size, boolean bold) {
        int sum=0;
        for (int i=0; i<text.length();) { int cp=text.codePointAt(i); sum+=tile(cp,bold).advance(); i+=Character.charCount(cp); }
        return sum*size/ClassicHudFont.CHAR_SIZE;
    }
    void draw(Matrix4f matrix, String text, double x, double y, double size, ClassicHudTimeline.Rgba color, boolean bold, ShaderInstance shader) {
        // Complete cold glyphs before binding the page so adjacent characters share one upload and one draw.
        width(text,size,bold);
        RenderSystem.setShader(shader==null?GameRenderer::getPositionTexColorShader:()->shader);
        Page active=null; BufferBuilder buffer=null;
        for (int i=0; i<text.length();) {
            int cp=text.codePointAt(i); i+=Character.charCount(cp);
            var t=tile(cp,bold);
            if (active!=t.page()) {
                if (buffer!=null) BufferUploader.drawWithShader(buffer.buildOrThrow());
                active=t.page(); active.bind();
                buffer=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            }
            float u=(float)t.x()/SIDE,v=(float)t.y()/SIDE, span=(float)ClassicHudFont.CHAR_SIZE/SIDE;
            vertex(buffer,matrix,x,y,u,v,color); vertex(buffer,matrix,x,y+size,u,v+span,color);
            vertex(buffer,matrix,x+size,y+size,u+span,v+span,color); vertex(buffer,matrix,x+size,y,u+span,v,color);
            x+=t.advance()*size/ClassicHudFont.CHAR_SIZE;
        }
        if (buffer!=null) BufferUploader.drawWithShader(buffer.buildOrThrow());
    }
    private static void vertex(BufferBuilder buf, Matrix4f pose, double x, double y, float u, float v, ClassicHudTimeline.Rgba c) {
        buf.addVertex(pose,(float)x,(float)y,0).setUv(u,v).setColor((float)c.r(),(float)c.g(),(float)c.b(),(float)c.a());
    }
    void release(Consumer<ResourceLocation> release) {
        pages.forEach(p->release.accept(p.id)); pages.clear(); tiles.clear();
    }
}
