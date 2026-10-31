package cn.zuolemei.alarm;
import android.content.*;
public class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        try{Store.restore(c);}catch(Exception e){android.util.Log.e("Zuolemei","Restore failed",e);}
    }
}
