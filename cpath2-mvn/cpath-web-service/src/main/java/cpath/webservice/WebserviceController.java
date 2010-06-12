/**
 ** Copyright (c) 2010 Memorial Sloan-Kettering Cancer Center (MSKCC)
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

package cpath.webservice;

import cpath.dao.PaxtoolsDAO;
import cpath.warehouse.internal.BioDataTypes;
import cpath.warehouse.internal.BioDataTypes.Type;
import cpath.webservice.args.BinaryInteractionRule;
import cpath.webservice.args.PathwayDataSource;
import cpath.webservice.args.ProtocolVersion;
import cpath.webservice.args.Cmd;
import cpath.webservice.args.GraphType;
import cpath.webservice.args.OutputFormat;
import cpath.webservice.args.binding.BinaryInteractionRuleEditor;
import cpath.webservice.args.binding.BiopaxTypeEditor;
import cpath.webservice.args.binding.CmdEditor;
import cpath.webservice.args.binding.PathwayDataSourceEditor;
import cpath.webservice.args.binding.GraphTypeEditor;
import cpath.webservice.args.binding.OutputFormatEditor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.io.simpleIO.SimpleExporter;
import org.biopax.paxtools.model.BioPAXElement;
import org.biopax.paxtools.model.Model;
import org.bridgedb.DataSource;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.util.*;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;

/**
 * cPathSquared Main Web Service.
 * 
 * @author rodche
 */
@Controller
public class WebserviceController {
    private static final Log log = LogFactory.getLog(WebserviceController.class);
    private static String newline = System.getProperty("line.separator");
    @NotNull
	private PaxtoolsDAO pcDAO;
	@NotNull
	private SimpleExporter exporter;
    
    @PostConstruct
    void init() {
    	// re-build Lucene index (TODO is this required?)
    	//pcDAO.createIndex();
    }
    

	public WebserviceController(PaxtoolsDAO mainDAO, SimpleExporter exporter) {
		this.pcDAO = mainDAO;
		this.exporter = exporter;
	}

	
	/**
	 * Customizes request strings conversion to internal types,
	 * e.g., "network of interest" is recognized as GraphType.NETWORK_OF_INTEREST, etc.
	 * 
	 * @param binder
	 */
	@InitBinder
    public void initBinder(WebDataBinder binder) {
        binder.registerCustomEditor(OutputFormat.class, new OutputFormatEditor());
        binder.registerCustomEditor(GraphType.class, new GraphTypeEditor());
        binder.registerCustomEditor(BioPAXElement.class, new BiopaxTypeEditor());
        binder.registerCustomEditor(PathwayDataSource.class, new PathwayDataSourceEditor());
        binder.registerCustomEditor(Cmd.class, new CmdEditor());
        binder.registerCustomEditor(BinaryInteractionRule.class, new BinaryInteractionRuleEditor());
    }
	
	
	/**
	 * List of formats that web methods return
	 * 
	 * @return
	 */
    @RequestMapping("/formats")
    @ResponseBody
    public String getFormats() {
    	StringBuffer toReturn = new StringBuffer();
    	for(OutputFormat f : OutputFormat.values()) {
    		toReturn.append(f.toString().toLowerCase()).append(newline);
    	}
    	return toReturn.toString();
    }
    
    
    //=== Web methods that help understand BioPAX model (and rules) ===
    
	/**
	 * List of BioPAX L3 Classes
	 * 
	 * @return
	 */
    @RequestMapping("/types")
    @ResponseBody
    public String getBiopaxTypes() {
    	StringBuffer toReturn = new StringBuffer();
    	for(String type : BiopaxTypeEditor.getTypesByName().keySet()) {
    		toReturn.append(type).append(newline);
    	}
    	return toReturn.toString();
    }

    
    //=== Web methods that list all BioPAX element or - by type ===
    
    /*
     * TODO all objects?.. This might be too much to ask :)
     */
    @RequestMapping(value="/all/elements")
    @ResponseBody
    public String getElements() throws IOException {
    	return getElementsOfType(BioPAXElement.class);
    }

    
    @RequestMapping(value="/types/{type}/elements")
    @ResponseBody
    public String getElementsOfType(@PathVariable("type") Class<? extends BioPAXElement> type) {
    	StringBuffer toReturn = new StringBuffer();
    	/* getObjects with eager=false, statless=false is ok for RDFIds only...
    	 * - no need to detach elements from the DAA session
    	 */
    	Set<? extends BioPAXElement> results = pcDAO.getElements(type, false); 
    	for(BioPAXElement e : results)
    	{
    		toReturn.append(e.getRDFId()).append(newline);
    	}
    	return toReturn.toString();
    }
    
     
    //=== Most critical web methods that get one element by ID (URI) ===//

    
    @RequestMapping(value="/elements")
    @ResponseBody
    public String elementById(@RequestParam("uri") String uri) {
    	if(log.isInfoEnabled()) log.info("POST Query /elements");
    	return elementById(OutputFormat.BIOPAX, uri);
    }

    
    @RequestMapping(value="/format/{format}/elements")
    @ResponseBody
    public String elementById(@PathVariable("format") OutputFormat format, 
    		@RequestParam("uri") String uri) 
    {
    	BioPAXElement element = pcDAO.getElement(uri, true); 
    	element = pcDAO.detach(element);

		if(log.isInfoEnabled()) log.info("Query - format:" + format + 
				", urn:" + uri + ", returned:" + element);

		String owl = toOWL(element);		
		return owl;
    }
    
    
    //=== Fulltext search web methods ===//
    
