/* AcademyCraft 1.0.7 TutorialRegistry.groupByLearned ordering adaptation. GPLv3. */
package cn.academy.port.client.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class TutorialNavigation {
    /** Source PreviewInfo stores a view index separately for every ViewGroup in the current TutInfo. */
    static final class Views {
        private int group;
        private int[] indices=new int[0],counts=new int[0];
        void update(int... sizes){
            indices=java.util.Arrays.copyOf(indices,sizes.length);counts=sizes.clone();group=Math.min(group,Math.max(0,sizes.length-1));
            for(int i=0;i<sizes.length;i++)indices[i]=sizes[i]==0?0:Math.floorMod(indices[i],sizes[i]);
        }
        void reset(int... sizes){indices=new int[0];group=0;update(sizes);}
        void select(int index){if(index<0||index>=counts.length)throw new IllegalArgumentException("Unknown preview group");group=index;}
        void shift(int direction){if(counts.length>0&&counts[group]>0)indices[group]=Math.floorMod(indices[group]+direction,counts[group]);}
        int group(){return group;}
        int view(){return indices.length==0?0:indices[group];}
    }
    static <T> List<T> researchOrder(List<T> source,Predicate<T> learned){
        var result=new ArrayList<T>();for(T page:source)if(learned.test(page))result.add(page);for(T page:source)if(!learned.test(page))result.add(page);return List.copyOf(result);
    }
    private TutorialNavigation(){}
}
