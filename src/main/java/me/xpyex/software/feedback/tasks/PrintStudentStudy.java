package me.xpyex.software.feedback.tasks;

import com.google.gson.JsonObject;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.packet.in.AYDResponse;
import me.xpyex.software.feedback.packet.in.StudyContentInfo;
import me.xpyex.software.feedback.packet.out.ApplyStudentCard;
import me.xpyex.software.feedback.packet.out.PrintMerge;
import me.xpyex.software.feedback.packet.out.PrintStudy;
import me.xpyex.software.feedback.packet.out.RecoverDay;
import me.xpyex.software.feedback.packet.out.RecoverMonth;
import me.xpyex.software.feedback.packet.util.StudyContentsUtil;
import me.xpyex.software.feedback.util.AiyouduUtil;
import me.xpyex.software.feedback.util.ConfigManager;
import me.xpyex.software.feedback.util.GsonUtil;
import me.xpyex.software.feedback.util.LogUtil;
import me.xpyex.software.feedback.util.NetworkUtil;
import me.xpyex.software.feedback.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学生批量打印处理任务
 * 工作流程:
 * 1. 检查学生词汇量 >= 200
 * 2. 检查剩余阅读篇幅 < 6
 * 3. 检查课程卡类型（按日结算且有剩余日期则退费）
 * 4. 续费一个月
 * 5. 根据配置文件打印篇数
 * 6. 执行退费（退掉刚续的一个月）
 */
public class PrintStudentStudy {
    // 配置文件路径
    public static final String CONFIG_FILE_PATH = "config/print.json";
    // 学生打印配置映射表（学生姓名 -> 篇数）
    public static final Map<String, Integer> studentPrintConfig = new HashMap<>();
    // 默认篇数（如果配置文件中找不到该学生）
    public static final int DEFAULT_ARTICLES = 2;
    private static final Logger log = LoggerFactory.getLogger(PrintStudentStudy.class.getSimpleName());
    // 需要人工核查的学生名单（学生姓名 -> 原因）
    private static final Map<String, String> studentsNeedManualCheck = new HashMap<>();// 每份最多 2 篇
    private static final int ARTICLES_PER_COPY = 2;

    // GUI选中的学生列表
    private static List<StudentInfo> selectedStudents = null;

    /**
     * 启动批量处理流程
     */
    public static void start() {
        startWithStudents(null);
    }

    /**
     * 启动批量处理流程（支持GUI选中学生）
     *
     * @param students GUI选中的学生列表，为null则处理所有学生
     */
    public static void startWithStudents(List<StudentInfo> students) {
        selectedStudents = students;

        LogUtil.line();
        log.info("   学生批量处理任务启动");
        LogUtil.line();

        try {
            // 步骤 1: 检查前置条件
            if (!checkPreconditions()) {
                log.error("前置条件不满足，任务终止");
                return;
            }

            // 步骤 2: 读取配置文件（每次从文件加载，确保使用最新配置）
            loadPrintConfig();

            // 步骤 3: 遍历所有学生进行处理
            processAllStudents();

            // 步骤 4: 输出需要人工核查的学生名单
            printManualCheckList();

            LogUtil.line();
            log.info("   批量处理任务完成！");
            LogUtil.line();

        } catch (Exception e) {
            log.error("批量处理过程中发生错误：", e);
        } finally {
            selectedStudents = null;  // 清空
        }
    }

    /**
     * 检查前置条件
     *
     * @return true 如果满足所有前置条件
     */
    private static boolean checkPreconditions() {
        // 检查 Token 是否存在
        if (AiyouduUtil.token == null || AiyouduUtil.token.isEmpty()) {
            log.error("Token 不存在！请先登录");
            return false;
        }

        // 检查是否有学生数据
        if (!StudentReader.hasStudents()) {
            log.error("没有学生数据！请先运行 StudentReader 读取学生信息");
            return false;
        }

        log.info("√ 前置条件检查通过");
        log.info("  - 已加载学生数量：{}", StudentReader.getStudentCount());
        return true;
    }

