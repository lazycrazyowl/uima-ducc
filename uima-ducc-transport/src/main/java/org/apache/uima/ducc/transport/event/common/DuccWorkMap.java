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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.SerializationUtils;
import org.apache.uima.ducc.common.utils.id.DuccId;
import org.apache.uima.ducc.transport.event.common.IDuccTypes.DuccType;


@SuppressWarnings({ "rawtypes" })
public class DuccWorkMap implements Serializable, Map {
	
	/**
	 * please increment this sUID when removing or modifying a field 
	 */
	private static final long serialVersionUID = 2L;
	
	private ConcurrentHashMap<DuccId,IDuccWork> concurrentWorkMap = new ConcurrentHashMap<DuccId,IDuccWork>();
	
	private AtomicInteger atomicJobCount = new AtomicInteger(0);
	private AtomicInteger atomicServiceCount = new AtomicInteger(0);
	private AtomicInteger atomicReservationCount = new AtomicInteger(0);
	
	private ConcurrentHashMap<String,DuccId> concurrentJobMap = null;
	private ConcurrentHashMap<String,DuccId> concurrentServiceMap = null;
	private ConcurrentHashMap<String,DuccId> concurrentReservationMap = null;
	
	public DuccWorkMap() {
		concurrentJobMap = new ConcurrentHashMap<String,DuccId>();
		concurrentServiceMap = new ConcurrentHashMap<String,DuccId>();
		concurrentReservationMap = new ConcurrentHashMap<String,DuccId>();
	}

	public int getJobCount() {
		return atomicJobCount.get();
	}
	
	public Set<DuccId> getJobKeySet() {
		Set<DuccId> retVal = new HashSet<DuccId>();
		Iterator<DuccId> iterator = keySet().iterator();
		while(iterator.hasNext()) {
			DuccId duccId = iterator.next();
			switch(concurrentWorkMap.get(duccId).getDuccType()) {
			case Job:
				retVal.add(duccId);
				break;
			default:
				break;
			}
		}
		return retVal;
	}
	
	public int getServiceCount() {
		return atomicServiceCount.get();
	}
	
	public Set<DuccId> getServiceKeySet() {
		Set<DuccId> retVal = new HashSet<DuccId>();
		Iterator<DuccId> iterator = keySet().iterator();
		while(iterator.hasNext()) {
			DuccId duccId = iterator.next();
			switch(concurrentWorkMap.get(duccId).getDuccType()) {
			case Service:
				retVal.add(duccId);
				break;
			default:
				break;
			}
		}
		return retVal;
	}
	
	public int getReservationCount() {
		return atomicReservationCount.get();
	}
	
	public Set<DuccId> getReservationKeySet() {
		Set<DuccId> retVal = new HashSet<DuccId>();
		Iterator<DuccId> iterator = keySet().iterator();
		while(iterator.hasNext()) {
			DuccId duccId = iterator.next();
			switch(concurrentWorkMap.get(duccId).getDuccType()) {
			case Reservation:
				retVal.add(duccId);
				break;
			default:
				break;
			}
		}
		return retVal;
	}
	
	public ConcurrentHashMap<DuccId,IDuccWork> getMap() {
		return concurrentWorkMap;
	}
	
	public static String normalize(String id) {
		String normalizedId = String.valueOf(Integer.parseInt(id));
		return normalizedId;
	}
	
	public void addDuccWork(IDuccWork duccWork) {
		synchronized(this) {
			duccWork.getDuccId();
			DuccId key = duccWork.getDuccId();
			concurrentWorkMap.put(key,duccWork);
			switch(duccWork.getDuccType()) {
			case Job:
				jobAddKey(duccWork.getId(),key);
				break;
			case Service:
				serviceAddKey(duccWork.getId(),key);
				break;
			case Reservation:
				reservationAddKey(duccWork.getId(),key);
				break;
			default:
				break;
			}
			switch(duccWork.getDuccType()) {
			case Job:
				atomicJobCount.incrementAndGet();
				break;
			case Service:
				atomicServiceCount.incrementAndGet();
				break;
			case Reservation:
				atomicReservationCount.incrementAndGet();
				break;
			default: 
				break;
			}
		}
	}
	
	public void removeDuccWork(DuccId duccId) {
		synchronized(this) {
			IDuccWork duccWork = concurrentWorkMap.remove(duccId);
			if(duccWork != null) {
				switch(duccWork.getDuccType()) {
				case Job:
					jobRemoveKey(duccWork.getId());
					break;
				case Service:
					serviceRemoveKey(duccWork.getId());
					break;
				case Reservation:
					reservationRemoveKey(duccWork.getId());
					break;
				default:
					break;
				}
			
                switch(duccWork.getDuccType()) {
                case Job:
                    atomicJobCount.decrementAndGet();
                    break;
                case Service:
                    atomicServiceCount.decrementAndGet();
                    break;
                case Reservation:
                    atomicReservationCount.decrementAndGet();
                    break;
                default: 
                    break;
                }
            }
		}
	}
	
	public IDuccWork findDuccWork(DuccId duccId) {
		synchronized(this) {
			return concurrentWorkMap.get(duccId);
		}
	}
	
