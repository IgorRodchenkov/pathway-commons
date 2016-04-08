<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page language="java" contentType="text/html; charset=UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<!DOCTYPE html>
<html>
<head>
<jsp:include page="head.jsp" />
<title>Downloads</title>
</head>
<body>
	<jsp:include page="header.jsp" />
		<div class="jumbotron">
			<h2>Batch Downloads</h2>
			<blockquote><p>
				<p>Current and old version pathway data archives in BioPAX, SIF and GMT <a href="formats">formats</a>,
				and more, can be downloaded <a rel="nofollow" href="${cpath.downloadsUrl}">from here</a>.
				</p>
			</blockquote>
			<h3>What you find there</h3>
			<ul>
				<li>Data archives there have names like
					<code>${cpath.name}.${cpath.version}.&lt;source&gt;.&lt;FORMAT&gt;.&lt;ext&gt;.gz</code>
					, where &lt;source&gt; is either identifier (part of the provenance URI) or
					'All' (all the data merged), or 'Detailed' (pathway data only; PSI-MI data were removed),
					or 'Warehouse' (only entity reference objects, xrefs, vocabularies; no pathways/interactions).
				</li>
				<li>With <a href="http://www.biopax.org/paxtools">Paxtools</a>,
					we can generate a BioPAX sub-model and convert it to a text format on your special request:
					select data sources, add extra columns (extended SIF format) or description (GSEA/GMT),
					implement new or filter existing binary interaction types (SIF inference rules) and output ID type.
				</li>
				<li>Also, original and intermediate (cleaned, converted, normalized) data archives
					<a rel="nofollow" href="datadir">are available here</a>.</li>
				<li>BioPAX <a href="http://www.biopax.org/validator">Validator</a> reports
					for each pathway/interaction data source in the system can be found
					<a rel="nofollow" href="validations">here</a>.</li>
				<li>blacklist.txt is used by the BioPAX to SIF and to SBGN converters to exclude ubiquitous small molecules.
					See also: <a href="https://github.com/BioPAX/Paxtools/wiki/PatternBinaryInteractionFramework"
					target="_blank">Using Binary Interaction Framework</a> for more information (contains blacklist.txt description).</li>
				<li>datasources.txt provides metadata and some statistics about each data source.</li>
				<li>pathways.txt - pathways and corresponding sub-pathways (URIs, names, source, etc.)</li>
			</ul>
		</div>
	<jsp:include page="footer.jsp" />
</body>
</html>
