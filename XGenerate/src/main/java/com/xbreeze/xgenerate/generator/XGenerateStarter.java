package com.xbreeze.xgenerate.generator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.xbreeze.license.LicenseException;
import com.xbreeze.license.LicensedClassLoader;
import com.xbreeze.xgenerate.config.ConfigException;
import com.xbreeze.xgenerate.config.app.XGenAppConfig;
import com.xbreeze.xgenerate.gui.GenerationProgressScreen;
import com.xbreeze.xgenerate.observer.GenerationObserverSource;

/**
 * Class for starting up generator using licensed classloader
 * @author Willem
 *
 */
public class XGenerateStarter extends GenerationObserverSource {
	
	//Set the parent logger in this class by creating a logger named com.xbreeze
	private static final Logger logger = Logger.getLogger("");
	
	/**
	 * The main for running CrossGenerate from command line.
	 * @param args The arguments.
	 */
	public static void main(String[] args) {
		
		// Check whether the number of arguments is correct.
		if (args.length <= 2 || args.length % 2 != 0) {
			if (args.length > 0) {
				System.err.println("Incorrect number of arguments specified");
			}
			System.out.println("Usage: CrossGenerate -config|c AppConfigFileLocation [-ModelTemplateConfig|mtc ModelFileLocation::TemplateFileLocation::GenConfigFileLocation]+ [-debug|d true] [-consoleLogLevel|cll fine|info|warning] [-fileLogLevel|fll fine|info|warning] [-fileLogDestination|fld path-to-logfile] [-progressScreen|ps true]");
		}
		
		// If correct amount of arguments, go through the arguments.
		else {
			
			new XGenerateStarter(args);
		}
	}
		
