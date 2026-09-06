package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import me.xpyex.software.feedback.data.StudentSchedule;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.schedule.ScheduleManager;
import me.xpyex.software.feedback.schedule.StudentGroupManager;
import me.xpyex.software.feedback.tasks.basis.StudentReader;
import me.xpyex.software.feedback.tasks.basis.TokenGetter;
import me.xpyex.software.feedback.tasks.feedback.DeepSeekAnalyzer;
import me.xpyex.software.feedback.tasks.feedback.StudentInfoCollector;
import me.xpyex.software.feedback.tasks.studyPrepare.PrintStudentStudy;
import me.xpyex.software.feedback.tasks.studyPrepare.RenewStudentCard;
import me.xpyex.software.feedback.util.NetworkUtil;
import me.xpyex.software.feedback.util.TaskExecutor;
import me.xpyex.software.feedback.util.TimeUtil;

/**
 * 主窗口：顶部操作按钮 + 中间学生表格（全部/按时段分组），日志统一输出到控制台。
 * <p>
 * 每行代表一名学生：勾选框 | 学生姓名 | 年级 | 剩余课次(≤5 红字加粗) | 课时安排 | 学案设置 | 修改信息 | 反馈相关
 */
public class MainWindow extends JFrame {
    public static MainWindow current;

    private final Set<Integer> selectedIds = new LinkedHashSet<>();
    private final JTabbedPane tabbedPane = new JTabbedPane();

    private JLabel statusLabel;
    private JButton btnGetToken;
    private JButton btnReadStudents;
    private JButton btnPrintStudy;
    private JButton btnFeedback;
    private JButton btnRenewCard;
    private JButton btnStop;
    private JButton btnSettings;

    public MainWindow() {
        initUI();
    }

