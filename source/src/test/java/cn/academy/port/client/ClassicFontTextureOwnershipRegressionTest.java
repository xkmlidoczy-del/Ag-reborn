package cn.academy.port.client;

import net.minecraft.resources.ResourceLocation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Actual ClassicHudFont allocation/cache/release methods, with a fake manager and no Minecraft/OpenGL init. */
public final class ClassicFontTextureOwnershipRegressionTest {
    private static int assertions;
    private static Method allocate,release;
    private static Constructor<?> glyph;
    private static Field normal,bold;
    private static final Map<ResourceLocation,ClassicHudFont> registered=new HashMap<>();
    private static final Set<ResourceLocation> allIds=new HashSet<>();
    private static void yes(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    @SuppressWarnings("unchecked")
    private static Map<Integer,Object> cache(ClassicHudFont font,boolean isBold)throws Exception{return (Map<Integer,Object>)(isBold?bold:normal).get(font);}
    private static ResourceLocation register(ClassicHudFont font,int cp,boolean isBold)throws Exception{
        ResourceLocation id=(ResourceLocation)allocate.invoke(font);yes(allIds.add(id),"texture ID never reused across owners or releases");
        yes(registered.put(id,font)==null,"register cannot replace another owner's glyph");cache(font,isBold).put(cp,glyph.newInstance(id,10));return id;
    }
    private static void close(ClassicHudFont font)throws Exception{
        Consumer<ResourceLocation> disposer=id->{yes(registered.get(id)==font,"release only removes this font's keys");registered.remove(id);};
        release.invoke(font,disposer);yes(cache(font,false).isEmpty()&&cache(font,true).isEmpty(),"both font caches cleared");
    }
    public static void main(String[] args)throws Exception{
        allocate=ClassicHudFont.class.getDeclaredMethod("nextTextureLocation");allocate.setAccessible(true);
        release=ClassicHudFont.class.getDeclaredMethod("releaseGlyphs",Consumer.class);release.setAccessible(true);
        normal=ClassicHudFont.class.getDeclaredField("normalGlyphs");normal.setAccessible(true);bold=ClassicHudFont.class.getDeclaredField("boldGlyphs");bold.setAccessible(true);
        Class<?> type=Class.forName("cn.academy.port.client.ClassicHudFont$Glyph");glyph=type.getDeclaredConstructor(ResourceLocation.class,int.class);glyph.setAccessible(true);
        var hud=new ClassicHudFont();ResourceLocation r=register(hud,'R',false),f=register(hud,'F',false);register(hud,'R',true);
        for(int screen=0;screen<50;screen++){
            var popup=new ClassicHudFont();ResourceLocation pr=register(popup,'R',false),pf=register(popup,'F',false);register(popup,'R',true);register(popup,0x5F00,false);
            yes(!r.equals(pr)&&!f.equals(pf),"HUD and GUI same letters have different globally registered IDs");
            close(popup);yes(registered.get(r)==hud&&registered.get(f)==hud,"closing GUI preserves HUD R/F textures");
            yes(cache(hud,false).size()==2&&cache(hud,true).size()==1,"closing GUI preserves HUD cache ownership");
            close(popup);yes(registered.get(r)==hud,"double close cannot release HUD");
            // Reusing the same screen/font cache after a release must not reuse its old registered keys.
            register(popup,'R',false);close(popup);yes(registered.get(f)==hud,"same-owner reopen cannot destroy HUD");
        }
        var other=new ClassicHudFont();ResourceLocation otherR=register(other,'R',false);close(hud);yes(registered.get(otherR)==other,"HUD preference-change release preserves other screen");
        ResourceLocation newR=register(hud,'R',false);yes(!newR.equals(r),"HUD font reload does not reuse stale glyph ID");close(other);yes(registered.get(newR)==hud,"other screen release preserves new HUD cache");close(hud);
        yes(registered.isEmpty(),"all fake manager textures released exactly by their owners");
        System.out.println("ClassicFontTextureOwnershipRegressionTest: "+assertions+" assertions passed");
    }
}
