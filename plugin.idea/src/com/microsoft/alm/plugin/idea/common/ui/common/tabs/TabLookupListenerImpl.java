// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.idea.common.ui.common.tabs;

import com.microsoft.alm.plugin.authentication.AuthHelper;
import com.microsoft.alm.plugin.context.RepositoryContext;
import com.microsoft.alm.plugin.context.ServerContext;
import com.microsoft.alm.plugin.context.ServerContextManager;
import com.microsoft.alm.plugin.idea.common.ui.common.VcsTabStatus;
import com.microsoft.alm.plugin.idea.common.utils.IdeaHelper;
import com.microsoft.alm.plugin.operations.Operation;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Listener for lookup operations
 */
public abstract class TabLookupListenerImpl implements Operation.Listener {

    private final TabModel model;
    private Operation activeOperation;
    protected RepositoryContext repositoryContext;

    public TabLookupListenerImpl(@NotNull final TabModel model) {
        this.model = model;
    }

    /**
     * Load data based on the repository context
     *
     * @param repositoryContext
     */
    public abstract void loadData(final RepositoryContext repositoryContext, final Operation.Inputs inputs);

    /**
     * Load data asynchronously based on the given operation.
     * Only do this if another operation is not in progress though.
     *
     * @param activeOperation
     */
    protected void loadData(final Operation activeOperation, final Operation.Inputs inputs) {
        assert activeOperation != null;
        if (model.getTabStatus() != VcsTabStatus.LOADING_IN_PROGRESS) {
            this.model.clearData();
            this.activeOperation = activeOperation;
            this.activeOperation.addListener(this);
            this.activeOperation.doWorkAsync(inputs);
        }
    }

    @Override
    public void notifyLookupStarted() {
        IdeaHelper.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                model.setTabStatus(VcsTabStatus.LOADING_IN_PROGRESS);
            }
        });
    }

    @Override
    public void notifyLookupCompleted() {
        operationDone();
        IdeaHelper.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                //set status to complete if it is still in-progress and not updated by notifyLookupResults
                if (model.getTabStatus() == VcsTabStatus.LOADING_IN_PROGRESS) {
                    model.setTabStatus(VcsTabStatus.LOADING_COMPLETED);
                }
            }
        });
    }

    @Override
    public void notifyLookupResults(final Operation.Results results) {
        if (results.isCancelled()) {
            operationDone();
            IdeaHelper.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    model.setTabStatus(VcsTabStatus.LOADING_COMPLETED);
                }
            });
        } else if (results.hasError()) {
            final ServerContext newContext;
            if (AuthHelper.isNotAuthorizedError(results.getError())) {
                newContext = ServerContextManager.getInstance().updateAuthenticationInfo(repositoryContext.getUrl()); //call this on a background thread, will hang UI thread if not
            } else if (results.getError() instanceof java.lang.AssertionError &&
                    StringUtils.containsIgnoreCase(results.getError().getMessage(), "Microsoft.TeamFoundation.Git.Server.GitRepositoryNotFoundException")) {
                //repo was probably deleted on the server
                ServerContextManager.getInstance().remove(repositoryContext.getUrl());
                newContext = null;
            } else {
                newContext = null;
            }
            IdeaHelper.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (AuthHelper.isNotAuthorizedError(results.getError())) {
                        if (newContext != null) {
                            //try reloading the data with new context and authentication info
                            model.loadData();
                        } else {
                            //user cancelled login, don't retry
                            model.setTabStatus(VcsTabStatus.NO_AUTH_INFO);
                        }
                    } else {
                        model.setTabStatus(VcsTabStatus.LOADING_COMPLETED_ERRORS);
                    }
                }
            });
        } else {
            IdeaHelper.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    model.appendData(results);
                }
            });
        }
    }

    protected void operationDone() {
        if (activeOperation != null) {
            activeOperation.removeListener(this);
            activeOperation = null;
        }
    }

    public void terminateActiveOperation() {
        if (activeOperation != null) {
            activeOperation.removeListener(this);
            activeOperation.cancel();
            activeOperation = null;
        }
    }
}