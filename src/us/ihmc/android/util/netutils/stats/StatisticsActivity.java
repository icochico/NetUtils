package us.ihmc.android.util.netutils.stats;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;
import org.apache.log4j.Logger;
import us.ihmc.android.util.netutils.ActivityMessenger;
import us.ihmc.android.util.netutils.Notification;
import us.ihmc.android.util.netutils.R;
import us.ihmc.android.util.netutils.comm.ClientCommService;
import us.ihmc.android.util.netutils.comm.ServerCommService;
import us.ihmc.netutils.Mode;

/**
 * StatisticsActivity.java
 * <p/>
 * NetUtils <code>StatisticsActivity</code> displays current statistics of the connection.
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class StatisticsActivity extends Activity
{
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate (Bundle savedInstanceState)
    {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.statistics);
    }


    class UpdateServerStatsTask extends AsyncTask<Bundle, String, String>
    {
        @Override
        protected String doInBackground (Bundle... params)
        {
            final String messagesReceived = String.valueOf(params[0].getInt(getString(R.string
                    .stats_messages_received)));
            final String bytesReceived = params[0].getString(getString(R.string.stats_bytes_received));
            final String lostPacketsReceived = params[0].getString(getString(R.string.stats_lost_packets_received));
            final String throughputReceived = params[0].getString(getString(R.string.stats_throughput_received));


            publishProgress(messagesReceived, bytesReceived, lostPacketsReceived, throughputReceived);

            return "OK";
        }

        protected void onProgressUpdate (String... progress)
        {
            final TextView tvMessagesReceived = (TextView) findViewById(R.id.tvMessagesReceivedValue);
            tvMessagesReceived.setText(progress[0]);

            final TextView tvBytesReceived = (TextView) findViewById(R.id.tvBytesReceivedValue);
            tvBytesReceived.setText(progress[1]);

            final TextView tvLostPacketsReceived = (TextView) findViewById(R.id.tvPacketLossReceivedValue);
            tvLostPacketsReceived.setText(progress[2]);

            final TextView tvThroughputReceived = (TextView) findViewById(R.id.tvThroughputReceived);
            tvThroughputReceived.setText(progress[3]);
        }
    }

    class UpdateClientStatsTask extends AsyncTask<Bundle, String, String>
    {
        @Override
        protected String doInBackground (Bundle... params)
        {
            final String messagesSent = String.valueOf(params[0].getInt(getString(R.string.stats_messages_sent)));
            final String bytesSent = params[0].getString(getString(R.string.stats_bytes_sent));
            final String lostPackets = params[0].getString(getString(R.string.stats_lost_packets_sent));
            final String throughputSent = params[0].getString(getString(R.string.stats_throughput_sent));

            publishProgress(messagesSent, bytesSent, lostPackets, throughputSent);

            return "OK";
        }

        protected void onProgressUpdate (String... progress)
        {
            final TextView tvMessagesSent = (TextView) findViewById(R.id.tvMessagesSentValue);
            tvMessagesSent.setText(progress[0]);

            final TextView tvBytesSent = (TextView) findViewById(R.id.tvBytesSentValue);
            tvBytesSent.setText(progress[1]);

            final TextView tvLostPackets = (TextView) findViewById(R.id.tvPacketLossSentValue);
            tvLostPackets.setText(progress[2]);

            final TextView tvThroughputSent = (TextView) findViewById(R.id.tvThroughputSent);
            tvThroughputSent.setText(progress[3]);
        }
    }

    private final Context _context = this;
    private final ActivityMessenger _activityMessenger = new ActivityMessenger(StatisticsActivity.class);
    private final static Logger LOG = Logger.getLogger(StatisticsActivity.class);
}
