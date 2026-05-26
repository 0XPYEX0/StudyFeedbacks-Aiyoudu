package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.tasks.StudentReader;

/**
 * 学生档案采集选择对话框
 * 布局：左边勾选框 + 中间学生信息 + 右边日期选择
 */
public class StudentProfileDialog extends JDialog {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final List<JCheckBox> studentCheckBoxes = new ArrayList<>();
    private final Map<Integer, JTextField> startDateFields = new HashMap<>();  // 学生ID -> 开始日期
    private final Map<Integer, JTextField> endDateFields = new HashMap<>();    // 学生ID -> 结束日期
    private final Map<JCheckBox, Integer> checkBoxToStudentId = new HashMap<>();
    private ProfileCallback callback;

    private StudentProfileDialog(JFrame parent, ProfileCallback callback) {
        super(parent, "采集档案 - 选择学生", true);
        this.callback = callback;
        setSize(700, 600);
        setLocationRelativeTo(parent);
        initUI();
    }

    public static void showDialog(JFrame parent, ProfileCallback callback) {
        StudentProfileDialog dialog = new StudentProfileDialog(parent, callback);
        dialog.setVisible(true);
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel hintLabel = new JLabel("请选择要采集档案的学生，并设置采集日期范围（开始日期 - 结束日期）：");
        hintLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
        mainPanel.add(hintLabel, BorderLayout.NORTH);

        JPanel studentPanel = new JPanel(new GridBagLayout());
        JScrollPane scrollPane = new JScrollPane(studentPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        // 设置滚动速度
        scrollPane.getVerticalScrollBar().setUnitIncrement(25);
        scrollPane.getVerticalScrollBar().setBlockIncrement(120);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(3, 5, 3, 5);

        Map<Integer, StudentInfo> allStudents = StudentReader.getAllStudents();
        String today = LocalDate.now().format(DATE_FORMATTER);

        int row = 0;
        for (Map.Entry<Integer, StudentInfo> entry : allStudents.entrySet()) {
            StudentInfo student = entry.getValue();
            int studentId = student.getStudentId();

            // 左侧：勾选框
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(true);
            studentCheckBoxes.add(checkBox);
            checkBoxToStudentId.put(checkBox, studentId);
            studentPanel.add(checkBox, gbc);

            // 中间：学生信息
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            String studentInfo = String.format("%-10s [%s]", student.getRealName(), student.getGroup());
            JLabel infoLabel = new JLabel(studentInfo);
            infoLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            studentPanel.add(infoLabel, gbc);

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

            startDateFields.put(studentId, startDateField);
            endDateFields.put(studentId, endDateField);
            studentPanel.add(datePanel, gbc);

            row++;
        }

        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(createButtonPanel(), BorderLayout.SOUTH);
        add(mainPanel);
    }

    private JPanel createButtonPanel() {
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

    private List<StudentInfo> getSelectedStudents() {
        List<StudentInfo> selected = new ArrayList<>();
        for (JCheckBox cb : studentCheckBoxes) {
            if (cb.isSelected()) {
                Integer studentId = checkBoxToStudentId.get(cb);
                if (studentId != null) {
                    StudentInfo student = StudentReader.getStudentById(studentId);
                    if (student != null) {
                        selected.add(student);
                    }
                }
            }
        }
        return selected;
    }

    public interface ProfileCallback {
        void onSelected(List<StudentInfo> students, Map<Integer, String[]> dateRangeMap);
    }
}
