package com.xbreeze.xgenerate.generator;

public class GenerationResult {
	
	public enum GenerationStatus {
		OK,
		ERROR
	}
	
	private String _templateFileName;
	
	private String _modelFileName;
	
	private String _preprocessedTemplate;
	
	private String _outputFileContent;
	
//	private String _outputFileLocation;
	
	/**
	 * The generation result status.
	 */
	private GenerationStatus _status = GenerationStatus.OK;
	
	/**
	 * The exception object for when the GenerationStatus is ERROR.
	 */
	private GeneratorException _exception;
	
	/**
	 * Constructor.
	 * @param modelFileName
	 * @param templateFileName
	 */
	public GenerationResult(String modelFileName, String templateFileName) {
		this._modelFileName = modelFileName;
		this._templateFileName = templateFileName;
	}

	/**
	 * @return the result
	 */
	public String getOutputFileContent() {
		return _outputFileContent;
	}

	/**
	 * @param outputFileContent the result to set
	 */
	public void setOutputFileContent(String outputFileContent) {
		this._outputFileContent = outputFileContent;
	}

//	/**
//	 * @return the outputFileLocation
//	 */
//	public String getOutputFileLocation() {
//		return _outputFileLocation;
//	}
//
//	/**
//	 * @param outputFileLocation the outputFileLocation to set
//	 */
//	public void setOutputFileLocation(String outputFileLocation) {
//		this._outputFileLocation = outputFileLocation;
//	}

	/**
	 * @return the preprocessedTemplate
	 */
	public String getPreprocessedTemplate() {
		return _preprocessedTemplate;
	}

	/**
	 * @param preprocessedTemplate the preprocessedTemplate to set
	 */
	public void setPreprocessedTemplate(String preprocessedTemplate) {
		this._preprocessedTemplate = preprocessedTemplate;
	}

	/**
	 * @return the templateFileName
	 */
	public String getTemplateFileName() {
		return _templateFileName;
	}

	/**
	 * @param templateFileName the templateFileName to set
	 */
	public void setTemplateFileName(String templateFileName) {
		this._templateFileName = templateFileName;
	}

	/**
	 * @return the modelFileName
	 */
	public String getModelFileName() {
		return _modelFileName;
	}

	/**
	 * @param modelFileName the modelFileName to set
	 */
	public void setModelFileName(String modelFileName) {
		this._modelFileName = modelFileName;
	}

	/**
	 * @return the status
	 */
	public GenerationStatus getStatus() {
		return _status;
	}

	/**
	 * @param status the status to set
	 */
	public void setStatus(GenerationStatus status) {
		this._status = status;
	}

	/**
	 * @return the exception
	 */
	public GeneratorException getException() {
		return _exception;
	}

	/**
	 * @param exception the exception to set
	 */
	public void setException(GeneratorException exception) {
		this.setStatus(GenerationStatus.ERROR);
		this._exception = exception;
	}
}
