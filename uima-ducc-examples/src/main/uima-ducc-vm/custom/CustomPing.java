
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;

import org.apache.uima.ducc.cli.AServicePing;
import org.apache.uima.ducc.cli.ServiceStatistics;


/**
 * This is designed for the simple sleeper "service" that does nothing other than
 * wait for requests from the pinger.
 *
 * The necessary endpoint is CUSTOM:name:host:port
 */
public class CustomPing
    extends AServicePing
{
    String host;
    String port;
    public void init(String arguments, String endpoint)
    	throws Exception
    {
        System.out.println("INIT: endpoint " + endpoint);
        String[] parts = endpoint.split(":");
        // [1] is the type of service, always ignored
        host = parts[1];
        port = parts[2];
        System.out.println("INIT: host " + host + " port " + port);
    }

    public void stop()
    {
    }

    public long readLong(DataInputStream dis)
    	throws Exception
    {
        long stat1 = dis.readLong();
        return Long.reverseBytes(stat1);
    }

    public ServiceStatistics getStatistics()
    {
        ServiceStatistics stats = new ServiceStatistics(false, false,"<NA>");
        Socket sock = null;
        try {
            sock = new Socket(host, Integer.parseInt(port));
            InputStream sock_in = sock.getInputStream();
            
            DataInputStream dis = new DataInputStream(sock_in);

            long stat1 = readLong(dis);
            long stat2 = readLong(dis);
            long stat3 = readLong(dis);
            long stat4 = readLong(dis);

            stats.setAlive(true);
            stats.setHealthy(true);
            stats.setInfo(  "SvcTimA[" + stat1 +
                            "] SvcTimB[" + stat2 +
                            "] SvcTimC[" + stat3 +
                            "] SvcTimD[" + stat4 +
                            "]"
                            );
        } catch ( Throwable t) {
        	t.printStackTrace();
            stats.setInfo(t.getMessage());
        } finally {
        	try { sock.close();} catch (IOException e) {}
        }
        return stats;        
    }

    public static void main(String[] args)
    {
        try {
			CustomPing cp = new CustomPing();
			cp.init(args[0], args[1]);
			for ( int i = 0; i < 10; i++ ) {
			    ServiceStatistics stats = cp.getStatistics();
			    System.out.println(stats);
			    Thread.sleep(2000);
			}
			cp.stop();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

}
