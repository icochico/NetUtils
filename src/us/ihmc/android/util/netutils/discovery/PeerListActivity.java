package us.ihmc.android.util.netutils.discovery;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import org.apache.log4j.Logger;
import us.ihmc.android.util.netutils.R;

import java.util.ArrayList;

/**
 * PeerListActivity.java
 * <p/>
 * Class <code>PeerListActivity</code> communicates with the discovery service in order to receive the latest updates
 * on the status of the peers that have been discovered.
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class PeerListActivity extends ListActivity
{
    @Override
    public void onCreate (Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.peer_list);

        // We need to use a different list item layout for devices older than Honeycomb
        int layout = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
                android.R.layout.simple_list_item_activated_1 : android.R.layout.simple_list_item_1;
        _peerListAdapter = new PeerAdapter(getApplicationContext(), layout, android.R.id.text1, new ArrayList<Peer>());

        Intent intent = getIntent();

        //with JSON strings
        ArrayList<String> peersStrList = intent.getStringArrayListExtra(getString(R.string.discovery_peer_list));
        ArrayList<Peer> peers = new ArrayList<Peer>();
        for (String peerStr : peersStrList) {
            Peer p = Peer.fromJSON(peerStr);
            peers.add(p);
        }

        //with parcelable
        //ArrayList<Peer> peers = intent.getParcelableArrayListExtra(getString(R.string.discovery_peer_list));
        _peerListAdapter.clear();
        _peerListAdapter.addAll(peers);
        _peerListAdapter.notifyDataSetChanged();


        //setting the adapter with
        setListAdapter(_peerListAdapter);
    }

    @Override
    public void onStart ()
    {
        super.onStart();
    }

    @Override
    public void onListItemClick (ListView l, View v, int position, long id)
    {

        Peer p = (Peer) getListView().getItemAtPosition(position);
        LOG.debug("Selected peer: " + p);

        //send back to main activity
        Intent toMain = new Intent();
        toMain.putExtra(getString(R.string.discovery_remote_peer), p);

        setResult(RESULT_OK, toMain);
        finish();
    }

    private ArrayAdapter<Peer> _peerListAdapter;
    private static final Logger LOG = Logger.getLogger(PeerListActivity.class);
}
