/* Adapted from AcademyCraft 1.0.7 Common.Console. GPLv3; see NOTICE. */
package cn.academy.port.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** Deterministic queued 20ms typewriter, 300ms boot animation, 400ms pause and ten-line terminal. */
public final class ClassicDeveloperConsole {
    private record Task(String text,long duration,boolean slow,boolean erase){}
    private final Deque<StringBuilder> lines=new ArrayDeque<>();
    private final Deque<Task> queue=new ArrayDeque<>();
    private Task active;
    private long began;
    private int printed;
    private boolean busy;
    private String input="";
    public ClassicDeveloperConsole(String welcome,String failure,String startup,long seed){
        queue.add(new Task(welcome,0,true,false));queue.add(new Task("",400,false,false));
        Random random=new Random(seed);
        for(int i=1;i<=6;i++)queue.add(new Task((i*10+random.nextInt(6)-3)+"%",300,false,true));
        queue.add(new Task((64+random.nextInt(4))+"%",300,false,true));queue.add(new Task(failure,300,false,false));
        queue.add(new Task(startup,0,true,false));
    }
    public void tick(long now){
        while(true){
            if(active==null){active=queue.pollFirst();if(active==null)return;began=now;printed=0;if(!active.slow())output(active.text());}
            if(active.slow()){
                int end=(int)Math.min(active.text().length(),Math.max(0,(now-began)/20));
                if(end>printed){output(active.text().substring(printed,end));printed=end;}
                if(printed<active.text().length())return;
            }else if(now-began<active.duration())return;
            if(active.erase())output("\b".repeat(active.text().length()));active=null;
        }
    }
    public boolean ready(){return active==null&&queue.isEmpty()&&!busy;}
    public void busy(boolean value){busy=value;}
    public boolean busy(){return busy;}
    public void type(char value){if(ready()&&value>=32&&value!=127&&input.length()<128)input+=value;}
    public void backspace(){if(ready()&&!input.isEmpty())input=input.substring(0,input.length()-1);}
    public String submit(){if(!ready())return "";String result=input;output("OS >"+input+"\n");input="";return result;}
    public void output(String text){
        if(lines.isEmpty())lines.add(new StringBuilder());
        for(char ch:text.toCharArray()){
            var line=lines.getLast();
            if(ch=='\b'){if(!line.isEmpty())line.setLength(line.length()-1);}
            else if(ch=='\n'){lines.add(new StringBuilder());while(lines.size()>10)lines.removeFirst();}
            else line.append(ch);
        }
    }
    public void replaceProgress(String text){if(lines.isEmpty())lines.add(new StringBuilder());lines.getLast().setLength(0);output(text);}
    public List<String> visibleLines(long now){
        var result=new ArrayList<String>();for(var line:lines)result.add(line.toString());
        if(ready())result.add("OS >"+input+(now%1000<500?"_":""));
        while(result.size()>10)result.removeFirst();return List.copyOf(result);
    }
    public String input(){return input;}
}
