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
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
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
 * 只维护"学生有没有来"：月历上点选「来了(绿)」或「没来(红)」，加课=来了、请假=没来。
 * <ul>
 *     <li>上课时段可多组（如 周五晚上 + 周六上午），仅用于分组、排课提示与自动补记</li>
 *     <li>自动补记只从"上次修改日"之后开始：一周没开软件，开机后会把上次修改日到昨天的排课日自动补上；
 *         上次修改日之前的历史不会被自动改动（中途调课不会多算课时），必要时手动点选修正</li>
 *     <li>排课日以浅黄底提示，方便对着排课补录</li>
 * </ul>
 * 任何改动都会立即写入 config/schedule/{真实姓名}_{studentId}.json。
 */
public class ScheduleDialog extends JDialog {
    private static final Color COLOR_ATTENDED = new Color(208, 248, 208); // 来了=绿
    private static final Color COLOR_ABSENT = new Color(255, 120, 120);   // 没来=红
    private static final Color COLOR_SCHEDULED = new Color(255, 249, 196); // 排课日提示=浅黄
    private static final Color COLOR_PAST = new Color(235, 235, 235);

    private final StudentInfo student;
    private final JTextField startField;
    private final JTextField totalField;
    private final JLabel infoLabel = new JLabel();
    private final JPanel periodsPanel = new JPanel();
    private final List<JPanel> periodRows = new ArrayList<>();
    private final JLabel monthTitle = new JLabel();
    private final JPanel calendarPanel = new JPanel();
    private StudentSchedule schedule;
    private JToggleButton btnAttendedMode;
    private JToggleButton btnAbsentMode;
    private int viewYear;
    private int viewMonth; // 1-12
    private boolean building = true; // 构建期屏蔽事件

