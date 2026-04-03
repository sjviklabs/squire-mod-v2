package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.entity.SquireEntity;
import com.sjviklabs.squire.entity.SquireTier;
import com.sjviklabs.squire.item.SquireArmorItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;

/**
 * Renders the squire's 4-piece armor set with tier-appropriate textures.
 * Uses Geckolib's ItemArmorGeoLayer which handles enchantment glint and
 * vanilla material fallback for non-SquireArmorItem items automatically.
 *
 * Tier to texture index mapping (5 tiers -> 4 texture indices):
 *   SERVANT(0)   -> t0
 *   APPRENTICE(1)-> t1
 *   SQUIRE(2)    -> t2
 *   KNIGHT(3)    -> t2  (clamped: Math.min(ordinal, 3))
 *   CHAMPION(4)  -> t3  (clamped: Math.min(ordinal, 3))
 *
 * Texture selection is done by overriding getVanillaArmorBuffer, which is the
 * actual hook point in Geckolib 4.8.4. The RESEARCH.md pattern referenced
 * getArmorTexture(), which does not exist in the 4.8.4 API.
 */
public class SquireArmorLayer extends ItemArmorGeoLayer<SquireEntity> {

    private static final ResourceLocation[] TIER_OUTER = {
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t0.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t1.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t2.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_1_t3.png"),
    };
    private static final ResourceLocation[] TIER_INNER = {
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t0.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t1.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t2.png"),
        ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/models/armor/squire_layer_2_t3.png"),
    };

    public SquireArmorLayer(GeoRenderer<SquireEntity> renderer) {
        super(renderer);
    }

    @Override
    @Nullable
    protected EquipmentSlot getEquipmentSlotForBone(GeoBone bone, ItemStack stack,
            SquireEntity animatable) {
        return switch (bone.getName()) {
            case "head"                    -> EquipmentSlot.HEAD;
            case "body"                    -> EquipmentSlot.CHEST;
            case "right_leg", "left_leg"   -> EquipmentSlot.LEGS;
            case "right_foot", "left_foot" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    @Override
    @Nullable
    protected ModelPart getModelPartForBone(GeoBone bone, EquipmentSlot slot,
            ItemStack stack, SquireEntity animatable, HumanoidModel<?> baseModel) {
        return switch (bone.getName()) {
            case "head"       -> baseModel.head;
            case "body"       -> baseModel.body;
            case "right_leg"  -> baseModel.rightLeg;
            case "left_leg"   -> baseModel.leftLeg;
            case "right_foot" -> baseModel.rightLeg;   // boots reuse the leggings model part
            case "left_foot"  -> baseModel.leftLeg;
            default -> null;
        };
    }

    @Override
    @Nullable
    protected ItemStack getArmorItemForBone(GeoBone bone, SquireEntity animatable) {
        return switch (bone.getName()) {
            case "head"                    -> animatable.getItemBySlot(EquipmentSlot.HEAD);
            case "body"                    -> animatable.getItemBySlot(EquipmentSlot.CHEST);
            case "right_leg", "left_leg"   -> animatable.getItemBySlot(EquipmentSlot.LEGS);
            case "right_foot", "left_foot" -> animatable.getItemBySlot(EquipmentSlot.FEET);
            default -> null;
        };
    }

    /**
     * Intercept texture selection for SquireArmorItem stacks.
     *
     * Geckolib 4.8.4 does not have getArmorTexture() — texture comes from
     * ArmorMaterial.Layer.texture(boolean). We override getVanillaArmorBuffer
     * to substitute a tier-appropriate RenderType when the item is SquireArmorItem.
     * Non-squire armor falls through to super (vanilla material texture lookup).
     */
    @Override
    protected VertexConsumer getVanillaArmorBuffer(MultiBufferSource bufferSource,
            SquireEntity animatable, ItemStack stack, EquipmentSlot slot,
            GeoBone bone, ArmorMaterial.Layer layer, int packedLight, int packedOverlay,
            boolean isEnchanted) {
        if (isEnchanted) {
            // Let super handle the glint buffer — texture doesn't matter for enchantment glint
            return super.getVanillaArmorBuffer(bufferSource, animatable, stack, slot,
                    bone, layer, packedLight, packedOverlay, true);
        }
        if (stack.getItem() instanceof SquireArmorItem) {
            int tierIdx = Math.min(
                SquireTier.fromLevel(animatable.getLevel()).ordinal(),
                TIER_OUTER.length - 1
            );
            boolean innerLayer = (slot == EquipmentSlot.LEGS);
            ResourceLocation texture = innerLayer ? TIER_INNER[tierIdx] : TIER_OUTER[tierIdx];
            return bufferSource.getBuffer(RenderType.armorCutoutNoCull(texture));
        }
        return super.getVanillaArmorBuffer(bufferSource, animatable, stack, slot,
                bone, layer, packedLight, packedOverlay, false);
    }
}
