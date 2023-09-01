package servent.handler;

import app.AppConfig;
import app.CausalShare;
import app.ServentInfo;
import servent.message.Message;
import servent.message.util.MessageUtil;

import java.util.Set;

public class CausalHandler implements MessageHandler {
    private final Message clientMessage;
    private final Set<Message> receivedBroadcasts;
    private final Object pendingMessagesLock;

    public CausalHandler(Message clientMessage,Set<Message> receivedBroadcasts, Object pendingMessagesLock) {
        this.clientMessage = clientMessage;
        this.receivedBroadcasts = receivedBroadcasts;
        this.pendingMessagesLock = pendingMessagesLock;
    }

    @Override
    public void run() {
        ServentInfo senderInfo = clientMessage.getOriginalSenderInfo();

        if (senderInfo.getId() == AppConfig.myServentInfo.getId()) {
            AppConfig.timestampedStandardPrint("Got own message back. No rebroadcast.");
        } else {
            synchronized (pendingMessagesLock) {
                boolean put = receivedBroadcasts.add(clientMessage);

                if (put) {
                    CausalShare.addPendingMessage(clientMessage);
                    CausalShare.checkPendingMessages();

                    if (!AppConfig.IS_CLIQUE) {
                        AppConfig.timestampedStandardPrint("Rebroadcasting... " + receivedBroadcasts.size());

                        for (Integer neighbor : AppConfig.myServentInfo.getNeighbors()) {
                            MessageUtil.sendMessage(clientMessage.changeReceiver(neighbor));
                        }

                    }
                } else {
                    AppConfig.timestampedStandardPrint("Already had this. No rebroadcast.");
                }
            }
        }
    }
}
