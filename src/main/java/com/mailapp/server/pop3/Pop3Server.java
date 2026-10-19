package com.mailapp.server.pop3;

import com.mailapp.account.AccountManager;
import com.mailapp.model.MailServerConfig;
import com.mailapp.storage.MailStorage;
import com.mailapp.util.ProtocolLogger;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Local POP3 Server running on TCP ServerSocket.
 */
public class Pop3Server {

    private final MailServerConfig config;
    private final MailStorage mailStorage;
    private final AccountManager accountManager;

    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private Thread listenerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<Socket> activeSockets = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public Pop3Server(MailServerConfig config, MailStorage mailStorage, AccountManager accountManager) {
        this.config = config;
        this.mailStorage = mailStorage;
        this.accountManager = accountManager;
    }

    /**
     * Starts the POP3 server.
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        InetAddress bindAddr = InetAddress.getByName(config.getPop3Host());
        serverSocket = new ServerSocket(config.getPop3Port(), 50, bindAddr);
        running.set(true);
        threadPool = Executors.newCachedThreadPool();

        ProtocolLogger.log(ProtocolLogger.LogType.POP3_SERVER,
                "POP3 Server started on " + serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getLocalPort());

        listenerThread = new Thread(this::listenForConnections, "POP3-Listener-" + serverSocket.getLocalPort());
        listenerThread.start();
    }

    private void listenForConnections() {
        while (running.get() && !serverSocket.isClosed()) {
            try {
                Socket clientSocket = serverSocket.accept();
                activeSockets.add(clientSocket);
                ProtocolLogger.log(ProtocolLogger.LogType.POP3_SERVER,
                        "Accepted POP3 connection from " + clientSocket.getRemoteSocketAddress());

                threadPool.submit(() -> {
                    try {
                        new Pop3SessionHandler(clientSocket, config, mailStorage, accountManager).run();
                    } finally {
                        activeSockets.remove(clientSocket);
                    }
                });
            } catch (IOException e) {
                if (!running.get()) {
                    break;
                }
                ProtocolLogger.log(ProtocolLogger.LogType.POP3_SERVER, "Error accepting connection: " + e.getMessage());
            }
        }
    }

    /**
     * Stops the POP3 server safely.
     */
    public synchronized void stop() {
        if (!running.getAndSet(false)) {
            return;
        }

        ProtocolLogger.log(ProtocolLogger.LogType.POP3_SERVER, "Stopping POP3 Server...");

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        for (Socket s : activeSockets) {
            try {
                if (s != null && !s.isClosed()) {
                    s.close();
                }
            } catch (IOException ignored) {}
        }
        activeSockets.clear();

        if (threadPool != null) {
            threadPool.shutdownNow();
            try {
                threadPool.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }

        ProtocolLogger.log(ProtocolLogger.LogType.POP3_SERVER, "POP3 Server stopped.");
    }

    public boolean isRunning() {
        return running.get() && serverSocket != null && !serverSocket.isClosed();
    }

    public int getPort() {
        return serverSocket != null ? serverSocket.getLocalPort() : config.getPop3Port();
    }

    public String getHost() {
        return config.getPop3Host();
    }
}
