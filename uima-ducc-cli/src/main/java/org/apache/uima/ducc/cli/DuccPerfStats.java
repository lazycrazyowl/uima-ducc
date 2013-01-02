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
package org.apache.uima.ducc.cli;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.uima.ducc.common.jd.files.IWorkItemState;
import org.apache.uima.ducc.common.jd.files.WorkItemStateManager;
import org.apache.uima.ducc.common.jd.files.IWorkItemState.State;
import org.apache.uima.ducc.transport.event.jd.PerformanceMetricsSummaryItem;
import org.apache.uima.ducc.transport.event.jd.PerformanceMetricsSummaryMap;
import org.apache.uima.ducc.transport.event.jd.PerformanceSummaryReader;



public class DuccPerfStats 
{     
    boolean csv = false;
    boolean summary = false;
    boolean workitems = false;

    String dir = ".";

    SummarySort  summarySort = SummarySort.TotalTime;
    WorkItemSort workItemSort = WorkItemSort.Seq;

    public enum ClOptions
    {
        //
        // I want to expose these things to the API with the ugly upper-case notation but don't
        // want that ugliness in the variables, so we have encode and decode routines to do the
        // correct translation.
        //
        Summary  { 
            public String decode()      { return "summary" ; } 
            public String description() { return "Display only job summary statistics (default)." ; } 
            public String argname()     { return null ; } 
        },
        WorkItems       { 
            public String decode()      { return "workitems"; } 
            public String description() { return "Display only work item statistics." ; } 
            public String argname()     { return null ; } 
            },
        Csv        { 
            public String decode()      { return "csv"; } 
            public String description() { return "Format display in CSV." ; } 
            public String argname()     { return null ; } 
        },
        Job      { 
            public String decode()      { return "job"; } 
            public String description() { return "This is the numeric ID of the job." ; } 
            public String argname()     { return "integer" ; } 
        },
        Logdir       { 
            public String decode()      { return "directory"; } 
            public String description() { return "This is your DUCC log directory." ; } 
            public String argname()     { return "Directory-name" ; } 
        },
        Sort        { 
            public String decode()      { return "sort"; } 
            public String description() { return "This is the field to sort on." ; } 
            public String argname()     { return "String name of sort field" ; } 
        },
        Help        { 
            public String decode()      { return "help"; } 
            public String description() { return "This help message." ; } 
            public String argname()     { return null ; } 
        },
        Unknown     { 
            public String decode()      { return "unknown"; } 
            public String description() { return "unknown" ; } 
            public String argname()     { return null ; } 
        },
        ;
    
        public abstract String decode();
        public abstract String description();
        public abstract String argname();

        public static ClOptions encode(String value)
        {
            if ( value.equals("summary") )   return Summary;
            if ( value.equals("workitems") ) return WorkItems;
            if ( value.equals("csv") )       return Csv;
            if ( value.equals("job") )       return Job;
            if ( value.equals("logdir") )    return Logdir;
            if ( value.equals("help") )      return Help;

            return Unknown;
        }
    }
    ;

    public enum SummarySort
    {
        Name    { 
            public String decode()      { return "name"; } 
            public String description() { return "Sort by AE name."; } 
        },

        TotalTime  { 
            public String decode()      { return "total" ; } 
            public String description() { return "Sort by AE total processign time (default)." ; } 
        },
        MaxTime       { 
            public String decode()      { return "max"; } 
            public String description() { return "Sort by maximum processing time." ; } 
            },
        MinTime        { 
            public String decode()      { return "min"; } 
            public String description() { return "Sort by minimum processing time." ; } 
        },
        ItemsProcessed      { 
            public String decode()      { return "items"; } 
            public String description() { return "Sort by items processed." ; } 
        },
        Unknown     { 
            public String decode()      { return "unknown"; } 
            public String description() { return "unknown" ; } 
        },
        ;
        public abstract String decode();
        public abstract String description();

        public static SummarySort encode(String value)
        {
            if ( value.equals("name") )  return Name;
            if ( value.equals("total") ) return TotalTime;
            if ( value.equals("max") )   return MaxTime;
            if ( value.equals("min") )   return MinTime;
            if ( value.equals("items") ) return ItemsProcessed;
            return Unknown;
        }
    }
    ;

