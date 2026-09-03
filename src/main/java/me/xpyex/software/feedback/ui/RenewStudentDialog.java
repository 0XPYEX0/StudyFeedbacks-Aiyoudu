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
import me.xpyex.software.feedback.util.ConfigManager;

/**
 * 学生续费天数配置对话框
 * 布局：左边勾选框 + 中间学生信息 + 右边续费天数输入（实时保存）
 */
public class RenewStudentDialog extends BaseStudentSelectionDialog {
    // 内存中的续费配置，键为学生姓名，值为续费天数
    private static final Map<String, Integer> renewConfig = new HashMap<>();

    private final Map<Integer, JTextField> dayFields = new HashMap<>();
    private RenewCallback callback;

    private RenewStudentDialog(JFrame parent, RenewCallback callback) {
        super(parent, "续费学生卡");
        this.callback = callback;
        setSize(700, 600);
        initBaseUI();
    }

    /**
     * 显示学生续费对话框
     */
    public static void showDialog(JFrame parent, RenewCallback callback) {
        // 加载续费配置
        loadRenewConfig();

        RenewStudentDialog dialog = new RenewStudentDialog(parent, callback);
        dialog.setVisible(true);
    }

    /**
     * 加载续费配置到内存
     */
    private static void loadRenewConfig() {
        renewConfig.clear();
        JsonObject config = ConfigManager.loadConfig("renew");
        config.entrySet().forEach(entry ->
                                      renewConfig.put(entry.getKey(), entry.getValue().getAsInt())
        );
    }

    @Override
    protected String getHintText() {
        return "请选择要续费的学生，并设置续费天数 (修改后自动保存，0表示不续费)";
    }

    @Override
    protected String formatStudentInfo(StudentInfo student) {
        String cardType;
        if (student.getCardType() == StudentInfo.CardType.IN_TRIAL.getCardType()) {
            cardType = "体验卡";
        } else if (student.getCardType() == StudentInfo.CardType.IN_MONTHS.getCardType()) {
            cardType = "月卡";
        } else if (student.getCardType() == StudentInfo.CardType.IN_DAYS.getCardType()) {
            cardType = "包月卡";
        } else {
            cardType = "未知";
        }
        int expireDay = student.getExpireDay();
        return String.format("%-10s [%s] [%s] %s 剩余%d天",
            student.getRealName(), student.getGradeValue(), student.getGroup(), cardType, expireDay);
    }

    @Override
    protected void customizeScrollPane(JScrollPane scrollPane) {
        // 设置滚动速度
        scrollPane.getVerticalScrollBar().setUnitIncrement(25);
        scrollPane.getVerticalScrollBar().setBlockIncrement(120);
    }

    @Override
    protected void addExtraColumns(GridBagConstraints gbc, int row, StudentInfo student, JPanel studentPanel) {
        // 右侧：续费天数输入框
        gbc.gridx = 2;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;

        // 从内存配置中加载默认值，默认为1（续1天）
        int defaultDays = renewConfig.getOrDefault(student.getRealName(), 1);

        JTextField dayField = new JTextField(String.valueOf(defaultDays), 5);
        dayField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        dayField.setHorizontalAlignment(JTextField.CENTER);
        dayFields.put(student.getStudentId(), dayField);

        // 实时更新内存配置
        dayField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                saveDayConfig(student.getRealName(), dayField);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                saveDayConfig(student.getRealName(), dayField);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                saveDayConfig(student.getRealName(), dayField);
            }
        });

        studentPanel.add(dayField, gbc);
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

            // 保存配置到文件
            JsonObject obj = new JsonObject();
            for (Map.Entry<String, Integer> entry : renewConfig.entrySet()) {
                obj.addProperty(entry.getKey(), entry.getValue());
            }
            ConfigManager.saveConfig("renew", obj);

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

    private void saveDayConfig(String studentName, JTextField field) {
        try {
            String text = field.getText().trim();
            if (text.isEmpty()) return;

            int days = Integer.parseInt(text);
            if (days < 0) {
                JOptionPane.showMessageDialog(this, "天数不能为负数！", "错误", JOptionPane.ERROR_MESSAGE);
                field.setText("0");
                return;
            }

            // 只更新内存，不立即保存到文件
            renewConfig.put(studentName, days);

        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "请输入有效的数字！", "错误", JOptionPane.ERROR_MESSAGE);
            field.setText("0");
        }
    }

    public interface RenewCallback {
        void onSelected(List<StudentInfo> students);
    }
}
