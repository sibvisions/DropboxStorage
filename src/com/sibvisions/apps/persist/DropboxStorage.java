/*
 * Copyright 2014 SIB Visions GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * 
 * History
 *
 * 11.11.2014 - [JR] - creation
 */
package com.sibvisions.apps.persist;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.rad.io.IFileHandle;
import javax.rad.io.RemoteFileHandle;
import javax.rad.model.RowDefinition;
import javax.rad.model.SortDefinition;
import javax.rad.model.condition.Equals;
import javax.rad.model.condition.ICondition;
import javax.rad.model.condition.OperatorCondition;
import javax.rad.model.datatype.BinaryDataType;
import javax.rad.model.datatype.StringDataType;
import javax.rad.persist.ColumnMetaData;
import javax.rad.persist.DataSourceException;
import javax.rad.persist.MetaData;
import javax.rad.persist.MetaData.Feature;

import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.dropbox.core.DbxHost;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.DbxWriteMode;
import com.dropbox.core.http.StandardHttpRequestor;
import com.sibvisions.rad.model.DataBookCSVExporter;
import com.sibvisions.rad.model.mem.MemDataBook;
import com.sibvisions.rad.persist.AbstractCachedStorage;
import com.sibvisions.util.ArrayUtil;
import com.sibvisions.util.ObjectCache;
import com.sibvisions.util.ProxyUtil;
import com.sibvisions.util.type.CommonUtil;
import com.sibvisions.util.type.FileUtil;
import com.sibvisions.util.type.StringUtil;

/**
 * The <code>DropboxStorage</code> is a cached storage for files which were store in a dropbox.
 * 
 * @author René Jahn
 */
public class DropboxStorage extends AbstractCachedStorage
{
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Class members
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /** the file type enumeration. */
    public enum FileType
    {
        /** file and directory. */
        All,
        /** only file. */
        File,
        /** only directory. */
        Folder
    }
    
    /** the dropbox client. */
    private DbxClient client;
    
    /** the metadata. */
    private MetaData metadata;

    /** the export databook. */
    private MemDataBook mdbExport;

    /** the access token. */
    private String sAccessToken;
    
    /** the initial root path. */
    private String sRootPath;
    
    /** the proxy host. */
    private String sProxyHost;
    
    /** the fetch filetype. */
    private FileType fileType = FileType.File;

    /** the proxy port. */
    private int iProxyPort;
    
    /** whether this storage is open. */
    private boolean bOpen;
    
    /** whether fetch should act recursive. */
    private boolean bRecursive;
    
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Initialization
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    
    /**
     * Creates a new instance of <code>DropboxStorage</code>.
     */
    public DropboxStorage()
    {
    }
    
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Interface implementation
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    
    /**
     * {@inheritDoc}
     */
    public MetaData getMetaData() throws DataSourceException
    {
        if (!isOpen())
        {
            throw new DataSourceException("Drobpox storage isn't open!");         
        }
        
        return metadata;
    }

    /**
     * {@inheritDoc}
     */
    public int getEstimatedRowCount(ICondition pFilter) throws DataSourceException
    {
        return 0;
    }

