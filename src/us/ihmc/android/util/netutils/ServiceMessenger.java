package us.ihmc.android.util.netutils;

import android.os.*;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ServiceMessenger.java
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class ServiceMessenger implements Runnable
{
    public ServiceMessenger (final Class serviceClass)
    {
        _serviceClass = serviceClass;
        _activities = new ConcurrentHashMap<Class, Messenger>();

        //TODO might wanna make this a parameter (Handler fromActivity)
        _serviceMessenger = new Messenger(new Handler()
        {
            @Override
            public void handleMessage (Message msg)
            {
                int code = msg.arg1;
                Notification notification = Notification.fromCode(code);

                Class className = (Class) msg.obj;
                LOG.debug(serviceClass.getSimpleName() + " received notification: " + notification + " from " + className);
                // Message wrap; //doing a wrap (copy) of the message because of Android OS requirements
                switch (notification) {

                    case HANDSHAKE:
                        if (!_activities.containsKey(className)) {
                            _activities.put(className, msg.replyTo);
                            LOG.debug("Added " + className + " to the activities of this " + _serviceClass);
                        }
                        break;
                }
            }
        });
    }

    public Handler toActivities ()
    {
        return _toActivityHandler;
    }

    public Messenger get ()
    {
        return _serviceMessenger;
    }

    public void run ()
    {
        LOG.trace(this + " started");

        Looper.prepare();

        //TODO check if it's actually needed to put this in a thread
        _toActivityHandler = new Handler()
        {
            @Override
            public void handleMessage (Message msg)
            {
                Message wrap = Message.obtain();
                if (wrap == null) {
                    LOG.trace("Message is null, doing nothing");
                    return;
                }
                wrap.copyFrom(msg);

                if (_activities.isEmpty()) {
                    LOG.warn("No clients found yet, reprocessing message");
                    this.sendMessageDelayed(wrap, 500);
                    return;
                }

                //LOG.trace("Number of registered activities: " +  _activities.size());
                for (Messenger msgr : _activities.values()) {
                    try {
                        msgr.send(wrap);
                    }
                    catch (RemoteException e) {
                        LOG.warn("Unable to send message to client, reprocessing message");
                        this.sendMessageDelayed(wrap, 500);
                    }
                }
            }
        };

        Looper.loop();

        LOG.trace(this + " ended");
    }

    //service to activity
    private Handler _toActivityHandler;
    private Map<Class, Messenger> _activities;
    private Messenger _serviceMessenger;

    //activity to service


    private final Class _serviceClass;

    private static final Logger LOG = Logger.getLogger(ServiceMessenger.class);
}
