package com.example.budg_v2.service;

import java.util.*;

/**
 * Graph structure for entity dependencies
 * Used to represent and analyze relationships between entities
 */
public class DependencyGraph {
    
    private final Map<String, Set<String>> dependencies = new HashMap<>(); // entity -> set of dependencies
    private final Map<String, Set<String>> dependents = new HashMap<>(); // entity -> set of dependents
    
    /**
     * Add a dependency: entity depends on dependency
     */
    public void addDependency(String entity, String dependency) {
        if (entity == null || dependency == null || entity.equals(dependency)) {
            return; // Skip self-dependencies
        }
        
        dependencies.computeIfAbsent(entity, k -> new HashSet<>()).add(dependency);
        dependents.computeIfAbsent(dependency, k -> new HashSet<>()).add(entity);
    }
    
    /**
     * Get dependencies for an entity
     */
    public Set<String> getDependencies(String entity) {
        return dependencies.getOrDefault(entity, Collections.emptySet());
    }
    
    /**
     * Get dependents for an entity (entities that depend on this one)
     */
    public Set<String> getDependents(String entity) {
        return dependents.getOrDefault(entity, Collections.emptySet());
    }
    
    /**
     * Get all entities in the graph
     */
    public Set<String> getAllEntities() {
        Set<String> all = new HashSet<>();
        all.addAll(dependencies.keySet());
        all.addAll(dependents.keySet());
        return all;
    }
    
    /**
     * Check if entity has dependencies
     */
    public boolean hasDependencies(String entity) {
        return dependencies.containsKey(entity) && !dependencies.get(entity).isEmpty();
    }
    
    /**
     * Get topological sort order (entities with no dependencies first)
     */
    public List<String> getTopologicalOrder() {
        List<String> result = new ArrayList<>();
        Map<String, Integer> inDegree = new HashMap<>();
        
        // Calculate in-degrees
        for (String entity : getAllEntities()) {
            inDegree.put(entity, getDependencies(entity).size());
        }
        
        // Kahn's algorithm for topological sort
        Queue<String> queue = new LinkedList<>();
        for (String entity : getAllEntities()) {
            if (inDegree.getOrDefault(entity, 0) == 0) {
                queue.offer(entity);
            }
        }
        
        while (!queue.isEmpty()) {
            String entity = queue.poll();
            result.add(entity);
            
            for (String dependent : getDependents(entity)) {
                int newInDegree = inDegree.get(dependent) - 1;
                inDegree.put(dependent, newInDegree);
                if (newInDegree == 0) {
                    queue.offer(dependent);
                }
            }
        }
        
        // If result size doesn't match all entities, there's a cycle
        if (result.size() != getAllEntities().size()) {
            // Handle cycle - add remaining entities
            for (String entity : getAllEntities()) {
                if (!result.contains(entity)) {
                    result.add(entity);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Check for circular dependencies
     */
    public boolean hasCircularDependency() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String entity : getAllEntities()) {
            if (!visited.contains(entity)) {
                if (hasCycle(entity, visited, recursionStack)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Find cycles using DFS
     */
    private boolean hasCycle(String entity, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(entity)) {
            return true; // Cycle detected
        }
        
        if (visited.contains(entity)) {
            return false; // Already processed
        }
        
        visited.add(entity);
        recursionStack.add(entity);
        
        for (String dependent : getDependents(entity)) {
            if (hasCycle(dependent, visited, recursionStack)) {
                return true;
            }
        }
        
        recursionStack.remove(entity);
        return false;
    }
    
    /**
     * Get circular dependency chains
     */
    public List<List<String>> getCircularDependencies() {
        List<List<String>> cycles = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        Map<String, String> parent = new HashMap<>();
        
        for (String entity : getAllEntities()) {
            if (!visited.contains(entity)) {
                findCycles(entity, entity, visited, recursionStack, parent, cycles);
            }
        }
        
        return cycles;
    }
    
    /**
     * Find cycles using DFS
     */
    private void findCycles(String start, String current, Set<String> visited, 
                           Set<String> recursionStack, Map<String, String> parent,
                           List<List<String>> cycles) {
        visited.add(current);
        recursionStack.add(current);
        
        for (String dependent : getDependents(current)) {
            if (!visited.contains(dependent)) {
                parent.put(dependent, current);
                findCycles(start, dependent, visited, recursionStack, parent, cycles);
            } else if (recursionStack.contains(dependent)) {
                // Cycle found
                List<String> cycle = new ArrayList<>();
                String node = current;
                while (node != null && !node.equals(dependent)) {
                    cycle.add(0, node);
                    node = parent.get(node);
                }
                cycle.add(0, dependent);
                cycle.add(dependent); // Complete the cycle
                cycles.add(cycle);
            }
        }
        
        recursionStack.remove(current);
    }
    
    /**
     * Get dependency levels (0 = no dependencies, 1 = depends on level 0, etc.)
     */
    public Map<String, Integer> getDependencyLevels() {
        Map<String, Integer> levels = new HashMap<>();
        Set<String> processed = new HashSet<>();
        
        // Entities with no dependencies are level 0
        Queue<String> queue = new LinkedList<>();
        for (String entity : getAllEntities()) {
            if (!hasDependencies(entity)) {
                levels.put(entity, 0);
                queue.offer(entity);
                processed.add(entity);
            }
        }
        
        // Process entities level by level
        int currentLevel = 1;
        while (!queue.isEmpty()) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                String entity = queue.poll();
                
                for (String dependent : getDependents(entity)) {
                    if (!processed.contains(dependent)) {
                        // Check if all dependencies are processed
                        boolean allDepsProcessed = true;
                        for (String dep : getDependencies(dependent)) {
                            if (!processed.contains(dep)) {
                                allDepsProcessed = false;
                                break;
                            }
                        }
                        
                        if (allDepsProcessed) {
                            levels.put(dependent, currentLevel);
                            queue.offer(dependent);
                            processed.add(dependent);
                        }
                    }
                }
            }
            currentLevel++;
        }
        
        // Assign max level to unprocessed entities (likely in cycles)
        for (String entity : getAllEntities()) {
            if (!processed.contains(entity)) {
                levels.put(entity, Integer.MAX_VALUE);
            }
        }
        
        return levels;
    }
}

