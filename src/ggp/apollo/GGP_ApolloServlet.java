package ggp.apollo;

import java.io.IOException;
import java.util.Date;

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
        for(DataPoint d : DataPoint.loadData()) {
            resp.getWriter().println("<li>" + d.getData());
        }
        resp.getWriter().println("</ul></body></html>");
    }
    
    public void runSchedulingRound() {
        new DataPoint("Scheduling round: " + new Date().toString());
    }
}
