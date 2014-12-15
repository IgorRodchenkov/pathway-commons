package cpath.service;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CachingWrapperFilter;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
//import org.apache.lucene.search.MultiTermQuery;
//import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.Version;
import org.biopax.paxtools.controller.Fetcher;
import org.biopax.paxtools.controller.ModelUtils;
import org.biopax.paxtools.controller.SimpleEditorMap;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.BioSource;
import org.biopax.paxtools.model.level3.Level3Element;
import org.biopax.paxtools.model.level3.Named;
import org.biopax.paxtools.model.level3.Pathway;
import org.biopax.paxtools.model.level3.Process;
import org.biopax.paxtools.model.level3.Provenance;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.XReferrable;
import org.biopax.paxtools.model.level3.Xref;
import org.biopax.paxtools.util.ClassFilterSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cpath.config.CPathSettings;
import cpath.service.jaxb.SearchHit;
import cpath.service.jaxb.SearchResponse;

/**
 * A full-text searcher/indexer for BioPAX L3 models.
 * 
 * @author rodche
 */
public class SearchEngine implements Indexer, Searcher {
	private static final Logger LOG = LoggerFactory.getLogger(SearchEngine.class);
	
	// search fields
	public static final String FIELD_URI = "uri";
	public static final String FIELD_KEYWORD = "keyword"; //anything, e.g., names, terms, comments, incl. - from child elements 
	public static final String FIELD_NAME = "name"; // standardName, displayName, other names
	public static final String FIELD_XREFDB = "xrefdb"; //xref.db
	public static final String FIELD_XREFID = "xrefid"; //xref.id
	public static final String FIELD_PATHWAY = "pathway"; //parent/owner pathways; to be inferred from the whole biopax model
	public static final String FIELD_SIZE = "size";	
	// Full-text search/filter fields (case sensitive) -
	//index organism names, cell/tissue type (term), taxonomy id, but only store BioSource URIs	
	public static final String FIELD_ORGANISM = "organism";
	//index data source names, but only URIs are stored in the index
	public static final String FIELD_DATASOURCE = "datasource";
	public static final String FIELD_TYPE = "type";
	
	public final static String[] DEFAULT_FIELDS = //to use with the MultiFieldQueryParser
	{
			FIELD_NAME, // standardName, displayName, other names
			FIELD_XREFDB, //xref.db
			FIELD_XREFID, //xref.id (also direct child's xref.id, i.e., can find both xref and its owners using a xrefid:<id> query string)
			FIELD_PATHWAY, // PARENT pathway names (URIs are stored in the index, but not analyzed/indexed)
			FIELD_SIZE, // find entities with a given no. child/associated processes...
			FIELD_KEYWORD, //includes data type properties (names, terms, comments), 
						  //also from  child elements up to given depth (3), also stores but not indexes parent pathway uris and names.			
// the following fields are for filtering only (thus excluded):
//			FIELD_ORGANISM,	
//			FIELD_DATASOURCE, 
//			FIELD_TYPE,
	};
		
	private final Model model;
	private int maxHitsPerPage;
	private final Analyzer analyzer;
	private final File indexFile;
	private SearcherManager searcherManager;

	public final static int DEFAULT_MAX_HITS_PER_PAGE = 100;
	
	/**
	 * Main Constructor.
	 * @param indexLocation
	 * @throws IOException 
	 */
	public SearchEngine(Model model, String indexLocation) {
		this.model = model;
		this.indexFile = new File(indexLocation);
		initSearcherManager();
		this.maxHitsPerPage = DEFAULT_MAX_HITS_PER_PAGE;
		this.analyzer = new StandardAnalyzer();
	}

	private void initSearcherManager() {
		try {
			if(indexFile.exists()) 
				this.searcherManager = 
					new SearcherManager(MMapDirectory.open(indexFile), new SearcherFactory());
			else 
				LOG.info(indexFile.getPath() + " does not exist.");
		} catch (IOException e) {
			LOG.warn("Could not create a searcher: " + e);
		}
	}
	
	public void setMaxHitsPerPage(int maxHitsPerPage) {
		this.maxHitsPerPage = maxHitsPerPage;
	}
	
