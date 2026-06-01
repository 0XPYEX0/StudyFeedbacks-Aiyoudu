package me.xpyex.software.feedback.packet.out;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.packet.both.AYDPacket;
import me.xpyex.software.feedback.util.AiyouduUtil;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class RecoverDay extends AYDPacket {
    // https://group.aiyoudu.cn/api2/platform/change/studentRecoverDay
    private static final String url = AiyouduUtil.apiUrl + "platform/change/studentRecoverDay";
    private int studentId;
    private int day;  // 回收卡片天数
}