    public enum WorkItemSort
    {
        //
        // I want to expose these things to the API with the ugly upper-case notation but don't
        // want that ugliness in the variables, so we have encode and decode routines to do the
        // correct translation.
        //
        Seq    { 
            public String decode()      { return "seq"; } 
            public String description() { return "Sort by work item sequence number."; } 
        },
        Id  { 
            public String decode()      { return "id" ; } 
            public String description() { return "Sort by work item ID." ; } 
        },
        State       { 
            public String decode()      { return "state"; } 
            public String description() { return "Sort by work item state." ; } 
            },
        QTime        { 
            public String decode()      { return "qtime"; } 
            public String description() { return "Sort by work item enqueue overhead time." ; } 
        },
        ProcessTime      { 
            public String decode()      { return "ptime"; } 
            public String description() { return "Sort by work item process time." ; } 
        },
        Node      { 
            public String decode()      { return "node"; } 
            public String description() { return "Sort by work item execution node." ; } 
        },
        Pid      { 
            public String decode()      { return "pid"; } 
            public String description() { return "Sort by work item process id." ; } 
        },
        Unknown     { 
            public String decode()      { return "unknown"; } 
            public String description() { return "unknown" ; } 
        },
        ;

        public abstract String decode();
        public abstract String description();
        
        public static WorkItemSort encode(String value)
        {
            if ( value.equals("seq") )   return Seq;
            if ( value.equals("id") )    return Id;
            if ( value.equals("state") ) return State;
            if ( value.equals("qtime") ) return QTime;
            if ( value.equals("ptime") ) return ProcessTime;
            if ( value.equals("node") )  return Node;
            if ( value.equals("pid") )   return Pid;
            return Unknown;
        }
    };

    public DuccPerfStats()
    {
//         if ( csvstr.equals("csv") ) csv = true;
//         this.sel = sel;
        
//         String jobdir  = (jobid  == null) ? "." : jobid;
//         this.logdir = (logdir == null) ? jobdir: (logdir + "/" + jobdir);

    }

    static void usage(String msg)
    {
        if ( msg != null ) {
            System.out.println(msg);
        }
        System.exit(1);
    }

	static void usage(Options options) 
    {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(DuccPerfStats.class.getName(), options);

        StringBuffer sb = new StringBuffer();
        for ( SummarySort ss : SummarySort.values() ) {
            if ( ss == SummarySort.Unknown ) continue;
            sb.append(ss.decode());
            sb.append(" ");                
        }
        String summaryFields = sb.toString();


        sb = new StringBuffer();
        for ( WorkItemSort ss : WorkItemSort.values() ) {
            if ( ss == WorkItemSort.Unknown ) continue;
            sb.append(ss.decode());
            sb.append(" ");                
        }
        String workItemFields = sb.toString();

        System.out.println("If no log directory is provided the CURRENT directory is searched for the specified job logs.");

        System.out.println("   If no job id is provided, the CURRENT directory is assumed to be the job's log directory and");
        System.out.println("is searched for stats files.");

        System.out.println("Sort fields for job summary: " + summaryFields);
        System.out.println("Sort fields for work items : " + workItemFields);
        
        System.out.println("Examples:");
        System.out.println("Format job summary statistics from the current directory:");
        System.out.println("   ducc_perf_stats -summary");
        System.out.println("");
        System.out.println("Format work item detailsfrom the current directory:");
        System.out.println("   ducc_perf_stats -workitems");
        System.out.println("");
        System.out.println("Format job summary statistics from some log directory and print in CSV:");
        System.out.println("   ducc_perf_stats -logdir /home/bob/ducc/logs -job 33 -summary -csv");
        System.out.println("");
        System.out.println("When using CSV, the first line of the job summary stats contains two numbers");
        System.out.println("  numitems numcas");
        System.out.println("Where numitems is the number of analytics found in the stats file.");
        System.out.println("Where numcas   is the number of input CASs (work items) processed.");        
        System.exit(0);
	}

