<%@taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core"%>
<%@page language="java" contentType="text/html; charset=UTF-8"%>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt"%>
<!DOCTYPE html>
<html>
<head>
<jsp:include page="head.jsp" />
<title>Downloads</title>
</head>
<body>
	<jsp:include page="header.jsp" />
<!-- 	  <div class="row"> -->
		<div class="jumbotron">
			<h2>Batch Downloads</h2>
			<blockquote><p>
				Data files listed below are auto-generated by this system from the integrated BioPAX database. 
				They are sorted alphabetically and generally named as follows:</p>
				<code>${cpath.name}.${cpath.version}.&lt;SOURCE&gt;.&lt;FORMAT&gt;.&lt;ext&gt;.gz</code>
				<p>(Other versions can be downloaded from <a href="${cpath.url}">${cpath.name}</a> archives.)</p>
			</blockquote>
			<em>&lt;SOURCE&gt;</em> is a standard name, taxonomy ID, or 'All'<br/>
			<em>&lt;FORMAT&gt;</em> is one of the <a href="formats">Output Formats</a><br/> 
			<em>&lt;ext&gt;</em> is the file type.
		</div>
<!-- 	  </div> -->
		
		<h3>Notes</h3>
		<ul>
			<li>See the <a href="formats">output formats description</a></strong> for more information
				about each format. Custom SIF output (e.g., to add/remove specific columns or interaction types) 
				can be generated by request or by using <a href="http://www.biopax.org/paxtools">Paxtools</a>, 
				using one of the BioPAX models and blacklist.txt file to filter ubiquitous small molecules.</li>
			<li>Archives by source - each is generated by searching for all BioPAX Entities 
				associated with the given organism or data source and making a new BioPAX sub-model, 
				which is then converted to various formats.</li>
			<li><strong>Detailed_Process_Data file</strong> is an additional BioPAX archive that integrates data 
				only from the BioPAX data sources (i.e, excluding those converted from PSI-MI format).</li>				
			<li>blacklist.txt is used by the BioPAX to SIF and SBGN converters to exclude ubiquitous small molecules from output. 
				See <a href="http://code.google.com/p/biopax-pattern/wiki/UsingBinaryInteractionFramework#Blacklisting_ubiquitous_small_molecules" 
				target="_blank">blacklist.txt file description</a> for more information.</li>
			<li>datasources.txt provides metadata and statistics about each data source.</li>				
			<li>Validation - full <a rel="nofollow" href='<c:url value="/validations"/>'>BioPAX validation reports</a> 
				(XML formatted) are available for each data source loaded into this databases. 
				We expect these to be mainly useful to data providers to help check the accuracy of their data.</li>
			<li>Original data files used to build this system <a rel="nofollow" href='<c:url value="/datadir"/>'>are available</a> 
				at various intermediate states, e.g., after cleaning, conversion, normalization, and before merging.</li>
		</ul>
		
		<h3>Files</h3>
		<ul>
			<c:forEach var="f" items="${files}">
				<li><a rel="nofollow" href='<c:url value="/downloads/${f.key}"/>'>${f.key}</a>&nbsp;(${f.value})</li>
			</c:forEach>
		</ul>

	<jsp:include page="footer.jsp" />
</body>
</html>
