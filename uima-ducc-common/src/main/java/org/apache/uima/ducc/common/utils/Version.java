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
package org.apache.uima.ducc.common.utils;

/**
 * Version updates.  Please try to remember to update the date, reason, and who you are for changing the level.
 *
 * 2012-05-07  0.6.0  First. jrc
 * 2012-05-14  0.6.1  Updated RM share counter. jrc
 * 2012-05-23  0.6.2  Service manager is live for "implicit" services. jrc
 * 2012-05-23  0.6.3  Service manager is ready for UIMA-AS services. jrc
 * 2012-08-27  0.6.4  All coding complete modulo boogs. jrc
 * 2012-10-09  0.7.0  Refactor for org.apache.ducc and add apache copyright. jrc
 */
public class Version
{
    private static final int major = 0;       // Major version
    private static final int minor = 7;       // Minor - may be API changes, or new function
    private static final int ptf   = 0;       // Fix level, fully compatible with previous, no relevent new function
    private static final String id = "beta";  // A short decoration, optional

    public final static String version()
    {
        StringBuffer sb = new StringBuffer();

        sb.append(Integer.toString(major));
        sb.append(".");

        sb.append(Integer.toString(minor));
        sb.append(".");

        sb.append(Integer.toString(ptf));

        if (id != null) {
            sb.append("-");
            sb.append(id);
        }

        return sb.toString();
    }

    public static void main(String[] args)
    {
        System.out.println(version());
    }

}