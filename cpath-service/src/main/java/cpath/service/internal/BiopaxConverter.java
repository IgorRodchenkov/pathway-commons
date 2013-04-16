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


package cpath.service.internal;

import java.io.*;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.paxtools.io.gsea.GSEAConverter;
import org.biopax.paxtools.io.sbgn.L3ToSBGNPDConverter;
import org.biopax.paxtools.io.sbgn.ListUbiqueDetector;
import org.biopax.paxtools.io.sif.InteractionRule;
import org.biopax.paxtools.io.sif.SimpleInteractionConverter;
import org.biopax.paxtools.io.*;
import org.biopax.paxtools.model.*;
import org.springframework.core.io.Resource;

import cpath.service.jaxb.DataResponse;
import cpath.service.jaxb.ServiceResponse;
import cpath.service.ErrorResponse;
import cpath.service.OutputFormat;
import static cpath.service.Status.*;

import javax.xml.bind.JAXBException;

/**
 * A utility class to convert a BioPAX 
 * L3 RDF/XML data stream or {@link Model} 
 * to one of {@link OutputFormat}s  
 * (including - to BioPAX L3 RDF/XML)
 * 
 * @author rodche
 */
public class BiopaxConverter {
	private static final Log log = LogFactory.getLog(BiopaxConverter.class);
	
	private final Set<String> blacklist;
	
    /**
     * Constructor.
     * 
     * @param blacklistResource a file resource, list of ubiquitous molecules to exclude (in some algorithms)
     * 
     * @throws IOException 
     * @throws  
     */
	public BiopaxConverter(Resource blacklistResource) throws IOException 
	{
		if(blacklistResource != null && blacklistResource.exists()) {
			Scanner scanner = new Scanner(blacklistResource.getFile());
			blacklist = new HashSet<String>();
			while(scanner.hasNextLine())
				blacklist.add(scanner.nextLine().trim());
			scanner.close();
			if(log.isInfoEnabled())
				log.info("Successfully loaded " + blacklist.size()
				+ " URIs for a (graph queries) 'blacklist' resource: " 
				+ blacklistResource.getDescription());
		} else {
			this.blacklist = null;
			log.warn("'blacklist' file is not used (" + 
				((blacklistResource == null) ? "not provided" 
					: blacklistResource.getDescription()) + ")");
		}
	}

	
	/**
	 * Constructor.
	 * 
	 * @param blacklist set of ubiquitous molecules to exclude (in some algorithms)
	 */
	public BiopaxConverter(Set<String> blacklist)
	{
		this.blacklist = blacklist;
	}


	/**
	 * For the given BioPAX data stream, converts it to the 
	 * desired output format.
	 *
	 * @param biopax
	 * @param format
	 * @return
	 */
    public ServiceResponse convert(InputStream biopax, OutputFormat format) {
        Model model = (new SimpleIOHandler()).convertFromOWL(biopax);
        if(model != null && !model.getObjects().isEmpty()) {
            return convert(model, format);
        } else {
        	return new ErrorResponse(NO_RESULTS_FOUND, "Empty BioPAX Model.");
        }
	}

    
	/**
     * Converts the BioPAX data into the other format.
     * 
     * @param m paxtools model
     * @param format output format
     * 
     * @return data response with the converted data or {@link ErrorResponse}.
     */
    public ServiceResponse convert(Model m, OutputFormat format, Object... args) 
    {
    	if(m == null || m.getObjects().isEmpty()) {
			return new ErrorResponse(NO_RESULTS_FOUND, "Empty BioPAX Model returned.");
		}

		// otherwise, do convert (it's a DataResponse)
    	String data = null;
    	try {
			switch (format) {
			case BIOPAX: //to OWL
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				new SimpleIOHandler().convertToOWL(m, baos);
				data = baos.toString();
				break;
			case BINARY_SIF:
				data = convertToBinarySIF(m, false);
				break;
			case EXTENDED_BINARY_SIF:
				data = convertToBinarySIF(m, true);
				break;
			case GSEA:
				data = convertToGSEA(m, "uniprot");
				break;
            case SBGN:

				boolean doLayout = true;
				if (args != null && args.length > 0 && args[0] instanceof Boolean)
					doLayout = (Boolean) args[0];
				
                data = convertToSBGN(m, blacklist, doLayout);
                break;
			default: //to BioPAX OWL
			}
			
			DataResponse dataResponse = new DataResponse();
			dataResponse.setData(data);
			return dataResponse;
		}
        catch (Exception e) {
        	return new ErrorResponse(INTERNAL_ERROR, e);
		}
    }


