package com.xbreeze.xgenerate.template;

import java.net.URI;
import java.util.ListIterator;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.xbreeze.xgenerate.UnhandledException;
import com.xbreeze.xgenerate.config.XGenConfig;
import com.xbreeze.xgenerate.config.binding.SectionModelBindingConfig;
import com.xbreeze.xgenerate.config.template.TemplateConfig;
import com.xbreeze.xgenerate.template.annotation.TemplateAnnotation;
import com.xbreeze.xgenerate.template.annotation.TemplateCommentAnnotation;
import com.xbreeze.xgenerate.template.annotation.TemplateSectionAnnotation;
import com.xbreeze.xgenerate.template.annotation.TemplateSectionAnnotation.RepetitionAction;
import com.xbreeze.xgenerate.template.annotation.TemplateSectionAnnotation.RepetitionStyle;
import com.xbreeze.xgenerate.template.annotation.TemplateSectionAnnotation.RepetitionType;
import com.xbreeze.xgenerate.template.annotation.TemplateSectionBoundsAnnotation;
import com.xbreeze.xgenerate.template.annotation.UnknownAnnotationException;
import com.xbreeze.xgenerate.template.section.CommentTemplateSection;
import com.xbreeze.xgenerate.template.section.NamedTemplateSection;
import com.xbreeze.xgenerate.template.section.RawTemplateSection;
import com.xbreeze.xgenerate.template.section.RepetitionTemplateSection;
import com.xbreeze.xgenerate.template.section.SectionedTemplate;
import com.xbreeze.xgenerate.template.xml.XMLUtils;

public abstract class TemplatePreprocessor {
	// The logger for this class.
	protected static final Logger logger = Logger.getLogger(TemplatePreprocessor.class.getName());
	
	/**
	 * The template config to use for pre-processing.
	 */
	protected XGenConfig _config;
	
	/**
	 * Constructor.
	 * Only to be used by child classes.
	 * @param rawTemplate The raw template.
	 * @param config The XGenConfig.
	 */
	public TemplatePreprocessor(XGenConfig config) {
		this._config = config;
	}
	
	/**
	 * @param config the XGenConfig to set
	 */
	public void setConfig(XGenConfig config) {
		this._config = config;
	}
	
	/**
	 * Perform the pre-processing to get to the pre-processed template.
	 * @return The pre-processed template.
	 * @throws UnhandledException 
	 * @throws UnknownAnnotationException 
	 */
	public XsltTemplate preProcess(RawTemplate rawTemplate, URI outputFileUri) throws TemplatePreprocessorException, UnhandledException {
		TemplateConfig templateConfig = _config.getTemplateConfig();
		
		// Perform the specific sectionizing for the current template.
		// This should detect sections from the raw template and transform it into a SectionedTemplate object.
		String rootSectionName = templateConfig.getRootSectionName();
		
		SectionModelBindingConfig[] rootSectionModelBindings = null;
		if (_config.getBindingConfig() != null)
			rootSectionModelBindings = _config.getBindingConfig().getSectionModelBindingConfigs(rootSectionName);
		
		// Check whether there is only 1 root section model binding. If not, throw an exception.
		if (rootSectionModelBindings == null || rootSectionModelBindings.length != 1) {
			throw new TemplatePreprocessorException("There must and can only be 1 section model binding for the root section.");
		}
		
		// Assign the section model binding for the root section to a local variable.
		SectionModelBindingConfig rootSectionModelBinding = rootSectionModelBindings[0];
		
		// Pre-process the template.
		PreprocessedTemplate preprocessedTemplate = this.getPreprocessedTemplate(rawTemplate);
		
		// Sectionize the template.
		SectionedTemplate sectionizedTemplate = this.sectionizeTemplate(preprocessedTemplate, rootSectionName);
		
		// Now the templates are pre-processed by their specific preprocessor, we can perform the generic pre-processing here.
		// TODO: Put in right output folder.
		XsltTemplate xsltTemplate = new XsltTemplate(rawTemplate.getRawTemplateFileName(), rawTemplate.getRawTemplateFileLocation(), templateConfig, outputFileUri, rootSectionModelBinding);
		
		// Append the Xslt from the section to the pre-processed template.
		sectionizedTemplate.appendTemplateXslt(xsltTemplate, _config, rootSectionModelBinding);
		
		// Finalize the template before returning it.
		xsltTemplate.finalizeTemplate();
		
		// Return the pre-processed template.
		return xsltTemplate;
	}
	
