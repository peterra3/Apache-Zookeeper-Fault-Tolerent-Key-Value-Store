import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.barriers.DistributedDoubleBarrier;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;

public class GraderBarrier {
    private static final int QTY = 5;
    private static final String PATH = "/examples/barrier";

    public static void main(String[] args) throws Exception {
	if (args.length != 3) {
	    System.err.println("Usage: GraderBarrier zkstring zkbarriernode num_clients");
	    System.exit(-1);
	}
	try {
	    String zkstring = args[0];
	    String zknode = args[1];
	    int numClients = Integer.parseInt(args[2]);
	    CuratorFramework client = CuratorFrameworkFactory.newClient(zkstring, new ExponentialBackoffRetry(1000, 3));
	    client.start();
	    DistributedDoubleBarrier barrier = new DistributedDoubleBarrier(client, zknode, numClients);
	    barrier.enter();
	    barrier.leave();
	    client.close();
	} catch (Exception e) {
	    e.printStackTrace();
	}
    }
}
