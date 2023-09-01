package cli.command;

import app.AppConfig;
import app.CausalShare;
import app.ServentInfo;

import app.snapshot_bitcake.AbManager;
import app.snapshot_bitcake.SnapshotCollector;
import servent.message.Message;
import servent.message.TransactionMessage;
import servent.message.util.MessageUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TransactionBurstCommand implements CLICommand {

    private static final int TRANSACTION_COUNT = 5;
    private static final int BURST_WORKERS = 5;
    private static final int MAX_TRANSFER_AMOUNT = 5;

    private final SnapshotCollector snapshotCollector;

    private final Object lock = new Object();

    public TransactionBurstCommand(SnapshotCollector snapshotCollector) {
        this.snapshotCollector = snapshotCollector;
    }

    private class TransactionBurstWorker implements Runnable {

        @Override
        public void run() {
            for (int i = 0; i < TRANSACTION_COUNT; i++) {
                ServentInfo receiverInfo = AppConfig.getInfoById((int) (Math.random() * AppConfig.getServentCount()));

                while (receiverInfo.getId() == AppConfig.myServentInfo.getId()) {
                    receiverInfo = AppConfig.getInfoById((int) (Math.random() * AppConfig.getServentCount()));
                }

                int amount = 1 + (int) (Math.random() * MAX_TRANSFER_AMOUNT);


                Message transactionMessage;
                synchronized (lock) {
                    Map<Integer, Integer> vectorClock = new ConcurrentHashMap<>(CausalShare.getVectorClock());

                    transactionMessage = new TransactionMessage(
                            AppConfig.myServentInfo,
                            receiverInfo,
                            null,
                            vectorClock,
                            amount,
                            snapshotCollector.getBitcakeManager()
                    );

                    transactionMessage.sendEffect();
                    CausalShare.incrementClockAndCheck(transactionMessage);
                    CausalShare.addSendTransaction(transactionMessage);
                }

                for (int neighbor : AppConfig.myServentInfo.getNeighbors()) {
                    MessageUtil.sendMessage(transactionMessage.changeReceiver(neighbor));
                }

            }
        }
    }

    @Override
    public String commandName() {
        return "transaction_burst";
    }

    @Override
    public void execute(String args) {
        for (int i = 0; i < BURST_WORKERS; i++) {
            Thread t = new Thread(new TransactionBurstWorker());

            t.start();
        }
    }


}
