package cn.academy.port.client;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.*;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Exercises actual Minecraft BlendMode bytecode with only GL calls redirected to recording hooks. No game/GL context. */
public final class ClassicPauseBlendRegressionTest {
    private static final String RENDER_SYSTEM="com/mojang/blaze3d/systems/RenderSystem";
    private static final String ORIGINAL="com/mojang/blaze3d/shaders/BlendMode";
    private static final String ORACLE="cn/academy/port/client/isolatedpause/BlendModeOracle";
    private static int checks;
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
    private static byte[] resource(String path)throws Exception{
        try(var in=ClassicPauseBlendRegressionTest.class.getClassLoader().getResourceAsStream(path)){
            check(in!=null,"missing mandatory compiled class: "+path);return in.readAllBytes();
        }
    }
    private static void canvasTeardown(byte[] data){
        var cls=new ClassNode();new ClassReader(data).accept(cls,ClassReader.SKIP_FRAMES|ClassReader.SKIP_DEBUG);
        var end=cls.methods.stream().filter(m->m.name.equals("end")).findFirst().orElseThrow();
        boolean disabled=false;
        for(var instruction:end.instructions){
            check(!(instruction instanceof JumpInsnNode),"canvas teardown must unconditionally restore blend state");
            if(instruction instanceof MethodInsnNode call&&call.owner.equals(RENDER_SYSTEM)){
                if(call.name.equals("disableBlend"))disabled=true;
                if(call.name.equals("enableBlend"))disabled=false;
            }
            if(instruction.getOpcode()==Opcodes.RETURN)check(disabled,"compiled ClassicHudCanvas.end must leave blending disabled for native menu blur");
        }
    }
    public static final class Hooks {
        public static boolean blending;
        public static int disableCalls,srcRgb=1,dstRgb=0,srcAlpha=1,dstAlpha=0;
        public static void enableBlend(){blending=true;}
        public static void disableBlend(){blending=false;disableCalls++;}
        public static void blendEquation(int equation){check(equation==32774,"source blur uses additive blend equation");}
        public static void blendFunc(int src,int dst){srcRgb=src;dstRgb=dst;srcAlpha=src;dstAlpha=dst;}
        public static void blendFuncSeparate(int sr,int dr,int sa,int da){srcRgb=sr;dstRgb=dr;srcAlpha=sa;dstAlpha=da;}
    }
    private static Class<?> actualBlendOracle(byte[] original){
        var writer=new ClassWriter(0);
        var mapping=new Remapper(){@Override public String map(String name){
            if(name.equals(ORIGINAL))return ORACLE;
            if(name.equals(RENDER_SYSTEM))return Hooks.class.getName().replace('.','/');
            return name;
        }};
        new ClassReader(original).accept(new ClassRemapper(writer,mapping),0);
        byte[] isolated=writer.toByteArray();
        class Loader extends ClassLoader {
            Loader(){super(ClassicPauseBlendRegressionTest.class.getClassLoader());}
            Class<?> define(){return defineClass(ORACLE.replace('/','.'),isolated,0,isolated.length);}
        }
        return new Loader().define();
    }
    private static void sourceHudBlend(){
        Hooks.enableBlend();
        // Exact RenderSystem.defaultBlendFunc RGB=SRC_ALPHA/ONE_MINUS_SRC_ALPHA, alpha=ONE/ZERO.
        Hooks.blendFuncSeparate(770,771,1,0);
    }
    private static double[] blurOverCleared(double rgb,double alpha){
        if(!Hooks.blending)return new double[]{rgb,alpha};
        check(Hooks.srcRgb==770&&Hooks.dstRgb==771&&Hooks.srcAlpha==1&&Hooks.dstAlpha==0,"leaked HUD default blending reaches blur");
        return new double[]{rgb*alpha,alpha};
    }
    private static double[] sixPasses(Object opaque,double rgb,double alpha)throws Exception{
        var apply=opaque.getClass().getMethod("apply");
        for(int pass=0;pass<6;pass++){
            apply.invoke(opaque);double[] output=blurOverCleared(rgb,alpha);rgb=output[0];alpha=output[1];
        }
        return new double[]{rgb,alpha};
    }
    public static void main(String[] args)throws Exception{
        canvasTeardown(resource("cn/academy/port/client/ClassicHudCanvas.class"));
        byte[] original=resource(ORIGINAL+".class");
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        check(hash.equals("281e5fb3d059662f861f1038b4327c78d87d3f860cf2da05e20036203addecb6"),"pinned actual Minecraft1.21.1 BlendMode class witness");
        Class<?> oracle=actualBlendOracle(original);Object opaque=oracle.getConstructor().newInstance();var apply=oracle.getMethod("apply");
        apply.invoke(opaque);check(!Hooks.blending&&Hooks.disableCalls==1,"first opaque effect explicitly disables blending");
        sourceHudBlend();int before=Hooks.disableCalls;
        apply.invoke(opaque);
        check(Hooks.blending&&Hooks.disableCalls==before,"actual cached opaque BlendMode does not undo direct HUD enableBlend");
        check(sixPasses(opaque,.8,0)[0]==0,"transparent-center mask alpha causes black world with leaked blending");
        sourceHudBlend();check(sixPasses(opaque,.8,26/255.0)[0]<.000001,"six inherited-alpha blur passes blacken the old mask corners");
        sourceHudBlend();check(Math.abs(sixPasses(opaque,.8,1)[0]-.8)<1e-12,"discarded center/high-alpha HUD remain visible in the negative control");
        for(double alpha:new double[]{0,1/255.0,24/255.0,26/255.0,77/255.0,1}){
            sourceHudBlend();Hooks.disableBlend(); // Staged canvas end; do not alter the original opaque cache.
            double[] output=sixPasses(opaque,.8,alpha);
            check(!Hooks.blending&&Math.abs(output[0]-.8)<1e-12,"explicit teardown prevents menu blackout at every mask alpha");
            check(Math.abs(output[1]-alpha)<1e-12,"opaque blur retains the intended source alpha");
        }
        System.out.println("PASS "+checks+" compiled teardown/actual opaque-cache/framebuffer-alpha checks (no game or GL context)");
    }
}
