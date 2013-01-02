//-------------------------------------------------------------------------------
// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
// 
//      http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
//-------------------------------------------------------------------------------
// --------------------------------------------------------------------------------
// IMPORTANT IMPORTANT IMPORTANT
//    ALWAYS update the version even for trivial changes
// IMPORTANT IMPORTANT IMPORTANT
// --------------------------------------------------------------------------------

#include <stdio.h>
#include <stdlib.h>
#include <stdlib.h>
#include <getopt.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <pwd.h>
#include <errno.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/param.h>
#include <sys/resource.h>

#define VERSION "0.6.4"

/**
 * 2012-05-04 Support -w <workingdir>.  jrc.
 * 2012-05-04 0.6.0 Update version to match current DUCC beta. jrc.
 * 2012-05-13 0.6.1 Update for MAC getpwnam() bug jrc.
 * 2012-05-13 0.6.2 Update to change group as well as userid. jrc.
 * 2012-07-12 0.6.3 RLIMIT_CORE support. jrc
 * 2012-10-04 0.6.4 renice. jrc
 */

/**
 * BUFFLEN is largest size for our stack buffers.
 * STRLEN  is longest string we'll place into a stack buffer.
 * MAX_COMPONENTS Is the largest number of intermediate components on a path passed by -b flag,
 *                This is controlled by DUCC itself so it's not subject to the user's whims.
 */
#define BUFLEN (PATH_MAX)
#define STRLEN (BUFLEN-1)
#define MAX_COMPONENTS (10)

void version()
{
    fprintf(stdout, "050 ducc_ling Version %s compiled %s at %s\n", VERSION, __DATE__, __TIME__);
}


void usage()
{
    fprintf(stderr, "999 Usage:\n");
    fprintf(stderr, "999   ducc_ling <-u user> [-w workingdir] [-f filepath] -- program_name [program args]\n");
    exit(1);
}

/**
 * Make a subdirectory.
 *
 * Number 2000
 */
int mksubdir(char *path)
{
    char buf[BUFLEN];
    struct stat statbuf;

    // if it exists and is a dir just return
    if ( stat(path, &statbuf) == 0 ) {
        fprintf(stdout, "2210 Directory %s already exists.\n", path);
        if ( ! ( statbuf.st_mode & S_IFDIR) ) { 
            fprintf(stderr, "2200 Log base %s is not a directory\n", path);
            return 0;
        }
        return 1;
    }

    fprintf(stdout, "2000 Creating directory %s\n", path);
    if ( mkdir(path, 0750) != 0 ) {

        if ( errno == EEXIST ) {
            // Terribly, terribly ugly.  Parts of the directory might be made already in the
            // CLI.  It is observed that if NFS is slow, or system dates are a bit off, when this
            // this code starts to run, the existance check above may fail, but the attempt to 
            // make the directory will now fail with "already exists".  So we simply repeat the
            // stat to make sure it's a directory and not a regular file.
            if ( stat(path, &statbuf) == 0 ) {
                fprintf(stdout, "2210 Directory %s already exists.\n", path);
                if ( ! ( statbuf.st_mode & S_IFDIR) ) { 
                    fprintf(stderr, "2200 Log base %s is not a directory\n", path);
                    return 0;
                }
                return 1;
            }
        }

        snprintf(buf, STRLEN, "2100 Cannot create log path component %s", path);
        buf[STRLEN] = '\0';
        perror(buf);
        return 0;
    }
    return 1;
}

/**
 * Concatenate thing to buf inplace within buf without overstepping BUFFLEN.
 * We assume buf is a buffer of length BUFLEN and will forcibly termintate the
 * the string at the end of the buffer.
 *
 * Number 3000
 */
void concat(char *buf, const char *thing)
{
    //
    // Shouldn't happen unless we're sloppy or being hacked. Die hard and fast.
    //
    if ( (strlen(buf) + strlen(thing) + 1 ) > BUFLEN ) {
        fprintf(stderr, "3000 Buffer overflow: string length too long to concatenate %s and %s.  maxlen = %d\n",
               buf, thing, BUFLEN);
        exit(1);
    }
    strncat(buf, thing, STRLEN);
    buf[STRLEN] = '\0';
}


/**
 * Walk the directory structure.  Check access until we find a spot where the dir does
 * not exist.  At this point the dir must have rwx privs and all components be dirs. If not,
 * error exit.  If so, then begin creating directories to create the full path.
 *
 * The path created is base/subdir/jobid.
 *
 * base is the user-specified log location.
 * filestem is the agent-specified log name, minus the required pid
 */
