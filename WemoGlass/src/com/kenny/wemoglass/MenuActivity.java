package com.kenny.wemoglass;
import java.util.List;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.glass.media.Sounds;
import com.kenny.wemoglass.WeMoService.LocalBinder;
public class MenuActivity extends Activity 
{
	private final static int SPEECH_ACTIVITY=1;
	private WeMoService wemoService;
	private boolean bound = false;
	 /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() 
    {
        @Override
        public void onServiceConnected(ComponentName className,IBinder service) 
        {
        	LocalBinder binder = (LocalBinder) service;
        	wemoService = binder.getService();
            bound = true;            
        }
        @Override
        public void onServiceDisconnected(ComponentName arg0) 
        {
            bound = false;
        }
    };
	@Override
	protected void onResume() 
	{
		//if we get the extra speech as true, launch the speech recognition service right away
		if(getIntent().getBooleanExtra("speech", false))
		{
			Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			startActivityForResult(intent, SPEECH_ACTIVITY);
		}
		else
		{
			openOptionsMenu();
		}
		super.onResume();
	}
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
		super.onStop();
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		getMenuInflater().inflate(R.menu.menu, menu);
		return super.onCreateOptionsMenu(menu);
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item) 
	{
		switch(item.getItemId())
		{
		//Refresh our devices
		case R.id.refresh:
			wemoService.refresh();
			finish();
			return true;
			//Open the speech input activity
		case R.id.speak:
			Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
			startActivityForResult(intent, SPEECH_ACTIVITY);
			return true;
			//Stop the app
		case R.id.stop:
			stopService(new Intent(getApplicationContext(),WeMoService.class));
			finish();
			return true;
			//Details about the devices
		case R.id.info:
			if(wemoService.getDevices()!=null&&wemoService.getDevices().size()>0)
			{
				startActivity(new Intent(getApplicationContext(),DeviceInfo.class));
				finish();
			}
			//If they have no devices, don't allow into the activity
			else
			{
				AudioManager audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
				audioManager.playSoundEffect(Sounds.DISALLOWED);
				audioManager=null;
				finish();
			}
			return true;
		}
		return false;
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
		 if (requestCode == SPEECH_ACTIVITY && resultCode == RESULT_OK) 
		 {
			 //Get our text and pass it back to the service to parse
			 List<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
		     String spokenText = results.get(0);
		     wemoService.parseSpeech(spokenText);
		     finish();
		 }
		super.onActivityResult(requestCode, resultCode, data);
	}
}
