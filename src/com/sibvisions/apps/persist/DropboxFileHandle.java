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
 * 22.12.2014 - [JR] - creation
 */
package com.sibvisions.apps.persist;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import jvx.rad.io.IFileHandle;

import com.dropbox.core.DbxClient;
import com.dropbox.core.DbxEntry;
import com.sibvisions.util.IValidatable;
import com.sibvisions.util.type.CommonUtil;
import com.sibvisions.util.type.FileUtil;

/**
 * The <code>DropboxFileHandle</code> is an {@link IFileHandle} which is connected to a file located
 * in a dropbox container.
 * 
 * @author René Jahn
 */
class DropboxFileHandle implements IFileHandle,
                                   IValidatable
{
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Class members
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    
    /** the dropbox client. */
    private DbxClient client;
    
    /** the file path. */
    private String sPath;

    /** the cache file. */
    private File fiTemp;
    
    /** the file. */
    private DbxEntry.File file;
    
    /** the file metadata. */
    private DbxEntry.File metaData;
    
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Initialization
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    
    /**
     * Creates a new instance of {@link DropboxFileHandle} with specific file.
     * 
     * @param pClient the dropbox client
     * @param pFile the file
     */
    public DropboxFileHandle(DbxClient pClient, DbxEntry.File pFile)
    {
        client = pClient;
        file  = pFile;
    }

    /**
     * Creates a new instance of {@link DropboxFileHandle} with path information.
     * 
     * @param pClient the dropbox client
     * @param pPath the path information
     */
    public DropboxFileHandle(DbxClient pClient, String pPath)
    {
        client = pClient;
        sPath  = pPath;
    }
    
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Interface implementation
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    
    // IFileHandle
    
    /**
     * {@inheritDoc}
     */
    public String getFileName()
    {
        if (file != null)
        {
            return file.name;
        }
        else
        {
            return FileUtil.getName(sPath);
        }
        
    }

    /**
     * {@inheritDoc}
     */
    public InputStream getInputStream() throws IOException
    {
        init();
        
        return new FileInputStream(fiTemp);
    }

    /**
     * {@inheritDoc}
     */
    public long getLength() throws IOException
    {
        //no init because BinaryDataType checks the length and this would trigger data transfer
        if (file != null)
        {
            return file.numBytes;
        }
        else
        {
            return fiTemp != null ? fiTemp.length() : -1; 
        }
    }

    // IValidatable
    
    /**
     * {@inheritDoc}
     */
    public boolean isValid()
    {
        //no init because this would trigger data transfer and ObjectCache checks isValid too often

        return fiTemp == null || metaData != null;
    }
    
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // User-defined methods
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Initializes the temp file.
     */
    private void init()
    {
        if (fiTemp == null)
        {
            String sName = getFileName();
            
            try
            {
                File fiNew = File.createTempFile(FileUtil.removeExtension(sName), FileUtil.getExtension(sName));
                fiNew.deleteOnExit();
                
                FileOutputStream fos = null;

                try
                {
                    fos = new FileOutputStream(fiNew);
                    
                    metaData = client.getFile(file != null ? file.path : sPath, null, fos);
                }
                finally
                {
                    CommonUtil.close(fos);
                }
                
                fiTemp = fiNew;
            }
            catch (Exception ex)
            {
                throw new RuntimeException(ex);
            }
        }
    }
    
}   // DropboxFileHandle
