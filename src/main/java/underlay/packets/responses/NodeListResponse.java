package underlay.packets.responses;

/* -------------------------------------------------------- */
/**
 File name : IdentityListResponse.java
 Creation Date : 2021-03-25
 Version : 1.0.3
 Author : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */

import skipnode.SkipNodeIdentity;
import underlay.packets.Response;

import java.util.ArrayList;

public class NodeListResponse extends Response {

    public final ArrayList<SkipNodeIdentity> nodeList;

    public NodeListResponse(ArrayList<SkipNodeIdentity> nodeList) { this.nodeList = nodeList; }
}
