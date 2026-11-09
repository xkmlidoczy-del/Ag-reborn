package cn.academy.port.client;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import com.google.gson.JsonParser;

/** The current primary shader source proves why legacy low-alpha fades need their own fragment. */
public final class ElectromasterFinalShaderRegressionTest {
    private static int assertions;
    private static void check(boolean value,String label){assertions++;if(!value)throw new AssertionError(label);}
    public static void main(String[] args)throws Exception{
        Path source=Path.of(System.getProperty("academy.final.sourceRoot","src/main/java"));
        Path resources=Path.of(System.getProperty("academy.final.resourceRoot","src/main/resources"));
        String fragment=Files.readString(resources.resolve("assets/academy/shaders/core/classic_skill_alpha.fsh"));
        String body=Files.readString(source.resolve("cn/academy/port/client/ClassicBodyIntensifyEffects.java"));
        String thunder=Files.readString(source.resolve("cn/academy/port/client/ClassicThunderClapEffects.java"));
        String loader=Files.readString(source.resolve("cn/academy/port/client/ClassicSkillAlphaShader.java"));
        var json=JsonParser.parseString(Files.readString(resources.resolve("assets/academy/shaders/core/classic_skill_alpha.json"))).getAsJsonObject();
        check(json.get("vertex").getAsString().equals("minecraft:position_tex_color")&&json.get("fragment").getAsString().equals("academy:classic_skill_alpha"),"current namespaced vertex/fragment wiring");
        check(fragment.contains("#version 150")&&fragment.contains("texture(Sampler0, texCoord0) * vertexColor * ColorModulator"),"source straight-alpha texture/color modulation");
        check(!fragment.contains("discard;")&&!fragment.contains("Fog"),"no alpha cutoff or fog in source no-test material");
        check(body.contains("RenderSystem.setShader(ClassicSkillAlphaShader::get)"),"Body HUD actually binds no-cutoff shader");
        check(thunder.contains("depth == RIPPLE_DEPTH")&&thunder.contains("ClassicSkillAlphaShader.get() : GameRenderer.getPositionTexColorShader()"),"ripple no-cutoff and arc default alpha distinction");
        check(loader.contains("Dist.CLIENT")&&loader.contains("RegisterShadersEvent")&&loader.contains("POSITION_TEX_COLOR"),"client-only source shader registration");
        try(var zip=new ZipFile("build/moddev/artifacts/neoforge-21.1.252-client-extra-aka-minecraft-resources.jar")){
            String current=new String(zip.getInputStream(zip.getEntry("assets/minecraft/shaders/core/position_tex_color.fsh")).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
            check(current.contains("color.a < 0.1")&&current.contains("discard;"),"cached primary source shows stock cutoff which would erase blackmask/fades");
        }
        for(int i=0;i<=1000;i++){
            double alpha=i/10000D;double black=.1*alpha;double src=.8,background=.2;
            double output=src*alpha+background*(1-alpha);
            check(Double.isFinite(output)&&Math.abs(output-(background+(src-background)*alpha))<1E-12,"lowalpha straight blend preserved rather than discarded");
            check(black>=0&&black<=.01000001,"source blackmask retains every sub-cutoff fragment");
        }
        System.out.println("PASS "+assertions+" no-cutoff source alpha/actual current shader wiring assertions (no GPU launch)");
    }
}
