package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.DeveloperType;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Actual catalog counterexamples and compiled GUI interaction wiring; native UI pixels are a separate test. */
public final class DeveloperLearningReadinessRegressionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static MethodNode method(ClassNode type,String name){return type.methods.stream().filter(m->m.name.equals(name)).findFirst().orElseThrow();}
    private static List<MethodInsnNode> calls(MethodNode m){return java.util.stream.StreamSupport.stream(m.instructions.spliterator(),false).filter(i->i instanceof MethodInsnNode).map(i->(MethodInsnNode)i).toList();}
    private static AbstractInsnNode next(AbstractInsnNode n){do{n=n.getNext();}while(n!=null&&n.getOpcode()<0);return n;}
    private static boolean hasCall(MethodNode m,String name){return calls(m).stream().anyMatch(c->c.name.equals(name));}
    public static void main(String[] args)throws Exception{
        var type=new ClassNode();new ClassReader("cn.academy.port.client.ClassicDeveloperScreen").accept(type,0);
        var readiness=method(type,"canStartSkill");
        for(String name:List.of("canConfirmSkill","developing","energy","estimated","find","supportsSkill","canLearn"))
            check(hasCall(readiness,name),"actual readiness reads the existing legal submission condition: "+name);
        check(calls(readiness).stream().filter(c->c.owner.equals("java/lang/Double")&&c.name.equals("isFinite")).count()==2,"both available IF and quote must be finite");
        for(var instruction:readiness.instructions){
            check(instruction.getOpcode()!=Opcodes.PUTFIELD&&instruction.getOpcode()!=Opcodes.PUTSTATIC,"readiness never mutates state, wallets or UI progress");
            if(instruction instanceof MethodInsnNode call)check(!List.of("request","beginLearning","startCover","learn","setLevel","putDouble").contains(call.name),"readiness sends no request and performs no learning");
        }
        MethodNode draw=method(type,"drawSkillDetails"),mouse=method(type,"mouseClicked"),start=method(type,"startCover");
        check(hasCall(draw,"canConfirmSkill"),"original visible/read-only fence still controls whether confirm exists");
        check(calls(draw).stream().filter(c->c.name.equals("canStartSkill")).count()==1,"draw computes enabled state from shared actual readiness");
        var drawGate=calls(draw).stream().filter(c->c.name.equals("canStartSkill")).findFirst().orElseThrow();
        check(next(drawGate).getOpcode()==Opcodes.DLOAD,"computed readiness is passed directly to detailButton before mouse coordinates");
        var mouseGate=calls(mouse).stream().filter(c->c.name.equals("canConfirmSkill")).findFirst().orElseThrow();
        check(next(mouseGate) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IFEQ,"original read-only/visible fence remains on actual mouse ingress");
        var skip=((JumpInsnNode)next(mouseGate)).label;boolean submitted=false;
        for(AbstractInsnNode i=mouseGate.getNext();i!=null&&i!=skip;i=i.getNext())if(i instanceof MethodInsnNode c&&c.name.equals("startCover"))submitted=true;
        check(submitted,"mouse confirmation reaches shared-readiness submission rather than swallowing eligible learning");
        var startGate=calls(start).stream().filter(c->c.name.equals("canStartSkill")).findFirst().orElseThrow();
        check(next(startGate) instanceof JumpInsnNode jump&&jump.getOpcode()==Opcodes.IFNE&&next(next(startGate)).getOpcode()==Opcodes.RETURN,"direct skill submit also returns on false readiness");
        var disposed=java.util.stream.StreamSupport.stream(start.instructions.spliterator(),false).filter(i->i instanceof FieldInsnNode f&&f.name.equals("buttonDisposed")&&i.getOpcode()==Opcodes.PUTFIELD).findFirst().orElseThrow();
        check(start.instructions.indexOf(startGate)<start.instructions.indexOf(disposed),"disabled click cannot consume or dispose a future valid confirmation");
        check(hasCall(start,"request"),"eligible native learning still uses the existing server-owned request path");

        // The actual photographed counterexample: all ability prerequisites met but PORTABLE cannot learn LV4.
        var pincer=SkillCatalog.find("teleporter","spatial_pincer_corridor").orElseThrow();
        var state=new AbilityProgress();state.selectCategory("teleporter");state.setLevel(5);
        for(var requirement:SkillCatalog.requirements(pincer)){state.learn(requirement.id());state.experience.put(requirement.id(),1.0);}
        check(SkillCatalog.canLearn(state,pincer),"actual pincer catalog conditions can be satisfied while using the wrong machine");
        check(!DeveloperType.PORTABLE.supportsSkill(pincer.level())&&DeveloperType.ADVANCED.supportsSkill(pincer.level()),"red advanced-machine requirement must independently disable the photographed portable button");
        // Preserve the observed positive case: real Forked Arc learning quotes 2340 finite IF at portable tier.
        var fork=SkillCatalog.find("electromaster","forked_arc").orElseThrow();state.selectCategory("electromaster");state.setLevel(5);
        for(var requirement:SkillCatalog.requirements(fork)){state.learn(requirement.id());state.experience.put(requirement.id(),1.0);}
        check(DeveloperType.PORTABLE.supportsSkill(fork.level())&&SkillCatalog.canLearn(state,fork),"actual catalog retains eligible portable Forked Arc learning");
        double quote=DeveloperType.PORTABLE.actualConsumption(ClassicRules.learningStimulations(fork.level()));
        check(quote==2340&&quote<=10000,"native positive fixture has enough IF for its original 2340 quote");
        check(DeveloperSkillDetailsLayout.canConfirm(false,false,false,false,false,true)&&!DeveloperSkillDetailsLayout.canConfirm(true,false,false,false,false,true),"view-only tree still has no confirmation path");
        System.out.println("DeveloperLearningReadinessRegressionTest: "+checks+" checks passed");
    }
}
