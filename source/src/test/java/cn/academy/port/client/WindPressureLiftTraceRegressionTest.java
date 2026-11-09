package cn.academy.port.client;

/** Run in separate JVMs with the QA property absent and present; diagnostics never touch engine state. */
public final class WindPressureLiftTraceRegressionTest {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private static int count(String name)throws Exception {
        var field=WindPressureLiftTrace.class.getDeclaredField(name);field.setAccessible(true);return field.getInt(null);
    }
    public static void main(String[] args)throws Exception {
        boolean requested=Boolean.getBoolean("academy.qa.windLiftTrace");
        check(WindPressureLiftTrace.ENABLED==requested,"only explicit QA property enables tracing");
        WindPressureLiftTrace.record(0,0,"clear","before any start");
        WindPressureLiftTrace.record(1,1,"receive","uncaptured input");
        check(count("used")==0 && count("total")==0,"initial clear and uncaptured identity emit no rows");
        WindPressureLiftTrace.start(1,1);
        for(int i=0;i<300;i++)WindPressureLiftTrace.record(1,1,"budget_test","sample");
        check(count("used")== (requested?240:0),"per-activation hard limit or default-off silence");
        check(count("total")== (requested?240:0),"global count matches only emitted rows");
        WindPressureLiftTrace.start(1,1);WindPressureLiftTrace.record(1,1,"budget_test","same input");
        check(count("total")== (requested?240:0),"same activation cannot reset budget");
        for(int cast=2;cast<=8;cast++) {
            WindPressureLiftTrace.start(cast,1);
            for(int i=0;i<300;i++)WindPressureLiftTrace.record(cast,1,"budget_test","sample");
        }
        check(count("total")== (requested?1024:0),"global hard limit survives repeated activations");
        WindPressureLiftTrace.record(999,999,"budget_test","foreign identity");
        check(count("total")== (requested?1024:0),"foreign identity cannot add output");
        System.out.println("PASS WindPressureLiftTraceRegressionTest: "+checks+" checks enabled="+requested);
    }
}
