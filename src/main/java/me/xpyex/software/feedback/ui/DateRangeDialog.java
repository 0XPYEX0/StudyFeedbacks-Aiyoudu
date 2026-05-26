package me.xpyex.software.feedback.ui;

import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.tasks.StudentInfoCollector;
import me.xpyex.software.feedback.util.TimeUtil;

/**
 * 日期范围选择对话框 - 用于选择采集任务的日期范围
 */
public class DateRangeDialog extends JDialog {
    private JTextField txtStartDate;
    private JTextField txtEndDate;

    private DateRangeDialog(JFrame parent, List<StudentInfo> selectedStudents) {
        super(parent, "选择采集日期范围", true);
        setSize(400, 200);
        setLocationRelativeTo(parent);

        initUI(selectedStudents);
    }

    /**
     * 显示日期范围选择对话框并执行采集任务
     *
     * @param parent           父窗口
     * @param selectedStudents 选中的学生列表
     */
    public static void showAndCollect(JFrame parent, List<StudentInfo> selectedStudents) {
        DateRangeDialog dialog = new DateRangeDialog(parent, selectedStudents);
        dialog.setVisible(true);
    }

    private void initUI(List<StudentInfo> selectedStudents) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        // 开始日期
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("开始日期 (yyyy-MM-dd):"), gbc);

        gbc.gridx = 1;
        txtStartDate = new JTextField(15);
        panel.add(txtStartDate, gbc);

        // 结束日期
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("结束日期 (yyyy-MM-dd):"), gbc);

        gbc.gridx = 1;
        txtEndDate = new JTextField(15);
        panel.add(txtEndDate, gbc);

        // 提示
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        JLabel hintLabel = new JLabel("留空则使用上周（上周一到周日）");
        hintLabel.setFont(new Font("微软雅黑", Font.ITALIC, 11));
        hintLabel.setForeground(Color.GRAY);
        panel.add(hintLabel, gbc);

        // 按钮面板
        JPanel buttonPanel = createButtonPanel(selectedStudents);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(buttonPanel, gbc);

        add(panel);
    }

    /**
     * 创建按钮面板
     */
    private JPanel createButtonPanel(List<StudentInfo> selectedStudents) {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton btnOK = new JButton("确定");
        JButton btnCancel = new JButton("取消");

        btnOK.addActionListener(ev -> {
            String startDate = txtStartDate.getText().trim();
            String endDate = txtEndDate.getText().trim();

            if (startDate.isEmpty()) {
                // 使用默认上周日期
                String[] lastWeekRange = TimeUtil.getLastWeekRange();
                StudentInfoCollector.setStart(lastWeekRange[0]);
                StudentInfoCollector.setEnd(lastWeekRange[1]);
            } else {
                StudentInfoCollector.setStart(startDate);
                StudentInfoCollector.setEnd(endDate);
            }

            dispose();

            // 设置选中的学生到采集器
            StudentInfoCollector.setSelectedStudents(selectedStudents);

            // 执行采集任务
            me.xpyex.software.feedback.util.TaskExecutor.executeTask(
                StudentInfoCollector::start,
                "StudentInfoCollector-Thread"
            );
        });

        btnCancel.addActionListener(ev -> dispose());

        buttonPanel.add(btnOK);
        buttonPanel.add(btnCancel);

        return buttonPanel;
    }
}
