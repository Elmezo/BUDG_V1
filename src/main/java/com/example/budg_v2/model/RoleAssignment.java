package com.example.budg_v2.model;

import java.util.Date;

public class RoleAssignment {
    private Integer id;
    private Integer objectRoleId;
    private String usersJson; // stored JSON string
    private Date createDate;
    private Date lastUpdatedDate;
    private String createDateString;
    private String lastUpdatedDateString;

    // For view
    private String facetName; // module name
    private String roleName;  // object_role.primaryname
    private Integer moduleId; // module id

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public Integer getObjectRoleId() { return objectRoleId; }
    public void setObjectRoleId(Integer objectRoleId) { this.objectRoleId = objectRoleId; }

    public String getUsersJson() { return usersJson; }
    public void setUsersJson(String usersJson) { this.usersJson = usersJson; }

    public Date getCreateDate() { return createDate; }
    public void setCreateDate(Date createDate) { this.createDate = createDate; }

    public Date getLastUpdatedDate() { return lastUpdatedDate; }
    public void setLastUpdatedDate(Date lastUpdatedDate) { this.lastUpdatedDate = lastUpdatedDate; }

    public String getCreateDateString() { return createDateString; }
    public void setCreateDateString(String createDateString) { this.createDateString = createDateString; }

    public String getLastUpdatedDateString() { return lastUpdatedDateString; }
    public void setLastUpdatedDateString(String lastUpdatedDateString) { this.lastUpdatedDateString = lastUpdatedDateString; }

    public String getFacetName() { return facetName; }
    public void setFacetName(String facetName) { this.facetName = facetName; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public Integer getModuleId() { return moduleId; }
    public void setModuleId(Integer moduleId) { this.moduleId = moduleId; }
}