    /**
     * Closes this storage.
     * 
     * @throws Throwable if closing failed
     */
    public void close() throws Throwable
    {
        if (bOpen)
        {
            metadata = null;
        }
    }
    
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Overwritten methods
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected List<Object[]> executeFetch(ICondition pFilter, SortDefinition pSort, int pFromRow, int pMinimumRowCount) throws DataSourceException
    {
        if (!isOpen())
        {
            throw new DataSourceException("DropboxStorage isn't open!");         
        }
        
        boolean bDeepSearch = bRecursive;
        
        String sDir = (String)getEqualsValue(pFilter, "FOLDER");
        
        if (StringUtil.isEmpty(sDir))
        {
            sDir = sRootPath;
        }
        else
        {
            //search specific path -> don't search recursive
            bDeepSearch = false;
        }
        
        if (StringUtil.isEmpty(sDir))
        {
            sDir = "/";
        }
        
        try
        {
            List<Object[]> liRecords = new ArrayUtil<Object[]>();

            searchRecords(sDir, bDeepSearch, liRecords);
            
            liRecords.add(null);
            
            return liRecords;
        }
        catch (Exception e)
        {
            throw new DataSourceException("Can't read folder!", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object[] executeRefetchRow(Object[] pDataRow) throws DataSourceException
    {
        if (!isOpen())
        {
            throw new DataSourceException("DropboxStorage isn't open!");         
        }

        removeFileHandle(pDataRow);
        
        //the only thing we can do, is to create a new file handle (maybe something has changed)
        if (FileType.File.toString().equals(pDataRow[4]))
        {
            return new Object[] {pDataRow[0], 
                                 pDataRow[1], 
                                 pDataRow[2], 
                                 pDataRow[3], 
                                 pDataRow[4], 
                                 createFileHandle((String)pDataRow[0], (String)pDataRow[3])};
        }
        else
        {
            return new Object[] {pDataRow[0], 
                                 pDataRow[1], 
                                 pDataRow[2], 
                                 null, 
                                 pDataRow[4], 
                                 null};
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    protected Object[] executeInsert(Object[] pDataRow) throws DataSourceException
    {
        if (!isOpen())
        {
            throw new DataSourceException("DropboxStorage isn't open!");         
        }

        if ((pDataRow[4] == null && pDataRow[3] != null)
            || FileType.File.toString().equals(pDataRow[4]))
        {
            if (pDataRow[3] == null)
            {
                throw new DataSourceException("Can't save file because file name is undefined!");
            }
            
            String sPath = buildPath(pDataRow);
            
            Object data = pDataRow[5];

            DbxEntry.File file;
            
            try
            {
                file = save(sPath, data);
                
                String sFolder = getFolder(file.path);
                
                return new Object[] {file.path,
                                     getParentFolder(sFolder),
                                     sFolder, 
                                     file.name, 
                                     FileType.File.toString(), 
                                     createFileHandle(file.path, file.name)};
            }
            catch (Exception ex)
            {
                if (ex instanceof DataSourceException)
                {
                    throw (DataSourceException)ex;
                }
                
                throw new DataSourceException("Couldn't create file '" + sPath + "'!", ex);
            }
        }
        else
        {
            if (pDataRow[2] == null)
            {
                throw new DataSourceException("Can't create folder because path is undefined!");
            }

            String sFolder = (String)pDataRow[2];
            
            if (StringUtil.isEmpty(sFolder))
            {
                sFolder = "/";
            }
            
            try
            {
                DbxEntry.Folder folder = client.createFolder(sFolder);
                
                return new Object[] {folder.path, getParentFolder(folder.path), folder.path, null, FileType.Folder.toString(), null};
            }
            catch (Exception ex)
            {
                throw new DataSourceException("Couldn't create folder '" + sFolder + "'!", ex);
            }
        }
    }

    @Override
    protected Object[] executeUpdate(Object[] pOldDataRow, Object[] pNewDataRow) throws DataSourceException
    {
        if (!isOpen())
        {
            throw new DataSourceException("DropboxStorage isn't open!");         
        }

        if (!CommonUtil.equals(pOldDataRow[4], pNewDataRow[4]))
        {
            throw new DataSourceException("Can't change file type '" + pOldDataRow[4] + "' to '" + pNewDataRow[4] + "'!");
        }
        
        String sOldPath = buildPath(pOldDataRow);
        String sNewPath = buildPath(pNewDataRow);
        
        Object[] oResult;
        
        if (!CommonUtil.equals(sOldPath, sNewPath))
        {
            try
            {
                //file exists?
                if (client.getMetadata(sNewPath) != null)
                {
                    //try to delete
                    client.delete(sNewPath);
                }
                
                DbxEntry entry = client.move(sOldPath, sNewPath);

                if (entry != null)
                {
                    removeFileHandle(pOldDataRow);
                    removeFileHandle(pNewDataRow);
                    
                    oResult = createRecord(entry);
                }
                else
                {
                    throw new DataSourceException("Can't move file '" + sOldPath + "' to '" + sNewPath + "'!");
                }
            }
            catch (Exception ex)
            {
                if (ex instanceof DataSourceException)
                {
                    throw (DataSourceException)ex;
                }
                
                throw new DataSourceException("Can't move file '" + sOldPath + "' to '" + sNewPath + "'!", ex);
            }
        }
        else
        {
            oResult = pNewDataRow;
        }

        if (!CommonUtil.equals(pOldDataRow[5], pNewDataRow[5]))
        {
            try
            {
                DbxEntry.File file = save(sNewPath, pNewDataRow[5]);

                removeFileHandle(oResult);
                
                oResult[5] = createFileHandle(file);
            }
            catch (Exception ex)
            {
                throw new DataSourceException("Can't change content of file '" + sNewPath + "'!", ex);
            }
        }
        
        return oResult;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void executeDelete(Object[] pDeleteDataRow) throws DataSourceException
    {
        if (!isOpen())
        {
            throw new DataSourceException("DropboxStorage isn't open!");         
        }

        try
        {
            client.delete((String)pDeleteDataRow[0]);
            
            removeFileHandle(pDeleteDataRow);
        }
        catch (Exception ex)
        {
            throw new DataSourceException("Couldn't delete file '" + pDeleteDataRow[0] + "'!", ex);
        }
    }
    
    @Override
    public void writeCSV(OutputStream pStream, String[] pColumnNames, String[] pLabels, ICondition pFilter, SortDefinition pSort, String pSeparator) throws Exception
    {
        if (!isOpen())
        {
            throw new DataSourceException("DropboxStorage isn't open!");         
        }

        if (mdbExport == null)
        {
            RowDefinition rowdef = new RowDefinition();
            
            for (int i = 0, cnt = metadata.getColumnMetaDataCount(); i < cnt; i++)
            {
                rowdef.addColumnDefinition(ColumnMetaData.createColumnDefinition(metadata.getColumnMetaData(i)));
            }
            
            mdbExport = new MemDataBook(rowdef);
            mdbExport.setName("export");
            mdbExport.open();
        }
        else
        {
            mdbExport.close();
            mdbExport.open();
        }
        
        
        for (Object[] record : fetch(pFilter, null, 0, -1))
        {
            if (record != null)
            {
                mdbExport.insert(false);
                mdbExport.setValues(null, record);
            }
        }
        
        DataBookCSVExporter.writeCSV(mdbExport, pStream, pColumnNames, pLabels, pFilter, pSort, pSeparator);        
    }

    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // User-defined methods
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Opens this storage.
     */
    public void open()
    {
        if (!bOpen)
        {
            String sHost = sProxyHost;
            int iPort = iProxyPort;
            
            try
            {
                Proxy proxy = ProxyUtil.getSystemProxy("https://" + DbxHost.Default.api);
                
                if (proxy != null)
                {
                    InetSocketAddress addr = (InetSocketAddress)proxy.address();
                    
                    sHost = addr.getHostName();
                    iPort = addr.getPort();
                }
            }
            catch (Exception e)
            {
                debug(e);
            }

            if (StringUtil.isEmpty(sHost))
            {
                sHost = System.getProperty("http.proxyHost");
                
                String sPort = System.getProperty("http.proxyPort");
                
                if (sHost == null)
                {
                    sHost = System.getProperty("https.proxyHost");
                    sPort = System.getProperty("https.proxyPort");
                }
                
                try
                {
                    iPort = Integer.parseInt(sPort);
                }
                catch (Exception e)
                {
                    iPort = 0;
                }
            }
            
            DbxRequestConfig config;
            
            if (!StringUtil.isEmpty(sHost) && iPort > 0)
            {
                config = new DbxRequestConfig("JVx", Locale.getDefault().toString(), 
                                              new StandardHttpRequestor(new Proxy(Type.HTTP, new InetSocketAddress(sHost, iPort))));
            }
            else
            {
                config = new DbxRequestConfig("JVx", Locale.getDefault().toString());
            }
            
            client = new DbxClient(config, sAccessToken);            

            MetaData md = new MetaData();

            ColumnMetaData cmd = new ColumnMetaData("PATH", StringDataType.TYPE_IDENTIFIER);
            cmd.setNullable(false);
            
            md.addColumnMetaData(cmd);
            
            cmd = new ColumnMetaData("PARENT_FOLDER", StringDataType.TYPE_IDENTIFIER);
            cmd.setNullable(false);
            
            md.addColumnMetaData(cmd);

            cmd = new ColumnMetaData("FOLDER", StringDataType.TYPE_IDENTIFIER);
            cmd.setNullable(false);
            
            md.addColumnMetaData(cmd);

            cmd = new ColumnMetaData("NAME", StringDataType.TYPE_IDENTIFIER);
            cmd.setNullable(false);
            
            md.addColumnMetaData(cmd);
            
            cmd = new ColumnMetaData("TYPE", StringDataType.TYPE_IDENTIFIER);
            cmd.setNullable(false);
            cmd.setAllowedValues(new Object[] {FileType.File.toString(), FileType.Folder.toString()});
            
            md.addColumnMetaData(cmd);

            cmd = new ColumnMetaData("CONTENT", BinaryDataType.TYPE_IDENTIFIER);
            cmd.setFetchLargeObjectsLazy(true);
            
            md.addColumnMetaData(cmd);

            md.removeFeature(Feature.Filter);
            md.removeFeature(Feature.Sort);
            
            md.setPrimaryKeyColumnNames(new String[] {"PATH"});
            
            metadata = md;            
            
            bOpen = true;
        }
    }

    /**
     * Gets whether this storage is open.
     * 
     * @return <code>true</code> if this storage was opened, <code>false</code> otherwise
     */
    public boolean isOpen()
    {
        return bOpen;
    }
    
    /**
     * Search dropbox records.
     * 
     * @param pFolder the folder to search
     * @param pDeep <code>true</code> to search in sub folders as well
     * @param pRecords the found records
     * @throws Exception if iterating folders fails
     */
    private void searchRecords(String pFolder, boolean pDeep, List<Object[]> pRecords) throws Exception
    {
        DbxEntry.WithChildren listing = client.getMetadataWithChildren(pFolder);
        
        for (DbxEntry entry : listing.children)
        {
            if (fileType == FileType.All
                || (fileType == FileType.File && entry.isFile())
                || (fileType == FileType.Folder && entry.isFolder()))
            {
                pRecords.add(createRecord(entry));
            }
            
            if (entry.isFolder() && pDeep)
            {
                searchRecords(entry.path, pDeep, pRecords);
            }
        }
    }
    
    /**
     * Creates a record for the given entry.
     * 
     * @param pEntry the remote entry
     * @return the record
     */
    private Object[] createRecord(DbxEntry pEntry)
    {
        boolean bFile = pEntry.isFile();

        String sDirectory = bFile ? getFolder(pEntry.path) : pEntry.path;
        
        return new Object[] {pEntry.path,
                             getParentFolder(sDirectory),
                             sDirectory,
                             bFile ? pEntry.name : null, 
                             bFile ? FileType.File.toString() : FileType.Folder.toString(),
                             bFile ? createFileHandle(pEntry.asFile()) : null};
    }
    
    /**
     * Creates a cached file handle for lazy loading.
     * 
     * @param pFile the file
     * @return the {@link RemoteFileHandle}
     */
    private RemoteFileHandle createFileHandle(DbxEntry.File pFile)
    {
        DropboxFileHandle handle = new DropboxFileHandle(client, pFile);
        
        String sUUID = UUID.randomUUID().toString();
        
        ObjectCache.put(sUUID, handle);
        
        return new RemoteFileHandle(pFile.name, sUUID);
    }

    /**
     * Creates a cached file handle for lazy loading.
     * 
     * @param pPath the file path
     * @param pName the file name
     * @return the {@link RemoteFileHandle}
     */
    private RemoteFileHandle createFileHandle(String pPath, String pName)
    {
        DropboxFileHandle handle = new DropboxFileHandle(client, pPath);
        
        String sUUID = UUID.randomUUID().toString();
        
        ObjectCache.put(sUUID, handle);

        return new RemoteFileHandle(pName, sUUID);
    }
    
    /**
     * Removes a file handle from the cache.
     * 
     * @param pRecord the record information. The [5] element should be an instance of {@link RemoteFileHandle} in
     *                order to remove the object from the cache.
     */
    private void removeFileHandle(Object[] pRecord)
    {
        if (pRecord[4] instanceof RemoteFileHandle)
        {
            Object oKey = ((RemoteFileHandle)pRecord[5]).getObjectCacheKey();
            
            if (oKey != null)
            {
                ObjectCache.remove(oKey);
            }
        }
    }
    
    /**
     * Gets the folder name from the given path.
     * 
     * @param pPath the path
     * @return the directory name
     */
    private String getFolder(String pPath)
    {
        String sPath = FileUtil.getDirectory(pPath);
        
        if (sPath == null)
        {
            sPath = "/";
        }
        else if (!sPath.startsWith("/"))
        {
            sPath = "/" + sPath;
        }
        
        return sPath;
    }
    
    /**
     * Gets the parent folder name for the given directory.
     * 
     * @param pPath the directory
     * @return the parent directory
     */
    private String getParentFolder(String pPath)
    {
        if ("/".equals(pPath))
        {
            return null;
        }
        
        return getFolder(pPath);
    }
    
    /**
     * Creates a path with given record.
     * 
     * @param pDataRow the record
     * @return the path
     */
    private String buildPath(Object[] pDataRow)
    {
        String sPath = (String)pDataRow[2];
        String sName = (String)pDataRow[3];
        
        if (StringUtil.isEmpty(sPath))
        {
            sPath = "/";
        }
        
        if (!StringUtil.isEmpty(sName))
        {
            if (sPath.endsWith("/"))
            {
                sPath += sName;
            }
            else
            {
                sPath += "/" + sName;
            }
        }
        
        return sPath;
    }

    /**
     * Saves a file.
     * 
     * @param pPath the path
     * @param pContent the new content
     * @return the saved file
     * @throws Exception if saving failed
     */
    private DbxEntry.File save(String pPath, Object pContent) throws Exception
    {
        if (pContent == null)
        {
            return client.uploadFile(pPath, DbxWriteMode.force(), 0, new ByteArrayInputStream(new byte[0]));
        }
        else if (pContent instanceof byte[])
        {
            return client.uploadFile(pPath, DbxWriteMode.force(), ((byte[])pContent).length, new ByteArrayInputStream((byte[])pContent));
        }
        else if (pContent instanceof IFileHandle)
        {
            return client.uploadFile(pPath, DbxWriteMode.force(), ((IFileHandle)pContent).getLength(), ((IFileHandle)pContent).getInputStream());
        }
        else if (pContent instanceof File)
        {
            FileInputStream fis = new FileInputStream((File)pContent);
            
            try
            {
                return client.uploadFile(pPath, DbxWriteMode.force(), ((File)pContent).length(), fis);
            }
            finally
            {
                CommonUtil.close(fis);
            }
        }
        else if (pContent instanceof InputStream)
        {
            return client.uploadFile(pPath, DbxWriteMode.force(), -1, (InputStream)pContent);
        }
        else
        {
            throw new DataSourceException("Unsupportet content type: " + pContent.getClass().getName());
        }
    }
    
    /**
     * Sets the access token.
     * 
     * @param pAccessToken the access token
     */
    public void setAccessToken(String pAccessToken)
    {
        sAccessToken = pAccessToken;
    }
    
    /**
     * Gets the access token.
     * 
     * @return the access token
     */
    public String getAccessToken()
    {
        return sAccessToken;
    }

    /**
     * Sets the file type. The file type will be used for listing dropbox records.
     * 
     * @param pType the {@link FileType}
     */
    public void setFileType(FileType pType)
    {
        fileType = pType;
    }
    
    /**
     * Gets the file type.
     * 
     * @return the {@link FileType}
     * @see #setFileType(FileType)
     */
    public FileType getFileType()
    {
        return fileType;
    }
    
    /** 
     * Sets the root folder.
     * 
     * @param pPath the (absolute) folder name/path
     */
    public void setRootFolder(String pPath)
    {
        sRootPath = pPath;
    }
    
    /**
     * Gets the root folder.
     * 
     * @return the folder name/path
     */
    public String getRootFolder()
    {
        return sRootPath;
    }

    /**
     * Gets a value from the given filter.
     * 
     * @param pFilter the filter
     * @param pColumn the column name 
     * @return the value or <code>null</code> if the column was not found
     */
    private Object getEqualsValue(ICondition pFilter, String pColumn)
    {
        if (pFilter instanceof OperatorCondition)
        {
            for (ICondition cond : ((OperatorCondition)pFilter).getConditions())
            {
                if (cond instanceof Equals)
                {
                    if (pColumn.equals(((Equals)cond).getColumnName()))
                    {
                        return ((Equals)cond).getValue();
                    }
                }
            }
        }
        else if (pFilter instanceof Equals)
        {
            if (pColumn.equals(((Equals)pFilter).getColumnName()))
            {
                return ((Equals)pFilter).getValue();
            }
        }
        
        return null;
    }    
    
    /**
     * Sets whether fetching should act recursive. The search results will be recursive if no specific path was
     * configured via fetch condition.
     * 
     * @param pRecursive <code>true</code> to search directories recursive, <code>false</code> to search
     *                   only the given directory
     */
    public void setRecursive(boolean pRecursive)
    {
        bRecursive = pRecursive;
    }
    
    /**
     * Gets whether fetching should act recursive.
     * 
     * @return <code>true</code> if directories will be iterated recursively, <code>false</code> otherwise
     */
    public boolean isRecursive()
    {
        return bRecursive;
    }
    
}   // DropboxStorage
