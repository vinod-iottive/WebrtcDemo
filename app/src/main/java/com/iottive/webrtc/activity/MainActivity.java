package com.iottive.webrtc.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.TextView;

import com.iottive.webrtc.R;
import com.iottive.webrtc.databinding.ActivityMainBinding;
import com.iottive.webrtc.util.AppMethods;
import com.iottive.webrtc.util.Constant;
import com.iottive.webrtc.util.ToastUtil;

import java.util.ArrayList;
import java.util.Random;

import static com.iottive.webrtc.util.AppMethods.isNetworkConnected;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int PERMISSION_REQUEST = 2;
    ActivityMainBinding binding;
    private static final String TAG = "MainActivity";
    private static final int CONNECTION_REQUEST = 1;
    public static boolean commandLineRun = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        initUI();
        requestPermissions();
    }

    private void initUI() {
        binding.btnNewMeeting.setOnClickListener(this);
        binding.btnJoin.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnNew_Meeting:
                CreateDialog();
                break;
            case R.id.btnJoin:
                JoinDialog();
                break;
        }
    }

    private void JoinDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar);
        dialog.setContentView(R.layout.dialog_create_join_meeting);
        EditText edJoinCode = dialog.findViewById(R.id.edit_join_code);
        dialog.findViewById(R.id.tv_join).setOnClickListener(v -> {
            String strJoinCode = edJoinCode.getText().toString().trim();
           if( isNetworkConnected(MainActivity.this)) {
               if (!strJoinCode.equals("")) {
                   dialog.dismiss();
                   connectToMeeting(strJoinCode, false, false, false, 0);
               } else {
                   ToastUtil.showToast(getString(R.string.please_enter_join_code));
               }
           }else {
               ToastUtil.showToast(getString(R.string.no_internet_connection));
           }
        });
        dialog.show();
        dialog.findViewById(R.id.iv_close).setOnClickListener(v -> {
            dialog.dismiss();
        });
    }

    public void CreateDialog() {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar);
        dialog.setContentView(R.layout.dialog_create_join_meeting);
        TextView tvMeeting = dialog.findViewById(R.id.tv_meeting);
        TextView tvMeetingText = dialog.findViewById(R.id.tv_enter_the_join_code);
        EditText edJoinCode = dialog.findViewById(R.id.edit_join_code);
        TextView tvCreate = dialog.findViewById(R.id.tv_join);
        tvMeeting.setText(getString(R.string.create_meeting));
        tvMeetingText.setText(getString(R.string.enter_code_or_use_random_code));
        tvCreate.setText(getString(R.string.create));

        edJoinCode.setText(AppMethods.getRandomString(8));
        edJoinCode.requestFocus();

        tvCreate.setOnClickListener(v -> {
            if( isNetworkConnected(MainActivity.this)) {
                String strJoinCode = edJoinCode.getText().toString().trim();
                if (!strJoinCode.equals("")) {
                    dialog.dismiss();
                    connectToMeeting(strJoinCode, false, false, false, 0);
                } else {
                    ToastUtil.showToast(getString(R.string.please_enter_join_code));
                }
            }else {
                ToastUtil.showToast(getString(R.string.no_internet_connection));
            }
        });
        dialog.findViewById(R.id.iv_close).setOnClickListener(v -> {
            dialog.dismiss();
        });
        edJoinCode.setCompoundDrawablesWithIntrinsicBounds(null, null, getDrawable(R.drawable.ic_baseline_share_24), null);
        edJoinCode.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (event.getRawX() >= (edJoinCode.getRight() - edJoinCode.getCompoundDrawables()[2].getBounds().width())) {
                        AppMethods.shareText(MainActivity.this, getString(R.string.share_invitation), getString(R.string.you_can_join_a_video_call_meeting) + " " + edJoinCode.getText().toString() + "\n" + getString(R.string._or_click_on_the_link_below_to_join_the_meeting) + " " + getString(R.string.pref_room_server_url_default) + "/r/" + edJoinCode.getText().toString());
                        return true;
                    }
                }
                return false;
            }
        });
        dialog.show();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            String[] missingPermissions = getMissingPermissions();
            if (missingPermissions.length != 0) {
                new AlertDialog.Builder(this)
                        .setMessage(R.string.missing_permissions_try_again)
                        .setPositiveButton(R.string.yes,
                                (dialog, id) -> {
                                    dialog.cancel();
                                    requestPermissions();
                                })
                        .setNegativeButton(R.string.no,
                                (dialog, id) -> {
                                    // User doesn't want to give the permissions.
                                    dialog.cancel();
                                })
                        .show();
            } else {
                // All permissions granted.
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        String[] missingPermissions = getMissingPermissions();
        if (missingPermissions.length != 0) {
            requestPermissions(missingPermissions, PERMISSION_REQUEST);
        } else {
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private String[] getMissingPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return new String[0];
        }

        PackageInfo info;
        try {
            info = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Failed to retrieve permissions.");
            return new String[0];
        }

        if (info.requestedPermissions == null) {
            Log.w(TAG, "No requested permissions.");
            return new String[0];
        }

        ArrayList<String> missingPermissions = new ArrayList<>();
        for (int i = 0; i < info.requestedPermissions.length; i++) {
            if ((info.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) == 0) {
                missingPermissions.add(info.requestedPermissions[i]);
            }
        }
        Log.d(TAG, "Missing permissions: " + missingPermissions);

        return missingPermissions.toArray(new String[missingPermissions.size()]);
    }


    private void connectToMeeting(String roomId, boolean commandLineRun, boolean loopback,
                                  boolean useValuesFromIntent, int runTimeMs) {
        MainActivity.commandLineRun = commandLineRun;

        if (loopback) {
            roomId = Integer.toString((new Random()).nextInt(100000000));
        }

        String roomUrl = getString(R.string.pref_room_server_url_default);

        // Video call enabled flag.
        boolean videoCallEnabled = true;
        // Use Camera2 option.
        boolean useCamera2 = true;

        // Get default codecs.
        String videoCodec = getString(R.string.pref_videocodec_default);
        String audioCodec = getString(R.string.pref_audiocodec_default);


        // Get video resolution from settings.
        int videoWidth = 0;
        int videoHeight = 0;
        if (useValuesFromIntent) {
            videoWidth = getIntent().getIntExtra(Constant.EXTRA_VIDEO_WIDTH, 0);
            videoHeight = getIntent().getIntExtra(Constant.EXTRA_VIDEO_HEIGHT, 0);
        }
        if (videoWidth == 0 && videoHeight == 0) {
            String resolution = getString(R.string.pref_resolution_default);
            String[] dimensions = resolution.split("[ x]+");
            if (dimensions.length == 2) {
                try {
                    videoWidth = Integer.parseInt(dimensions[0]);
                    videoHeight = Integer.parseInt(dimensions[1]);
                } catch (NumberFormatException e) {
                    videoWidth = 0;
                    videoHeight = 0;
                    Log.e(TAG, "Wrong video resolution setting: " + resolution);
                }
            }
        }

        int cameraFps = 0;
        if (useValuesFromIntent) {
            cameraFps = getIntent().getIntExtra(Constant.EXTRA_VIDEO_FPS, 0);
        }
        if (cameraFps == 0) {
            String fps = getString(R.string.pref_fps_default);
            String[] fpsValues = fps.split("[ x]+");
            if (fpsValues.length == 2) {
                try {
                    cameraFps = Integer.parseInt(fpsValues[0]);
                } catch (NumberFormatException e) {
                    cameraFps = 0;
                    Log.e(TAG, "Wrong camera fps setting: " + fps);
                }
            }
        }
        int videoStartBitrate = 0;
        if (useValuesFromIntent) {
            videoStartBitrate = getIntent().getIntExtra(Constant.EXTRA_VIDEO_BITRATE, 0);
        }
        if (videoStartBitrate == 0) {
            String bitrateValue = getString(R.string.pref_maxvideobitratevalue_default);
            videoStartBitrate = Integer.parseInt(bitrateValue);
        }

        int audioStartBitrate = 0;
        if (useValuesFromIntent) {
            audioStartBitrate = getIntent().getIntExtra(Constant.EXTRA_AUDIO_BITRATE, 0);
        }
        if (audioStartBitrate == 0) {
            String bitrateValue = getString(R.string.pref_startaudiobitratevalue_default);
            audioStartBitrate = Integer.parseInt(bitrateValue);
        }

        boolean dataChannelEnabled = true;
        String protocol = getString(R.string.pref_data_protocol_default);

        Log.d(TAG, "Connecting to room " + roomId + " at URL " + roomUrl);
        if (validateUrl(roomUrl)) {
            Uri uri = Uri.parse(roomUrl);
            Intent intent = new Intent(this, CallingActivity.class);
            intent.setData(uri);
            intent.putExtra(Constant.EXTRA_ROOMID, roomId);
            intent.putExtra(Constant.EXTRA_LOOPBACK, loopback);
            intent.putExtra(Constant.EXTRA_VIDEO_CALL, videoCallEnabled);
            intent.putExtra(Constant.EXTRA_SCREENCAPTURE, false);
            intent.putExtra(Constant.EXTRA_CAMERA2, useCamera2);
            intent.putExtra(Constant.EXTRA_VIDEO_WIDTH, videoWidth);
            intent.putExtra(Constant.EXTRA_VIDEO_HEIGHT, videoHeight);
            intent.putExtra(Constant.EXTRA_VIDEO_FPS, cameraFps);
            intent.putExtra(Constant.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, false);
            intent.putExtra(Constant.EXTRA_VIDEO_BITRATE, videoStartBitrate);
            intent.putExtra(Constant.EXTRA_VIDEOCODEC, videoCodec);
            intent.putExtra(Constant.EXTRA_HWCODEC_ENABLED, true);
            intent.putExtra(Constant.EXTRA_CAPTURETOTEXTURE_ENABLED, true);
            intent.putExtra(Constant.EXTRA_FLEXFEC_ENABLED, true);
            intent.putExtra(Constant.EXTRA_NOAUDIOPROCESSING_ENABLED, false);
            intent.putExtra(Constant.EXTRA_AECDUMP_ENABLED, false);
            intent.putExtra(Constant.EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, false);
            intent.putExtra(Constant.EXTRA_OPENSLES_ENABLED, false);
            intent.putExtra(Constant.EXTRA_DISABLE_BUILT_IN_AEC, false);
            intent.putExtra(Constant.EXTRA_DISABLE_BUILT_IN_AGC, false);
            intent.putExtra(Constant.EXTRA_DISABLE_BUILT_IN_NS, false);
            intent.putExtra(Constant.EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false);
            intent.putExtra(Constant.EXTRA_AUDIO_BITRATE, audioStartBitrate);
            intent.putExtra(Constant.EXTRA_AUDIOCODEC, audioCodec);
            intent.putExtra(Constant.EXTRA_DISPLAY_HUD, false);
            intent.putExtra(Constant.EXTRA_TRACING, false);
            intent.putExtra(Constant.EXTRA_ENABLE_RTCEVENTLOG, false);
            intent.putExtra(Constant.EXTRA_CMDLINE, commandLineRun);
            intent.putExtra(Constant.EXTRA_RUNTIME, runTimeMs);
            intent.putExtra(Constant.EXTRA_DATA_CHANNEL_ENABLED, dataChannelEnabled);

            if (dataChannelEnabled) {
                intent.putExtra(Constant.EXTRA_ORDERED, true);
                intent.putExtra(Constant.EXTRA_MAX_RETRANSMITS_MS, -1);
                intent.putExtra(Constant.EXTRA_MAX_RETRANSMITS, -1);
                intent.putExtra(Constant.EXTRA_PROTOCOL, protocol);
                intent.putExtra(Constant.EXTRA_NEGOTIATED, false);
                intent.putExtra(Constant.EXTRA_ID, -1);
            }

            if (useValuesFromIntent) {
                if (getIntent().hasExtra(Constant.EXTRA_VIDEO_FILE_AS_CAMERA)) {
                    String videoFileAsCamera =
                            getIntent().getStringExtra(Constant.EXTRA_VIDEO_FILE_AS_CAMERA);
                    intent.putExtra(Constant.EXTRA_VIDEO_FILE_AS_CAMERA, videoFileAsCamera);
                }

                if (getIntent().hasExtra(Constant.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE)) {
                    String saveRemoteVideoToFile =
                            getIntent().getStringExtra(Constant.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE);
                    intent.putExtra(Constant.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE, saveRemoteVideoToFile);
                }

                if (getIntent().hasExtra(Constant.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH)) {
                    int videoOutWidth =
                            getIntent().getIntExtra(Constant.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0);
                    intent.putExtra(Constant.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, videoOutWidth);
                }

                if (getIntent().hasExtra(Constant.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT)) {
                    int videoOutHeight =
                            getIntent().getIntExtra(Constant.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0);
                    intent.putExtra(Constant.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, videoOutHeight);
                }
            }

            startActivityForResult(intent, CONNECTION_REQUEST);
        }
    }

    private boolean validateUrl(String url) {
        if (URLUtil.isHttpsUrl(url) || URLUtil.isHttpUrl(url)) {
            return true;
        }
        return false;
    }
}