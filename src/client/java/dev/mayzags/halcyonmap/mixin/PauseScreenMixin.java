package dev.mayzags.halcyonmap.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.mayzags.halcyonmap.MapButton;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PauseScreen.class)
abstract class PauseScreenMixin {

    /**
     * В ванили строка иконок (баг, отзыв, друзья, жалобы) добавляется в сетку через
     * RowHelper.addChild(element, 2, settings). Перед этим вызовом добавляем нашу кнопку в эту строку,
     * и LinearLayout сам выровняет все кнопки по центру.
     */
    @WrapOperation(
            method = "createPauseMenu",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;ILnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;"
            )
    )
    private LayoutElement halcyonmap$addMapButton(
            GridLayout.RowHelper helper,
            LayoutElement element,
            int columns,
            LayoutSettings settings,
            Operation<LayoutElement> original
    ) {
        if (element instanceof LinearLayout row) {
            row.addChild(MapButton.create((PauseScreen) (Object) this));
        }
        return original.call(helper, element, columns, settings);
    }

}
