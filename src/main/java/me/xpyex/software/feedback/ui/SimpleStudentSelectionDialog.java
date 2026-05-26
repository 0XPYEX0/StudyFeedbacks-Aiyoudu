package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.tasks.StudentReader;

/**
 * 通用学生选择对话框 - 用于选择要处理的学生列表（不包含额外配置项）
 * 适用于只需要选择学生的场景，如AI点评等
 */
public class SimpleStudentSelectionDialog extends JDialog {
    private final List<JCheckBox> studentCheckBoxes = new ArrayList<>();
    private final Map<JCheckBox, Integer> checkBoxToStudentId = new HashMap<>(); // 勾选框 -> 学生ID

    private SimpleStudentSelectionDialog(JFrame parent, String title, Consumer<List<StudentInfo>> callback) {
        super(parent, "选择学生 - " + title, true);
        setSize(450, 550);
        setLocationRelativeTo(parent);

        initUI(callback);
    }

    /**
     * 显示学生选择对话框
     *
     * @param parent   父窗口
     * @param title    对话框标题
     * @param callback 回调函数，接收选中的学生列表
     */
    public static void showDialog(JFrame parent, String title, Consumer<List<StudentInfo>> callback) {
        SimpleStudentSelectionDialog dialog = new SimpleStudentSelectionDialog(parent, title, callback);
        dialog.setVisible(true);
    }

    private void initUI(Consumer<List<StudentInfo>> callback) {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // 顶部提示
        JLabel hintLabel = new JLabel("请选择要处理的学生：");
        hintLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
        mainPanel.add(hintLabel, BorderLayout.NORTH);

        // 中间复选框列表（带滚动）
        JPanel checkBoxPanel = new JPanel(new GridBagLayout());
        JScrollPane scrollPane = new JScrollPane(checkBoxPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(3, 5, 3, 5);

        studentCheckBoxes.clear();
        Map<Integer, StudentInfo> allStudents = StudentReader.getAllStudents();

        int row = 0;
        for (Map.Entry<Integer, StudentInfo> entry : allStudents.entrySet()) {
            StudentInfo student = entry.getValue();
            int studentId = student.getStudentId();

            // 左侧：勾选框
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            JCheckBox checkBox = new JCheckBox();
            checkBox.setSelected(true); // 默认全选
            studentCheckBoxes.add(checkBox);
            checkBoxToStudentId.put(checkBox, studentId);
            checkBoxPanel.add(checkBox, gbc);

            // 右侧：学生信息
            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            String studentInfo = String.format("%-10s [ID:%d] - %s",
                student.getRealName(),
                studentId,
                student.getGroup());
            JLabel infoLabel = new JLabel(studentInfo);
            infoLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            checkBoxPanel.add(infoLabel, gbc);

            row++;
        }

        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 底部按钮面板
        JPanel buttonPanel = createButtonPanel(callback);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    /**
     * 创建底部按钮面板
     */
    private JPanel createButtonPanel(Consumer<List<StudentInfo>> callback) {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton btnSelectAll = new JButton("全选");
        JButton btnDeselectAll = new JButton("取消全选");
        JButton btnOK = new JButton("确定");
        JButton btnCancel = new JButton("取消");

        // 全选按钮
        btnSelectAll.addActionListener(ev -> {
            for (JCheckBox cb : studentCheckBoxes) {
                cb.setSelected(true);
            }
        });

        // 取消全选按钮
        btnDeselectAll.addActionListener(ev -> {
            for (JCheckBox cb : studentCheckBoxes) {
                cb.setSelected(false);
            }
        });

        // 确定按钮
        btnOK.addActionListener(ev -> {
            List<StudentInfo> selectedStudents = getSelectedStudents();

            if (selectedStudents.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                    "请至少选择一个学生！",
                    "提示",
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            dispose();
            callback.accept(selectedStudents);
        });

        // 取消按钮
        btnCancel.addActionListener(ev -> dispose());

        buttonPanel.add(btnSelectAll);
        buttonPanel.add(btnDeselectAll);
        buttonPanel.add(btnOK);
        buttonPanel.add(btnCancel);

        return buttonPanel;
    }

    /**
     * 获取选中的学生列表
     */
    private List<StudentInfo> getSelectedStudents() {
        return studentCheckBoxes.stream()
                   .filter(JCheckBox::isSelected)
                   .map(cb -> {
                       Integer studentId = checkBoxToStudentId.get(cb);
                       if (studentId != null) {
                           return StudentReader.getStudentById(studentId);
                       }
                       return null;
                   })
                   .filter(s -> s != null)
                   .collect(Collectors.toList());
    }
}
