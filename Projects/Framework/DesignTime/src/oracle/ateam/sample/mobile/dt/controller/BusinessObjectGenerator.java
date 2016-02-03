/*******************************************************************************
 Copyright (c) 2014,2015, Oracle and/or its affiliates. All rights reserved.
 
 $revision_history$
 06-feb-2013   Steven Davelaar
 1.0           initial creation
******************************************************************************/
package oracle.ateam.sample.mobile.dt.controller;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import java.net.URL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import java.util.Properties;

import oracle.adfdt.model.ide.managers.ApplicationManager;
import oracle.adfdt.model.objects.Application;

import oracle.adfmf.common.util.McAppUtils;
import oracle.adfmf.framework.dt.editor.FrameworkXmlEditorConstants;
import oracle.adfmf.framework.dt.editor.FrameworkXmlKeys;

import oracle.ateam.sample.mobile.dt.model.BusinessObjectGeneratorModel;
import oracle.ateam.sample.mobile.dt.model.DataObjectInfo;
import oracle.ateam.sample.mobile.dt.controller.velocity.VelocityInitializer;
import oracle.ateam.sample.mobile.dt.controller.velocity.VelocityTemplateProcessor;

import oracle.ateam.sample.mobile.dt.util.FileUtils;

import oracle.ateam.sample.mobile.dt.util.ProjectUtils;

import oracle.bali.xml.addin.XMLSourceNode;
import oracle.bali.xml.gui.base.xmlComponent.XmlComponentModel;
import oracle.bali.xml.gui.jdev.JDevXmlContext;
import oracle.bali.xml.gui.jdev.xmlComponent.XmlPanelGui;
import oracle.bali.xml.model.XmlView;

import oracle.ide.Context;
import oracle.ide.model.NodeFactory;
import oracle.ide.model.Project;
import oracle.ide.model.TextNode;
import oracle.ide.net.URLFactory;
import oracle.ide.net.URLFileSystem;
import oracle.ide.net.URLPath;

import oracle.javatools.buffer.TextBuffer;
import oracle.javatools.parser.java.v2.model.SourceElement;
import oracle.javatools.parser.java.v2.model.SourceFile;
import oracle.javatools.parser.java.v2.write.SourceTransaction;

import oracle.jdeveloper.java.JavaManager;
import oracle.jdeveloper.java.TransactionDescriptor;
import oracle.jdeveloper.model.JProjectUtil;
import oracle.jdeveloper.model.JavaSourceNode;
import oracle.jdeveloper.model.PathsConfiguration;


public class BusinessObjectGenerator
{
  private BusinessObjectGeneratorModel model;
  private Project project;

  public BusinessObjectGenerator(BusinessObjectGeneratorModel model)
  {
    super();
    this.model = model;
    this.project = model.getGeneratorProject();
  }

