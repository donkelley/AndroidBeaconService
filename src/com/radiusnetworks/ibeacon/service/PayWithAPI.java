package com.radiusnetworks.ibeacon.service;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.util.Log;

public class PayWithAPI {
	
	protected String action = null;
	private Context context;
	private static final String PREFERENCES = "com.paywith.paywith";
	//private SharedPreferences settings  = PreferenceManager.getDefaultSharedPreferences(context);
	private SharedPreferences settings;
	private SharedPreferences.Editor editor;	

	private String access_token;
	private String beacon_end_point = "/v1/mobile/beacons.json";
	private String api_domain;
	private String app_domain;
	private String url = null;
	private String url2 = null;
	
	private String uuid = null;
	private String major_id = null;
	private String minor_id = null;
	private String last_updated_at = null;
	private String current_datetime = null;
	private String format = null;
	private String params = null;
	private StrictMode.ThreadPolicy policy;

	protected PayWithAPI(Context context) {
		// this constructor receives the context from the call and sets our class context
		this.context = context;
		settings = context.getSharedPreferences(PREFERENCES, 0);
		access_token = getUserAccessToken();//getAccessToken(); // or getUserAccessToken();???
		api_domain = getApi_domainSetting();
		app_domain = getApp_domainSetting();
		policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
			StrictMode.setThreadPolicy(policy);
	}
	protected Map<String, String> getBeacon(String muuid, String mmajor, String mminor) {

		if (access_token == null) {
			// user is not logged in, exit early from api callback
			return null;
		}
		action = "beaconInfoRequest";
		uuid = muuid;
		major_id = mmajor;
		minor_id = mminor;
		last_updated_at = null; // need to do the date formatting below.
		current_datetime = null;
		// also need to store results of this api beacon call in sharedprefs and check
		// if data for a found beacon exists there and use that data before making a
		// possibly unneccessary api callback about it.
		
		// note: I need to do something like this still (from iOS app):

		format = "yyyy-MM-dd HH:mm:ss Z";
	    SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
	    //System.out.format("%30s %s\n", format, sdf.format(new Date(0)));
	    //sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
	    current_datetime = sdf.format(new Date(0));

	    //System.out.format("%30s %s\n", format, sdf.format(new Date(0)));
	 
				 
	    // Set a Beacon Shell object into UserHelper with last updated at of right now
	    // so that we do not query for this Beacon again until +24hours since we do not want to overwhelm device
	    /*NSDateFormatter *dateFormatter = [[NSDateFormatter alloc]init];
	    [dateFormatter setDateFormat:DATE_FORMAT];
	    NSString *last_updated_at = [dateFormatter stringFromDate:[NSDate date]];*/
		// the call should include last_updated_at, @"last_updated_at
		Map<String, String> resultdata = callAPI();
		return resultdata;
	}
	
	public String getApi_domainSetting() {
		// Don Kelley, August 2014
		return settings.getString("api_domainSetting", null);
	}
	
	public String getApp_domainSetting() {
		// Don Kelley, August 2014
		return settings.getString("app_domainSetting", null);
	}
	
	public String getAccessToken() {
		// Don Kelley, July 2014
		return settings.getString("access_token", null);
	}
	
	public String getUserAccessToken() {
		// Don Kelley, July 2014
		return settings.getString("user_access_token", null);
	}
	
	public String getCurrentLaunchUrl() {
		// Don Kelley, August 2014
		return settings.getString("currentLaunchUrl",  null);
	}
	
	public void setCurrentLaunchUrl(String mcurrentLaunchUrl) {
		// Don Kelley, August 2014
		//editor.putString("currentLaunchUrl", mcurrentLaunchUrl);
		//editor.commit();
	}

	
	public String findBeaconHistory(String muuid, Integer mmajor, Integer mminor) {
		// Don Kelley, August 2014
		//return settings.get("api_domainSetting", null);
		//Look through shared prefs for this beacon
		return null;
	}
	
