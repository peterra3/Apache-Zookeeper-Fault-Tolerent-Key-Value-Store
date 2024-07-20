import java.util.*;
import java.util.concurrent.*;

import org.apache.thrift.*;
import org.apache.thrift.server.*;
import org.apache.thrift.transport.*;
import org.apache.thrift.protocol.*;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.*;

import org.apache.curator.*;
import org.apache.curator.retry.*;
import org.apache.curator.framework.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.*;

public class KeyValueHandler implements KeyValueService.Iface {
    private Map<String, String> myMap;
    private ConcurrentHashMap<String, Integer> timestamps;
    private AtomicInteger time;
    private CuratorFramework curClient;
    private String zkNode;
    private String host;
    private ROLE role = ROLE.UNDEFINED;
    private int port;
    private boolean isPrimary;
    private boolean isAlone = true;
    private static final int MAX_MAP_SIZE = 100000;
    private ClientPool clientPool;

    public void setAlone(boolean alone) {
        isAlone = alone;
    }

    enum ROLE {
        PRIMARY,
        BACKUP,
        UNDEFINED
    }

    public void setRole(ROLE newRole) {
        role = newRole;
    }

    public ROLE getRole() {
        return role;
    }

    public KeyValueHandler(String host, int port, CuratorFramework curClient, String zkNode) {
        this.host = host;
        this.port = port;
        this.curClient = curClient;
        this.zkNode = zkNode;
        this.myMap = new ConcurrentHashMap<String, String>();    
        this.timestamps = new ConcurrentHashMap<String, Integer>();    
        this.time = new AtomicInteger(0);

    }

    // get request function
    public String get(String key) throws org.apache.thrift.TException {
        String ret = myMap.get(key);
        return ret == null ? "" : ret;
    }

    // logic will change depending on if server is primary or secondary
    public void put(String key, String value) throws org.apache.thrift.TException {
        myMap.put(key, value);
        if (role.equals(ROLE.PRIMARY) && !isAlone) {
            // System.out.println("I am primary and will propagate call to backup");
            propagateToBackup(key, value);
        }
    } 

    public void checkPutOrder(String key, String value, int requestTimeStamp) {
        // Implementation needed here

        try{
            if (timestamps.containsKey(key))
            {
                int itemAddedTime = timestamps.get(key);
                if (requestTimeStamp >= itemAddedTime)
                {
                    timestamps.put(key,requestTimeStamp);
                    put(key,value);
                }
            }
            else
            {
                timestamps.put(key,requestTimeStamp);
                put(key,value);
            }
        }
        catch (Exception e)
        {
            // System.out.println("Issue in checkPutOrder: " + e.getMessage());
        }
    }

    public void updatePrimaryStatus(List<String> children) {
        try {
            // curClient.sync();  // Synchronize client state with ZooKeeper server
            if (children.isEmpty()) {  // Check if there are no children
                // System.out.println("No primary found");
                role = ROLE.UNDEFINED;  // No children means no role can be determined
                isAlone = true;
                return;
            }

            Collections.sort(children);  // Sort to find the smallest lexicographical order
            String primaryPath = zkNode + "/" + children.get(0);  // Path to the presumed primary node
            byte[] data = curClient.getData().forPath(primaryPath);  // Get data from the primary node
            String strData = new String(data);
            String[] primary = strData.split(":");  // Assume data is in "host:port" format

            // System.out.println("Found primary: " + strData);

            isPrimary = host.equals(primary[0]) && port == Integer.parseInt(primary[1]);  // Check if this node is the primary
            role = isPrimary ? ROLE.PRIMARY : ROLE.BACKUP;
            isAlone = children.size() <= 1;

            // System.out.println("Role: " + role);
            // System.out.println("Is alone: " + isAlone);
        } catch (Exception e) {
            // System.err.println("Failed to determine if it is the primary due to: " + e.getMessage());
            // e.printStackTrace();
            role = ROLE.UNDEFINED;
            isAlone = true;
        }
    }

    // Send RPC calls to the backup node and wait for acknowledgment
    private void propagateToBackup(String key, String value) {
        KeyValueService.Client backupClient = null;
        try {
            // curClient.sync();  // Synchronize client state with ZooKeeper server
            // List<String> children = curClient.getChildren().forPath(zkNode);  // Retrieve child nodes

            // if (children.size() <= 1) {  // Check if backup node exists
            //     return;
            // }

            backupClient = this.clientPool.getClient();
            
            backupClient.checkPutOrder(key, value, this.time.getAndIncrement());  // Remote call to backup node
            
        } catch (Exception e) {
            // System.err.println("Failed to propagate: " + e.getMessage());
            // e.printStackTrace();
        } finally {
            if (backupClient != null) {
                this.clientPool.addClientToQueue(backupClient);
            }
        }
    }

    public void generateClients(String backupHost, int backupPort) throws Exception, InterruptedException {
        this.clientPool = new ClientPool(backupHost,backupPort);
    }

    public void transferMap() throws org.apache.thrift.TException, InterruptedException {
            int requestTimeStamp = this.time.getAndIncrement();
            List<String> keys = new ArrayList<>(myMap.keySet());
            List<String> values = new ArrayList<>(keys.size());
            
            for (String key : keys) {
                values.add(myMap.get(key));
            }

            int totalKeys = keys.size();
            int index = 0;

            while (index < totalKeys) {
                int end = Math.min(index + MAX_MAP_SIZE, totalKeys);

                List<String> chunkKeys = keys.subList(index, end);
                List<String> chunkValues = values.subList(index, end);

                Thread thread = new Thread(() -> {
                    KeyValueService.Client backupClient = null;
                    try {
                        backupClient = this.clientPool.getClient();
                        backupClient.setMyMap(chunkKeys, chunkValues, requestTimeStamp);
                    } 
                    catch (Exception e) {
                        // System.err.println("Failed to set map for chunk due to: " + e.getMessage());
                    }
                    finally {
                        if (backupClient != null) {
                            this.clientPool.addClientToQueue(backupClient);
                        }
                    }
                });
                // threads.add(thread);
                thread.start();

                index = end;  // Move to the next chunk
            }
        }

    // Getter for myMap
    public Map<String, String> getMyMap() {
        return myMap;
    }

    // Setter for myMap
    public void setMyMap(List<String> keys, List<String> values, int requestTimeStamp) {
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = values.get(i);
            if (!myMap.containsKey(key)) {
                timestamps.put(key,requestTimeStamp);
                myMap.put(key, value);
            }
        }
    }


    // Getter for isPrimary
    public boolean isPrimary() {
        return isPrimary;
    }

    // Setter for isPrimary
    public void setPrimary(boolean isPrimary) {
        this.isPrimary = isPrimary;
    }

    public String getHost() {
        return this.host;
    }

    public int getPort() {
        return this.port;
    }

    public HostPort getBackupHostAndPort(List<String> children) throws Exception  {
        int lastIndex = children.size()-1;
        String child = children.get(lastIndex);

        String childPath = zkNode + "/" + child;
        byte[] childData = curClient.getData().forPath(childPath);
        String childStrData = new String(childData);
        String[] childNode = childStrData.split(":"); // Assume data is in "host:port" format

        return new HostPort(childNode[0], Integer.parseInt(childNode[1]));
        
    }
}