	/**
	 * Procedure which each specific implementation of the TemplatePreprocessor needs to implement to get to the generic PreprocessedTemplate.
	 * In this procedure the raw template needs to be pre-processed and annotations gathered.
	 */
	protected abstract PreprocessedTemplate getPreprocessedTemplate(RawTemplate rawTemplate) throws TemplatePreprocessorException;
	
	/**
	 * Sectionize the template after its annotations and configurations are processed into a list of annotations.
	 * @param preprocessedTemplate The pre-processed template.
	 * @param rootSectionName The root section name.
	 * @return The sectionized template.
	 * @throws TemplatePreprocessorException
	 */
	private SectionedTemplate sectionizeTemplate(PreprocessedTemplate preprocessedTemplate, String rootSectionName) throws TemplatePreprocessorException {
		// Initialize the sectionized template.
		SectionedTemplate sectionizedTextTemplate = new SectionedTemplate(rootSectionName);
		// The end index of the root section is the length of the raw template.
		int rootSectionEndIndex = preprocessedTemplate.getPreprocessedRawTemplate().length();
		// Set the root section end index on the sectionized template.
		sectionizedTextTemplate.setSectionEndIndex(rootSectionEndIndex);
		
		// We use the ListIterator here since we can go forward and backward.
		ListIterator<TemplateAnnotation> taIterator = preprocessedTemplate.getTemplateAnnotations().listIterator();
		
		// Create an implicit template section bounds annotation with the begin index on 0.
		TemplateSectionBoundsAnnotation rootSectionBoundsAnnotation = new TemplateSectionBoundsAnnotation(sectionizedTextTemplate.getTemplateSectionAnnotation(), 0);
		// Set the root section bounds end index.
		rootSectionBoundsAnnotation.setAnnotationEndIndex(rootSectionEndIndex);
		
		// Process the content of the section.
		// Pass in 0 for parentPreviousSectionEndIndex.
		processNamedTemplateSection(rootSectionBoundsAnnotation, sectionizedTextTemplate, preprocessedTemplate.getPreprocessedRawTemplate(), taIterator, 0, true);
		
		// Return the SectionizedTextTemplate;
		return sectionizedTextTemplate;
	}
	
