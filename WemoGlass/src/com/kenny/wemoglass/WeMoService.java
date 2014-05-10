package com.kenny.wemoglass;
import java.util.ArrayList;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import com.belkin.wemo.localsdk.WeMoDevice;
import com.belkin.wemo.localsdk.WeMoSDKContext;
import com.belkin.wemo.localsdk.WeMoSDKContext.NotificationListener;
import com.google.android.glass.media.Sounds;
import com.google.android.glass.timeline.LiveCard;
public class WeMoService extends Service
{
	//Interface that fires when our list of devices gets updated
	public interface UpdateListener
	{
		void onDevicesUpdated(int numOfDevices);
	}
	private UpdateListener updateListener;
	private final String CARD_ID="wemo_glass";
	private LiveCard liveCard;
	//Object that handles all of the WeMo devices
	private WeMoSDKContext wemoContext = null;
	//List to keep track of our devices
	private ArrayList<WeMoDevice>devices;
	private RemoteViews remoteViews;
	private AudioManager audioManager;
	private final IBinder binder = new LocalBinder();
	@Override
	public void onCreate() 
	{
		super.onCreate();
		devices= new ArrayList<WeMoDevice>();
		audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
		wemoContext = new WeMoSDKContext(getApplicationContext());		
		wemoContext.addNotificationListener(new NotificationListener() 
		{
			//Event will be along the lines of refresh, add, or remove. udn is the unique udn identifier that belongs to the WeMo device
			@Override
			public void onNotify(String event, String udn) 
			{			
				//Refresh event
				if(event.equals(WeMoSDKContext.REFRESH_LIST))
				{
					//Get available devices found. This usually does not come up with all of them. So don't be surprised if it
					//does not find all(if any) of your devices right away. The add event will fire (eventually) and find it.
					ArrayList<String> udns = wemoContext.getListOfWeMoDevicesOnLAN();
					for (String s : udns) 
					{
						WeMoDevice foundDevice = wemoContext.getWeMoDeviceByUDN(s);
						if (foundDevice != null && foundDevice.isAvailable()) 
						{
							boolean shouldAdd=true;
							//Loop through our devices and see if we already have it in our list. If we do, don't add it
							for(WeMoDevice d:devices)
							{
								if(d.getUDN().equals(foundDevice.getUDN()))
								{
									shouldAdd=false;
									break;
								}
							}
							if(shouldAdd)
							{
								devices.add(foundDevice);
							}
						}
					}
				}
				//Add device
				else if(event.equals(WeMoSDKContext.ADD_DEVICE))
				{
					WeMoDevice foundDevice = wemoContext.getWeMoDeviceByUDN(udn);
					if (foundDevice != null && foundDevice.isAvailable()) 
					{
						boolean shouldAdd=true;
						//Loop through our devices and see if we already have it in our list. If we do, don't add it
						for(WeMoDevice d:devices)
						{
							if(d.getUDN().equals(foundDevice.getUDN()))
							{
								shouldAdd=false;
								break;
							}
						}
						if(shouldAdd)
						{
							devices.add(foundDevice);
						}
					}
				}
				//Remove device
				else if(event.equals(WeMoSDKContext.REMOVE_DEVICE))
				{
					WeMoDevice foundDevice = wemoContext.getWeMoDeviceByUDN(udn);
					if (foundDevice != null) 
					{
						boolean shouldRemove=false;
						int index=-1;
						//Loop through our devices and see if device exists in our list. If it does, remove it
						for(int i=0;i<devices.size();i++)
						{
							if(devices.get(i).getUDN().equals(foundDevice.getUDN()))
							{
								shouldRemove=true;
								break;
							}
						}
						if(shouldRemove&&index>-1)
						{
							devices.remove(index);							
						}
					}
				}
				updateListener.onDevicesUpdated(devices.size());				
			}
		});
		updateListener= new UpdateListener() 
		{			
			@Override
			public void onDevicesUpdated(int numOfDevices) 
			{
				remoteViews.setViewVisibility(R.id.progressBar, View.GONE);
				if(numOfDevices>0)
				{
					remoteViews.setTextViewText(R.id.infoText,numOfDevices+" "+getString(R.string.devices_found));
				}
				else
				{
					remoteViews.setTextViewText(R.id.infoText,getString(R.string.no_devices));		
				}
				liveCard.setViews(remoteViews);
			}
		};
	}
	@Override
	public void onDestroy() 
	{
		//make sure to stop the WeMo context upon exit
		if(wemoContext!=null)
		{
			wemoContext.stop();
		}
		//unpublish our live card
		if (liveCard != null) 
		{
			liveCard.unpublish();
			liveCard = null;
		}
		audioManager=null;		
		super.onDestroy();
	}
	/***
	 * Search the network for devices
	 */
	public void refresh()
	{
		remoteViews.setViewVisibility(R.id.progressBar, View.VISIBLE);
		remoteViews.setTextViewText(R.id.infoText,getString(R.string.searching_devices));
		liveCard.setViews(remoteViews);
		if(devices!=null)
		{
			devices.clear();
		}
		wemoContext.refreshListOfWeMoDevicesOnLAN();
	}
	@Override
	public IBinder onBind(Intent intent) 
	{
		return binder;
	}
	public class LocalBinder extends Binder 
	{
		WeMoService getService() 
		{
			return WeMoService.this;
	    }
	}
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) 
	{
		if (liveCard == null) 
	    {
			liveCard =  new LiveCard(getApplicationContext(), CARD_ID);
	        // Display the options menu when the live card is tapped.
	        Intent menuIntent = new Intent(this, MenuActivity.class);
	        menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
	        liveCard.setAction(PendingIntent.getActivity(this, 0, menuIntent, 0));
	        remoteViews= new RemoteViews(getApplicationContext().getPackageName(), R.layout.loading);
	        liveCard.setViews(remoteViews);
	        liveCard.publish(LiveCard.PublishMode.REVEAL);
	        refresh();
	    }
		//If the user triggered the voice command after the app has already been started, it will bring up the voice input immediately
		else
		{
			 Intent menuIntent = new Intent(this, MenuActivity.class);
			 menuIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
		     menuIntent.putExtra("speech", true);
		     startActivity(menuIntent);
		}
		return START_STICKY;
	}	
	/***
	 * Parses the given phrase.
	 * @param speech the Phrase to parse
	 */
	public void parseSpeech(String speech)
	{
		//Split our phrase and get each word
		//The phrases that will be acceptable will be Turn On <Device Name> and Turn Off <Device Name>
		String[] results=speech.split("\\s");		
		//Make sure our phrase matches the acceptable conditions
		if(results!=null&&results.length>=3)
		{
			if(results[0].equalsIgnoreCase(getString(R.string.turn)))
			{
				if(results[1].equalsIgnoreCase(getString(R.string.on))||results[1].equalsIgnoreCase(getString(R.string.off)))
				{
					//Append the remaining string values into one string
					String deviceName="";
					for(int i=2;i<results.length;i++)
					{
						deviceName+=results[i]+" ";
					}
					//Remove trailing white space at end, not sure if it will matter
					deviceName = deviceName.substring(0, deviceName.length() - 1);
					toggleDevice(results[1],deviceName);
				}
				//If our second word is not on or off, it is not allowed
				else
				{
					audioManager.playSoundEffect(Sounds.DISALLOWED);
				}
			}
			//If the first word is not "Turn" then the phrase is not acceptable
			else
			{
				audioManager.playSoundEffect(Sounds.DISALLOWED);
			}
		}
		else
		{
			audioManager.playSoundEffect(Sounds.ERROR);
		}
	}
	/***
	 * Toggles the device on or off
	 * @param action on or off
	 * @param name friendly name of device
	 */
	public void toggleDevice(String action,String name)
	{
		//If our command is to turn device on
		boolean turnOn=action.equalsIgnoreCase(getString(R.string.on));
		//If our device is currently on
		boolean isOn;		
		for(WeMoDevice d:devices)
		{
			Log.e("NAME", d.getFriendlyName());
			if(d.getFriendlyName().equalsIgnoreCase(name))
			{
				String state = d.getState().split("\\|")[0];
				isOn=state.equals(WeMoDevice.WEMO_DEVICE_ON);
				//If our command is to turn on and the device is on, do nothing
				if(turnOn&&isOn)
				{
					audioManager.playSoundEffect(Sounds.DISALLOWED);
				}
				//Same goes for turning off and being off
				else if(!turnOn&&!isOn)
				{
					audioManager.playSoundEffect(Sounds.DISALLOWED);
				}
				else
				{
					if(turnOn)
					{
						wemoContext.setDeviceState(WeMoDevice.WEMO_DEVICE_ON, d.getUDN());
					}
					else
					{
						Log.e("OFF", "OFF");
						wemoContext.setDeviceState(WeMoDevice.WEMO_DEVICE_OFF, d.getUDN());
					}
					audioManager.playSoundEffect(Sounds.SUCCESS);
				}
				break;
			}
		}
	}
	/***
	 * Returns list of devices
	 * @return ArrayList containing our WeMoDevices
	 */
	public ArrayList<WeMoDevice>getDevices()
	{
		return devices;
	}
}