    @SuppressWarnings("static-access")
	protected void addOptions(Options options)
    {
        for ( ClOptions opt : ClOptions.values() ) {
            if ( opt == ClOptions.Unknown ) continue;
            if ( opt.argname() == null ) {
                options.addOption(OptionBuilder
                                  .withLongOpt    (opt.decode())
                                  .withDescription(opt.description())
                                  .create         ()
                                  );
            } else {
                options.addOption(OptionBuilder
                                  .withLongOpt    (opt.decode())
                                  .withDescription(opt.description())
                                  .withArgName    (opt.argname())
                                  .hasArg         (true)
                                  .create()
                                  );

            }
        }
    }

    protected String dup(String s, int count)
    {
        StringBuffer buf = new StringBuffer();
        for ( int i = 0; i < count; i++ ) {
            buf.append(s);
        }
        return buf.toString();
    }

    protected void formatSummary()
    {
        PerformanceSummaryReader psf = new PerformanceSummaryReader(dir);
        PerformanceMetricsSummaryMap pms = psf.readSummary();
        if ( pms == null ) return;

        int cascount = pms.casCount();
        int size = pms.size();
        Set<Entry<String, PerformanceMetricsSummaryItem>> set = pms.entrySet();
        ArrayList<PerformanceMetricsSummaryItem> items = new ArrayList<PerformanceMetricsSummaryItem>();
        int maxl = 0;
        for ( Entry<String, PerformanceMetricsSummaryItem> e : set ) {
            PerformanceMetricsSummaryItem pmi = e.getValue();
            String k = pmi.getName();
            maxl = Math.max(maxl, k.length());
            items.add(pmi);
        }

        Comparator<PerformanceMetricsSummaryItem> sorter = null;
        switch (summarySort) {
            case ItemsProcessed: sorter = new SummaryItemsProcessedSorter(); break;
            case MaxTime:        sorter = new SummaryMaxTimeSorter();  break;
            case MinTime:        sorter = new SummaryMinTimeSorter();  break;
            case Name:           sorter = new SummaryNameSorter();     break;
            case TotalTime:      sorter = new SummaryTimeSorter();     break;
        }

        Collections.sort(items, sorter);
        String fmt;
        if ( csv ) {
            fmt = "%s,%d,%d,%d,%d";
            System.out.println(""+size + "," + cascount);
        } else {
            System.out.println("Size: " + size + " CASCount: " + cascount);
            fmt = "%" + maxl + "s %16s %16s %16s %16s";
            System.out.println(String.format(fmt, "Name", "Total Time", "Max Time", "Min Time", "Items Processed"));
            System.out.println(String.format(fmt, dup("-", maxl), "----------", "--------", "--------", "---------------"));
            fmt = "%" + maxl + "s %16d %16d %16d %16d";
        }
        
        for ( int i = 0; i < items.size(); i++ ) {
            PerformanceMetricsSummaryItem pmi = items.get(i);            
            System.out.println(String.format(fmt, pmi.getName(), pmi.getAnalysisTime(), pmi.getAnalysisTimeMax(), pmi.getAnalysisTimeMin(), pmi.getNumProcessed()));
        }
    }

