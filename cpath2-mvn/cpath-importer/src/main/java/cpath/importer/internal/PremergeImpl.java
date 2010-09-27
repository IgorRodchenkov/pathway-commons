/**
 ** Copyright (c) 2009 Memorial Sloan-Kettering Cancer Center (MSKCC)
 ** and University of Toronto (UofT).
 **
 ** This is free software; you can redistribute it and/or modify it
 ** under the terms of the GNU Lesser General Public License as published
 ** by the Free Software Foundation; either version 2.1 of the License, or
 ** any later version.
 **
 ** This library is distributed in the hope that it will be useful, but
 ** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 ** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 ** documentation provided hereunder is on an "as is" basis, and
 ** both UofT and MSKCC have no obligations to provide maintenance, 
 ** support, updates, enhancements or modifications.  In no event shall
 ** UofT or MSKCC be liable to any party for direct, indirect, special,
 ** incidental or consequential damages, including lost profits, arising
 ** out of the use of this software and its documentation, even if
 ** UofT or MSKCC have been advised of the possibility of such damage.  
 ** See the GNU Lesser General Public License for more details.
 **
 ** You should have received a copy of the GNU Lesser General Public License
 ** along with this software; if not, write to the Free Software Foundation,
 ** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA;
 ** or find it at http://www.fsf.org/ or http://www.gnu.org.
 **/
package cpath.importer.internal;

// imports
import cpath.cleaner.Cleaner;
import cpath.config.CPathSettings;
import cpath.dao.DataServices;
import cpath.dao.PaxtoolsDAO;
import cpath.dao.internal.DataServicesFactoryBean;
import cpath.importer.Premerge;
import cpath.warehouse.PathwayDataDAO;
import cpath.warehouse.beans.Metadata;
import cpath.warehouse.beans.PathwayData;

import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.io.simpleIO.SimpleReader;
import org.biopax.paxtools.io.simpleIO.SimpleExporter;
import org.biopax.validator.Validator;
import org.biopax.validator.result.Validation;
import org.biopax.validator.utils.BiopaxValidatorUtils;
import org.biopax.paxtools.converter.OneTwoThree;

import org.mskcc.psibiopax.converter.PSIMIBioPAXConverter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.Collection;

import java.io.*;

import javax.sql.DataSource;

/**
 * Class responsible for premerging pathway data.
 */
public class PremergeImpl extends Thread implements Premerge {

	// log
    private static Log log = LogFactory.getLog(PremergeImpl.class);

	// ref to PathwayDataDAO
    private PathwayDataDAO pathwayDataDAO;

    @Autowired
	private ApplicationContext applicationContext;

	// ref to validator
	private Validator validator;
	
	// ref to dispatcher
	private PremergeDispatcher premergeDispatcher;

	// ref to metadata
	private Metadata metadata;
	
	// ref to cleaner
	private Cleaner cleaner;

	/**
	 *
	 * Constructor.
	 *
	 * @param metadata Metadata
	 * @param pathwayDataDAO PathwayDataDAO
	 * @param validator Biopax Validator
	 */
	public PremergeImpl(final PathwayDataDAO pathwayDataDAO,
						final Validator validator) 
	{
		this.pathwayDataDAO = pathwayDataDAO;
		this.validator = validator;
	}

    /**
	 * (non-Javadoc)
	 * @see cpath.importer.Premerge#setDispatcher(cpath.importer.internal.PremergeDispatcher)
	 */
	@Override
	public void setDispatcher(final PremergeDispatcher premergeDispatcher) {
		this.premergeDispatcher = premergeDispatcher;
	}

	/**
	 * (non-Javadoc)
	 * @see cpath.importer.Premerge#setMetadata(cpath.warehouse.beans.Metadata)
	 */
	@Override
	public void setMetadata(final Metadata metadata) {
		this.metadata = metadata;
	}

    /**
	 * (non-Javadoc)
	 * @see cpath.importer.Premerge#premerge
	 */
	@Override
	public void premerge() {
		this.start();
	}

