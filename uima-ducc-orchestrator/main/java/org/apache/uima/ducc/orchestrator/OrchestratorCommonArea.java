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

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.SerializationUtils;
import org.apache.uima.ducc.common.IDuccEnv;
import org.apache.uima.ducc.common.config.CommonConfiguration;
import org.apache.uima.ducc.common.internationalization.Messages;
import org.apache.uima.ducc.common.persistence.IPropertiesFileManager;
import org.apache.uima.ducc.common.persistence.PropertiesFileManager;
import org.apache.uima.ducc.common.utils.DuccLogger;
import org.apache.uima.ducc.common.utils.DuccLoggerComponents;
import org.apache.uima.ducc.common.utils.IDuccLoggerComponents;
import org.apache.uima.ducc.common.utils.id.DuccId;
import org.apache.uima.ducc.common.utils.id.DuccIdFactory;
import org.apache.uima.ducc.common.utils.id.IDuccIdFactory;
import org.apache.uima.ducc.orchestrator.utilities.Checkpointable;
import org.apache.uima.ducc.orchestrator.utilities.ComponentHelper;
import org.apache.uima.ducc.transport.event.common.DuccWorkMap;
import org.apache.uima.ducc.transport.event.common.history.HistoryPersistenceManager;
import org.apache.uima.ducc.transport.event.jd.DriverStatusReport;


public class OrchestratorCommonArea {

	private static OrchestratorCommonArea orchestratorCommonArea = null;
	
	private static final DuccLogger logger = DuccLoggerComponents.getOrLogger(OrchestratorCommonArea.class.getName());
	
	public static OrchestratorCommonArea getInstance() {
		assert(orchestratorCommonArea != null);
		return orchestratorCommonArea;
	}
	
	public static void initialize(CommonConfiguration commonConfiguration) throws IOException {
		orchestratorCommonArea = new OrchestratorCommonArea();
		orchestratorCommonArea.commonConfiguration = commonConfiguration;
		orchestratorCommonArea.init();
	}
	
	private OrchestratorCommonArea() {
	}
	
	private CommonConfiguration commonConfiguration = null;
	
	public CommonConfiguration getCommonConfiguration() {
		return commonConfiguration;
	}
	
	private HistoryPersistenceManager historyPersistenceManager = null;
	
	private void init() {
		ComponentHelper.oneInstance(IDuccEnv.DUCC_STATE_DIR,"orchestrator");
		setPropertiesFileManager(new PropertiesFileManager(IDuccLoggerComponents.abbrv_orchestrator, IDuccEnv.DUCC_STATE_DIR, constOrchestratorProperties, false, true));
		setJobDuccIdFactory(new DuccIdFactory(propertiesFileManager,constJobSeqNo));
		setServiceDuccIdFactory(new DuccIdFactory(propertiesFileManager,constServiceSeqNo));
		setReservationDuccIdFactory(new DuccIdFactory(propertiesFileManager,constReservationSeqNo));
		workMap = new DuccWorkMap();
		driverStatusReportMap = new ConcurrentHashMap<DuccId,DriverStatusReport>();
		processAccounting = new ProcessAccounting();
		OrchestratorCheckpoint.getInstance().switchOnOff(commonConfiguration.orchestratorCheckpoint);
		OrchestratorCheckpoint.getInstance().restoreState();
		hostManager = JobDriverHostManager.getInstance();
		historyPersistenceManager = HistoryPersistenceManager.getInstance();
	}
	
	public String getStateDirectory() {
		return IDuccEnv.DUCC_STATE_DIR;
	}
	
	private static final String constOrchestratorProperties = "orchestrator.properties";
	private static final String constJobSeqNo = "job.seqno";
	private static final String constServiceSeqNo = "service.seqno";
	private static final String constReservationSeqNo = "reservation.seqno";
	
	// **********
	
	private IPropertiesFileManager propertiesFileManager = null;
	
	private void setPropertiesFileManager(IPropertiesFileManager instance) {
		propertiesFileManager = instance;
	}
	
	public IPropertiesFileManager getPropertiesFileManager() {
		assert(propertiesFileManager != null);
		return propertiesFileManager;
	}
	
