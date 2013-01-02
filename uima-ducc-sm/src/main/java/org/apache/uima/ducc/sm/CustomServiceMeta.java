/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package org.apache.uima.ducc.sm;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import org.apache.uima.ducc.common.ServiceStatistics;
import org.apache.uima.ducc.common.utils.DuccLogger;
import org.apache.uima.ducc.common.utils.DuccProperties;


/**
 * This runs the watchdog thread for custom service pingers.
 *
 * It spawns a process, as the user, which in turn will instantiate an object which extends
 * AServiceMeta to implement the pinger.
 *
 * The processes communicate via a pipe: every ping interval the meta puts relevent information onto its
 * stdout:
 *     0|1 long long
 * The first token is 1 if the ping succeeded, 0 otherwise.
 * The second token is the total cumulative work executed by the service.
 * The third token is the current queue depth of the service.       
 */

class CustomServiceMeta
    implements IServiceMeta,
               SmConstants
{
    private DuccLogger logger = DuccLogger.getLogger(this.getClass().getName(), COMPONENT_NAME);	

    String[] jvm_args;
    String endpoint;
    String ping_class;
    String classpath;
    boolean ping_ok;
    int missed_pings = 0;
    ServiceSet sset;
    boolean test_mode = false;

    Process ping_main;

    StdioListener sin_listener = null;
    StdioListener ser_listener = null;
    PingThread pinger = null;

    int meta_ping_rate;
    int meta_ping_stability;
    ServiceStatistics service_statistics = null;

    String user;
    String working_directory;
    String log_directory;

    CustomServiceMeta(ServiceSet sset)
    {        
        this.sset = sset;
        DuccProperties job_props = sset.getJobProperties();
        DuccProperties meta_props = sset.getMetaProperties();

        String jvm_args_str = job_props.getStringProperty("service_custom_jvm_args");
        this.endpoint       = job_props.getStringProperty("service_custom_endpoint");
        this.ping_class     = job_props.getStringProperty("service_custom_ping");
        this.classpath      = job_props.getStringProperty("service_custom_classpath");
        this.user           = meta_props.getStringProperty("user");
        this.working_directory = job_props.getStringProperty("working_directory");
        this.log_directory     = job_props.getStringProperty("log_directory");
        
        jvm_args = jvm_args_str.split(" ");

        this.meta_ping_rate = ServiceManagerComponent.meta_ping_rate;
        this.meta_ping_stability = ServiceManagerComponent.meta_ping_stability;

    }

    /**
     * Test from main only
     */
    CustomServiceMeta(String props)
    {        
        DuccProperties dp = new DuccProperties();
        try {
			dp.load(props);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

        String jvm_args_str = dp.getStringProperty("service_custom_jvm_args");
        this.endpoint = dp.getStringProperty("service_custom_endpoint");
        this.ping_class = dp.getStringProperty("service_custom_ping");
        this.classpath = dp.getStringProperty("service_custom_classpath");
        jvm_args = jvm_args_str.split(" ");
        this.test_mode = true;
    }

    public ServiceStatistics getServiceStatistics()
    {
        return service_statistics;
    }

    public void run() 
    {
        String methodName = "run";
        try {
            pinger =  new PingThread();
        } catch ( Throwable t ) {
            logger.error(methodName, sset.getId(), "Cannot start listen socket, pinger not started.", t);
            sset.setUnresponsive();
            return;
        }
        int port = pinger.getPort();

        Thread ping_thread = new Thread(pinger);
        ping_thread.start();                            // sets up the listener, before we start the the external process

        ArrayList<String> arglist = new ArrayList<String>();
        if ( ! test_mode ) {
            arglist.add(System.getProperty("ducc.agent.launcher.ducc_spawn_path"));
            arglist.add("-u");
            arglist.add(user);
            arglist.add("-w");
            arglist.add(working_directory);
            arglist.add("-f");
            arglist.add(log_directory + "/" + sset.getId() + "/" + sset.getId() + "-CUSTOM_PING");
            arglist.add("--");
        }

        arglist.add(System.getProperty("ducc.jvm"));
        arglist.add("-cp");
        arglist.add(System.getProperty("java.class.path") + ":" + classpath);
        for ( String s : jvm_args) {
            arglist.add(s);
        }
        arglist.add("org.apache.uima.ducc.sm.sm.ServicePingMain");
        arglist.add("--class");
        arglist.add(ping_class);
        arglist.add("--endpoint");
        arglist.add(endpoint);
        arglist.add("--port");
        arglist.add(Integer.toString(port));

        int i = 0;
        for ( String s : arglist) {
            logger.debug(methodName, sset.getId(), "Args[", i++,"]:  ", s);
        }

        ProcessBuilder pb = new ProcessBuilder(arglist);
        
        //
        // Establish our pinger
        //
        InputStream stdout = null;
        InputStream stderr = null;
        try {
            ping_main = pb.start();
            stdout = ping_main.getInputStream();
            stderr = ping_main.getErrorStream();
            
            sin_listener = new StdioListener(1, stdout);
            ser_listener = new StdioListener(2, stderr);
            Thread sol = new Thread(sin_listener);
            Thread sel = new Thread(ser_listener);
            sol.start();
            sel.start();
        } catch (Throwable t) {
            logger.error(methodName, sset.getId(), "Cannot establish custom ping process:", t);
            sset.setUnresponsive();
            return;
        }
        
        int rc;
        while ( true ) {
            try {
                rc = ping_main.waitFor();
                logger.debug(methodName, sset.getId(), "Pinger returns rc ", rc);
                break;
            } catch (InterruptedException e2) {
                // nothing
            }
        }
		
		pinger.stop();
        sin_listener.stop();
        ser_listener.stop();
    }

    public void stop()
    {
        pinger.stop();
        sin_listener.stop();
        ser_listener.stop();
        ping_main.destroy();
    }

    class PingThread
        implements Runnable
    {
        ServerSocket server;
        int port = -1;
        boolean done = false;
        int errors =0;
        int error_threshold = 5;

        PingThread()
            throws IOException
        {
            this.server = new ServerSocket(0);
            this.port = server.getLocalPort();
		}

        int getPort()
        {
            return this.port;
        }

        synchronized void stop()
        {
            this.done = true;
        }

        public void run()
        {
        	String methodName = "PingThread.run()";
            try {

                Socket sock = server.accept();
                // Socket sock = new Socket("localhost", port);
                sock.setSoTimeout(5000);
                OutputStream out = sock.getOutputStream();
                InputStream  in =  sock.getInputStream();
                ObjectInputStream ois = new ObjectInputStream(in);
                
                ping_ok = false;         // we expect the callback to change this
				while ( true ) {
                    synchronized(this) {
                        if ( done ) return;
                    }

                    if ( errors > error_threshold ) {
                        stop();
                    }

                    // Ask for the ping
                    try {
                        logger.trace(methodName, sset.getId(), "PingDriver: ping OUT");
                        out.write('P');
                        out.flush();
                    } catch (IOException e1) {
                        logger.error(methodName, sset.getId(), e1);
                        errors++;
                    }

                    // Wait a bit
                    try {
                        Thread.sleep(meta_ping_rate);
                    } catch (InterruptedException e) {
                        // nothing
                    }
                    
                    // Try to read the response
                    // TODO: set the socket timeout on this
                    service_statistics = (ServiceStatistics) ois.readObject();
                    if ( service_statistics == null ) {
                        logger.error(methodName, sset.getId(), "Stats are null!");
                        errors++;
                    } else {
                        if ( service_statistics.getPing() ) {
                            synchronized(this) {
                                if ( done ) return;
                                sset.setResponsive();
                            }
                            logger.info(methodName, sset.getId(), "Ping ok: ", endpoint, service_statistics.toString());
                            missed_pings = 0;
                        } else {
                            logger.error(methodName, sset.getId(), "Missed_pings ", missed_pings, "endpoint", endpoint);
                            if ( ++missed_pings > meta_ping_stability ) {
                                sset.setUnresponsive();
                                logger.info(methodName, sset.getId(), "Seting state to unresponsive, endpoint",endpoint);
                            } else {
                                sset.setWaiting();
                                logger.info(methodName, sset.getId(), "Seting state to waiting, endpoint,", endpoint);
                            }                
                        }
                    }
                }
			} catch (IOException e) {
                logger.error(methodName, sset.getId(), "Error receiving ping", e);
                errors++;
			} catch (ClassNotFoundException e) {
                logger.error(methodName, sset.getId(), "Input garbled:", e);
                errors++;
			}
        }       
    }

    class StdioListener
        implements Runnable
    {
        InputStream in;
        String tag;
        boolean done = false;

        StdioListener(int which, InputStream in)
        {
            this.in = in;
            switch ( which ) {
               case 1: tag = "STDOUT: "; break;
               case 2: tag = "STDERR: "; break;
            }
        }

        void stop()
        {
            this.done = true;
        }

        public void run()
        {
            if ( done ) return;
            String methodName = "StdioListener.run";

            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            while ( true ) {
                try {
                    String s = br.readLine();
                    if ( test_mode ) System.out.println(tag + s);
                    else             logger.info(methodName, sset.getId(), tag, s);
                    if ( s == null ) {
                        String msg = tag + "closed, listener returns";
                        if ( test_mode ) System.out.println(msg);
                        else             logger.info(methodName, sset.getId(), msg);
                        return;
                    }
				} catch (IOException e) {
                    // if anything goes wrong this guy is toast.
                    if ( test_mode) e.printStackTrace();
                    else            logger.error(methodName, sset.getId(), e);
                    return;
				}
            }

        }
    }

    public static void main(String[] args)
    {
        // arg0 = amqurl = put into -Dbroker.url
        // arg1 = endpoint - pass to ServicePingMain
        // call ServicePingMain --class org.apache.uima.ducc.sm.PingTester --endpoint FixedSleepAE_1
        //    make sure test.jar is in the classpath
        CustomServiceMeta csm = new CustomServiceMeta(args[0]);
        csm.run();
    }

}