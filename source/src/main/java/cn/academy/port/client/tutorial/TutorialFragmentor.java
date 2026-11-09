/* LambdaLib 1.2.3 Fragmentor adaptation. MIT; source witness is retained in client audit. */
package cn.academy.port.client.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Preserve source word/CJKV/punctuation grouping, including its end-of-line punctuation tolerance. */
final class TutorialFragmentor {
    private static final String PUNCT=",.<>?;:'\"[]{}【】：；“‘《》，。？";
    private enum Type{WORD,SPACE,CJKV,PUNCT}
    private static Type type(char ch){return punct(ch)?Type.PUNCT:Character.isWhitespace(ch)?Type.SPACE:Character.isIdeographic(ch)?Type.CJKV:Type.WORD;}
    private static boolean punct(char ch){return PUNCT.indexOf(ch)>=0;}
    static List<String> multiline(String source,ToDoubleFunction<String> metrics,double localX,double limit){
        var lines=new ArrayList<String>();var builder=new StringBuilder();int index=0;
        while(index<source.length()){
            Type initial=type(source.charAt(index));int from=index++;
            for(;index<source.length();index++){Type next=type(source.charAt(index));if(initial!=Type.CJKV&&initial!=next&&next!=Type.PUNCT)break;}
            String token=source.substring(from,index);double length=metrics.applyAsDouble(token);
            if(localX+length>limit){
                if(initial==Type.WORD){if(!builder.isEmpty())lines.add(builder.toString());builder.setLength(0);builder.append(token);localX=length;}
                else while(!token.isEmpty()){
                    double accumulated=0;int i=0;
                    for(;i<token.length()&&localX+accumulated<=limit;i++)accumulated+=metrics.applyAsDouble(token.substring(i,i+1));
                    if(i<token.length()&&punct(token.charAt(i)))i++;
                    // Source can emit an empty fragment when an earlier unsplittable word exceeded the limit.
                    builder.append(token,0,i);lines.add(builder.toString());builder.setLength(0);localX=0;token=token.substring(i);
                }
            }else{builder.append(token);localX+=length;}
        }
        if(!builder.isEmpty())lines.add(builder.toString());return lines;
    }
    private TutorialFragmentor(){}
}
