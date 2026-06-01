package me.xpyex.software.feedback.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import lombok.Setter;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.packet.in.AYDResponse;
import me.xpyex.software.feedback.packet.in.FinishedTaskPanel;
import me.xpyex.software.feedback.packet.in.SinglePanel;
import me.xpyex.software.feedback.packet.util.StudyContentsUtil;
import me.xpyex.software.feedback.ui.MainWindow;
import me.xpyex.software.feedback.util.AiyouduUtil;
import me.xpyex.software.feedback.util.GsonUtil;
import me.xpyex.software.feedback.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学生信息自动采集器
 * 工作流程：
 * 1. 启动并等待用户登录（无超时限制）
 * 2. 获取所有有 group 值的学生
 * 3. 逐个获取学生信息和上周周报，保存到文件
 */
public class StudentInfoCollector {
    private static final Logger log = LoggerFactory.getLogger(StudentInfoCollector.class.getSimpleName());
    private static final String defInfo = """
        {$name}{$start}-{$end}课堂反馈
        ♦️【累计学情数据】
             摸底词汇: {$vocabularyStart}个
             当前词汇: {$vocabulary}个
        📚词汇部分
             学习词汇: {$studyWord}个
             测试词汇: {$checkWord}个
             词汇增长: {$increaseWord}个
             复习词汇: {$reviewWord}个
        """;
    private static final String panelInfo = """
        {$icon}{$task}
             完成篇幅: {$amount}
             平均正确率: {$rate}
        """;
    private static final String[] icons = {
        "❤️‍🔥", "❤️‍", "💡", "🔮", "🔥", "🌟", "📕",
        "📙", "📒", "📔", "📗", "📘", "📓", "⛱️", "🫧"
    };
    // 每个学生的操作间隔时间（秒）
    private static final int SLEEP_SECONDS_BETWEEN_STUDENTS = 5;
    private static final String getProfileDateUrl = AiyouduUtil.apiUrl + "organiztion/student/myMonthData?studentId={$id}&startDate={$start}&endDate={$end}";
    @Setter
    public static String end;
    @Setter
    private static String start;
    @Setter
    private static List<StudentInfo> selectedStudents = null;  // GUI选中的学生列表

    /**
     * 启动采集流程
     */
    public static void start() {
        log.info("========================================");
        log.info("   学生信息自动采集器启动");
        log.info("========================================");

        // 检查是否已有学生数据，如果没有则提示用户先读取学生
        if (!StudentReader.hasStudents()) {
            log.warn("未检测到已保存的学生数据！");
            log.warn("请先执行【1】readStudents 操作读取学生信息");
            log.info("========================================");
            return;
        }

        // 检查 Token 是否存在
        if (AiyouduUtil.token == null || AiyouduUtil.token.isEmpty()) {
            log.warn("未检测到有效的 Token！");
            log.warn("请先执行【1】readStudents 操作获取 Token");
            log.info("========================================");
            return;
        }

        // 检查日期是否已设置
        if (start == null || end == null) {
            log.warn("未设置日期范围！");
            log.warn("请在 Main 中通过用户交互设置 start 和 end");
            log.info("========================================");
            return;
        }

        log.info("采集日期范围：{} ~ {}", start, end);

        try {
            // 步骤 1: 无需登录，直接使用已有的 Token 和学生数据
            log.info("√ 检测到已有学生数据和 Token，跳过登录步骤");

            // 步骤 2: 从 StudentReader 的静态 Map 中获取所有有 group 值的学生
            List<StudentInfo> validStudents = getValidStudentsWithGroup();

            if (validStudents.isEmpty()) {
                log.warn("未找到任何有 group 值的学生");
                return;
            }

            log.info("找到 {} 个有 group 值的学生", validStudents.size());

            // 步骤 3: 逐个获取学生信息和周报
            collectStudentInfos(validStudents);

            log.info("========================================");
            log.info("   所有学生信息采集完成！");
            log.info("========================================");

        } catch (Exception e) {
            log.error("采集过程中发生错误：", e);
        }
    }

