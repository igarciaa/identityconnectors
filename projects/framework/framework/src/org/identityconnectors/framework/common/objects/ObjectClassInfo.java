/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.     
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
package org.identityconnectors.framework.common.objects;

import java.util.Set;
import java.util.Map;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.framework.common.serializer.SerializerUtil;


/**
 * Extension of Attribute to distinguish it from a regular attribute.
 * 
 * @author Will Droste
 * @version $Revision: 1.1 $
 * @since 1.0
 */
public final class ObjectClassInfo {

    private final String _type;
    private final Set<AttributeInfo> _info;
    private final boolean _isContainer;

    /**
     * Public only for serialization; Use ObjectClassInfoBuilder instead.
     * @param type The name of the object class, treated as case-insensitive.
     * @param attrInfo The attributes of the object class.
     * @param isContainer True if this can contain other object classes.
     */
    public ObjectClassInfo(String type, 
            Set<AttributeInfo> attrInfo,
            boolean isContainer)
    {        
        if ( type == null ) {
            throw new IllegalArgumentException("Type cannot be null.");
        }
        _type = type;
        _info = CollectionUtil.newReadOnlySet(attrInfo);
        _isContainer = isContainer;
        // check to make sure name exists and if not throw
        Map<String, AttributeInfo> map = AttributeInfoUtil.toMap(attrInfo);
        if (!map.containsKey(Name.NAME)) {
            final String MSG = "Missing 'Name' attribute info.";
            throw new IllegalArgumentException(MSG);
        }
    }
    
    public boolean isContainer() {
        return _isContainer;
    }

    public Set<AttributeInfo> getAttributeInfo() {
        return CollectionUtil.newReadOnlySet(_info);
    }

    public String getType() {
        return _type;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ObjectClassInfo) {
            ObjectClassInfo other = (ObjectClassInfo)obj;
            if (!getType().equalsIgnoreCase(other.getType())) {
                return false;
            }
            if (!CollectionUtil.equals(getAttributeInfo(),
                                          other.getAttributeInfo())) {
                return false;
            }
            if (!_isContainer == other._isContainer) {
                return false;
            }
            return true;            
        }
        return false;

    }

    @Override
    public int hashCode() {
        return _type.toUpperCase().hashCode();
    }


    @Override
    public String toString() {
        return SerializerUtil.serializeXmlObject(this, false);
    }
}