	/**
	 * (non-Javadoc)
	 * @see java.lang.Thread#run()
	 */
	@Override
	public void run() {

		log.info("run(), starting...");

		// sanity check
		if (metadata == null) {
			log.info("run(), metadata object is null.");
			return;
		}

		// get pathway data
		log.info("run(), getting pathway data for provider.");
		Collection<PathwayData> pathwayDataCollection =
			pathwayDataDAO.getByIdentifierAndVersion(metadata.getIdentifier(), metadata.getVersion());

		// create cleaner
		log.info("run(), getting a cleaner with name: " + metadata.getCleanerClassname());
		cleaner = getCleaner(metadata.getCleanerClassname());
		if (cleaner == null) {
			// TDB: report failure
			log.info("run(), could not create cleaner class " + metadata.getCleanerClassname());
			return;
		}
		
		// iterate over all pathway data
		log.info("run(), interating over pathway data.");
		for (PathwayData pathwayData : pathwayDataCollection) {
			pipeline(pathwayData);
		}

		// outta here
		premergeDispatcher.premergeComplete(metadata);
		log.info("run(), exiting...");
	}

	/**
	 * Pushes given PathwayData through pipeline.
	 *
	 * @param pathwayData PathwayData
	 */
	private void pipeline(final PathwayData pathwayData) {
		String pathwayDataStr = null;
		String pathwayDataDescription = (pathwayData.getIdentifier() + ", " +
										 pathwayData.getVersion() + ", " +
										 pathwayData.getFilename() + ".");

		// clean
		if(log.isInfoEnabled())
			log.info("pipeline(), cleaning pathway data.");
		if (metadata.getType() == Metadata.TYPE.BIOPAX_L2) {
			pathwayDataStr = pathwayData.getPathwayData();
			// convert to biopax l3, then clean
			pathwayDataStr = convertBioPAXL2ToLevel3(pathwayDataStr);
			pathwayDataStr = cleaner.clean(pathwayDataStr);
		}
		else {
			pathwayDataStr = cleaner.clean(pathwayData.getPathwayData());
			// if psi-mi, convert to biopax
			if (metadata.getType() == Metadata.TYPE.PSI_MI) {
				if(log.isInfoEnabled())
					log.info("pipeline(), converting psi-mi data.");
				pathwayDataStr = convertPSIToBioPAX(pathwayDataStr);
			}
		}

		// error during conversion
		if (pathwayDataStr == null || pathwayDataStr.length() == 0) {
			// TBD: report failure
			log.error("pipeline(), error converting to biopax: "
					+ pathwayDataDescription);
			return;
		}

		// normalize
		if(log.isInfoEnabled())
			log.info("pipeline(), normalizing pathway data.");
		
		try {
			pathwayDataStr = (new NormalizerImpl()).normalize(pathwayDataStr);
		} catch (RuntimeException e) {
			// TBD: report failure
			log.error("pipeline(), error normalizing pathway data: " 
				+ pathwayDataDescription, e);
			return;
		}

		// validate
		if(log.isInfoEnabled())
			log.info("pipeline(), validating pathway data.");
		pathwayData.setPremergeData(pathwayDataStr);
		boolean valid = validatePathway(pathwayData);
		// save with validation results
		pathwayDataDAO.importPathwayData(pathwayData);
		// shall we proceed?
		if(!valid) {			
			log.error("pipeline(), biopax errors found in pathway data: "
				+ pathwayDataDescription);
			return;
		}
		
		// create a model from the normalized and validated pathway data
		if(log.isInfoEnabled())
			log.info("run(), creating paxtools model from pathway data.");
		Model model = (new SimpleReader()).convertFromOWL(
				new ByteArrayInputStream(pathwayDataStr.getBytes()));
		
		// persist
		if(log.isInfoEnabled())
			log.info("pipeline(), persisting pathway data.");
		if (!persistPathway(pathwayData, model)) {
			// TBD: report failure
			log.error("pipeline(), error persisting pathway data: "
				+ pathwayDataDescription);
			return;
		}
	}

