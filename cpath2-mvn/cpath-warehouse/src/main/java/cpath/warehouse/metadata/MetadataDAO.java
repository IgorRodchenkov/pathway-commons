package cpath.warehouse.metadata;

// imports
import cpath.warehouse.beans.Metadata;

import java.util.Collection;

/**
 * An interface which provides methods to persist and query provider metadata.
 */
public interface MetadataDAO {

    /**
     * Persists the given metadata object to the db.
     *
     * @param metadata Metadata
     */
    void importMetadata(final Metadata metadata);

    /**
     * This method returns the metadata object with the given Identifier.
	 *
     * @param identifier String
     * @return Metadata
     */
    Metadata getByIdentifier(final String identifier);

    /**
     * This method returns all metadata objects in warehouse.
	 *
     * @return Collection<Metadata>
     */
    Collection<Metadata> getAll();
}
