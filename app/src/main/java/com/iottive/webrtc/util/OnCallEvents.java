package com.iottive.webrtc.util;

import org.webrtc.RendererCommon;

public interface OnCallEvents {
    void onCallHangUp();

    void onCameraSwitch();

    void onVideoScalingSwitch(RendererCommon.ScalingType scalingType);

    boolean onToggleMic();
}
