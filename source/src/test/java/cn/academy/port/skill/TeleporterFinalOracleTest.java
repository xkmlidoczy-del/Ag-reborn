package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import javax.tools.ToolProvider;
import static cn.academy.port.skill.TeleporterFinalRules.*;

/** Differential against original source expressions and original Java method bodies.
 * Flash getDest, LambdaLib Motion3D, scalar expressions and strict line-box intersection code
 * come from hash-pinned classpath source bytes. Additional oracle methods accept the tuned
 * endpoints as arguments while preserving the original expression and geometry.
 * This does not execute Minecraft, Scala, Gradle or a reference checkout. */
public final class TeleporterFinalOracleTest {
    private static final String PACKAGE = "cn.academy.port.skill.teleporterfinaloracle";
    private static int checks;

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    private static void exact(float actual, float original, String why) {
        check(Float.floatToIntBits(actual) == Float.floatToIntBits(original), why + ": " + actual + " != " + original);
    }

    private static String expression(String source, String regex) {
        var match = Pattern.compile(regex).matcher(source);
        if (!match.find()) throw new AssertionError("Missing original expression: " + regex);
        return match.group(1).trim();
    }

    /** Copies a complete original Java method, including its unchanged body. */
    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new AssertionError("Missing original method " + signature);
        int open = source.indexOf('{', start), depth = 1, end = open + 1;
        while (depth != 0 && end < source.length()) {
            char ch = source.charAt(end++);
            if (ch == '{') depth++;
            if (ch == '}') depth--;
        }
        if (depth != 0) throw new AssertionError("Unbalanced original method " + signature);
        return source.substring(start, end);
    }

    private static URLClassLoader oracle() throws Exception {
        String location = TeleporterFinalSourceFixtures.location();
        String shift = TeleporterFinalSourceFixtures.shift();
        String flash = TeleporterFinalSourceFixtures.flashing();
        String vector = TeleporterFinalSourceFixtures.lambda("src/main/java/cn/lambdalib/util/generic/VecUtils.java");
        String motion = TeleporterFinalSourceFixtures.lambda("src/main/java/cn/lambdalib/util/helper/Motion3D.java");
        String math = TeleporterFinalSourceFixtures.lambda("src/main/java/cn/lambdalib/util/generic/MathUtils.java");
        Map<String, String> expressions = new LinkedHashMap<>();
        for (String id : new String[]{"Damage", "Range", "Consumption", "Overload"}) {
            expressions.put("shift" + id, expression(shift, "get" + id + "\\(exp: Float\\): Float = (lerpf\\([^\\n]+\\))"));
        }
        expressions.put("shiftCooldown", expression(shift, "ctx.setCooldown\\((lerpf\\(100, 60, exp\\))\\.toInt\\)"));
        Map<String, String> fields = Map.of("flashConsumption", "consumption", "flashStartOverload", "overload_start",
                "flashStartConsumption", "consumption_start", "flashMaxTime", "max_time", "flashCooldown", "cooldown_time");
        for (var field : fields.entrySet()) {
            expressions.put(field.getKey(), expression(flash, "\\b" + field.getValue() + " = (?:\\(int\\) )?(lerpf\\([^;]+\\));"));
        }
        expressions.put("flashRange", expression(flash, "double dist = (lerpf\\([^;]+\\));"));
        expressions.put("locationCooldown", expression(location, "ctx.setCooldown\\(MathUtils\\.(lerpf\\([^\\n]+\\))\\.toInt\\)").replace("ctx.getSkillExp", "exp"));
        String locationCp = expression(location, "(MathUtils\\.lerpf\\(200, 150, data.getSkillExp\\(this\\)\\) \\* dimPenalty \\*\\s*math.max\\([^\\n]+\\))")
                .replace("data.getSkillExp(this)", "exp").replace("math.", "Math.");
        // The final closing parenthesis belongs to Scala's (overload, CP) tuple.
        locationCp = locationCp.substring(0, locationCp.length() - 1);
        String dirs = expression(flash, "(?s)(private static final Vec3\\[\\] dirs = new Vec3\\[\\] \\{.*?\\n    \\};)");
        String direction = expression(flash, "(?s)(Vec3 dir = VecUtils.copy\\(dirs\\[keyid\\]\\);.*?dir.rotateAroundY\\([^;]+;)" );
        StringBuilder original = new StringBuilder("package " + PACKAGE + ";\nimport java.util.Random;\nimport static " + PACKAGE + ".MathUtils.*;\npublic class OriginalFinal {\n");
        original.append(hosts());
        for (var scalar : expressions.entrySet()) {
            original.append("public static float ").append(scalar.getKey()).append("(float exp){return ").append(scalar.getValue()).append(";}\n");
        }
        for (String name : new String[]{"shiftRange", "flashMaxTime", "flashRange"}) {
            var parameters = Pattern.compile("lerpf\\(\\s*[-.0-9fF]+\\s*,\\s*[-.0-9fF]+\\s*,\\s*exp\\s*\\)").matcher(expressions.get(name));
            if (!parameters.matches()) throw new AssertionError("Unexpected original interpolation: " + name);
            original.append("public static float tuned_").append(name).append("(float exp,float novice,float mastered){return ")
                    .append(parameters.replaceFirst("lerpf(novice, mastered, exp)")).append(";}\n");
        }
        original.append("public static float locationConsumption(float exp,float distance,boolean cross){int dimPenalty=cross?2:1;return ").append(locationCp).append(";}\n");
        String expExpr = expression(location, "val expincr = if \\(([^\\n]+)" );
        var xp = Pattern.compile("([^)]*)\\) ([^ ]+) else ([^ ]+)").matcher(expExpr);
        if (!xp.matches()) throw new AssertionError("Original Location experience syntax");
        original.append("public static float locationExperience(double dist){return ").append(xp.group(1)).append('?').append(xp.group(2)).append(':').append(xp.group(3)).append(";}\n");
        original.append(dirs).append('\n');
        original.append("public static double[] direction(int keyid,float yaw,float pitch){EntityPlayer player=new EntityPlayer();player.rotationYaw=yaw;player.rotationPitch=pitch;")
                .append(direction).append("return new double[]{dir.xCoord,dir.yCoord,dir.zCoord};}\n");
        original.append("static class VecUtils {\n");
        for (String signature : new String[]{"public static Vec3 vec(", "public static Vec3 multiply(", "public static Vec3 lerp(",
                "public static Vec3 neg(", "public static Vec3 add(", "public static Vec3 subtract(", "public static Vec3 copy(Vec3 v)",
                "private static Vec3 getIntersection(", "private static boolean inBox(", "public static Vec3 checkLineBox("}) {
            original.append(method(vector, signature)).append('\n');
        }
        original.append("}\npublic static boolean lineBox(double[] min,double[] max,double[] a,double[] b){return VecUtils.checkLineBox(v(min),v(max),v(a),v(b))!=null;}\n");
        original.append("static Vec3 v(double[] a){return VecUtils.vec(a[0],a[1],a[2]);}\n");
        String originalMotion = motion.replaceFirst("(?m)^package [^;]+;", "").replaceAll("(?m)^import [^;]+;", "")
                .replace("public class Motion3D", "static class Motion3D");
        original.append(originalMotion).append('\n');
        original.append("static class MainContext {float exp,overloadKeep;int max_time,ticks;boolean terminated;EntityPlayer player;ContextHost ctx=new ContextHost();MainContext(float e,EntityPlayer p){exp=e;player=p;max_time=(int)flashMaxTime(e);}void terminate(){terminated=true;}\n")
                .append(method(flash, "private Vec3 getDest(int keyid)")).append('\n')
                .append(method(flash, "void serverTick()")).append("}\n");
        original.append("public static int[] flashTickAtLimit(float exp,int limit,int ticks,float overload,float keep){MainContext m=new MainContext(exp,new EntityPlayer());m.max_time=limit;m.ticks=ticks;m.overloadKeep=keep;m.ctx.cpData.overload=overload;m.serverTick();return new int[]{m.terminated?1:0,m.ticks,Float.floatToIntBits(m.ctx.cpData.overload)};}\n");
        String castDestination = expression(shift, "Array\\[Int\\]\\((mo\\.px\\.toInt, mo\\.py\\.toInt, mo\\.pz\\.toInt)\\)")
                .replaceAll("(mo\\.p[xyz])\\.toInt", "(int)$1");
        original.append("public static int[] shiftFallbackAtRange(double[] eye,double[] direction,float range){Motion3D mo=new Motion3D(v(eye),v(direction)).move(range);return new int[]{")
                .append(castDestination).append("};}\n");
        original.append("public static double[] flashBlock(double[] hit,int side,int blockY,boolean clear){EntityPlayer p=new EntityPlayer();p.worldObj.air=clear;MovingObjectPosition mop=new MovingObjectPosition();mop.hitVec=v(hit);mop.sideHit=side;mop.blockY=blockY;mop.typeOfHit=MovingObjectType.BLOCK;Raytrace.hit=mop;Vec3 result=new MainContext(0,p).getDest(1);return new double[]{result.xCoord,result.yCoord,result.zCoord};}\n");
        original.append("public static double[] flashMiss(double[] feet,float eyeHeight,float yaw,float pitch,int keyid,float exp){EntityPlayer p=new EntityPlayer();p.posX=feet[0];p.posY=feet[1];p.posZ=feet[2];p.eyeHeight=eyeHeight;p.rotationYaw=yaw;p.rotationPitch=pitch;Raytrace.hit=null;Vec3 result=new MainContext(exp,p).getDest(keyid);return new double[]{result.xCoord,result.yCoord,result.zCoord};}\n");
        original.append("}\n");
        math = math.replace("package cn.lambdalib.util.generic;", "package " + PACKAGE + ";")
                .replace("import net.minecraft.util.Vec3;", "import " + PACKAGE + ".OriginalFinal.Vec3;");
        Path temp = Files.createTempDirectory("academy-teleporter-final-original-oracle-");
        Files.writeString(temp.resolve("OriginalFinal.java"), original);
        Files.writeString(temp.resolve("MathUtils.java"), math);
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new AssertionError("Official cached JDK compiler required");
        int result = compiler.run(null, null, null, "-proc:none", "-d", temp.toString(), temp.resolve("OriginalFinal.java").toString(), temp.resolve("MathUtils.java").toString());
        if (result != 0) throw new AssertionError("Original source-expression oracle compilation failed at " + temp);
        return new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()}, TeleporterFinalOracleTest.class.getClassLoader());
    }

    private static String hosts() {
        return """
            public enum Side { CLIENT,SERVER } public @interface SideOnly { Side value(); }
            public static final class Vec3 {
                public double xCoord,yCoord,zCoord;
                Vec3(double x,double y,double z){xCoord=x;yCoord=y;zCoord=z;}
                public static Vec3 createVectorHelper(double x,double y,double z){return new Vec3(x,y,z);}
                public void rotateAroundZ(float a){float c=MathHelper.cos(a),s=MathHelper.sin(a);double x=xCoord*c+yCoord*s,y=yCoord*c-xCoord*s;xCoord=x;yCoord=y;}
                public void rotateAroundY(float a){float c=MathHelper.cos(a),s=MathHelper.sin(a);double x=xCoord*c+zCoord*s,z=zCoord*c-xCoord*s;xCoord=x;zCoord=z;}
            }
            static final class MathHelper {
                private static final float[] TABLE=new float[65536];
                static {for(int i=0;i<65536;i++)TABLE[i]=(float)Math.sin(i*Math.PI*2/65536);}
                static float sin(float a){return TABLE[(int)(a*10430.378F)&65535];}
                static float cos(float a){return TABLE[(int)(a*10430.378F+16384F)&65535];}
                static float sqrt_float(float a){return (float)Math.sqrt(a);}
            }
            static class World {boolean isRemote,air=true;boolean isAirBlock(int x,int y,int z){return air;}}
            static class Entity {double posX,posY,posZ,motionX,motionY,motionZ;float rotationYaw,rotationPitch,prevRotationYaw,prevRotationPitch,eyeHeight=1.62F;World worldObj=new World();float getEyeHeight(){return eyeHeight;}float getRotationYawHead(){return rotationYaw;}void setPosition(double x,double y,double z){posX=x;posY=y;posZ=z;}}
            static class EntityLivingBase extends Entity {} static class EntityPlayer extends EntityLivingBase {}
            static class Minecraft {EntityPlayer thePlayer;static Minecraft getMinecraft(){return new Minecraft();}}
            static final class Objects {static Helper toStringHelper(Object o){return new Helper();}static class Helper {Helper add(String k,Object v){return this;}}}
            static final class Preconditions {static void checkState(boolean b){if(!b)throw new IllegalStateException();}}
            static final class EntitySelectors {static java.util.function.Predicate<Entity> living(){return e->true;}static java.util.function.Predicate<Entity> exclude(Entity e){return x->x!=e;}}
            enum MovingObjectType {BLOCK,ENTITY}
            static class MovingObjectPosition {Vec3 hitVec;MovingObjectType typeOfHit;int sideHit,blockY;Entity entityHit;}
            static class Raytrace {static MovingObjectPosition hit;static MovingObjectPosition perform(World w,Vec3 a,Vec3 b,java.util.function.Predicate<Entity> p){return hit;}}
            static class ContextHost {CPHost cpData=new CPHost();}static class CPHost {float overload;float getOverload(){return overload;}void setOverload(float value){overload=value;}}
            """;
    }

    private static float scalar(Class<?> original, String name, float exp) throws Exception {
        return (float) original.getMethod(name, float.class).invoke(null, exp);
    }

    private static float tunedScalar(Class<?> original, String name, String skill, String field, float exp) throws Exception {
        var method = original.getMethod("tuned_" + name, float.class, float.class, float.class);
        float classic = (float) method.invoke(null, exp, scalar(original, name, 0), scalar(original, name, 1));
        exact(classic, scalar(original, name, exp), "parameterized oracle preserves original " + name);
        return (float) method.invoke(null, exp, (float) SkillTuning.number(skill, field + "_novice"),
                (float) SkillTuning.number(skill, field + "_mastered"));
    }

    private static double[] array(Point point) { return new double[]{point.x(), point.y(), point.z()}; }
    private static void exactPoint(Point actual, double[] expected, String why) {
        check(Double.doubleToLongBits(actual.x()) == Double.doubleToLongBits(expected[0])
                && Double.doubleToLongBits(actual.y()) == Double.doubleToLongBits(expected[1])
                && Double.doubleToLongBits(actual.z()) == Double.doubleToLongBits(expected[2]), why + ": " + actual + " != " + java.util.Arrays.toString(expected));
    }

    public static void main(String[] args) throws Exception {
        check(TeleporterFinalSourceFixtures.verifyAll() == 44, "Complete exact original sources");
        check(TeleporterFinalSourceFixtures.verifyMedia() == 28, "Complete original runtime media hashes");
        try (var loader = oracle()) {
            Class<?> original = loader.loadClass(PACKAGE + ".OriginalFinal");
            for (int i = 0; i <= 1000; i++) {
                float exp = i / 1000F;
                exact(shiftDamage(exp), scalar(original, "shiftDamage", exp), "Shift damage");
                exact(shiftRange(exp), tunedScalar(original, "shiftRange", "shift_tp", "reach", exp), "Shift tuned range");
                exact(shiftConsumption(exp), scalar(original, "shiftConsumption", exp), "Shift CP");
                exact(shiftOverload(exp), scalar(original, "shiftOverload", exp), "Shift overload");
                check(shiftCooldown(exp) == (int) scalar(original, "shiftCooldown", exp), "Shift cooldown truncation");
                exact(flashConsumption(exp), scalar(original, "flashConsumption", exp), "Flash CP");
                exact(flashStartOverload(exp), scalar(original, "flashStartOverload", exp), "Flash startup overload");
                exact(flashStartConsumption(exp), scalar(original, "flashStartConsumption", exp), "Flash startup CP");
                int flashLimit = (int) tunedScalar(original, "flashMaxTime", "flashing", "duration_ticks", exp);
                check(flashMaxTime(exp) == flashLimit, "Flash tuned lifetime truncation");
                check(flashCooldown(exp) == (int) scalar(original, "flashCooldown", exp), "Flash cooldown truncation");
                exact(flashRange(exp), tunedScalar(original, "flashRange", "flashing", "reach", exp), "Flash tuned range");
                check(locationCooldown(exp) == (int) scalar(original, "locationCooldown", exp), "Location updated experience cooldown");
                for (int ticks : new int[]{0, flashLimit - 1, flashLimit, flashLimit + 1, flashLimit + 2}) {
                    int[] expected = (int[]) original.getMethod("flashTickAtLimit", float.class, int.class, int.class, float.class, float.class)
                            .invoke(null, exp, flashLimit, ticks, 100F, 200F);
                    check(flashExpired(ticks, exp) == (expected[0] != 0), "Flash strict preincrement lifetime");
                    check(expected[1] == ticks + 1 && expected[2] == Float.floatToIntBits(200F), "Original Flash always increments and floors captured overload");
                }
                for (double distance : new double[]{0, 7.999999, 8, 63.999999, 64, 64.000001, 199.999, 200, 799.99999, 800, 800.0001, 120000}) {
                    for (boolean cross : new boolean[]{false, true}) {
                        float expected = (float) original.getMethod("locationConsumption", float.class, float.class, boolean.class)
                                .invoke(null, exp, (float) distance, cross);
                        exact(locationConsumption(exp, distance, cross), expected, "Location float distance/sqrt/minimum/dimension CP");
                    }
                }
            }
            for (double postDistance : new double[]{0, Math.nextDown(200D), 200, Math.nextUp(200D), 500}) {
                exact(locationExperience(postDistance), (float) original.getMethod("locationExperience", double.class).invoke(null, postDistance), "Location post-move experience threshold");
            }
            Random random = new Random(1072026);
            for (int i = 0; i < 12000; i++) {
                float yaw = (random.nextFloat() - .5F) * 7200, pitch = (random.nextFloat() - .5F) * 360;
                int key = 1 + random.nextInt(4);
                double[] expected = (double[]) original.getMethod("direction", int.class, float.class, float.class).invoke(null, key, yaw, pitch);
                exactPoint(flashDirection(key, yaw, pitch), expected, "Original Flash A,D,W,S rotateZ then rotateY");
                Point eye = new Point(random.nextDouble() * 10 - 5, random.nextDouble() * 10 - 5, random.nextDouble() * 10 - 5);
                Point direction = new Point(expected[0], expected[1], expected[2]);
                float shiftExp = random.nextFloat();
                float shiftRange = tunedScalar(original, "shiftRange", "shift_tp", "reach", shiftExp);
                int[] shiftExpected = (int[]) original.getMethod("shiftFallbackAtRange", double[].class, double[].class, float.class)
                        .invoke(null, array(eye), array(direction), shiftRange);
                check(java.util.Arrays.equals(shiftFallback(eye, direction, shiftExp), shiftExpected), "Original Shift Motion3D truncates the tuned range endpoint toward zero");
                Point min = new Point(random.nextDouble() * 20 - 10, random.nextDouble() * 20 - 10, random.nextDouble() * 20 - 10);
                Point max = min.add(new Point(random.nextDouble() * 4, random.nextDouble() * 4, random.nextDouble() * 4));
                Point a = new Point(random.nextDouble() * 30 - 15, random.nextDouble() * 30 - 15, random.nextDouble() * 30 - 15);
                Point b = new Point(random.nextDouble() * 30 - 15, random.nextDouble() * 30 - 15, random.nextDouble() * 30 - 15);
                boolean line = (boolean) original.getMethod("lineBox", double[].class, double[].class, double[].class, double[].class)
                        .invoke(null, array(min), array(max), array(a), array(b));
                check(lineBox(min, max, a, b) == line, "Original LambdaLib strict line box");
            }
            for (int side = 0; side < 6; side++) for (boolean clear : new boolean[]{false, true}) {
                for (Point hit : new Point[]{new Point(-.2, 5.3, -.7), new Point(2.999999, -3.4, 4.000001)}) {
                    double[] expected = (double[]) original.getMethod("flashBlock", double[].class, int.class, int.class, boolean.class)
                            .invoke(null, array(hit), side, -3, clear);
                    exactPoint(flashBlock(hit, side, -3, (x, y, z) -> clear), expected, "Original Flash block face/head offsets");
                }
            }
        }
        System.out.println("PASS " + checks + " original Teleporter expressions and Java body checks with explicit tuned range and lifetime inputs");
    }
}
