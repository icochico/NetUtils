package us.ihmc.android.util.netutils.comm;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.widget.Toast;
import com.koushikdutta.async.Util;
import org.apache.log4j.Logger;
import us.ihmc.android.util.netutils.*;
import us.ihmc.netutils.*;
import us.ihmc.netutils.protocol.Protocol;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * ServerCommService.java
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class ServerCommService extends Service implements MessageListener
{
    @Override
    public IBinder onBind (Intent intent)
    {
        if (INSTANCE == null) {

            _stats = new StatsRunnable(_serviceMessenger);
            (new Thread(_stats)).start();

            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

            String listenAddr = intent.getStringExtra(getString(R.string.transport_local_host));
            LOG.info("Using local listen address: " + listenAddr);

            int tcpPort = Integer.valueOf(sharedPref.getString(getString(R.string.netutils_tcp_port),
                    getString(R.string.netutils_tcp_port_default)));
            startTCPServer(listenAddr, tcpPort);

            int mocketsPort = Integer.valueOf(sharedPref.getString(getString(R.string.netutils_mockets_port),
                    getString(R.string.netutils_mockets_port_default)));
            startMocketsServer(listenAddr, mocketsPort);

            int streamMocketsPort = Integer.valueOf(sharedPref.getString(getString(R.string
                    .netutils_mockets_stream_port), getString(R.string.netutils_mockets_stream_port_default)));
            startStreamMocketsServer(listenAddr, streamMocketsPort);

            int udpPort = Integer.valueOf(sharedPref.getString(getString(R.string.netutils_udp_port),
                    getString(R.string.netutils_udp_port_default)));
            int tcpControlPort = Integer.valueOf(sharedPref.getString(getString(R.string.netutils_tcp_control_port),
                    getString(R.string.netutils_tcp_control_port_default)));
            boolean isMulticast = sharedPref.getBoolean(getString(R.string
                    .netutils_udp_multicast_enabled), Boolean.valueOf(getString(R.string
                    .netutils_udp_multicast_enabled_default)));
            startUDPServer(listenAddr, udpPort, tcpControlPort, isMulticast);

            (new Thread(_serviceMessenger, ServerCommService.class.getSimpleName() + "Messenger")).start();

            INSTANCE = this;
        }

        return _serviceMessenger.get().getBinder();
    }

    @Override
    public void onCreate ()
    {
        LOG.trace("Creating " + ServerCommService.class.getSimpleName());
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId)
    {
        return START_STICKY;
    }

    private void startTCPServer (String listenAddr, int tcpPort)
    {
        String serverConn = "TCP server on port " + tcpPort;
        LOG.debug("Starting " + serverConn);
        Server tcpServer;
        tcpServer = new Server(listenAddr, tcpPort, Protocol.TCP, null);
        tcpServer.addMessageListener(this);
        if (tcpServer.init())
            tcpServer.start();

    }

    private void startMocketsServer (String listenAddr, int mocketsPort)
    {
        String serverConn = "Mockets server on port " + mocketsPort;
        LOG.debug("Starting " + serverConn);
        Server mocketsServer;
        mocketsServer = new Server(listenAddr, mocketsPort, Protocol.Mockets,
                Utils.getConfigDir(this) + File.separator +
                        getString(R.string.app_mockets_conf_file));
        mocketsServer.addMessageListener(this);
        if (mocketsServer.init())
            mocketsServer.start();
    }

    private void startStreamMocketsServer (String listenAddr, int streamMocketsPort)
    {
        String serverConn = "StreamMockets server on port " + streamMocketsPort;
        LOG.debug("Starting " + serverConn);
        Server streamMocketsServer;
        streamMocketsServer = new Server(listenAddr, streamMocketsPort, Protocol.StreamMockets,
                Utils.getConfigDir(this) + File.separator + getString(R.string.app_mockets_conf_file));
        streamMocketsServer.addMessageListener(this);
        if (streamMocketsServer.init())
            streamMocketsServer.start();
    }

    private void startUDPServer (String listenAddr, int udpPort, int tcpControlPort, boolean isMulticast)
    {
        LOG.debug("Starting UDP server on port " + udpPort + " with control TCP server on port: " +
                tcpControlPort);
        Server udpServer;

        udpServer = new Server(isMulticast ? UDPCommHelper.DEFAULT_MULTICAST_ADDRESS : listenAddr,
                udpPort,
                isMulticast ? Protocol.UDPMulticast : Protocol.UDP, null,
                tcpControlPort);
        udpServer.addMessageListener(this);

        udpServer.init(); // TODO fix this difference of initialization between protocols
        udpServer.start();
    }

    @Override
    public void onMessage (short clientId, byte[] buf)
    {
        LOG.debug("Received message of size " + buf.length + " from client id: " + clientId);

        _msgSize.set(buf.length);
        _msgReceived.incrementAndGet();
    }

    @Override
    public void onProgress (int bytesSent, int total)
    {
        //do nothing
    }

    @Override
    public void onTestStream (short clientId, int streamSize, final Stats stats)
    {
        LOG.debug("Received test stream of size " + streamSize + " from Client: " + clientId);
        LOG.debug(stats);
        _streamSize.set(streamSize);
        deliverServerStats(stats);
    }

    @Override
    public void onStats (short clientId, final Stats stats)
    {
        LOG.debug("Received stats for client " + clientId + " sent msgs: " + stats.msgSent.get() + " received msgs:" +
                " " + stats.msgReceived.get());

        deliverServerStats(stats);
    }

    @Override
    public void onBoundError (Protocol protocol, int port)
    {
        String error = "Unable to bind " + protocol + " on port " + port;
        LOG.error(error);
        new ShowToastTask().execute(error);

    }

    class ShowToastTask extends AsyncTask<String, String, String>
    {
        @Override
        protected String doInBackground (String... params)
        {
            publishProgress(params[0]);
            return "OK";
        }

        protected void onProgressUpdate (String... progress)
        {
            Toast.makeText(INSTANCE, progress[0], Toast.LENGTH_LONG).show();
        }
    }


    private void deliverServerStats (Stats stats)
    {
        //update msg received with the ones effectively received by the server
        Stats serverStats = new Stats(Stats.Type.Server);
        serverStats.msgSent.set(stats.msgSent.get());
        serverStats.msgReceived.set(_msgReceived.get());
        serverStats.throughputSent.set(stats.throughputSent.get());
        serverStats.throughputReceived.set(stats.throughputReceived.get());

        String lostPacketsHR = serverStats.getPacketLoss() + " (" + serverStats.getPacketLossPercent() + "%)";
        String byteReceivedHR = Utils.humanReadableByteCount((_streamSize.get() == 0) ? _msgReceived.get() * _msgSize
                .get() : _streamSize.get(), true);
        String throughputSentHR = (Utils.humanReadableByteCount(stats.throughputSent.get(),
                true)) + "/sec"; //bytes/sec //FROM CLIENT
        String throughputReceivedHR = (Utils.humanReadableByteCount(stats.throughputReceived.get(),
                true)) + "/sec"; //bytes/sec

        Bundle data = new Bundle();
        data.putInt(getString(R.string.stats_messages_received), _msgReceived.intValue());
        data.putString(getString(R.string.stats_bytes_received), byteReceivedHR);
        data.putString(getString(R.string.stats_lost_packets_received), lostPacketsHR);
        data.putString(getString(R.string.stats_throughput_sent), throughputSentHR);
        data.putString(getString(R.string.stats_throughput_received), throughputReceivedHR);

        Message msg = Message.obtain(); //use of .obtain() instead of new Message() saves resources
        msg.arg1 = Notification.SERVER_STATS.code();
        msg.setData(data);

        _stats.addMessage(msg);
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

    private static volatile ServerCommService INSTANCE = null;
    private final ServiceMessenger _serviceMessenger = new ServiceMessenger(ServerCommService.class);
    private StatsRunnable _stats;
    private final AtomicInteger _msgReceived = new AtomicInteger(0);
    private final AtomicInteger _msgSize = new AtomicInteger(0);
    private final AtomicInteger _streamSize = new AtomicInteger(0);
    private final static Logger LOG = Logger.getLogger(ServerCommService.class);
}