	public int getMaxHitsPerPage() {
		return maxHitsPerPage;
	}
	

	public SearchResponse search(String query, int page,
			Class<? extends BioPAXElement> filterByType, String[] datasources,
			String[] organisms) 
	{
		SearchResponse response = null;
		
		LOG.debug("search: " + query + ", page: " + page 
			+ ", filterBy: " + filterByType
			+ "; extra filters: ds in (" + Arrays.toString(datasources)
			+ "), org. in (" + Arrays.toString(organisms) + ")");
		
		IndexSearcher searcher = null;
	
		try {	
			QueryParser queryParser = new MultiFieldQueryParser(DEFAULT_FIELDS, analyzer);
			queryParser.setAllowLeadingWildcard(true);//TODO do we really want leading wildcards (e.g. *sulin)?
			
			searcher = searcherManager.acquire();	
			
			//find and transform top docs to search hits (beans), considering pagination...
			if(!query.trim().equals("*")) { //if not "*" query, which is not supported out-of-the-box, then
				//create the lucene query
				Query luceneQuery = queryParser.parse(query);
//do NOT (Lucene 4.1), or scoring/highlighting won't work for wildcard queries...				
//luceneQuery = searcher.rewrite(luceneQuery); 
				LOG.debug("parsed lucene query is " + luceneQuery.getClass().getSimpleName());
				
				//create filter: type AND (d OR d...) AND (o OR o...)
				Filter filter = createFilter(filterByType, datasources, organisms);
				
				//get the first page of top hits
				TopDocs topDocs = searcher.search(luceneQuery, filter, maxHitsPerPage);
				//get the required hits page if page>0
				if(page>0) {
					TopScoreDocCollector collector = TopScoreDocCollector.create(maxHitsPerPage*(page+1), true);  
					searcher.search(luceneQuery, filter, collector);
					topDocs = collector.topDocs(page * maxHitsPerPage, maxHitsPerPage);
				}
				
				//transform docs to hits, use a highlighter to get excerpts
				response = transform(luceneQuery, searcher, true, topDocs);
	
			} else { //find ALL objects of a particular BioPAX class (+ filters by organism, datasource)
				if(filterByType==null) 
					filterByType = Level3Element.class;

				//replace q="*" with a search for the class or its sub-class name in the TYPE field
				BooleanQuery luceneQuery = new BooleanQuery();
				for(Class<? extends BioPAXElement> subType : SimpleEditorMap.L3.getKnownSubClassesOf(filterByType)) {
					luceneQuery.add(new TermQuery(new Term(FIELD_TYPE, subType.getSimpleName().toLowerCase())), Occur.SHOULD);
				}
				Filter filter = createFilter(null, datasources, organisms);
				
				//get the first page of top hits
				TopDocs topDocs = searcher.search(luceneQuery, filter, maxHitsPerPage);
				//get the required hits page if page>0
				if(page>0) {
					TopScoreDocCollector collector = TopScoreDocCollector.create(maxHitsPerPage*(page+1), true);  
					searcher.search(luceneQuery, filter, collector);
					topDocs = collector.topDocs(page * maxHitsPerPage, maxHitsPerPage);	
				}
				
				//convert
				response = transform(luceneQuery, searcher, false, topDocs);			
			}	
			
		} catch (ParseException e) {
			throw new RuntimeException("getTopDocs: failed to parse the query string.", e);
		} catch (IOException e) {
			throw new RuntimeException("getTopDocs: failed.", e);
		} finally {
			try {
				if(searcher!=null) {
					searcherManager.release(searcher);
					searcher = null;
				}
			} catch (IOException e) {}	
		}
	
		response.setPageNo(page);
		
		return response;
	}

	
	// Transform Lucene docs to hits (xml/java beans)
	private SearchResponse transform(Query query, IndexSearcher searcher, boolean highlight, TopDocs topDocs) 
			throws CorruptIndexException, IOException 
	{	
		if(topDocs == null)
			throw new IllegalArgumentException("topDocs is null");
		
		SearchResponse response = new SearchResponse();
		response.setMaxHitsPerPage(maxHitsPerPage);
		response.setNumHits(topDocs.totalHits);	
		List<SearchHit> hits = response.getSearchHit();//empty list
		assert hits!=null && hits.isEmpty();
		LOG.debug("transform, no. TopDocs to process:" + topDocs.scoreDocs.length);
		for(ScoreDoc scoreDoc : topDocs.scoreDocs) {			
			SearchHit hit = new SearchHit();
			Document doc = searcher.doc(scoreDoc.doc);
			String uri = doc.get(FIELD_URI);
			BioPAXElement bpe = model.getByID(uri);			
			LOG.debug("transform: doc:" + scoreDoc.doc + ", uri:" + uri);
			
			// use the highlighter (get matching fragments)
			// for this to work, all keywords were stored in the index field
			if (highlight && doc.get(FIELD_KEYWORD) != null) {				
				// use a Highlighter (store.YES must be enabled for 'keyword' field)
				QueryScorer scorer = new QueryScorer(query, FIELD_KEYWORD); 
				//this fixes scoring/highlighting for all-field wildcard queries like q=insulin* 
				//but not for term/prefix queries, i.e, q=name:insulin*, q=pathway:brca2. TODO
				scorer.setExpandMultiTermQuery(true);	
				
				//TODO use PostingsHighlighter once it's stable (see http://lucene.apache.org/core/4_10_0/highlighter/org/apache/lucene/search/postingshighlight/PostingsHighlighter.html)				
				SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<span class='hitHL'>", "</span>");
				Highlighter highlighter = new Highlighter(formatter, scorer);
				highlighter.setTextFragmenter(new SimpleSpanFragmenter(scorer, 80));
										
				final String text = StringUtils.join(doc.getValues(FIELD_KEYWORD), " ");
				try {
					TokenStream tokenStream = analyzer.tokenStream("", new StringReader(text));
					String res = highlighter.getBestFragments(tokenStream, text, 7, "...");

					if(res != null && !res.isEmpty())
						hit.setExcerpt(res);

				} catch (Exception e) {throw new RuntimeException(e);}

			} else if(highlight) {
				LOG.warn("Highlighter skipped, because KEYWORD field was null; hit: " 
						+ uri + ", " + bpe.getModelInterface().getSimpleName());
			}
			
			hit.setUri(uri);
			hit.setBiopaxClass(bpe.getModelInterface().getSimpleName());
			
			// add standard and display names if any -
			if (bpe instanceof Named) {
				Named named = (Named) bpe;
				String std = named.getStandardName();
				if (std != null)
					hit.setName(std);
				else
					hit.setName(named.getDisplayName());
				
				// a hack for BioSource (store more info)
				if(bpe instanceof BioSource) {
					for(String name : named.getName())
						hit.getOrganism().add(name);
					String txid = getTaxonId((BioSource)named);
					if(txid != null)
						hit.getOrganism().add(txid);
				}
				
				// a hack for Provenance: save/return other names 
				// (to be used as filter by data source values)
				if(bpe instanceof Provenance) {
					for(String name : named.getName())
						hit.getDataSource().add(name);
				}	
			}
						
			// extract organisms (URI only) 
			if(doc.get(FIELD_ORGANISM) != null) {
				Set<String> uniqueVals = new TreeSet<String>();
				for(String o : doc.getValues(FIELD_ORGANISM)) {
					//note: only URIS are stored in the index					
					uniqueVals.add(o);
				}
				hit.getOrganism().addAll(uniqueVals);
			}
			
			// extract values form the index
			if(doc.get(FIELD_DATASOURCE) != null) {
				Set<String> uniqueVals = new TreeSet<String>();
				for(String d : doc.getValues(FIELD_DATASOURCE)) {
					//note: only URIS are stored in the index
					uniqueVals.add(d);
				}
				hit.getDataSource().addAll(uniqueVals);
			}	
			
			// extract only pathway URIs 
			//(because names and IDs used to be stored in the index field as well)
			if(doc.get(FIELD_PATHWAY) != null) {
				Set<String> uniqueVals = new TreeSet<String>();
				for(String d : doc.getValues(FIELD_PATHWAY)) {
					//only URIs were stored (though all names/ids were indexed/analyzed)
					if(!d.equals(uri)) //exclude itself
						uniqueVals.add(d);
				}
				hit.getPathway().addAll(uniqueVals);
			}
			
			//no. processes in the sub-network
			if(doc.get(FIELD_SIZE)!=null)
				hit.setSize(Integer.valueOf(doc.get(FIELD_SIZE))); 
			
			//if cpath2 debugging, update hit.excerpt - add the Lucene's 'explain' using the query and doc.
			if(CPathSettings.getInstance().isDebugEnabled()) {
				String excerpt = hit.getExcerpt();
				if(excerpt == null) excerpt = "";
				hit.setExcerpt(excerpt + " -SCORE- " + scoreDoc.score 
						+ " -EXPLANATION- " + searcher.explain(query, scoreDoc.doc));
			}
			
			hits.add(hit);
		}
				
		//add the Provenance's standardName(s) to the search response
		if(!hits.isEmpty()) {
			for(String puri : response.provenanceUris()) {
				Provenance p = (Provenance) model.getByID(puri);
				response.getProviders()
					.add((p.getStandardName()!=null)?p.getStandardName():p.getDisplayName());
			}
		}
		
		return response;
	}


