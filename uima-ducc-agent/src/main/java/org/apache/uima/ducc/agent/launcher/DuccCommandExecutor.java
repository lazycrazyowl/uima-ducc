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
package org.apache.uima.ducc.agent.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.uima.ducc.agent.NodeAgent;
import org.apache.uima.ducc.common.utils.DuccLogger;
import org.apache.uima.ducc.common.utils.TimeStamp;
import org.apache.uima.ducc.common.utils.Utils;
import org.apache.uima.ducc.common.utils.id.DuccId;
import org.apache.uima.ducc.transport.DuccExchange;
import org.apache.uima.ducc.transport.cmdline.ACommandLine;
import org.apache.uima.ducc.transport.cmdline.ICommandLine;
import org.apache.uima.ducc.transport.cmdline.JavaCommandLine;
import org.apache.uima.ducc.transport.event.ProcessStopDuccEvent;
import org.apache.uima.ducc.transport.event.common.IDuccProcess;
import org.apache.uima.ducc.transport.event.common.ITimeWindow;
import org.apache.uima.ducc.transport.event.common.TimeWindow;
import org.apache.uima.ducc.transport.event.common.IDuccProcessType.ProcessType;
import org.apache.uima.ducc.transport.event.common.IProcessState.ProcessState;


public class DuccCommandExecutor extends CommandExecutor {
	DuccLogger logger = DuccLogger.getLogger(this.getClass(),
			NodeAgent.COMPONENT_NAME);
	@SuppressWarnings("unused")
	private static AtomicInteger nextPort = new AtomicInteger(30000);
	
	public DuccCommandExecutor(NodeAgent agent, ICommandLine cmdLine,String host, String ip, Process managedProcess)
			throws Exception {
		super(agent, cmdLine, host, ip, managedProcess);
	}
	public DuccCommandExecutor(ICommandLine cmdLine,String host, String ip, Process managedProcess)
			throws Exception {
		super(null, cmdLine, host, ip, managedProcess);
	}

	private boolean useDuccSpawn() {
		if ( super.managedProcess.isAgentProcess() || Utils.isWindows() ) {
			return false;
		}
		// On non-windows check if we should spawn the process via ducc_ling
		String useSpawn = System.getProperty("ducc.agent.launcher.use.ducc_spawn");
		if ( useSpawn != null && useSpawn.toLowerCase().equals("true")) {
			return true;
		}
		// default 
		return false;
	}

