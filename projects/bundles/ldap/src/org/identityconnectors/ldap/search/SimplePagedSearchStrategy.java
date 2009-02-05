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
package org.identityconnectors.ldap.search;

import java.io.IOException;
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.PagedResultsControl;
import javax.naming.ldap.PagedResultsResponseControl;

import org.identityconnectors.common.logging.Log;

/**
 *
 * @author Andrei Badea
 */
public class SimplePagedSearchStrategy extends LdapSearchStrategy {

    private static final Log log = Log.getLog(SimplePagedSearchStrategy.class);

    private final int pageSize;

    public SimplePagedSearchStrategy(LdapContext initCtx, List<String> baseDNs, String query, SearchControls searchControls, int pageSize) {
        super(initCtx, baseDNs, query, searchControls);
        this.pageSize = pageSize;
    }

    @Override
    public void doSearch(SearchResultsHandler handler) throws NamingException {
        log.ok("Searching in {0} with filter {1} and {2}", baseDNs, query, searchControlsToString(searchControls));

        LdapContext ctx = initCtx.newInstance(new Control[] { createControl(null) });
        try {
            for (String baseDN : baseDNs) {
                byte[] cookie = null;
                do {
                    NamingEnumeration<SearchResult> results = ctx.search(baseDN, query, searchControls);
                    try {
                        boolean proceed = true;
                        while (proceed && results.hasMore()) {
                            proceed = handler.handle(baseDN, results.next());
                        }
                    } finally {
                        results.close();
                    }
                    cookie = getResponseCookie(ctx.getResponseControls());
                    if (cookie != null) {
                        ctx.setRequestControls(new Control[] { createControl(cookie) });
                    }
                } while (cookie != null);
            }
        } finally {
            ctx.close();
        }
    }

    private Control createControl(byte[] cookie) throws NamingException {
        try {
            return new PagedResultsControl(pageSize, cookie, Control.CRITICAL);
        } catch (IOException e) {
            throw (NamingException) new NamingException(e.getMessage()).initCause(e);
        }
    }

    private byte[] getResponseCookie(Control[] controls) {
        if (controls != null) {
            for (Control control : controls) {
                if (control instanceof PagedResultsResponseControl) {
                    PagedResultsResponseControl pagedControl = (PagedResultsResponseControl) control;
                    return pagedControl.getCookie();
                }
            }
        }
        return null;
    }
}