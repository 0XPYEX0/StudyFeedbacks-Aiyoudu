package me.xpyex.software.feedback.packet.in;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class GroupInfo {
    private String groupName;
    private int groupId;
    private int peopleNum;
}
