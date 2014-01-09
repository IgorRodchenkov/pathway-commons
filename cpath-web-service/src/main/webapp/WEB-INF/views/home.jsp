<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<c:set var="req" value="${pageContext.request}" />
<c:set var="uri" value="${req.requestURI}" />
<c:set var="base" value="${fn:replace(req.requestURL, fn:substring(uri, 1, fn:length(uri)), req.contextPath)}" />

<!DOCTYPE html>
<html>
<head>
	<meta charset="utf-8"/>
	<meta name="author" content="${cpath.name}"/>
	<meta name="description" content="cPath2 Service Description"/>
	<meta name="keywords" content="${cpath.name}, cPath2, cPathSquared, web service, help, documentation"/>
	<script src="http://ajax.googleapis.com/ajax/libs/jquery/1.10.2/jquery.min.js"></script>
	<script src="<c:url value="/resources/scripts/json.min.js"/>"></script>
	<script src="<c:url value="/resources/scripts/help.js"/>"></script>
	<link rel="stylesheet" href="<c:url value="/resources/css/cpath2.css"/>" media="screen"/>

	<title>cPath2::Info</title>
</head>
<body>
	<!-- Google Analytics -->
	<script>
  		(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
  		(i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
  		m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
  		})(window,document,'script','//www.google-analytics.com/analytics.js','ga');

  		ga('create', 'UA-43341809-3', 'pathwaycommons.org');
  		ga('send', 'pageview');
		window.ga = ga;
	</script>

<jsp:include page="header.jsp"/>

<div id="content">

