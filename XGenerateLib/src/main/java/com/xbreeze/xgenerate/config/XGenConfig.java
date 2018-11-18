package com.xbreeze.xgenerate.config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.logging.Logger;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.UnmarshalException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.io.IOUtils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

import com.xbreeze.xgenerate.config.binding.BindingConfig;
import com.xbreeze.xgenerate.config.model.ModelConfig;
import com.xbreeze.xgenerate.config.template.RootTemplateConfig;
import com.xbreeze.xgenerate.config.template.TextTemplateConfig;
import com.xbreeze.xgenerate.config.template.XMLTemplateConfig;
import com.xbreeze.xgenerate.generator.GeneratorException;
import com.xbreeze.xgenerate.utils.FileUtils;
import com.xbreeze.xgenerate.utils.XMLUtils;
import com.ximpleware.*;
import com.ximpleware.xpath.*;

/**
 * The XGenConfig class represents the configuration object for CrossGenerate.
 * It contains the model, template and binding configuration.
 * @author Harmen
 */
@XmlRootElement(name="XGenConfig") // , namespace="http://generate.x-breeze.com/XGenConfig"
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder={"modelConfig","templateConfig","bindingConfig"})
public class XGenConfig {
	// The logger for this class.
	private static final Logger logger = Logger.getLogger(XGenConfig.class.getName());
	
	/**
	 * The model configuration.
	 * @see ModelConfig
	 */
	@XmlElement(name="Model")
	private ModelConfig modelConfig;
	
	// 
	/**
	 * The template configuration
	 * @see XMLTemplateConfig
	 */
    @XmlElements({
        @XmlElement(name="TextTemplate", type=TextTemplateConfig.class),
        @XmlElement(name="XmlTemplate", type=XMLTemplateConfig.class),
    })
	private RootTemplateConfig templateConfig;
	
	/**
	 * The binding configuration.
	 * @see BindingConfig
	 */
	@XmlElement(name="Binding")
	private BindingConfig bindingConfig;

	/**
	 * @return the model
	 */
	public ModelConfig getModelConfig() {
		return modelConfig;
	}

	/**
	 * @param modelConfig the model to set
	 */
	public void setModelConfig(ModelConfig modelConfig) {
		this.modelConfig = modelConfig;
	}

	/**
	 * @return the template
	 */
	public RootTemplateConfig getTemplateConfig() {
		return templateConfig;
	}

	/**
	 * @param templateConfig the template to set
	 */
	public void setTemplateConfig(XMLTemplateConfig templateConfig) {
		this.templateConfig = templateConfig;
	}

	/**
	 * @return the binding
	 */
	public BindingConfig getBindingConfig() {
		return bindingConfig;
	}

	/**
	 * @param bindingConfig the binding to set
	 */
	public void setBindingConfig(BindingConfig bindingConfig) {
		this.bindingConfig = bindingConfig;
	}
	