    /** 显示主窗口 */
    public static void showMainWindow() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            MainWindow window = new MainWindow();
            window.setVisible(true);
            window.refreshAll();
            current = window;
        });
    }

    private void initUI() {
        setTitle("爱优读学生反馈系统");
        setSize(1180, 760);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        mainPanel.add(createTopBar(), BorderLayout.NORTH);

        tabbedPane.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        statusLabel = new JLabel("就绪");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        add(mainPanel);

        // 窗口关闭时清理资源
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                TaskExecutor.stopCurrentTask();
                NetworkUtil.killEdgeDriver();
            }
        });
    }

    // ==================== 顶部按钮栏 ====================

    private JPanel createTopBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        btnGetToken = button("获取token", "启动浏览器并登录以获取 Token");
        btnReadStudents = button("读取学生", "读取学生信息并刷新下方表格");
        btnPrintStudy = button("批量打印学案", "对勾选学生按学案设置批量打印");
        btnFeedback = button("批量生成反馈", "采集档案 / AI 生成反馈");
        btnRenewCard = button("续费学生卡", "按配置为学生卡续费");
        btnStop = button("停止任务", "立即停止当前正在运行的任务");
        btnStop.setBackground(new Color(255, 100, 100));
        btnSettings = button("偏好设置", "设置生成反馈时合并最近几条历史反馈");

        bar.add(btnGetToken);
        bar.add(btnReadStudents);
        bar.add(btnPrintStudy);
        bar.add(btnFeedback);
        bar.add(btnRenewCard);
        bar.add(btnStop);
        bar.add(btnSettings);

        bindActions();

        // 让任务运行期间自动禁用顶部按钮并更新状态栏
        TaskExecutor.initGUI(this, statusLabel, btnGetToken, btnReadStudents, btnPrintStudy,
            btnFeedback, btnRenewCard, btnStop, btnSettings);
        return bar;
    }

    private static JButton button(String text, String tip) {
        JButton b = new JButton(text);
        b.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        b.setPreferredSize(new Dimension(118, 34));
        b.setToolTipText(tip);
        return b;
    }

    private void bindActions() {
        btnGetToken.addActionListener(e -> TaskExecutor.executeTask(() -> {
            log("正在启动浏览器，请在浏览器中完成登录...");
            TimeUtil.sleep(3000);
            TokenGetter.start();
        }, "TokenGetter-Thread"));

        btnReadStudents.addActionListener(e -> TaskExecutor.executeTask(() -> {
            StudentReader.start();
            SwingUtilities.invokeLater(this::refreshAll);
        }, "StudentReader-Thread"));

        btnPrintStudy.addActionListener(e -> {
            if (!ensureStudents()) return;
            List<StudentInfo> selected = getSelectedStudents();
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(this, "请先在表格中勾选学生！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            TaskExecutor.executeTask(() -> PrintStudentStudy.startWithStudents(selected), "PrintStudentStudy-Thread");
        });

        btnFeedback.addActionListener(e -> {
            if (!ensureStudents()) return;
            int choice = JOptionPane.showOptionDialog(this,
                "请选择要执行的操作：", "批量生成反馈",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new Object[]{"采集档案（生成 AI 素材）", "AI 反馈"}, null);
            if (choice == 0) {
                openCollectDialog();
            } else if (choice == 1) {
                openAiFeedbackDialog();
            }
        });

        btnRenewCard.addActionListener(e -> {
            if (!ensureStudents()) return;
            RenewStudentDialog.showDialog(this, selected ->
                TaskExecutor.executeTask(() -> RenewStudentCard.startWithStudents(selected), "RenewStudentCard-Thread"));
        });

        btnStop.addActionListener(e -> TaskExecutor.stopCurrentTask());
        btnSettings.addActionListener(e -> SettingsDialog.showDialog(this));
    }

    private boolean ensureStudents() {
        if (StudentReader.hasStudents()) return true;
        JOptionPane.showMessageDialog(this, "请先执行【读取学生】操作！", "提示", JOptionPane.WARNING_MESSAGE);
        return false;
    }

    /** 采集档案：选择学生与日期范围 */
    private void openCollectDialog() {
        StudentProfileDialog.showDialog(this, (students, dateRangeMap) -> {
            if (students.isEmpty()) return;
            StudentInfoCollector.setSelectedStudents(students);
            // 使用同一日期范围（批量选择时通常一致）
            if (!dateRangeMap.isEmpty()) {
                String[] range = dateRangeMap.values().iterator().next();
                StudentInfoCollector.setStart(range[0]);
                StudentInfoCollector.setEnd(range[1]);
            }
            TaskExecutor.executeTask(StudentInfoCollector::start, "StudentInfoCollector-Thread");
        });
    }

    /** AI 反馈：选择学生 */
    private void openAiFeedbackDialog() {
        SimpleStudentSelectionDialog.showDialog(this, "AI 反馈", selected ->
            TaskExecutor.executeTask(() -> DeepSeekAnalyzer.startWithStudents(selected), "DeepSeekAnalyzer-Thread"));
    }

    // ==================== 学生表格 ====================

    /**
     * 刷新整个中部区域：重算所有在生课时 → 重建「全部」与各分组标签页。
     * 必须在 EDT 上调用。
     */
    public void refreshAll() {
        Map<Integer, StudentInfo> students = StudentReader.copyStudents();
        ScheduleManager.recomputeAllStudents(students.values()); // 保持剩余课次随日期滚动（顺带写对文件名）

        tabbedPane.removeAll();
        selectedIds.retainAll(students.keySet());

        if (students.isEmpty()) {
            JLabel empty = new JLabel("暂无学生数据，请点击顶部【读取学生】加载", JLabel.CENTER);
            empty.setFont(new Font("微软雅黑", Font.PLAIN, 15));
            empty.setForeground(Color.GRAY);
            JPanel p = new JPanel(new BorderLayout());
            p.add(empty, BorderLayout.CENTER);
            tabbedPane.addTab("全部学生", p);
        } else {
            List<StudentInfo> all = students.values().stream()
                                     .sorted(Comparator.comparingInt(StudentInfo::getGrade)
                                                   .thenComparing(StudentInfo::getRealName,
                                                       Comparator.nullsLast(String::compareTo)))
                                     .toList();

            tabbedPane.addTab("全部学生", buildStudentTab(all));
            Map<String, List<StudentInfo>> groups = StudentGroupManager.groupBy(all);
            for (Map.Entry<String, List<StudentInfo>> entry : groups.entrySet()) {
                tabbedPane.addTab(entry.getKey(), buildStudentTab(entry.getValue()));
            }
        }
        updateStatusLabel();
    }

    /** 构建一个标签页：表头(含全选) + 可滚动学生行 */
    private JPanel buildStudentTab(List<StudentInfo> students) {
        JPanel content = new JPanel(new BorderLayout());

        List<JCheckBox> rowBoxes = new ArrayList<>();
        JPanel rowsPanel = new JPanel();
        rowsPanel.setLayout(new javax.swing.BoxLayout(rowsPanel, javax.swing.BoxLayout.Y_AXIS));
        for (StudentInfo student : students) {
            rowsPanel.add(buildStudentRow(student, rowBoxes));
        }

        content.add(buildHeader(rowBoxes), BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(rowsPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        content.add(scroll, BorderLayout.CENTER);
        return content;
    }

    /** 表头行（列宽与数据行保持一致：8 列 GridLayout） */
    private JPanel buildHeader(List<JCheckBox> rowBoxes) {
        JPanel header = new JPanel(new GridLayout(1, 8, 2, 0));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, Color.DARK_GRAY));

        JCheckBox selectAll = new JCheckBox("全选");
        selectAll.setHorizontalAlignment(JCheckBox.CENTER);
        selectAll.addActionListener(e -> {
            for (JCheckBox cb : rowBoxes) {
                cb.setSelected(selectAll.isSelected());
            }
            updateStatusLabel();
        });

        header.add(selectAll);
        header.add(headerLabel("学生姓名"));
        header.add(headerLabel("年级"));
        header.add(headerLabel("剩余课次"));
        header.add(headerLabel("课时安排"));
        header.add(headerLabel("学案设置"));
        header.add(headerLabel("修改信息"));
        header.add(headerLabel("反馈相关"));
        return header;
    }

    private static JLabel headerLabel(String text) {
        JLabel l = new JLabel(text, JLabel.CENTER);
        l.setFont(new Font("微软雅黑", Font.BOLD, 13));
        l.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return l;
    }

    /** 单行：勾选框 | 姓名 | 年级 | 剩余课次 | 4 个操作按钮 */
    private JPanel buildStudentRow(StudentInfo student, List<JCheckBox> rowBoxes) {
        JPanel row = new JPanel(new GridLayout(1, 8, 2, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        row.setPreferredSize(new Dimension(600, 40));
        row.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));

        int studentId = student.getStudentId();

        // 第0列 勾选框
        JCheckBox cb = new JCheckBox("", selectedIds.contains(studentId));
        cb.setHorizontalAlignment(JCheckBox.CENTER);
        cb.addItemListener(e -> {
            if (cb.isSelected()) {
                selectedIds.add(studentId);
            } else {
                selectedIds.remove(studentId);
            }
            updateStatusLabel();
        });
        rowBoxes.add(cb);
        row.add(cb);

        // 第1列 姓名
        row.add(cellLabel(student.getRealName()));

        // 第2列 年级
        row.add(cellLabel(student.getGradeValue()));

        // 第3列 剩余课次（≤5 红字加粗）
        JLabel remaining = remainingCell(student);
        row.add(remaining);

        // 第4-7列 操作按钮
        JButton btnSchedule = new JButton("课时安排");
        JButton btnStudy = new JButton("学案设置");
        JButton btnEdit = new JButton("修改信息");
        JButton btnFeedback = new JButton("反馈相关");
        for (JButton b : new JButton[]{btnSchedule, btnStudy, btnEdit, btnFeedback}) {
            b.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            row.add(b);
        }

        btnSchedule.addActionListener(e -> {
            ScheduleDialog.showDialog(this, student);
            refreshAll(); // 课时变化会改变剩余课次与所属分组
        });
        btnStudy.addActionListener(e -> StudyConfigDialog.showDialog(this, student));
        btnEdit.addActionListener(e -> {
            EditStudentInfoDialog.showDialog(this, student);
            refreshAll(); // 提交成功会刷新，这里兜底
        });
        btnFeedback.addActionListener(e -> StudentActionDialog.showDialog(this, student));
        return row;
    }

    private static JLabel cellLabel(String text) {
        JLabel l = new JLabel(text == null ? "" : text, JLabel.CENTER);
        l.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        return l;
    }

    /** 剩余课次单元格：无课时配置显示 —；≤5 红色加粗 */
    private JLabel remainingCell(StudentInfo student) {
        StudentSchedule schedule = ScheduleManager.load(student.getStudentId());
        JLabel label = new JLabel("—", JLabel.CENTER);
        label.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        if (schedule != null) {
            int remaining = schedule.remainingLessons();
            label.setText(String.valueOf(remaining));
            if (remaining <= 5) {
                label.setFont(new Font("微软雅黑", Font.BOLD, 13));
                label.setForeground(new Color(200, 0, 0));
            }
        }
        return label;
    }

    /** 勾选状态 -> 学生列表（按勾选顺序） */
    private List<StudentInfo> getSelectedStudents() {
        List<StudentInfo> list = new ArrayList<>();
        for (int id : selectedIds) {
            StudentInfo s = StudentReader.getStudentById(id);
            if (s != null) list.add(s);
        }
        return list;
    }

    private void updateStatusLabel() {
        int total = StudentReader.getStudentCount();
        statusLabel.setText(String.format("共读取 %d 名学生 · 已选择 %d", total, selectedIds.size()));
    }

    // ==================== 控制台日志（兼容旧调用点，不再写入界面） ====================

    public void log(String message) {
        System.out.println(message);
    }

    public void logError(String message) {
        System.err.println(message);
    }
}