char * mklogfile(const char *filepath)
{
    //
    // First step, the base must exist and be writable.
    //
    char buf[BUFLEN];
    char *path_components[MAX_COMPONENTS];
    char *next_tok = NULL;
    char *final_tok = NULL;

    int i,j = 0;
    char *tmp;
    char *fullpath = strdup(filepath);
    
    i = 0;
    printf("Dir: %s\n", fullpath);
    for ( next_tok = strtok(fullpath, "/"); next_tok; next_tok = strtok(NULL, "/") ) {
        printf("Component %d: %s\n", i, next_tok);
        path_components[i++] = next_tok;        
    }

    buf[0] = '\0';                    // make it into a "" string
    if ( filepath[0] == '/' ) {       // strtok removes the '/'es so if it's absolute path, need to put one back in
        concat(buf, "/");
    }
    for ( j = 0; j < i-1; j++ ) {
        concat(buf, path_components[j]);        
        if ( ! mksubdir(buf) ) {
            return NULL;
        }
        concat(buf, "/");
    }

    tmp = strdup(buf);
    snprintf(buf, STRLEN, "%s%s-%d.log", tmp, path_components[i-1], getpid());
    return strdup(buf);
}

char * mklogfileOld(const char *base, char *subdir)
{
    //
    // First step, the base must exist and be writable.
    //
    char buf[BUFLEN];
    char *path_components[MAX_COMPONENTS];
    char *next_tok = NULL;
    char *final_tok = NULL;
    char * jobid = getenv("JobId");

    if ( jobid == NULL ) {
        fprintf(stderr, "2230 Environment must contain \"JobId\" in order to write to a log file.\n");
        return;
    }

    int i,j = 0;

    char *tmp;

    struct stat statbuf;

    // if it exists and is a dir just return
    if ( stat(base, &statbuf) == 0 ) {
        fprintf(stdout, "2210 Directory %s exists.\n", base);
        if ( ! ( statbuf.st_mode & S_IFDIR) ) { 
            fprintf(stderr, "2200 Log base %s is not a directory\n", base);
            return NULL;
        }
    }

    if ( access(base, R_OK + W_OK + X_OK) != 0 ) {        // make sure I can use the base dir
        snprintf(buf, STRLEN, "Can't access %s: %d\n", base, errno);
        buf[STRLEN] = '\0';
        perror(buf);
        return NULL;
    }
    
    i = 0;
    for ( next_tok = strtok(subdir, "/"); next_tok; next_tok = strtok(NULL, "/") ) {
        path_components[i++] = next_tok;
    }

    buf[0] = '\0';   // make it into a "" string
    concat(buf, base);
    for ( j = 0; j < i-1; j++ ) {
        concat(buf, "/");
        concat(buf, path_components[j]);        

        if ( ! mksubdir(buf) ) {
            return NULL;
        }
    }

    concat(buf, "/");
    concat(buf, jobid);
    if ( ! mksubdir(buf) ) {
        fprintf(stderr, "Can't make directory %s, quitting.\n", buf);
        return NULL;
    }

    tmp = strdup(buf);
    snprintf(buf, STRLEN, "%s/%s-%d.log", tmp, path_components[i-1], getpid());
    return strdup(buf);
}

void show_env(char **envp)
{
    int count = -1;
    while ( envp[++count] != NULL ) {
        fprintf(stdout, "Envoron[%d] = %s\n", count, envp[count]);
    }
}

/**
 * If DUCC_RLIMIT_CORE is in env, get its value and set the soft core limit.
 *
 * Number 4000
 */
void set_limits()
{
    char *climit = getenv("DUCC_RLIMIT_CORE");
    char buf[BUFLEN];

    if ( climit != NULL ) {
        char *en = 0;
        long long lim = strtoll(climit, &en, 10);
        struct rlimit limstruct;
        
        fprintf(stdout, "4000 Setting RLIMIT_CORE\n");
        if (*en) {
            fprintf(stderr, "4010 DUCC_RLIMIT_CORE is not numeric; core limit note set: %s\n", climit);
            return;
        }

        getrlimit(RLIMIT_CORE, &limstruct);
        fprintf(stdout, "4030 Before: RLIMIT_CORE soft[%lld] hard[%lld]\n", limstruct.rlim_cur, limstruct.rlim_max);

        limstruct.rlim_cur = lim;
        int rc = setrlimit(RLIMIT_CORE, &limstruct);
        if ( rc != 0 ) {
            perror("4030 Core soft limit was not set.");
        }

        getrlimit(RLIMIT_CORE, &limstruct);
        fprintf(stdout, "4040 After: RLIMIT_CORE soft[%lld] hard[%lld]\n", limstruct.rlim_cur, limstruct.rlim_max);
    }
}

