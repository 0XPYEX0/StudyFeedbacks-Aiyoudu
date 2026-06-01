package me.xpyex.software.feedback.util;

import java.util.WeakHashMap;
import javax.swing.JOptionPane;
import me.xpyex.software.feedback.ui.MainWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogUtil {
    private static final WeakHashMap<String, Logger> LOGGER_MAP = new WeakHashMap<>();
    private static final int lineLength = 40;

    public static Logger getLogger() {
        String name = getCallerName();
        if (!LOGGER_MAP.containsKey(name)) {
            LOGGER_MAP.put(name, LoggerFactory.getLogger(name));
        }
        return LOGGER_MAP.get(name);
    }

    private static String getCallerName() {
        // 获取调用者的类名
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        // stackTrace[0] = getStackTrace, stackTrace[1] = getLogger, stackTrace[2] = 实际调用者
        String name = "Unknown";
        if (stackTrace.length > 2) {
            for (int i = 2; i < stackTrace.length; i++) {
                if (stackTrace[i].getClassName().equals(LogUtil.class.getName())) {
                    continue;
                }
                String className = stackTrace[i].getClassName();
                // 提取简单类名(去掉包名)
                int lastDot = className.lastIndexOf('.');
                name = lastDot > 0 ? className.substring(lastDot + 1) : className;
                break;
            }
        }
        return name;
    }

    public static void line() {
        line("=", lineLength);
    }

    public static void line(String sign, int length) {
        if (sign == null || sign.trim().isEmpty()) return;
        getLogger().info(sign.repeat(Math.max(0, length)));
    }

    public static void logNecessary(String msg) {
        if (MainWindow.current != null) {
            MainWindow.current.log("[" + getCallerName() + "] " + msg);
        } else {
            getLogger().info(msg);
        }
    }

    public static void warn(String msg) {
        if (MainWindow.current != null) {
            JOptionPane.showMessageDialog(MainWindow.current, msg, "警告: " + getCallerName(), JOptionPane.WARNING_MESSAGE);
        } else {
            getLogger().warn(msg);
        }
    }
}
