package eu.leoregner.express;
import java.util.*;
import java.text.SimpleDateFormat;

public class SessionManager
{
	private final long sessionDuration;
	private final String sessionCookieName;
	private final Map<String, Session> sessions = new HashMap<String, Session>();
	
	public SessionManager()
	{
		this.sessionDuration = 2592000;
		this.sessionCookieName = "sessionId";
	}
	
	public SessionManager(long duration, String cookieName)
	{
		this.sessionDuration = duration;
		this.sessionCookieName = cookieName;
	}
	
	/** @return the session with the specified sessionId */
	public Session get(String sessionId)
	{
		return sessions.get(sessionId);
	}
	
	/** @return the session for the specified HTTP request */
	public Session getSession(Express.Request req)
	{
		return get(req.cookie(sessionCookieName));
	}
	
	/** @return the session for the specified HTTP request or creates a new session if none exists */
	public Session start(Express.Request req, Express.Response res)
	{
		Session session = getSession(req);
		
		if(session == null)
		{
			String sessionId = UUID.randomUUID().toString() + "-" + (Math.round(Math.random() * (9999 - 1000)) + 1000);
			String exp = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz").format(new Date(new Date().getTime() + sessionDuration * 1000));
			sessions.put(sessionId, session = new Session(sessionId));
			res.cookie(sessionCookieName, sessionId, "Path=/;Max-Age=" + sessionDuration + ";expires=" + exp);
		}
		
		return session;
	}
	
	/** represents one user's session */
	public static class Session
	{
		private final String sessionId;
		private final Map<String, String> sessionParameters = new HashMap<String, String>();
		
		private Session(String sessionId)
		{
			this.sessionId = sessionId;
		}
		
		/** stores the specified key value pair within the session */
		public Session set(String key, String value)
		{
			sessionParameters.put(key, value);
			return this;
		}
		
		/** @return the value corresponding to the specified key within the session */
		public String get(String key)
		{
			return sessionParameters.get(key);
		}
		
		/** @return the session ID of this session */
		public String getSessionId()
		{
			return sessionId;
		}
	}
}