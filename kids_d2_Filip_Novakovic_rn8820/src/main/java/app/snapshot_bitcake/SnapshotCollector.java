package app.snapshot_bitcake;

import app.Cancellable;
import servent.message.Message;

import java.util.List;

/**
 * Describes a snapshot collector. Made not-so-flexibly for readability.
 *
 * @author bmilojkovic
 *
 */
public interface SnapshotCollector extends Runnable, Cancellable {

    BitcakeManager getBitcakeManager();


    void addInfoInFinalCollectedAbResults(String snapshotSubject, int amount,
                                          List<Message> sendTransactions, List<Message> receivedTransactions);

    void startCollecting();

}