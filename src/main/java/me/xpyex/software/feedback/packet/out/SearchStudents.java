package me.xpyex.software.feedback.packet.out;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.packet.both.AYDPacket;
import me.xpyex.software.feedback.util.AiyouduUtil;

// 用“学生列表”的翻页数据实现搜索
@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class SearchStudents extends AYDPacket {
    public static final String url = AiyouduUtil.orgUrl + "student/page";

    private int current = 1;  // 当前页数
    private int size;  // 单页显示几个学生
    private SearchCondition t = SearchCondition.of();  // 默认搜索条件，在JSON里面叫做t

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor(staticName = "of")
    public static class SearchCondition {
        private Long groupId = null;
        private int status = 997;
        private Integer curriculumStatus = null;
        private String userName = "";  // 用户名(应该是账号，手机号)
        private String nickName = "";  // 昵称
        private String realName = "";  // 真实姓名
        private String startTime = ""; // 入学时间
        private String endTime = "";  // 目前到期时间
        private Boolean isTraining = null;  // 集训模式
        private String teacherId = "";  // 对接老师在系统的账号
        private String referrerId = "";
        private String isSupervision = "";
        private String organizationId = "";
        private List<String> stageArr = new ArrayList<>();
        private String sortType = null;
        private String stage = null;
        private String grade = null;  // 年级
        private Object studentIds = null;
    }
}
