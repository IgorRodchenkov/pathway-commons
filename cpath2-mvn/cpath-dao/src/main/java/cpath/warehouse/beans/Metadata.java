package cpath.warehouse.beans;

// imports
import java.io.File;
import java.util.HashSet;
import java.util.regex.Pattern;

import javax.persistence.*;

import org.biopax.paxtools.model.Model;
import org.biopax.paxtools.model.level3.Provenance;

import cpath.config.CPathSettings;

/**
 * Data Provider Metadata.
 */
@Entity
@Table(name="metadata")
@NamedQueries({
		@NamedQuery(name="cpath.warehouse.beans.providerByIdentifier",
					query="from Metadata as metadata where identifier = :identifier order by id"),
		@NamedQuery(name="cpath.warehouse.beans.allProvider", 
					query="from Metadata as metadata order by id")
})
public final class Metadata {

    private static final Pattern BAD_ID_PATTERN = Pattern.compile("\\s|-");
	
	// METADATA_TYPE Enum
    public static enum METADATA_TYPE {
        // data types
        PSI_MI(true), // interactions to be converted to Biopax L3 format
		BIOPAX(true), // pathways and interactions in Biopax L2 or L3 format
		WAREHOUSE(false), // warehouse data to be converted to BioPAX and used during the merge stage
		MAPPING(false); //extra gene/protein id-mapping data (two column, TSV format: "some id or name" \t "primary uniprot AC")
        
        private final boolean pathwayData;
        
        private METADATA_TYPE(boolean isPathwayData) {
			this.pathwayData = isPathwayData;
		}
        
        public boolean isNotPathwayData() {
			return !pathwayData;
		}
        
    }

	@Id
	@GeneratedValue(strategy=GenerationType.AUTO)
    private Integer id;
	@Column(length=40, unique = true, nullable = false)
    private String identifier;
	@Column(nullable=false)
    private String name;
	@Column(nullable=false)
    private String description;
	@Column(nullable=false)
    private String urlToData;
	@Column(nullable=false)
    private String urlToHomepage;
	@Lob
	@Column(nullable=false)
    private byte[] icon;
	@Column(nullable=false)
	@Enumerated(EnumType.STRING)
    private METADATA_TYPE metadata_type;
    private String cleanerClassname;
    private String converterClassname;

	/**
	 * Default Constructor.
	 */
	public Metadata() {}

    /**
     * Create a Metadata obj with the specified properties;
     *
     * @param identifier String (string used in web service calls)
     * @param name String
     * @param description String
     * @param urlToData String
     * @param urlToHomepage String
     * @param icon byte[]
     * @param cleanerClassname String
     * @param converterClassname String
     * @param isPSI Boolean
     * @throws IllegalArgumentException
     */
    public Metadata(final String identifier, final String name, final String description, final String urlToData, String urlToHomepage,
					final byte[] icon, final METADATA_TYPE metadata_type, final String cleanerClassname, final String converterClassname) {

    	//set/validate all parameters
    	setIdentifier(identifier); 
        setName(name);
        setDescription(description);
        setUrlToData(urlToData);
        setUrlToHomepage(urlToHomepage);
        setIcon(icon);
        setType(metadata_type);
        setCleanerClassname(cleanerClassname);
        setConverterClassname(converterClassname);
    }


	void setId(Integer id) {
		this.id = id;
	}
    public Integer getId() { return new Integer(id); }
     
    
    /**
	 * Sets the identifier.
	 * No spaces, dashes, allowed. 
	 * 
	 * @param identifier
	 * @throws IllegalArgumentException if it's null, empty string, or contains spaces or dashes
	 */
    void setIdentifier(String identifier) {
    	// validate the parameter
    	
    	if(identifier == null 
    		|| identifier.length() == 0
    		|| BAD_ID_PATTERN.matcher(identifier).find())
    		throw new IllegalAccessError("Bad metadata identifier: " + identifier);
    		
		// copy value
    	this.identifier = identifier;
	}
	
	/**
	 * Data source metadata identifier.
	 * 
	 * It can be also used as filter ('datasource') 
	 * value in cpath2 full-text search queries
	 * (for pathway datasource types only)
	 * 
	 * @return
	 */
    public String getIdentifier() { return identifier; }

