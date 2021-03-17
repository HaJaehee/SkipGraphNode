package underlay.packets.requests;

import underlay.packets.Request;
import underlay.packets.RequestType;

import java.math.BigInteger;

public class SearchByNumIDRequest extends Request {

    public final BigInteger targetNumID;

    public SearchByNumIDRequest(BigInteger targetNumID) {
        super(RequestType.SearchByNumID);
        this.targetNumID = targetNumID;
    }
}