    /**
     * 读取打印配置文件
     * 配置文件格式：{"张三": 3, "李四": 5, ...}
     */
    public static void loadPrintConfig() {
        // 加载配置文件
        JsonObject print = ConfigManager.loadConfig("print");

        // 清空旧配置
        studentPrintConfig.clear();

        // 遍历 JSON 对象的所有键（学生姓名）
        for (String studentName : print.keySet()) {
            int articles = ConfigManager.getInt(print, studentName, DEFAULT_ARTICLES);
            studentPrintConfig.put(studentName, articles);
            log.debug("  加载配置：{} -> {} 篇", studentName, articles);
        }

        if (!studentPrintConfig.isEmpty()) {
            log.info("√ 配置文件读取成功，共加载 {} 个学生的配置", studentPrintConfig.size());
        } else {
            log.warn("配置文件 {} 不存在或为空，所有学生将使用默认篇数：{}", CONFIG_FILE_PATH, DEFAULT_ARTICLES);
        }
    }

    /**
     * 处理所有学生
     */
    private static void processAllStudents() {
        Map<Integer, StudentInfo> studentMap;

        // 如果GUI选中了学生，使用选中的列表
        if (selectedStudents != null && !selectedStudents.isEmpty()) {
            log.info(">>> 使用GUI选中的 {} 个学生", selectedStudents.size());
            studentMap = new HashMap<>();
            for (StudentInfo s : selectedStudents) {
                studentMap.put(s.getStudentId(), s);
            }
        } else {
            // 否则使用所有学生
            studentMap = StudentReader.getAllStudents();
        }

        int totalStudents = studentMap.size();
        int processedCount = 0;
        int successCount = 0;
        int skippedCount = 0;
        int manualCheckCount = 0;

        log.info(">>> 开始处理 {} 个学生...", totalStudents);

        for (StudentInfo student : studentMap.values()) {
            processedCount++;

            LogUtil.line("-", 40);
            log.info("[{}/{}] 正在处理学生：{} [ID:{}]",
                processedCount, totalStudents,
                student.getRealName(), student.getStudentId());

            try {
                // 步骤 1: 检查词汇量
                if (!checkVocabulary(student)) {
                    skippedCount++;
                    continue;
                }

                // 新增：检查剩余阅读篇幅（即已打印但未反馈的学案）
                int remainingArticles = getRemainingArticles(student);
                if (remainingArticles >= 6) {
                    String reason = String.format("剩余阅读篇幅过多：%d篇 >= 6篇", remainingArticles);
                    log.info("  ✗ {}，跳过打印和续费，记录到人工核查名单", reason);
                    studentsNeedManualCheck.put(student.getRealName(), reason);
                    manualCheckCount++;
                    continue;
                }
                log.info("  √ 剩余阅读篇幅检测通过：{}", remainingArticles);

                // 新增：检查学案总数限制（已有 + 新打印 <= 10）
                int articlesToPrint = studentPrintConfig.getOrDefault(student.getRealName(), DEFAULT_ARTICLES);
                int totalCount = remainingArticles + articlesToPrint;

                if (totalCount > 10) {
                    String reason = String.format("学案超限：已有%d篇 + 新打印%d篇 = %d篇 > 10篇",
                        remainingArticles, articlesToPrint, totalCount);
                    log.info("  ✗ {}", reason);
                    studentsNeedManualCheck.put(student.getRealName(), reason);
                    manualCheckCount++;
                    continue;
                }
                log.info("  √ 学案总数检测通过：已有{} + 新打印{} = {}", remainingArticles, articlesToPrint, totalCount);

                // 新增：检查课程卡类型，体验卡不打印
                int cardType = student.getCardType();
                if (cardType == 1) {
                    log.info("  ✗ 学生为体验卡类型，跳过打印和续费");
                    studentsNeedManualCheck.put(student.getRealName(), "学生为体验卡类型");
                    continue;
                }
                if (!student.getGroup().contains("周")) {
                    log.info("  ✗ 学生不在正式分组内，跳过打印和续费");
                    studentsNeedManualCheck.put(student.getRealName(), "学生不在正式分组内: " + student.getGroup());
                    continue;
                }
                log.info("  √ 课程卡类型检测通过：{}", cardType);

                // 步骤 2: 检查课程卡类型并处理退费
                handleCardTypeAndRefund(student);

                // 步骤 3: 续费一个月
                rechargeOneMonth(student);

                // 步骤 4: 打印学习材料
                printStudy(student);

                // 步骤 5: 执行退费（退掉刚续的一个月）
                refundRechargedDays(student);

                successCount++;
                log.info("√ 学生 {} 处理完成", student.getRealName());

            } catch (Exception e) {
                log.error("处理学生 {} 时发生异常：", student.getRealName(), e);
            }

            // 防止 API 限流，每个处理后休息 2 秒
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                log.warn("处理被中断");
                Thread.currentThread().interrupt();
                break;
            }
        }

