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
package org.apache.uima.ducc.orchestrator;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.uima.ducc.common.NodeIdentity;
import org.apache.uima.ducc.common.config.CommonConfiguration;
import org.apache.uima.ducc.common.utils.DuccLogger;
import org.apache.uima.ducc.common.utils.DuccLoggerComponents;
import org.apache.uima.ducc.common.utils.DuccPropertiesResolver;
import org.apache.uima.ducc.common.utils.TimeStamp;
import org.apache.uima.ducc.common.utils.id.DuccId;
import org.apache.uima.ducc.common.utils.id.DuccIdFactory;
import org.apache.uima.ducc.common.utils.id.IDuccIdFactory;
import org.apache.uima.ducc.transport.cmdline.JavaCommandLine;
import org.apache.uima.ducc.transport.event.cli.JobRequestProperties;
import org.apache.uima.ducc.transport.event.cli.JobSpecificationProperties;
import org.apache.uima.ducc.transport.event.cli.ServiceRequestProperties;
import org.apache.uima.ducc.transport.event.common.DuccProcess;
import org.apache.uima.ducc.transport.event.common.DuccSchedulingInfo;
import org.apache.uima.ducc.transport.event.common.DuccStandardInfo;
import org.apache.uima.ducc.transport.event.common.DuccUimaAggregate;
import org.apache.uima.ducc.transport.event.common.DuccUimaAggregateComponent;
import org.apache.uima.ducc.transport.event.common.DuccUimaDeploymentDescriptor;
import org.apache.uima.ducc.transport.event.common.DuccWorkJob;
import org.apache.uima.ducc.transport.event.common.DuccWorkPopDriver;
import org.apache.uima.ducc.transport.event.common.IDuccCommand;
import org.apache.uima.ducc.transport.event.common.IDuccUimaAggregate;
import org.apache.uima.ducc.transport.event.common.IDuccUimaAggregateComponent;
import org.apache.uima.ducc.transport.event.common.IDuccUimaDeploymentDescriptor;
import org.apache.uima.ducc.transport.event.common.IDuccProcessType.ProcessType;
import org.apache.uima.ducc.transport.event.common.IDuccTypes.DuccType;
import org.apache.uima.ducc.transport.event.common.IDuccUnits.MemoryUnits;
import org.apache.uima.ducc.transport.event.common.IResourceState.ResourceState;


public class JobFactory {
	private static JobFactory jobFactory = new JobFactory();
	private static final DuccLogger logger = DuccLoggerComponents.getOrLogger(JobFactory.class.getName());
	
	public static JobFactory getInstance() {
		return jobFactory;
	}
	
	private OrchestratorCommonArea orchestratorCommonArea = OrchestratorCommonArea.getInstance();
	private IDuccIdFactory jobDuccIdFactory = orchestratorCommonArea.getJobDuccIdFactory();
	private IDuccIdFactory serviceDuccIdFactory = orchestratorCommonArea.getServiceDuccIdFactory();
	private JobDriverHostManager hostManager = orchestratorCommonArea.getHostManager();
	private DuccIdFactory duccIdFactory = new DuccIdFactory();
	
	private String java_classpath = System.getProperty("java.class.path");
	private String classpath_order = System.getProperty("ducc.orchestrator.job.factory.classpath.order");
	
	private int addEnvironment(DuccWorkJob job, String type, JavaCommandLine javaCommandLine, String environmentVariables) {
		String methodName = "addEnvironment";
		logger.trace(methodName, job.getDuccId(), "enter");
		int retVal = 0;
		if(environmentVariables != null) {
			String[] envVarList = environmentVariables.split("\\s+");
			for (String envVar : envVarList) {
				String[] kv = envVar.split("=");
				String envKey = kv[0].trim();
				String envValue = kv[1].trim();
				javaCommandLine.addEnvVar(envKey, envValue);
				String message = "type:"+type+" "+"key:"+envKey+" "+"value:"+envValue;
				logger.debug(methodName, job.getDuccId(), message);
			}
			retVal++;
		}
		logger.trace(methodName, job.getDuccId(), "exit");
		return retVal;
	}
	
