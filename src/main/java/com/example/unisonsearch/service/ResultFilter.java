package com.example.unisonsearch.service;

import com.example.unisonsearch.model.ActiveTask;
import com.example.unisonsearch.model.SearchResponse;
import com.example.unisonsearch.model.SearchResult;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Filter search results based on permissions.
 */
public class ResultFilter {
    
    private final PermissionService permissionService;
    
    public ResultFilter(PermissionService permissionService) {
        this.permissionService = permissionService;
    }
    
    /**
     * Filter results based on user permissions.
     */
    public SearchResponse filterResults(SearchResponse response, Object user) {
        if (response == null || user == null) {
            return response;
        }
        
        // Filter search results
        List<SearchResult> filteredResults = response.getResults().stream()
            .filter(result -> permissionService.canViewObject(
                result.entityFacet(), 
                result.id(), 
                user))
            .collect(Collectors.toList());
        
        response.setResults(filteredResults);
        
        // Filter active tasks
        List<ActiveTask> filteredTasks = response.getActiveTasks().stream()
            .filter(task -> permissionService.canViewObject(
                task.getObjectType(),
                task.getObjectId(),
                user))
            .collect(Collectors.toList());
        
        response.setActiveTasks(filteredTasks);
        
        return response;
    }
}

