/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.     
 * 
 * The contents of this file are subject to the terms of the Common Development 
 * and Distribution License("CDDL") (the "License").  You may not use this file 
 * except in compliance with the License.
 * 
 * You can obtain a copy of the License at 
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.db2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.*;
import java.util.*;

import org.identityconnectors.common.security.*;
import org.identityconnectors.framework.api.*;
import org.identityconnectors.framework.common.exceptions.*;
import org.identityconnectors.framework.common.objects.*;
import org.identityconnectors.framework.common.objects.filter.*;
import org.identityconnectors.test.common.*;
import org.junit.*;


/**
 * Test for {@link DB2Connector}
 * @author kitko
 *
 */
public class DB2ConnectorTest {
    private static PropertyBag testProps;
	private static DB2Configuration testConf;
    private static ConnectorFacade facade;

	/**
	 * Setup for all tests
	 */
	@BeforeClass
	public static void setupClass(){
		testConf = DB2ConfigurationTest.createTestConfiguration();
		facade = createFacade(testConf);
		testProps = TestHelpers.getProperties(DB2Connector.class);
	}
	
    private static ConnectorFacade createFacade(DB2Configuration conf) {
        ConnectorFacadeFactory factory = ConnectorFacadeFactory.getInstance();
        APIConfiguration apiCfg = TestHelpers.createTestConfiguration(DB2Connector.class, conf);
        return factory.newInstance(apiCfg);
    }
    
    /**
     * Just call test
     */
    @Test
    public void testTest(){
    	facade.test();
    }
    
    /**
     * Test schema api
     */
    @Test
    public void testSchemaApi() {
        Schema schema = facade.schema();
        // Schema should not be null
        assertNotNull(schema);
        Set<ObjectClassInfo> objectInfos = schema.getObjectClassInfo();
        assertNotNull(objectInfos);
        assertEquals(1, objectInfos.size());
        ObjectClassInfo objectInfo = (ObjectClassInfo) objectInfos.toArray()[0];
        assertNotNull(objectInfo);
        // the object class has to ACCOUNT_NAME
        assertTrue(objectInfo.is(ObjectClass.ACCOUNT_NAME));
        // iterate through AttributeInfo Set
        Set<AttributeInfo> attInfos = objectInfo.getAttributeInfo();
        
        assertNotNull(AttributeInfoUtil.find(Name.NAME, attInfos));
    }
    
	/**
	 * test successful Authenticate
	 */
	@Test
	public void testAuthenticateSuc(){
		String username = getTestRequiredProperty("testUser");
		String password = getTestRequiredProperty("testPassword");
		Map<String, Object> emptyMap = Collections.emptyMap();
		facade.authenticate(ObjectClass.ACCOUNT, username, new GuardedString(password.toCharArray()),new OperationOptions(emptyMap));
	}
	
	/**
	 * Test fail of Authenticate
	 */
	@Test(expected=RuntimeException.class)
	public void testAuthenticateFail(){
		String username = "undefined";
		String password = "testPassword";
		Map<String, Object> emptyMap = Collections.emptyMap();
		facade.authenticate(ObjectClass.ACCOUNT, username, new GuardedString(password.toCharArray()),new OperationOptions(emptyMap));
	}

	static Connection createTestConnection() throws Exception{
		DB2Configuration conf = DB2ConfigurationTest.createTestConfiguration();
		return conf.createAdminConnection();
	}

	static String getTestRequiredProperty(String name){
	    String value = testProps.getStringProperty(name);
	    return value;
	}
	
