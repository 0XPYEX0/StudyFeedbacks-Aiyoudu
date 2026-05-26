package me.xpyex.software.feedback.util;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.Getter;
import me.xpyex.software.feedback.Main;

/**
 * 任务执行器 - 统一管理CLI和GUI的任务执行
 * 支持线程中断停止功能
 */
public class TaskExecutor {
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    @Getter
    private static Thread currentTaskThread = null;
    // GUI相关（可选）
    private static JFrame guiFrame = null;
    private static JLabel statusLabel = null;
    private static JButton[] guiButtons = null;

    /**
     * 初始化GUI组件（仅在GUI模式下调用）
     */
    public static void initGUI(JFrame frame, JLabel status, JButton... buttons) {
        guiFrame = frame;
        statusLabel = status;
        guiButtons = buttons;
    }

    /**
     * 在独立线程中执行任务（自动适配CLI/GUI模式）
     *
     * @param task       要执行的任务
     * @param threadName 线程名称
     * @return 如果任务无法启动（已有任务运行），返回错误信息；成功启动返回null
     */
    public static String executeTask(Runnable task, String threadName) {
        if (isRunning.get()) {
            String errorMsg = "已有任务正在运行，请先停止当前任务！";
            if (guiFrame != null) {
                JOptionPane.showMessageDialog(guiFrame, errorMsg, "提示", JOptionPane.WARNING_MESSAGE);
            } else {
                System.out.println(errorMsg);
            }
            return errorMsg; // 返回失败原因
        }

        isRunning.set(true);
        updateStatus("运行中: " + threadName);

        // GUI模式下禁用按钮
        if (guiFrame != null) {
            setButtonsEnabled(false);
        }

        currentTaskThread = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                if (e instanceof InterruptedException || Thread.currentThread().isInterrupted()) {
                    log("任务被中断");
                } else {
                    logError("任务执行出错: " + e.getMessage());
                    e.printStackTrace();
                }
            } finally {
                isRunning.set(false);
                currentTaskThread = null;

                if (guiFrame != null) {
                    // GUI模式：更新UI
                    SwingUtilities.invokeLater(() -> {
                        updateStatus("就绪");
                        setButtonsEnabled(true);
                    });
                } else {
                    // CLI模式：显示帮助
                    updateStatus("就绪");
                    Main.help();
                }
            }
        }, threadName);

        currentTaskThread.start();
        return null; // 成功启动返回null
    }

    /**
     * 停止当前任务
     */
    public static void stopCurrentTask() {
        if (currentTaskThread != null && currentTaskThread.isAlive()) {
            log("正在停止当前任务...");
            currentTaskThread.interrupt();
            isRunning.set(false);
            currentTaskThread = null;
            updateStatus("已停止");
            setButtonsEnabled(true);
            log("任务已停止");
        } else {
            log("当前没有正在运行的任务");
        }
    }

    /**
     * 检查是否有任务正在运行
     */
    public static boolean isRunning() {
        return isRunning.get();
    }

    /**
     * 更新状态（自动适配CLI/GUI）
     */
    private static void updateStatus(String status) {
        if (guiFrame != null && statusLabel != null) {
            SwingUtilities.invokeLater(() -> statusLabel.setText(status));
        } else {
            // CLI模式不需要状态栏
        }
    }

    /**
     * 设置按钮启用状态（仅GUI模式）
     */
    private static void setButtonsEnabled(boolean enabled) {
        if (guiButtons != null) {
            for (JButton button : guiButtons) {
                if (button != null) {
                    button.setEnabled(enabled);
                }
            }
        }
    }

    /**
     * 记录日志（自动适配CLI/GUI）
     */
    public static void log(String message) {
        if (guiFrame != null) {
            // GUI模式下由MainWindow处理
            SwingUtilities.invokeLater(() -> {
                String timestamp = TimeUtil.parseDate(new Date(), "HH:mm:ss");
                // 这里需要调用MainWindow的log方法，但由于循环依赖，我们直接输出
                System.out.println("[" + timestamp + "] " + message);
            });
        } else {
            System.out.println(message);
        }
    }

    /**
     * 记录错误日志（自动适配CLI/GUI）
     */
    public static void logError(String message) {
        if (guiFrame != null) {
            SwingUtilities.invokeLater(() -> {
                String timestamp = TimeUtil.parseDate(new Date(), "HH:mm:ss");
                System.err.println("[" + timestamp + "] [错误] " + message);
            });
        } else {
            System.err.println("[错误] " + message);
        }
    }

}