	public void index() {
		final int numObjects =  model.getObjects().size();
		LOG.info("index(), there are " + numObjects + " BioPAX objects to be (re-)indexed.");		
		IndexWriter iw;		
		try {
			//close the searcher manager if the old index exists
			if(searcherManager != null) {
				searcherManager.close();
				searcherManager = null;
			}
			IndexWriterConfig conf = new IndexWriterConfig(Version.LATEST, analyzer);
			iw = new IndexWriter(FSDirectory.open(indexFile), conf);
			//cleanup
			iw.deleteAll();
			iw.commit();
		} catch (IOException e) {
			throw new RuntimeException("Failed to create a new IndexWriter.", e);
		}		
		final IndexWriter indexWriter = iw;

		ExecutorService exec = Executors.newFixedThreadPool(30);
		
		final AtomicInteger numLeft = new AtomicInteger(numObjects);
		for(final BioPAXElement bpe : model.getObjects()) {	
			// prepare & index each element in a separate thread
			exec.execute(new Runnable() {
				public void run() {					
					// get or infer some important values if possible from this, child or parent objects:
					Set<String> keywords = ModelUtils.getKeywords(bpe, 3);
					
					// a hack to remove special (debugging) biopax comments
					for(String s : new HashSet<String>(keywords)) {
						//exclude additional comments generated by normalizer, merger, etc.
						if(s.startsWith("REPLACED ") || s.contains("ADDED"))
							keywords.remove(s);
					}
					
					bpe.getAnnotations().put(FIELD_KEYWORD, keywords);
					bpe.getAnnotations().put(FIELD_DATASOURCE, ModelUtils.getDatasources(bpe));
					bpe.getAnnotations().put(FIELD_ORGANISM, ModelUtils.getOrganisms(bpe));
					bpe.getAnnotations().put(FIELD_PATHWAY, ModelUtils.getParentPathways(bpe));
					
					// for bio processes, also save the total no. member interactions and pathways:
					if(bpe instanceof org.biopax.paxtools.model.level3.Process) {
						int size = new Fetcher(SimpleEditorMap.L3, Fetcher.nextStepFilter)
								.fetch(bpe, Process.class).size(); //except itself						
						bpe.getAnnotations().put(FIELD_SIZE, Integer.toString(size)); 
					}

					index(bpe, indexWriter);
					
					//count, log a progress message
					int left = numLeft.decrementAndGet();
					if(left % 10000 == 0)
						LOG.info("index(), biopax objects left to index: " + left);
				}
			});
		}
		
		exec.shutdown(); //stop accepting new tasks	
		try { //wait
			exec.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			throw new RuntimeException("Interrupted!", e);
		}
		
		try {
			indexWriter.close(); //wait for pending op., auto-commit, close.
		} catch (IOException e) {
			throw new RuntimeException("Failed to close IndexWriter.", e);
		} 
		
		//finally, create a new searcher manager
		initSearcherManager();
	}
	
	
	// internal methods
	
