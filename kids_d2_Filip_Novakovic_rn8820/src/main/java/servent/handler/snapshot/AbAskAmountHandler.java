package servent.handler.snapshot;

import app.AppConfig;
import app.CausalShare;
import app.snapshot_bitcake.BitcakeManager;
import app.snapshot_bitcake.SnapshotCollector;
import servent.handler.MessageHandler;
import servent.message.Message;
import servent.message.MessageType;
import servent.message.snapshot.AbTellAmountMessage;
import servent.message.util.MessageUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AbAskAmountHandler implements MessageHandler {

    private final Message clientMessage;
    private final SnapshotCollector snapshotCollector;

    public AbAskAmountHandler(Message clientMessage, SnapshotCollector snapshotCollector) {
        this.clientMessage = clientMessage;
        this.snapshotCollector = snapshotCollector;
    }

    @Override
    public void run() {
        if (clientMessage.getMessageType() == MessageType.Ab_ASK_AMOUNT) {
            BitcakeManager bitcakeManager = snapshotCollector.getBitcakeManager();
            int currentAmount = bitcakeManager.getCurrentBitcakeAmount();

            Map<Integer, Integer> vectorClock = new ConcurrentHashMap<>(CausalShare.getVectorClock());

            Message tellMessage = new AbTellAmountMessage(
                    AppConfig.myServentInfo, clientMessage.getOriginalSenderInfo(),
                    null, vectorClock, currentAmount,
                    CausalShare.getSendTransactions(),
                    CausalShare.getReceivedTransactions()
            );

            CausalShare.incrementClockAndCheck(tellMessage);

            for (int neighbor : AppConfig.myServentInfo.getNeighbors()) {
                MessageUtil.sendMessage(tellMessage.changeReceiver(neighbor));
            }

        } else {
            AppConfig.timestampedErrorPrint("Ask amount handler got: " + clientMessage);
        }

    }

}