	/**
	 * test create
	 */
	@Test
	public void testCreate(){
		String username = getTestRequiredProperty("testUser");
		Map<String, Object> emptyMap = Collections.emptyMap();
		Set<Attribute> attributes = new HashSet<Attribute>();
		attributes.add(new Name(username));
		//attributes.add(AttributeBuilder.buildPassword(new char[]{'a','b','c'}));
		attributes.add(AttributeBuilder.build(DB2Connector.USER_AUTH_GRANTS,"CONNECT ON DATABASE"));
		Uid uid = null;
		try{
			uid = facade.create(ObjectClass.ACCOUNT, attributes, new OperationOptions(emptyMap));
			assertNotNull(uid);
		}
		catch(AlreadyExistsException e){
			facade.delete(ObjectClass.ACCOUNT, new Uid(username), new OperationOptions(emptyMap));
			uid = facade.create(ObjectClass.ACCOUNT, attributes, new OperationOptions(emptyMap));
			assertNotNull(uid);
		}
		//find user
		uid = findUser(username);
		assertNotNull("Cannot find new created user",uid);
	}
	
	private Uid createTestUser(){
		String userName = getTestRequiredProperty("testUser");
		return createTestUser(userName);
	}
	
	private Uid createTestUser(String userName){
		Map<String, Object> emptyMap = Collections.emptyMap();
		Set<Attribute> attributes = new HashSet<Attribute>();
		attributes.add(new Name(userName));
		//attributes.add(AttributeBuilder.buildPassword(new char[]{'a','b','c'}));
		attributes.add(AttributeBuilder.build(DB2Connector.USER_AUTH_GRANTS,"CONNECT ON DATABASE"));
		Uid uid = null;
		try{
			uid = facade.create(ObjectClass.ACCOUNT, attributes, new OperationOptions(emptyMap));
			assertNotNull(uid);
			return uid;
		}
		catch(AlreadyExistsException e){
			return new Uid(userName);
		}
	}
	
	/**
	 * Test delete of user
	 */
	@Test
	public void testDelete(){
		String username = testProps.getProperty("testUser",String.class,"TEST");
		Map<String, Object> emptyMap = Collections.emptyMap();
		Set<Attribute> attributes = new HashSet<Attribute>();
		attributes.add(new Name(username));
		attributes.add(AttributeBuilder.buildPassword(new char[]{'a','b','c'}));
		Uid uid = findUser(username);
		if(uid == null){
			uid = facade.create(ObjectClass.ACCOUNT, attributes, new OperationOptions(emptyMap));
			assertNotNull(uid);
		}
		facade.delete(ObjectClass.ACCOUNT, new Uid(username), new OperationOptions(emptyMap));
		uid = findUser(username);
		assertNull("User not deleted",uid);
	}
	
	private Uid findUser(String name){
        final Uid expected = new Uid(name);
        FindUidObjectHandler handler = new FindUidObjectHandler(expected);
        facade.search(ObjectClass.ACCOUNT, new EqualsFilter(expected), handler, null);
        final Uid actual = handler.getFoundUID();
        return actual;
	}
	
    /**
     * test find by uid 
     */
    @Test
    public void testFindUserByUid() {
    	String username = testProps.getProperty("testUser",String.class,"TEST");
        createTestUser();
        final Uid expected = new Uid(username);
        FindUidObjectHandler handler = new FindUidObjectHandler(expected);
        // attempt to find the newly created object..
        facade.search(ObjectClass.ACCOUNT, new EqualsFilter(expected), handler, null);
        assertTrue("The testuser was not found", handler.found);
        final Uid actual = handler.getFoundUID();
        assertNotNull(actual);
        assertTrue(actual.is(expected.getName()));  
     }
    
