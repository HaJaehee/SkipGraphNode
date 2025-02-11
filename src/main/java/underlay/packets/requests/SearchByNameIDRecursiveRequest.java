package underlay.packets.requests;

/* -------------------------------------------------------- */
/**
 File name : SearchByNameIDRecursiveRequest.java
 Rev. history : 2021-03-23
 Version : 1.0.2
 Implemented handleResourceByNameID().
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)

 //TODO
 */
/* -------------------------------------------------------- */


import underlay.packets.Request;
import underlay.packets.RequestType;

public class SearchByNameIDRecursiveRequest extends Request {

    public final String target;
    public final int level;
    public final boolean isGettingResource;
    public final boolean isSettingResource;
    public final String resourceKey;
    public final String resourceValue;

    public SearchByNameIDRecursiveRequest(String target, int level, boolean isGettingResource, boolean isSettingResource, String resourceKey, String resourceValue) {
        super(RequestType.SearchByNameIDRecursive);
        this.target = target;
        this.level = level;
        this.isGettingResource = isGettingResource;
        this.isSettingResource = isSettingResource;
        this.resourceKey = resourceKey;
        this.resourceValue = resourceValue;
    }
}
