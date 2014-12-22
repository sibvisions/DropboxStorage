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

import javax.rad.io.IFileHandle;

import org.junit.Test;

import com.sibvisions.apps.persist.DropboxStorage.FileType;
import com.sibvisions.rad.persist.StorageDataBook;
import com.sibvisions.util.FileViewer;
import com.sibvisions.util.type.FileUtil;
import com.sibvisions.util.xml.XmlNode;
import com.sibvisions.util.xml.XmlWorker;

/**
 * Tests functionality of {@link DropboxStorage}.
 * 
 * @author René Jahn
 */
public class TestDropboxStorage
{
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // User-defined methods
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    /**
     * Gets the Dropbox access token from the XML configuration (config.xml).
     * 
     * @return the access token
     * @throws Exception if reading XML configuration failed
     */
    private String getAccessToken() throws Exception
    {
        XmlNode node = XmlWorker.readNode(new File(new File("").getAbsoluteFile(), "config.xml"));
        
        return node.getNodeValue("/config/app/accessToken");
    }

    /**
     * Gets a temporary file with content of the given file handle.
     * 
     * @param pFileHandle the file handle
     * @return the temporary output file
     * @throws Exception if saving file handle fails
     */
    public File getTempOutputFile(IFileHandle pFileHandle) throws Exception
    {
        File fiTemp = new File(System.getProperty("java.io.tmpdir"), pFileHandle.getFileName()); 
        
        FileUtil.copy(pFileHandle.getInputStream(), fiTemp);
        
        return fiTemp;
    }    
    
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Test methods
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    
    /**
     * Tests fetching.
     * 
     * @throws Exception if test fails
     */
    @Test
    public void testFetch() throws Exception
    {
        DropboxStorage dst = new DropboxStorage();
        dst.setAccessToken(getAccessToken());
        dst.setRecursive(true);
        dst.setFileType(FileType.All);
        dst.open();
        
        StorageDataBook book = new StorageDataBook(dst);
        book.open();

        book.fetchAll();
        
        book.setSelectedRow(0);

        File fiTemp = getTempOutputFile((IFileHandle)book.getValue("CONTENT"));
        
        FileViewer.open(fiTemp);
    }
    
}   // TestDropboxStorage
