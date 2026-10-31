package cn.zuolemei.alarm;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;

public class RingActivity extends Activity {
    private String id;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable check=new Runnable(){public void run(){
        try{JSONObject t=Store.find(Store.read(RingActivity.this).getJSONArray("tasks"),id);
            if(t==null || !"ringing".equals(t.optString("status"))){finish();return;}
        }catch(Exception e){finish();return;}handler.postDelayed(this,1000);
    }};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(Build.VERSION.SDK_INT>=27){setShowWhenLocked(true);setTurnScreenOn(true);}
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        id=getIntent().getStringExtra("id");
        try{
            JSONObject t=Store.find(Store.read(this).getJSONArray("tasks"),id);
            if(t==null){finish();return;}
            LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(32,60,32,60);box.setBackgroundColor(0xff111111);
            TextView title=new TextView(this);title.setText(t.optString("title"));title.setTextColor(0xffffffff);title.setTextSize(30);box.addView(title);
            EditText activity=new EditText(this);activity.setHint("你现在正在做什么？（可留空）");activity.setTextColor(0xffffffff);activity.setHintTextColor(0xffaaaaaa);box.addView(activity);
            for(int minutes:new int[]{0,5,10,15}){
                Button button=new Button(this);button.setText(minutes==0?"完成了":"稍后 "+minutes+" 分钟");
                button.setOnClickListener(v->{try{
                    Store.act(this,id,minutes,activity.getText().toString());
                    startService(new Intent(this,RingService.class).setAction("refresh"));finish();
                }catch(Exception e){new AlertDialog.Builder(this).setMessage(e.getMessage()).setPositiveButton("知道了",null).show();}});
                box.addView(button,new LinearLayout.LayoutParams(-1,-2));
            }
            setContentView(box);
        }catch(Exception e){finish();}
    }
    @Override protected void onResume(){super.onResume();handler.post(check);}
    @Override protected void onPause(){handler.removeCallbacks(check);super.onPause();}
}