	/**
	 * Unmarshal a config from a String.
	 * @param configFileContent The String object to unmarshal.
	 * @param basePath, the basepath used to resolve relative XIncludes
	 * @return The unmarshelled XGenConfig object.
	 * @throws ConfigException
	 */
	public static XGenConfig fromString(String configFileContent, URI basePath) throws ConfigException {		
		XGenConfig xGenConfig;
		// Before validating against the XSD, resolve any includes first
		LinkedList<URI> resolvedIncludes = new LinkedList<>();
		logger.info(String.format("Reading config from %s and resolving includes when found.", basePath.toString()));
		String resolvedInputSource = getConfigWithResolvedIncludes(configFileContent, basePath, resolvedIncludes);
		// Create a resource on the schema file.
		// Schema file generated using following tutorial: https://examples.javacodegeeks.com/core-java/xml/bind/jaxb-schema-validation-example/
		String xGenConfigXsdFileName = String.format("%s.xsd", XGenConfig.class.getSimpleName());
		InputStream xGenConfigSchemaAsStream = XGenConfig.class.getResourceAsStream(xGenConfigXsdFileName);
		// If the schema file can't be found, throw an exception.
		if (xGenConfigSchemaAsStream == null) {
			throw new ConfigException(String.format("Can't find the schema file '%s'", xGenConfigXsdFileName));
		}
		// Create the StreamSource for the schema.
		StreamSource xGenConfigXsdResource = new StreamSource(xGenConfigSchemaAsStream);
		
		// Try to load the schema.
		Schema configSchema;
		try {
			SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			configSchema = sf.newSchema(xGenConfigXsdResource);
		} catch (SAXException e) {
			throw new ConfigException(String.format("Couldn't read the schema file (%s)", xGenConfigXsdResource.toString()), e);
		}
		
		// Try to unmarshal the config file.
		try {
			// Create the JAXB context.
			JAXBContext jaxbContext = JAXBContext.newInstance(XGenConfig.class);
			Unmarshaller xGenConfigUnmarshaller = jaxbContext.createUnmarshaller();
			//xGenConfigUnmarshaller.setSchema(configSchema);
			//Create a SAXParser factory
			 SAXParserFactory spf = SAXParserFactory.newInstance();
			 //Since XInclude processing is handled through custom implementation, disabled in the spf
			 // spf.setXIncludeAware(true);
			 spf.setNamespaceAware(true);
			 spf.setSchema(configSchema);
			 XMLReader xr = spf.newSAXParser().getXMLReader();
			 //Prevent xml:base tags from being added when includes are processed
			 xr.setFeature("http://apache.org/xml/features/xinclude/fixup-base-uris", false);
			 SAXSource saxSource = new SAXSource(xr, new InputSource(new StringReader(resolvedInputSource)));
			
			 // Set the event handler.
			 xGenConfigUnmarshaller.setEventHandler(new UnmarshallValidationEventHandler());			
					 
			// Unmarshal the config.			
			xGenConfig = (XGenConfig) xGenConfigUnmarshaller.unmarshal(saxSource);
		} catch (UnmarshalException | SAXException  e) {
			// If the linked exception is a sax parse exception, it contains the error in the config file.
			if (e instanceof UnmarshalException && ((UnmarshalException)e).getLinkedException() instanceof SAXParseException) {
				throw new ConfigException(String.format("Error in config file: %s", ((UnmarshalException)e).getLinkedException().getMessage()), e);
			} else {
				throw new ConfigException(String.format("Error in config file: %s", e.getMessage()), e);
			}
		} catch (JAXBException e) {
			throw new ConfigException(String.format("Couldn't read the config file"), e);
		} catch (ParserConfigurationException e) { 
			throw new ConfigException(String.format("Parser configuration error"), e);
		} 
		logger.info(String.format("Reading config from %s complete.", basePath.toString()));
		return xGenConfig;
		
	}
	
	/**
	 * Unmarshal a file into a XGenConfig object.
	 * @param configFileUri The file to unmarshal.
	 * @return The unmarshalled XGenConfig object.
	 * @throws ConfigException 
	 */
	public static XGenConfig fromFile(URI configFileUri) throws ConfigException {
		logger.fine(String.format("Creating XGenConfigFile object from '%s'", configFileUri));		
		XGenConfig xGenConfig;
		try {
			xGenConfig = fromString(FileUtils.getFileContent(configFileUri), configFileUri);
		} catch (ConfigException | IOException e) {
			// Catch the config exception here to add the filename in the exception text.
			throw new ConfigException(String.format("%s (%s)", e.getMessage(), configFileUri.toString()), e.getCause());
		} 		
		return xGenConfig;
	}
	