	// **********
	
	private IDuccIdFactory jobDuccIdFactory = null;
	
	private void setJobDuccIdFactory(IDuccIdFactory instance) {
		jobDuccIdFactory = instance;
	}
	
	public IDuccIdFactory getJobDuccIdFactory() {
		return jobDuccIdFactory;
	}
	
	// **********
	
	private IDuccIdFactory serviceDuccIdFactory = null;
	
	private void setServiceDuccIdFactory(IDuccIdFactory instance) {
		serviceDuccIdFactory = instance;
	}
	
	public IDuccIdFactory getServiceDuccIdFactory() {
		return serviceDuccIdFactory;
	}
	
	// **********
	
	private IDuccIdFactory reservationDuccIdFactory = null;
	
	private void setReservationDuccIdFactory(IDuccIdFactory instance) {
		reservationDuccIdFactory = instance;
	}
	
	public IDuccIdFactory getReservationDuccIdFactory() {
		return reservationDuccIdFactory;
	}
	
	// **********
	
	@SuppressWarnings("unchecked")
	public Checkpointable getCheckpointable() {
		String methodName = "getCheckpointable";
		DuccWorkMap ckptWorkMap;
		ConcurrentHashMap<DuccId,DuccId> ckptProcessToJobMap;
		long t0 = System.currentTimeMillis();
		synchronized(workMap) {
			ckptWorkMap = (DuccWorkMap)SerializationUtils.clone(workMap);
			ckptProcessToJobMap = (ConcurrentHashMap<DuccId,DuccId>)SerializationUtils.clone(processAccounting.getProcessToJobMap());
		}
		long t1 = System.currentTimeMillis();
		long elapsed = t1 - t0;
		if(elapsed > Constants.SYNC_LIMIT) {
			logger.debug(methodName, null, "elapsed msecs: "+elapsed);
		}
		return new Checkpointable(ckptWorkMap,ckptProcessToJobMap);
	}
	
	public void setCheckpointable(Checkpointable checkpointable) {
		synchronized(workMap) {
			workMap = checkpointable.getWorkMap();
			processAccounting = new ProcessAccounting(checkpointable.getProcessToJobMap());
		}
	}
	
	// **********
	
	private ProcessAccounting processAccounting;
	
	public ProcessAccounting getProcessAccounting() {
		return processAccounting;
	}
	
	// **********
	
	private DuccWorkMap workMap = null;
	
	public DuccWorkMap getWorkMap() {
		return workMap;
	}
	
	public void setWorkMap(DuccWorkMap workMap) {
		this.workMap = workMap;
	}
	
	// **********
	
	private ConcurrentHashMap<DuccId,DriverStatusReport> driverStatusReportMap = null;
	
	public ConcurrentHashMap<DuccId,DriverStatusReport> getDriverStatusReportMap() {
		return driverStatusReportMap;
	}
	
	public void setDriverStatusReportMap(ConcurrentHashMap<DuccId,DriverStatusReport> driverStatusReportMap) {
		this.driverStatusReportMap = driverStatusReportMap;
	}
	
	// **********
	
	private Messages systemMessages= Messages.getInstance();
	private Messages userMessages= Messages.getInstance();
	
	public void initSystemMessages(String language, String country) {
		systemMessages = Messages.getInstance(language,country);
	}
	
	public void initUserMessages(String language, String country) {
		userMessages = Messages.getInstance(language,country);
	}

	public Messages getSystemMessages() {
		return systemMessages;
	}
	
	public Messages getUserMessages() {
		return userMessages;
	}
	
	// **********
	
	private JobDriverHostManager hostManager = null;
	
	public JobDriverHostManager getHostManager() {
		return hostManager;
	}
	
	// **********
	
	public HistoryPersistenceManager getHistoryPersistencemanager() {
		return historyPersistenceManager;
	}
	
	// **********
	
	private boolean signatureRequired = true;
	
	public void setSignatureRequired() {
		signatureRequired = true;
	}
	
	public void resetSignatureRequired() {
		signatureRequired = false;
	}
	
	public boolean isSignatureRequired() {
		return signatureRequired;
	}
	
}