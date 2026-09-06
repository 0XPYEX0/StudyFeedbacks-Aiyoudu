package me.xpyex.software.feedback.tasks.studyPrepare;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.ExtensionMethod;
import me.xpyex.software.feedback.data.StudyConfig;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.packet.in.AYDResponse;
import me.xpyex.software.feedback.packet.in.StudyContentInfo;
import me.xpyex.software.feedback.packet.out.ApplyStudentCard;
import me.xpyex.software.feedback.packet.out.PrintMerge;
import me.xpyex.software.feedback.packet.out.PrintStudy;
import me.xpyex.software.feedback.packet.out.RecoverDay;
import me.xpyex.software.feedback.packet.out.RecoverMonth;
import me.xpyex.software.feedback.packet.util.StudyContentsUtil;
import me.xpyex.software.feedback.packet.util.StudyContentsUtil.FinishedType;
import me.xpyex.software.feedback.packet.util.StudyContentsUtil.StudyType;
import me.xpyex.software.feedback.study.StudyConfigManager;
import me.xpyex.software.feedback.tasks.basis.StudentReader;
import me.xpyex.software.feedback.tasks.basis.StudentUpdater;
import me.xpyex.software.feedback.util.AiyouduUtil;
import me.xpyex.software.feedback.util.GsonUtil;
import me.xpyex.software.feedback.util.LogUtil;
import me.xpyex.software.feedback.util.NetworkUtil;
import me.xpyex.software.feedback.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学生批量打印学案任务。
 * <p>
 * 打印篇数以每个学生的「学案设置」为准（config/study/{姓名}_{studentId}.json），
 * 不再读取全局 config/print.json。单生流程：
 * <ol>
 *     <li>若为按日结算卡且有剩余天数，先退掉剩余天数（沿用）</li>
 *     <li>按月续费一个月</li>
 *     <li>临时把班级改为分组名以便学案上突出 → 按题型分类打印，同题型 PrintMerge 合并出 PDF → 恢复原班级</li>
 *     <li>根据学案设置里的 refundMonth 决定是否回收（退掉）刚续的月卡</li>
 * </ol>
 */
@ExtensionMethod(AiyouduUtil.class)
public class PrintStudentStudy {
    private static final Logger log = LoggerFactory.getLogger(PrintStudentStudy.class.getSimpleName());
    /** 每份最多可包含的文章篇数 */
    private static final int ARTICLES_PER_COPY = 2;

    /** GUI 选中的学生列表，为 null 表示处理全部在读学生 */
    private static List<StudentInfo> selectedStudents = null;

    /**
     * 启动批量处理流程（处理全部在读学生）
     */
    public static void start() {
        startWithStudents(null);
    }

    /**
     * 启动批量处理流程（支持 GUI 选中学生）
     *
     * @param students GUI 选中的学生列表，为 null 则处理所有学生
     */
    public static void startWithStudents(List<StudentInfo> students) {
        selectedStudents = students;

        LogUtil.line();
        log.info("   学生批量打印任务启动");
        LogUtil.line();

        try {
            // 前置条件
            if (AiyouduUtil.token == null || AiyouduUtil.token.isEmpty()) {
                log.error("Token 不存在！请先执行【获取token】登录");
                return;
            }
            if (!StudentReader.hasStudents()) {
                log.error("没有学生数据！请先执行【读取学生】");
                return;
            }

            // 组装本次要处理的学生
            Map<Integer, StudentInfo> toProcess;
            if (selectedStudents != null && !selectedStudents.isEmpty()) {
                toProcess = selectedStudents.stream().collect(Collectors.toMap(StudentInfo::getStudentId, s -> s));
                log.info(">>> 使用选中学生 {} 名", toProcess.size());
            } else {
                toProcess = StudentReader.copyStudents();
                log.info(">>> 处理全部在读学生 {} 名", toProcess.size());
            }

            processAll(toProcess);

            LogUtil.line();
            log.info("   批量打印任务结束");
            LogUtil.line();
        } catch (Exception e) {
            log.error("批量打印过程中发生错误：", e);
        } finally {
            selectedStudents = null; // 清空
        }
    }

