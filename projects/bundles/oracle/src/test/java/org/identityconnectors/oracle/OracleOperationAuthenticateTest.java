/**
 * 
 */
package org.identityconnectors.oracle;

import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.Collections;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.api.ConnectorFacade;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.ConnectorSecurityException;
import org.identityconnectors.framework.common.exceptions.InvalidCredentialException;
import org.identityconnectors.framework.common.exceptions.PasswordExpiredException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.junit.Assert;
import org.junit.Test;
import org.junit.matchers.JUnitMatchers;

/**
 * @author kitko
 *
 */
public class OracleOperationAuthenticateTest extends OracleConnectorAbstractTest {
    
    /**
     * Test method for {@link org.identityconnectors.oracle.OracleConnector#authenticate(org.identityconnectors.framework.common.objects.ObjectClass, java.lang.String, org.identityconnectors.common.security.GuardedString, org.identityconnectors.framework.common.objects.OperationOptions)}.
     * with driver configuration 
     */
    @Test
    public void testAuthenticate() {
        testAuthenticate(facade);
    }
    
    /** 
     * Test authenticate using datasource
     */
    @Test
    public void testAuthenticateDS(){
       OracleConfiguration cfg = DataSourceMockHelper.createDataSourceConfiguration();
       ConnectorFacade facade = createFacade(cfg);
       testAuthenticate(facade);
    }
    
    @Test
    public void testAuthenticateForceUsingDriver(){
        OracleConfiguration cfg = DataSourceMockHelper.createDataSourceConfiguration();
        cfg.setUseDriverForAuthentication(true);
        ConnectorFacade facade = createFacade(cfg);
        testAuthenticate(facade);
    }
    
    
    /**
     * Test method for {@link org.identityconnectors.oracle.OracleConnector#authenticate(org.identityconnectors.framework.common.objects.ObjectClass, java.lang.String, org.identityconnectors.common.security.GuardedString, org.identityconnectors.framework.common.objects.OperationOptions)}.
     */
    private void testAuthenticate(ConnectorFacade facade) {
    	String user = "TESTUSER";
    	GuardedString password = new GuardedString("TEST".toCharArray());
    	try{
    		facade.delete(ObjectClass.ACCOUNT, new Uid(user),null);
    	}catch(UnknownUidException e){}
        Attribute authentication = AttributeBuilder.build(OracleConstants.ORACLE_AUTHENTICATION_ATTR_NAME, OracleConstants.ORACLE_AUTH_LOCAL);
        Attribute name = new Name(user);
        Attribute passwordAttribute = AttributeBuilder.buildPassword(password);
        Attribute privileges = AttributeBuilder.build(OracleConstants.ORACLE_PRIVS_ATTR_NAME,"CREATE SESSION");
        Uid uid = facade.create(ObjectClass.ACCOUNT, CollectionUtil.newSet(authentication,name,passwordAttribute,privileges), null);
    	
        Uid aUid = facade.authenticate(ObjectClass.ACCOUNT, user, password, null);
        Assert.assertEquals("Uid returned from authenticate must be same as returned from create ", uid, aUid);
        
        //Invalid password
        try{
            facade.authenticate(ObjectClass.ACCOUNT , user, new GuardedString("wrongPassword".toCharArray()), null);
            fail("Authenticate must fail for invalid user/password");
        }
        catch(PasswordExpiredException e){
        	fail("Invalid password cannot throw PasswordExpiredException");
        }
        catch(InvalidCredentialException e){
        }
        
        //Invalid object class
        try{
            facade.authenticate(ObjectClass.GROUP, user, password, null);
            fail("Authenticate must fail for invalid object class");
        }
        catch(IllegalArgumentException e){
        }
        
        //Null user name
        try{
            facade.authenticate(ObjectClass.ACCOUNT, null, password, null);
            fail("Must fail for null user");
        }
        catch(Exception e){}
        
        //Expired password must throw PasswordExpiredException
        facade.update(ObjectClass.ACCOUNT, uid, Collections.singleton(AttributeBuilder.buildPasswordExpired(true)), null);
        try{
        	uid = facade.authenticate(ObjectClass.ACCOUNT, user, password, null);
        	fail("Should not Authenticate with expired password");
        }catch(PasswordExpiredException e){
        }
        catch(RuntimeException e){
        	fail("Authenticate with expired accoun must throw PasswordExpiredException only :" + e);
        }
        
        //Lock account
        facade.update(ObjectClass.ACCOUNT, uid, Collections.singleton(AttributeBuilder.buildPassword(password)), null);
        facade.authenticate(ObjectClass.ACCOUNT, user, password, null);
        facade.update(ObjectClass.ACCOUNT, uid, Collections.singleton(AttributeBuilder.buildEnabled(false)), null);
        try{
        	facade.authenticate(ObjectClass.ACCOUNT, user, password, null);
        }
        catch(PasswordExpiredException e){
        	fail("Must not throw PasswordExpiredException on locked account");
        }
        catch(ConnectorSecurityException e){
        	Assert.assertThat(e.getMessage(), JUnitMatchers.containsString("account is locked"));
        }
        facade.update(ObjectClass.ACCOUNT, uid, Collections.singleton(AttributeBuilder.buildEnabled(true)), null);
    	facade.authenticate(ObjectClass.ACCOUNT, user, password, null);
    	
    	//Update to external
    	facade.update(ObjectClass.ACCOUNT, uid, Collections.singleton(AttributeBuilder.build(OracleConstants.ORACLE_AUTHENTICATION_ATTR_NAME,OracleConstants.ORACLE_AUTH_EXTERNAL)), null);
    	try{
    		facade.authenticate(ObjectClass.ACCOUNT, user, password, null);
    		fail("Cannot authenticate with external authentication");
    	}catch(ConnectorException e){}
    	
    	//Update to Global
    	boolean tryGlobal = false;
    	try{
			facade.update(ObjectClass.ACCOUNT, uid, CollectionUtil
					.newSet(AttributeBuilder.build(OracleConstants.ORACLE_AUTHENTICATION_ATTR_NAME,OracleConstants.ORACLE_AUTH_GLOBAL),
							AttributeBuilder.build(OracleConstants.ORACLE_GLOBAL_ATTR_NAME,"anyGlobalName")
							), null);
			tryGlobal = true;
    	}catch(ConnectorException e){
            if(e.getCause() instanceof SQLException){
                if("67000".equals(((SQLException)e.getCause()).getSQLState()) && 439 == ((SQLException)e.getCause()).getErrorCode()){
                }
                else{
                    fail(e.getMessage());
                }
            }
            else{
                fail(e.getMessage());
            }
        }
    	if(tryGlobal){
	    	try{
	    		facade.authenticate(ObjectClass.ACCOUNT, user, password, null);
	    		fail("Cannot authenticate with global authentication");
	    	}catch(ConnectorException e){}
    	}
        
        facade.delete(ObjectClass.ACCOUNT, uid, null);
        
    }
    
