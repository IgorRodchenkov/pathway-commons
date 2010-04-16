package cpath.converter.internal;
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


import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

//import org.biopax.paxtools.io.simpleIO.SimpleExporter;
import org.biopax.paxtools.model.BioPAXLevel;
import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.ProteinReference;
import org.biopax.paxtools.model.level3.RelationshipXref;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;

import cpath.converter.Converter;
import cpath.dao.PaxtoolsDAO;

/**
 * @author rodche
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:applicationContext-whouseProteins.xml"})
@TransactionConfiguration(transactionManager="proteinsTransactionManager")
public class UniprotConverterImplTest {

	@Autowired
	PaxtoolsDAO proteinsDAO;

	/**
	 * Test method for {@link cpath.converter.internal.UniprotConverterImpl#convert(java.io.InputStream, org.biopax.paxtools.model.BioPAXLevel)}.
	 * @throws IOException 
	 */
	@Test
	@Transactional
	public final void testConvert() throws IOException {
		Converter converter = new UniprotConverterImpl();
		InputStream is = this.getClass().getClassLoader().getResourceAsStream("test_uniprot_data.dat");
		Model model = converter.convert(is, BioPAXLevel.L3);
		
		Set<ProteinReference> proteinReferences = model.getObjects(ProteinReference.class);
		assertTrue(proteinReferences.size()==2);
		
		//(new SimpleExporter(BioPAXLevel.L3)).convertToOWL(model, System.out);
		
		proteinsDAO.importModel(model, true);
		
		Set<RelationshipXref> returnClasses = proteinsDAO.search("entrez gene", RelationshipXref.class);
		assertTrue(returnClasses.size()==4);
	}

}
