/* Port/source witness regression; AcademyCraft GPLv3 + additional terms, LambdaLib MIT. See noticed classpath witnesses. */
package cn.academy.port.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/** Current source + compiled draw + actual packaged shader binding. No Minecraft/GL initialization. */
public final class ClassicFontBindingRegressionTest {
    private static final String PACKAGE="cn/academy/port/client/";
    private static final String WITNESSES=PACKAGE+"classic-font-checkpoints/";
    private static final String SHADERS="assets/academy/shaders/core/";
    private static final String WITNESS_SHA="71912570c575e6701d08bac911aeb5523217f97749e8eaa96520126c595c9f34";
    private static final String NOTICE_SHA="f56554a2513e14cb97452ba79e16b1bc8b35439e03d1be26ae4ef978ffe5bf68";
    private static final String MIT_SHA="852bcc033a46c62f99fb5ffd43b3241ba2c0c440c6034aa2114505f2c4f03c4a";
    private static int checks,mutants;
    private static void require(boolean b,String reason){checks++;if(!b)throw new AssertionError(reason);}
    private static String text(byte[] data){return new String(data,StandardCharsets.UTF_8);}
    private static String compact(String s){return s.replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*","").replaceAll("\\s+","");}
    private static String sha(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    private static byte[] resource(String name)throws Exception{
        try(var in=ClassicFontBindingRegressionTest.class.getClassLoader().getResourceAsStream(name)){
            require(in!=null,"missing required classpath resource: "+name);return in.readAllBytes();
        }
    }
    private static void pinned(byte[] bytes,String expected,String name)throws Exception{require(sha(bytes).equals(expected),"changed or corrupt noticed witness: "+name);}
    private static void witnesses(byte[] witness,byte[] notice,byte[] mit)throws Exception{
        pinned(witness,WITNESS_SHA,"original-text.json");pinned(notice,NOTICE_SHA,"NOTICE");pinned(mit,MIT_SHA,"LAMBDALIB-LICENSE");
        JsonObject root=JsonParser.parseString(text(witness)).getAsJsonObject();
        require(root.keySet().equals(Set.of("font","cpbar","preset")),"witness scope must remain bounded");
        String font=excerpts(root,"font"),cp=excerpts(root,"cpbar"),preset=excerpts(root,"preset");
        require(font.contains("glDisable(GL_ALPHA_TEST)"),"canonical font disables alpha test");
        require(font.contains("1 + metrics.getAscent")&&font.contains("g.clearRect(0, 0, charSize, charSize)"),"canonical raster baseline/background");
        require(cp.contains("else if (showingNumbers)")&&cp.contains("(dt - 200) / 400f"),"canonical hold-only number timing");
        require(cp.contains("option.color.a = 0.6f * mAlpha * alpha")&&cp.contains("if(time - presetChangeTime < preset_wait)"),"canonical alpha and transient presets");
        require(preset.contains("content = c == null ? \"\" : c.getHintText()"),"canonical empty editor slots stay blank");
        for(String key:root.keySet()){
            JsonObject entry=root.getAsJsonObject(key);
            require(entry.get("copyright_notice").getAsString().contains("Copyright (c) Lambda Innovation, 2013-2016"),"canonical notice preserved: "+key);
            require(entry.get("full_file_sha256").getAsString().matches("[0-9a-f]{64}"),"canonical full-file digest: "+key);
        }
    }
    private static String excerpts(JsonObject root,String key){var b=new StringBuilder();for(var e:root.getAsJsonObject(key).getAsJsonArray("excerpts"))b.append(e.getAsJsonObject().get("text").getAsString());return b.toString();}
    private static void binding(String fontSource,String registrationSource,String fragment,String descriptor,byte[] fontClass,byte[] shaderClass)throws Exception{
        String font=compact(fontSource),register=compact(registrationSource),fsh=compact(fragment);
        require(font.contains("ShaderInstanceshader=mono?ClassicHudShaders.mono:ClassicHudShaders.font;"),"ordinary font must bind no-discard shader; monochrome keeps mono");
        require(font.contains("color.a()<=0"),"only fully invisible color can skip font drawing");
        require(font.contains("color,shader,null)"),"selected shader reaches actual glyph canvas draw");
        require(register.contains("font=null;"),"font shader reset on reload");
        require(register.contains("id(\"classic_font\"),DefaultVertexFormat.POSITION_TEX_COLOR),s->font=s"),"font shader descriptor registered with actual field callback");
        require(!fsh.contains("discard"),"no alpha cutoff in source font pass");
        require(fsh.contains("fragColor=texture(Sampler0,texCoord0)*vertexColor*ColorModulator;"),"source coverage and colored tint product");
        require(fsh.equals("#version150uniformsampler2DSampler0;uniformvec4ColorModulator;invec2texCoord0;invec4vertexColor;outvec4fragColor;voidmain(){fragColor=texture(Sampler0,texCoord0)*vertexColor*ColorModulator;}"),"exact bounded no-discard fragment program");
        JsonObject json=JsonParser.parseString(descriptor).getAsJsonObject();
        require(json.get("vertex").getAsString().equals("minecraft:position_tex_color"),"compatible POSITION_TEX_COLOR vertex shader");
        require(json.get("fragment").getAsString().equals("academy:classic_font"),"descriptor references actual source font fragment");
        var samplers=json.getAsJsonArray("samplers");require(samplers.size()==1&&samplers.get(0).getAsJsonObject().get("name").getAsString().equals("Sampler0"),"font sampler binding");
        Map<String,Integer> expected=Map.of("ModelViewMat",16,"ProjMat",16,"ColorModulator",4);
        var uniforms=json.getAsJsonArray("uniforms");require(uniforms.size()==3,"exact source-compatible font uniforms");
        var seen=new java.util.HashSet<String>();
        for(var u:uniforms){
            var obj=u.getAsJsonObject();String name=obj.get("name").getAsString();
            require(seen.add(name)&&expected.containsKey(name),"known unique shader uniform");
            require(obj.get("type").getAsString().equals(name.equals("ColorModulator")?"float":"matrix4x4"),"uniform type "+name);
            var values=obj.getAsJsonArray("values");
            require(obj.get("count").getAsInt()==expected.get(name)&&values.size()==expected.get(name),"uniform shape "+name);
            for(int i=0;i<values.size();i++)require(values.get(i).getAsDouble()==(name.equals("ColorModulator")||i%5==0?1:0),"source-compatible default uniform value "+name+"["+i+"]");
        }
        // Untouched source-fidelity-critical raster/upload/filter/ownership obligations, checked on CURRENT source.
        for(String snippet:new String[]{"FONT_SIZE=24,CHAR_SIZE=(int)(FONT_SIZE*1.4)","BufferedImage.TYPE_INT_ARGB","g.setBackground(newColor(255,255,255,0))","g.drawString(newString(Character.toChars(key)),3,1+metrics.getAscent())","(argb&0xff00ff00)|((argb>>>16)&255)|((argb&255)<<16)","prepareImage(texture.getId(),5,CHAR_SIZE,CHAR_SIZE)","texture.upload();texture.setFilter(true,true)","glGenerateMipmap","GL_TEXTURE_LOD_BIAS,-.65f","textureOwner=NEXT_TEXTURE_OWNER.getAndIncrement()"})require(font.contains(snippet),"current raster/upload/ownership obligation: "+snippet);
        ClassFile glyph=new ClassFile(fontClass);ClassFile shaders=new ClassFile(shaderClass);
        require(glyph.hasGetStatic("draw",PACKAGE+"ClassicHudShaders","font"),"compiled draw method must read actual no-discard font shader field");
        require(glyph.hasGetStatic("draw",PACKAGE+"ClassicHudShaders","mono"),"compiled draw method retains monochrome route");
        require(shaders.hasStringLoad("register","classic_font"),"compiled shader registration contains real font descriptor id");
    }
    @FunctionalInterface private interface Checked{void run()throws Exception;}
    private static void rejects(String label,Checked action)throws Exception{
        try{action.run();}catch(AssertionError|RuntimeException|java.io.IOException expected){mutants++;return;}
        throw new AssertionError("mutation escaped binding check: "+label);
    }
    private static String replace(String s,String from,String to){require(s.contains(from),"mutation target exists: "+from);return s.replace(from,to);}
    private static void sameResource(Path root,String name,byte[] actual)throws Exception{
        Path source=root.resolve("src/main/resources").resolve(name);
        require(Files.isRegularFile(source),"missing current source resource: "+source);
        require(sha(Files.readAllBytes(source)).equals(sha(actual)),"classpath shader differs from CURRENT resource file: "+name);
    }
    public static void main(String[] args)throws Exception{
        String property=System.getProperty("academy.sourceRoot");require(property!=null&&!property.isBlank(),"set -Dacademy.sourceRoot to the current project root; no fallback checkout path is allowed");
        Path root=Path.of(property).toAbsolutePath().normalize();
        String font=Files.readString(root.resolve("src/main/java/"+PACKAGE+"ClassicHudFont.java"));
        String registration=Files.readString(root.resolve("src/main/java/"+PACKAGE+"ClassicHudShaders.java"));
        byte[] fragment=resource(SHADERS+"classic_font.fsh"),descriptor=resource(SHADERS+"classic_font.json");
        sameResource(root,SHADERS+"classic_font.fsh",fragment);sameResource(root,SHADERS+"classic_font.json",descriptor);
        byte[] fontClass=resource(PACKAGE+"ClassicHudFont.class"),shaderClass=resource(PACKAGE+"ClassicHudShaders.class");
        byte[] witness=resource(WITNESSES+"original-text.json"),notice=resource(WITNESSES+"NOTICE"),mit=resource(WITNESSES+"LAMBDALIB-LICENSE");
        witnesses(witness,notice,mit);String fsh=text(fragment),json=text(descriptor);
        binding(font,registration,fsh,json,fontClass,shaderClass);
        rejects("vanilla route",()->binding(replace(font,"ClassicHudShaders.font;","null;"),registration,fsh,json,fontClass,shaderClass));
        rejects("alpha discard",()->binding(font,registration,replace(fsh,"void main() {","void main() { if(vertexColor.a < 0.1) discard;"),json,fontClass,shaderClass));
        rejects("lost tint",()->binding(font,registration,replace(fsh,"texture(Sampler0,texCoord0) * vertexColor * ColorModulator","vec4(1.0)"),json,fontClass,shaderClass));
        rejects("missing registration",()->binding(font,replace(registration,"id(\"classic_font\")","id(\"other\")"),fsh,json,fontClass,shaderClass));
        rejects("wrong sampler",()->binding(font,registration,fsh,replace(json,"Sampler0","Sampler1"),fontClass,shaderClass));
        rejects("overridden fragment output",()->binding(font,registration,replace(fsh,"ColorModulator;\n}","ColorModulator; fragColor=vec4(0.0);\n}"),json,fontClass,shaderClass));
        rejects("stale classpath shader",()->sameResource(root,SHADERS+"classic_font.fsh",(fsh+"\n// stale byte mismatch").getBytes(StandardCharsets.UTF_8)));
        rejects("wrong fragment",()->binding(font,registration,fsh,replace(json,"academy:classic_font","academy:classic_mono"),fontClass,shaderClass));
        rejects("wrong vertex",()->binding(font,registration,fsh,replace(json,"minecraft:position_tex_color","minecraft:position_color"),fontClass,shaderClass));
        byte[] corrupt=witness.clone();corrupt[corrupt.length/2]^=1;rejects("corrupt source witness",()->witnesses(corrupt,notice,mit));
        byte[] corruptNotice=notice.clone();corruptNotice[0]^=1;rejects("changed notice",()->witnesses(witness,corruptNotice,mit));
        rejects("missing witness",()->resource(WITNESSES+"required-missing.json"));
        var zeroDefaults=JsonParser.parseString(json).getAsJsonObject();zeroDefaults.getAsJsonArray("uniforms").get(2).getAsJsonObject().getAsJsonArray("values").set(3,new com.google.gson.JsonPrimitive(0));
        rejects("zero default color alpha",()->binding(font,registration,fsh,zeroDefaults.toString(),fontClass,shaderClass));
        rejects("invalid shader JSON",()->binding(font,registration,fsh,"{}",fontClass,shaderClass));
        rejects("stale compiled draw",()->binding(font,registration,fsh,json,renameUtf8(fontClass,"font","gone"),shaderClass));
        rejects("changed glyph mip count",()->binding(replace(font,"texture.getId(),5,CHAR_SIZE","texture.getId(),0,CHAR_SIZE"),registration,fsh,json,fontClass,shaderClass));
        System.out.println("ClassicFontBindingRegressionTest: "+checks+" source/classpath/witness checks passed; "+mutants+" fail-closed mutations rejected (no game launch)");
    }
    /** Minimal JVM class reader, avoiding ASM/Minecraft initialization; bytecode instructions are decoded, not byte-searched. */
    private static final class ClassFile {
        final int[] tag,a,b;final String[] utf;final Map<String,byte[]> methods=new java.util.HashMap<>();
        ClassFile(byte[] bytes)throws Exception{
            var in=new DataInputStream(new ByteArrayInputStream(bytes));require(in.readInt()==0xCAFEBABE,"valid compiled class magic");in.readUnsignedShort();in.readUnsignedShort();int count=in.readUnsignedShort();tag=new int[count];a=new int[count];b=new int[count];utf=new String[count];
            for(int i=1;i<count;i++)switch(tag[i]=in.readUnsignedByte()){
                case 1 -> utf[i]=in.readUTF();case 3,4 -> in.readInt();case 5,6 -> {in.readLong();i++;}
                case 7,8,16,19,20 -> a[i]=in.readUnsignedShort();case 9,10,11,12,17,18 -> {a[i]=in.readUnsignedShort();b[i]=in.readUnsignedShort();}
                case 15 -> {a[i]=in.readUnsignedByte();b[i]=in.readUnsignedShort();}default -> throw new AssertionError("unsupported class constant tag");
            }
            in.readUnsignedShort();in.readUnsignedShort();in.readUnsignedShort();for(int n=in.readUnsignedShort();n-->0;)in.readUnsignedShort();
            members(in,false);members(in,true);
        }
        void members(DataInputStream in,boolean method)throws Exception{
            for(int n=in.readUnsignedShort();n-->0;){in.readUnsignedShort();String name=utf[in.readUnsignedShort()];in.readUnsignedShort();for(int k=in.readUnsignedShort();k-->0;){String attr=utf[in.readUnsignedShort()];int len=in.readInt();byte[] data=in.readNBytes(len);require(data.length==len,"complete class attribute");if(method&&attr.equals("Code")){var code=new DataInputStream(new ByteArrayInputStream(data));code.readUnsignedShort();code.readUnsignedShort();methods.put(name,code.readNBytes(code.readInt()));}}}
        }
        boolean hasGetStatic(String method,String owner,String field){return instruction(method,0xB2,idx->tag[idx]==9&&owner.equals(utf[a[a[idx]]])&&field.equals(utf[a[b[idx]]]));}
        boolean hasStringLoad(String method,String value){return instruction(method,-1,idx->tag[idx]==8&&value.equals(utf[a[idx]]));}
        interface IndexCheck{boolean test(int index);}
        boolean instruction(String name,int wanted,IndexCheck match){
            byte[] code=methods.get(name);require(code!=null,"compiled method exists: "+name);
            for(int p=0;p<code.length;){int op=code[p]&255;int idx=op==0x12?code[p+1]&255:(op==0x13||op==0xB2)?u2(code,p+1):-1;
                if(idx>0&&(wanted==op||(wanted==-1&&(op==0x12||op==0x13)))&&match.test(idx))return true;
                int len=length(code,p,op);require(len>0&&p+len<=code.length,"valid bytecode instruction extent");p+=len;
            }return false;
        }
        static int length(byte[] c,int p,int op){
            if(op==0xAA||op==0xAB){int q=(p+4)&~3;return op==0xAA?q-p+12+4*(i4(c,q+8)-i4(c,q+4)+1):q-p+8+8*i4(c,q+4);}
            if(op==0xC4)return (c[p+1]&255)==0x84?6:4;
            if(op==0xB9||op==0xBA||op==0xC8||op==0xC9)return 5;if(op==0xC5)return 4;
            if(op==0x11||op==0x13||op==0x14||op==0x84||(op>=0x99&&op<=0xA8)||(op>=0xB2&&op<=0xB8)||op==0xBB||op==0xBD||op==0xC0||op==0xC1||op==0xC6||op==0xC7)return 3;
            if(op==0x10||op==0x12||(op>=0x15&&op<=0x19)||(op>=0x36&&op<=0x3A)||op==0xA9||op==0xBC)return 2;return 1;
        }
        static int u2(byte[] c,int p){return ((c[p]&255)<<8)|(c[p+1]&255);}static int i4(byte[] c,int p){return (c[p]<<24)|((c[p+1]&255)<<16)|((c[p+2]&255)<<8)|(c[p+3]&255);}
    }
    private static byte[] renameUtf8(byte[] original,String from,String to)throws Exception{
        require(from.length()==to.length(),"same-width bytecode mutation");byte[] copy=original.clone();var in=new DataInputStream(new ByteArrayInputStream(copy));in.skipNBytes(8);int count=in.readUnsignedShort();
        for(int i=1;i<count;i++)switch(in.readUnsignedByte()){
            case 1 -> {int size=in.readUnsignedShort();int start=copy.length-in.available();byte[] bytes=in.readNBytes(size);if(text(bytes).equals(from))System.arraycopy(to.getBytes(StandardCharsets.UTF_8),0,copy,start,size);}
            case 3,4,9,10,11,12,17,18 -> in.skipNBytes(4);case 5,6 -> {in.skipNBytes(8);i++;}case 7,8,16,19,20 -> in.skipNBytes(2);case 15 -> in.skipNBytes(3);default -> throw new AssertionError("unknown constant tag");
        }return copy;
    }
}
