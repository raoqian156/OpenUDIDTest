package study.rq.com.openudidtest;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import study.rq.com.openudidtest.utils.OpenUDID_manager;
import study.rq.com.openudidtest.utils.UDIDHelper;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OpenUDID_manager.sync(MainActivity.this);
        setContentView(R.layout.activity_main);
        TextView tvShow = (TextView) findViewById(R.id.tv_show);
        String openId = "";
        String UDIDHelperGet = UDIDHelper.getUDID(MainActivity.this);
        String OpenUDIDGet = OpenUDID_manager.getOpenUDID();
        openId += "UDIDHelperGet = " + UDIDHelperGet + "\n";
        openId += "OpenUDIDGet = " + OpenUDIDGet;
        tvShow.setText(openId);
    }
}
