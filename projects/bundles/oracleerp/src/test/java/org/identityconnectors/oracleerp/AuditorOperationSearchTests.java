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
package org.identityconnectors.oracleerp;

import static org.identityconnectors.oracleerp.OracleERPUtil.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Set;

import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.Filter;
import org.identityconnectors.framework.common.objects.filter.FilterBuilder;
import org.identityconnectors.test.common.TestHelpers;
import org.junit.Test;




/**
 * Attempts to test the {@link AuditorOperationSearch}.
 * 
 * @author petr
 * @version 1.0
 * @since 1.0
 */
public class AuditorOperationSearchTests extends OracleERPTestsBase { 

    /**
     * Test method for
     * {@link AuditorOperationSearch#executeQuery(ObjectClass, org.identityconnectors.dbcommon.FilterWhereBuilder, org.identityconnectors.framework.common.objects.ResultsHandler, OperationOptions)}
     */
    @Test
    public void testAuditorSearch() {
        final OracleERPConnector c = getConnector(CONFIG_TST);


        final Set<Attribute> attrs = getAttributeSet(ACCOUNT_ALL_ATTRS);
        replaceNameByRandom(attrs);
        
        final Uid uid = c.create(ObjectClass.ACCOUNT, attrs, null);
        assertNotNull(uid);
        
        final OperationOptionsBuilder oob = new OperationOptionsBuilder();
        final Set<Attribute> attrsOpt = getAttributeSet(ACCOUNT_OPTIONS);
        attrsOpt.add(AttributeBuilder.build("id",uid.getUidValue()));
        addAuditorDataOptions(oob, attrsOpt);
        
        
        final Filter filter = FilterBuilder.equalTo(AttributeBuilder.build(NAME, "Does no mather, not null"));
        List<ConnectorObject> results = TestHelpers.searchToList(c, AUDITOR_RESPS_OC, filter, oob.build());
        System.out.println(results);
        assertTrue("expect 1 connector object", results.size() == 1);
    }
}
