package com.radiusnetworks.ibeacon.service;

import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DatabaseHandler extends SQLiteOpenHelper {
	 
    // All Static variables
    // Database Version
    private static final int DATABASE_VERSION = 1;
 
    // Database Name
    private static final String DATABASE_NAME = "beaconstoreManager";
 
    // beaconstore table name
    private static final String TABLE_BEACONSTORE = "beaconstore";
 
    // beaconstore Table Columns names
    private static final String KEY_ID = "id";
    private static final String KEY_UUID = "uuid";
    private static final String KEY_MAJOR = "major";
    private static final String KEY_MINOR = "minor";
    private static final String KEY_UPDATED_DATE = "updated_date";
    private static final String KEY_MERCHANT_NAME = "merchant_name";
    private static final String KEY_LOCATION_ID = "location_id";
    private static final String KEY_URL = "url";
 
    public DatabaseHandler(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
 
    // Creating Tables
    @Override
    public void onCreate(SQLiteDatabase db) {
        String CREATE_BEACONSTORE_TABLE = "CREATE TABLE " + TABLE_BEACONSTORE + "("
                + KEY_ID + " INTEGER PRIMARY KEY," 
        		+ KEY_UUID + " TEXT,"
                + KEY_MAJOR + " INT,"
                + KEY_MINOR + " INT,"
        		+ KEY_UPDATED_DATE + " TEXT,"
        		+ KEY_MERCHANT_NAME + " TEXT,"
        		+ KEY_LOCATION_ID + " TEXT,"
        		+ KEY_URL + " TEXT" + ")";
        db.execSQL(CREATE_BEACONSTORE_TABLE);
    }
 
    // Upgrading database
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Drop older table if existed
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BEACONSTORE);
 
        // Create tables again
        onCreate(db);
    }
    
 // Adding new beacon
    public void addBeacon(BeaconStore beacon) {

        SQLiteDatabase db = this.getWritableDatabase();
     
        ContentValues values = new ContentValues();
        values.put(KEY_UUID, beacon.getUuid()); // Beacon UUID
        values.put(KEY_MAJOR, beacon.getMajor()); // Beacon Major
        values.put(KEY_MINOR, beacon.getMinor()); // Beacon Minor
        values.put(KEY_UPDATED_DATE, beacon.getUpdatedDate()); // Beacon Record Updated Date
        values.put(KEY_MERCHANT_NAME, beacon.getMerchantName()); // Beacon Merchant Name
        values.put(KEY_LOCATION_ID, beacon.getLocationId()); // Beacon Location ID
        values.put(KEY_URL, beacon.getUrl()); // Beacon URL
     
        // Inserting Row
        db.insert(TABLE_BEACONSTORE, null, values);
        db.close(); // Closing database connection
    }
     
    // Getting single beacon
    public BeaconStore getBeacon(int id) {

        SQLiteDatabase db = this.getReadableDatabase();
     
        Cursor cursor = db.query(TABLE_BEACONSTORE, new String[] { KEY_ID,
        		KEY_UUID, KEY_MAJOR, KEY_MINOR, KEY_UPDATED_DATE, KEY_MERCHANT_NAME, KEY_LOCATION_ID, KEY_URL }, KEY_ID + "=?",
                new String[] { String.valueOf(id) }, null, null, null, null);
        if (cursor != null)
            cursor.moveToFirst();
     
        BeaconStore beacon = new BeaconStore(Integer.parseInt(cursor.getString(0)),
                cursor.getString(1), cursor.getInt(2), cursor.getInt(3), cursor.getString(4), cursor.getString(5), cursor.getInt(6), cursor.getString(7));
    	return beacon;
    }
    
   // Getting single beacon
   public BeaconStore getBeacon(String uuid, int major, int minor) {

       SQLiteDatabase db = this.getReadableDatabase();
    //Log.e("db getbeacon:",String.valueOf(uuid) + "/" + String.valueOf(major) + "/" + String.valueOf(minor));
       //Cursor cursor = db.query(TABLE_BEACONSTORE, new String[] { KEY_ID, KEY_UUID, KEY_MAJOR, KEY_MINOR }, KEY_UUID + "=? AND " + KEY_MAJOR + "=? AND " + KEY_MINOR + "=?",
       //        new String[] { uuid, String.valueOf(major), String.valueOf(minor) }, null, null, null, null);
              
    String sql = "SELECT * FROM " + TABLE_BEACONSTORE + " WHERE " + KEY_UUID + " = '" + uuid + "' AND " + KEY_MAJOR + " = '" + String.valueOf(major) + "' AND " + KEY_MINOR + " = '" + String.valueOf(minor) + "'";
    //Log.e("db query", sql);
       Cursor cursor = db.rawQuery(sql, null);
       BeaconStore beacon;
       if(cursor.getCount() > 0) {
           cursor.moveToFirst();
           beacon = new BeaconStore(Integer.parseInt(cursor.getString(0)), cursor.getString(1), cursor.getInt(2), cursor.getInt(3), cursor.getString(4), cursor.getString(5), cursor.getInt(6), cursor.getString(7));
       } else {
    	   beacon = null;
       }
   	return beacon;
   }
     
    // Getting All Beacon
    public List<BeaconStore> getAllBeacons() {
    	
        List<BeaconStore> beacons = new ArrayList<BeaconStore>();
        // Select All Query
        String selectQuery = "SELECT  * FROM " + TABLE_BEACONSTORE;
     
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
     
        // looping through all rows and adding to list
        if (cursor.moveToFirst()) {
            do {
            	BeaconStore beacon = new BeaconStore();
            	beacon.setID(Integer.parseInt(cursor.getString(0)));
            	beacon.setUuid(cursor.getString(1));
            	beacon.setMajor(cursor.getInt(2));
            	beacon.setMinor(cursor.getInt(3));
            	beacon.setUpdatedDate(cursor.getString(4));
            	beacon.setMerchantName(cursor.getString(5));
            	beacon.setLocationId(cursor.getInt(6));
            	beacon.setUrl(cursor.getString(7));
                // Adding contact to list
                beacons.add(beacon);
            } while (cursor.moveToNext());
        }
     
    	return beacons;
    }
     
    // Getting beacons Count
    public int getBeaconsCount() {
    	
        String countQuery = "SELECT  * FROM " + TABLE_BEACONSTORE;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(countQuery, null);
        cursor.close();
 
        // return count
        return cursor.getCount();
    }
    
    // Updating single beacon
    public int updateBeacon(BeaconStore beacon) {
    	
        SQLiteDatabase db = this.getWritableDatabase();
        
        ContentValues values = new ContentValues();
        values.put(KEY_UUID, beacon.getUuid()); // Beacon UUID
        values.put(KEY_MAJOR, beacon.getMajor()); // Beacon Major
        values.put(KEY_MINOR, beacon.getMajor()); // Beacon Minor
        values.put(KEY_UPDATED_DATE, beacon.getUpdatedDate()); // Beacon Record Updated Date
        values.put(KEY_MERCHANT_NAME, beacon.getMerchantName()); // Beacon Merchant Name
        values.put(KEY_LOCATION_ID, beacon.getLocationId()); // Beacon Location ID
        values.put(KEY_URL, beacon.getUrl()); // Beacon URL
     
        // updating row
        return db.update(TABLE_BEACONSTORE, values, KEY_ID + " = ?",
                new String[] { String.valueOf(beacon.getID()) });
    }
     
    // Deleting single beacon
    public void deleteBeacon(BeaconStore beacon) {

        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_BEACONSTORE, KEY_ID + " = ?",
                new String[] { String.valueOf(beacon.getID()) });
        db.close();
    }

}