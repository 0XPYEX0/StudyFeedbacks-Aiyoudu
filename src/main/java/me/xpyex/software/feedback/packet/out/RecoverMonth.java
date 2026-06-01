package me.xpyex.software.feedback.packet.out;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.packet.both.AYDPacket;
import me.xpyex.software.feedback.util.AiyouduUtil;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class RecoverMonth extends AYDPacket {
    public static final String url = AiyouduUtil.apiUrl + "organiztion/student/studentRefund";
    private int month;
    private int studentId;
}