	private void checkSpec(DuccWorkJob job, JobRequestProperties jobRequestProperties) {
		String methodName = "checkSpec";
		logger.trace(methodName, job.getDuccId(), "enter");
		jobRequestProperties.normalize();
		Enumeration<Object> keys = jobRequestProperties.keys();
		while(keys.hasMoreElements()) {
			String key = (String) keys.nextElement();
			if(!jobRequestProperties.isRecognized(key)) {
				logger.warn(methodName, job.getDuccId(), "unrecognized: "+key);
			}
		}
		logger.trace(methodName, job.getDuccId(), "exit");
		return;
	}
	
	private ArrayList<String> toArrayList(String overrides) {
		String methodName = "toArrayList";
		logger.trace(methodName, null, "enter");
		ArrayList<String> list = new ArrayList<String>();
		if(overrides != null) {
			String[] items = overrides.split(",");
			for(String item : items) {
				list.add(item.trim());
			}
		}
		logger.trace(methodName, null, "exit");
		return list;
	}

	/*
	private static ArrayList<String> parseJvmArgs(String jvmArgs) {
		ArrayList<String> args = new ArrayList<String>();
		if(jvmArgs != null) {
			String[] tokens = jvmArgs.split("-");
			for(String token : tokens) {
				String flag = token.trim();
				if(flag.equals("")) {
				}
				else if(flag.equals("\"")) {
				}
				else if(flag.equals("'")) {
				}
				else {
					args.add("-"+flag);
				}
			}
		}
		return args;
	}
	*/
	
	// tokenize by blanks, but keep quoted items together
	
	private ArrayList<String> parseJvmArgs(String jvmArgs) {
		ArrayList<String> matchList = new ArrayList<String>();
		if(jvmArgs != null) {
			Pattern regex = Pattern.compile("[^\\s\"']+|\"([^\"]*)\"|'([^']*)'");
			Matcher regexMatcher = regex.matcher(jvmArgs);
			while (regexMatcher.find()) {
				if (regexMatcher.group(1) != null) {
					// Add double-quoted string without the quotes
					matchList.add(regexMatcher.group(1));
				} else if (regexMatcher.group(2) != null) {
					// Add single-quoted string without the quotes
					matchList.add(regexMatcher.group(2));
				} else {
					// Add unquoted word
					matchList.add(regexMatcher.group());
				}
			}
		}
		return matchList;
	}
	
	private void dump(DuccWorkJob job, IDuccUimaAggregate uimaAggregate) {
		String methodName = "dump";
		logger.info(methodName, job.getDuccId(), "brokerURL     "+uimaAggregate.getBrokerURL());
		logger.info(methodName, job.getDuccId(), "endpoint      "+uimaAggregate.getEndpoint());
		logger.info(methodName, job.getDuccId(), "description   "+uimaAggregate.getDescription());
		logger.info(methodName, job.getDuccId(), "name          "+uimaAggregate.getName());
		logger.info(methodName, job.getDuccId(), "thread-count  "+uimaAggregate.getThreadCount());
		List<IDuccUimaAggregateComponent> components = uimaAggregate.getComponents();
		for(IDuccUimaAggregateComponent component : components) {
			logger.info(methodName, job.getDuccId(), "descriptor    "+component.getDescriptor());
			List<String> overrides = component.getOverrides();
			for(String override : overrides) {
				logger.info(methodName, job.getDuccId(), "override      "+override);
			}
		}
	}
	
	private void dump(DuccWorkJob job, IDuccUimaDeploymentDescriptor uimaDeploymentDescriptor) {
		String methodName = "dump";
		logger.info(methodName, job.getDuccId(), "uimaDeploymentDescriptor      "+uimaDeploymentDescriptor);
	}
	
