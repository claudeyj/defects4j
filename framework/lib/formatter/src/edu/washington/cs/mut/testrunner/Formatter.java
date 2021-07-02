package edu.washington.cs.mut.testrunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.*;
import javax.xml.transform.stream.*;
import javax.xml.transform.dom.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;

import junit.framework.AssertionFailedError;
import junit.framework.Test;
import junit.framework.TestSuite;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitResultFormatter;
import org.apache.tools.ant.taskdefs.optional.junit.JUnitTest;


public class Formatter implements JUnitResultFormatter {

	private PrintStream ps;
	private PrintStream allTests;
	private PrintStream testlog;
	private List<TestInfo> testInfoList;
	private Map<String, Long> testStartTime;
	private TestInfo curTestInfo;


	{
		try {
			this.ps = new PrintStream(new FileOutputStream(System.getProperty("OUTFILE", "failing-tests.txt"), true), true);
			this.allTests = new PrintStream(new FileOutputStream(System.getProperty("ALLTESTS", "all_tests"), true), true);
			this.testlog = new PrintStream(new FileOutputStream(System.getProperty("DEFECTS4J_HOME", "/home/junyang/temp/defects4j") + "/log/testlog.log", true), true);
			this.testInfoList = new ArrayList<>();
			this.testStartTime = new HashMap<>();
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public void endTestSuite(JUnitTest junitTest) throws BuildException {
		try {
			writeTests(testInfoList, "test_time.xml");
		} catch (Exception e) {
			e.printStackTrace(testlog);
		}
		
		
	}

	@Override
	public void setOutput(OutputStream arg0) {
	}

	@Override
	public void setSystemError(String arg0) {	
	}

	@Override
	public void setSystemOutput(String arg0) {
	}

	String className ;
	boolean alreadyPrinted = true;
	
	@Override
	public void startTestSuite(JUnitTest junitTest) throws BuildException {
		className = junitTest.getName();
		alreadyPrinted = false;
	}
	

	@Override
	public void addError(Test test, Throwable t) {
		handle(test, t);
	}

	@Override
	public void addFailure(Test test, AssertionFailedError t) {
		handle(test,t);
	}
	
	private void handle(Test test, Throwable t) {
		String prefix = "--- " ;
		String className = null;
		String methodName = null;

		if (test == null) { // if test is null it indicates an initialization error for the class
			failClass(t, prefix);  
			return;
		}
		
		{
			Pattern regexp = Pattern.compile("(.*)\\((.*)\\)");
			Matcher match  = regexp.matcher(test.toString());
			if (match.matches()) {
				className = match.group(2);
				methodName = match.group(1);
			}
		}
		{
			Pattern regexp = Pattern.compile("(.*):(.*)"); // for some weird reason this format is used for Timeout in Junit4
			Matcher match  = regexp.matcher(test.toString());
			if (match.matches()) {
				className = match.group(1);
				methodName = match.group(2);
			}
		}
		
		if ("warning".equals(methodName) || "initializationError".equals(methodName)) {
			failClass(t, prefix); // there is an issue with the class, not the method.
		} else if (null != methodName && null != className) {
			if (isJunit4InitFail(t)) {
				failClass(t, prefix);
			} else {
				ps.println(prefix + className + "::" + methodName); // normal case
				t.printStackTrace(ps);
			}
		} else {
			ps.print(prefix + "broken test input " + test.toString());
			t.printStackTrace(ps);
		}
		
	}

	private void failClass(Throwable t, String prefix) {
		if (!this.alreadyPrinted) {
			ps.println(prefix + this.className);
			t.printStackTrace(ps);
			this.alreadyPrinted = true;
		}
	}

	private boolean isJunit4InitFail(Throwable t) {
		for (StackTraceElement ste: t.getStackTrace()) {
			if ("createTest".equals(ste.getMethodName())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void endTest(Test test) {
		long time = System.currentTimeMillis() - testStartTime.get(createDescription(test));
		// TestInfo testInfo = new TestInfo(test);
		curTestInfo.setTimeAndResult(time);
		testInfoList.add(curTestInfo);
	}

	@Override
	public void startTest(Test test) {
	    allTests.println(test.toString());
		testStartTime.put(createDescription(test), System.currentTimeMillis());
		curTestInfo = new TestInfo(test);
	}

	@Deprecated
	private void writeTestWithDom(Test test, long time, String xmlPath) throws Exception
	{
		//get XML file
		String testDescription = createDescription(test);
		DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        Document document = documentBuilder.parse(xmlPath);
        Element root = document.getDocumentElement();
		Element testNode = document.createElement("test");

		Element testName = document.createElement("testName");
		testName.appendChild(document.createTextNode(testDescription));

		Element testResult = document.createElement("result");
		testResult.appendChild(document.createTextNode(getResult(testDescription)));

		Element testTime = document.createElement("time");
		testTime.appendChild(document.createTextNode(String.valueOf(time)));

		testNode.appendChild(testName);
		testNode.appendChild(testResult);
		testNode.appendChild(testTime);

		root.appendChild(testNode);

		DOMSource source = new DOMSource(document);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        StreamResult streamResult = new StreamResult(xmlPath);
        transformer.transform(source, streamResult);
	}

	private void writeTests(List<TestInfo> testInfoList, String xmlPath) throws Exception
	{
		List<String> lines = Files.readAllLines(Paths.get(xmlPath), StandardCharsets.UTF_8);
		RandomAccessFile raf = new RandomAccessFile(xmlPath, "rw");
		for (TestInfo testInfo : testInfoList)
		{
			String testNameNode = makeNode(testInfo.testDescription, "name") ;
			String testResultNode = makeNode(testInfo.result, "result");
			String testTimeNode = makeNode(String.valueOf(testInfo.time), "time");
			String testNode = makeNode(testNameNode + testResultNode + testTimeNode, "test");
			String content = "\n\t" + testNode + "\n</tests>";
			raf.seek(raf.length() - 9);
			raf.write(content.getBytes());
		}
		raf.close();
	}

	private String makeNode(final String value, final String tag) {
        if (value != null) {
            return "<" + tag + ">" + value + "</" + tag + ">";
        } else {
            return "<" + tag + "/>";
        }
    }

	private String createDescription(final Test test) {
		// return JUnitVersionHelper.getTestCaseClassName(test) + "::" + JUnitVersionHelper.getTestCaseName(test);
		String className = null;
		String methodName = null;

		{
			Pattern regexp = Pattern.compile("(.*)\\((.*)\\)");
			Matcher match  = regexp.matcher(test.toString());
			if (match.matches()) {
				className = match.group(2);
				methodName = match.group(1);
			}
		}
		{
			Pattern regexp = Pattern.compile("(.*):(.*)"); // for some weird reason this format is used for Timeout in Junit4
			Matcher match  = regexp.matcher(test.toString());
			if (match.matches()) {
				className = match.group(1);
				methodName = match.group(2);
			}
		}

		return className + "::" + methodName;
    }

	private String getResult(final String testDescription)
	{
		try {
		String prefix = "--- " ;
		String failTestsPath = System.getProperty("OUTFILE");
		Set<String> failedTests = new HashSet<>();
		for (String line : Files.readAllLines(Paths.get(failTestsPath), StandardCharsets.UTF_8))
		{
			if (!line.startsWith(prefix)) continue;
			String stripped = line.trim();
			failedTests.add(stripped.split(" ")[1]);
		}

		return failedTests.contains(testDescription) ? "FAIL" : "SUCCESS";
	} catch(IOException e)
	{
		e.printStackTrace(testlog);
		return null;
	}
	}

	private class TestInfo
	{
		String testDescription;
		String result;
		long time;
		private TestInfo(Test test)
		{
			testDescription = createDescription(test);
			result = "TIMEOUT";
			time = -1;
		}
		public void setTimeAndResult(long time)
		{
			this.time = time;
			result = getResult(testDescription);
		}
	}
}
