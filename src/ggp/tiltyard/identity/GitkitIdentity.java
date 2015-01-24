package ggp.tiltyard.identity;
import java.io.File;
import java.net.URLEncoder;
import java.security.SignatureException;
import java.util.Properties;
import java.util.Scanner;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;

import com.google.gson.JsonObject;
import com.google.identitytoolkit.GitkitClient;
import com.google.identitytoolkit.GitkitClient.OobAction;
import com.google.identitytoolkit.GitkitClient.OobResponse;
import com.google.identitytoolkit.GitkitClientException;
import com.google.identitytoolkit.GitkitUser;
import com.google.identitytoolkit.HttpSender;
import com.google.identitytoolkit.JsonTokenHelper;
import com.google.identitytoolkit.RpcHelper;

public class GitkitIdentity {
	static GitkitClient getGitkitClient() {
		GitkitClient gitkitClient = GitkitClient.newBuilder()
				.setCookieName("gtoken")
				.setServiceAccountEmail("559907751382-hlolfu5dp7n25t2q2c9a3cramgi6584q@developer.gserviceaccount.com")
				.setWidgetUrl("http://tiltyard.ggp.org/oauth2callback")
				.setGoogleClientId("559907751382-0abmh2i3efe8j5vuq53270u76ol39v3c.apps.googleusercontent.com")
				.setKeyStream(GitkitApiKey.getApiKey())
				.build();
		return gitkitClient;
	}
	
	// TODO: Remove this hack and figure out a way for Gitkit to natively support the "verified" email property.
	static String getAuthTokenFromRequest(HttpServletRequest request) {
		try {
		    Cookie[] cookies = request.getCookies();
		    if (cookies == null) {
		      return null;
		    }
		    for (Cookie cookie : cookies) {
		      if ("gtoken".equals(cookie.getName())) {
		    	  RpcHelper rpcHelper = new RpcHelper(new HttpSender(), "https://www.googleapis.com/identitytoolkit/v3/relyingparty/", "559907751382-hlolfu5dp7n25t2q2c9a3cramgi6584q@developer.gserviceaccount.com", GitkitApiKey.getApiKey());
		    	  JsonTokenHelper tokenHelper = new JsonTokenHelper("559907751382-0abmh2i3efe8j5vuq53270u76ol39v3c.apps.googleusercontent.com", rpcHelper, null);	    	  
		          JsonObject jsonToken = tokenHelper.verifyAndDeserialize(cookie.getValue()).getPayloadAsJsonObject();
		          return jsonToken.toString();
		      }
		    }
		} catch (SignatureException se) {
			throw new RuntimeException(se);
		}
	    return null;
	}
	
	static boolean doesUserHaveVerifiedEmail(HttpServletRequest request) {
		try {
			String authToken = getAuthTokenFromRequest(request);
			if (authToken == null) return false;
			JSONObject theToken = new JSONObject(authToken);
			return theToken.getBoolean("verified");
		} catch (JSONException e) {
			return false;
		}		
	}
	
	public static GitkitUser getUser(HttpServletRequest request) {
		try {
			GitkitClient gitkitClient = getGitkitClient();
			GitkitUser gitkitUser = gitkitClient.validateTokenInRequest(request);
			
	    	if (gitkitUser != null && !GitkitIdentity.doesUserHaveVerifiedEmail(request)) {
	    		gitkitUser.setEmail(null);
	    	} 
			
			return gitkitUser;
		} catch (GitkitClientException gce) {
			return null;
		}
	}
	
	public static void handleOauthCallback(HttpServletRequest request, HttpServletResponse resp) {
		try {
	      resp.setContentType("text/html");

	      StringBuilder builder = new StringBuilder();
	      String line;
	        while ((line = request.getReader().readLine()) != null) {
	          builder.append(line);
	        }
	      String postBody = URLEncoder.encode(builder.toString(), "UTF-8");

	      Scanner scan = new Scanner(new File("gitkit-widget.html"), "UTF-8");
	      scan.useDelimiter("\\A");
	      resp.getWriter().print(scan.next().replaceAll("JAVASCRIPT_ESCAPED_POST_BODY", postBody).toString());	      
	      resp.setStatus(HttpServletResponse.SC_OK);
	      scan.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public static void doGet(String theHandler, HttpServletRequest request, HttpServletResponse resp) {
		try {
			if (theHandler.equals("gitkit_email")) {
				GitkitClient gitkitClient = getGitkitClient();
				OobResponse oobResponse = gitkitClient.getOobResponse(request);
				
				Properties props = new Properties();
				Session session = Session.getDefaultInstance(props, null);

				try {
				    Message msg = new MimeMessage(session);
				    msg.setFrom(new InternetAddress("noreply-accounts@ggp-apollo.appspotmail.com", "GGP.org Accounts"));
				    msg.addRecipient(Message.RecipientType.TO, new InternetAddress(oobResponse.getEmail(), oobResponse.getRecipient()));
					if (oobResponse.getOobAction().equals(OobAction.CHANGE_EMAIL)) {
						msg.setSubject("Email address change for GGP.org account");
						msg.setText("Hello!\n\n The email address for your GGP.org account will be changed from " + oobResponse.getEmail() + " to " + oobResponse.getNewEmail() + " when you click this confirmation link:\n\n " + oobResponse.getOobUrl().get() + "\n\nIf you didn't request an email address change for this account, please disregard this message.");
					} else if (oobResponse.getOobAction().equals(OobAction.RESET_PASSWORD)) {
						msg.setSubject("Password change for GGP.org account");
						msg.setText("Hello!\n\n The password for your GGP.org account will be reset when you click this confirmation link:\n\n " + oobResponse.getOobUrl().get() + "\n\nIf you didn't request a password change for this account, please disregard this message.");
					}
				    Transport.send(msg);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				
				resp.getWriter().write(oobResponse.getResponseBody());
			} else {
				resp.getWriter().write("Unknown handler: " + theHandler);
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}