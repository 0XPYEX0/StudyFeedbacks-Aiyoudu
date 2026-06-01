package me.xpyex.software.feedback.packet.in;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.util.AiyouduUtil;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class GroupInfo {
    public static final String url = AiyouduUtil.apiUrl + "organization/group/queryList";

    private String groupName;
    private int groupId;
    private int peopleNum;
}
