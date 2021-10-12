/*
 * originally adapted from ANTLR4 org/antlr/v4/gui/TestRig.java
 */
/*
Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
SPDX-License-Identifier: Apache-2.0
*/

package compass;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.PredictionMode;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.SimpleDateFormat;
import java.util.stream.Collectors;

import parser.*;

/*
 * Invoke as:
 *  # java compass.Compass -help
 */
public class Compass {

	static Integer nrFileNotFound = 0;
	static Integer totalBatches = 0;
	static Integer totalParseErrors = 0;
	static Map<Integer,Integer> passCount = new HashMap<>();
	static Integer nrLinesTotalP1 = 0;
	static Integer nrLinesTotalP2 = 0;	
	static Integer retrySLL = 0;
	static boolean hasParseError = false;
	static StringBuilder parseErrorMsg = new StringBuilder();

	static long startRun;
	static String startRunFmt;
	static long endRun;
	static String endRunFmt;
	static long elapsedRun;
	
	static long startTime = 0;
	static long endTime = 0;
	static long duration = 0;	

	static Map<String, Integer> timeCount = new HashMap<>();

	protected static boolean quitNow = false;
	
	protected static boolean readStdin = false;
	protected static boolean parseOnly = false;
	protected static boolean importOnly = false;
	protected static boolean dumpParseTree = false;
	protected static boolean dumpBatchFile = false;
	protected static boolean forceAppName = false; 
	protected static String  quotedIdentifier = "ON"; 
	protected static boolean addReport = false;
	protected static boolean replaceFiles = false;
	protected static boolean generateReport = true;
	protected static boolean reAnalyze = false;
	protected static boolean reportOnly = false;
	protected static boolean deleteReport = false;
	protected static boolean userSpecifiedBabelfishVersion = false;
	protected static boolean listContents = false;
	protected static boolean pgImport = false;
	protected static boolean pgImportAppend = false;

	protected static boolean antlrSLL = true;
	protected static boolean antlrShowTokens = false;
	protected static boolean antlrTrace = false;
	protected static boolean antlrDiagnostics = false;
	protected static Charset charset;
	protected static String userEncoding = null;
	
	public static String reportName = null; // must be null when not initialized
	public static String applicationName;
	public static String sessionLog;

	public static List<String> inputFiles = new ArrayList<>();
	public static List<String> inputFilesOrig = new ArrayList<>();
	public static List<String> cmdFlags = new ArrayList<>();
	public static List<String> pgImportFlags = new ArrayList<>();

	private static TSQLParser.Tsql_fileContext exportedParseTree;

	public static CompassUtilities u = CompassUtilities.getInstance();
	public static CompassConfig cfg = CompassConfig.getInstance();
	public static CompassAnalyze a = CompassAnalyze.getInstance();

