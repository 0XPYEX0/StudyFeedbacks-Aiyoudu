package me.xpyex.software.feedback.packet.in;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.util.AiyouduUtil;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class StudentSignIn {
    // https://group.aiyoudu.cn/api2/organiztion/student/signInTaskNew?currentDay=2026-09-01&studentId=266979
    public static final String url = AiyouduUtil.orgUrl + "student/signInTaskNew?currentDay={$monthFirstDay}&studentId={$student}";

    @SerializedName("openCard1")
    private int bronzeCount;  // 青铜打卡次数
    @SerializedName("openCard2")
    private int silverCount;  // 白银打卡次数
    @SerializedName("openCard3")
    private int goldCount;  // 黄金打卡次数

    private String day;  // 点进去月历具体某一天的打卡日期
    private List<StudentSignIn> signINList;  // 该月所有打卡天
}