  public void run()
    throws IOException
  {
    Project appControllerProject = ProjectUtils.getApplicationControllerProject();

    GeneratorLogPage log = GeneratorLogPage.getPage(model.getLogTitle());
    log.initialize();
    log.info(model.getLogTitle()+" started");

    // add extension libs
    // Very strange: if there is other lib but the AMPA libs, the source editor shows all refernces to AMPA classes/methods red underlined!
    // if we add one other strandard lib, doesn't matter which one, everything is fine again... Need to log bug...
    // so, that's why Apache Ant is added here...
    String[] libraries = new String[] { "A-Team Mobile Persistence Designtime", "A-Team Mobile Persistence Runtime", "Apache Ant"};
    JProjectUtil.addLibraries(project, libraries);
    log.info("Added A-Team Mobile Persistence Libraries to "+project.getShortLabel());
    JProjectUtil.addLibraries(appControllerProject, libraries);
    log.info("Added A-Team Mobile Persistence Libraries to "+appControllerProject.getShortLabel());
    VelocityInitializer vi = new VelocityInitializer();
    // wsm = web service model
    vi.initVelocity("WSM",model.isMaf20Style());
    VelocityTemplateProcessor processor = new VelocityTemplateProcessor(vi);

    for (DataObjectInfo doi: model.getSelectedDataObjects())
    {
      // set fully qualified package name if new data object using generator options wizard page setting
      // we need to do this in separate loop because parentr data object class might need inport of child service class
      if (!doi.isExisting())
      {
        doi.setPackageName(model.getPackageName());        
        doi.setServicePackageName(model.getServicePackageName());
      }
    }

    // generate data object Java class
    for (DataObjectInfo doi: model.getSelectedDataObjects())
    {
      model.setCurrentDataObject(doi);
      URL sourceURL = FileUtils.getSourceURL(project, doi.getPackageName(), doi.getClassName() + ".java");
      if (!FileUtils.fileExists(sourceURL) || model.isOverwriteDataObjectClasses())
      {
        String output = processor.processTemplate(model, "dataObjectClass.vm");
        FileUtils.addFileToProject(sourceURL, output, null);
        FileUtils.formatJavaFile(project, sourceURL);
        log.info("Data object class "+doi.getFullyQualifiedClassName() + ".java"+" created in "+project.getShortLabel());        
      }
      if (doi.isGenerateServiceClass())
      {
        sourceURL = FileUtils.getSourceURL(project, doi.getServicePackageName(), doi.getServiceClassName() + ".java");
        if (!FileUtils.fileExists(sourceURL) || model.isOverwriteServiceObjectClasses())
        {
          String output = processor.processTemplate(model, "serviceObjectClass.vm");
          FileUtils.addFileToProject(sourceURL, output, null);
          FileUtils.formatJavaFile(project, sourceURL);        
          log.info("Service object class "+ doi.getFullyQualifiedServiceClassName() + ".java"+" created in "+project.getShortLabel());          
        }
      }
    }

    // generate sql ddl file
    // get current file content, so we can append new content
    String fileName = model.getWorkspaceName() + ".sql";
    URL sourceURL = FileUtils.getSourceURL(appControllerProject, "META-INF", fileName);
    String content = FileUtils.getStringFromInputStream(FileUtils.getInputStream(sourceURL));
    if (content!=null)
    {
      content = removeSelectedTablesFromExistingSQLFile(content);
      model.setSqlFileContent(content);      
    }
    String output = processor.processTemplate(model, "sql.vm");
    FileUtils.addFileToProject(sourceURL, output, null);
    String verb = content!=null ? " updated " : " created ";
    log.info("SQLite DDL script "+fileName+verb+" in src/META-INF folder in "+appControllerProject.getShortLabel());

    // generate OLD MAF 2.0 persistence mapping file
     fileName = "persistenceMapping.xml";
     sourceURL = FileUtils.getSourceURL(appControllerProject, "META-INF", fileName);
     content = FileUtils.getStringFromInputStream(FileUtils.getInputStream(sourceURL));
    if (content!=null)
    {
      Map<String,String> mappings = getExistingMappings(content);
      model.setExistingDescriptorMappings(new ArrayList<String>(mappings.values()));            
      output = processor.processTemplate(model, "persistenceMapping.vm");
      if (output!=null)
      {
        FileUtils.addFileToProject(sourceURL, output, null);      
        verb = content!=null ? " updated " : " created ";
        log.info("Object-relational mapping file "+fileName+verb+" in src/META-INF folder in "+appControllerProject.getShortLabel());
      }
      else
      {
        log.error("Error creating object-relational mapping file "+fileName+verb+" in src/META-INF folder in "+appControllerProject.getShortLabel());
      //      throw new RuntimeException("Error parsing persistenceMapping.vm");
      }
    }
    else
    {
      // generate new persistence-mapping.xml using jaxb model
      fileName = "persistence-mapping.xml";
      sourceURL = FileUtils.getSourceURL(appControllerProject, "META-INF", fileName);
      InputStream is =FileUtils.getInputStream(sourceURL);
      PersistenceMappingGenerator pmg = new PersistenceMappingGenerator(model);
      output = pmg.run();
      if (output!=null)
      {
        FileUtils.addFileToProject(sourceURL, output, null);      
        verb = is!=null ? " updated " : " created ";
        log.info("Object-relational mapping file "+fileName+verb+" in src/META-INF folder in "+appControllerProject.getShortLabel());
      }
      else
      {
        log.error("Error creating object-relational mapping file "+fileName+verb+" in src/META-INF folder in "+appControllerProject.getShortLabel());
      //      throw new RuntimeException("Error parsing persistenceMapping.vm");
      }
    }

    // generate config properties file
    fileName = "mobile-persistence-config.properties";
    sourceURL = FileUtils.getSourceURL(appControllerProject, "META-INF", fileName);
    // 2-2-2016: Always regenerate properties file to get/update MCS entries when MCS connection is used after initialy usage of wizard
    InputStream is =FileUtils.getInputStream(sourceURL);
//    if (!URLFileSystem.exists(sourceURL))
//    {
    // don't regenerate when running edit persistence mapping wizard
    if (!model.isEditMode()) 
    {
      output = processor.processTemplate(model, "persistenceConfig.vm");
      FileUtils.addFileToProject(sourceURL, output, null);      
      verb = is!=null ? " updated " : " created ";
      log.info("Persistence configuration file "+fileName+verb+"  in src/META-INF folder in "+appControllerProject.getShortLabel());      

      setApplicationListenerClass(log);      
    }


    if (model.getDataControl()!=null)
    {
      addWebServiceDataControlUsage(log);      
    }
    log.info(model.getLogTitle()+" finished succesfully");
  }

