package org.jetbrains.tfsIntegration.core.tfs;

import org.jetbrains.tfsIntegration.exceptions.TfsException;

import java.util.*;

public class WorkstationHelper {

  private WorkstationHelper() {
  }

  public interface Delegate<T> {
    /**
     * @return serverPath -> result
     */
    Map<String, T> executeRequest(WorkspaceInfo workspace, List<String> serverPaths) throws TfsException;
  }

  public interface VoidDelegate {

    void executeRequest(WorkspaceInfo workspace, List<String> serverPaths) throws TfsException;
  }

  public static class ProcessResult<T> {
    public final Map<String, T> results;
    public final List<String> workspaceNotFound;

    public ProcessResult(final Map<String, T> results, final List<String> workspaceNotFound) {
      this.results = results;
      this.workspaceNotFound = workspaceNotFound;
    }
  }

  /**
   * @return workspace not found
   */
  public static List<String> processByWorkspaces(List<String> localPaths, final VoidDelegate delegate) throws TfsException {
    ProcessResult<Object> result = processByWorkspaces(localPaths, new Delegate<Object>() {
      public Map<String, Object> executeRequest(final WorkspaceInfo workspace, final List<String> serverPaths) throws TfsException {
        delegate.executeRequest(workspace, serverPaths);
        return Collections.emptyMap();
      }
    });
    return result.workspaceNotFound;
  }

  public static <T> ProcessResult<T> processByWorkspaces(List<String> localPaths, Delegate<T> delegate) throws TfsException {
    List<String> workspaceNotFoundLocalPaths = new ArrayList<String>();
    Map<WorkspaceInfo, List<String>> workspace2localPaths = new HashMap<WorkspaceInfo, List<String>>();
    for (String localPath : localPaths) {
      WorkspaceInfo workspace = Workstation.getInstance().findWorkspace(localPath);
      if (workspace != null) {
        List<String> workspaceLocalPaths = workspace2localPaths.get(workspace);
        if (workspaceLocalPaths == null) {
          workspaceLocalPaths = new ArrayList<String>();
          workspace2localPaths.put(workspace, workspaceLocalPaths);
        }
        workspaceLocalPaths.add(localPath);
      }
      else {
        workspaceNotFoundLocalPaths.add(localPath);
      }
    }

    Map<String, T> overallResults = new HashMap<String, T>(localPaths.size());
    for (WorkspaceInfo workspace : workspace2localPaths.keySet()) {
      List<String> currentLocalPaths = workspace2localPaths.get(workspace);
      List<String> currentServerPaths = new ArrayList<String>(currentLocalPaths.size());
      for (String localPath : currentLocalPaths) {
        currentServerPaths.add(workspace.findServerPathByLocalPath(localPath));
      }
      Map<String, T> serverPath2result = delegate.executeRequest(workspace, currentServerPaths);
      for (int i = 0; i < currentLocalPaths.size(); i++) {
        overallResults.put(currentLocalPaths.get(i), serverPath2result.get(currentServerPaths.get(i)));
      }
    }

    return new ProcessResult<T>(overallResults, workspaceNotFoundLocalPaths);
  }

}
