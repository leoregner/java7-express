package eu.leoregner.express;
import java.io.*;
import java.util.*;
import java.util.regex.*;
import java.net.URLDecoder;
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
	public Express(int port) throws IOException
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
		private ByteArrayOutputStream request = null;
		private final Map<String, UploadedFile> uploads = new HashMap<String, UploadedFile>();
		private final Map<String, String> params = new HashMap<String, String>(), formData = new HashMap<String, String>();
		
		private Request(HttpExchange e)
		{
			this.e = e;
		}
		
		/** @return the request body as a string */
		public String body()
		{
			if(request == null)
			{
				try
				{
					int read;
					byte[] buffer = new byte[1024];
					final ByteArrayOutputStream requestBuffer = new ByteArrayOutputStream();
					
					final InputStream reader = e.getRequestBody();
					while((read = reader.read(buffer)) > -1)
						requestBuffer.write(buffer, 0, read);
					reader.close();
					
					this.request = requestBuffer;
				}
				catch(IOException x)
				{
					x.printStackTrace();
					return new String();
				}
				
				try
				{
					// parse form data
					if(getHeader("content-type").get(0).toLowerCase().contains("application/x-www-form-urlencoded"))
						for(String pair : new String(this.request.toByteArray(), "UTF-8").trim().split("&"))
						{
							String key = pair.contains("=") ? pair.split("=")[0] : pair, charset = "ISO-8859-1";
							String value = pair.length() > key.length() ? URLDecoder.decode(pair.substring(key.length() + 1), charset) : "";
							formData.put(key, value);
						}
					
					// parse multipart data
					else if(getHeader("content-type").get(0).toLowerCase().contains("multipart/form-data"))
					{
						String contentType = getHeader("content-type").get(0);
						String data = new String(this.request.toByteArray(), "ASCII");
						String boundary = "--" + contentType.substring(contentType.toLowerCase().indexOf("boundary=") + 9);
						String[] parts = data.split(Pattern.quote(boundary));
						
						for(int start = 0, i = 0; i < parts.length; ++i)
						{
							int end = start + parts[i].length();
							int innerStart = i > 0 ? start + 2 : start, innerEnd = i > 0 ? end - 2 : end, bodyStart = innerStart;
							
							// extract header of part
							String[] headers = new String[0];
							String part = data.substring(innerStart, innerEnd);
							if(part.contains("\r\n\r\n"))
							{
								headers = part.split("\r\n\r\n")[0].split("\r\n");
								bodyStart += part.indexOf("\r\n\r\n") + 4;
							}
							
							// extract binary body of part
							byte[] partBody = Arrays.copyOfRange(this.request.toByteArray(), bodyStart, innerEnd);
							
							// store uploaded files and process form data
							for(String header : headers)
								if(header.toLowerCase().contains("content-disposition") && !header.toLowerCase().contains("filename"))
								{
									String name = extractFromHeader(header, "name");
									formData.put(name, new String(partBody, "ISO-8859-1"));
								}
								else
								{
									String name = extractFromHeader(header, "name");
									String fileName = extractFromHeader(header, "filename");
									uploads.put(name, new UploadedFile(fileName, partBody));
								}
							
							start = end + boundary.length();
						}
					}
				}
				catch(Throwable x)
				{
					x.printStackTrace();
				}
			}
			
			// return value is the full request body as string if interpreted using UTF-8
			try { return new String(request.toByteArray(), "UTF-8"); }
			catch(UnsupportedEncodingException x) { return null; }
		}
		
		/** @return the argument value of the specified key within the specified HTTP header */
		private static final String extractFromHeader(String header, String key)
		{
			int start = header.indexOf(key + "=\"");
			
			if(start == -1)
				return null;
			
			String value = header.substring(start + key.length() + 2);
			int end = value.length();
			
			if(value.length() > 0)
				if(value.charAt(0) == '\"')
					end = 0;
				else
				{
					final java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("[^\\\\]\\\"").matcher(value);
					if(matcher.find())
						end = matcher.start();
					++end;
				}
			
			return value.substring(0, end);
		}
		
		/** @return the post form data value with the specified key */
		public String data(String key)
		{
			body();
			return formData.get(key);
		}
		
		/** @return the uploaded file with the specified key */
		public UploadedFile file(String key)
		{
			body();
			return uploads.get(key);
		}
		
		/** @return the value of the cookie with the specified name */
		public String cookie(String name)
		{
			try
			{
				for(String header : getHeader("Cookie"))
					for(String cookie : header.split(";"))
						try
						{
							cookie = cookie.trim();
							String key = cookie.split("=")[0];
							String value = java.net.URLDecoder.decode(cookie.substring(name.length() + 1), "UTF-8");
							
							if(key.equals(name))
								return java.net.URLDecoder.decode(value, "UTF-8");
						}
						catch(UnsupportedEncodingException x) {}
			}
			catch(NullPointerException x) {}
			
			return null;
		}
		
		private void setParam(String key, String value)
		{
			params.put(key, value);
		}
		
		/** @return the value of the URI parameter with the specified placeholder name */
		public String getParam(String key)
		{
			return params.get(key);
		}
		
		/** @return all request headers with the specified key */
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
		public boolean doNotCloseConnection = false;
		
		private Response(HttpExchange e)
		{
			this.e = e;
		}
		
		/** sets a HTTP header field */
		public Response set(String headerKey, String headerValue)
		{
			e.getResponseHeaders().add(headerKey, headerValue);
			return this;
		}
		
		/** sets the HTTP response status code */
		public Response status(int statusCode)
		{
			this.statusCode = statusCode;
			return this;
		}
		
		/** sets a cookie */
		public Response cookie(String name, String value)
		{
			return this.cookie(name, value, null);
		}
		
		/** sets a cookie with the specified semicolon-separated attributes */
		public Response cookie(String name, String value, String attributes)
		{
			try
			{
				value = java.net.URLEncoder.encode(value, "UTF-8");
				attributes = (attributes == null) ? new String() : (";" + attributes);
				set("Set-Cookie", name + "=" + value + attributes);
			}
			catch(UnsupportedEncodingException x) {}
			
			return this;
		}
		
		/** sends the specified object as JSON string response */
		public void send(Object body) throws IOException
		{
			this.set("Content-Type", "application/json; charset=UTF-8");
			this.send(JSON.stringify(body).getBytes("UTF-8"));
		}
		
		/** sends the specified string as plain text response */
		public void send(String body) throws IOException
		{
			this.set("Content-Type", "text/plain; charset=UTF-8");
			this.send(body.getBytes("UTF-8"));
		}
		
		/** sends the specified bytes as response */
		public void send(byte[] body) throws IOException
		{
			e.sendResponseHeaders(statusCode, body.length);
			
			final OutputStream responseBody = e.getResponseBody();
			responseBody.write(body);
			responseBody.flush();
			responseBody.close();
			e.close();
		}
	}
	
	public static class UploadedFile
	{
		private final String fileName;
		private final byte[] data;
		
		private UploadedFile(String name, byte[] data)
		{
			this.fileName = name;
			this.data = data;
		}
		
		/** @return the original file name */
		public String getFileName()
		{
			return fileName;
		}
		
		/** writes the uploaded file into a temporary file on the disk */
		public File tmp() throws IOException
		{
			String niceName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf(".")) : fileName;
			String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".")) : new String();
			
			final File tmp = File.createTempFile(niceName, extension);
			tmp.deleteOnExit();
			mv(tmp);
			return tmp;
		}
		
		/** writes the uploaded file into a file at the specified location */
		public void mv(File file) throws IOException
		{
			final OutputStream writer = new FileOutputStream(file);
			writer.write(data);
			writer.flush();
			writer.close();
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
					try
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
					catch(Throwable x)
					{
						x.printStackTrace();
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
					
					final Matcher matcher = Pattern.compile("\\:[a-zA-Z0-9_]+").matcher(uri);
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
						while(patternMatcher.find())
							for(int i = 0; i < patternMatcher.groupCount(); ++i)
								req.setParam(params.get(i), patternMatcher.group(i + 1));
						
						final Response res = new Response(e);
						handlers.get(uri).handle(req, res);
						if(!res.doNotCloseConnection)
							e.close();
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