	/**
	 * Process the named template section.
	 * @param namedTemplateSection
	 * @param rawTemplateContent
	 * @throws TemplatePreprocessorException 
	 */
	private int processNamedTemplateSection(TemplateSectionBoundsAnnotation parentSectionBounds, NamedTemplateSection parentTemplateSection, String rawTemplateContent, ListIterator<TemplateAnnotation> taIterator, int parentPreviousSectionEndIndex, boolean isRootSection) throws TemplatePreprocessorException {
		logger.fine(String.format("processNamedTemplateSection called for section '%s', parentPreviousSectionEndIndex=%d", parentTemplateSection.getSectionName(), parentPreviousSectionEndIndex));
		// Loop through the template annotations.
		int previousSectionEndIndex = parentPreviousSectionEndIndex; 
		
		// Before processing the content, we first check whether a prefix is set on the annotation.
		if (parentSectionBounds.getTemplateSectionAnnotation().getPrefix() != null && parentSectionBounds.getTemplateSectionAnnotation().getPrefix().length() > 0) {
			// If the prefix is set, we scan for any whitespace at the beginning of the section and store it as a separate RawTemplate part after which we add the RepetitionTemplateSection for the prefix.
			logger.info(String.format("Prefix is defined for section '%s', searching for whitespace and creating appropriate sections.", parentTemplateSection.getSectionName()));
			// Now we scan for any whitespace at the end of the found raw-template and add the suffix section before the whitespace.
			// So <raw-template><prefix>
			// \A     -> The begin of the input
			// [ \t]+ -> Any space or tab: [ \t\n\x0B\f\r]
		    Pattern pattern = Pattern.compile(String.format("\\A[ \\t]+"));
		    Matcher matcher = pattern.matcher(rawTemplateContent.substring(previousSectionEndIndex));
			if (matcher.find()) {
				// Get the begin and end position of the whitespace at the begin of the section.
				int whitespaceStartIndex = previousSectionEndIndex + matcher.start();
				int whitespaceEndIndex = previousSectionEndIndex + matcher.end();
				logger.fine(String.format("Whitespace found between %d and %d, so creating seperate sections.", whitespaceStartIndex, whitespaceEndIndex));
				// Add the template content before the suffix position as a raw template.
				addRawTemplate(parentTemplateSection, rawTemplateContent, whitespaceStartIndex, whitespaceEndIndex);
				// Add the repetition template section for the suffix.
				addRepetitionTemplate(parentTemplateSection, parentSectionBounds.getTemplateSectionAnnotation().getPrefix(), whitespaceEndIndex, RepetitionType.prefix, parentSectionBounds.getTemplateSectionAnnotation().getPrefixStyle(), parentSectionBounds.getTemplateSectionAnnotation().getPrefixAction());
			}
			// No whitespace found, so add the suffix to the start.
			else {
				logger.fine("No whitespace found, so creating prefix repetition section at the start.");
				// Add the repetition template section for the suffix.
				addRepetitionTemplate(parentTemplateSection, parentSectionBounds.getTemplateSectionAnnotation().getPrefix(), previousSectionEndIndex, RepetitionType.prefix, parentSectionBounds.getTemplateSectionAnnotation().getPrefixStyle(), parentSectionBounds.getTemplateSectionAnnotation().getPrefixAction());
			}
		}
		
		// Loop till we reached the end of the template./
		// If we reach the end of a section in between its handled within the loop.
		while (previousSectionEndIndex < rawTemplateContent.length()) {
			
			// Initialize the template annotation variable. This will only be assigned when there is still an annotation available.
			TemplateAnnotation templateAnnotation = null;
			
			// Check whether there is something between the previous and current annotation.
			{
				int nextSectionBeginIndex;
				// If there is another annotation, store the next annotation begin index.
				if (taIterator.hasNext()) {
					// Store the next annotation in the variable.
					templateAnnotation = taIterator.next();
					// Store the next annotation in a local variable (also moves the cursor one forward).
					nextSectionBeginIndex = templateAnnotation.getAnnotationBeginIndex();
				}
				// Otherwise we are at the end of the template and there might be some raw template left.
				else {
					// Set the actual 'next section' index to the end of the template.
					nextSectionBeginIndex = rawTemplateContent.length();
				}
				
				// If the templateAnnotation start index is not the next number after the previous section end, we add a RawTemplateSection to the parent NamedTemplateSection.
				// Let's check whether there is some template section which is after the last section and before the next (a raw template section).
				if (nextSectionBeginIndex > previousSectionEndIndex) {
					
					// Let's check whether the current parent section ends before the next annotation.
					int parentSectionEndIndex = parentSectionBounds.getAnnotationEndIndex();
					
					// Only check for end of section when it is not set yet (for the root its always set).
					if (parentSectionEndIndex == -1) {
						// Before we add the raw template, we first check whether the end of the section is in this part of raw template.
						// If so we set the end index of this section, add the template up till that part as raw template, set the iterator one back and return the end index (so the parent will pickup the remaining annotations).
						logger.info(String.format("Searching for section end index for '%s' between index %d and %d", parentTemplateSection.getSectionName(), previousSectionEndIndex, nextSectionBeginIndex));
						parentSectionEndIndex = findSectionEndIndex(parentSectionBounds, rawTemplateContent, previousSectionEndIndex, nextSectionBeginIndex);
						if (parentSectionEndIndex != -1) {
							// Store the section end index on the parent annotation.
							parentSectionBounds.setAnnotationEndIndex(parentSectionEndIndex);
							// Store the end index on the parent template section.
							parentTemplateSection.setSectionEndIndex(parentSectionEndIndex);
							logger.info(String.format("Successfully found begin and end position of section (%s -> %d:%d)", parentTemplateSection.getSectionName(), parentTemplateSection.getSectionBeginIndex(), parentTemplateSection.getSectionEndIndex()));
						}
					}
					
					// Check whether the end index is found (-1 means not found), and whether the end index is before the next annotation.
					if (parentSectionEndIndex != -1 && parentSectionEndIndex <= nextSectionBeginIndex) {
						
						// If the parent section end index is later then the expected beginning of the next section we create a raw template with the part between.
						if (parentSectionEndIndex > previousSectionEndIndex) {
							// If there is a suffix defined for the parent section, add a repetition section before the newline.
							TemplateSectionAnnotation currentSectionAnnotation = parentSectionBounds.getTemplateSectionAnnotation();
							if (currentSectionAnnotation.getSuffix() != null && currentSectionAnnotation.getSuffix().length() > 0) {
								logger.info(String.format("Suffix is defined for section '%s', searching for whitespace and creating appropriate sections.", parentTemplateSection.getSectionName()));
								// Now we scan for any whitespace at the end of the found raw-template and add the suffix section before the whitespace.
								// So <raw-template><suffix><whitespace>
								// \s  -> Any whitespace character: [ \t\n\x0B\f\r]
								// \z  -> The end of the input
							    Pattern pattern = Pattern.compile(String.format("\\s+\\z"));
							    Matcher matcher = pattern.matcher(rawTemplateContent.substring(0, parentSectionEndIndex));
								if (matcher.find(previousSectionEndIndex)) {
									// Get the begin and end position of the whitespace at the end of the section.
									int whitespaceStartIndex = matcher.start();
									int whitespaceEndIndex = matcher.end();
									logger.fine(String.format("Whitespace found between %d and %d, so creating seperate sections.", whitespaceStartIndex, whitespaceEndIndex));
									// Add the template content before the suffix position as a raw template.
									addRawTemplate(parentTemplateSection, rawTemplateContent, previousSectionEndIndex, whitespaceStartIndex);
									// Add the repetition template section for the suffix.
									addRepetitionTemplate(parentTemplateSection, currentSectionAnnotation.getSuffix(), whitespaceStartIndex, RepetitionType.suffix, currentSectionAnnotation.getSuffixStyle(), currentSectionAnnotation.getSuffixAction());
									// Add the raw template containing the ending whitespace.
									addRawTemplate(parentTemplateSection, rawTemplateContent, whitespaceStartIndex, whitespaceEndIndex);
								}
								// No whitespace found, so add the suffix to the end.
								else {
									logger.fine("No whitespace found, so creating repetition section at the end.");
									// Add the whole template part as raw template, since there is no whitespace at the end.
									addRawTemplate(parentTemplateSection, rawTemplateContent, previousSectionEndIndex, parentSectionEndIndex);
									// Add the repetition template section for the suffix.
									addRepetitionTemplate(parentTemplateSection, currentSectionAnnotation.getSuffix(), parentSectionEndIndex, RepetitionType.suffix, currentSectionAnnotation.getSuffixStyle(), currentSectionAnnotation.getSuffixAction());
								}
							}
							// No suffix defined, so all remaining is raw template.
							else {
								addRawTemplate(parentTemplateSection, rawTemplateContent, previousSectionEndIndex, parentSectionEndIndex);
							}
						}
						
						// Move the cursor on the iterator one back so the parent will handle the current annotation.
						if (templateAnnotation != null)
							taIterator.previous();
						
						// Return the parent section end index as the previousSectionEndIndex and let the parent section handle the rest of the annotations (since this one is closed).
						return parentSectionEndIndex;
					}
					// The end of section wasn't found, so all before the new section is raw template of the parent.
					else {
						addRawTemplate(parentTemplateSection, rawTemplateContent, previousSectionEndIndex, nextSectionBeginIndex);
						// Set the end index to the end of the raw template.
						previousSectionEndIndex = nextSectionBeginIndex;
						
						// When the templateAnnotation is null, this is the last piece of raw template.
						// So return the index of the end here.
						if (templateAnnotation == null)
							return previousSectionEndIndex;
					}
				}
			}
			
			
			// Get the next template annotation and handle it.
			{
				// When the templateAnnotation is null here we have a problem. This shouldn't occur.
				if (templateAnnotation == null)
					throw new TemplatePreprocessorException("An illegal state has been reached while pre-processing the template, trying to process an annotation in the last loop.");
				
				// Based on the type of annotation we can have different actions.
				// For example when it is a TemplateCommentAnnotation, we just add it to the current NamedTemplate.
				// Here we go through the annotation types one by one.
				
				// TemplateCommentAnnotation
				if (templateAnnotation instanceof TemplateCommentAnnotation) {
					// Add a CommentTemplateSection for each TemplateCommentAnnotation.
					TemplateCommentAnnotation templateCommentAnnotation = ((TemplateCommentAnnotation) templateAnnotation);
					parentTemplateSection.addTemplateSection(new CommentTemplateSection(templateCommentAnnotation.getComment(), templateCommentAnnotation.getAnnotationBeginIndex(), templateCommentAnnotation.getAnnotationEndIndex()));
					logger.info(String.format("Added CommentTemplateSection to SectionizedTextTemplate (%d:%d)", templateCommentAnnotation.getAnnotationBeginIndex(), templateCommentAnnotation.getAnnotationEndIndex()));
				}
				
				else if (templateAnnotation instanceof TemplateSectionAnnotation) {
					// If it is a template section annotation we skip this section.
					// It's not represented in the preprocessed template.
				}
				
				// TemplateSectionBoundsAnnotation
				else if (templateAnnotation instanceof TemplateSectionBoundsAnnotation) {
					// Add a NamedTemplateSection for each TemplateSectionAnnotation.
					TemplateSectionBoundsAnnotation templateSectionBoundsAnnotation = (TemplateSectionBoundsAnnotation) templateAnnotation;
					logger.info(String.format("Start of processing NamedTemplateSection %s", templateSectionBoundsAnnotation.getName()));
					// Create the named template section.
					NamedTemplateSection namedTemplateSection = new NamedTemplateSection(templateSectionBoundsAnnotation.getName(), templateSectionBoundsAnnotation.getAnnotationBeginIndex(), templateSectionBoundsAnnotation.getTemplateSectionAnnotation());
					// If the end index is already known, also set it.
					if (templateSectionBoundsAnnotation.getAnnotationEndIndex() != -1)
						namedTemplateSection.setSectionEndIndex(templateSectionBoundsAnnotation.getAnnotationEndIndex());
					
					// Process the content of the named template (recursively).
					// This process will return the end index of the section (or throw an exception if not found).
					processNamedTemplateSection(templateSectionBoundsAnnotation, namedTemplateSection, rawTemplateContent, taIterator, previousSectionEndIndex, false);
					// Add the named template section to the sectionized template.
					parentTemplateSection.addTemplateSection(namedTemplateSection);
					logger.info(String.format("Added NamedTemplateSection to SectionizedTextTemplate (%s -> %d:%d)", namedTemplateSection.getSectionName(), namedTemplateSection.getSectionBeginIndex(), namedTemplateSection.getSectionEndIndex()));
				}
				
				// If there is some other annotation found we didn't handle, let's throw an exception.
				else {
					throw new TemplatePreprocessorException(String.format("Unhandled annotation found: %s", templateAnnotation.getAnnotationName()));
				}
				
				// Set the ending index of the current section for the next cycle. only if current annotation is not a templateSectionAnnotation
				// Do not set ending index if the current section is a templateSectionAnnotation specified in a config.
				if ((templateAnnotation.isDefinedInTemplate()) || !(templateAnnotation instanceof TemplateSectionAnnotation)) {
					previousSectionEndIndex = templateAnnotation.getAnnotationEndIndex();
				}
			}
		}
		
		// If this is the root section, return the end index.
		if (isRootSection)
			return parentSectionBounds.getAnnotationEndIndex();
		
		// If we reach the code here we haven't found the end of a section for some reason.
		// So we throw an exception.
		throw new TemplatePreprocessorException(String.format("The end of section '%s' can't be found!", parentTemplateSection.getSectionName()));
	}
	
