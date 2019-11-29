package eu.leoregner.express;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.sun.net.httpserver.*;

/** Java implementation analogously to the Node.js express server
  * @author Leopold Mathias Regner, mailto:office@leoregner.eu */
public class Express
{
	private final HttpServer server;
	
	private File rootDirectory = null;
	private final Map<String, EndpointHandler> getHandlers = new HashMap<String, EndpointHandler>(),
											   postHandlers = new HashMap<String, EndpointHandler>(),
											   putHandlers = new HashMap<String, EndpointHandler>(),
											   patchHandlers = new HashMap<String, EndpointHandler>(),
											   deleteHandlers = new HashMap<String, EndpointHandler>();
	
	/** start the server listening to port 80 */
	public Express() throws Exception
	{
		this(80);
	}
	
	/** starts the server listening to the specified port */
	public Express(int port) throws Exception
	{
		this("0.0.0.0", port);
	}
	
	/** starts the server listening to the specified port, bound to the specified IP address */
	public Express(String ipAddress, int port) throws IOException
	{
		server = HttpServer.create(new java.net.InetSocketAddress(ipAddress, port), 0);
		server.createContext("/", restApiHandler);
		server.start();
	}
	
	/** @return the TCP port this web server is listening to */
	public int getPort()
	{
		return server.getAddress().getPort();
	}
	
	/** allows serving static files from the local file system */
	public void setRootDirForStaticPages(File rootDirectory)
	{
		this.rootDirectory = rootDirectory;
	}
	
	/** adds a HTTP GET end point handler to the web server */
	public void get(String uri, EndpointHandler handler)
	{
		getHandlers.put(uri, handler);
	}
	
	/** adds a HTTP POST end point handler to the web server */
	public void post(String uri, EndpointHandler handler)
	{
		postHandlers.put(uri, handler);
	}
	
	/** adds a HTTP PUT end point handler to the web server */
	public void put(String uri, EndpointHandler handler)
	{
		putHandlers.put(uri, handler);
	}
	
	/** adds a HTTP PATCH end point handler to the web server */
	public void patch(String uri, EndpointHandler handler)
	{
		patchHandlers.put(uri, handler);
	}
	
	/** adds a HTTP DELETE end point handler to the web server */
	public void delete(String uri, EndpointHandler handler)
	{
		deleteHandlers.put(uri, handler);
	}
	
	/** adds a HTTP end point handler for all methods to the web server */
	public void all(String uri, EndpointHandler handler)
	{
		getHandlers.put(uri, handler);
		postHandlers.put(uri, handler);
		putHandlers.put(uri, handler);
		patchHandlers.put(uri, handler);
		deleteHandlers.put(uri, handler);
	}
	
	public static interface EndpointHandler
	{
		void handle(Request req, Response res) throws Throwable;
	}
	
	/** represents a HTTP request */
	public static class Request
	{
		private final HttpExchange e;
		private String request = null;
		private final Map<String, String> params = new HashMap<String, String>();
		
		private Request(HttpExchange e)
		{
			this.e = e;
		}
		
		public String body()
		{
			if(request != null)
				return request;
			
			try
			{
				String line;
				final StringBuffer requestBuffer = new StringBuffer();
				final BufferedReader reader = new BufferedReader(new InputStreamReader(e.getRequestBody()));
				while((line = reader.readLine()) != null)
					requestBuffer.append(line).append("\r\n");
				reader.close();
				return this.request = requestBuffer.toString();
			}
			catch(IOException x)
			{
				x.printStackTrace();
				return new String();
			}
		}
		
		public String getSessionUserName()
		{
			return System.getProperty("user.name");
		}
		
		private void setParam(String key, String value)
		{
			params.put(key, value);
		}
		
		public String getParam(String key)
		{
			return params.get(key);
		}
		
		public List<String> getHeader(String key)
		{
			return e.getRequestHeaders().get(key);
		}
	}
	
	/** manipulator of a HTTP response */
	public static class Response
	{
		private final HttpExchange e;
		private int statusCode = 200;
		
		private Response(HttpExchange e)
		{
			this.e = e;
		}
		
		public void set(String headerKey, String headerValue)
		{
			e.getResponseHeaders().add(headerKey, headerValue);
		}
		
		public Response status(int statusCode)
		{
			this.statusCode = statusCode;
			return this;
		}
		
		public static interface JsonObjectifyable
		{
			Map<String, ?> toJsonObject();
		}
		