    /**
     * 步骤 2: 获取所有有 group 值的学生
     * 从 StudentReader 的静态 Map 中读取，或使用GUI选中的学生列表
     *
     * @return 有效的学生列表
     */
    private static List<StudentInfo> getValidStudentsWithGroup() {
        // 如果GUI选中了学生，使用选中的列表
        if (selectedStudents != null && !selectedStudents.isEmpty()) {
            log.info(">>> 使用GUI选中的 {} 个学生", selectedStudents.size());
            List<StudentInfo> result = new ArrayList<>(selectedStudents);
            selectedStudents = null;  // 清空，下次不使用
            return result;
        }

        return StudentReader.copyStudents().values().stream().toList();
    }

    /**
     * 步骤 3: 逐个获取学生信息和上周周报
     *
     * @param students 学生列表
     */
    private static void collectStudentInfos(List<StudentInfo> students) {
        log.info(">>> 开始采集学生详细信息和周报...");

        int total = students.size();
        int current = 0;

        for (StudentInfo student : students) {
            current++;
            log.info("----------------------------------------");
            log.info("[{}/{}] 正在处理学生：{} [ID:{}] - 分组：{}",
                current, total,
                student.getRealName(), student.getStudentId(), student.getGroup());

            try {
                // 获取学生当前信息
                log.info("  正在获取学生当前信息...");

                log.info("  采集日期范围：{} ~ {}", start, end);

                // 获取指定日期范围的周报
                log.info("  正在获取周报...");
                FinishedTaskPanel finishedTask = getStudentFinished(
                    student.getStudentId(),
                    start,
                    end
                );

                if (finishedTask == null) {
                    log.error("  获取学生 {} 的周报失败", student.getRealName());
                    continue;
                }

                // 保存信息到文件
                saveStudentInfoToFile(student, finishedTask, start, end);

                log.info("  √ 学生 {} 信息采集完成", student.getRealName());

            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    log.warn("任务已中断");
                    return;
                }
                log.error("  处理学生 {} 时发生错误：", student.getRealName(), e);
            }

            // 如果不是最后一个学生，则等待一段时间
            if (current < total) {
                log.info("  等待 {} 秒后继续下一个学生...", SLEEP_SECONDS_BETWEEN_STUDENTS);
                if (TimeUtil.sleep(SLEEP_SECONDS_BETWEEN_STUDENTS * 1000) != null) {
                    log.error("等待被中断");
                }
            }
        }
    }

    /**
     * 保存学生信息到文件
     *
     * @param studentInfo  学生详细信息
     * @param finishedTask 上周周报
     * @param startDate    开始日期
     * @param endDate      结束日期
     */
    private static void saveStudentInfoToFile(StudentInfo studentInfo, FinishedTaskPanel finishedTask,
                                              String startDate, String endDate) {
        try {
            // 创建文件夹：学生名+ID
            String realName = studentInfo.getRealName();
            String folderName = studentInfo.getGroup().replace(":", ".") + "_" + realName + "_" + studentInfo.getStudentId();
            File studentFolder = new File("students/" + folderName);

            if (!studentFolder.exists()) {
                studentFolder.mkdirs();
                log.info("  创建文件夹：{}", studentFolder.getAbsolutePath());
            }

            // 文件名：日期.json
            String fileName = studentInfo.getRealName() + startDate + "-" + endDate + ".txt";
            File outputFile = new File(studentFolder, fileName);

            // 构建完整的 JSON 数据
            // 有姓名前提下，这里只写名字，不写姓
            String content = defInfo.replace("{$name}", realName.length() < 3 ? realName : realName.substring(realName.length() - 2))
                                 .replace("{$start}", startDate.substring(5).replace("-", "."))
                                 .replace("{$end}", endDate.substring(5).replace("-", "."))
                                 .replace("{$vocabularyStart}", "" + studentInfo.getDataInfo().getVocabularyStart())
                                 .replace("{$vocabulary}", "" + studentInfo.getDataInfo().getVocabulary())
                                 .replace("{$studyWord}", "" + finishedTask.getStudyWord())
                                 .replace("{$checkWord}", "" + finishedTask.getCheckWord())
                                 .replace("{$increaseWord}", "" + finishedTask.getIncreaseWord())
                                 .replace("{$reviewWord}", "" + finishedTask.getReviewWord());

            ArrayList<SinglePanel> tasks = new ArrayList<>();
            finishedTask.getWordAndReadList().forEach(panel -> taskFilter(panel, tasks));
            finishedTask.getListeningAndList().forEach(panel -> taskFilter(panel, tasks));

            for (int i = 0; i < icons.length && i < tasks.size(); i++) {
                SinglePanel singlePanel = tasks.get(i);
                String panel = panelInfo.replace("{$icon}", icons[i])
                                   .replace("{$task}", singlePanel.getTitle())
                                   .replace("{$amount}", singlePanel.getAmount())
                                   .replace("{$rate}", singlePanel.getRate().replace("正确率", ""));
                StudyContentsUtil.StudyType studyType = StudyContentsUtil.StudyType.getStudyTypeByName(singlePanel.getTitle());
                if (studyType != null) {
                    int averageDifficulty = StudyContentsUtil.getAverageDifficulty(
                        studentInfo.getStudentId(),
                        singlePanel.getTitle().contains("纸面") ? StudyContentsUtil.FinishedType.FINISHED_PAPER : StudyContentsUtil.FinishedType.FINISHED_ONLINE,
                        studyType,
                        Integer.parseInt(singlePanel.getAmount().replaceAll("[^0-9]", "")  //替换所有非数字的内容为空
                        ));
                    panel += "     平均难度: " + averageDifficulty + "\n";
                }
                if (singlePanel.getTitle().contains("口语")) {
                    panel = panel.replace("平均正确率", "平均得分");
                }
                content += panel;
            }

            // 写入文件
            Files.writeString(outputFile.toPath(), content);

            log.info("  信息已保存到：{}", outputFile.getAbsolutePath());

        } catch (IOException e) {
            log.error("  保存文件失败：", e);
        }
    }

    private static void taskFilter(SinglePanel panel, ArrayList<SinglePanel> tasks) {
        for (String s : panel.getContent().split("/")) {
            if (s.contains("%") || s.contains("分")) {
                panel.setRate(s);
            } else if (!s.contains("min") && !s.contains("星")) {
                panel.setAmount(s);
            }
        }
        if (panel.getAmount().trim().startsWith("0")) return;
        switch (panel.getTitle()) {
            case "精准读":
                tasks.add(panel.setTitle("精准阅读"));
                break;
            case "单词朗读", "句子朗读":
                tasks.add(panel.setTitle("口语训练(" + panel.getTitle() + ")"));
                break;
            case "语法掌握度":
            case "语感掌握度":
            case "语法掌握进度":
            case "语感掌握进度":
            case "时文阅读":
            case "时文悦读":
            case "分级视听":
            case "分级试听":
            case "分级阅读":
                break;  //忽略
            default:
                tasks.add(panel);  //不用特殊处理，直接添加
        }
    }

    public static FinishedTaskPanel getStudentFinished(int id, String startTime, String endTime) {
        String apiUrl = getProfileDateUrl
                            .replace("{$id}", "" + id)
                            .replace("{$start}", startTime)
                            .replace("{$end}", endTime);
        AYDResponse body = GsonUtil.parseObj(AiyouduUtil.getUrlWithToken(apiUrl), AYDResponse.class);
        if (body.isSuccess()) {
            return GsonUtil.getGson().fromJson(body.getDataAsJsonObject().getAsJsonObject("myDataInfo"), FinishedTaskPanel.class)
                       .setWordAndReadList(body.getDataAsJsonObject()
                                               .getAsJsonArray("wordAndReadList").asList()
                                               .stream()
                                               .map(e -> GsonUtil.getGson().fromJson(e, SinglePanel.class))
                                               .toList()
                       ).setListeningAndList(body.getDataAsJsonObject()
                                                 .getAsJsonArray("listeningAndList")
                                                 .asList()
                                                 .stream()
                                                 .map(e -> GsonUtil.getGson().fromJson(e, SinglePanel.class))
                                                 .toList()
                );
        }
        return null;
    }
}
