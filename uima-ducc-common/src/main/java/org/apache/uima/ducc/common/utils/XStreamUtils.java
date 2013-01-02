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

import org.apache.camel.dataformat.xstream.XStreamDataFormat;
import org.apache.camel.impl.DefaultClassResolver;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

public class XStreamUtils {
	
	public static String marshall( Object targetToMarshall) throws Exception {
		XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
        XStream xStream = xStreamDataFormat.getXStream(new DefaultClassResolver());
        return xStream.toXML(targetToMarshall);
	}
	public static Object unmarshall( String targetToUnmarshall) throws Exception {
		XStream xStream = new XStream(new DomDriver());
		return xStream.fromXML(targetToUnmarshall);
	}
}