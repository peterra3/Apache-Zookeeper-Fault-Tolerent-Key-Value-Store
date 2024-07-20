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
import java.util.concurrent.locks.ReentrantLock;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.zookeeper.Watcher.Event.EventType;

public class Watcher implements CuratorWatcher  {
    private String zkNode;
    private KeyValueHandler handler;
    private CuratorFramework curClient;

    public Watcher(KeyValueHandler handler, CuratorFramework curClient, String zkNode) {
        this.handler = handler;
        this.curClient = curClient;
        this.zkNode = zkNode;
    }

    /*
     * 
     * Each time the children of this node change (addition/deletion of children), the process method will be called.
     */
    synchronized public void process(WatchedEvent event) {
        // System.out.println("WatchedEvent received: " + event);

        try{
            //this gets the number of children from the znode
            // curClient.sync();
            List<String> children = curClient.getChildren().usingWatcher(this).forPath(zkNode);
            Collections.sort(children);

            // String address = handler.getHost() + ":" + handler.getPort();

            // Check if lowest node shares same addr


            //updates the status of the server node, it will either get primary or secondary
            handler.updatePrimaryStatus(children);

            

            
            //there is children
            if(children.size() > 1){
                //set alone flag to false now
                handler.setAlone(false);

                HostPort hostPort = handler.getBackupHostAndPort(children);
                String backupHost = hostPort.getHost();
                int backupPort = hostPort.getPort();

                if (children.size() == 2)
                {
                    if(handler.getRole().equals(KeyValueHandler.ROLE.PRIMARY)){
                        // if (children.size() == 3) {
                        //     curClient.delete().forPath(zkNode + "/" + children.get(0));
                        // }


                        //we have to transfer the whole map
                        handler.generateClients(backupHost,backupPort);
                        handler.transferMap();
                    }
                }

                // Manually kill zknodes
                else if (children.size() == 3) {

                    if(handler.getRole().equals(KeyValueHandler.ROLE.PRIMARY)) {
                        curClient.delete().forPath(zkNode + "/" + children.get(1));
                    }
                    else if (!handler.getHost().equals(backupHost) || handler.getPort() != backupPort )
                    {
                        // Secondary
                        curClient.delete().forPath(zkNode + "/" + children.get(0));
                    }  
                }
    

                //get the address of the new node and then forward data to this 

            }
            //there is no children
            else{
                handler.setAlone(true);
            }
        } catch (Exception e) {
            // System.out.println("cant determine the main node: " + e);
        }

    }

    

}



