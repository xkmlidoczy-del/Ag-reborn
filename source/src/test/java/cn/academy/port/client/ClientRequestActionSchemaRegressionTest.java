package cn.academy.port.client;

import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.TargetedContextTermination;
import cn.academy.port.highpower.HighPowerInput;
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/** Parse actual Java syntax, audit finite client action sets, and separately bound known value schemas. */
public final class ClientRequestActionSchemaRegressionTest {
    private static final int ACTION_LIMIT=32, VALUE_LIMIT=64;
    private static int checks;
    private record Eval(Set<String> values, boolean complete) {
        static Eval one(String text){return new Eval(Set.of(text),true);}
        static Eval unknown(){return new Eval(Set.of(),false);}
    }
    private record Site(String domain,String action,String location) {}
    private final Map<String,ExpressionTree> constants=new HashMap<>();
    private final Map<String,Set<String>> registered=new LinkedHashMap<>();
    private final List<Site> emitted=new ArrayList<>();
    private final Set<String> dynamic=new TreeSet<>();
    private final Map<String,String> source=new HashMap<>();
    private final Map<String,CompilationUnitTree> units=new HashMap<>();
    private final Map<String,MethodTree> methods=new HashMap<>();
    private Trees trees;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        Path root=args.length>0?Path.of(args[0]):Path.of("");
        var audit=new ClientRequestActionSchemaRegressionTest();audit.parse(root);audit.collectConstants();audit.collectRegistrations();
        audit.codecContracts();audit.developerAdapterContract();audit.collectEmissions();audit.actionContracts();audit.valueContracts(root);audit.parserNegativeCases();
        String report=audit.report();System.out.print(report);
        if(args.length>1)Files.writeString(Path.of(args[1]),report);
    }
    private void parse(Path root)throws Exception {
        var compiler=ToolProvider.getSystemJavaCompiler();check(compiler!=null,"JDK compiler required for the actual-source action audit");
        var diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(var manager=compiler.getStandardFileManager(diagnostics,null,null);var paths=Files.walk(root.resolve("src/main/java/cn/academy/port"))) {
            List<Path> selected=paths.filter(p->p.toString().endsWith(".java")&&!p.toString().contains("/gametest/")).sorted().toList();
            var task=(JavacTask)compiler.getTask(new StringWriter(),manager,diagnostics,List.of("-proc:none","--release","21"),null,
                    manager.getJavaFileObjectsFromPaths(selected));trees=Trees.instance(task);
            for(var unit:task.parse()) {
                String name=Path.of(unit.getSourceFile().toUri()).getFileName().toString().replace(".java","");
                check(!units.containsKey(name),"unique production filename required: "+name);
                units.put(name,unit);source.put(name,Files.readString(Path.of(unit.getSourceFile().toUri())));
            }
            check(diagnostics.getDiagnostics().stream().noneMatch(d->d.getKind()==Diagnostic.Kind.ERROR),"actual production sources must parse before auditing actions");
        }
    }
    private void collectConstants() {
        for(var entry:units.entrySet())new TreeScanner<Void,String>() {
            @Override public Void visitClass(ClassTree tree,String ignored){return super.visitClass(tree,tree.getSimpleName().toString());}
            @Override public Void visitVariable(VariableTree tree,String owner){
                if(owner!=null&&tree.getModifiers().getFlags().contains(Modifier.STATIC)&&tree.getModifiers().getFlags().contains(Modifier.FINAL)
                        &&tree.getInitializer()!=null)constants.put(owner+"."+tree.getName(),tree.getInitializer());
                return super.visitVariable(tree,owner);
            }
            @Override public Void visitMethod(MethodTree tree,String owner){if(owner!=null)methods.put(owner+"."+tree.getName(),tree);return super.visitMethod(tree,owner);}
        }.scan(entry.getValue(),entry.getKey());
    }
    private Eval evaluate(ExpressionTree tree,String owner){return evaluate(tree,owner,Map.of(),0);}
    private Eval evaluate(ExpressionTree tree,String owner,Map<String,String> parameters,int depth) {
        if(tree==null||depth>32)return Eval.unknown();
        if(tree instanceof LiteralTree literal&&literal.getValue() instanceof String text)return Eval.one(text);
        if(tree instanceof ParenthesizedTree p)return evaluate(p.getExpression(),owner,parameters,depth+1);
        if(tree instanceof IdentifierTree id&&parameters.containsKey(id.getName().toString()))return Eval.one(parameters.get(id.getName().toString()));
        if(tree instanceof IdentifierTree||tree instanceof MemberSelectTree) {
            String name=tree.toString();if(tree instanceof IdentifierTree)name=owner+"."+name;
            String[] parts=name.split("\\.");if(parts.length>=2)name=parts[parts.length-2]+"."+parts[parts.length-1];
            return constants.containsKey(name)?evaluate(constants.get(name),name.substring(0,name.indexOf('.')),parameters,depth+1):Eval.unknown();
        }
        if(tree instanceof ConditionalExpressionTree conditional) {
            Boolean condition=condition(conditional.getCondition(),owner,parameters,depth+1);
            if(condition!=null)return evaluate(condition?conditional.getTrueExpression():conditional.getFalseExpression(),owner,parameters,depth+1);
            return union(evaluate(conditional.getTrueExpression(),owner,parameters,depth+1),evaluate(conditional.getFalseExpression(),owner,parameters,depth+1));
        }
        if(tree instanceof BinaryTree binary&&tree.getKind()==Tree.Kind.PLUS) {
            Eval left=evaluate(binary.getLeftOperand(),owner,parameters,depth+1),right=evaluate(binary.getRightOperand(),owner,parameters,depth+1);
            if(!left.complete||!right.complete)return Eval.unknown();
            var values=new TreeSet<String>();for(String a:left.values)for(String b:right.values)values.add(a+b);
            return new Eval(values,true);
        }
        if(tree instanceof SwitchExpressionTree expression) {
            Eval out=new Eval(Set.of(),true);
            for(var c:expression.getCases()) {
                if(c.getBody() instanceof ExpressionTree body)out=union(out,evaluate(body,owner,parameters,depth+1));
                else out=new Eval(out.values,false);
            }
            return out;
        }
        return Eval.unknown();
    }
    private Boolean condition(ExpressionTree tree,String owner,Map<String,String> parameters,int depth) {
        if(tree instanceof MethodInvocationTree call&&call.getMethodSelect() instanceof MemberSelectTree selected
                &&selected.getIdentifier().contentEquals("equals")&&call.getArguments().size()==1) {
            Eval a=evaluate(selected.getExpression(),owner,parameters,depth+1),b=evaluate(call.getArguments().getFirst(),owner,parameters,depth+1);
            if(a.complete&&b.complete&&a.values.size()==1&&b.values.size()==1)return a.values.equals(b.values);
        }
        return null;
    }
    private static Eval union(Eval a,Eval b){var values=new TreeSet<>(a.values);values.addAll(b.values);return new Eval(values,a.complete&&b.complete);}
    private String domain(String owner) {
        return switch(owner) {
            case "AcademyGameplay"->"gameplay";case "TerminalNetwork"->"terminal";case "TutorialNetwork"->"tutorial";
            case "MachineDeveloperSessions"->"machine";case "ClassicWirelessProtocol"->"wireless";
            case "ClassicInterfererNetwork"->"interferer";case "TerminalFrequencySessions"->"frequency";
            case "SpatialPincerConsent"->"pincer";case "TacticalCoordinateFormation"->"coordinate";default->"";
        };
    }
    private void collectRegistrations() {
        for(String owner:List.of("AcademyGameplay","TerminalNetwork","TutorialNetwork","MachineDeveloperSessions","ClassicWirelessProtocol",
                "ClassicInterfererNetwork","TerminalFrequencySessions","SpatialPincerConsent","TacticalCoordinateFormation")) {
            Set<String> actions=registered.computeIfAbsent(domain(owner),ignored->new TreeSet<>());
            new TreeScanner<Void,String>() {
                @Override public Void visitMethod(MethodTree method,String ignored){return super.visitMethod(method,method.getName().toString());}
                @Override public Void visitSwitch(SwitchTree tree,String method) {
                    String selector=tree.getExpression().toString().replace("(","").replace(")","");
                    if((!owner.equals("AcademyGameplay")||"requestFromClient".equals(method))&&Set.of("request.action","request.kind").contains(selector))
                        for(var c:tree.getCases())for(var label:c.getExpressions())actions.addAll(evaluate(label,owner).values);
                    return super.visitSwitch(tree,method);
                }
                @Override public Void visitSwitchExpression(SwitchExpressionTree tree,String method) {
                    String selector=tree.getExpression().toString().replace("(","").replace(")","");
                    if(Set.of("request.action","request.kind").contains(selector))for(var c:tree.getCases())for(var label:c.getExpressions())actions.addAll(evaluate(label,owner).values);
                    return super.visitSwitchExpression(tree,method);
                }
                @Override public Void visitMethodInvocation(MethodInvocationTree tree,String method) {
                    String select=tree.getMethodSelect().toString();
                    if(select.matches("request\\.(action\\(\\)|action|kind\\(\\)|kind)\\.equals")&&tree.getArguments().size()==1
                            &&(!owner.equals("AcademyGameplay")||"requestFromClient".equals(method)))actions.addAll(evaluate(tree.getArguments().getFirst(),owner).values);
                    return super.visitMethodInvocation(tree,method);
                }
            }.scan(units.get(owner),"");
            for(String field:List.of("ACTIONS","KINDS"))if(constants.get(owner+"."+field) instanceof MethodInvocationTree call)
                for(var argument:call.getArguments())actions.addAll(evaluate(argument,owner).values);
            check(!actions.isEmpty(),"registered action set found for "+owner);
        }
    }
    private void codecContracts() {
        String codec=source.get("AcademyNetwork").replaceAll("\\s+","");
        check(codec.contains("writeUtf(p.action,32)")&&codec.contains("writeUtf(p.value,64)")
                &&codec.contains("newRequest(buf.readUtf(32),buf.readUtf(64))"),"Academy Request action32/value64 encoder and decoder stay unchanged");
        for(String prefix:List.of("terminal_","tutorial_","machine_"))check(codec.contains("startsWith(\""+prefix+"\")"),"registered common dispatcher preserves "+prefix+" routing");
        for(String owner:List.of("SpatialPincerConsent","TacticalCoordinateFormation")) {
            String body=source.get(owner).replaceAll("\\s+","");
            check(body.contains("writeUtf(request.action,16)")&&body.contains("readUtf(16)"),owner+" separate action16 codec");
        }
        check(source.get("TerminalNetwork").replaceAll("\\s+","").contains("writeUtf(r.kind,32)"),"frequency is a separate kind32/password128 payload");
        check(source.get("ClassicWirelessProtocol").replaceAll("\\s+","").contains("writeUtf(request.action,32)"),"wireless separate action32/value128 payload");
        check(source.get("ClassicInterfererNetwork").replaceAll("\\s+","").contains("writeUtf(r.action,32)"),"interferer separate action32 payload");
    }
    private void developerAdapterContract() {
        String developer=source.get("ClassicDeveloperScreen").replaceAll("\\s+","");
        check(developer.contains("privateDeveloperTypedeveloperType=DeveloperType.PORTABLE;"),"portable developer starts with PORTABLE type");
        check(developer.contains("if(developerType!=DeveloperType.ADVANCED)"),"reset emission requires ADVANCED developer");
        check(developer.contains("machineToken=machine.getString(\"session\")")&&developer.contains("developerType=DeveloperType.valueOf(machine.getString(\"type\"))"),"machine snapshot supplies both session and ADVANCED type");
        check(developer.contains("case\"develop_reset\"->\"machine_reset\""),"internal reset label translates to machine_reset on real machine wire");
        check(source.get("MachineDeveloperSessions").contains("session.token().toString()")&&source.get("MachineDeveloperSessions").contains("machine.developerType().name()"),"real machine snapshot carries nonempty token and type together");
    }
    private void collectEmissions() {
        for(var entry:units.entrySet()) {
            String owner=entry.getKey();var unit=entry.getValue();String filename=unit.getSourceFile().getName();
            if(!filename.contains("/client/")&&!owner.equals("TerminalNetwork"))continue;
            new TreeScanner<Void,Void>() {
                private void add(Tree tree,String channel,ExpressionTree expression) {
                    Eval eval=evaluate(expression,owner);
                    long position=trees.getSourcePositions().getStartPosition(unit,tree);
                    String location=owner+":"+unit.getLineMap().getLineNumber(position);
                    if(!eval.complete)dynamic.add(channel+" "+location+" expression="+expression);
                    for(String action:eval.values) {
                        if(action.isEmpty())continue; // Disabled wireless edit fields are never submitted.
                        if(owner.equals("ClassicDeveloperScreen")&&action.equals("develop_reset"))continue; // Proven ADVANCED-only adapter branch above.
                        emitted.add(new Site(channel,action,location));
                        if(owner.equals("ClassicDeveloperScreen")&&channel.equals("common")) {
                            String translated=switch(action){case "develop_level"->"machine_level";case "learn"->"machine_learn";case "hp_learn"->"machine_hp_learn";default->null;};
                            if(translated!=null)emitted.add(new Site("common",translated,location+" machine adapter"));
                        }
                    }
                }
                @Override public Void visitMethodInvocation(MethodInvocationTree tree,Void ignored) {
                    String select=tree.getMethodSelect().toString();var args=tree.getArguments();
                    if(!args.isEmpty()&&(select.endsWith("AcademyClient.request")||select.equals("request")&&Set.of("AcademyClient","ClassicDeveloperScreen").contains(owner)
                            ||select.equals("sendPersistentCancellation")&&owner.equals("AcademyClient")||select.equals("send")&&owner.equals("ClassicLocationTeleportScreen")))add(tree,"common",args.getFirst());
                    if(select.equals("request")&&Set.of("ClassicWirelessScreen","ClassicEnergyBridgeScreen").contains(owner)&&!args.isEmpty())add(tree,"wireless",args.getFirst());
                    if(select.equals("field")&&owner.equals("ClassicWirelessScreen")&&args.size()>5)add(tree,"wireless",args.get(5));
                    if(select.equals("request")&&owner.equals("ClassicInterfererScreen")&&!args.isEmpty())add(tree,"interferer",args.getFirst());
                    if(select.equals("request")&&owner.equals("FrequencyTransmitter")&&!args.isEmpty())add(tree,"frequency",args.getFirst());
                    if(Set.of("TacticalCoordinateClient","SpatialPincerClient").contains(owner)&&select.equals("send")&&args.size()>1)add(tree,owner.equals("SpatialPincerClient")?"pincer":"coordinate",args.get(1));
                    if(Set.of("TacticalCoordinateScreen","SpatialPincerScreen").contains(owner)&&select.equals("action")&&!args.isEmpty())add(tree,owner.equals("SpatialPincerScreen")?"pincer":"coordinate",args.getFirst());
                    return super.visitMethodInvocation(tree,ignored);
                }
                @Override public Void visitNewClass(NewClassTree tree,Void ignored) {
                    String type=tree.getIdentifier().toString();var args=tree.getArguments();
                    if(type.endsWith("AcademyNetwork.Request")&&!args.isEmpty())add(tree,"common",args.getFirst());
                    if(type.endsWith("ClassicWirelessProtocol.Request")&&args.size()>2)add(tree,"wireless",args.get(2));
                    if(type.endsWith("ClassicInterfererNetwork.Request")&&args.size()>3)add(tree,"interferer",args.get(3));
                    if(type.endsWith("TacticalCoordinateFormation.Request")&&args.size()>4)add(tree,"coordinate",args.get(4));
                    if(type.endsWith("SpatialPincerConsent.Request")&&args.size()>4)add(tree,"pincer",args.get(4));
                    return super.visitNewClass(tree,ignored);
                }
            }.scan(unit,null);
        }
    }
    private void actionContracts() {
        for(var entry:registered.entrySet())for(String action:entry.getValue())check(action.length()<=limit(entry.getKey()),"registered action exceeds codec: "+entry.getKey()+" "+action);
        check(emitted.size()>100,"actual client action emissions scanned, not an empty manifest");
        var errors=new ArrayList<String>();
        for(var site:emitted) {
            if(site.action.length()>limit(site.domain))errors.add("oversize "+site);
            String destination=site.domain;
            if(destination.equals("common"))destination=site.action.startsWith("terminal_")?"terminal":site.action.startsWith("tutorial_")?"tutorial":site.action.startsWith("machine_")?"machine":"gameplay";
            if(!registered.get(destination).contains(site.action))errors.add("no registered handler "+site);
            checks++;
        }
        check(errors.isEmpty(),"actual static client action schema mismatch: "+errors);
        Set<String> common=actions("common");
        check(common.containsAll(Set.of("hp_mla_start","hp_mla_release","hp_mla_abort")),"all three actual NEW351 action emitters are present");
        check(common.stream().noneMatch(s->s.startsWith("hp_multidimensional_array_")),"old NEW351 long IDs cannot survive in client emitters");
    }
    private static int limit(String domain){return Set.of("pincer","coordinate").contains(domain)?16:ACTION_LIMIT;}
    private Set<String> actions(String channel){var actions=new TreeSet<String>();for(var site:emitted)if(site.domain.equals(channel))actions.add(site.action);return actions;}
    private void valueContracts(Path root)throws Exception {
        long largest=Long.MAX_VALUE;int actor=Integer.MAX_VALUE;
        var high=new HighPowerInput.Input(3,largest,actor,largest);
        check(high.wire().length()==52&&high.wire().length()<=VALUE_LIMIT&&high.equals(HighPowerInput.parse(high.wire())),"actual largest high-power native identity fits64 and round-trips");
        for(var skill:LegacySingleKeyProtocol.Skill.values()) {
            var start=new LegacySingleKeyProtocol.StartRequest(3,skill,largest,actor,largest);
            var pending=new LegacySingleKeyProtocol.PendingRequest(skill,largest,actor,largest);
            var accepted=new LegacySingleKeyProtocol.AcceptedRequest(skill,largest,actor,largest,largest);
            check(start.wire().length()<=VALUE_LIMIT&&start.equals(LegacySingleKeyProtocol.parseStart(start.wire())),"legacy start maximum "+skill);
            check(pending.wire().length()<=VALUE_LIMIT&&pending.equals(LegacySingleKeyProtocol.parsePending(pending.wire())),"legacy pending maximum "+skill);
            check(accepted.wire().length()<=VALUE_LIMIT&&accepted.equals(LegacySingleKeyProtocol.parseAccepted(accepted.wire())),"legacy accepted maximum "+skill);
        }
        for(var skill:TargetedContextTermination.Skill.values()) {
            var context=new TargetedContextTermination.Request(skill,largest,largest);
            var pending=new TargetedContextTermination.PendingRequest(skill,largest);
            check(context.wire().length()<=VALUE_LIMIT&&context.equals(TargetedContextTermination.parse(context.wire())),"accepted classic context maximum "+skill);
            check(pending.wire().length()<=VALUE_LIMIT&&pending.equals(TargetedContextTermination.parsePending(pending.wire())),"pending classic context maximum "+skill);
        }
        var flash=new TargetedContextTermination.FlashDirection(largest,largest,4096,4);
        check(flash.wire().length()<=VALUE_LIMIT&&flash.equals(TargetedContextTermination.parseFlashDirection(flash.wire())),"bounded Flashing direction maximum fits64");
        check(TargetedContextTermination.parseFlashDirection(largest+":"+largest+":4097:4")==null,"Flashing4097 remains a known parser boundary, not arbitrary schema certification");
        String learning=source.get("HighPowerLearningProtocol").replaceAll("\\s+","");
        check(learning.contains("wireSkill(skill)+\":~\"+Integer.toString(sequence,36)"),"actual high-power learning sequence remains compact base36");
        // Execute the real parser: canon aliases have guarded branches and collision checks,
        // so a single-return-expression AST evaluator cannot establish this contract.
        var ids=cn.academy.port.highpower.HighPowerSkills.IDS;
        check(ids.size()==61+cn.academy.port.canon.CanonSkills.IDS.size(),"complete legacy and canon learning inventory");
        var aliases=new java.util.HashSet<String>();
        for(String id:ids) {
            var request=new cn.academy.port.highpower.HighPowerLearningProtocol.Request(id,Integer.MAX_VALUE);
            String wire=request.wire();
            check(36+1+wire.length()<=VALUE_LIMIT,"UUID+actual learning alias+maximum sequence fits64: "+id);
            check(request.equals(cn.academy.port.highpower.HighPowerLearningProtocol.parse(wire)),"actual learning alias restores canonical skill "+id);
            check(aliases.add(wire),"learning aliases cannot collide: "+id);
            check(cn.academy.port.highpower.HighPowerLearningProtocol.parse(wire+"0")==null,"overflowed learning sequence refused: "+id);
        }
        String classic=Files.readString(root.resolve("src/main/resources/classic-skills.json"));
        var matcher=Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"").matcher(classic);var classicIds=new TreeSet<String>();while(matcher.find())classicIds.add(matcher.group(1));
        check(Pattern.compile("\"category\"\\s*:").matcher(classic).results().count()==50&&classicIds.size()==41,
                "actual canonical50 records/41 distinct skill IDs found for machine classic learning bound");
        for(String id:classicIds)check(36+1+id.length()<=VALUE_LIMIT,"UUID+canonical classic skill fits64: "+id);
        check(19+1+16<=VALUE_LIMIT&&source.get("ClassicLocationTeleportScreen").contains("setMaxLength(16)"),"location add nonce plus native16-character name fits64");
        check(source.get("ClassicWirelessScreen").contains("box.setMaxLength(128)"),"wireless text fields obey their separate128-character codec");
        var apps=(MethodInvocationTree)constants.get("TerminalState.APPS");
        for(var argument:apps.getArguments())for(String app:evaluate(argument,"TerminalState").values)
            check(app.length()<=VALUE_LIMIT,"actual terminal app value fits common64 codec: "+app);
        check(source.get("TerminalState").contains("APPS.contains(app)")&&source.get("TerminalClient").contains("state.isInstalled(app)"),
                "genuine terminal app requests are gated by the actual finite installed-app whitelist");
        check(source.get("TerminalNetwork").contains("!request.value().isEmpty()")&&source.get("TutorialNetwork").contains("!request.value().isEmpty()"),
                "terminal open/close and tutorial open accept empty values only");
    }
    private void parserNegativeCases()throws Exception {
        String synthetic="class AcademyClient { static final String PREFIX=\"hp_\"; void f(){request(\""+"a".repeat(33)+"\",\"\");request(PREFIX+\"mla_release\",\"\");request(flag?\"hp_mla_start\":\"hp_mla_abort\",\"\");request(dynamic,\"\");} }";
        var compiler=ToolProvider.getSystemJavaCompiler();
        JavaFileObject file=new SimpleJavaFileObject(java.net.URI.create("string:///AcademyClient.java"),JavaFileObject.Kind.SOURCE){@Override public CharSequence getCharContent(boolean ignore){return synthetic;}};
        var task=(JavacTask)compiler.getTask(new StringWriter(),null,null,List.of("-proc:none"),null,List.of(file));
        var expressions=new ArrayList<ExpressionTree>();for(var unit:task.parse())new TreeScanner<Void,Void>(){
            @Override public Void visitVariable(VariableTree variable,Void ignored){if(variable.getName().contentEquals("PREFIX"))constants.put("AcademyClient.PREFIX",variable.getInitializer());return super.visitVariable(variable,ignored);}
            @Override public Void visitMethodInvocation(MethodInvocationTree call,Void ignored){if(call.getMethodSelect().toString().equals("request"))expressions.add(call.getArguments().getFirst());return super.visitMethodInvocation(call,ignored);}
        }.scan(unit,null);
        check(evaluate(expressions.get(0),"AcademyClient").values.iterator().next().length()>ACTION_LIMIT,"source parser catches original33-character regression shape");
        check(evaluate(expressions.get(1),"AcademyClient").values.equals(Set.of("hp_mla_release")),"constant-prefix action concatenation is evaluated before checking bounds");
        check(evaluate(expressions.get(2),"AcademyClient").values.equals(Set.of("hp_mla_start","hp_mla_abort")),"conditional action branches are both audited");
        check(!evaluate(expressions.get(3),"AcademyClient").complete,"arbitrary runtime action is explicitly unproved");
        check("hp_multidimensional_array_release".length()==33&&"hp_mla_release".length()==14,"old actual release spelling is33 and replacement fits32");
    }
    private String report() {
        var out=new StringBuilder("ClientRequestActionSchemaRegressionTest: "+checks+" source/pure checks passed\n");
        out.append("Scope: finite literals/constants/conditional branches and known adapters in actual production client request emitters; no native codec or arbitrary-runtime-string claim.\n");
        for(var entry:registered.entrySet())out.append("Registered ").append(entry.getKey()).append(": ").append(entry.getValue().size()).append(" IDs, maximum ")
                .append(entry.getValue().stream().mapToInt(String::length).max().orElse(0)).append("/ ").append(limit(entry.getKey())).append(" characters\n");
        for(String channel:List.of("common","wireless","interferer","frequency","pincer","coordinate"))out.append("Static emitted ").append(channel).append(": ").append(actions(channel).size()).append(" distinct IDs ").append(actions(channel)).append('\n');
        out.append("Static emitted sites: ").append(emitted.size()).append("; unresolved forwarding/dynamic expressions: ").append(dynamic.size()).append('\n');
        for(String expression:dynamic)out.append("Unproved runtime action: ").append(expression).append('\n');
        out.append("Values checked separately: native input52 max; six legacy aliases; targeted contexts; bounded Flashing4096; all registered high-power machine-learning aliases; canonical50 classic records/41 distinct machine-learning IDs; five actual terminal apps plus empty terminal/tutorial opens; location-name16; wireless128 fields. Academy Request value64 unchanged.\n");
        out.append("Known separate boundary: Flashing client increments hopSequence without a4096 check; registered parser rejects4097. No gameplay changes or arbitrary extension-field certification.\n");
        return out.toString();
    }
}
