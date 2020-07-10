package com.ibm.research.kar.reeferserver.error;
import java.util.List;
import javax.xml.bind.annotation.XmlRootElement;
 
@XmlRootElement(name = "error")
public class ErrorResponse {
     
    public ErrorResponse(String message, List<String> details) {
        super();
        this.message = message;
        this.details = details;
    }
 
    //General error message about nature of error
    private String message;
 
    //Specific errors in API request processing
    private List<String> details;
  
    public List<String> getDetails() {
        return details;
    }
    public String getMessage() {
        return message;
    }
}