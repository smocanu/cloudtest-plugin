package com.soasta.jenkins.cloud.postbuild;

import java.io.File;
import java.io.IOException;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.util.ArgumentListBuilder;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import com.soasta.jenkins.cloud.CloudCommandBuilder;
import com.soasta.jenkins.cloud.CloudStatus;

import javax.xml.parsers.*;
import java.io.*;

import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Recorder;

public abstract class CloudCommandBasePostBuild extends Recorder
{
  private final String name;
  private final String cloudTestServerID;
  private final String url;
  private final int timeOut; 

  public CloudCommandBasePostBuild(String url, String cloudTestServerID, String name)
  {
    this.name = name;
    this.cloudTestServerID = cloudTestServerID;
    this.url = url;
    this.timeOut = -1;
  }
  
  public CloudCommandBasePostBuild(String url, String cloudTestServerID, String name, int timeOut)
  {
    this.name = name;
    this.cloudTestServerID = cloudTestServerID;
    this.url = url;
    
    if (timeOut == 0)
    {
      this.timeOut = getDefaultTimeout();
    }
    else
    {
      this.timeOut = timeOut;
    }
  }
  
  public String getName()
  {
    return name;
  }
  
  public String getCloudTestServerID()
  {
    return cloudTestServerID;
  }
  
  public int getTimeOut()
  {
    return timeOut;
  }
  
  @Override
  public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
    String command = getCommand();
  // Create a unique sub-directory to store all test results.
    String resultsDir = "." + command;
    // set the basic commands. 
    ArgumentListBuilder args =
    new CloudCommandBuilder()
    .setBuild(build)
    .setUrl(url)
    .setListener(listener)
    .setCloudTestServerID(cloudTestServerID)
    .build();
    
    args.add("cmd=" + command, "wait=true", "format=xml")
        .add("name=" + name);
        
    if (timeOut >= 0)
    {
      args.add("timeout=" + timeOut);
    }
    
    String fileName = name + ".xml";

    // Strip off any leading slash characters (composition names
    // will typically be the full CloudTest folder path).
    if (fileName.startsWith("/")) {
        fileName = fileName.substring(1);
    }

    // Put the file in the test results directory.
    fileName = resultsDir + File.separator + fileName;
    
    FilePath xml = new FilePath(build.getWorkspace(), fileName);
    
    // Make sure the directory exists.
    xml.getParent().mkdirs();

    // Run it!
    int exitCode = launcher
        .launch()
        .cmds(args)
        .pwd(build.getWorkspace())
        .stdout(xml.write())
        .stderr(listener.getLogger())
        .join();

    if (xml.length() == 0)
    {
        // SCommand did not produce any output.
        // This should never happen, but just in case...
        return false;
    }

    try
    {
      return isSucessful(xml.readToString());
    }
    catch (Exception e)
    {
      e.printStackTrace();
      
      return false;
    }
  }
  /**
   * Returns the specific cloud command. E.g 'start-grid', 'start-env', 'terminate-env'
   * @return
   */
  public abstract String getCommand();
  
  /**
   * Returns the expected str for a sucessful start / terminate. 
   * @return
   */
  public abstract CloudStatus getSuccessStatus();
  
  /**
   * Returns the timeout until Checked Status, in Seconds. 
   * @return
   */
  public abstract int getDefaultTimeout();
  
  /**
   * Parses the output xml for a success code. 
   * @param xml
   * @return
   */
  private boolean isSucessful(String xml) throws Exception
  {
    DocumentBuilderFactory factory =
    DocumentBuilderFactory.newInstance();
    DocumentBuilder builder = factory.newDocumentBuilder();
    Document doc = builder.parse(new InputSource(new ByteArrayInputStream(xml.getBytes("utf-8"))));
    
    NodeList list = doc.getElementsByTagName("Status");
    
    if (list != null && list.getLength() > 0)
    {
      String successCriteria = getSuccessStatus().name();
      return list.item(0).getTextContent().equals(successCriteria);
    }
    
    return false;
  }
  
  @Override
  public BuildStepMonitor getRequiredMonitorService()
  {
    return BuildStepMonitor.NONE;
  }

}