	public Compass(String[] args) {
		u.getPlatform();		

		if (args.length < 1) {
			System.err.println("Must specify arguments. Try -help");
			return;
		}

		u.targetBabelfishVersion = u.baseBabelfishVersion;  // init at 1.0

		for (int i = 0; i < args.length; i++) {
			cmdFlags.add(args[i]);
		}
		
		for (int i = 0; i < args.length; ) {
			String arg = args[i];
			i++;
			if (arg.equals("-version")) {
				// info already printed
				quitNow = true;
				return;
			}
			if (arg.equals("-help")) {
				String helpOption  = "";
				if (args.length > i) {
					if (args[i].equals("-reportoption")) helpOption = "reportoption";
				}
				if (!helpOption.isEmpty()) {
					u.appOutput("-reportoption  [ <options> ] : additional reporting detail. ");				
					u.appOutput(" <options> are comma-separated as follows:");									
					u.appOutput(" One of the following:");				
					u.appOutput("    xref=feature     : generate X-ref by feature");				
					u.appOutput("    xref=object      : generate X-ref by object");				
					u.appOutput("    xref=all         : generate both X-refs");				
					u.appOutput("    xref             : same as xref=all");				
					u.appOutput(" One or more of the following:");				
					u.appOutput("    detail           : generate additional X-ref detail (e.g. object names)");				
					u.appOutput("    apps             : with >1 app, shows app count in summary section");				
					u.appOutput("    linenrs=<number> : max.nr of line numbers shown in list (default="+u.maxLineNrsInListDefault+")");				
					u.appOutput("    batchnr          : in xref, show batch number + line nr in batch");				
					u.appOutput("    status=<status>  : generate X-ref for items with the specified status");				
					u.appOutput("    notabs           : do not open a Xref link in a new tab(default=open in new tab)");				
					u.appOutput("        Without status=, no X-refs are generated for 'Supported' and 'Ignored' features");				
					u.appOutput(" One or more of the following:");				
					u.appOutput("    filter=<pattern>: only report X-ref items matching the pattern (case-sensitive)");				
					u.appOutput("Without -reportoption, only the assessment summary is generated (no X-refs)");				
					u.appOutput("NB: generating X-refs may produce a very large report.");							
					u.appOutput("NB: do not put spaces anywhere in the options");							
					u.appOutput("");				
					u.appOutput("Examples:");				
					u.appOutput("  -reportoption xref,status=supported,detail ");				
					u.appOutput("  -reportoption xref -reportoption status=supported -reportoption detail ");				
					u.appOutput("  -reportoption xref=object,status=reviewsemantics,status=reviewperformance,detail");				
					quitNow = true;
					return;					
				}
				
				u.appOutput("Usage: " + u.thisProgExec + "  <reportName>  <options> ");
				u.appOutput("<options> can be:");
				u.appOutput("   inputfile [ inputfile ...]   : one or more input files to import into the report");
				u.appOutput("   -delete                      : first deletes report directory, incl. all report files");
				u.appOutput("   -appname <appname>           : use application name <appname> for all inputfiles");
				u.appOutput("   -add                         : import additional inputfile(s) into existing report");  
				u.appOutput("   -replace                     : replace already-imported input file(s)"); 
				u.appOutput("   -noreport                    : analyze only, do not generate a report");
				u.appOutput("   -importonly                  : import input file(s), no analysis or report");				
				u.appOutput("   -reportonly                  : (re)generate report based on earlier analysis");				
				u.appOutput("   -reportoption <options>      : additional reporting detail (try -help -reportoption)");				
				u.appOutput("   -reanalyze                   : re-run analysis on imported files, and generate report");				
				u.appOutput("   -babelfish-version <version> : specify target Babelfish version (default=latest)");
				u.appOutput("   -encoding <encoding>         : input file encoding, e.g. '-encoding utf8'. Default="+Charset.defaultCharset());
				u.appOutput("                                  use '-encoding help' to list available encodings");
				u.appOutput("   -quotedid {on|off}           : set QUOTED_IDENTIFIER at start of script (default=ON)");
				u.appOutput("   -pgimport <list>             : imports captured items into a PostgreSQL table for SQL querying");
				u.appOutput("                                  <list> is: host,port,username,password,dbname  (requires psql to be installed)");
				u.appOutput("   -pgimportappend              : with -pgimport, appends to existing table (instead of drop/recreate)");
				u.appOutput("   -version                     : show version of this tool");
				u.appOutput("   -help                        : show this information");		
				u.appOutput("   -explain                     : some high-level migration guidance");		
				if (u.devOptions) {		
				u.appOutput("");
				u.appOutput("For development only:");
				u.appOutput("   -dbgreport                   : use fixed report name (no timestamp)");
				u.appOutput("   -echocapture                 : echo captured items to stdout");
				u.appOutput("   -parsetree                   : print parse tree & copy parsed batches to file");
				u.appOutput("   -parseonly                   : parse & print parse tree, no analysis");
				u.appOutput("   -stdin                       : read from stdin, parse only (no analysis) and exit");
				u.appOutput("   -noSLL                       : do not use SLL mode (slow; for troubleshooting only)");
				u.appOutput("   -showtokens                  : print tokens");
				u.appOutput("   -antlrtrace                  : print ANTLR parsing trace");
				u.appOutput("   -antlrdiagnostics            : print ANTLR diagnostics");
				}
				quitNow = true;
				return;
			}
			if (arg.equals("-explain")) {
				u.appOutput("Babelfish Compass is currently a command-line-only tool, running on Windows only.\nIt takes one or more DDL/SQL scripts as input and generates a compatibility assessment report.\nBabelfish Compass does not connect directly to a SQL Server instance.\n\nThe purpose of Babelfish Compass is to analyze a SQL Server DDL/SQL script\nfor compatibility with Babelfish, to inform a decision about whether\nit is worth considering starting a migration project to Babelfish.\nTake the following steps:\n1. Reverse-engineer the SQL Server database(s) in question\n   with SSMS (right-click a database --> Tasks --> Generate Scripts.\n   Do not forget to enable triggers, logins, owners and permissions (disabled in SSMS by default).\n2. Use the resulting DDL/SQL script as input for Babelfish Compass to generate an assessment report\n3. Discuss the results of Babelfish Compass with the application owner and interpret the findings in the\n   context of the application to be migrated.\n4. Keep in mind that a Babelfish migration involves more than just the server-side DDL/SQL code\n   (e.g. data migration, client applications, external interfaces, etc.)\n\nRun "+u.thisProgExec+" -help for further usage info.\n\n");
				u.errorExit();
			}
			if ((arg.equals("-babelfish-version") || arg.equals("-babelfish_version"))) {
				if (i == args.length) {
					u.appOutput("Must specify argument for -babelfish-version ");
					u.errorExit();
				}

				u.targetBabelfishVersion = args[i];
				userSpecifiedBabelfishVersion = true;
				i++;
				continue;
			}
			if (arg.equals("-appname")) {
				if (i == args.length) {
					u.appOutput("Must specify argument for -appname");
					u.errorExit();
				}
				forceAppName = true;
				applicationName = args[i];
				String invalidMsg = u.nameFormatValid("appname", applicationName);
				if (!invalidMsg.isEmpty()) {
					u.appOutput("Application name '"+applicationName+"' contains invalid character(s) "+invalidMsg);
					u.errorExit();
				}
				i++;
				continue;
			}
			if (arg.equals("-replace")) {
				replaceFiles = true;
				continue;
			}
			if (arg.equals("-add")) {
				addReport = true;
				continue;
			}
			if (arg.equals("-list")) {
				listContents = true;
				continue;
			}
			if (arg.equals("-delete")) {
				deleteReport = true;
				continue;			
			}
			if (arg.equals("-quotedid")) {
				if (i == args.length) {
					u.appOutput("Must specify -quotedid ON or -quotedid OFF");
					u.errorExit();
				}
				quotedIdentifier = args[i].toUpperCase();
				if (!u.OnOffOption.contains(quotedIdentifier)) {
					u.appOutput("Must specify -quotedid ON or -quotedid OFF");
					u.errorExit();
				}
				i++;
				continue;
			}
			if (arg.equals("-noreport")) {
				generateReport = false;
				continue;
			}	
			if (arg.equals("-reportonly")) {
				reportOnly = true;
				continue;
			}	
			if (arg.equals("-reanalyze")) {
				reAnalyze = true;
				continue;
			}	
			if (arg.equals("-importonly")) {
				importOnly = true;
				continue;
			}				
			if (arg.equals("-encoding")) {
				if (i >= args.length) {
					System.err.println("missing encoding on -encoding");
					return;
				}
				userEncoding = args[i];
				i++;
				continue;
			}						
			if ((arg.equals("-reportoption") || arg.equals("-reportoptions"))) {   
				if (i == args.length) {
					u.appOutput("Must specify arguments for -reportoption ");
					u.errorExit();
				}
				List<String> reportOptions = Arrays.asList("xref", "detail", "status", "filter", "apps", "batchnr", "linenrs", "notabs");
				List<String> reportOptionsXref = Arrays.asList("", "all", "object", "feature");
				List<String> reportFlags = new LinkedList<>(Arrays.asList(args[i].split(",")));
				reportFlags.removeIf(String::isEmpty);
				for(String option : reportFlags) {
					String optionValue = a.getOptionValue(option);
					option = a.getOptionName(option);							
					if (reportOptions.contains(option.toLowerCase())) {
						// OK
						if (option.equals("xref")) {
							if (!reportOptionsXref.contains(optionValue)) {
								u.appOutput("Invalid option '"+optionValue+"' for -reportoption xref=");
								u.appOutput("Valid options: "+reportOptionsXref);
								u.errorExit();								
							}
							if (optionValue.isEmpty()) u.reportOptionXref = "all";
							else u.reportOptionXref = optionValue.toLowerCase();
						}
						else if (option.equals("status")) {
							List<String> statusOptions = new ArrayList<>(u.validSupportOptionsCfgFile);
							statusOptions.add(0,u.Supported);
							statusOptions.add(0,"ALL");
							if (!statusOptions.contains(optionValue.toUpperCase())) {
								u.appOutput("Invalid option '"+optionValue+"' for -reportoption status=");
								u.listToLowerCase(statusOptions);
								u.appOutput("Valid options: "+statusOptions);
								u.errorExit();								
							}
							u.reportOptionStatus += " " + optionValue.toLowerCase() + " ";
						}
						else if (option.equals("detail")) {
							u.reportOptionDetail = option;  // no option values defined right now
						}
						else if (option.equals("apps")) {
							u.reportOptionApps = option;  
						}
						else if (option.equals("batchnr")) {
							u.reportShowBatchNr = option;  
						}
						else if (option.equals("notabs")) {
							u.linkInNewTab = false;  
							u.tgtBlank = "";  
						}
						else if (option.equals("linenrs")) {
							Integer ln = 0;
							try {
								ln = Integer.parseInt(optionValue);
								if (ln < 1) Integer.parseInt("x");
							} catch (Exception e) { 
								u.appOutput("Invalid option '"+optionValue+"' for -reportoption linenrs=, must be number > 0");
								u.errorExit();								
							}
							u.maxLineNrsInList = ln;  
						}
						else if (option.equals("filter")) {							 
							if (optionValue.isEmpty()) {
								u.appOutput("No value specified for option 'filter='");
								u.errorExit();				
							}
							u.reportOptionFilter = optionValue; 
						}
					}
					else {
						u.appOutput("Invalid option '"+option+"' for -reportoption");
						u.appOutput("Valid options: "+reportOptions);
						u.errorExit();
					}
				}				
				i++;
				continue;
			}
			if (arg.equals("-pgimportappend")) {	
				pgImportAppend = true;	
				continue;
			}				
			if (arg.equals("-pgimport")) {					
				if (i == args.length) {
					u.appOutput("Must specify arguments for -pgimport: host,port,username,password,dbname ");
					u.errorExit();
				}
				pgImportFlags = new LinkedList<>(Arrays.asList(args[i].split(",")));
				pgImportFlags.removeIf(String::isEmpty);
				if (pgImportFlags.size() != 5) {
					u.appOutput("Must specify 5 arguments for -pgimport: host,port,username,password,dbname ");
					u.errorExit();								
				}
				pgImport = true;	
				i++;
				continue;		
			}							
			if (u.devOptions) {
				if (arg.equals("-debug")) {   // development only
					if (i == args.length) {
						u.appOutput("Must specify arguments for -debug ");
						u.errorExit();
					}
					List<String> debugValues = new LinkedList<>(Arrays.asList(args[i].split(",")));		
					debugValues.removeIf(String::isEmpty);
					u.specifiedDbgOptions.addAll(debugValues);
					u.specifiedDbgOptions.retainAll(u.dbgOptions);
					u.appOutput("Debug flag set: "+ u.specifiedDbgOptions);
					if (u.specifiedDbgOptions.isEmpty()) {
						u.appOutput("Valid debug options: " + u.dbgOptions);
						u.errorExit();
					}
					u.setDebugFlags();
					i++;
					continue;
				}
				if (arg.equals("-echocapture")) { // development only
					u.echoCapture = true;
					continue;
				}
				if (arg.equals("-dbgreport") || (arg.equals("-devreport"))) { // development only
					u.stdReport = true;
					continue;
				}
				if (arg.equals("-parseonly")) { // development only
					parseOnly = true;
					dumpParseTree = true;
					continue;
				}
				if (arg.equals("-parsetree")) { // development only
					dumpParseTree = true;
					dumpBatchFile = true;
					continue;
				}
				if (arg.equals("-stdin")) {  // development only
					readStdin = true;
					dumpParseTree = true;
					parseOnly = true;
					continue;
				}			
				if (arg.equals("-noSLL")) {  // development only
					antlrSLL = false;
					continue;
				}
				if (arg.equals("-showtokens")) {  // development only
					antlrShowTokens = true;
					continue;
				}
				if (arg.equals("-antlrtrace")) {  // development only
					antlrTrace = true;
					continue;
				}
				if (arg.equals("-antlrdiagnostics")) {  // development only
					antlrDiagnostics = true;
					continue;
				}
				if (arg.equals("-"+u.reverseString("muskcehc"))) { // development only
					u.configOnly = true;
					continue;
				}
				if (arg.equals("-caching")) { // development only
					u.caching = true;
					continue;
				}
			}
			// arguments must start with [A-Z0-9 _-./] : anything else is invalid
			if (u.getPatternGroup(arg.substring(0,1), "^([\\w\\-\\.\\/])$", 1).isEmpty()) {
				System.err.println("Invalid option ["+arg+"]. Try -help");
				u.errorExit();
			}
							
			if (arg.charAt(0) != '-') { // non-options
				if (reportName == null) {
					// this is the report name, must be first argument
					if (i != 1) {
						u.appOutput("Report name must be the first argument (try -help)");
						u.errorExit();
					}
					reportName = arg.trim();
					u.reportName = reportName;
					if (reportName.isEmpty()) {
						u.appOutput("Report name cannot be blank");
						u.errorExit();
					}
					String invalidMsg = u.nameFormatValid("report", reportName);
					if (!invalidMsg.isEmpty()) {
						u.appOutput("Report name '"+reportName+"' contains invalid character(s) "+invalidMsg);
						u.errorExit();
					}
				}
				else {
					inputFiles.add(arg);
				}
				continue;
			}
			
			// exit on invalid argument
			System.err.println("Invalid option ["+arg+"]. Try -help");
			u.errorExit();
		}

		inputFilesOrig.addAll(inputFiles);
	}

