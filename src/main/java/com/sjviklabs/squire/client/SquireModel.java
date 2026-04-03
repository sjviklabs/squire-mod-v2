package com.sjviklabs.squire.client;

import com.sjviklabs.squire.SquireMod;
import com.sjviklabs.squire.entity.SquireEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * Geckolib model for SquireEntity.
 * getModelResource and getTextureResource route male/female assets based on
 * the SLIM_MODEL SynchedEntityData field (entity.isSlimModel()).
 *
 * GeoEntityRenderer handles .geo.json loading at runtime — no baked model layer
 * registration is needed (unlike vanilla HumanoidModel).
 *
 * Asset paths (created in 03-02):
 *   geo/squire_male.geo.json / geo/squire_female.geo.json
 *   textures/entity/squire_male.png / textures/entity/squire_female.png
 *   animations/squire.animation.json
 */
public class SquireModel extends GeoModel<SquireEntity> {

    private static final ResourceLocation MALE_MODEL =
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "geo/squire_male.geo.json");
    private static final ResourceLocation FEMALE_MODEL =
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "geo/squire_female.geo.json");
    private static final ResourceLocation MALE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/entity/squire_male.png");
    private static final ResourceLocation FEMALE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "textures/entity/squire_female.png");

    @Override
    public ResourceLocation getModelResource(SquireEntity entity) {
        return entity.isSlimModel() ? FEMALE_MODEL : MALE_MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(SquireEntity entity) {
        return entity.isSlimModel() ? FEMALE_TEXTURE : MALE_TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(SquireEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(SquireMod.MODID, "animations/squire.animation.json");
    }
}
