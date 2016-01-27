package cpath.jpa;

/**
 * Categories of the data download/access events. 
 *
 * In fact, these are not mutually
 * exclusive types, for a web request/response usually
 * counts more than once: once, for sure, in the "total" 
 * category but might also - in "providers", "commands", 
 * "formats", or "errors" at the same time.
 * 
 * @author rodche
 *
 */
public enum LogType {
	PROVIDER("All Providers"),
	COMMAND("All Web Commands"),
	FORMAT("All Output Formats"),
	FILE("All Files"),
	ERROR("All Errors"),
	TOTAL("All");
	
	public final String description;
	
	LogType(String description) {
		this.description = description;
	}
}
