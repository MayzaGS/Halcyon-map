package dev.mayzags.halcyonmap;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Подключает экран настроек к кнопке настроек мода в Mod Menu. Загружается, только если Mod Menu установлен. */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new MapConfigScreen(parent);
    }

}