	public static void main(String[] args) throws Exception {
		u.appOutput(u.thisProgName + " v." + u.thisProgVersion + ", " + u.thisProgVersionDate);
		u.appOutput(u.thisProgNameLong);
		u.appOutput(u.copyrightLine);
		u.appOutput("");	
		
 		if(args.length < 1) {
			u.appOutput("No arguments specified. Try -help");
			return;
 		}

 		// validate arguments
		Compass comp = new Compass(args);

 		if (quitNow) {
			return;
 		}
 		
 		// perform PG import
		if (pgImport) {
			runPGImport();
			return;
		}
		
 		// read config file
 		u.cfgFileName = u.defaultCfgFileName; // todo: make configurable?
		cfg.validateCfgFile(u.cfgFileName);
		assert u.cfgFileFormatVersionRead > 0 : "cfgFileFormatVersionRead=["+u.cfgFileFormatVersionRead+"], must be > 0";
		if (u.cfgFileFormatVersionRead > u.cfgFileFormatVersionSupported) {
			u.appOutput("File format version number in "+ u.cfgFileName + " is "+ u.cfgFileFormatVersionRead+".");
			u.appOutput("This version of "+ u.thisProgName+ " supports version "+u.cfgFileFormatVersionSupported+ " or earlier.");
			u.errorExit();			
		}

		// only for debugging the config class:
//		if (u.debugCfg) {
//			CompassTestConfig.testConfig();
//		}

		// copy cfg structure
		a.cfg = cfg;

		// init Babelfish target version at latest version, unless user specified a version
		if (userSpecifiedBabelfishVersion) {
			// validate user-specified version
			if (!cfg.isValidBabelfishVersion(u.targetBabelfishVersion)) {
				u.appOutput("Invalid target Babelfish version specified [" + u.targetBabelfishVersion + "].\nValid Babelfish versions: " + cfg.validBabelfishVersions());
				return;
			}
		}
		else {
			u.targetBabelfishVersion = cfg.latestBabelfishVersion();
		}

		// validate combinations of options specified		
		if (!optionsValid()) {
			return;
		}
				
		if (userEncoding != null) {
			if (userEncoding.equals("help")) {
				u.appOutput("Allowed values for -encoding:");
				for (String cs : Charset.availableCharsets().keySet()) {
					u.appOutput(u.lineIndent + cs);
				}
				return;
			}
		}
		
		startRunFmt = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss").format(new Date());		
		if (readStdin) {
			// only take stdin input, skip other steps
		}
		else {		
			if (!reportOnly) {
				if (!inputFilesValid()) {
					return;
				}
			}
						
			if (deleteReport) {
				// first delete the report dir before proceeding
				u.deleteReportDir(reportName); 
			}		
							
			u.checkDir(u.getDocDirPathname(), false, true);
			if (u.checkReportExists(reportName, inputFiles, forceAppName, applicationName, replaceFiles, addReport)) {
				// we cannot proceed for some reason
				return;
			}			
			String reportDirName = u.getReportDirPathname(reportName);
			sessionLog = u.openSessionLogFile(reportName, startRunFmt);
			//sessionLog = sessionLog.substring(reportDirName.length()+1);		
					
			if (listContents) {
				u.appOutput("Report name               : "+reportName);
				u.appOutput("Report directory location : "+reportDirName);				
				u.appOutput("");				
				// list all files/apps currently imported for this report
				u.listReportFiles(reportName);
				return;
			}
			
			cmdFlags.removeAll(inputFilesOrig);			
								
			u.appOutput("");
			u.appOutput("Run starting               : "+startRunFmt);
			String tmp = "";
			tmp =       u.cfgFileName+" file : v."+cfg.latestBabelfishVersion() + ", " + u.cfgFileTimestamp;			
			u.reportHdrLines += tmp + "\n";			
			u.appOutput(tmp);
			tmp =       "Target Babelfish version   : v."+u.targetBabelfishVersion;			
			u.reportHdrLines += tmp + "\n";			
			u.appOutput(tmp);
			tmp =       "Command line arguments     : "+String.join(" ",cmdFlags);
			u.reportHdrLines += tmp + "\n";			
			u.appOutput(tmp);
			tmp =       "Command line input files   : "+String.join(" ",inputFilesOrig);
			u.reportHdrLines += tmp + "\n";			
			u.appOutput(tmp);
			u.appOutput("QUOTED_IDENTIFIER default  : "+quotedIdentifier);
			tmp =       "Report name                : "+reportName;
			u.reportHdrLines += tmp;
			u.appOutput(tmp);
			u.appOutput("Report directory location  : "+reportDirName);
			u.appOutput("Session log file           : "+sessionLog);			
			u.appOutput("");
		}
		// init time counters
		timeCount.put("parseTime",0);
		timeCount.put("analysisTimeP1",0);
		timeCount.put("analysisTimeP2",0);
		timeCount.put("report",0);

		// start
		startRun = System.currentTimeMillis();								
									
		if (reAnalyze) {
			// create fresh symbol table and capture file
			u.deleteReAnalyze(reportName);
		}
											
		if (reportOnly) {
			// only generate reported from already-captured items
			startTime = System.currentTimeMillis();
			u.createReport(reportName);
			endTime = System.currentTimeMillis();
			duration = (endTime - startTime);
			timeCount.put("report", timeCount.get("report") + (int) duration);
		}
		else {	
			// ---- pass 1 --------------------------------------

			if ((inputFiles.size() > 0) || readStdin || reAnalyze) {
				u.analysisPass = 1;
				comp.processInput(startRunFmt);

				if (readStdin) {
					if (hasParseError) {
						u.appOutput(parseErrorMsg);
					}
					return;
				}
			}

			// ---- pass 2 --------------------------------------
			if (!parseOnly) {				
				if (importOnly) {
					u.appOutput("Not performing analysis or generating assessment report.\nUse -reanalyze later to analyze & generate a report.");
					u.appOutput("");		
				}
				else {
					u.analysisPass = 2;
					
					// do we have any input files in this report?
					List<Path> importFiles = u.getImportFiles(reportName);
					int nrImportFiles = importFiles.size();
								
					if (nrImportFiles > 0) {
						// read symbol table from disk, it must exist
						try { u.readSymTab(reportName); }
						catch (Exception e) {
							u.appOutput("Error reading symbol table "+u.symTabFilePathName);
							throw e;
						}
						comp.processInput(startRunFmt);
					}
					else {
						u.appOutput(nrImportFiles+" input files found for report "+reportName);				
					}

					if (!generateReport) {
						 // -noreport
						u.appOutput("Not generating assessment report.\nUse -reportonly later to generate a report.");
					}
					else {
						startTime = System.currentTimeMillis();
						u.createReport(reportName);
						endTime = System.currentTimeMillis();
						duration = (endTime - startTime);
						timeCount.put("report", timeCount.get("report") + (int) duration);						
					}
				}		
			}
		}

		endRun = System.currentTimeMillis();
		endRunFmt = new SimpleDateFormat("dd-MMM-yyyy HH:mm:ss").format(new Date());		
		elapsedRun = (endRun - startRun)/ 1000;

		comp.reportFinalStats();
		if (!parseOnly) u.closeReportFile();
		
		// launch explorer window with the generated report
		if (u.onWindows) {
			if (!u.devOptions) {
				u.runOScmd("cmd /c \"explorer.exe /n,/select,\"\""+u.reportFilePathName+"\"\" \"");
				u.runOScmd("cmd /c \"explorer.exe \"\""+u.reportFilePathName+"\"\" \"");
			}
		}
	}