    private static void processAll(Map<Integer, StudentInfo> studentMap) {
        int total = studentMap.size();
        int done = 0, noConfig = 0, skipped = 0, failed = 0;

        for (StudentInfo student : studentMap.values()) {
            done++;
            LogUtil.line("-", 40);
            log.info("[{}/{}] 正在处理学生：{} [ID:{}]", done, total, student.getRealName(), student.getStudentId());

            StudyConfig config = StudyConfigManager.load(student);
            if (config == null) {
                log.info("  ✗ 该生未设置学案，跳过（请先在「学案设置」中配置题型篇数）");
                noConfig++;
                continue;
            }
            if (config.isEmpty()) {
                log.info("  ✗ 该生学案篇数全为 0，跳过");
                skipped++;
                continue;
            }

            try {
                processStudent(student, config);
                log.info("√ 学生 {} 处理完成", student.getRealName());
            } catch (Exception e) {
                failed++;
                log.error("处理学生 {} 时发生异常：", student.getRealName(), e);
            }

            // 防止 API 限流
            TimeUtil.sleep(2000);
        }

        LogUtil.line();
        log.info("处理统计：总数 {}，成功 {}，无学案配置 {}，篇数为0 {}，异常 {}",
            total, total - noConfig - skipped - failed, noConfig, skipped, failed);
    }

    private static void processStudent(StudentInfo student, StudyConfig config) {
        log.info("  学案设置：{}", config.getTypeCountMap());
        log.info("  打印后退费（回收月卡）：{}", config.isRefundMonth());

        // 步骤 1: 按日结算卡有剩余天数先退费（沿用）
        handleCardTypeAndRefund(student);

        // 步骤 2: 按月续费
        rechargeOneMonth(student);

        // 步骤 3: 临时把班级改为组名以便学案上突出，打印后恢复（沿用既有逻辑：同步月卡类型与计费方式）
        String originClassName = student.getClassName();
        try {
            updateAndLog(student, "临时修改班级为分组名",
                student.setClassName(student.getGroup())
                       .setCardType(StudentInfo.CardType.IN_MONTHS.getCardType())
                       .setBillingType(0));
            printByTypes(student, config);
        } finally {
            updateAndLog(student, "恢复原班级",
                student.setClassName(originClassName)
                       .setCardType(StudentInfo.CardType.IN_MONTHS.getCardType())
                       .setBillingType(0));
        }

        // 步骤 4: 按学案设置决定是否回收刚续的月卡
        if (config.isRefundMonth()) {
            refundRechargedDays(student);
        } else {
            log.info("  √ 学案设置为不退费，保留该生月卡（真实续费一个月）");
        }
    }

    private static void updateAndLog(StudentInfo student, String action, StudentInfo updated) {
        AYDResponse response = StudentUpdater.updateStudentInfo(updated);
        if (response != null && response.isSuccess()) {
            log.info("  √ {}", action);
        } else {
            log.warn("  ! {}失败：{}", action, response == null ? "请求未返回有效响应" : response.getMessage());
        }
    }

    /**
     * 按题型分类打印：遍历学案设置的每种题型，按每份最多 2 篇拆份打印，
     * 再将刚生成的该题型学案 printId 通过 PrintMerge 合并成一个 PDF 下载。
     */
    private static void printByTypes(StudentInfo student, StudyConfig config) {
        for (Map.Entry<String, Integer> entry : config.getTypeCountMap().entrySet()) {
            String typeName = entry.getKey();
            Integer countObj = entry.getValue();
            if (countObj == null || countObj <= 0) {
                log.debug("  跳过题型 {}（篇数 {}）", typeName, countObj);
                continue;
            }
            int count = countObj;

            StudyType type = StudyContentsUtil.StudyType.getStudyTypeByName(typeName);
            if (type == null) {
                log.warn("  ! 未知题型：{}，已跳过", typeName);
                continue;
            }

            log.info("  → 按题型打印：{} × {} 篇", typeName, count);

            // 生成该题型的打印学案（每份最多 2 篇）
            int fullCopies = count / ARTICLES_PER_COPY;
            int remainder = count % ARTICLES_PER_COPY;
            for (int i = 0; i < fullCopies; i++) {
                printOneCopy(student, ARTICLES_PER_COPY);
            }
            if (remainder > 0) {
                printOneCopy(student, remainder);
            }

            // 汇总该题型刚生成的学案，PrintMerge 合并下载
            Set<Integer> printIds = queryPrintIds(student, type, count);
            if (printIds.isEmpty()) {
                log.warn("  ! 未获取到题型 {} 的打印学案（可能未生成纸质版），跳过合并下载", typeName);
                continue;
            }

            AYDResponse pdfLink = AYDResponse.of(PrintMerge.url.postUrlWithToken(PrintMerge.of().addAll(printIds)));
            if (pdfLink.isSuccess()) {
                log.info("  √ 题型 {} 学案合并完成，开始下载（{} 个学案）", typeName, printIds.size());
                downloadPdf(pdfLink.getData().getAsString(), student, typeName);
            } else {
                log.warn("  ! 题型 {} 学案合并失败：{}", typeName, pdfLink.getMessage());
            }
            TimeUtil.sleep(1000);
        }
    }