	public IDuccWork findDuccWork(DuccType duccType, String id) {
		IDuccWork duccWork = null;
		String key = id;
		DuccId duccId;
		synchronized(this) {
			switch(duccType) {
			case Job:
				duccId = concurrentJobMap.get(normalize(key));
				if(duccId != null) {
					duccWork = concurrentWorkMap.get(duccId);
				}
				break;
			case Service:
				duccId = concurrentServiceMap.get(normalize(key));
				if(duccId != null) {
					duccWork = concurrentWorkMap.get(duccId);
				}
				break;
			case Reservation:
				duccId = concurrentReservationMap.get(normalize(key));
				if(duccId != null) {
					duccWork = concurrentWorkMap.get(duccId);
				}
				break;
			default:
				break;
			}
			return duccWork;
		}
	}
	
	public IDuccWork findDuccWork(DuccType duccType, Long id) {
		IDuccWork duccWork = null;
		String key = normalize(String.valueOf(id));
		DuccId duccId;
		synchronized(this) {
			switch(duccType) {
			case Job:
				duccId = concurrentJobMap.get(normalize(key));
				if(duccId != null) {
					duccWork = concurrentWorkMap.get(duccId);
				}
				break;
			case Service:
				duccId = concurrentServiceMap.get(normalize(key));
				if(duccId != null) {
					duccWork = concurrentWorkMap.get(duccId);
				}
				break;
			case Reservation:
				duccId = concurrentReservationMap.get(normalize(key));
				if(duccId != null) {
					duccWork = concurrentWorkMap.get(duccId);
				}
				break;
			default:
				break;
			}
			return duccWork;
		}
	}
	
	public DuccWorkMap deepCopy() {
		synchronized (this) {
			return (DuccWorkMap)SerializationUtils.clone(this);
		}
	}
	
	/*
	 * *****
	 */

	private void jobAddKey(String id, DuccId duccId) {
		concurrentJobMap.put(normalize(id),duccId);
	}
	
	private void jobRemoveKey(String id) {
		concurrentJobMap.remove(normalize(id));
	}
	
	/*
	 * *****
	 */

	private void serviceAddKey(String id, DuccId duccId) {
		concurrentServiceMap.put(normalize(id),duccId);
	}
	
	private void serviceRemoveKey(String id) {
		concurrentServiceMap.remove(normalize(id));
	}
	
	/*
	 * *****
	 */

	private void reservationAddKey(String id, DuccId duccId) {
		concurrentReservationMap.put(normalize(id),duccId);
	}
	
	private void reservationRemoveKey(String id) {
		concurrentReservationMap.remove(normalize(id));
	}
	
	/*
	 * *****
	 */	
	
	@Override
	public int size() {
		return concurrentWorkMap.size();
	}

	@Override
	public boolean isEmpty() {
		return concurrentWorkMap.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return concurrentWorkMap.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return concurrentWorkMap.containsValue((IDuccWork)value);
	}

	@Override
	public Object get(Object key) {
		return concurrentWorkMap.get((DuccId)key);
	}

	@Override
	public Object put(Object key, Object value) {
		return concurrentWorkMap.put((DuccId)key, (IDuccWork)value);
	}

	@Override
	public Object remove(Object key) {
		return concurrentWorkMap.remove(key);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void putAll(Map m) {
		concurrentWorkMap.putAll(m);
	}

	@Override
	public void clear() {
		 concurrentWorkMap.clear();
	}

	@Override
	public Set<DuccId> keySet() {
		return concurrentWorkMap.keySet();
	}

	@Override
	public Collection<IDuccWork> values() {
		return concurrentWorkMap.values();
	}

	@Override
	public Set entrySet() {
		return concurrentWorkMap.entrySet();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + atomicJobCount.get();
		result = prime * result + ((concurrentJobMap == null) ? 0 : concurrentJobMap.hashCode());
		result = prime * result + atomicServiceCount.get();
		result = prime * result + ((concurrentServiceMap == null) ? 0 : concurrentServiceMap.hashCode());
		result = prime * result + atomicReservationCount.get();
		result = prime * result	+ ((concurrentReservationMap == null) ? 0 : concurrentReservationMap.hashCode());
		result = prime * result + ((concurrentWorkMap == null) ? 0 : concurrentWorkMap.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DuccWorkMap other = (DuccWorkMap) obj;
		if (atomicJobCount.get() != other.atomicJobCount.get())
			return false;
		if (concurrentJobMap == null) {
			if (other.concurrentJobMap != null)
				return false;
		} else if (!concurrentJobMap.equals(other.concurrentJobMap))
			return false;
		if (atomicServiceCount.get() != other.atomicServiceCount.get())
			return false;
		if (concurrentServiceMap == null) {
			if (other.concurrentServiceMap != null)
				return false;
		} else if (!concurrentServiceMap.equals(other.concurrentServiceMap))
			return false;
		if (atomicReservationCount != other.atomicReservationCount)
			return false;
		if (concurrentReservationMap == null) {
			if (other.concurrentReservationMap != null)
				return false;
		} else if (!concurrentReservationMap.equals(other.concurrentReservationMap))
			return false;
		if (concurrentWorkMap == null) {
			if (other.concurrentWorkMap != null)
				return false;
		} else if (!concurrentWorkMap.equals(other.concurrentWorkMap)) {
			return false;
		}
		return true;
	}
	
}