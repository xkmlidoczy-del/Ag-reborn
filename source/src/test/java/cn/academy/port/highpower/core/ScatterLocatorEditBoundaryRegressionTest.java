package cn.academy.port.highpower.core;
import java.util.List;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import static cn.academy.port.highpower.core.ScatterLocatorRules.*;
/** Independent boundary amendment; immutable .80 rules regression remains byte-exact. */
public final class ScatterLocatorEditBoundaryRegressionTest {
    private static int checks;
    public static void main(String[] args) {
        exactCenterEditLimit();
        System.out.println("PASS "+checks+" NEW223 exact-center edit boundary checks; historical runner unchanged");
    }
    private static void exactCenterEditLimit(){
        // Retained native witness: the exact center is 24 m away, but division rounds
        // the plane parameter one ULP beyond 24. NEW221 contact must stay unchanged.
        Vector normal=new Vector(-0.5558760818982492,0.06762226384557349,0.8285101152103398);
        Vector direction=new Vector(0,0,1);
        for(Vector origin:List.of(new Vector(0,0,0),new Vector(12.5,-40.37999999523163,3.5),
                new Vector(-6531920.5,-40.37999999523163,-13247565.5),
                new Vector(-6545252.5,-40.37999999523163,-351454.5),
                new Vector(29_999_980,64,29_999_956),new Vector(-29_999_980,64,-29_999_980))){
            Vector center=origin.add(direction.scale(PLACE_RANGE));Plate plate=new Plate(0,center,normal);
            Vector delta=center.subtract(origin);
            check(delta.dot(delta)==PLACE_RANGE*PLACE_RANGE,"translated edit witness is exactly at the hard center range: "+origin);
            double parameter=delta.dot(normal)/direction.dot(normal);
            check(parameter==Math.nextUp(PLACE_RANGE),"retained plane division witness is exactly one ULP outside: "+origin);
            check(FoldedMirrorGeometry.contact(plate,origin,direction,PLACE_RANGE).isEmpty(),"original NEW221 contact keeps its strict division result: "+origin);
            check(exactCenterAtEditLimit(plate,origin,direction),"only exact-center locator editing recovers the real boundary endpoint: "+origin);
            check(FoldedMirrorGeometry.contact(plate,origin,direction,PLACE_RANGE).isEmpty(),"locator selection never changes subsequent shared damage geometry: "+origin);
            Plate beyond=new Plate(0,new Vector(center.x(),center.y(),Math.nextUp(center.z())),normal);
            check(beyond.center().subtract(origin).dot(beyond.center().subtract(origin))>PLACE_RANGE*PLACE_RANGE,"next representable center is genuinely beyond24m: "+origin);
            check(!exactCenterAtEditLimit(beyond,origin,direction),"one representable step beyond the hard limit remains refused: "+origin);
            Plate offCenter=new Plate(0,new Vector(Math.nextUp(center.x()),center.y(),center.z()),normal);
            check(!exactCenterAtEditLimit(offCenter,origin,direction),"one representable sideways offset cannot become an exact-center witness: "+origin);
        }
        Vector origin=new Vector(0,0,0);Plate plate=new Plate(0,new Vector(0,0,24),normal);
        check(!exactCenterAtEditLimit(new Plate(0,new Vector(.1,0,23.9),normal),origin,direction),"in-range noncenter endpoint does not gain an edit fallback");
        check(!exactCenterAtEditLimit(new Plate(0,new Vector(0,0,23),normal),origin,direction),"ordinary interior selection remains with the native contact path");
        check(!exactCenterAtEditLimit(new Plate(0,plate.center(),new Vector(1,0,0)),origin,direction),"a ray lying in the plane is not a crossing");
        check(!exactCenterAtEditLimit(new Plate(0,origin,normal),origin,new Vector(0,0,0)),"zero direction cannot certify an endpoint");
        check(!exactCenterAtEditLimit(new Plate(0,new Vector(0,0,12),normal),origin,new Vector(0,0,.5)),"a nonunit direction cannot shorten the edit limit");
        check(!exactCenterAtEditLimit(plate,origin,new Vector(0,0,2)),"nonunit overlong direction is refused");
        check(!exactCenterAtEditLimit(plate,origin,new Vector(Double.NaN,0,1)),"NaN direction is refused");
        check(!exactCenterAtEditLimit(plate,new Vector(0,Double.NaN,0),direction),"NaN origin is refused");
        check(!exactCenterAtEditLimit(plate,origin,new Vector(0,0,Double.POSITIVE_INFINITY)),"infinite direction is refused");
        check(!exactCenterAtEditLimit(plate,new Vector(0,0,Double.NEGATIVE_INFINITY),direction),"infinite origin is refused");
        check(!exactCenterAtEditLimit(null,origin,direction)&&!exactCenterAtEditLimit(plate,null,direction)
                &&!exactCenterAtEditLimit(plate,origin,null),"missing edit witnesses fail closed");
    }
    private static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
}
