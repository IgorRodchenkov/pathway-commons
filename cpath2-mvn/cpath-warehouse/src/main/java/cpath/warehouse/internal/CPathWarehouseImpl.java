package cpath.warehouse.internal;

// imports
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.model.level3.ControlledVocabulary;
import org.biopax.paxtools.model.level3.Level3Factory;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.UtilityClass;
import org.biopax.paxtools.proxy.level3.BioPAXFactoryForPersistence;
import org.springframework.stereotype.Service;

import cpath.warehouse.CPathWarehouse;
import cpath.warehouse.CvRepository;
import cpath.warehouse.IdRepository;
import cpath.warehouse.beans.Cv;
import cpath.warehouse.metadata.MetadataDAO;

import java.util.Set;

/**
 * @author rodch
 *
 */
@Service
public final class CPathWarehouseImpl implements CPathWarehouse {
	private final static Log log = LogFactory.getLog(CPathWarehouseImpl.class);
	
	private static Level3Factory level3Factory = new BioPAXFactoryForPersistence();
	
    private MetadataDAO metadataDAO;
    private CvRepository cvRepository;
    private IdRepository idRepository;
	
	
	public CPathWarehouseImpl(MetadataDAO metadataDAO,
			CvRepository cvRepository, IdRepository idRepository) {
		this.metadataDAO = metadataDAO;
		this.cvRepository = cvRepository;
		this.idRepository = idRepository;
	}

	/* (non-Javadoc)
	 * @see cpath.warehouse.CPathWarehouse#createUtilityClass(java.lang.String, java.lang.Class)
	 */
	@Override
	public <T extends UtilityClass> T createUtilityClass(String primaryUrn,
			Class<T> utilityClazz) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see cpath.warehouse.CPathWarehouse#getAllChildrenOfCv(java.lang.String)
	 */
	@Override
	public Set<String> getAllChildrenOfCv(String urn) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see cpath.warehouse.CPathWarehouse#getDirectChildrenOfCv(java.lang.String)
	 */
	@Override
	public Set<String> getDirectChildrenOfCv(String urn) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see cpath.warehouse.CPathWarehouse#getParentsOfCv(java.lang.String)
	 */
	@Override
	public Set<String> getParentsOfCv(String urn) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see cpath.warehouse.CPathWarehouse#getParentsOfCv(java.lang.String)
	 */
	@Override
	public Set<String> getDirectParentsOfCv(String urn) {
		// TODO Auto-generated method stub
		return null;
	}
	
	/* (non-Javadoc)
	 * @see cpath.warehouse.CPathWarehouse#getPrimaryURI(java.lang.String)
	 */
	@Override
	public String getPrimaryURI(String urn) {
		String primary = idRepository.getPrimaryUrn(urn);
		return primary;
	}

	
	/* internal staff */
	
	
	<T extends ControlledVocabulary> T createControlledVocabulary(String urn, Class<T> clazz) {	
		Cv bean = cvRepository.getById(urn);
			
		T cv = level3Factory.reflectivelyCreate(clazz);
		cv.setRDFId(urn);
		// set preferred name
		String preferredName = bean.getNames().iterator().next();
		if(preferredName != null) {
			cv.addTerm(preferredName);
		} else {
			log.error("No CV term name found for : " + urn);
		}
		UnificationXref uref = level3Factory.createUnificationXref();
		uref.setRDFId("xref:"+urn); // TODO how to generate xrefs's ids?
		uref.setDb(bean.getOntologyId());
		uref.setId(bean.getAccession());
		cv.addXref(uref);
		
		return cv;
	}
	
}
