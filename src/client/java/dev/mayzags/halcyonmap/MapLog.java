package dev.mayzags.halcyonmap;

import net.dimaskama.mcef.api.MCEFApi;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Собирает отчёт об ошибке карты: события мода и выдержку из latest.log, относящуюся только к моду и
 * браузеру (Chromium). Перед копированием из текста автоматически убираются ники, пути к папкам
 * пользователя, IP-адреса, UUID, e-mail и токены.
 */
public final class MapLog {

    private static final int MAX_EVENTS = 200;
    private static final int MAX_LOG_LINES = 300;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Deque<String> EVENTS = new ArrayDeque<>();

    // Строки лога, которые относятся к моду, браузеру и видеокарте.
    private static final Pattern LOG_KEYWORDS = Pattern.compile(
            "(?i)halcyon|mcef|jcef|\\bcef\\b|chromium|me_friwi|graphics (device|backend)|Exception in thread|AWT-EventQueue");
    // Продолжение предыдущей записи: строки трассировки стека и заголовки исключений.
    private static final Pattern LOG_CONTINUATION = Pattern.compile(
            "^\\s+(at |\\.\\.\\. \\d+ more|Suppressed:)|^Caused by:|^[\\w.$]+(Exception|Error)(:|$)");

    private static final Pattern USER_PATH = Pattern.compile("(?i)([A-Z]:[\\\\/]Users[\\\\/]|/Users/|/home/)[^\\\\/\\s]+");
    private static final Pattern IPV4 = Pattern.compile("(?<![\\w.+-])(?:\\d{1,3}\\.){3}\\d{1,3}(?![\\w-])");
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(access[_-]?token|session[_-]?id|token|password|secret)\\s*[=:]\\s*\\S+");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+\\S+");
    private static final Pattern SETTING_USER = Pattern.compile("(?i)(Setting user:)\\s*\\S+");

    private MapLog() {
    }

    public static synchronized void event(String message) {
        EVENTS.addLast(LocalTime.now().format(TIME) + " " + message);
        while (EVENTS.size() > MAX_EVENTS) {
            EVENTS.removeFirst();
        }
    }

    public static synchronized void event(String message, Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        event(message + "\n" + writer);
    }

    /** Полный текст отчёта, уже очищенный от личных данных. */
    public static synchronized String buildReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Halcyon Map: отчёт об ошибке ===\n");
        sb.append("Мод: ").append(modVersion(HalcyonMapClient.MOD_ID)).append('\n');
        sb.append("Minecraft: ").append(modVersion("minecraft")).append('\n');
        sb.append("Fabric Loader: ").append(modVersion("fabricloader")).append('\n');
        sb.append("MCEF Modern: ").append(modVersion("mcef-modern")).append('\n');
        sb.append("Java: ").append(System.getProperty("java.version")).append(" (")
                .append(System.getProperty("java.vendor")).append(")\n");
        sb.append("ОС: ").append(System.getProperty("os.name")).append(' ')
                .append(System.getProperty("os.arch")).append('\n');
        sb.append("Настройки: zoom=").append(MapConfig.zoom()).append(", fps=").append(MapConfig.fps()).append('\n');
        sb.append("Состояние MCEF: ").append(mcefState()).append("\n\n");

        sb.append("=== События мода ===\n");
        if (EVENTS.isEmpty()) {
            sb.append("(пусто)\n");
        }
        for (String event : EVENTS) {
            sb.append(event).append('\n');
        }

        sb.append("\n=== Выдержка из latest.log (только мод и браузер) ===\n");
        List<String> excerpt = logExcerpt();
        if (excerpt.isEmpty()) {
            sb.append("(пусто)\n");
        }
        for (String line : excerpt) {
            sb.append(line).append('\n');
        }

        return sanitize(sb.toString());
    }

    /** Кладёт текст в буфер обмена. Возвращает false, если не получилось. */
    public static boolean copyToClipboard(Minecraft minecraft, String text) {
        // Способ игры (тот же, что у текстовых полей). Через рефлексию, чтобы не зависеть от внутренних названий.
        try {
            Object keyboardHandler = Minecraft.class.getField("keyboardHandler").get(minecraft);
            keyboardHandler.getClass().getMethod("setClipboard", String.class).invoke(keyboardHandler, text);
            return true;
        } catch (Throwable ignored) {
            // пробуем запасной вариант
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            return true;
        } catch (Throwable t) {
            event("Не удалось скопировать в буфер обмена", t);
            return false;
        }
    }

    private static String modVersion(String id) {
        return FabricLoader.getInstance().getModContainer(id)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("не найден");
    }

    private static String mcefState() {
        try {
            MCEFApi.Initialization initialization = MCEFApi.initialize();
            float percent = initialization.getPercentage();
            return initialization.getStage() + (percent >= 0 ? " " + Math.round(percent) + "%" : "")
                    + (initialization.getFuture().isCompletedExceptionally() ? " (завершилась ошибкой)" : "");
        } catch (Throwable t) {
            return "неизвестно (" + t.getClass().getSimpleName() + ")";
        }
    }

    private static List<String> logExcerpt() {
        List<String> result = new ArrayList<>();
        try {
            Path log = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("latest.log");
            if (!Files.exists(log)) {
                return result;
            }
            String[] lines = new String(Files.readAllBytes(log), StandardCharsets.UTF_8).split("\\R");
            boolean include = false;
            for (String line : lines) {
                if (LOG_KEYWORDS.matcher(line).find()) {
                    include = true;
                } else if (!(include && LOG_CONTINUATION.matcher(line).find())) {
                    include = false;
                }
                if (include) {
                    result.add(line);
                }
            }
        } catch (Throwable t) {
            result.add("Не удалось прочитать latest.log: " + t.getClass().getSimpleName());
        }
        if (result.size() > MAX_LOG_LINES) {
            return new ArrayList<>(result.subList(result.size() - MAX_LOG_LINES, result.size()));
        }
        return result;
    }

    static String sanitize(String text) {
        String home = System.getProperty("user.home");
        text = replaceLiteral(text, home, "<home>");
        if (home != null) {
            text = replaceLiteral(text, home.replace('\\', '/'), "<home>");
        }
        text = replaceLiteral(text, System.getProperty("user.name"), "<user>");
        text = replaceLiteral(text, playerName(), "<player>");
        text = SETTING_USER.matcher(text).replaceAll("$1 <player>");
        text = USER_PATH.matcher(text).replaceAll("$1<user>");
        text = SECRET.matcher(text).replaceAll("$1=<redacted>");
        text = BEARER.matcher(text).replaceAll("Bearer <redacted>");
        text = EMAIL.matcher(text).replaceAll("<email>");
        text = UUID.matcher(text).replaceAll("<uuid>");
        text = IPV4.matcher(text).replaceAll("<ip>");
        return text;
    }

    private static String replaceLiteral(String text, String value, String replacement) {
        if (value == null || value.length() < 3) {
            return text;
        }
        return text.replace(value, replacement);
    }

    /** Ник игрока (через рефлексию; если не получилось, ник просто не вырезается отдельно). */
    private static String playerName() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            Object user = Minecraft.class.getMethod("getUser").invoke(minecraft);
            return (String) user.getClass().getMethod("getName").invoke(user);
        } catch (Throwable t) {
            return null;
        }
    }

}
