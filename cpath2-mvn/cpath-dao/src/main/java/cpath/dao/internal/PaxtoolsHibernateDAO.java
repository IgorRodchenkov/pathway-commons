// $Id$
//------------------------------------------------------------------------------
/** Copyright (c) 2009 Memorial Sloan-Kettering Cancer Center.
 **
 ** This library is free software; you can redistribute it and/or modify it
 ** under the terms of the GNU Lesser General Public License as published
 ** by the Free Software Foundation; either version 2.1 of the License, or
 ** any later version.
 **
 ** This library is distributed in the hope that it will be useful, but
 ** WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 ** MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 ** documentation provided hereunder is on an "as is" basis, and
 ** Memorial Sloan-Kettering Cancer Center
 ** has no obligations to provide maintenance, support,
 ** updates, enhancements or modifications.  In no event shall
 ** Memorial Sloan-Kettering Cancer Center
 ** be liable to any party for direct, indirect, special,
 ** incidental or consequential damages, including lost profits, arising
 ** out of the use of this software and its documentation, even if
 ** Memorial Sloan-Kettering Cancer Center
 ** has been advised of the possibility of such damage.  See
 ** the GNU Lesser General Public License for more details.
 **
 ** You should have received a copy of the GNU Lesser General Public License
 ** along with this library; if not, write to the Free Software Foundation,
 ** Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 **/
package cpath.dao.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.util.Version;
import static org.biopax.paxtools.impl.BioPAXElementImpl.*;
import org.biopax.paxtools.model.*;
import org.biopax.paxtools.util.IllegalBioPAXArgumentException;
import org.biopax.paxtools.controller.*;
import org.biopax.paxtools.impl.BioPAXElementImpl;
import org.biopax.paxtools.impl.level3.PathwayImpl;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.hibernate.*;
import org.hibernate.search.*;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;

import cpath.config.CPathSettings;
import cpath.dao.Analysis;
import cpath.dao.PaxtoolsDAO;
import cpath.service.jaxb.SearchHit;
import cpath.service.jaxb.SearchResponse;
import cpath.service.jaxb.TraverseEntry;
import cpath.service.jaxb.TraverseResponse;

import java.util.*;
import java.io.*;


/**
 * Paxtools BioPAX main Model and DAO
 * (not a warehouse)
 *
 */
@Transactional
@Repository
public class PaxtoolsHibernateDAO implements PaxtoolsDAO
{
	private static final long serialVersionUID = 1L;
	
	private final static int BATCH_SIZE = 20;
	private final static int DEFAULT_MAX_HITS_PER_PAGE = Integer.MAX_VALUE;
	private final static int IDX_BATCH_SIZE = 200;
	private int maxHitsPerPage = DEFAULT_MAX_HITS_PER_PAGE;

	public final static String[] DEFAULT_SEARCH_FIELDS =
		{
			// auto-generated index fields (from the annotations in paxtools-core)
			FIELD_AVAILABILITY,
			FIELD_COMMENT, // biopax comments
			FIELD_KEYWORD, //anything, e.g., names, terms, comments, incl. - from child elements 
			FIELD_NAME, // standardName, displayName, other names
			FIELD_TERM, // CV terms
			FIELD_XREFDB, //xref.db
			FIELD_XREFID, //xref.id (incl. direct child xref's id, if any)
			FIELD_ECNUMBER,
			FIELD_SEQUENCE,
			// plus, filter fields (TODO experiment with not including these fields into default search fields; use for filtering only)
			FIELD_ORGANISM,
			FIELD_DATASOURCE,
			FIELD_PATHWAY, // i.e., helps find an object by a parent pathway name or filter a search results by pathway ;) 
		};

	private static Log log = LogFactory.getLog(PaxtoolsHibernateDAO.class);
	private SessionFactory sessionFactory;
	private final Map<String, String> nameSpacePrefixMap;
	private final BioPAXLevel level;
	private final BioPAXFactory factory;
	protected SimpleIOHandler simpleIO;
	private boolean addDependencies = false;
	protected MultiFieldQueryParser multiFieldQueryParser;
	private String xmlBase;

