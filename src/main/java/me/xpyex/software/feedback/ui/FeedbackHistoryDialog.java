package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import me.xpyex.software.feedback.data.StudentFeedbackHistory;
import me.xpyex.software.feedback.feedback.FeedbackHistoryManager;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.schedule.ScheduleManager;

/**
 * 填入历史反馈弹窗：把曾经真正交给该学生的反馈文本粘贴进来，仅保存。
 * 发送给 AI 生成新反馈时会取最近 N 条（全局自定义）一并参考。
 */
public class FeedbackHistoryDialog extends JDialog {
    private final StudentInfo student;
    private final JPanel recordsPanel = new JPanel();
    private final JTextField dateField = new JTextField(ScheduleManager.toIso(LocalDate.now()), 10);
    private final JTextArea textArea = new JTextArea(4, 40);

    private FeedbackHistoryDialog(JFrame parent, StudentInfo student) {
        super(parent, "填入历史反馈 - " + student.getRealName(), true);
        this.student = student;
        initUI();
        pack();
        setLocationRelativeTo(parent);
    }

    public static void showDialog(JFrame parent, StudentInfo student) {
        new FeedbackHistoryDialog(parent, student).setVisible(true);
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

        JLabel hint = new JLabel("<html>将每次真正交给该学生的历史反馈粘贴到下方并「追加保存」。"
            + "<br>生成 AI 反馈时，程序会取最近若干条（可在主界面「偏好设置」调整条数）一并合并给 AI 参考。</html>");
        hint.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        main.add(hint, BorderLayout.NORTH);

        main.add(buildHistoryList(), BorderLayout.CENTER);
        main.add(buildAppendForm(), BorderLayout.SOUTH);

        add(main);
    }

    /** 历史记录列表（可查看全文 / 删除） */
    private JPanel buildHistoryList() {
        JPanel wrap = new JPanel(new BorderLayout());
        JLabel title = new JLabel("已保存的历史反馈（按日期从旧到新）");
        title.setFont(new Font("微软雅黑", Font.BOLD, 13));
        wrap.add(title, BorderLayout.NORTH);

        recordsPanel.setLayout(new BoxLayout(recordsPanel, BoxLayout.Y_AXIS));
        refreshRecords();

        JScrollPane scroll = new JScrollPane(recordsPanel);
        scroll.setPreferredSize(new Dimension(560, 240));
        wrap.add(scroll, BorderLayout.CENTER);
        return wrap;
    }

    private void refreshRecords() {
        recordsPanel.removeAll();
        StudentFeedbackHistory history = FeedbackHistoryManager.loadByStudentId(student.getStudentId());
        List<StudentFeedbackHistory.Record> records =
            history == null ? List.of() : history.getRecords();
        if (records.isEmpty()) {
            recordsPanel.add(new JLabel("（暂无历史反馈）"));
        } else {
            for (int i = 0; i < records.size(); i++) {
                recordsPanel.add(buildRecordRow(i, records.get(i)));
            }
        }
        recordsPanel.revalidate();
        recordsPanel.repaint();
    }

    private JPanel buildRecordRow(int index, StudentFeedbackHistory.Record record) {
        JPanel row = new JPanel(new BorderLayout(8, 2));
        row.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

        JLabel date = new JLabel(record.getDate() == null ? "" : record.getDate());
        date.setPreferredSize(new Dimension(100, 26));
        date.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        row.add(date, BorderLayout.WEST);

        String text = record.getText() == null ? "" : record.getText();
        String preview = text.replaceAll("\\s+", " ");
        if (preview.length() > 60) preview = preview.substring(0, 60) + "…";
        JLabel previewLabel = new JLabel(preview);
        previewLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        row.add(previewLabel, BorderLayout.CENTER);

        JButton view = new JButton("查看全文");
        JButton del = new JButton("删除");
        view.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        del.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        JPanel ops = new JPanel(new GridLayout(1, 2, 4, 0));
        ops.add(view);
        ops.add(del);
        row.add(ops, BorderLayout.EAST);

        view.addActionListener(e -> showFullText(record));
        del.addActionListener(e -> deleteRecord(index));
        return row;
    }

    private void showFullText(StudentFeedbackHistory.Record record) {
        JTextArea area = new JTextArea(record.getText() == null ? "" : record.getText(), 12, 50);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
            "历史反馈 - " + (record.getDate() == null ? "" : record.getDate()),
            JOptionPane.PLAIN_MESSAGE);
    }

    private void deleteRecord(int index) {
        if (index < 0) return;
        boolean ok = FeedbackHistoryManager.deleteAt(student, index);
        if (ok) {
            System.out.println("已删除一条历史反馈");
        }
        refreshRecords();
    }

    /** 追加表单：日期 + 文本 + 追加保存 */
    private JPanel buildAppendForm() {
        JPanel form = new JPanel(new BorderLayout(6, 4));
        form.setBorder(BorderFactory.createTitledBorder("追加历史反馈"));

        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        line.add(new JLabel("日期："));
        dateField.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        dateField.setToolTipText("yyyy-MM-dd");
        line.add(dateField);
        form.add(line, BorderLayout.NORTH);

        textArea.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        JScrollPane areaScroll = new JScrollPane(textArea);
        areaScroll.setPreferredSize(new Dimension(560, 110));
        form.add(areaScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton append = new JButton("追加保存");
        append.addActionListener(e -> appendRecord());
        bottom.add(append);
        form.add(bottom, BorderLayout.SOUTH);
        return form;
    }

    private void appendRecord() {
        String date = dateField.getText().trim();
        try {
            LocalDate.parse(date, ScheduleManager.ISO);
        } catch (DateTimeParseException e) {
            JOptionPane.showMessageDialog(this, "日期格式错误！请使用 yyyy-MM-dd", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String text = textArea.getText().trim();
        if (text.isEmpty()) {
            JOptionPane.showMessageDialog(this, "反馈文本不能为空！", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean ok = FeedbackHistoryManager.append(student, date, text);
        if (ok) {
            System.out.println("√ 已保存历史反馈：" + student.getRealName() + " [" + date + "]");
            textArea.setText("");
            refreshRecords();
        } else {
            System.out.println("✗ 保存历史反馈失败，请查看控制台");
            JOptionPane.showMessageDialog(this, "保存失败，请查看控制台", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}
