Overview
========

A JVx storage implementation for Dropbox.

Usage
=====

### Flat

**Server-side**

```java
public DropboxStorage getFiles() throws Exception
{
    DropboxStorage storage = (DropboxStorage)get("files");
    
    if (storage == null)
    {
        storage = new DropboxStorage();
        storage.setAccessToken(SessionContext.getCurrentSessionConfig().
                                   getProperty("/application/dropbox/accessToken"));
        storage.setFileType(FileType.All);
        //use "flat" style
        storage.setRecursive(true);
        storage.open();
        
        put("files", storage);
    }
    
    return storage;
}
```

**Client-side**

```java
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
book.setValue("CONTENT", 
              FileUtil.getContent(ResourceUtil.getResourceAsStream(resourcepath)));
```

### Master/Detail or Self-joined###

**Server-side**

```java
public DropboxStorage getFiles() throws Exception
{
    DropboxStorage storage = (DropboxStorage)get("files");
    
    if (storage == null)
    {
        storage = new DropboxStorage();
        storage.setAccessToken(SessionContext.getCurrentSessionConfig().
                                   getProperty("/application/dropbox/accessToken"));
        storage.setFileType(FileType.File);
        storage.open();
        
        put("files", storage);
    }
    
    return storage;
}

public DropboxStorage getFolders() throws Exception
{
    DropboxStorage storage = (DropboxStorage)get("folders");
    
    if (storage == null)
    {
        storage = new DropboxStorage();
        storage.setAccessToken(SessionContext.getCurrentSessionConfig().
                                   getProperty("/application/dropbox/accessToken"));
        storage.setFileType(FileType.Folder);
        storage.open();
        
        put("folders", storage);
    }
    
    return storage;
}
```

**Client-side**

```java
RemoteDataBook rdbFolder.setName("folders");
rdbFolder.setDataSource(getDataSource());
rdbFolder.setMasterReference(new ReferenceDefinition(new String[] { "PARENT_FOLDER" }, 
                                                     rdbFolder, 
                                                     new String[] { "FOLDER" }));
rdbFolder.open();
		
rdbFolder.getRowDefinition().setColumnView(ITreeControl.class, 
                                           new ColumnView(new String[] { "NAME" }));

rdbFiles.setName("files");
rdbFiles.setDataSource(getDataSource());
rdbFiles.setMasterReference(new ReferenceDefinition(new String[] { "FOLDER" }, 
                                                    rdbFolder, 
                                                    new String[] { "FOLDER" }));
rdbFiles.open();
		
rdbFiles.getRowDefinition().setColumnView(ITableControl.class, 
                                          new ColumnView(new String[] { "NAME" }));
```

The test cases need a config.xml in the project directory (same location as README.md).
The file should contain:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<config>
  <app accessToken="YOUR_ACCESS_TOKEN" />
</config>
```

Special folders
===============

- The **dropbox** directory is a Dropbox template, used for test cases.
- The **doc** directory contains source archives for libs.


License
-------

Apache 2.0 (http://www.apache.org/licenses/)


Have fun!
