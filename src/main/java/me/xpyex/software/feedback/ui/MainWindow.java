package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import me.xpyex.software.feedback.tasks.DeepSeekAnalyzer;
import me.xpyex.software.feedback.tasks.PrintStudentStudy;
import me.xpyex.software.feedback.tasks.StudentInfoCollector;
import me.xpyex.software.feedback.tasks.StudentReader;
import me.xpyex.software.feedback.tasks.TokenGetter;
import me.xpyex.software.feedback.util.NetworkUtil;
import me.xpyex.software.feedback.util.TaskExecutor;
import me.xpyex.software.feedback.util.TimeUtil;

/**
 * 主窗口类 - 提供图形化界面
 */
public class MainWindow extends JFrame {
    private JTextArea logArea;
    private JButton btnGetToken;
    private JButton btnReadStudents;
    private JButton btnCollectProfiles;
    private JButton btnFeedback;
    private JButton btnPrintStudy;
    private JButton btnStop;
    private JLabel statusLabel;

    public MainWindow() {
        initUI();
    }

    /**
     * 显示主窗口
     */
    public static void showMainWindow() {
        SwingUtilities.invokeLater(() -> {
            try {
                // 设置系统外观
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }

            MainWindow window = new MainWindow();
            window.log("欢迎使用爱优读学生反馈系统");
            window.log("请点击上方按钮执行相应操作");

            // 重定向标准输出到GUI日志区域
            ConsoleRedirector.redirect(new ConsoleRedirector.LogCallback() {
                @Override
                public void log(String message) {
                    window.log(message);
                }

                @Override
                public void logError(String message) {
                    window.logError(message);
                }
            });

            window.setVisible(true);
        });
    }

    private void initUI() {
        setTitle("爱优读学生反馈系统");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // 居中显示

        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 顶部控制面板
        JPanel controlPanel = createControlPanel();
        mainPanel.add(controlPanel, BorderLayout.NORTH);

        // 中间日志区域
        JScrollPane scrollPane = new JScrollPane();
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        scrollPane.setViewportView(logArea);
        // 设置滚动速度
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        scrollPane.getHorizontalScrollBar().setUnitIncrement(20);
        scrollPane.getVerticalScrollBar().setBlockIncrement(100);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 底部状态栏
        statusLabel = new JLabel("就绪");
        statusLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        add(mainPanel);

        // 窗口关闭时清理资源
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                TaskExecutor.stopCurrentTask();
                NetworkUtil.killEdgeDriver();
            }
        });
    }

    /**
     * 创建控制面板
     */
    private JPanel createControlPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("操作面板"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 第一行：主要功能按钮
        gbc.gridx = 0;
        gbc.gridy = 0;
        btnGetToken = createButton("获取Token", "启动浏览器并登录以获取Token");
        panel.add(btnGetToken, gbc);

        gbc.gridx = 1;
        btnReadStudents = createButton("读取学生", "读取学生信息并保存到内存");
        panel.add(btnReadStudents, gbc);

        gbc.gridx = 2;
        btnCollectProfiles = createButton("采集档案", "收集学生详细信息");
        panel.add(btnCollectProfiles, gbc);

        gbc.gridx = 3;
        btnFeedback = createButton("AI点评", "使用AI生成学生点评");
        panel.add(btnFeedback, gbc);

        gbc.gridx = 4;
        btnPrintStudy = createButton("打印学案", "批量打印学生学案");
        panel.add(btnPrintStudy, gbc);

        // 第二行：停止按钮和说明
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 5;
        btnStop = createButton("停止任务", "立即停止当前正在运行的任务");
        btnStop.setBackground(new Color(255, 100, 100));
        btnStop.setForeground(Color.BLACK);
        panel.add(btnStop, gbc);

        // 绑定事件监听器
        bindActionListeners();

        return panel;
    }

    /**
     * 创建按钮
     */
    private JButton createButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(140, 35));
        return button;
    }

    /**
     * 绑定按钮事件
     */
    private void bindActionListeners() {
        btnGetToken.addActionListener(e -> {
            String error = TaskExecutor.executeTask(() -> {
                log("正在启动浏览器，请手动登录...");
                log("如果没有安装 Edge 浏览器，请安装，因为使用了 EdgeDriver");
                TimeUtil.sleep(3000);
                TokenGetter.start();
            }, "TokenGetter-Thread");
            // 如果返回错误信息，executeTask已经显示了提示框
        });

        btnReadStudents.addActionListener(e -> {
            String error = TaskExecutor.executeTask(StudentReader::start, "StudentReader-Thread");
            // 如果返回错误信息，executeTask已经显示了提示框
        });

        btnCollectProfiles.addActionListener(e -> {
            if (!StudentReader.hasStudents()) {
                JOptionPane.showMessageDialog(this,
                    "请先执行【读取学生】操作！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            StudentProfileDialog.showDialog(this, (students, dateRangeMap) -> {
                log("开始采集学生档案...");

                // 设置选中的学生到采集器
                me.xpyex.software.feedback.tasks.StudentInfoCollector.setSelectedStudents(students);

                // 设置日期范围（使用第一个学生的日期，因为批量设置时所有学生日期相同）
                if (!dateRangeMap.isEmpty()) {
                    // 获取任意一个学生的日期范围
                    String[] firstDateRange = dateRangeMap.values().iterator().next();
                    StudentInfoCollector.setStart(firstDateRange[0]);
                    StudentInfoCollector.setEnd(firstDateRange[1]);
                    log(String.format("采集日期范围：%s 至 %s", firstDateRange[0], firstDateRange[1]));
                }

                // 执行采集任务
                String error = TaskExecutor.executeTask(
                    StudentInfoCollector::start,
                    "StudentInfoCollector-Thread"
                );
                // 如果返回错误信息，executeTask已经显示了提示框
            });
        });

        btnFeedback.addActionListener(e -> {
            if (!StudentReader.hasStudents()) {
                JOptionPane.showMessageDialog(this,
                    "请先执行【读取学生】操作！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            SimpleStudentSelectionDialog.showDialog(this, "AI点评", selectedStudents -> {
                log("交由 AI 处理数据...");
                // 使用 TaskExecutor 异步执行，避免阻塞 UI
                String error = TaskExecutor.executeTask(
                    () -> DeepSeekAnalyzer.startWithStudents(selectedStudents),
                    "DeepSeekAnalyzer-Thread"
                );
                // 如果返回错误信息，executeTask已经显示了提示框
            });
        });

        btnPrintStudy.addActionListener(e -> {
            if (!StudentReader.hasStudents()) {
                JOptionPane.showMessageDialog(this,
                    "请先执行【读取学生】操作！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            StudentPrintDialog.showDialog(this, selectedStudents -> {
                log("开始批量处理学生...");
                // 使用 TaskExecutor 异步执行，避免阻塞 UI
                String error = TaskExecutor.executeTask(
                    () -> PrintStudentStudy.startWithStudents(selectedStudents),
                    "PrintStudentStudy-Thread"
                );
                // 如果返回错误信息，executeTask已经显示了提示框
            });
        });

        btnStop.addActionListener(e -> TaskExecutor.stopCurrentTask());
    }

    /**
     * 记录日志
     */
    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = TimeUtil.parseDate(new Date(), "HH:mm:ss");
            logArea.append("[" + timestamp + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    /**
     * 记录错误日志
     */
    private void logError(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = TimeUtil.parseDate(new Date(), "HH:mm:ss");
            logArea.append("[" + timestamp + "] [错误] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}
