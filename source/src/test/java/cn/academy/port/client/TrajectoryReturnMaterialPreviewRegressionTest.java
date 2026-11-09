package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;

/**
 * Deterministic CPU geometry/material regression, not native capture or beauty/soft-shoulder acceptance.
 * The first unverified test demanded >=90 head-on pixels but measured 88. Its rasterizer omitted the
 * pinned Minecraft 1.21.1 position_tex_color.fsh discard when texture alpha * vertex alpha < 0.1.
 * Keep that failed result as evidence; do not enlarge production geometry to satisfy an invented floor.
 * This model covers an empty scene at rest, not terrain, native GPU rounding, view bob, or framebuffer QA.
 */
public final class TrajectoryReturnMaterialPreviewRegressionTest {
    private static final int W=640,H=360,BG=0xff191d23;
    private static final String TEXTURE_SHA256="0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361";
    private static final Point CAMERA=new Point(0,1.6,0),FORWARD=new Point(0,0,1),RIGHT=new Point(1,0,0);
    private static final double NATIVE_ALPHA_CUTOFF=.1;
    private static int checks;
    private enum Mode { NATIVE, LEGACY_NO_DISCARD, ZERO_ALPHA, TRANSPARENT_UV }
    private record Vertex(double x,double y,double inverseZ,double u,double v,double alpha) {}
    private record Prepared(TrajectoryReturnMesh.Profile profile,TrajectoryReturnMesh.Ribbon ribbon) {}
    private record Coverage(int faint,int visible,int bright) {
        int lowContrastBand(){return faint-bright;}
    }
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static TrajectoryReturnState.Controller cast(Point velocity,boolean hammer,int status){
        var state=new TrajectoryReturnState();var id=new TrajectoryReturnState.Identity(19,new UUID(431,66),71,1,1);
        Point a=hammer?new Point(.25,1.5,.4):new Point(0,1.6,6),b=hammer?new Point(.25,1.5,6):a;
        check(state.accept(new TrajectoryReturnState.Frame(id,1,hammer?TrajectoryReturnState.AIR_HAMMER:TrajectoryReturnState.REVERSE,
                0,a,b,velocity,status),true),"preview starts from accepted production receipt");return state.controllers().getFirst();
    }
    public static void main(String[] args)throws Exception{
        Path texturePath=Path.of("src/main/resources/assets/academy/textures/effects/glow_line.png");
        String textureSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(texturePath)));
        check(TEXTURE_SHA256.equals(textureSha),"measured material fixture uses the pinned glow_line.png bytes");
        var texture=ImageIO.read(texturePath.toFile());
        check(texture.getWidth()==32&&texture.getHeight()==32,"pinned glow-line dimensions");
        for(int x=0;x<32;x++)check((texture.getRGB(x,0)>>>24)==0&&(texture.getRGB(x,15)>>>24)==255,
                "wrong-UV control samples a transparent row; the production core samples an opaque center");
        TrajectoryReturnState.Controller head=cast(FORWARD,false,TrajectoryReturnState.COMMITTED),side=cast(RIGHT,false,TrajectoryReturnState.COMMITTED);
        TrajectoryReturnState.Controller refused=cast(TrajectoryReturnState.ZERO,true,TrajectoryReturnState.REFUSED),damaged=cast(TrajectoryReturnState.ZERO,true,TrajectoryReturnState.DAMAGED);
        var headShape=TrajectoryReturnMesh.shape(head,0);var minimalShape=TrajectoryReturnMesh.fallback(head,0);
        var releaseShape=TrajectoryReturnMesh.shape(refused,2);var contactShape=TrajectoryReturnMesh.shape(damaged,2);
        BufferedImage full=render(headShape,texture),minimal=render(minimalShape,texture);
        var sideImage=render(TrajectoryReturnMesh.shape(side,0),texture);
        BufferedImage release=render(releaseShape,texture),contact=render(contactShape,texture),expired=render(TrajectoryReturnMesh.shape(head,6),texture);
        // These are measured regression snapshots, not minimum acceptable visual quality.
        snapshot("native head-on",full,new Coverage(82,82,82));
        snapshot("native minimal arrow",minimal,new Coverage(16,16,16));
        snapshot("native side-on",sideImage,new Coverage(134,134,124));
        snapshot("native release",release,new Coverage(499,499,437));
        snapshot("native confirmed contact",contact,new Coverage(528,528,471));
        snapshot("expired reversal",expired,new Coverage(0,0,0));
        var legacy=render(headShape,texture,0,0,Mode.LEGACY_NO_DISCARD);
        snapshot("preserved original failed preview",legacy,new Coverage(148,88,82));
        check(coverage(legacy,12)==88&&coverage(legacy,12)<90,
                "historical evidence: the original unverified >=90 assertion failed at 88 pixels");
        check(measure(full).lowContrastBand()==0&&measure(legacy).lowContrastBand()==66,
                "native alpha cutoff removes the old model's claimed low-contrast shoulder band in this fixture");

        int minPixels=Integer.MAX_VALUE,maxPixels=0;
        for(double x:new double[]{0,.25,.5,.75})for(double y:new double[]{0,.25,.5,.75}){
            var shifted=render(headShape,texture,x,y,Mode.NATIVE);int count=coverage(shifted,12);
            minPixels=Math.min(minPixels,count);maxPixels=Math.max(maxPixels,count);
            check(count>=72&&count<=105,"measured native head-on range survives quarter-pixel raster phases");
            check(count>2*coverage(render(minimalShape,texture,x,y,Mode.NATIVE),12),
                    "complete pressure geometry remains distinct from the minimal arrow at each raster phase");
            check(coverage(render(contactShape,texture,x,y,Mode.NATIVE),12)>coverage(render(releaseShape,texture,x,y,Mode.NATIVE),12),
                    "confirmed marks add visible pixels at each raster phase");
            check(unchanged(render(headShape,texture,x,y,Mode.ZERO_ALPHA)),"zero-alpha mutation renders no material at any raster phase");
            check(unchanged(render(headShape,texture,x,y,Mode.TRANSPARENT_UV)),"transparent-row UV mutation renders no material at any raster phase");
        }
        check(minPixels==72&&maxPixels==105,"pinned subpixel sweep retains its measured endpoints; no single phase is beauty acceptance");
        var belowCutoff=new TrajectoryReturnMesh.Shape(headShape.strokes().stream().map(s->
                new TrajectoryReturnMesh.Stroke(s.from(),s.to(),s.anchor(),s.width(),25,s.role())).toList());
        check(unchanged(render(belowCutoff,texture)),"native shader rejects even opaque texels when vertex alpha is 25/255 < 0.1");
        check(coverage(render(belowCutoff,texture,0,0,Mode.LEGACY_NO_DISCARD),3)>0,
                "cutoff negative control distinguishes the previous optimistic rasterizer");

        var sheet=new BufferedImage(W*2,H*3,BufferedImage.TYPE_INT_ARGB);var graphics=sheet.createGraphics();
        graphics.setColor(new Color(BG,true));graphics.fillRect(0,0,sheet.getWidth(),sheet.getHeight());
        List<BufferedImage> panels=List.of(full,minimal,sideImage,release,contact,expired);
        List<String> labels=List.of("CPU native-cutoff model / head-on / age 0 / 82 px","Whole-decoration fallback / same camera / 16 px",
                "Side-on reversal / default size / age 0 / 134 px","Air hammer: REFUSED / age 2 / no hit marks",
                "Air hammer: DAMAGED / age 2 / confirmed marks","Reversal glyph expired / no invented flight");
        for(int i=0;i<panels.size();i++){
            int x=(i%2)*W,y=(i/2)*H;graphics.drawImage(panels.get(i),x,y,null);graphics.setColor(new Color(200,205,212));
            graphics.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));graphics.drawString(labels.get(i),x+15,y+25);
        }
        graphics.setColor(new Color(170,176,184));
        graphics.drawString("CPU regression only. Not a native Minecraft screenshot or beauty/soft-shoulder acceptance.",15,H*3-32);
        graphics.drawString("Original unverified >=90 px check failed at 88; native alpha cutoff yields 82. Low-contrast band: 66 -> 0.",15,H*3-15);
        graphics.dispose();
        Path output=Path.of(System.getProperty("academy.trajectoryReturnPreviewOutput","build/reports/trajectory-return/default-material-preview.png"));
        Files.createDirectories(output.toAbsolutePath().getParent());ImageIO.write(sheet,"png",output.toFile());
        System.out.println("TrajectoryReturnMaterialPreviewRegressionTest: "+checks+" checks; native pixels head=82, fallback=16, side=134, release=499, contact=528; "
                +"head phase range="+minPixels+".."+maxPixels+"; legacy head=88, legacy/native low-contrast band=66/0; zero-alpha and wrong-UV controls=0; texture SHA-256="+textureSha+"; "+output);
    }
    private static void snapshot(String name,BufferedImage image,Coverage expected){
        Coverage actual=measure(image);check(actual.equals(expected),name+" measured snapshot changed: expected "+expected+", got "+actual);
    }
    private static BufferedImage render(TrajectoryReturnMesh.Shape shape,BufferedImage texture){return render(shape,texture,0,0,Mode.NATIVE);}
    private static BufferedImage render(TrajectoryReturnMesh.Shape shape,BufferedImage texture,double phaseX,double phaseY,Mode mode){
        var result=new BufferedImage(W,H,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<H;y++)for(int x=0;x<W;x++)result.setRGB(x,y,BG);
        int[] emitted=new int[W*H];int quad=0;
        for(boolean core:new boolean[]{false,true}){
            var prepared=new ArrayList<Prepared>();
            for(var stroke:shape.strokes()){
                var profile=TrajectoryReturnMesh.profile(stroke,core);var ribbon=TrajectoryReturnMesh.ribbon(stroke,profile,CAMERA,FORWARD,RIGHT);
                if(ribbon!=null)prepared.add(new Prepared(profile,ribbon));
            }
            // Production RenderType is sorted: MeshData uses corners 0/2 as the quad centroid.
            if(mode!=Mode.LEGACY_NO_DISCARD)prepared.sort(Comparator.comparingDouble(TrajectoryReturnMaterialPreviewRegressionTest::distanceSquared).reversed());
            for(var preparedQuad:prepared){
                var profile=preparedQuad.profile();var ribbon=preparedQuad.ribbon();var c=ribbon.corners();quad++;
                Vertex[] v={project(c.get(0),0,profile.maxV(),ribbon.startAlpha(),phaseX,phaseY,mode),project(c.get(1),0,profile.minV(),ribbon.startAlpha(),phaseX,phaseY,mode),
                        project(c.get(2),1,profile.minV(),ribbon.endAlpha(),phaseX,phaseY,mode),project(c.get(3),1,profile.maxV(),ribbon.endAlpha(),phaseX,phaseY,mode)};
                triangle(result,texture,profile.gray(),v[0],v[1],v[2],mode,emitted,quad);
                triangle(result,texture,profile.gray(),v[2],v[3],v[0],mode,emitted,quad);
            }
        }
        return result;
    }
    private static double distanceSquared(Prepared prepared){
        var a=prepared.ribbon().corners().get(0);var b=prepared.ribbon().corners().get(2);
        float x=((float)a.x()+(float)b.x())/2,y=((float)a.y()+(float)b.y())/2,z=((float)a.z()+(float)b.z())/2;
        return x*x+y*y+z*z;
    }
    private static Vertex project(Point p,double u,double v,double alpha,double phaseX,double phaseY,Mode mode){
        double focal=H/(2*Math.tan(Math.toRadians(70)/2));
        return new Vertex(W*.5+p.x()*focal/p.z()+phaseX,H*.5-p.y()*focal/p.z()+phaseY,1/p.z(),u,
                mode==Mode.TRANSPARENT_UV?.5/32:v,mode==Mode.ZERO_ALPHA?0:alpha);
    }
    private static double edge(Vertex a,Vertex b,double x,double y){return (x-a.x)*(b.y-a.y)-(y-a.y)*(b.x-a.x);}
    private static void triangle(BufferedImage out,BufferedImage texture,int gray,Vertex a,Vertex b,Vertex c,Mode mode,int[] emitted,int quad){
        double area=edge(a,b,c.x,c.y);if(Math.abs(area)<1e-8)return;
        int minX=Math.max(0,(int)Math.floor(Math.min(a.x,Math.min(b.x,c.x)))),maxX=Math.min(W-1,(int)Math.ceil(Math.max(a.x,Math.max(b.x,c.x))));
        int minY=Math.max(0,(int)Math.floor(Math.min(a.y,Math.min(b.y,c.y)))),maxY=Math.min(H-1,(int)Math.ceil(Math.max(a.y,Math.max(b.y,c.y))));
        for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){
            double wa=edge(b,c,x+.5,y+.5)/area,wb=edge(c,a,x+.5,y+.5)/area,wc=1-wa-wb;
            if(wa<0||wb<0||wc<0)continue;
            // A shared triangle diagonal is rasterized once by the GPU, not blended twice.
            if(mode!=Mode.LEGACY_NO_DISCARD){if(emitted[y*W+x]==quad)continue;emitted[y*W+x]=quad;}
            double z=wa*a.inverseZ+wb*b.inverseZ+wc*c.inverseZ;
            double u=(wa*a.u*a.inverseZ+wb*b.u*b.inverseZ+wc*c.u*c.inverseZ)/z;
            double v=(wa*a.v*a.inverseZ+wb*b.v*b.inverseZ+wc*c.v*c.inverseZ)/z;
            double alpha=(wa*a.alpha*a.inverseZ+wb*b.alpha*b.inverseZ+wc*c.alpha*c.inverseZ)/z/255;
            int tex=texture.getRGB(Math.max(0,Math.min(texture.getWidth()-1,(int)(u*texture.getWidth()))),Math.max(0,Math.min(texture.getHeight()-1,(int)(v*texture.getHeight()))));
            alpha*=((tex>>>24)&255)/255.;
            // Exact position_tex_color.fsh rule, before ColorModulator (production sets it to white).
            if(mode!=Mode.LEGACY_NO_DISCARD&&alpha<NATIVE_ALPHA_CUTOFF)continue;
            int previous=out.getRGB(x,y),red=blend((previous>>>16)&255,(tex>>>16)&255,gray,alpha),green=blend((previous>>>8)&255,(tex>>>8)&255,gray,alpha),blue=blend(previous&255,tex&255,gray,alpha);
            out.setRGB(x,y,0xff000000|red<<16|green<<8|blue);
        }
    }
    private static int blend(int background,int texture,int gray,double alpha){return (int)Math.round(background*(1-alpha)+texture*gray/255.*alpha);}
    private static Coverage measure(BufferedImage image){return new Coverage(coverage(image,3),coverage(image,12),coverage(image,40));}
    private static boolean unchanged(BufferedImage image){
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(image.getRGB(x,y)!=BG)return false;return true;
    }
    private static int coverage(BufferedImage image,int contrast){
        int count=0;for(int y=0;y<H;y++)for(int x=0;x<W;x++)if(((image.getRGB(x,y)>>>16)&255)-((BG>>>16)&255)>contrast)count++;return count;
    }
}