    private ScheduleDialog(JFrame parent, StudentInfo student) {
        super(parent, "课时安排 - " + student.getRealName(), true);
        this.student = student;

        StudentSchedule loaded = ScheduleManager.load(student.getStudentId());
        LocalDate today = LocalDate.now();
        boolean existed = loaded != null;
        if (existed) {
            this.schedule = loaded;
        } else {
            this.schedule = StudentSchedule.of()
                                .setStudentId(student.getStudentId())
                                .setStartDate(ScheduleManager.toIso(today))
                                .setTotalLessons(30);
        }
        this.schedule.setRealName(student.getRealName());
        if (existed) {
            // 开机/打开时先把"上次修改日 → 昨天"的排课日补齐（一周没开也不会漏）
            ScheduleManager.autoFillAndSave(this.schedule);
        }

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
        setMinimumSize(new Dimension(620, 720));
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

    /**
     * 顶部：开始日期/总课时 + 上课时段（可多组）
     */
    private JPanel buildTopPanel() {
        JPanel top = new JPanel(new BorderLayout(8, 6));

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

        periodsPanel.setLayout(new javax.swing.BoxLayout(periodsPanel, javax.swing.BoxLayout.Y_AXIS));
        for (SchedulePeriod p : schedule.getRealPeriods()) {
            addPeriodRow(p.getWeeklyDay(), p.getTimeSlot());
        }
        if (periodRows.isEmpty()) {
            addPeriodRow(null, null); // 默认一行，便于直接选择
        }
        JScrollPane periodScroll = new JScrollPane(periodsPanel);
        periodScroll.setPreferredSize(new Dimension(570, 88));
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(line2, BorderLayout.NORTH);
        wrapper.add(periodScroll, BorderLayout.CENTER);
        top.add(wrapper, BorderLayout.CENTER);

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

    /**
     * 新增一行时段选择：星期 + 时段 + 删除
     */
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

    /**
     * 中央：月历
     */
    private JPanel buildCalendarCenter() {
        JPanel center = new JPanel(new BorderLayout(4, 4));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        btnAttendedMode = new JToggleButton("来了(绿)", true);
        btnAbsentMode = new JToggleButton("没来(红)", false);
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(btnAttendedMode);
        modeGroup.add(btnAbsentMode);

        JButton prev = new JButton("<");
        JButton next = new JButton(">");
        monthTitle.setFont(new Font("微软雅黑", Font.BOLD, 15));
        prev.addActionListener(e -> shiftMonth(-1));
        next.addActionListener(e -> shiftMonth(1));

        toolbar.add(btnAttendedMode);
        toolbar.add(btnAbsentMode);
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

        // 固定 7 列，行数自适应（0 = 行数不限），这样 5 周的月份不会多出一整行空白
        calendarPanel.setLayout(new GridLayout(0, 7, 2, 2));
        calendarPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        body.add(calendarPanel, BorderLayout.CENTER);
        center.add(body, BorderLayout.CENTER);

        JLabel legend = new JLabel("图例：绿=来了(计入)  红=没来(不计入)  浅黄=排课日(参考)  深灰边=今天；过去/未来都可点选");
        legend.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        center.add(legend, BorderLayout.SOUTH);
        return center;
    }

    // ==================== 逻辑 ====================

    /**
     * 读取表单（开始日期/总课时/时段）并落盘 + 刷新；改动时段或开始日时，自动补记起点重置为今天
     */
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
        String oldStart = schedule.getStartDate();
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

        boolean existedBefore = ScheduleManager.exists(schedule.getStudentId());
        List<SchedulePeriod> oldPeriods = new ArrayList<>(schedule.getRealPeriods());
        List<SchedulePeriod> newPeriods = collectPeriods();
        boolean changed = !Objects.equals(oldPeriods, newPeriods)
                              || !Objects.equals(oldStart, schedule.getStartDate());
        schedule.setPeriods(newPeriods);

        // 首次建立配置，或改动了时段/开始日：自动补记起点置为今天（此后按新排课自动补，历史不动）
        if (!existedBefore || changed) {
            schedule.setLastModify(ScheduleManager.toIso(LocalDate.now()));
        }

        boolean hasPeriod = !schedule.getPeriods().isEmpty();
        if (existedBefore || (hasPeriod && schedule.getStartDate() != null)) {
            ScheduleManager.autoFillAndSave(schedule);
        }

        updateInfoLabel();
        rebuildCalendar();
    }

    private void updateInfoLabel() {
        String periodsText = schedule.getPeriods().isEmpty()
                                 ? "未设置上课时段" : String.join("、", schedule.groupKeys());
        int remaining = schedule.remainingLessons();
        String lastModify = schedule.getLastModify() == null ? "未设置" : schedule.getLastModify();
        infoLabel.setText(String.format("  已上 %d 次   剩余 %d 次   没来 %d 次   [%s]   上次修改:%s",
            schedule.attendedCount(), remaining, schedule.getLeaveDates().size(), periodsText, lastModify));
        infoLabel.setForeground(remaining <= 5 ? new Color(200, 0, 0) : Color.BLACK);
    }

    private void shiftMonth(int delta) {
        LocalDate target = LocalDate.of(viewYear, viewMonth, 1).plusMonths(delta);
        viewYear = target.getYear();
        viewMonth = target.getMonthValue();
        rebuildCalendar();
    }

    /**
     * 重画月历。日期区固定为 7 列（周一…周日），行数由内容自动换行：
     * 先在开头补上"1 号之前的空格"，使 1 号落在它对应的星期列；之后逐天往后放，每 7 个自动换行。
     * 因此每月第一行/最后一行可能不足 7 个日期（例如 2026-09-01 是周二，第一行只有 6 个日期），属正常日历排布。
     */
    private void rebuildCalendar() {
        calendarPanel.removeAll();
        monthTitle.setText(viewYear + " 年 " + String.format("%02d", viewMonth) + " 月");

        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.of(viewYear, viewMonth);
        int leadingBlanks = ym.atDay(1).getDayOfWeek().getValue() - 1; // 周一起始：周一=0…周日=6
        int daysInMonth = ym.lengthOfMonth();

        // 1) 月初空格：让 1 号对到正确的星期列
        for (int i = 0; i < leadingBlanks; i++) {
            calendarPanel.add(new JLabel(""));
        }
        // 2) 依次放入 1 号到最后一天（7 列会自动换行）
        for (int day = 1; day <= daysInMonth; day++) {
            calendarPanel.add(buildDayButton(ym.atDay(day), today));
        }
        // 3) 月末无需补位：网格里没有组件的格子不会绘制任何东西

        calendarPanel.revalidate();
        calendarPanel.repaint();
    }

    private JButton buildDayButton(LocalDate date, LocalDate today) {
        String iso = ScheduleManager.toIso(date);
        boolean isAttended = schedule.getAttendedDates().contains(iso);
        boolean isAbsent = schedule.getLeaveDates().contains(iso);
        boolean isScheduled = schedule.isScheduledOn(date);
        boolean isPast = date.isBefore(today);
        boolean isToday = date.equals(today);

        JButton btn = new JButton(String.valueOf(date.getDayOfMonth()));
        // Windows 主题下让按钮“矩形化”，确保底色真实显示（否则会被渐变外观吞掉，只剩描边）
        btn.putClientProperty("JButton.buttonType", "square");
        btn.setContentAreaFilled(true);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setRolloverEnabled(false);
        btn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        btn.setMargin(new Insets(2, 2, 2, 2));

        // 底色优先级：来了(绿) > 没来(红) > 排课日提示(浅黄) > 已过去(灰) > 白
        Color markerBorder = null;
        Color base;
        if (isAttended) {
            base = COLOR_ATTENDED;
            markerBorder = new Color(90, 170, 90);
        } else if (isAbsent) {
            base = COLOR_ABSENT;
            markerBorder = new Color(190, 50, 50);
        } else if (isScheduled) {
            base = COLOR_SCHEDULED;
            markerBorder = new Color(220, 200, 90);
        } else if (isPast) {
            base = COLOR_PAST;
        } else {
            base = Color.WHITE;
        }
        btn.setBackground(base);
        btn.setForeground(Color.BLACK);
        btn.setBorder(BorderFactory.createLineBorder(markerBorder != null ? markerBorder : new Color(0xCCCCCC), 1));

        if (isToday) {
            btn.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70), 2));
            btn.setFont(new Font("微软雅黑", Font.BOLD, 12));
        }

        String tip = iso
                         + (isAttended ? "（来了）" : "") + (isAbsent ? "（没来）" : "")
                         + (isScheduled ? "（排课日）" : "")
                         + (isPast ? "（已过去，可点选修改）" : "")
                         + (isToday ? "（今天）" : "");
        btn.setToolTipText(tip);

        // 过去、今天、未来都可点选：来了=计入；没来=不计入
        btn.addActionListener(e -> toggleCalendarDate(date));
        return btn;
    }

    /**
     * 月历点选：按当前模式标记该日期"来了/没来"，随后落盘刷新
     */
    private void toggleCalendarDate(LocalDate date) {
        if (btnAttendedMode.isSelected()) {
            ScheduleManager.toggleAttended(schedule, date);
        } else {
            ScheduleManager.toggleLeave(schedule, date);
        }
        ScheduleManager.save(schedule);
        updateInfoLabel();
        rebuildCalendar();
    }
}
