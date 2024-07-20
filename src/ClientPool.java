import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientPool {
    public static final int NUM_CLIENTS = 16;
    private static LinkedBlockingQueue<KeyValueService.Client> clientPool;
    private static String backupHost;
    private static int backupPort;

    public ClientPool(String host, int port) {
        // Initialize and close old transports
        // closeTransports(); // Close method is refactored for better readability

        this.clientPool = new LinkedBlockingQueue<>();
        this.backupHost = host;
        this.backupPort = port;

        // Initialize clients and add them to the pool
        for (int i = 0; i < NUM_CLIENTS; ++i) {
            try {
                clientPool.put(makeClient());
            }
            catch (Exception e) {
                // System.out.println("Error initializing client: " + e.getMessage());
            }
        }
    }

    static public KeyValueService.Client makeClient() {
        try {
            TSocket sock = new TSocket(backupHost, backupPort);
            TTransport transport = new TFramedTransport(sock);
            // TSocket sock = new TSocket(backupHost, backupPort);
            // int batchSize = 128;
            // int itemSize = 1024;
            // int frameSize = batchSize * itemSize * 3; // Custom frame size based on expected data volume

            // TTransport transport = new TFramedTransport(sock, frameSize);

            // Keep track of transport to close in the future
            transport.open();

            TProtocol protocol = new TBinaryProtocol(transport);
            return new KeyValueService.Client(protocol);
        } catch (Exception e) {
            // System.out.println("Failed to open transport");
            return null;
        }
    }

    static public KeyValueService.Client getClient() {
        try{
            return clientPool.poll(100L, TimeUnit.SECONDS);
           
        }
        catch (Exception e) {
            // System.out.println("Failed to get client from queue");
            return null;
        }
    }

    static public void addClientToQueue(KeyValueService.Client client) {
        try {
            if (client != null) {
                clientPool.put(client);
            } 
            // else {
            //     System.out.println("Attempted to add a null client to the pool");
            // }
        } catch (Exception e) {
            // System.out.println("Failed to add client back to queue");
        }
    }
}