    /**
     * Test searching by grants attribute
     */
    @Test
    public void testFindByGrants(){
    	AllResultsHandler handler = new AllResultsHandler();
    	facade.search(ObjectClass.ACCOUNT, null,handler, null);
    	for(ConnectorObject object : handler.getResults()){
    		final String uidValue = object.getUid().getUidValue().toUpperCase();
    		if(!uidValue.startsWith("db2inst".toUpperCase()) && !uidValue.startsWith("db2admin".toUpperCase())){
    		    facade.delete(ObjectClass.ACCOUNT, object.getUid(), null);
    		}
    	}
    	Attribute grants1 = AttributeBuilder.build(DB2Connector.USER_AUTH_GRANTS,"LOAD ON DATABASE","SELECT ON SYSCAT.TABLES");
    	Set<Attribute> attributes1 = new HashSet<Attribute>();
    	attributes1.add(grants1);
    	attributes1.add(new Name("TEST1"));
    	Uid testUid1 = facade.create(ObjectClass.ACCOUNT,attributes1,null);
    	assertNotNull(testUid1);
    	
    	Attribute grants2 = AttributeBuilder.build(DB2Connector.USER_AUTH_GRANTS,"LOAD ON DATABASE","SELECT ON SYSCAT.TABLES");
    	Set<Attribute> attributes2 = new HashSet<Attribute>();
    	attributes2.add(grants2);
    	attributes2.add(new Name("TEST2"));
    	Uid testUid2 = facade.create(ObjectClass.ACCOUNT,attributes2,null);
    	assertNotNull(testUid2);
    	
    	Attribute grants3 = AttributeBuilder.build(DB2Connector.USER_AUTH_GRANTS,"LOAD ON DATABASE");
    	Set<Attribute> attributes3 = new HashSet<Attribute>();
    	attributes3.add(grants3);
    	attributes3.add(new Name("TEST3"));
    	Uid testUid3 = facade.create(ObjectClass.ACCOUNT,attributes3,null);
    	assertNotNull(testUid3);
    	
    	handler.clear();
    	facade.search(ObjectClass.ACCOUNT, new ContainsAllValuesFilter(grants1), handler, new OperationOptionsBuilder().setAttributesToGet(Arrays.asList(DB2Connector.USER_AUTH_GRANTS)).build());
    	assertTrue(!handler.getResults().isEmpty());
    	assertTrue(handler.getResultUids().contains(new Uid("TEST1")));
    	assertTrue(handler.getResultUids().contains(new Uid("TEST2")));
    	assertFalse(handler.getResultUids().contains(new Uid("TEST3")));
    }
    
    /**
     * Test find by end with operator
     */
    @Test
    public void testFindUserByEndWith() {
    	String username = testProps.getProperty("testUser",String.class,"TEST");
        createTestUser();
        final Attribute expected = AttributeBuilder.build(Name.NAME, username);
        FindUidObjectHandler handler = new FindUidObjectHandler(new Uid(username));
        // attempt to find the newly created object..
        facade.search(ObjectClass.ACCOUNT, new EndsWithFilter(expected), handler, null);
        assertTrue("The user was not found", handler.found);
        final ConnectorObject actual = handler.getFoundObject();
        assertNotNull(actual);
        assertEquals("Expected user is not same",username.toUpperCase(), AttributeUtil.getAsStringValue(actual.getName()).toUpperCase());
     }


    /**
     * test find by start with operator
     */
    @Test
    public void testFindUserByStartWith() {
    	String username = testProps.getProperty("testUser",String.class,"TEST");
        createTestUser();
        final Attribute expected = AttributeBuilder.build(Name.NAME, username);
        FindUidObjectHandler handler = new FindUidObjectHandler(new Uid(username));
        // attempt to find the newly created object..
        facade.search(ObjectClass.ACCOUNT, new StartsWithFilter(expected), handler, null);
        assertTrue("The user was not found", handler.found);
        final ConnectorObject actual = handler.getFoundObject();
        assertNotNull(actual);
        assertEquals("Expected user is not same",username.toUpperCase(), AttributeUtil.getAsStringValue(actual.getName()).toUpperCase());
     }
    
