package us.ihmc.android.util.netutils;

/**
 * Notification.java
 * <p/>
 * Enum <code>Notification</code> handles notifications the Service to the MainActivity
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public enum Notification
{
    BOUND_ERROR(-2),
    CONNECTION_ERROR(-1),
    HANDSHAKE(0),
    NEW_PEER(1),
    DEAD_PEER(2),
    PEER_LIST(3),
    CLIENT_STATS(4),
    SERVER_STATS(5),
    MOCKETS_STATS(6),
    MOCKETS_SHORT_STATS(7),
    PROGRESS_STATS(8),
    OPTIONS(9);

    Notification (int code)
    {
        _code = code;
    }

    public int code ()
    {
        return _code;
    }

    public static Notification fromCode (int code)
    {
        switch (code) {
            case -2:
                return BOUND_ERROR;
            case -1:
                return CONNECTION_ERROR;
            case 0:
                return HANDSHAKE;
            case 1:
                return NEW_PEER;
            case 2:
                return DEAD_PEER;
            case 3:
                return PEER_LIST;
            case 4:
                return CLIENT_STATS;
            case 5:
                return SERVER_STATS;
            case 6:
                return MOCKETS_STATS;
            case 7:
                return MOCKETS_SHORT_STATS;
            case 8:
                return PROGRESS_STATS;
            case 9:
                return OPTIONS;
            default:
                throw new NumberFormatException("Unrecognized code " + code);
        }
    }

    private final int _code;
}
