package org.identityconnectors.oracle;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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
import static org.identityconnectors.oracle.OracleMessages.*;

/**
 * Oracle create operation. 
 * It builds create sql using {@link OracleCreateOrAlterStBuilder} and builds grants using {@link OracleRolesAndPrivsBuilder} 
 * @author kitko
 *
 */
final class OracleOperationCreate extends AbstractOracleOperation implements CreateOp{
    private final static Log log = Log.getLog(OracleOperationCreate.class);
	private static final Collection<String> VALID_CREATE_ATTRIBUTES;
	
	static {
		Collection<String> tmp = new TreeSet<String>(OracleConnectorHelper.getAttributeNamesComparator());
		tmp.addAll(OracleConstants.ALL_ATTRIBUTE_NAMES);
		tmp.removeAll(Arrays.asList(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME,OperationalAttributes.DISABLE_DATE_NAME));
		VALID_CREATE_ATTRIBUTES = Collections.unmodifiableCollection(tmp);
	}
    
	
    OracleOperationCreate(OracleConfiguration cfg,Connection adminConn) {
        super(cfg, adminConn);
    }

    public Uid create(ObjectClass oclass, Set<Attribute> attrs, OperationOptions options) {
        OracleConnectorHelper.checkObjectClass(oclass, cfg.getConnectorMessages());
        Map<String, Attribute> map = AttributeUtil.toMap(attrs);
        checkCreateAttributes(map);
        String userName = OracleConnectorHelper.getStringValue(map, Name.NAME, cfg.getConnectorMessages());
        new LocalizedAssert(cfg.getConnectorMessages()).assertNotBlank(userName,Name.NAME);
        log.info("Creating user : [{0}]", userName);
        OracleUserAttributes.Builder builder = new OracleUserAttributes.Builder();
        builder.setUserName(userName);
        new OracleAttributesReader(cfg.getConnectorMessages()).readCreateAttributes(map, builder);
        OracleUserAttributes caAttributes = builder.build();
        try {
	        String createSQL = new OracleCreateOrAlterStBuilder(cfg).buildCreateUserSt(caAttributes);
	        if(createSQL == null){
	        	//This should not happen, we want to be just more defensive 
	        	throw new ConnectorException("No create SQL generated, probably not enough attributes");
	        }
	        Attribute roles = AttributeUtil.find(OracleConstants.ORACLE_ROLES_ATTR_NAME, attrs);
	        Attribute privileges = AttributeUtil.find(OracleConstants.ORACLE_PRIVS_ATTR_NAME, attrs);
	        List<String> rolesSQL = new OracleRolesAndPrivsBuilder(cfg.getCSSetup())
	                .buildGrantRoles(userName, OracleConnectorHelper.castList(
	                        roles, String.class)); 
	        List<String> privilegesSQL = new OracleRolesAndPrivsBuilder(cfg.getCSSetup())
	        .buildGrantPrivileges(userName, OracleConnectorHelper.castList(
                privileges, String.class)); 
            //Now execute create and grant statements
            SQLUtil.executeUpdateStatement(adminConn, createSQL);
            //here we can use normal or batch updates
            //actually on driver tested , there was no performance gain, so use just normal updates 
            OracleSpecifics.execStatemts(adminConn, rolesSQL);
            OracleSpecifics.execStatemts(adminConn, privilegesSQL);
            adminConn.commit();
            log.info("User created : [{0}]", userName);
        } catch (Exception e) {
            SQLUtil.rollbackQuietly(adminConn);
            if(e instanceof SQLException){
                SQLException sqle = (SQLException) e;
                if("42000".equals(sqle.getSQLState()) && 1920 == sqle.getErrorCode()){
                    throw new AlreadyExistsException("User [" + userName + "] already exists");
                }
            }
            throw new ConnectorException(cfg.getConnectorMessages().format(MSG_CREATE_OF_USER_FAILED,null,userName),e);
        }
        return new Uid(userName);
    }

    
    private void checkCreateAttributes(Map<String, Attribute> map) {
    	if(map.isEmpty()){
    		throw new IllegalArgumentException(cfg.getConnectorMessages().format(MSG_CREATE_NO_ATTRIBUTES, null));
    	}
		for(Attribute attr : map.values()){
			if(!VALID_CREATE_ATTRIBUTES.contains(attr.getName())){
				throw new IllegalArgumentException(cfg.getConnectorMessages().format(MSG_CREATE_ATTRIBUTE_NOT_SUPPORTED, null, attr.getName()));
			}
		}
	}
}
