package us.ihmc.android.util.netutils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import org.apache.log4j.Logger;
import us.ihmc.netutils.Client;
import us.ihmc.netutils.Mode;

/**
 * OptionsActivity.java
 * <p/>
 * Class <code>OptionsActivity</code> handles fundamental connection options like message size, interval etc.
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class OptionsActivity extends Activity
{
    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.options);

        Intent intent = getIntent();
        Mode mode = Mode.valueOf(intent.getStringExtra(getString(R.string.netutils_mode)));
        String msgSize = intent.getStringExtra(getString(R.string.netutils_msg_size));
        long lMsgSize = Long.parseLong(msgSize);
        String hrMsgSize = Utils.humanReadableByteCount(lMsgSize, true);
        populateSeekBarMessageSize(mode, lMsgSize);
        populateETMessageSize(Utils.getNumericOfByteCount(hrMsgSize));
        populateSpnByteUnit(Utils.getUnitOfByteCountAsIndex(hrMsgSize));
        //populate based on current mode
        populateSpnInterval(mode.equals(Mode.Interval));
        populateETInterval(getString(R.string.netutils_interval_default), mode.equals(Mode.Interval));
        populateTVInterval(mode.equals(Mode.Interval));
        populateButtonSetOptions();
    }

    private Spinner populateSpnByteUnit (int byteUnitIndex)
    {
        final Spinner spnByteUnit = (Spinner) findViewById(R.id.spnByteUnit);
        final ArrayAdapter<CharSequence> byteUnitAdapter = ArrayAdapter.createFromResource(this,
                R.array.bytes_array, android.R.layout.simple_spinner_item);
        byteUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        spnByteUnit.setAdapter(byteUnitAdapter);
        spnByteUnit.setSelection(byteUnitIndex);
        return spnByteUnit;

    }

    private TextView populateTVInterval (boolean isVisible)
    {
        final TextView tvInterval = (TextView) findViewById(R.id.tvInterval);
        tvInterval.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
        return tvInterval;
    }

    private Button populateButtonSetOptions ()
    {
        final Button btSetOptions = (Button) findViewById(R.id.btSetOptions);
        btSetOptions.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v)
            {
                try {

                    String numericByteCount = ((EditText) (findViewById(R.id.etMessageSize))).getText()
                            .toString();
                    final Spinner spnByteUnit = (Spinner) findViewById(R.id.spnByteUnit);
                    String byteUnit = spnByteUnit.getSelectedItem().toString();
                    String humanReadableByteCount = numericByteCount + " " + byteUnit;
                    LOG.info("Detected human readable byte count is " + humanReadableByteCount);

                    int messageSize = Utils.byteCount(humanReadableByteCount, true);
                    int interval = Integer.parseInt(((EditText) (findViewById(R.id.etInterval))).getText()
                            .toString());

                    //send back to main activity
                    Intent toMain = new Intent();
                    toMain.putExtra(getString(R.string.netutils_msg_size), messageSize);
                    LOG.info("Set messageSize (bytes) to: " + messageSize);
                    toMain.putExtra(getString(R.string.netutils_interval), interval);

                    setResult(RESULT_OK, toMain);
                    finish();
                }
                catch (NumberFormatException e) {
                    LOG.error("Error while trying to parse int from message size or interval", e);
                    Toast.makeText(OptionsActivity.this, "Message size or interval not in the correct format.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        return btSetOptions;
    }

    private Spinner populateSpnInterval (final boolean isVisible)
    {
        final Spinner spnInterval = (Spinner) findViewById(R.id.spnInterval);
        spnInterval.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);

        final ArrayAdapter<CharSequence> intervalAdapter = ArrayAdapter.createFromResource(this,
                R.array.intervals_array, android.R.layout.simple_spinner_item);
        intervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        spnInterval.setAdapter(intervalAdapter);
        spnInterval.setSelection(4); //1000 msec
        spnInterval.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener()
        {
            @Override
            public void onItemSelected (AdapterView<?> parent, View view, int position, long id)
            {
                String intervalLabel = spnInterval.getSelectedItem().toString();
                LOG.debug("Called onItemSelected, selected interval: " + intervalLabel);
                String interval = intervalLabel.split(" ")[0];

                int intervalInt = Integer.valueOf(interval);
                String value = String.valueOf((intervalInt == 1 || intervalInt == 2 || intervalInt == 5) ?
                        (intervalInt * 1000) : intervalInt);

                populateETInterval(value, isVisible);
            }

            @Override
            public void onNothingSelected (AdapterView<?> parent)
            {
                //spinSelect.setSelection(iFaceList.size() - 1);
            }
        });

        return spnInterval;
    }

    private EditText populateETInterval (String value, boolean isVisible)
    {
        final EditText etInterval = (EditText) findViewById(R.id.etInterval);
        etInterval.setVisibility(isVisible ? View.VISIBLE : View.INVISIBLE);
        etInterval.setText(value);
        return etInterval;
    }

    private EditText populateETMessageSize (String MsgSizeHR)
    {
        final EditText etMessageSize = (EditText) findViewById(R.id.etMessageSize);
        etMessageSize.setText(MsgSizeHR);
        return etMessageSize;
    }

    private SeekBar populateSeekBarMessageSize (Mode mode, long msgSize)
    {
        final SeekBar sbMessageSize = (SeekBar) findViewById(R.id.sbMessageSize);
        sbMessageSize.setMax((mode.equals(Mode.Stream)) ? Client.MAX_MSG_SIZE_STREAM : Client.MAX_MSG_SIZE_INTERVAL);
        sbMessageSize.setProgress((int) msgSize);
        sbMessageSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener()
        {
            @Override
            public void onProgressChanged (SeekBar seekBar, int progress, boolean fromUser)
            {
                String hrByteCount = Utils.humanReadableByteCount(progress, true);
                populateETMessageSize(Utils.getNumericOfByteCount(hrByteCount));
                final Spinner spnByteUnit = (Spinner) findViewById(R.id.spnByteUnit);
                spnByteUnit.setSelection(Utils.getUnitOfByteCountAsIndex(hrByteCount), true);
            }

            @Override
            public void onStartTrackingTouch (SeekBar seekBar)
            {

            }

            @Override
            public void onStopTrackingTouch (SeekBar seekBar)
            {

            }
        });

        return sbMessageSize;
    }

    @Override
    public void onStart ()
    {
        super.onStart();
    }

    private final static Logger LOG = Logger.getLogger(OptionsActivity.class);
}