<section id="description_section">

	<h2>About ${cpath.name}</h2>
	<p>${cpath.description}</p>
	<p>Data is freely available, under the license terms of each contributing <a href="datasources.html"> database</a>.</p>

	<h2>cPath2 Web Service Description</h2>
	<p>To query the integrated biological pathway data from this server 
		use the Web application programming interface (API) described below.
		This page also provides some examples to help you get started.</p>

	<!-- start of web service api documentation  - move it to a different page?-->

	<h2 id="commands">Commands</h2>
	<h3>For most users</h3>
	<p>Researchers and application developers can 
	use the following web services (the stable API):</p>
	<ol>
		<li><a href="#search">SEARCH</a></li>
		<li><a href="#get">GET</a></li>
		<li><a href="#graph">GRAPH</a></li>
		<li><a href="#traverse">TRAVERSE</a></li>
		<li><a href="#top_pathways">TOP_PATHWAYS</a></li>
	</ol>	
	
	<h3>For advanced users</h3>
	<p>Pathway data integrators and developers who build a  
	service or website on top of <strong>their own cPath2 instance</strong> 
	may also send requests to the following URL paths, which return 
	HTML/plain text or JSON objects (please refer to the 
	<a href="http://cpath2-site.pathway-commons.googlecode.com/hg/index.html">developer's documentation</a>,
	e.g., <a href="http://cpath2-site.pathway-commons.googlecode.com/hg/cpath-web-service/xref/index.html">web 
	controllers source code</a>, and use with care, for all these features are supplementary and may change):</p>
	<ol>
		<li><a href="#idmapping">/idmapping</a> (can map some identifiers to primary UniProt IDs)</li>		
		<li>/robots.txt and /favicon.ico</li>
		<li>/resources/* (css and scripts)</li>
		<li>/admin and /admin/** (a work-in-progress Web console...)</li>
		<li>/metadata/* (e.g., /metadata/datasources, /metadata/validations)</li>
		<li>/help (a REST web service that returns a XML/JSON Help object, 
		nested information pages about main web service commands, parameters, 
		BioPAX types and properties; e.g., /help/schema, 
		<a href="help/commands">/help/commands</a>, /help/types, all /help.json, etc.)</li>
		<li>/log and /log/* (server access logs summary and statistics, e.g.,)</li>
	</ol>
	
	<h3>Also</h3>
	<p>Everything else attached to the base web service URL is considered 
		a BioPAX element's <em>local ID</em> and translated to the corresponding <a href="#get">GET</a> 
		query. For example, ${base}pid (as well as ${cpath.xmlBase}pid) is currently 
		forwarded to <a href="get?uri=${cpath.xmlBase}pid">${base}get?uri=${cpath.xmlBase}pid</a>
		and returns the Provenance object (in BioPAX format). 
		This together with setting up a partial redirect for ${cpath.xmlBase} can make 
		most of the URIs in the database resolvable (Linked Data friendly). Normally, client 
		application developers are to use "get?uri=...&uri=..." directly and favor HTTP POST 
		queries instead of sending HTTP GET requests to ${cpath.xmlBase}* or ${base} URLs
		(which are not guaranteed to always work or return BioPAX; in future versions, there would be HTML instead).
	</p>

	<!-- URIs -->
	<h3>
		<a id="miriam"></a>Note about using URIs:
	</h3>

	<p>
		Some of the commands require URIs of <strong>existing</strong> BioPAX elements (parameters: 
		'source', 'uri', 'target'). Such URIs are either <a href="http://identifiers.org" rel="external">Identifiers.org</a>
		standard URLs (of canonical entity references, controlled vocabularies, etc., of participants of interactions and pathways), 
		or URLs that start with current xml:base, ${cpath.xmlBase} (e.g., URIs of most Entities and Xrefs).
		BioPAX elements's URIs are not something to guess or about or hit by chance. 
		For example, despite knowing current URI namespace ${cpath.xmlBase} and actual service location ${base}, 
		one should not normally hit ${base}foo, ${cpath.xmlBase}foo, or 
		${base}get?uri=${cpath.xmlBase}foo unless the corresponding BioPAX individual in fact there exists.
		Consider using <em>search, top_pathways</em>, and other query results to find objects of interest and extract valid URIs.
		Alternatively, official gene symbols, SwissProt, RefSeq, Ensembl, NCBI gene/protein <strong>identifiers
		might work as well in place of the full URIs</strong> in <em>get</em> and <em>graph</em> queries.
		As a rule, using full URIs makes a precise query, whereas using identifiers - more exploratory one 
		(which internally performs a simple id-mapping to UniProt and full-text search to discover the URIs for the query).
	</p>

	<h3><a id="enco"></a>About examples on this page</h3>

	<p>This is a research resource for biological pathway data, a web service
		to be consumed via (bioinformatic) software, such as Cytoscape, PCViz, ChIBE,
		and Javascript clients (normally, users are not expected to type-in
		a web service query in a browser's address line). However, in order to give  
		you an idea of what the web resource can do, this page was intentionally 
		made to contain examples one can simply click to run 
		(which results in a HTTP GET request sent to the server).
		This works because a) all example queries are quite simple, and b) their parameters, 
		such as non-trivial URIs, were properly URL-encoded. 
		Besides, <strong>consider using HTTP POST method only</strong> 
		(which does not have caching, encoding, nor too long URL issues) instead of GET. 
		Also, all URIs are case-sensitive and have no spaces.</p>
</section>

<section id="commands_description_section">

<!-- command bodies -->
<ol>
<!-- search command -->
<li>
	<h2>
		<a id="search"></a>Command: SEARCH
	</h2>

	<h3>Description:</h3>

	<p>
		This command provides a text search using the <a
			href="http://lucene.apache.org/core/3_6_2/queryparsersyntax.html"> Lucene query syntax </a>. Indexed
		fields were selected based on most common searches. Some of these fields are direct BioPAX properties, 
		others are composite relationships. All index fields are (case-sensitive):<em>comment,
		ecnumber, keyword, name, pathway, term, xrefdb, xrefid, dataSource, and organism</em>.
		The <em>pathway</em> field maps to all participants of pathways that contain the keyword(s) in any of its text
		fields. This field is transitive in the sense that participants of all sub-pathways are also returned.
		Finally, <em>keyword</em> is a transitive aggregate field that includes all searchable keywords of that element 
		and its child elements - e.g. a complex would be returned by a keyword search if one of its members has a match.
		Keyword is the default field type. All searches can also be filtered by data source and organism. It is also
		possible to restrict the domain class using the 'type' parameter. This query can be used standalone or to
		retrieve starting points for graph searches. Search strings are case insensitive unless put inside quotes.
	</p>

	<h3>Returns:</h3> 	
	<p>A set of BioPAX individuals that match the string search criteria. By default the results are
	returned as a XML document that follows the <a href="help/schema">Search Response XML Schema</a>. It is also
	possible to obtain the results in JSON by appending '.json' to the query URL. The results are paginated 
	(the page size is configured on the server side; current value is returned with every result, as attribute).
	</p>

	<h3 id="search_parameters">Parameters:</h3>
	<ul>
		<li><em>q=</em> [Required] a keyword, name, external identifier, or a Lucene query string.</li>
		<li><em>page=N</em> [Optional] (N&gt;=0, default is 0), search result page number.
		</li>
		<li><em>datasource=</em> [Optional] filter by data source (use names or URIs 
			of <a href="/datasources.html">pathway data sources</a> or of any existing Provenance object). 
			If multiple data source values are specified, a union of hits from specified sources is returned. For example, 
			<em>datasource=reactome&amp;datasource=pid</em> returns hits associated with Reactome or PID.
		</li>
		<li><em>organism=</em> [Optional] organism filter. The organism can be specified either by official name, e.g.
			"homo sapiens" or by NCBI taxonomy id, e.g. "9606". Similar to data sources, if multiple organisms are
			declared a union of all hits from specified organisms is returned. For example
			'organism=9606&amp;organism=10016' returns results for both human and mice. 
			Note the <a href="#organisms">officially supported species</a>.
		</li>
		<li><em>type=</em> [Optional] BioPAX class filter (<a href="#biopax_types">values</a>)
		</li>
	</ul>

	<h3>Examples:</h3> <br/>
	<ol>
		<li><a rel="example" href="search.xml?q=Q06609">A basic text search. This query returns all entities
			that contain the "Q06609" keyword in XML</a></li>
		<li><a rel="example" href="search.json?q=Q06609"> Same query returned in JSON format</a></li>
		<li><a rel="example" href="search?q=xrefid:Q06609">This query returns entities
			"Q06609" only in the 'xrefid' index field in XML </a></li>
		<li><a rel="example" href="search.json?q=Q06609&type=pathway">Search for
			Pathways containing "Q06609" (search all fields), return JSON</a></li>
		<li><a rel="example"
		       href='search?q=brca2&type=proteinreference&organism=homo%20sapiens&datasource=pid'>Search
			for ProteinReference entries that contain "brca2" keyword in any indexed field, return only human
			proteins from NCI Pathway Interaction Database</a></li>
		<li><a rel="example"
		       href="search.xml?q=name:'col5a1'&type=proteinreference&organism=9606">Similar to search above,
			but searches specifically in the "name" field</a></li>
		<li><a rel="example"
		       href="search?q=brc*&type=control&organism=9606&datasource=reactome">This query
			uses wildcard notation to match any Control interactions that has a word that starts with brca in any of
			its indexed fields. The results are restricted to human interactions from the Reactome database.</a></li>
		<li><a rel="example" href="search?q=a*&page=3">An example use of pagination -- This query returns the
			the forth page (page=3) for all elements that has an indexed word that starts with "a"</a></li>
		<li><a rel="example"
		       href="search?q=+binding%20NOT%20transcription*&type=control&page=0">This query finds Control
			interactions that contain the word "binding" but not "transcription" in their indexed fields, explicitly
			request the first page.</a></li>
		<li><a rel="example" href="search?q=pathway:immune*&type=conversion">This query will find all
			interactions that directly or indirectly participate in a pathway that has a keyword match for "immune"
			. </a></li>
		<li><a rel="example" href="search?q=*&type=pathway&datasource=reactome">This query will return
			all Reactome pathways</a></li>
		<li><a rel="example" href="search?q=*&type=biosource">This query will list all organisms,
			including secondary organisms such as pathogens or model organisms listed in the evidence or
			interaction objects</a></li>
	</ol>
</li>
<!-- get command -->
<li>
	<h2>
		<a id="get"></a>Command: GET
	</h2>

	<h3>Summary:</h3> This command retrieves full pathway information for a set of elements such as pathway,
	interaction or physical entity given the RDF IDs. Get commands only retrieve the BioPAX elements that are
	directly mapped to the ID. Use the <a href="#traverse">"traverse </a>query to traverse BioPAX graph and obtain
	child/owner elements.

	<h3>Parameters:</h3>
	<ul>
		<li><em>uri=</em> [Required] valid/existing BioPAX element's URI 
			(RDF ID; for utility classes that were "normalized", such as entity
			refereneces and controlled vocabularies, it is usually a
			Idntifiers.org URL. Multiple IDs are allowed per query, for
			example, 'uri=http://identifiers.org/uniprot/Q06609&amp;uri=http://identifiers.org/uniprot/Q549Z0'
			<a href="#miriam">See also</a> about MIRIAM and Identifiers.org.
		</li>
		<li><em>format=</em> [Optional] output format (<a
				href="#output_formats">values</a>)
		</li>
	</ul>
	<h3>Output:</h3> By default, a complete BioPAX representation for
	the record pointed to by the given URI is returned. Other output formats are produced by converting the BioPAX
	record on demand and can be specified by the optional format parameter. Please be advised that with some output
	formats it might return "no result found" error if the conversion is not applicable for the BioPAX result. 
	For example, BINARY_SIF output usually works if there are some interactions, complexes, or pathways in the retrieved set
	and not only physical entities.
	<h4>Query Examples:</h4>
	<br/>
	<ol>
		<li><a rel="example" href="get?uri=http://identifiers.org/uniprot/Q06609">
			This command returns the BioPAX representation of http://identifiers.org/uniprot/Q06609</a> 
			(<strong>ProteinReference</strong>)</li>
		<li><a rel="example" href="get?uri=COL5A1">
			This command returns Xref(s) in BioPAX format found by gene symbol COL5A1</a> 		
			<strong>Note:</strong> UniProt, RefSeq, NCBI Gene, and Ensemble identifiers ususally work here too 
			if these, or their corresponding primary UniProt accession, match at least one Xref.id BioPAX property value.</li>
	</ol>
</li>

<!-- graph command -->
<li>
	<h2>
		<a id="graph"></a>Command: GRAPH
	</h2>

	<h3>Summary:</h3> Graph searches are useful for finding connections and neighborhoods of elements.  such as the
	shortest path between two proteins or the neighborhood for a particular protein state or all states. Graph
	searches take detailed BioPAX semantics such as generics or nested complexes into account and traverse the graph
	accordingly. The starting points can be either physical entites or entity references. In the case of the latter
	the graph search starts from ALL the physical entities that belong to that particular entity references, i.e. all
	of its states.

	Note that we integrate BioPAX data from multiple databases based on our proteins and small molecules data warehouse
	and consistently normalize UnificationXref, EntityReference, Provenance, BioSource, and ControlledVocabulary
	objects when we are absolutely sure that two objects of the same type are equivalent. We, however, do not merge
	physical entities and reactions from different sources as matching and aligning pathways at that level is still an
	open research problem. As a result, graph searches can return several similar but disconnected sub-networks that 
	correspond to the pathway data from different providers (though some physical entities often refer to the 
	same small molecule or protein reference or controlled vocabulary).
	<h3>Parameters:</h3>
	<ul>
		<li><em>kind=</em> [Required] graph query (<a
				href="#graph_kinds">values</a>)
		</li>
		<li><em>source=</em> [Required] source object's URI/ID. Multiple source URIs/IDs are allowed per query, for example
			'source=http://identifiers.org/uniprot/Q06609&amp;source=http://identifiers.org/uniprot/Q549Z0'.
			See <a href="#miriam">a note about MIRIAM and Identifiers.org</a>.
		</li>
		<li><em>target=</em> [Required for PATHSFROMTO graph query]
			target URI/ID. Multiple target URIs are allowed per query; for
			example 'target=http://identifiers.org/uniprot/Q06609&amp;target=http://identifiers.org/uniprot/Q549Z0'.
			See <a href="#miriam">a note about MIRIAM and Identifiers.org</a>.
		</li>
		<li><em>direction=</em> [Optional, for NEIGHBORHOOD and COMMONSTREAM algorithms] - graph search direction (<a
				href="#graph_directions">values</a>).
		</li>
		<li><em>limit=</em> [Optional] graph query search distance limit (default = 1).
		</li>
		<li><em>format=</em> [Optional] output format (<a
				href="#graph_formats">values</a>)
		</li>
		<li><em>datasource=</em> [Optional] datasource filter (same as for <a href="#search_parameters">'search'</a>).
		</li>
		<li><em>organism=</em> [Optional] organism filter (same as for <a href="#search_parameters">'search'</a>).
		</li>
	</ul>
	<h3>Output:</h3> By default, graph queries return a complete BioPAX representation of the
	subnetwork matched by the algorithm. Other output formats are available as specified
	by the optional format parameter. Please be advised that some output
	format choices might cause "no result found" error if the conversion is not applicable for the BioPAX result 
	(e.g., BINARY_SIF output fails if there are no interactions, complexes, nor pathways in the retrieved set).
	<h3>Query Examples:</h3> Neighborhood of COL5A1 (P20908,
	CO5A1_HUMAN): <br/>
	<ol>
		<li><a rel="example" href="graph?source=http://identifiers.org/uniprot/P20908&kind=neighborhood&format=EXTENDED_BINARY_SIF">
			This query finds the BioPAX nearest neighborhood of the protein reference</a> http://identifiers.org/uniprot/P20908, i.e., 
			all reactions where the corresponding protein forms participate; returned in the Simple Interaction Format (SIF)</li>	
		<li><a rel="example" href="graph?source=P20908&kind=neighborhood">
			This query finds the 1 distance neighborhood of P20908</a> - starting from the corresponding Xref, 
			finds all reactions that its oners (e.g., a protein reference) and their states (protein forms) 
			participate in, and returns the BioPAX model.</li>		
		<li><a rel="example" href="graph?source=COL5A1&kind=neighborhood">
			A similar query using the gene symbol COL5A1 instead of URI or UniProt ID</a> 
			(this also implies internal id-mapping to primary UniProt IDs). Compared with above examples, 
			particularly the first one, a query like this potentially returns a larger subnetwork, for
			it possibly starts its graph traversing from several unification and relationship Xrefs 
			rather than from the ProteinReference (http://identifiers.org/uniprot/P20908).
			One can mix: submit URI along with UniProt accession, RefSeq ID, NCBI Gene ID and Ensemble IDs
			in a single /graph or /get query; other identifiers might also work, by chance (if present 
			in the db).
		</li>
	</ol>
</li>

<!-- traverse command -->
<li>
	<h2>
		<a id="traverse"></a>Command: TRAVERSE
	</h2>

	<h3>Summary:</h3> This command provides XPath-like access to the PC. With travers users can
	explicitly state the paths they would like to access.
	The format of the path query is in the form:
	<em>[Initial Class]/[property1]:[classRestriction(optional)]/[property2]...</em>
	A "*" sign after the property instructs path accessor to transitively
	traverse that property.

	For example, the following path accessor will traverse through all physical entity components within a complex:<br/>
	"Complex/component*/entityReference/xref:UnificationXref"<br/>
	
	The following will list display names of all participants of interactions, 
	which are components (<em>pathwayComponent</em>) of a pathway (note: 
	<em>pathwayOrder</em> property, where same or other interactions can be reached,
	is not considered here):<br/>
	"Pathway/pathwayComponent:Interaction/participant*/displayName"<br/>

	The optional parameter <em>classRestriction</em> allows to restrict/filter the 
	returned property values to a certain subclass of the range of that property.
	In the first example above, this is used to get only the Unification Xrefs. 

	<a href="http://www.biopax.org/paxtools/apidocs/org/biopax/paxtools/controller/PathAccessor.html">
	Path accessors</a> can use all the official BioPAX properties as well as additional derived classes and parameters in
	paxtools such as inverse parameters and interfaces that represent anonymous union classes in OWL. 
	(See <a href="http://www.biopax.org/paxtools/">Paxtools documentation</a> for more details).
	<h3>Parameters:</h3>
	<ul>
		<li><em>uri=</em> [Required] a BioPAX element URI - specified similarly to the
			<a href="#get">'GET' command above</a>). Multiple IDs are
			allowed (uri=...&amp;uri=...&amp;uri=...).
		</li>
		<li><em>path=</em> [Required] a BioPAX propery path in the form of
			property1[:type1]/property2[:type2];  see <a href="#biopax_properties">properties</a>,
			<a href="#biopax_inverse_properties">inverse properties</a>, <a href="http://www.biopax.org/paxtools">Paxtools</a>,
			<a href="http://www.biopax.org/paxtools/apidocs/org/biopax/paxtools/controller/PathAccessor.html">
			org.biopax.paxtools.controller.PathAccessor</a>.
		</li>
	</ul>
	<h3>Output:</h3> XML result that follows the <a
		href="help/schema">Search Response XML
	Schema</a>&nbsp;(TraverseResponse type; pagination is disabled: returns
	all values at once)<br/>
	<h3>Query Examples:</h3>
	<ol>
		<li><a rel="example"
		       href="traverse?uri=http://identifiers.org/uniprot/P38398&path=ProteinReference/organism/displayName">
			This query returns the display name of the organism of the ProteinReference specified by the URI.</a></li>
		<li><a rel="example"
		       href="traverse?uri=http://identifiers.org/uniprot/P38398&uri=http://identifiers.org/uniprot/Q06609&path=ProteinReference/organism">
			This query returns the URI of the organism for each of the Protein References</a></li>
		<li><a rel="example"
		       href="traverse?uri=http://identifiers.org/uniprot/Q06609&path=ProteinReference/entityReferenceOf:Protein/name">
			This query returns the names of all states of RAD51 protein (by its ProteinReference URI, using
			property path="ProteinReference/entityReferenceOf:Protein/name")</a></li>
		<li><a rel="example"
		       href="traverse?uri=http://identifiers.org/uniprot/P38398&path=ProteinReference/entityReferenceOf:Protein">
			This query returns the URIs of states of BRCA1_HUMAN</a></li>
		<li><a rel="example"
		       href="traverse?uri=http://identifiers.org/uniprot/P38398&uri=http://identifiers.org/taxonomy/9606&path=Named/name">
			This query returns the names of several different objects (using abstract type 'Named' from Paxtools
			API)</a></li>
	
	</ol>
	