	public XGenerateStarter(String[] args) {			
		// Set debug mode default.
		boolean debugMode = false;
		
		// Setup the global LogManager.
		LogManager logManager = LogManager.getLogManager();
		// Read the logging configuration from the resource file.
		try {
			logManager.readConfiguration(XGenerateStarter.class.getResourceAsStream("logging.properties"));
		} catch (SecurityException | IOException e) {
			System.err.println(String.format("Error while getting logging configuration", e.getMessage()));
		}
		
		// Create a list for the model-template-config combinations.
		ArrayList<ModelTemplateConfigCombination> modelTemplateConfigCombinations = new ArrayList<ModelTemplateConfigCombination>();
		// Initialize a XGenAppConfig object to be set using the commands.
		XGenAppConfig appConfig = null;
		
		// Try reading the parameters from the console.
		try {
			
			Level fileLogLevel = null;
			String fileLogDestination = null;
			
			// Loop through the arguments as pairs.
			for (int i=0; i<args.length; i+=2) {
				String key = args[i];
				String value = args[i+1];
				
				// Handle the key-value pair.
				switch (key.toLowerCase()) {
					// config | c
					case "-c":
					case "-config":
						// If the appConfig is already set, print en error.
						if (appConfig != null)
							throw new GeneratorException("The app config is specified multiple times");
						
						try {
							appConfig = XGenAppConfig.fromFile(Paths.get(value).toUri());
						} catch (ConfigException e) {
							throw new GeneratorException(String.format("Error found in app config: %s", e.getMessage()));
						}
						break;
					// ModelTemplateConfig | mtc
					case "-mtc":
					case "-modeltemplateconfig":
						modelTemplateConfigCombinations.add(ModelTemplateConfigCombination.fromString(value));
						break;
					// debug | d
					case "-d":
					case "-debug":
						debugMode = Boolean.parseBoolean(value);
						break;
					// consoleLogLevel | cll
					case "-cll":
					case "-consoleloglevel":
						// Add a logger for the console to log message below warning (and error).
						ConsoleHandler outputConsoleHandler = new ConsoleHandler() {
							@Override
							protected synchronized void setOutputStream(OutputStream out) throws SecurityException {
								super.setOutputStream(System.out);
							}
						};
						Level consoleLogLevel = getLogLevel(value);
						outputConsoleHandler.setLevel(consoleLogLevel);
						// Only log message with a lower level then warning.
						outputConsoleHandler.setFilter(new Filter() {
							@Override
							public boolean isLoggable(LogRecord record) {
								return record.getLevel().intValue() < Level.SEVERE.intValue();
							}
						});
						// Update the log level to the lowest level.
						logger.setLevel((consoleLogLevel.intValue() < logger.getLevel().intValue()) ? consoleLogLevel : logger.getLevel());
						logger.addHandler(outputConsoleHandler);
						break;
					// fileLogLevel | fll
					case "-fll":
					case "-fileloglevel":
						// Set the system property for file handler level.
						fileLogLevel = getLogLevel(value);
						break;
					// fileLogDestination | fld
					case "-fld":
					case "-filelogdestination":
						fileLogDestination = value;
						break;
					case "-ps":
					case "-progressscreen":
						try {
							this.addGenerationObserver(new GenerationProgressScreen());
						} catch (Exception e) {
							e.printStackTrace();
							throw new GeneratorException(String.format("Error while showing progress screen: '%s'", e.getMessage()));
						}
						break;
					// unrecognized parameter
					default:
						throw new GeneratorException(String.format("Unexpected parameter specified: '%s'", key));
				}
			}
			
			// If the file log destination is set, create a file handler.
			if (fileLogDestination != null && fileLogDestination.length() > 0) {
				try {
					// Create the file handler for the file logger.
					FileHandler fh = new FileHandler(fileLogDestination, true);
					if (fileLogLevel != null) {
						// Set the log level on the file handler.
						fh.setLevel(fileLogLevel);
						// Update the log level to the lowest level.
						logger.setLevel((fileLogLevel.intValue() < logger.getLevel().intValue()) ? fileLogLevel : logger.getLevel());							
					}
					// Add the file log handler.
					logger.addHandler(fh);
				} catch (SecurityException | IOException e) {
					throw new GeneratorException(String.format("Error setting log destination: %s", e.getMessage()));
				}
			}
			// If the file log level is set, but the destination is not set, throw an exception.
			else if (fileLogLevel != null) {
				throw new GeneratorException("fileLogLevel is set but no fileLogDestination specified.");
			}
			
			// If the appConfig is still null, print an error.
			if (appConfig == null)
				throw new GeneratorException("No app config file location was specified");
			
			// If there is no mtc combination specified, print an error.
			if (modelTemplateConfigCombinations.size() == 0)
				throw new GeneratorException("No Model-Template-Config (mtc) combination(s) specified");
		}
		// When an error occurred during reading of all arguments, print an error in the console.
		catch (GeneratorException e) {
			System.err.println(e.getMessage());
			// Return, so the generator won't start when an error occured uptill here.
			return;
		}
		
		// All parameters are read and logging setup, so we can safely start the generator.
		startGenerator(appConfig, modelTemplateConfigCombinations, debugMode);
	}
	
