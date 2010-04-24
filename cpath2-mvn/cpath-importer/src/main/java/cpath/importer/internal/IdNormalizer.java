/**
 ** Copyright (c) 2009 Memorial Sloan-Kettering Cancer Center (MSKCC)
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

package cpath.importer.internal;

import java.io.*;
import java.net.URLEncoder;
import java.util.*;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.biopax.miriam.MiriamLink;
import org.biopax.paxtools.io.BioPAXIOHandler;
import org.biopax.paxtools.io.simpleIO.SimpleExporter;
import org.biopax.paxtools.io.simpleIO.SimpleReader;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.BioSource;
import org.biopax.paxtools.model.level3.ControlledVocabulary;
import org.biopax.paxtools.model.level3.EntityReference;
import org.biopax.paxtools.model.level3.Provenance;
import org.biopax.paxtools.model.level3.UnificationXref;
import org.biopax.paxtools.model.level3.UtilityClass;
import org.biopax.paxtools.model.level3.XReferrable;
import org.biopax.paxtools.model.level3.Xref;
import org.biopax.paxtools.util.ClassFilterSet;

import cpath.importer.Normalizer;

/**
 * @author rodch
 *
 */
public class IdNormalizer implements Normalizer {
	private static final Log log = LogFactory.getLog(IdNormalizer.class);
	public static final String BIOPAX_URI_PREFIX = "http://biopax.org/"; // for xrefs
	
	
	private MiriamLink miriam;
	private BioPAXIOHandler biopaxReader;
	

