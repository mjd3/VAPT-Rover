package com.virtualapt.rover.cheburashka;

import android.content.Context;
import android.content.SharedPreferences;

/*
**  Handles App settings and preferences
*/
public class AppPreferences {

    private static final String TAG = "PTP_Pref";
    
    public static final String PREF_NAME = Constants.PACKAGE_NAME;

    private SharedPreferences mPref;

   /**
     * Get the value of a key
     * @param key
     * @return
     */
    public String getString(String key) {
        return mPref.getString(key, null);
    }

    /**
     * Set the value of a key
     * @param key
     * @return
     */
    public void setString(String key, String value) {
        SharedPreferences.Editor editor = mPref.edit();
        editor.putString(key, value);
        editor.commit();
    }
    /**
     * Get the value of a key from a different preference in the same application
     * adb shell cat /data/data/com.motorola.contextual.smartrules/shared_prefs/com.motorola.contextual.virtualsensor.locationsensor.xml
	 *
	 * <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
	 * <map>
	 * <string name="poi">Location:1331910178767</string>
	 * <string name="background_scan">1</string>
	 * 
	 * @param preferenceFileName, the file name under shared_prefs dir
	 * @param key the key
	 * </map>
     */
    public static String getStringFromPref(Context ctx, String preferenceFileName, String key) {
    	String value = null;
    	SharedPreferences pref = ctx.getSharedPreferences(preferenceFileName, 0);
    	if( pref != null){
    		value = pref.getString(key, null);
    	}
        return value;
    }
    
    public static void setStringToPref(Context ctx, String preferenceFileName, String key, String value) {
    	SharedPreferences pref = ctx.getSharedPreferences(preferenceFileName, 0);
    	if( pref != null){
    		SharedPreferences.Editor editor = pref.edit();
            editor.putString(key, value);
            editor.commit();
    	}
    }
    
    /**
     * Get the value of a key from a different preference in the same application
     * adb shell cat /data/data/com.motorola.contextual.smartrules/shared_prefs/com.motorola.contextual.virtualsensor.locationsensor.xml
	 *
	 * <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
	 * <map>
	 * <string name="poi">Location:1331910178767</string>
	 * <string name="background_scan">1</string>
	 * </map>
	 * 
	 * @param preferenceFileName, the file name under shared_prefs dir
	 * @param key the key
	 
     */
    public static boolean getBooleanFromPref(Context ctx, String preferenceFileName, String key) {
    	boolean value = false;
    	SharedPreferences pref = ctx.getSharedPreferences(preferenceFileName, 0);
    	if( pref != null){
    		value = pref.getBoolean(key, false);
    	}
        return value;
    }
}
