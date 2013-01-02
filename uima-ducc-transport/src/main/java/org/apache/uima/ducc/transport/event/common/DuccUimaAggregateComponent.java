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

import java.util.List;

public class DuccUimaAggregateComponent implements IDuccUimaAggregateComponent {

	/**
	 * please increment this sUID when removing or modifying a field 
	 */
	private static final long serialVersionUID = 1L;
	private String descriptor;
	private List<String> overrides;
	
	public DuccUimaAggregateComponent(String descriptor, List<String> overrides) {
		setDescriptor(descriptor);
		setOverrides(overrides);
	}
	
	@Override
	public String getDescriptor() {
		return this.descriptor;
	}

	@Override
	public void setDescriptor(String descriptor) {
		this.descriptor = descriptor;
	}

	@Override
	public List<String> getOverrides() {
		return this.overrides;
	}

	@Override
	public void setOverrides(List<String> overrides) {
		this.overrides = overrides;
	}

}