	/**
	 * For the given cleaner class name,
	 * returns an instance of a class which
	 * implements the cleaner interface.
	 *
	 * @param cleanerClassName String
	 * @return Cleaner
	 */
	private Cleaner getCleaner(final String cleanerClassName) {
		try {
			Class cleanerClass = getClass().forName(cleanerClassName);
			return (cleanerClass == null) ?
				null : (Cleaner)cleanerClass.newInstance();
		}
		catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * Converts psi-mi string to biopax
	 *
	 * @param psimiData String
	 */
	private String convertPSIToBioPAX(final String psimiData) {

		String toReturn = "";
				
		try {

			ByteArrayOutputStream os = new ByteArrayOutputStream();
			InputStream is = new ByteArrayInputStream(psimiData.getBytes());
			PSIMIBioPAXConverter psimiConverter = new PSIMIBioPAXConverter(BioPAXLevel.L3);
			psimiConverter.convert(is, os);

			// wait for conversion to finish
			while(true) {
				sleep(100);
				if (psimiConverter.conversionIsComplete()) {
					break;
				}
			}

			// made it here, conversion is complete
			toReturn = os.toString();
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		// outta here
		return toReturn;
	}

	/**
	 * Converts biopax l2 string to biopax l3
	 *
	 * @param bpl2Data String
	 */
	private String convertBioPAXL2ToLevel3(final String bpl2Data) {

		String toReturn = "";
				
		try {
			ByteArrayOutputStream os = new ByteArrayOutputStream();
			InputStream is = new ByteArrayInputStream(bpl2Data.getBytes());

			SimpleReader reader = new SimpleReader();
			Model model = reader.convertFromOWL(is);
			model = (new OneTwoThree()).filter(model);
			if (model != null) {
				SimpleExporter exporter = new SimpleExporter(model.getLevel());
				exporter.convertToOWL(model, os);
				toReturn = os.toString();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}

		// outta here
		return toReturn;
	}

	/**
	 * Validates the given pathway data.
	 *
	 * @param pathwayData
	 * @param pathwayDataStr OWL content; may be different from original one (e.g., normalized)
	 * @return boolean
	 */
	private boolean validatePathway(final PathwayData pathwayData) {

		boolean toReturn = true;
		
		// get result and marshall to xml string to store
		StringWriter writer = new StringWriter();
		// the following is 
		// create a new empty validation and associate with the model data
		Validation validation = new Validation(pathwayData.getIdentifier());
		// because errors are reported during the import (e.g., syntax)
		validator.importModel(validation, 
			new ByteArrayInputStream(pathwayData.getPremergeData().getBytes()));
		// now post-validate
		validator.validate(validation);
		// serialize
		BiopaxValidatorUtils.write(validation, writer, null);
		pathwayData.setValidationResults(writer.toString());

		// no errors?
		toReturn = (validation.countErrors(null, null, null, true) == 0);
		
		if(log.isInfoEnabled()) {
			log.info("Summary for " + pathwayData.getIdentifier() 
				+ ". Critical errors found:" + toReturn + ". " 
				+ validation.getComment().toString() + "; " 
				+ validation.toString());
		}
		
		
		// outta here 
		return toReturn;
	}

	/**
	 * Persists the given PathwayData object with clean data
	 *
	 * @param pathwayData PathwayData
	 * @param model Model
	 * @return boolean
	 */
	@Transactional
	private boolean persistPathway(final PathwayData pathwayData, final Model model) {
		// create a new database schema for this data provider
		String premergeDbName = CPathSettings.CPATH_DB_PREFIX + metadata.getIdentifier();
		DataServicesFactoryBean.createSchema(premergeDbName);
		
		// get the data source factory bean (that is aware of the driver, user, and password)
		// applicationContext is injected (auto-wired) parent context
		DataServices dataServices = (DataServices) applicationContext.getBean("&cpath2_meta");
		// get the DataSource (for the database just created)
		DataSource premergeDataSource = dataServices.getDataSource(premergeDbName);
		// get the PaxtoolsDAO instance
		PaxtoolsDAO paxtoolsDAO = buildPremergeDAO(premergeDbName, premergeDataSource);
		paxtoolsDAO.merge(model);

		// outta here
		return true;
	}
	
	/**
	 * Creates new PaxtoolsDAO instance to work with existing "premerge"
	 * database. This is used both during the "pre-merge" (here) and "merge".
	 */
	public static PaxtoolsDAO buildPremergeDAO(String premergeDbName, DataSource premergeDataSource) {
		DataServicesFactoryBean.getDataSourceMap().put(CPathSettings.PREMERGE_DB_KEY, premergeDataSource);
		/* 
		 * set system properties and data source 
		 * (replaces existing one in the same thread),
		 * load a special application context
		 */
		String home = System.getenv(CPathSettings.HOME_VARIABLE_NAME);
		if (home==null) throw new RuntimeException(
				"Please set " + CPathSettings.HOME_VARIABLE_NAME + " environment variable " +
            	" (point to a directory where cpath.properties, etc. files are placed)");
		String indexDir = home + File.separator + premergeDbName;
		// set the system variable that is used by the following spring context
		System.setProperty(CPathSettings.PREMERGE_INDEX_DIR_VARIABLE, indexDir);
		ApplicationContext context = 
			new ClassPathXmlApplicationContext("classpath:internalContext-premerge.xml");
				
		// get a ref to the PaxtoolsDAO bean
		return (PaxtoolsDAO)context.getBean("premergePaxtoolsDAO");
	}
}