	/**
	 * Sets data provider/source name. 
	 * 
	 * Please use a standard name for pathway/interaction data types,
	 * if possible (for warehouse data it's not so important), 
	 * as this will be recommended to use as filter ('datasource') 
	 * value in cpath2 full-text search queries 
	 * 
     * @param name
     * @throws IllegalArgumentException
     */
	public void setName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
		this.name = name;
	}
	
	/**
	 * Gets the data provider/source name.
	 * 
	 * @return
	 */
    public String getName() { return name; }


	public void setDescription(String releaseDate) {
        if (releaseDate == null) {
            throw new IllegalArgumentException("release data must not be null");
        }
        this.description = releaseDate;
	}
    public String getDescription() { return description; }

	public void setUrlToData(String urlToData) {
        if (urlToData == null) {
            throw new IllegalArgumentException("PROVIDER_URL to data must not be null");
        }
        this.urlToData = urlToData;
	}
    public String getUrlToData() { return urlToData; }

	public void setUrlToHomepage(String urlToHomepage) {
        if (urlToHomepage == null) {
            throw new IllegalArgumentException("PROVIDER_URL to Homepage must not be null");
        }
        this.urlToHomepage = urlToHomepage;
	}
    public String getUrlToHomepage() { return urlToHomepage; } 
    
    public void setIcon(byte[] icon) {
        if (icon == null) {
            throw new IllegalArgumentException("icon must not be null");
        }
        this.icon = icon;
	}
    public byte[] getIcon() { return icon.clone(); }

	public void setType(METADATA_TYPE metadata_type) {
        if (metadata_type == null) {
            throw new IllegalArgumentException("metadata_type must not be null");
        }
        this.metadata_type = metadata_type;
	}
    public METADATA_TYPE getType() { return metadata_type; }

	public void setCleanerClassname(String cleanerClassname) {
		if(cleanerClassname == null || cleanerClassname.trim().length() == 0)
			this.cleanerClassname = null;
		else 
			this.cleanerClassname = cleanerClassname.trim();
	}
    public String getCleanerClassname() { 
    	return (cleanerClassname == null || cleanerClassname.length() == 0) 
    			? null : cleanerClassname; 
    }

	public void setConverterClassname(String converterClassname) {
		if(converterClassname == null || converterClassname.trim().length() == 0)
			this.converterClassname = null;
		else
			this.converterClassname = converterClassname.trim();
	}
    public String getConverterClassname() { 
    	return (converterClassname == null || converterClassname.length() == 0) 
    			? null : converterClassname; 
    }

    @Override
    public String toString() {
        return identifier;
    }
    
    
	/**
	 * Gets the full path to the local data file
	 * 
	 * @return
	 */
    @Transient
    public String localDataFile() {
    	String name = CPathSettings.localDataDir() 
    	+ File.separator + identifier;
    	
    	
    	// add the file extension, if any
		int idx = urlToData.lastIndexOf('.');
		if(idx >= 0) {
			String ext = urlToData.substring(idx+1);
			if(!ext.isEmpty())
				name += "." + ext;
		}
		
		return name;
    }
    
    
    /**
     * Generate a URI (for a Provenance instance.)
     * 
     * @return
     */
    @Transient
    public String getUri() {
    	return CPathSettings.xmlBase() + identifier;
    }

    
	/**
	 * Creates a new Provenance from this Metadata and sets
	 * if to all Entity class objects in the model; removes
	 * all previous dataSource property values.
	 * 
	 * @param model BioPAX model to update
	 */
	public void setProvenanceFor(Model model) {
		Provenance pro = null;
		
		// we create URI from the Metadata identifier and version.
		pro = model.addNew(Provenance.class, getUri());
		
		// parse/set names
		String[] names = getName().split(";");
		String name = names[0].trim();
		pro.setDisplayName(name.toLowerCase());
		if(names.length > 1)
			pro.setStandardName(names[1].trim());
		else
			pro.setStandardName(name);
		
		if(names.length > 2)
			for(int i=2; i < names.length; i++)
				pro.addName(names[i].trim());
		
		// add additional info about the current version, source, identifier, etc...
		final String loc = getUrlToData(); 
		pro.addComment("Source " + 
			//skip for a local or empty (default) location
			((loc.startsWith("http:") || loc.startsWith("ftp:")) ? loc : "") 
			+ " metadata_type: " + getType() + ", " + getDescription());
		
		// replace for all entities
		for (org.biopax.paxtools.model.level3.Entity ent : model.getObjects(org.biopax.paxtools.model.level3.Entity.class)) {
			for(Provenance ds : new HashSet<Provenance>(ent.getDataSource()))
				ent.removeDataSource(ds);			
			ent.addDataSource(pro);
		}
	}
}
