package me.xpyex.software.feedback.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import me.xpyex.software.feedback.data.StudentSchedule;
import me.xpyex.software.feedback.data.StudentSchedule.SchedulePeriod;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.schedule.ScheduleManager;

/**
 * 课时安排弹窗（单名学生）。
 * <p>
 * 一个学生可以有多个上课时段（如 周五晚上 + 周六上午）。本弹窗支持动态增删时段行：
 * <ul>
 *     <li>开始日期 / 总课时 + 实时"已上课次 / 剩余课次"</li>
 *     <li>上课时段列表：每行"星期 + 时段"，可添加/删除</li>
 *     <li>可切换月份的月历：红=请假，蓝=加课，绿=已上课(仅历史)；过去日期也可点选补录</li>
 *     <li>今天以描边标记，不计入"已上课"，也不套红</li>
 * </ul>
 * 任何改动都会立即写入 config/schedule/{真实姓名}_{studentId}.json。
 * 中途修改上课时段后新时段自修改当天起算，不自动改动此前的旧历史；过去可在月历上手动修正。
 */
public class ScheduleDialog extends JDialog {
    private static final Color COLOR_LEAVE = new Color(255, 120, 120);
    private static final Color COLOR_EXTRA = new Color(120, 175, 255);
    private static final Color COLOR_ATTENDED = new Color(208, 248, 208);
    private static final Color COLOR_PAST = new Color(235, 235, 235);

    private final StudentInfo student;
    private StudentSchedule schedule;

    private final JTextField startField;
    private final JTextField totalField;
    private final JLabel infoLabel = new JLabel();
    private final JPanel periodsPanel = new JPanel();
    private final List<JPanel> periodRows = new ArrayList<>();

    private JToggleButton btnLeaveMode;
    private JToggleButton btnExtraMode;

    private int viewYear;
    private int viewMonth; // 1-12
    private final JLabel monthTitle = new JLabel();
    private final JPanel calendarPanel = new JPanel();

    private boolean building = true; // 构建期屏蔽事件

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
        // 用最新真实姓名命名文件（config/schedule/{真实姓名}_{studentId}.json）
        this.schedule.setRealName(student.getRealName());
        LocalDate anchor = ScheduleManager.parseIso(this.schedule.getStartDate());
        anchor = anchor == null ? today : anchor;
        this.viewYear = anchor.getYear();
        this.viewMonth = anchor.getMonthValue();

        startField = new JTextField(schedule.getStartDate() == null ? "" : schedule.getStartDate(), 9);
        totalField = new JTextField(String.valueOf(schedule.getTotalLessons()), 4);