	private static boolean inputFilesValid() throws Exception {		
		// validate input files specified
		AtomicBoolean inputValid = new AtomicBoolean(true);

		inputFiles = inputFiles.stream().filter(inFile -> {
			boolean pathValid = true;
			Path path = null;
			try {
				path = Paths.get(inFile);
			} catch (java.nio.file.InvalidPathException ignored) {
				pathValid = false;
			}
			if (!pathValid || !Files.exists(path)) {
				pathValid = false;
				nrFileNotFound++;
				u.appOutput("Input file " + inFile + " not found, proceeding...");
			}
			if (!u.inputScriptValid(inFile)) {
				u.appOutput("Input file " + inFile + " not valid");
				inputValid.set(false);
			}
			return pathValid;
		}).collect(Collectors.toList());

		if (!inputValid.get()) {
			return false;
		}
		
		// do we have any input files left to process?
		if ((inputFiles.size()) == 0) {
			if (deleteReport) {
				u.appOutput("With -delete, must specify input file(s)");
				return false;
			}
			if (addReport) {
				u.appOutput("With -add, must specify input file(s)");
				return false;
			}
		}
		return true;		
	}
	
	private static boolean optionsValid() throws Exception {
		// validate combinations of various options specified
		// return true if options are valid
		
 		if (reportName == null) {
 			reportName = "";
 		}
 		
 		if (reportName.isEmpty()) {
 			if (!readStdin) {
				u.appOutput("Report name must be specified");
				return false;
			}
		}
		
 		if (pgImport || pgImportAppend) {
 			if (readStdin || listContents || importOnly || inputFiles.size() > 0 || reAnalyze || deleteReport || reportOnly) {
				u.appOutput("-pgimport cannot be combined with other options");
				return false;
			}
			return true;
		}
		
 		if (pgImportAppend) {
 			if (!pgImport) {
				u.appOutput("-pgimportappend requires -pgimport");
				return false; 				
 			}
			return true;
		}
		
		if (listContents && readStdin) {
			u.appOutput("Cannot combine -list and -stdin");
			return false;
		}
		
		if (importOnly && readStdin) {
			u.appOutput("Cannot combine -importonly and -stdin");
			return false;
		}
		
		if (importOnly && reAnalyze) {
			u.appOutput("Cannot combine -importonly and -reanalyze");
			return false;
		}
		
		if (importOnly && reportOnly) {
			u.appOutput("Cannot combine -importonly and -reportonly");
			return false;
		}
		
		if (importOnly && listContents) {
			u.appOutput("Cannot combine -list and -importonly");
			return false;
		}
		
		if (reAnalyze && readStdin) {
			u.appOutput("Cannot combine -reanalyze and -stdin");
			return false;
		}
		
		if (reAnalyze && parseOnly) {
			u.appOutput("Cannot combine -reanalyze and -parseonly");
			return false;
		}
		
		if (reAnalyze && forceAppName) {
			u.appOutput("Cannot combine -reanalyze and -appname");
			return false;
		}
		
		if (reAnalyze && (!generateReport)) {
			u.appOutput("Cannot combine -reanalyze and -noreport");
			return false;
		}
		
		if (reportOnly && forceAppName) {
			u.appOutput("Cannot combine -reportonly and -appname");
			return false;
		}
		
		if ((inputFiles.size() > 0) && readStdin) {
			u.appOutput("Cannot combine -stdin and input files");
			return false;
		}
		
		if ((inputFiles.size() > 0) && reAnalyze) {
			u.appOutput("Cannot combine -reanalyze and input files");
			return false;
		}
		
		if ((inputFiles.size() > 0) && listContents) {
			u.appOutput("Cannot combine -list and input files");
			return false;
		}
		
		if ((inputFiles.size() > 0) && reportOnly) {
			u.appOutput("Cannot combine -reportonly and input files");
			return false;
		}
		
		if (deleteReport && reAnalyze) {
			u.appOutput("Cannot combine -delete and -reanalyze");
			return false;
		}		
		
		if (deleteReport && readStdin) {
			u.appOutput("Cannot combine -delete and -stdin");
			return false;
		}		
		
		if (reportOnly && readStdin) {
			u.appOutput("Cannot combine -reportonly and -stdin");
			return false;
		}		
		
		if (reportOnly && reAnalyze) {
			u.appOutput("Cannot combine -reportonly and -reanalyze");
			return false;
		}		
		
		if (deleteReport && listContents) {
			u.appOutput("Cannot combine -delete and -list");
			return false;
		}
		
		if (deleteReport && reportOnly) {
			u.appOutput("Cannot combine -delete and -reportonly");
			return false;
		}
		
		if (listContents && (addReport || replaceFiles)) {
			u.appOutput("Cannot combine -list and -add/-replace");
			return false;
		}
		
		if (deleteReport && (addReport||replaceFiles)) {
			u.appOutput("Cannot combine -delete and -add/-replace");
			return false;
		}
		
		if (reportOnly && (addReport||replaceFiles)) {
			u.appOutput("Cannot combine -reportonly and -add/-replace");
			return false;
		}			
				
		if (inputFiles.size()==0) {
			if (deleteReport) {
				u.appOutput("With -delete, must specify input file(s)");
				return false;
			}
			if (addReport) {
				u.appOutput("With -add, must specify input file(s)");
				return false;
			}
		}	

 		if ((inputFiles.size()==0) && (!readStdin) && (!generateReport)) {
 			u.appOutput("No input files specified. Try -help");
			if (!reportName.isEmpty()) {
 				u.appOutput("(NB: first argument is the report name, not the input file)");
 			}
			return false;
		}
		
		// when only specifying the report name, must at least specify -list or -reanalyze or -reportonly
		if (!reportName.isEmpty()) {
			if ((inputFiles.size()==0) && (!readStdin) && (!deleteReport)) {
				if (!(listContents || reportOnly || reAnalyze)) {
	 				u.appOutput("Must specify input file(s), or -list/-reanalyze/-reportonly");
	 				return false;
				}
			}
		}
		
		if ((inputFiles.size() > 0) && (reportOnly)) {
			u.appOutput("Cannot combine -reportonly and input files");
			return false;
		}
		
		if ((!reportName.isEmpty()) && (readStdin)) {
			// print message
			u.appOutput("Ignoring report name with -stdin");
		}
		
		// if we get here, we're good
		return true;
	}
	
	private static void runPGImport () { 
		// import the captured.dat file into a PG table
		String cmd = "psql --echo-all --file=~file~ \"postgresql://~username~:~password~@~host~:~port~/~dbname~\"";

		cmd = u.applyPatternFirst(cmd, "~host~", pgImportFlags.get(0));
		cmd = u.applyPatternFirst(cmd, "~port~", pgImportFlags.get(1));
		cmd = u.applyPatternFirst(cmd, "~username~", pgImportFlags.get(2));
		cmd = u.applyPatternFirst(cmd, "~dbname~", pgImportFlags.get(4));		
					
		try { u.importPG(pgImportAppend, cmd, pgImportFlags.get(3)); } catch (Exception e) { /*nothing*/ }		
		
		//Note: by exiting here, the password entered on command line is not getting written into any files or log files!		
		u.errorExit();
	}

