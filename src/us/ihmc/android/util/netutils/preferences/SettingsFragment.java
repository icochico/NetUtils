package us.ihmc.android.util.netutils.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import org.apache.log4j.Logger;
import us.ihmc.android.util.netutils.Utils;
import us.ihmc.android.util.netutils.R;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

/**
 * SettingsFragment.java
 * <p/>
 * Class <code>SettingsFragment</code> extends an Android Fragment and handles the configurations settings of the app.
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class SettingsFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener
{
    @Override
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }

    @Override
    public void onPause ()
    {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        savePreferences();
    }

    @Override
    public void onResume ()
    {
        LOG.info("Calling onResume");
        super.onResume();
        loadPreferenceSummaries();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }


    @Override
    public void onSharedPreferenceChanged (SharedPreferences sharedPreferences, String key)
    {
        if (_config == null) {
            LOG.error("GroupManager config file is null, unable to save changes");
            return;
        }

        Preference pref = findPreference(key);
        String summary = sharedPreferences.getString(key, "");

        if (key.equals(getString(R.string.netutils_udp_multicast_enabled))) {

            Boolean bValue = sharedPreferences.getBoolean(key, false);
            summary = String.valueOf(bValue);
        }
        else if (key.equals(getString(R.string.aci_groupmanager_netIFs))) {
            //translate value to summary
            ListPreference listPref = (ListPreference) findPreference(key);

            String localIp = Utils.getActiveIPAddress(getActivity());
            summary = localIp + "/" + listPref.getEntry().toString();
        }

        // Set summary to be the user-description for the selected value
        pref.setSummary(summary);

        LOG.info("Changed " + key + " to value " + summary);
        _config.setProperty(key, summary);
        savePreferences();
    }

    private void loadPreferenceSummaries ()
    {

        //get configuration file path
        File configFile = new File(Utils.getConfigFile(getActivity()));

        try {
            _config = Utils.readConfigFile(configFile.getAbsolutePath());
        }
        catch (IOException e) {
            LOG.error("Unable to load configuration file:" + configFile.getAbsolutePath());
        }

        //transport
        findPreference(getString(R.string.netutils_tcp_port)).setSummary(_config.getProperty(getString(R
                .string.netutils_tcp_port)));
        findPreference(getString(R.string.netutils_tcp_control_port)).setSummary(_config.getProperty(getString(R
                .string.netutils_tcp_control_port)));
        findPreference(getString(R.string.netutils_mockets_port)).setSummary(_config.getProperty(getString(R
                .string.netutils_mockets_port)));
        findPreference(getString(R.string.netutils_mockets_stream_port)).setSummary(_config.getProperty(getString(R
                .string.netutils_mockets_stream_port)));
        findPreference(getString(R.string.netutils_udp_port)).setSummary(_config.getProperty(getString(R
                .string.netutils_udp_port)));

        //discovery
        findPreference(getString(R.string.aci_groupmanager_port)).setSummary(_config.getProperty(getString(R
                .string.aci_groupmanager_port)));
        findPreference(getString(R.string.aci_groupmanager_netIFs)).setSummary(_config.getProperty(getString(R
                .string.aci_groupmanager_netIFs)));
        findPreference(getString(R.string.aci_groupmanager_ping_hopcount)).setSummary(_config.getProperty
                (getString(R.string.aci_groupmanager_ping_hopcount)));
        findPreference(getString(R.string.aci_groupmanager_ping_interval)).setSummary(_config.getProperty
                (getString(R.string.aci_groupmanager_ping_interval)));
        findPreference(getString(R.string.aci_groupmanager_info_interval)).setSummary(_config.getProperty
                (getString(R.string.aci_groupmanager_info_interval)));

    }

    private void savePreferences ()
    {
        if (_config == null) return;

        try {
            Utils.writeConfigFile(_config, Utils.getConfigFile(getActivity()));
            LOG.info("Preferences saved on file " + Utils.getConfigFile(getActivity()));
        }
        catch (IOException e) {
            LOG.error("Unable to write configuration file:" + Utils.getConfigFile(getActivity()));
        }
    }

    private Properties _config;
    private final static Logger LOG = Logger.getLogger(SettingsFragment.class);
}
