/* LambdaLib1.2.3 TextBox caret/paste/delete/emit semantics. MIT; see NOTICE. */
package cn.academy.port.client;
import java.util.function.ToDoubleFunction;
/** Plain source textbox state. The64-character transport limit is an explicit modern adaptation. */
public final class ClassicInterfererEditor {
    private String content="";private int caret,offset;
    public String content(){return content;}public int caret(){return caret;}public int offset(){return offset;}
    public String display(ToDoubleFunction<String> width){return emit(content.substring(offset),40,width);}
    public double caretX(ToDoubleFunction<String> width){return width.applyAsDouble(content.substring(offset,caret));}
    public void right(ToDoubleFunction<String> width){caret=Math.min(content.length(),caret+1);region(width);}
    public void left(){caret=Math.max(0,caret-1);if(caret<offset)offset=caret;}
    public void backspace(ToDoubleFunction<String> width){if(caret>0){content=content.substring(0,caret-1)+content.substring(caret);caret--;if(offset>0)offset--;region(width);validate();}}
    public void delete(){content="";validate();}
    public void type(char input,ToDoubleFunction<String> width){if(input=='\u00a7'||Character.isISOControl(input)||content.length()>=64)return;content=content.substring(0,caret)+input+content.substring(caret);caret++;region(width);}
    /** Source Ctrl+V inserts at the caret but deliberately does not advance it. */
    public void paste(String value){String clean=value.codePoints().filter(cp->!Character.isISOControl(cp)).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();int take=Math.min(clean.length(),64-content.length());content=content.substring(0,caret)+clean.substring(0,take)+content.substring(caret);validate();}
    public void click(double x,ToDoubleFunction<String> width){String visible=content.substring(offset);double sum=0;int index=0;for(;sum<x&&index<visible.length();index++)sum+=width.applyAsDouble(visible.substring(index,index+1));if(index>0&&x<sum-width.applyAsDouble(visible.substring(index-1,index))*.5)index--;caret=offset+index;region(width);}
    private void validate(){if(offset>=content.length()||caret>content.length()){offset=caret=0;}}
    private void region(ToDoubleFunction<String> width){double distance=caretX(width);if(distance>40){String local=content.substring(offset);double sum=0;int index=0;for(;index<caret-offset&&distance-sum>40;index++)sum+=width.applyAsDouble(local.substring(index,index+1));offset+=index;}}
    /** Literal source emit draws the final character that crosses the available width. */
    public static String emit(String value,double limit,ToDoubleFunction<String> width){double sum=0;int i=0;for(;i<value.length()&&sum<limit;i++)sum+=width.applyAsDouble(value.substring(i,i+1));return value.substring(0,i);}
}
