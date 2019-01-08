package com.example.admin.qrcodedemo;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.example.zxing.activity.CaptureActivity;
import com.hjq.permissions.OnPermission;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private Button to_zxing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();


    }

    private void initView() {
        to_zxing = (Button) findViewById(R.id.to_zxing);

        to_zxing.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.to_zxing:
                getPermission();
                break;
        }
    }

    private void getPermission() {
        if(!XXPermissions.isHasPermission(this,Permission.CAMERA)){
            XXPermissions.with(this).permission(Permission.CAMERA).request(new OnPermission() {
                @Override
                public void hasPermission(List<String> granted, boolean isAll) {
                    intentActivity();
                }



                @Override
                public void noPermission(List<String> denied, boolean quick) {

                }
            });
        }else {
            intentActivity();
        }

    }
    private void intentActivity() {
        Intent intent = new Intent(this, CaptureActivity.class);
        Bundle bundle = new Bundle();
//        bundle.putBoolean(CaptureActivity.KEY_NEED_BEEP, CaptureActivity.VALUE_BEEP);
//        bundle.putBoolean(CaptureActivity.KEY_NEED_VIBRATION, CaptureActivity.VALUE_VIBRATION);
//        bundle.putBoolean(CaptureActivity.KEY_NEED_EXPOSURE, CaptureActivity.VALUE_NO_EXPOSURE);
//        bundle.putByte(CaptureActivity.KEY_FLASHLIGHT_MODE, CaptureActivity.VALUE_FLASHLIGHT_OFF);
//        bundle.putByte(CaptureActivity.KEY_ORIENTATION_MODE, CaptureActivity.VALUE_ORIENTATION_AUTO);
//        bundle.putBoolean(CaptureActivity.KEY_SCAN_AREA_FULL_SCREEN, CaptureActivity.VALUE_SCAN_AREA_FULL_SCREEN);
//        bundle.putBoolean(CaptureActivity.KEY_NEED_SCAN_HINT_TEXT, CaptureActivity.VALUE_SCAN_HINT_TEXT);
//        intent.putExtra(CaptureActivity.EXTRA_SETTING_BUNDLE, bundle);
        startActivityForResult(intent, CaptureActivity.REQ_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case CaptureActivity.REQ_CODE:
                switch (resultCode) {
                    case RESULT_OK:
                        //or do sth
                        Toast.makeText(this, ""+data.getStringExtra(CaptureActivity.INTENT_EXTRA_KEY_QR_SCAN), Toast.LENGTH_SHORT).show();

                        break;
                    case RESULT_CANCELED:
                        if (data != null) {
                            // for some reason camera is not working correctly
                            Toast.makeText(this, ""+data.getStringExtra(CaptureActivity.INTENT_EXTRA_KEY_QR_SCAN), Toast.LENGTH_SHORT).show();

                        }
                        break;
                    default:
                        break;
                }
            default:
                break;
        }
    }


}
