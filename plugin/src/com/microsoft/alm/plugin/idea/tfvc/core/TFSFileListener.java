// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.microsoft.alm.plugin.idea.tfvc.core;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.LocalFilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsVFSListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import com.microsoft.alm.plugin.external.models.PendingChange;
import com.microsoft.alm.plugin.external.utils.CommandUtils;
import com.microsoft.alm.plugin.idea.common.resources.TfPluginBundle;
import com.microsoft.alm.plugin.idea.tfvc.core.tfs.ServerStatus;
import com.microsoft.alm.plugin.idea.tfvc.core.tfs.StatusProvider;
import com.microsoft.alm.plugin.idea.tfvc.core.tfs.StatusVisitor;
import com.microsoft.alm.plugin.idea.tfvc.core.tfs.TFVCUtil;
import com.microsoft.alm.plugin.idea.tfvc.core.tfs.TfsFileUtil;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class TFSFileListener extends VcsVFSListener {
    public static final Logger logger = LoggerFactory.getLogger(TFSFileListener.class);

    public TFSFileListener(Project project, TFSVcs vcs) {
        super(project, vcs);
    }

    protected String getAddTitle() {
        return TfPluginBundle.message(TfPluginBundle.KEY_TFVC_ADD_ITEMS);
    }

    protected String getSingleFileAddTitle() {
        return TfPluginBundle.message(TfPluginBundle.KEY_TFVC_ADD_ITEM);
    }

    protected String getSingleFileAddPromptTemplate() {
        // pass {0} as a param because the current {0} in the string needs to be replaced by something or else there is an error
        // the {0} will be replaced higher up by the file name that we don't have here
        return TfPluginBundle.message(TfPluginBundle.KEY_TFVC_ADD_PROMPT, "{0}");
    }

    @Override
    protected void executeAdd(List<VirtualFile> addedFiles, Map<VirtualFile, VirtualFile> copyFromMap) {
        logger.info("executeAdd executing...");
        Application application = ApplicationManager.getApplication();

        final List<String> filePaths = TfsFileUtil.getFilePathStrings(addedFiles);
        final List<PendingChange> pendingChanges = new ArrayList<PendingChange>();

        application.invokeAndWait(() -> {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
                ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
                pendingChanges.addAll(
                        CommandUtils.getStatusForFiles(
                                myProject,
                                TFSVcs.getInstance(myProject).getServerContext(true),
                                filePaths));
            }, TfPluginBundle.message(TfPluginBundle.KEY_TFVC_ADD_SCHEDULING), false, myProject);
        });

        for (final PendingChange pendingChange : pendingChanges) {
            StatusProvider.visitByStatus(new StatusVisitor() {
                public void unversioned(final @NotNull FilePath localPath,
                                        final boolean localItemExists,
                                        final @NotNull ServerStatus serverStatus) {
                    // ignore
                }

                public void checkedOutForEdit(final @NotNull FilePath localPath,
                                              final boolean localItemExists,
                                              final @NotNull ServerStatus serverStatus) {
                    // TODO (JetBrains): add local conflict
                }

                @Override
                public void locked(@NotNull FilePath localPath, boolean localItemExists, @NotNull ServerStatus serverStatus) {
                    // ignore
                }

                public void scheduledForAddition(final @NotNull FilePath localPath,
                                                 final boolean localItemExists,
                                                 final @NotNull ServerStatus serverStatus) {
                    addedFiles.remove(localPath.getVirtualFile());
                }

                public void scheduledForDeletion(final @NotNull FilePath localPath,
                                                 final boolean localItemExists,
                                                 final @NotNull ServerStatus serverStatus) {
                    // TODO (JetBrains): add local conflict
                }

                public void renamed(final @NotNull FilePath localPath, final boolean localItemExists, final @NotNull ServerStatus serverStatus) {
                    // TODO (JetBrains): add local conflict
                }

                public void renamedCheckedOut(final @NotNull FilePath localPath,
                                              final boolean localItemExists,
                                              final @NotNull ServerStatus serverStatus) {
                    // TODO (JetBrains): add local conflict
                }

                public void undeleted(final @NotNull FilePath localPath,
                                      final boolean localItemExists,
                                      final @NotNull ServerStatus serverStatus) {
                    // TODO (JetBrains): add local conflict
                }

            }, pendingChange);
        }

        removeInvalidTFVCAddedFiles(addedFiles);

        if (!addedFiles.isEmpty()) {
            application.invokeAndWait(() -> super.executeAdd(addedFiles, copyFromMap));
        }
    }

    protected void performDeletion(final List<FilePath> filesToDelete) {
        // nothing to do here since we already have taken care of the deleted file
    }

    @Override
    protected void executeDelete() {
        // overriding so we don't do any special logic here for delete since it's already been taken care of
    }

    @Override
    protected void performAdding(final Collection<VirtualFile> addedFiles, final Map<VirtualFile, VirtualFile> copyFromMap) {
        final List<VcsException> errors = new ArrayList<VcsException>();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
            public void run() {
                ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
                errors.addAll(TFSVcs.getInstance(myProject).getCheckinEnvironment().scheduleUnversionedFilesForAddition(new ArrayList(addedFiles)));
            }
        }, TfPluginBundle.message(TfPluginBundle.KEY_TFVC_ADD_PROGRESS), false, myProject);

        if (!errors.isEmpty()) {
            AbstractVcsHelper.getInstance(myProject).showErrors(errors, TFSVcs.TFVC_NAME);
        }
    }

    protected String getDeleteTitle() {
        return StringUtils.EMPTY; // never called
    }

    protected String getSingleFileDeleteTitle() {
        return null;
    }

    protected String getSingleFileDeletePromptTemplate() {
        return null;
    }

    protected void performMoveRename(final List<MovedFileInfo> movedFiles) {
        final List<VcsException> errors = new ArrayList<VcsException>();

        // Refreshes the files so that the changes show up in the Local Changes tab
        final Collection<FilePath> invalidate = new ArrayList<FilePath>(movedFiles.size());
        for (final MovedFileInfo info : movedFiles) {
            invalidate.add(VcsUtil.getFilePath(info.myOldPath));
        }
        TfsFileUtil.markDirtyRecursively(myProject, invalidate);

        if (!errors.isEmpty()) {
            AbstractVcsHelper.getInstance(myProject).showErrors(errors, TFSVcs.TFVC_NAME);
        }
    }

    protected boolean isDirectoryVersioningSupported() {
        return true;
    }

    private void removeInvalidTFVCAddedFiles(List<VirtualFile> addedFiles) {
        Map<VirtualFile, FilePath> addedFilePaths = addedFiles.stream()
                .collect(Collectors.toMap(vf -> vf, vf -> new LocalFilePath(vf.getPath(), vf.isDirectory())));
        Set<FilePath> invalidPaths = TFVCUtil.collectInvalidTFVCPaths((TFSVcs) myVcs, addedFilePaths.values().stream())
                .collect(Collectors.toSet());

        addedFiles.removeIf(file -> invalidPaths.contains(addedFilePaths.get(file)));
    }
}
