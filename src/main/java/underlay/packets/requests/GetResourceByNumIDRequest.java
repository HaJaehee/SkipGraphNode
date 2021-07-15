package underlay.packets.requests;

/* -------------------------------------------------------- */
/**
 File name : GetResourceByNumIDRequest.java
 Creation Date : 2021-07-15
 Version : 1.1.8
 Author : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */

import underlay.packets.Request;
import underlay.packets.RequestType;

import java.math.BigInteger;

public class GetResourceByNumIDRequest extends Request {

    public final BigInteger targetNumID;

    public GetResourceByNumIDRequest(BigInteger targetNumID) {
        super(RequestType.GetResourceByNumIDRequest);
        this.targetNumID = targetNumID;
    }
}
