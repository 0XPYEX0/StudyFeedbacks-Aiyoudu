package me.xpyex.software.feedback.ui;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import me.xpyex.software.feedback.packet.both.StudentInfo;

/**
 * 学生档案采集选择对话框
 * 布局：左边勾选框 + 中间学生信息 + 右边日期选择
 */
public class StudentProfileDialog extends BaseStudentSelectionDialog {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final Map<Integer, JTextField> startDateFields = new HashMap<>();  // 学生ID -> 开始日期
    private final Map<Integer, JTextField> endDateFields = new HashMap<>();    // 学生ID -> 结束日期
    private ProfileCallback callback;

    private StudentProfileDialog(JFrame parent, ProfileCallback callback) {
        super(parent, "采集档案");
        this.callback = callback;
        setSize(700, 600);
        initBaseUI();
    }

    @Override
    protected String getHintText() {
        return "请选择要采集档案的学生，并设置采集日期范围（开始日期 - 结束日期）：";
    }

    @Override
    protected void customizeScrollPane(JScrollPane scrollPane) {
        // 设置滚动速度
        scrollPane.getVerticalScrollBar().setUnitIncrement(25);
        scrollPane.getVerticalScrollBar().setBlockIncrement(120);
    }

    @Override
    protected void addExtraColumns(GridBagConstraints gbc, int row, StudentInfo student, JPanel studentPanel) {
        // 右侧：日期范围输入框（开始日期 - 结束日期）
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;

        // 开始日期
        String lastWeekStart = LocalDate.now().minusWeeks(1).with(java.time.DayOfWeek.MONDAY).format(DATE_FORMATTER);
        String lastWeekEnd = LocalDate.now().minusWeeks(1).with(java.time.DayOfWeek.SUNDAY).format(DATE_FORMATTER);

        JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JTextField startDateField = new JTextField(lastWeekStart, 10);
        startDateField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        startDateField.setToolTipText("开始日期：yyyy-MM-dd");

        JLabel dashLabel = new JLabel("至");
        dashLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));

        JTextField endDateField = new JTextField(lastWeekEnd, 10);
        endDateField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        endDateField.setToolTipText("结束日期：yyyy-MM-dd");

        datePanel.add(startDateField);
        datePanel.add(dashLabel);
        datePanel.add(endDateField);

        startDateFields.put(student.getStudentId(), startDateField);
        endDateFields.put(student.getStudentId(), endDateField);
        studentPanel.add(datePanel, gbc);
    }

    @Override
    protected JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton btnSelectAll = new JButton("全选");
        JButton btnDeselectAll = new JButton("取消全选");
        JButton btnBatchSetDate = new JButton("批量设置日期");
        JButton btnOK = new JButton("确定");
        JButton btnCancel = new JButton("取消");

        btnSelectAll.addActionListener(ev -> studentCheckBoxes.forEach(cb -> cb.setSelected(true)));
        btnDeselectAll.addActionListener(ev -> studentCheckBoxes.forEach(cb -> cb.setSelected(false)));
        btnBatchSetDate.addActionListener(ev -> showBatchSetDateDialog());

        btnOK.addActionListener(ev -> {
            List<StudentInfo> selectedStudents = getSelectedStudents();
            if (selectedStudents.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请至少选择一个学生！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            Map<Integer, String[]> dateRangeMap = new HashMap<>();
            for (StudentInfo student : selectedStudents) {
                JTextField startDateField = startDateFields.get(student.getStudentId());
                JTextField endDateField = endDateFields.get(student.getStudentId());
                if (startDateField != null && endDateField != null) {
                    String startDate = startDateField.getText().trim();
                    String endDate = endDateField.getText().trim();
                    if (!startDate.isEmpty() && !endDate.isEmpty()) {
                        dateRangeMap.put(student.getStudentId(), new String[]{startDate, endDate});
                    }
                }
            }

            dispose();
            if (callback != null) {
                callback.onSelected(selectedStudents, dateRangeMap);
            }
        });

        btnCancel.addActionListener(ev -> dispose());

        buttonPanel.add(btnSelectAll);
        buttonPanel.add(btnDeselectAll);
        buttonPanel.add(btnBatchSetDate);
        buttonPanel.add(btnOK);
        buttonPanel.add(btnCancel);

        return buttonPanel;
    }

    @Override
    protected String formatStudentInfo(StudentInfo student) {
        return String.format("%-10s [%s]", student.getRealName(), student.getGroup());
    }

    @Override
    protected void onOK(List<StudentInfo> selectedStudents) {
        // 由 createButtonPanel 中的自定义逻辑处理
    }

    private void showBatchSetDateDialog() {
        JTextField startInput = new JTextField(
            LocalDate.now().minusWeeks(1).with(java.time.DayOfWeek.MONDAY).format(DATE_FORMATTER), 10);
        JTextField endInput = new JTextField(
            LocalDate.now().minusWeeks(1).with(java.time.DayOfWeek.SUNDAY).format(DATE_FORMATTER), 10);

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        panel.add(new JLabel("开始日期："));
        panel.add(startInput);
        panel.add(new JLabel("至"));
        panel.add(endInput);

        Object[] message = {"请输入日期范围（格式：yyyy-MM-dd）：", panel};

        int option = JOptionPane.showConfirmDialog(this, message, "批量设置日期范围",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (option == JOptionPane.OK_OPTION) {
            String startStr = startInput.getText().trim();
            String endStr = endInput.getText().trim();
            try {
                LocalDate.parse(startStr, DATE_FORMATTER);
                LocalDate.parse(endStr, DATE_FORMATTER);

                int count = 0;
                for (int i = 0; i < studentCheckBoxes.size(); i++) {
                    if (studentCheckBoxes.get(i).isSelected()) {
                        Integer studentId = checkBoxToStudentId.get(studentCheckBoxes.get(i));
                        if (studentId != null) {
                            JTextField startDateField = startDateFields.get(studentId);
                            JTextField endDateField = endDateFields.get(studentId);
                            if (startDateField != null && endDateField != null) {
                                startDateField.setText(startStr);
                                endDateField.setText(endStr);
                                count++;
                            }
                        }
                    }
                }

                JOptionPane.showMessageDialog(this,
                    String.format("已成功设置 %d 个学生的日期范围为：%s 至 %s", count, startStr, endStr),
                    "成功", JOptionPane.INFORMATION_MESSAGE);

            } catch (Exception e) {
                JOptionPane.showMessageDialog(this,
                    "日期格式错误！请使用 yyyy-MM-dd 格式（例如：2024-01-15）",
                    "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 显示学生档案采集对话框
     */
    public static void showDialog(JFrame parent, ProfileCallback callback) {
        StudentProfileDialog dialog = new StudentProfileDialog(parent, callback);
        dialog.setVisible(true);
    }

    public interface ProfileCallback {
        void onSelected(List<StudentInfo> students, Map<Integer, String[]> dateRangeMap);
    }
}