    /** 发送一次打印学案请求（该题型的一"份"） */
    private static void printOneCopy(StudentInfo student, int articleNum) {
        PrintStudy printPacket = PrintStudy.of().setStudentId(student.getStudentId()).setArticleNum(articleNum);
        AYDResponse response = AYDResponse.of(PrintStudy.url.postUrlWithToken(printPacket));
        if (response != null && response.isSuccess()) {
            log.info("  √ 已打印 1 份（{} 篇）", articleNum);
        } else {
            log.warn("  ! 打印请求失败：{}", response == null ? "无响应" : response.getMessage());
        }
        TimeUtil.sleep(500);
    }

    /** 拉取某学生某题型待反馈的学案 printId（用于合并） */
    private static Set<Integer> queryPrintIds(StudentInfo student, StudyType type, int amount) {
        try {
            String url = StudyContentsUtil.getPrintUrl(student, FinishedType.NOT_FINISHED, type, amount, null);
            AYDResponse response = AYDResponse.of(url.getUrlWithToken());
            if (response != null && response.isSuccess() && "成功".equals(response.getMessage())) {
                return StudyContentsUtil.getStudyContents(response).stream()
                           .map(StudyContentInfo::getPrintId)
                           .filter(Objects::nonNull)
                           .collect(Collectors.toCollection(LinkedHashSet::new));
            }
        } catch (Exception e) {
            log.error("  拉取题型 {} 的打印学案失败：", type.getName(), e);
        }
        return new LinkedHashSet<>();
    }

    /**
     * 下载合并后的 PDF 到 print/{分组}/{姓名}_{题型}_{原始文件名}
     */
    private static void downloadPdf(String pdfUrl, StudentInfo student, String typeName) {
        try {
            String group = student.getGroup() != null ? student.getGroup().trim() : "未分组";
            group = group.replaceAll("[/\\\\:*?\"<>|]", "_");

            // 从 URL 中提取原始文件名并去掉查询参数
            String fileName = pdfUrl.substring(pdfUrl.lastIndexOf('/') + 1);
            if (fileName.contains("?")) {
                fileName = fileName.substring(0, fileName.indexOf('?'));
            }
            fileName = student.getRealName() + "_" + typeName + "_" + fileName;

            File groupDir = new File("print", group);
            NetworkUtil.downloadFile(pdfUrl, groupDir, fileName);
        } catch (Exception e) {
            log.error("  ✗ 下载 PDF 时发生异常：", e);
        }
    }

    // ==================== 以下为沿用既有逻辑的续费/退费小步骤 ====================

    /** 按日结算卡且有剩余天数 → 先退费 */
    private static void handleCardTypeAndRefund(StudentInfo student) {
        int cardType = student.getCardType();
        int expireDay = student.getExpireDay();
        log.info("  课程卡类型：{}，剩余天数：{}", cardType, expireDay);

        // cardType: 1-体验，2-包月起步，3-按日结算
        if (cardType == StudentInfo.CardType.IN_DAYS.getCardType() && expireDay > 0) {
            log.info("  → 按日结算卡有剩余 {} 天，先执行退费...", expireDay);
            RecoverDay recoverDay = RecoverDay.of()
                                          .setStudentId(student.getStudentId())
                                          .setDay(expireDay);
            AYDResponse response = AYDResponse.of(
                AiyouduUtil.apiUrl + "platform/change/studentRecoverDay".postUrlWithToken(recoverDay));
            if (response != null && response.isSuccess()) {
                log.info("  √ 退费请求已发送");
            } else {
                log.warn("  ! 退费响应异常");
            }
            TimeUtil.sleep(1000);
        } else {
            log.debug("  - 无需预退费（卡类型：{}，剩余天数：{}）", cardType, expireDay);
        }
    }

    /** 对学生账号按月续费一个月 */
    private static void rechargeOneMonth(StudentInfo student) {
        log.info("  → 正在按月续费一个月...");
        ApplyStudentCard rechargePacket = ApplyStudentCard.month(1).setStudentId(student.getStudentId());
        String requestJson = GsonUtil.toJsonStr(rechargePacket, false);
        String response = AiyouduUtil.postUrlWithToken(ApplyStudentCard.url, requestJson);
        if (response != null && !response.isEmpty()) {
            log.info("  √ 续费请求已发送");
        } else {
            log.error("  ✗ 续费请求失败");
        }
    }

    /** 回收刚续的月卡（退费一个月） */
    private static void refundRechargedDays(StudentInfo student) {
        log.info("  → 正在回收月卡（退掉刚续的一个月）...");
        RecoverMonth recover = RecoverMonth.of()
                                   .setStudentId(student.getStudentId())
                                   .setMonth(1);
        String response = AiyouduUtil.postUrlWithToken(RecoverMonth.url, recover);
        if (response != null && !response.isEmpty()) {
            log.info("  √ 回收月卡请求已发送（1 个月）");
        } else {
            log.error("  ✗ 回收月卡请求失败");
        }
    }
}
