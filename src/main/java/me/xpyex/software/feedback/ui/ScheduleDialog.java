package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import me.xpyex.software.feedback.data.StudentSchedule;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.schedule.ScheduleManager;

/**
 * 课时安排弹窗（单名学生）：只维护「当前计费开始日期 + 总次数」，改动即保存。
 * <p>
 * 月历、上课/请假等考勤已移除；「剩余课次」的已上课数据来源见
 * {@code ScheduleManager.attendedCount(...)} 的 TODO 占位。
 */
public class ScheduleDialog extends JDialog {
    private final StudentInfo student;
    private final StudentSchedule schedule;

    private final JTextField startField;
    private final JTextField totalField;

    private ScheduleDialog(JFrame parent, StudentInfo student) {
        super(parent, "课时安排 - " + student.getRealName(), true);
        this.student = student;

        StudentSchedule loaded = ScheduleManager.load(student.getStudentId());
        LocalDate today = LocalDate.now();
        if (loaded != null) {
            this.schedule = loaded;
        } else {
            this.schedule = StudentSchedule.of()
                                    .setStudentId(student.getStudentId())
                                    .setStartDate(ScheduleManager.toIso(today))
                                    .setTotalLessons(30);
        }
        this.schedule.setRealName(student.getRealName());

        startField = new JTextField(schedule.getStartDate() == null ? "" : schedule.getStartDate(), 12);
        totalField = new JTextField(String.valueOf(schedule.getTotalLessons()), 8);

        initUI();
        pack();
        setMinimumSize(new Dimension(360, 200));
        setLocationRelativeTo(parent);
    }

    public static void showDialog(JFrame parent, StudentInfo student) {
        new ScheduleDialog(parent, student).setVisible(true);
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        JLabel hint = new JLabel("填写该学生的计费开始日期与总次数，改动即保存。");
        hint.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        main.add(hint, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 6, 8, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        form.add(new JLabel("开始日期"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        startField.setToolTipText("yyyy-MM-dd，回车或失焦后保存");
        form.add(startField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        form.add(new JLabel("总次数"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1.0;
        totalField.setToolTipText("非负整数，回车或失焦后保存");
        form.add(totalField, gbc);

        main.add(form, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        JButton save = new JButton("保存");
        JButton close = new JButton("完成");
        save.addActionListener(e -> persist());
        close.addActionListener(e -> {
            persist();
            dispose();
        });
        bottom.add(save);
        bottom.add(close);
        main.add(bottom, BorderLayout.SOUTH);

        add(main);

        // 回车/失焦即保存
        startField.addActionListener(e -> persist());
        totalField.addActionListener(e -> persist());
        startField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                persist();
            }
        });
        totalField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                persist();
            }
        });
    }

    /** 读取表单并保存；非法输入保留上次有效值并在控制台提示 */
    private void persist() {
        // 总次数
        String totalText = totalField.getText().trim();
        if (!totalText.isEmpty()) {
            try {
                schedule.setTotalLessons(Math.max(0, Integer.parseInt(totalText)));
            } catch (NumberFormatException e) {
                System.out.println("课时安排：总次数输入非法，已保留上次有效值");
                totalField.setText(String.valueOf(schedule.getTotalLessons()));
            }
        }

        // 开始日期
        String raw = startField.getText().trim();
        if (!raw.isEmpty()) {
            try {
                schedule.setStartDate(ScheduleManager.toIso(LocalDate.parse(raw, ScheduleManager.ISO)));
            } catch (DateTimeParseException e) {
                System.out.println("课时安排：开始日期格式非法（应为 yyyy-MM-dd），已保留上次有效值");
                startField.setText(schedule.getStartDate() == null ? "" : schedule.getStartDate());
            }
        } else {
            schedule.setStartDate(null);
        }

        schedule.setRealName(student.getRealName());
        if (ScheduleManager.save(schedule)) {
            System.out.println("√ 已保存课时安排：" + student.getRealName()
                                   + " 开始日期=" + schedule.getStartDate()
                                   + " 总次数=" + schedule.getTotalLessons());
        }
    }
}