    protected void formatWorkItems()
    {
        WorkItemStateManager workItemStateManager = new WorkItemStateManager(dir);
        workItemStateManager.importData();
        ConcurrentSkipListMap<Long,IWorkItemState> map = workItemStateManager.getMap();
        int namemax = 0;
        int nodemax = 0;

        ArrayList<IWorkItemState> items = new ArrayList<IWorkItemState>();
        for ( Long l : map.keySet() ) {
            IWorkItemState iws = map.get(l);
            String id   = iws.getWiId();
            String node = iws.getNode();            
            namemax = Math.max(namemax, id.length());
            nodemax = Math.max(nodemax, node.length());
            items.add(iws);
        }

        String fmt;
        if ( csv ) {
            // seq,id,state,overhead,proc,node,pid
            fmt = "%s,%s,%s,%s,%s,%s,%s";
        } else {
            fmt = "%5s %" + namemax + "s %10s %16s %16s %" + nodemax + "s %5s";
            System.out.println(String.format(fmt, "Seq", "Id", "State", "QTime", "ProcTime", "Node", "PID"));
            System.out.println(String.format(fmt, "-----", dup("-", namemax), "----------", "----------------", "----------------", dup("-", nodemax), "-----"));
        }

        Comparator<IWorkItemState> sorter = null;
        switch (workItemSort) {
            case Seq:         sorter = new WorkItemSequenceSorter();    break;
            case Id:          sorter = new WorkItemIdSorter();          break;
            case State:       sorter = new WorkItemStateSorter();       break;
            case QTime:       sorter = new WorkItemQTimeSorter();       break;
            case ProcessTime: sorter = new WorkItemProcessTimeSorter(); break;
            case Node:        sorter = new WorkItemNodeSorter();        break;
            case Pid:         sorter = new WorkItemPidSorter();         break;
        }
        Collections.sort(items, sorter);
        for ( IWorkItemState iws : items ) {
            String seq  = iws.getSeqNo();
            String id   = iws.getWiId();
            String node = iws.getNode();
            String pid  = iws.getPid();
            State state = iws.getState();
            long  proctime = iws.getMillisProcessing();
            long  overhead = iws.getMillisOverhead();

            System.out.println(String.format(fmt, seq, id, state, overhead, proctime, node, pid));
        }
    }

    public void run(String[] args)
    {
        Options options = new Options();
        addOptions(options);

        CommandLineParser parser = new PosixParser();
        CommandLine commandLine = null;
		try {
			commandLine = parser.parse(options, args);
		} catch (ParseException e) {
            usage("Cannot parse command line: " + e.getMessage());            
		}

        if ( commandLine.hasOption(ClOptions.Help.decode()) ) {
            usage(options);
        }

        if ( commandLine.hasOption(ClOptions.Csv.decode()) ) {
            csv = true;
        }
        if ( commandLine.hasOption(ClOptions.Summary.decode()) ) {
            summary = true;
        }
        if ( commandLine.hasOption(ClOptions.WorkItems.decode()) ) {
            workitems = true;
        }

        if ( summary && workitems ) {            // both is an oopsie
            usage("Must specify only one of " + ClOptions.Summary.decode() + " or " + ClOptions.WorkItems.decode());
        }

        if ( ! (summary || workitems) ) {        // neither, use default
            summary = true;
        }

        String logdir = commandLine.getOptionValue(ClOptions.Logdir.decode());
        String job    = commandLine.getOptionValue(ClOptions.Job.decode());
        
        if ( (job == null) && (logdir != null) ) {
            usage("Must specify job if log directory is specified");
        }

        String sortfield = commandLine.getOptionValue(ClOptions.Sort.decode());
        if ( summary ) {
            if ( sortfield != null ) {
                summarySort = SummarySort.encode(sortfield);
                if ( summarySort == SummarySort.Unknown ) {
                    usage("Unrecognized sort field for summary listing: " + sortfield);
                }
            }
        } else {
            if ( sortfield != null ) {
                workItemSort = WorkItemSort.encode(sortfield);
                if ( workItemSort == WorkItemSort.Unknown ) {
                    usage("Unrecognized sort field for work item listing: " + sortfield);
                }
            }
        }

        if ( job != null )    dir = job;
        if ( logdir != null ) dir = logdir + "/" + job;

        File f = new File(dir);
        if ( ! f.exists() || ! f.isDirectory() ) {
            usage(dir + " is does not exist or is not a directory.");
        }

        if ( summary ) {
            formatSummary();
        }
        if ( workitems ) {
            formatWorkItems();
        }
    }

    /** 
     * Options:
     *   -csv
     *      Print as CSV
     */  
    public static void main(String[] args)
    {
        // args - we assume a script front-end will parse args and pass them in in the following format
        // 0 == 'summary' or 'workitems' or 'all'
        // 1 == '-csv' or '-human'
        // 2 == jobid
        // 3 == logdir

        // Everything is optional. Default is to format everything from the current directory
        // in human-readable format.  If a jobid is given, we assume to be in the parent of the
        // log collection and the files in in the dir 'jobid'.  If a logdir is given, we assume
        // the files are in 'logdir/jobid'.

        DuccPerfStats dfs = new DuccPerfStats();
        dfs.run(args);
    }