	protected Map<String, String> callAPI() {
		// Don Kelley, Aug 2014
		// service method to talk to api and build response into string regarding Beacon data
		if (action == "beaconInfoRequest") {
			
			url = api_domain + beacon_end_point + "?access_token=" + access_token;// /native?acces...
			url2 = "&uuid=" + uuid + "&major_id=" + major_id + "&minor_id=" + minor_id + "&last_updated_at=" + null;//URLEncoder.encode(current_datetime);
			Log.e("service callAPI()::beaconInfoRequest",url + url2);
		}
    	Map<String, String> resultmap = new Hashtable();
		try {  
			/*new Thread(new Runnable() {
				   public void run() {
				   }                        
			}).start();*/
			//URL aURL = new URL(url + url2);//new URL( urls[0]);
			DefaultHttpClient httpclient= new DefaultHttpClient();
			HttpGet get = new HttpGet(url + url2);
			
			HttpContext c = null;
			String responseStr = httpclient.execute(get, new BasicResponseHandler(), c);
			//System.out.println("!!!! aURL = " + aURL);
			JSONObject respJSO = null;
			try {
				respJSO = new JSONObject(responseStr);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				Log.e("jsonerror",e.toString());
			}
			
			try {
				if (respJSO == null) {
					Log.e("api","respJSO == null");
					return null;
				}
				Log.d("IBeaconService PayWithAPI", "Credentials: " + respJSO.toString(2));

				JSONObject response = (JSONObject) respJSO.get("response");
				JSONObject data = (JSONObject) response.get("data");
				JSONArray beacons = (JSONArray) data.get("beacons");
				//JSONObject beacon = (JSONObject) beacons.get("beacon");
				//String message = (String) response.get("message");
				Integer status = (Integer) response.get("status");
				String location_id = null;
				
				String app_redirect_url = null;
				String merchant_name = "";
				if (status != 200) {
					// response bad
					return null;
				}
				//Date current_datetimeDT = null;
				//Date last_updated_atDT = null;
				//SimpleDateFormat formatter = new SimpleDateFormat(format);
				for (int i = 0; i < beacons.length(); i++) {

				    JSONObject arr = beacons.getJSONObject(i); 
				    //try {
				    	JSONObject resource = (JSONObject) arr.get("beacon_resource");
					    merchant_name = (String) resource.get("name");
						location_id = (String) resource.get("id");
					//} catch (ParseException e) {
						// TODO Auto-generated catch block
					//	e.printStackTrace();
					//}
Log.e("beacon name",merchant_name);
				    app_redirect_url = (String) arr.get("app_redirect_url");
				    /*last_updated_at = (String) arr.get("last_updated_at");
				    try {
						current_datetimeDT = formatter.parse(current_datetime);
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				    try {
						last_updated_atDT = formatter.parse(last_updated_at);
					} catch (ParseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				    if (current_datetimeDT != null && last_updated_atDT != null) {
				    	
					    long diffInMS = current_datetimeDT.getTime() - last_updated_atDT.getTime();
					    long diffInMin = TimeUnit.MILLISECONDS.toMinutes(diffInMS);
					    if (diffInMin >= 5) {
					    	Log.e("API - beacon time check","!!!!longer than 5 minutes past");
					    	return app_domain + app_redirect_url;
					    } else {
					    	Log.e("API - beacon time check","LESS than 5 minutes past");
					    	
					    }
				    }*/
					resultmap.put("url", app_domain + app_redirect_url);
					resultmap.put("name",merchant_name);
					resultmap.put("location_id",location_id);
					return resultmap;
				    //return app_domain + app_redirect_url;
				}
				//current_datetime = System.currentTimeMillis();
				return null;
				//Integer user_id = (Integer) user.get("id");
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				Log.e("jsonresponseerror",e.toString());
			}
			// return app_domain + location url stuff
			//System.out.println("!!!! url[1] = " + urls[1].toString());
			
			/* Open a connection to that URL. */
		    /*final HttpURLConnection aHttpsURLConnection = (HttpURLConnection) aURL.openConnection();
		    aHttpsURLConnection.setRequestMethod("GET");
		    //aHttpsURLConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		    //aHttpsURLConnection.setRequestProperty("Content-Length", "" 
		    //		+ Integer.toString(url2.getBytes().length));
		    aHttpsURLConnection.setUseCaches (false);
		    aHttpsURLConnection.setDoInput(true);
		    aHttpsURLConnection.setDoOutput(true);

			//System.out.println("!!!! done connection");
		    //Send request
		    DataOutputStream wr = new DataOutputStream (
		    		aHttpsURLConnection.getOutputStream ());
		    //wr.writeBytes (url2);
		    wr.flush ();
		    wr.close ();
	      
			//System.out.println("!!!! done post send");
		    // Define InputStreams to read from the URLConnection. 
		    InputStream aInputStream = aHttpsURLConnection.getInputStream();
		    BufferedReader aBufferedReader = new BufferedReader(
		            new InputStreamReader(aInputStream));

		    
		    String line;
	        StringBuffer response = new StringBuffer(); 
	        while((line = aBufferedReader.readLine()) != null) {
	        	response.append(line);
	        	response.append('\r');
	        }
	        aBufferedReader.close();
	        
			Log.e("PayWithAPI","!!!! done get read");
			Log.e("PayWithAPI","!!! response: " + response.toString());
*/
	        // note: the response needs to be handled like so:
	        /**beacons = data[@"beacons"];
	        if (beacons.count >= 1)
	        {
	            NSMutableDictionary *beacon = beacons[0];
	            [userHelper setBeaconByUUIDMajorMinorId:beacon[@"uuid"] MajorId:beacon[@"major_id"] MinorId:beacon[@"minor_id"] Beacon:beacon];
	            [self handleBeaconAction:beacon[@"uuid"] MajorId:beacon[@"major_id"] MinorId:beacon[@"minor_id"]];
	        }*/
	        
			return null;
		} catch (IOException e) {
			System.out.println("!!!! error " + e.toString());
			// on failed user authentication, THIS is where it's handled (as a filenotfoundexception!!), not in onpostexecute...
			if (e.toString().contains("UnknownHostException")) {
				// connection problem - alert user then exit;

				resultmap.put("url", "connection problem");
				resultmap.put("name", "");
				return resultmap;
			}
			if (action == "beaconInfoRequest") {
				Log.d("API","signInUser failed authentication or related error");
				// need to toast (or similar) a message to user here about bad login information so they check it and retry.
				// also should really make the phone number input box remember what you entered last time, right?  oh well, nice to have.

				// restart sign in procedure in mainactivity:

				resultmap.put("url", "signinfailure");
				resultmap.put("name", "");
				return resultmap;
			}
		}

		resultmap.put("url", "connection problem");
		resultmap.put("name", "");
		return resultmap;
	}
}
