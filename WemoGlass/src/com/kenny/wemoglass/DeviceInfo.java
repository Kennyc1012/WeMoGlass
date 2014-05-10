package com.kenny.wemoglass;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.belkin.wemo.localsdk.WeMoDevice;
import com.google.android.glass.app.Card;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;
import com.kenny.utils.MemoryCache;
import com.kenny.wemoglass.WeMoService.LocalBinder;
public class DeviceInfo extends Activity 
{
	private boolean bound=false;
	private DeviceAdapter adapter;
	private CardScrollView cardScrollView;
	private MenuItem menuItem;
	private WeMoService wemoService;
	 /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() 
    {
        @Override
        public void onServiceConnected(ComponentName className,IBinder service) 
        {
        	//Once we bind to the service, get our devices and put them into a list
        	LocalBinder binder = (LocalBinder) service;
        	wemoService=binder.getService();
        	ArrayList<WeMoDevice>dev=wemoService.getDevices();
        	//This SHOULD never happen, but just a precaution
        	if(dev!=null&&dev.size()>0)
        	{
        		cardScrollView = new CardScrollView(getApplicationContext());
        		adapter= new DeviceAdapter(getApplicationContext(), dev);
        		cardScrollView.setAdapter(adapter);
        		cardScrollView.activate();
        		cardScrollView.setOnItemClickListener(new OnItemClickListener() 
        		{
					@Override
					public void onItemClick(AdapterView<?> arg0, View arg1,int position, long arg3) 
					{
						//based on the state of the device, we display a different menu item
						WeMoDevice device=(WeMoDevice) adapter.getItem(position);
						openOptionsMenu();
						if(device.getState().equals(WeMoDevice.WEMO_DEVICE_ON))
						{
							menuItem.setTitle(R.string.turn_off);
							menuItem.setIcon(R.drawable.ic_no_50);
						}
						else
						{
							menuItem.setTitle(R.string.turn_on);
							menuItem.setIcon(R.drawable.ic_done_50);
						}						
					}
				});
        	    setContentView(cardScrollView);
        	}
        	else
        	{
        		Card card = new Card(getApplicationContext());
        		card.setText(R.string.no_devices);
        		card.setFootnote(R.string.app_name);
        		card.addImage(R.drawable.ic_warning_150);
        		setContentView(card.getView());
        	}
            bound = true;            
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) 
        {
            bound = false;
        }
    };
    @Override
	protected void onStart()
	{
		//Bind to our service
		Intent intent = new Intent(this, WeMoService.class);
	    bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		super.onStart();
	}
	@Override
	protected void onStop() 
	{
		 // Unbind from the service
        if (bound) 
        {
            unbindService(mConnection);
            bound = false;
        }
        if(adapter!=null)
        {
        	//Make sure to free up memory when we exit
        	adapter.clearCache();
        }
		super.onDestroy();
	}
	@Override
	protected void onCreate(Bundle savedInstanceState) 
	{
		super.onCreate(savedInstanceState);
	}
	private class DeviceAdapter extends CardScrollAdapter
	{
		private ArrayList<WeMoDevice>devices;
		private LayoutInflater inflater;
		private Context context;
		private MemoryCache cache;
		public DeviceAdapter(Context context,ArrayList<WeMoDevice>devices)
		{
			this.context=context;
			this.devices=devices;
			inflater=LayoutInflater.from(context);
			//Create a memory cache 1/16 of our available memory
			cache=new MemoryCache(16);
		}
		@Override
		public int getCount() 
		{
			if(devices!=null)
			{
				return devices.size();
			}
			return 0;
		}
		@Override
		public Object getItem(int position) 
		{
			if(devices!=null)
			{
				return devices.get(position);
			}
			return null;
		}
		@Override
		public int getPosition(Object arg0) 
		{
			return 0;
		}
		@Override
		public long getItemId(int position) 
		{
			return 0;
		}
		@Override
		public View getView(int position, View convertView, ViewGroup parent) 
		{
			ViewHolder holder=null;
			WeMoDevice d=devices.get(position);
			if(convertView==null)
			{
				convertView=inflater.inflate(R.layout.card_layout, parent,false);
				holder=new ViewHolder();
				holder.logo=(ImageView)convertView.findViewById(R.id.logo);
				holder.name=(TextView)convertView.findViewById(R.id.name);
				holder.state=(TextView)convertView.findViewById(R.id.state);
				convertView.setTag(holder);
			}
			else
			{
				holder=(ViewHolder)convertView.getTag();
			}
			holder.name.setText(d.getFriendlyName());
			if(d.getState().equals(WeMoDevice.WEMO_DEVICE_ON))
			{
				holder.state.setText(context.getString(R.string.state)+" "+context.getString(R.string.on));
			}
			else
			{
				holder.state.setText(context.getString(R.string.state)+" "+context.getString(R.string.off));
			}
			//Check for our image in the cache first, download it if we don't have it
			Bitmap b=cache.getBitmapFromMemCache(d.getLogoURL());
			if(b==null)
			{
				new LogoDownloader(holder.logo,d.getLogoURL());
			}
			else
			{
				holder.logo.setImageBitmap(b);
			}
			return convertView;
		}	
		class ViewHolder
		{
			ImageView logo;
			TextView name,state;
		}
		/***
		 * Class to download the devices thumbnail* 
		 *
		 */
		private class LogoDownloader extends AsyncTask<String, Void, Bitmap> 
		{
			private ImageView logo;
			private String logoUrl;
			LogoDownloader(ImageView image,String url)
			{
				logo=image;
				logoUrl=url;
				execute();
			}
			@Override
			protected Bitmap doInBackground(String... parameters) 
			{
				//use a standard http connection to download the image in the background
				Bitmap bitmap = null;				
				try 
				{
					URL url = new URL(logoUrl);
			 		HttpURLConnection connection = (HttpURLConnection)url.openConnection();
			 		connection.setRequestMethod("GET");
					if (url.getHost() != null) 
					{
						connection.setRequestProperty("HOST", url.getHost());
					}
					InputStream inputStream = connection.getInputStream();
					bitmap = BitmapFactory.decodeStream(inputStream);
					inputStream.close();
				} 
				catch (Exception e) 
				{
					
				}				
				return bitmap;
			}
			@Override
			protected void onPostExecute(Bitmap bitmap) 
			{
				if (bitmap != null) 
				{
					cache.addBitmapToMemoryCache(logoUrl, bitmap);
					logo.setImageBitmap(bitmap);
				}
				else
				{
					logo.setImageResource(R.drawable.ic_question_150);
				}
			}		
		}
		/***
		 * Clear our images out of memory
		 */
		public void clearCache()
		{
			if(cache!=null)
			{
				cache.clearCache();
			}
		}
		
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{	
		getMenuInflater().inflate(R.menu.device_menu, menu);
		menuItem=menu.findItem(R.id.on_off);
		return super.onCreateOptionsMenu(menu);
	}	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		switch(item.getItemId())
		{
		case R.id.on_off:
			WeMoDevice device =(WeMoDevice)adapter.getItem(cardScrollView.getSelectedItemPosition());
			if(item.getTitle().equals(getString(R.string.turn_off)))
			{
				wemoService.toggleDevice(getString(R.string.off), device.getFriendlyName());
			}
			else
			{
				wemoService.toggleDevice(getString(R.string.on), device.getFriendlyName());
			}
			closeOptionsMenu();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
