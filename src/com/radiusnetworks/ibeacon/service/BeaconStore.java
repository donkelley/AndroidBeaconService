package com.radiusnetworks.ibeacon.service;

public class BeaconStore {
	//private variables
    int _id;
    String _uuid;
    Integer _major;
    Integer _minor;
     
    // Empty constructor
    public BeaconStore(){
         
    }
    
    // constructor
    public BeaconStore(int id, String uuid, Integer _major, Integer _minor){
        this._id = id;
        this._uuid = uuid;
        this._major = _major;
        this._minor = _minor;
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
}
