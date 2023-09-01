package servent;

import app.AppConfig;
import app.Cancellable;
import app.CausalShare;
import servent.handler.*;
import servent.message.Message;
import servent.message.util.MessageUtil;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleServentListener implements Runnable, Cancellable {

    private volatile boolean working = true;
    private final Set<Message> receivedBroadcasts = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Object pendingMessagesLock = new Object();

    public SimpleServentListener(){
    }

    /*
     * Thread pool for executing the handlers. Each client will get it's own handler thread.
     */
    private final ExecutorService threadPool = Executors.newWorkStealingPool();

    @Override
    public void run() {
        ServerSocket listenerSocket = null;
        try {

            listenerSocket = new ServerSocket(AppConfig.myServentInfo.getListenerPort(), 100);
            listenerSocket.setSoTimeout(1000);
        } catch (IOException e) {
            AppConfig.timestampedErrorPrint("Couldn't open listener socket on: " + AppConfig.myServentInfo.getListenerPort());
            System.exit(0);
        }

        while (working) {
            try {
                Message clientMessage;
                Socket clientSocket = listenerSocket.accept();

                clientMessage = MessageUtil.readMessage(clientSocket);
                MessageHandler messageHandler = new CausalHandler(clientMessage, receivedBroadcasts, pendingMessagesLock);

                threadPool.submit(messageHandler);
            } catch (SocketTimeoutException timeoutEx) {
                //wait
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public void stop() {
        this.working = false;
    }

}
