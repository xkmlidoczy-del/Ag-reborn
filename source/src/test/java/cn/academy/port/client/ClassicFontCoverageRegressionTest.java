/* Source raster oracle: LambdaLib 1.2.3, MIT; see source-checkpoints notices. */
package cn.academy.port.client;

import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Independent source oracle: does not initialize Minecraft, GL, or a GUI. */
public final class ClassicFontCoverageRegressionTest {
    private static int assertions;
    private static void yes(boolean b,String message){assertions++;if(!b)throw new AssertionError(message);}
    private static void eq(double a,double b){yes(Math.abs(a-b)<1e-9,a+" != "+b);}
    private static BufferedImage sourceGlyph(int cp,boolean bold){
        Font font=new Font(null,bold?Font.BOLD:Font.PLAIN,24);
        int charSize=(int)(font.getSize()*1.4);
        var image=new BufferedImage(charSize,charSize,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();g.setFont(font);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setBackground(new Color(255,255,255,0));g.clearRect(0,0,charSize,charSize);g.setColor(Color.WHITE);
        g.drawString(new String(Character.toChars(cp)),3,1+g.getFontMetrics().getAscent());g.dispose();return image;
    }
    public static void main(String[] args)throws Exception{
        int regularCovered=0,regularEdge=0,removedEdges=0;
        for(boolean bold:new boolean[]{false,true})for(int cp:"CP OL /0123456789RF".codePoints().distinct().toArray()){
            var image=sourceGlyph(cp,bold);int covered=0,edge=0,below=0;
            for(int y=0;y<33;y++)for(int x=0;x<33;x++){
                int argb=image.getRGB(x,y),alpha=argb>>>24;
                int abgr=(argb&0xff00ff00)|((argb>>>16)&255)|((argb&255)<<16);
                yes((abgr>>>24)==alpha,"NativeImage conversion preserves coverage");
                if(alpha>0){covered++;if(alpha<255)edge++;if(alpha*.6/255<.1)below++;}
            }
            if(cp==' ')yes(covered==0,"space is intentionally transparent");
            else{
                yes(covered>0,"source raster contains glyph "+cp);yes(edge>0,"source antialiased edge exists "+cp);
                removedEdges+=below;
                if(!bold){regularCovered+=covered;regularEdge+=edge;}
            }
        }
        yes(removedEdges>0,"vanilla .1 cutoff removes genuine source antialiased edges");
        // A source-visible fade at .06 is wholly discarded by the vanilla shader, even on opaque glyph centers.
        int oldSurvive=0,sourceSurvive=0;var c=sourceGlyph('C',false);
        for(int y=0;y<33;y++)for(int x=0;x<33;x++){
            double alpha=(c.getRGB(x,y)>>>24)/255.0*.06;
            if(alpha>=.1)oldSurvive++;if(alpha>0)sourceSurvive++;
        }
        yes(oldSurvive==0&&sourceSurvive>0,"no-discard shader repairs source low-alpha fade");
        // But settled .6 numbers have opaque cores. The vanilla cutoff cannot explain total settled absence.
        int settledSurvive=0;for(int y=0;y<33;y++)for(int x=0;x<33;x++)if((c.getRGB(x,y)>>>24)/255.0*.6>=.1)settledSurvive++;
        yes(settledSurvive>0,"avoid false global-font diagnosis");
        var t=new ClassicHudTimeline(7);
        t.update(10000,10000,true,false,false,false,1,0,false,0,"meltdowner");
        var ordinary=t.update(11000,11000,true,false,false,false,1,0,false,0,"meltdowner");
        eq(0,ordinary.numbersAlpha());eq(0,ordinary.presetAlpha());
        t.update(11100,11100,true,false,false,false,1,0,true,0,"meltdowner");
        eq(0,t.update(11300,11300,true,false,false,false,1,0,true,0,"meltdowner").numbersAlpha());
        eq(.5,t.update(11500,11500,true,false,false,false,1,0,true,0,"meltdowner").numbersAlpha());
        eq(1,t.update(11700,11700,true,false,false,false,1,0,true,0,"meltdowner").numbersAlpha());
        eq(1,t.update(12150,12150,true,false,false,false,1,0,false,0,"meltdowner").numbersAlpha());
        eq(0,t.update(12450,12450,true,false,false,false,1,0,false,0,"meltdowner").numbersAlpha());
        eq(0,t.update(12500,12500,true,false,false,false,1,0,false,1,"meltdowner").presetAlpha());
        yes(t.update(12900,12900,true,false,false,false,1,0,false,1,"meltdowner").presetAlpha()>0,"C-switch preset hint is intentionally transient");
        if(args.length>0){
            Path out=Path.of(args[0]);Files.createDirectories(out);ImageIO.write(c,"png",out.resolve("source-glyph-C.png").toFile());
            byte[] rgba=new byte[33*33*4];for(int y=0;y<33;y++)for(int x=0;x<33;x++){
                int p=c.getRGB(x,y),i=(y*33+x)*4;rgba[i]=(byte)(p>>>16);rgba[i+1]=(byte)(p>>>8);rgba[i+2]=(byte)p;rgba[i+3]=(byte)(p>>>24);
            }
            Files.write(out.resolve("source-glyph-C.rgba"),rgba);
        }
        System.out.println("ClassicFontCoverageRegressionTest: "+assertions+" source raster/coverage/timing checks passed; covered="+regularCovered+", antialiased="+regularEdge+", settled survivors="+settledSurvive);
    }
}