#ifndef __APPLE__
void renice()
{
    char *nicestr = getenv("DUCC_NICE");
    int   niceval = 10;
    if ( nicestr != NULL ) {
        char *en = 0;
        niceval = strtol(nicestr, &en, 10);
    }
    fprintf(stdout, "4050 Nice: Using %d\n", niceval);
    int rc = nice(niceval);
    if ( rc < 0 ) {
       perror("4060 Can't set nice.");     
    }
}
#else
void renice()
{
    // mac seems to have no 'nice' syscall but we don't care since its only for test and devel anyway
}
#endif

/**
 * Proposed calling conventtion:
 *    ducc_ling <duccling args> -- executable_name <executable args>
 * Where
 *    executable is whatever, usually the path to the jova binary
 *    <executable args> are whatever you want to start java with, probably
 *       the JVM parms followed by the app parms
 *    <duccling args> are args for ducc_ling to process.  Perhaps something like:
 *       -u <userid>      - userid to switch to
 *       -f <filepath>    - if provided, ducc_ling will attempt to use this as
 *                          the log path.  The string <pid>.log is appended, where
 *                          <pid> is the process id.  Intermediate directories are
 *                          created as needed.
 *       -w <workingdir>  - if provided, ducc_ling will attempt to cd to the
 *                          specified dir as workingdir before execing to
 *                          the indicated process.
 *
 *     If -f is missing, no redirection is performed and no files are created.
 */
