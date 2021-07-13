
package com.iottive.webrtc.websocket;

import android.util.Log;

import androidx.annotation.Nullable;

import org.webrtc.ThreadUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;


public class TCPChannelClient {
    private static final String TAG = "TCPChannelClient";

    private final ExecutorService executor;
    private final ThreadUtils.ThreadChecker executorThreadCheck;
    private final TCPChannelEvents eventListener;
    private TCPSocket socket;

    public interface TCPChannelEvents {
        void onTCPConnected(boolean server);

        void onTCPMessage(String message);

        void onTCPError(String description);

        void onTCPClose();
    }

    public TCPChannelClient(
            ExecutorService executor, TCPChannelEvents eventListener, String ip, int port) {
        this.executor = executor;
        executorThreadCheck = new ThreadUtils.ThreadChecker();
        executorThreadCheck.detachThread();
        this.eventListener = eventListener;

        InetAddress address;
        try {
            address = InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            reportError("Invalid IP address.");
            return;
        }

        if (address.isAnyLocalAddress()) {
            socket = new TCPSocketServer(address, port);
        } else {
            socket = new TCPSocketClient(address, port);
        }

        socket.start();
    }

    public void disconnect() {
        executorThreadCheck.checkIsOnValidThread();

        socket.disconnect();
    }

    public void send(String message) {
        executorThreadCheck.checkIsOnValidThread();

        socket.send(message);
    }

    private void reportError(final String message) {
        Log.e(TAG, "TCP Error: " + message);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                eventListener.onTCPError(message);
            }
        });
    }

    private abstract class TCPSocket extends Thread {
        protected final Object rawSocketLock;
        @Nullable
        private PrintWriter out;
        @Nullable
        private Socket rawSocket;


        @Nullable
        public abstract Socket connect();

        public abstract boolean isServer();

        TCPSocket() {
            rawSocketLock = new Object();
        }

        @Override
        public void run() {
            Log.d(TAG, "Listening thread started...");

            Socket tempSocket = connect();
            BufferedReader in;

            Log.d(TAG, "TCP connection established.");

            synchronized (rawSocketLock) {
                if (rawSocket != null) {
                    Log.e(TAG, "Socket already existed and will be replaced.");
                }

                rawSocket = tempSocket;

                if (rawSocket == null) {
                    return;
                }

                try {
                    out = new PrintWriter(
                            new OutputStreamWriter(rawSocket.getOutputStream(), Charset.forName("UTF-8")), true);
                    in = new BufferedReader(
                            new InputStreamReader(rawSocket.getInputStream(), Charset.forName("UTF-8")));
                } catch (IOException e) {
                    reportError("Failed to open IO on rawSocket: " + e.getMessage());
                    return;
                }
            }

            Log.v(TAG, "Execute onTCPConnected");
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    Log.v(TAG, "Run onTCPConnected");
                    eventListener.onTCPConnected(isServer());
                }
            });

            while (true) {
                final String message;
                try {
                    message = in.readLine();
                } catch (IOException e) {
                    synchronized (rawSocketLock) {
                        if (rawSocket == null) {
                            break;
                        }
                    }

                    reportError("Failed to read from rawSocket: " + e.getMessage());
                    break;
                }

                if (message == null) {
                    break;
                }

                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        Log.v(TAG, "Receive: " + message);
                        eventListener.onTCPMessage(message);
                    }
                });
            }

            Log.d(TAG, "Receiving thread exiting...");

            disconnect();
        }

        public void disconnect() {
            try {
                synchronized (rawSocketLock) {
                    if (rawSocket != null) {
                        rawSocket.close();
                        rawSocket = null;
                        out = null;

                        executor.execute(new Runnable() {
                            @Override
                            public void run() {
                                eventListener.onTCPClose();
                            }
                        });
                    }
                }
            } catch (IOException e) {
                reportError("Failed to close rawSocket: " + e.getMessage());
            }
        }

        public void send(String message) {
            Log.v(TAG, "Send: " + message);

            synchronized (rawSocketLock) {
                if (out == null) {
                    reportError("Sending data on closed socket.");
                    return;
                }

                out.write(message + "\n");
                out.flush();
            }
        }
    }

    private class TCPSocketServer extends TCPSocket {
        @Nullable
        private ServerSocket serverSocket;

        final private InetAddress address;
        final private int port;

        public TCPSocketServer(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }

        @Nullable
        @Override
        public Socket connect() {
            Log.d(TAG, "Listening on [" + address.getHostAddress() + "]:" + Integer.toString(port));

            final ServerSocket tempSocket;
            try {
                tempSocket = new ServerSocket(port, 0, address);
            } catch (IOException e) {
                reportError("Failed to create server socket: " + e.getMessage());
                return null;
            }

            synchronized (rawSocketLock) {
                if (serverSocket != null) {
                    Log.e(TAG, "Server rawSocket was already listening and new will be opened.");
                }

                serverSocket = tempSocket;
            }

            try {
                return tempSocket.accept();
            } catch (IOException e) {
                reportError("Failed to receive connection: " + e.getMessage());
                return null;
            }
        }

        @Override
        public void disconnect() {
            try {
                synchronized (rawSocketLock) {
                    if (serverSocket != null) {
                        serverSocket.close();
                        serverSocket = null;
                    }
                }
            } catch (IOException e) {
                reportError("Failed to close server socket: " + e.getMessage());
            }

            super.disconnect();
        }

        @Override
        public boolean isServer() {
            return true;
        }
    }

    private class TCPSocketClient extends TCPSocket {
        final private InetAddress address;
        final private int port;

        public TCPSocketClient(InetAddress address, int port) {
            this.address = address;
            this.port = port;
        }

        @Nullable
        @Override
        public Socket connect() {
            Log.d(TAG, "Connecting to [" + address.getHostAddress() + "]:" + Integer.toString(port));

            try {
                return new Socket(address, port);
            } catch (IOException e) {
                reportError("Failed to connect: " + e.getMessage());
                return null;
            }
        }

        @Override
        public boolean isServer() {
            return false;
        }
    }
}
