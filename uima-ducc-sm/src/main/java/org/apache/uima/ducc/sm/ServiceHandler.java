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
package org.apache.uima.ducc.sm;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.uima.ducc.common.utils.DuccLogger;
import org.apache.uima.ducc.common.utils.DuccProperties;
import org.apache.uima.ducc.common.utils.id.DuccId;
import org.apache.uima.ducc.transport.event.ServiceModifyEvent;
import org.apache.uima.ducc.transport.event.ServiceQueryEvent;
import org.apache.uima.ducc.transport.event.ServiceQueryReplyEvent;
import org.apache.uima.ducc.transport.event.ServiceReplyEvent;
import org.apache.uima.ducc.transport.event.ServiceStartEvent;
import org.apache.uima.ducc.transport.event.ServiceStopEvent;
import org.apache.uima.ducc.transport.event.ServiceUnregisterEvent;
import org.apache.uima.ducc.transport.event.common.DuccWorkJob;
import org.apache.uima.ducc.transport.event.common.IDuccWork;
import org.apache.uima.ducc.transport.event.common.IDuccState.JobState;
import org.apache.uima.ducc.transport.event.sm.ServiceDependency;
import org.apache.uima.ducc.transport.event.sm.ServiceDescription;
import org.apache.uima.ducc.transport.event.sm.ServiceMap;