	public void logSweeper(String logDir, DuccId jobId) {
		String methodName = "logSweeper";
		if(logDir != null) {
			if(jobId != null) {
				if(!logDir.endsWith(File.separator)) {
					logDir += File.separator;
				}
				logDir += jobId;
				try {
					File file = new File(logDir);
					if(file.exists()) {
						File dest = new File(logDir+"."+"sweep"+"."+java.util.Calendar.getInstance().getTime().toString());
						file.renameTo(dest);
						logger.warn(methodName, jobId, "renamed "+logDir);
					}
				}
				catch(Throwable t) {
					logger.warn(methodName, jobId, "unable to rename "+logDir, t);
				}
			}
			else {
				logger.warn(methodName, jobId, "jobId is null");
			}
		}
		else {
			logger.warn(methodName, jobId, "logDir is null");
		}
	}
	
	private boolean isClasspathOrderUserBeforeDucc(String user_specified_classpath_order, DuccId jobId) {
		String methodName = "isClasspathOrderUserBeforeDucc";
		boolean retVal = false;
		if(user_specified_classpath_order != null) {
			if(user_specified_classpath_order.trim().equals("user-before-ducc")) {
				logger.warn(methodName, jobId, "user specified classpath order: "+user_specified_classpath_order);
				retVal = true;
			}
		}
		else if(classpath_order != null) {
			if(classpath_order.trim().equals("user-before-ducc")) {
				retVal = true;
			}
		}
		return retVal;
	}
	
