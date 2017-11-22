package us.ihmc.android.util.netutils.discovery;

import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import org.apache.log4j.Logger;

import java.util.List;

/**
 * PeerAdapter.java
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class PeerAdapter extends ArrayAdapter<Peer>
{
    public PeerAdapter (Context context, int resource, int textViewResourceId, List<Peer> objects)
    {
        super(context, resource, textViewResourceId, objects);
        mPeers = objects;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public View getView (int position, View convertView, ViewGroup parent)
    {
        // We need to use a different list item layout for devices older than Honeycomb
        int layout = Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ?
                android.R.layout.simple_list_item_activated_1 : android.R.layout.simple_list_item_1;
        View view = mInflater.inflate(layout, null, false);
        TextView text = (TextView) view.findViewById(android.R.id.text1);
        text.setText(mPeers.get(position).toSpannable());
        return view;
    }

    List<Peer> mPeers;
    LayoutInflater mInflater;
}