	/**
	 *  A supplementary {@link Analysis} (algorithm) to be executed 
	 *  when detaching elements (element(s) as a sub-model) from this model
	 */
	private Analysis getTheseElements;
	
	protected PaxtoolsHibernateDAO()
	{
		this.level = BioPAXLevel.L3;
		this.factory = level.getDefaultFactory();
		// no namespace!
		this.nameSpacePrefixMap = new HashMap<String, String>();
		//this.nameSpacePrefixMap.put("", "urn:biopax:");
		this.simpleIO = new SimpleIOHandler(BioPAXLevel.L3);
		this.simpleIO.mergeDuplicates(true);
		this.simpleIO.normalizeNameSpaces(false);
		this.multiFieldQueryParser = new MultiFieldQueryParser(
			Version.LUCENE_31, DEFAULT_SEARCH_FIELDS, 
				new StandardAnalyzer(Version.LUCENE_31));
		//- seems, - query parser turns search keywords to lower case and expects index field values are also lower case...
		
		// this simply implements how to get a list of elements from the list of URIs
		this.getTheseElements = new Analysis() {
			@Override
			public Set<BioPAXElement> execute(Model model, Object... args) {
				Set<BioPAXElement> bioPAXElements = new HashSet<BioPAXElement>();
				for(Object id : args) {
					BioPAXElement bpe = getByID(id.toString());
					if(bpe != null)
						bioPAXElements.add(bpe);
				}
				return bioPAXElements;
			}
		};
	}
	
	
	// get/set methods used by spring
	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	public BioPAXIOHandler getReader() {
		return simpleIO;
	}


	protected Session session() {
		return sessionFactory.getCurrentSession();
	}
	

	public int getMaxHitsPerPage() {
		return maxHitsPerPage;
	}

	/**
	 * Sets the max. number of hits returned at once (per page).
	 * 
	 * @param maxHits
	 */
	/*
	 * This parameter is currently set via Spring xml beans configuration,
	 * which, in turn, reads it from the capth.property file in the current $CPATH2_HOME dir)
	 */
	public void setMaxHitsPerPage(int maxHits) {
		if(maxHits <= 0)
			this.maxHitsPerPage = DEFAULT_MAX_HITS_PER_PAGE;
		else 
			this.maxHitsPerPage = maxHits;
	}