	/**
	 * Creates a new Lucene Document that corresponds to a BioPAX object.
	 * It does not check whether the document exists (should not be there,
	 * because the {@link #index(Model)} method cleans up the index)
	 * 
	 * Some fields also include biopax data type property values not only from 
	 * the biopax object but also from its child elements, up to some depth 
	 * (using key-value pairs in the pre-computed bpe.annotations map):
	 * 
	 *  'uri' - biopax object's absolute URI, index=no, analyze=no, store=yes;
	 * 
	 *  'keyword' - infer from this bpe and its child objects' data properties,
	 *            such as Score.value, structureData, structureFormat, chemicalFormula, 
	 *            availability, term, comment, patoData, author, source, title, url, published, 
	 *            up to given depth/level; and also all 'pathway' field values are included here; 
	 *            analyze=yes, store=yes;
	 *  
	 *  'datasource', 'organism' and 'pathway' - infer from this bpe and its child objects 
	 *  									  up to given depth/level, analyze=no, store=yes;
	 *  
	 *  'size' - number of child processes, an integer as string; analyze=no, store=yes
	 * 
	 * @param bpe
	 * @param indexWriter
	*/
	void index(BioPAXElement bpe, IndexWriter indexWriter) {		
		// create a new document
		final Document doc = new Document();
		
		// save URI (not indexed field)
		Field field = new StoredField(FIELD_URI, bpe.getRDFId());
		doc.add(field);
		
		// index and store but not analyze/tokenize the biopax class name:
		field = new StringField(FIELD_TYPE, bpe.getModelInterface().getSimpleName().toLowerCase(), Field.Store.YES);
		doc.add(field);
		
		// make index fields from the annotations map (of pre-calculated/inferred values)
		if(!bpe.getAnnotations().isEmpty()) {
			if(bpe.getAnnotations().containsKey(FIELD_PATHWAY)) {
				addPathways((Set<Pathway>)bpe.getAnnotations().get(FIELD_PATHWAY), doc);
			}
			if(bpe.getAnnotations().containsKey(FIELD_ORGANISM)) {
				addOrganisms((Set<BioSource>)bpe.getAnnotations().get(FIELD_ORGANISM), doc);
			}
			if(bpe.getAnnotations().containsKey(FIELD_DATASOURCE)) {
				addDatasources((Set<Provenance>)bpe.getAnnotations().get(FIELD_DATASOURCE), doc);
			}
			if(bpe.getAnnotations().containsKey(FIELD_KEYWORD)) {
				addKeywords((Set<String>)bpe.getAnnotations().get(FIELD_KEYWORD), doc);
			}
			if(bpe.getAnnotations().containsKey(FIELD_SIZE)) {
				field = new IntField(FIELD_SIZE, 
					Integer.parseInt((String)bpe.getAnnotations()
					.get(FIELD_SIZE)), Field.Store.YES);
				doc.add(field);
			}
		}
		bpe.getAnnotations().remove(FIELD_KEYWORD);
		bpe.getAnnotations().remove(FIELD_DATASOURCE);
		bpe.getAnnotations().remove(FIELD_ORGANISM);
		bpe.getAnnotations().remove(FIELD_PATHWAY);
		bpe.getAnnotations().remove(FIELD_SIZE);
			
		// name
		if(bpe instanceof Named) {
			Named named = (Named) bpe;
			if(named.getDisplayName() != null) {
				field = new TextField(FIELD_NAME, named.getDisplayName(), Field.Store.NO);
				field.setBoost(3.0f);
				doc.add(field);
			}
			if(named.getStandardName() != null) {
				field = new TextField(FIELD_NAME, named.getStandardName(), Field.Store.NO);
				field.setBoost(3.5f);
				doc.add(field);
			}
			for(String name : named.getName()) {
				if(name.equals(named.getDisplayName()) || name.equals(named.getStandardName()))
					continue;
				field = new TextField(FIELD_NAME, name.toLowerCase(), Field.Store.NO);
				field.setBoost(2.5f);
				doc.add(field);
			}
		}
		
		// XReferrable.xref - build 'xrefid' index field from all Xrefs)
		if(bpe instanceof XReferrable) {
			XReferrable xr = (XReferrable) bpe;
			for(Xref xref : xr.getXref()) {
				if (xref.getId() != null) {
					//the filed is not_analyzed; so in order to make search case-insensitive 
					//(when searcher uses standard analyzer), we turn the value to lowercase.
					field = new StringField(FIELD_XREFID, xref.getId().toLowerCase(), Field.Store.NO);
					doc.add(field);
				}
			}
		}
		
		// Xref db/id (these are for a precise search by standard bio ID)
		if(bpe instanceof Xref) {
			Xref xref = (Xref) bpe;
			if (xref.getId() != null) {
				field = new StringField(FIELD_XREFID, xref.getId().toLowerCase(), Field.Store.NO);
				doc.add(field);
			}
			if (xref.getDb() != null) {
				field = new TextField(FIELD_XREFDB, xref.getDb().toLowerCase(), Field.Store.NO);
				doc.add(field);
			}
		}
		
		// write
		try {
			indexWriter.addDocument(doc);
		} catch (IOException e) {
			throw new RuntimeException("Failed to index; " + bpe.getRDFId(), e);
		}
	}

