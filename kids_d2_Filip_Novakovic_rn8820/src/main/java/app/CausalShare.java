package app;

import app.snapshot_bitcake.SnapshotCollector;
import servent.handler.MessageHandler;
import servent.handler.NullHandler;
import servent.handler.TransactionHandler;
import servent.handler.snapshot.AbAskAmountHandler;
import servent.handler.snapshot.AbTellAmountHandler;
import servent.message.BasicMessage;
import servent.message.Message;

import java.util.*;
import java.util.concurrent.*;

/**
 * This class contains shared data for the Causal Broadcast implementation:
 * <ul>
 * <li> Vector clock for current instance
 * <li> Commited message list
 * <li> Pending queue
 * </ul>
 * As well as operations for working with all of the above.
 *
 * @author bmilojkovic
 *
 */
public class CausalShare {

    private static final Map<Integer, Integer> vectorClock = new ConcurrentHashMap<>();
    private static final Queue<Message> pendingMessages = new ConcurrentLinkedQueue<>();
    private static final Object pendingMessagesLock = new Object();
    private static SnapshotCollector snapshotCollector;
    private static final List<Message> sendTransactions = new CopyOnWriteArrayList<>();
    private static final List<Message> receivedTransactions = new CopyOnWriteArrayList<>();
    private static final Set<Message> receivedAbAsk = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private static final ExecutorService threadPool = Executors.newWorkStealingPool();

    public static void setSnapshotCollector(SnapshotCollector sc) {
        snapshotCollector = sc;
    }

    public static void addReceivedTransaction(Message receivedTransaction) {
        receivedTransactions.add(receivedTransaction);
    }

    public static List<Message> getReceivedTransactions() {
        return receivedTransactions;
    }

    public static void addSendTransaction(Message sendTransaction) {
        sendTransactions.add(sendTransaction);
    }

    public static List<Message> getSendTransactions() {
        return sendTransactions;
    }

    public static void initializeVectorClock(int serventCount) {
        for(int i = 0; i < serventCount; i++) {
            vectorClock.put(i, 0);
        }
    }

    public static void incrementClock(int serventId) {
        vectorClock.computeIfPresent(serventId, (key, oldValue) -> oldValue+1);
    }

    public static Map<Integer, Integer> getVectorClock() {
        return vectorClock;
    }

    public static void addPendingMessage(Message msg) {
        pendingMessages.add(msg);
    }

    public static void incrementClockAndCheck(Message newMessage) {
        AppConfig.timestampedStandardPrint("Incrementing clock and checking pending messages, on message = " + newMessage);
        incrementClock(newMessage.getOriginalSenderInfo().getId());
        checkPendingMessages();
    }

    private static boolean otherClockGreater(Map<Integer, Integer> clock1, Map<Integer, Integer> clock2) {
        if (clock1.size() != clock2.size()) {
            throw new IllegalArgumentException("Clocks are not same size how why");
        }

        for(int i = 0; i < clock1.size(); i++) {
            if (clock2.get(i) > clock1.get(i)) {
                return true;
            }
        }

        return false;
    }

    public static void checkPendingMessages() {
        boolean gotWork = true;

        while (gotWork) {
            gotWork = false;

            synchronized (pendingMessagesLock) {
                Iterator<Message> iterator = pendingMessages.iterator();

                Map<Integer, Integer> myVectorClock = getVectorClock();
                while (iterator.hasNext()) {
                    Message pendingMessage = iterator.next();

                    BasicMessage basicMessage = (BasicMessage)pendingMessage;

                    if (!otherClockGreater(myVectorClock, basicMessage.getSenderVectorClock())) {
                        gotWork = true;

                        AppConfig.timestampedStandardPrint("Incrementing clock on message = " + pendingMessage);
                        incrementClock(pendingMessage.getOriginalSenderInfo().getId());

                        MessageHandler messageHandler = new NullHandler(basicMessage);

                        boolean put;
                        switch (basicMessage.getMessageType()) {
                            case TRANSACTION:
                                if (basicMessage.getOriginalReceiverInfo().getId() == AppConfig.myServentInfo.getId()) {
                                    messageHandler = new TransactionHandler(basicMessage, snapshotCollector.getBitcakeManager());
                                }
                                break;
                            case Ab_ASK_AMOUNT:
                                put = receivedAbAsk.add(basicMessage);

                                if (put) {
                                    messageHandler = new AbAskAmountHandler(basicMessage, snapshotCollector);
                                }
                                break;
                            case Ab_TELL_AMOUNT:
                                if (basicMessage.getOriginalReceiverInfo().getId() == AppConfig.myServentInfo.getId()) {
                                    messageHandler = new AbTellAmountHandler(basicMessage, snapshotCollector);
                                }
                                break;
                        }

                        threadPool.submit(messageHandler);

                        iterator.remove();
                        break;
                    }
                }
            }
        }

    }
}
