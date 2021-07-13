package com.iottive.webrtc.util;

import android.widget.Toast;

import com.iottive.webrtc.MyApplication;

public class ToastUtil {
    public static void showToast(String s) {
        Toast.makeText(MyApplication.getContext, s, Toast.LENGTH_SHORT).show();
    }
}
