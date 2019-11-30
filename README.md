# Java7 Express
Light-weight Java library for a web server similar to Express in Node.js

# Installation
Using Maven:

```xml
<repositories>
	<repository>
	    <id>jitpack.io</id>
	    <url>https://jitpack.io</url>
	</repository>
</repositories>

<dependency>
    <groupId>com.github.leoregner</groupId>
    <artifactId>java7-express</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```

# How To Use
Example:

```java
import eu.leoregner.express.Express;
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