        LogUtil.line();
        log.info("处理统计:");
        log.info("  - 总学生数：{}", totalStudents);
        log.info("  - 成功处理：{}", successCount);
        log.info("  - 跳过（词汇量不足）：{}", skippedCount);
        log.info("  - 跳过（剩余篇幅 >= 6）：{}", manualCheckCount);
        LogUtil.line();
    }

    /**
     * 步骤 1: 检查学生词汇量是否 >= 200
     *
     * @param student 学生信息
     * @return true 如果词汇量满足要求
     */
    private static boolean checkVocabulary(StudentInfo student) {
        if (student.getDataInfo() == null) {
            log.warn("学生 {} 的词汇量数据为空，跳过", student.getRealName());
            return false;
        }

        int vocabulary = student.getDataInfo().getVocabulary();

        if (vocabulary < 200) {
            log.info("  ✗ 词汇量 {} < 200，跳过该学生", vocabulary);
            return false;
        }

        log.info("  √ 词汇量检测通过：{}", vocabulary);
        return true;
    }

    /**
     * 步骤 2: 检查课程卡类型，如果是按日结算且有剩余日期，执行退费
     *
     * @param student 学生信息
     */
    private static void handleCardTypeAndRefund(StudentInfo student) {
        int cardType = student.getCardType();
        int expireDay = student.getExpireDay();

        log.info("  课程卡类型：{}, 剩余天数：{}", cardType, expireDay);

        // cardType: 1-体验，2-包月起步，3-按日结算
        if (cardType == 3 && expireDay > 0) {
            log.info("  → 检测到按日结算卡且有剩余 {} 天，执行退费...", expireDay);

            RecoverDay recoverDay = RecoverDay.of()
                                        .setStudentId(student.getStudentId())
                                        .setDay(expireDay);

            String requestJson = GsonUtil.toJsonStr(recoverDay, false);
            String response = AiyouduUtil.postUrlWithToken(
                AiyouduUtil.apiUrl + "platform/change/studentRecoverDay",
                requestJson
            );

            if (response != null && !response.isEmpty()) {
                log.info("  √ 退费请求已发送");
            } else {
                log.warn("  ! 退费响应为空，可能失败");
            }

            // 等待退费处理完成
            TimeUtil.sleep(1000);
        } else {
            log.debug("  - 不需要退费（卡类型：{}, 剩余天数：{})", cardType, expireDay);
        }
    }

    /**
     * 步骤 3: 对学生账号续费一个月
     *
     * @param student 学生信息
     */
    private static void rechargeOneMonth(StudentInfo student) {
        log.info("  → 正在续费一个月...");

        ApplyStudentCard rechargePacket = ApplyStudentCard.month(1).setStudentId(student.getStudentId());
        String requestJson = GsonUtil.toJsonStr(rechargePacket, false);
        String response = AiyouduUtil.postUrlWithToken(ApplyStudentCard.url, requestJson);

        if (response != null && !response.isEmpty()) {
            log.info("  √ 续费请求已发送");
        } else {
            log.error("  ✗ 续费请求失败");
        }
    }

    /**
     * 步骤 4: 根据配置文件打印对应篇数
     * 每份最多 2 篇，可以打印多份，奇数篇数则有一份只有一篇
     *
     * @param student 学生信息
     */
    private static void printStudy(StudentInfo student) {
        String studentName = student.getRealName();

        // 从配置中获取该学生的篇数，如果没有则使用默认值
        int totalArticles = studentPrintConfig.getOrDefault(studentName, DEFAULT_ARTICLES);

        log.info("  → 准备打印学案...");
        log.info("  学生：{}, 打印篇数：{}", studentName, totalArticles);

        String originClassName = student.getClassName();
        AYDResponse response1 = AYDResponse.of(AiyouduUtil.putUrlWithToken(StudentInfo.updateUrl, GsonUtil.toJsonStr(student.setClassName(student.getGroup()), false)));
        if (response1.isSuccess()) {
            log.info("  √ 临时修改班级请求成功");
        } else {
            log.warn("  ! 更新班级失败：{}", response1.getMessage());
        }
        // 临时修改学生所属班级为组名，以便在学案上突出，打印后恢复

        // 计算需要打印多少份
        int fullCopies = totalArticles / ARTICLES_PER_COPY;  // 完整的份数（每份 2 篇）
        int remainder = totalArticles % ARTICLES_PER_COPY;   // 剩余的篇数（0 或 1）

        log.info("  总篇数：{}, 每份最多 {} 篇", totalArticles, ARTICLES_PER_COPY);
        log.info("  完整份数：{} (每份 2 篇)", fullCopies);
        if (remainder > 0) {
            log.info("  额外份数：1 ({} 篇)", remainder);
        }

        // 打印完整份数（每份 2 篇）
        for (int i = 0; i < fullCopies; i++) {
            PrintStudy printPacket = PrintStudy.of().setStudentId(student.getStudentId()).setArticleNum(2);  // 每份 2 篇

            String requestJson = GsonUtil.toJsonStr(printPacket, false);
            AYDResponse response = AYDResponse.of(AiyouduUtil.postUrlWithToken(PrintStudy.url, requestJson));

            if (response.isSuccess()) {
                log.info("  √ 已生成第 {} 份 (2 篇)", i + 1);

                // 下载 PDF 文件
                // downloadPdfFromResponse(response, student);
            }

            // 短暂延迟
            TimeUtil.sleep(500);
        }

        // 如果有剩余，打印只有 1 篇的那份
        if (remainder > 0) {
            PrintStudy printPacket = PrintStudy.of().setStudentId(student.getStudentId()).setArticleNum(1);  // 每份 1 篇

            String requestJson = GsonUtil.toJsonStr(printPacket, false);
            AYDResponse response = AYDResponse.of(AiyouduUtil.postUrlWithToken(PrintStudy.url, requestJson));

            if (response.isSuccess()) {
                log.info("  √ 已生成最后 1 份 ({} 篇)", remainder);
                // 下载 PDF 文件
                // downloadPdfFromResponse(response, student);
            }
        }

        Set<Integer> ids = StudyContentsUtil.getStudyContents(AYDResponse.of(AiyouduUtil.getUrlWithToken(
                StudyContentsUtil.getPrintUrl(student, StudyContentsUtil.FinishedType.NOT_FINISHED, StudyContentsUtil.StudyType.NORMAL_READ, totalArticles, null)
            ))).stream()
                               .map(StudyContentInfo::getPrintId)
                               .collect(Collectors.toSet());
        AYDResponse response = AYDResponse.of(AiyouduUtil.postUrlWithToken(PrintMerge.url, GsonUtil.toJsonStr(PrintMerge.of().addAll(ids), false)));
        if (response.isSuccess()) {
            log.info("  √ 学案已合并完成，开始下载");
            downloadPdf(response.getData().getAsString(), student);
        }


        log.info("  √ 打印完成，共合并 {} 份，总计 {} 篇",
            fullCopies + (remainder > 0 ? 1 : 0), totalArticles);

        AYDResponse response2 = AYDResponse.of(AiyouduUtil.putUrlWithToken(StudentInfo.updateUrl, GsonUtil.toJsonStr(student.setClassName(originClassName), false)));
        if (response2.isSuccess()) {
            log.info("  √ 已恢复原班级");
        } else {
            log.warn("  ! 恢复班级失败");
        }
    }

    /**
     * 下载 PDF 文件到指定目录
     *
     * @param pdfUrl  PDF 下载链接
     * @param student 学生信息
     */
    private static void downloadPdf(String pdfUrl, StudentInfo student) {
        try {
            // 构建文件路径：print/分组/xxx.pdf
            String group = student.getGroup() != null ? student.getGroup().trim() : "未分组";
            // 清理分组名称中的非法字符
            group = group.replaceAll("[/\\:*?\"<>|]", "_");

            // 从 URL 中提取文件名（最后一截）
            String fileName = pdfUrl.substring(pdfUrl.lastIndexOf('/') + 1);
            // 如果文件名有查询参数，去掉
            if (fileName.contains("?")) {
                fileName = fileName.substring(0, fileName.indexOf('?'));
            }

            fileName = student.getRealName() + "_" + fileName;

            log.info("  → 文件名：{}", fileName);

            // 创建目录
            File groupDir = new File("print", group);
            NetworkUtil.downloadFile(pdfUrl, groupDir, fileName);

        } catch (Exception e) {
            log.error("  ✗ 下载 PDF 时发生异常：", e);
        }
    }

    /**
     * 获取学生剩余的待反馈阅读篇幅数量
     * 参考 StudyContentsUtil 的实现
     *
     * @param student 学生信息
     * @return 剩余篇幅数量，-1 表示获取失败
     */
    private static int getRemainingArticles(StudentInfo student) {
        String url = StudyContentsUtil.getPrintUrl(student, StudyContentsUtil.FinishedType.NOT_FINISHED, StudyContentsUtil.StudyType.NORMAL_READ, 100, null);

        try {
            String responseJson = AiyouduUtil.getUrlWithToken(url);
            AYDResponse response = GsonUtil.parseObj(responseJson, AYDResponse.class);

            if (response.isSuccess() && "成功".equals(response.getMessage())) {
                // 从 records 数组中获取待反馈的文章列表
                List<StudyContentInfo> records = response.getDataAsJsonObject()
                                                     .getAsJsonArray("records")
                                                     .asList()
                                                     .stream()
                                                     .map(e -> GsonUtil.parseObj(GsonUtil.toJsonStr(e.getAsJsonObject(), false), StudyContentInfo.class))
                                                     .toList();

                log.debug("  学生 {} 的待反馈文章数量：{}", student.getRealName(), records.size());
                return records.size();
            } else {
                log.warn("  获取剩余篇幅失败：{}", response.getMessage());
                return -1;
            }
        } catch (Exception e) {
            log.error("  获取剩余篇幅时发生异常：", e);
            return -1;
        }
    }


    /**
     * 输出需要人工核查的学生名单
     */
    private static void printManualCheckList() {
        if (studentsNeedManualCheck.isEmpty()) {
            LogUtil.line();
            log.info("√ 无需人工核查的学生");
            LogUtil.line();
            return;
        }

        LogUtil.line();
        log.info("⚠️  以下学生需要人工核查：");
        LogUtil.line();
        log.info("{} - {}", "学生姓名", "原因");
        LogUtil.line("-", 40);

        for (Map.Entry<String, String> entry : studentsNeedManualCheck.entrySet()) {
            log.info("{} - {}", entry.getKey(), entry.getValue());
        }

        LogUtil.line("-", 40);
        log.info("共 {} 个学生需要人工核查", studentsNeedManualCheck.size());
        LogUtil.line("-", 40);
    }

    /**
     * 步骤 5: 执行退费，把刚刚续的一个月退掉
     *
     * @param student 学生信息
     */
    private static void refundRechargedDays(StudentInfo student) {
        log.info("  → 正在退费（退掉刚续的一个月）...");
        int month = 1;

        RecoverMonth recover = RecoverMonth.of()
                                   .setStudentId(student.getStudentId())
                                   .setMonth(month);

        String requestJson = GsonUtil.toJsonStr(recover, false);
        String response = AiyouduUtil.postUrlWithToken(RecoverMonth.url, requestJson);

        if (response != null && !response.isEmpty()) {
            log.info("  √ 退费请求已发送（{} 月）", month);
        } else {
            log.error("  ✗ 退费请求失败");
        }
    }
}