	public Process exec(ICommandLine cmdLine, Map<String, String> processEnv)
			throws Exception {
		String methodName = "exec";
		
//		DuccId duccId = ((ManagedProcess)super.managedProcess).getDuccId();
		try {
			String[] cmd = getDeployableCommandLine(cmdLine,processEnv);			
			if ( isKillCommand(cmdLine) ) {
        logger.info(methodName, null, "Killing process");
				stopProcess(cmdLine, cmd);
			} else {
				startProcess(cmdLine, cmd, processEnv);
			}
			return managedProcess;
		} catch (Exception e) {
		  if ( ((ManagedProcess)super.managedProcess).getDuccProcess() != null ) {
  	    DuccId duccId = ((ManagedProcess)super.managedProcess).getDuccId();
	      logger.error(methodName, duccId, ((ManagedProcess)super.managedProcess).getDuccProcess().getDuccId(), e, new Object[]{});
		  }
			throw e;
		} 
	}
	private boolean isProcessTerminated( IDuccProcess process ) {
		return process.getProcessState().equals(ProcessState.Stopped) ||
				process.getProcessState().equals(ProcessState.Failed) ||
				process.getProcessState().equals(ProcessState.FailedInitialization);
	}
	private void stopProcess(ICommandLine cmdLine, String[] cmd ) throws Exception {
		String methodName = "stopProcess";
		Future<?> future = ((ManagedProcess) managedProcess).getFuture();
		if ( future == null ) {
			throw new Exception("Future Object not Found. Unable to Stop Process with PID:"+((ManagedProcess) managedProcess).getPid());
		}
		// for stop to work, PID must be provided
		if (((ManagedProcess) managedProcess).getDuccProcess().getPID() == null || 
				((ManagedProcess) managedProcess).getDuccProcess().getPID().trim().length() == 0 ) {  
			throw new Exception("Process Stop Command Failed. PID not provided.");
		}
		long maxTimeToWaitForProcessToStop = 60000; // default 1 minute 
		if ( super.agent.configurationFactory.processStopTimeout != null ) {
			maxTimeToWaitForProcessToStop = Long.valueOf(super.agent.configurationFactory.processStopTimeout);
		}
		try {
			//	if the process is marked for death or still initializing or it is JD, kill it
			if (    ((ManagedProcess) managedProcess).doKill() ||
					((ManagedProcess) managedProcess).getDuccProcess().getProcessType().equals(ProcessType.Pop) ||
					((ManagedProcess) managedProcess).getDuccProcess().getProcessState().equals(ProcessState.Initializing) ||
          ((ManagedProcess) managedProcess).getDuccProcess().getProcessState().equals(ProcessState.Starting) ||
					((ManagedProcess) managedProcess).getDuccProcess().getProcessState().equals(ProcessState.FailedInitialization)) {
				logger.info(methodName,((ManagedProcess)super.managedProcess).getDuccId(),">>>>>>>>>>>>>>> Killing Process:"+((ManagedProcess) managedProcess).getPid()+" .Process State:Initializing");
				doExec(new ProcessBuilder(cmd), cmd, true);
			} else { // send stop request to quiesce the service
				Map<String, Object> msgHeader = new HashMap<String, Object>();
				//	Add PID to the header. Receiving process has a PID filter in its router
				//  to determine if the stop message is destined for it. 
				msgHeader.put(DuccExchange.ProcessPID, ((ManagedProcess) managedProcess).getPid());		
				msgHeader.put(DuccExchange.DUCCNODEIP, ((ManagedProcess) managedProcess).getDuccProcess().getNodeIdentity().getIp());		
				logger.info(methodName,((ManagedProcess)super.managedProcess).getDuccId(),"Agent Sending Stop request to remote process with PID:"+((ManagedProcess) managedProcess).getPid()+ " On Node:"+((ManagedProcess) managedProcess).getDuccProcess().getNodeIdentity().getIp());
				try {
	        //  Use either Mina (socket) or jms to communicate with an agent
	        if (((ManagedProcess) managedProcess).getSocketEndpoint() != null ) {
	          
	          logger.info(methodName,null,"Agent Sending <<STOP>> to Endpoint:"+((ManagedProcess) managedProcess).getSocketEndpoint());
	          //  socket transport
	          super.
	          agent.
	            getEventDispatcherForRemoteProcess().
	              dispatch( new ProcessStopDuccEvent(new HashMap<DuccId, IDuccProcess>()), 
	                      ((ManagedProcess) managedProcess).getSocketEndpoint(), msgHeader );
	          
	        } else {
	          //  jms transport
	          super.
	            agent.
	              getEventDispatcherForRemoteProcess().
	                dispatch(new ProcessStopDuccEvent(new HashMap<DuccId, IDuccProcess>()),msgHeader );
	        }
				} catch( Exception ex) {
		      logger.error(methodName, ((ManagedProcess)super.managedProcess).getDuccId(), ex, new Object[]{});
				} finally {
	        logger.info(methodName,((ManagedProcess)super.managedProcess).getDuccId(),"------------ Agent Dispatched STOP Request to Process with PID:"+((ManagedProcess) managedProcess).getDuccProcess().getPID()+" Process State: "+((ManagedProcess) managedProcess).getDuccProcess().getProcessState()+" Waiting for Process to Stop. Timout Value:"+maxTimeToWaitForProcessToStop+" millis");
	        try {
	          // Start Kill timer only if process is still running
	          if ( ((ManagedProcess)super.managedProcess).getDuccProcess().getProcessState().equals(ProcessState.Running)) {
	            // the following call will block!!! Waits for process to stop on its own. If it doesnt stop, kills it hard.
	            // The exact time the service is allotted for stopping is defined in ducc.properties 
	            logger.info(methodName,((ManagedProcess)super.managedProcess).getDuccId(),"------------ Agent Starting Timer For Process with PID:"+((ManagedProcess) managedProcess).getDuccProcess().getPID()+" Process State: "+((ManagedProcess) managedProcess).getDuccProcess().getProcessState());
	            future.get(maxTimeToWaitForProcessToStop, TimeUnit.MILLISECONDS);
	          } else {
	            logger.info(methodName,((ManagedProcess)super.managedProcess).getDuccId(),"------------ Agent Dispatched STOP Request to Process with PID:"+((ManagedProcess) managedProcess).getDuccProcess().getPID()+" Process State: "+((ManagedProcess) managedProcess).getDuccProcess().getProcessState()+" .Process Not In Running State");
	          }
	        } catch (TimeoutException tex) { // on time out kill the process
	          if ( ((ManagedProcess)super.managedProcess).getDuccProcess().getProcessState().equals(ProcessState.Running)) {
	            logger.info(methodName,((ManagedProcess)super.managedProcess).getDuccId(),"------------ Agent Timed-out Waiting for Process with PID:"+((ManagedProcess) managedProcess).getDuccProcess().getPID()+" to Stop. Process State:"+((ManagedProcess) managedProcess).getDuccProcess().getProcessState()+" .Process did not stop in alloted time of "+maxTimeToWaitForProcessToStop+" millis");
	            logger.info(methodName,((ManagedProcess)super.managedProcess).getDuccId(),">>>>>>>>>>>>>>> Killing Process:"+((ManagedProcess) managedProcess).getDuccProcess().getPID()+" .Process State:"+((ManagedProcess) managedProcess).getDuccProcess().getProcessState());
	            doExec(new ProcessBuilder(cmd), cmd, true);
	          } else {
	            logger.info(methodName,((ManagedProcess)super.managedProcess).getDuccId(),"------------ Agent Timed-out Waiting for Process with PID:"+((ManagedProcess) managedProcess).getDuccProcess().getPID()+" to Stop but the process is not in a running state. Process State:"+((ManagedProcess) managedProcess).getDuccProcess().getProcessState());
	          }
	        } 
				}
			}
		} catch( Exception e) {  // InterruptedException, ExecutionException
		  logger.error(methodName, ((ManagedProcess)super.managedProcess).getDuccId(), e, new Object[]{});
		}
	}
	
