import java.io.*;
import java.util.*;
import java.util.logging.Handler;

import org.apache.thrift.*;
import org.apache.thrift.server.*;
import org.apache.thrift.transport.*;
import org.apache.thrift.protocol.*;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.*;
import org.apache.curator.*;
import org.apache.curator.retry.*;
import org.apache.curator.framework.*;
import org.apache.curator.utils.*;

import org.apache.log4j.*;

public class StorageNode {
	static Logger log;

	public static void main(String[] args) throws Exception {
		// BasicConfigurator.configure();
		log = Logger.getLogger(StorageNode.class.getName());

		if (args.length != 4) {
			System.err.println("Usage: java StorageNode host port zkconnectstring zknode");
			System.exit(-1);
		}

		String host = args[0];
		String port = args[1];
		String zkconnectstring = args[2];
		String zknode = args[3];
		String nodePath = zknode + "/child-";

		String hostPort = host + ":" + port;
		
		//client to communicate with zookeeper parent
		CuratorFramework curClient = CuratorFrameworkFactory.builder()
				.connectString(zkconnectstring)
				.retryPolicy(new RetryNTimes(10, 1000))
				.connectionTimeoutMs(1000)
				.sessionTimeoutMs(10000)
				.build();
		//starting zookeeper communication client 
		curClient.start();
		
		//
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				curClient.close();
			}
		});

		KeyValueHandler handler = new KeyValueHandler(host, Integer.parseInt(port), curClient, zknode);
		
		// Add watcher for each handler
		Watcher watcher = new Watcher(handler, curClient, zknode);
		//make watcher here
		List<String> children = curClient.getChildren().usingWatcher(watcher).forPath(zknode);

		KeyValueService.Processor<KeyValueService.Iface> processor = new KeyValueService.Processor<>(handler);
		//socket used for commincations between backends
		TServerSocket socket = new TServerSocket(Integer.parseInt(port));
		//configuring server
		TThreadPoolServer.Args sargs = new TThreadPoolServer.Args(socket);
		sargs.protocolFactory(new TBinaryProtocol.Factory());
		sargs.transportFactory(new TFramedTransport.Factory());
		sargs.processorFactory(new TProcessorFactory(processor));
		sargs.maxWorkerThreads(64);
		//making server
		TServer server = new TThreadPoolServer(sargs);
		log.info("Launching server");
		//running server in new thread
		new Thread(new Runnable() {
			public void run() {
				server.serve();
			}
		}).start();

		//making child node under zookeeper parent node
		// System.out.println("This is before backup is launched");
		String childNodePath = curClient.create().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(nodePath, hostPort.getBytes());
	}
}


