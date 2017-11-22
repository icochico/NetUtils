package us.ihmc.android.util.netutils.comm;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import org.apache.log4j.Logger;
import us.ihmc.android.util.netutils.Notification;
import us.ihmc.android.util.netutils.R;
import us.ihmc.android.util.netutils.ServiceMessenger;
import us.ihmc.android.util.netutils.Utils;
import us.ihmc.mockets.monitor.MocketStatus;
import us.ihmc.mockets.monitor.MocketStatusListener;
import us.ihmc.mockets.monitor.Monitor;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * MocketsStatsService.java
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class MocketsStatsService extends Service implements MocketStatusListener
{
    @Override
    public IBinder onBind (Intent intent)
    {
        if (INSTANCE == null) {

            _stats = new StatsRunnable(_serviceMessenger);
            (new Thread(_stats)).start();
            (new Thread(_serviceMessenger, MocketsStatsService.class.getSimpleName() + "Messenger")).start();

            //mockets monitor
            int mocketsUDPPort = intent.getIntExtra(getString(R.string.stats_mockets_monitor_port),
                    MocketStatus.DEFAULT_MOCKET_STATUS_PORT);
            try {
                Monitor m = new Monitor(mocketsUDPPort);
                m.setListener(this);
                m.start();
            }
            catch (Exception e) {
                LOG.error("Unable to start Mockets Monitor on UDP port " + mocketsUDPPort, e);
            }

            INSTANCE = this;
        }

        return _serviceMessenger.get().getBinder();
    }

    @Override
    public void onCreate ()
    {
        LOG.trace("Creating " + MocketsStatsService.class.getSimpleName());
    }

    @Override
    public int onStartCommand (Intent intent, int flags, int startId)
    {
        return START_STICKY;
    }

    @Override
    public void statusUpdateArrived (MocketStatus.EndPointsInfo epi, byte msgType)
    {
        System.out.println("\nStatus Update <" + MocketStatus.MocketStatusNoticeType.values()[msgType] + "> received " +
                "from " + epi.PID + " " + epi.identifier);
    }

    @Override
    public void statisticsInfoUpdateArrived (MocketStatus.EndPointsInfo epi, MocketStatus.MocketStatisticsInfo msi)
    {
        LOG.debug("Mockets statisticsInfoUpdateArrived - EndPointsInfo, MocketStatisticsInfo");

        //send 1 every 10 to not clog the main thread
        int count = _updateCounter.incrementAndGet();
        if (count == 1 || (count % 10) == 0) {
            StatsWrapper wr = new StatsWrapper(epi, msi, null);
            deliverMocketsStats(wr);
        }
    }

    @Override
    public void statisticsInfoUpdateArrived (MocketStatus.EndPointsInfo epi, MocketStatus.MessageStatisticsInfo messSi)
    {
        LOG.debug("Mockets statisticsInfoUpdateArrived - EndPointsInfo, MessageStatisticsInfo");

        //StatsWrapper wr = new StatsWrapper(epi, null, messSi);
    }

    private void deliverMocketsStats (StatsWrapper stats)
    {
        //update msg received with the ones effectively received by the server
        Bundle data = new Bundle();
        data.putString(getString(R.string.stats_mockets_last_contact_time),
                Utils.getTimeStamp(stats.msi.lastContactTime));
        data.putString(getString(R.string.stats_mockets_bytes_sent),
                Utils.humanReadableByteCount(stats.msi.sentBytes, true));
        data.putString(getString(R.string.stats_mockets_packets_sent),
                String.valueOf(stats.msi.sentPackets));
        data.putString(getString(R.string.stats_mockets_retransmits),
                String.valueOf(stats.msi.retransmits));
        data.putString(getString(R.string.stats_mockets_bytes_received),
                Utils.humanReadableByteCount(stats.msi.receivedBytes, true));
        data.putString(getString(R.string.stats_mockets_packets_received),
                String.valueOf(stats.msi.receivedPackets));
        data.putString(getString(R.string.stats_mockets_duplicated_discarded_packets),
                String.valueOf(stats.msi.duplicatedDiscardedPackets));
        data.putString(getString(R.string.stats_mockets_no_room_discarded_packets),
                String.valueOf(stats.msi.noRoomDiscardedPackets));
        data.putString(getString(R.string.stats_mockets_reassembly_skipped_discarded_packets),
                String.valueOf(stats.msi.reassemblySkippedDiscardedPackets));
        data.putString(getString(R.string.stats_mockets_estimated_round_trip_time),
                String.valueOf(stats.msi.estimatedRTT));
        data.putString(getString(R.string.stats_mockets_unacknowledged_data_size),
                Utils.humanReadableByteCount(stats.msi.unacknowledgedDataSize, true));
        data.putString(getString(R.string.stats_mockets_pending_data_size),
                Utils.humanReadableByteCount(stats.msi.pendingDataSize, true));
        data.putString(getString(R.string.stats_mockets_pending_packet_queue_size),
                String.valueOf(stats.msi.pendingPacketQueueSize));
        data.putString(getString(R.string.stats_mockets_reliable_sequenced_data_size),
                Utils.humanReadableByteCount(stats.msi.reliableSequencedDataSize, true));
        data.putString(getString(R.string.stats_mockets_reliable_sequenced_packet_queue_size),
                String.valueOf(stats.msi.reliableSequencedPacketQueueSize));

        Message msg = Message.obtain(); //use of .obtain() instead of new Message() saves resources
        msg.arg1 = Notification.MOCKETS_STATS.code();
        msg.setData(data);

        _stats.addMessage(msg);
    }

    class StatsWrapper
    {
        StatsWrapper (MocketStatus.EndPointsInfo epi,
                      MocketStatus.MocketStatisticsInfo msi,
                      MocketStatus.MessageStatisticsInfo messSi)
        {
            this.epi = epi;
            this.msi = msi;
            this.messSi = messSi;
        }

        final MocketStatus.EndPointsInfo epi;
        final MocketStatus.MocketStatisticsInfo msi;
        final MocketStatus.MessageStatisticsInfo messSi;
    }

    private static volatile MocketsStatsService INSTANCE = null;
    private final ServiceMessenger _serviceMessenger = new ServiceMessenger(MocketsStatsService.class);
    private final AtomicInteger _updateCounter = new AtomicInteger(0);
    private StatsRunnable _stats;

    private final static Logger LOG = Logger.getLogger(MocketsStatsService.class);
}
