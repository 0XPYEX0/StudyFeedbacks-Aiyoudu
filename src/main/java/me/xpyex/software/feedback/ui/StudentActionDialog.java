package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.tasks.feedback.DeepSeekAnalyzer;
import me.xpyex.software.feedback.tasks.feedback.StudentInfoCollector;
import me.xpyex.software.feedback.util.TaskExecutor;

/**
 * 反馈相关弹窗（单名学生）。
 * <p>
 * 需求明言反馈细分功能待定，此处先预留位置，并提供该学生的两个基础动作：
 * 「采集档案(AI素材)」「生成AI反馈」。后续可按需扩展。
 */
public class StudentActionDialog extends JDialog {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final StudentInfo student;
    /** 父 JFrame，用于继续打开其它弹窗 */
    private final JFrame owner;

    private StudentActionDialog(JFrame parent, StudentInfo student) {
        super(parent, "反馈相关 - " + student.getRealName(), true);
        this.student = student;
        this.owner = parent;
        initUI();
        pack();
        setLocationRelativeTo(parent);
    }

    public static void showDialog(JFrame parent, StudentInfo student) {
        new StudentActionDialog(parent, student).setVisible(true);
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel hint = new JLabel("<html>反馈细化功能待扩展（已预留位置）。<br>目前支持对当前学生执行以下动作：</html>");
        hint.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        main.add(hint, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.gridx = 0;
        gbc.gridy = 0;
        JButton btnCollect = new JButton("采集该生档案(AI素材)");
        JButton btnFeedback = new JButton("生成该生AI反馈");
        JButton btnHistory = new JButton("填入历史反馈");
        java.awt.Dimension btnSize = new java.awt.Dimension(220, 36);
        btnCollect.setPreferredSize(btnSize);
        btnFeedback.setPreferredSize(btnSize);
        btnHistory.setPreferredSize(btnSize);

        gbc.gridy = 0;
        center.add(btnCollect, gbc);
        gbc.gridy = 1;
        center.add(btnFeedback, gbc);
        gbc.gridy = 2;
        center.add(btnHistory, gbc);
        main.add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dispose());
        bottom.add(close);
        main.add(bottom, BorderLayout.SOUTH);

        add(main);

        btnCollect.addActionListener(e -> collectOne());
        btnFeedback.addActionListener(e -> {
            dispose();
            TaskExecutor.executeTask(() -> DeepSeekAnalyzer.startWithStudents(java.util.List.of(student)),
                "DeepSeekAnalyzer-Thread");
        });
        btnHistory.addActionListener(e -> FeedbackHistoryDialog.showDialog(owner, student));
    }

    /** 为单个学生采集档案：先询问日期范围（默认上周） */
    private void collectOne() {
        JTextField startField = new JTextField(LocalDate.now().minusWeeks(1).with(DayOfWeek.MONDAY).format(FORMAT), 10);
        JTextField endField = new JTextField(LocalDate.now().minusWeeks(1).with(DayOfWeek.SUNDAY).format(FORMAT), 10);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel.add(new JLabel("开始日期："));
        panel.add(startField);
        panel.add(new JLabel("至"));
        panel.add(endField);

        int option = JOptionPane.showConfirmDialog(this,
            new Object[]{"请输入采集日期范围（yyyy-MM-dd）：", panel}, "采集该生档案",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) return;

        String start = startField.getText().trim();
        String end = endField.getText().trim();
        try {
            LocalDate.parse(start, FORMAT);
            LocalDate.parse(end, FORMAT);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "日期格式错误！请使用 yyyy-MM-dd", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        StudentInfoCollector.setSelectedStudents(java.util.List.of(student));
        StudentInfoCollector.setStart(start);
        StudentInfoCollector.setEnd(end);
        dispose();
        TaskExecutor.executeTask(StudentInfoCollector::start, "StudentInfoCollector-Thread");
    }
}
