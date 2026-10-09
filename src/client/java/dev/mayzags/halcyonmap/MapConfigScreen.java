package dev.mayzags.halcyonmap;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Экран настроек мода (открывается из Mod Menu): адрес карты, масштаб страницы и частота кадров. */
public class MapConfigScreen extends Screen {

    private static final int FPS_STEP = 10;

    private final Screen parent;
    private EditBox urlBox;
    private StringWidget zoomLabel;
    private StringWidget fpsLabel;
    private double zoom;
    private int fps;

    public MapConfigScreen(Screen parent) {
        super(Component.translatable("halcyon_map.config.title"));
        this.parent = parent;
        this.zoom = MapConfig.zoom();
        this.fps = MapConfig.fps();
    }

    @Override
    protected void init() {
        int boxWidth = Math.min(300, width - 40);
        int left = width / 2 - boxWidth / 2;
        int right = left + boxWidth;

        int titleWidth = font.width(title);
        addRenderableWidget(new StringWidget(width / 2 - titleWidth / 2, 20, titleWidth, 9, title, font));

        // Адрес карты и кнопка сброса на карту сервера Halcyon.
        Component urlText = Component.translatable("halcyon_map.config.url");
        addRenderableWidget(new StringWidget(left, 46, font.width(urlText), 9, urlText, font));
        String previous = urlBox != null ? urlBox.getValue() : MapConfig.url();
        urlBox = addRenderableWidget(new EditBox(font, left, 58, boxWidth - 76, 20, urlText));
        urlBox.setMaxLength(512);
        urlBox.setValue(previous);
        addRenderableWidget(Button.builder(Component.translatable("halcyon_map.config.reset"),
                        button -> urlBox.setValue(MapConfig.DEFAULT_URL))
                .bounds(right - 72, 58, 72, 20)
                .build());

        // Масштаб страницы.
        zoomLabel = addRenderableWidget(new StringWidget(left, 100, 10, 9, Component.empty(), font));
        addRenderableWidget(Button.builder(Component.literal("-"), button -> changeZoom(-0.1))
                .bounds(right - 44, 94, 20, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> changeZoom(0.1))
                .bounds(right - 20, 94, 20, 20)
                .build());

        // Частота кадров.
        fpsLabel = addRenderableWidget(new StringWidget(left, 128, 10, 9, Component.empty(), font));
        addRenderableWidget(Button.builder(Component.literal("-"), button -> changeFps(-FPS_STEP))
                .bounds(right - 44, 122, 20, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("+"), button -> changeFps(FPS_STEP))
                .bounds(right - 20, 122, 20, 20)
                .build());

        int buttonWidth = (boxWidth - 8) / 2;
        addRenderableWidget(Button.builder(Component.translatable("halcyon_map.config.save"), button -> save())
                .bounds(left, height - 36, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("halcyon_map.config.cancel"), button -> onClose())
                .bounds(right - buttonWidth, height - 36, buttonWidth, 20)
                .build());

        updateLabels(left);
    }

    private void changeZoom(double delta) {
        zoom = Math.max(MapConfig.MIN_ZOOM, Math.min(MapConfig.MAX_ZOOM, Math.round((zoom + delta) * 10.0) / 10.0));
        updateLabels(width / 2 - Math.min(300, width - 40) / 2);
    }

    private void changeFps(int delta) {
        fps = Math.max(MapConfig.MIN_FPS, Math.min(MapConfig.MAX_FPS, fps + delta));
        updateLabels(width / 2 - Math.min(300, width - 40) / 2);
    }

    private void updateLabels(int left) {
        setLabel(zoomLabel, Component.translatable("halcyon_map.config.zoom", Math.round(zoom * 100)), left);
        setLabel(fpsLabel, Component.translatable("halcyon_map.config.fps", fps), left);
    }

    /** У StringWidget ширина задаётся вручную, поэтому подгоняем её под текст. */
    private void setLabel(StringWidget label, Component text, int left) {
        label.setMessage(text);
        label.setWidth(font.width(text));
        label.setX(left);
    }

    private void save() {
        MapConfig.apply(urlBox.getValue(), zoom, fps);
        onClose();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

}