    /**
     * Converts a BioPAX Model to SBGN format.
     *
     * @param m
     * @return
     * @throws IOException
     */
    String convertToSBGN(Model m, Set<String> blackList, boolean doLayout)
		throws IOException, JAXBException
	{
		L3ToSBGNPDConverter converter = new L3ToSBGNPDConverter(
			new ListUbiqueDetector(blackList), null, doLayout);

		OutputStream stream = new ByteArrayOutputStream();
		converter.writeSBGN(m, stream);
        return stream.toString();
    }


    /**
	 * Converts service results that contain 
	 * a not empty BioPAX Model to GSEA format.
	 * 
     * @param m paxtools model
	 * @param outputIdType output identifiers type (db name)
	 * @return
	 * @throws IOException 
	 */
	String convertToGSEA(Model m, String outputIdType) throws IOException 
	{	
		// convert, replace DATA
		GSEAConverter gseaConverter = new GSEAConverter(outputIdType, true);
		OutputStream stream = new ByteArrayOutputStream();
		gseaConverter.writeToGSEA(m, stream);
		return stream.toString();
	}

	
	/**
	 * Converts a not empty BioPAX Model (contained in the service bean) 
	 * to SIF data format.
	 * 
	 * TODO 'rules' parameter is currently ignored (requires conversion 
	 * from strings to the rules, e.g., using enum. BinaryInteractionRule from cpath-web-service)
	 * 
     * @param m biopax paxtools to convert
     * @param extended if true, call SIFNX else SIF
	 * @return
	 * @throws IOException 
	 */
	String convertToBinarySIF(Model m, boolean extended) throws IOException 
	{
		// convert, replace DATA value in the map to return
		// TODO match 'rules' parameter to rule types (currently, it uses all)
		SimpleInteractionConverter sic = new SimpleInteractionConverter(
                new HashMap(), blacklist,
                SimpleInteractionConverter.getRules(BioPAXLevel.L3).toArray(new InteractionRule[]{})
			);
		OutputStream edgeStream = new ByteArrayOutputStream();
		if (extended) {
			OutputStream nodeStream = new ByteArrayOutputStream();
			convertToExtendedBinarySIF(m, edgeStream, nodeStream);
			// join two files together, one after another -
			return edgeStream + "\n\n" + nodeStream;
		} else {
			sic.writeInteractionsInSIF(m, edgeStream);
			return edgeStream.toString();
		}
	}

	
    /**
     * Converts a BioPAX model to the cPath2 Extended Binary SIF format
     * using two output streams, - edges and nodes.
     * 
     * @param model
     * @param edgeStream
     * @param nodeStream
     * @throws IOException
     */
	public void convertToExtendedBinarySIF(Model m, OutputStream edgeStream, OutputStream nodeStream) throws IOException 
	{
		final String edgeColumns = "PARTICIPANT_A\tINTERACTION_TYPE\tPARTICIPANT_B\tINTERACTION_DATA_SOURCE\tINTERACTION_PUBMED_ID\n";
		edgeStream.write(edgeColumns.getBytes());
		final String nodeColumns = "PARTICIPANT\tPARTICIPANT_TYPE\tPARTICIPANT_NAME\tUNIFICATION_XREF\tRELATIONSHIP_XREF\n";	
		nodeStream.write(nodeColumns.getBytes());
		
		SimpleInteractionConverter sic = new SimpleInteractionConverter(
                new HashMap(), blacklist,
                SimpleInteractionConverter.getRules(BioPAXLevel.L3).toArray(new InteractionRule[]{})
		);
		
		sic.writeInteractionsInSIFNX(
			m, edgeStream, nodeStream,
			Arrays.asList("EntityReference/displayName", "EntityReference/xref:UnificationXref", "EntityReference/xref:RelationshipXref"),
			Arrays.asList("Interaction/dataSource/displayName", "Interaction/xref:PublicationXref"), true);
	}

}