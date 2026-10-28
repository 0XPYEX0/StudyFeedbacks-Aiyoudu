package me.xpyex.software.feedback.data;

import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 单个学生的课时配置（对应 config/schedule/{真实姓名}_{studentId}.json）。
 * <p>
 * 现在只记录两个基本信息：
 * <ul>
 *     <li>{@code startDate}：当前计费开始日期</li>
 *     <li>{@code totalLessons}：总次数</li>
 * </ul>
 * 上课/请假等考勤不再在本地维护（月历已移除）；「剩余课次」由外部数据源计算，
 * 见 {@code ScheduleManager.remainingLessons(...)} 的占位实现。
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class StudentSchedule {
    private int studentId;
    /** 冗余保存真实姓名，用于生成可读的文件名（config/schedule/{真实姓名}_{studentId}.json） */
    private String realName;
    /** 当前计费开始日期，ISO yyyy-MM-dd */
    private String startDate;
    /** 总次数（总课时） */
    private int totalLessons = 30;
    private Map<String, Integer> countsInMonthAfterDate = new HashMap<>();  // 单个月的某一天(从这天之后这个月上了几次), 次数
}
