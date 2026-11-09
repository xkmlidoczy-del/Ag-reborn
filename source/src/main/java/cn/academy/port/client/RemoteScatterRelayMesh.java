package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** NEW253 borrows original shield/ray artwork without extending NEW221's gameplay geometry. */
public final class RemoteScatterRelayMesh {
    public static final int MAX_VISIBLE_CONTEXTS = 4, MAX_SCENE_VERTICES = 4096, MAX_SCENE_QUADS = MAX_SCENE_VERTICES / 4, SPAWNED_PARTICLES = 0;
    public static final int MAX_LABEL_QUADS = 128, COMPACT_ELLIPSE_STEPS = 12, COMPACT_DASHES_PER_LEG = 8;
    /** Includes the separate GUI row: one bar plus two glyph passes, with one spare quad. */
    public static final int MAX_HUD_GLYPHS = 79, MAX_HUD_QUADS = 160;
    public record Label(Vector position, String key, String value) {}
    public record Scene(List<FoldedMirrorMesh.Face> faces, boolean enhanced, boolean compact, int vertices) {}
    private RemoteScatterRelayMesh() {}
    public static Plate physicalPlate(RemoteScatterRelayState.Node node) { return new Plate(0, node.center(), node.normal()); }
    public static List<FoldedMirrorMesh.Face> plate(RemoteScatterRelayState.Node node, boolean ghost, float phase, float power) {
        if (node == null) return List.of();
        return FoldedMirrorMesh.plate(physicalPlate(node), ghost, phase, power > 0 ? node.center() : null, power);
    }
    /** Each bounded leg uses the proven original ray mesh; no reflection law or invented target enters this renderer. */
    public static List<FoldedMirrorMesh.Face> path(RemoteScatterRelayState.Frame frame, Vector camera, Vector look, float age) {
        if (!RemoteScatterRelayState.valid(frame) || frame.phase() == RemoteScatterRelayState.ENDED) return List.of();
        if (frame.phase() == RemoteScatterRelayState.FIRE && (age < 0 || age >= RemoteScatterRelayState.FLASH_TICKS)) return List.of();
        List<FoldedMirrorMesh.Face> faces = new ArrayList<>();
        List<Plate> physical = frame.plates().isEmpty() ? List.of() : List.of(physicalPlate(frame.plates().getFirst()));
        for (var leg : frame.path()) {
            var id = frame.id();
            var adapted = new FoldedMirrorState.Frame(new FoldedMirrorState.Identity(id.entity(), id.owner(), id.epoch(), id.input(), id.token()),
                    frame.sequence(), frame.phase() == RemoteScatterRelayState.FIRE ? FoldedMirrorState.FIRE : FoldedMirrorState.ACTIVE,
                    frame.phase() == RemoteScatterRelayState.FIRE ? frame.status() : FoldedMirrorState.PREPARED,
                    frame.remainingTicks(), frame.ownerFeet(), physical, new Vector(0, 0, 0), new Vector(0, 0, 0),
                    List.of(new FoldedMirrorState.Segment(leg.start(), leg.end())));
            faces.addAll(FoldedMirrorMesh.path(adapted, camera, look, age));
        }
        return List.copyOf(faces);
    }
    /** Lower tessellation preserves every physical node and its original continuous shield UVs. */
    public static List<FoldedMirrorMesh.Face> compactPlate(RemoteScatterRelayState.Node node, boolean ghost) {
        if (node == null) return List.of();
        List<FoldedMirrorMesh.Face> out = new ArrayList<>(); Plate plate = physicalPlate(node);
        for (int i = 0; i < COMPACT_ELLIPSE_STEPS; i++) {
            if (ghost && i % 2 != 0) continue;
            double a = Math.PI * 2 * i / COMPACT_ELLIPSE_STEPS, b = Math.PI * 2 * (i + 1) / COMPACT_ELLIPSE_STEPS;
            Vector oa = FoldedMirrorGeometry.perimeterPoint(plate, a), ob = FoldedMirrorGeometry.perimeterPoint(plate, b);
            Vector ia = node.center().add(oa.subtract(node.center()).scale(.978)), ib = node.center().add(ob.subtract(node.center()).scale(.978));
            if (!ghost) {
                var uv = new FoldedMirrorMesh.Uv(.5,.5);
                out.add(new FoldedMirrorMesh.Face(node.center(),ia,ib,node.center(),0xFFFFFF,255,FoldedMirrorMesh.FILL,FoldedMirrorMesh.SHIELD_TEXTURE,
                        uv,shieldUv(a),shieldUv(b),uv));
                Vector depth = node.normal().scale(FoldedMirrorGeometry.THICKNESS / 2);
                out.add(new FoldedMirrorMesh.Face(oa.subtract(depth),ob.subtract(depth),ob.add(depth),oa.add(depth),FoldedMirrorMesh.EDGE,82,FoldedMirrorMesh.SIDE,-1));
            }
            out.add(new FoldedMirrorMesh.Face(oa,ob,ib,ia,ghost?FoldedMirrorMesh.PREVIEW:FoldedMirrorMesh.GREEN,
                    ghost?160:120,ghost?FoldedMirrorMesh.GHOST:FoldedMirrorMesh.RIM,-1));
        }
        return List.copyOf(out);
    }
    private static FoldedMirrorMesh.Uv shieldUv(double angle) {
        return new FoldedMirrorMesh.Uv(.5+Math.cos(angle)*.43*.978,.5-Math.sin(angle)*.43*.978);
    }
    /** Finite original-texture rays and spaced dots are useful even when all four controllers are visible. */
    public static List<FoldedMirrorMesh.Face> compactPath(RemoteScatterRelayState.Frame frame, Vector camera, Vector look, float age) {
        if (!RemoteScatterRelayState.valid(frame) || frame.phase()==RemoteScatterRelayState.ENDED || camera==null || !camera.finite()
                || !RemoteScatterRelayState.direction(look)) return List.of();
        boolean fire=frame.phase()==RemoteScatterRelayState.FIRE;
        if (fire&&(age<0||age>=RemoteScatterRelayState.FLASH_TICKS)) return List.of();
        List<FoldedMirrorMesh.Face> out=new ArrayList<>();
        for (var leg:frame.path()) {
            Vector delta=leg.end().subtract(leg.start());double length=delta.length();
            Vector side=delta.cross(camera.subtract(leg.start()));
            if(side.length()<1e-8)side=delta.cross(Math.abs(look.y())>.9?new Vector(1,0,0):new Vector(0,1,0));
            side=FoldedMirrorGeometry.unit(side).orElse(null);if(side==null)continue;
            if(!fire) {
                int dots=Math.min(COMPACT_DASHES_PER_LEG,(int)Math.ceil(length/.65));
                for(int i=0;i<dots;i++) {
                    double begin=(double)i/dots,end=Math.min(1,begin+.20/length);
                    compactRay(out,leg.start().add(delta.scale(begin)),leg.start().add(delta.scale(end)),side,camera,look,
                            .006,FoldedMirrorMesh.PREVIEW,140,FoldedMirrorMesh.PLANNED,-1);
                }
            } else {
                int alpha=(int)(245*(RemoteScatterRelayState.FLASH_TICKS-age)/RemoteScatterRelayState.FLASH_TICKS);
                double blend=Math.min(.24/length,.25);double[] cuts={0,blend,1-blend,1};
                for(int part=0;part<3;part++) {
                    Vector a=leg.start().add(delta.scale(cuts[part])),b=leg.start().add(delta.scale(cuts[part+1]));
                    compactRay(out,a,b,side,camera,look,FoldedMirrorGeometry.BEAM_RADIUS,0x6AF26A,alpha*80/245,FoldedMirrorMesh.BEAM_EDGE,-1);
                    compactRay(out,a,b,side,camera,look,.014,FoldedMirrorMesh.CORE,alpha*230/245,FoldedMirrorMesh.BEAM_CORE,part);
                }
            }
        }
        return List.copyOf(out);
    }
    private static void compactRay(List<FoldedMirrorMesh.Face> out,Vector a,Vector b,Vector side,Vector camera,Vector look,
                                   double width,int color,int alpha,int kind,int texture) {
        double za=a.subtract(camera).dot(look),zb=b.subtract(camera).dot(look),uA=0,uB=1;
        if(za<FoldedMirrorMesh.RAY_NEAR_DEPTH&&zb<FoldedMirrorMesh.RAY_NEAR_DEPTH)return;
        if(za<FoldedMirrorMesh.RAY_NEAR_DEPTH){double t=(FoldedMirrorMesh.RAY_NEAR_DEPTH-za)/(zb-za);a=a.add(b.subtract(a).scale(t));uA=t;}
        else if(zb<FoldedMirrorMesh.RAY_NEAR_DEPTH){double t=(FoldedMirrorMesh.RAY_NEAR_DEPTH-za)/(zb-za);b=a.add(b.subtract(a).scale(t));uB=t;}
        if(a.subtract(b).length()<1e-8)return;
        Vector wa=side.scale(compactWidth(a.subtract(camera),side,look,width,kind)),wb=side.scale(compactWidth(b.subtract(camera),side,look,width,kind));
        out.add(new FoldedMirrorMesh.Face(a.add(wa),a.subtract(wa),b.subtract(wb),b.add(wb),color,alpha,kind,texture,
                new FoldedMirrorMesh.Uv(uA,1),new FoldedMirrorMesh.Uv(uA,0),new FoldedMirrorMesh.Uv(uB,0),new FoldedMirrorMesh.Uv(uB,1)));
    }
    private static double compactWidth(Vector relative,Vector side,Vector look,double width,int kind) {
        double z=relative.dot(look),depth=side.dot(look),radius=kind==FoldedMirrorMesh.BEAM_CORE?FoldedMirrorMesh.CORE_RADIUS:FoldedMirrorGeometry.BEAM_RADIUS;
        double angular=FoldedMirrorMesh.angularLimit(kind)*width/radius;
        double denominator=side.scale(z).subtract(relative.scale(depth)).length()+angular*z*Math.abs(depth);
        return denominator<=1e-8?width:Math.min(width,angular*z*z/denominator);
    }
    /** Energize only a receipt-confirmed relay crossing; there is no target-impact decoration. */
    public static boolean reached(RemoteScatterRelayState.Frame frame, RemoteScatterRelayState.Node node) {
        if (frame == null || node == null || frame.phase() != RemoteScatterRelayState.FIRE) return false;
        int index = node.index();
        return index + 1 < frame.path().size() && frame.path().get(index).end().subtract(node.center()).length() <= 1e-7
                && frame.path().get(index + 1).start().subtract(node.center()).length() <= 1e-7;
    }
    public static List<Label> labels(RemoteScatterRelayState.Frame frame) {
        if (!RemoteScatterRelayState.valid(frame) || frame.phase() == RemoteScatterRelayState.ENDED) return List.of();
        List<Label> labels = new ArrayList<>();
        for (var node : frame.plates()) labels.add(new Label(node.center().add(new Vector(0, .82, 0)), "node", Integer.toString(node.index() + 1)));
        for (var leg : frame.path()) labels.add(new Label(leg.start().add(leg.end()).scale(.5).add(new Vector(0, .16, 0)),
                "distance", String.format(Locale.ROOT, "%.1f", leg.end().subtract(leg.start()).length())));
        if (RemoteScatterRelayState.hasEndpoint(frame)) labels.add(new Label(frame.ghostCenter().add(new Vector(0, .22, 0)), "terminal", ""));
        return List.copyOf(labels);
    }
    /** The camera translation happens in doubles before the renderer converts to float vertices. */
    public static Vector relative(Vector world, Vector camera) { return world.subtract(camera); }
    public static List<FoldedMirrorMesh.Face> clipped(List<FoldedMirrorMesh.Face> faces, Vector camera, Vector look) {
        List<FoldedMirrorMesh.Face> clipped = new ArrayList<>();
        for (var face : faces) if (face.alpha() > 0) clipped.addAll(FoldedMirrorMesh.nearClip(face, camera, look));
        return List.copyOf(clipped);
    }
    public static List<FoldedMirrorMesh.Face> ordered(List<FoldedMirrorMesh.Face> clipped, Vector camera) {
        return clipped.stream().sorted(Comparator.comparingInt(FoldedMirrorMesh::layer)
                .thenComparing(Comparator.comparingDouble((FoldedMirrorMesh.Face f) ->
                        f.a().add(f.b()).add(f.c()).add(f.d()).scale(.25).subtract(camera).length()).reversed())).toList();
    }
    public static String boundedHudText(String text) {
        if(text==null)return "";
        return text.codePointCount(0,text.length())<=MAX_HUD_GLYPHS?text:text.substring(0,text.offsetByCodePoints(0,MAX_HUD_GLYPHS-1))+"…";
    }
    /** Choose once for the complete scene, so fallback never stacks on top of enhanced geometry. */
    public static Scene scene(List<FoldedMirrorMesh.Face> detailed,List<FoldedMirrorMesh.Face> compact,int labelQuads,boolean materialReady) {
        if(labelQuads<0||labelQuads>MAX_LABEL_QUADS)throw new IllegalArgumentException("Bounded label reservation required");
        var budget=new SceneBudget();
        budget.takeGroup(0,MAX_HUD_QUADS);
        if(materialReady&&budget.takeGroup(detailed.size(),labelQuads))return new Scene(List.copyOf(detailed),true,false,budget.usedVertices());
        List<FoldedMirrorMesh.Face> selected=compact;
        // Rims and thickness decoration are expendable; real plate fills, dots and accepted rays are retained.
        if(selected.size()+labelQuads+MAX_HUD_QUADS>MAX_SCENE_QUADS)selected=selected.stream().filter(face->face.kind()!=FoldedMirrorMesh.RIM&&face.kind()!=FoldedMirrorMesh.SIDE).toList();
        if(selected.size()+labelQuads+MAX_HUD_QUADS>MAX_SCENE_QUADS)selected=selected.stream().filter(face->face.kind()!=FoldedMirrorMesh.GHOST).toList();
        budget=new SceneBudget();
        budget.takeGroup(0,MAX_HUD_QUADS);
        if(!budget.takeGroup(selected.size(),labelQuads))return new Scene(List.of(),false,true,(labelQuads+MAX_HUD_QUADS)*4);
        return new Scene(List.copyOf(selected),false,true,budget.usedVertices());
    }
    /** Reserve the entire owner group, including text glyph quads, before emitting any of it. */
    public static final class SceneBudget {
        private int used;
        public boolean takeGroup(int meshQuads, int labelGlyphQuads) {
            if (meshQuads < 0 || labelGlyphQuads < 0 || meshQuads > MAX_SCENE_QUADS - used
                    || labelGlyphQuads > MAX_SCENE_QUADS - used - meshQuads) return false;
            used += meshQuads + labelGlyphQuads; return true;
        }
        public int used() { return used; }
        public int usedVertices() { return used * 4; }
    }
    /** One decision selects original-texture fallback for every surface and ray in the scene. */
    public static boolean enhanced(boolean materialAvailable, boolean forceFallback) { return materialAvailable && !forceFallback; }
}
