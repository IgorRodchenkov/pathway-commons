package cpath.service.jaxb;

import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;


@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TraverseEntry")
public class TraverseEntry {
    @XmlAttribute(required = true)
    protected String uri;
    
    protected List<String> value;


    public String getUri() {
        return uri;
    }
    public void setUri(String value) {
        this.uri = value;
    }
	
	public List<String> getValue() {
		if(value == null) {
			value = new ArrayList<String>();
		}
		return value;
	}
	public void setValue(List<String> value) {
		this.value = value;
	}

}
