package com.radiusnetworks.ibeacon.service;

public class BeaconStore {
	//private variables
    int _id;
    String _uuid;
    Integer _major;
    Integer _minor;
    String _updatedDate;
    String _merchantName;
    Integer _locationId;
    String _url;
     
    // Empty constructor
    public BeaconStore(){
         
    }
    
    // constructor
    public BeaconStore(int id, String uuid, Integer _major, Integer _minor, String _updatedDate, String _merchantName, Integer _locationId, String _url){
        this._id = id;
        this._uuid = uuid;
        this._major = _major;
        this._minor = _minor;
        this._updatedDate = _updatedDate;
        this._merchantName = _merchantName;
        this._locationId = _locationId;
        this._url = _url;
    }
    
    // constructor
    public BeaconStore(String uuid, Integer _major, Integer _minor, String _updatedDate, String _merchantName, Integer _locationId, String _url){
        this._uuid = uuid;
        this._major = _major;
        this._minor = _minor;
        this._updatedDate = _updatedDate;
        this._merchantName = _merchantName;
        this._locationId = _locationId;
        this._url = _url;
    }
    
    // getting ID
    public int getID(){
        return this._id;
    }
     
    // setting id
    public void setID(int id){
        this._id = id;
    }
     
    // getting uuid
    public String getUuid(){
        return this._uuid;
    }
     
    // setting uuid
    public void setUuid(String uuid){
        this._uuid = uuid;
    }
    
   // getting major
   public Integer getMajor(){
       return this._major;
   }
    
   // setting major
   public void setMajor(Integer major){
       this._major = major;
   }
   
  // getting minor
  public Integer getMinor(){
      return this._minor;
  }
   
  // setting minor
  public void setMinor(Integer minor){
      this._minor = minor;
  }
  
 // getting updatedDate
 public String getUpdatedDate(){
     return this._updatedDate;
 }
  
 // setting updatedDate
 public void setUpdatedDate(String updatedDate){
     this._updatedDate = updatedDate;
 }
  
 // getting merchantName
 public String getMerchantName(){
     return this._merchantName;
 }
  
 // setting merchantName
 public void setMerchantName(String merchantName){
     this._merchantName = merchantName;
 }
 
 // getting locationId
 public Integer getLocationId(){
     return this._locationId;
 }
 
 // setting locationId
 public void setLocationId(Integer locationId){
     this._locationId = locationId;
 }
 
 // getting url
 public String getUrl(){
     return this._url;
 }
  
 // setting url
 public void setUrl(String url){
     this._url = url;
 }
}