	private void startProcess(ICommandLine cmdLine,String[] cmd, Map<String, String> processEnv) throws Exception {
		String methodName = "startProcess";
		
		ITimeWindow twr = new TimeWindow();
			String millis;
			millis = TimeStamp.getCurrentMillis();
			
			((ManagedProcess) managedProcess).getDuccProcess().setTimeWindowRun(twr);
			twr.setStart(millis);
			ProcessBuilder pb = new ProcessBuilder(cmd);

			if ( ((ManagedProcess)super.managedProcess).getDuccProcess().getProcessType().equals(ProcessType.Pop)) {
				ITimeWindow twi = new TimeWindow();
				((ManagedProcess) managedProcess).getDuccProcess().setTimeWindowInit(twi);
				twi.setStart(millis);
				twi.setEnd(millis);
			}
			String workingDir = null;
			//	Set working directory if a user specified it in a job specification
			//if ( ((ManagedProcess)super.managedProcess).getProcessInfo() != null ) {
			//	workingDir = ((ManagedProcess)super.managedProcess).getProcessInfo().getWorkingDirectory();
			//}
			//if ( workingDir != null ) {
            //		logger.info(methodName, ((ManagedProcess)super.managedProcess).getDuccId(), "Launching process in a user provided working dir:"+workingDir);
            //		pb.directory(new File(workingDir));
			//}
			Map<String, String> env = pb.environment();
			// enrich Process environment
			env.putAll(processEnv);
			if( cmdLine instanceof ACommandLine ) {
				// enrich Process environment with one from a given command line
				env.putAll(((ACommandLine)cmdLine).getEnvironment());
			}
			if ( logger.isTrace()) {
	      // <dump>
	      Iterator<String> iterator = env.keySet().iterator();
	      while(iterator.hasNext()) {
	        String key = iterator.next();
	        String value = env.get(key);
	        String message = "key:"+key+" "+"value:"+value;
	        logger.trace(methodName, ((ManagedProcess)super.managedProcess).getDuccId(), message);
	      }
			}
			try {
				doExec(pb, cmd, isKillCommand(cmdLine));
			} catch(Exception e) {
				throw e;
			} finally {
				millis = TimeStamp.getCurrentMillis();
				twr.setEnd(millis);
			}
	}
	private void doExec(ProcessBuilder pb, String[] cmd, boolean isKillCmd) throws Exception {
		String methodName = "doExec";

		try {
			
			StringBuilder sb = new StringBuilder((isKillCommand(cmdLine) ?"--->Killing Process ":"---> Launching Process:")
					+ " Using command line:");
			int inx=0;
			for (String cmdPart : cmd) { 
				sb.append("\n\t[").append(inx++).append("]").append(Utils.resolvePlaceholderIfExists(cmdPart, System.getProperties()));
			}
			logger.info(methodName, ((ManagedProcess)super.managedProcess).getDuccId(), sb.toString());

			java.lang.Process process = pb.start();
			// Drain process streams
			postExecStep(process, logger, isKillCmd);
			// block waiting for the process to terminate.
			process.waitFor();
			if ( !isKillCommand(cmdLine) ) {
				logger.info(methodName, ((ManagedProcess)super.managedProcess).getDuccId(), ">>>>>>>>>>>>> Process with PID:"+((ManagedProcess)super.managedProcess).getDuccProcess().getPID()+" Terminated");
			}
		} catch( NullPointerException ex) {
			((ManagedProcess)super.managedProcess).getDuccProcess().setProcessState(ProcessState.Failed);
			StringBuffer sb = new StringBuffer();
			sb.setLength(0);  
			sb.append("\n\tJava ProcessBuilder Failed to Launch Process due to NullPointerException. An Entry in the Command Array Must be Null. Look at Command Array Below:\n");
			for (String cmdPart : cmd) { 
				if ( cmdPart != null ) {
					sb.append("\n\t").append(cmdPart);
				} 
			}
			logger.info(methodName, ((ManagedProcess)super.managedProcess).getDuccId(), sb.toString());
			((ManagedProcess)super.managedProcess).getDuccProcess().setProcessState(ProcessState.Failed);
			throw ex;
		} catch( Exception ex) {
			((ManagedProcess)super.managedProcess).getDuccProcess().setProcessState(ProcessState.Failed);
			throw ex;
		} finally {
			//	 Per team discussion on Aug 31 2011, the process is stopped by an agent when initialization
			//   times out or initialization failed. Both Initialization_Timeout and FailedIntialization imply
			//   that the process is stopped.
			if ( !((ManagedProcess) managedProcess).getDuccProcess().getProcessState().equals(ProcessState.InitializationTimeout) && 
				 !((ManagedProcess) managedProcess).getDuccProcess().getProcessState().equals(ProcessState.FailedInitialization) &&
				 !((ManagedProcess) managedProcess).getDuccProcess().getProcessState().equals(ProcessState.Failed) && 
        !((ManagedProcess) managedProcess).getDuccProcess().getProcessState().equals(ProcessState.Killed)) { 
				((ManagedProcess) managedProcess).getDuccProcess().setProcessState(ProcessState.Stopped);
			}
		}

	}

