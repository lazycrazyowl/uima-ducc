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
package org.apache.uima.ducc.transport.event.rm;

import org.apache.uima.ducc.common.NodeIdentity;
import org.apache.uima.ducc.common.utils.id.DuccId;

public class Resource implements IResource {

    private DuccId duccId;                // DuccId of the share, assigned by RM
    private NodeIdentity nodeId;          // Node id, assigned by Agent
    private boolean purged;               // Purged, for node failure
    private int qShares;                  // Number of quantum shares this resource occupies

    // dissallow
    @SuppressWarnings("unused")
	private Resource()
    {
    }

    public Resource(DuccId duccId, NodeIdentity nodeId, boolean purged, int qShares) 
    {
        this.duccId  = duccId;
        this.nodeId  = nodeId;
        this.purged  = purged;
        this.qShares = qShares;
    }
	
    @Override
    public DuccId getId() 
    {
        return duccId;
    }

    @Override
    public NodeIdentity getNodeId() 
    {
        return nodeId;
    }

    @Override
    public boolean isPurged() 
    {
        return purged;
    }

    @Override
    public int countShares()
    {
        return qShares;
    }

    public String toString()
    {
        return nodeId.getName() + "." + duccId.getFriendly() + ( purged ? "[P]" : "");
    }
}