	private void reportFinalStats() throws Exception {
		if (readStdin) {
			return;
		}
		
		int nrFiles = (inputFiles.size());

		if (nrFiles > 1) {
			u.appOutput("");
			u.appOutput(u.composeOutputLine("--- Report '"+reportName+"' Contents ", "-"));
			u.listReportFiles(reportName);
		}

		String errFiles = "";
		if (nrFileNotFound > 0) {
			errFiles = " ("+nrFileNotFound+" specified, but not found)";
		}

		String parseErrorMsg = "";
		if (totalParseErrors > 0) {
			parseErrorMsg = "  (see "+u.getReportDirPathname(reportName, u.errBatchDirName)+File.separator+"*."+u.errBatchFileSuffix+")";
		}
		
		int linesSQL=0;
		if (u.linesSQLInReport > 0) linesSQL = u.linesSQLInReport;
		else if (nrLinesTotalP1 > 0) linesSQL = nrLinesTotalP1;
		else if (nrLinesTotalP2 > 0) linesSQL = nrLinesTotalP2;
		
		String linesSQLPerSecFmt = "";
		Long linesSQLPerSec = (elapsedRun > 0) ? (linesSQL / elapsedRun) : linesSQL;	
		if (linesSQLPerSec > 0) linesSQLPerSecFmt = "  ("+linesSQLPerSec.toString()+" lines/sec)";

		boolean writeToReport = true;
		if (parseOnly) writeToReport = false;
		
		u.appOutput("");
		u.appOutput(u.composeOutputLine("--- Run Metrics ", "-"), writeToReport);
		u.appOutput("Run start            : "+ startRunFmt, writeToReport);
		u.appOutput("Run end              : "+ endRunFmt, writeToReport);
		u.appOutput("Run time             : "+ elapsedRun + " seconds", writeToReport);
		u.appOutput("#Lines of SQL        : "+ linesSQL + linesSQLPerSecFmt, writeToReport);
		
		if ((totalParseErrors > 0) || u.devOptions) {
			u.appOutput("#syntax errors       : "+ totalParseErrors + parseErrorMsg, writeToReport);
		}
		
		if (u.devOptions) {
		u.appOutput("#input files         : "+ nrFiles+errFiles);
		u.appOutput("#batches             : "+ totalBatches);
		u.appOutput("#lines of SQL ph.1   : "+ nrLinesTotalP1);
		u.appOutput("#lines of SQL ph.2   : "+ nrLinesTotalP2);
		u.appOutput("#SQL features        : "+ u.constructsFound, writeToReport);
		u.appOutput("Parse time           : "+ timeCount.get("parseTime")/1000 + " seconds");
		u.appOutput("Analysis time ph.1   : "+ timeCount.get("analysisTimeP1")/1000 + " seconds");
		u.appOutput("Analysis time ph.2   : "+ timeCount.get("analysisTimeP2")/1000 + " seconds");
		u.appOutput("Report gen time      : "+ timeCount.get("report")/1000 + " seconds");

		Integer retryPct = 0;
		String SLL_fmt = "-noSLL";
		if (antlrSLL) {
			if (passCount.size() == 2) retrySLL = retrySLL/2;
			if (totalBatches > 0) { retryPct = (retrySLL*100/totalBatches); }
			SLL_fmt = retrySLL.toString() + "/"+totalBatches.toString()+ " ("+retryPct.toString()+"%)";
		}
		
		u.appOutput("#SLL retries         : "+ SLL_fmt);
		u.appOutput("Compatibility        : "+ u.compatPctStr + "%   (uncorrected: "+u.compatPctStrRaw+"%)" );
		}
		
		u.appOutput("Session log          : "+ sessionLog, writeToReport);
		u.appOutput("Assessment report    : "+ u.reportFilePathName, writeToReport);
		u.appOutput(u.composeOutputLine("","="), writeToReport);
		
		if (u.devOptions) {
			u.appOutput(u.thisProc()+"caching  =["+u.caching+"] ");
			u.appOutput(u.thisProc()+"stripDelimitedIdentifierCall  =["+u.stripDelimitedIdentifierCall+"] ");
			u.appOutput(u.thisProc()+"stripDelimitedIdentifierCached=["+u.stripDelimitedIdentifierCached+"] ");
			u.appOutput(u.thisProc()+"normalizeNameCall  =["+u.normalizeNameCall+"] ");
			u.appOutput(u.thisProc()+"normalizeNameCached=["+u.normalizeNameCached+"] ");
		}
	}

