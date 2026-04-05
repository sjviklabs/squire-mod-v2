package com.sjviklabs.squire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

/**
 * Geckolib entity renderer for the squire.
 *
 * All renderer-readable state comes from SynchedEntityData on SquireEntity — never from SquireBrain.
 * SquireModel handles male/female routing via entity.isSlimModel().
 *
 * Render layers (added in 03-03):
 *   addRenderLayer(new SquireArmorLayer(this))
 *   addRenderLayer(new SquireBackpackLayer(this))
 *
 * Health bar rendering is ported from v0.5.0 via renderNameTag override in 03-04.
 */
public class SquireRenderer extends GeoEntityRenderer<SquireEntity> {

    public SquireRenderer(EntityRendererProvider.Context context) {
        super(context, new SquireModel());
        addRenderLayer(new SquireArmorLayer(this));
        addRenderLayer(new SquireBackpackLayer(this));
        addRenderLayer(new BlockAndItemGeoLayer<>(this,
                (bone, entity) -> switch (bone.getName()) {
                    case "left_hand"  -> entity.getMainHandItem();
                    case "right_hand" -> entity.getOffhandItem();
                    default -> ItemStack.EMPTY;
                },
                (bone, entity) -> null
        ));
    }

    @Override
    protected void renderNameTag(SquireEntity entity, Component displayName,
            PoseStack poseStack, MultiBufferSource bufferSource,
            int packedLight, float partialTick) {
        super.renderNameTag(entity, displayName, poseStack, bufferSource, packedLight, partialTick);
        // Health bar: port from v0.5.0 SquireRenderer.renderHealthBar() in Phase 3-04
        // Stub is intentionally empty — name tag renders via super call above
    }
}