	/**
	 * Constructor
	 */
	public IdNormalizer(MiriamLink miriam) {
		this.miriam = miriam;
		this.biopaxReader = new SimpleReader(); //may be to use 'biopaxReader' bean that uses (new BioPAXFactoryForPersistence(), BioPAXLevel.L3);
	}

	
	/* (non-Javadoc)
	 * @see cpath.importer.Normalizer#normalize(String)
	 */
	public String normalize(String biopaxOwlData) {
		// build the model
		Model model = biopaxReader.convertFromOWL(new ByteArrayInputStream(biopaxOwlData.getBytes()));
		if(model == null || model.getLevel() != BioPAXLevel.L3) {
			throw new IllegalArgumentException(model.getLevel() + " is not supported!");
		}
		
		// clean/normalize xrefs first!
		normalizeXrefs(model);
		
		// copy
		Set<? extends UtilityClass> objects = 
			new HashSet<UtilityClass>(model.getObjects(UtilityClass.class));
		// process the rest of utility classes (selectively though)
		for(UtilityClass bpe : objects) {
			UnificationXref uref = null;
			if(bpe instanceof ControlledVocabulary) {
				uref = getFirstUnificationXref((XReferrable) bpe);
			} else if(bpe instanceof EntityReference) {
				uref = getFirstUnificationXrefOfEr((EntityReference) bpe);
			} else if(bpe instanceof BioSource) {
				uref = ((BioSource)bpe).getTaxonXref(); // taxonXref is deprecated; BioSource will become Xreferrable
			} else if(bpe instanceof Provenance) {
				Provenance pro = (Provenance) bpe;
				String urn = miriam.getDataTypeURI(pro.getStandardName());
				model.updateID(pro.getRDFId(), urn);
				continue;
			} else {
				continue;
			}
			
			if (uref == null) {
				throw new IllegalArgumentException(
						"Cannot find a unification xrefs of : " + bpe);
			}

			String urn = miriam.getURI(uref.getDb(), uref.getId());
			model.updateID(bpe.getRDFId(), urn);
		}
		
		// return as BioPAX OWL
		String owl = convertToOWL(model);
		return owl;
	}

	
	private String convertToOWL(Model model) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			(new SimpleExporter(model.getLevel())).convertToOWL(model, out);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return out.toString();
	}

	/* (non-Javadoc)
	 * @see cpath.importer.Normalizer#normalizeXrefs(org.biopax.paxtools.model.Model)
	 */
	public void normalizeXrefs(Model model) {
		// normalize xrefs first: set db name as in Miriam and rdfid as db_id
		
		// make a copy (to safely remove duplicates)
		Set<? extends Xref> xrefs = new HashSet<Xref>(model.getObjects(Xref.class));
		for(Xref ref : xrefs) {
			// get database official urn
			Xref x = ref; // can be replaced below...
			String name = x.getDb();
			try {
				String urn = miriam.getDataTypeURI(x.getDb());
				// update name to the primary one
				name = miriam.getName(urn);
				x.setDb(name);
			} catch (IllegalArgumentException e) {
				log.error("Unknown or misspelled datanase name! Won't fix this now... " + e);
			}
			String rdfid =  BIOPAX_URI_PREFIX + x.getModelInterface().getSimpleName() 
				+ "#" + URLEncoder.encode(name + "_" + x.getId());
			if(model.containsID(rdfid) 
					&& model.getByID(rdfid).getModelInterface().equals(x.getModelInterface())) {
				log.warn("Model has 'equivalent' xrefs. This one: " 
							+ model.getByID(rdfid) + " (" + rdfid + ") refers to the same thing as " 
							+ x + " (" + x.getRDFId() + ")! Re-wiring...");
				Xref existingXref = (Xref) model.getByID(rdfid);
				// copy parents (because replacing the xref would change this set as well)
				Set<? extends XReferrable> elementsThatUseThisRef = new HashSet<XReferrable>(x.getXrefOf());
				// replace xref
				for(XReferrable bpe : elementsThatUseThisRef) {
					bpe.removeXref(x);
					bpe.addXref(existingXref);
				}
				assert(x.getXrefOf().isEmpty());
				model.remove(x); // for this reason, the xref set is a copy of that in model
				x = existingXref;
			} else {
				model.updateID(x.getRDFId(), rdfid);
			}
			
			// warn if two elements reference the same unif. xref!
			if(x instanceof UnificationXref && x.getXrefOf().size()>1) {
				log.warn("UnificationXref " + x + 
						" is used by several elements : " + x.getXrefOf().toString() + 
						". (It's either a BioPAX error or those utility classes " +
						"are the same and should be merged!");
			}
		}
	}


	private List<UnificationXref> getUnificationXrefsSorted(XReferrable referrable) {
		List<UnificationXref> urefs = new ArrayList<UnificationXref>(
			new ClassFilterSet<UnificationXref>(referrable.getXref(), UnificationXref.class)
		);	
		
		Comparator<UnificationXref> comparator = new Comparator<UnificationXref>() {
			@Override
			public int compare(UnificationXref o1, UnificationXref o2) {
				String s1 = o1.getDb() + o1.getId();
				String s2 = o2.getDb() + o2.getId();
				return s1.compareTo(s2);
			}
		};
		
		Collections.sort(urefs, comparator);
		
		return urefs;
	}

	
	/*
	 * Gets the first one, the set is not empty, or null.
	 */
	private UnificationXref getFirstUnificationXref(XReferrable xr) {
		List<UnificationXref> urefs = getUnificationXrefsSorted(xr);
		return (urefs.isEmpty()) ? null : urefs.get(0);
	}

	
	/*
	 * The first uniprot or enterz gene xref, if exists, will be returned;
	 * otherwise, the first one of any kind is the answer.
	 */
	private UnificationXref getFirstUnificationXrefOfEr(EntityReference er) {
		List<UnificationXref> urefs = getUnificationXrefsSorted(er);
		for(UnificationXref uref : urefs) {
			if(uref.getDb().toLowerCase().startsWith("uniprot") 
				|| uref.getDb().toLowerCase().startsWith("entrez")) {
				return uref;
			}
		}
		// otherwise, take the first one
		return (urefs.isEmpty()) ? null : urefs.get(0);
	}


	/* (non-Javadoc)
	 * @see cpath.importer.Normalizer#normalize(org.biopax.paxtools.model.Model)
	 */
	public String normalize(Model model) {
		String owl = convertToOWL(model);
		return normalize(owl);
	}

}
