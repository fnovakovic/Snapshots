# Snapshots

Concurrent and distributed systems - homework 2

This project aims to implement a distributed system for creating snapshots in the Java programming language. This system supports the following functionalities:

Implementing a system with fully asynchronous non-FIFO communication between an arbitrary number of nodes in the system, where each node has its own port on which it accepts messages from its neighbors.

Enabling the creation of system snapshots from any node in the system. The implementation of Acharya-Badrinath snapshot algorithm is supported, and  algorithm is configured according to the settings in the configuration file.

Regular exchange of bitcakes between nodes based on user commands. The bitcake state in the system should be the result of the execution of the snapshot algorithm.

Introducing a random message delay to simulate network latency.

Nodes may only exchange messages if they are listed as neighbors in the configuration file.

In addition, the system is resistant to situations such as an attempt to create a snapshot on a node that already has an active snapshot, and concurrently running multiple snapshots on different nodes.

Each node generates dump messages that provide information about the flow of communication so that the history of the system's operation can be reconstructed.

The configuration file defines the number of nodes in the system, the ports on which the nodes listen, and the list of neighbors for each node.