	private void processInput(String runStartTime) throws Exception {	
		if (readStdin) {
			// quick parse option, for development only
			if (u.analysisPass > 1) return;

			String EOT = "CTRL-D";
			if (u.onWindows) {
				EOT = "CTRL-Z";
			}

			u.appOutput("Setting QUOTED_IDENTIFIER=" + quotedIdentifier);
			u.appOutput("Enter SQL to be parsed, and close with " + EOT + " + <ENTER> (on separate line):");

			charset = Charset.defaultCharset();
			CharStream charStream = CharStreams.fromStream(System.in, charset);
			String ptree = parseBatch(charStream, "", 0, 0, false);
			u.appOutput("parse tree:");
			u.appOutput(ptree);
			return;
		}

		// in first pass, process input files and create symbol table (a very basic version)
		// in second pass, process copies of input files made in the first pass, and perform analysis
		// with -reanalyze, run both passes on the copies of the originally imported input files
		if (u.debugging) u.dbgOutput("pass=" + u.analysisPass + ".  reAnalyze=[" + reAnalyze + "] ", u.debugBatch);

		if (parseOnly) {
			if (u.analysisPass > 1) {
				return;
			}
		}

		if (reAnalyze) {
			// reprocess from the start, based on the input copy files, so no need to re-import source files
			// do this when:
			//  - with a new version of the Babelfish Compass tool, with changes to the analysis logic or grammar/parser
			//  - re-running the analysis for a different Babelfish version than originally

			// set up input files for processing
			List<Path> importFiles = u.getImportFiles(reportName);

			//copy imported filenames into inputFiles
			inputFiles.clear();  // should be redundant
			for (Path imf : importFiles) {
				inputFiles.add(imf.toString());
			}

		} 
		else {
			if (u.analysisPass == 1) {
				if (inputFiles.size() == 0) {
					u.appOutput("No input files specified");
					u.errorExit();
				}
			}
		}

		int nrFiles = inputFiles.size();
		int fileCount = 0;
		for (String inFile : inputFiles) {
			fileCount++;
			String appName = "";
			String origSrcFile = "";
			String inFileCopy = "";
			FileInputStream fis = null;
			InputStreamReader isr = null;
			
			if (!reAnalyze) {
				// process the input files when importing. i.e. the very first time
				if (!Files.exists(Paths.get(inFile))) {
					continue;
				}
				appName = forceAppName ? applicationName : u.getFileNameFromPathName(inFile);
				appName = u.fixNameChars("appname", appName);
				if (appName.isEmpty()) {
					u.appOutput("Application name '" + appName + "' is blank. Use -appname");
					u.errorExit();
				}
				String invalidMsg = u.nameFormatValid("appname", appName);
				if (!invalidMsg.isEmpty()) {
					// if we get here, then fixNameChars() is not right
					u.appOutput("Application name '" + appName + "' still contains invalid character after removing known characters " + invalidMsg);
					u.errorExit();
				}

				inFileCopy = u.getImportFilePathName(reportName, inFile, appName);
			} 
			else {
				// process the already-imported files				
				inFileCopy = inFile;
				String line = u.importFileFirstLine(inFile);   // read only first line to pick up file attributes
				u.currentSrcFile = u.importFileAttribute(line, 1);
				u.currentAppName = u.importFileAttribute(line, 2);
				appName = u.currentAppName;
			}

			if (u.analysisPass == 1) {
				boolean replacing = false;
				if (!reAnalyze) {
					// process the input files when importing. i.e. the very first time
					if (Files.exists(Paths.get(inFileCopy))) {
						replacing = true;
					}
					u.appOutput(u.progressCnt(fileCount, nrFiles) + "Importing " + Paths.get(inFile).toAbsolutePath() + ", for application '" + appName + "'");
					if (replacing) {
						if (replaceFiles) {
							u.appOutput("Replacing input file " + Paths.get(inFile).toAbsolutePath());
						}
					}
				} 
				else {
					// process the already-imported files
					u.appOutput(u.progressCnt(fileCount, nrFiles) + "Re-processing " + u.currentSrcFile + ", for application '" + appName + "'");
				}

				if (dumpBatchFile) {
					String f = u.openBatchFile(reportName, inFile);
					u.appOutput("Logging SQL batches + parse trees to " + f);
				} 
				else {
					u.deleteBatchFile(reportName, inFile);
				}

				// handle charset conversion
				// NB: when specifying multiple input files, all input files will be handled with the same encoding
				if (!reAnalyze) {
					if (userEncoding == null) {
						// if no encoding specified, try to detect cases of UTF16
						String detectedEncoding = u.detectEncoding(inFile);
						if (detectedEncoding != null) {
							charset = Charset.forName(detectedEncoding);
							u.appOutput(u.stringRepeat(" ", u.progressCnt(fileCount, nrFiles).length()) + "Detected encoding '" + detectedEncoding + "' for input file " + inFile);
						} 
						else {
							charset = Charset.defaultCharset();
						}
					} 
					else {
						try {
							charset = Charset.forName(userEncoding);
						} catch (Exception e) {
							u.appOutput("Invalid -encoding value specified: [" + userEncoding + "]\nUse '-encoding help' to list available encodings.");
							return;
						}
					}
					if (u.debugging) u.dbgOutput(u.thisProc() + "Using encoding=[" + charset.toString() + "]", u.debugBatch);

					u.openImportFile(reportName, inFile, appName, charset.toString());  // open to write a copy of the input file

					fis = new FileInputStream(inFile);
					isr = new InputStreamReader(fis, charset);
				}
			}

			if ((u.analysisPass == 2) || (reAnalyze && (u.analysisPass == 1))) {
				String line = u.importFileFirstLine(inFileCopy);   // read only first line to pick up file attributes
				u.currentSrcFile = u.importFileAttribute(line, 1);
				u.currentAppName = u.importFileAttribute(line, 2);
				String batchesLines = u.importFileAttribute(line, 4);
				if ((reAnalyze && (u.analysisPass == 1))) {
					// don't print					
				}
				else {
					String w = "Analyzing";
					if (reAnalyze) w = "Re-analyzing";
					u.appOutput(u.progressCnt(fileCount, nrFiles) + w+" " + u.currentSrcFile + ", for application '" + u.currentAppName + "'; #batches/lines: " + batchesLines);
				}
			
				fis = new FileInputStream(inFileCopy);
				isr = new InputStreamReader(fis, "UTF-8");
			}
			
			BufferedReader inFileReader = new BufferedReader(isr);
			
			if (u.analysisPass== 2) {
				u.openCaptureFile(reportName, u.currentSrcFile, u.currentAppName);
			}
			
			CodePointCharStream charStream;

			// set QUOTED_IDENTIFIER to the default value at the start of the input file
			a.setQuotedIdentifier(quotedIdentifier);

			// process input file line by line, identifying batches to be parsed
			// this follows the 'sqlcmd' utility which uses 'go' and 'reset' as batch terminators
			// other sqlcmd commands/directives are not supported or detected except 'exit'/'quit'
			// Todo: other sqlcmd commands/directives are not supported (e.g. :r), these should be detected.

			StringBuilder batchText = new StringBuilder();
			int batchLines = 0;
			int lineNr = 0;
			int nrLinesInFile = 0;
			String line;
			boolean endBatchFound = false;
			boolean startOfNewBatch = true;
			int startBatchLineNr = 1;
			int inComment = 0;
			boolean inString = false;
			String openQuote = "";
			boolean endOfFile = false;
			boolean pass2Init = false;

			// set to true to ignore leading blank lines in a batch; 
			// this has the downside of line numbers being off by as many lines as were ignored
			boolean skipLeadingBlankLines = false;  

			long timeElapsed = 0;
			long timeElapsedFile = 0;

			int batchNr = 0;
			int nrParseErrors = 0;

			boolean doEncodingChecks = true;
			int nrEncodingWarnings = 0;
			int maxEncodingWarnings = 5;

			while (true) {
				boolean somethingFoundOnLine = false;
				line = inFileReader.readLine();
				if (line == null) {
					if (u.debugging) u.dbgOutput("end of file", u.debugBatch);
					endBatchFound = true;
					endOfFile = true;
					if (inComment > 0) {
						// seems we missed a comment close mark, let's add it
						if (u.debugging) u.dbgOutput("unclosed bracketed comment at end of file, adding " + inComment + " comment close marker(s)", u.debugBatch);
						for (int i = 0; i < inComment; i++) {
							batchText.append("\n */");
						}
					}
					if (inString) {
						// seems we missed a string close mark, let's add it
						if (u.debugging) u.dbgOutput("unclosed string at end of file, adding string delimiter [" + openQuote + "]", u.debugBatch);
						batchText.append(openQuote);
					}
				} 
				else {
					if (u.analysisPass == 1) {

						if (!reAnalyze) {
							u.writeImportFile(line);
						}
					}
					if ((u.analysisPass == 2) || (reAnalyze && (u.analysisPass == 1))) {
						if ((lineNr == 0) && (!pass2Init)) {
							// skip first line
							pass2Init = true;
							continue;
						}
					}

					// process the line. CR & LF have been stripped off the end
					// objective is to find the batch terminator, taking into account multi-line strings and potentially nested comments
					lineNr++;
					batchLines++;
					if (u.debugging) u.dbgOutput("read line " + lineNr + "(len:" + line.length() + ")=[" + line + "]", u.debugBatch);

					// check for indications that encoding is not correctly specified
					if (doEncodingChecks) {
						if (line.length() == 1) {
							if (line.charAt(0) == 0) {
								u.appOutput("Line " + lineNr + " contains only 0x00. Please verify input file encoding. Continuing, but errors may occur.");
								nrEncodingWarnings++;
							}
						}

						// don't drown the session in warnings
						if (nrEncodingWarnings > maxEncodingWarnings) {
							u.appOutput("(not reporting further 0x00-related errors)");
							doEncodingChecks = false;
						}
					}


					String lineCopy = line;
					boolean lineCopyProcessed = false;

					lineCopy = lineCopy.replaceAll("''", "");
					lineCopy = lineCopy.replaceAll("\"\"", " ");  // do not remove, we may need to detect this as a double-quoted string

					int lineCopyLenChk = lineCopy.length();
					int lineCopyLoopCntMax = 2;  // #times to check on line length not reducing
					int lineCopyLoopCnt = 0;
					int lineCopyLoopChk = 0;

					while (!lineCopyProcessed) {
						// loop protection, for some cases of invalid syntax, or incorrectly specified encoding
						lineCopyLoopCnt++;
						if (lineCopyLoopCnt > 1) {
							if (u.debugging) u.dbgOutput("loop chk top: prev length=[" + lineCopyLenChk + "], current length=[" + lineCopy.length() + "], lineCopy=[" + lineCopy + "]", u.debugBatch);
							if (lineCopyLenChk == lineCopy.length()) {
								lineCopyLoopChk++;
							} 
							else {
								lineCopyLoopChk = 0;
								lineCopyLenChk = lineCopy.length();
							}
							if (u.debugging) u.dbgOutput("loop chk top: lineCopyLoopChk=[" + lineCopyLoopChk + "]", u.debugBatch);
						}
						if (lineCopyLoopChk > lineCopyLoopCntMax) {
							// we seem to be in a loop...
							if (u.debugging) u.dbgOutput("loop chk: exit: lineCopy=[" + lineCopy + "]", u.debugBatch);
							u.appOutput("Error processing input file. Please verify input file encoding. Continuing, but errors may occur.");
							break;
						}
						if (u.debugging) u.dbgOutput("top loop: lineCopyLoopCnt=[" + lineCopyLoopCnt + "] inComment=" + inComment + ", inString=" + inString + ", lineCopy top=[" + lineCopy + "]", u.debugBatch);
						if (inString) {
							// do nothing until we find a matching closing quote
							int ix = lineCopy.indexOf(openQuote);
							if (ix > -1) {
								lineCopy = (lineCopy + ' ').substring(ix + 1);
								inString = false;
								somethingFoundOnLine = true;
								if (u.debugging) u.dbgOutput("string close found", u.debugBatch);
							} 
							else {
								break;
							}
						}

						if ((!inString) && (inComment == 0)) {
							// search for string or comment
							Pattern linePattern = Pattern.compile("^(.*?)((--|/\\*|'|\"|[\\[]).*$)");
							Matcher lineMatcher = linePattern.matcher(lineCopy);
							while (!lineCopyProcessed) {
								if (u.debugging) u.dbgOutput("lineCopy top loop A=[" + lineCopy + "]", u.debugBatch);
								if (lineMatcher.find()) {
									String prefix = lineMatcher.group(1);
									String token = lineMatcher.group(3);
									lineCopy = lineMatcher.group(2);
									if (u.debugging) u.dbgOutput("token=[" + token + "]", u.debugBatch);
									if (token.equals("--")) { // can be on a batch delimiter line
										if (u.debugging) u.dbgOutput("simple comment", u.debugBatch);
										lineCopyProcessed = true;
										break;
									}
									somethingFoundOnLine = true;

									int lineCopyLen = lineCopy.length();

									if (token.equals("[")) { // delimiter identifier, will not span line boundary
										lineCopy = u.applyPatternFirst(lineCopy, "[\\[].*?[\\]]", "");  // seen identifier in an XPath context:  SELECT ... AS [Account/*]
										if (u.debugging) u.dbgOutput("bracketed identifier", u.debugBatch);
										if (lineCopy.length() == lineCopyLen) {
											// likely invalid syntax, avoid getting into a loop
											if (u.debugging) u.dbgOutput("ignoring orphan square bracket", u.debugBatch);
											break;
										}
										// do another round of stripping
										lineMatcher = linePattern.matcher(lineCopy);
										continue;
									}

									switch (token) {
										case "/*":
											lineCopy = u.applyPatternFirst(lineCopy, "/\\*.*?\\*/", "");
											break;
										case "'":
											lineCopy = u.applyPatternFirst(lineCopy, "'.*?'", "");
											break;
										case "\"":
											lineCopy = u.applyPatternFirst(lineCopy, "\".*?\"", "");
											break;
									}
									if (u.debugging) u.dbgOutput("lineCopy after initial strip: len=" + lineCopyLen + ", [" + lineCopy + "]", u.debugBatch);

									// were any chars removed, or do we have an open string or comment?
									if (lineCopy.length() == lineCopyLen) {
										if (token.equals("/*")) {
											inComment++;
											lineCopy = (lineCopy + ' ').substring(2);
										} 
										else {
											openQuote = token;
											inString = true;
											lineCopy = (lineCopy + ' ').substring(1);
										}
										break;
									}
									// do another round of stripping
									lineMatcher = linePattern.matcher(lineCopy);
								} 
								else {
									if (u.debugging) u.dbgOutput("no match, top", u.debugBatch);
									lineCopyProcessed = true;
									break;
								}
							} // while
						}
						if (u.debugging) u.dbgOutput("somethingFoundOnLine=" + somethingFoundOnLine + ", inComment=" + inComment + ", inString=" + inString + ", lineCopy after strip=[" + lineCopy + "]", u.debugBatch);

						if (inComment > 0) {
							// do nothing until we find a matching closing delimiter - which can be nested in T-SQL
							Pattern commentPattern = Pattern.compile("^(.*?)(\\/\\*|\\*\\/)");
							Matcher commentMatcher = commentPattern.matcher(lineCopy);
							if (u.debugging) u.dbgOutput("lineCopy before loop B=[" + lineCopy + "]", u.debugBatch);
							boolean commentFound = false;
							while (commentMatcher.find()) {
								String p1 = commentMatcher.group(1);
								String c = commentMatcher.group(2);
								lineCopy = (lineCopy + ' ').substring((p1 + c).length());
								commentFound = true;
								if (c.equals("/*")) inComment++;
								else if (c.equals("*/")) inComment--;
								commentMatcher = commentPattern.matcher(lineCopy);
								somethingFoundOnLine = true;
								if (u.debugging) u.dbgOutput("inComment=[" + inComment + "]  p1=[" + p1 + "]  c=[" + c + "]  lineCopy=[" + lineCopy + "]", u.debugBatch);
								if (inComment == 0) {
									if (u.debugging) u.dbgOutput("break on inComment=0, [" + lineCopy + "]", u.debugBatch);
									break;
								}
							} // while
							if (!commentFound) {
								break;
							}
						}
					} // while

					if (!somethingFoundOnLine) {
						if ((!inString) && (inComment == 0)) {
							// check line for batch terminator
							line = u.applyPatternFirst(line, "^\\s*GO\\s*?(\\s\\d+\\s*|--.*)?$", "go");
							line = u.applyPatternFirst(line, "^\\s*RESET\\s*(--.*)?$", "reset");
							line = u.applyPatternFirst(line, "^\\s*(EXIT|QUIT)\\s*(--.*)?$", "exit");
							if (u.debugging) u.dbgOutput("read2=[" + line + "]", u.debugBatch);
							if (line.trim().equalsIgnoreCase("go")) {
								if (u.debugging) u.dbgOutput("line is go=[" + line + "]", u.debugBatch);
								line = "";
								endBatchFound = true;
							}
							if (line.trim().equalsIgnoreCase("reset")) {
								// Todo: batch is not written to the .batch file
								if (u.debugging) u.dbgOutput("line is reset=[" + line + "]", u.debugBatch);
								endBatchFound = false;
								startOfNewBatch = true;
								startBatchLineNr = lineNr + 1;
								batchText = new StringBuilder();
								continue;
							}
							if (line.trim().equalsIgnoreCase("exit")) {
								if (u.debugging) u.dbgOutput("exit found", u.debugBatch);
								endOfFile = true;
								endBatchFound = false;
								if (u.analysisPass == 1) {
									if (batchNr == 0) {
										u.appOutput("No batches found in this file.");
									}
								}

								break;
							}
						}
					}
					if (u.debugging) u.dbgOutput("startOfNewBatch=" + startOfNewBatch + ", endBatchFound=" + endBatchFound, u.debugBatch);

					if (startOfNewBatch && !endBatchFound) {
						if (line.trim().isEmpty()) {
							if (skipLeadingBlankLines) {
								if (u.debugging) u.dbgOutput("skipping LeadingBlankLines", u.debugBatch);
								continue;
							}
						}
						startOfNewBatch = false;
					}

					if (!endBatchFound) {
						if (u.debugging) u.dbgOutput("adding line=[" + line + "]", u.debugBatch);
						batchText.append(line).append("\n");
					}
				}

				if (endBatchFound) {
					// process the batch
					if (startOfNewBatch) {
						// nothing to process
						if (endOfFile) {
							if (u.analysisPass == 1) {
								if (batchNr == 0) {
									u.appOutput("No batches found in this file.");
								}
							}
							break;
						}
						if (u.debugging) u.dbgOutput("endBatchFound, startOfNewBatch: nothing to process", u.debugBatch);
						endBatchFound = false;
						continue;
					} 
					else {
						batchNr++;
						batchLines--; // subtract the last line

						if (u.analysisPass == 1) {
							nrLinesTotalP1 += batchLines;
						}
						if (u.analysisPass == 2) {
							nrLinesTotalP2 += batchLines;
						}

						u.batchNrInFile = batchNr;
						u.lineNrInFile = startBatchLineNr;

						if (dumpBatchFile) {
							if (u.analysisPass == 1) {
								u.writeBatchFile("\npass=[" + u.analysisPass + "] Batch " + batchNr + ", lineNrinFile=[" + lineNr + "],  batch=[" + batchText + "]");
							}
						}
						if (dumpParseTree) {
							u.appOutput("\npass=[" + u.analysisPass + "] Batch " + batchNr + "=[" + batchText + "]");
						}

						charStream = CharStreams.fromString(batchText.toString());

						// parse a batch and put the parse tree in the list for subsequent analysis
						startTime = System.currentTimeMillis();
						String ptreeText = parseBatch(charStream, inFile, batchNr, batchLines, antlrSLL);
						endTime = System.currentTimeMillis();
						duration = (endTime - startTime);
						timeElapsed = duration / 1000;
						timeCount.put("parseTime", timeCount.get("parseTime") + (int) duration);

						if (dumpBatchFile) {
							if (!hasParseError) {
								u.writeBatchFile(ptreeText);
							}
							u.writeBatchFile("Batch " + batchNr + ": lines=" + batchLines + ", parse time(secs)=" + timeElapsed);
						}

						if (hasParseError) {
							nrParseErrors++;
						}

						if (hasParseError) {
							boolean printErrMsg = true;
							if (u.analysisPass == 2) {
								if (!dumpParseTree) {
									printErrMsg = false;
								}
							}
							if (printErrMsg) {
								// print to session
								String errMsg = "Syntax error in batch " + batchNr + ", starting at line " + startBatchLineNr + " in input file\n" + parseErrorMsg.toString().trim();
								u.appOutput(errMsg);
								if (!dumpParseTree) {
									u.appOutput("");
								}  // need separator line
							}
						}

						if (u.analysisPass == 1) {
							if (hasParseError) {
								// write error batch
								if (u.errBatchFileWriter == null) {
									u.openErrBatchFile(reportName, inFile, runStartTime);
								}

								// log error batch to file
								u.writeErrBatchFile("Syntax error in batch " + batchNr + ", starting at line " + startBatchLineNr + " in file " + Paths.get(inFile).toAbsolutePath() + "\nBatch=[" + batchText + "]");
								u.writeErrBatchFile(parseErrorMsg.toString().trim() + "\n");
								u.writeErrBatchFile(u.composeOutputLine("-", "-") + "\n");
							}

							if (dumpParseTree) {
								if (!hasParseError) {
									u.appOutput(ptreeText);
								}
								u.appOutput("Batch " + batchNr + ": lines=" + batchLines + ", parse time(secs)=" + timeElapsed);
							}
						}

						// analyze the tree
						// can this be done in PLL ?
						if (!hasParseError) {
							if (parseOnly && (u.analysisPass > 1)) {
								// do nothing
							} 
							else {
								// even with -parseonly, we need to run analysis in order to process set quoted_identifier, which affects parsing
								String phase = "analysisTimeP" + u.analysisPass;
								startTime = System.currentTimeMillis();

								a.analyzeTree(exportedParseTree, batchNr, batchLines, u.analysisPass);
								
								endTime = System.currentTimeMillis();
								duration = (endTime - startTime);
								timeElapsed = duration / 1000;
								timeElapsedFile += duration;
								timeCount.put(phase, timeCount.get(phase) + (int) duration);
							}
						}

						if (hasParseError) {
							// clear error indication
							hasParseError = false;
							parseErrorMsg = new StringBuilder();
						}

						// prep for next batch
						endBatchFound = false;
						startOfNewBatch = true;
						startBatchLineNr = lineNr + 1;
						batchText = new StringBuilder();
						nrLinesInFile += batchLines;
						batchLines = 0;
						inComment = 0;
						inString = false;
						if (u.debugging) u.dbgOutput("resetting: startOfNewBatch=" + startOfNewBatch + ", endBatchFound=" + endBatchFound, u.debugBatch);
					}
				}

				if (endOfFile) {
					break;
				}
			}  // while

			passCount.put(u.analysisPass,1);
			if (passCount.size() > 1) {
				// don't add, or we'd be doubling up the totals
			} 
			else {
				totalBatches += batchNr;
				totalParseErrors += nrParseErrors;				
			}

			inFileReader.close();

			if (u.analysisPass == 1) {
				// save symbol table to disk
				try {
					String inFileTmp = inFile;
					if (reAnalyze) inFileTmp = u.currentSrcFile;
					//u.appOutput(u.thisProc()+"symtab inFile=["+inFileTmp+"] ");
					u.writeSymTab(reportName, inFileTmp, appName);
				} catch (Exception e) {
					u.appOutput("Error writing symbol table " + u.symTabFilePathName);
					throw e;
				}
				u.clearSymTab();
			}

			if (u.analysisPass == 2) {
				u.appendCaptureFile(u.makeMetricsLine(u.currentSrcFile, u.currentAppName, batchNr, nrParseErrors, lineNr));
				u.closeCaptureFile();
			}

			if (u.analysisPass == 2) {
				if (u.devOptions) {
					// temporary, for development
					int secs = ((int) timeElapsedFile / 1000);
					int linesSec = (secs > 0) ? (nrLinesInFile / secs) : nrLinesInFile;
					int batchesSec = (secs > 0) ? (batchNr / secs) : batchNr;
					u.appOutput("ELAPSED TIME: " + u.currentSrcFile + " : seconds=" + secs + "   lines/sec=" + linesSec + "   batches/sec=" + batchesSec);
				}
			}

			u.currentSrcFile = "";
			u.currentAppName = "";

			if (!reAnalyze) {
				u.closeImportFile();
				if (u.analysisPass == 1) {
					u.importFileUpdateBatchLines(inFileCopy, batchNr, nrLinesInFile);
				}
			}

			if (dumpBatchFile) {
				u.closeBatchFile();
			}
			if (u.errBatchFileWriter != null) {
				u.closeErrBatchFile();
			}
		} //for inputfiles
	}
	
