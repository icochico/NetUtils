package us.ihmc.android.util.netutils.stats;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import org.apache.log4j.Logger;
import us.ihmc.android.util.netutils.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * StringListActivity.java
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class StringListActivity extends Activity
{
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        Intent fromMain = getIntent();
        String statsFilePath = fromMain.getStringExtra(getString(R.string.stats_file_path));

        //read properties
        StringList stats = new StringList();
        try {
            stats.read(statsFilePath);
            LOG.info("Size of the current stats file: " + stats.size());
        }
        catch (IOException e) {
            String strError = "Unable to read statistics file";
            LOG.error(strError);
            stats.add(0, strError);

        }
        catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        //sort the properties
        stats.sort();

        ArrayAdapter logAdapter = new ArrayAdapter<String>(this, R.layout.stats, R.id.stats,
                new ArrayList<String>(stats));
        ListView lv = new ListView(this);
        lv.setAdapter(logAdapter);
        setContentView(lv);
    }

    @Override
    public void onDestroy()
    {
        //_stop.set(true);
    }


    //private final AtomicBoolean _stop = new AtomicBoolean(false);
    private final static Logger LOG = Logger.getLogger(StringListActivity.class);
}