        initUI();
        building = false;
        updateInfoLabel();
        rebuildCalendar();
        pack();
        setMinimumSize(new Dimension(600, 700));
        setLocationRelativeTo(parent);
    }

    public static void showDialog(JFrame parent, StudentInfo student) {
        new ScheduleDialog(parent, student).setVisible(true);
    }

    // ==================== UI 构建 ====================

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(8, 8));
        main.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        main.add(buildTopPanel(), BorderLayout.NORTH);
        main.add(buildCalendarCenter(), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton close = new JButton("完成");
        close.addActionListener(e -> dispose());
        bottom.add(close);
        main.add(bottom, BorderLayout.SOUTH);

        add(main);
    }

    /** 顶部：开始日期/总课时 + 上课时段（可多组） */
    private JPanel buildTopPanel() {
        JPanel top = new JPanel(new BorderLayout(8, 6));

        // 行1：开始日期、总课时、已上/剩余
        JPanel line1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        line1.add(new JLabel("开始日期"));
        startField.setToolTipText("yyyy-MM-dd，回车或失焦后生效");
        line1.add(startField);
        line1.add(new JLabel("总课时"));
        totalField.setToolTipText("正整数");
        line1.add(totalField);
        infoLabel.setFont(new Font("微软雅黑", Font.BOLD, 13));
        line1.add(infoLabel);
        top.add(line1, BorderLayout.NORTH);

        // 行2：上课时段标题 + 添加按钮
        JPanel line2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        JLabel title = new JLabel("上课时段（可多组，例如 周五晚上 + 周六上午）：");
        title.setFont(new Font("微软雅黑", Font.BOLD, 13));
        JButton add = new JButton("添加时段");
        add.addActionListener(e -> {
            addPeriodRow(null, null);
            persistAndRefresh();
        });
        line2.add(title);
        line2.add(add);

        // 时段行容器（放在中间以下，用嵌套布局）
        periodsPanel.setLayout(new javax.swing.BoxLayout(periodsPanel, javax.swing.BoxLayout.Y_AXIS));
        for (SchedulePeriod p : schedule.getRealPeriods()) {
            addPeriodRow(p.getWeeklyDay(), p.getTimeSlot());
        }
        if (periodRows.isEmpty()) {
            addPeriodRow(null, null); // 默认一行，便于直接选择
        }
        JScrollPane periodScroll = new JScrollPane(periodsPanel);
        periodScroll.setPreferredSize(new Dimension(560, 88));
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(line2, BorderLayout.NORTH);
        wrapper.add(periodScroll, BorderLayout.CENTER);
        top.add(wrapper, BorderLayout.CENTER);

        // 开始/总课时生效时机：回车或失焦
        startField.addActionListener(e -> persistAndRefresh());
        totalField.addActionListener(e -> persistAndRefresh());
        startField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                persistAndRefresh();
            }
        });
        totalField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                persistAndRefresh();
            }
        });
        return top;
    }

    /** 新增一行时段选择：星期 + 时段 + 删除 */
    private void addPeriodRow(String weeklyDay, String timeSlot) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JComboBox<String> weekBox = new JComboBox<>(StudentSchedule.WEEK_DAYS.toArray(new String[0]));
        JComboBox<String> slotBox = new JComboBox<>(StudentSchedule.TIME_SLOTS.toArray(new String[0]));
        if (weeklyDay != null) weekBox.setSelectedItem(weeklyDay);
        if (timeSlot != null) slotBox.setSelectedItem(timeSlot);
        weekBox.setPreferredSize(new Dimension(92, 28));
        slotBox.setPreferredSize(new Dimension(92, 28));

        JButton remove = new JButton("删除");
        remove.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        remove.addActionListener(e -> {
            periodsPanel.remove(row);
            periodRows.remove(row);
            periodsPanel.revalidate();
            periodsPanel.repaint();
            if (!building) persistAndRefresh();
        });

        row.add(new JLabel("星期"));
        row.add(weekBox);
        row.add(new JLabel("时段"));
        row.add(slotBox);
        row.add(remove);

        weekBox.addActionListener(e -> {
            if (!building) persistAndRefresh();
        });
        slotBox.addActionListener(e -> {
            if (!building) persistAndRefresh();
        });

        // 记住组件以便序列化读取
        row.putClientProperty("weekBox", weekBox);
        row.putClientProperty("slotBox", slotBox);

        periodRows.add(row);
        periodsPanel.add(row);
    }

    private List<SchedulePeriod> collectPeriods() {
        List<SchedulePeriod> list = new ArrayList<>();
        for (JPanel row : periodRows) {
            JComboBox<?> week = (JComboBox<?>) row.getClientProperty("weekBox");
            JComboBox<?> slot = (JComboBox<?>) row.getClientProperty("slotBox");
            if (week == null || slot == null) continue;
            String w = week.getSelectedItem() == null ? null : week.getSelectedItem().toString();
            String t = slot.getSelectedItem() == null ? null : slot.getSelectedItem().toString();
            if (w != null && !w.isBlank()) list.add(SchedulePeriod.of().setWeeklyDay(w).setTimeSlot(t));
        }
        return list;
    }

    /** 中央：月历 */
    private JPanel buildCalendarCenter() {
        JPanel center = new JPanel(new BorderLayout(4, 4));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        btnLeaveMode = new JToggleButton("请假(红)", true);
        btnExtraMode = new JToggleButton("加课(蓝)", false);
        javax.swing.ButtonGroup modeGroup = new javax.swing.ButtonGroup();
        modeGroup.add(btnLeaveMode);
        modeGroup.add(btnExtraMode);

        JButton prev = new JButton("<");
        JButton next = new JButton(">");
        monthTitle.setFont(new Font("微软雅黑", Font.BOLD, 15));
        prev.addActionListener(e -> shiftMonth(-1));
        next.addActionListener(e -> shiftMonth(1));

        toolbar.add(btnLeaveMode);
        toolbar.add(btnExtraMode);
        toolbar.add(prev);
        toolbar.add(monthTitle);
        toolbar.add(next);
        center.add(toolbar, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout());
        JPanel weekHeader = new JPanel(new GridLayout(1, 7));
        for (String day : StudentSchedule.WEEK_DAYS) {
            JLabel l = new JLabel(day, JLabel.CENTER);
            l.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            l.setOpaque(true);
            l.setBackground(new Color(240, 240, 245));
            weekHeader.add(l);
        }
        body.add(weekHeader, BorderLayout.NORTH);

        calendarPanel.setLayout(new GridLayout(6, 7, 2, 2));
        calendarPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        body.add(calendarPanel, BorderLayout.CENTER);
        center.add(body, BorderLayout.CENTER);

        JLabel legend = new JLabel("图例：红=请假  蓝=加课  绿=已上课  描边=今天；过去日期也可点选（补录请假/加课以修正已上课）");
        legend.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        center.add(legend, BorderLayout.SOUTH);
        return center;
    }

    // ==================== 逻辑 ====================

    /** 读取表单：开始日期/总课时/时段并落盘 + 重算 + 刷新 */
    private void persistAndRefresh() {
        // 总课时
        int total = schedule.getTotalLessons();
        String totalText = totalField.getText().trim();
        if (!totalText.isEmpty()) {
            try {
                total = Math.max(0, Integer.parseInt(totalText));
            } catch (NumberFormatException e) {
                System.out.println("课时安排：总课时输入非法，已保留上次有效值");
                totalField.setText(String.valueOf(schedule.getTotalLessons()));
            }
        }
        schedule.setTotalLessons(total);

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

        // 上课时段（多组）
        boolean existedBefore = ScheduleManager.exists(schedule.getStudentId());
        List<SchedulePeriod> oldPeriods = new ArrayList<>(schedule.getRealPeriods());
        List<SchedulePeriod> newPeriods = collectPeriods();

        // 中途修改时段：新时段自修改当天起算（updateDate=今天），不去自动改动此前的旧历史；
        // 全新首次配置则不加 updateDate，从 startDate 补齐
        if (existedBefore && !java.util.Objects.equals(oldPeriods, newPeriods)) {
            schedule.setUpdateDate(ScheduleManager.toIso(LocalDate.now()));
        }
        schedule.setPeriods(newPeriods);

        // 落盘：全新配置需选好时段+日期才落盘；已存在的一律落盘
        boolean hasPeriod = !schedule.getPeriods().isEmpty();
        if (hasPeriod && schedule.getStartDate() != null) {
            ScheduleManager.recomputeAndSave(schedule);
        } else if (existedBefore) {
            ScheduleManager.save(schedule);
        }

        updateInfoLabel();
        rebuildCalendar();
    }

    private void updateInfoLabel() {
        String periodsText = schedule.getPeriods().isEmpty()
            ? "未设置上课时段" : schedule.groupKeys().stream().collect(Collectors.joining("、"));
        int remaining = schedule.remainingLessons();
        infoLabel.setText(String.format("  已上 %d 次   剩余 %d 次   [%s]",
            schedule.attendedCount(), remaining, periodsText));
        infoLabel.setForeground(remaining <= 5 ? new Color(200, 0, 0) : Color.BLACK);
    }

    private void shiftMonth(int delta) {
        LocalDate target = LocalDate.of(viewYear, viewMonth, 1).plusMonths(delta);
        viewYear = target.getYear();
        viewMonth = target.getMonthValue();
        rebuildCalendar();
    }

    private void rebuildCalendar() {
        calendarPanel.removeAll();
        monthTitle.setText(viewYear + " 年 " + String.format("%02d", viewMonth) + " 月");

        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.of(viewYear, viewMonth);
        LocalDate first = ym.atDay(1);
        int offset = first.getDayOfWeek().getValue() - 1; // 周一起始
        int daysInMonth = ym.lengthOfMonth();

        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < offset; i++) dates.add(null);
        for (int d = 1; d <= daysInMonth; d++) dates.add(ym.atDay(d));
        while (dates.size() % 7 != 0) dates.add(null);

        for (LocalDate date : dates) {
            if (date == null) {
                calendarPanel.add(new JLabel(""));
                continue;
            }
            calendarPanel.add(buildDayButton(date, today));
        }

        calendarPanel.revalidate();
        calendarPanel.repaint();
    }

    private JButton buildDayButton(LocalDate date, LocalDate today) {
        String iso = ScheduleManager.toIso(date);
        boolean isLeave = schedule.getLeaveDates().contains(iso);
        boolean isExtra = schedule.getExtraDates().contains(iso);
        boolean isAttended = schedule.getAttendedDates().contains(iso);
        boolean isPast = date.isBefore(today);
        boolean isToday = date.equals(today);

        JButton btn = new JButton(String.valueOf(date.getDayOfMonth()));
        btn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        btn.setMargin(new Insets(2, 2, 2, 2));

        // 颜色优先级：请假 > 加课 > 已上课；今天的颜色在其之上叠加描边（不套红）
        if (isLeave) btn.setBackground(COLOR_LEAVE);
        else if (isExtra) btn.setBackground(COLOR_EXTRA);
        else if (isAttended) btn.setBackground(COLOR_ATTENDED);
        else if (isPast) btn.setBackground(COLOR_PAST);
        else btn.setBackground(Color.WHITE);

        if (isToday) {
            btn.setBorder(BorderFactory.createLineBorder(new Color(0, 110, 200), 2));
            btn.setFont(new Font("微软雅黑", Font.BOLD, 12));
            btn.setForeground(Color.BLACK);
        }

        String tip = iso
            + (isLeave ? "（请假）" : "") + (isExtra ? "（加课）" : "")
            + (isAttended ? "（已上课）" : "") + (isPast ? "（已过去，可点选修改）" : "")
            + (isToday ? "（今天，不计为自动已上课）" : "");
        btn.setToolTipText(tip);

        // 过去、今天、未来的日期都可以点选，用于补录请假/加课、修正已上课记录
        btn.addActionListener(e -> toggleCalendarDate(date));
        return btn;
    }

    /** 月历点选：按当前模式切换该日期的请假/加课标记，随后重算并落盘 */
    private void toggleCalendarDate(LocalDate date) {
        boolean changed = btnLeaveMode.isSelected()
            ? ScheduleManager.toggleLeave(schedule, date)
            : ScheduleManager.toggleExtra(schedule, date);
        if (changed) {
            ScheduleManager.recomputeAndSave(schedule);
            updateInfoLabel();
            rebuildCalendar();
        }
    }
}
