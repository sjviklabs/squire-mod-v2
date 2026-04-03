package com.sjviklabs.squire.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * KeyMapping constants for Squire client controls.
 *
 * All constants in this class are loaded only on the physical client
 * because this class lives in the client/ package and is only referenced
 * from @EventBusSubscriber(value = Dist.CLIENT) handlers.
 */
public final class SquireKeybinds {

    /**
     * Radial menu keybind — defaults to R.
     *
     * Registered in the "key.categories.squire" category so it appears
     * in the Controls screen under a dedicated Squire section.
     * KeyConflictContext.IN_GAME prevents conflicts with chat-open context.
     */
    public static final KeyMapping RADIAL_MENU = new KeyMapping(
            "key.squire.radial_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.squire"
    );

    private SquireKeybinds() {}
}
