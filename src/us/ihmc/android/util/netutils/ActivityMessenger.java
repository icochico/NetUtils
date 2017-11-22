package us.ihmc.android.util.netutils;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.*;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ActivityMessenger.java
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class ActivityMessenger
{
    public ActivityMessenger (Class activityClass)
    {
        _activityClass = activityClass;
        _serviceConnections = new ConcurrentHashMap<Class, ServiceConnection>();
        _activityMessengers = new ConcurrentHashMap<Class, Messenger>();
        _isServiceBound = new ConcurrentHashMap<Class, Boolean>();
    }

    public ServiceConnection createServiceConnection (final Class toService, final Handler replyTo)
    {
        ServiceConnection conn = new ServiceConnection()
        {
            @Override
            public void onServiceConnected (ComponentName className,
                                            IBinder service)
            {
                if (service == null) {
                    LOG.error("IBinder is null");
                    return;
                }

                LOG.info("Connected to " + toService.getSimpleName());
                _service = service;

                //Do the handshake with Service in order to receive messages from it
                if (_activityMessengers.get(toService) == null) {

                    Messenger activityMessenger = new Messenger(service);
                    _activityMessengers.put(toService, activityMessenger);

                    Message handshake = Message.obtain();
                    handshake.arg1 = Notification.HANDSHAKE.code();
                    handshake.obj = _activityClass;

                    handshake.replyTo = new Messenger(replyTo);

                    try {
                        activityMessenger.send(handshake);
                    }
                    catch (RemoteException e) {
                        LOG.warn("Unable to send handshake message to " + toService.getSimpleName(), e);
                    }

                    _isServiceBound.put(toService, true);
                }
            }

            @Override
            public void onServiceDisconnected (ComponentName arg0)
            {
                LOG.debug("Called onServiceDisconnected");
                _isServiceBound.put(toService, false);
            }

            IBinder _service;
        };

        _serviceConnections.put(toService, conn);

        return conn;
    }

    public ServiceConnection getServiceConnection (Class serviceClass)
    {
        return _serviceConnections.get(serviceClass);
    }

    public void destroyServiceConnection (Class serviceClass)
    {
        LOG.debug("Destroying Service connection " + serviceClass);
        _serviceConnections.remove(serviceClass);
        _activityMessengers.remove(serviceClass);
        _isServiceBound.put(serviceClass, false);
    }

    public boolean isServiceBound (Class serviceClass)
    {
        if (_isServiceBound.get(serviceClass) == null) return false;

        return _isServiceBound.get(serviceClass);
    }

    private Map<Class, ServiceConnection> _serviceConnections;
    private Map<Class, Boolean> _isServiceBound;
    private Map<Class, Messenger> _activityMessengers;

    private final Class _activityClass;

    private static final Logger LOG = Logger.getLogger(ActivityMessenger.class);
}