public class ServiceHandler
    implements SmConstants, 
               Runnable
{
    private DuccLogger logger = DuccLogger.getLogger(ServiceHandler.class.getName(), COMPONENT_NAME);	
    private IServiceManager serviceManager;

    private ServiceStateHandler serviceStateHandler = new ServiceStateHandler();
	private ServiceMap serviceMap = new ServiceMap();       // note this is the sync object for publish

    private HashMap<DuccId, IDuccWork> newJobs = new HashMap<DuccId, IDuccWork>();
    private HashMap<DuccId, IDuccWork> newServices = new HashMap<DuccId, IDuccWork>();
    
    private HashMap<DuccId, IDuccWork> deletedJobs = new HashMap<DuccId, IDuccWork>();
    private HashMap<DuccId, IDuccWork> deletedServices = new HashMap<DuccId, IDuccWork>();
    
    private HashMap<DuccId, IDuccWork> modifiedJobs = new HashMap<DuccId, IDuccWork>();
    private HashMap<DuccId, IDuccWork> modifiedServices = new HashMap<DuccId, IDuccWork>();
    
    public ServiceHandler(IServiceManager serviceManager)
    {
        this.serviceManager = serviceManager;        
    }

    public synchronized void run()
    {
    	String methodName = "run";
        while ( true ) {
            try {
				wait();
			} catch (InterruptedException e) {
				logger.error(methodName, null, e);
			}

            try {
                processUpdates();
            } catch (Throwable t) {
                logger.error(methodName, null, t);
            }
        }
    }

    /**
     * At boot only ... pass in the set of all known active services to each service so it can update
     * internal state with current published state.
     */
    void synchronizeImplementors(Map<DuccId, JobState> servicemap)
    {
        ArrayList<String> keys = serviceStateHandler.getServiceNames();
        for ( String k : keys ) {
            ServiceSet sset = serviceStateHandler.getServiceByName(k);
            sset.synchronizeImplementors(servicemap);
        }
    }

    void processUpdates()
    {
    	String methodName = "processUpdates";
        logger.info(methodName, null, "Processing updates.");
        HashMap<DuccId, IDuccWork> incoming;

        incoming = new HashMap<DuccId, IDuccWork>();
        synchronized(deletedJobs) {
            incoming.putAll(deletedJobs);
            deletedJobs.clear();
        }
        handleDeletedJobs(incoming);

        incoming = new HashMap<DuccId, IDuccWork>();
        synchronized(modifiedJobs) {
            incoming.putAll(modifiedJobs);
            modifiedJobs.clear();
        }
        handleModifiedJobs(incoming);

        incoming = new HashMap<DuccId, IDuccWork>();
        synchronized(deletedServices) {
            incoming.putAll(deletedServices);
            deletedServices.clear();
        }
        handleDeletedServices(incoming);

        incoming = new HashMap<DuccId, IDuccWork>();
        synchronized(modifiedServices) {
            incoming.putAll(modifiedServices);
            modifiedServices.clear();
        }
        handleModifiedServices(incoming);

        incoming = new HashMap<DuccId, IDuccWork>();
        synchronized(newServices) {
            incoming.putAll(newServices);
            newServices.clear();
        }
        handleNewServices(incoming);

        incoming = new HashMap<DuccId, IDuccWork>();
        synchronized(newJobs) {
            incoming.putAll(newJobs);
            newJobs.clear();
        }
        handleNewJobs(incoming);

        synchronized(serviceMap) {
            serviceManager.publish(serviceMap);
        }

        List<ServiceSet> regsvcs = serviceStateHandler.getRegisteredServices();
        for ( ServiceSet sset : regsvcs ) {
            sset.enforceAutostart();
        }
    }

    void signalUpdates( // This is the incoming or map, with work split into categories.
                                     // The incoming maps are volatile - must save contents before returning.
                                    HashMap<DuccId, IDuccWork> newJobs, 
                                    HashMap<DuccId, IDuccWork> newServices,                               
                                    HashMap<DuccId, IDuccWork> deletedJobs,
                                    HashMap<DuccId, IDuccWork> deletedServices,                                    
                                    HashMap<DuccId, IDuccWork> modifiedJobs,
                                    HashMap<DuccId, IDuccWork> modifiedServices
                                    )
    {

        synchronized(newJobs) {
            this.newJobs.putAll(newJobs);
        }

        synchronized(newServices) {
            this.newServices.putAll(newServices);
        }

        synchronized(deletedJobs) {
            this.deletedJobs.putAll(deletedJobs);
        }

        synchronized(deletedServices) {
            this.deletedServices.putAll(deletedServices);
        }

        synchronized(modifiedJobs) {
            this.modifiedJobs.putAll(modifiedJobs);
        }

        synchronized(modifiedServices) {
            this.modifiedServices.putAll(modifiedServices);
        }
      
        synchronized(this) {
            notify();
        }
    } 
    
    /**
     * Resolves state for the job in id based on the what it is dependent upon - the independent services
     */
    protected void resolveState(DuccId id, ServiceDependency dep)
    {        
        Map<String, ServiceSet> services = serviceStateHandler.getServicesForJob(id);
        if ( services == null ) {
            dep.setState(ServiceState.NotAvailable);       // says that nothing i need is available
            return;
        }

        ServiceState state = ServiceState.Available;              
        //
        // Start with the most permissive state and reduce it as we walk the list
        // Running > Initializing > Waiting > NotAvailable
        //
        // This sets the state to the min(all dependent service states)
        //
        for ( ServiceSet sset : services.values() ) {
            if ( sset.getServiceState().ordinality() < state.ordinality() ) state = sset.getServiceState();
             dep.setIndividualState(sset.getKey(), sset.getServiceState());
        }
        dep.setState(state);
    }

    /**
     * This is called when an endpoint is referenced as a dependent service from a job or a service.
     * It is called only when a new job or service is first discovred in the OR map.
     */
    protected Map<String, ServiceSet> resolveDependencies(DuccWorkJob w, ServiceDependency s)
    {
    	String methodName = "resolveDependencies";
    	DuccId id = w.getDuccId();
        String[] deps = w.getServiceDependencies();

        // New services, if any are discovered
        boolean fatal = false;
        Map<String, ServiceSet> jobServices = new HashMap<String, ServiceSet>();
        for ( String dep : deps ) {
            
            // put it into the global map of known services if needed and up the ref count
            ServiceSet sset = serviceStateHandler.getServiceByName(dep);                            
            if ( sset == null ) {                              // first time, so it's by reference only
                try {
                    sset = new ServiceSet(dep);
                    serviceStateHandler.putServiceByName(dep, sset);
                } catch ( IllegalArgumentException e ) {       // if 'dep' is invalid we throw
                    s.addMessage(dep, e.getMessage());
                    s.setState(ServiceState.NotAvailable);
                    fatal = true;
                    continue;
                }
            }

            if ( sset.isDeregistered() ) { 
                // Registerered services only - the service might even still be alive because it can
                // take a while to get rid of these guys - we need to be sure we don't attach any
                // new jobs to it.
                s.addMessage(dep, "Independent registered service [" + dep + "] has been deregistered and is terminating.");
                s.setState(ServiceState.NotAvailable);
                fatal = true;
                continue;
            }

            //
            // We try to vet all services so the message is complete.  If we've already had some fatal problems
            // we need to bypass any attempt to cope with registered services or updating the sset.
            //
            if ( ! fatal ) {
                if ( sset.isRegistered() && (sset.countImplementors() == 0) ) {
                    // Registered but not alive, well, we can fix that!
                    int ninstances = sset.getNInstances();
                    logger.debug(methodName, sset.getId(), "Reference-starting registered service, instances =", ninstances);
                    sset.setReferencedStart(true);
                    for ( int i = 0; i < ninstances; i++ ) {
                        sset.start();
                    }
                }
                
                jobServices.put(dep, sset);
                sset.reference(id);
                serviceStateHandler.putServiceForJob(w.getDuccId(), sset);
                logger.debug(methodName, id, "Service init ok. Ref[", dep, "] incr to", sset.countReferences());
            }
        }

        if ( fatal ) {
            jobServices.clear();            
        }
        return jobServices;
    }
 
    protected void handleNewJobs(HashMap<DuccId, IDuccWork> work)
    { 
        String methodName = "handleNewJobs";

        // Map of updates to send to OR
        HashMap<DuccId, ServiceDependency> updates = new HashMap<DuccId, ServiceDependency>();

        for ( DuccId id : work.keySet() ) {
            DuccWorkJob w = (DuccWorkJob) work.get(id);

            if ( !w.isActive() ) {
                logger.info(methodName, id, "Bypassing inactive job, state =", w.getStateObject());
                continue;
            }

            ServiceDependency s = new ServiceDependency(); // for the OR
            updates.put(id, s);

            String[] deps = w.getServiceDependencies();
            if ( deps == null ) {   // no deps, just mark it running and move on
                s.setState(ServiceState.Available);
                logger.info(methodName, id, "Added to map, no service dependencies.");
                continue;
            }

            //
            // Get dependency references and fire up their state machines
            //
            Map<String, ServiceSet> jobServices = resolveDependencies(w, s);
            for ( ServiceSet sset : jobServices.values() ) {
                sset.establish();
            }
            resolveState(id, s);
            logger.info(methodName, id, "Added job to map, with service dependency state.", s.getState());
        }

        serviceMap.putAll(updates);
    }

    /**
     * A job or service has ended.  Here's common code to clean up the dependent services.
     * @param id - the id of the job or service that stopped
     * @param deps - the services that 'id' was dependent upon
     */
    protected void stopDependentServices(DuccId id)
    {
    	String methodName = "stopDependentServices";

        Map<String, ServiceSet> deps = serviceStateHandler.getServicesForJob(id);
        if ( deps == null ) {
            logger.debug(methodName, id, "No dependent services to stop, returning.");
            return;                                              // service already deleted, timing issue
        }

        //
        // Bop through all the things job 'id' is dependent upon, and update their refcounts. If
        // the refs go to 0 we stop the pinger and sometimes the independent service itself. 
        //
        for ( String dep : deps.keySet() ) {
            logger.debug(methodName, id, "Looking up service", dep);
            
            ServiceSet sset = deps.get(dep);
            if ( sset == null ) {
                throw new IllegalStateException("Null service for " + dep);      // sanity check, should never happen
            }
            
            int count = sset.dereference(id);                                    // also maybe stops the pinger
            logger.debug(methodName, id, "Ref count for", sset.getKey(), "goes down to", count);
            if ( count == 0 ) {
                if ( sset.isImplicit() ) {
                    logger.debug(methodName, id, "Removing unreferenced implicit service", dep, "refcount", count);
                    serviceStateHandler.removeService(dep);
                }
                if ( sset.isRegistered() && sset.isReferencedStart() ) {
                    logger.debug(methodName, id, "Stopping reference-started service", dep, "refcount", count);
                    sset.lingeringStop();
                }

            }
        }

        // last, indicate that job 'id' has nothing its dependent upon any more
        serviceStateHandler.removeServicesForJob(id);            
    }

    protected void handleDeletedJobs(HashMap<DuccId, IDuccWork> work)
    {
        String methodName = "handleCompletedJobs";

        for ( DuccId id : work.keySet() ) {
            DuccWorkJob w = (DuccWorkJob) work.get(id);
            
            String[] deps = w.getServiceDependencies();
            if ( deps == null ) {   // no deps, just mark it running and move on
                logger.info(methodName, id, "No service dependencies, no updates made.");
                continue;
            }

            stopDependentServices(id);

            logger.info(methodName, id, "Deleted job from map");
        }

        serviceMap.removeAll(work.keySet());
    }

    protected void handleModifiedJobs(HashMap<DuccId, IDuccWork> work)
    {
        String methodName = "handleModifiedJobs";

        //
        // Only look at active jobs.  The others will be going away soon and we use
        // that time as a grace period to keep the management machinery running in 
        // case more work comes in in the next few minutes.
        //
        // Everything is already in the service map so we just update the state.
        //
        for ( DuccId id : work.keySet() ) {
            
            DuccWorkJob j = (DuccWorkJob) work.get(id);
            String[] deps = j.getServiceDependencies();
            if ( deps == null ) {   // no deps, just mark it running and move on
                logger.info(methodName, id, "No service dependencies, no updates made.");
                continue;
            }

            ServiceDependency s = serviceMap.get(id);
            if ( j.isFinished() ) {
                stopDependentServices(id);
                s.setState(ServiceState.NotAvailable);
            } else  if ( j.isActive() ) {
                resolveState(id, s);
            } 
        }

    }

    protected void handleNewServices(HashMap<DuccId, IDuccWork> work)
    {
        String methodName = "handleNewServices";

        Map<DuccId, ServiceDependency>  updates = new HashMap<DuccId, ServiceDependency>();   // to be added to the service map sent to OR
        Map<String, ServiceSet>     newservices = new HashMap<String, ServiceSet>();          // to be added to our internal maps in serviceState
        for ( DuccId id : work.keySet() ) {
            DuccWorkJob w = (DuccWorkJob) work.get(id);

            // On restart we sometimes get stale stuff that we just ignore.
            // What else? Is the the right thing to do?
            //  
            // TODO: For registered services we probably have to call some sort of recovery code
            if ( !w.isActive() ) {
                logger.info(methodName, id, "Bypassing inactive service, state=", w.getStateObject());
                continue;
            }

            ServiceDependency s = new ServiceDependency();
            updates.put(id, s);

            String endpoint = w.getServiceEndpoint();
            if ( endpoint == null ) {                                     // the job is damaged if this happens
                String msg = "No service endpoint.  Service cannot be validated.";
                logger.warn(methodName, id, msg);
                s.addMessage("null", msg);                                // this is a fatal state always
                s.setState(ServiceState.NotAvailable);
                continue;
            }

            String[] deps = w.getServiceDependencies();                  // other services this svc depends on
            ServiceSet sset = serviceStateHandler.getServiceByName(endpoint);
            if ( sset == null ) {
                // submitted, we just track but not much else
                try {
                    sset = new ServiceSet(id, endpoint, deps);             // creates a "submitted" service
                    serviceStateHandler.putServiceByName(endpoint, sset);
                } catch ( IllegalArgumentException e ) {
                    s.addMessage(endpoint, e.getMessage());
                    s.setState(ServiceState.NotAvailable);
                    continue;
                }
            } else if ( sset.isDeregistered() ) {
                s.addMessage(endpoint, "Duplicate endpoint: terminating deregistered service.");
                s.setState(ServiceState.NotAvailable);
                continue;
            } else if ( sset.matches(id) ) {
                // TODO: not clear we have to do anything here since establish() below will
                //       add to the implementors.  Be sure to update the check so the
                //       code in the following 'else' clause is executed correctly though.

                // and instance/implementor of our own registered services
                sset.addImplementor(id, w.getJobState());
            } else { 
                //
                // If the new service is not a registered service, and it is a duplicate of another service
                // which isn't registered, we allow it to join the party.
                //
                // When it joins, it needs to "propmote" the ServiceSet to "Submitted".
                //
                // a) in the case of "implicit" we don't know enough to many any moral judgements at all
                // b) in the case of "submitted" it could be the user is increasing the pool of servers by
                //    submitting more jobs.  Perhaps we would better handle this via modify but for the moment,
                //    just allow it.
                // c) in the case of "registered" we know and manage everything and don't allow it.  users must
                //    use the services modify api to increase or decrease instances.
                //

                if ( !sset.isRegistered() ) {
                    sset.addImplementor(id, w.getJobState());
                    sset.promote();          // we'll do this explicitly as a reminder that it's happening and
                                             // to insure we NEVER promote a registered service (which is actually
                                             // a demotion!).
                } else {
                    String msg = "Duplicate endpoint: Registered service.";
                    logger.warn(methodName, id, msg);
                    s.addMessage(endpoint, msg);
                    s.setState(ServiceState.NotAvailable);
                    continue;
                }
            }

            // The service is new and unique if we get this far

            //
            // No deps.  Put it in the map and move on.
            //
            if ( deps == null ) {                
                logger.info(methodName, id, "Added service to map, no service dependencies. ");
                s.setState(ServiceState.Available);                        // good to go in the OR (the state of things i'm dependent upon)
                sset.establish(id, w.getJobState());                       // sets my own state based entirely on state of w
                continue;
            }

            Map<String, ServiceSet> jobServices = resolveDependencies(w, s); // 
            for ( ServiceSet depset : jobServices.values() ) {
                depset.establish();
            }
            resolveState(id, s);
            sset.establish(id, w.getJobState());
            logger.info(methodName, id, "Added to map, with service dependencies,", s.getState());
        }

        serviceStateHandler.recordNewServices(newservices);
        serviceMap.putAll(updates);
    }

    //
    // We're here because we got OR state for the service that it has stopped running.
    // Must clean up.
    //
    protected void handleDeletedServices(HashMap<DuccId, IDuccWork> work)
    {
        String methodName = "handleDeletedServices";

        for ( DuccId id : work.keySet() ) {
        	DuccWorkJob w = (DuccWorkJob) work.get(id);
        	String endpoint = w.getServiceEndpoint();
            logger.info(methodName, id, "Deleted service:", endpoint);
            
            // 
            // Dereference and maybe stop the services I'm dependent upon
            //
            if ( w.getServiceDependencies() == null ) { 
                logger.info(methodName, id, "No service dependencies to update on removal.");
            } else {
                stopDependentServices(id);        // update references, remove implicit services if any
            }

            if (endpoint == null ) {              // probably impossible but lets not chance NPE
                logger.warn(methodName, id, "Missing service endpoint, ignoring.");
                continue;
            }
            ServiceSet sset = serviceStateHandler.getServiceByName(endpoint);            

            // may have been removed already if we saw it go to complet[ed/ing] and it lingered a while anyway, which is usual
            if ( sset != null ) {                
                sset.removeImplementor(id);                      // also stops the ping thread if it's the last one
            }
        }

        //serviceStateHandler.removeServicesForJobs(work.keySet());   // services we were dependent upon
        serviceMap.removeAll(work.keySet());                          // and finally the deleted services
                                                                      // from the published map
    }
    
    protected void handleModifiedServices(HashMap<DuccId, IDuccWork> work)
    {
        String methodName = "handleModifiedServices";        
        
        //
        // This is a specific service process, but not necessarily the whole service.
        //
        for ( DuccId id : work.keySet() ) {
            DuccWorkJob w = (DuccWorkJob) work.get(id);
            String endpoint = w.getServiceEndpoint();

            if (endpoint == null ) {              // probably impossible but lets not chance NPE
                logger.info(methodName, id, "Missing service endpoint, ignoring.");
                continue;
            }

            ServiceSet sset = serviceStateHandler.getServiceByName(endpoint);
            if ( sset == null ) {
                // may have already died and this is just leftover OR publications.
                if ( w.isActive() ) {             // or maybe we just screwed up!
                    try {
                        throw new IllegalStateException("Got update for service " + id.toString() + " but no ServiceSet! Job state: " + w.getJobState());
                    } catch ( Throwable t ) {
                        // catch and log stack but don't crash SM
                        logger.error(methodName, id, t);
                        continue;
                    }
                }
                continue;
            }

            ServiceDependency s = serviceMap.get(id);
            if ( w.isFinished() ) {              // nothing more, just dereference and maybe stop stuff I'm dependent upon
                stopDependentServices(id);
                s.setState(ServiceState.NotAvailable);              // tell orchestrator
            } else  if ( w.getServiceDependencies() != null ) {     // update state from things I'm dependent upon
                resolveState(id, s);
            } 

            // now factor in cumulative state of the implementors and manage the ping thread as needed
            sset.establish(id, w.getJobState());

            if ( (sset.getServiceState() == ServiceState.NotAvailable) && (sset.countReferences() == 0) ) {
                // this service is now toast.  remove from our maps asap to avoid clashes if it gets
                // resubmitted before the OR can purge it.
                if ( ! sset.isRegistered() ) {
                    logger.debug(methodName, id, "Removing service", endpoint, "because it died and has no more references.");
                    serviceStateHandler.removeService(endpoint);
                }
                serviceStateHandler.removeServicesForJob(id);
            }
        }
        
    }

    /**
     * Add in the service dependencies to the query.
     */
    void updateServiceQuery(ServiceDescription sd, ServiceSet sset)
    {

        if ( sset.isRegistered() ) {
            // 
            // The thing may not be running yet / at-all.  Pull out the deps from the registration and
            // query them individually.
            //
            String[] deps = sset.getIndependentServices();
            if ( deps != null ) {
                for ( String dep : deps ) {
                    ServiceSet independent = serviceStateHandler.getServiceByName(dep);
                    if ( independent != null ) {
                        sd.addDependency(dep, independent.getServiceState().decode());
                    } else {
                        sd.addDependency(dep, ServiceState.NotAvailable.decode());
                    }
                }
            }
        } else {
            //
            // If it's not registered we have to look up all the dependencies of the implementors instead
            //
            Map<DuccId, JobState> implementors = sset.getImplementors();
            for ( DuccId id : implementors.keySet() ) {
                Map<String, ServiceSet> deps = serviceStateHandler.getServicesForJob(id); // all the stuff 'id' is dependent upon
                if ( deps != null ) {
                    for ( String s : deps.keySet() ) {
                        ServiceSet depsvc = deps.get(s);
                        sd.addDependency(s, depsvc.getServiceState().decode());
                    }
                }                
            }
        }
    }

    ServiceQueryReplyEvent query(ServiceQueryEvent ev)
    {
    	//String methodName = "query";
        long friendly = ev.getFriendly();
        String epname = ev.getEndpoint();
        ServiceQueryReplyEvent reply = new ServiceQueryReplyEvent();

        if (( friendly == -1) && ( epname == null )) {
            ArrayList<String> keys = serviceStateHandler.getServiceNames();
            for ( String k : keys ) {
                ServiceSet sset = serviceStateHandler.getServiceByName(k);
                if ( k == null ) continue;                    // the unlikely event it changed out from under us
                
                ServiceDescription sd = sset.query();
                updateServiceQuery(sd, sset);
                reply.addService(sd);
            }
        } else {
            ServiceSet sset = serviceStateHandler.getServiceForApi(friendly, epname);
            ServiceDescription sd = sset.query();
            updateServiceQuery(sd, sset);
            reply.addService(sd);
        } 

        return reply;
    }

    ServiceReplyEvent start(ServiceStartEvent ev)
    {
        //String methodName = "start";
        
        long friendly = ev.getFriendly();
        String epname = ev.getEndpoint();
        String serviceIdString = extractId(friendly, epname);
        ServiceSet sset = serviceStateHandler.getServiceForApi(friendly, epname);

        if ( sset == null ) {
            return new ServiceReplyEvent(ServiceCode.NOTOK, "Service " + serviceIdString + " does not exist.", null, null);
        }

        if ( sset.isRegistered() ) {
            int running = sset.countImplementors();
            int instances = ev.getInstances();
            int registered = sset.getNInstances();
            int wanted = 0;

            if ( instances == 0 ) {
                wanted = Math.max(0, registered - running);
            } else {
                wanted = instances;
            }
            if ( wanted == 0 ) {
                return new ServiceReplyEvent(ServiceCode.NOTOK, 
                                             "Service " + serviceIdString + " is started, instances[" + running + "]: ", 
                                             sset.getKey(), 
                                             sset.getId());
            }

            // only start something if we don't have enought already going
            ApiHandler  apih = new ApiHandler(ev, this);
            Thread t = new Thread(apih);
            t.start();
            return new ServiceReplyEvent(ServiceCode.OK, 
                                         "Service " + serviceIdString + " start request accepted, new instances[" + wanted + "]", 
                                         sset.getKey(), 
                                         sset.getId());
        } else {
            return new ServiceReplyEvent(ServiceCode.NOTOK, 
                                         "Service " + serviceIdString + " is not a registered service.", 
                                         sset.getKey(), 
                                         null);    
        }
    }

    //
    // Everything to do this must be vetted before it is called
    //
    // Start with no instance says: start enough new processes to get up the registered amount
    // Start with some instances says: start exactly this many
    // If the --save option is included, also update the registration
    //
    void doStart(long friendly, String epname, int instances, boolean update)
    {
    	//String methodName = "doStart";
        ServiceSet sset = serviceStateHandler.getServiceForApi(friendly, epname);

        int running    = sset.countImplementors();
        int registered = sset.getNInstances();
        int wanted     = 0;

        if ( instances == 0 ) {
            wanted = Math.max(0, registered - running);
        } else {
            wanted = instances;
        }

        if ( update ) {
            sset.setNInstances(running + instances);
        }
                          
        for ( int i = 0; i < wanted; i++ ) {
            sset.start();
        } 


    }

    ServiceReplyEvent stop(ServiceStopEvent ev)
    {
        long friendly = ev.getFriendly();
        String epname = ev.getEndpoint();
        String serviceIdString = extractId(friendly, epname);
        ServiceSet sset = serviceStateHandler.getServiceForApi(friendly, epname);

        if ( sset == null ) {
            return new ServiceReplyEvent(ServiceCode.NOTOK, "Service " + serviceIdString + " does not exist.", null, null);
        }

        if ( sset.isRegistered() ) {
            if ( (sset.countImplementors() == 0) && ( sset.isUimaAs()) ) {
                return new ServiceReplyEvent(ServiceCode.NOTOK, "Service " + serviceIdString + " is already stopped.", sset.getKey(), sset.getId());
            }

            int running    = sset.countImplementors();
            int instances  = ev.getInstances();
            int tolose;
            if ( instances == 0 ) {
                tolose     = running;
            } else {
                tolose     = Math.min(instances, running);
            }
            
            ApiHandler  apih = new ApiHandler(ev, this);

            Thread t = new Thread(apih);
            t.start();
            return new ServiceReplyEvent(ServiceCode.OK, "Service " + serviceIdString + " stop request accepted for [" + tolose + "] instances.", sset.getKey(), sset.getId());
        } else {
            return new ServiceReplyEvent(ServiceCode.NOTOK, "Service " + friendly + " is not a registered service.", sset.getKey(), null);            
        }

    }

    //
    // Everything to do this must be vetted before it is called
    //
    // If instances == 0 set stop the whole service
    // Otherwise we just stop the number asked for
    // If --save is insicated we update the registry
    //
    void doStop(long friendly, String epname, int instances, boolean update)
    {
        //String methodName = "doStop";

        ServiceSet sset = serviceStateHandler.getServiceForApi(friendly, epname);

        int running    = sset.countImplementors();
        int tolose;
        if ( instances == 0 ) {
            tolose = running;
        } else {
            tolose = Math.min(instances, running);
        }

        if ( update ) {
            sset.setNInstances(Math.max(0, running - instances)); // never persist < 0 registered instance
        }
        
        if ( tolose == running ) {
            sset.stop();                                  // blind stop
        } else {
            sset.stop(tolose);                            // selective stop (TODO: eventually)
        }
    }

    ServiceReplyEvent register(DuccId id, String props_filename, String meta_filename, DuccProperties props, DuccProperties meta)
    {
    	String methodName = "register";
        ServiceSet sset = new ServiceSet(id, props_filename, meta_filename, props, meta);
        String key = sset.getKey();
        
        String error = null;
        boolean must_deregister = false;
        if (serviceStateHandler.getServiceByName(key) == null ) {
            try {
                props.store(new FileOutputStream(props_filename), "Service descriptor.");
            } catch ( Exception e ) {
                error = ("Internal error; unable to store service descriptor. " + key);
                logger.error(methodName, id, e);
                must_deregister = true;
            }
            
            try {
                if ( ! must_deregister ) {
                    meta.store(new FileOutputStream(meta_filename), "Meta descriptor");
                }
            } catch ( Exception e ) {
                error = ("Internal error; unable to store service meta-descriptor. " + key);
                logger.error(methodName, id, e);
                must_deregister = true;
            }

            // must check for cycles or we can deadlock
            if ( ! must_deregister ) {
                CycleChecker cc = new CycleChecker(sset);
                if ( cc.hasCycle() ) {
                    error = ("Service dependencies contain a cycle with " + cc.getCycles());
                    logger.error(methodName, id, error);
                    must_deregister = true;
                }
            }
        } else {
            error = ("Duplicate service: " + key + ".  Registration fails");
        }

        if ( error == null ) {
            serviceStateHandler.putServiceByName(sset.getKey(), sset);
            return new ServiceReplyEvent(ServiceCode.OK, "Registered service.", key, id);
        } else {
            File mf = new File(meta_filename);
            mf.delete();
            
            File pf = new File(props_filename);
            pf.delete();
            return new ServiceReplyEvent(ServiceCode.NOTOK, error, key, id);
        }
    }

    public ServiceReplyEvent modify(ServiceModifyEvent ev)
    {
        long friendly       = ev.getFriendly();
        String epname = ev.getEndpoint();
        String serviceIdString = extractId(friendly, epname);
    	ServiceSet sset = serviceStateHandler.getServiceForApi(friendly, epname);

    	if ( sset.isRegistered() ) {            
            ApiHandler  apih = new ApiHandler(ev, this);

            Thread t = new Thread(apih);
            t.start();
            return new ServiceReplyEvent(ServiceCode.OK, "Service " + serviceIdString + " modify request accepted.", sset.getKey(), sset.getId());
        } else {
            return new ServiceReplyEvent(ServiceCode.NOTOK, "Service " + friendly + " is not a registered service.", sset.getKey(), null);            
        }
    }

    void doModify(long friendly, String epname, int instances, Trinary autostart, boolean activate)
    {
        //String methodName = "doStop";

        ServiceSet sset = serviceStateHandler.getServiceForApi(friendly, epname);
        
        if ( instances >= 0 ) {
            sset.setNInstances(instances);                // also persists instances
        }

        if ( autostart != Trinary.Unset ) {
            sset.setAutostart(autostart.decode());
        }

        if ( activate ) {
            int running    = sset.countImplementors();
            int diff = instances - running;
            
            if ( diff > 0 ) {
                while ( diff-- > 0 ) {
                    sset.start();
                }
            } else if ( diff < 0 ) {
                sset.stop(-diff);
            }
        }
    }

    public ServiceReplyEvent unregister(ServiceUnregisterEvent ev)
    {
        long friendly = ev.getFriendly();
        String epname = ev.getEndpoint();
        String serviceIdString = extractId(friendly, epname);
        ServiceSet sset = serviceStateHandler.getServiceForApi(friendly, epname);

        if ( sset == null ) {
            return new ServiceReplyEvent(ServiceCode.NOTOK, "Service " + serviceIdString + " does not exist.",  serviceIdString, null);
        }

        if ( sset.isRegistered() ) {            
            sset.deregister();          // just sets a flag so we know how to handle it when it starts to die
            ApiHandler  apih = new ApiHandler(ev, this);
            Thread t = new Thread(apih);
            t.start();
            return new ServiceReplyEvent(ServiceCode.OK, "Service " + serviceIdString + " unregistered. Shutting down implementors.", sset.getKey(), sset.getId());
        } else {
            return new ServiceReplyEvent(ServiceCode.NOTOK, "Service " + serviceIdString + " is not a registered service.", sset.getKey(), null);            
        }
        
    }

    //
    // Everything to do this must be vetted before it is called. Run in a new thread to not hold up the API.
    //
    void doUnregister(long friendly, String epname)
    {
    	String methodName = "doUnregister";
        ServiceSet sset = serviceStateHandler.getServiceForApi(friendly, epname);                

        if ( sset.countImplementors() > 0 ) {
            logger.debug(methodName, sset.getId(), "Stopping implementors:", friendly, epname);
            sset.stop();
        } else {
            logger.debug(methodName, sset.getId(), "Removing from map:", friendly, epname);
            serviceStateHandler.removeService(epname, friendly);
        }

        String metafn =  sset.getMetaFilename();
        String propsfn = sset.getPropsFilename();

        if ( metafn != null ) {
            File mf = new File(metafn);
            mf.delete();
        }
        if ( propsfn != null ) {
            File pf = new File(propsfn);
            pf.delete();
        }

    }

    String extractId(long friendly, String epname)
    {
        return ((epname == null) ? Long.toString(friendly) : epname);
    }

    /**
     * From: http://en.wikipedia.org/wiki/Topological_sorting
     *
     * L �? Empty list that will contain the sorted elements
     * S �? Set of all nodes with no incoming edges
     * while S is non-empty do
     *     remove a node n from S
     *     insert n into L
     *     for each node m with an edge e from n to m do
     *         remove edge e from the graph
     *         if m has no other incoming edges then
     *             insert m into S
     * if graph has edges then
     *     return error (graph has at least one cycle)
     * else 
     *     return L (a topologically sorted order)
     */
    class CycleChecker
    {
        ServiceSet sset;
        int edges = 0;
        List<String> cycles = null;

        CycleChecker(ServiceSet sset)
        {
            this.sset = sset;
        }

        boolean hasCycle()
        {
            // Start by building the dependency graph
            // TODO: Maybe consider saving this.  Not clear there's much of a
            //       gain doing the extra bookeeping beause the graphs will always
            //       be small and will only need checking on registration or arrival
            //       of a submitted service.  So this cycle checking is always
            //       fast anyway.
            //
            //       Bookeeping could be a bit ugly because a submitted service could
            //       bop in and change some dependency graph.  We really only care
            //       for checking cycles, so we'll check the cycles as things change
            //       and then forget about it.
            //
            String[] deps = sset.getIndependentServices();
            if ( deps == null ) return false;          // man, that was fast!

            Map<String, ServiceSet> visited = new HashMap<String, ServiceSet>();     // all the nodes in the graph
            clearEdges(sset, visited);

            List<ServiceSet> nodes = new ArrayList<ServiceSet>();
            nodes.addAll(visited.values());
            buildGraph(nodes);
            
            List<ServiceSet>        sorted = new ArrayList<ServiceSet>();          // topo-sorted list of nodes
            List<ServiceSet>        current = new ArrayList<ServiceSet>();         // nodes with no incoming edges

            // Constant: current has all nodes with no incoming edges
            for ( ServiceSet node : nodes ) {
                if ( ! node.hasPredecessor() ) current.add(node);
            }

            while ( current.size() > 0 ) {
                ServiceSet next = current.remove(0);                            // remove a node n from S
                sorted.add(next);                                               // insert n int L
                List<ServiceSet> successors = next.getSuccessors();
                for ( ServiceSet succ : successors ) {                          // for each node m(pred) with an edge e from n to m do
                    next.removeSuccessor(succ);                                 // remove edge from graph
                    succ.removePredecessor(next);                               //    ...
                    edges--;
                    if ( !succ.hasPredecessor() ) current.add(succ);            // if m(pred) has no incoming edges insert m into S
                }
            }

            if ( edges == 0 ) return false;                                     // if graph has no edges, no cycles

            cycles = new ArrayList<String>();                                   // oops, and here they are
            for ( ServiceSet node : nodes ) {
                if ( node.hasSuccessor() ) {
                    for ( ServiceSet succ : node.getSuccessors() ) {
                        cycles.add(node.getKey() + " -> " + succ.getKey());
                    }
                }
            }
            return true;
        }

        String getCycles()
        {
            return cycles.toString();
        }
        
        //
        // Traveerse the graph and make sure all the nodes are "clean" 
        //
        void clearEdges(ServiceSet node, Map<String, ServiceSet> visited)
        {
            String key = node.getKey();
            node.clearEdges();
            if ( visited.containsKey(key) ) return;

            visited.put(node.getKey(), node);
            String[] deps = node.getIndependentServices();
            if ( deps == null ) return;
            
            for ( String dep : deps ) {
                ServiceSet sset = serviceStateHandler.getServiceByName(dep);
                clearEdges(sset, visited);
            }
        }
            
        void buildGraph(List<ServiceSet> nodes)
        {            
            for ( ServiceSet node : nodes ) {           
                String[] deps = node.getIndependentServices();           // never null if we get this far
                if ( deps != null ) {
                    for ( String d : deps ) {
                        ServiceSet outgoing = serviceStateHandler.getServiceByName(d);
                        outgoing.setIncoming(node);
                        node.setOutgoing(outgoing);
                        edges++;
                    }
                }
            }
        }
    }

    class ServiceStateHandler
    {

        // Map of active service descriptors by endpoint.  For UIMA services, key is the endpoint.
        private HashMap<String,  ServiceSet>  servicesByName     = new HashMap<String,  ServiceSet>();
        private HashMap<Long,    ServiceSet>  servicesByFriendly = new HashMap<Long,    ServiceSet>();

        // For each job, the collection of services it is dependent upon
        // DUccId is a Job Id (or id for serice that has dependencies)
        private HashMap<DuccId, Map<String, ServiceSet>>  servicesByJob = new HashMap<DuccId, Map<String, ServiceSet>>();

        ServiceStateHandler()
        {
        }

        /**
         * Return a copy of the keys so we can fetch the services in an orderly manner.
         */
        synchronized ArrayList<String> getServiceNames()
        {
            ArrayList<String> answer = new ArrayList<String>();
            for ( String k : servicesByName.keySet() ) {
                answer.add(k);
            }
            return answer;
        }

        synchronized ServiceSet getServiceByName(String n)
        {
            return servicesByName.get(n);
        }

        synchronized ServiceSet getServiceByFriendly(long id)
        {
            return servicesByFriendly.get( id );
        }

        // API passes in a friendly (maybe) and an endpiont (maybe) but only one of these
        // Here we look up the service by whatever was passed in.
        synchronized ServiceSet  getServiceForApi(long id, String n)
        {
            if ( n == null ) return getServiceByFriendly(id);
            return getServiceByName(n);
        }

        synchronized List<ServiceSet> getRegisteredServices()
        {
            ArrayList<ServiceSet> answer = new ArrayList<ServiceSet>();
            for ( ServiceSet sset : servicesByName.values() ) {
                if ( sset.isRegistered() ) {
                    answer.add(sset);
                }
            }
            return answer;
        }

        synchronized void putServiceByName(String n, ServiceSet s)
        {
            servicesByName.put(n, s);
            DuccId id = s.getId();
            if ( id != null ) {
                servicesByFriendly.put(id.getFriendly(), s);
            }
        }

        synchronized ServiceSet removeService(String n)
        {
            ServiceSet s = servicesByName.remove(n);
            if ( s != null ) {
                DuccId id = s.getId();
                if ( id != null ) {
                    servicesByFriendly.remove(id.getFriendly());
                }
            }
            return s;
        }

        synchronized void removeService(long id)
        {
            ServiceSet sset = servicesByFriendly.remove(id);
            if ( sset != null ) {
                String key = sset.getKey();
                servicesByName.remove(key);
            }
        }

        synchronized void removeService(String n, long id)
        {
            if ( n == null ) removeService(id);
            else             removeService(n);
        }

        synchronized Map<String, ServiceSet> getServicesForJob(DuccId id)
        {

        	return servicesByJob.get(id);
        }

        synchronized void putServiceForJob(DuccId id, ServiceSet s)
        {
            Map<String, ServiceSet> services = servicesByJob.get(id);
            if ( services == null ) {
                services = new HashMap<String, ServiceSet>();
                servicesByJob.put(id, services);
            }
            services.put(s.getKey(), s);
        }

        synchronized void removeServicesForJob(DuccId id)
        {
            servicesByJob.remove(id);
        }

        synchronized void recordNewServices(Map<String, ServiceSet> services) 
        {
            servicesByName.putAll(services);
        }

    }

    /**
     * Tester for topo sorter.
     * Input is props file, e.g. for the graph:
     *   A -> B, A -> C, B -> C:
     *
     *    services = A B C
     *    svc.A = B C
     *    svc.B = C
     *    svc.C = 
     *
     */
    private void runSortTester(String propsfile)
    {
        int friendly = 1;
        DuccProperties props = new DuccProperties();
        try {
			props.load(propsfile);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
            System.exit(1);
		}

        
        String svcnames = props.getStringProperty("services");
        String[] svcs = svcnames.split("\\s");
        ServiceSet[] allServices = new ServiceSet[svcs.length];
        int ndx = 0;
        for ( String svc : svcs ) {
            svc = svc.trim();
            String key = "UIMA-AS:" + svc + ":tcp://foo:123";
            ServiceSet dep = serviceStateHandler.getServiceByName(key);
            if ( dep == null ) {
                dep = new ServiceSet(new DuccId(friendly++), key, null);
                serviceStateHandler.putServiceByName(key, dep);
                allServices[ndx++] = dep;
            }

            String depnames = props.getStringProperty("svc." + svc);
            String[] deps = depnames.split("\\s");
            List<String> subdeps = new ArrayList<String>();
            for ( String subsvc : deps ) {
                subsvc = subsvc.trim();
                if ( subsvc.equals("")) continue;

                String subkey = "UIMA-AS:" + subsvc + ":tcp://foo:123";
                ServiceSet subdep = serviceStateHandler.getServiceByName(subkey);
                if ( subdep == null ) {
                    subdep = new ServiceSet(new DuccId(friendly++), subkey, null);
                    serviceStateHandler.putServiceByName(subkey, subdep);
                    allServices[ndx++] = subdep;
                }
                subdeps.add(subkey);
            }
            if ( subdeps.size() > 0 ) {
            	dep.setIndependentServices(subdeps.toArray(new String[subdeps.size()]));
            }
        }

        CycleChecker cc = new CycleChecker(allServices[0]);
        if ( cc.hasCycle() ) {
            System.out.println("Service dependencies contain a cycle with " + cc.getCycles());
        } else {
            System.out.println("No cycles detected");
        }

    }

    // tester for the topo sorter
    public static void main(String[] args)
    {
        ServiceHandler sh = new ServiceHandler(null);
        sh.runSortTester(args[0]);
    }
}