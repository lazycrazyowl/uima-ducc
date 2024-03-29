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
package org.apache.uima.ducc.transport.event.common;

import java.io.Serializable;
import java.util.List;

import org.apache.uima.ducc.common.utils.id.DuccId;
import org.apache.uima.ducc.transport.cmdline.ICommandLine;


public interface IDuccJobDeployment extends Serializable {
	/**
	 * Returns command line for launching JD
	 * @return {@link JavaCommandLine}
	 */
	public ICommandLine getJdCmdLine();
	/**
	 * Returns command line for launching JP
	 * @return {@link JavaCommandLine}
	 */
	public ICommandLine getJpCmdLine();
	/**
	 * Returns StandardInfo class 
	 * @return {@link IDuccStandardInfo}
	 */
	public IDuccStandardInfo getStandardInfo();
	/**
	 * Returns JD process
	 * @return {@link IDuccProcess}
	 */
	public IDuccProcess getJdProcess();
	/**
	 * Returns job's JP process list 
	 * @return {@link IDuccProcess}
	 */
	public List<IDuccProcess> getJpProcessList();
	
	/**
	 * Returns job's DuccId
	 * @return
	 */
	public DuccId getJobId();
	
	/** 
	 * Returns memory size assigned by user to this process
	 * @return
	 */
  public ProcessMemoryAssignment getProcessMemoryAssignment();

}
