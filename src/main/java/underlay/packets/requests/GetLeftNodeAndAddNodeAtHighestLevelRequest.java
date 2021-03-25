package underlay.packets.requests;

/* -------------------------------------------------------- */

import skipnode.SkipNodeIdentity;
import underlay.packets.Request;
import underlay.packets.RequestType;

/**
 File name : GetLeftNodeAndAddNodeAtHighestLevelRequest.java
 Creation Date : 2021-03-25
 Version : 1.0.3
 Author : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */


public class GetLeftNodeAndAddNodeAtHighestLevelRequest extends Request {

    public final int level;
    public final SkipNodeIdentity snId;

    public GetLeftNodeAndAddNodeAtHighestLevelRequest(int level, SkipNodeIdentity snId) {
        super(RequestType.GetLeftNodeAndAddNodeAtHighestLevel);
        this.level = level;
        this.snId = snId;
    }
}
