package underlay.packets.requests;

/* -------------------------------------------------------- */
/**
 File name : HandleMapStorageWithNumIDRequest.java
 Rev. history : 2021-07-16
 Version : 1.2.0
 Implemented handleResourceByNumID().
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */


import underlay.packets.Request;
import underlay.packets.RequestType;

public class HandleMapStorageWithRsrcKeyRequest extends Request {

    public final String resourceKey;
    public final boolean isGettingResource;
    public final boolean isSettingResource;
    public final String resourceValue;

    public HandleMapStorageWithRsrcKeyRequest(boolean isGettingResource, boolean isSettingResource, String resourceKey, String resourceValue) {
        super(RequestType.HandleMapStorageWithRsrcKeyRequest);

        this.resourceKey = resourceKey;
        this.isGettingResource = isGettingResource;
        this.isSettingResource = isSettingResource;
        this.resourceValue = resourceValue;
    }
}
