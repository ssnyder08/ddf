/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either
 * version 3 of the License, or any later version. 
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
package ddf.security.service;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.ws.security.WSSecurityException;
import org.apache.ws.security.saml.ext.AssertionWrapper;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.saml2.core.AttributeStatement;
import org.opensaml.xml.XMLObject;
import org.opensaml.xml.schema.XSString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.security.assertion.SecurityAssertion;
import ddf.security.permission.KeyValuePermission;


/**
 * Abstraction class used to perform authorization for a realm. This class
 * contains generic methods that can be used to parse out the credentials from
 * an incoming security token. It also handles caching tokens for later use.
 */
public abstract class AbstractAuthorizingRealm extends AuthorizingRealm
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractAuthorizingRealm.class);

    private static final String SAML_ROLE = "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/role";

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo( PrincipalCollection principalCollection )
    {
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        LOGGER.debug("Retrieving authorizationinfo for {}", principalCollection.getPrimaryPrincipal());
        SecurityAssertion assertion = principalCollection.oneByType(SecurityAssertion.class);
        if (assertion == null)
        {
            String msg = "No assertion found, cannot retrieve authorization info.";
            LOGGER.warn(msg);
            throw new AuthorizationException(msg);
        }
        try
        {
            AssertionWrapper wrapper = new AssertionWrapper(assertion.getSecurityToken().getToken());
            List<AttributeStatement> attributeStatements = wrapper.getSaml2().getAttributeStatements();
            List<Attribute> attributes;
            Set<Permission> permissions = new HashSet<Permission>();
            Set<String> roles = new HashSet<String>();
            KeyValuePermission curPermission;
            for ( AttributeStatement curStatement : attributeStatements )
            {
                attributes = curStatement.getAttributes();

                for ( Attribute curAttribute : attributes )
                {
                    curPermission = new KeyValuePermission(curAttribute.getName());
                    for ( XMLObject curValue : curAttribute.getAttributeValues() )
                    {
                        if (curValue instanceof XSString)
                        {
                            String value = ((XSString) curValue).getValue();
                            curPermission.addValue(value);
                            if (SAML_ROLE.equals(curAttribute.getName()))
                            {
                                LOGGER.debug("Adding role to authorization info: {}", value);
                                roles.add(value);
                            }
                        }
                    }
                    LOGGER.debug("Adding permission: {}", curPermission.toString());
                    permissions.add(curPermission);
                }
            }
            info.setObjectPermissions(permissions);
            info.setRoles(roles);
        }
        catch (WSSecurityException e)
        {
            String msg = "Error Processing Token.";
            LOGGER.warn(msg);
            throw new AuthorizationException(msg, e);
        }

        return info;
    }

}