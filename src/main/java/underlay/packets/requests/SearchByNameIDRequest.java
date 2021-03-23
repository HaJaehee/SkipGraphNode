package underlay.packets.requests;

/* -------------------------------------------------------- */
/**
 File name : SearchByNameIDRequest.java
 Rev. history : 2021-03-23
 Version : 1.0.2
 Implemented handleResourceByNameID().
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 //TODO
 */
/* -------------------------------------------------------- */

import underlay.packets.Request;
import underlay.packets.RequestType;

public class SearchByNameIDRequest extends Request {

    public final String targetNameID;
    public final boolean isGettingResource;
    public final boolean isSettingResource;
    public final String resourceKey;
    public final String resourceValue;

    public SearchByNameIDRequest(String targetNameID, boolean isGettingResource, boolean isSettingResource, String resourceKey, String resourceValue) {
        super(RequestType.SearchByNameID);
        this.targetNameID = targetNameID;
        this.isGettingResource = isGettingResource;
        this.isSettingResource = isSettingResource;
        this.resourceKey = resourceKey;
        this.resourceValue = resourceValue;
    }
}
