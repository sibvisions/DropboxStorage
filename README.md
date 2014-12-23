Overview
========

A JVx storage implementation for Dropbox.

Usage
=====

Server-side

<pre>
DropboxStorage storage = new DropboxStorage();
storage.setAccessToken(getAccessToken());
storage.setRecursive(true);
storage.setFileType(FileType.All);
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


License
-------

Apache 2.0 (http://www.apache.org/licenses/)


Have fun!