</li>

<!-- top_pathways command -->
<li>
	<h2>
		<a id="top_pathways"></a>Command: TOP_PATHWAYS
	</h2>

	<h3>Summary:</h3> This command returns all "top" pathways -- pathways that are neither
	'controlled' nor 'pathwayComponent' of another process.
	<h3>Parameters:</h3>
	<ul>
		<li><em>datasource=</em> [Optional] filter by data source (same as for <a href="#search_parameters">'search'</a>).
		</li>
		<li><em>organism=</em> [Optional] organism filter (same as for <a href="#search_parameters">'search'</a>).
		</li>
	</ul>	
	<h3>Output:</h3> XML result that follows the <a
		href="help/schema"> Search Response XML
	Schema</a>&nbsp;(SearchResponse type; pagination is disabled: returns
	all pathways at once)<br/>
	<h4>Query Examples:</h4>
	<ol>
		<li><a href="top_pathways"> get top/root pathways (XML)</a></li>
		<li><a href="top_pathways.json"> get top/root pathways in JSON format</a></li>
	</ol>
</li>

<!-- idmapping command -->
<li>
	<h2><a id="idmapping"></a>IDMAPPING</h2>

	<h3>Summary:</h3> Unambiguously maps, e.g., HGNC gene symbols, NCBI Gene, RefSeq, ENS*, and
	secondary UniProt identifiers to the primary UniProt accessions, or -
	ChEBI and PubChem IDs to primary ChEBI. You can mix different standard ID types in one query.
	NOTE: This is a specific id-mapping (not general-purpose) for reference proteins and small molecules;
	it was first designed for internal use, such as to improve BioPAX data integration and allow for graph
	queries accept not only URIs but also standard IDs. The mapping tables were derived
	exclusively from Swiss-Prot (DR fields) and ChEBI data (manually created tables and other mapping types and
	sources can be added in the future versions if necessary).
	<h3>Output:</h3> JSON (serialized Map)
	<h4>Examples:</h4> <br/>
	<ol>
		<li><a rel="example" href="idmapping?id=BRCA2&id=TP53">/idmapping?id=BRCA2&amp;id=TP53</a></li>
	</ol>