	//not transactional (but it's 'merge' method that creates a new transaction)
	public void importModel(File biopaxFile) throws FileNotFoundException
	{
		if (log.isInfoEnabled()) {
			log.info("Creating biopax model using: " + biopaxFile.getAbsolutePath());
		}

		// convert file to model
		Model m = simpleIO.convertFromOWL(new FileInputStream(biopaxFile));
		merge(m);
	}

	 
	// non @Transactional here 
	@Override
	public void merge(final Model model)
	{
		/* 
		 * Level3 property/propertyOf ORM mapping and getters/setters 
		 * are to be properly implemented for this to work!
		 * Persistence annotations must move to new setter/getter pair 
		 * that do NOT call inverse prop. 'add' methods in the setter.
		 */
		if(model != null && !model.getObjects().isEmpty()) {
			// First, insert new elements using a stateless session!
			if(log.isDebugEnabled())
				log.debug("merge(model): inserting BioPAX elements (stateless)...");
			StatelessSession stls = getSessionFactory().openStatelessSession();
			int i = 0;
			Transaction tx = stls.beginTransaction();
			for (BioPAXElement bpe : model.getObjects()) {
				if(stls.get(bpe.getClass(), bpe.getRDFId()) == null) {
					stls.insert(bpe);
					i++;
				}
				//else stls.update(bpe); // looks, not required at all (unless we want overwrite)
			}
			tx.commit();
			stls.close();
			
			if (log.isDebugEnabled()) {
				log.debug("merge(model): inserted " + i + " objects");
			}
			
			//run batch update in a separate transaction;
			//this saves object relationships and collections
			update(model);
		}
	}

	
	@Transactional(propagation=Propagation.REQUIRED)
	public void update(final Model model) 
	{	
		Session ses = session();
		
		int i = 0;
		for (BioPAXElement bpe : model.getObjects()) {			
			// save/update
			merge(bpe);
			
			i++;
			if(i % BATCH_SIZE == 0) {
				ses.flush();
				ses.clear();
				if (log.isDebugEnabled()) {
					log.debug("update(model): saved " + i);
				}
			}
		}
		
		if (log.isDebugEnabled()) {
			log.debug("update(model): total merged " + i + " objects");
		}
	}
	
	
	/**
	 * Saves or merges the element 
	 * (use when it's updated or unsure if saved ever before...)
	 * 
	 */
	@Transactional(propagation = Propagation.REQUIRED)
	public void merge(BioPAXElement aBioPAXElement) {
		String rdfId = aBioPAXElement.getRDFId();
		if (rdfId == null) {
			throw new IllegalBioPAXArgumentException(
					"null ID: every object must have an RDF ID");
		} else {
			if (log.isDebugEnabled())
				log.debug("merge(aBioPAXElement): merging " + rdfId + " " 
					+ aBioPAXElement.getModelInterface().getSimpleName());
			
			session().merge(aBioPAXElement);
		}
	}

	
	@Override
	@Transactional(propagation = Propagation.REQUIRED, readOnly = true)
	public SearchResponse search(String query, int page,
			Class<? extends BioPAXElement> filterByType, String[] dsources,
			String[] organisms) 
	{
		// collect matching elements here
		SearchResponse searchResponse = new SearchResponse();

		if (log.isInfoEnabled())
			log.info("search: " + query + ", filterBy: " + filterByType
					+ "; extra filters: ds in (" + Arrays.toString(dsources)
					+ "), org. in (" + Arrays.toString(organisms) + ")");

		org.apache.lucene.search.Query luceneQuery = null;

		try {
			luceneQuery = multiFieldQueryParser.parse(query);
		} catch (ParseException e) {
			log.info("parser exception: " + e.getMessage());
			return searchResponse;
		}

		// get a full text session
		FullTextSession fullTextSession = Search.getFullTextSession(session());
		FullTextQuery fullTextQuery;

		if (filterByType != null) {
			// full-text query cannot filter by interfaces...
			// so let's translate them to the annotated entity classes
			Class<?> filterClass = getEntityClass(filterByType);
			fullTextQuery = fullTextSession.createFullTextQuery(luceneQuery, filterClass);
		} else {
			fullTextQuery = fullTextSession.createFullTextQuery(luceneQuery);
		}

		// set read-only
		fullTextQuery.setReadOnly(true);

		// use Projection (allows extracting/highlighting matching text, etc.)
		if (CPathSettings.isDebug())
			fullTextQuery.setProjection(FullTextQuery.THIS, FullTextQuery.DOCUMENT,
					FullTextQuery.SCORE, FullTextQuery.EXPLANATION);
		else
			fullTextQuery.setProjection(FullTextQuery.THIS, FullTextQuery.DOCUMENT);

		// enable filters
		if (dsources != null && dsources.length > 0)
			fullTextQuery.enableFullTextFilter(FILTER_BY_DATASOURCE)
					.setParameter("values", dsources);
		if (organisms != null && organisms.length > 0)
			fullTextQuery.enableFullTextFilter(FILTER_BY_ORGANISM)
					.setParameter("values", organisms);

		// set pagination
		int l = page * maxHitsPerPage; // - the first hit no., if any
		fullTextQuery.setMaxResults(maxHitsPerPage);
		fullTextQuery.setFirstResult(l);

		/*
		 * use a highlighter (to get the excerpt);
		 * (also use store.YES in some annotations, esp. on 'keyword' field)
		 */
		//TODO shall I rewrite the luceneQuery?
		QueryScorer scorer = new QueryScorer(luceneQuery, FIELD_KEYWORD);   
	    SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<span class='hitHL'>", "</span>");
	    Highlighter highlighter = new Highlighter(formatter, scorer);
	    highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, 80));

	    // set the result to search hit beans transformer
	    fullTextQuery.setResultTransformer(new SearchHitsTransformer(highlighter));

		searchResponse.setMaxHitsPerPage(maxHitsPerPage);

		int count = fullTextQuery.getResultSize(); // cheap operation
		if (log.isInfoEnabled())
			log.info("Query '" + query + "', results size = " + count);
		searchResponse.setNumHits(count);

		// do search and get (auto-transformed) hits
		List<SearchHit> searchHits = (List<SearchHit>) fullTextQuery.list();

		searchResponse.setSearchHit(searchHits);

		return searchResponse;
	}
	
	
	@Override
	@Transactional(propagation=Propagation.REQUIRED)
	public void add(BioPAXElement aBioPAXElement)
	{
		String rdfId = aBioPAXElement.getRDFId();
		
		if (log.isDebugEnabled())
			log.debug("about to add: " + rdfId);
		
		if (!level.hasElement(aBioPAXElement))
		{
			throw new IllegalBioPAXArgumentException(
					"Given object is of wrong level");
		}
		else if (rdfId == null)
		{
			throw new IllegalBioPAXArgumentException(
					"null ID: every object must have an RDF ID");
		}
		else
		{
			if (log.isDebugEnabled())
				log.debug("adding " + rdfId);
			session().persist(aBioPAXElement); 
			// - many elements are affected, because of cascades...
		}
	}


	/* (non-Javadoc)
	 * @see org.biopax.paxtools.model.Model#remove(org.biopax.paxtools.model.BioPAXElement)
	 */
	@Override
	@Transactional(propagation=Propagation.REQUIRED)
	public void remove(BioPAXElement aBioPAXElement)
	{
		BioPAXElement bpe = getByID(aBioPAXElement.getRDFId());
		session().delete(bpe); // affects many elements, because of cascade=ALL!
	}


	@Override
	public <T extends BioPAXElement> T addNew(Class<T> type, String id)
	{
		T bpe = factory.create(type, id);
		add(bpe); // many elements, because of cascade=ALL
		return bpe;
	}


	@Override
	@Transactional(readOnly=true)
	public boolean contains(BioPAXElement bpe)
	{
		return getByID(bpe.getRDFId()) != null;
	}


	@Override
	@Transactional(readOnly=true)
	public boolean containsID(String id)
	{
		return getByID(id) != null;
	}


	@Override
	@Transactional(propagation=Propagation.REQUIRED)
	public BioPAXElement getByID(String id) 
	{
		if(id == null || "".equals(id)) 
			throw new RuntimeException("getByID(null) is called!");

		BioPAXElement toReturn = null;
		// rdfid is the Primary Key see BioPAXElementImpl)
		toReturn = (BioPAXElement) session().get(BioPAXElementImpl.class, id);

		return toReturn; // null means no such element
	}
	
	
	@Override
	public BioPAXLevel getLevel()
	{
		return level;
	}


	@Override
	public Map<String, String> getNameSpacePrefixMap()
	{
		return Collections.unmodifiableMap(nameSpacePrefixMap);
	}


	@Override
	public Set<BioPAXElement> getObjects()
	{
		return getObjects(BioPAXElement.class);
	}


	@Override
	@Transactional(propagation=Propagation.REQUIRED)
	public <T extends BioPAXElement> Set<T> getObjects(Class<T> clazz)
	{
		String query = "from " + clazz.getCanonicalName();
		List<T> results = session().createQuery(query).list();
		Set<T> toReturn = new HashSet<T>(results);
		return toReturn;
	}


	@Override
	public boolean isAddDependencies() {
		return addDependencies;
	}


	@Override
	public void setAddDependencies(boolean addDependencies)
	{
		this.addDependencies = addDependencies;
	}


	@Override
	public void setFactory(BioPAXFactory factory)
	{
		throw new UnsupportedOperationException(
			"Internal BioPAX Factory cannot be modified.");//would be unsafe!
	}

	
	/**
	 * Gets a Hibernate annotated entity class 
	 * (implementation) by BioPAX Model interface class 
	 * 
	 */
	protected Class<? extends BioPAXElement> getEntityClass(
			Class<? extends BioPAXElement> filterBy) 
	{
		Class<? extends BioPAXElement> clazz = (BioPAXElement.class.equals(filterBy))
			? BioPAXElementImpl.class : factory.getImplClass(filterBy);
		
		if(clazz == null) {
			clazz = BioPAXElementImpl.class; // fall-back (to no filtering)
			log.error("Expected argument: a BioPAX interface"
					+ "; actual argument: " + filterBy.getCanonicalName());
		}

		return clazz;
	}

	/* (non-Javadoc)
	 * @see cpath.dao.PaxtoolsDAO#exportModel(java.io.OutputStream)
	 */
	/**
	 * When an non-empty list of IDs (RDFId, URI) is provided,
	 * it will use {@link SimpleIOHandler#convertToOWL(Model, OutputStream, String...)} 
	 * which in turn uses {@link Fetcher#fetch(BioPAXElement, Model)}
	 * (rather than {@link #getValidSubModel(Collection)}) method
	 * to recursively extract each listed element (with all children and properties)
	 * and put into a new sub-model, which is then serialized and 
	 * written to the output stream. Note: using the Fetcher, there is a risk 
	 * (depending on the data stored) of pulling almost entire network 
	 * by providing one or a few IDs... Also implemented here is 
	 * that the Fetcher/traverser does not follow BioPAX
	 * property 'nextStep', which otherwise could lead to infinite loops.
	 */
	@Override
	@Transactional(propagation=Propagation.REQUIRES_NEW)
	public void exportModel(OutputStream outputStream, String... ids) 
	{
		simpleIO.convertToOWL(this, outputStream, ids);
	}
	

	/** 
	 * 
	 * Creates a new detached BioPAX sub-model from the list of URIs
	 * using Paxtools's {@link Completer} and {@link Cloner} approach
	 * (thus ignoring those not in the same list); runs within a transaction.
	 * 
	 */
	@Override
	public Model getValidSubModel(Collection<String> ids) {
		// run the analysis (in a new transaction)
		return runAnalysis(this.getTheseElements, ids.toArray());
	}
	
	
	/* 
	 * All properties and inverse properties 
	 * (not very deep) initialization
	 * 
	 */
	@Transactional(propagation=Propagation.REQUIRED, readOnly=true)
	@Override
	public void initialize(Object obj) 
	{
		if(obj instanceof BioPAXElement) {		
			//just reassociate:
			session().buildLockRequest(LockOptions.NONE).lock(obj);
			Hibernate.initialize(obj);
	
			BioPAXElement element = (BioPAXElement) obj;

			// init. biopax properties
			Set<PropertyEditor> editors = simpleIO.getEditorMap()
				.getEditorsOf(element);
			if (editors != null) {
				for (PropertyEditor editor : editors) {
					Set<?> value = editor.getValueFromBean(element);
					Hibernate.initialize(value); //yup, it inits collections as well ;)
					for(Object v : value) {
						Hibernate.initialize(v); 
					}
				}
			}

			// init. inverse object properties (xxxOf)
			Set<ObjectPropertyEditor> invEditors = simpleIO.getEditorMap()
				.getInverseEditorsOf(element);
			if (invEditors != null) {
				for (ObjectPropertyEditor editor : invEditors) {
					// does collections as well!
					Set<?> value = editor.getInverseAccessor().getValueFromBean(element);
					Hibernate.initialize(value);
					//values the set can be BioPAX elements only
					for(Object v : value) {
						Hibernate.initialize(v); 
					}
				}
			}
		}
	}

	
	/* (non-Javadoc)
	 * @see cpath.dao.PaxtoolsDAO#runAnalysis(cpath.dao.Analysis, java.lang.Object[])
	 */
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Model runAnalysis(Analysis analysis, Object... args) {
		// perform
		Set<BioPAXElement> result = analysis.execute(this, args);
		
		if(log.isDebugEnabled())
			log.debug("runAnalysis: finished; now detaching the sub-moodel...");
		
		// auto-complete/detach
		if(result != null) {
			if(log.isDebugEnabled())
				log.debug("runAnalysis: running auto-complete...");
			Completer c = new Completer(simpleIO.getEditorMap());
			result = c.complete(result, null); //null - because the (would be) model is never used there anyway
			if(log.isDebugEnabled())
				log.debug("runAnalysis: cloning...");
			Cloner cln = new Cloner(simpleIO.getEditorMap(), factory);
			Model submodel = cln.clone(null, result);
			
			if(log.isDebugEnabled())
				log.debug("runAnalysis: returned");
			return submodel; // new (sub-)model
		} 
		
		if(log.isDebugEnabled())
			log.debug("runAnalysis: returned NULL");
		return null;
	}

	
	@Override
	public void replace(BioPAXElement existing, BioPAXElement replacement) {
		throw new UnsupportedOperationException("not supported");
	}

	
	@Override
	public void repair() {
		throw new UnsupportedOperationException("not supported");
	}
	
	
	@Override
	public void setXmlBase(String base) {
		this.xmlBase = base;
	}

	
	@Override
	public String getXmlBase() {
		return xmlBase;
	}

	
	/**
	 * "Top pathway" can mean different things...
	 * 
	 * 1) One may want simply collect pathways which are not 
	 * values of any BioPAX property (i.e., a "graph-theoretic" approach, 
	 * used by {@link ModelUtils#getRootElements(Class)} method) and by 
	 * BioPAX normalizer, which (in the cPath2 "premerge" stage),
	 * for all Entities, generates relationship xrefs to their "parent" pathways.
	 * 
	 * 2) Another approach would be to check whether specific (inverse) 
	 * properties, such as controlledOf, pathwayComponentOf and stepProcessOf, are empty.
	 * 
	 * Here we follow the second method!
	 * 
	 */
	@Override
	@Transactional(readOnly = true)
	public SearchResponse getTopPathways() {
		SearchResponse searchResponse = new SearchResponse();
		//TODO find all pathways where 'pathway' index field is empty (i.e., no controlledOf and pathwayComponentOf values)
		
		FullTextSession fullTextSession = Search.getFullTextSession(session());
		QueryBuilder queryBuilder = fullTextSession.getSearchFactory().buildQueryBuilder().forEntity(PathwayImpl.class).get();
		org.apache.lucene.search.Query luceneQuery = queryBuilder.all().createQuery();
		
		FullTextQuery fullTextQuery = fullTextSession.createFullTextQuery(luceneQuery, PathwayImpl.class);
		fullTextQuery.setProjection(FullTextQuery.THIS, FullTextQuery.DOCUMENT);	
		fullTextQuery.setReadOnly(true);
		fullTextQuery.setResultTransformer(new SearchHitsTransformer(null)); //highlighter is not required here
		
		// do search and get (auto-transformed from BioPAX elements and index fields) hits
		List<SearchHit> searchHits = searchResponse.getSearchHit();
		//remove pathways having pathway field not empty
		for(SearchHit h : (List<SearchHit>) fullTextQuery.list()) {
			if(h.getPathway().isEmpty())
				searchHits.add(h);
		}
		
		searchResponse.setNumHits(searchHits.size());
		//TODO update the following comment if implementation has changed!
		searchResponse.setComment("Top Pathways (technically, each has empty index " +
				"field 'pathway'; that also means, they are neither components of " +
				"other pathways nor controlled of any process)");
		searchResponse.setMaxHitsPerPage(searchHits.size());
		searchResponse.setPageNo(0);
		
		return searchResponse;
	}


	/**
	 * It generates results only for those URIs where
	 * the property path apply, although the values set 
	 * can be empty.
	 * TODO may be to return a row for each query URI regardless path apply or not; e.g., use 'valid' attr...
	 */
	@Transactional(readOnly = true)
	@Override
	public TraverseResponse traverse(String propertyPath, String... uris) {
		TraverseResponse resp = new TraverseResponse();
		resp.setPropertyPath(propertyPath);
		PathAccessor pathAccessor = new PathAccessor(propertyPath, getLevel());
		for(String uri : uris) {
			BioPAXElement bpe = getByID(uri);
			try {
				Set<?> v = pathAccessor.getValueFromBean(bpe);
				TraverseEntry entry = new TraverseEntry();
				entry.setUri(uri);
				if(!pathAccessor.isUnknown(v)) {
//					entry.getValue().addAll(v);
					for(Object o : v) {
						if(o instanceof BioPAXElement) 
							entry.getValue().add(((BioPAXElement) o).getRDFId());
						else
							entry.getValue().add(String.valueOf(o));
					}
				}
				// add (it might have no values, but the path is correct)
				resp.getTraverseEntry().add(entry); 
			} catch (IllegalBioPAXArgumentException e) {
				// log, ignore if the path does not apply
				if(log.isDebugEnabled())
					log.debug("Failed to get values at: " + 
						propertyPath + " from the element: " + uri, e);
			}
		}
		
		return resp;
	}
	
	
	@Transactional
	public void index() {
		FullTextSession fullTextSession = Search.getFullTextSession(session());
		// Manual indexing
		fullTextSession.setFlushMode(FlushMode.MANUAL);
		fullTextSession.setCacheMode(CacheMode.IGNORE);
		//Scrollable results will avoid loading too many objects in memory
		ScrollableResults results = fullTextSession.createCriteria( BioPAXElementImpl.class )
		    .setFetchSize(IDX_BATCH_SIZE)
		    .scroll( ScrollMode.FORWARD_ONLY );
		int index = 0;
		while( results.next() ) {
		    index++;
		    BioPAXElement bpe = (BioPAXElement) results.get(0);
		    fullTextSession.index( bpe ); //index each element
		    if (index % IDX_BATCH_SIZE == 0) {
		        fullTextSession.flushToIndexes(); //apply changes to indexes
		        fullTextSession.clear(); //free memory since the queue is processed
		        if(log.isInfoEnabled())
					log.info("Indexed " + index);
		    }
		}
        fullTextSession.flushToIndexes();
        fullTextSession.clear();

		/* Much (> 10x) faster indexing (mass-parallel),
		 * which unfortunately is too "fragile" due to various reasons 
		 * (e.g., framework bugs...) and less flexible.
		 * 
		 * Warning: weird, however, having slightly higher (still reasonable) 
		 * numbers below can easily lead to indexer hangs or fails with 
		 * "too many db connections" log messages or even StackOverFlow...
		 */
//		try {
//			fullTextSession.createIndexer()
//				.purgeAllOnStart(true)
//				.batchSizeToLoadObjects(4) // up to 10 once worked for me [Igor]
//				.threadsForSubsequentFetching(1) // >1 caused issues [Igor]
//				.threadsToLoadObjects(1) // >1 caused issues [Igor]
////				.optimizeOnFinish(true)
//				.startAndWait();
//		} catch (Exception e) {
//			throw new RuntimeException("Index re-build is interrupted.", e);
//		}
		
		if (log.isInfoEnabled())
			log.info("Done indexing.");
	}
}
