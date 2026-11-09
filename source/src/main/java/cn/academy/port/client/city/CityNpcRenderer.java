package cn.academy.port.client.city;

import cn.academy.port.city.CityNpcContent;
import cn.academy.port.city.CityNpcEntity;
import cn.academy.port.city.CityNpcEntity.Activity;
import cn.academy.port.city.CityNpcEntity.Look;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CityNpcRenderer extends MobRenderer<CityNpcEntity, CityNpcRenderer.Model> {
    private static final String TEXTURES = "textures/entity/city_npc/";
    private final Map<Look, ResourceLocation> skins = new EnumMap<>(Look.class);
    private final Model regular;
    private final Model slim;

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CityNpcContent.NPC.get(), CityNpcRenderer::new);
    }

    private CityNpcRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.PLAYER), false), 0.35f);
        regular = model;
        slim = new Model(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        for (Look look : Look.values()) {
            skins.put(look, ResourceLocation.fromNamespaceAndPath("academy", TEXTURES + look.id() + ".png"));
        }
        addLayer(new Accessories(this));
        addLayer(new Faces(this));
        addLayer(new net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer<>(this,
                new net.minecraft.client.model.HumanoidModel<CityNpcEntity>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new net.minecraft.client.model.HumanoidModel<CityNpcEntity>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),context.getModelManager()));
    }

    @Override
    public ResourceLocation getTextureLocation(CityNpcEntity npc) {
        return skins.get(npc.look());
    }

    @Override
    public void render(CityNpcEntity npc, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        model = npc.look().slim() ? slim : regular;
        super.render(npc, yaw, partialTick, pose, buffers, light);
    }

    @Override
    protected void scale(CityNpcEntity npc, PoseStack pose, float partialTick) {
        if (npc.activity() == CityNpcEntity.Activity.SIT && !npc.isPassenger()) {
            // Anchor the hips over the front edge of a one-block-deep seat.
            pose.translate(0, 0.62, -0.28);
        }
    }

    static final class Model extends PlayerModel<CityNpcEntity> {
        Model(ModelPart root, boolean slim) {
            super(root, slim);
        }

        @Override
        public void setupAnim(CityNpcEntity npc, float stride, float strideAmount, float age, float yaw, float pitch) {
            super.setupAnim(npc, stride, strideAmount, age, yaw, pitch);
            boolean sitting = npc.activity() == Activity.SIT;
            rightLeg.skipDraw = leftLeg.skipDraw = sitting;
            rightPants.skipDraw = leftPants.skipDraw = sitting;
            head.zRot = 0;
            float beat = Mth.sin(age * 0.23f + npc.getId());
            switch (npc.activity()) {
                case STAND -> {}
                case SIT -> {
                    rightLeg.xRot = leftLeg.xRot = -Mth.HALF_PI;
                    rightLeg.yRot = leftLeg.yRot = 0;
                    rightLeg.zRot = leftLeg.zRot = 0;
                    rightArm.xRot = -0.28f;
                    leftArm.xRot = -0.28f;
                }
                case PHONE -> {
                    head.xRot = 0.30f;
                    head.yRot = -0.10f;
                    rightArm.xRot = -1.05f;
                    rightArm.yRot = 0;
                    rightArm.zRot = -0.23f;
                }
                case READ -> {
                    head.xRot = 0.31f;
                    rightArm.xRot = -1.00f;
                    leftArm.xRot = -0.91f;
                    rightArm.yRot = 0;
                    leftArm.yRot = 0;
                    rightArm.zRot = -0.32f;
                    leftArm.zRot = 0.29f;
                }
                case WAVE -> {
                    rightArm.xRot = -2.32f;
                    rightArm.zRot = -0.55f + beat * 0.36f;
                    head.zRot = 0.05f;
                }
                case CHAT -> {
                    head.xRot += beat * 0.12f;
                    rightArm.xRot = -0.36f + beat * 0.30f;
                    rightArm.zRot = 0.14f;
                }
                case PHOTO -> {
                    head.xRot = 0.06f;
                    rightArm.xRot = -1.54f;
                    leftArm.xRot = -1.48f;
                    rightArm.zRot = -0.30f;
                    leftArm.zRot = 0.28f;
                }
                case CHECK -> {
                    head.xRot = 0.35f + beat * 0.04f;
                    rightArm.xRot = -1.06f;
                    rightArm.zRot = -0.23f;
                }
                case STRETCH -> {
                    rightArm.xRot = -2.52f;
                    leftArm.xRot = -2.52f;
                    rightArm.zRot = -0.46f + beat * 0.08f;
                    leftArm.zRot = 0.46f - beat * 0.08f;
                    head.xRot = -0.10f;
                }
                case TEND -> {
                    head.xRot = 0.22f;
                    rightArm.xRot = -0.77f + beat * 0.19f;
                    leftArm.xRot = -0.64f - beat * 0.15f;
                    rightArm.zRot = -0.22f;
                    leftArm.zRot = 0.22f;
                }
                case SCAN -> head.yRot += Mth.sin(age * 0.075f + npc.getId()) * 0.46f;
                case PERFORM -> {
                    head.xRot = beat * 0.13f;
                    rightArm.xRot = -0.70f + beat * 0.20f;
                    rightArm.zRot = -0.42f;
                    leftArm.xRot = -0.96f;
                    leftArm.zRot = 0.14f;
                }
                case EAT -> {
                    head.xRot = 0.14f + beat * 0.035f;
                    rightArm.xRot = -1.05f + beat * 0.15f;
                    rightArm.zRot = -0.25f;
                }
                case DRINK -> {
                    head.xRot = -0.08f + beat * 0.025f;
                    rightArm.xRot = -1.42f + beat * 0.07f;
                    rightArm.zRot = -0.18f;
                }
                case CAST -> {
                    head.xRot=.05f;
                    rightArm.xRot=-1.27f+beat*.06f;rightArm.zRot=-.35f;
                    leftArm.xRot=-.72f;leftArm.zRot=.45f;
                    if(npc.esper()==cn.academy.port.city.CityEspers.Profile.MENTAL) rightArm.xRot=-2.1f;
                    if(npc.esper()==cn.academy.port.city.CityEspers.Profile.NITROGEN) leftArm.xRot=rightArm.xRot=-1.05f;
                }
                case TREAT -> {
                    head.xRot=.22f;rightArm.xRot=-1.12f;leftArm.xRot=-.95f;
                    rightArm.zRot=-.18f+beat*.04f;leftArm.zRot=.18f;
                }
                case REPAIR -> {
                    head.xRot=.4f;rightArm.xRot=-1.05f+beat*.22f;leftArm.xRot=-1.1f;
                    rightArm.zRot=-.22f;leftArm.zRot=.20f;
                }
            }
            hat.copyFrom(head);
            leftPants.copyFrom(leftLeg);
            rightPants.copyFrom(rightLeg);
            leftSleeve.copyFrom(leftArm);
            rightSleeve.copyFrom(rightArm);
        }
    }

    private static final class Faces extends RenderLayer<CityNpcEntity, Model> {
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("academy", TEXTURES + "expressions.png");
        private final CityNpcRenderer parent;
        private final ModelPart[][] faces = new ModelPart[Look.values().length][4];

        Faces(CityNpcRenderer parent) {
            super(parent);
            this.parent = parent;
            for (Look look : Look.values()) {
                for (int expression = 0; expression < 4; expression++) {
                    int index = look.ordinal() * 4 + expression;
                    MeshDefinition mesh = new MeshDefinition();
                    mesh.getRoot().addOrReplaceChild("face", CubeListBuilder.create()
                            .texOffs((index % 8) * 8, (index / 8) * 8)
                            .addBox(-4, -8, -4.025f, 8, 8, 0, Set.of(Direction.NORTH)), PartPose.ZERO);
                    faces[look.ordinal()][expression] = LayerDefinition.create(mesh, 64, 128).bakeRoot().getChild("face");
                }
            }
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CityNpcEntity npc,
                           float stride, float amount, float partialTick, float age, float yaw, float pitch) {
            if (npc.isInvisible() || parent.entityRenderDispatcher.distanceToSqr(npc) > 28 * 28) return;
            boolean blinking = Math.floorMod(npc.tickCount + npc.getId() * 37, 97 + Math.floorMod(npc.getId(), 23)) < 3;
            int index = blinking ? 3 : npc.expression().ordinal() - 1;
            if (index < 0) return;
            pose.pushPose();
            getParentModel().head.translateAndRotate(pose);
            faces[npc.look().ordinal()][index].render(pose, buffers.getBuffer(RenderType.entityCutout(TEXTURE)),
                    light, getOverlayCoords(npc, 0));
            pose.popPose();
        }
    }

    /** Boxes use sixteen palette cells arranged in four columns of 32 by 16 pixels. */
    private static final class Accessories extends RenderLayer<CityNpcEntity, Model> {
        private static final ResourceLocation PALETTE = ResourceLocation.fromNamespaceAndPath("academy", TEXTURES + "accessories.png");
        private static final int DARK = 0, BURGUNDY = 1, JADE = 2, LEATHER = 3, HAIR = 4, MINT = 5,
                GOLD = 6, GLASS = 7, PAPER = 8, BOOK = 9, BLACK = 11, YELLOW = 12, NAVY = 13,
                WHITE = 14, PURPLE = 15;
        private final Map<Look, ModelPart> hairAndHats = new EnumMap<>(Look.class);
        private final Map<Look, ModelPart> bags = new EnumMap<>(Look.class);
        private final ModelPart phone;
        private final ModelPart book;
        private final ModelPart camera;
        private final ModelPart parcel;
        private final ModelPart instrument;
        private final ModelPart snack;
        private final ModelPart drink;
        private final ModelPart rightSeatedLeg = seatedLeg(0, 16);
        private final ModelPart leftSeatedLeg = seatedLeg(16, 48);

        Accessories(CityNpcRenderer parent) {
            super(parent);
            hairAndHats.put(Look.STUDENT_BAG, bake(box(box(CubeListBuilder.create(), HAIR, -1.5f, -6, 3.8f, 3, 3, 2),
                    HAIR, -1, -4, 4.9f, 2, 5, 2)));
            hairAndHats.put(Look.STUDENT_BOOKS, bake(box(box(CubeListBuilder.create(), DARK, -4.25f, -5, 1.6f, 2, 6, 3),
                    DARK, 2.25f, -5, 1.6f, 2, 6, 3)));
            hairAndHats.put(Look.VISITOR, bake(box(box(CubeListBuilder.create(), MINT, -4.25f, -8.8f, -4.25f, 8, 2, 8),
                    JADE, -3.75f, -7.6f, -7.4f, 7, 1, 4)));
            hairAndHats.put(Look.GUARD, cap(NAVY, BLACK));
            hairAndHats.put(Look.TECHNICIAN, cap(YELLOW, GOLD));
            hairAndHats.put(Look.AIRPORT_STAFF, cap(NAVY, GOLD));
            hairAndHats.put(Look.ATHLETE, bake(box(CubeListBuilder.create(), WHITE, -4.55f, -6.8f, -4.55f, 9, 1, 9)));
            hairAndHats.put(Look.PERFORMER, cap(PURPLE, BURGUNDY));
            hairAndHats.put(Look.RAIL_STAFF, cap(NAVY, GOLD));
            hairAndHats.put(Look.ENGINEER, cap(YELLOW, DARK));
            hairAndHats.put(Look.TRAVELER, cap(MINT, JADE));
            hairAndHats.put(Look.STUDENT_BLAZER, bake(box(box(CubeListBuilder.create(), DARK, -4.15f, -5, 2.4f, 2, 6, 2),
                    DARK, 2.2f, -5, 2.4f, 2, 6, 2)));
            hairAndHats.put(Look.BARISTA, bake(box(CubeListBuilder.create(), LEATHER, -4.2f, -8.6f, -4.2f, 8, 2, 8)));
            bags.put(Look.STUDENT_BAG, bag(BURGUNDY, LEATHER, -3.5f, 3.1f, 7, 8, 3));
            bags.put(Look.STUDENT_SPORT, bag(JADE, MINT, -3, 4.6f, 6, 6, 4));
            bags.put(Look.COMMUTER, bag(LEATHER, GOLD, -3.5f, 6.2f, 7, 4, 2));
            bags.put(Look.OFFICE_STAFF, bag(NAVY, LEATHER, -3, 6.2f, 6, 4, 2));
            bags.put(Look.TECHNICIAN, bag(DARK, YELLOW, -3.5f, 5.2f, 7, 5, 3));
            bags.put(Look.CASUAL_VISITOR, bag(BURGUNDY, GOLD, -3, 5.2f, 6, 5, 2));
            bags.put(Look.VISITOR, bake(box(box(CubeListBuilder.create(), BLACK, -2.5f, 5, -3.7f, 5, 3, 2),
                    GLASS, -1, 5.5f, -4.5f, 2, 2, 1)));
            bags.put(Look.STUDENT_BLAZER, bag(NAVY, GOLD, -3.5f, 3.1f, 7, 8, 3));
            bags.put(Look.STUDENT_CASUAL, bag(JADE, MINT, -3, 4, 6, 7, 3));
            bags.put(Look.TRAVELER, bag(LEATHER, GOLD, -3.5f, 4.0f, 7, 7, 3));
            bags.put(Look.ENGINEER, bag(DARK, YELLOW, -3.5f, 5.2f, 7, 5, 3));
            phone = bake(box(box(CubeListBuilder.create(), BLACK, -2, 8, -3.1f, 3, 5, 1),
                    GLASS, -1.5f, 8.5f, -3.25f, 2, 4, 1));
            book = bake(box(box(CubeListBuilder.create(), BOOK, -1, 8.0f, -3.25f, 6, 5, 1),
                    PAPER, -0.5f, 8.5f, -3.55f, 5, 4, 1));
            camera = bake(box(box(CubeListBuilder.create(), BLACK, -0.7f, 8, -3.65f, 5, 3, 2),
                    GLASS, 0.8f, 8.7f, -4.6f, 2, 2, 1));
            parcel = bake(box(box(CubeListBuilder.create(), PAPER, -0.9f, 8, -4.4f, 5, 4, 3),
                    GOLD, 1.1f, 7.85f, -4.5f, 1, 1, 3));
            instrument = bake(box(box(box(CubeListBuilder.create(), BOOK, -3.2f, 6, -4.1f, 6, 5, 2),
                    DARK, -1.25f, 7, -4.3f, 2, 2, 1), LEATHER, 2.45f, 6.4f, -3.8f, 7, 2, 1));
            snack = bake(box(box(CubeListBuilder.create(), PAPER, -1.5f, 8.5f, -3.8f, 4, 3, 2),
                    GOLD, -1, 8.1f, -4, 3, 1, 2));
            drink = bake(box(box(CubeListBuilder.create(), MINT, -1.1f, 8, -3.7f, 3, 4, 3),
                    WHITE, -1.2f, 7.5f, -3.8f, 3, 1, 3));
        }

        private static ModelPart cap(int color, int brim) {
            return bake(box(box(CubeListBuilder.create(), color, -4.6f, -10.1f, -4.6f, 9, 3, 9),
                    brim, -4.25f, -7.9f, -7.7f, 8, 1, 4));
        }

        private static ModelPart bag(int color, int trim, float x, float y, int width, int height, int depth) {
            CubeListBuilder cubes = box(CubeListBuilder.create(), color, x, y, 2.1f, width, height, depth);
            box(cubes, trim, x + 1, y + height - 3, 2.1f + depth - 0.4f, width - 2, 2, 1);
            return bake(cubes);
        }

        private static CubeListBuilder box(CubeListBuilder cubes, int cell, float x, float y, float z,
                                          int width, int height, int depth) {
            return cubes.texOffs((cell % 4) * 32, (cell / 4) * 16).addBox(x, y, z, width, height, depth);
        }

        private static ModelPart bake(CubeListBuilder cubes) {
            MeshDefinition mesh = new MeshDefinition();
            mesh.getRoot().addOrReplaceChild("part", cubes, PartPose.ZERO);
            return LayerDefinition.create(mesh, 128, 64).bakeRoot().getChild("part");
        }

        private static ModelPart seatedLeg(int u, int v) {
            MeshDefinition mesh = new MeshDefinition();
            var thigh = mesh.getRoot().addOrReplaceChild("thigh", CubeListBuilder.create().texOffs(u, v)
                    .addBox(-2, 0, -2, 4, 6, 4), PartPose.ZERO);
            thigh.addOrReplaceChild("shin", CubeListBuilder.create().texOffs(u, v + 6)
                    .addBox(-2, 0, -2, 4, 6, 4), PartPose.offsetAndRotation(0, 6, 0, Mth.HALF_PI, 0, 0));
            var result = LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("thigh");
            result.getChild("shin").yScale = 1.55f;
            return result;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, CityNpcEntity npc,
                           float stride, float amount, float partialTick, float age, float yaw, float pitch) {
            if (npc.isInvisible()) return;
            var model = getParentModel();
            var vertices = buffers.getBuffer(RenderType.entityCutoutNoCull(PALETTE));
            int overlay = getOverlayCoords(npc, 0);
            if(!cn.academy.port.city.defense.armor.DefenseArmor.full(npc,false))draw(hairAndHats.get(npc.look()), model.head, pose, vertices, light, overlay);
            if (npc.look() != Look.VISITOR || npc.activity() != Activity.PHOTO) {
                draw(bags.get(npc.look()), model.body, pose, vertices, light, overlay);
            }
            switch (npc.activity()) {
                case PHONE -> draw(phone, model.rightArm, pose, vertices, light, overlay);
                case READ, CHECK -> draw(book, model.rightArm, pose, vertices, light, overlay);
                case PHOTO -> draw(camera, model.rightArm, pose, vertices, light, overlay);
                case TEND -> draw(parcel, model.rightArm, pose, vertices, light, overlay);
                case PERFORM -> draw(instrument, model.body, pose, vertices, light, overlay);
                case EAT -> draw(snack, model.rightArm, pose, vertices, light, overlay);
                case DRINK -> draw(drink, model.rightArm, pose, vertices, light, overlay);
                case SIT -> {
                    var skinVertices = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(npc)));
                    draw(rightSeatedLeg, model.rightLeg, pose, skinVertices, light, overlay);
                    draw(leftSeatedLeg, model.leftLeg, pose, skinVertices, light, overlay);
                }
                default -> {}
            }
        }

        private static void draw(ModelPart part, ModelPart joint, PoseStack pose, VertexConsumer vertices, int light, int overlay) {
            if (part == null) return;
            pose.pushPose();
            joint.translateAndRotate(pose);
            part.render(pose, vertices, light, overlay);
            pose.popPose();
        }
    }
}