</li>
</ol>
<br/>
</section>
<section id="parameters_description_section">
	<h2>Organisms</h2>

	<div class="parameters" id="organisms">
		<h3>Officially supported organisms</h3>

		<p>Having the above data sources, we chose to integrate
			all the pathway data files only for the following species:</p>
		<ul>
			<c:forEach var="org" items="${cpath.organisms}">
				<em><strong><c:out value="${org}"/></strong></em>
			</c:forEach>
		</ul>
		<p>There are still other organisms associated with some BioPAX elements too,
			because original pathway data might contain disease pathways, other lab experiment details,
			use generics (i.e., wildcard proteins), etc. We did not specially clean or convert such data.
			You can find all organisms by using <a href="search?q=*&type=biosource">search for all BioSource objects</a>.
		</p>
	</div>

	<!-- additional parameter details -->
	<h2 id="additional_parameters">Query Parameter Values</h2>

	<div class="parameters" id="output_formats">
		<h3>Output Formats ('format'):</h3>

		<p>
			See also <a href="help/formats.html">output formats.</a>
		</p>
		<!-- items are to be added here by a javascript -->
		<ul id="formats"></ul>
		<br/>
	</div>

	<div class="parameters" id="graph_kinds">
		<h3>Built-in graph queries ('kind'):</h3>
		<!-- items are to be added here by a javascript -->
		<ul id="kinds"></ul>
		<br/>
	</div>

	<div class="parameters" id="graph_directions">
		<h3>Graph traversal directions ('direction'):</h3>
		<!-- items are to be added here by a javascript -->
		<ul id="directions"></ul>
		<br/>
	</div>

	<div class="parameters" id="biopax_types">
		<h3>BioPAX classes ('type'):</h3>

		<p><a href="javascript:switchit('types')">Click here</a>
			to show/hide the list</p>
		<!-- items are to be added here by a javascript -->
		<ul id="types" style="display: none;"></ul>
		<br/>
	</div>

	<div class="parameters" id="biopax_properties">
		<h3>Official BioPAX Properties and Domain/Range Restrictions:</h3>

		<p>Note: "XReferrable xref Xref
			D:ControlledVocabulary=UnificationXref
			D:Provenance=UnificationXref,PublicationXref" means
			XReferrable.xref, and that, for a ControlledVocabulary.xref, the
			value can only be of UnificationXref type, etc.</p>

		<p><a href="javascript:switchit('properties')">Click here</a>
			to show/hide the list of properties</p>
		<!-- items are to be added here by a javascript -->
		<ul id="properties" style="display: none;"></ul>
		<br/>
	</div>

	<div class="parameters" id="biopax_inverse_properties">
		<h3>Inverse BioPAX Object Properties and Domain/Range
			Restrictions (useful feature of Paxtools API):</h3>

		<p>Note: Some of object range BioPAX properties can be traversed in the
			inverse direction e.g, 'xref' - 'xrefOf'. These are listed below. But, e.g., unlike
			the normal xref property, the same restriction ("XReferrable xref
			Xref D:ControlledVocabulary=UnificationXref
			D:Provenance=UnificationXref,PublicationXref") must read/comprehend
			differently: it's actually now means Xref.xrefOf, and that
			RelationshipXref.xrefOf cannot contain a ControlledVocabulary (or
			its sub-class) values, etc.</p>

		<p><a href="javascript:switchit('inverse_properties')">Click here</a>
			to show/hide the list of properties</p>
		<!-- items are to be added here by a javascript -->
		<ul id="inverse_properties" style="display: none;"></ul>
		<br/>
	</div>

	<!-- error codes -->
	<h2 id="errors">Errors:</h2>

	<p>
		If an error or no results happens while processing a user's request,
		the client will receive a HTTP response with error status code and message
		(then browsers usually display a error page sent by the server; clients normally
		check the status before further processing the results, if any.)
	</p>
	<br/>
</section>
</div>
<jsp:include page="footer.jsp"/>

</body>
</html>
