package ggp.apollo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.servlet.http.*;

@SuppressWarnings("serial")
public class GGP_ApolloServlet extends HttpServlet {
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        if (req.getRequestURI().equals("/cron/scheduling_round")) {
            runSchedulingRound();
            resp.setContentType("text/plain");
            resp.getWriter().println("Starting scheduling round.");            
            return;
        }
        
        resp.getWriter().println("<html><body>");
        resp.getWriter().println("Hello, world! This is Apollo.<ul>");
        
        // Output a sorted list of the mock scheduling round times
        List<String> theDataStrings = new ArrayList<String>();
        for(DataPoint d : DataPoint.loadData()) {
            theDataStrings.add(d.getData());
        }
        Collections.sort(theDataStrings);
        for(String s : theDataStrings) {
            resp.getWriter().println("<li>" + s);        
        }
        
        resp.getWriter().println("</ul></body></html>");
    }
    
    public void runSchedulingRound() {
        new DataPoint("Scheduling round: " + new Date().toString());
    }
}
