/**
 * 
 */
package org.identityconnectors.db2;

import static org.junit.Assert.*;

import java.lang.reflect.*;
import java.sql.Connection;
import java.util.*;

import javax.naming.*;
import javax.naming.spi.InitialContextFactory;
import javax.sql.DataSource;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.dbcommon.PropertiesResolver;
import org.identityconnectors.framework.test.TestHelpers;
import org.junit.*;

/**
 * Tests for {@link DB2Configuration}
 * @author kitko
 *
 */
public class DB2ConfigurationTest {
	private static ThreadLocal<DB2Configuration> cfg = new ThreadLocal<DB2Configuration>();
	private final static Log log = Log.getLog(DB2ConfigurationTest.class);
	
	/**
	 * Test validation
	 */
	@Test
	public void testValidateSuc(){
		DB2Configuration testee = createTestConfiguration();
		testee.validate();
	}
	
	static DB2Configuration createTestConfiguration(){
		DB2Configuration conf = null;
		String connType = TestHelpers.getProperty("connType",null);
		if("type4".equals(connType)){
			conf = createTestType4Configuration();
		}
		else if("type2".equals(connType)){
			conf = createTestType2Configuration();
		}
		else{
			throw new IllegalArgumentException("Ilegall connType " + connType);
		}
		if(conf == null){
			throw new IllegalStateException("Configuration not created");
		}
		conf.validate();
		return conf;
	}
	
	private static DB2Configuration createTestType4Configuration(){
		DB2Configuration conf = new DB2Configuration();
		Properties properties = TestHelpers.getProperties();
		properties = PropertiesResolver.resolveProperties(properties);
		String databaseName = properties.getProperty("type4.databaseName",null);
		String adminAcoount = properties.getProperty("type4.adminAccount",null);
		String adminPassword = properties.getProperty("type4.adminPassword",null);
		String host = properties.getProperty("type4.host");
		String port = properties.getProperty("type4.port");
		conf.setDatabaseName(databaseName);
		conf.setAdminAccount(adminAcoount);
		conf.setAdminPassword(new GuardedString(adminPassword.toCharArray()));
		conf.setHost(host);
		conf.setPort(port);
		conf.setJdbcDriver(DB2Specifics.JCC_DRIVER);
		return conf;
	}
	
	/**
	 * Validates and create connection using type4 driver
	 */
	@Test
	public void testType4Configuration(){
		DB2Configuration conf = createTestType4Configuration();
		conf.validate();
		Connection conn = conf.createAdminConnection();
		assertNotNull(conn);
		conf.setAliasName("sample");
		try{
			conf.validate();
			fail("Cannot set alias , when having enough info to connect using type4 driver");
		}
		catch(Exception e){}
	}
	
	
	private static DB2Configuration createTestType2Configuration(){
		Properties properties = TestHelpers.getProperties();
		properties = PropertiesResolver.resolveProperties(properties);
		String alias = properties.getProperty("type2.alias");
		if(alias == null){
			return null;
		}
		DB2Configuration conf = new DB2Configuration();
		String adminAccount = properties.getProperty("type2.adminAccount");
		String adminPassword = properties.getProperty("type2.adminPassword");
		conf.setAliasName(alias);
		conf.setAdminAccount(adminAccount);
		conf.setAdminPassword(new GuardedString(adminPassword.toCharArray()));
		conf.setJdbcDriver(DB2Specifics.APP_DRIVER);
		return conf;
	}

	
	/**
	 * Validates and create connection using type2 driver
	 */
	@Test
	public void testType2Configuration(){
		DB2Configuration conf = createTestType2Configuration();
		//we need having alias on local machine, so we will test only when type2.alias property is set
		if(conf == null){
			conf = new DB2Configuration();
			conf.setAliasName("myDBAlias");
			conf.setAdminAccount("dummy");
			conf.setAdminPassword(new GuardedString());
			conf.setJdbcDriver(DB2Specifics.APP_DRIVER);
			try{
				conf.validate();
			}
			catch(UnsatisfiedLinkError error){
				//This will happen when having driver on classpath, but db2 client is not installed
				log.warn(error,"Cannot load db2 type2 driver, probably db2client not installed");
			}
		}
		else{
			try{
				conf.validate();
				final Connection conn = conf.createAdminConnection();
				assertNotNull(conn);
			}
			catch(UnsatisfiedLinkError error){
				//This will happen when having driver on classpath, but db2 client is not installed
				log.warn(error,"Cannot load db2 type2 driver, probably db2client not installed");
			}
		}
	}

	/**
	 * Test getting Connection from DS
	 */
	@Test
	public void testDataSourceConfig(){
		DB2Configuration conf = new DB2Configuration();
		//set to thread local
		cfg.set(conf);
		conf.setDataSource("testDS");
		conf.setAdminAccount("user");
		conf.setAdminPassword(new GuardedString(new char[]{'t'}));
		final String[] dsJNDIEnv = new String[]{"java.naming.factory.initial=" + MockContextFactory.class.getName()};
		conf.setDsJNDIEnv(dsJNDIEnv);
		assertArrayEquals(conf.getDsJNDIEnv(), dsJNDIEnv);
		conf.validate();
		Connection conn = conf.createAdminConnection();
		conf.setAdminAccount(null);
		conf.setAdminPassword(null);
		conf.validate();
		conn = conf.createAdminConnection();
		assertNotNull(conn);
	}
	
	
	/**
	 * Mock for {@link InitialContextFactory}
	 * @author kitko
	 *
	 */
	public static class MockContextFactory implements InitialContextFactory{
		
		public Context getInitialContext(Hashtable<?, ?> environment) throws NamingException {
			Context context = (Context)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Context.class}, new ContextIH());
			return context;
		}
	}
	
	private static class ContextIH implements InvocationHandler{

		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if(method.getName().equals("lookup")){
				return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{DataSource.class}, new DataSourceIH());
			}
			return null;
		}
	}
	
	private static class DataSourceIH implements InvocationHandler{
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			if(method.getName().equals("getConnection")){
				if(cfg.get().getAdminAccount() == null){
					Assert.assertEquals("getConnection must be called without user and password",0,method.getParameterTypes().length);
				}
				else{
					Assert.assertEquals("getConnection must be called with user and password",2,method.getParameterTypes().length);
				}
				return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class}, new ConnectionIH());
			}
			throw new IllegalArgumentException("Invalid method");
		}
	}
	
	private static  class ConnectionIH implements InvocationHandler{
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			return null;
		}
	}
	
	
	
	
	
	
	
	
	
	
}
