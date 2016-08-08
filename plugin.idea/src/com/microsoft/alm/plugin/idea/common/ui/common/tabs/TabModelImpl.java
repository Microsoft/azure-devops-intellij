// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.idea.common.ui.common.tabs;

import com.intellij.openapi.project.Project;
import com.microsoft.alm.client.utils.StringUtil;
import com.microsoft.alm.plugin.context.RepositoryContext;
import com.microsoft.alm.plugin.idea.common.services.PropertyServiceImpl;
import com.microsoft.alm.plugin.idea.common.ui.common.AbstractModel;
import com.microsoft.alm.plugin.idea.common.ui.common.FilteredModel;
import com.microsoft.alm.plugin.idea.common.ui.common.VcsTabStatus;
import com.microsoft.alm.plugin.idea.common.utils.VcsHelper;
import com.microsoft.alm.plugin.idea.git.ui.vcsimport.ImportController;
import com.microsoft.alm.plugin.operations.Operation;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TabModelImpl<T extends FilteredModel> extends AbstractModel implements TabModel {
    private static final Logger logger = LoggerFactory.getLogger(TabModelImpl.class);

    protected final Project project;
    protected final T viewForModel;
    private final String propertyStoragePrefix;
    protected TabLookupListenerImpl dataProvider;
    protected RepositoryContext repositoryContext;
    private String filter = StringUtils.EMPTY;
    private boolean autoRefresh;
    private VcsTabStatus tabStatus = VcsTabStatus.NOT_TF_GIT_REPO;
    protected Operation.CredInputsImpl operationInputs;

    public TabModelImpl(@NotNull final Project project, @NotNull T viewModel, String propertyStoragePrefix) {
        this.project = project;
        this.viewForModel = viewModel;
        // Make sure the prefix isn't null
        this.propertyStoragePrefix = StringUtil.isNullOrEmpty(propertyStoragePrefix) ? StringUtil.EMPTY : propertyStoragePrefix;

        // Get the value of auto refresh from the property service
        // If the value is found, use it. Otherwise default to true;
        String autoRefreshText = PropertyServiceImpl.getInstance().getProperty(propertyStoragePrefix + PROP_AUTO_REFRESH);
        autoRefresh = StringUtils.isEmpty(autoRefreshText) ? true : Boolean.parseBoolean(autoRefreshText);

        // need to create data provider after calling parent class since it passes the class to the provider
        createDataProvider();
    }

    /**
     * Creates data provider object based on child class
     */
    protected abstract void createDataProvider();

    public abstract void openGitRepoLink();

    public VcsTabStatus getTabStatus() {
        return tabStatus;
    }

    public void setTabStatus(final VcsTabStatus status) {
        if (this.tabStatus != status) {
            this.tabStatus = status;
            setChangedAndNotify(PROP_TAB_STATUS);
        }
    }

    public T getModelForView() {
        return viewForModel;
    }

    protected boolean isTeamServicesRepository() {
        repositoryContext = VcsHelper.getRepositoryContext(project);
        if (repositoryContext == null) {
            setTabStatus(VcsTabStatus.NOT_TF_GIT_REPO);
            logger.debug("isTeamServicesRepository: Failed to get Git repo for current project");
            return false;
        } else {
            return true;
        }
    }

    public void loadData() {
        loadData(true);
    }

    public void loadData(final boolean promptForCreds) {
        if (isTeamServicesRepository()) {
            logger.info("Loading data for tab. Prompting " + promptForCreds);
            getOperationInputs().setPromptForCreds(promptForCreds);
            dataProvider.loadData(repositoryContext, getOperationInputs());
        }
    }

    public void importIntoTeamServicesGit() {
        final ImportController controller = new ImportController(project);
        controller.showModalDialog();
    }

    public abstract void openSelectedItemsLink();

    public abstract void appendData(final Operation.Results results);

    public abstract void clearData();

    public abstract void createNewItem();

    public void setFilter(final String filter) {
        if (!StringUtils.equals(this.filter, filter)) {
            this.filter = filter;
            setChangedAndNotify(PROP_FILTER);
            viewForModel.setFilter(filter);
        }
    }

    public String getFilter() {
        return filter;
    }

    public void setAutoRefresh(final boolean autoRefresh) {
        if (this.autoRefresh != autoRefresh) {
            this.autoRefresh = autoRefresh;
            setChangedAndNotify(PROP_AUTO_REFRESH);
            PropertyServiceImpl.getInstance().setProperty(propertyStoragePrefix + PROP_AUTO_REFRESH,
                    Boolean.toString(autoRefresh));
        }
    }

    public boolean getAutoRefresh() {
        return autoRefresh;
    }

    public Operation.CredInputsImpl getOperationInputs() {
        return operationInputs;
    }

    public void setOperationInputs(final Operation.CredInputsImpl operationInputs) {
        this.operationInputs = operationInputs;
    }

    public void dispose() {
        dataProvider.terminateActiveOperation();
    }
}