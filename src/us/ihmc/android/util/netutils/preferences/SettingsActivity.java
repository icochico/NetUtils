package us.ihmc.android.util.netutils.preferences;

import android.app.Activity;
import android.os.Bundle;
import org.apache.log4j.Logger;

/**
 * SettingsActivity.java
 * <p/>
 * Class <code>SettingsActivity</code> extends an Android Activity and handles the configurations settings of the app.
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class SettingsActivity extends Activity
{
    private final static Logger LOG = Logger.getLogger(SettingsActivity.class);

    @Override
    protected void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Display the fragment as the main content.
        getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
    }

    @Override
    public void onPause ()
    {
        //changedStatus("onPause()");
        super.onResume();
    }


    @Override
    public void onResume ()
    {
        //changedStatus("onResume()");
        super.onResume();
    }

    @Override
    public void onDestroy ()
    {
        //changedStatus("onDestroy()");
        super.onDestroy();
    }

    public void changedStatus (String statusChanged)
    {
        //log the status change
        LOG.info("Called " + statusChanged);
    }
}