	/**
	 * Recursively resolve XIncludes in the xGenConfig string	 * 
	 * @param xGenConfig The config that might include XIncludes to resolve
	 * @param basePath The basePath needed for relative inclusions
	 * @param resolvedIncludes A collection of previously resolved includes to detect a cycle of inclusions
	 * @return The inputSource with resolved includes 
	 * @throws ConfigException
	 */
	private static String getConfigWithResolvedIncludes(String xGenConfig, URI configFileUri, LinkedList<URI> resolvedIncludes) throws ConfigException{
		logger.fine(String.format("Scanning config file %s for includes", configFileUri.toString()));
	
		//Get basePath of configFile. If the provided URI refers to a file, us its parent path, if it refers to a folder use it as base path
		try {
			URI basePath  = new URI("file:///../");			
			File configFile = new File(configFileUri.getPath());
			if (configFile.isDirectory())
					basePath = configFileUri;
			if (configFile.isFile()) {
				String parentPath =configFile.getParent();
				if (parentPath != null) {			
					basePath = Paths.get(parentPath).toUri();	
				}
			}
			
			//	Open the config file and look for includes		
			VTDNav nav = XMLUtils.getVTDNav(xGenConfig);
			AutoPilot ap = new AutoPilot(nav); 
			//@TODO Make this XPath namespace aware so it actually lookes for xi:include instead of include in all namespaces
			ap.selectXPath("//include");
			int includeCount = 0;		
			try {
				XMLModifier vm = new XMLModifier (nav);
				while ((ap.evalXPath()) != -1) {
					//obtain the filename of include
					AutoPilot ap_href = new AutoPilot(nav);
					ap_href.selectXPath("@href");
					String includeFileLocation = ap_href.evalXPathToString();
					logger.fine(String.format("Found include for %s in config file %s", includeFileLocation, configFileUri.toString()));
					//resolve include to a valid path against the basePath
					logger.fine(String.format("base path %s", basePath.toString()));
					Path p = Paths.get(basePath);
					URI includeFileUri = p.resolve(Paths.get(includeFileLocation)).toAbsolutePath().toUri();
					logger.fine(String.format("Resolved include to %s", includeFileUri.toString()));
					//Check for cycle detection, e.g. an include that is already included previously
					if (resolvedIncludes.contains(includeFileUri)) {
						throw new ConfigException(String.format("Config include cycle detected, file %s is already included in a file that includes %s", includeFileUri.toString(), configFileUri.toString()));
					}
					resolvedIncludes.add(includeFileUri);
					//get file contents, recursively processing any includes found
					try {
					String includeContents = getConfigWithResolvedIncludes(FileUtils.getFileContent(includeFileUri), includeFileUri, resolvedIncludes);
					//Replace the node with the include contents
					vm.insertAfterElement(includeContents);
					//then remove the include node
					vm.remove();					
					} catch (IOException e) {
						throw new ConfigException(String.format("Could not read contents of included config file %s", includeFileUri.toString()), e);
					}	
					includeCount++;
				}
				logger.fine(String.format("Found %d includes in config file %s", includeCount, configFileUri.toString()));
				//if includes were found, output and parse the modifier and return it, otherwise return the original one
				if (includeCount > 0) {
					String resolvedXGenConfig = XMLUtils.getResultingXml(vm);					
					logger.fine(String.format("Config file %s with includes resolved:", configFileUri.toString()));
					logger.fine("**** Begin of config file ****");
					logger.fine(resolvedXGenConfig);
					logger.fine("**** End of config file ****");					
					return resolvedXGenConfig;
				} else {
					return xGenConfig;
				}
			} catch (NavException e) {
				throw new ConfigException(String.format("Error scanning %s for includes", configFileUri.toString()),e);
			} 	catch (ModifyException e  ) {
				throw new ConfigException(String.format("Error modifying config file %s", configFileUri.toString()), e);
			} 
		} catch (URISyntaxException e) {
			throw new ConfigException(String.format("Could not extract base path from config file %s", configFileUri.toString()), e);
		}	catch (GeneratorException e) {
			throw new ConfigException(String.format("Could not read config from %s", configFileUri.toString()), e);		
		} catch (XPathParseException | XPathEvalException e) {
			throw new ConfigException(String.format("XPath error scanning for includes in %s", configFileUri.toString()), e);				
		}
	}	
}
