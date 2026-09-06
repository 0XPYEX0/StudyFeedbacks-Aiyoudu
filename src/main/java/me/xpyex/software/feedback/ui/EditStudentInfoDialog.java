package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.packet.in.AYDResponse;
import me.xpyex.software.feedback.tasks.basis.StudentReader;
import me.xpyex.software.feedback.tasks.basis.StudentUpdater;
import me.xpyex.software.feedback.util.GsonUtil;

/**
 * 修改信息弹窗：编辑单名学生的常用信息（姓名、年级、联系方式等），提交后 PUT 到服务端。
 * <p>
 * 提交成功后自动更新内存中的学生并刷新主界面表格。
 */
public class EditStudentInfoDialog extends JDialog {
    /** 可编辑字段名 -> (读取getter / 写入setter) */
    private final Map<String, Function<StudentInfo, String>> getters = new LinkedHashMap<>();
    private final Map<String, BiConsumer<StudentInfo, String>> setters = new LinkedHashMap<>();
    private final Map<String, JTextField> fields = new LinkedHashMap<>();

    private final StudentInfo working; // 编辑的工作副本，提交成功才写回内存
    private JButton btnSubmit;

    private EditStudentInfoDialog(JFrame parent, StudentInfo student) {
        super(parent, "修改信息 - " + student.getRealName(), true);
        this.working = cloneOf(student);
        registerEditableFields();
        initUI();
        pack();
        setLocationRelativeTo(parent);
    }

    public static void showDialog(JFrame parent, StudentInfo student) {
        new EditStudentInfoDialog(parent, student).setVisible(true);
    }

    /** 通过 Gson 深拷贝一份学生信息，作为可编辑的工作副本（不直接改内存对象） */
    private static StudentInfo cloneOf(StudentInfo origin) {
        if (origin == null) return null;
        return GsonUtil.parseObj(GsonUtil.toJsonStr(origin, false), StudentInfo.class);
    }

    private void registerEditableFields() {
        register("真实姓名", StudentInfo::getRealName, StudentInfo::setRealName);
        register("昵称", StudentInfo::getNickName, StudentInfo::setNickName);
        register("年级", StudentInfo::getGradeValue, StudentInfo::setGradeValue);
        register("手机号", StudentInfo::getPhone, StudentInfo::setPhone);
        register("班级", StudentInfo::getClassName, StudentInfo::setClassName);
        register("学校", StudentInfo::getSchoolName, StudentInfo::setSchoolName);
        register("家长", StudentInfo::getParent, StudentInfo::setParent);
        register("家长电话", StudentInfo::getParentPhone, StudentInfo::setParentPhone);
        register("学号", StudentInfo::getStudyNo, StudentInfo::setStudyNo);
        register("省份", StudentInfo::getProvince, StudentInfo::setProvince);
        register("城市", StudentInfo::getCity, StudentInfo::setCity);
        register("备注", StudentInfo::getRemark, StudentInfo::setRemark);
    }

    private void register(String label, Function<StudentInfo, String> getter, BiConsumer<StudentInfo, String> setter) {
        getters.put(label, getter);
        setters.put(label, setter);
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        for (String label : getters.keySet()) {
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.weightx = 0;
            JLabel l = new JLabel(label);
            l.setFont(new Font("微软雅黑", Font.PLAIN, 13));
            form.add(l, gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            JTextField field = new JTextField(24);
            field.setFont(new Font("微软雅黑", Font.PLAIN, 13));
            String value = getters.get(label).apply(working);
            field.setText(value == null ? "" : value);
            fields.put(label, field);
            form.add(field, gbc);
            row++;
        }

        JScrollPane scroll = new JScrollPane(form);
        scroll.setPreferredSize(new Dimension(460, 420));
        main.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        btnSubmit = new JButton("提交");
        JButton btnCancel = new JButton("取消");
        btnSubmit.addActionListener(e -> submit());
        btnCancel.addActionListener(e -> dispose());
        bottom.add(btnSubmit);
        bottom.add(btnCancel);
        main.add(bottom, BorderLayout.SOUTH);

        add(main);
    }

    private void submit() {
        if (working == null) {
            JOptionPane.showMessageDialog(this, "学生信息为空，无法提交", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        // 收集表单值到工作副本
        for (String label : getters.keySet()) {
            String text = fields.get(label).getText().trim();
            setters.get(label).accept(working, text.isEmpty() ? null : text);
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("提交中...");

        // 后台线程执行 PUT，避免阻塞界面
        new Thread(() -> {
            AYDResponse response = StudentUpdater.updateStudentInfo(working);
            SwingUtilities.invokeLater(() -> {
                if (response != null && response.isSuccess()) {
                    StudentReader.updateStudent(working);
                    MainWindow window = MainWindow.current;
                    if (window != null) {
                        window.refreshAll();
                    }
                    System.out.println("√ 学生 " + working.getRealName() + " 信息修改成功");
                    dispose();
                } else {
                    System.out.println("✗ 学生 " + working.getRealName() + " 信息修改失败，请查看控制台");
                    JOptionPane.showMessageDialog(this, "修改失败，请查看控制台", "错误", JOptionPane.ERROR_MESSAGE);
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("提交");
                }
            });
        }, "EditStudentInfo-Thread").start();
    }
}
