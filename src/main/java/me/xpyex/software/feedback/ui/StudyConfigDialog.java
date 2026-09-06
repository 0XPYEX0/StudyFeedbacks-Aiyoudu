package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import me.xpyex.software.feedback.data.StudyConfig;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.packet.util.StudyContentsUtil.StudyType;
import me.xpyex.software.feedback.study.StudyConfigManager;

/**
 * 学案设置弹窗：为单个学生配置每种题型的打印篇数（实时自动保存，无需提交按钮）。
 * <p>
 * 保存位置：config/study/{真实姓名}_{studentId}.json
 */
public class StudyConfigDialog extends JDialog {
    private final StudentInfo student;
    private final StudyConfig config;
    private final Map<StudyType, JTextField> countFields = new EnumMap<>(StudyType.class);
    private boolean initializing = true; // 防止初始化填充字段时触发保存

    private StudyConfigDialog(JFrame parent, StudentInfo student) {
        super(parent, "学案设置 - " + student.getRealName(), true);
        this.student = student;
        StudyConfig loaded = StudyConfigManager.load(student);
        this.config = loaded == null ? StudyConfig.of().setStudentId(student.getStudentId()).setRealName(student.getRealName())
                           : loaded;

        initUI();
        initializing = false;
        pack();
        setLocationRelativeTo(getParent());
    }

    /** 显示学案设置弹窗（模态） */
    public static void showDialog(JFrame parent, StudentInfo student) {
        new StudyConfigDialog(parent, student).setVisible(true);
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JLabel hint = new JLabel("配置各题型打印篇数（正整数），修改后自动保存");
        hint.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        main.add(hint, BorderLayout.NORTH);

        // 各题型计数行
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        for (StudyType type : StudyType.values()) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 1.0;
            JLabel typeLabel = new JLabel(type.getName());
            typeLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            form.add(typeLabel, gbc);

            gbc.gridx = 1;
            gbc.weightx = 0;
            JTextField field = new JTextField(6);
            field.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            field.setHorizontalAlignment(JTextField.CENTER);
            field.setPreferredSize(new Dimension(90, 28));
            int existing = config.getTypeCountMap().getOrDefault(type.getName(), 0);
            field.setText(existing > 0 ? String.valueOf(existing) : "");
            countFields.put(type, field);
            form.add(field, gbc);

            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent e) {
                    saveType(type, field);
                }

                @Override
                public void removeUpdate(DocumentEvent e) {
                    saveType(type, field);
                }

                @Override
                public void changedUpdate(DocumentEvent e) {
                    saveType(type, field);
                }
            });
            row++;
        }

        // 是否回收月卡
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        JCheckBox refundBox = new JCheckBox("打印完成后回收月卡（退费）；不勾选则给学生保留该月卡", config.isRefundMonth());
        refundBox.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        form.add(refundBox, gbc);
        refundBox.addActionListener(e -> {
            config.setRefundMonth(refundBox.isSelected());
            StudyConfigManager.save(config);
        });

        main.add(form, BorderLayout.CENTER);

        // 底部关闭按钮
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("关闭");
        close.addActionListener(e -> dispose());
        bottom.add(close);
        main.add(bottom, BorderLayout.SOUTH);

        add(main);
    }

    /** 保存当前题型篇数；非法/负数输入在控制台提示并重置为 0 */
    private void saveType(StudyType type, JTextField field) {
        if (initializing) return;
        String text = field.getText().trim();
        if (text.isEmpty()) {
            // 空输入不立即覆盖（视为正在编辑），此前合法值已保存过
            return;
        }
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            System.out.println("学案设置：题型 " + type.getName() + " 输入非法（非数字），已重置为 0");
            resetField(type, field);
            return;
        }
        if (value < 0) {
            System.out.println("学案设置：题型 " + type.getName() + " 不能为负数，已重置为 0");
            resetField(type, field);
            return;
        }

        if (value > 0) {
            config.getTypeCountMap().put(type.getName(), value);
        } else {
            config.getTypeCountMap().remove(type.getName());
        }
        StudyConfigManager.save(config);
    }

    private void resetField(StudyType type, JTextField field) {
        initializing = true;
        try {
            field.setText("0");
            config.getTypeCountMap().remove(type.getName());
            StudyConfigManager.save(config);
        } finally {
            initializing = false;
        }
    }
}
