package me.xpyex.software.feedback.ui;

import com.google.gson.JsonObject;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.tasks.PrintStudentStudy;
import me.xpyex.software.feedback.tasks.StudentReader;
import me.xpyex.software.feedback.util.ConfigManager;

/**
 * 学生打印学案选择对话框
 * 布局：左边勾选框 + 中间学生信息 + 右边篇数输入（实时保存）
 */
public class StudentPrintDialog extends JDialog {
    private final List<JCheckBox> studentCheckBoxes = new ArrayList<>();
    private final Map<Integer, JTextField> articleFields = new HashMap<>();
    private final Map<JCheckBox, Integer> checkBoxToStudentId = new HashMap<>();
    private PrintCallback callback;

    private StudentPrintDialog(JFrame parent, PrintCallback callback) {
        super(parent, "打印学案 - 选择学生", true);
        this.callback = callback;
        setSize(700, 600);
        setLocationRelativeTo(parent);
        initUI();
    }

    public static void showDialog(JFrame parent, PrintCallback callback) {
        StudentPrintDialog dialog = new StudentPrintDialog(parent, callback);
        dialog.setVisible(true);
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel hintLabel = new JLabel("请选择要打印学案的学生，并设置打印篇数 (修改后自动保存)");
        hintLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
        mainPanel.add(hintLabel, BorderLayout.NORTH);

        // 加载打印配置
        PrintStudentStudy.loadPrintConfig();

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

            // 右侧：篇数输入框
            gbc.gridx = 2;
            gbc.weightx = 0;
            gbc.fill = GridBagConstraints.NONE;
            int defaultArticles = PrintStudentStudy.studentPrintConfig.getOrDefault(
                student.getRealName(), PrintStudentStudy.DEFAULT_ARTICLES);
            JTextField articleField = new JTextField(String.valueOf(defaultArticles), 5);
            articleField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            articleField.setHorizontalAlignment(JTextField.CENTER);
            articleFields.put(studentId, articleField);

            // 实时保存配置
            articleField.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    saveArticleConfig(student.getRealName(), articleField);
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    saveArticleConfig(student.getRealName(), articleField);
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    saveArticleConfig(student.getRealName(), articleField);
                }
            });

            studentPanel.add(articleField, gbc);
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
        JButton btnOK = new JButton("确定");
        JButton btnCancel = new JButton("取消");

        btnSelectAll.addActionListener(ev -> studentCheckBoxes.forEach(cb -> cb.setSelected(true)));
        btnDeselectAll.addActionListener(ev -> studentCheckBoxes.forEach(cb -> cb.setSelected(false)));

        btnOK.addActionListener(ev -> {
            List<StudentInfo> selectedStudents = getSelectedStudents();
            if (selectedStudents.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请至少选择一个学生！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }

            JsonObject obj = new JsonObject();
            PrintStudentStudy.studentPrintConfig.forEach(obj::addProperty);
            ConfigManager.saveConfig("print", obj);

            dispose();
            if (callback != null) {
                callback.onSelected(selectedStudents);
            }
        });

        btnCancel.addActionListener(ev -> dispose());

        buttonPanel.add(btnSelectAll);
        buttonPanel.add(btnDeselectAll);
        buttonPanel.add(btnOK);
        buttonPanel.add(btnCancel);

        return buttonPanel;
    }

    private void saveArticleConfig(String studentName, JTextField field) {
        try {
            String text = field.getText().trim();
            if (text.isEmpty()) return;

            int articles = Integer.parseInt(text);
            if (articles < 0) {
                JOptionPane.showMessageDialog(this, "篇数不能为负数！", "错误", JOptionPane.ERROR_MESSAGE);
                field.setText("0");
                return;
            }

            // 只更新内存，不立即保存到文件
            PrintStudentStudy.studentPrintConfig.put(studentName, articles);

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "请输入有效的数字！", "错误", JOptionPane.ERROR_MESSAGE);
            field.setText("0");
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

    public interface PrintCallback {
        void onSelected(List<StudentInfo> students);
    }
}
