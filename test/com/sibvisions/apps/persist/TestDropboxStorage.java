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

import jvx.rad.io.IFileHandle;
import jvx.rad.model.condition.Equals;
import jvx.rad.model.condition.Like;
import jvx.rad.model.condition.LikeIgnoreCase;
import jvx.rad.model.reference.ReferenceDefinition;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.sibvisions.apps.persist.DropboxStorage.FileType;
import com.sibvisions.rad.persist.StorageDataBook;
import com.sibvisions.util.type.CommonUtil;
import com.sibvisions.util.type.FileUtil;
import com.sibvisions.util.type.ResourceUtil;
import com.sibvisions.util.xml.XmlNode;
import com.sibvisions.util.xml.XmlWorker;

/**
 * Tests functionality of {@link DropboxStorage}.
 * 
 * @author Ren� Jahn
 */
public class TestDropboxStorage
{
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Class members
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    
    /** the storage. */
    private DropboxStorage storage;
    
    /** the databook. */
    private StorageDataBook book;
    
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
    // Initialization
    //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
        
    /**
     * Sets values before each test.
     * 
     * @throws Exception if set values fails
     */
    @Before
    public void beforeTest() throws Exception
    {
        storage = new DropboxStorage();
        storage.setAccessToken(getAccessToken());
        storage.setFileType(FileType.All);
        storage.open();
        
        book = new StorageDataBook(storage);
        book.open();
    }
    
    /**
     * Reset values after each test.
     * 
     * @throws Exception if reset values fails
     */
    @After
    public void afterTest() throws Exception
    {
        CommonUtil.close(storage, book);
    }
    
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
        storage.setRecursive(true);
        
        book.fetchAll();
        
        Assert.assertTrue("Dropbox is empty!", book.getRowCount() > 1);
        
        book.setSelectedRow(book.searchNext(new Like("NAME", "Erste Schritte.pdf")));
        
        Assert.assertTrue("PDF document wasn't found!", book.getSelectedRow() >= 0);

        File fiTemp = getTempOutputFile((IFileHandle)book.getValue("CONTENT"));
        
        Assert.assertArrayEquals(FileUtil.getContent(new File(new File("").getAbsolutePath(), "/dropbox/Erste Schritte.pdf")), FileUtil.getContent(fiTemp));
    }

    /**
     * Tests fetching with filter condition.
     * 
     * @throws Exception if fetching data failed
     */
    @Test
    public void testFetchWithFilter() throws Exception
    {
        book.setFilter(new LikeIgnoreCase("NAME", "*Contacts.*"));
        book.fetchAll();
        
        Assert.assertEquals(2, book.getRowCount());
    }
    
    /**
     * Tests fetching.
     * 
     * @throws Exception if test fails
     */
    @Test
    public void testFetchCustomFolder() throws Exception
    {
        storage.setFileType(FileType.Folder);
        
        DropboxStorage storageDetail = new DropboxStorage();
        storageDetail.setAccessToken(getAccessToken());
        storageDetail.setFileType(FileType.File);
        storageDetail.open();
        
        StorageDataBook bookDetail = new StorageDataBook(storageDetail);
        bookDetail.setMasterReference(new ReferenceDefinition(new String[] {"FOLDER"}, book, new String[] {"PATH"}));
        bookDetail.open();
        
        book.fetchAll();

        Assert.assertTrue("Dropbox is empty!", book.getRowCount() > 1);
        
        book.setSelectedRow(book.searchNext(new Equals("PATH", "/subfolder")));
        
        bookDetail.setSelectedRow(0);
        
        Assert.assertEquals("1_Contacts.xls", bookDetail.getValueAsString("NAME"));
    }
    
    /**
     * Tests inserting.
     * 
     * @throws Exception if test fails
     */
    @Test
    public void testInsertDelete() throws Exception
    {
        book.insert(false);
        book.setValue("FOLDER", "/");
        book.setValue("NAME", "app_small_test.png");
        book.setValue("CONTENT", FileUtil.getContent(ResourceUtil.getResourceAsStream("/com/sibvisions/apps/persist/app_small.png")));
        book.saveSelectedRow();
        
        Assert.assertEquals("/app_small_test.png", book.getValueAsString("PATH"));
        
        book.delete();
        book.saveAllRows();
    }

    /**
     * Tests inserting.
     * 
     * @throws Exception if test fails
     */
    @Test
    public void testUpdateDelete() throws Exception
    {
        book.insert(false);
        book.setValue("FOLDER", "/");
        book.setValue("NAME", "app_small_test.png");
        book.setValue("CONTENT", FileUtil.getContent(ResourceUtil.getResourceAsStream("/com/sibvisions/apps/persist/app_small.png")));
        book.saveSelectedRow();
        
        Assert.assertEquals("/app_small_test.png", book.getValueAsString("PATH"));

        book.setValue("FOLDER", "/renamed");
        book.setValue("NAME", "eclipse.png");
        book.setValue("CONTENT", FileUtil.getContent(ResourceUtil.getResourceAsStream("/com/sibvisions/apps/persist/eclipse.png")));
        book.saveAllRows();
        
        book.delete();
        
        book.setSelectedRow(book.searchNext(new Equals("FOLDER", "/renamed").and(new Equals("TYPE", "Folder"))));
        book.delete();
    }

    /**
     * Tests CSV creation.
     * 
     * @throws Exception if test fails
     */
    @Test
    public void testCSV() throws Exception
    {
        storage.setRecursive(true);
        
        IFileHandle fileHandle = storage.createCSV("dropbox.csv", null, null, new Equals("TYPE", "Folder"), null);
        
        File fiTemp = getTempOutputFile(fileHandle);
        
        //FileViewer.open(fiTemp);
        
        fileHandle = storage.createCSV("dropbox_all.csv", null, null, null, null);
        
        System.out.println(fiTemp);
        
        Assert.assertArrayEquals(FileUtil.getContent(ResourceUtil.getResourceAsStream("/com/sibvisions/apps/persist/Folders.csv")), 
                                 FileUtil.getContent(fiTemp));
        
        fiTemp = getTempOutputFile(fileHandle);
        
        System.out.println(fiTemp);        
        
        Assert.assertArrayEquals(FileUtil.getContent(ResourceUtil.getResourceAsStream("/com/sibvisions/apps/persist/AllFiles.csv")), 
                                 FileUtil.getContent(fiTemp));

        //FileViewer.open(fiTemp);
    }

    /**
     * Creates a test folder and renames it.
     * 
     * @throws Exception if test fails
     */
    @Test
    public void testCreateAndRenameFolder() throws Exception
    {
        book.setSelectedRow(book.searchNext(new Equals("FOLDER", "/newfolder").and(new Equals("TYPE", "Folder"))));
        book.delete();
        
        book.insert(false);
        book.setValue("FOLDER", "/newfolder");
        book.saveSelectedRow();
        
        book.setValue("FOLDER", "/deleteMe");
        book.saveSelectedRow();
    }
    
}   // TestDropboxStorage
