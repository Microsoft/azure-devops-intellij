// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.idea.ui.common;

import com.intellij.ide.BrowserUtil;
import com.microsoft.alm.common.utils.UrlHelper;
import com.microsoft.alm.plugin.authentication.AuthHelper;
import com.microsoft.alm.plugin.authentication.AuthenticationInfo;
import com.microsoft.alm.plugin.authentication.VsoAuthenticationInfo;
import com.microsoft.alm.plugin.context.ServerContext;
import com.microsoft.alm.plugin.context.ServerContextManager;
import com.microsoft.alm.plugin.idea.resources.TfPluginBundle;
import com.microsoft.tf.common.authentication.aad.PersonalAccessTokenFactory;
import com.microsoft.tf.common.authentication.aad.TokenScope;
import com.microsoft.tf.common.authentication.aad.impl.PersonalAccessTokenFactoryImpl;
import com.microsoft.visualstudio.services.authentication.DelegatedAuthorization.webapi.model.SessionToken;
import com.microsoftopentechnologies.auth.AuthenticationResult;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class LoginPageModelImpl extends AbstractModel implements LoginPageModel {
    private boolean connected = false;
    private boolean authenticating = false;
    //default values for Strings should be "" rather than null.
    private String userName = "";
    private String serverName = "";
    private PageModel pageModel;

    public LoginPageModelImpl(final PageModel pageModel) {
        this.pageModel = pageModel;
    }

    /**
     * Generates a new server context with session token information for VSO and saves it as the active context
     * @param context
     * @return
     */
    public ServerContext completeSignIn(final ServerContext context) {
        if(context.getType() == ServerContext.Type.VSO_DEPLOYMENT) {
           //generate PAT
            VsoAuthenticationInfo vsoAuthenticationInfo = (VsoAuthenticationInfo) context.getAuthenticationInfo();
            final AuthenticationResult result = AuthHelper.getAuthenticationResult(vsoAuthenticationInfo);
            final PersonalAccessTokenFactory patFactory = new PersonalAccessTokenFactoryImpl(result);

            //TODO: handle case where session token cannot be created
            SessionToken sessionToken = patFactory.createSessionToken(
                    TfPluginBundle.message(TfPluginBundle.KEY_CHECKOUT_DIALOG_PAT_TOKEN_DESC),
                    Arrays.asList(TokenScope.CODE_READ, TokenScope.CODE_WRITE, TokenScope.CODE_MANAGE), context.getAccountId());

            //create a VSO context with session token
            final AuthenticationInfo finalAuthenticationInfo = new VsoAuthenticationInfo(context.getUri().toString(),
                    result, sessionToken);
            final ServerContext newContext = ServerContext.createVSOContext(context, (VsoAuthenticationInfo) finalAuthenticationInfo);
            ServerContextManager.getInstance().setActiveContext(newContext);
            return newContext;
        } else {
            ServerContextManager.getInstance().setActiveContext(context);
            return context;
        }
    }

    @Override
    public void gotoLink(final String url) {
        if (StringUtils.isNotEmpty(url)) {
            BrowserUtil.browse(url);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void setConnected(final boolean connected) {
        if (this.connected != connected) {
            this.connected = connected;
            setChangedAndNotify(PROP_CONNECTED);
        }
    }

    @Override
    public void signOut() {
        // TODO should this be in the generic impl class?
        setAuthenticating(false);
        setConnected(false);
        setServerName("");
        // since the user explicitly signed out, clear the context
        ServerContextManager.getInstance().clearServerContext(ServerContextManager.getInstance().getActiveContext());
    }

    @Override
    public String getUserName() {
        return userName;
    }

    @Override
    public void setUserName(final String userName) {
        if (!StringUtils.equals(this.userName, userName)) {
            this.userName = userName;
            setChangedAndNotify(PROP_USER_NAME);
        }
    }

    @Override
    public boolean isAuthenticating() {
        return authenticating;
    }

    @Override
    public void setAuthenticating(final boolean authenticating) {
        if (this.authenticating != authenticating) {
            this.authenticating = authenticating;
            setChangedAndNotify(PROP_AUTHENTICATING);
        }
    }

    @Override
    public String getServerName() {
        return serverName;
    }

    @Override
    public void setServerName(final String serverName) {
        if (!StringUtils.equals(this.serverName, serverName)) {
            final String newServerName;
            // Allow just the server name as a short hand
            if (StringUtils.isNotEmpty(serverName) && !StringUtils.contains(serverName, UrlHelper.URL_SEPARATOR)
                    && !StringUtils.equals(serverName, TfPluginBundle.message(TfPluginBundle.KEY_USER_ACCOUNT_PANEL_VSO_SERVER_NAME))) {
                // no slash and not "Microsoft Account" means it must just be a onpremise TFS server name, so add all the normal stuff
                newServerName = String.format(DEFAULT_SERVER_FORMAT, serverName);
            } else {
                newServerName = serverName;
            }
            setServerNameInternal(newServerName);
        }
    }

    /**
     * This method allows the derived classes to directly set the server name without the normal setServerName
     * method changing it.
     */
    protected void setServerNameInternal(final String serverName) {
        this.serverName = serverName;
        setChangedAndNotify(PROP_SERVER_NAME);
    }

    @Override
    public void addError(final ModelValidationInfo error) {
        if (pageModel != null) {
            pageModel.addError(error);
        }
    }

    @Override
    public void clearErrors() {
        if (pageModel != null) {
            pageModel.clearErrors();
        }
    }

    @Override
    public List<ModelValidationInfo> getErrors() {
        if (pageModel != null) {
            return pageModel.getErrors();
        }

        return Collections.unmodifiableList(new ArrayList<ModelValidationInfo>());
    }

    @Override
    public boolean hasErrors() {
        if (pageModel != null) {
            return pageModel.hasErrors();
        }

        return false;
    }

    @Override
    public ModelValidationInfo validate() {
        if (!isConnected()) {
            //We should never get here in the UI since "Clone/Import" is disabled unless the user is connected
            //Leaving the extra check here for safety in case the "Clone/Import" gets enabled for some other reason
            return ModelValidationInfo.createWithResource(PROP_CONNECTED,
                    TfPluginBundle.KEY_LOGIN_FORM_ERRORS_NOT_CONNECTED);
        }

        return pageModel.validate();
    }
}
