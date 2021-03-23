package underlay.packets.requests;

/* -------------------------------------------------------- */
/**
 File name : SearchByNumIDRequest.java
 Rev. history : 2021-03-23
 Version : 1.0.2
 Implemented handleResourceByNumID().
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */


import underlay.packets.Request;
import underlay.packets.RequestType;

import java.math.BigInteger;

public class SearchByNumIDRequest extends Request {

    public final BigInteger targetNumID;
    public final boolean isGettingResource;
    public final boolean isSettingResource;
    public final String resourceValue;

    public SearchByNumIDRequest(BigInteger targetNumID, boolean isGettingResource, boolean isSettingResource, String resourceValue) {
        super(RequestType.SearchByNumID);

        this.targetNumID = targetNumID;
        this.isGettingResource = isGettingResource;
        this.isSettingResource = isSettingResource;
        this.resourceValue = resourceValue;
    }
}
