package us.ihmc.android.util.netutils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.*;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.apache.log4j.Logger;
import us.ihmc.aci.grpMgr.TransportMode;
import us.ihmc.android.util.netutils.comm.ClientCommService;
import us.ihmc.android.util.netutils.comm.MocketsStatsService;
import us.ihmc.android.util.netutils.comm.ServerCommService;
import us.ihmc.android.util.netutils.discovery.DiscoveryService;
import us.ihmc.android.util.netutils.discovery.Peer;
import us.ihmc.android.util.netutils.discovery.PeerListActivity;
import us.ihmc.android.util.netutils.preferences.SettingsActivity;
import us.ihmc.android.util.netutils.stats.StringListActivity;
import us.ihmc.mockets.monitor.MocketStatus;
import us.ihmc.netutils.Client;
import us.ihmc.netutils.Mode;
import us.ihmc.netutils.protocol.Protocol;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MainActivity.java
 * <p/>
 * NetUtils main activity.
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class MainActivity extends Activity
{
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        //configure logger
        Utils.configureLog4J(_context);
        //configure app
        loadConfig();
        //repopulate network list at connectivity change
        registerIntentsReceiver();

        populateTVDeviceModel();
        populateTVBuildVersion();
        populateTVConfDir();
        populateSpnNetInterface();
        populateSpnMode();
        populateButtonOptions(true);
        populateSpnProtocol();
        populateButtonFindTarget(true);
        populateProgressBar(false);
        populateButtonStart(true);
        populateButtonStop(false);
        populateACTVTarget(true);
        populateButtonStatistics(null);

        //bind services
        bindDiscoveryService();
        bindServerCommService(_localPeer.getIpAddress());
        bindMocketsStatsService();
    }

    private void loadConfig ()
    {
        LOG.info("== Loading app configuration ==");
        //get default preferences
        //SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences sharedPref = getPreferences(MODE_PRIVATE);
        //PreferenceManager.setDefaultValues(this, R.xml.preferences, true);

        LOG.info("== Discovery ==");
        //get GroupManager configuration file path
        File configFile = new File(Utils.getConfigFile(this));
        LOG.info("Default discovery config file is: " + configFile.getAbsolutePath());

        if (!configFile.exists()) {
            File configDir = new File(Utils.getConfigDir(this));

            if (!configDir.exists()) {
                LOG.warn("Unable to find configuration directory, creating default dir: " + configDir);
                configDir.mkdir();
            }

            try {
                Utils.writeConfigFile(getDefaultPreferences(), configFile.getAbsolutePath());
                LOG.info("Config file not found, created default file in: " + configFile.getAbsolutePath());
            }
            catch (IOException e) {
                LOG.error("Unable to write config file to: " + configFile.getAbsolutePath());
            }
        }

        try {
            _config = Utils.readConfigFile(configFile.getAbsolutePath());
        }
        catch (IOException e) {
            LOG.error("Unable to read config file frp,: " + configFile.getAbsolutePath());
            return;
        }

        LOG.info("Read preferences from file: " + configFile);

        //ATTEMPT to acquire multicast lock
        WifiManager wifi = (WifiManager)getSystemService( Context.WIFI_SERVICE );
        if(wifi != null){
            WifiManager.MulticastLock lock = wifi.createMulticastLock("NetUtils");
            lock.acquire();
        }

        String localIp = Utils.getActiveIPAddress(_context);
        // Set local Ip and update config
        //_config.setProperty(getString(R.string.aci_groupmanager_wifiIFs), Utils.getWiFiIPAddress(_context));
        String netIFs = localIp + "/" + getString(R.string.aci_groupmanager_netIFs_default); //add transport mode
        _config.setProperty(getString(R.string.aci_groupmanager_netIFs), netIFs);
        _config.setProperty(getString(R.string.netutils_local_host), localIp);

        try {
            Utils.writeConfigFile(_config, configFile.getAbsolutePath());
        }
        catch (IOException e) {
            LOG.error("Unable to write config file to: " + configFile.getAbsolutePath());
        }

        // Set localId
        String localId = _config.getProperty(getString(R.string.aci_groupmanager_nodeUUID));
        LOG.debug("Local id as read from configuration was: " + localId);
        try {
            _localPeer = new Peer(localId);
            LOG.debug("Local peer as read from configuration was: " + _localPeer);
        }
        catch (IllegalArgumentException e) {
            _localPeer = new Peer(Utils.getDeviceModel(), localIp);
            LOG.debug("Wrong format for peer id, setting it to: " + _localPeer.getId());
        }

        _localPeer.setIpAddress(localIp); //update the id to the new IP address
        LOG.debug("(update) Local peer is now: " + _localPeer);

        SharedPreferences.Editor editor = sharedPref.edit();
        //updating preferences
        for (Map.Entry<Object, Object> entry : _config.entrySet()) {
            LOG.trace("Found pref: " + entry.getKey() + ": " + entry.getValue().toString());
            editor.putString(entry.getKey().toString(), entry.getValue().toString());
        }
        editor.apply();

        Map<String, ?> keys = sharedPref.getAll();
        LOG.debug("Number of saved preferences: " + keys.entrySet().size());
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            LOG.debug(entry.getKey() + ": " + entry.getValue().toString());
        }
    }

    private Properties getDefaultPreferences ()
    {
        Properties defaultPref = new Properties();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        //properties already present in Settings
        Map<String, ?> keys = sharedPref.getAll();
        for (Map.Entry<String, ?> entry : keys.entrySet()) {
            defaultPref.setProperty(entry.getKey(), entry.getValue().toString());
        }

        //special case for netIFs
        String localIP = Utils.getActiveIPAddress(_context);
        String defaultNetIFs = localIP + "/" + getString(R.string.aci_groupmanager_netIFs_default);
        defaultPref.setProperty(getString(R.string.aci_groupmanager_netIFs), defaultNetIFs);
        LOG.info("Setting default network interface to: " + defaultNetIFs);

        defaultPref.setProperty(getString(R.string.aci_groupmanager_nodeUUID), Peer.idGenerator(Utils.getDeviceModel
                (), localIP)); //remove all whitespaces
        LOG.info("Setting default (GroupManager) node uuid to: " + Peer.idGenerator(Utils.getDeviceModel(), localIP));

        return defaultPref;
    }


    private void populateACTVTarget (boolean enabled)
    {
        final AutoCompleteTextView actvTarget = (AutoCompleteTextView) findViewById(R.id.actvSelectedTargetHost);
        actvTarget.setImeActionLabel(getString(R.string.actvTargetDone), KeyEvent.KEYCODE_ENTER);
        actvTarget.setOnKeyListener(new View.OnKeyListener()
        {
            @Override
            public boolean onKey (View v, int keyCode, KeyEvent event)
            {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    // Perform action on key press
                    Toast.makeText(MainActivity.this, "Target IP set to " + actvTarget.getText(),
                            Toast.LENGTH_SHORT).show();

                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(actvTarget.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });

        actvTarget.setText(Utils.getPreferenceFromDisk(_config, getString(R.string.netutils_remote_host),
                getString(R.string.netutils_remote_host_default)));
        actvTarget.setEnabled(enabled);
    }

    private Button populateButtonStop (boolean enabled)
    {
        final Button btStop = (Button) findViewById(R.id.btStop);
        btStop.setEnabled(enabled);
        btStop.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v)
            {
                unbindClientCommService();
            }
        });

        return btStop;
    }

    private void unbindClientCommService ()
    {
        //stopService(new Intent(_context, ClientCommService.class));
        if (_activityMessenger.isServiceBound(ClientCommService.class)) {
            LOG.debug("Unbinding " + ClientCommService.class.getSimpleName());
            unbindService(_activityMessenger.getServiceConnection(ClientCommService.class));
            _activityMessenger.destroyServiceConnection(ClientCommService.class);
        }

        _progressBar.setProgress(0);
        populateProgressBar(false);
        populateButtonStart(true);
        populateButtonStop(false);
        populateButtonFindTarget(true);
        populateButtonOptions(true);
        populateACTVTarget(true);
        populateButtonStatistics(dumpStatistics().getAbsolutePath());
    }

    private Button populateButtonStart (boolean enabled)
    {
        final Button btStart = (Button) findViewById(R.id.btStart);
        btStart.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v)
            {
                String remoteHost = ((AutoCompleteTextView) findViewById(R.id.actvSelectedTargetHost)).getText()
                        .toString();
                if (!Utils.isIPAddress(remoteHost)) {
                    Toast.makeText(_context, "IP address in 'Target' is not valid. Please specify a valid IP " +
                            "address", Toast.LENGTH_LONG).show();
                    return;
                }

                Utils.savePreference(_context, _config, getString(R.string.netutils_remote_host), remoteHost);

                SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(_context);
                Mode mode = Mode.valueOf(((Spinner) (findViewById(R.id.spnMode))).getSelectedItem().toString());
                Protocol protocol = Protocol.valueOf(((Spinner) (findViewById(R.id.spnProtocol))).getSelectedItem()
                        .toString());
                int messageSize = Integer.parseInt(Utils.getPreferenceFromDisk(_config,
                        getString(R.string.netutils_msg_size), getString(R.string.netutils_msg_size_default)));
                int interval = Integer.parseInt(Utils.getPreferenceFromDisk(_config,
                        getString(R.string.netutils_interval), getString(R.string.netutils_interval_default)));

                if (!isInputValid(mode, protocol)) return;

                bindClientCommService(remoteHost, protocol, messageSize, mode, interval);
            }
        });
        btStart.setEnabled(enabled);

        return btStart;
    }

    private void populateSpnNetInterface ()
    {
        final Spinner spnNetInterface = (Spinner) findViewById(R.id.spnNetInterface);

        List<Utils.NetInterface> interfaces = Utils.getNetInterfaces();
        if (interfaces.size() == 0) interfaces.add(new Utils.NetInterface("No interfaces found", ""));
        ArrayAdapter<Utils.NetInterface> netInterfaceAdapter = new ArrayAdapter<Utils.NetInterface>(this,
                android.R.layout.simple_spinner_item, interfaces);
        spnNetInterface.setAdapter(netInterfaceAdapter);
        spnNetInterface.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected (AdapterView<?> parent, View view, int position, long id)
            {
                String netInterface = spnNetInterface.getSelectedItem().toString();
                LOG.debug("Called onItemSelected, selected network interface: " + netInterface);
            }

            @Override
            public void onNothingSelected (AdapterView<?> parent)
            {
                //spinSelect.setSelection(iFaceList.size() - 1);
            }
        });
    }

    private ProgressBar populateProgressBar (boolean visible)
    {
        if (_progressBar == null) {
            _progressBar = (ProgressBar) findViewById(R.id.pbProgress);
        }
        _progressBar.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        return _progressBar;
    }

    private Button populateButtonFindTarget (boolean enabled)
    {
        final Button btFindTarget = (Button) findViewById(R.id.btFindTarget);
        btFindTarget.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v)
            {

                Intent intent = new Intent(_context, PeerListActivity.class);

                // intent.putParcelableArrayListExtra(getString(R.string.discovery_peer_list),
                // new ArrayList<Peer>(_peers.values()));
                ArrayList<String> peersStrList = new ArrayList<String>();
                for (Peer p : _peers.values()) {
                    String peerStr = p.toJSON();
                    peersStrList.add(peerStr);
                }
                intent.putStringArrayListExtra(getString(R.string.discovery_peer_list), peersStrList);
                startActivityForResult(intent, Notification.PEER_LIST.code());
            }
        });

        btFindTarget.setEnabled(enabled);

        return btFindTarget;
    }

    private Spinner populateSpnMode ()
    {
        final Spinner spnMode = (Spinner) findViewById(R.id.spnMode);
        final ArrayAdapter<CharSequence> modeAdapter = ArrayAdapter.createFromResource(this,
                R.array.modes_array, android.R.layout.simple_spinner_item);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        spnMode.setAdapter(modeAdapter);

        return spnMode;
    }

    private Button populateButtonOptions (boolean enabled)
    {
        final Button btOptions = (Button) findViewById(R.id.btOptions);
        btOptions.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v)
            {

                Intent intent = new Intent(_context, OptionsActivity.class);

                Mode mode = Mode.valueOf(((Spinner) (findViewById(R.id.spnMode))).getSelectedItem().toString());
                Protocol protocol = Protocol.valueOf(((Spinner) (findViewById(R.id.spnProtocol))).getSelectedItem()
                        .toString());
                if (!isInputValid(mode, protocol)) return;

                switch (mode) {
                    case Interval:
                        intent.putExtra(getString(R.string.netutils_msg_size), getString(R.string
                                .netutils_msg_size_default));
                        break;
                    case Stream:
                        intent.putExtra(getString(R.string.netutils_msg_size), Utils.getPreferenceFromDisk(_config,
                                getString(R.string.netutils_msg_size), String.valueOf(Client.DEFAULT_MSG_SIZE_STREAM)));
                        break;

                }
                intent.putExtra(getString(R.string.netutils_mode), mode.toString());
                startActivityForResult(intent, Notification.OPTIONS.code());
            }
        });

        btOptions.setEnabled(enabled);

        return btOptions;
    }

    private boolean isInputValid (Mode mode, Protocol protocol)
    {
        if (mode.equals(Mode.Stream) && protocol.equals(Protocol.UDP)) {
            Toast.makeText(_context, "Unable to use Stream mode with UDP.", Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    private Button populateButtonStatistics (final String statsFilePath)
    {
        final Button btStatistics = (Button) findViewById(R.id.btStatistics);
        btStatistics.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v)
            {

                Intent intent = new Intent(_context, StringListActivity.class);
                intent.putExtra(getString(R.string.stats_file_path), statsFilePath);
                startActivity(intent);
            }
        });

        btStatistics.setEnabled((statsFilePath != null));
        return btStatistics;
    }

    private TextView populateTVBuildVersion ()
    {
        final TextView tvBuildVersion = (TextView) findViewById(R.id.tvBuildVersion);
        tvBuildVersion.append(" " + Utils.getLastBuildTimeStamp(_context));

        return tvBuildVersion;
    }

    private TextView populateTVDeviceModel ()
    {
        final TextView tvDeviceModel = (TextView) findViewById(R.id.tvDeviceModel);
        tvDeviceModel.append(" " + Utils.getDeviceModel());

        return tvDeviceModel;
    }

    private TextView populateTVConfDir ()
    {
        final TextView tvConfDir = (TextView) findViewById(R.id.tvConfDir);
        tvConfDir.append(" " + Utils.getConfigDir(_context));

        return tvConfDir;
    }

    private Spinner populateSpnProtocol ()
    {
        final Spinner spnProtocol = (Spinner) findViewById(R.id.spnProtocol);
        final ArrayAdapter<CharSequence> protocolAdapter = ArrayAdapter.createFromResource(this,
                R.array.protocols_array, android.R.layout.simple_spinner_item);
        protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        spnProtocol.setAdapter(protocolAdapter);

        return spnProtocol;
    }

    @Override
    protected void onActivityResult (int requestCode, int resultCode, Intent data)
    {
        LOG.debug("Called onActivityResult with request code: " + requestCode + " result code: " + resultCode);

        if (resultCode != RESULT_OK) {
            LOG.warn("Result code was NOT OK for activity result.");
            return;
        }

        if (requestCode == Notification.PEER_LIST.code()) {

            LOG.debug("Processing result of a " + Notification.PEER_LIST + " request");
            Peer peer = data.getParcelableExtra(getString(R.string.discovery_remote_peer));
            final AutoCompleteTextView actvTargetHost = (AutoCompleteTextView) findViewById(R.id
                    .actvSelectedTargetHost);
            LOG.debug("Setting target host to: " + peer.getIpAddress());
            actvTargetHost.setText(peer.getIpAddress());
        }
        else if (requestCode == Notification.OPTIONS.code()) {
            LOG.debug("Processing result of a " + Notification.OPTIONS + " request");

            int messageSize = data.getIntExtra(getString(R.string.netutils_msg_size),
                    Integer.parseInt(getString(R.string.netutils_msg_size_default)));
            Toast.makeText(MainActivity.this, "Message size set to " + Utils.humanReadableByteCount(messageSize,
                    true), Toast.LENGTH_SHORT).show();
            int interval = data.getIntExtra(getString(R.string.netutils_interval), Integer.parseInt(getString(R
                    .string.netutils_interval_default)));

            Utils.savePreference(_context, _config, getString(R.string.netutils_interval), String.valueOf(interval));
            Utils.savePreference(_context, _config, getString(R.string.netutils_msg_size), String.valueOf(messageSize));
        }
    }

    public void bindDiscoveryService ()
    {
        //Create Intent
        Intent intent = new Intent(this, DiscoveryService.class);

        if (_config != null) {
            LOG.info("Using configuration file to bind");
            intent.putExtra(getString(R.string.discovery_config_enabled), true);
            //save changes to config on SDCard
            _config.setProperty(getString(R.string.aci_groupmanager_nodeUUID), _localPeer.getId());
            try {
                _config.store(new FileOutputStream(Utils.getConfigFile(this)), null);
            }
            catch (IOException e) {
                String warnMsg = "Unable to write GroupManager config file on SDCard.";
                LOG.warn(warnMsg);
                Toast.makeText(MainActivity.this, warnMsg, Toast.LENGTH_SHORT).show();
            }
        }
        else {
            LOG.info("Using default settings to bind");
            intent.putExtra(getString(R.string.discovery_config_enabled), false);
            intent.putExtra(getString(R.string.discovery_local_peer), _localPeer);
            intent.putExtra(getString(R.string.discovery_transport), TransportMode.UDP_MULTICAST.getModeAsString());
            intent.putExtra(getString(R.string.discovery_port), getString(R.string.aci_groupmanager_port_default));
        }

        // Bind to DiscoveryService
        bindService(intent, _activityMessenger.createServiceConnection(DiscoveryService.class,
                _fromDiscoveryService), Context.BIND_AUTO_CREATE);
    }

    /**
     * Messages from DiscoveryService.
     */
    private Handler _fromDiscoveryService = new Handler()
    {
        @Override
        public void handleMessage (Message msg)
        {
            int code = msg.arg1;
            Notification notification = Notification.fromCode(code);
            LOG.trace("Received notification: " + notification);

            Peer p = msg.getData().getParcelable(getString(R.string.discovery_remote_peer));
            _peers.put(p.getId(), p);  //even in case of dead peer, update reachable status. don't remove

            switch (notification) {
                case NEW_PEER:
                    break;
                case DEAD_PEER:
                    break;
            }
        }
    };

    private void bindServerCommService (String localHost)
    {
        Intent intent = new Intent(this, ServerCommService.class);
        intent.putExtra(getString(R.string.transport_local_host), localHost);
        // Bind to ServerCommService
        bindService(intent, _activityMessenger.createServiceConnection(ServerCommService.class,
                _fromService), Context.BIND_AUTO_CREATE);
    }

    private void bindMocketsStatsService ()
    {
        Intent intent = new Intent(this, MocketsStatsService.class);
        intent.putExtra(getString(R.string.stats_mockets_monitor_port), MocketStatus.DEFAULT_MOCKET_STATUS_PORT);
        bindService(intent, _activityMessenger.createServiceConnection(MocketsStatsService.class,
                _fromService), Context.BIND_AUTO_CREATE);
    }

    private void bindClientCommService (String remoteHost, Protocol protocol, int messageSize, Mode mode, int interval)
    {

        Intent intent = new Intent(this, ClientCommService.class);

        //TODO Make Comm Parcelable so it is possible to build it here and send it

        intent.putExtra(getString(R.string.transport_remote_host), remoteHost);
        intent.putExtra(getString(R.string.transport_protocol), protocol.toString());
        intent.putExtra(getString(R.string.transport_msg_size), messageSize);
        intent.putExtra(getString(R.string.transport_mode), mode.toString());
        intent.putExtra(getString(R.string.transport_interval), interval);

        // Bind to ClientCommService
        if (bindService(intent, _activityMessenger.createServiceConnection(ClientCommService.class,
                _fromService), Context.BIND_AUTO_CREATE)) {
            populateButtonStart(false);
            populateButtonStop(true);
            populateButtonFindTarget(false);
            populateACTVTarget(false);
            populateButtonOptions(false);
            populateButtonStatistics(null);

            _progressBar = populateProgressBar(true);
            _progressBar.setProgress(0);
            _progressBar.setMax(messageSize);
        }
    }

    private File dumpStatistics ()
    {
        //write properties on file
        File statsDir = new File(Utils.getExternalStorageDirectory()
                + File.separator
                + getString(R.string.app_dir)
                + File.separator
                + getString(R.string.app_stats_dir));

        if (!statsDir.exists()) statsDir.mkdir();
        String statsId = _config.getProperty(getString(R.string.aci_groupmanager_nodeUUID))
                + "-"
                + Utils.getTimeStampNow();
        File statsFile = new File(statsDir.getAbsolutePath()
                + File.separator
                + getString(R.string.app_stats_file)
                + statsId
                + ".txt");
        try {
            _statistics.store(new FileOutputStream(statsFile), null);
        }
        catch (IOException e) {
            LOG.error("Unable to save statistics file", e);
        }

        return statsFile;
    }

    /**
     * Messages from ClientCommService, ServerCommService, and MocketsStatsService.
     */
    private Handler _fromService = new Handler()
    {
        @Override
        public void handleMessage (Message msg)
        {
            int code = msg.arg1;
            Notification notification = Notification.fromCode(code);
            if (notification.code() != Notification.PROGRESS_STATS.code())
                LOG.debug("Received notification: " + notification);
            final Bundle data = msg.getData();

            switch (notification) {
                case CONNECTION_ERROR:
                    _progressBar.post(new Runnable()
                    {
                        @Override
                        public void run ()
                        {
                            String remoteHost = data.getString(getString(R.string.transport_remote_host));
                            int port = data.getInt(getString(R.string.transport_remote_port));
                            unbindClientCommService();
                            _progressBar.setProgress(0);
                            Toast.makeText(MainActivity.this, "Unable to connect to " + remoteHost + ":" + port,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    break;
                case BOUND_ERROR:
                    _progressBar.post(new Runnable()
                    {
                        @Override
                        public void run ()
                        {
                            String protocol = data.getString(getString(R.string.transport_protocol));
                            int port = data.getInt(getString(R.string.transport_local_port));
                            Toast.makeText(MainActivity.this, "Unable to bind local " + protocol + " server to port "
                                            + port,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    break;
                case CLIENT_STATS:
                    _statistics.setProperty(getString(R.string.stats_remote_host),
                            data.getString(getString(R.string.stats_remote_host)));
                    _statistics.setProperty(getString(R.string.stats_remote_port),
                            data.getString(getString(R.string.stats_remote_port)));
                    _statistics.setProperty(getString(R.string.stats_protocol),
                            data.getString(getString(R.string.stats_protocol)));
                    _statistics.setProperty(getString(R.string.stats_mode),
                            data.getString(getString(R.string.stats_mode)));
                    _statistics.setProperty(getString(R.string.stats_message_size),
                            data.getString(getString(R.string.stats_message_size)));
                    _statistics.setProperty(getString(R.string.stats_messages_sent),
                            String.valueOf(data.getInt(getString(R.string.stats_messages_sent))));
                    _statistics.setProperty(getString(R.string.stats_bytes_sent),
                            data.getString(getString(R.string.stats_bytes_sent)));
                    _statistics.setProperty(getString(R.string.stats_lost_packets_sent),
                            data.getString(getString(R.string.stats_lost_packets_sent)));
                    _statistics.setProperty(getString(R.string.stats_throughput_sent),
                            data.getString(getString(R.string.stats_throughput_sent)));
                    _statistics.setProperty(getString(R.string.stats_throughput_received),
                            data.getString(getString(R.string.stats_throughput_received)));
                    break;
                case SERVER_STATS:
                    _statistics.setProperty(getString(R.string.stats_messages_received),
                            String.valueOf(data.getInt(getString(R.string.stats_messages_received))));
                    _statistics.setProperty(getString(R.string.stats_bytes_received),
                            data.getString(getString(R.string.stats_bytes_received)));
                    _statistics.setProperty(getString(R.string.stats_lost_packets_received),
                            data.getString(getString(R.string.stats_lost_packets_received)));
                    _statistics.setProperty(getString(R.string.stats_throughput_sent),
                            data.getString(getString(R.string.stats_throughput_sent)));
                    _statistics.setProperty(getString(R.string.stats_throughput_received),
                            data.getString(getString(R.string.stats_throughput_received)));
                    break;
                case MOCKETS_STATS:
                case MOCKETS_SHORT_STATS:
                    _statistics.setProperty(getString(R.string.stats_mockets_bytes_sent),
                            data.getString(getString(R.string.stats_mockets_bytes_sent)));
                    _statistics.setProperty(getString(R.string.stats_mockets_packets_sent),
                            data.getString(getString(R.string.stats_mockets_packets_sent)));
                    _statistics.setProperty(getString(R.string.stats_mockets_retransmits),
                            data.getString(getString(R.string.stats_mockets_retransmits)));
                    _statistics.setProperty(getString(R.string.stats_mockets_bytes_received),
                            data.getString(getString(R.string.stats_mockets_bytes_received)));
                    _statistics.setProperty(getString(R.string.stats_mockets_packets_received),
                            data.getString(getString(R.string.stats_mockets_packets_received)));
                    _statistics.setProperty(getString(R.string.stats_mockets_duplicated_discarded_packets),
                            data.getString(getString(R.string.stats_mockets_duplicated_discarded_packets)));
                    _statistics.setProperty(getString(R.string.stats_mockets_no_room_discarded_packets),
                            data.getString(getString(R.string.stats_mockets_no_room_discarded_packets)));

                    //extended stats not available on MOCKETS_SHORT_STATS
                    if (notification.equals(Notification.MOCKETS_STATS)) {
                        _statistics.setProperty(getString(R.string.stats_mockets_last_contact_time),
                                String.valueOf(data.getString(getString(R.string.stats_mockets_last_contact_time))));
                        _statistics.setProperty(getString(R.string.stats_mockets_reassembly_skipped_discarded_packets),
                                data.getString(getString(R.string.stats_mockets_reassembly_skipped_discarded_packets)));
                        _statistics.setProperty(getString(R.string.stats_mockets_estimated_round_trip_time),
                                data.getString(getString(R.string.stats_mockets_estimated_round_trip_time)));
                        _statistics.setProperty(getString(R.string.stats_mockets_unacknowledged_data_size),
                                data.getString(getString(R.string.stats_mockets_unacknowledged_data_size)));
                        _statistics.setProperty(getString(R.string.stats_mockets_pending_data_size),
                                data.getString(getString(R.string.stats_mockets_pending_data_size)));
                        _statistics.setProperty(getString(R.string.stats_mockets_pending_packet_queue_size),
                                data.getString(getString(R.string.stats_mockets_pending_packet_queue_size)));
                        _statistics.setProperty(getString(R.string.stats_mockets_reliable_sequenced_data_size),
                                data.getString(getString(R.string.stats_mockets_reliable_sequenced_data_size)));
                        _statistics.setProperty(getString(R.string.stats_mockets_reliable_sequenced_packet_queue_size),
                                data.getString(getString(R.string.stats_mockets_reliable_sequenced_packet_queue_size)));
                    }

                    break;

                case PROGRESS_STATS:
                    final int bytesSent = data.getInt(getString(R.string.stats_bytes_sent));
                    final int total = data.getInt(getString(R.string.stats_message_size));
                    _progressBar.post(new Runnable()
                    {
                        @Override
                        public void run ()
                        {
                            //updated progress bar
                            //_progressBar.setMax(total);
                            _progressBar.setProgress(bytesSent);
                            if (bytesSent >= total) {
                                unbindClientCommService();
                                _progressBar.setProgress(0);
                                Toast.makeText(MainActivity.this, "Sent " + Utils.humanReadableByteCount(bytesSent,
                                                true) + " successfully!",
                                        Toast.LENGTH_SHORT).show();
                            }

                        }
                    });
                    break;
            }

            if (!notification.equals(Notification.PROGRESS_STATS)) {
                _fromService.post(new Runnable()
                {
                    @Override
                    public void run ()
                    {
                        populateButtonStatistics(dumpStatistics().getAbsolutePath());
                    }
                });
            }
            //new UpdateUITask().execute();
        }
    };

    class UpdateUITask extends AsyncTask<Bundle, String, String>
    {
        @Override
        protected String doInBackground (Bundle... params)
        {
            publishProgress("OK");
            return "OK";
        }

        protected void onProgressUpdate (String... progress)
        {
            unbindClientCommService();
        }
    }


    @Override
    public boolean onCreateOptionsMenu (Menu menu)
    {
        // Configure the application's settings menu using the XML file res/menu/settings.xml.
        this.getMenuInflater().inflate(R.menu.settings, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.app_menu_preferences:
                Intent i = new Intent(this, SettingsActivity.class);
                startActivity(i);
                return super.onOptionsItemSelected(item);
            case R.id.app_menu_exit:
                exit();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Cleanly exits the app.
     */
    public void exit ()
    {
        LOG.info("== Quit " + getString(R.string.app_name) + " ==");
        if (_activityMessenger.isServiceBound(DiscoveryService.class)) {
            LOG.debug("Unbinding " + ClientCommService.class.getSimpleName());
            unbindService(_activityMessenger.getServiceConnection(DiscoveryService.class));
            _activityMessenger.destroyServiceConnection(DiscoveryService.class);
        }
        if (_activityMessenger.isServiceBound(ClientCommService.class)) {
            LOG.debug("Unbinding " + ClientCommService.class.getSimpleName());
            unbindService(_activityMessenger.getServiceConnection(ClientCommService.class));
            _activityMessenger.destroyServiceConnection(ClientCommService.class);
        }
        if (_activityMessenger.isServiceBound(ServerCommService.class)) {
            LOG.debug("Unbinding " + ClientCommService.class.getSimpleName());
            unbindService(_activityMessenger.getServiceConnection(ServerCommService.class));
            _activityMessenger.destroyServiceConnection(ServerCommService.class);
        }
        //explicitly call garbage collector
        System.gc();
        //kill process
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    @Override
    protected void onSaveInstanceState (Bundle savedInstanceState)
    {
        //save the app current status
        savedInstanceState.putParcelable(getString(R.string.discovery_local_peer), _localPeer);

        LOG.debug("Called onSavedInstanceState");
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
        LOG.debug("Called onRestoreInstanceState");

        _localPeer = savedInstanceState.getParcelable(getString(R.string.discovery_local_peer));

        LOG.info("Restored peer: " + _localPeer);
        LOG.debug("Discovery service bound? " + _activityMessenger.isServiceBound(DiscoveryService.class));

        if (!_activityMessenger.isServiceBound(DiscoveryService.class)) {
            LOG.debug("Re-binding PeerService");
            bindDiscoveryService();
        }
    }

    @Override
    public void onBackPressed ()
    {
        //super.onBackPressed();
        LOG.info("Back button pressed, showing dialog");
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(_context);

        // set title
        alertDialogBuilder.setTitle("Exit " + getString(R.string.app_name));

        // set dialog message
        alertDialogBuilder
                .setMessage("Are you sure you want to exit?")
                .setCancelable(false)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener()
                {
                    public void onClick (DialogInterface dialog, int id)
                    {
                        // if this button is clicked, close
                        // current activity
                        MainActivity.this.finish();
                        exit();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener()
                {
                    public void onClick (DialogInterface dialog, int id)
                    {
                        // if this button is clicked, just close
                        // the dialog box and do nothing
                        dialog.cancel();
                    }
                });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    @Override
    public void onDestroy ()
    {
        unregisterReceiver(_intentsReceiver);
    }

    private void registerIntentsReceiver ()
    {
        _intentsReceiver = new IntentsReceiver();
        LOG.info("Registering IntentReceiver");
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        //intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        //intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        //intentFilter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        registerReceiver(_intentsReceiver, filter);
    }

    class IntentsReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive (Context context, Intent intent)
        {
            final String action = intent.getAction();
            LOG.warn("+++ BroadcastReceiver - received action: " + action);

            if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                populateSpnNetInterface();
            }
        }
    }

    //views
    private ProgressBar _progressBar;

    private final Context _context = this;
    private final ActivityMessenger _activityMessenger = new ActivityMessenger(MainActivity.class);
    private IntentsReceiver _intentsReceiver;
    private Properties _config = null;
    private Properties _statistics = new Properties();
    private File _currentStatsFile = null;
    private Peer _localPeer = null;

    private final Map<String, Peer> _peers = new ConcurrentHashMap<String, Peer>();   //key 'nodeUUID-ipAddress' /val
    private final static Logger LOG = Logger.getLogger(MainActivity.class);
}