    @Test
    public void testReturnUidOnly(){
    	String user = "TESTUSER";
    	GuardedString password = new GuardedString("TEST".toCharArray());
        Attribute authentication = AttributeBuilder.build(OracleConstants.ORACLE_AUTHENTICATION_ATTR_NAME, OracleConstants.ORACLE_AUTH_LOCAL);
        Attribute name = new Name(user);
        Attribute passwordAttribute = AttributeBuilder.buildPassword(password);
        Attribute privileges = AttributeBuilder.build(OracleConstants.ORACLE_PRIVS_ATTR_NAME,"CREATE SESSION");
        try{
            facade.delete(ObjectClass.ACCOUNT, new Uid(user),null);
        }catch(UnknownUidException e){}
    	Uid uid = facade.create(ObjectClass.ACCOUNT, CollectionUtil.newSet(authentication,name,passwordAttribute,privileges), null);
    	Uid aUid = facade.authenticate(ObjectClass.ACCOUNT, user, password, null);
    	Assert.assertEquals("Uid returned from authenticate must be same as returned from create ", uid, aUid);
    	GuardedString badPassword = new GuardedString("badPassword".toCharArray());
    	try{
			facade.authenticate(ObjectClass.ACCOUNT, user, badPassword, null);
    		fail("Must fail for bad password");
    	}
    	catch(ConnectorException e){}
    	OperationOptions options = new OperationOptionsBuilder().setOption("returnUidOnly", Boolean.TRUE).build();
    	Uid aUid2 = facade.authenticate(ObjectClass.ACCOUNT, user, badPassword, options);
    	Assert.assertEquals("Uid returned from authenticate must be same as returned from create ", uid, aUid2);
    	String invalidUser = "TESTUSER2";
    	try{
    		facade.delete(ObjectClass.ACCOUNT, new Uid(invalidUser),null);
    	}
    	catch(UnknownUidException e){}
    	try{
    		facade.authenticate(ObjectClass.ACCOUNT, invalidUser, password, options);
    		fail("Authenticate with returnUidOnly must fail for not existing user");
    	}
    	catch(InvalidCredentialException e){}
    	facade.delete(ObjectClass.ACCOUNT, uid,null);
    }
    
    
    
    
    
    

}
