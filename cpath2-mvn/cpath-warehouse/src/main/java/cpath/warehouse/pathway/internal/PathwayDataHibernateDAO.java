package cpath.warehouse.pathway.internal;

// imports
import cpath.warehouse.beans.PathwayData;
import cpath.warehouse.pathway.PathwayDataDAO;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.HashSet;
import java.util.Collection;

/**
 * Implementation of PathwayDataDAO interface.
 */
@Repository
public final class PathwayDataHibernateDAO implements PathwayDataDAO {

    // log
    private static Log log = LogFactory.getLog(PathwayDataHibernateDAO.class);

    // session factory prop/methods used by spring
    private SessionFactory sessionFactory;
    public SessionFactory getSessionFactory() { return sessionFactory; }
    public void setSessionFactory(SessionFactory sessionFactory) {
	this.sessionFactory = sessionFactory;
    }
	
    // a shortcut to get current session
    private Session getSession() {
	return getSessionFactory().getCurrentSession();
    }
	
    /**
     * (non-Javadoc)
     * @see cpath.warehouse.pathway.PathwayDataDAO#importPathwayData(cpath.warehouse.beans.PathwayData);
     */
    @Transactional(propagation=Propagation.NESTED)
	public void importPathwayData(final PathwayData pathwayData) {

		Session session = getSession();
		// check for existing object
		PathwayData existing = getByIdentifierAndVersionAndDigest(pathwayData.getIdentifier(), pathwayData.getVersion(), pathwayData.getDigest());
		if (existing == null) {
			log.info("PathwayData object with identifier: " + pathwayData.getIdentifier() +
					 "and version: " + pathwayData.getVersion() +
					 "and digest: " + pathwayData.getDigest() +
					 " does not exist, saving.");
			session.save(pathwayData);
		}
		else {
			log.info("PathwayData object with identifier: " + pathwayData.getIdentifier() +
					 "and version: " + pathwayData.getVersion() + 
					 "and digest: " + pathwayData.getDigest() +
					 " already exists, skipping.");
		}
		log.info("metadata object has been sucessessfully saved or merged.");
    }

    /**
     * (non-Javadoc)
     * @see cpath.warehouse.pathway.PathwayDataDAO#getByIdentifier(java.lang.String);
     */
    public Collection<PathwayData> getByIdentifier(final String identifier) {

		Session session = getSession();
		Query query = session.getNamedQuery("cpath.warehouse.beans.pathwayByIdentifier");
		query.setParameter("identifier", identifier);
		List toReturn = query.list();
		return (toReturn.size() > 0) ? new HashSet(toReturn) : new HashSet();
    }

    /**
     * (non-Javadoc)
     * @see cpath.warehouse.pathway.PathwayDataDAO#getByIdentifierAndVersion(java.lang.String, java.lang.Float);
     */
    public Collection<PathwayData> getByIdentifierAndVersion(final String identifier, final Float version) {

		Session session = getSession();
		Query query = session.getNamedQuery("cpath.warehouse.beans.pathwayByIdentifierAndVersion");
		query.setParameter("identifier", identifier);
		query.setParameter("version", version);
		List toReturn = query.list();
		return (toReturn.size() > 0) ? new HashSet(toReturn) : new HashSet();
    }

    /**
     * (non-Javadoc)
     * @see cpath.warehouse.pathway.PathwayDataDAO#getByIdentifierAndVersionAndDigest(java.lang.String, java.lang.Float, java.lang.String);
     */
    public PathwayData getByIdentifierAndVersionAndDigest(final String identifier, final Float version, final String digest) {

		Session session = getSession();
		Query query = session.getNamedQuery("cpath.warehouse.beans.pathwayByIdentifierAndVersionAndDigest");
		query.setParameter("identifier", identifier);
		query.setParameter("version", version);
		query.setParameter("digest", digest);
		return (PathwayData)query.uniqueResult();
    }
}