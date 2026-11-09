/* AcademyCraft 1.0.7 ACTutorial/GuiTutorial document split adaptation. GPLv3. */
package cn.academy.port.client.tutorial;

import java.util.Locale;

public record TutorialDocument(String title,String brief,String content) {
    public static final String UNKNOWN="![title]\nUNKNOWN \n![brief]\n![content]\n ";
    public static TutorialDocument parse(String raw){
        int title=raw.indexOf("![title]"),brief=raw.indexOf("![brief]"),content=raw.indexOf("![content]");
        if(title<0||title>=brief||brief>=content)throw new IllegalArgumentException("Malformed classic tutorial document");
        return new TutorialDocument(trimHead(raw.substring(title+8,brief)),trimHead(raw.substring(brief+8,content)),trimHead(raw.substring(content+10)));
    }
    private static String trimHead(String s){int i=0;while(i<s.length()&&(s.charAt(i)=='\r'||s.charAt(i)=='\n'||s.charAt(i)==' '))i++;return s.substring(i);}
    /** Modern pack paths must be lowercase; document bytes are the original source bytes. */
    public static String language(String configured){return configured.toLowerCase(Locale.ROOT);}
}