    /**
     * Test find by uid and check grants attribute
     */
    @Test
    public void testFindCheckAttributes(){
    	String username = testProps.getProperty("testUser",String.class,"TEST");
    	final Uid expected = new Uid(username);
        createTestUser();
        FindUidObjectHandler handler = new FindUidObjectHandler(new Uid(username));
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet(Arrays.asList(Name.NAME,DB2Connector.USER_AUTH_GRANTS));
        OperationOptions options = builder.build();
        facade.search(ObjectClass.ACCOUNT, new EqualsFilter(expected), handler, options);
        assertTrue("The user was not found", handler.found);
        final ConnectorObject actual = handler.getFoundObject();
        assertNotNull(actual);
        final Attribute grants = actual.getAttributeByName(DB2Connector.USER_AUTH_GRANTS);
        assertNotNull(grants);
        assertNotNull(grants.getValue());
    }
    
    /**
     * Testing update
     */
    @Test
    public void testUpdate(){
		String username = getTestRequiredProperty("testUser");
    	final Uid uid = new Uid(username);
        createTestUser();
        FindUidObjectHandler handler = new FindUidObjectHandler(new Uid(username));
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        builder.setAttributesToGet(Arrays.asList(Name.NAME,DB2Connector.USER_AUTH_GRANTS));
        OperationOptions options = builder.build();
        facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, options);
        ConnectorObject actual = handler.getFoundObject();
        assertNotNull(actual);
        Attribute grants1 = actual.getAttributeByName(DB2Connector.USER_AUTH_GRANTS);
        Attribute oldGrants = grants1; 
        grants1 = AttributeBuilder.build(DB2Connector.USER_AUTH_GRANTS,"LOAD ON DATABASE","SELECT ON SYSCAT.TABLES");
        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(grants1);
        Map<String, Object> emptyMap = Collections.emptyMap();
        
