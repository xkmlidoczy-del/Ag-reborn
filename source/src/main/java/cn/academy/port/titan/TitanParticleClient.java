package cn.academy.port.titan;

import java.util.Locale;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid=TitanBlocks.MODID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class TitanParticleClient {
    private TitanParticleClient() {}
    private static final ParticleRenderType ADDITIVE=new ParticleRenderType() {
        @Override public BufferBuilder begin(Tesselator tessellator,TextureManager textures) {
            RenderSystem.depthMask(false);RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA,GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_PARTICLES);
            return tessellator.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);
        }
        @Override public String toString(){return "TITAN_ADDITIVE";}
    };
    private static final ParticleRenderType MATERIAL=new ParticleRenderType() {
        @Override public BufferBuilder begin(Tesselator tessellator,TextureManager textures) {
            RenderSystem.depthMask(false);RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0,TextureAtlas.LOCATION_PARTICLES);
            return tessellator.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.PARTICLE);
        }
        @Override public String toString(){return "TITAN_MATERIAL";}
    };
    @SubscribeEvent public static void providers(RegisterParticleProvidersEvent event) {
        TitanProfiles.all().forEach((id,profile)-> {
            for (TitanParticleTypes.Shape shape:TitanParticleTypes.Shape.values()) {
                String name=id+"_"+shape.name().toLowerCase(Locale.ROOT);
                event.registerSpriteSet(TitanParticleTypes.ALL.get(name).get(),sprites ->
                    (type,level,x,y,z,vx,vy,vz)->new Glow(level,x,y,z,vx,vy,vz,sprites,profile.color(),shape));
            }
        });
    }
    private static final class Glow extends TextureSheetParticle {
        private final float startSize;
        private final TitanParticleTypes.Shape shape;
        Glow(ClientLevel level,double x,double y,double z,double vx,double vy,double vz,SpriteSet sprites,int color,TitanParticleTypes.Shape shape) {
            super(level,x,y,z,vx,vy,vz);
            this.shape=shape;this.hasPhysics=false;this.gravity=0;this.friction=.96f;
            this.xd=vx;this.yd=vy;this.zd=vz;
            this.lifetime=switch(shape){case CROWN->36;case MOTIF->30;case SIGIL->30;case SLASH->22;case STARBURST->25;case WISP->35;case OUTLINE->7;};
            this.startSize=switch(shape){case CROWN->19.0f;case MOTIF->7.0f;case SIGIL->5.5f;case SLASH->2.9f;case STARBURST->1.7f;case WISP->1.3f;case OUTLINE->.65f;};
            this.quadSize=startSize*(.8f+random.nextFloat()*.4f);
            if(shape!=TitanParticleTypes.Shape.CROWN && shape!=TitanParticleTypes.Shape.MOTIF)
                this.setColor(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f);
            this.setAlpha(.90f);this.setSpriteFromAge(sprites);
            this.roll=(shape==TitanParticleTypes.Shape.CROWN||shape==TitanParticleTypes.Shape.MOTIF)?0:random.nextFloat()*(float)Math.PI*2;
            this.oRoll=this.roll;
        }
        @Override public void tick() {
            super.tick();
            float life=(float)age/(float)lifetime;
            float fadeIn=Math.min(1,life*7);float fadeOut=Math.min(1,(1-life)*2.5f);
            float opacity=switch(shape){case CROWN->.85f;case MOTIF->.72f;case SIGIL->.58f;case SLASH->.72f;case STARBURST->.66f;case WISP->.68f;case OUTLINE->.40f;};
            setAlpha(Math.max(0,opacity*fadeIn*fadeOut));
            if(shape==TitanParticleTypes.Shape.SIGIL||shape==TitanParticleTypes.Shape.CROWN||shape==TitanParticleTypes.Shape.MOTIF)
                this.quadSize=startSize*(.65f+life*.8f);
            this.oRoll=this.roll;this.roll+=(shape==TitanParticleTypes.Shape.CROWN?.004f:shape==TitanParticleTypes.Shape.MOTIF?.012f:shape==TitanParticleTypes.Shape.SIGIL?.025f:.045f);
        }
        @Override protected int getLightColor(float partialTick) {return 0xF000F0;}
        @Override public AABB getRenderBoundingBox(float partialTick) {
            // Billboards can span tens of blocks; the default tiny physics box clips their edges.
            double px=Mth.lerp(partialTick,xo,x),py=Mth.lerp(partialTick,yo,y),pz=Mth.lerp(partialTick,zo,z);
            double radius=Math.max(1,quadSize*1.5+1);
            return new AABB(px-radius,py-radius,pz-radius,px+radius,py+radius,pz+radius);
        }
        @Override public ParticleRenderType getRenderType() {
            if(shape==TitanParticleTypes.Shape.WISP||shape==TitanParticleTypes.Shape.OUTLINE)return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
            if(shape==TitanParticleTypes.Shape.CROWN||shape==TitanParticleTypes.Shape.MOTIF)return MATERIAL;
            return ADDITIVE;
        }
    }
}
