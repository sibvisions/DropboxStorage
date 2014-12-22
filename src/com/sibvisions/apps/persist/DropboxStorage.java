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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Proxy.Type;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.rad.io.RemoteFileHandle;
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
import com.dropbox.core.http.StandardHttpRequestor;
import com.sibvisions.rad.persist.AbstractCachedStorage;
import com.sibvisions.util.ArrayUtil;
import com.sibvisions.util.ObjectCache;
import com.sibvisions.util.ProxyUtil;
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

    /** the access token. */
    private String sAccessToken;
    
    /** the initial root path. */
    private String sRootPath;
    
    /** the proxy host. */
    private String sProxyHost;
    
    /** the proxy port. */
    private int iProxyPort;
    
    /** the fetch filetype. */
    private FileType fileType = FileType.File;
    
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
        String sPath = getPath(pFilter);

        System.out.println(sPath);
        
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
    
    @Override
    protected List<Object[]> executeFetch(ICondition pFilter, SortDefinition pSort, int pFromRow, int pMinimumRowCount) throws DataSourceException
    {
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

    @Override
    protected Object[] executeRefetchRow(Object[] pDataRow) throws DataSourceException
    {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    protected Object[] executeInsert(Object[] pDataRow) throws DataSourceException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected Object[] executeUpdate(Object[] pOldDataRow, Object[] pNewDataRow) throws DataSourceException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected void executeDelete(Object[] pDeleteDataRow) throws DataSourceException
    {
        // TODO Auto-generated method stub
        
    }
    
    @Override
    public void writeCSV(OutputStream pStream, String[] pColumnNames, String[] pLabels, ICondition pFilter, SortDefinition pSort, String pSeparator) throws Exception
    {
        // TODO Auto-generated method stub
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
            
            cmd = new ColumnMetaData("NAME", StringDataType.TYPE_IDENTIFIER);
            cmd.setNullable(false);
            
            md.addColumnMetaData(cmd);
            
            cmd = new ColumnMetaData("TYPE", StringDataType.TYPE_IDENTIFIER);
            cmd.setNullable(false);
            cmd.setAllowedValues(new Object[] {FileType.File.toString(), FileType.Folder.toString()});
            
            md.addColumnMetaData(cmd);
            md.addColumnMetaData(new ColumnMetaData("CONTENT", BinaryDataType.TYPE_IDENTIFIER));

            md.removeFeature(Feature.Filter);
            md.removeFeature(Feature.Sort);
            
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
        
        Object[] oRecord;
        
        String sUUID;

        boolean bFile;
        
        for (DbxEntry entry : listing.children)
        {
            if (fileType == FileType.All
                || (fileType == FileType.File && entry.isFile())
                || (fileType == FileType.Folder && entry.isFolder()))
            {
                bFile = entry.isFile();
                
                if (bFile)
                {
                    DropboxFileHandle handle = new DropboxFileHandle(client, entry.path);
                    
                    sUUID = UUID.randomUUID().toString();
                    
                    ObjectCache.put(sUUID, handle);
                }
                else
                {
                    sUUID = null;
                }
                
                oRecord = new Object[] {entry.path, 
                                        entry.name, 
                                        bFile ? FileType.File.toString() : FileType.Folder.toString(),
                                        bFile ? new RemoteFileHandle(entry.name, sUUID) : null};

                pRecords.add(oRecord);
            }
            
            if (entry.isFolder() && pDeep)
            {
                searchRecords(entry.path, pDeep, pRecords);
            }
        }
    }
    
    /**
     * Gets the path from a condition.
     * 
     * @param pCondition the condition
     * @return the path or <code>/</code> if path condition wasn't found
     */
    private String getPath(ICondition pCondition)
    {
        return null;
    }

    /**
     * Sets the initial/default root path.
     * 
     * @param pRootPath the path (unix style)
     */
    public void setRootPath(String pRootPath)
    {
        sRootPath = pRootPath;
    }
    
    /**
     * Gets the initial/default root path.
     * 
     * @return the path (unix style)
     */
    public String getRootPath()
    {
        return sRootPath;
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
