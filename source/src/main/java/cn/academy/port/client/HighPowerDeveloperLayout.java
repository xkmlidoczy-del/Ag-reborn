package cn.academy.port.client;

import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerSkills;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Extension catalog and learning/navigation state. Legacy page geometry is retained for historical proofs;
 * the live screen selects and renders UnifiedSkillTreeLayout instead. Source node identities and assets are untouched. */
public final class HighPowerDeveloperLayout {
    private HighPowerDeveloperLayout() {}
    public enum Page { CLASSIC, HIGH_POWER }
    public enum Back { SELECTION, CLASSIC_PAGE, CLOSE_SCREEN, BUSY }
    public enum DevelopmentUpdate { IGNORED, PROGRESS, DONE, FAILED, DISPLACED }
    /** Native completion clears its action before publishing DONE/FAILED. A matching start,
     * after request acceptance, owns that blank terminal until another action replaces it. */
    public static final class DevelopmentFeedback {
        private String expected="";
        private boolean started;
        public void begin(String action){if(action==null||action.isEmpty())throw new IllegalArgumentException("Expected action required");expected=action;started=false;}
        public void cancel(){expected="";started=false;}
        public DevelopmentUpdate receive(String status,String action,boolean accepted){
            if(expected.isEmpty()||!accepted||status==null||action==null)return DevelopmentUpdate.IGNORED;
            if(status.equals("DEVELOPING")){
                if(expected.equals(action)){started=true;return DevelopmentUpdate.PROGRESS;}
                if(started){cancel();return DevelopmentUpdate.DISPLACED;}
                return DevelopmentUpdate.IGNORED;
            }
            if(status.equals("DONE")||status.equals("FAILED")){
                if(!started)return DevelopmentUpdate.IGNORED;
                if(!action.isEmpty()&&!expected.equals(action)){cancel();return DevelopmentUpdate.DISPLACED;}
                cancel();return status.equals("DONE")?DevelopmentUpdate.DONE:DevelopmentUpdate.FAILED;
            }
            if(status.equals("IDLE")&&started){cancel();return DevelopmentUpdate.DISPLACED;}
            return DevelopmentUpdate.IGNORED;
        }
    }
    public static boolean paged(String category) {
        return cn.academy.port.highpower.HighPowerCategories.MELTDOWNER.equals(category)
                || cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(category)
                || cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(category);
    }
    public static String highPowerTitle(String category) {
        if (cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(category))
            return "screen.academy.developer.page_vector_highpower";
        return cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(category)
                ? "screen.academy.developer.page_tp_highpower" : "screen.academy.developer.page_md_highpower";
    }
    /** Display-only pages: IDs, prerequisites, conditions, binding and save schema stay canonical. */
    public static List<ClassicDeveloperLayout.Node> page(String category,Page page) {
        if(!paged(category))return ClassicDeveloperLayout.category(category);
        if(page==Page.CLASSIC)return ClassicDeveloperLayout.category(category).stream()
                .filter(node->!HighPowerSkills.contains(node.id())).toList();
        if(cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(category)
                || cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(category))return category(category);
        return category(category).stream().map(node->{
            double x,y;
            switch(node.id()) {
                case HighPowerSkills.DUAL_POINT -> {x=26;y=61;}
                case HighPowerSkills.SCATTER_PLATES -> {x=110;y=28;}
                case HighPowerSkills.BEAM_ETCHING -> {x=110;y=98;}
                case HighPowerSkills.POINT_MELTDOWN -> {x=20;y=105;}
                case HighPowerSkills.THERMAL_SHELL -> {x=206;y=29;}
                case HighPowerSkills.FOLDED_MIRROR_ARRAY -> {x=206;y=97;}
                case HighPowerSkills.DISINTEGRATION_SWEEP -> {x=160;y=105;}
                case HighPowerSkills.BEAM_STEP -> {x=54;y=20;}
                case HighPowerSkills.PARTICLE_LAMINATE -> {x=225;y=63;}
                case HighPowerSkills.DISSIPATION_CURTAIN -> {x=160;y=20;}
                case HighPowerSkills.MULTICORE_CANOPY -> {x=62;y=106;}
                case HighPowerSkills.ZERO_GAP_ARMOR -> {x=161;y=61;}
                case HighPowerSkills.SCATTER_LOCATOR_PLATE -> {x=16;y=17;}
                case HighPowerSkills.REMOTE_SCATTER_RELAY -> {x=75;y=61;}
                case HighPowerSkills.REVERSE_JET -> {x=123;y=63;}
                default -> throw new IllegalStateException("Unplaced MD high-power skill "+node.id());
            }
            return new ClassicDeveloperLayout.Node(node.category(),node.id(),node.index(),node.level(),x,y,
                    node.parent(),node.enabled(),node.hintIcon(),node.nameKey(),node.descriptionKey(),node.conditions());
        }).toList();
    }
    /** Pure navigation state used by the actual screen and tests. No ability/save state is written. */
    public static final class Navigation {
        private String category="",selected="";
        private Page page=Page.CLASSIC;
        private boolean pending;
        public boolean synchronize(String category) {
            String next=category==null?"":category;
            if(this.category.equals(next))return false;
            this.category=next;page=Page.CLASSIC;clearSelection();return true;
        }
        public Page page(){return page;}
        public String selected(){return selected;}
        public boolean pending(){return pending;}
        public boolean switchTo(Page next) {
            if(next==null||next==page||!paged(category)||pending||!selected.isEmpty())return false;
            page=next;return true;
        }
        public boolean select(String id) {
            if(pending||!selected.isEmpty()||id==null||HighPowerDeveloperLayout.page(category,page).stream()
                    .noneMatch(node->node.enabled()&&node.id().equals(id)))return false;
            selected=id;return true;
        }
        /** Selection against the merged single-page tree the screen shows ({@link UnifiedSkillTreeLayout}). */
        public boolean selectIn(String id,List<ClassicDeveloperLayout.Node> nodes) {
            if(pending||!selected.isEmpty()||id==null||nodes==null)return false;
            var canonical=UnifiedSkillTreeLayout.category(category).stream().filter(node->node.enabled()&&node.id().equals(id)).findFirst();
            if(canonical.isEmpty()||nodes.stream().noneMatch(node->node.equals(canonical.get())))return false;
            // A merged-tree selection never carries a legacy page into the Back path.
            page=Page.CLASSIC;selected=id;return true;
        }
        public boolean beginLearning(){if(selected.isEmpty()||pending)return false;pending=true;return true;}
        public void finishLearning(){pending=false;}
        public void clearSelection(){selected="";pending=false;}
        public Back back() {
            if(pending)return Back.BUSY;
            if(!selected.isEmpty()){clearSelection();return Back.SELECTION;}
            if(page==Page.HIGH_POWER){page=Page.CLASSIC;return Back.CLASSIC_PAGE;}
            return Back.CLOSE_SCREEN;
        }
    }
    /** NEW213 alone uses the retained tree gaps; a 2px link clears every unrelated full-hover circle. */
    public static List<ClassicDeveloperTimeline.Edge> beamEtchingDependency(double reveal) {
        return dependency(new double[][]{{168,118},{134,92},{106,58.5},{103.5,52},{100.1,18.6}},reveal,1);
    }
    /** NEW253 occupies the direct NEW212 display branch; route only the high-power page around its hover circle. */
    public static List<ClassicDeveloperTimeline.Edge> scatterPlatesPageDependency(double reveal) {
        return meltdownerDependency(HighPowerSkills.SCATTER_PLATES,reveal);
    }
    /** Dedicated high-power-page branch; the original NEW213 source-coordinate route above remains unchanged. */
    public static List<ClassicDeveloperTimeline.Edge> beamEtchingPageDependency(double reveal) {
        return meltdownerDependency(HighPowerSkills.BEAM_ETCHING,reveal);
    }
    /** NEW252 and NEW253 fill the old corridor; this display-only detour preserves all node positions. */
    public static List<ClassicDeveloperTimeline.Edge> foldedMirrorDependency(double reveal) {
        return meltdownerDependency(HighPowerSkills.FOLDED_MIRROR_ARRAY,reveal);
    }
    /** NEW223 uses existing row gaps then the left margin; old nodes and links retain their coordinates. */
    public static List<ClassicDeveloperTimeline.Edge> scatterLocatorDependency(double reveal) {
        return meltdownerDependency(HighPowerSkills.SCATTER_LOCATOR_PLATE,reveal);
    }
    /** NEW253 joins NEW221 through the retained inter-row channel. */
    public static List<ClassicDeveloperTimeline.Edge> remoteScatterRelayDependency(double reveal) {
        return meltdownerDependency(HighPowerSkills.REMOTE_SCATTER_RELAY,reveal);
    }
    /** NEW233 fits the lower-left gap; its link stays between the retained Dual Point and Canopy hover rings. */
    public static List<ClassicDeveloperTimeline.Edge> pointMeltdownDependency(double reveal) {
        return meltdownerDependency(HighPowerSkills.POINT_MELTDOWN,reveal);
    }
    /** NEW232 skirts the top edge so its prerequisite line crosses no existing skill icon. */
    public static List<ClassicDeveloperTimeline.Edge> beamStepDependency(double reveal) {
        return meltdownerDependency(HighPowerSkills.BEAM_STEP,reveal);
    }
    /** NEW252 fills the former shell-link corridor; this detour preserves every node position. */
    public static List<ClassicDeveloperTimeline.Edge> thermalShellDependency(double reveal) {
        return meltdownerDependency(HighPowerSkills.THERMAL_SHELL,reveal);
    }
    /** NEW251 follows the outside edge so the existing node hover regions stay clear. */
    public static List<ClassicDeveloperTimeline.Edge> multicoreCanopyDependency(double reveal) {
        return meltdownerDependency(HighPowerSkills.MULTICORE_CANOPY,reveal);
    }
    /** NEW241 skirts the lower/right margins without crossing accepted node hover rings. */
    public static List<ClassicDeveloperTimeline.Edge> particleLaminateDependency(double reveal) {
        return meltdownerDependency(HighPowerSkills.PARTICLE_LAMINATE,reveal);
    }
    /** NEW351 occupies the former diagonal approach; NEW321 now approaches vertically through its row gap. */
    public static List<ClassicDeveloperTimeline.Edge> crossSubstitutionDependency(double reveal) {
        return teleporterDependency(HighPowerSkills.CROSS_SUBSTITUTION,reveal);
    }
    /** NEW353 and NEW351 require detours; retain every node and both original inset endpoints. */
    public static List<ClassicDeveloperTimeline.Edge> cornerExchangeDependency(double reveal) {
        return teleporterDependency(HighPowerSkills.CORNER_EXCHANGE,reveal);
    }
    /** NEW331 follows the upper row gap from learned NEW321 without moving any retained TP node. */
    public static List<ClassicDeveloperTimeline.Edge> shortInterceptionDependency(double reveal) {
        return teleporterDependency(HighPowerSkills.SHORT_INTERCEPTION,reveal);
    }
    /** NEW343 skirts the left edge around learned NEW311 without moving any retained TP node. */
    public static List<ClassicDeveloperTimeline.Edge> spatialRelayDependency(double reveal) {
        return teleporterDependency(HighPowerSkills.SPATIAL_RELAY,reveal);
    }
    /** NEW342 branches through the left column gap without moving any of the nine retained TP nodes. */
    public static List<ClassicDeveloperTimeline.Edge> emergencyReturnDependency(double reveal) {
        return teleporterDependency(HighPowerSkills.EMERGENCY_RETURN,reveal);
    }
    /** NEW353 follows the inter-row gap without moving retained nodes. */
    public static List<ClassicDeveloperTimeline.Edge> tacticalCoordinateDependency(double reveal) {
        return teleporterDependency(HighPowerSkills.TACTICAL_COORDINATE_FORMATION,reveal);
    }
    /** NEW341 reaches the retained lower-row gap without moving an existing node. */
    public static List<ClassicDeveloperTimeline.Edge> spatialPincerDependency(double reveal) {
        return teleporterDependency(HighPowerSkills.SPATIAL_PINCER_CORRIDOR,reveal);
    }
    /** NEW351 uses the empty lower-row cell; its dependency uses the established inter-row channel. */
    public static List<ClassicDeveloperTimeline.Edge> multidimensionalLandingArrayDependency(double reveal) {
        return teleporterDependency(HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY,reveal);
    }
    /** NEW433 follows the top margin, preserving every earlier node and hover footprint. */
    public static List<ClassicDeveloperTimeline.Edge> circulationListeningDependency(double reveal) {
        return dependency(new double[][]{{206,25},{206,3},{46,3},{46,25}},reveal,ClassicDeveloperTimeline.LINK_WIDTH/2);
    }
    /** NEW443 routes through the existing inter-row gap without moving old nodes. */
    public static List<ClassicDeveloperTimeline.Edge> windPressureLiftDependency(double reveal) {
        return dependency(new double[][]{{86,69},{86,91},{166,91},{166,113}},reveal,ClassicDeveloperTimeline.LINK_WIDTH/2);
    }
    // Reviewed v2 paths, precomputed around full hover circles. No path search runs on the render thread.
    private static final java.util.Map<String,double[][]> MD_ROUTES=java.util.Map.ofEntries(
            java.util.Map.entry("beam_etching",new double[][]{{34.0,69.0},{77.35,93.81},{118.0,106.0}}),
            java.util.Map.entry("beam_step",new double[][]{{214.0,37.0},{254.25,2.75},{168.0,6.52},{42.6,14.26},{62.0,28.0}}),
            java.util.Map.entry("directional_dissipation_curtain",new double[][]{{118.0,36.0},{168.0,28.0}}),
            java.util.Map.entry("disintegration_sweep",new double[][]{{214.0,105.0},{168.0,113.0}}),
            java.util.Map.entry("folded_mirror_array",new double[][]{{34.0,69.0},{73.73,92.84},{79.27,90.16},{97.81,113.35},{86.46,127.81},{118.0,127.48},{151.54,126.81},{139.16,109.73},{154.19,96.54},{157.26,94.4},{165.27,90.16},{168.0,91.52},{169.0,90.48},{214.0,105.0}}),
            java.util.Map.entry("multicore_canopy",new double[][]{{233.0,71.0},{176.35,89.19},{168.0,91.52},{165.27,90.16},{157.26,94.4},{154.19,96.54},{139.16,109.73},{151.54,126.81},{118.0,127.48},{46.6,123.74},{70.0,114.0}}),
            java.util.Map.entry("particle_laminate",new double[][]{{168.0,113.0},{200.19,88.54},{203.26,86.4},{233.0,71.0}}),
            java.util.Map.entry("point_meltdown",new double[][]{{118.0,106.0},{75.65,89.19},{72.26,87.6},{28.0,113.0}}),
            java.util.Map.entry("remote_scatter_relay",new double[][]{{214.0,105.0},{169.0,90.48},{168.0,91.52},{165.27,90.16},{157.26,94.4},{154.19,96.54},{139.16,109.73},{151.54,126.81},{118.0,127.48},{86.46,127.81},{97.81,113.35},{83.0,69.0}}),
            java.util.Map.entry("reverse_jet_brake",new double[][]{{62.0,28.0},{131.0,71.0}}),
            java.util.Map.entry("scatter_locator_plate",new double[][]{{214.0,105.0},{169.0,90.48},{168.0,91.52},{165.27,90.16},{157.26,94.4},{154.19,96.54},{139.16,109.73},{151.54,126.81},{118.0,127.48},{86.46,127.81},{97.81,113.35},{79.27,90.16},{70.0,92.52},{45.54,41.81},{24.0,25.0}}),
            java.util.Map.entry("scatter_plates",new double[][]{{34.0,69.0},{69.35,48.19},{118.0,36.0}}),
            java.util.Map.entry("thermal_shell",new double[][]{{118.0,36.0},{78.46,14.19},{168.0,6.52},{254.25,2.75},{214.0,37.0}}),
            java.util.Map.entry("zero_gap_armor",new double[][]{{168.0,28.0},{169.0,69.0}})
    );
    public static List<ClassicDeveloperTimeline.Edge> meltdownerDependency(String id,double reveal) {
        var points=MD_ROUTES.get(id);
        if(points==null){
            if(id.equals(HighPowerSkills.DUAL_POINT))return List.of();
            throw new IllegalArgumentException("Missing reviewed MD route: "+id);
        }
        // dependency applies endpoint insets to a private copy; cached paths never accumulate drift.
        return dependency(java.util.Arrays.stream(points).map(double[]::clone).toArray(double[][]::new),reveal,ClassicDeveloperTimeline.LINK_WIDTH/2);
    }
    /** Row channels keep native-width links clear of all fifteen teleporter hover circles. */
    public static List<ClassicDeveloperTimeline.Edge> teleporterDependency(String id,double reveal) {
        var nodes=category("teleporter");
        var node=nodes.stream().filter(n->n.id().equals(id)).findFirst().orElseThrow();
        if(node.parent().isEmpty())return List.of();
        var parent=nodes.stream().filter(n->n.id().equals(node.parent())).findFirst().orElseThrow();
        double ax=parent.x()+8,ay=parent.y()+8,bx=node.x()+8,by=node.y()+8;
        double[][] points;
        if(Math.abs(ay-by)<=44) {
            double channel=ay==by?(ay==113?91:ay+22):(ay+by)/2;
            if(ax==bx&&ay!=by)points=new double[][]{{ax,ay},{bx,by}};
            else points=new double[][]{{ax,ay},{ax,channel},{bx,channel},{bx,by}};
        } else {
            double first=ay==25?47:91,last=by==25?47:91;
            double side=ax+bx<284?16:238;
            points=new double[][]{{ax,ay},{ax,first},{side,first},{side,last},{bx,last},{bx,by}};
        }
        return dependency(points,reveal,ClassicDeveloperTimeline.LINK_WIDTH/2);
    }
    private static List<ClassicDeveloperTimeline.Edge> dependency(double[][] points,double reveal,double halfWidth) {
        double firstLength=Math.hypot(points[1][0]-points[0][0],points[1][1]-points[0][1]);
        points[0][0]+=(points[1][0]-points[0][0])*ClassicDeveloperTimeline.LINK_INSET/firstLength;
        points[0][1]+=(points[1][1]-points[0][1])*ClassicDeveloperTimeline.LINK_INSET/firstLength;
        int last=points.length-1;
        double lastLength=Math.hypot(points[last-1][0]-points[last][0],points[last-1][1]-points[last][1]);
        points[last][0]+=(points[last-1][0]-points[last][0])*ClassicDeveloperTimeline.LINK_INSET/lastLength;
        points[last][1]+=(points[last-1][1]-points[last][1])*ClassicDeveloperTimeline.LINK_INSET/lastLength;
        double length=0;
        for(int i=1;i<points.length;i++)length+=Math.hypot(points[i][0]-points[i-1][0],points[i][1]-points[i-1][1]);
        double remaining=length*ClassicDeveloperTimeline.clamp(reveal);
        var edges=new ArrayList<ClassicDeveloperTimeline.Edge>();
        for(int i=1;i<points.length&&remaining>0;i++){
            double x=points[i-1][0],y=points[i-1][1],dx=points[i][0]-x,dy=points[i][1]-y;
            double norm=Math.hypot(dx,dy),fraction=Math.min(1,remaining/norm);
            edges.add(new ClassicDeveloperTimeline.Edge(x,y,x+dx*fraction,y+dy*fraction,dy/norm*halfWidth,-dx/norm*halfWidth));
            remaining-=norm;
        }
        return List.copyOf(edges);
    }
    public static List<ClassicDeveloperLayout.Node> category(String category) {
        if(cn.academy.port.canon.CanonSkills.activeCategory(category)) {
            var result = new ArrayList<ClassicDeveloperLayout.Node>();
            int index = 700;
            for (var skill : cn.academy.port.canon.CanonSkills.ALL) {
                if (!skill.category().equals(category)) continue;
                String parent = skill.requirements().isEmpty() ? "" : skill.requirements().getFirst().id();
                result.add(node(category, skill.id(), index++, skill.level(), 20, 20, parent));
            }
            return List.copyOf(result);
        }
        if(cn.academy.port.highpower.HighPowerCategories.VECMANIP.equals(category))
            return List.of(node(category,HighPowerSkills.DEFLECTION_STRIKE,411,1,118,61,""),
                    node(category,HighPowerSkills.VECTOR_PARRY,412,1,198,61,HighPowerSkills.DEFLECTION_STRIKE),
                    node(category,HighPowerSkills.BRAKING_TOUCH,413,1,118,126,HighPowerSkills.DEFLECTION_STRIKE),
                    node(category,HighPowerSkills.SHEAR_WINDBLADE,421,2,38,105,HighPowerSkills.DEFLECTION_STRIKE),
                    node(category,HighPowerSkills.INERTIAL_ANCHOR,422,2,118,105,HighPowerSkills.VECTOR_PARRY),
                    node(category,HighPowerSkills.YAW_PALM,423,2,198,105,HighPowerSkills.BRAKING_TOUCH),
                    node(category,HighPowerSkills.TRAJECTORY_RETURN,431,3,118,17,HighPowerSkills.YAW_PALM),
                    node(category,HighPowerSkills.CIRCULATION_RELIEF,432,3,198,17,HighPowerSkills.INERTIAL_ANCHOR),
                    node(category,HighPowerSkills.CIRCULATION_LISTENING,433,3,38,17,HighPowerSkills.CIRCULATION_RELIEF),
                    node(category,HighPowerSkills.VECTOR_CAGE,441,4,38,61,HighPowerSkills.TRAJECTORY_RETURN),
                    node(category,HighPowerSkills.MULTI_AXIS_REFLECTION,442,4,78,61,HighPowerSkills.VECTOR_CAGE),
                    node(category,HighPowerSkills.WIND_PRESSURE_LIFT,443,4,158,105,HighPowerSkills.MULTI_AXIS_REFLECTION),
                    node(category,HighPowerSkills.AUTONOMOUS_VECTOR_CIRCLE,452,5,78,17,HighPowerSkills.MULTI_AXIS_REFLECTION),
                    node(category,HighPowerSkills.ZENITH_DOMINION,451,5,158,17,HighPowerSkills.VECTOR_CAGE),
                    node(category,HighPowerSkills.IMPULSE_SPLIT,453,5,158,61,HighPowerSkills.MULTI_AXIS_REFLECTION),
                    node(category,HighPowerSkills.VECTOR_CONSTRUCT,444,4,38,126,HighPowerSkills.VECTOR_CAGE));
        if(cn.academy.port.highpower.HighPowerCategories.TELEPORTER.equals(category))
            return List.of(node(category,HighPowerSkills.SAFE_LANDING_PROBE,313,1,118,61,""),
                    node(category,HighPowerSkills.PHASE_SIDESTEP,312,1,198,61,HighPowerSkills.SAFE_LANDING_PROBE),
                    node(category,HighPowerSkills.DISPLACED_PROJECTION,311,1,38,61,HighPowerSkills.SAFE_LANDING_PROBE),
                    node(category,HighPowerSkills.RETURN_MARK,322,2,118,105,HighPowerSkills.SAFE_LANDING_PROBE),
                    node(category,HighPowerSkills.RECOVERY_MARK,323,2,38,105,HighPowerSkills.DISPLACED_PROJECTION),
                    node(category,HighPowerSkills.CROSS_SUBSTITUTION,321,2,198,105,HighPowerSkills.DISPLACED_PROJECTION),
                    node(category,HighPowerSkills.CORNER_EXCHANGE,333,3,198,17,HighPowerSkills.RETURN_MARK),
                    node(category,HighPowerSkills.SHORT_INTERCEPTION,331,3,118,17,HighPowerSkills.CROSS_SUBSTITUTION),
                    node(category,HighPowerSkills.SPATIAL_RELAY,343,4,38,17,HighPowerSkills.RECOVERY_MARK),
                    node(category,HighPowerSkills.EMERGENCY_RETURN,342,4,78,17,HighPowerSkills.RETURN_MARK),
                    node(category,HighPowerSkills.TACTICAL_COORDINATE_FORMATION,353,5,158,61,HighPowerSkills.SPATIAL_RELAY),
                    node(category,HighPowerSkills.SPATIAL_PINCER_CORRIDOR,341,4,78,105,HighPowerSkills.CROSS_SUBSTITUTION),
                    node(category,HighPowerSkills.MULTIDIMENSIONAL_LANDING_ARRAY,351,5,158,105,HighPowerSkills.SPATIAL_PINCER_CORRIDOR),
                    node(category,HighPowerSkills.INCIDENT_BYPASS,332,3,158,17,HighPowerSkills.CROSS_SUBSTITUTION),
                    node(category,HighPowerSkills.PHASE_CORRIDOR,352,5,78,61,HighPowerSkills.EMERGENCY_RETURN));
        if(cn.academy.port.highpower.HighPowerCategories.MELTDOWNER.equals(category))
            return List.of(node(category,HighPowerSkills.DUAL_POINT,211,1,160,110,""),
                    node(category,HighPowerSkills.SCATTER_PLATES,212,1,83,87,HighPowerSkills.DUAL_POINT),
                    node(category,HighPowerSkills.BEAM_ETCHING,213,1,92.1,10.6,HighPowerSkills.DUAL_POINT),
                    node(category,HighPowerSkills.THERMAL_SHELL,222,2,230,110,HighPowerSkills.SCATTER_PLATES),
                    node(category,HighPowerSkills.FOLDED_MIRROR_ARRAY,221,2,206,97,HighPowerSkills.DUAL_POINT),
                    node(category,HighPowerSkills.DISINTEGRATION_SWEEP,231,3,160,105,HighPowerSkills.FOLDED_MIRROR_ARRAY),
                    node(category,HighPowerSkills.BEAM_STEP,232,3,50,20,HighPowerSkills.THERMAL_SHELL),
                    node(category,HighPowerSkills.POINT_MELTDOWN,233,3,20,105,HighPowerSkills.BEAM_ETCHING),
                    node(category,HighPowerSkills.PARTICLE_LAMINATE,241,4,225,63,HighPowerSkills.DISINTEGRATION_SWEEP),
                    node(category,HighPowerSkills.DISSIPATION_CURTAIN,242,4,142,40,HighPowerSkills.SCATTER_PLATES),
                    node(category,HighPowerSkills.MULTICORE_CANOPY,251,5,8,110,HighPowerSkills.PARTICLE_LAMINATE),
                    node(category,HighPowerSkills.ZERO_GAP_ARMOR,252,5,160,61,HighPowerSkills.DISSIPATION_CURTAIN),
                    node(category,HighPowerSkills.REMOTE_SCATTER_RELAY,253,5,75,61,HighPowerSkills.FOLDED_MIRROR_ARRAY),
                    node(category,HighPowerSkills.SCATTER_LOCATOR_PLATE,223,2,8,8,HighPowerSkills.FOLDED_MIRROR_ARRAY),
                    node(category,HighPowerSkills.REVERSE_JET,243,4,190,20,HighPowerSkills.BEAM_STEP));
        if(!HighPowerSkills.CATEGORY.equals(category))return List.of();
        return List.of(node(HighPowerSkills.FORKED_ARC,111,1,8,8,""),
                node(HighPowerSkills.MAGNETIC_BRACER,112,1,8,78,HighPowerSkills.FORKED_ARC),
                node(HighPowerSkills.CHARGE_DECOY,123,2,73,110,HighPowerSkills.MAGNETIC_BRACER),
                node(HighPowerSkills.POLARITY_TETHER,113,1,127,76,HighPowerSkills.FORKED_ARC),
                node(HighPowerSkills.CONDUCTIVE_CIRCUIT,121,2,170,12,HighPowerSkills.FORKED_ARC),
                node(HighPowerSkills.GROUNDING_RETURN,122,2,160,112,HighPowerSkills.MAGNETIC_BRACER),
                node(HighPowerSkills.MAGNETIC_GROUND_STEP,132,3,230,56.5,HighPowerSkills.POLARITY_TETHER),
                node(HighPowerSkills.MAGNETIC_ITEM_PULL,133,3,46,77,HighPowerSkills.POLARITY_TETHER),
                node(HighPowerSkills.IRON_SAND_LANCE,131,3,107,44,HighPowerSkills.POLARITY_TETHER),
                node(HighPowerSkills.THUNDER_NET,141,4,77,38,HighPowerSkills.CONDUCTIVE_CIRCUIT),
                node(HighPowerSkills.POTENTIAL_BUFFER,142,4,230,94,HighPowerSkills.GROUNDING_RETURN),
                node(HighPowerSkills.RAIL_THUNDER_ARRAY,151,5,124,11,HighPowerSkills.THUNDER_NET),
                node(HighPowerSkills.MAGNETIC_DOMAIN,152,5,94,93,HighPowerSkills.POTENTIAL_BUFFER),
                node(HighPowerSkills.PULSE_SHIELD,143,4,178,63,HighPowerSkills.THUNDER_NET),
                node(HighPowerSkills.RAIL_RELAY,153,5,211,14,HighPowerSkills.RAIL_THUNDER_ARRAY));
    }
    private static ClassicDeveloperLayout.Node node(String id,int proposal,int level,double x,double y,String parent) {
        return node(HighPowerSkills.CATEGORY,id,proposal,level,x,y,parent);
    }
    private static ClassicDeveloperLayout.Node node(String category,String id,int proposal,int level,double x,double y,String parent) {
        String base="ac.ability."+category+"."+id;
        var conditions=new ArrayList<JsonObject>();
        String minimum=cn.academy.port.develop.DeveloperType.minimumForSkill(level).name().toLowerCase(java.util.Locale.ROOT);
        var tier=new JsonObject();tier.addProperty("type","developer_type");tier.addProperty("minimum",minimum);
        tier.addProperty("shouldDisplay",true);tier.addProperty("icon",minimum.equals("portable")?"academy:textures/items/developer_portable_empty.png":"academy:textures/blocks/developer_"+minimum+".png");
        tier.addProperty("hintKey","ac.skill_tree.type_"+minimum);conditions.add(tier);
        if(!parent.isEmpty()){
            var requirement=new JsonObject();requirement.addProperty("type","skill_dependency");requirement.addProperty("id",parent);
            requirement.addProperty("minimum_exp",0);requirement.addProperty("shouldDisplay",true);
            requirement.addProperty("icon","academy:textures/abilities/"+category+"/skills/"+parent+".png");
            requirement.addProperty("hintNameKey","ac.ability."+category+"."+parent+".name");requirement.addProperty("hintSuffix","");conditions.add(requirement);
        }
        return new ClassicDeveloperLayout.Node(category,id,proposal,level,x,y,parent,
                SkillCatalog.enabled(category,id),"academy:textures/abilities/"+category+"/skills/"+id+".png",
                base+".name",base+".desc",List.copyOf(conditions));
    }
}
