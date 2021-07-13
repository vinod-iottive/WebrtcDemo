package com.iottive.webrtc.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.iottive.webrtc.R;
import com.iottive.webrtc.databinding.FragmentCallBinding;
import com.iottive.webrtc.util.AppMethods;
import com.iottive.webrtc.util.Constant;
import com.iottive.webrtc.util.OnCallEvents;

import org.webrtc.RendererCommon.ScalingType;


public class CallFragment extends Fragment implements View.OnClickListener {
    private OnCallEvents callEvents;
    private ScalingType scalingType;
    private boolean videoCallEnabled = true;


    FragmentCallBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentCallBinding.inflate(inflater, container, false);
        View controlView = binding.getRoot();
        scalingType = ScalingType.SCALE_ASPECT_FILL;
        initUI();
        return controlView;
    }

    private void initUI() {
        binding.buttonCallToggleMic.setOnClickListener(this::onClick);
        binding.buttonCallDisconnect.setOnClickListener(this::onClick);
        binding.buttonCallSwitchCamera.setOnClickListener(this::onClick);
        binding.ivShare.setOnClickListener(this::onClick);
        binding.buttonCallScalingMode.setOnClickListener(this::onClick);
    }

    @Override
    public void onStart() {
        super.onStart();
        Bundle args = getArguments();
        if (args != null) {
            String contactName = args.getString(Constant.EXTRA_ROOMID);
            binding.contactNameCall.setText(contactName);
            videoCallEnabled = args.getBoolean(Constant.EXTRA_VIDEO_CALL, true);
            boolean captureSliderEnabled = videoCallEnabled && args.getBoolean(Constant.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, false);
        }
        if (!videoCallEnabled) {
            binding.buttonCallSwitchCamera.setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_call_disconnect:
                callEvents.onCallHangUp();
                break;
            case R.id.iv_share:
                AppMethods.shareText(getActivity(), getString(R.string.share_invitation), getString(R.string.you_can_join_a_video_call_meeting) + " " + binding.contactNameCall.getText().toString() + "\n" + getString(R.string._or_click_on_the_link_below_to_join_the_meeting) + " " + getString(R.string.pref_room_server_url_default) + "/r/" + binding.contactNameCall.getText().toString());
                break;
            case R.id.button_call_toggle_mic:
                boolean enabled = callEvents.onToggleMic();
                binding.buttonCallToggleMic.setAlpha(enabled ? 1.0f : 0.3f);
                break;
            case R.id.button_call_switch_camera:
                callEvents.onCameraSwitch();
                break;
            case R.id.button_call_scaling_mode:
                if (scalingType == ScalingType.SCALE_ASPECT_FILL) {
                    binding.buttonCallScalingMode.setBackgroundResource(R.drawable.ic_action_full_screen);
                    scalingType = ScalingType.SCALE_ASPECT_FIT;
                } else {
                    binding.buttonCallScalingMode.setBackgroundResource(R.drawable.ic_action_return_from_full_screen);
                    scalingType = ScalingType.SCALE_ASPECT_FILL;
                }
                callEvents.onVideoScalingSwitch(scalingType);
                break;

        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        callEvents = (OnCallEvents) activity;
    }
}
