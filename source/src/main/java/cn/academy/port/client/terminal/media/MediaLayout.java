package cn.academy.port.client.terminal.media;

import java.io.InputStream;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Geometry is read directly from the byte-preserved classic media-player XML, not inferred from screenshots. */
public final class MediaLayout {
    public record Box(double x,double y,double width,double height,double scale,String alignX,String alignY){
        public Box resolved(double parentWidth,double parentHeight){return new Box(x+align(alignX,parentWidth,width*scale),y+align(alignY,parentHeight,height*scale),width,height,scale,"LEFT","TOP");}
        private static double align(String align,double parent,double child){return switch(align){case "RIGHT","BOTTOM"->parent-child;case "CENTER"->(parent-child)/2;default->0;};}
        public boolean contains(double px,double py){return px>=x&&py>=y&&px<x+width*scale&&py<y+height*scale;}
    }
    public record Element(Box box,double fontSize,String textAlign,String heightAlign,double alpha){}
    private final Map<String,Element> elements;
    private MediaLayout(Map<String,Element> elements){this.elements=Map.copyOf(elements);}
    public Element element(String path){Element e=elements.get(path);if(e==null)throw new IllegalArgumentException("Missing media XML widget "+path);return e;}
    public Box box(String path,double pw,double ph){return element(path).box.resolved(pw,ph);}
    public static MediaLayout main(){return Holder.MAIN;}public static MediaLayout hud(){return Holder.HUD;}
    private static final class Holder{static final MediaLayout MAIN=load("assets/academy/guis/media_player.xml"),HUD=load("assets/academy/guis/media_player_aux.xml");}
    public static MediaLayout load(String resource){try(InputStream in=MediaLayout.class.getClassLoader().getResourceAsStream(resource)){
        if(in==null)throw new IllegalStateException("Missing media layout "+resource);var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");var doc=f.newDocumentBuilder().parse(in);Map<String,Element>map=new LinkedHashMap<>();for(Node n=doc.getDocumentElement().getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof org.w3c.dom.Element e&&e.getTagName().equals("Widget"))read(e,"",map);return new MediaLayout(map);
    }catch(Exception e){throw new IllegalStateException("Invalid media layout "+resource,e);}}
    private static void read(org.w3c.dom.Element widget,String parent,Map<String,Element>map){String path=parent.isEmpty()?widget.getAttribute("name"):parent+"/"+widget.getAttribute("name");org.w3c.dom.Element transform=null,text=null;
        for(Node n=widget.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof org.w3c.dom.Element c&&c.getTagName().equals("Component")){if(c.getAttribute("class").endsWith(".Transform"))transform=c;if(c.getAttribute("class").endsWith(".TextBox"))text=c;}
        if(transform!=null){double font=0,alpha=1;String align="LEFT",height="TOP";if(text!=null){org.w3c.dom.Element option=child(text,"option");font=number(option,"fontSize",12);align=value(option,"align","LEFT");height=value(text,"heightAlign","TOP");org.w3c.dom.Element color=child(option,"color");alpha=number(color,"a",1);}
            map.put(path,new Element(new Box(number(transform,"x",0),number(transform,"y",0),number(transform,"width",0),number(transform,"height",0),number(transform,"scale",1),value(transform,"alignWidth","LEFT"),value(transform,"alignHeight","TOP")),font,align,height,alpha));}
        for(Node n=widget.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof org.w3c.dom.Element e&&e.getTagName().equals("Widget"))read(e,path,map);
    }
    private static org.w3c.dom.Element child(org.w3c.dom.Element p,String name){if(p!=null)for(Node n=p.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof org.w3c.dom.Element e&&e.getTagName().equals(name))return e;return null;}
    private static String value(org.w3c.dom.Element p,String name,String fallback){var e=child(p,name);return e==null?fallback:e.getTextContent();}
    private static double number(org.w3c.dom.Element p,String name,double fallback){return Double.parseDouble(value(p,name,Double.toString(fallback)));}
    public static int visibleRows(){return(int)Math.floor(main().element("back/area").box.height/main().element("back/t_one").box.height);}
    public static int maxScroll(int count){return Math.max(0,count-visibleRows());}
}