	@RequestMapping(value="/find/{query}")
	@ResponseBody
    public String fulltextSearch(@PathVariable("query") String query) {
		return fulltextSearchForType(BioPAXElement.class, query);
	}
        

    @RequestMapping(value="/types/{type}/find/{query}")
    @ResponseBody
    public String fulltextSearchForType(
    		@PathVariable("type") Class<? extends BioPAXElement> type, 
    		@PathVariable("query") String query) 
    {		
    	if(log.isInfoEnabled()) log.info("Fulltext Search for type:" 
				+ type.getCanonicalName() + ", query:" + query);
    	
    	// do search
		List<BioPAXElement> results = (List<BioPAXElement>) pcDAO.search(query, type);
		StringBuffer toReturn = new StringBuffer();
		for(BioPAXElement e : results) {
			toReturn.append(e.getRDFId()).append(newline);
		}
		
		return toReturn.toString(); 
	}
    
	
    // TODO later, remove "method = RequestMethod.POST" and the test form method below
	@RequestMapping(value="/graph", method = RequestMethod.POST)
	@ResponseBody
    public String graphQuery(@RequestBody MultiValueMap<String, String> formData)
    {
		if(log.isInfoEnabled()) log.info("GraphQuery format:" 
				+ formData.get("format") + ", kind:" + formData.get("kind") 
				+ ", source:" + formData.get("source") 
				+ ", dest:" + formData.get("dest"));
		
		StringBuffer toReturn = new StringBuffer("Not Implemented Yet :)" 
				+ "GraphQuery format:" + formData.get("format") + 
				", kind:" + formData.get("kind") + ", source:" 
				+ formData.get("source") + ", dest:" 
				+ formData.get("dest"));
		
		return toReturn.toString(); 
	}
	
	
	// temporary - for testing
	@RequestMapping(value="/graph", method = RequestMethod.GET)
    public void testForm() {}
	
	
	/**
	 * List of bio-network data sources.
	 * 
	 * @return
	 */
    @RequestMapping("/datasources")
    @ResponseBody
    public String getDatasources() {
    	StringBuffer toReturn = new StringBuffer();
    	for(DataSource ds : BioDataTypes.getDataSources(Type.PATHWAY_DATA)) {
    		String code = ds.getSystemCode();
    		toReturn.append(code).append(newline);
    	}
    	return toReturn.toString();
    }	
	
    
    /**
     * Mapping and controller for the legacy cPath web services
     * (for backward compatibility).
     */
    @RequestMapping("/webservice.do")
    @ResponseBody
    public String doWebservice(
    		@RequestParam("cmd") @NotNull Cmd cmd, 
    		@RequestParam(value="version", required=false) String version,
    		@RequestParam("q") String q,
    		@RequestParam(value="output", required=false) @NotNull OutputFormat output,
    		@RequestParam(value="organism", required=false) @NotNull Integer organism,
    		@RequestParam(value="input_id_type", required=false) String inputIdType,
    		@RequestParam(value="data_source", required=false) @NotNull PathwayDataSource dataSource,
    		@RequestParam(value="output_id_type", required=false) String outputIdType,
    		@RequestParam(value="binary_interaction_rule", required=false) @NotNull BinaryInteractionRule rule,
    		BindingResult result
    	) 
    {
    	String toReturn = "";
    	
    	// TODO check individual parameters validation result (BindingResult); and return only the first error?..
    	
    	
    	// TODO continue to validate using ProtocolRequest and ProtocolValidator....
    	
    	
    	return toReturn;
    }

    
    /*========= private staff ==============*/
	

	private String toOWL(BioPAXElement element) {
		if(element == null) return "NOTFOUND"; // temporary
		
		StringWriter writer = new StringWriter();
		try {
			exporter.writeObject(writer, element);
		} catch (IOException e) {
			log.error(e);
		}
		return writer.toString();
	}
	
	
	private String toOWL(Model model) {
		if(model == null) return null;
		
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			exporter.convertToOWL(model, out);
		} catch (IOException e) {
			log.error(e);
		}
		return out.toString();
	}

}