package cn.academy.port.worldboss.client;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import java.io.IOException;
/** Dedicated mipmapped atlas: preserves source PNG while reducing distant scale-pattern aliasing. */
final class BossTexture extends AbstractTexture {
 private final String file;
 BossTexture(){this("boss.png");}
 BossTexture(String file){this.file=file;}
 public void load(ResourceManager manager) throws IOException {
  try(var input=manager.open(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy","worldboss/"+file));var source=NativeImage.read(input)){TextureUtil.prepareImage(getId(),4,source.getWidth(),source.getHeight());
   source.upload(0,0,0,0,0,source.getWidth(),source.getHeight(),true,false,true,false);
   for(int level=1;level<=4;level++)try(var mip=new NativeImage(source.getWidth()>>level,source.getHeight()>>level,false)){source.resizeSubRectTo(0,0,source.getWidth(),source.getHeight(),mip);mip.upload(level,0,0,0,0,mip.getWidth(),mip.getHeight(),true,false,true,false);}
   super.setFilter(true,true);
  }
 }
 @Override public void setFilter(boolean blur,boolean mipmap){super.setFilter(true,true);}
}
