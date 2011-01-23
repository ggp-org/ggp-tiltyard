package ggp.apollo;

import java.io.IOException;
import javax.servlet.http.*;

@SuppressWarnings("serial")
public class GGP_ApolloServlet extends HttpServlet {
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("text/plain");
        resp.getWriter().println("Hello, world! This is Apollo.");
    }
}