        //Test add
        facade.addAttributeValues(ObjectClass.ACCOUNT,uid,attributes, new OperationOptions(emptyMap));
        facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, options);
        actual = handler.getFoundObject();
        List<Object> newGrantsValue = actual.getAttributeByName(DB2Connector.USER_AUTH_GRANTS).getValue();
		assertTrue(newGrantsValue.contains("LOAD ON DATABASE"));
        assertTrue(newGrantsValue.contains("CONNECT ON DATABASE"));
        assertTrue(newGrantsValue.contains("SELECT ON SYSCAT.TABLES"));
        
        //Test replace
        handler.clear();
        attributes.clear();
        attributes.add(AttributeBuilder.build(DB2Connector.USER_AUTH_GRANTS,"SELECT ON SYSCAT.TABLES"));
        facade.update(ObjectClass.ACCOUNT,uid, attributes, new OperationOptions(emptyMap));
        facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, options);
        actual = handler.getFoundObject();
        newGrantsValue = actual.getAttributeByName(DB2Connector.USER_AUTH_GRANTS).getValue();
		assertFalse(newGrantsValue.contains("LOAD ON DATABASE"));
        assertTrue(newGrantsValue.contains("CONNECT ON DATABASE"));
        assertTrue(newGrantsValue.contains("SELECT ON SYSCAT.TABLES"));
        
        //Test delete
        handler.clear();
        facade.removeAttributeValues(ObjectClass.ACCOUNT,uid, AttributeUtil.filterUid(attributes), new OperationOptions(emptyMap));
        facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, options);
        actual = handler.getFoundObject();
        newGrantsValue = actual.getAttributeByName(DB2Connector.USER_AUTH_GRANTS).getValue();
		assertFalse(newGrantsValue.contains("LOAD ON DATABASE"));
        assertTrue(newGrantsValue.contains("CONNECT ON DATABASE"));
        assertFalse(newGrantsValue.contains("SELECT ON SYSCAT.TABLES"));
        
        //test replace/add using DB2Configuration.isReplaceAllGrantsOnUpdate switch
        //Test replace
        final DB2Configuration testConfiguration = DB2ConfigurationTest.createTestConfiguration();
        testConfiguration.setReplaceAllGrantsOnUpdate(true);
        ConnectorFacade testFacade = createFacade(testConfiguration);
        attributes.clear();
        attributes.add(AttributeBuilder.build(DB2Connector.USER_AUTH_GRANTS,"SELECT ON SYSCAT.TABLES"));
        testFacade.update(ObjectClass.ACCOUNT,uid, attributes, new OperationOptions(emptyMap));
        testFacade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, options);
        actual = handler.getFoundObject();
        newGrantsValue = actual.getAttributeByName(DB2Connector.USER_AUTH_GRANTS).getValue();
        assertFalse(newGrantsValue.contains("LOAD ON DATABASE"));
        assertTrue(newGrantsValue.contains("CONNECT ON DATABASE"));
        assertTrue(newGrantsValue.contains("SELECT ON SYSCAT.TABLES"));
        
        //Test add
        testConfiguration.setReplaceAllGrantsOnUpdate(false);
        testFacade = createFacade(testConfiguration);
        attributes.clear();
        attributes.add(AttributeBuilder.build(DB2Connector.USER_AUTH_GRANTS,"LOAD ON DATABASE"));
        testFacade.update(ObjectClass.ACCOUNT,uid, attributes, new OperationOptions(emptyMap));
        testFacade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, options);
        actual = handler.getFoundObject();
        newGrantsValue = actual.getAttributeByName(DB2Connector.USER_AUTH_GRANTS).getValue();
        assertTrue(newGrantsValue.contains("LOAD ON DATABASE"));
        assertTrue(newGrantsValue.contains("CONNECT ON DATABASE"));
        assertTrue(newGrantsValue.contains("SELECT ON SYSCAT.TABLES"));
        
        
        //Reset to old value
        attributes.clear();
        attributes.add(oldGrants);
        facade.update(ObjectClass.ACCOUNT,uid,attributes, new OperationOptions(emptyMap));
    }
    
    
    
	
    
    private static class FindUidObjectHandler implements ResultsHandler {
        private ConnectorObject connectorObject = null;
        private boolean found = false;
        private final Uid uid;
        
        /**
         * @param uid
         */
        public FindUidObjectHandler(Uid uid) {
            this.uid = uid;
        }
        
        ConnectorObject getFoundObject() {
			return connectorObject;
		}

		Uid getFoundUID() {
            return connectorObject != null ? connectorObject.getUid() : null;
        }
		
		void clear(){
			found = false;
			connectorObject = null;
		}
        

        public boolean handle(ConnectorObject obj) {
            System.out.println("Object: " + obj);
            if (obj.getUid().getUidValue().equalsIgnoreCase(uid.getUidValue())) {
                found = true;
                connectorObject = obj;
                return false;
            }
            return true;
        }
    }
    
    private static class AllResultsHandler implements ResultsHandler{
    	private List<ConnectorObject> results = new ArrayList<ConnectorObject>();
		public boolean handle(ConnectorObject obj) {
			results.add(obj);
			return true;
		}
		List<ConnectorObject> getResults(){
			return Collections.unmodifiableList(results);
		}
		void clear(){
			results.clear();
		}
		List<Uid> getResultUids(){
			List<Uid> uids = new ArrayList<Uid>(results.size());
			for(ConnectorObject co : results){
				uids.add(co.getUid());
			}
			return uids;
		}
    }
    
    /**
     * Tests deleting user that does not exist
     */
    @Test(expected=UnknownUidException.class)
    public void testDeleteUnexisting() {
        assertNotNull(facade);
        String userName = "delUser";
        Uid uid = createTestUser(userName);
        uid = findUser(userName);
        assertNotNull(uid);
        facade.delete(ObjectClass.ACCOUNT, uid, null);
        uid = findUser(userName);
        assertNull(uid);
        facade.delete(ObjectClass.ACCOUNT, new Uid("UNKNOWN"), null);
        fail("Delete of not existing user should fail");
    }
    
    /**
     * Tests more creates
     */
    @Test
    public void testMultiCreate(){
        for(int i = 0;i < 10;i++){
            String userName = "testUser" + i;
            Uid uid = new Uid(userName);
            try{
                facade.delete(ObjectClass.ACCOUNT, uid, null);
            }
            catch(UnknownUidException e){}
            Set<Attribute> attributes = new HashSet<Attribute>();
            attributes.add(new Name(userName));
            //attributes.add(AttributeBuilder.buildPassword(new char[]{'a','b','c'}));
            attributes.add(AttributeBuilder.build("grants","CONNECT ON DATABASE"));
            facade.create(ObjectClass.ACCOUNT, attributes, null);
            FindUidObjectHandler handler = new FindUidObjectHandler(uid);
            // attempt to find the newly created object..
            try{
                facade.search(ObjectClass.ACCOUNT, new EqualsFilter(uid), handler, null);
                assertTrue("The testuser was not found", handler.found);
            }
            finally{
                facade.delete(ObjectClass.ACCOUNT, uid, null);
            }
        }
    }
    
    /**
     * Test validity of names for create
     */
    @Test
    public void testValidNamesOnCreate(){
        //too long name
        char[] goodName = new char[DB2Specifics.MAX_NAME_SIZE];
        char[] tooLongName = new char[DB2Specifics.MAX_NAME_SIZE + 1];
        for(int i = 0; i < tooLongName.length;i++){
            tooLongName[i] = (char)('a');
        }
        System.arraycopy(tooLongName, 0, goodName, 0, goodName.length);
        String invalid1 = "%";
        String invalid2 = "SQL";
        String invalid3 = "DATABASE";
        
        Set<Attribute> attributes = new HashSet<Attribute>();
        attributes.add(new Name(new String(tooLongName)));
        //attributes.add(AttributeBuilder.buildPassword(new char[]{'a','b','c'}));
        attributes.add(AttributeBuilder.build(DB2Connector.USER_AUTH_GRANTS,"CONNECT ON DATABASE"));
        try{
            facade.create(ObjectClass.ACCOUNT, attributes, null);
            fail("Validate should fail : too long user name");
        }
        catch(IllegalArgumentException e){}
        
        attributes.remove(new Name(new String(tooLongName)));
        attributes.add(new Name(new String(goodName)));
        Uid uid = facade.create(ObjectClass.ACCOUNT, attributes, null);
        facade.delete(ObjectClass.ACCOUNT, uid, null);
        
        attributes.remove(new Name(new String(goodName)));
        attributes.add(new Name(new String(invalid1)));
        try{
            facade.create(ObjectClass.ACCOUNT, attributes, null);
            fail("Validate should fail : Invalid character name");
        }
        catch(IllegalArgumentException e){}
        
        attributes.remove(new Name(invalid1));
        attributes.add(new Name(new String(invalid2)));
        try{
            facade.create(ObjectClass.ACCOUNT, attributes, null);
            fail("Validate should fail : Invalid prefix");
        }
        catch(IllegalArgumentException e){}
        
        attributes.remove(new Name(invalid2));
        attributes.add(new Name(new String(invalid3)));
        try{
            facade.create(ObjectClass.ACCOUNT, attributes, null);
            fail("Validate should fail : Reserved keyword");
        }
        catch(IllegalArgumentException e){}
        
    }
    
    /** Test valid names */
    @Test
    public void testDB2Validity(){
    	DB2Connector connector = new DB2Connector();
    	connector.init(testConf);
    	connector.checkDB2Validity("TEST1");
    	connector.checkDB2Validity("#TEST1");
    	testFailForValidity(connector,"ABCDEFGHIJKLMNOPRSTABCDEFGHIJKLMNO","Must fail for too long name");
    	testFailForValidity(connector,"US%US","Must fail for invalid char");
    }
    
    private void testFailForValidity(DB2Connector connector,String name,String msg){
    	try{
    		connector.checkDB2Validity(name);
    		fail(msg);
    	}
    	catch(RuntimeException e){
    	}
    }
    
    
}