	public DuccWorkJob create(CommonConfiguration common, JobRequestProperties jobRequestProperties) {
		String methodName = "create";
		DuccWorkJob job = new DuccWorkJob();
		checkSpec(job, jobRequestProperties);
		// id, type
		String ddCr = jobRequestProperties.getProperty(JobSpecificationProperties.key_driver_descriptor_CR);
		if(ddCr == null) {
			job.setDuccType(DuccType.Service);
			job.setDuccId(serviceDuccIdFactory.next());
		}
		else {
			job.setDuccType(DuccType.Job);
			job.setDuccId(jobDuccIdFactory.next());
			
		}
		// sweep out leftover logging trash
		logSweeper(jobRequestProperties.getProperty(JobRequestProperties.key_log_directory), job.getDuccId());
		// log
		jobRequestProperties.specification(logger);
		// classpath
		String processClasspath = jobRequestProperties.getProperty(JobSpecificationProperties.key_process_classpath);
		logger.debug(methodName, job.getDuccId(), "process CP (spec):"+processClasspath);
		logger.debug(methodName, job.getDuccId(), "java CP:"+java_classpath);
		if(processClasspath != null) {
			if(isClasspathOrderUserBeforeDucc(jobRequestProperties.getProperty(JobSpecificationProperties.key_classpath_order),job.getDuccId())) {
				logger.debug(methodName, job.getDuccId(), "process:OrderUserBeforeDucc");
				processClasspath=processClasspath+File.pathSeparator+java_classpath;
			}
			else {
				processClasspath=java_classpath+File.pathSeparator+processClasspath;
			}
		}
		else {
			processClasspath=java_classpath;
		}
		logger.debug(methodName, job.getDuccId(), "process CP (combined):"+processClasspath);
		// java command
		String javaCmd = jobRequestProperties.getProperty(JobSpecificationProperties.key_jvm);
		if(javaCmd == null) {
            // Agent will set javaCmd for Driver and Processes
		}
		// driver
		switch(job.getDuccType()) {
		case Job:
			String job_broker = jobRequestProperties.getProperty(JobRequestProperties.key_job_broker);
			if(job_broker == null) {
				job_broker = common.brokerUrl;
			}
			job.setJobBroker(job_broker);
			String job_queue = jobRequestProperties.getProperty(JobRequestProperties.key_job_endpoint);
			if(job_queue == null) {
				job_queue = common.jdQueuePrefix+job.getDuccId();
			}
			job.setJobQueue(job_queue);
			String crxml = jobRequestProperties.getProperty(JobSpecificationProperties.key_driver_descriptor_CR);
			String crcfg = jobRequestProperties.getProperty(JobSpecificationProperties.key_driver_descriptor_CR_overrides);
			String meta_time = jobRequestProperties.getProperty(JobRequestProperties.key_process_get_meta_time_max);
			if(meta_time == null) {
				meta_time = DuccPropertiesResolver.getInstance().getFileProperty(DuccPropertiesResolver.default_process_get_meta_time_max);
			}
			String wi_time = jobRequestProperties.getProperty(JobRequestProperties.key_process_per_item_time_max);
			if(wi_time == null) {
				wi_time = DuccPropertiesResolver.getInstance().getFileProperty(DuccPropertiesResolver.default_process_per_item_time_max);
			}
			String processExceptionHandler = jobRequestProperties.getProperty(JobRequestProperties.key_driver_exception_handler);
			DuccWorkPopDriver driver = new DuccWorkPopDriver(job.getjobBroker(), job.getjobQueue(), crxml, crcfg, meta_time, wi_time, processExceptionHandler);
			JavaCommandLine driverCommandLine = new JavaCommandLine(javaCmd);
			driverCommandLine.setClassName(IDuccCommand.main);
			driverCommandLine.addOption(IDuccCommand.arg_ducc_deploy_configruation);
			driverCommandLine.addOption(IDuccCommand.arg_ducc_deploy_components);
			driverCommandLine.addOption(IDuccCommand.arg_ducc_job_id+job.getDuccId().toString());
			// classpath
			String driverClasspath = jobRequestProperties.getProperty(JobSpecificationProperties.key_driver_classpath);
			logger.debug(methodName, job.getDuccId(), "driver CP (spec):"+driverClasspath);
			logger.debug(methodName, job.getDuccId(), "java CP:"+java_classpath);
			if(driverClasspath != null) {
				if(isClasspathOrderUserBeforeDucc(jobRequestProperties.getProperty(JobSpecificationProperties.key_classpath_order),job.getDuccId())) {
					logger.debug(methodName, job.getDuccId(), "driver:OrderUserBeforeDucc");
					driverClasspath=driverClasspath+File.pathSeparator+java_classpath;
				}
				else {
					driverClasspath=java_classpath+File.pathSeparator+driverClasspath;
				}
			}
			else {
				driverClasspath=java_classpath;
			}
			logger.debug(methodName, job.getDuccId(), "driver CP (combined):"+driverClasspath);
			driverCommandLine.setClasspath(driverClasspath);
			String driver_jvm_args = jobRequestProperties.getProperty(JobRequestProperties.key_driver_jvm_args);
			ArrayList<String> dTokens = parseJvmArgs(driver_jvm_args);
			for(String token : dTokens) {
				driverCommandLine.addOption(token);
			}
			String driverEnvironmentVariables = jobRequestProperties.getProperty(JobRequestProperties.key_driver_environment);
			int envCountDriver = addEnvironment(job, "driver", driverCommandLine, driverEnvironmentVariables);
			logger.info(methodName, job.getDuccId(), "driver env vars: "+envCountDriver);
			logger.debug(methodName, job.getDuccId(), "driver: "+driverCommandLine.getCommand());
			driverCommandLine.setLogDirectory(jobRequestProperties.getProperty(JobSpecificationProperties.key_log_directory));
			driver.setCommandLine(driverCommandLine);
			//
			NodeIdentity nodeIdentity = hostManager.getNode();
			DuccId duccId = duccIdFactory.next();
			duccId.setFriendly(0);
			DuccProcess driverProcess = new DuccProcess(duccId,nodeIdentity,ProcessType.Pop);
			driverProcess.setResourceState(ResourceState.Allocated);
			driverProcess.setNodeIdentity(nodeIdentity);
			driver.getProcessMap().put(driverProcess.getDuccId(), driverProcess);
			//
			orchestratorCommonArea.getProcessAccounting().addProcess(duccId, job.getDuccId());
			//
			job.setDriver(driver);
			break;
		case Service:
			break;
		}
		// standard info
		DuccStandardInfo standardInfo = new DuccStandardInfo();
		job.setStandardInfo(standardInfo);
		standardInfo.setUser(jobRequestProperties.getProperty(JobSpecificationProperties.key_user));
		standardInfo.setDateOfSubmission(TimeStamp.getCurrentMillis());
		standardInfo.setDateOfCompletion(null);
		standardInfo.setDescription(jobRequestProperties.getProperty(JobSpecificationProperties.key_description));
		standardInfo.setLogDirectory(jobRequestProperties.getProperty(JobSpecificationProperties.key_log_directory));
		standardInfo.setWorkingDirectory(jobRequestProperties.getProperty(JobSpecificationProperties.key_working_directory));
		String notifications = jobRequestProperties.getProperty(JobSpecificationProperties.key_notifications);
		if(notifications == null) {
			standardInfo.setNotifications(null);
		}
		else {
			String[] notificationsArray = notifications.split(" ,");
			for(String notification : notificationsArray) {
				notification.trim();
			}
			standardInfo.setNotifications(notificationsArray);
		}
		// scheduling info
		DuccSchedulingInfo schedulingInfo = new DuccSchedulingInfo();
		String ducc_rm_share_quantum = DuccPropertiesResolver.getInstance().getFileProperty(DuccPropertiesResolver.ducc_rm_share_quantum);
		if(ducc_rm_share_quantum != null) {
			ducc_rm_share_quantum = ducc_rm_share_quantum.trim();
			if(ducc_rm_share_quantum.length() > 0) {
				schedulingInfo.setShareMemorySize(ducc_rm_share_quantum);
			}
		}
		job.setSchedulingInfo(schedulingInfo);
		schedulingInfo.setSchedulingClass(jobRequestProperties.getProperty(JobSpecificationProperties.key_scheduling_class));
		schedulingInfo.setSchedulingPriority(jobRequestProperties.getProperty(JobSpecificationProperties.key_scheduling_priority));
		schedulingInfo.setSharesMax(jobRequestProperties.getProperty(JobSpecificationProperties.key_process_deployments_max));
		schedulingInfo.setSharesMin(jobRequestProperties.getProperty(JobSpecificationProperties.key_process_deployments_min));
		schedulingInfo.setThreadsPerShare(jobRequestProperties.getProperty(JobSpecificationProperties.key_process_thread_count));
		schedulingInfo.setShareMemorySize(jobRequestProperties.getProperty(JobSpecificationProperties.key_process_memory_size));
		schedulingInfo.setShareMemoryUnits(MemoryUnits.GB);
		String process_DD = jobRequestProperties.getProperty(JobSpecificationProperties.key_process_DD);
		if(process_DD != null) {
			// user DD
			IDuccUimaDeploymentDescriptor uimaDeploymentDescriptor = new DuccUimaDeploymentDescriptor(process_DD);
			job.setUimaDeployableConfiguration(uimaDeploymentDescriptor);
			dump(job, uimaDeploymentDescriptor);
		}
		else {
			// UIMA aggregate
			String name = common.jdQueuePrefix+job.getDuccId().toString();
			String description = job.getStandardInfo().getDescription();
			int threadCount = Integer.parseInt(job.getSchedulingInfo().getThreadsPerShare());
			String brokerURL = job.getjobBroker();;
			String endpoint = job.getjobQueue();
			ArrayList<IDuccUimaAggregateComponent> components = new ArrayList<IDuccUimaAggregateComponent>();
			String CMDescriptor = jobRequestProperties.getProperty(JobSpecificationProperties.key_process_descriptor_CM);
			if(CMDescriptor != null) {
				ArrayList<String> CMOverrides = toArrayList(jobRequestProperties.getProperty(JobSpecificationProperties.key_process_descriptor_CM_overrides));
				IDuccUimaAggregateComponent componentCM = new DuccUimaAggregateComponent(CMDescriptor, CMOverrides);
				components.add(componentCM);
			}
			String AEDescriptor = jobRequestProperties.getProperty(JobSpecificationProperties.key_process_descriptor_AE);
			if(AEDescriptor != null) {
				ArrayList<String> AEOverrides = toArrayList(jobRequestProperties.getProperty(JobSpecificationProperties.key_process_descriptor_AE_overrides));
				IDuccUimaAggregateComponent componentAE = new DuccUimaAggregateComponent(AEDescriptor, AEOverrides);
				components.add(componentAE);
			}
			String CCDescriptor = jobRequestProperties.getProperty(JobSpecificationProperties.key_process_descriptor_CC);
			if(CCDescriptor != null) {
				ArrayList<String> CCOverrides = toArrayList(jobRequestProperties.getProperty(JobSpecificationProperties.key_process_descriptor_CC_overrides));
				IDuccUimaAggregateComponent componentCC = new DuccUimaAggregateComponent(CCDescriptor, CCOverrides);
				components.add(componentCC);
			}
			IDuccUimaAggregate uimaAggregate = new DuccUimaAggregate(name,description,threadCount,brokerURL,endpoint,components);
			job.setUimaDeployableConfiguration(uimaAggregate);
			dump(job, uimaAggregate);
		}
		// pipelines
		JavaCommandLine pipelineCommandLine = new JavaCommandLine(javaCmd);
		pipelineCommandLine.setClassName("main:provided-by-Process-Manager");
		pipelineCommandLine.setClasspath(processClasspath);
		String process_jvm_args = jobRequestProperties.getProperty(JobRequestProperties.key_process_jvm_args);
		ArrayList<String> pTokens = parseJvmArgs(process_jvm_args);
		for(String token : pTokens) {
			pipelineCommandLine.addOption(token);
		}
		String processEnvironmentVariables = jobRequestProperties.getProperty(JobRequestProperties.key_process_environment);
		int envCountProcess = addEnvironment(job, "process", pipelineCommandLine, processEnvironmentVariables);
		logger.info(methodName, job.getDuccId(), "process env vars: "+envCountProcess);
		logger.debug(methodName, job.getDuccId(), "pipeline: "+pipelineCommandLine.getCommand());
		pipelineCommandLine.setLogDirectory(jobRequestProperties.getProperty(JobSpecificationProperties.key_log_directory));
		job.setCommandLine(pipelineCommandLine);
		// process_initialization_failures_cap
		String failures_cap = jobRequestProperties.getProperty(JobSpecificationProperties.key_process_initialization_failures_cap);
		try {
			long process_failures_cap = Long.parseLong(failures_cap);
			if(process_failures_cap > 0) {
				job.setProcessInitFailureCap(process_failures_cap);
			}
			else {
				logger.warn(methodName, job.getDuccId(), "invalid "+JobSpecificationProperties.key_process_initialization_failures_cap+": "+failures_cap);
			}
		}
		catch(Exception e) {
			logger.warn(methodName, job.getDuccId(), "invalid "+JobSpecificationProperties.key_process_initialization_failures_cap+": "+failures_cap);
		}
		// process_failures_limit
		String failures_limit = jobRequestProperties.getProperty(JobSpecificationProperties.key_process_failures_limit);
		try {
			long process_failures_limit = Long.parseLong(failures_limit);
			if(process_failures_limit > 0) {
				job.setProcessFailureLimit(process_failures_limit);
			}
			else {
				logger.warn(methodName, job.getDuccId(), "invalid "+JobSpecificationProperties.key_process_failures_limit+": "+failures_limit);
			}
		}
		catch(Exception e) {
			logger.warn(methodName, job.getDuccId(), "invalid "+JobSpecificationProperties.key_process_failures_limit+": "+failures_limit);
		}

        //
        // Set the service dependency, if there is one.
        //
        String depstr = jobRequestProperties.getProperty(JobSpecificationProperties.key_service_dependency);
        if ( depstr == null ) {            
            logger.debug(methodName, job.getDuccId(), "No service dependencies");
        } else {
            logger.debug(methodName, job.getDuccId(), "Adding service dependency", depstr);
            String[] deps = depstr.split(",");      
            job.setServiceDependencies(deps);
        }

        String ep = jobRequestProperties.getProperty(ServiceRequestProperties.key_service_request_endpoint);
        if ( ep == null ) {                     
            logger.debug(methodName, job.getDuccId(), "No service endpoint");
        } else {
            logger.debug(methodName, job.getDuccId(), "Adding service endpoint", ep);
            job.setServiceEndpoint(ep);
        }
		//TODO be sure to clean-up fpath upon job completion!
		return job;
	}
}