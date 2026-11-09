package cn.academy.port.client;

/** Client-only probe for shader packs without a hard Iris dependency. */
public final class ShaderPackState {
    private static Boolean shaderPack;
    private static boolean shaderApiAbsent;
    private static long shaderPackCheckedAt=Long.MIN_VALUE;
    private static java.lang.reflect.Method shaderPackQuery;
    private static Object shaderApi;
    private ShaderPackState() {}

    public static boolean active(){
        if(shaderApiAbsent)return false;
        long now=System.nanoTime()/50_000_000L;
        if(shaderPack!=null&&shaderPackCheckedAt==now)return shaderPack;
        shaderPackCheckedAt=now;
        try{
            if(shaderPackQuery==null){
                Class<?> api=Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                shaderApi=api.getMethod("getInstance").invoke(null);
                shaderPackQuery=api.getMethod("isShaderPackInUse");
            }
            shaderPack=(Boolean)shaderPackQuery.invoke(shaderApi);
            return shaderPack;
        }catch(ClassNotFoundException absent){shaderApiAbsent=true;shaderPack=false;return false;}
        catch(ReflectiveOperationException|LinkageError|ClassCastException unavailable){
            // An installed but unqueryable shader API is conservative: keep ordinary materials until it is usable again.
            shaderPack=true;return true;
        }
    }
}
