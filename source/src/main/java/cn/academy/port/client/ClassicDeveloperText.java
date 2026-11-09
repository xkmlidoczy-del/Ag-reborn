/* LambdaLib1.2.3 Fragmentor/IFont.drawSeperated adaptation. MIT; see NOTICE. */
package cn.academy.port.client;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Original word/CJK/punctuation fragmentation, not modern bitmap-font line wrapping. */
public final class ClassicDeveloperText {
    private static final String PUNCT=",.<>?;:'\"[]{}【】：；“‘《》，。？";
    private enum Kind { WORD(false),SPACE(true),CJK(true),PUNCT(true);final boolean split;Kind(boolean split){this.split=split;} }
    private record Token(Kind kind,String value){}
    private static Kind kind(char ch){return PUNCT.indexOf(ch)>=0?Kind.PUNCT:Character.isWhitespace(ch)?Kind.SPACE:Character.isIdeographic(ch)?Kind.CJK:Kind.WORD;}
    public static List<String> wrap(String text,double limit,ToDoubleFunction<String> width){
        var tokens=new ArrayList<Token>();int position=0;
        while(position<text.length()){
            int begin=position;Kind initial=kind(text.charAt(position++));
            while(position<text.length()){
                Kind next=kind(text.charAt(position));if(initial!=Kind.CJK&&initial!=next&&next!=Kind.PUNCT)break;position++;
            }
            tokens.add(new Token(initial,text.substring(begin,position)));
        }
        var result=new ArrayList<String>();StringBuilder line=new StringBuilder();double local=0;
        for(var token:tokens){
            String content=token.value();double length=width.applyAsDouble(content);
            if(local+length>limit){
                if(!token.kind().split){if(!line.isEmpty())result.add(line.toString());line.setLength(0);line.append(content);local=length;}
                else while(!content.isEmpty()){
                    double accumulated=0;int end=0;
                    for(;end<content.length()&&local+accumulated<=limit;end++)accumulated+=width.applyAsDouble(content.substring(end,end+1));
                    if(end<content.length()&&PUNCT.indexOf(content.charAt(end))>=0)end++;
                    // Source never sees zero-width limits. This guard bounds malformed runtime inputs.
                    if(end==0)end=1;
                    line.append(content,0,end);result.add(line.toString());line.setLength(0);local=0;content=content.substring(end);
                }
            }else{line.append(content);local+=length;}
        }
        if(!line.isEmpty())result.add(line.toString());return List.copyOf(result);
    }
    private ClassicDeveloperText(){}
}