	/**
	 * Find the section end index.
	 * @param sectionAnnotationBounds The TemplateSectionBoundsAnnotation
	 * @param rawTemplateContent The raw template content.
	 * @param sectionEndSearchBeginIndex The index to start searching for the end from.
	 * @return The position of the end if found, otherwise -1.
	 * @throws TemplatePreprocessorException
	 */
	private int findSectionEndIndex(TemplateSectionBoundsAnnotation sectionAnnotationBounds, String rawTemplateContent, int sectionEndSearchBeginIndex, int sectionEndSearchEndIndex) throws TemplatePreprocessorException {
		// The variable to return at the end, if the end is not found this function will return -1.
		int sectionEndCharIndex = -1;
		
		TemplateSectionAnnotation templateSectionAnnotation = sectionAnnotationBounds.getTemplateSectionAnnotation();
		
		// If the end is specified, we use end.
		if (templateSectionAnnotation.getEnd() != null && templateSectionAnnotation.getEnd().length() > 0) {
			// Get the position of the end tag of the section.
			sectionEndCharIndex = rawTemplateContent.indexOf(templateSectionAnnotation.getEnd(), sectionEndSearchBeginIndex);
			
			// Check whether the end was found in the range, if not return -1.
			if (sectionEndCharIndex == -1)
				return sectionEndCharIndex;
			
			// If the section includes the end, we add the length of the end to the index.
			if (templateSectionAnnotation.isIncludeEnd())
				sectionEndCharIndex += templateSectionAnnotation.getEnd().length();
		}
		// literalOnLastLine
		else if (templateSectionAnnotation.getLiteralOnLastLine() != null && templateSectionAnnotation.getLiteralOnLastLine().length() > 0) {
		    Pattern pattern = Pattern.compile(String.format("%s.*\\r?\\n?", Pattern.quote(templateSectionAnnotation.getLiteralOnLastLine())));
		    Matcher matcher = pattern.matcher(rawTemplateContent);
			if (matcher.find(sectionEndSearchBeginIndex) && matcher.end() <= sectionEndSearchEndIndex) {
				return matcher.end();
			} else {
				return -1;
			}
		}
		// If end was not specified, check nrOfLines, this has a default value of 1 if not explicitly set.	
		else  {
			// Get the nrOfLines from the annotation.
			Integer sectionNrOfLines = templateSectionAnnotation.getNrOfLines();
			
			// Loop through the newlines as of the start of the section.
			for (int currentNrOfLines = 0; currentNrOfLines < sectionNrOfLines; currentNrOfLines++) {
				// The end of the section is the first newline we encounter after the begin of the section. We include the newline in the section.
				sectionEndCharIndex = rawTemplateContent.indexOf('\n', (sectionEndCharIndex == -1) ? sectionEndSearchBeginIndex : sectionEndCharIndex + 1);
				
				// If the end of line wasn't found, break out of the loop.
				if (sectionEndCharIndex == -1)
					break;
			}
			
			if (sectionEndCharIndex == -1)
				return -1;
			
			// Add the 1 to the length to compensate for the \n character.
			sectionEndCharIndex += 1;
		}
		// Otherwise, we can't get the end location, leave the return value at the initial -1.
		
		// If the found index is out of range, return -1.
		if (sectionEndCharIndex > sectionEndSearchEndIndex)
			return -1;
		
		// Otherwise return the found index.
		return sectionEndCharIndex;
	}
	
