package us.ihmc.android.util.netutils.comm;

import android.os.Message;
import us.ihmc.android.util.netutils.ServiceMessenger;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * StatsRunnable.java
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class StatsRunnable implements Runnable
{
    public StatsRunnable (ServiceMessenger serviceMessenger)
    {
        _serviceMessenger = serviceMessenger;
        _messages = new LinkedBlockingDeque<Message>();
    }

    public void addMessage (Message message)
    {
        _messages.add(message);
    }

    @Override
    public void run ()
    {
        while (!STOP.get()) {
            try {
                Message m = _messages.poll(Long.MAX_VALUE, TimeUnit.DAYS);
                _serviceMessenger.toActivities().sendMessage(m);

                //TimeUnit.MILLISECONDS.sleep(50);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public final AtomicBoolean STOP = new AtomicBoolean(false);
    private final BlockingDeque<Message> _messages;
    private final ServiceMessenger _serviceMessenger;
}
