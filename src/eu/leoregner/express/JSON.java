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
			string.append(object.toString().replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("\\", "\\\\"));
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
}