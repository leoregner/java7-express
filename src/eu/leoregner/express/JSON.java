package eu.leoregner.express;
import java.text.SimpleDateFormat;
import java.util.*;

public class JSON
{
	public static interface JsonObjectifyable
	{
		Map<String, ?> toJsonObject();
	}
	
	/** @return the JSON string representing the specified object */
	public static final String stringify(Object object)
	{
		final StringBuffer string = new StringBuffer();
		
		if(object == null)
		{
			string.append("null");
		}
		
		else if(object instanceof JsonObjectifyable)
		{
			final Map<String, ?> keyValuePairs = ((JsonObjectifyable) object).toJsonObject();
			string.append(stringify(keyValuePairs));
		}
		
		else if(object instanceof Map)
		{
			string.append("{");
			for(Object key : ((Map<?, ?>) object).keySet())
				string.append(stringify(key)).append(":").append(stringify(((Map<?, ?>) object).get(key))).append(",");
			if(((Map<?, ?>) object).size() > 0)
				string.deleteCharAt(string.length() - 1);
			string.append("}");
		}
		
		else if(object instanceof List)
		{
			string.append("[");
			for(Object item : (List<?>) object)
				string.append(stringify(item)).append(",");
			if(((List<?>) object).size() > 0)
				string.deleteCharAt(string.length() - 1);
			string.append("]");
		}
		
		else if(object instanceof String)
		{
			string.append("\"");
			string.append(escapeString(object.toString()));
			string.append("\"");
		}
		
		else if(object instanceof Date)
		{
			final SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
			string.append("\"").append(timestampFormat.format((Date) object)).append("\"");
		}
		
		else string.append(stringify(object.toString()));
		
		return string.toString();
	}
	
	/** JSON-escapes special characters in the specified string */
	private static String escapeString(String in)
	{
		// @see https://stackoverflow.com/questions/18898773/java-escape-json-string
		in = in.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
		in = in.replace("\t", "\\t").replace("\f", "\\f").replace("\b", "\\b");
		
		// @see https://stackoverflow.com/questions/28176578/convert-utf-8-unicode-string-to-ascii-unicode-escaped-string
		final StringBuilder out = new StringBuilder();
		for(int i = 0; i < in.length(); ++i)
		{
			final char ch = in.charAt(i);
			if(ch <= 127) out.append(ch);
			else out.append("\\u").append(String.format("%04x", (int) ch));
		}
		return out.toString();
	}
}