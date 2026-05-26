package me.xpyex.software.feedback.ui;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 控制台重定向器 - 将 System.out 和 System.err 重定向到 GUI 日志区域
 */
public class ConsoleRedirector {

    /**
     * 重定向标准输出和错误输出到指定的日志回调
     *
     * @param callback 日志回调接口
     */
    public static void redirect(LogCallback callback) {
        // 保存原始的 System.out 和 System.err（用于 SLF4J）
        final PrintStream originalOut = System.out;
        final PrintStream originalErr = System.err;

        // 重定向标准输出（使用 UTF-8 编码）
        System.setOut(new PrintStream(new OutputStream() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public void write(int b) {
                if (b == '\n') {
                    flushBuffer(callback, false);
                } else if (b == '\r') {
                    // 忽略回车符
                } else {
                    buffer.write(b);
                }
            }

            @Override
            public void flush() {
                flushBuffer(callback, false);
            }

            private void flushBuffer(LogCallback cb, boolean isError) {
                try {
                    String message = buffer.toString(StandardCharsets.UTF_8.name()).trim();
                    if (!message.isEmpty()) {
                        if (isError) {
                            cb.logError(message);
                        } else {
                            cb.log(message);
                        }
                    }
                    buffer.reset();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, true, StandardCharsets.UTF_8));

        // 重定向错误输出（使用 UTF-8 编码）
        System.setErr(new PrintStream(new OutputStream() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

            @Override
            public void write(int b) {
                if (b == '\n') {
                    flushBuffer(callback, true);
                } else if (b == '\r') {
                    // 忽略回车符
                } else {
                    buffer.write(b);
                }
            }

            @Override
            public void flush() {
                flushBuffer(callback, true);
            }

            private void flushBuffer(LogCallback cb, boolean isError) {
                try {
                    String message = buffer.toString(StandardCharsets.UTF_8.name()).trim();
                    if (!message.isEmpty()) {
                        if (isError) {
                            cb.logError(message);
                        } else {
                            cb.log(message);
                        }
                    }
                    buffer.reset();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, true, StandardCharsets.UTF_8));
    }

    /**
     * 日志接口 - 用于接收重定向的输出
     */
    public interface LogCallback {
        void log(String message);

        void logError(String message);
    }
}