int main(int argc, char **argv, char **envp)
{
    int i;
    int opt;
    char *userid = NULL;
    char *filepath = NULL;
    char *workingdir = NULL;
    struct passwd *pwd= NULL;
    int switch_ids = 0;
    int redirect = 1;
    char buf[BUFLEN];

    version();            // this gets echoed into the Agent's log

    // dont allow root to exec a process
    if ( getuid() == 0 ) {
        fprintf(stderr, "400 Can't run ducc_ling as root\n");
    	exit(1);
    }

    while ( (opt = getopt(argc, argv, "f:w:u:h?") ) != -1) {
        switch (opt) {
        case 'u':
            userid = optarg;
            break;
        case 'f':
            filepath = optarg;
            break;
        case 'w':
            workingdir = optarg;
            break;
        case 'h':
        case '?':
            usage();
        default:
            fprintf(stderr, "100 Unrecognized argument %s\n", optarg);
            usage();
        }
    }
    argc -= optind;
    argv += optind;

    if ( userid == NULL ) {
        fprintf(stderr, "200 missing userid\n");
        exit(1);
    } 

    if ( filepath == NULL ) {
        fprintf(stdout, "300 Bypassing redirect of log\n");
        redirect = 0;
    } 
    
    // do this here before redirection stdout / stderr
    fprintf(stdout, "0 %d\n", getpid());                                         // code 0 means we passed tests and are about to dup I/O
    
    //	fetch "ducc" user passwd structure
    pwd = getpwnam("ducc");
    
    if ( pwd == NULL ) {
        pwd = getpwuid(getuid());
#ifdef __APPLE__
        // Seems theres a bug in getpwuid and nobody seems to have a good answer.  On mac we don't
        // care anyway so we ignore it.
        if ( pwd == NULL ) {
            fprintf(stdout, "600 No \"ducc\" user found and I can't find my own name.  Running as id %d", getuid());
        } else {
            fprintf(stdout, "600 No \"ducc\" user found, running instead as %s.\n", pwd->pw_name);
        }
#else
        fprintf(stdout, "600 No \"ducc\" user found, running instead as %s.\n", pwd->pw_name);
#endif
    } else if ( pwd->pw_uid != getuid() ) {
        fprintf(stdout, "700 Caller is not ducc (%d), not switching ids ... \n", pwd->pw_uid);
        pwd = getpwuid(getuid());
        fprintf(stdout, "800 Running instead as %s.\n", pwd->pw_name);
        //exit(0);
    } else {
        switch_ids = 1;
    }
    
    //	fetch given user's passwd structure and try switch identities.
    if ( switch_ids ) {
        pwd = getpwnam(userid);
        if ( pwd == NULL ) {
            fprintf(stderr, "820 User \"%s\" does not exist.\n", userid);
            exit(1);
        }
        
        //	dont allow to change uid to root.
        if ( pwd->pw_uid == 0 ) {
            fprintf(stderr, "900 setuid to root not allowed. Exiting.\n");
            exit(1);
        }

        if ( setgid(pwd->pw_gid) != 0 ) {
            snprintf(buf, STRLEN,  "1100 Unable to switch to group id %d.",pwd->pw_gid);
            buf[STRLEN] = '\0';
            perror(buf);
        } else {
            fprintf(stdout, "830 Switched to group %d.\n", pwd-> pw_gid);
        }

        if ( setuid(pwd->pw_uid) != 0 ) {
            snprintf(buf, STRLEN,  "1100 Unable to switch to user id %s.",userid);
            buf[STRLEN] = '\0';
            perror(buf);
        } else {
            fprintf(stdout, "840 Switched to user %d.\n", pwd-> pw_uid);
        }
    }

    set_limits();         // AFTER the switch, set soft limits if needed
    renice();

    if ( workingdir != NULL ) {
        int rc = chdir(workingdir);
        if ( rc == -1 ) {
            snprintf(buf, STRLEN,  "1110 Unable to switch to working director %s.", workingdir);
            buf[STRLEN] = '\0';
            perror(buf);
            exit(1);
        }
        fprintf(stdout, "1120 Changed to working dir %s\n", workingdir);
    }
    
    //
    // Set up logging dir.  We have swithed by this time so we can't do anything the user couldn't do.
    //
    if ( redirect ) {
        char *logfile = mklogfile(filepath);
        if ( logfile == NULL ) exit(1);                 // mklogdir creates sufficient erro rmessages
        
        //snprintf(buf, STRLEN, "%s/%s-%d.log", logdir, log, getpid());
        //buf[STRLEN] = '\0';
        
        fprintf(stdout, "1200 Redirecting stdout and stderr to %s as uid %d euid %d\n", logfile, getuid(), geteuid());

        fflush(stdout);
        fflush(stderr);

        // do we want apend or trunc?
        int fd = open(logfile, O_CREAT + O_WRONLY + O_TRUNC, 0644);
        // dup stdout and stderr into the log file
        if ( fd >= 0 ) {
            int rc1 = dup2(fd, 1);
            int rc2 = dup2(fd, 2);
        } else {
            snprintf(buf, STRLEN, "1300 cannot open file %s", logfile);
            buf[STRLEN] = '\0';
            perror(buf);
            exit(1);
        }

        version();             // this gets echoed into the redirected log
    }

    // 
    // Translate DUCC_LD_LIBRARY_PATH into LD_LIBRARY_PATH, if it exists.
    //
    //show_env(envp);

    int env_index = -1;
    char ** pathstr = NULL;
    while ( envp[++env_index] != NULL ) {
        char *srchstring = "DUCC_LD_LIBRARY_PATH=";
        int len = strlen(srchstring);
        if ( strncmp(envp[env_index], srchstring, len) == 0 ) {
            fprintf(stdout, "3000 Found DUCC_LD_LIBRARY_PATH and it is %s\n", envp[env_index]);
            pathstr = &envp[env_index];
            break;
        }
    }
    if ( pathstr == NULL ) {
        fprintf(stdout, "3001 Did not find DUCC_LD_LIBRARY_PATH, not setting LD_LIBRARY_PATH.\n");
    } else {
        //
        // We modify the variable in place.
        //
        char *val = getenv("DUCC_LD_LIBRARY_PATH");
        fprintf(stdout, "3002 Changing DUCC_LD_LIBRARY_PATH to LD_LIBRARY_PATH\n");
        sprintf(*pathstr, "LD_LIBRARY_PATH=%s", val);
    }


    //
    // Now just transmogrify into the requested command
    //
    fprintf(stdout, "1000 Command to exec: %s\n", argv[0]);
    for ( i = 1; i < argc; i++ ) {
        fprintf(stdout, "    arg[%d]: %s\n", i, argv[i]);
    }
    execve(argv[0], argv, envp);                     // just run the passed-in command

    //
    // if we get here it's because exec failed - it never returns if it succeeds.
    //
    snprintf(buf, STRLEN, "1400 cannot exec %s", argv[0]);
    buf[STRLEN] = '\0';
    perror(buf);
    exit(1);
}