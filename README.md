Overview
========

A JVx storage implementation for Dropbox.

Usage
=====

Server-side

<pre>
DropboxStorage storage = new DropboxStorage();
storage.setAccessToken(getAccessToken());
storage.open();
</pre>

Client-side

<pre>
RemoteDataBook book = new RemoteDataBook();
book.setName(storagename);
book.setDataSource(getDataSource());
book.open();

//save first file
book.setSelectedRow(book.searchNext(new Equals("TYPE", "File"));

IFileHandle file = (IFileHandle)book.getValue("CONTENT");
FileUtil.save(new File(book.getValueAsString("NAME")), file.getInputStream());

//add new file (image)
book.insert(false);
book.setValue("FOLDER", "/");
book.setValue("NAME", "newimage.png");
book.setValue("CONTENT", FileUtil.getContent(ResourceUtil.getResourceAsStream(resourcepath)));
</pre>

It's also possible to create master/detail relations with DropboxStorage.

The test cases need a config.xml in the project directory (same location as README.md).
The file should contain:

<pre>
&lt;?xml version="1.0" encoding="UTF-8"?&gt;

&lt;config&gt;
  &lt;app accessToken="YOUR_ACCESS_TOKEN" /&gt;
&lt;/config&gt;
</pre>

The dropbox directory is a template, used for test cases.


License
-------

Apache 2.0 (http://www.apache.org/licenses/)


Have fun!
