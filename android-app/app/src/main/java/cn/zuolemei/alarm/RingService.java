package cn.zuolemei.alarm;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import org.json.*;

public class RingService extends Service {
    private static final int NOTIFICATION=100;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Vibrator vibrator;
    private PowerManager.WakeLock wake;
    private String activeId="";
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();
        NotificationChannel channel=new NotificationChannel("alarm","闹钟响铃",NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("到期事项的响铃与操作");channel.setSound(null,null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    private PendingIntent action(String id,int minutes){
        return PendingIntent.getService(this,minutes,new Intent(this,RingService.class).setAction("act."+id+"."+minutes)
            .putExtra("id",id).putExtra("minutes",minutes),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private Notification notification(JSONObject task){
        String id=task.optString("id");
        PendingIntent show=PendingIntent.getActivity(this,0,new Intent(this,RingActivity.class).putExtra("id",id)
            .setAction("show."+id),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,"alarm").setSmallIcon(cn.zuolemei.alarm.R.drawable.ic_alarm)
            .setContentTitle(task.optString("title")).setContentText("该做这件事了 · 点此打开")
            .setCategory(Notification.CATEGORY_ALARM).setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true).setContentIntent(show).setFullScreenIntent(show,true)
            .addAction(new Notification.Action.Builder(null,"完成",action(id,0)).build())
            .addAction(new Notification.Action.Builder(null,"10分钟后",action(id,10)).build()).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        try{
            if(intent!=null && intent.getAction()!=null && intent.getAction().startsWith("act."))
                Store.act(this,intent.getStringExtra("id"),intent.getIntExtra("minutes",0),"");
            JSONObject next=null;
            JSONArray tasks=Store.read(this).getJSONArray("tasks");
            for(int n=0;n<tasks.length();n++)if("ringing".equals(tasks.getJSONObject(n).optString("status"))){next=tasks.getJSONObject(n);break;}
            if(next==null){stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();return START_NOT_STICKY;}
            startForeground(NOTIFICATION,notification(next));
            if(!activeId.equals(next.optString("id"))){
                stopSound();activeId=next.optString("id");playSound();
                String soundingId=activeId;
                handler.postDelayed(()->timeout(soundingId),5*60*1000);
            }
        }catch(Exception e){android.util.Log.e("Zuolemei","Ringing failed",e);stopForeground(STOP_FOREGROUND_REMOVE);stopSelf();}
        return START_NOT_STICKY;
    }
    private void playSound(){
        try{
            wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"zuolemei:alarm");wake.acquire(6*60*1000L);
            player=new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            android.net.Uri sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if(sound==null)sound=RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            player.setDataSource(this,sound);player.setLooping(true);player.prepare();player.start();
        }catch(Exception e){android.util.Log.e("Zuolemei","Sound unavailable",e);}
        vibrator=(Vibrator)getSystemService(VIBRATOR_SERVICE);
        if(vibrator!=null)vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,700,500},0),new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
    }
    private void timeout(String id){
        try{
            if(!id.equals(activeId))return;
            Store.markMissed(this,id);
            onStartCommand(new Intent().setAction("refresh"),0,0);
        }catch(Exception e){stopSelf();}
    }
    private void stopSound(){
        handler.removeCallbacksAndMessages(null);
        if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}
        if(vibrator!=null)vibrator.cancel();
        if(wake!=null && wake.isHeld())wake.release();wake=null;activeId="";
    }
    @Override public void onDestroy(){stopSound();super.onDestroy();}
}
