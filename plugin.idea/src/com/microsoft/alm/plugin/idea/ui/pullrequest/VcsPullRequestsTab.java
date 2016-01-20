// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.idea.ui.pullrequest;

import com.intellij.openapi.project.Project;
import com.microsoft.alm.plugin.telemetry.TfsTelemetryConstants;
import com.microsoft.alm.plugin.telemetry.TfsTelemetryHelper;
import org.jetbrains.annotations.NotNull;

import javax.swing.JComponent;
import java.awt.event.ActionListener;
import java.util.Date;

/**
 * UI class for Version Control pull requests tab
 */
public class VcsPullRequestsTab {
    private final Project project;
    private VcsPullRequestsForm form;

    public VcsPullRequestsTab(@NotNull final Project project) {
        this.project = project;
        form = new VcsPullRequestsForm();

        // Make a telemetry entry for this UI tab opening
        TfsTelemetryHelper.getInstance().sendDialogOpened(this.getClass().getName(),
                new TfsTelemetryHelper.PropertyMapBuilder()
                        .activeServerContext()
                        .pair(TfsTelemetryConstants.PLUGIN_EVENT_PROPERTY_DIALOG, "Pull Requests")
                        .build());

    }

    public JComponent getPanel() {
        return form.getPanel();
    }

    public void addActionListener(final ActionListener listener) {
        form.addActionListener(listener);
    }

    public void setLoading(final boolean loading) {
        form.setLoading(loading);
    }

    public void setConnected(final boolean connected) {
        form.setConnected(connected);
    }

    public void setLastRefreshed(final Date lastRefreshed) {
        form.setLastRefreshed(lastRefreshed);
    }

    public void setPullRequestsTree(final PullRequestsTreeModel treeModel) {
        form.setPullRequestsTree(treeModel);
    }
}
