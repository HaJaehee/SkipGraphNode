package underlay.packets.requests;

/* -------------------------------------------------------- */
/**
 File name : StoreResourceByNumIDRequest.java
 Creation Date : 2021-03-27
 Version : 1.1.0
 Author : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */

import underlay.packets.Request;
import underlay.packets.RequestType;

import java.math.BigInteger;

public class StoreResourceByNumIDRequest extends Request {

    public final BigInteger targetNumID;
    public final String resourceValue;

    public StoreResourceByNumIDRequest(BigInteger targetNumID, String resourceValue) {
        super(RequestType.StoreResourceByNumIDRequest);
        this.targetNumID = targetNumID;
        this.resourceValue = resourceValue;
    }
}
