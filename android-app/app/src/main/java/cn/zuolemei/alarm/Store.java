package cn.zuolemei.alarm;

import android.content.*;
import android.app.*;
import android.os.Build;
import org.json.*;
import java.util.UUID;

final class Store {
    static synchronized JSONObject read(Context c) {
        try { return new JSONObject(c.getSharedPreferences("alarms", 0).getString("data", "{\"tasks\":[],\"history\":[]}")); }
        catch (JSONException e) { throw new IllegalStateException("本地记录读取失败", e); }
    }
    static void write(Context c, JSONObject d) {
        if (!c.getSharedPreferences("alarms", 0).edit().putString("data", d.toString()).commit())
            throw new IllegalStateException("本地记录保存失败");
    }
    static AlarmManager manager(Context c) { return (AlarmManager)c.getSystemService(Context.ALARM_SERVICE); }
    static boolean allowed(Context c) { return Build.VERSION.SDK_INT < 31 || manager(c).canScheduleExactAlarms(); }
    static PendingIntent pending(Context c, String id) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction("ring." + id).putExtra("id", id);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    @android.annotation.SuppressLint("ScheduleExactAlarm")
    static void schedule(Context c, JSONObject t) {
        if (!allowed(c)) throw new IllegalStateException("请先开启闹钟和提醒权限");
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager(c).setAlarmClock(new AlarmManager.AlarmClockInfo(t.optLong("targetTime"), show), pending(c, t.optString("id")));
    }
    static JSONObject find(JSONArray a, String id) {
        for (int n=0;n<a.length();n++) if (id.equals(a.optJSONObject(n).optString("id"))) return a.optJSONObject(n);
        return null;
    }
    static synchronized void add(Context c, String title, int minutes) throws JSONException {
        if (minutes < 1 || minutes > 10080) throw new IllegalArgumentException("提醒时间需为 1 至 10080 分钟");
        JSONObject d = read(c);
        JSONObject t = new JSONObject().put("id", UUID.randomUUID().toString()).put("title", title.trim().isEmpty()?"未命名提醒":title.trim())
            .put("targetTime", System.currentTimeMillis()+minutes*60000L).put("status", "scheduled").put("snoozeCount",0);
        d.getJSONArray("tasks").put(t);
        write(c,d);
        try { schedule(c,t); } catch (RuntimeException e) { remove(c,t.optString("id")); throw e; }
    }
    static synchronized void remove(Context c, String id) throws JSONException {
        JSONObject d=read(c); JSONArray a=d.getJSONArray("tasks");
        for(int n=a.length()-1;n>=0;n--) if(id.equals(a.getJSONObject(n).optString("id"))) a.remove(n);
        write(c,d); manager(c).cancel(pending(c,id));
    }
    static synchronized boolean fire(Context c,String id) throws JSONException {
        JSONObject d=read(c); JSONObject t=find(d.getJSONArray("tasks"),id);
        if(t==null || !"scheduled".equals(t.optString("status"))) return false;
        // Ignore an obsolete broadcast delivered after a snooze.
        if(t.optLong("targetTime")>System.currentTimeMillis()+1000) return false;
        t.put("status","ringing"); write(c,d); return true;
    }
    static synchronized void act(Context c,String id,int snooze,String activity) throws JSONException {
        JSONObject d=read(c); JSONArray a=d.getJSONArray("tasks"); JSONObject t=find(a,id);
        if(t==null) return;
        if(snooze>0) {
            JSONObject updated=new JSONObject(t.toString()).put("targetTime",System.currentTimeMillis()+snooze*60000L)
                .put("status","scheduled").put("snoozeCount",t.optInt("snoozeCount")+1);
            schedule(c,updated);
            for(int n=0;n<a.length();n++) if(id.equals(a.getJSONObject(n).optString("id"))) a.put(n,updated);
            try {write(c,d);} catch(RuntimeException e) {manager(c).cancel(pending(c,id));throw e;}
        } else {
            JSONArray history=d.getJSONArray("history");
            history.put(new JSONObject(t.toString()).put("completedAt",System.currentTimeMillis()).put("activity",activity));
            for(int n=a.length()-1;n>=0;n--) if(id.equals(a.getJSONObject(n).optString("id"))) a.remove(n);
            write(c,d); manager(c).cancel(pending(c,id));
        }
    }
    static synchronized void markMissed(Context c,String id) throws JSONException {
        JSONObject d=read(c); JSONObject t=find(d.getJSONArray("tasks"),id);
        if(t!=null){t.put("status","missed");write(c,d);}
    }
    static synchronized void restore(Context c) throws JSONException {
        if(!allowed(c)) return;
        JSONObject d=read(c); JSONArray a=d.getJSONArray("tasks"); long now=System.currentTimeMillis();
        for(int n=0;n<a.length();n++) {
            JSONObject t=a.getJSONObject(n);
            if("missed".equals(t.optString("status"))) continue;
            if("ringing".equals(t.optString("status")) || t.optLong("targetTime")<=now) {
                // One recovery alarm, rather than silently dropping an overdue reminder.
                t.put("targetTime",now+3000).put("status","scheduled");
            }
            schedule(c,t);
        }
        write(c,d);
    }
}