  private void setApplicationListenerClass(GeneratorLogPage log)
  {
    // set app listener in adfmf-application.xml
    XMLSourceNode appXml = McAppUtils.findOrCreateApplicationXml(project.getWorkspace());
    JDevXmlContext jdevXmlContext = JDevXmlContext.getXmlContext(new Context(appXml));
    XmlView appView = jdevXmlContext.getView(FrameworkXmlEditorConstants.APPLICATION_USAGE);
    XmlPanelGui appPanelGui = new XmlPanelGui(appView); 
    XmlComponentModel appXmlModel = XmlComponentModel.createXmlComponentModel(FrameworkXmlKeys.XMLKEY_ROOT_ATTR_LISTENER_CLASS,
                                              FrameworkXmlKeys.PANEL_ROOT_XMLKEY,
                                              appPanelGui); 
    String listener = appXmlModel.getModelValue();
    if (listener==null || listener.endsWith(".LifeCycleListenerImpl"))
    {
      appXmlModel.updateModelValue("oracle.ateam.sample.mobile.lifecycle.InitDBLifeCycleListener");
      log.info("Application Lifecycle Event Listener class in adfmf-application.xml set to oracle.ateam.sample.mobile.lifecycle.InitDBLifeCycleListener");      
    }
  }

  /**
   * This method is only used for generating old MAF 2.0 style persistenceMapping XML.
   * @param text
   * @return
   */
  private Map<String,String> getExistingMappings(String text)
  {
    String startElem = "<class-mapping-descriptor>";
    String endElem = "</class-mapping-descriptor>";
    //    StringTokenizer st = new StringTokenizer(text,"");
    int startMappingPos= text.indexOf(startElem);
    Map<String,String> mappings = new HashMap<String,String>();
    while(startMappingPos>-1)
    {
      int startpos= text.indexOf("<class>",startMappingPos);
      int endpos= text.indexOf("</class>",startMappingPos);
      String className = text.substring(startpos+7,endpos);
      int endMappingPos= text.indexOf(endElem,startMappingPos);
      String mappingContent = text.substring(startMappingPos,endMappingPos+endElem.length());   
      mappings.put(className,mappingContent);
      startMappingPos = text.indexOf("<class-mapping-descriptor>",startMappingPos+25);
    }        
    // remove mappings that wll be regenerated
    for (DataObjectInfo doi : model.getSelectedDataObjects())
    {
      String fullyQualifiedClassName = model.getPackageName()+"."+doi.getClassName();
      mappings.remove(fullyQualifiedClassName);
    }
    // also remove DATA_SYNCH_ACTIONS mapping
    mappings.remove("oracle.ateam.sample.mobile.persistence.service.DataSynchAction");
    return mappings;
  }

  private String removeSelectedTablesFromExistingSQLFile(String content)
  {
    String newContent = content;
    for (DataObjectInfo dataObject: model.getSelectedDataObjects())
    {
      String startMatch = "CREATE TABLE " + dataObject.getTableName() + " ";
      int startPos = newContent.indexOf(startMatch);
      if (startPos > -1)
      {
        int endPos = newContent.indexOf(");", startPos);
        newContent = newContent.substring(0, (startPos==0 ? 0 : startPos - 1)) + newContent.substring(endPos + 2);
      }
    }
    // also remove DATA_SYNCH_ACTIONS table
    String startMatch = "CREATE TABLE DATA_SYNCH_ACTIONS ";
    int startPos = newContent.indexOf(startMatch);
    if (startPos > -1)
    {
      int endPos = newContent.indexOf(");", startPos);
      newContent = newContent.substring(0, (startPos==0 ? 0 : startPos - 1)) + newContent.substring(endPos + 2);
    }
    // also remove WEB_SERVICE_CALL table
     startMatch = "CREATE TABLE WEB_SERVICE_CALL ";
     startPos = newContent.indexOf(startMatch);
    if (startPos > -1)
    {
      int endPos = newContent.indexOf(");", startPos);
      newContent = newContent.substring(0, (startPos==0 ? 0 : startPos - 1)) + newContent.substring(endPos + 2);
    }
    // also remove STORAGE_OBJECT table
     startMatch = "CREATE TABLE STORAGE_OBJECT ";
     startPos = newContent.indexOf(startMatch);
    if (startPos > -1)
    {
      int endPos = newContent.indexOf(");", startPos);
      newContent = newContent.substring(0, (startPos==0 ? 0 : startPos - 1)) + newContent.substring(endPos + 2);
    }
    return newContent;
  }

  
  private void addWebServiceDataControlUsage(GeneratorLogPage log)
  {
    Context context = new Context(null, project);
    Application application = ApplicationManager.getDTApplication(context);
    if (application == null)
    {
      //        mApplication = ApplicationManager.createKavaApplication(mProject, mViewPackage+".DataBindings");
      String defaultPackage = project.getProperty("defaultPackage");
      application =
          ApplicationManager.findOrCreateApplication(context, defaultPackage +
                                                     ".DataBindings");
    }    
    // application.findOrCreateDataControl(model.getDataControl());
    ApplicationManager.findOrCreateDataControlInApplication(application, model.getDataControl());
    log.info("Added usage for "+model.getDataControlName()+" data control to DataBindings.cpx");
  }
}
