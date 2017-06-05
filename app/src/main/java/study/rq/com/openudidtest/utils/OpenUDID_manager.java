package study.rq.com.openudidtest.utils;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.Settings.Secure;
import android.util.Log;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;


public class OpenUDID_manager implements ServiceConnection {
    public final static String PREF_KEY = "openudid";
    public final static String PREFS_NAME = "openudid_prefs";
    public final static String TAG = "OpenUDID";

    private final Context mContext; //Application context
    private List<ResolveInfo> mMatchingIntents; //List of available OpenUDID Intents
    private Map<String, Integer> mReceivedOpenUDIDs; //Map of OpenUDIDs found so far

    private final SharedPreferences mPreferences; //Preferences to store the OpenUDID
    private final Random mRandom;

    private OpenUDID_manager(Context context) {
        mPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        mContext = context;
        mRandom = new Random();
        mReceivedOpenUDIDs = new HashMap<>();
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder service) {
        //Get the OpenUDID from the remote service
        android.os.Parcel data = null;
        android.os.Parcel reply = null;

        try {
            //Send a random number to the service
            int randomToken = mRandom.nextInt();
            data = android.os.Parcel.obtain();
            data.writeInt(randomToken);

            reply = android.os.Parcel.obtain();
            service.transact(1, data, reply, 0);

            //Check if the service returns us this number
            if (randomToken == reply.readInt()) {
                final String _openUDID = reply.readString();
                //if valid OpenUDID, save it
                if (isUDID(_openUDID)) {
                    if (mReceivedOpenUDIDs.containsKey(_openUDID)) {
                        mReceivedOpenUDIDs.put(_openUDID, mReceivedOpenUDIDs.get(_openUDID) + 1);
                    } else {
                        mReceivedOpenUDIDs.put(_openUDID, 1);
                    }
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        } finally {
            if (data != null) {
                data.recycle();
            }
            if (reply != null) {
                reply.recycle();
            }
            mContext.unbindService(this);
            //Try the next one
            startService();
        }
    }

    /**
     * FIX ISSUE: avoid UDID falsification.
     *
     * @see #generateOpenUDID()
     */
    public static boolean isUDID(String src) {
        return !(src == null || src.length() < 15);
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
    }

    private void storeOpenUDID() {
        final Editor e = mPreferences.edit();
        e.putString(PREF_KEY, OpenUDID);
        e.commit();
    }

    /*
     * Generate a new OpenUDID
     */
    private void generateOpenUDID() {
        OpenUDID = Secure.getString(mContext.getContentResolver(), Secure.ANDROID_ID);
        //android2.2或者是某些山寨手机使用，会返回一个固定的值 9774d56d682e549c
        if (OpenUDID == null || OpenUDID.equals("9774d56d682e549c") || OpenUDID.length() < 15) {
            //if ANDROID_ID is null, or it's equals to the GalaxyTab generic ANDROID_ID or bad, generates a new one
            final SecureRandom random = new SecureRandom();
            OpenUDID = new BigInteger(64, random).toString(16);
        }
    }

    /*
     * Start the oldest service
     */
    private void startService() {
        if (mMatchingIntents.size() > 0) { //There are some Intents untested
            final ServiceInfo servInfo = mMatchingIntents.get(0).serviceInfo;
            final Intent i = new Intent();
            i.setComponent(new ComponentName(servInfo.applicationInfo.packageName, servInfo.name));
            mMatchingIntents.remove(0);
            // try added by Lionscribe
            try {
                // FIX ISSUE: if bindService(...) return false
                if (!mContext.bindService(i, this, Context.BIND_AUTO_CREATE)) {
                    // if bindService(...) return false, start next one
                    startService();
                }
            } catch (SecurityException e) {
                // ignore this one, and start next one
                startService();
            }
        } else {
            //No more service to test, Choose the most frequent
            getMostFrequentOpenUDID();
            if (!isUDID(OpenUDID)) {
                generateOpenUDID();
            }
            storeOpenUDID();
            mInitialized = true;
        }
    }

    private void getMostFrequentOpenUDID() {
        if (!mReceivedOpenUDIDs.isEmpty()) {
            final TreeMap<String, Integer> sorted_OpenUDIDS = new TreeMap<>(new ValueComparator());
            sorted_OpenUDIDS.putAll(mReceivedOpenUDIDs);
            OpenUDID = sorted_OpenUDIDS.firstKey();
        }
    }


    private static String OpenUDID = null;
    private static boolean mInitialized = false;

    /**
     * The Method to call to get OpenUDID
     *
     * @return the OpenUDID
     */
    public static String getOpenUDID() {
        if (!mInitialized) {
            Log.e("OpenUDID", "Initialisation isn't done");
        }
        return OpenUDID;
    }

    /**
     * The Method to call to get OpenUDID
     *
     * @return the OpenUDID
     */
    public static boolean isInitialized() {
        return mInitialized;
    }

    /**
     * The Method the call at the init of your app
     *
     * @param context you current context
     */
    public static void sync(Context context) {
        //Initialise the Manager
        OpenUDID_manager manager = new OpenUDID_manager(context);
        //Try to get the openudid from local preferences
        OpenUDID = manager.mPreferences.getString(PREF_KEY, null);
        if (!isUDID(OpenUDID)) {
            //Get the list of all OpenUDID services available (including itself)
            manager.mMatchingIntents = context.getPackageManager().queryIntentServices(new Intent("org.OpenUDID.GETUDID"), 0);
            if (manager.mMatchingIntents != null)
                //Start services one by one
                manager.startService();

        } else {
            mInitialized = true;
        }
    }

    /*
     * Used to sort the OpenUDIDs collected by occurrence
     */
    private class ValueComparator implements Comparator {
        public int compare(Object a, Object b) {
            if (mReceivedOpenUDIDs.get(a) < mReceivedOpenUDIDs.get(b)) {
                return 1;
            } else if (mReceivedOpenUDIDs.get(a) == mReceivedOpenUDIDs.get(b)) {
                return 0;
            } else {
                return -1;
            }
        }
    }
}