	private void addKeywords(Set<String> keywords, Document doc) {
		for (String keyword : keywords) {
			Field f = new TextField(FIELD_KEYWORD, keyword.toLowerCase(), Field.Store.YES);
//			f.setBoost(0.5f);
			doc.add(f);
		}
	}

	private void addDatasources(Set<Provenance> set, Document doc) {
		for (Provenance p : set) {
			// Index and store URI (untokinized) - 
			// required to accurately calculate no. entities or to filter by data source (diff. datasources may share same names)
			doc.add(new StringField(FIELD_DATASOURCE, p.getRDFId(), Field.Store.YES));
			// index names as well
			for (String s : p.getName())
				doc.add(new TextField(FIELD_DATASOURCE, s.toLowerCase(), Field.Store.NO));
		}
	}

	private void addOrganisms(Set<BioSource> set, Document doc) {	
		for(BioSource bs : set) {
			// store URI as is (not indexed, untokinized)
			doc.add(new StoredField(FIELD_ORGANISM, bs.getRDFId()));
				
			// add organism names
			for(String s : bs.getName()) {
				doc.add(new TextField(FIELD_ORGANISM, s.toLowerCase(), Field.Store.NO));
			}
			// add taxonomy
			for(UnificationXref x : 
				new ClassFilterSet<Xref,UnificationXref>(bs.getXref(), UnificationXref.class)) {
				if(x.getId() != null)
					doc.add(new TextField(FIELD_ORGANISM, x.getId().toLowerCase(), Field.Store.NO));
			}
			// include tissue type terms
			if (bs.getTissue() != null) {
				for (String s : bs.getTissue().getTerm())
					doc.add(new TextField(FIELD_ORGANISM, s.toLowerCase(), Field.Store.NO));
			}
			// include cell type terms
			if (bs.getCellType() != null) {
				for (String s : bs.getCellType().getTerm()) {
					doc.add(new TextField(FIELD_ORGANISM, s.toLowerCase(), Field.Store.NO));
				}
			}
		}
	}

