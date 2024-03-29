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
package org.apache.uima.ducc.transport.event;

import java.util.Iterator;

import org.apache.uima.ducc.common.utils.id.DuccId;
import org.apache.uima.ducc.transport.event.common.DuccWorkMap;
import org.apache.uima.ducc.transport.event.common.IDuccWork;
import org.apache.uima.ducc.transport.event.common.IDuccWorkJob;


public class OrchestratorAbbreviatedStateDuccEvent extends AbstractDuccEvent  {

	private static final long serialVersionUID = 3637372507135841728L;

	private DuccWorkMap workMap;
	
	
	public OrchestratorAbbreviatedStateDuccEvent() {
		super(EventType.ORCHESTRATOR_STATE);
	}
	
	private void abbreviate() {
		if(workMap != null) {
			Iterator<DuccId> jobKeys = workMap.getJobKeySet().iterator();
			while(jobKeys.hasNext()) {
				DuccId jobKey = jobKeys.next();
				IDuccWork duccWork = workMap.findDuccWork(jobKey);
				IDuccWorkJob job = (IDuccWorkJob)duccWork;
				job.setCommandLine(null);
				job.getDriver().setCommandLine(null);
			}
		}
	}
	
	public void setWorkMap(DuccWorkMap workMap) {
		this.workMap = workMap;
		abbreviate();
	}
	
	public DuccWorkMap getWorkMap() {
		return this.workMap;
	}
}
