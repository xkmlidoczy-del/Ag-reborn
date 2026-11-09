/* LambdaLib 1.2.3 MarkdownParser/GLMarkdownRenderer adaptation (MIT); AcademyCraft tags GPLv3. */
package cn.academy.port.client.tutorial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Source markdown dialect. Ordinary source lines flow together; blank lines end paragraphs. */
public final class TutorialMarkdown {
    public enum Style { NORMAL, BOLD, ITALIC }
    public interface Context {
        double width(String text,double size,Style style);
        String key(String id);
        String misakaName();
        double[] imageSize(String resource);
    }
    public sealed interface Draw permits Text,Image,Dot{}
    public record Text(String value,double x,double y,double size,Style style,boolean reference) implements Draw{}
    public record Image(String resource,String hover,double x,double y,double width,double height) implements Draw{}
    public record Dot(double x,double y,double size) implements Draw{}
    private static final Pattern TAG=Pattern.compile("!\\[([^\\[\\]]*)\\](?:\\(([^()]+)\\))?");
    private static final Pattern PROPERTY=Pattern.compile("([^ =]+)\\s*=\\s*(?:\"([^\"]*)\"|([^ =]+))");
    private final List<Draw> draws=new ArrayList<>();
    private final Context context;
    private final double width;
    private double x,y,lastSize;
    private boolean lineBegin=true;
    public TutorialMarkdown(String source,double width,Context context){
        this.width=width;this.context=context;
        source.lines().forEach(this::line);
    }
    public List<Draw> draws(){return List.copyOf(draws);}
    public double height(){return y+8;}
    private void line(String line){
        double size=8;Style style=Style.NORMAL;boolean list=false,reference=false,newline=false;
        if(line.startsWith("#")){
            int count=0;while(count<line.length()&&line.charAt(count)=='#')count++;
            int level=Math.min(6,count);size*=switch(level){case 1->1.6;case 2->1.4;case 3->1.2;default->1;};
            style=level<=3?Style.BOLD:Style.NORMAL;line=line.substring(count).stripLeading();newline=true;
        }else if(line.startsWith("* ")){list=true;line=line.substring(2);newline=true;}
        else if(line.startsWith("> ")){reference=true;line=line.substring(2);}
        if(line.isEmpty())newline(false);else span(line,size,style,reference,list);
        if(newline)newline(false);
    }
    private void span(String text,double size,Style style,boolean reference,boolean list){
        var matcher=TAG.matcher(text);int pos=0;
        while(matcher.find()){
            styled(text.substring(pos,matcher.start()),size,style,reference,list);list=false;
            if(matcher.group(2)!=null)image(matcher.group(2),matcher.group(1),Map.of());
            else{
                String tag=matcher.group(1);int end=tag.indexOf(' ');String name=end<0?tag:tag.substring(0,end);
                Map<String,String> attrs=new HashMap<>();var properties=PROPERTY.matcher(end<0?"":tag.substring(end));
                while(properties.find())attrs.put(properties.group(1),properties.group(2)==null?properties.group(3):properties.group(2));
                switch(name){
                    case "key"->text(context.key(attrs.getOrDefault("id","")),size,style,true,false);
                    case "misakaname"->text(context.misakaName(),size,Style.BOLD,false,false);
                    case "img"->image(attrs.getOrDefault("src",""),attrs.getOrDefault("hover",""),attrs);
                    default->{} // The source renderer ignores unrecognized tags.
                }
            }
            pos=matcher.end();
        }
        styled(text.substring(pos),size,style,reference,list);
    }
    private void styled(String text,double size,Style style,boolean reference,boolean list){
        int star=text.indexOf("**"),under=text.indexOf("__");int p=star<0?under:under<0?star:Math.min(star,under);
        if(p<0){text(text,size,style,reference,list);return;}
        String marker=text.substring(p,p+2);int end=text.indexOf(marker,p+2);
        if(end<0){text(text,size,style,reference,list);return;}
        text(text.substring(0,p),size,style,reference,list);
        text(text.substring(p+2,end),size,marker.equals("__")?Style.BOLD:Style.ITALIC,reference,list&&p==0);
        styled(text.substring(end+2),size,style,reference,false);
    }
    private void text(String value,double size,Style style,boolean reference,boolean list){
        if(value.isEmpty())return;
        if(reference&&x==0)x=size;
        List<String> lines=TutorialFragmentor.multiline(value,text->context.width(text,size,style),x,width);
        if(list&&lineBegin){double dot=size*.2;x=size*1.2;draws.add(new Dot(x,y+size/2-dot/2,dot));x+=dot*2;}
        for(int i=0;i<lines.size();i++){
            String line=lines.get(i);
            // GLMarkdownRenderer retains a 1.2-width tolerance for a first unbreakable word.
            if(i!=0||context.width(line,size,style)+x>width*1.2)newline(true);
            emit(line,size,style,reference);
        }
    }
    private void emit(String text,double size,Style style,boolean reference){
        if(text.isEmpty())return;draws.add(new Text(text,x,y,size,style,reference));x+=context.width(text,size,style);lastSize=size;lineBegin=false;
    }
    private void newline(boolean continuation){x=0;y+=lastSize+(continuation?0:4);if(!continuation)lastSize=0;lineBegin=!continuation;}
    private void image(String resource,String hover,Map<String,String> attrs){
        if(resource.isEmpty())return;double[] dimensions=context.imageSize(resource);double scale=number(attrs,"scale",1);
        double w=number(attrs,"width",dimensions[0])*scale,h=number(attrs,"height",dimensions[1])*scale;
        if(w+x>width){double ratio=Math.max(0,(width-x)/w);w*=ratio;h*=ratio;}
        double iy=h>=lastSize?y:y+lastSize-h;draws.add(new Image(resource,hover,x,iy,w,h));
        double newY=y+Math.max(0,h-8);
        // Source raises text already emitted on the same line to the image baseline.
        for(int i=draws.size()-2;i>=0&&draws.get(i) instanceof Text;i--){Text t=(Text)draws.get(i);if(t.y()!=y)break;draws.set(i,new Text(t.value(),t.x(),newY,t.size(),t.style(),t.reference()));}
        x+=w;y=newY;lastSize=8;
    }
    private static double number(Map<String,String> attrs,String key,double fallback){try{return Double.parseDouble(attrs.getOrDefault(key,Double.toString(fallback)));}catch(NumberFormatException ignored){return fallback;}}
}
