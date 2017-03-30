// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.idea.common.ui.checkout;

import com.google.common.annotations.VisibleForTesting;
import com.microsoft.alm.plugin.authentication.AuthenticationInfo;
import com.microsoft.alm.plugin.authentication.VsoAuthenticationProvider;
import com.microsoft.alm.plugin.context.RepositoryContext;
import com.microsoft.alm.plugin.idea.common.resources.TfPluginBundle;
import com.microsoft.alm.plugin.idea.common.ui.common.LookupHelper;
import com.microsoft.alm.plugin.idea.common.ui.common.ServerContextTableModel;
import com.microsoft.alm.plugin.operations.ServerContextLookupOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class implements the abstract CheckoutPageModel for VSO.
 */
public class VsoCheckoutPageModel extends CheckoutPageModelImpl {
    private static final Logger logger = LoggerFactory.getLogger(VsoCheckoutPageModel.class);
    private VsoAuthenticationProvider authenticationProvider = VsoAuthenticationProvider.getInstance();
    private final ServerContextLookupOperation.ContextScope scope;


    public VsoCheckoutPageModel(final CheckoutModel checkoutModel) {
        this(checkoutModel, true);
    }

    @VisibleForTesting
    public VsoCheckoutPageModel(final CheckoutModel checkoutModel, final boolean autoLoad) {
        super(checkoutModel,
                checkoutModel.getRepositoryType() == RepositoryContext.Type.GIT ?
                        ServerContextTableModel.VSO_GIT_REPO_COLUMNS :
                        ServerContextTableModel.VSO_TFVC_REPO_COLUMNS);


        // Set default server name for VSO
        setServerNameInternal(TfPluginBundle.message(TfPluginBundle.KEY_USER_ACCOUNT_PANEL_VSO_SERVER_NAME));

        setConnected(false);
        setAuthenticating(false);

        // Check the repository type to get the SCOPE of the query
        // Git => repository scope
        // TFVC => project scope (TFVC repositories are not separate from the team projects)
        scope = (checkoutModel.getRepositoryType() == RepositoryContext.Type.GIT) ?
                ServerContextLookupOperation.ContextScope.REPOSITORY :
                ServerContextLookupOperation.ContextScope.PROJECT;

        final String serverUri = LookupHelper.getVsspsUrlFromDisplayName(this.getServerName());
        if (autoLoad && authenticationProvider.isAuthenticated(serverUri)) {
            logger.info("Loading contexts in constructor");
            LookupHelper.loadVsoContexts(this, this,
                    authenticationProvider, getRepositoryProvider(),
                    scope);
        } else {
            logger.info("Skipping loading contexts in constructor");
        }
    }

    @Override
    protected AuthenticationInfo getAuthenticationInfo() {
        return authenticationProvider.getAuthenticationInfo(this.getServerName());
    }

    @Override
    public void signOut() {
        logger.info("signOut called");
        String serverUri = LookupHelper.getVsspsUrlFromDisplayName(this.getServerName());
        authenticationProvider.clearAuthenticationDetails(serverUri);

        super.signOut();
    }

    @Override
    public void loadRepositories() {
        logger.info("loadRepositories called");
        LookupHelper.authenticateAndLoadVsoContexts(this, this,
                authenticationProvider, getRepositoryProvider(),
                scope);
    }
}
