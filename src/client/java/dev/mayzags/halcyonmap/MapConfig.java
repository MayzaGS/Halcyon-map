package dev.mayzags.halcyonmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** Конфиг: config/halcyon_map.json, поля "url", "zoom" и "fps". Также настраивается через Mod Menu. */
public final class MapConfig {

    /** Карта сервера Halcyon: адрес по умолчанию. */
    public static final String DEFAULT_URL = "http://map.halcyon.su/";
    public static final double MIN_ZOOM = 0.5;
    public static final double MAX_ZOOM = 3.0;
    public static final int MIN_FPS = 1;
    public static final int MAX_FPS = 120;

    private static final Logger LOGGER = LoggerFactory.getLogger(HalcyonMapClient.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static String url = DEFAULT_URL;
    private static double zoom = 1.0;
    private static int fps = 60;

    private MapConfig() {
    }

    /** Адрес открываемой карты. */
    public static String url() {
        return url;
    }

    /** 1.0 = как обычный браузер на 100%; больше = крупнее, меньше = мельче. */
    public static double zoom() {
        return zoom;
    }

    /** Частота кадров страницы (по умолчанию у Chromium 30, здесь 60). */
    public static int fps() {
        return fps;
    }

    public static void setZoom(double value) {
        zoom = clampZoom(value);
        save();
    }

    /** Применяет все настройки разом (экран настроек) и сохраняет файл один раз. */
    public static void apply(String newUrl, double newZoom, int newFps) {
        url = normalizeUrl(newUrl);
        zoom = clampZoom(newZoom);
        fps = clampFps(newFps);
        save();
    }

    /**
     * Приводит введённый адрес к рабочему виду: пустой, а также любая схема кроме http/https
     * заменяются адресом по умолчанию; если схемы нет, добавляется https://.
     */
    public static String normalizeUrl(String value) {
        if (value == null) {
            return DEFAULT_URL;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_URL;
        }
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.contains("://")) {
            return DEFAULT_URL;
        }
        return "https://" + trimmed;
    }

    private static double clampZoom(double value) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, value));
    }

    private static int clampFps(int value) {
        return Math.max(MIN_FPS, Math.min(MAX_FPS, value));
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("halcyon_map.json");
    }

    public static void load() {
        Path file = file();
        try {
            if (Files.exists(file)) {
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if (json.has("url")) {
                    url = normalizeUrl(json.get("url").getAsString());
                }
                if (json.has("zoom")) {
                    zoom = clampZoom(json.get("zoom").getAsDouble());
                }
                if (json.has("fps")) {
                    fps = clampFps(json.get("fps").getAsInt());
                }
            }
            // Перезаписываем файл, чтобы в нём были все поля и корректные значения.
            save();
        } catch (Exception e) {
            LOGGER.warn("Could not read {}, using defaults", file, e);
        }
    }

    private static void save() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("url", url);
            json.addProperty("zoom", zoom);
            json.addProperty("fps", fps);
            Files.writeString(file(), GSON.toJson(json));
        } catch (Exception e) {
            LOGGER.warn("Could not save config", e);
        }
    }

}