	private String[] getDeployableCommandLine(ICommandLine cmdLine, Map<String, String> processEnv) throws Exception {
	  String methodName = "getDeployableCommandLine";
	  String[] cmd = new String[0];

	  try {
	    //	lock using Agent single permit semaphore. The Utils.concatAllArrays()
	    //  uses native call (for efficiency) which appears not thread safe.
	    NodeAgent.lock();
	    //	Use ducc_ling (c code) as a launcher for the actual process. The ducc_ling
	    //  allows the process to run as a specified user in order to write out logs in
	    //  user's space as oppose to ducc space.
	    String c_launcher_path = 
	            Utils.resolvePlaceholderIfExists(
	                    System.getProperty("ducc.agent.launcher.ducc_spawn_path"),System.getProperties());

	    //	if the command line is kill, don't provide any logging info to the ducc_ling. Otherwise, 
	    //  ducc_ling creates and empty log for each time we are killing a process
	    if ( isKillCommand(cmdLine) ) {
	      // Duccling, with no logging, always run by ducc, no need for workingdir
	      String[] duccling_nolog = new String[] { c_launcher_path,
	              "-u", ((ManagedProcess)super.managedProcess).getOwner(),
	      "--" };        
	      if ( useDuccSpawn() ) {
	        cmd = Utils.concatAllArrays(duccling_nolog, new String[] {cmdLine.getExecutable()},cmdLine.getCommandLine());
	      } else {
	        cmd = Utils.concatAllArrays(new String[] {cmdLine.getExecutable()},cmdLine.getCommandLine());
	      }
	    } else {
	      String processType = "-UIMA-";
	      if ( ((ManagedProcess)super.managedProcess).getDuccProcess().getProcessType().equals(ProcessType.Pop)) {
	        processType = "-JD-";
	      }             
	      String processLogDir = ((ManagedProcess)super.managedProcess).getProcessInfo().getLogDirectory()+
	              (((ManagedProcess)super.managedProcess).getProcessInfo().getLogDirectory().endsWith(File.separator) ? "" : File.separator)+
	              ((ManagedProcess)super.managedProcess).getWorkDuccId()+File.separator;
	      String processLogFile = ((ManagedProcess)super.managedProcess).getWorkDuccId()+ processType+host;            
	      String workingDir = ((ManagedProcess)super.managedProcess).getProcessInfo().getWorkingDirectory();
	      if ( workingDir == null ) {
	        workingDir = "NONE";
	      }

	      // Duccling, with logging
	      String[] duccling = new String[] { c_launcher_path,
	              "-f", processLogDir+processLogFile, 
	              "-w", workingDir,
	              "-u", ((ManagedProcess)super.managedProcess).getOwner(),
	              "--" };        
	      //	For now, log to user's home directory
	      //					String baseDir = 
	      //							((ManagedProcess)super.managedProcess).getProcessInfo().getLogDirectory();

	      String executable = cmdLine.getExecutable(); 
	      //	Check if user specified which java to use to launch the process. If not provided,
	      //	use the same java that the agent is running with 
	      if (executable == null || executable.trim().length() == 0 ) {
	        executable = System.getProperty("java.home")+File.separator+"bin"+File.separator+"java";
	      }
	      List<String> operationalProperties = new ArrayList<String>();

	      if ( cmdLine instanceof JavaCommandLine ) {
	        String duccHomePath = null;
	        if ( (duccHomePath = System.getenv("DUCC_HOME")) == null ) {
	          duccHomePath = System.getProperty("DUCC_HOME");
	        }
	        operationalProperties.add("-DDUCC_HOME="+duccHomePath);
	        operationalProperties.add("-Dducc.deploy.configuration="+ 
	                System.getProperty("ducc.deploy.configuration"));
	        if ( System.getProperties().containsKey("ducc.agent.managed.process.state.update.endpoint.type") ) {
	          String type = System.getProperty("ducc.agent.managed.process.state.update.endpoint.type");
	          if (type != null && type.equalsIgnoreCase("socket")) {
	            operationalProperties.add("-D"+NodeAgent.ProcessStateUpdatePort+"="+System.getProperty(NodeAgent.ProcessStateUpdatePort));
	          }
	        }
	        operationalProperties.add("-Dducc.process.log.dir="+processLogDir);
	        operationalProperties.add("-Dducc.process.log.basename="+processLogFile); //((ManagedProcess)super.managedProcess).getWorkDuccId()+ processType+host);
	        operationalProperties.add("-Dducc.job.id="+((ManagedProcess)super.managedProcess).getWorkDuccId());

	      }
	      String[] operationalPropertiesArray = new String[operationalProperties.size()];

	      if ( useDuccSpawn() ) {
	        cmd = Utils.concatAllArrays(duccling, new String[] {executable}, operationalProperties.toArray(operationalPropertiesArray), cmdLine.getCommandLine());
	      } else {
	        cmd = Utils.concatAllArrays(new String[] {executable}, operationalProperties.toArray(operationalPropertiesArray), cmdLine.getCommandLine());
	      }
	      // add JobId to the env
	      if ( processEnv != null ) {
	        processEnv.put("JobId", String.valueOf(((ManagedProcess)super.managedProcess).getWorkDuccId().getFriendly()));
	        //	for now just use user.home. In the long run the LogDir may
	        //  come from job spec
	      }
	    }
	    return cmd;
	  } catch( Exception ex) {
	    ((ManagedProcess)super.managedProcess).getDuccProcess().setProcessState(ProcessState.Failed);
	    throw ex;
	  } finally {
	    NodeAgent.unlock();
	  }

	}
	public void stop() {
	}
}