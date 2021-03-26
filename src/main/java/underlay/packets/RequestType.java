package underlay.packets;

/* -------------------------------------------------------- */
/**
 File name : RequestType.java
 Rev. history : 2021-03-25
 Version : 1.0.3
 Added GetNodeListAtHighestLevel, GetLeftNodeAndAddNodeAtHighestLevel and GetRightNodeAndAddNodeAtHighestLevel .
 Modifier : Jaehee ha (jaehee.ha@kaist.ac.kr)
 */
/* -------------------------------------------------------- */

/**
 * Denotes the types of the requests that will be employed at the underlay layer.
 */
public enum RequestType {
    SearchByNameID,
    SearchByNameIDRecursive,
    SearchByNumID,
    NameIDLevelSearch,
    UpdateLeftNode,
    UpdateRightNode,
    GetLeftNode,
    GetRightNode,
    AcquireNeighbors,
    FindLadder,
    AnnounceNeighbor,
    IsAvailable,
    GetLeftLadder,
    Increment,
    Injection,
    GetRightLadder,
    AcquireLock,
    ReleaseLock,
    GetIdentity,
    GetNodeListAtHighestLevel,
    GetLeftNodeAndAddNodeAtHighestLevel,
    GetRightNodeAndAddNodeAtHighestLevel
}
