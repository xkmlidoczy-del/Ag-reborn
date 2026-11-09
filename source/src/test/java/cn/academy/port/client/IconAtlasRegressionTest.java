package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import javax.imageio.ImageIO;

/** Resource contract, not screenshot acceptance: registration, pixels, tier marks and original provenance. */
public final class IconAtlasRegressionTest {
    private static int checks;
    private static void check(boolean ok, String detail) { checks++; if (!ok) throw new AssertionError(detail); }
    public static void main(String[] args) throws Exception {
        var identities = new HashSet<String>(); var textures = new HashSet<String>(); var hashes = new HashSet<String>();
        check(SkillIconAtlas.ALL.size() == SkillCatalog.REGISTERED.size() && SkillIconAtlas.ALL.size() == 111 + cn.academy.port.canon.CanonSkills.ALL.size(), "all legacy and canon category-scoped records mapped");
        var manifestStream=IconAtlasRegressionTest.class.getResourceAsStream("/skill-icons-v2.json");
        check(manifestStream!=null,"original generation manifest present");
        var manifest=JsonParser.parseReader(new InputStreamReader(manifestStream,StandardCharsets.UTF_8)).getAsJsonObject();
        check(!manifest.get("external_artwork").getAsBoolean(),"no external imagery in generated icons");
        check(manifest.getAsJsonArray("icons").size()==111+cn.academy.port.canon.CanonSkills.ALL.size(),"all enabled semantic provenance records");
        var meanings=new java.util.HashMap<String,String>();
        var expectedHashes=new java.util.HashMap<String,String>();
        for(var e:manifest.getAsJsonArray("icons")) {
            var m=e.getAsJsonObject();check(m.get("original").getAsBoolean(),"every icon original");
            String key=m.get("category").getAsString()+":"+m.get("id").getAsString();
            check(meanings.put(key,m.get("meaning").getAsString())==null,"no duplicate manifest identity");
            expectedHashes.put(key,m.get("sha256").getAsString());
            check(m.get("meaning").getAsString().length()>15,"functional design rationale present");
        }
        for(var entry:SkillIconAtlas.ALL) {
            String key=entry.category()+":"+entry.skill();
            check(identities.add(key),"unique registered identity: "+key);
            check(textures.add(entry.texture().toString()),"unique category-scoped texture path: "+key);
            check(SkillIconAtlas.texture(entry.category(),entry.skill()).equals(entry.texture()),"public mapping resolves: "+key);
            check(meanings.containsKey(key),"semantic design entry: "+key);
            var skill=SkillCatalog.find(entry.category(),entry.skill()).orElseThrow();
            check(entry.level()==skill.level() && entry.active()==skill.controllable(),"preserved original level and activity");
            String path="/assets/"+entry.texture().getNamespace()+"/"+entry.texture().getPath();
            byte[] bytes;
            try(var stream=IconAtlasRegressionTest.class.getResourceAsStream(path)) {
                check(stream!=null,"resource exists: "+path);bytes=stream.readAllBytes();
            }
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            check(hashes.add(sha),"not a duplicate PNG: "+key);
            check(sha.equals(expectedHashes.get(key)),"manifest hash matches packaged bytes: "+key);
            var image=ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            check(image!=null && image.getWidth()==32 && image.getHeight()==32,"32px valid PNG");
            check(image.getColorModel().hasAlpha(),"transparent resource");
            int opaque=0,goldTop=0,goldBottom=0;
            for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
                int pixel=image.getRGB(x,y);int alpha=pixel>>>24;
                if(x==0||x==31||y==0||y==31)check(alpha==0,"transparent outer pixel: "+key);
                if(x>3&&x<29&&y>3&&y<29&&alpha>0)opaque++;
                if((pixel&0x00ffffff)==0x00ffcd68 && alpha>0){if(y<=3)goldTop++;if(y==30)goldBottom++;}
            }
            check(opaque>=25 && opaque<600,"nonempty readable central glyph");
            check(entry.level()<4 ? goldTop==0 : goldTop>=8,"LV4/LV5 top tier marker");
            check(entry.level()<5 ? goldBottom==0 : goldBottom>=8,"LV5 lower tier marker");
        }
        check(textures.size()==111+cn.academy.port.canon.CanonSkills.ALL.size() && hashes.size()==textures.size(),"distinct original sprites for the complete enabled catalog");
        check(!SkillIconAtlas.contains("bogus","railgun"),"invalid category rejected");
        System.out.println("IconAtlasRegressionTest: "+checks+" checks passed");
    }
}