    static private class SummaryItemsProcessedSorter
        implements Comparator<PerformanceMetricsSummaryItem>
    {
        public int compare(PerformanceMetricsSummaryItem a, PerformanceMetricsSummaryItem b)
        {
            long aa = a.getNumProcessed();
            long bb = b.getNumProcessed();
            return (int) ( bb - aa );
        }
    }
    static private class SummaryMaxTimeSorter
        implements Comparator<PerformanceMetricsSummaryItem>
    {
        public int compare(PerformanceMetricsSummaryItem a, PerformanceMetricsSummaryItem b)
        {
            long aa = a.getAnalysisTimeMax();
            long bb = b.getAnalysisTimeMax();
            return (int) ( bb - aa );
        }
    }
    static private class SummaryMinTimeSorter
        implements Comparator<PerformanceMetricsSummaryItem>
    {
        public int compare(PerformanceMetricsSummaryItem a, PerformanceMetricsSummaryItem b)
        {
            long aa = a.getAnalysisTimeMin();
            long bb = b.getAnalysisTimeMin();
            return (int) ( aa - bb );
        }
    }

    static private class SummaryNameSorter
        implements Comparator<PerformanceMetricsSummaryItem>
    {
        public int compare(PerformanceMetricsSummaryItem a, PerformanceMetricsSummaryItem b)
        {
            String aa = a.getName();
            String bb = b.getName();
            return aa.compareTo(bb);
        }
    }

    static private class SummaryTimeSorter
        implements Comparator<PerformanceMetricsSummaryItem>
    {
        public int compare(PerformanceMetricsSummaryItem a, PerformanceMetricsSummaryItem b)
        {
            long aa = a.getAnalysisTime();
            long bb = b.getAnalysisTime();
            return (int) ( bb - aa );
        }
    }

    static private class WorkItemSequenceSorter
        implements Comparator<IWorkItemState>
    {
        public int compare(IWorkItemState a, IWorkItemState b)
        {
            long aa = Long.parseLong(a.getSeqNo());
            long bb = Long.parseLong(b.getSeqNo());
            return (int) ( aa - bb );
        }
    }

    static private class WorkItemIdSorter
        implements Comparator<IWorkItemState>
    {
        public int compare(IWorkItemState a, IWorkItemState b)
        {
            String aa = a.getWiId();
            String bb = b.getWiId();
            return aa.compareTo(bb);
        }
    }

    static private class WorkItemStateSorter
        implements Comparator<IWorkItemState>
    {
        public int compare(IWorkItemState a, IWorkItemState b)
        {
            State aa = a.getState();
            State bb = b.getState();
            return aa.compareTo(bb);
        }
    }

    static private class WorkItemQTimeSorter
        implements Comparator<IWorkItemState>
    {
        public int compare(IWorkItemState a, IWorkItemState b)
        {
            long aa = a.getMillisOverhead();
            long bb = b.getMillisOverhead();
            return (int) ( bb - aa );
        }
    }

    static private class WorkItemProcessTimeSorter
        implements Comparator<IWorkItemState>
    {
        public int compare(IWorkItemState a, IWorkItemState b)
        {
            long aa = a.getMillisProcessing();
            long bb = b.getMillisProcessing();
            return (int) ( bb - aa );
        }
    }

    static private class WorkItemNodeSorter
        implements Comparator<IWorkItemState>
    {
        public int compare(IWorkItemState a, IWorkItemState b)
        {
            String aa = a.getNode();
            String bb = b.getNode();
            return aa.compareTo(bb);
        }
    }

    static private class WorkItemPidSorter
        implements Comparator<IWorkItemState>
    {
        public int compare(IWorkItemState a, IWorkItemState b)
        {
            long apid = Long.parseLong(a.getPid());
            long bpid = Long.parseLong(b.getPid());
            return  (int)(apid - bpid);
        }
    }

}