package org.identityconnectors.oracle;

import java.sql.Connection;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.dbcommon.LocalizedAssert;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.spi.operations.CreateOp;

/**
 * Oracle create operation. 
 * It builds create sql using OracleCreateOrAlterStBuilder and builds grants using OracleRolesAndPrivsBuilder 
 * @author kitko
 *
 */
final class OracleOperationCreate extends AbstractOracleOperation implements CreateOp{
    
	private static final Collection<String> VALID_CREATE_ATTRIBUTES;
	
	static {
		Collection<String> tmp = new HashSet<String>(OracleConstants.ALL_ATTRIBUTE_NAMES);
		tmp.removeAll(Arrays.asList(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME,OperationalAttributes.DISABLE_DATE_NAME));
		VALID_CREATE_ATTRIBUTES = Collections.unmodifiableCollection(tmp);
	}
    
	
    OracleOperationCreate(OracleConfiguration cfg,Connection adminConn, Log log) {
        super(cfg, adminConn, log);
    }

    public Uid create(ObjectClass oclass, Set<Attribute> attrs, OperationOptions options) {
        OracleConnector.checkObjectClass(oclass, cfg.getConnectorMessages());
        Map<String, Attribute> map = AttributeUtil.toMap(attrs);
        String userName = OracleConnectorHelper.getStringValue(map, Name.NAME);
        new LocalizedAssert(cfg.getConnectorMessages()).assertNotBlank(userName,Name.NAME);
        checkCreateAttributes(map);
        checkUserNotExist(userName);
        log.info("Creating user : [{0}]", userName);
        OracleUserAttributes.Builder builder = new OracleUserAttributes.Builder();
        builder.setUserName(userName);
        new OracleAttributesReader(cfg.getConnectorMessages()).readCreateAttributes(map, builder);
        OracleUserAttributes caAttributes = builder.build();
        String createSQL = new OracleCreateOrAlterStBuilder(cfg.getCSSetup(),cfg.getConnectorMessages()).buildCreateUserSt(caAttributes).toString();
        if(createSQL == null){
        	//This should not happen, but be more deffensive
        	throw new ConnectorException("No create SQL generated, probably not enough attributes");
        }
        Attribute roles = AttributeUtil.find(OracleConstants.ORACLE_ROLES_ATTR_NAME, attrs);
        Attribute privileges = AttributeUtil.find(OracleConstants.ORACLE_PRIVS_ATTR_NAME, attrs);
        List<String> rolesSQL = new OracleRolesAndPrivsBuilder(cfg.getCSSetup())
                .buildGrantRolesSQL(userName, OracleConnectorHelper.castList(
                        roles, String.class)); 
        List<String> privilegesSQL = new OracleRolesAndPrivsBuilder(cfg.getCSSetup())
        .buildGrantPrivilegesSQL(userName, OracleConnectorHelper.castList(
                privileges, String.class)); 
        try {
            //Now execute create and grant statements
            SQLUtil.executeUpdateStatement(adminConn, createSQL);
            for(String sql : rolesSQL){
                SQLUtil.executeUpdateStatement(adminConn, sql);
            }
            for(String sql : privilegesSQL){
                SQLUtil.executeUpdateStatement(adminConn, sql);
            }
            adminConn.commit();
            log.info("User created : [{0}]", userName);
        } catch (Exception e) {
            SQLUtil.rollbackQuietly(adminConn);
            throw ConnectorException.wrap(e);
        }
        return new Uid(userName);
    }

    
    private void checkCreateAttributes(Map<String, Attribute> map) {
		for(Attribute attr : map.values()){
			if(!VALID_CREATE_ATTRIBUTES.contains(attr.getName())){
				throw new IllegalArgumentException(MessageFormat.format("Attribute [{0}] not supported for create",attr.getName()));
			}
		}
	}

	private void checkUserNotExist(String user) {
        boolean userExist = new OracleUserReader(adminConn).userExist(user);
        if(userExist){
            throw new AlreadyExistsException("User " + user + " already exists");
        }
    }

}
