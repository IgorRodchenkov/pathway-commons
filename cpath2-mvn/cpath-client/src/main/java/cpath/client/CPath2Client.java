package cpath.client;

import cpath.client.util.BioPAXHttpMessageConverter;
import cpath.client.util.CPathException;
import cpath.client.util.CPathExceptions;
import cpath.client.util.ServiceResponseHttpMessageConverter;
import cpath.service.Cmd;
import cpath.service.CmdArgs;
import cpath.service.GraphType;
import cpath.service.OutputFormat;
import cpath.service.jaxb.*;

import org.apache.commons.lang.StringUtils;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.SimpleIOHandler;
import org.biopax.paxtools.model.Model;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * CPath2 Client (read as: CPath Squared). 
 * 
 * Development CPath2 WEB API demo is http://awabi.cbio.mskcc.org/pc2-demo/, 
 * and the released one was http://www.pathwaycommons.org/pc2-demo/
 * 
 * For "/get" and "/graph" queries, this client 
 * returns data in BioPAX L3 format only (or - error).
 * But BioPAX can be converted to other formats on the client side
 * (e.g., using converters provided by Paxtools)
 * 
 * TODO add support for other output formats that '/get','/graph' can return (at least - for BINARY_SIF)
 */
public final class CPath2Client
{
	public static final String JVM_PROPERTY_ENDPOINT_URL = "cPath2Url";
	public static final String DEFAULT_ENDPOINT_URL = "http://pathwaycommons.org/pc2/";

	
	/**
	 * This is an <em>equivalent</em> to {@link Direction}
	 * enumeration, and is defined here for convenience (i.e.,
	 * to free all users of this web service client from 
	 * depending on paxtools-query module at runtime.)
	 * 
	 */
	//TODO move it to the cpath-api (cause, e.g., OutputFormat is already there)?
	public static enum Direction
    {
		UPSTREAM, DOWNSTREAM, BOTHSTREAM;
    }
	
	
	private final RestTemplate restTemplate;
	
	private String endPointURL;
	private Integer page = 0;
    private Integer graphQueryLimit = 1;
    private Collection<String> organisms = new HashSet<String>();
    private Collection<String> dataSources = new HashSet<String>();
    private String type = null;
    private String path = null;
    private Direction direction = Direction.DOWNSTREAM;
    
