package cn.academy.port.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Read-only getters into the frozen classic renderer. Never exposes its mutable records/lists. */
final class ClassicDecorSnapshots {
    private ClassicDecorSnapshots() {}
    private static final MethodHandle BEAMS=staticGetter("BEAMS",List.class), ARCS=staticGetter("ARCS",List.class), CLOCK=staticGetter("clockMillis",long.class);
    private static final MethodHandle BEAM_ORIGIN=getter("Beam","origin",Vec3.class), BEAM_DIRECTION=getter("Beam","direction",Vec3.class),
            BEAM_LENGTH=getter("Beam","length",double.class), BEAM_CREATED=getter("Beam","created",long.class), BEAM_OWNER=getter("Beam","nearEyeOwner",Object.class);
    private static final MethodHandle ARC_ORIGIN=getter("WeakArc","origin",Vec3.class), ARC_DIRECTION=getter("WeakArc","direction",Vec3.class),
            ARC_LENGTH=getter("WeakArc","length",double.class), ARC_TICKS=getter("WeakArc","ticks",int.class);
    private static MethodHandle staticGetter(String name,Class<?> type){
        try{return MethodHandles.privateLookupIn(ClassicEffects.class,MethodHandles.lookup()).findStaticGetter(ClassicEffects.class,name,type);}
        catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}
    }
    private static MethodHandle getter(String nested,String name,Class<?> result){
        try {
            var owner=Class.forName(ClassicEffects.class.getName()+"$"+nested,false,ClassicEffects.class.getClassLoader());
            var type=owner.getDeclaredField(name).getType();
            if(result!=Object.class&&type!=result)throw new NoSuchFieldException("Changed frozen field type: "+name);
            return MethodHandles.privateLookupIn(owner,MethodHandles.lookup()).findGetter(owner,name,type)
                    .asType(MethodType.methodType(result,Object.class));
        }catch(ReflectiveOperationException error){throw new ExceptionInInitializerError(error);}
    }
    static List<double[]> beams(){
        try {
            var source=(List<?>)BEAMS.invokeExact();long clock=(long)CLOCK.invokeExact();
            var result=new ArrayList<double[]>(source.size());
            for(Object beam:source){
                var o=(Vec3)BEAM_ORIGIN.invokeExact(beam);var d=(Vec3)BEAM_DIRECTION.invokeExact(beam);
                double length=(double)BEAM_LENGTH.invokeExact(beam);long created=(long)BEAM_CREATED.invokeExact(beam);Object owner=BEAM_OWNER.invokeExact(beam);
                result.add(new double[]{o.x,o.y,o.z,d.x,d.y,d.z,length,clock-created,owner!=null?1:0,created});
            }
            return result;
        }catch(Throwable error){throw new IllegalStateException("Frozen classic beam snapshot failed",error);}
    }
    static List<double[]> arcs(){
        try {
            var source=(List<?>)ARCS.invokeExact();var result=new ArrayList<double[]>(source.size());
            for(Object arc:source){
                var o=(Vec3)ARC_ORIGIN.invokeExact(arc);var d=(Vec3)ARC_DIRECTION.invokeExact(arc);
                double length=(double)ARC_LENGTH.invokeExact(arc);int ticks=(int)ARC_TICKS.invokeExact(arc);
                result.add(new double[]{o.x,o.y,o.z,d.x,d.y,d.z,length,ticks});
            }
            return result;
        }catch(Throwable error){throw new IllegalStateException("Frozen classic arc snapshot failed",error);}
    }
}
