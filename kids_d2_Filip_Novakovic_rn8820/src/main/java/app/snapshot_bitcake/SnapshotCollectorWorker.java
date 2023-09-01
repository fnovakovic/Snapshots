package app.snapshot_bitcake;

import app.AppConfig;
import app.CausalShare;
import servent.message.Message;
import servent.message.snapshot.AbAskAmountMessage;
import servent.message.util.MessageUtil;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main snapshot collector class. Has support for Naive, Chandy-Lamport
 * and Lai-Yang snapshot algorithms.
 *
 * @author bmilojkovic
 *
 */
public class SnapshotCollectorWorker implements SnapshotCollector {

    private volatile boolean working = true;

    private final AtomicBoolean collecting = new AtomicBoolean(false);

    private final Map<String, Object[]> finalCollectedAbResults = new ConcurrentHashMap<>();

    private SnapshotType snapshotType;

    private BitcakeManager bitcakeManager;

    public SnapshotCollectorWorker(SnapshotType snapshotType) {
        this.snapshotType = snapshotType;

        switch (snapshotType) {
            case ab -> bitcakeManager = new AbManager();
            case NONE -> {
                AppConfig.timestampedErrorPrint("Making snapshot collector without specifying type. Exiting...");
                System.exit(0);
            }
        }
    }

    @Override
    public BitcakeManager getBitcakeManager() {
        return bitcakeManager;
    }

    @Override
    public void run() {
        while(working) {
            while (!collecting.get()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (!working) {
                    return;
                }
            }

            Map<Integer, Integer> clock;
            Message askMessage;

            //1 send asks
            switch (snapshotType) {
                case ab:
                    clock = new ConcurrentHashMap<>(CausalShare.getVectorClock());

                    askMessage = new AbAskAmountMessage(
                            AppConfig.myServentInfo,
                            null,
                            null,
                            clock
                    );

                    for (Integer neighbor : AppConfig.myServentInfo.getNeighbors()) {
                        MessageUtil.sendMessage(askMessage.changeReceiver(neighbor));
                    }

                    addInfoInFinalCollectedAbResults(
                            "node"+AppConfig.myServentInfo.getId(),
                            bitcakeManager.getCurrentBitcakeAmount(),
                            CausalShare.getSendTransactions(),
                            CausalShare.getReceivedTransactions()
                    );

                    CausalShare.incrementClockAndCheck(askMessage);
                    break;

                case NONE:
                    break;
            }

            //2 wait for responses or finish
            boolean waiting = true;
            while (waiting) {
                switch (snapshotType) {
                    case ab:
                        if (finalCollectedAbResults.size() == AppConfig.getServentCount()) {
                            waiting = false;
                        }
                        break;

                    case NONE:
                        break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (!working) {
                    return;
                }
            }

            //print
            int sum;
            switch (snapshotType) {
                case ab:
                    sum = 0;
                    for (Entry<String, Object[]> item : finalCollectedAbResults.entrySet()) {

                        Object[] result = item.getValue();

                        int totalAmount = (int) result[0];


                        List<Message> sendTransactions = (List<Message>) result[1];

                        sum += totalAmount;
                        AppConfig.timestampedStandardPrint(
                                "Info for " + item.getKey() + " = " + totalAmount + " bitcake");

                        for (Message sendTransaction : sendTransactions) {
                            result = finalCollectedAbResults.get("node" + sendTransaction.getOriginalReceiverInfo().getId());
                            List<Message> receivedTransactions = (List<Message>) result[2];

                            boolean exist = false;
                            for (Message receivedTran : receivedTransactions) {
                                if (
                                                sendTransaction.getOriginalSenderInfo().getId() == receivedTran.getOriginalSenderInfo().getId() &&
                                                sendTransaction.getOriginalReceiverInfo().getId() == receivedTran.getOriginalReceiverInfo().getId() &&
                                                        sendTransaction.getMessageId() == receivedTran.getMessageId()
                                ) {
                                    exist = true;
                                    break;
                                }
                            }

                            if (!exist) {
                                AppConfig.timestampedStandardPrint(
                                        "Info for unprocessed transaction: " + sendTransaction.getMessageText()  + " bitcake,sender:  " +
                                        sendTransaction.getOriginalSenderInfo() + " ,reciever: " + sendTransaction.getOriginalReceiverInfo());

                                int amountNumber = Integer.parseInt(sendTransaction.getMessageText());

                                sum += amountNumber;
                            }
                        }
                    }

                    AppConfig.timestampedStandardPrint("System bitcake count: " + sum);

                    finalCollectedAbResults.clear();
                    break;

                case NONE:
                    break;
            }
            collecting.set(false);
        }

    }

      @Override
    public void addInfoInFinalCollectedAbResults(String snapshot, int amount,
                                                 List<Message> sendTransactions, List<Message> receivedTransactions) {

        List<Message> sendTransactionss = new CopyOnWriteArrayList<>(sendTransactions);
        List<Message> recTransactions = new CopyOnWriteArrayList<>(receivedTransactions);

        Object[] res = new Object[]{
                amount,
                sendTransactionss,
                recTransactions
        };

        finalCollectedAbResults.put(snapshot, res);
    }

    @Override
    public void startCollecting() {
        boolean oldValue = this.collecting.getAndSet(true);

        if (oldValue) {
            AppConfig.timestampedErrorPrint("Tried to start collecting before finished with previous.");
        }
    }

    @Override
    public void stop() {
        working = false;
    }

}
