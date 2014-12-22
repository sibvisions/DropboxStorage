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
import java.util.List;

import javax.rad.model.SortDefinition;
import javax.rad.model.condition.ICondition;
import javax.rad.persist.DataSourceException;
import javax.rad.persist.MetaData;

import com.sibvisions.rad.persist.AbstractCachedStorage;

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
    
    /** the metadata. */
    private MetaData metadata;

    /** the initial root path. */
    private String sRootPath;
    
    /** whether this storage is open. */
    private boolean bOpen;
    
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
    public void writeCSV(OutputStream pStream, String[] pColumnNames, String[] pLabels, ICondition pFilter, SortDefinition pSort, String pSeparator) throws Exception
    {
        // TODO Auto-generated method stub
        
    }

    @Override
    protected Object[] executeRefetchRow(Object[] pDataRow) throws DataSourceException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    protected List<Object[]> executeFetch(ICondition pFilter, SortDefinition pSort, int pFromRow, int pMinimumRowCount) throws DataSourceException
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
    
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // User-defined methods
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Opens this storage.
     */
    public void open()
    {
        MetaData md = new MetaData();
       
        metadata = md;
        
        bOpen = true;
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
    
}   // DropboxStorage
