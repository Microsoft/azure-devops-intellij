// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.idea.git.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.microsoft.alm.plugin.idea.common.resources.Icons;
import com.microsoft.alm.plugin.idea.common.resources.TfPluginBundle;
import com.microsoft.alm.plugin.idea.git.ui.pullrequest.CreatePullRequestController;
import com.microsoft.alm.plugin.idea.git.utils.TfGitHelper;
import git4idea.repo.GitRepository;

import java.util.concurrent.TimeUnit;

/**
 * This class adds a "Create Pull Request" menu item to git menu.
 */
public class CreatePullRequestAction extends DumbAwareAction {

    public CreatePullRequestAction() {
        super(TfPluginBundle.message(TfPluginBundle.KEY_ACTIONS_CREATE_PULL_REQUEST),
                TfPluginBundle.message(TfPluginBundle.KEY_ACTIONS_CREATE_PULL_REQUEST_MSG),
                Icons.VSLogoSmall);
    }

    @Override
    public void update(AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getData(CommonDataKeys.PROJECT);

        if (project == null || project.isDefault() || TfGitHelper.getTfGitRepository(project) == null) {
            anActionEvent.getPresentation().setVisible(false);
            anActionEvent.getPresentation().setEnabled(false);
        } else {
            anActionEvent.getPresentation().setVisible(true);
            anActionEvent.getPresentation().setEnabled(true);
        }
    }

    @Override
    public void actionPerformed(AnActionEvent anActionEvent) {
        final Project project = anActionEvent.getData(CommonDataKeys.PROJECT);
        if (project == null)
            return;

        final GitRepository gitRepository = TfGitHelper.getTfGitRepository(project);

        if (gitRepository != null) {
            CreatePullRequestController createPullRequestController
                    = new CreatePullRequestController(project, gitRepository);
            try {
                createPullRequestController.showModalDialog();
            } finally {
                try {
                    // We don't need synchronous dispose here, so pass zero timeout and ignore the exit code. The model
                    // will be disposed eventually, which is okay.
                    createPullRequestController.dispose(0, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    // ignore interruption
                }
            }
        }
    }
}
