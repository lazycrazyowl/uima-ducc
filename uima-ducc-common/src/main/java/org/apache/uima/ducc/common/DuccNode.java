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
package org.apache.uima.ducc.common;

import org.apache.uima.ducc.common.node.metrics.NodeMetrics;

public class DuccNode implements Node {
	private static final long serialVersionUID = -2579068359378835062L;
	private NodeIdentity nodeIdentity;
	private NodeMetrics nodeMetrics;
	private boolean duccLingExists;
	private boolean runWithDuccLing;
	
	public DuccNode( NodeIdentity nodeIdentity, NodeMetrics nodeMetrics) {
		this.nodeIdentity = nodeIdentity;
		this.nodeMetrics = nodeMetrics;
	}
	public boolean duccLingExists() {
		return duccLingExists;
	}
	public void duccLingExists(boolean duccLingExists) {
		this.duccLingExists = duccLingExists;
	}
	public boolean runWithDuccLing() {
		return runWithDuccLing;
	}
	public void runWithDuccLing(boolean runWithDuccLing) {
		this.runWithDuccLing = runWithDuccLing;
	}

	public NodeMetrics getNodeMetrics() {
		return nodeMetrics;
	}

	public NodeIdentity getNodeIdentity() {
		return nodeIdentity;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((nodeIdentity == null) ? 0 : nodeIdentity.hashCode());
		return result;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DuccNode other = (DuccNode) obj;
		if (nodeIdentity == null) {
			if (other.nodeIdentity != null)
				return false;
		} else if (!nodeIdentity.equals(other.nodeIdentity))
			return false;
		return true;
	}
}
