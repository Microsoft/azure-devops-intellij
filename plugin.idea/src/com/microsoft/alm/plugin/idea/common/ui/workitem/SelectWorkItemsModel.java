// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.idea.common.ui.workitem;

import com.intellij.openapi.project.Project;
import com.microsoft.alm.common.utils.ArgumentHelper;
import com.microsoft.alm.common.utils.UrlHelper;
import com.microsoft.alm.plugin.authentication.AuthHelper;
import com.microsoft.alm.plugin.context.RepositoryContext;
import com.microsoft.alm.plugin.context.ServerContext;
import com.microsoft.alm.plugin.context.ServerContextManager;
import com.microsoft.alm.plugin.idea.common.ui.common.AbstractModel;
import com.microsoft.alm.plugin.idea.common.utils.IdeaHelper;
import com.microsoft.alm.plugin.operations.Operation;
import com.microsoft.alm.plugin.operations.WorkItemLookupOperation;
import com.microsoft.alm.workitemtracking.webapi.models.WorkItem;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.ListSelectionModel;
import java.net.URI;

public class SelectWorkItemsModel extends AbstractModel {
    private static final Logger logger = LoggerFactory.getLogger(SelectWorkItemsModel.class);

    public final static String PROP_LOADING = "loading";
    public final static String PROP_FILTER = "filter";
    public final static String PROP_SERVER_NAME = "serverName";

    private final WorkItemsTableModel tableModel;
    private final Project project;
    private final RepositoryContext repositoryContext;
    private boolean loading = false;
    private String filter;
    private ServerContext latestServerContext;

    private boolean maxItemsReached = false;

    public SelectWorkItemsModel(final Project project, final RepositoryContext repositoryContext) {
        ArgumentHelper.checkNotNull(project, "project");
        ArgumentHelper.checkNotNull(repositoryContext, "repositoryContext");
        this.project = project;
        this.repositoryContext = repositoryContext;
        tableModel = new WorkItemsTableModel(WorkItemsTableModel.DEFAULT_COLUMNS);
    }

    public boolean isLoading() {
        return loading;
    }

    public void setLoading(final boolean loading) {
        if (this.loading != loading) {
            this.loading = loading;
            setChangedAndNotify(PROP_LOADING);
        }
    }

    public boolean isMaxItemsReached() {
        return maxItemsReached;
    }

    //TODO replace server label on form with UserAccountControl
    public String getServerName() {
        if (latestServerContext != null) {
            return latestServerContext.getServerUri().toString();
        }
        return StringUtils.EMPTY;
    }

    public void loadWorkItems() {
        setLoading(true);
        tableModel.clearRows();

        WorkItemLookupOperation operation = new WorkItemLookupOperation(repositoryContext);
        operation.addListener(new Operation.Listener() {
            @Override
            public void notifyLookupStarted() {
                // nothing to do
                logger.info("WorkItemLookupOperation started.");
            }

            @Override
            public void notifyLookupCompleted() {
                logger.info("WorkItemLookupOperation completed.");

                // Set loading to false to stop the spinner
                IdeaHelper.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        setLoading(false);
                    }
                });
            }

            @Override
            public void notifyLookupResults(final Operation.Results results) {
                final WorkItemLookupOperation.WitResults wiResults = (WorkItemLookupOperation.WitResults) results;
                maxItemsReached = wiResults.maxItemsReached();

                if (wiResults.isCancelled()) {
                    // Do nothing
                } else {
                    final ServerContext newContext;
                    if (wiResults.hasError() && AuthHelper.isNotAuthorizedError(wiResults.getError())) {
                        //401 or 403 - token is not valid, prompt user for credentials and retry
                        newContext = ServerContextManager.getInstance().updateAuthenticationInfo(repositoryContext.getUrl()); //call this on a background thread, will hang UI thread if not
                    } else {
                        newContext = null;
                    }
                    // Update table model on UI thread
                    IdeaHelper.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (wiResults.hasError()) {
                                if (AuthHelper.isNotAuthorizedError(wiResults.getError())) {
                                    if (newContext != null) {
                                        //retry loading workitems with new context and authentication info
                                        loadWorkItems();
                                    } else {
                                        //user cancelled login, don't retry
                                    }
                                } else {
                                    IdeaHelper.showErrorDialog(project, wiResults.getError());
                                }
                            }

                            //update the server label
                            if (wiResults.getContext() != null) {
                                // Set the latestServerContext
                                latestServerContext = wiResults.getContext();
                                // Notify observers that the server name changed
                                setChangedAndNotify(PROP_SERVER_NAME);
                            }

                            tableModel.addWorkItems(wiResults.getWorkItems());
                        }
                    });
                }
            }
        });

        operation.doWorkAsync(new WorkItemLookupOperation.WitInputs(
                WorkItemHelper.getAssignedToMeQuery(),
                WorkItemHelper.getDefaultFields()));
    }

    public void createWorkItem() {
        if (latestServerContext != null && latestServerContext.getTeamProjectURI() != null) {
            final URI teamProjectURI = latestServerContext.getTeamProjectURI();
            if (teamProjectURI != null) {
                super.gotoLink(UrlHelper.getCreateWorkItemURI(teamProjectURI).toString());
            } else {
                logger.warn("Can't goto 'create work item' link: Unable to get team project URI from server context.");
            }
        }
    }

    public void gotoMyWorkItems() {
        if (latestServerContext != null && latestServerContext.getTeamProjectURI() != null) {
            final URI teamProjectURI = latestServerContext.getTeamProjectURI();
            if (teamProjectURI != null) {
                super.gotoLink(UrlHelper.getCreateWorkItemURI(teamProjectURI).toString());
            } else {
                logger.warn("Can't goto 'create work item' link: Unable to get team project URI from server context.");
            }
        }
    }

    public WorkItemsTableModel getTableModel() {
        return tableModel;
    }

    public ListSelectionModel getTableSelectionModel() {
        return tableModel.getSelectionModel();
    }

    public void setFilter(final String filter) {
        if (!StringUtils.equals(this.filter, filter)) {
            this.filter = filter;
            setChangedAndNotify(PROP_FILTER);
            tableModel.setFilter(filter);
        }
    }

    public String getFilter() {
        return filter;
    }

    public String getComment() {
        final ListSelectionModel selectionModel = getTableSelectionModel();
        if (!selectionModel.isSelectionEmpty()) {
            final StringBuilder sb = new StringBuilder();
            String separator = "";
            for (final WorkItem item : tableModel.getSelectedWorkItems()) {
                sb.append(separator);
                sb.append(WorkItemHelper.getWorkItemCommitMessage(item));
                separator = "\n";
            }
            return sb.toString();
        }

        return StringUtils.EMPTY;
    }

}

