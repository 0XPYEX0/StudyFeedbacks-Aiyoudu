package me.xpyex.software.feedback.ui;

import com.google.gson.JsonObject;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.tasks.PrintStudentStudy;
import me.xpyex.software.feedback.util.ConfigManager;

/**
 * 学生打印学案选择对话框
 * 布局：左边勾选框 + 中间学生信息 + 右边篇数输入（实时保存）
 */
public class StudentPrintDialog extends BaseStudentSelectionDialog {
    private final Map<Integer, JTextField> articleFields = new HashMap<>();
    private PrintCallback callback;

    private StudentPrintDialog(JFrame parent, PrintCallback callback) {
        super(parent, "打印学案");
        this.callback = callback;
        setSize(700, 600);
        initBaseUI();
    }

    /**
     * 显示学生打印对话框
     */
    public static void showDialog(JFrame parent, PrintCallback callback) {
        // 加载打印配置
        PrintStudentStudy.loadPrintConfig();

        StudentPrintDialog dialog = new StudentPrintDialog(parent, callback);
        dialog.setVisible(true);
    }

    @Override
    protected String getHintText() {
        return "请选择要打印学案的学生，并设置打印篇数 (修改后自动保存)";
    }

    @Override
    protected String formatStudentInfo(StudentInfo student) {
        return String.format("%-10s [%s] [%s]", student.getRealName(), student.getGradeValue(), student.getGroup());
    }

    @Override
    protected void customizeScrollPane(JScrollPane scrollPane) {
        // 设置滚动速度
        scrollPane.getVerticalScrollBar().setUnitIncrement(25);
        scrollPane.getVerticalScrollBar().setBlockIncrement(120);
    }

    @Override
    protected void addExtraColumns(GridBagConstraints gbc, int row, StudentInfo student, JPanel studentPanel) {
        // 右侧：篇数输入框
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        int defaultArticles = PrintStudentStudy.studentPrintConfig.getOrDefault(
            student.getRealName(), PrintStudentStudy.DEFAULT_ARTICLES);
        JTextField articleField = new JTextField(String.valueOf(defaultArticles), 5);
        articleField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        articleField.setHorizontalAlignment(JTextField.CENTER);
        articleFields.put(student.getStudentId(), articleField);

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
    }

    @Override
    protected JPanel createButtonPanel() {
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

    @Override
    protected void onOK(List<StudentInfo> selectedStudents) {
        // 由 createButtonPanel 中的自定义逻辑处理
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

    public interface PrintCallback {
        void onSelected(List<StudentInfo> students);
    }
}
