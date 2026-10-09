package dev.mayzags.halcyonmap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class MapButton {

    // assets/halcyon_map/textures/gui/sprites/pause_menu/map.png
    // Ресурспаки могут заменить этот спрайт, а фон кнопки берётся из ванильных спрайтов кнопок.
    private static final Identifier SPRITE = Identifier.fromNamespaceAndPath(HalcyonMapClient.MOD_ID, "pause_menu/map");
    private static final Component TITLE = Component.translatable("halcyon_map.button.map");

    private MapButton() {
    }

    public static SpriteIconButton create(Screen parent) {
        return SpriteIconButton.builder(TITLE, button -> Minecraft.getInstance().gui.setScreen(new MapScreen(parent)), true)
                .width(20)
                .sprite(SPRITE, 16, 16)
                .withTootip()
                .build();
    }

}
