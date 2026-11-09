package cn.academy.port.client.tutorial;

import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import javax.tools.ToolProvider;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Headless source-backed client checks; no Minecraft, renderer, registry bootstrap or game launch. */
public final class TutorialClientRegressionTest {
    private static int checks;
    private static Path files,resources,fixtures;
    public static void main(String[] args)throws Exception {
        files=args.length>0?Path.of(args[0]):Path.of(System.getProperty("academy.tutorial.root",System.getProperty("user.dir")));
        resources=files.resolve("src/main/resources");fixtures=files.resolve("src/test/resources/classic-tutorial-client");
        assets();geometry();documents();fragmentorOracle();markdown();navigation();notifications();bindings();
        System.out.println("TutorialClientRegressionTest: "+checks+" source-backed checks passed");
    }
    private static void check(boolean result,String message){checks++;if(!result)throw new AssertionError(message);}
    private static void equal(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-7,message+" actual="+actual+" expected="+expected);}
    private static String read(Path path)throws Exception{return Files.readString(path,StandardCharsets.UTF_8);}
    private static String sha(Path path)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
    private static void assets()throws Exception{
        var all=JsonParser.parseString(read(fixtures.resolve("source-assets.json"))).getAsJsonArray();int docs=0;
        for(var entry:all){var object=entry.getAsJsonObject();String target=object.get("target").getAsString();check(sha(files.resolve(target)).equals(object.get("sha256").getAsString()),"source bytes "+target);if(target.endsWith(".md"))docs++;}
        check(docs==28,"14 source documents in both languages");
        for(var entry:JsonParser.parseString(read(fixtures.resolve("source-witnesses.json"))).getAsJsonArray()){var o=entry.getAsJsonObject();check(sha(fixtures.resolve(o.get("fixture").getAsString())).equals(o.get("sha256").getAsString()),"source witness "+o.get("fixture"));}
        for(var entry:all){String target=entry.getAsJsonObject().get("target").getAsString();check(!target.endsWith(".ogg")&&!target.endsWith(".mp3")&&!Path.of(target).getFileName().toString().startsWith("media_"),"guide source songs and cover assets excluded "+target);}
        var cards=JsonParser.parseString(read(resources.resolve("assets/academy/tutorials/source_recipes.json"))).getAsJsonArray();check(cards.size()==30,"all 30 source output-recipe cards");
        int mf=0,rf=0,fusor=0;for(var e:cards){var c=e.getAsJsonObject();if(c.get("type").getAsString().equals("metal_former"))mf++;String out=c.getAsJsonObject("output").get("item").getAsString();if(out.equals("academy:rf_input")||out.equals("academy:rf_output"))rf++;if(out.equals("academy:imag_fusor"))fusor++;}
        check(mf==4&&rf==4&&fusor==3,"source MF alternatives, RF conversion recipes and fusor variants");
    }
    private static Element widget(Element root,String name){var nodes=root.getElementsByTagName("Widget");for(int i=0;i<nodes.getLength();i++){var e=(Element)nodes.item(i);if(e.getAttribute("name").equals(name))return e;}throw new AssertionError(name);}
    private static double transform(Element widget,String field){var nodes=widget.getChildNodes();for(int i=0;i<nodes.getLength();i++)if(nodes.item(i) instanceof Element e&&e.getTagName().equals("Component")&&e.getAttribute("class").endsWith(".Transform"))return Double.parseDouble(e.getElementsByTagName(field).item(0).getTextContent());throw new AssertionError(field);}
    private static void geometry()throws Exception{
        var root=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(fixtures.resolve("tutorial.xml").toFile()).getDocumentElement();Element frame=widget(root,"frame"),left=widget(root,"leftPart"),center=widget(root,"centerPart"),rightPart=widget(root,"rightPart"),right=widget(root,"rightWindow");
        equal(TutorialLayout.FRAME_WIDTH,transform(frame,"width"),"source frame width");equal(TutorialLayout.FRAME_HEIGHT,transform(frame,"height"),"source frame height");equal(TutorialLayout.LEFT.x(),transform(left,"x"),"source left x");equal(TutorialLayout.LEFT.y(),(transform(frame,"height")-transform(left,"height"))/2,"source centered left y");
        equal(TutorialLayout.RIGHT.x(),transform(rightPart,"x")+transform(rightPart,"width")-transform(right,"width"),"source right aligned x");equal(TutorialLayout.RIGHT.y(),TutorialLayout.LEFT.y()+transform(rightPart,"height")-transform(right,"height"),"source bottom right y");
        var text=widget(center,"text");equal(TutorialLayout.CONTENT.x(),transform(rightPart,"x")+transform(text,"x"),"source text x");equal(TutorialLayout.CONTENT.y(),TutorialLayout.LEFT.y()+(transform(center,"height")-transform(text,"height"))/2,"source centered text y");equal(TutorialLayout.CONTENT.width(),transform(text,"width"),"source content clip width");equal(TutorialLayout.CONTENT.height(),transform(text,"height"),"source content clip height");
        var list=widget(left,"list");equal(TutorialLayout.LIST.x(),transform(left,"x")+transform(list,"x"),"source list x");equal(TutorialLayout.LIST.y(),TutorialLayout.LEFT.y()+transform(list,"y"),"source list y");
        String source=read(fixtures.resolve("GuiTutorial.java.txt"));check(source.contains("REF_WIDTH = 480"),"source width scaling witness");check(source.contains("widthLimit_$eq(130)")&&source.contains("widthLimit_$eq(150)"),"source markdown widths");
        for(int width:new int[]{240,480,854,1920})for(int height:new int[]{240,480,1080}){var p=TutorialLayout.placement(width,height);equal(p.scale(),width/480d,"width scaling");equal(p.localX(p.x()+94*p.scale()),94,"mouse inverse x");equal(p.localY(p.y()+14.75*p.scale()),14.75,"mouse inverse y");}
        check(!TutorialLayout.listVisible(true,2.4)&&TutorialLayout.listVisible(true,2.400001)&&TutorialLayout.listVisible(false,0),"source intro delayed list vs repeat open");equal(TutorialLayout.logo3Y(.7),164.375,"initial emblem position");equal(TutorialLayout.logo3Y(1.1),65.375,"source final emblem position");equal(TutorialLayout.scrollRange(400),199.5,"source scroll height plus10");equal(TutorialLayout.scrollRange(50),0,"short article no scrolling");
        var glow=TutorialLayout.glowSegments(true,.9);equal(glow[0],200,"source second-stage glow start");equal(glow[1],500,"source glow endpoint");
        check(TutorialLayout.PREVIOUS.contains(271,52)&&!TutorialLayout.PREVIOUS.contains(270,52)&&TutorialLayout.NEXT.contains(406,52),"preview button authored hit bounds");
    }
    private static TutorialMarkdown.Context context(){return new TutorialMarkdown.Context(){public double width(String text,double size,TutorialMarkdown.Style style){return text.length()*size*.5;}public String key(String id){return Map.of("ability_activation","V","edit_preset","N","switch_preset","C").getOrDefault(id,"???");}public String misakaName(){return "Misaka No.1234";}public double[] imageSize(String resource){return new double[]{300,150};}};}
    private static void documents()throws Exception{
        for(String language:List.of("en_us","zh_cn"))try(var docs=Files.list(resources.resolve("assets/academy/tutorials/"+language))){for(Path doc:docs.toList()){var parsed=TutorialDocument.parse(read(doc));check(!parsed.title().isBlank(),"title "+doc);check(!parsed.content().isBlank(),"content "+doc);var body=new TutorialMarkdown(parsed.content(),150,context());check(Double.isFinite(body.height())&&body.height()>0,"renderable source content "+doc);check(body.draws().stream().filter(d->d instanceof TutorialMarkdown.Image).allMatch(d->((TutorialMarkdown.Image)d).width()<=150),"source image width fit "+doc);}}
        check(TutorialDocument.language("zh_CN").equals("zh_cn")&&TutorialDocument.language("en_US").equals("en_us"),"modern lowercase resource aliases");
        check(TutorialDocument.parse("![title]\r\n T\r\n![brief]\n B\n![content]\n C").title().equals("T\r\n"),"source trimHead preserves trailing source bytes");
        boolean malformed=false;try{TutorialDocument.parse("![content]wrong![title]");}catch(IllegalArgumentException expected){malformed=true;}check(malformed,"malformed marker order rejected");check(TutorialDocument.parse(TutorialDocument.UNKNOWN).title().strip().equals("UNKNOWN"),"source final fallback");
    }
    private static void fragmentorOracle()throws Exception{
        Path work=Files.createTempDirectory("classic-tutorial-original-fragmentor-");Path java=work.resolve("Fragmentor.java");Files.copy(fixtures.resolve("Fragmentor.java.txt"),java);
        int result=ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-cp",System.getProperty("java.class.path"),"-d",work.toString(),java.toString());check(result==0,"original LambdaLib Fragmentor compiled unchanged");
        try(var loader=new URLClassLoader(new java.net.URL[]{work.toUri().toURL()},TutorialClientRegressionTest.class.getClassLoader())){
            Class<?> original=loader.loadClass("cn.lambdalib.util.client.font.Fragmentor"),metrics=loader.loadClass("cn.lambdalib.util.client.font.Fragmentor$IFontSizeProvider");Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{metrics},(o,m,args)->m.getName().equals("getCharWidth")?4d:((String)args[0]).length()*4d);var method=original.getMethod("toMultiline",String.class,metrics,double.class,double.class);
            var samples=new ArrayList<String>();samples.addAll(List.of("short text with punctuation, yes.","很长的中文字符串，标点符号。","wordtoolongtosplit","> quoted __text__", " \t space"));
            Random random=new Random(107);String alphabet="abCD  ,.?御坂云终端";for(int i=0;i<500;i++){var sample=new StringBuilder();for(int n=random.nextInt(70)+1;n>0;n--)sample.append(alphabet.charAt(random.nextInt(alphabet.length())));samples.add(sample.toString());}
            for(String text:samples)for(double width:new double[]{12,40,130,150})for(double x:new double[]{0,8,24}){Object expected=method.invoke(null,text,proxy,x,width);check(TutorialFragmentor.multiline(text,s->s.length()*4d,x,width).equals(expected),"original fragmentation "+text+" @"+x+"/"+width);}
        }
    }
    private static void markdown(){
        var markdown=new TutorialMarkdown("# Header\n\n__bold__ and **italic** ![key id=\"ability_activation\"] ![misakaname]\n\n![image](academy:textures/tutorial/ability_ui.png)",150,context());
        var texts=markdown.draws().stream().filter(d->d instanceof TutorialMarkdown.Text).map(d->(TutorialMarkdown.Text)d).toList();
        check(texts.stream().anyMatch(t->t.value().equals("Header")&&t.size()==12.8&&t.style()==TutorialMarkdown.Style.BOLD),"source header indexing");check(texts.stream().anyMatch(t->t.value().equals("bold")&&t.style()==TutorialMarkdown.Style.BOLD),"source underscores are bold");check(texts.stream().anyMatch(t->t.value().equals("italic")&&t.style()==TutorialMarkdown.Style.ITALIC),"source double stars are italic");check(texts.stream().anyMatch(t->t.value().equals("V")&&t.reference()),"live key tag colored reference");check(texts.stream().anyMatch(t->t.value().contains("1234")&&t.style()==TutorialMarkdown.Style.BOLD),"stable Misaka bold tag");
        var image=(TutorialMarkdown.Image)markdown.draws().stream().filter(d->d instanceof TutorialMarkdown.Image).findFirst().orElseThrow();equal(image.width(),150,"source image constrained width");equal(image.height(),75,"source image aspect");check(image.hover().equals("image"),"source image hover");
        var flow=new TutorialMarkdown("one\ntwo\n\nthree",150,context());var lines=flow.draws().stream().filter(d->d instanceof TutorialMarkdown.Text).map(d->(TutorialMarkdown.Text)d).toList();equal(lines.get(0).y(),lines.get(1).y(),"ordinary source lines share paragraph");check(lines.get(2).y()>lines.get(1).y(),"blank line separates paragraph");
        check(new TutorialMarkdown("![key id=\"open_data_terminal\"]",150,context()).draws().stream().anyMatch(d->d instanceof TutorialMarkdown.Text t&&t.value().equals("???")),"unsupported key honest source unknown");
    }
    private static void navigation(){
        var source=List.of("welcome","ores","phase","basis","wireless");check(TutorialNavigation.researchOrder(source,p->List.of("welcome","basis","wireless").contains(p)).equals(List.of("welcome","basis","wireless","ores","phase")),"source learned-first stable ordering");
        var views=new TutorialNavigation.Views();views.reset(3,2,0);views.shift(-1);check(views.group()==0&&views.view()==2,"previous subview wraps");views.select(1);views.shift(1);check(views.view()==1,"second group own index");views.select(0);check(views.view()==2,"source group revisit preserves viewed recipe");views.update(3,2,0);check(views.view()==2,"resize/refreshed snapshot preserves index");views.update(1,2);check(views.view()==0,"changed recipe list clamps index");views.select(1);check(views.view()==1,"other group index retained");views.reset(3,2);check(views.group()==0&&views.view()==0,"new page resets source TutInfo");views.reset();views.shift(1);check(views.view()==0,"empty preview safe");
        for(int count=1;count<10;count++){views.reset(count);for(int step=0;step<2*count+1;step++){views.shift(1);check(views.view()==(step+1)%count,"next recipe wraps count="+count);}}
    }
    private static void notifications()throws Exception{
        String source=read(fixtures.resolve("NotifyUI.java.txt"));check(source.contains("KEEP_TIME = 6000")&&source.contains("BLEND_IN_TIME = 500, SCAN_TIME = 500, BLEND_OUT_TIME = 300"),"source notification duration witnesses");
        var first=TutorialNotificationTimeline.frame(0);equal(first.background(),0,"notification back fade starts0");equal(first.iconX(),420,"source notification icon start");equal(TutorialNotificationTimeline.frame(350).icon(),.5,"source icon delayed blend");var scan=TutorialNotificationTimeline.frame(750);equal(scan.iconX(),420+(34-420)*Math.sin(Math.PI/4),"source notification sine scan");equal(scan.text(),Math.sin(Math.PI/4),"source text scan blend");equal(TutorialNotificationTimeline.frame(1000).iconX(),34,"source notification icon endpoint");equal(TutorialNotificationTimeline.frame(5850).background(),.5,"source notification fade");check(!TutorialNotificationTimeline.frame(6000).visible(),"source notification expires6s");
    }
    private static void bindings()throws Exception{
        Path client=files.resolve("src/main/java/cn/academy/port/client/tutorial");String screen=read(client.resolve("ClassicTutorialScreen.java")),previews=read(client.resolve("TutorialPreviews.java")),entry=read(client.resolve("TutorialClient.java")),font=read(client.resolve("TutorialFont.java")),notice=read(client.resolve("TutorialNotifications.java"));
        check(screen.contains("if(state.visible(selected.id()))drawContent")&&screen.contains("drawBrief(canvas,placement);drawPreview"),"locked article hides full content while showing brief and previews");check(screen.contains("isPauseScreen(){return true;}"),"inherited source singleplayer pause default");check(screen.contains("progress=0")&&screen.contains("previewViews.reset(groupSizes())"),"new source TutInfo resets scroll/previews");check(screen.contains("previewViews.select(i)")&&!screen.contains("viewIndex=0"),"group switches preserve own recipe positions");
        check(screen.contains("source_details")&&screen.contains("case \"misc\"")&&screen.contains("case \"ability_basis\"")&&screen.contains("case \"wireless_network\"")&&screen.contains("case \"develop_ability\"")&&screen.contains("TutorialPreviews.registeredItem(target)==null"),"contextual source-only notice conditional on missing references");
        check(entry.contains("AcademyClient.request(\"tutorial_open\",\"\")")&&entry.contains("new ClassicTutorialScreen(state)"),"direct open and server-authored snapshot screen entry");check(entry.contains("TutorialStorage.decode(snapshot)")&&entry.contains("TutorialActivatedEvent"),"sync decodes before source activation event");check(previews.contains("BuiltInRegistries.ITEM.containsKey(id)")&&previews.contains("RecipeType.SMELTING"),"registered-only stack resolution and furnace recipe adapter");check(previews.contains("Source recipe remains")||previews.contains("A source recipe remains"),"conditional source references");check(font.contains("Font.ITALIC")&&font.contains("Font.BOLD")&&font.contains("NEXT_TEXTURE_OWNER"),"source system font faces and isolated texture ownership");check(notice.contains("tutorial/update_notify")&&notice.contains("guis/notification/back")&&notice.contains("RenderGuiEvent.Post"),"source notification media actually bound");check(notice.contains("!mc.isPaused()"),"source notification time pauses with game");
    }
}