    // suppress using constructors in favor of static factories
    private CPath2Client() {
     	// create a new REST template
     	restTemplate = new RestTemplate(); //custom message converters will be added there
    }
    
    
    /**
     * Default static factory, initializes the class using
     * {@link org.biopax.paxtools.io.SimpleIOHandler}
     */
    public static CPath2Client newInstance() {
    	return newInstance(new SimpleIOHandler());
    }

    
    /**
     * Static factory.
     * @param bioPAXIOHandler BioPAXIOHandler for reading BioPAX Models
     */
    public static CPath2Client newInstance(BioPAXIOHandler bioPAXIOHandler) {
    	CPath2Client client = new CPath2Client(); 
    	
    	// Remove default message converters
//    	client.restTemplate.getMessageConverters().clear();
    	
     	// add custom cPath2 XML message converter as the first one (accepts 'application/xml' content type)
    	// because one of existing/default msg converters, XML root element based jaxb2, does not work for ServiceResponce types...
    	client.restTemplate.getMessageConverters().add(0, new ServiceResponseHttpMessageConverter());
    	// add BioPAX http message converter
        client.restTemplate.getMessageConverters().add(1, new BioPAXHttpMessageConverter(bioPAXIOHandler));
    	
    	// init the server URL
    	client.endPointURL = System.getProperty(JVM_PROPERTY_ENDPOINT_URL, DEFAULT_ENDPOINT_URL);
    	assert client.endPointURL != null :  "BUG: cpath2 URL is not defined!";
    	
    	return client;
    }

    
	/**
	 * Executes a 'get' and 'graph' type cPath2 
	 * Web Service API URL query and returns 
	 * the resulting sub-model (sub-network) as String.
	 * 
	 * @see #queryGet(Collection)
	 * @see #queryNeighborhood(Collection)
	 * @see #queryCommonStream(Collection)
	 * @see #queryPathsFromTo(Collection, Collection)
	 * @see #queryPathsBetween(Collection)
	 * 
	 * 
	 * @param url
	 * @param outputFormat default is {@link OutputFormat#BIOPAX}
	 * @return
	 */
	public String executeQuery(final String url, final OutputFormat outputFormat) {
		final String q = (outputFormat == null)
			? url : url + "&" + CmdArgs.format + "=" + outputFormat;
		
		return restTemplate.getForObject(q, String.class);
	}
    
    
    /**
     * Full text search.
     * @see #search(Collection)
     *
     * @param keyword keywords (e.g., ); can also be a Lucene query
     * @return 
     * @throws CPathException when the WEB API gives an error
     */
    public ServiceResponse search(String keyword) throws CPathException {
        return search(Collections.singleton(keyword));
    }

    
    /**
     * Full text search. 
     * 
     * Retrieve the ordered by score list (hits)
     * of BioPAX objects that match the search query
     * and pass currently set filters.
     * 
     * See the cPath2 web service online description 
     * for the list of available index fields and filter values
     * (currently loaded data sources and organisms).
     *
     * @see #setDataSources(Collection)
     * @see #setOrganisms(Collection)
     *
     * @param keywords names, identifiers, or Lucene queries (will be joint with 'OR')
     * @return
     * @throws CPathException when the WEB API gives an error
     */
    public ServiceResponse search(Collection<String> keywords) 
    		throws CPathException 
    {
    	String url = endPointURL + Cmd.SEARCH + "?" 
            	+ CmdArgs.q + "=" + join("", keywords, " ") // spaces means 'OR'
                + (getPage() > 0 ? "&" + CmdArgs.page + "=" + getPage() : "")
                + (getDataSources().isEmpty() ? "" : "&" + join(CmdArgs.datasource + "=", getDataSources(), "&"))
                + (getOrganisms().isEmpty() ? "" : "&" + join(CmdArgs.organism + "=", getOrganisms(), "&"))
                + (getType() != null ? "&" + CmdArgs.type + "=" + getType() : "");
    	
        ServiceResponse resp = restTemplate.getForObject(url, SearchResponse.class);
        if(resp instanceof ErrorResponse) {
            throw CPathExceptions.newException((ErrorResponse) resp);
        }
        
        return resp;
    }

    
    /**
     * Retrieves details regarding one or more records, such as pathway,
     * interaction or physical entity. For example, get the complete
     * Apoptosis pathway from Reactome.
     *
     * @param id a BioPAX element ID
     * @return BioPAX model containing the requested element
     */
    public Model get(String id) {
        return get(Collections.singleton(id));
    }

    
    /**
     * Builds a <em>get</em> BioPAX (default format) 
     * by URI(s) query URL string.
     * 
     * @param ids
     * @return
     */
    public String queryGet(Collection<String> ids) {
        return endPointURL + Cmd.GET + "?" 
        	+ join(CmdArgs.uri + "=" , ids, "&");
    }
    
    
    /**
     * Retrieves details regarding one or more records, such as pathway,
     * interaction or physical entity. For example, get the complete
     * Apoptosis pathway from Reactome.
     *
     * @param ids a set of BioPAX element IDs
     * @return BioPAX model containing the requested element
     */
    public Model get(Collection<String> ids) {
        String url = queryGet(ids);
        return restTemplate.getForObject(url, Model.class);
    }

    
    /**
     * Builds a 'PATHS BETWEEN' BioPAX <em>graph</em> query URL string.
     * 
     * @param sourceSet
     * @return
     */
    public String queryPathsBetween(Collection<String> sourceSet) {
    	return endPointURL + Cmd.GRAPH + "?" + CmdArgs.kind + "=" +
    		GraphType.PATHSBETWEEN.name().toLowerCase() + "&"
    		+ join(CmdArgs.source + "=", sourceSet, "&") + "&"
    		+ CmdArgs.limit + "=" + graphQueryLimit;
    }  
    
    
    /**
	 *  Finds paths between a given source set of objects. The source set may contain Xref,
	 *  EntityReference, and/or PhysicalEntity objects.
	 *
	 * @param sourceSet set of xrefs, entity references, or physical entities
	 * @return a BioPAX model that contains the path(s).
	 */
	public Model getPathsBetween(Collection<String> sourceSet)
	{
		String url = queryPathsBetween(sourceSet);
		return restTemplate.getForObject(url, Model.class);
	}

	
	/**
	 * Builds a 'PATHS FROM TO' <em>graph</em> query URL string.
	 * 
	 * @param sourceSet
	 * @param targetSet
	 * @return
	 */
	public String queryPathsFromTo(Collection<String> sourceSet, Collection<String> targetSet)
	{
		return endPointURL + Cmd.GRAPH + "?" + CmdArgs.kind + "=" +
			GraphType.PATHSFROMTO.name().toLowerCase() + "&"
			+ join(CmdArgs.source + "=", sourceSet, "&") + "&"
			+ join(CmdArgs.target + "=", targetSet, "&") + "&"
			+ CmdArgs.limit + "=" + graphQueryLimit;
	}
	
	
	/**
	 *  Finds paths from a given source set of objects to a given target set of objects. 
	 *  Source and target sets may contain Xref, EntityReference, and/or PhysicalEntity objects.
	 *
	 * @param sourceSet set of xrefs, entity references, or physical entities
	 * @param targetSet set of xrefs, entity references, or physical entities
	 * @return a BioPAX model that contains the path(s).
	 */
	public Model getPathsFromTo(Collection<String> sourceSet, Collection<String> targetSet)
	{
		String url = queryPathsFromTo(sourceSet, targetSet);
		return restTemplate.getForObject(url, Model.class);
	}