	/**
	 * Add a raw template to the NamedTemplateSection
	 * @param parentTemplateSection The parent NamedTemplateSection
	 * @param rawTemplateContent The raw template content
	 * @param startIndex The starting index of the raw template.
	 * @param endIndex The ending index of the raw template.
	 */
	private void addRawTemplate(NamedTemplateSection parentTemplateSection, String rawTemplateContent, int startIndex, int endIndex) {
		// Escape XML chars, since the raw template will be put in an XSLT transformation.
		String rawTemplateSectionContent = XMLUtils.excapeXMLChars(doubleEntityEncode(rawTemplateContent.substring(startIndex, endIndex)));
		logger.info(String.format("Found a raw template section in section '%s' between index %d and %d: '%s'", parentTemplateSection.getSectionName(), startIndex, endIndex, rawTemplateSectionContent));
		parentTemplateSection.addTemplateSection(new RawTemplateSection(rawTemplateSectionContent, startIndex, endIndex));
	}
	
	/**
	 * Add a raw template to the NamedTemplateSection
	 * @param parentTemplateSection The parent NamedTemplateSection
	 * @param rawTemplateContent The raw template content
	 * @param startIndex The starting index of the raw template.
	 * @param endIndex The ending index of the raw template.
	 */
	private void addRepetitionTemplate(NamedTemplateSection parentTemplateSection, String repetitionContent, int sectionIndex, RepetitionType repetitionType, RepetitionStyle repetitionStyle, RepetitionAction repetitionAction) {
		logger.info(String.format("Found a repetition template section in section '%s' at index %d: %s", parentTemplateSection.getSectionName(), sectionIndex, repetitionType.name()));
		parentTemplateSection.addTemplateSection(new RepetitionTemplateSection(repetitionContent, sectionIndex, repetitionType, repetitionStyle, repetitionAction));
	}
	
	/**
	 * Double entity encode a String.
	 * @param input
	 * @return
	 */
	private static String doubleEntityEncode(String input) {
		return input.replaceAll("&([a-zA-Z0-9]+;)", "&amp;$1");
	}
}