	private void addPathways(Set<Pathway> set, Document doc) {
		for(Pathway pw : set) {
			//add URI as is (do not lowercase; do not index; store=yes - required to report hits, e.g., as xml)
			doc.add(new StoredField(FIELD_PATHWAY, pw.getRDFId()));
			
			// add names to the 'pathway' (don't store) and 'keywords' (store) indexes
			for (String s : pw.getName()) {
				doc.add(new TextField(FIELD_PATHWAY, s.toLowerCase(), Field.Store.NO));
				doc.add(new StoredField(FIELD_KEYWORD, s.toLowerCase())); //don't index but store for highlighting
			}
			
			// add unification xref IDs too
			for (UnificationXref x : new ClassFilterSet<Xref, UnificationXref>(
					pw.getXref(), UnificationXref.class)) {
				if (x.getId() != null) {
					// index in both 'pathway' (don't store) and 'keywords' (store)
					doc.add(new TextField(FIELD_PATHWAY, x.getId().toLowerCase(), Field.Store.NO));
					doc.add(new StoredField(FIELD_KEYWORD, x.getId().toLowerCase()));//don't index but store for highlighting
				}
			}
		}
	}

	
	private String getTaxonId(BioSource bioSource) {
		String id = null;
		if(!bioSource.getXref().isEmpty()) {
			Set<UnificationXref> uxs = new 
				ClassFilterSet<Xref,UnificationXref>(bioSource.getXref(), 
						UnificationXref.class);
			for(UnificationXref ux : uxs) {
				if("taxonomy".equalsIgnoreCase(ux.getDb())) {
					id = ux.getId();
					break;
				}
			}
		}
		return id;
	}
	
