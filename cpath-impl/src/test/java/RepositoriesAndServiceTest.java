
import java.io.IOException;
import java.util.*;

import org.biopax.validator.api.beans.Validation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import cpath.config.CPathSettings;
import cpath.service.LogUtils;
import cpath.jpa.Content;
import cpath.jpa.LogEntity;
import cpath.jpa.LogEvent;
import cpath.jpa.LogType;
import cpath.jpa.Mapping;
import cpath.jpa.Metadata;
import cpath.jpa.Metadata.METADATA_TYPE;
import cpath.service.CPathService;
import cpath.service.Cmd;
import cpath.service.GraphType;
import cpath.service.OutputFormat;
import cpath.service.Status;
import static org.junit.Assert.*;

/**
 * 
 * @author rodche
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={
		"classpath:META-INF/spring/applicationContext-jpa.xml"})
@ActiveProfiles("dev")
public class RepositoriesAndServiceTest {
	
	@Autowired
	private CPathService service;
	
	@Test
	@DirtiesContext //other tests might added records too; do cleanup
	public final void testSave() {
		final String ipAddr = "66.249.74.168";
		//explicitly create and save a new log record
		LogEntity logEntity = new LogEntity(LogUtils.today(), 
				LogEvent.from(Status.INTERNAL_ERROR), ipAddr); //country="US"
		assertNull(logEntity.getId());
		assertEquals(0L, logEntity.getCount().longValue());
	}

	
	@DirtiesContext //other tests might added records too; do cleanup
	@Test
	public final void testTimeline() {	
		final String ipAddr = "66.249.74.168"; //some IP (perhaps it's Google's)
		
		service.log(LogEvent.from(Status.INTERNAL_ERROR), ipAddr);
		service.log(LogEvent.TOTAL, ipAddr);
		service.log(LogEvent.from(Status.NO_RESULTS_FOUND), ipAddr);
		service.log(LogEvent.TOTAL, ipAddr);
		service.log(new LogEvent(LogType.PROVIDER, "Reactome"), ipAddr);
		service.log(LogEvent.TOTAL, ipAddr);
		service.log(new LogEvent(LogType.PROVIDER, "HumanCyc"), ipAddr);
		service.log(LogEvent.TOTAL, ipAddr);
		service.log(LogEvent.from(Cmd.SEARCH), ipAddr);

		// add some logs (for two days, several categories):
		// Today
		Set<LogEvent> events = new HashSet<LogEvent>(
			Arrays.asList(
					LogEvent.from(OutputFormat.BIOPAX),
					new LogEvent(LogType.PROVIDER, "Reactome"),
					new LogEvent(LogType.PROVIDER, "HumanCyc"),
					LogEvent.from(GraphType.NEIGHBORHOOD)
			)
		);

		service.log(events, ipAddr);
	}
	
	@Test
	public final void testLogEventFromDownloads() {
		CPathSettings cpath = CPathSettings.getInstance();
		
		//additional 'test' metadata entry (Reactome); 
		//without this service.logEventsFromFilename(file) 
		//won't be able to match provider by name/id and create PROVIDER type log event
		Metadata md = new Metadata("test", "Reactome", "Foo", "", "", 
				"", METADATA_TYPE.BIOPAX, "", "", null, "free");		
		service.save(md);
		
		String file = cpath.exportArchivePrefix() + "Reactome.BIOPAX.owl.gz";
		assertEquals(OutputFormat.BIOPAX, LogUtils.fileOutputFormat(file));
		assertEquals("Reactome", LogUtils.fileSrcOrScope(file));
		Set<LogEvent> events = service.logEventsFromFilename(file);
		assertEquals(3, events.size()); //log in types: PROVIDER, FILE, FORMAT
		
		//'All' 
		file = cpath.exportArchivePrefix() + "All.BIOPAX.owl.gz";
		events = service.logEventsFromFilename(file);
		assertEquals(2, events.size());
		
		file = cpath.exportArchivePrefix() + "Reactome.GSEA.gmt.gz";
		events = service.logEventsFromFilename(file);
		assertEquals(3, events.size());
		
		//illegal format (ignored, i.e., no FORMAT type log event is added)
		file = cpath.exportArchivePrefix() + "Reactome.foo.gmt.gz";
		events = service.logEventsFromFilename(file);
		assertEquals(2, events.size());
		
		//other (metadata etc.)
		file = "blacklist.txt";
		events = service.logEventsFromFilename(file);
		assertEquals(1, events.size());//counted in FILE log type only
		assertEquals(LogType.FILE, events.iterator().next().getType());
		
		//provider name is now matched ignoring case, 
		//(FORMAT type event is not there as well due to 'foo')
		file = cpath.exportArchivePrefix() + "reactome.foo.gmt.gz";
		events = service.logEventsFromFilename(file);
		assertEquals(2, events.size());
	}
	
	
	@Test
	@DirtiesContext
	public void testIdMapping() {		
        //capitalization is important in 99% of identifier types (we should not ignore it)
        // we should be able to save it and not get 'duplicate key' exception here
		service.mapping().save(new Mapping("GeneCards", "ZHX1", "UNIPROT", "P12345"));
		service.mapping().save(new Mapping("GeneCards", "ZHX1-C8orf76", "UNIPROT", "Q12345"));
		service.mapping().save(new Mapping("GeneCards", "ZHX1-C8ORF76", "UNIPROT", "Q12345"));
		assertEquals(1, service.mapping()
			.findBySrcIgnoreCaseAndSrcIdAndDestIgnoreCase("GeneCards", "ZHX1-C8ORF76", "UNIPROT").size());
        
        //check it's saved
        assertEquals(1, service.map("ZHX1-C8orf76", "UNIPROT").size());
        assertEquals(1, service.map("ZHX1-C8ORF76", "UNIPROT").size());
        
        // repeat (should successfully update)- add a Mapping
        service.mapping().save(new Mapping("TEST", "FooBar", "CHEBI", "CHEBI:12345"));
        assertTrue(service.map("FooBar", "UNIPROT").isEmpty());

        Set<String> mapsTo = service.map("FooBar", "CHEBI");
        assertEquals(1, mapsTo.size());
        assertEquals("CHEBI:12345", mapsTo.iterator().next());
		mapsTo = service.map("FooBar", "CHEBI");
		assertEquals(1, mapsTo.size());
		assertEquals("CHEBI:12345", mapsTo.iterator().next());
        
        //test that service.map(..) method can map isoform IDs despite they're not explicitly added to the mapping db
		service.mapping().save(new Mapping("UNIPROT", "A2A2M3", "UNIPROT", "A2A2M3"));
		assertEquals(1, service.map("A2A2M3-1", "UNIPROT").size());
		assertEquals(1, service.map("A2A2M3", "UNIPROT").size());
        
        Mapping m = new Mapping("PubChem-substance", "14438", "CHEBI", "CHEBI:20");
        service.mapping().save(m);
		assertEquals("SID:14438", m.getSrcId());
        assertNotNull(service.mapping().findBySrcIgnoreCaseAndSrcIdAndDestIgnoreCaseAndDestId(
        		m.getSrc(), m.getSrcId(), m.getDest(), m.getDestId()));
		assertEquals(1, service.map("SID:14438", "CHEBI").size());

		//map from a list of IDs to target ID type (UNIPROT)
		List<String> srcIds = new ArrayList<String>();
		//add IDs - both map to the same uniprot ID ()
		srcIds.add("ZHX1");
		srcIds.add("A2A2M3");
		//currently, mapping().find* methods cannot map uniprot isoform IDs (service.map(..) - can do by removing the suffix)
		List<Mapping> mappings = service.mapping().findBySrcIdInAndDestIgnoreCase(srcIds, "UNIPROT");
		assertEquals(2, mappings.size());
		//test new service.map(null, srcIds, "UNIPROT"), which must also support isoform IDs
		srcIds.remove("A2A2M3");
		srcIds.add("A2A2M3-1");
		assertEquals(2, service.map(srcIds, "UNIPROT").size());
	}
	
	@Test
	@DirtiesContext
	public void testImportContent() throws IOException {
        // mock metadata and pathway data
        Metadata md = new Metadata("TEST", "test", "test", "", "",
        		"", METADATA_TYPE.BIOPAX, null, null, null, "free");        
        
        //cleanup previous tests data if any
        md = service.clear(md);
        
        Content content = new Content(md, "test0");
        md.getContent().add(content);
        //add the second pd (for the tests at the end of this method)
        final Content pd = new Content(md, "test1");
        md.getContent().add(pd);
               
        // persist
        service.save(md);
        
        // test pathwaydata content is not accidentally erased
        Iterator<Content> it = md.getContent().iterator();
        content = it.next();
        //we want test0 for following assertions
        if("test1".equals(content.getFilename()))
        	content = it.next();
        assertEquals("test0",content.getFilename());    
        
        //even if we update from the db, data must not be empty
        md = service.metadata().findByIdentifier(md.getIdentifier());
        assertNotNull(md);
        assertEquals("TEST", md.getIdentifier());
        assertEquals(2, md.getContent().size()); 
        it = md.getContent().iterator();
        content = it.next();
        //we want test0 for following assertions
        if("test1".equals(content.getFilename()))
        	content =it.next();
        assertEquals("test0",content.getFilename());      

        // add validation result());  
        for(Content o : md.getContent())
        	o.saveValidationReport(new Validation(null));        
        // update
        service.save(md);
         
        //read the latest state
        md = service.metadata().findByIdentifier("TEST");
        assertNotNull(md);
        Set<Content>  lpd = md.getContent();
        assertFalse(lpd.isEmpty());
        content = lpd.iterator().next();
        assertNotNull(content);  
        
        //cleanup
        md = service.clear(md);
        assertTrue(md.getContent().isEmpty()); 
        md = service.metadata().findByIdentifier("TEST");
        assertTrue(md.getContent().isEmpty());         
	}
}
