package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import me.xpyex.software.feedback.feedback.FeedbackHistoryManager;

/**
 * 偏好设置弹窗（全局）：设定生成 AI 反馈时合并的"最近几条历史反馈"条数。
 * 设置保存到 config/ai.json 的 historyCount。
 */
public class SettingsDialog extends JDialog {
    private final JSpinner countSpinner;

    private SettingsDialog(JFrame parent) {
        super(parent, "偏好设置", true);
        countSpinner = new JSpinner(new SpinnerNumberModel(
            FeedbackHistoryManager.getGlobalCount(), 0, 50, 1));
        initUI();
        pack();
        setLocationRelativeTo(parent);
    }

    public static void showDialog(JFrame parent) {
        new SettingsDialog(parent).setVisible(true);
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel hint = new JLabel("<html>生成 AI 反馈时，把该生最近 N 条已保存的历史反馈一并合并给 AI，<br>"
            + "以便保持风格一致、避免重复。设为 0 表示不附带历史反馈。</html>");
        hint.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        main.add(hint, BorderLayout.NORTH);

        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        line.add(new JLabel("合并最近历史反馈条数："));
        countSpinner.setPreferredSize(new Dimension(70, 28));
        line.add(countSpinner);
        main.add(line, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        JButton save = new JButton("保存");
        JButton cancel = new JButton("取消");
        save.addActionListener(e -> saveSetting());
        cancel.addActionListener(e -> dispose());
        bottom.add(save);
        bottom.add(cancel);
        main.add(bottom, BorderLayout.SOUTH);

        add(main);
    }

    private void saveSetting() {
        int value = (Integer) countSpinner.getValue();
        FeedbackHistoryManager.setGlobalCount(value);
        dispose();
    }
}