	/**
	 * Procedure for starting the generator.
	 * @param appConfig The XGenAppConfig.
	 * @param modelTemplateConfigCombinations The ModelTemplateConfig combination(s).
	 * @param debugMode Debug mode indicator.
	 */
	private void startGenerator(XGenAppConfig appConfig, ArrayList<ModelTemplateConfigCombination> modelTemplateConfigCombinations, boolean debugMode) {
		try {
			// Notify the generation observers the generation is starting.
			this.notifyGenerationStarting(modelTemplateConfigCombinations.size(), LocalDateTime.now());
			
			//Load Generator from licenseClassLoader
			LicensedClassLoader lcl = new LicensedClassLoader(GeneratorStub.class.getClassLoader(), appConfig.getLicenseConfig(), debugMode);
			
			// Load generator class from licensed ClassLoader
			Class<?> c = lcl.loadClass("com.xbreeze.xgenerate.generator.Generator");                                      
            // Instantiate new object
			GeneratorStub generator = (GeneratorStub)c.newInstance();

			// Loop through the model-template-config combinations and perform the generation.
			for (int generationStepIndex=0; generationStepIndex<modelTemplateConfigCombinations.size(); generationStepIndex++) {
				ModelTemplateConfigCombination modelTemplateConfigCombination = modelTemplateConfigCombinations.get(generationStepIndex);
				
				// Notify the generation observers the generation step is starting.
				this.notifyGenerationStepStarting(generationStepIndex, modelTemplateConfigCombination.getTemplateFileLocation(), LocalDateTime.now());
				
				// Create the full paths to the needed files.
				URI modelFileLocation = Paths.get(appConfig.getAppConfig().getModelFolder(), modelTemplateConfigCombination.getModelFileLocation()).toUri();
				URI templateFileLocation = Paths.get(appConfig.getAppConfig().getTemplateFolder(), modelTemplateConfigCombination.getTemplateFileLocation()).toUri();
				URI configFileLocation = Paths.get(appConfig.getAppConfig().getConfigFolder(), modelTemplateConfigCombination.getConfigFileLocation()).toUri();
				// Write the output to the output folder and the relative folder the template is in.
				URI outputFolderLocation = Paths.get(appConfig.getAppConfig().getOutputFolder()).toUri();
				
				// Derive the relative template folder, if there is a parent folder for the template file.
				String relativeTemplateFolder = "";
				Path templateFileParent = Paths.get(modelTemplateConfigCombination.getTemplateFileLocation()).getParent();
				if (templateFileParent != null)
					relativeTemplateFolder = templateFileParent.toString();
				
				logger.info("Starting CrossGenerate with the following arguments:");
				logger.info(String.format(" - ModelFileLocation: %s", modelFileLocation));
				logger.info(String.format(" - TemplateFileLocation: %s", templateFileLocation));
				logger.info(String.format(" - ConfigFileLocation: %s", configFileLocation));
				logger.info(String.format(" - OutputFolderLocation: %s", outputFolderLocation));
				if (debugMode) {
					logger.warning("Debug mode enabled");
				}
								
				generator.setDebugMode(debugMode);
				// Set the model using the file location.
				generator.setModelFromFile(modelFileLocation);
				// Wrap the generation in a try-catch so we can inform the observers if something went wrong.
				try {
					// Generate the output using the file locations.
					generator.generateFromFilesAndWriteOutput(templateFileLocation, configFileLocation, outputFolderLocation, relativeTemplateFolder);
				} catch (GeneratorException ge) {
					// Notify the generation observers the generation step is failed.
					this.notifyGenerationStepFailed(generationStepIndex, modelTemplateConfigCombination.getTemplateFileLocation(), ge.getMessage(), LocalDateTime.now());
					// Re-throw the exception so it is handled correctly.
					throw ge;
				}
				
				// Notify the generation observers the generation step is finished.
				this.notifyGenerationStepFinished(generationStepIndex, modelTemplateConfigCombination.getTemplateFileLocation(), LocalDateTime.now());
			}
			
			// Notify the generation observers the generation is finished.
			this.notifyGenerationFinished(LocalDateTime.now());
			logger.info("Generation complete");
		} catch (GeneratorException | LicenseException | ClassNotFoundException | IllegalAccessException | InstantiationException e) {
			logger.severe("Error occured while generating");
			logger.severe(e.getMessage());
			System.err.println("Error occured while generating, see log for more information");
		}
		finally {
			// Close all existing log handlers
			for(Handler h : logger.getHandlers())
			{
			    h.close();   
			}
		}
	}
	
	/**
	 * Get the log level using the textual representation from the config.
	 * @param level The log level
	 * @return The Level constant.
	 * @throws GeneratorException
	 */
	private static Level getLogLevel(String level) throws GeneratorException {
		try {
			return Level.parse(level.toUpperCase());
		} catch (IllegalArgumentException | NullPointerException e) {
			throw new GeneratorException(String.format("Unknown LogLevel specified: '%s'", level), e);
		}		
	}
}