	/** 
	 * Creates a search filter like 
	 * type AND (datasource OR datasource...) 
	 *      AND (organism OR organism OR...)
	 * 
	 * Both names (partial or full) and URIs should work as filter values. 
	 * 
	 * @param type
	 * @param datasources
	 * @param organisms
	 */	
	private Filter createFilter(Class<? extends BioPAXElement> type, 
			String[] datasources, String[] organisms) {
		
		BooleanQuery filterQuery = new BooleanQuery();
		
		//AND datasources	
		if (datasources != null && datasources.length > 0) {
			filterQuery.add(subQuery(datasources, FIELD_DATASOURCE), Occur.MUST);
		}
		//AND organisms
		if (organisms != null && organisms.length > 0) {
			filterQuery.add(subQuery(organisms, FIELD_ORGANISM), Occur.MUST);
		}		
		//AND type	
		if(type != null) { //add biopax class filter
			BooleanQuery query = new BooleanQuery();
			query.add(new TermQuery(new Term(FIELD_TYPE, type.getSimpleName().toLowerCase())), Occur.SHOULD);//OR
			//for each biopax subclass (interface), add the name to the filter query
			for(Class<? extends BioPAXElement> subType : SimpleEditorMap.L3.getKnownSubClassesOf(type)) {
				query.add(new TermQuery(new Term(FIELD_TYPE, subType.getSimpleName().toLowerCase())), Occur.SHOULD);//OR
			}		
			filterQuery.add(query, Occur.MUST);
		}
		
		if(!filterQuery.clauses().isEmpty()) {
			LOG.debug("filterQuery: " + filterQuery.toString());
			return new CachingWrapperFilter( new QueryWrapperFilter(filterQuery) ); //TODO why CachingWrapperFilter, QueryWrapperFilter?
		} else 
			return null;
	}

	/*
	 * Values are joint with OR, but if a value
	 * has whitespace symbols, it also make a sub-query,
	 * in which terms are joint with AND. This is to filter
	 * by datasource/organism's full name, partial name, uri,
	 * using multiple datasources/organisms.
	 * For example, 
	 * "search?q=*&datasource=intact complex&type..." - will get only IntAct Complex objects;
	 * "search?q=*&datasource=intact&type..." - will consider both IntAct and IntAct Complex
	 * "search?q=*&datasource=intact biogrid&type..." means "to occur in both intact and biogrid" 
	 *  (can be a canonical protein reference; it's is not equivalent to "search?q=*&datasource=intact&datasource=biogrid&...",
	 *  which means "to occur either in intact, incl. IntAct Complex, or in biogrid or in all these")
	 * "search?q=*&datasource=intact complex biogrid&..." - won't match anything.
	 * 
	 * @param filterValues
	 * @param filterField
	 * @return
	 */
	private Query subQuery(String[] filterValues, String filterField) {
		BooleanQuery query = new BooleanQuery();	
		final Pattern pattern = Pattern.compile("\\s");
		
		for(String v : filterValues) {
			if(pattern.matcher(v).find()) {
				BooleanQuery bq = new BooleanQuery();
				for(String w : v.split("\\s+")) {
					bq.add(new TermQuery(new Term(filterField, w.toLowerCase())), Occur.MUST);
					LOG.debug("subQuery, add part: " + w.toLowerCase());
				}
				query.add(bq, Occur.SHOULD);
			} else {
				query.add(new TermQuery(new Term(filterField, v.toLowerCase())), Occur.SHOULD);
			}
			
		}
		
		return query;
	}
	
}
