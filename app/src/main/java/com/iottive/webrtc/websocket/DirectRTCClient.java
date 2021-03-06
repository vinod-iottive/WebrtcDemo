package com.iottive.webrtc.websocket;

import android.util.Log;

import androidx.annotation.Nullable;

import com.iottive.webrtc.websocket.apprtc.AppRTCClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DirectRTCClient implements AppRTCClient, TCPChannelClient.TCPChannelEvents {
    private static final String TAG = "DirectRTCClient";
    private static final int DEFAULT_PORT = 8888;

    public static final Pattern IP_PATTERN = Pattern.compile("("
            // IPv4
            + "((\\d+\\.){3}\\d+)|"
            // IPv6
            + "\\[((([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4})?::"
            + "(([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4})?)\\]|"
            + "\\[(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4})\\]|"
            // IPv6 without []
            + "((([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4})?::(([0-9a-fA-F]{1,4}:)*[0-9a-fA-F]{1,4})?)|"
            + "(([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4})|"
            // Literals
            + "localhost"
            + ")"
            // Optional port number
            + "(:(\\d+))?");

    private final ExecutorService executor;
    private final SignalingEvents events;
    @Nullable
    private TCPChannelClient tcpClient;
    private RoomConnectionParameters connectionParameters;

    private enum ConnectionState {NEW, CONNECTED, CLOSED, ERROR}

    private ConnectionState roomState;

    public DirectRTCClient(SignalingEvents events) {
        this.events = events;

        executor = Executors.newSingleThreadExecutor();
        roomState = ConnectionState.NEW;
    }

    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        this.connectionParameters = connectionParameters;

        if (connectionParameters.loopback) {
            reportError("Loopback connections aren't supported by DirectRTCClient.");
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                connectToRoomInternal();
            }
        });
    }

    @Override
    public void disconnectFromRoom() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                disconnectFromRoomInternal();
            }
        });
    }

    private void connectToRoomInternal() {
        this.roomState = ConnectionState.NEW;

        String endpoint = connectionParameters.roomId;

        Matcher matcher = IP_PATTERN.matcher(endpoint);
        if (!matcher.matches()) {
            reportError("roomId must match IP_PATTERN for DirectRTCClient.");
            return;
        }

        String ip = matcher.group(1);
        String portStr = matcher.group(matcher.groupCount());
        int port;

        if (portStr != null) {
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException e) {
                reportError("Invalid port number: " + portStr);
                return;
            }
        } else {
            port = DEFAULT_PORT;
        }

        tcpClient = new TCPChannelClient(executor, this, ip, port);
    }

    private void disconnectFromRoomInternal() {
        roomState = ConnectionState.CLOSED;

        if (tcpClient != null) {
            tcpClient.disconnect();
            tcpClient = null;
        }
        executor.shutdown();
    }

    @Override
    public void sendOfferSdp(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending offer SDP in non connected state.");
                    return;
                }
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "offer");
                sendMessage(json.toString());
            }
        });
    }

    @Override
    public void sendAnswerSdp(final SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "sdp", sdp.description);
                jsonPut(json, "type", "answer");
                sendMessage(json.toString());
            }
        });
    }

    @Override
    public void sendLocalIceCandidate(final IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "candidate");
                jsonPut(json, "label", candidate.sdpMLineIndex);
                jsonPut(json, "id", candidate.sdpMid);
                jsonPut(json, "candidate", candidate.sdp);

                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending ICE candidate in non connected state.");
                    return;
                }
                sendMessage(json.toString());
            }
        });
    }

    @Override
    public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                JSONObject json = new JSONObject();
                jsonPut(json, "type", "remove-candidates");
                JSONArray jsonArray = new JSONArray();
                for (final IceCandidate candidate : candidates) {
                    jsonArray.put(toJsonCandidate(candidate));
                }
                jsonPut(json, "candidates", jsonArray);

                if (roomState != ConnectionState.CONNECTED) {
                    reportError("Sending ICE candidate removals in non connected state.");
                    return;
                }
                sendMessage(json.toString());
            }
        });
    }


    @Override
    public void onTCPConnected(boolean isServer) {
        if (isServer) {
            roomState = ConnectionState.CONNECTED;

            SignalingParameters parameters = new SignalingParameters(
                    new ArrayList<>(), isServer, null, null, null, null, null);
            events.onConnectedToRoom(parameters);
        }
    }

    @Override
    public void onTCPMessage(String msg) {
        try {
            JSONObject json = new JSONObject(msg);
            String type = json.optString("type");
            if (type.equals("candidate")) {
                events.onRemoteIceCandidate(toJavaCandidate(json));
            } else if (type.equals("remove-candidates")) {
                JSONArray candidateArray = json.getJSONArray("candidates");
                IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
                for (int i = 0; i < candidateArray.length(); ++i) {
                    candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
                }
                events.onRemoteIceCandidatesRemoved(candidates);
            } else if (type.equals("answer")) {
                SessionDescription sdp = new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));
                events.onRemoteDescription(sdp);
            } else if (type.equals("offer")) {
                SessionDescription sdp = new SessionDescription(
                        SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));

                SignalingParameters parameters = new SignalingParameters(
                        new ArrayList<>(), false, null, null, null, sdp, null);
                roomState = ConnectionState.CONNECTED;
                events.onConnectedToRoom(parameters);
            } else {
                reportError("Unexpected TCP message: " + msg);
            }
        } catch (JSONException e) {
            reportError("TCP message JSON parsing error: " + e.toString());
        }
    }

    @Override
    public void onTCPError(String description) {
        reportError("TCP connection error: " + description);
    }

    @Override
    public void onTCPClose() {
        events.onChannelClose();
    }

    private void reportError(final String errorMessage) {
        Log.e(TAG, errorMessage);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                if (roomState != ConnectionState.ERROR) {
                    roomState = ConnectionState.ERROR;
                    events.onChannelError(errorMessage);
                }
            }
        });
    }

    private void sendMessage(final String message) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                tcpClient.send(message);
            }
        });
    }

    private static void jsonPut(JSONObject json, String key, Object value) {
        try {
            json.put(key, value);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private static JSONObject toJsonCandidate(final IceCandidate candidate) {
        JSONObject json = new JSONObject();
        jsonPut(json, "label", candidate.sdpMLineIndex);
        jsonPut(json, "id", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);
        return json;
    }

    private static IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
        return new IceCandidate(
                json.getString("id"), json.getInt("label"), json.getString("candidate"));
    }
}
