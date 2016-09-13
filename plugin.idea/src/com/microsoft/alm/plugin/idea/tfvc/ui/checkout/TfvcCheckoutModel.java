// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.idea.tfvc.ui.checkout;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.CheckoutProvider;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.microsoft.alm.helpers.Path;
import com.microsoft.alm.plugin.context.RepositoryContext;
import com.microsoft.alm.plugin.context.ServerContext;
import com.microsoft.alm.plugin.external.commands.CreateWorkspaceCommand;
import com.microsoft.alm.plugin.external.commands.UpdateWorkspaceMappingCommand;
import com.microsoft.alm.plugin.external.models.Workspace;
import com.microsoft.alm.plugin.external.utils.CommandUtils;
import com.microsoft.alm.plugin.idea.common.resources.TfPluginBundle;
import com.microsoft.alm.plugin.idea.common.ui.checkout.VcsSpecificCheckoutModel;
import com.microsoft.alm.plugin.idea.common.utils.IdeaHelper;
import com.microsoft.alm.plugin.idea.common.utils.VcsHelper;
import com.microsoft.alm.plugin.idea.tfvc.core.TFSVcs;
import com.microsoft.alm.plugin.idea.tfvc.ui.workspace.WorkspaceController;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class TfvcCheckoutModel implements VcsSpecificCheckoutModel {


    @Override
    public void doCheckout(final Project project, final CheckoutProvider.Listener listener,
                           final ServerContext context, final VirtualFile destinationParent,
                           final String directoryName, final String parentDirectory, final boolean isAdvancedChecked) {
        final String workspaceName = directoryName;
        final String teamProjectName = getRepositoryName(context);
        final String localPath = Path.combine(parentDirectory, directoryName);
        final AtomicBoolean checkoutResult = new AtomicBoolean();
        (new Task.Backgroundable(project,
                TfPluginBundle.message(TfPluginBundle.KEY_CHECKOUT_TFVC_CREATING_WORKSPACE),
                true, PerformInBackgroundOption.DEAF) {
            public void run(@NotNull final ProgressIndicator indicator) {
                IdeaHelper.setProgress(indicator, 0.10, TfPluginBundle.message(TfPluginBundle.KEY_CHECKOUT_TFVC_PROGRESS_CREATING));

                // Create the workspace with default values
                final CreateWorkspaceCommand command = new CreateWorkspaceCommand(
                        context, workspaceName, TfPluginBundle.message(TfPluginBundle.KEY_CHECKOUT_TFVC_WORKSPACE_COMMENT), null, null);
                command.runSynchronously();

                IdeaHelper.setProgress(indicator, 0.20, TfPluginBundle.message(TfPluginBundle.KEY_CHECKOUT_TFVC_PROGRESS_ADD_ROOT));

                // Map the project root to the local folder
                final String serverPath = VcsHelper.TFVC_ROOT + teamProjectName;
                final UpdateWorkspaceMappingCommand mappingCommand = new UpdateWorkspaceMappingCommand(context, workspaceName,
                        new Workspace.Mapping(serverPath, localPath, false), false);
                mappingCommand.runSynchronously();

                IdeaHelper.setProgress(indicator, 0.30, TfPluginBundle.message(TfPluginBundle.KEY_CHECKOUT_TFVC_PROGRESS_CREATE_FOLDER));

                // Ensure that the local folder exists
                final File file = new File(localPath);
                if (!file.mkdirs()) {
                    //TODO should we throw here?
                }

                // if advanced is set, then sync just some of the files (those that we need for IntelliJ)
                // Otherwise, sync all the files for the team project
                if (!isAdvancedChecked) {
                    IdeaHelper.setProgress(indicator, 0.50, TfPluginBundle.message(TfPluginBundle.KEY_CHECKOUT_TFVC_PROGRESS_SYNC));
                    // Sync all files recursively
                    CommandUtils.syncWorkspace(context, localPath);
                }

                IdeaHelper.setProgress(indicator, 1.00, "", true);

                // No exception means that it was successful
                checkoutResult.set(true);
            }

            public void onSuccess() {
                if (checkoutResult.get()) {
                    // Do final checkout steps to setup project
                    UpdateVersionControlSystem(project, parentDirectory, directoryName, destinationParent, listener);

                    // Check the isAdvanced flag
                    if (isAdvancedChecked) {
                        // A new project was created during checkout so use that going forward
                        final Project currentProject = IdeaHelper.getCurrentProject();

                        // The user wants to edit the workspace before syncing...
                        final RepositoryContext repositoryContext = RepositoryContext.createTfvcContext(localPath, workspaceName, teamProjectName, context.getServerUri().toString());
                        final WorkspaceController controller = new WorkspaceController(currentProject, repositoryContext, workspaceName);
                        if (controller.showModalDialog(false)) {
                            // Save and Sync the workspace (this will be backgrounded)
                            controller.saveWorkspace(true, null);
                        }
                    }
                }
            }
        }).queue();
    }

    private void UpdateVersionControlSystem(final Project project, String parentDirectory, String directoryName, final VirtualFile destinationParent, CheckoutProvider.Listener listener) {
        // Add our new directory to IntelliJ's project
        DvcsUtil.addMappingIfSubRoot(project, FileUtil.join(new String[]{parentDirectory, directoryName}), TFSVcs.TFVC_NAME);

        // Check the folder for any dirty files
        destinationParent.refresh(true, true, new Runnable() {
            public void run() {
                if (project.isOpen() && !project.isDisposed() && !project.isDefault()) {
                    VcsDirtyScopeManager mgr = VcsDirtyScopeManager.getInstance(project);
                    mgr.fileDirty(destinationParent);
                }

            }
        });

        // Trigger our listener events
        listener.directoryCheckedOut(new File(parentDirectory, directoryName), TFSVcs.getKey());
        listener.checkoutCompleted();
    }

    @Override
    public String getTelemetryAction() {
        return "create_workspace";
    }

    @Override
    public String getButtonText() {
        return TfPluginBundle.message(TfPluginBundle.KEY_CHECKOUT_DIALOG_CREATE_WORKSPACE_BUTTON);
    }

    @Override
    public String getRepositoryName(final ServerContext context) {
        return (context != null && context.getTeamProjectReference() != null)
                ? context.getTeamProjectReference().getName() : StringUtils.EMPTY;
    }

    @Override
    public RepositoryContext.Type getRepositoryType() {
        return RepositoryContext.Type.TFVC;
    }
}
