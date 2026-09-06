package me.xpyex.software.feedback.tasks.basis;

import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.packet.in.AYDResponse;
import me.xpyex.software.feedback.util.AiyouduUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学生信息提交器。
 * <p>
 * 将原来散落在打印逻辑里的"提交修改学生信息（PUT updateStudentInfo）"HTTP 请求
 * 统一抽取为公共方法，供「批量打印学案」与「修改信息」弹窗复用，避免重复代码。
 */
public class StudentUpdater {
    private static final Logger log = LoggerFactory.getLogger(StudentUpdater.class.getSimpleName());

    private StudentUpdater() {
    }

    /**
     * PUT 提交学生信息到服务端。
     *
     * @param info 学生信息（含需要更新的字段）
     * @return 解析后的响应；请求失败（返回空串/非 JSON）时返回 null
     */
    public static AYDResponse updateStudentInfo(StudentInfo info) {
        if (info == null) {
            log.warn("提交修改信息失败：学生对象为空");
            return null;
        }
        try {
            String response = AiyouduUtil.putUrlWithToken(StudentInfo.updateUrl, info);
            if (response == null || response.isBlank()) {
                log.error("提交修改信息失败：服务端返回为空（可能网络异常或 Token 失效），学生：{} [ID:{}]",
                    info.getRealName(), info.getStudentId());
                return null;
            }
            return AYDResponse.of(response);
        } catch (Exception e) {
            log.error("提交修改信息时发生异常，学生：{} [ID:{}]", info.getRealName(), info.getStudentId(), e);
            return null;
        }
    }
}
