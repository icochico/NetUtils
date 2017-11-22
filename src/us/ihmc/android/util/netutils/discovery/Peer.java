package us.ihmc.android.util.netutils.discovery;

import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


/**
 * Peer.java
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class Peer implements Parcelable
{

    public Peer (final String name, final String ipAddress)
    {
        this.id = idGenerator(name, ipAddress);
        this.name = name;
        this.ipAddress = ipAddress;
        this.isReachable = false;
    }

    /**
     * USE ONLY FOR SERIALIZATION
     */
    public Peer ()
    {

    }

    //id must be in the form "name-ipaddress"
    public Peer (final String id)
    {
        this.id = id;
        this.name = getNameFromId(id);
        this.ipAddress = getIpAddressFromId(id);
        this.isReachable = false;
    }

    private Peer (Parcel in)
    {
        id = in.readString();
        name = in.readString();
        ipAddress = in.readString();
        isReachable = (Boolean) in.readValue(Boolean.class.getClassLoader());
    }

    @Override
    public int hashCode ()
    {
        return new HashCodeBuilder(17, 31). //two randomly chosen prime numbers
                append(id).
                append(name).
                append(ipAddress).
                hashCode();
    }

    @Override
    public boolean equals (final Object o)
    {
        if (!(o instanceof Peer))
            return false;

        Peer p = (Peer) o;
        return new EqualsBuilder().
                append(id, p.id).
                append(name, p.name).
                append(ipAddress, p.ipAddress).
                isEquals();
    }

    /**
     * This is used to regenerate your object. All Parcelables must have a CREATOR that implements these two methods
     */
    public static final Creator<Peer> CREATOR = new Creator<Peer>()
    {
        public Peer createFromParcel (Parcel in)
        {
            return new Peer(in);
        }

        public Peer[] newArray (int size)
        {
            return new Peer[size];
        }
    };

    public static Peer fromJSON (String peerJSON) throws JsonSyntaxException
    {
        Gson gson = new Gson();
        return gson.fromJson(peerJSON, Peer.class);
    }

    public String toJSON ()
    {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public String getId ()
    {
        return id;
    }

    public String getName ()
    {
        return name;
    }

    public CharSequence getNameSpannable ()
    {
        Spannable span = SpannableStringBuilder.valueOf(name);
        if (isReachable()) {
            span.setSpan(new ForegroundColorSpan(Color.GREEN), 0, name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        else {
            span.setSpan(new ForegroundColorSpan(Color.RED), 0, name.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    public void setIpAddress (String ipAddress)
    {
        this.ipAddress = ipAddress;
        this.id = idGenerator(name, ipAddress);
    }

    public String getIpAddress ()
    {
        return ipAddress;
    }

    public boolean isReachable ()
    {
        return isReachable;
    }

    void setReachable (final boolean reachable)
    {
        isReachable = reachable;
    }

    @Override
    public String toString ()
    {
        return name + " [" + ipAddress + "] " + (isReachable() ? reachable : unreachable);
    }

    public Spannable toSpannable ()
    {
        String current = this.toString();
        Spannable span = SpannableStringBuilder.valueOf(current);
        int i, j;
        if (isReachable()) {
            i = current.indexOf(name);
            span.setSpan(new ForegroundColorSpan(Color.GREEN), i, i + name.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            j = current.indexOf(reachable);
            span.setSpan(new ForegroundColorSpan(Color.GREEN), j, j + reachable.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        else {
            i = current.indexOf(name);
            span.setSpan(new ForegroundColorSpan(Color.RED), i, i + name.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            j = current.indexOf(unreachable);
            span.setSpan(new ForegroundColorSpan(Color.RED), j, j + unreachable.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    public String toShortString ()
    {
        return name + " [" + ipAddress + "]";
    }

    public Spannable toShortSpannable ()
    {
        String current = this.toShortString();
        Spannable span = SpannableStringBuilder.valueOf(current);
        int i;
        if (isReachable()) {
            i = current.indexOf(name);
            span.setSpan(new ForegroundColorSpan(Color.GREEN), i, i + name.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        else {
            i = current.indexOf(name);
            span.setSpan(new ForegroundColorSpan(Color.RED), i, i + name.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return span;

    }

    public static String idGenerator (final String name, final String ipAddress)
    {
        if (name == null || ipAddress == null)
            throw new IllegalArgumentException("Name and ipAddress can't be null.");

        return name.replaceAll("\\s","") + idSeparator + ipAddress.replaceAll("\\s","");
    }

    private static String[] separator (String id)
    {
        if (id == null)
            throw new IllegalArgumentException("Id can't be null.");

        String parts[] = id.split(idSeparator);

        if (parts.length != idParts)
            throw new IllegalArgumentException("Id must be in the form name" + idSeparator + "ipAddress");

        return parts;
    }

    public static String getIdFromText (String text)
    {
        if (text == null)
            throw new IllegalArgumentException("Text can't be null");

        String parts[] = text.split(" ");
        String name = parts[0].trim();
        String ipAddr = parts[1].substring(1, parts[1].length() - 2);

        return idGenerator(name, ipAddr);
    }

    public static String getNameFromId (final String id)
    {
        return separator(id)[0];
    }

    public static String getIpAddressFromId (final String id)
    {
        return separator(id)[1];
    }

    @Override
    public int describeContents ()
    {
        return 0;
    }

    @Override
    public void writeToParcel (Parcel dest, int flags)
    {
        dest.writeString(id);
        dest.writeString(name);
        dest.writeString(ipAddress);
        dest.writeValue(isReachable);
    }

    private String id;
    private String name;
    private String ipAddress;
    private boolean isReachable;
    public final static String idSeparator = "@";
    public final static String reachable = "(strong)";
    public final static String unreachable = "(weak)";
    public final static int idParts = 2;
}
