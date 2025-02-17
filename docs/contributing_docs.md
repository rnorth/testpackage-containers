# Contributing to documentation

The Testcontainers for Java documentation is a static site built with [MkDocs](https://www.mkdocs.org/).
We use the [Material for MkDocs](https://squidfunk.github.io/mkdocs-material/) theme, which offers a number of useful extensions to MkDocs.

In addition we use a [custom plugin](https://github.com/rnorth/mkdocs-codeinclude-plugin) for inclusion of code snippets.

We publish our documentation using Netlify. 

## Previewing rendered content

### Using Docker locally

The root of the project contains a `docker-compose.yml` file. Simply run `docker-compose up` and then access the docs at [http://localhost:8000](http://localhost:8000).

### Using Python locally

* Ensure that you have Python 3.8.0 or higher.
* Set up a virtualenv and run `pip install -r requirements.txt` in the `testcontainers-java` root directory.
* Once Python dependencies have been installed, run `mkdocs serve` to start a local auto-updating MkDocs server.

### PR Preview deployments

Note that documentation for pull requests will automatically be published by Netlify as 'deploy previews'.
These deployment previews can be accessed via the `deploy/netlify` check that appears for each pull request.

## Codeincludes

The Gradle project under `docs/examples` is intended to hold compilable, runnable example code that can be included as
snippets into the documentation at build-time.

As a result, we can have more confidence that code samples shown in the documentation is valid.

We use a custom plugin for MkDocs to include snippets into our docs.

A codeinclude block will resemble a regular markdown link surrounded by a pair of XML comments, e.g.:

<!-- 
To prevent this from being rendered as a codeinclude when rendering this page, we use HTML tags.
See this in its rendered form to understand its actual appearance, or look at other pages in the
docs.
-->

<pre><code>&lt;!--codeinclude--&gt;
[Human readable title for snippet](./relative_path_to_example_code.java) targeting_expression
&lt;!--/codeinclude--&gt;
</code></pre>

Where `targeting_expression` could be:

* `block:someString` or
* `inside_block:someString`

If these are provided, the macro will seek out any line containing the token `someString` and grab the next curly brace
delimited block that it finds. `block` will grab the starting line and closing brace, whereas `inside_block` will omit 
these.

e.g., given:
```java

public class FooService {

    public void doFoo() {
        foo.doSomething();
    }
    
    ...

```

If we use `block:doFoo` as our targeting expression, we will have the following content included into our page:

```java
public void doFoo() {
    foo.doSomething();
}
```

Whereas using `inside_block:doFoo` we would just have the inner content of the method included:

```java
foo.doSomething();
```

Note that:

* Any code included will have its indentation reduced
* Every line in the source file will be searched for an instance of the token (e.g. `doFoo`). If more than one line
  includes that token, then potentially more than one block could be targeted for inclusion. It is advisable to use a
  specific, unique token to avoid unexpected behaviour.
  
When we wish to include a section of code that does not naturally appear within braces, we can simply insert our token,
with matching braces, in a comment. 
While a little ugly, this has the benefit of working in any context and is easy to understand. 
For example:

```java
public class FooService {

    public void boringMethod() {
        doSomethingBoring();
        
        // doFoo {
        doTheThingThatWeActuallyWantToShow();
        // }
    }


``` 