		private static final String stringifyJson(Object object)
		{
			final StringBuffer string = new StringBuffer();
			
			if(object == null)
			{
				string.append("null");
			}
			else if(object instanceof JsonObjectifyable)
			{
				final Map<String, ?> keyValuePairs = ((JsonObjectifyable) object).toJsonObject();
				string.append(stringifyJson(keyValuePairs));
			}
			else if(object instanceof Map)
			{
				string.append("{");
				for(Object key : ((Map<?, ?>) object).keySet())
					string.append(stringifyJson(key)).append(":").append(stringifyJson(((Map<?, ?>) object).get(key))).append(",");
				if(((Map<?, ?>) object).size() > 0)
					string.deleteCharAt(string.length() - 1);
				string.append("}");
			}
			else if(object instanceof List)
			{
				string.append("[");
				for(Object item : (List<?>) object)
					string.append(stringifyJson(item)).append(",");
				if(((List<?>) object).size() > 0)
					string.deleteCharAt(string.length() - 1);
				string.append("]");
			}
			else if(object instanceof String)
			{
				string.append("\"");
				string.append(object.toString().replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"));
				string.append("\"");
			}
			else if(object instanceof Date)
			{
				final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
				string.append("\"").append(timestampFormat.format((Date) object)).append("\"");
			}
			else string.append(stringifyJson(object.toString()));
			
			return string.toString();
		}
		
		public void send(Object body) throws IOException
		{
			this.set("Content-Type", "application/json; charset=UTF-8");
			this.send(stringifyJson(body).getBytes("UTF-8"));
		}
		
		public void send(String body) throws IOException
		{
			this.set("Content-Type", "text/plain; charset=UTF-8");
			this.send(body.getBytes("UTF-8"));
		}
		
		public void send(byte[] body) throws IOException
		{
			e.sendResponseHeaders(statusCode, body.length);
			e.getResponseBody().write(body);
			e.getResponseBody().flush();
			e.close();
		}
	}
	
	private final HttpHandler restApiHandler = new HttpHandler()
	{
		/** @return the mime type for the specified file based on its file extension */
		private final String getMimeType(File file)
		{
			try
			{
				switch(file.getName().substring(file.getName().lastIndexOf(".") + 1).toLowerCase())
				{
					case "css":
						return "text/css";
					
					case "htm":
					case "html":
						return "text/html";
					
					case "js":
						return "text/javascript";
					
					case "txt":
						return "text/plain";
				}
			
			}
			catch(Exception x) {}
			
			return "application/octet-stream";
		}
		
		@Override
		public void handle(HttpExchange e) throws IOException
		{
			// serve static file if exists
			if(e.getRequestMethod().equals("GET") && rootDirectory != null)
			{
				File requestedFile = new File(rootDirectory, e.getRequestURI().getPath().substring(1));
				if(requestedFile.exists() && requestedFile.isDirectory())
					requestedFile = new File(requestedFile, "index.html");
				if(requestedFile.exists() && requestedFile.isFile() && requestedFile.canRead())
				{
					e.getResponseHeaders().set("Content-Type", getMimeType(requestedFile));
					e.sendResponseHeaders(200, requestedFile.length());
					
					int read;
					byte[] buffer = new byte[1024];
					final InputStream in = new FileInputStream(requestedFile);
					while((read = in.read(buffer)) > -1)
						e.getResponseBody().write(buffer, 0, read);
					e.getResponseBody().flush();
					in.close();
					e.close();
					return;
				}
			}
			
			// match request with end point patterns
			final Map<String, EndpointHandler> handlers = e.getRequestMethod().equals("GET") ? getHandlers :
														  e.getRequestMethod().equals("POST") ? postHandlers :
														  e.getRequestMethod().equals("PUT") ? putHandlers :
														  e.getRequestMethod().equals("PATCH") ? patchHandlers :
														  e.getRequestMethod().equals("DELETE") ? deleteHandlers :
														  new HashMap<String, EndpointHandler>();
			
			// @see https://github.com/leoregner/embedded-router/blob/master/embedded-router.js
			for(final String uri : handlers.keySet())
				try
				{
					int index = 0;
					String pattern = "";
					final List<String> params = new ArrayList<String>();
					
					final Matcher matcher = Pattern.compile("\\:[a-zA-Z0-9_]*").matcher(uri);
					while(matcher.find())
					{
						params.add(matcher.group().substring(1));
						pattern += Pattern.quote(uri.substring(index, matcher.start())) + "([^\\/\\?]+)";
						index = matcher.end();
					}
					pattern += Pattern.quote(uri.substring(index));
					
					if(e.getRequestURI().getPath().matches(pattern))
					{
						final Request req = new Request(e);
						
						final Matcher patternMatcher = Pattern.compile(pattern).matcher(e.getRequestURI().getPath());
						for(int i = 0; patternMatcher.find() && i < params.size(); ++i)
							req.setParam(params.get(i), patternMatcher.group(i + 1));
						
						handlers.get(uri).handle(req, new Response(e));
						return;
					}
				}
				catch(Throwable x)
				{
					new Response(e).status(500).send("internal server error: " + x.getLocalizedMessage());
					x.printStackTrace();
				}
			
			// no end point found
			new Response(e).status(404).send(e.getRequestURI().getPath() + " not found");
		}
	};
}	