	protected String parseBatch(CharStream batchText, String fileName, int batchNr, int batchLines, boolean useSLL)  {
		String batchTextCopy = batchText.toString();
		TSQLLexer lexer = new TSQLLexer(batchText);
		CommonTokenStream tokenStream = new CommonTokenStream(lexer);
		TSQLParser parser = new TSQLParser(tokenStream);

		// get the grammar rule names
		if (u.grammarRuleNames == null) {
			u.grammarRuleNames = parser.getRuleNames();
		}
		
		if ( antlrShowTokens ) {
			tokenStream.fill();
			for (Token tok : tokenStream.getTokens()) {
				if ( tok instanceof CommonToken ) {
					String stok = "text=["+tok.getText()+"] line="+ tok.getLine()+ "  col="+tok.getCharPositionInLine();
					System.out.println("stok: "+stok );
				}
				else {
					String stok = "text2=["+tok.getText()+"] line2="+ tok.getLine()+ "  col2="+tok.getCharPositionInLine();
					System.out.println("stok2: "+stok );
				}
			}
		}

		if ( antlrDiagnostics ) {
			parser.addErrorListener(new DiagnosticErrorListener());
			parser.getInterpreter().setPredictionMode(PredictionMode.LL_EXACT_AMBIG_DETECTION);
		}

		// set up parsing
		parser.setBuildParseTree(true);  
		parser.setTrace(antlrTrace);

		// capture parser error messages
		BaseErrorListener errorListener = new BaseErrorListener() {
			@Override
			public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
									int charPositionInLine, String msg, RecognitionException e) {
				Token token = (Token)offendingSymbol;
				msg = u.limitTextSize(msg);
				parseErrorMsg.append("Line ").append(line).append(":").append(charPositionInLine + 1).append(", ");

				if (token.getType() == TSQLLexer.UNMATCHED_CHARACTER) {
					u.addLexicalErrorHex(parseErrorMsg, token.getText());
				}
				else {
					parseErrorMsg.append("syntax error: ").append(msg);
				}
				hasParseError = true;
			}
		};
		parser.removeErrorListeners();
		parser.addErrorListener(errorListener);

