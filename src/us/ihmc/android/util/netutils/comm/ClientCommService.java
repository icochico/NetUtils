package us.ihmc.android.util.netutils.comm;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import org.apache.log4j.Logger;
import us.ihmc.android.util.netutils.*;
import us.ihmc.mockets.Mocket;
import us.ihmc.netutils.*;
import us.ihmc.netutils.protocol.Protocol;

import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ClientCommService.java
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class ClientCommService extends Service implements MessageListener
{
    @Override
    public IBinder onBind (Intent intent)
    {
        (new Thread(_serviceMessenger, ClientCommService.class.getSimpleName() + "Messenger")).start();
        _stats = new StatsRunnable(_serviceMessenger);
        (new Thread(_stats)).start();
        onStart(intent);

        return _serviceMessenger.get().getBinder();
    }

    @Override
    public boolean onUnbind (Intent intent)
    {
        LOG.debug("Called onUnbind");
        _stop.set(true);    //stop threads
        return false;

    }

    @Override
    public void onCreate ()
    {
        LOG.trace("Creating " + ClientCommService.class.getSimpleName());
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId)
    {
        //onStart(intent);

        return START_STICKY;
    }

    private void onStart (Intent intent)
    {
        //get options
        String remotePeer = intent.getStringExtra(getString(R.string.transport_remote_host));
        Protocol protocol = Protocol.valueOf(intent.getStringExtra(getString(R.string.transport_protocol)));
        Mode mode = Mode.valueOf(intent.getStringExtra(getString(R.string.transport_mode)));

        int msgSize = intent.getIntExtra(getString(R.string.transport_msg_size), Integer.parseInt(getString(R.string
                .netutils_msg_size_default)));

        //only for Mode = Interval
        int interval = intent.getIntExtra(getString(R.string.transport_interval), Integer.parseInt(getString(R.string
                .netutils_interval_default)));

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        int port;
        switch (protocol.socket) {
            case Socket:
                port = Integer.valueOf(sharedPref.getString(getString(R.string.netutils_tcp_port),
                        getString(R.string.netutils_tcp_port_default)));
                break;
            case DatagramSocket:
                port = Integer.valueOf(sharedPref.getString(getString(R.string.netutils_udp_port),
                        getString(R.string.netutils_udp_port_default)));
                break;
            case Mocket:
                port = Integer.valueOf(sharedPref.getString(getString(R.string.netutils_mockets_port),
                        getString(R.string.netutils_mockets_port_default)));
                break;
            case StreamMocket:
                port = Integer.valueOf(sharedPref.getString(getString(R.string.netutils_mockets_stream_port),
                        getString(R.string.netutils_mockets_stream_port_default)));
                break;
            default:
                port = Integer.parseInt(getString(R.string.netutils_tcp_port_default)); //default TCP port
        }

        int tcpControlPort = Integer.valueOf(sharedPref.getString(getString(R.string.netutils_tcp_control_port),
                getString(R.string.netutils_tcp_control_port_default)));

        _commDetails = new CommDetails.Builder(remotePeer, protocol, port)
                .tcpControlPort(tcpControlPort)
                .mode(mode)
                .configFile(Utils.getConfigDir(this) + File.separator + getString(R.string.app_mockets_conf_file))
                .msgSize(msgSize)
                .interval(interval)
                .build();

        startCommClient();
    }

    @Override
    public void onDestroy ()
    {
        LOG.debug("Called onDestroy");
    }

    public void startCommClient ()
    {

        _client = new Client(_commDetails.getProtocol().toString() + "Client", _commDetails);
        _client.addMessageListener(this);

        //start threads
        (new Thread(new SendMessageRunnable(_commDetails))).start();
        if (_commDetails.getMode().equals(Mode.Interval)) (new Thread(new RequestStatsRunnable(3000))).start();
        // Only for interval mode
        //TODO change to fetch request interval from input
    }

    @Override
    public void onMessage (short clientId, byte[] buf)
    {
        LOG.debug("Received message of size " + buf.length + " from Server (client id: " + clientId + ")");
    }

    @Override
    public void onProgress (int bytesSent, int total)
    {
        deliverProgressStats(bytesSent, total);
    }

    @Override
    public void onTestStream (short clientId, int streamSize, Stats stats)
    {
        LOG.debug("Sent test stream of size " + streamSize + " to Server (client id: " + clientId + ")");
        LOG.debug("onTestStream - " + stats.toString());
        deliverClientStats(stats);
    }

    @Override
    public void onStats (short clientId, final Stats stats)
    {
        LOG.debug("onStats - " + stats.toString());
        deliverClientStats(stats);
    }

    @Override
    public void onBoundError (Protocol protocol, int port)
    {
        deliverBoundError(protocol, port);
    }

    private void deliverBoundError (Protocol protocol, int port)
    {
        Bundle data = new Bundle();

        data.putString(getString(R.string.transport_protocol), protocol.toString());
        data.putInt(getString(R.string.transport_local_port), port);
        Message msg = Message.obtain(); //use of .obtain() instead of new Message() saves resources
        msg.arg1 = Notification.BOUND_ERROR.code();
        msg.setData(data);
        _stats.addMessage(msg);
    }

    private void deliverConnectionError (String host, int port)
    {
        Bundle data = new Bundle();

        data.putString(getString(R.string.transport_remote_host), host);
        data.putInt(getString(R.string.transport_remote_port), port);
        Message msg = Message.obtain(); //use of .obtain() instead of new Message() saves resources
        msg.arg1 = Notification.CONNECTION_ERROR.code();
        msg.setData(data);
        _stats.addMessage(msg);
    }

    private void deliverClientStats (Stats stats)
    {
        String lostPacketsHR = stats.getPacketLoss() + " (" + stats.getPacketLossPercent() + "%)";
        String byteSentHR = Utils.humanReadableByteCount(_commDetails.getMode().equals(Mode.Stream) ? _commDetails
                .getMsgSize() :
                _commDetails.getMsgSize() * stats.msgSent.get(), true);
        String throughputSentHR = (Utils.humanReadableByteCount(stats.throughputSent.get(), true)) + "/sec"; //bytes/sec
        String throughputReceivedHR = (Utils.humanReadableByteCount(stats.throughputReceived.get(),
                true)) + "/sec"; //bytes/sec

        Bundle data = new Bundle();
        data.putString(getString(R.string.stats_remote_host), _commDetails.getRemoteHost());
        data.putString(getString(R.string.stats_remote_port), String.valueOf(_commDetails.getPort()));
        data.putString(getString(R.string.stats_protocol), _commDetails.getProtocol().toString());
        data.putString(getString(R.string.stats_mode), _commDetails.getMode().toString());
        data.putString(getString(R.string.stats_message_size), Utils.humanReadableByteCount(_commDetails.getMsgSize(),
                true));
        data.putInt(getString(R.string.stats_messages_sent), stats.msgSent.get());
        data.putString(getString(R.string.stats_bytes_sent), byteSentHR);
        data.putString(getString(R.string.stats_lost_packets_sent), lostPacketsHR);
        data.putString(getString(R.string.stats_throughput_sent), throughputSentHR);
        data.putString(getString(R.string.stats_throughput_received), throughputReceivedHR);

        Message msg = Message.obtain(); //use of .obtain() instead of new Message() saves resources
        msg.arg1 = Notification.CLIENT_STATS.code();
        msg.setData(data);

        _stats.addMessage(msg);
    }

    private void deliverProgressStats (int bytesSent, int total)
    {
        Bundle data = new Bundle();
        data.putInt(getString(R.string.stats_bytes_sent), bytesSent);
        data.putInt(getString(R.string.stats_message_size), total);
        Message msg = Message.obtain(); //use of .obtain() instead of new Message() saves resources
        msg.arg1 = Notification.PROGRESS_STATS.code();
        msg.setData(data);

        _stats.addMessage(msg);
    }

    class SendMessageRunnable implements Runnable
    {
        public SendMessageRunnable (CommDetails commDetails)
        {
            _commDetails = commDetails;
        }

        @Override
        public void run ()
        {
            if (!_client.connect()) {
                LOG.error("Unable to connect client to " + _commDetails);
                deliverConnectionError(_commDetails.getRemoteHost(), _commDetails.getPort());
                return;
            }

            LOG.info("Started client with " + _commDetails);

            byte[] message;
            while (!_stop.get()) {

                switch (_commDetails.getMode()) {
                    case Interval:

                        message = new byte[_commDetails.getMsgSize()];
                        new Random().nextBytes(message);

                        if (_client.sendMessage(message)) {
                            LOG.debug("Sent message through " + _commDetails);
                        }

                        try {
                            TimeUnit.MILLISECONDS.sleep(_commDetails.getInterval());
                        }
                        catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        break;

                    case Stream:

                        message = new byte[Client.MAX_MSG_SIZE_INTERVAL];
                        //sends n times this buffer until reached desired stream size
                        new Random().nextBytes(message);

                        if (_client.sendTestStream(message, _commDetails.getMsgSize())) {
                            LOG.debug("Sent test stream through " + _commDetails);
                        }

                        _stop.set(true);
                        break;
                }
            }

            //closing the connection
            //closeConnection();
        }

        private final CommDetails _commDetails;
    }

    class RequestStatsRunnable implements Runnable
    {
        public RequestStatsRunnable (int requestInterval)
        {
            _requestInterval = requestInterval;
        }

        @Override
        public void run ()
        {
            while (!_stop.get()) {

                try {
                    TimeUnit.MILLISECONDS.sleep(_requestInterval);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }

                //TODO this works only because we are waiting enough time, fix this it is a race condition

                try {
                    _client.requestStats();
                }
                catch (IOException e) {
                    LOG.error("Unable to request stats", e);
                }
            }

            //closeConnection();
        }

        private final int _requestInterval;
    }

    private void closeConnection ()
    {
        //final request before it dies
        try {
            Mocket.Statistics statistics = _client.requestStats();

            if (statistics == null) {
                LOG.warn("Requested statistics from Mockets is null");
            }

            if (statistics != null) {

                Bundle data = new Bundle();
                data.putString(getString(R.string.stats_mockets_bytes_sent),
                        Utils.humanReadableByteCount(statistics.getSentByteCount(), true));
                data.putString(getString(R.string.stats_mockets_packets_sent),
                        String.valueOf(statistics.getSentPacketCount()));
                data.putString(getString(R.string.stats_mockets_retransmits),
                        String.valueOf(statistics.getRetransmittedPacketCount()));
                data.putString(getString(R.string.stats_mockets_bytes_received),
                        Utils.humanReadableByteCount(statistics.getReceivedByteCount(), true));
                data.putString(getString(R.string.stats_mockets_packets_received),
                        String.valueOf(statistics.getReceivedPacketCount()));
                data.putString(getString(R.string.stats_mockets_duplicated_discarded_packets),
                        String.valueOf(statistics.getDuplicatedDiscardedPacketCount()));
                data.putString(getString(R.string.stats_mockets_no_room_discarded_packets),
                        String.valueOf(statistics.getNoRoomDiscardedPacketCount()));
                Message msg = Message.obtain(); //use of .obtain() instead of new Message() saves resources
                msg.arg1 = Notification.MOCKETS_SHORT_STATS.code();
                msg.setData(data);

                _stats.addMessage(msg);
            }
        }
        catch (IOException e) {
            LOG.error("Unable to request stats", e);
        }

        LOG.debug("Closing connection");
        _client.closeConnection();
    }

    /*
     * This is used to load the GroupManager and Mockets libraries on Service startup.
     */
    static {
        System.loadLibrary("gnustl_shared");
        System.loadLibrary("util");
        System.loadLibrary("mockets");
        System.loadLibrary("mocketsjavawrapper");
        System.loadLibrary("netutils");
    }

    private CommDetails _commDetails;
    private Client _client;
    private final AtomicBoolean _stop = new AtomicBoolean(false);
    private final ServiceMessenger _serviceMessenger = new ServiceMessenger(ClientCommService.class);
    private StatsRunnable _stats;
    private final static Logger LOG = Logger.getLogger(ClientCommService.class);
}
