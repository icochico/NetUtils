package us.ihmc.android.util.netutils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.format.Formatter;
import de.mindpipe.android.logging.log4j.LogConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utils.java
 * <p/>
 * Class <code>Utils</code> contains static utility methods for Android.
 *
 * @author Enrico Casini (ecasini@ihmc.us)
 */
public class Utils
{
    public static void configureLog4J (final Context context)
    {
        final LogConfigurator logConfigurator = new LogConfigurator();
        File logsDirPath = new File(getExternalStorageDirectory() + File.separator + context.getString(R.string
                .app_dir) + File.separator + context.getString(R.string.app_logs_dir));
        if (!logsDirPath.exists())
            logsDirPath.mkdir();
        logConfigurator.setFileName(logsDirPath.getAbsolutePath() + File.separator
                + context.getString(R.string.app_dir) + getTimeStampNow() + ".log");
        logConfigurator.setRootLevel(Level.DEBUG);
        // Set log level of a specific logger
        //logConfigurator.setLevel("org.apache", Level.ERROR);
        logConfigurator.configure();
    }

    public static String getTimeStampNow ()
    {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(Calendar.getInstance().getTime());
    }

    public static String getTimeStamp (long time)
    {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date(time));
    }

    /**
     * Get the last build timestamp of the app.
     *
     * @param context the current Android's <code>Context</code> object.
     * @return a <code>String</code> containing the last build timestamp of the app.
     */
    public static String getLastBuildTimeStamp (final Context context)
    {
        try {
            ApplicationInfo ai = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            ZipFile zf = new ZipFile(ai.sourceDir);
            ZipEntry ze = zf.getEntry("classes.dex");
            long time = ze.getTime();
            return SimpleDateFormat.getInstance().format(new java.util.Date(time));
        }
        catch (Exception e) {
            LOG.error("Unable to retrieve last build timestamp", e);
            return "Undefined";
        }
    }

    /**
     * Gets the configuration directory of the app on SD card, following a standard.
     *
     * @param context the Context of the app.
     * @return a <code>String</code> class representing the directory.
     */
    public static String getConfigDir (Context context)
    {
        return Utils.getExternalStorageDirectory() + File.separator +
                context.getString(R.string.app_dir) + File.separator +
                context.getString(R.string.app_conf_dir);
    }

    /**
     * Gets the configuration file of the app on SD card, following a standard.
     *
     * @param context the Context of the app.
     * @return a <code>String</code> class representing the file path.
     */
    public static String getConfigFile (Context context)
    {
        return Utils.getExternalStorageDirectory() + File.separator +
                context.getString(R.string.app_dir) + File.separator +
                context.getString(R.string.app_conf_dir) + File.separator +
                context.getString(R.string.app_conf_file);
    }

    public static boolean writeToFile (final String filePath, final byte[] data)
    {
        FileOutputStream fos = null;
        boolean success = true;
        try {
            fos = new FileOutputStream(String.format(filePath));
            fos.write(data);
            LOG.info("Data written on file: " + filePath);
        }
        catch (IOException e) {
            LOG.warn("Error while writing file to disk.");
            success = false;
        }
        finally {
            if (fos != null) {
                try {
                    fos.flush();
                    fos.close();
                    success = true;
                }
                catch (IOException e) {
                    LOG.warn("Error while closing file.");
                    success = false;
                }
            }
        }

        return success;
    }

    public static Properties readConfigFile (final String filePath) throws IOException
    {
        File configFile = new File(filePath);
        Properties configProperties = new Properties();
        FileInputStream fileInputStream = new FileInputStream(configFile);
        configProperties.load(fileInputStream);

        LOG.info("Configuration file read successfully");
        return configProperties;
    }

    public static boolean writeConfigFile (final Properties configProperties, final String filePath) throws IOException
    {
        FileOutputStream fileOutputStream = new FileOutputStream(filePath);
        configProperties.store(fileOutputStream, null);

        LOG.info("Configuration file written successfully");
        return true;
    }

    public static String getDeviceModel ()
    {
        LOG.info("Device model: " + Build.MODEL);

        return Build.MODEL;
    }

    public static String getDeviceManufacturer ()
    {
        LOG.info("Device manufacturer: " + Build.MANUFACTURER);

        return Build.MANUFACTURER;
    }

    public static String getExternalStorageDirectory ()
    {

        String sdCard;
        String devModel = getDeviceModel();

        if (devModel.equals("DROID X2")) {
            sdCard = "/sdcard-ext";
        }
        else if (devModel.equals("SGH-I987")) {
            sdCard = "/sdcard/external_sd";
        }
        else {
            try {
                sdCard = Environment.getExternalStorageDirectory().getAbsolutePath();
                if (sdCard.contains("emulated")) {
                    LOG.warn("Found emulated external storage, forcing /sdcard");
                    sdCard = "/sdcard";
                }
            }
            catch (Exception e) {
                sdCard = "/sdcard";
            }
        }

        LOG.info("SD card path is: " + sdCard);
        return sdCard;
    }

    public static String getActiveIPAddress (Context context)
    {
        String localIp = "127.0.0.1";
        //detect if WiFi or mobile
        if (isConnectedToWifi(context)) {
            LOG.info("Detected WiFi connection");
            localIp = getWiFiIPAddress(context);
        }
        else if (isConnectedToMobile(context)) {
            LOG.info("Detected cell mobile connection");
            List<Utils.NetInterface> interfaces = getNetInterfaces();
            if (interfaces.size() > 0) {
                localIp = interfaces.get(0).ip;
                LOG.info("Using cell mobile IP " + localIp);
            }
        }

        return localIp;
    }


    /**
     * Get the local WiFi interface IP Address (if any).
     *
     * @param context the current Android's <code>Context</code> object.
     * @return String representing the local WiFi IP Address
     */
    public static String getWiFiIPAddress (final Context context)
    {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiInfo wiFiInfo = wifiManager.getConnectionInfo();
        String wifiIPAddress = Formatter.formatIpAddress(wiFiInfo.getIpAddress());
        LOG.debug("Current WiFi interface IP address: " + wifiIPAddress);

        // old method
        //        try {
        //            DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
        //            // Get the IP Address
        //            wifiIPAddress = intToIp((dhcpInfo.ipAddress));
        //        }
        //        catch (Exception e) {
        //            LOG.error(e.toString());
        //        }

        return wifiIPAddress;
    }

    /**
     * Convenience method to determine if we are connected to a mobile network.
     *
     * @return true if connected to a mobile network, false otherwise.
     */
    public static boolean isConnectedToMobile (final Context context)
    {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
        return networkInfo.isConnected();
    }

    /**
     * Convenience method to determine if we are connected to wifi.
     *
     * @return true if connected to wifi, false otherwise.
     */
    public static boolean isConnectedToWifi (final Context context)
    {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return networkInfo.isConnected();
    }

    /**
     * Get all the currently available network interfaces in the format of
     * a <code>List</code> of <code>NetInterface</code> objects.
     *
     * @return a <code>List</code> of <code>NetInterface</code> objects.
     */
    public static List<NetInterface> getNetInterfaces ()
    {
        List<NetInterface> netInterfaceList = new ArrayList<NetInterface>();
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements(); ) {
                NetworkInterface networkInterface = en.nextElement();

                for (Enumeration<InetAddress> addresses = networkInterface.getInetAddresses(); addresses
                        .hasMoreElements(); ) {
                    InetAddress inetAddress = addresses.nextElement();
                    if (!inetAddress.isLoopbackAddress()) {

                        String ipAddress = inetAddress.getHostAddress();
                        if (ipAddress.contains(":")) continue; //exclude IPv6 addresses

                        String shortName = networkInterface.getName();
                        netInterfaceList.add(new NetInterface(shortName, ipAddress));
                    }
                }
            }
        }
        catch (SocketException e) {
            LOG.error("Exception while querying network interfaces", e);
        }

        return netInterfaceList;
    }

    /**
     * Method that validates a given IP address.
     *
     * @param ipAddress The IP Address input string
     * @return true if the IP is valid, false otherwise
     */
    public static boolean isIPAddress (String ipAddress)
    {
        String[] parts = ipAddress.split("\\.");

        if (parts.length != 4) {
            return false;
        }

        for (String s : parts) {
            try {
                int i = Integer.parseInt(s);
                if ((i < 0) || (i > 255)) {
                    return false;
                }
            }
            catch (NumberFormatException e) {
                LOG.error("Provided IP address is invalid", e);
                return false;
            }

        }

        return true;
    }

    /**
     * Method for converting an existing IP in Integer format to String format.
     *
     * @param integerIp The IP Address in Integer format
     * @return A String representation of the IP Address
     */
    public static String intToIp (int integerIp)
    {
        return (integerIp & 0xFF) + "." + ((integerIp >> 8) & 0xFF)
                + "." + ((integerIp >> 16) & 0xFF) + "." + ((integerIp >> 24) & 0xFF);
    }

    /**
     * Returns a human readable version of a byte count.
     *
     * @param bytes the bytes value
     * @param si    the format to use (SI = 1000, BI = 1024)
     * @return a <code>String</code> representing the byte count.
     */
    public static String humanReadableByteCount (long bytes, boolean si)
    {

        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }


    /**
     * Returns a byte count from a human readable version.
     *
     * @param humanReadableByteCount the human readable byte count
     * @param si    the format to use (SI = 1000, BI = 1024)
     * @return a <code>String</code> representing the byte count.
     */
    public static int byteCount (String humanReadableByteCount, boolean si)
    {
        int unit = si ? 1000 : 1024;
        String byteUnit = getUnitOfByteCount(humanReadableByteCount);
        String numeric = getNumericOfByteCount(humanReadableByteCount);
        if (byteUnit.equals("B")) {
            return Double.valueOf(Double.parseDouble(numeric)).intValue();
        }
        else if (byteUnit.equals("kB")) {
            return Double.valueOf(Double.parseDouble(numeric) * unit).intValue();
        }
        else if (byteUnit.equals("MB")) {
            return Double.valueOf(Double.parseDouble(numeric) * unit * unit).intValue();
        }

        return 0;
    }

    public static class NetInterface
    {
        public String name;
        public String ip;

        public NetInterface (String name, String ip)
        {
            this.name = name;
            this.ip = ip;
        }

        @Override
        public String toString ()
        {
            return this.name + " " + this.ip;
        }
    }

    public static String getNumericOfByteCount (String humanReadableByteCount)
    {
        return humanReadableByteCount.split(" ")[0];
    }

    public static String getUnitOfByteCount (String humanReadableByteCount)
    {
        return humanReadableByteCount.split(" ")[1];
    }

    public static int getUnitOfByteCountAsIndex (String humanReadableByteCount)
    {
        String byteUnit = getUnitOfByteCount(humanReadableByteCount);
        if (byteUnit.equals("B")) {
            return 0;
        }
        else if (byteUnit.equals("kB")) {
            return 1;
        }
        else {  //MB
            return 2;
        }
    }

    public static String getPreferenceFromMemory (Context context, String key, String defaultValue)
    {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPref.getString(key, defaultValue);
    }

    public static String getPreferenceFromDisk (Properties config, String key, String defaultValue)
    {
        //SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(_context);
        //return sharedPref.getString(key, defaultValue);

        return ((config.getProperty(key) != null) ? config.getProperty(key) : defaultValue);
    }

    public static boolean savePreference (Context context, Properties config, String key, String value)
    {
        //memory
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(context);
        sharedPref.edit().putString(key, value);
        sharedPref.edit().apply();

        //disk
        config.setProperty(key, value);

        try {
            Utils.writeConfigFile(config, new File(Utils.getConfigFile(context)).getAbsolutePath());
            LOG.info("Preferences saved on file " + Utils.getConfigFile(context));
            return true;
        }
        catch (IOException e) {
            LOG.error("Unable to write configuration file:" + Utils.getConfigFile(context), e);
            return false;
        }
    }

    private final static Logger LOG = Logger.getLogger(Utils.class);
}
