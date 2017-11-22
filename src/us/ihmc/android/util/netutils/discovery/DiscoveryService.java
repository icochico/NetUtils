package us.ihmc.android.util.netutils.discovery;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.widget.Toast;
import org.apache.log4j.Logger;
import us.ihmc.aci.grpMgr.GroupManager;
import us.ihmc.aci.grpMgr.GroupManagerListener;
import us.ihmc.aci.grpMgr.TransportMode;
import us.ihmc.android.util.netutils.Notification;
import us.ihmc.android.util.netutils.ServiceMessenger;
import us.ihmc.android.util.netutils.Utils;
import us.ihmc.android.util.netutils.R;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * DiscoveryService.java
 * <p/>
 * Class <code>DiscoveryService</code> provides a basic discovery service using GroupManager.
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class DiscoveryService extends Service implements GroupManagerListener
{
    @Override
    public IBinder onBind (Intent intent)
    {

        if (INSTANCE == null) {

            (new Thread(_serviceMessenger, DiscoveryService.class.getSimpleName() + "Messenger")).start();
            (new Thread(new PeerStatus(), "PeerStatus")).start();
            if (!initGroupManager(intent)) return null;

            INSTANCE = this;
        }

        return _serviceMessenger.get().getBinder();
    }

    private boolean initGroupManager (Intent intent)
    {
        //setting Logger
        _groupManager.setLogger(getAppPath() + File.separator + getString(R.string.app_logs_dir) + File.separator +
                getString(R
                        .string.app_gm_log_file));

        boolean withConfig = intent.getBooleanExtra(getString(R.string.discovery_config_enabled), false);
        try {
            if (withConfig) {
                String configFile = Utils.getConfigFile(this);
                _groupManager.init(configFile);
                LOG.debug("Initializing GroupManager with config configuration file: " + configFile);
            }
            else {
                Peer localPeer = intent.getParcelableExtra(getString(R.string.discovery_local_peer));
                int grpMgrPort = intent.getIntExtra(getString(R.string.discovery_port),
                        Integer.parseInt(getString(R.string.aci_groupmanager_port_default)));
                TransportMode tMode = TransportMode.valueOf(intent.getStringExtra(getString(R.string
                        .discovery_transport)));
                _groupManager.init(localPeer.getId(), grpMgrPort, localPeer.getIpAddress(), tMode);
                LOG.debug("Initializing GroupManager with default values in strings.xml. Port: " + grpMgrPort + " " +
                        "Mode: " + tMode.toString());

                if (_groupManager.setNodeName(localPeer.getName()) != 0)
                    LOG.warn("Unable to set peer node name to: " + localPeer.getName());
                LOG.debug("Setting PING interval to: " + getString(R.string.aci_groupmanager_ping_interval_default));
                _groupManager.setPingInterval(Integer.parseInt(getString(R.string
                        .aci_groupmanager_ping_interval_default)));
                LOG.debug("Setting INFO interval to: " + getString(R.string.aci_groupmanager_info_interval_default));
                _groupManager.setInfoBCastInterval(Integer.parseInt(getString(R.string
                        .aci_groupmanager_info_interval_default)));
                LOG.debug("Setting PING hop count to: " + getString(R.string.aci_groupmanager_ping_hopcount_default));
                _groupManager.setPingHopCount(Integer.parseInt(getString(R.string
                        .aci_groupmanager_ping_hopcount_default)));

            }
        }
        catch (IOException e) {
            LOG.error("Unable to initialize GroupManager", e);
            Toast.makeText(this, "Unable to initialize discovery service, GroupManager", Toast.LENGTH_SHORT).show();
            return false;
        }

        _groupManager.setGroupManagerListener(this);
        LOG.trace("Starting GroupManager native thread");
        _groupManager.start();

        return true;
    }

    private String getAppPath ()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(Utils.getExternalStorageDirectory())
                .append(File.separator)
                .append(getString(R.string.app_dir))
                .append(File.separator);

        return sb.toString();
    }

    @Override
    public void onCreate ()
    {
        LOG.trace("Creating " + DiscoveryService.class.getSimpleName());
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId)
    {
        return START_STICKY;
    }

    @Override
    public void onDestroy ()
    {
        LOG.warn(DiscoveryService.class.getSimpleName() + " has been destroyed");
    }

    private void toActivities (Bundle data, Notification notification)
    {
        Message msg = Message.obtain(); //use of .obtain() instead of new Message() saves resources
        msg.arg1 = notification.code();
        msg.setData(data);
        LOG.debug("Received notification: " + notification + ". Queued to ServiceMessenger");

        _serviceMessenger.toActivities().sendMessage(msg);
    }

    @Override
    public void newPeer (String nodeUUID)
    {
        LOG.debug("newPeer callback received for peer: " + nodeUUID);
        Peer p = new Peer(nodeUUID);
        p.setReachable(true);
        _incomingPeers.add(new Peer(nodeUUID));
    }

    @Override
    public void deadPeer (String nodeUUID)
    {
        LOG.debug("deadPeer callback received for peer: " + nodeUUID);
        Peer p = new Peer(nodeUUID);
        p.setReachable(false); // to notify that it was reachable but it's dead now.
        _incomingPeers.add(p);
    }

    @Override
    public void groupListChange (String nodeUUID)
    {
        LOG.debug("groupListChange callback received - nodeUUID: " + nodeUUID);
    }

    @Override
    public void newGroupMember (String groupName, String memberUUID, byte[] data)
    {
        LOG.debug("newGroupMember callback received - groupName: " + groupName + " memberUUID: " + memberUUID);
    }

    @Override
    public void groupMemberLeft (String groupName, String memberUUID)
    {
        LOG.debug("groupMemberLeft callback received - groupName: " + groupName + " memberUUID: " + memberUUID);
    }

    @Override
    public void conflictWithPrivatePeerGroup (String groupName, String nodeUUID)
    {
        LOG.debug("conflictWithPrivatePeerGroup callback received - groupName: " + groupName + " memberUUID: " +
                nodeUUID);
    }

    @Override
    public void peerGroupDataChanged (String groupName, String nodeUUID, byte[] data)
    {
        LOG.debug("peerGroupDataChanged callback received - groupName: " + groupName + " nodeUUID: " + nodeUUID);
    }

    // Invoked by the GroupManager when a search request is received from another node
    // Application may choose to respond by invoking the searchReply() method in the GroupManager
    @Override
    public void peerSearchRequestReceived (String groupName, String nodeUUID, String searchUUID, byte[] param)
    {
        LOG.debug("peerSearchRequestReceived callback received - groupName: " + groupName + " nodeUUID: " + nodeUUID +
                " searchUUID: " + searchUUID);
    }

    // Invoked by the GroupManager when a response to a search request is received
    // This may be invoked multiple times, once per response received
    @Override
    public void peerSearchResultReceived (String groupName, String nodeUUID, String searchUUID, byte[] param)
    {
        LOG.debug("peerSearchResultReceived callback received - groupName: " + groupName + " nodeUUID: " + nodeUUID +
                " searchUUID: " + searchUUID);
    }

    // Message received from another peer
    // groupName is null if the message is a direct message to the node (unicast)
    @Override
    public void peerMessageReceived (String groupName, String nodeUUID, byte[] data)
    {
        LOG.debug("peerMessageReceived callback received - groupName: " + groupName + " nodeUUID: " + nodeUUID);
    }

    @Override
    public void persistentPeerSearchTerminated (String groupName, String nodeUUID, String peerSearchUUID)
    {
        LOG.debug("peerSearchResultReceived callback received - groupName: " + groupName + " nodeUUID: " + nodeUUID +
                " searchUUID: " + peerSearchUUID);
    }

    class PeerStatus implements Runnable
    {
        @Override
        public void run ()
        {
            while (true) {
                try {
                    Peer p = _incomingPeers.poll(Long.MAX_VALUE, TimeUnit.DAYS);
                    //String ip = _groupManager.getPeerIPAddress(peerId);
                    LOG.debug("Found new peer " + p);
                    //pre-connect to save time
                    if (p.isReachable()) {
                        LOG.debug("Found new peer " + p);
                        Notification type = Notification.NEW_PEER;
                        Bundle data = new Bundle();
                        data.putParcelable(getString(R.string.discovery_remote_peer), p);
                        //LOG.debug(p.toJSON());
                        toActivities(data, type);
                    }
                    else {
                        LOG.debug("Found dead peer " + p);
                        //send message back to MainActivity
                        Notification type = Notification.DEAD_PEER;
                        Bundle data = new Bundle();
                        data.putParcelable(getString(R.string.discovery_remote_peer), p);
                        toActivities(data, type);
                    }
                }
                catch (NullPointerException e) {
                    LOG.error("Unable to find IP address of the new peer", e);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*
     * This is used to load the GroupManager and Mockets libraries on Service startup.
     */
    static {
        System.loadLibrary("gnustl_shared");
        System.loadLibrary("util");
        System.loadLibrary("grpmgr");
        System.loadLibrary("grpmgrjavawrapper");
        System.loadLibrary("netutils");
    }

    private final GroupManager _groupManager = new GroupManager();
    private final BlockingDeque<Peer> _incomingPeers = new LinkedBlockingDeque<Peer>();

    private static volatile DiscoveryService INSTANCE = null;
    private ServiceMessenger _serviceMessenger = new ServiceMessenger(DiscoveryService.class);

    private final static Logger LOG = Logger.getLogger(DiscoveryService.class);
}