		// add listener to always know last token
		CompassLastTokenListener lastTokenListener = new CompassLastTokenListener();
		parser.addParseListener(lastTokenListener);

		// stop parsing when an error is encountered
		parser.setErrorHandler(new BailErrorStrategy());

		if (useSLL) {
			if (u.debugging) u.dbgOutput("useSLL=[" + useSLL + "] batchNr=[" + batchNr + "] batchLines=[" + batchLines + "]", u.debugBatch);
			parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
		}

		String treeString = "";
		try {
			// get the parse tree
			TSQLParser.Tsql_fileContext tree = parser.tsql_file();

			// catch lexer errors, currently these don't throw an exception
			// like parser errors do
			if (parseErrorMsg.length() > 0) {
				return "";
			}

			// export the parse tree
			if (batchNr > 0) {
				exportedParseTree = tree;
			}

			// return parse tree as string, if required
			if (dumpParseTree) {
				treeString = tree.toStringTree(parser);
			}

		} catch (Exception e) {
			// we get here for parser errors
			if (u.debugging) u.dbgOutput("syntax error in catch; pass=" + u.analysisPass + " useSLL=[" + useSLL + "] batchNr=[" + batchNr + "] ", u.debugPtree);
			if (useSLL) {
				retrySLL++;
				parseErrorMsg = new StringBuilder();
				hasParseError = false;
				// retry with SLL = false
				return parseBatch(CharStreams.fromString(batchTextCopy), fileName, batchNr, batchLines, false);
			}

			if (parseErrorMsg.length() == 0) {
				String unmatchedLexerError = u.getAndSetNullErrorMsg();
				if (unmatchedLexerError != null) {
					parseErrorMsg.append(unmatchedLexerError);
				} 
				else {
					Token lastToken = lastTokenListener.getLastToken();
					parseErrorMsg.append("Line ").append(lastToken.getLine()).append(":").append(lastToken.getCharPositionInLine() + 1).
							append(", ").append("syntax error: Unable to parse token '").append(lastToken.getText()).append("'");
				}
			}
			hasParseError = true;
		}
		return treeString;
	}	
}
