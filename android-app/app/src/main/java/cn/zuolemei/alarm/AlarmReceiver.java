package cn.zuolemei.alarm;
import android.content.*;
public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        try {if(Store.fire(c,i.getStringExtra("id")))c.startForegroundService(new Intent(c,RingService.class));}
        catch(Exception e){android.util.Log.e("Zuolemei","Alarm delivery failed",e);}
    }
}
