// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.plugin.external.commands;

import com.google.common.collect.ImmutableList;
import com.microsoft.alm.plugin.external.ToolRunner;
import com.microsoft.alm.plugin.idea.tfvc.core.TfvcDeleteResult;
import com.microsoft.alm.plugin.idea.tfvc.core.tfs.TfsFileUtil;
import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

public class DeleteCommandTest extends AbstractCommandTest {
    private List<String> filePaths = ImmutableList.of("/path/to/file.txt","/path/to/file2.txt", "/path/to/another/file2.txt");

    @Test
    public void testConstructor_nullContext() {
        final DeleteCommand cmd = new DeleteCommand(null, filePaths, "workspaceName", true);
    }

    @Test
    public void testConstructor_withContext() {
        final DeleteCommand cmd = new DeleteCommand(context, filePaths, "workspaceName", true);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructor_nullArgs() {
        final DeleteCommand cmd = new DeleteCommand(null, null, null, false);
    }

    @Test
    public void testGetArgumentBuilder_withContext() {
        final DeleteCommand cmd = new DeleteCommand(context, filePaths, "workspaceName", false);
        final ToolRunner.ArgumentBuilder builder = cmd.getArgumentBuilder();
        Assert.assertEquals("delete -noprompt -collection:http://server:8080/tfs/defaultcollection ******** /path/to/file.txt /path/to/file2.txt /path/to/another/file2.txt -workspace:workspaceName", builder.toString());
    }

    @Test
    public void testGetArgumentBuilder_nullContext() {
        final DeleteCommand cmd = new DeleteCommand(null, filePaths, "workspaceName", false);
        final ToolRunner.ArgumentBuilder builder = cmd.getArgumentBuilder();
        Assert.assertEquals("delete -noprompt /path/to/file.txt /path/to/file2.txt /path/to/another/file2.txt -workspace:workspaceName", builder.toString());
    }

    @Test
    public void testGetArgumentBuilder_recursive() {
        final DeleteCommand cmd = new DeleteCommand(context, filePaths, "workspaceName", true);
        final ToolRunner.ArgumentBuilder builder = cmd.getArgumentBuilder();
        Assert.assertEquals("delete -noprompt -collection:http://server:8080/tfs/defaultcollection ******** /path/to/file.txt /path/to/file2.txt /path/to/another/file2.txt -recursive -workspace:workspaceName", builder.toString());
    }

    @Test
    public void testParseOutput_noErrors() {
        final DeleteCommand cmd = new DeleteCommand(context, filePaths, "workspaceName", true);
        TfvcDeleteResult result = cmd.parseOutput("/path/to:\nfile.txt\nfile2.txt\n\n/path/to/another:\nfile2.txt", "");
        List<Path> output = result.getDeletedPaths();
        Assert.assertEquals(3, output.size());
        Assert.assertEquals(Paths.get("/path/to", "file.txt"), output.get(0));
        Assert.assertEquals(Paths.get("/path/to", "file2.txt"), output.get(1));
        Assert.assertEquals(Paths.get("/path/to/another", "file2.txt"), output.get(2));
    }

    @Test
    public void testParseOutput_FilesNotFound() {
        final DeleteCommand cmd = new DeleteCommand(context, filePaths, "workspaceName", true);
        TfvcDeleteResult result = cmd.parseOutput("", "No matching items found in /path/file.txt in your workspace, or you do not have permission to access them.\r\nNo arguments matched any files to delete.\r\n");

        Assert.assertEquals(Collections.emptyList(), result.getDeletedPaths());
        Assert.assertEquals(
                Collections.singletonList(TfsFileUtil.createLocalPath("/path/file.txt")),
                result.getNotFoundPaths());
        Assert.assertEquals(Collections.emptyList(), result.getErrorMessages());
    }
}