	/**
	 * Builds a 'NEIGHBORHOOD' BioPAX <em>graph</em> query URL string.
	 * 
	 * @param sourceSet
	 * @return
	 */
	public String queryNeighborhood(Collection<String> sourceSet)
	{
		return endPointURL + Cmd.GRAPH + "?" + CmdArgs.kind + "=" +
			GraphType.NEIGHBORHOOD.name().toLowerCase() + "&"
			+ join(CmdArgs.source + "=", sourceSet, "&")
			+ "&" + CmdArgs.direction + "=" + direction 
			+ "&" + CmdArgs.limit + "=" + graphQueryLimit;
	}

	
	/**
	 * Searches directed paths from and/or to the given source set of entities, in the specified search limit.
	 *
	 * @param sourceSet Set of source physical entities
	 * @param direction direction to extends network towards neighbors
	 * @return BioPAX model representing the neighborhood.
	 */
	public Model getNeighborhood(Collection<String> sourceSet)
	{
		String url = queryNeighborhood(sourceSet);
		return restTemplate.getForObject(url, Model.class);
	}

	
	/**
	 * Builds a 'COMMON STREAM' BioPAX <em>graph</em> query URL string.
	 * 
	 * @see #setDirection(Direction)
	 * 
	 * @param sourceSet
	 * @return
	 */
	public String queryCommonStream(Collection<String> sourceSet)
	{
		if (direction == Direction.BOTHSTREAM)
			throw new IllegalArgumentException(
				"Direction of common-stream query should be either upstream or downstream.");

		return endPointURL + Cmd.GRAPH + "?" + CmdArgs.kind + "=" +
			GraphType.COMMONSTREAM.name().toLowerCase() + "&"
			+ join(CmdArgs.source + "=", sourceSet, "&") 
			+ "&" + CmdArgs.direction + "=" + direction 
			+ "&" + CmdArgs.limit + "=" + graphQueryLimit;
	}
	
	
	/**
	 * This query searches for the common upstream (common regulators) or
	 * common downstream (common targets) objects of the given source set.
	 *
	 * @see #setDirection(Direction) (only upstream or downstream)
	 *
	 * @param sourceSet set of physical entities
	 * @return a BioPAX model that contains the common stream
	 */
	public Model getCommonStream(Collection<String> sourceSet)
	{
		if (direction == Direction.BOTHSTREAM)
			throw new IllegalArgumentException(
				"Direction of common-stream query should be either upstream or downstream.");
		
		String url = queryCommonStream(sourceSet);
		return restTemplate.getForObject(url, Model.class);
	}

	
    /**
     * Gets the list of top (root) pathways 
     * (in the same xml format used by the full-text search commands)
     * 
     * @return
     */
    public SearchResponse getTopPathways() {
    	SearchResponse resp = restTemplate.getForObject(endPointURL 
        		+ Cmd.TOP_PATHWAYS, SearchResponse.class);
    	
    	Collections.sort(resp.getSearchHit(), new Comparator<SearchHit>() {
			@Override
			public int compare(SearchHit h1, SearchHit h2) {
				return h1.toString().compareTo(h2.toString());
			}
		});
    	
    	
    	return resp;
    }    

    
    /**
     * Gets the values (for data types) or URIs (objects) from a 
     * BioPAX property path starting from each of specified URIs.
     * 
     * @param uris
     * @return
     * @throws CPathException 
     */
    public TraverseResponse traverse(Collection<String> uris) throws CPathException {
        String url = endPointURL + Cmd.TRAVERSE + "?" 
        		+ join(CmdArgs.uri + "=", uris, "&")
        		+ "&" + CmdArgs.path + "=" + path;
        
        ServiceResponse resp = restTemplate.getForObject(url, TraverseResponse.class);
        if(resp instanceof ErrorResponse) {
            throw CPathExceptions.newException((ErrorResponse) resp);
        }
        
        return (TraverseResponse) resp;
    }
    
    
    private String join(String prefix, Collection<String> strings, String delimiter) {
        List<String> prefixed = new ArrayList<String>();

       	for(String s: strings)
			prefixed.add(prefix + s);

        return StringUtils.join(prefixed, delimiter);
    }

    
    /**
     * The WEB Service URL prefix.
     * 
     * @return the end point URL as a string
     */
    public String getEndPointURL() {
        return endPointURL;
    }

    
    /**
     * @see #getEndPointURL()
     * @param endPointURL the end point URL as a string
     */
    public void setEndPointURL(String endPointURL) {
        this.endPointURL = endPointURL;
    }

    
    /**
     * Pathway Commons returns no more than N (e.g., 1000) search hits per request.
     * You can request results beyond the first N by using the page parameter.
     * Default is 0. Total number of result pages (P) can be calculated using the first
     * page SearchResponse attributes as follows: P = INT[ (numHits - 1)/numHitsPerPage + 1 ]
     *
     * @see #search(Collection)
     *
     * @return the page number
     */
    public Integer getPage() {
        return page;
    }

    
    /**
     * @see #getPage()
     * @param page page number ()
     */
    public void setPage(Integer page) {
    	if(page >= 0)
    		this.page = page;
    	else 
    		throw new IllegalArgumentException("Negative page numbers are not supported!");
    }

    
    /**
     * Graph query search distance limit (default = 1).
     *
     * @see #getNeighborhood(Collection)
     * @see #getCommonStream(Collection)
     * @see #getPathsBetween(Collection)
     *
     * @return distance limit.
     */
    public Integer getGraphQueryLimit() {
        return graphQueryLimit;
    }

    
    /**
     * @see #getGraphQueryLimit()
     *
     * @param graphQueryLimit graph distance limit
     */
    public void setGraphQueryLimit(Integer graphQueryLimit) {
        this.graphQueryLimit = graphQueryLimit;
    }

    
    /**
     * BioPAX class filter for find() method.
     *
     * @see #search(String)
     * @see #findEntity(String)
     *
     * @return BioPAX L3 Class simple name
     */
    public String getType() {
        return type;
    }

    
    /**
     * @see #getType()
     *
     * @param type a BioPAX L3 Class
     */
    public void setType(String type) {
        this.type = type;
    }

    
    /**
     * Organism filter for find(). Multiple organism filters are allowed per query.
     *
     * @see #search(String)
     * @see #findEntity(String)
     *
     * @return set of strings representing organisms.
     */
    public Collection<String> getOrganisms() {
        return organisms;
    }

    
    /**
     * @see #getOrganisms()
     *
     * @param organisms set of strings representing organisms.
     */
    public void setOrganisms(Collection<String> organisms) {
        this.organisms = organisms;
    }

    
    /**
     * Data source filter for find(). Multiple data source filters are allowed per query.
     *
     * @see #search(String)
     * @see #findEntity(String)
     *
     * @return data sources as strings
     */
    public Collection<String> getDataSources() {
        return dataSources;
    }

    
    /**
     * @see #getDataSources()
     *
     * @param dataSources data sources as strings
     */
    public void setDataSources(Collection<String> dataSources) {
        this.dataSources = dataSources;
    }

    
    /**
     * @see #getDataSources()
     * @see #setDataSources(java.util.Collection)
     * @return valid values for the datasource parameter as a Help object.
     */
    public Map<String, String> getValidDataSources() {
        Help h = restTemplate.getForObject(endPointURL + "help/datasources", Help.class);
        return parseHelpSimple(h);
    }

    
    /**
     * @see #getOrganisms()
     * @see #setOrganisms(java.util.Collection)
     * @return valid values for the organism parameter as a Help object.
     */
    public Map<String,String> getValidOrganisms() {
        Help h = restTemplate.getForObject(endPointURL + "help/organisms", Help.class);
        return parseHelpSimple(h);
    }

    
    /**
     * @see #getType()
     * @see #setType(String)
     * @return valid values for the BioPAX type parameter.
     */
    public Collection<String> getValidTypes() {
    	Help help = restTemplate.getForObject(endPointURL + "help/types", Help.class);
    	return parseHelpSimple(help).keySet();
    }

    
    private Map<String, String> parseHelpSimple(Help help) {
        Map<String,String> types = new TreeMap<String,String>();
    	for(Help h : help.getMembers()) {
    		String title = (h.getTitle() != null) 
    			? h.getTitle().toUpperCase() : h.getId();
    		types.put(h.getId(), title);
    	}
    	return types;
    }
    
	/**
	 * Gets the BioPAX property path (current value).
	 * @see #traverse(Collection)
	 * 
	 * @return
	 */
	public String getPath() {
		return path;
	}

	
	/**
	 * @see #getPath()
	 * @see #traverse(Collection)
	 * 
	 * @param path
	 */
	public void setPath(String path) {
		this.path = path;
	}
    
		
	/**
	 * Graph query direction (depends on the graph query type)
	 * 
	 * @return the direction
	 */
	public Direction getDirection() {
		return direction;
	}


	/**
	 * @see #getDirection()
	 * @param direction the direction to set (if null, then 'downstream' is used)
	 */
	public void setDirection(Direction direction) {
		this.direction = (direction == null)
			? Direction.DOWNSTREAM : direction;
	}
	
}
