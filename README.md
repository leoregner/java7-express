# Java7 Express
Light-weight Java library for a web server similar to Express in Node.js

# How To Use

```java
import eu.leoregner.express.Express.*;

public class Test
{
	public static void main(String[] args) throws Exception
	{
		Express express = new Express();

		express.get("/home", new EndpointHandler()
		{
			public void handle(Request req, Response res) throws Throwable
			{
				res.send("Hello!");
			}
		});
	}
}
```
