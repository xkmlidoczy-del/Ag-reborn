/* AcademyCraft1.0.7 source witnesses retain GPLv3/additional terms; see classic-vector-mask-source/NOTICE. */
package cn.academy.port.client;

import com.google.gson.JsonParser;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSectionSerializer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

/** Actual compiled route, official metadata parser and recorded-contour pixel regression; no game/GL initialization. */
public final class ClassicVectorMaskRegressionTest {
    private static final String PREFIX="cn/academy/port/client/";
    private static final String WITNESSES=PREFIX+"classic-vector-mask-source/";
    private static final String MASK="assets/academy/textures/effects/screen_mask.png";
    private static final String SHADER="assets/academy/shaders/core/classic_skill_alpha";
    private static final Map<String,String> PINNED=Map.of(
            "ClientResources.java","4bb75d45b6c3ac5f0dcb5eae00638fe748d33ff36d392315ad7a8a9a55a9e352",
            "BackgroundMask.java","1e9986a65be3f4cdc4c30232a5063be335f8c8c9a4e0655d62fd834b74fb2b28",
            "CatVecManip.scala","2bf1907f5971692395a5556a927e08660f74dea45609459a42d13afab04daf45",
            "README.md","6666d51532b31b3f6c1506ad1855aa22241926ffcec9f452e64e5e54285f9794",
            "GPLv3-LICENSE","3972dc9744f6499f0f9b2dbf76696f2ae7ad8af9b23dde66d6af86c9dfb36986",
            "NOTICE","56315189de82532056a477e26d3ebfcc79b6090c3fbd4c54d876f851895815e7");
    private static int checks,mutants;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static byte[] resource(String path)throws Exception{
        try(var in=ClassicVectorMaskRegressionTest.class.getClassLoader().getResourceAsStream(path)){
            check(in!=null,"missing mandatory classpath resource: "+path);return in.readAllBytes();
        }
    }
    private static String text(byte[] bytes){return new String(bytes,StandardCharsets.UTF_8);}
    private static String compact(String s){return s.replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*","").replaceAll("\\s+","");}
    private static String sha(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void witnesses()throws Exception{
        for(var entry:PINNED.entrySet())check(sha(resource(WITNESSES+entry.getKey())).equals(entry.getValue()),"altered canonical witness: "+entry.getKey());
        String sampler=compact(text(resource(WITNESSES+"ClientResources.java")));
        check(sampler.contains("GL_TEXTURE_MAG_FILTER,GL_LINEAR"),"source magnification is linear");
        check(sampler.contains("GL_TEXTURE_MIN_FILTER,GL_LINEAR_MIPMAP_LINEAR")&&sampler.contains("glGenerateMipmap"),"source also requires trilinear mipmap minification; metadata does not establish it");
        check(sampler.contains("GL_TEXTURE_WRAP_S,GL_CLAMP")&&sampler.contains("GL_TEXTURE_WRAP_T,GL_CLAMP"),"source clamps both sampler axes");
        String mask=compact(text(resource(WITNESSES+"BackgroundMask.java")));
        check(mask.contains("glDisable(GL11.GL_ALPHA_TEST)")&&mask.contains("glEnable(GL11.GL_ALPHA_TEST)"),"source disables/restores alpha testing for the vignette");
        check(mask.contains("cpData.isActivated()")&&mask.contains("getCategory().getColorStyle()"),"category mask does not depend on skills");
        check(compact(text(resource(WITNESSES+"CatVecManip.scala"))).contains("colorStyle.fromHexColor(0xff000000)"),"source Vector mask color is opaque black");
    }
    private static void metadata(String json){
        // Exercise Minecraft's actual serializer, which supplies the flags used by SimpleTexture.upload.
        var metadata=new TextureMetadataSectionSerializer().fromJson(JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("texture"));
        check(metadata.isBlur(),"screen mask must select official GL_LINEAR filtering");
        check(metadata.isClamp(),"screen mask must select official clamp-to-edge upload");
    }
    private static void shader(String fragment,String json){
        String f=compact(fragment);
        check(!f.contains("discard"),"source no-alpha-test mask cannot discard low-alpha fragments");
        check(f.equals("#version150uniformsampler2DSampler0;uniformvec4ColorModulator;invec2texCoord0;invec4vertexColor;outvec4fragColor;voidmain(){fragColor=texture(Sampler0,texCoord0)*vertexColor*ColorModulator;}"),"exact no-cutoff tint/coverage shader program");
        var descriptor=JsonParser.parseString(json).getAsJsonObject();
        check(descriptor.get("fragment").getAsString().equals("academy:classic_skill_alpha"),"actual descriptor points to checked fragment");
        check(descriptor.get("vertex").getAsString().equals("minecraft:position_tex_color"),"actual descriptor uses compatible GUI vertex shader");
    }
    private static void route(byte[] bytes){
        var cls=new ClassNode();new ClassReader(bytes).accept(cls,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        MethodNode render=cls.methods.stream().filter(m->m.name.equals("render")).findFirst().orElseThrow();
        int masks=0;
        for(var instruction:render.instructions){
            if(!(instruction instanceof FieldInsnNode field)||field.getOpcode()!=Opcodes.GETSTATIC||!field.name.equals("SCREEN_MASK"))continue;
            masks++;boolean selected=false,drawn=false;
            for(var next=instruction.getNext();next!=null;next=next.getNext()){
                if(next instanceof MethodInsnNode call){
                    if(call.getOpcode()==Opcodes.INVOKESTATIC&&call.owner.equals(PREFIX+"ClassicSkillAlphaShader")&&call.name.equals("get"))selected=true;
                    if(call.owner.equals(PREFIX+"ClassicHudCanvas")&&call.name.equals("rect")){
                        check(selected&&call.desc.contains("Lnet/minecraft/client/renderer/ShaderInstance;"),"compiled SCREEN_MASK draw must receive the no-cutoff shader");drawn=true;break;
                    }
                }
            }
            check(drawn,"compiled category mask actually reaches a rectangle draw");
        }
        check(masks==1,"exactly one bounded category mask draw");
    }
    private static double alpha(BufferedImage image,int x,int y){return (image.getRGB(Math.max(0,Math.min(image.getWidth()-1,x)),Math.max(0,Math.min(image.getHeight()-1,y)))>>>24)/255.0;}
    private static double linear(BufferedImage image,double x,double y){
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);double fx=x-ix,fy=y-iy;
        return (alpha(image,ix,iy)*(1-fx)+alpha(image,ix+1,iy)*fx)*(1-fy)
                +(alpha(image,ix,iy+1)*(1-fx)+alpha(image,ix+1,iy+1)*fx)*fy;
    }
    private static void pixels(byte[] bytes)throws Exception{
        check(sha(bytes).equals("c69c1cc1a96aced517335f648c69d9c17320b879fe310325192661299c48bbf6"),"actual mask must retain original texture bytes");
        var image=ImageIO.read(new ByteArrayInputStream(bytes));check(image!=null&&image.getWidth()==512&&image.getHeight()==288,"canonical vignette raster dimensions");
        check(Math.abs(alpha(image,45,138)-26/255.0)<1e-12&&Math.abs(alpha(image,46,138)-24/255.0)<1e-12,"recorded source contour texels");
        // Independently reproduce screenshot 2026-10-01_15.18.41.png row391, x105→106.
        double aLeft=alpha(image,(int)((105.5/1180)*512),(int)((391.5/812)*288));
        double aRight=alpha(image,(int)((106.5/1180)*512),(int)((391.5/812)*288));
        double oldLeft=255*(1-(aLeft<.1?0:aLeft)),oldRight=255*(1-(aRight<.1?0:aRight));
        check(Math.abs(oldLeft-229)<1e-9&&oldRight==255,"legacy shader reproduces the photographed 229→255 blue-channel jump");
        check(oldRight-oldLeft>=25,"negative control demonstrates the visible alpha-cutoff defect");
        double maxJump=0,last=-1;
        for(int x=100;x<=112;x++){
            double coverage=linear(image,((x+.5)/1180)*512-.5,((391+.5)/812)*288-.5);
            double blue=255*(1-coverage);check(coverage>0&&blue<255,"sub-threshold vignette coverage is retained");
            if(last>=0)maxJump=Math.max(maxJump,Math.abs(blue-last));last=blue;
        }
        check(maxJump<1,"linear/no-cutoff repair keeps photographed contour continuous, below one blue-channel unit per screen pixel");
        double midpoint=linear(image,45.5,138);
        check(midpoint>alpha(image,46,138)&&midpoint<alpha(image,45,138),"linear magnification interpolates actual mask alpha instead of nearest sampling");
        check(linear(image,-20,138)==alpha(image,0,138)&&linear(image,600,138)==alpha(image,511,138),"clamp-to-edge model retains edge coverage without wrap seams");
    }
    private interface Checked {void run()throws Exception;}
    private static void rejects(String label,Checked action)throws Exception{
        try{action.run();}catch(AssertionError|RuntimeException expected){mutants++;return;}
        throw new AssertionError("regression failed to reject "+label);
    }
    public static void main(String[] args)throws Exception{
        witnesses();String meta=text(resource(MASK+".mcmeta"));metadata(meta);
        String fragment=text(resource(SHADER+".fsh")),descriptor=text(resource(SHADER+".json"));shader(fragment,descriptor);
        route(resource(PREFIX+"ClassicAbilityHud.class"));pixels(resource(MASK));
        rejects("nearest filtering",()->metadata(meta.replace("\"blur\": true","\"blur\": false")));
        rejects("repeat wrapping",()->metadata(meta.replace("\"clamp\": true","\"clamp\": false")));
        rejects("reintroduced alpha cutoff",()->shader(fragment.replace("void main() {","void main() { if (vertexColor.a < .1) discard;"),descriptor));
        rejects("wrong fragment descriptor",()->shader(fragment,descriptor.replace("academy:classic_skill_alpha","minecraft:position_tex_color")));
        System.out.println("PASS "+checks+" compiled-route/official-filter/pixel checks; "+mutants+" material mutants rejected (no game or GL context)");
    }
}
