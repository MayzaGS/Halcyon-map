package dev.mayzags.halcyonmap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.dimaskama.mcef.api.MCEFApi;
import net.dimaskama.mcef.api.MCEFBrowser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Карта сервера на весь экран. Закрывается по Esc или кнопкой со стрелкой влево. */
public class MapScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(HalcyonMapClient.MOD_ID);
    private static final double ZOOM_STEP = 0.1;
    // В Chromium коэффициент масштаба = 1.2 ^ уровень.
    private static final double LN_1_2 = Math.log(1.2);

    private final Screen parent;
    private MCEFBrowser browser;
    private boolean failed;
    private boolean settingsApplied;
    private StringWidget statusLabel;
    private StringWidget zoomLabel;

    // Строка «Копировать логи мода для отчета об ошибке» под сообщением об ошибке.
    private StringWidget reportLink;
    private StringWidget reportRest;
    private boolean errorShown;
    private boolean copied;
    private boolean timedOut;
    private long browserCreatedAt;
    private String lastLoggedStage = "";

    // Во сколько раз размер страницы в пикселях больше размера экрана в GUI-единицах (= «масштаб интерфейса»).
    private double scaleX = 1.0;
    private double scaleY = 1.0;

    public MapScreen(Screen parent) {
        super(Component.translatable("halcyon_map.button.map"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        statusLabel = addRenderableWidget(new StringWidget(width / 2 - 200, height / 2 - 4, 400, 9, Component.empty(), font));

        // «Назад», затем «−», подпись с процентами и «+». Обычные кнопки, стиль берётся из ресурспака.
        addRenderableWidget(Button.builder(Component.literal("\u2190"), button -> onClose())
                .bounds(6, 6, 20, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("-"), button -> changeZoom(-ZOOM_STEP))
                .bounds(32, 6, 20, 20)
                .build());
        zoomLabel = addRenderableWidget(new StringWidget(54, 12, 34, 9, zoomText(), font));
        addRenderableWidget(Button.builder(Component.literal("+"), button -> changeZoom(ZOOM_STEP))
                .bounds(90, 6, 20, 20)
                .build());

        reportLink = addRenderableWidget(new StringWidget(0, height / 2 + 8, 10, 9, linkText(), font));
        reportRest = addRenderableWidget(new StringWidget(0, height / 2 + 8, 10, 9,
                Component.translatable("halcyon_map.report.rest"), font));
        reportLink.visible = false;
        reportRest.visible = false;
        if (errorShown) {
            showReportRow();
        }

        tryCreateBrowser();
        applyViewport();
    }

    /** Создаёт браузер, как только MCEF закончил подготовку (первый запуск качает Chromium). */
    private void tryCreateBrowser() {
        if (browser != null || failed) {
            return;
        }
        try {
            MCEFApi.Initialization initialization = MCEFApi.initialize();
            if (initialization.getFuture().isCompletedExceptionally()) {
                Throwable cause = null;
                try {
                    cause = initialization.getFuture().exceptionNow();
                } catch (Throwable ignored) {
                    // отменено или не завершено, причины нет
                }
                fail(cause);
                return;
            }
            if (!initialization.isDone()) {
                float percent = initialization.getPercentage();
                String stage = initialization.getStage().name();
                if (!stage.equals(lastLoggedStage)) {
                    lastLoggedStage = stage;
                    MapLog.event("Подготовка MCEF: этап " + stage);
                }
                setStatus(Component.translatable(
                        "halcyon_map.screen.init",
                        percent >= 0 ? stage + " " + Math.round(percent) + "%" : stage
                ));
                return;
            }
            browser = MCEFApi.getInstanceFuture().join().createBrowser(MapConfig.url(), false);
            browser.setFocus(true);
            browserCreatedAt = System.currentTimeMillis();
            MapLog.event("Браузер создан");
            setStatus(Component.translatable("halcyon_map.screen.waiting"));
            applyViewport();
        } catch (Throwable t) {
            fail(t);
        }
    }

    /** Меняет надпись по центру экрана (у StringWidget ширина и позиция задаются вручную). */
    private void setStatus(Component message) {
        statusLabel.setMessage(message);
        int textWidth = font.width(message);
        statusLabel.setWidth(textWidth);
        statusLabel.setX(width / 2 - textWidth / 2);
    }

    private void fail(Throwable t) {
        failed = true;
        if (t != null) {
            LOGGER.error("Failed to create browser", t);
            MapLog.event("Не удалось запустить браузер", t);
        } else {
            MapLog.event("Не удалось запустить браузер (причина неизвестна)");
        }
        setStatus(Component.translatable("halcyon_map.screen.failed"));
        showReportRow();
    }

    private Component linkText() {
        if (copied) {
            return Component.translatable("halcyon_map.report.copied").withStyle(ChatFormatting.GREEN);
        }
        return Component.translatable("halcyon_map.report.copy").withStyle(ChatFormatting.UNDERLINE);
    }

    private void showReportRow() {
        errorShown = true;
        reportLink.visible = true;
        reportRest.visible = true;
        layoutReportRow();
    }

    private void hideReportRow() {
        errorShown = false;
        reportLink.visible = false;
        reportRest.visible = false;
    }

    /** Ставит «Копировать» и остальной текст в одну строку по центру под сообщением об ошибке. */
    private void layoutReportRow() {
        reportLink.setMessage(linkText());
        int linkWidth = font.width(reportLink.getMessage());
        int restWidth = font.width(reportRest.getMessage());
        int left = width / 2 - (linkWidth + restWidth) / 2;
        int y = height / 2 + 8;
        reportLink.setWidth(linkWidth);
        reportLink.setPosition(left, y);
        reportRest.setWidth(restWidth);
        reportRest.setPosition(left + linkWidth, y);
    }

    private void copyReport() {
        boolean ok = MapLog.copyToClipboard(minecraft, MapLog.buildReport());
        copied = ok;
        if (ok) {
            layoutReportRow();
        } else {
            reportLink.setMessage(Component.translatable("halcyon_map.report.copy_failed").withStyle(ChatFormatting.RED));
        }
    }

    private Component zoomText() {
        return Component.literal(Math.round(MapConfig.zoom() * 100) + "%");
    }

    private void changeZoom(double delta) {
        MapConfig.setZoom(Math.round((MapConfig.zoom() + delta) * 10.0) / 10.0);
        if (zoomLabel != null) {
            zoomLabel.setMessage(zoomText());
        }
        if (settingsApplied) {
            applyBrowserSettings();
        }
    }

    /**
     * Страница всегда рисуется в реальном разрешении окна, поэтому остаётся чёткой при любом
     * «Масштабе интерфейса». Сам масштаб (zoom) применяется внутри Chromium.
     */
    private void applyViewport() {
        if (browser == null || width <= 0 || height <= 0) {
            return;
        }
        int pixelWidth = Math.max(1, minecraft.getWindow().getWidth());
        int pixelHeight = Math.max(1, minecraft.getWindow().getHeight());
        scaleX = pixelWidth / (double) width;
        scaleY = pixelHeight / (double) height;
        browser.resize(pixelWidth, pixelHeight);
    }

    /**
     * Зум Chromium и частота кадров. Вызывается, когда браузер уже отдаёт первый кадр.
     * Класс CefBrowser компилятору не виден (библиотека JCEF вложена в MCEF Modern), поэтому вызываем
     * его методы через рефлексию. В игре класс доступен.
     */
    private void applyBrowserSettings() {
        if (browser == null) {
            return;
        }
        try {
            Object cef = MCEFBrowser.class.getMethod("getCefBrowser").invoke(browser);
            Class<?> cefClass = Class.forName("org.cef.browser.CefBrowser");
            cefClass.getMethod("setZoomLevel", double.class).invoke(cef, Math.log(MapConfig.zoom()) / LN_1_2);
            cefClass.getMethod("setWindowlessFrameRate", int.class).invoke(cef, MapConfig.fps());
        } catch (Throwable t) {
            LOGGER.warn("Could not apply zoom/fps to the browser", t);
            MapLog.event("Не удалось применить зум и FPS", t);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Ванильный размытый фон не нужен: вместо него страница.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (browser == null) {
            tryCreateBrowser();
        }
        boolean showStatus = true;
        if (browser != null) {
            GpuTextureView view = browser.getTextureView();
            if (view == null && !timedOut && System.currentTimeMillis() - browserCreatedAt > 20_000) {
                timedOut = true;
                MapLog.event("Первый кадр страницы не получен за 20 секунд");
                setStatus(Component.translatable("halcyon_map.screen.timeout"));
                showReportRow();
            }
            if (view != null) {
                showStatus = false;
                if (timedOut) {
                    timedOut = false;
                    hideReportRow();
                }
                if (!settingsApplied) {
                    settingsApplied = true;
                    applyBrowserSettings();
                }
                graphics.guiRenderState.addGuiElement(new BlitRenderState(
                        RenderPipelines.GUI_TEXTURED,
                        TextureSetup.singleTexture(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)),
                        new Matrix3x2f(graphics.pose()),
                        0,
                        0,
                        width,
                        height,
                        0.0F,
                        1.0F,
                        0.0F,
                        1.0F,
                        0xFFFFFFFF,
                        graphics.scissorStack.peek()
                ));
            }
            graphics.requestCursor(browser.getCursorType());
        }
        statusLabel.visible = showStatus;
        // Кнопки рисуем поверх страницы.
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void removed() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
    }

    private MouseButtonEvent toBrowser(MouseButtonEvent event) {
        return new MouseButtonEvent(event.x() * scaleX, event.y() * scaleY, event.buttonInfo());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        // Ссылка «Копировать» в сообщении об ошибке.
        if (errorShown && reportLink.visible && reportLink.isMouseOver(event.x(), event.y())) {
            copyReport();
            return true;
        }
        // Сначала кнопки; если попали не в них, клик идёт на страницу.
        if (super.mouseClicked(event, doubled)) {
            return true;
        }
        if (browser != null) {
            browser.onMouseClicked(toBrowser(event), doubled);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (browser != null) {
            browser.onMouseReleased(toBrowser(event));
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (browser != null) {
            browser.onMouseScrolled((int) (mouseX * scaleX), (int) (mouseY * scaleY), verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseMoved(double x, double y) {
        if (browser != null) {
            browser.onMouseMoved((int) (x * scaleX), (int) (y * scaleY));
        }
        super.mouseMoved(x, y);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onClose();
            return true;
        }
        if (browser != null) {
            browser.onKeyPressed(event);
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (browser != null) {
            browser.onKeyReleased(event);
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (browser != null) {
            browser.onCharTyped(event);
        }
        return true;
    }

}
