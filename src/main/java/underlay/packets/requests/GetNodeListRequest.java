package underlay.packets.requests;

/* -------------------------------------------------------- */
/**
 File name : GetNodeListRequest.java
 Creation Date : 2021-03-25
 Version : 1.0.3
 Author : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */

import underlay.packets.Request;
import underlay.packets.RequestType;


public class GetNodeListRequest extends Request {
    public GetNodeListRequest() {
        super(RequestType.GetNodeListAtHighestLevel);
    }
}
