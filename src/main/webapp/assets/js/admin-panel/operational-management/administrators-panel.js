// Administrator's Panel functionality for Operational Management

function showAdministratorsPanelContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.administratorsPanel');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    
    contentArea.innerHTML = `
        <div class="administrators-panel-content">
            <h2>${T('adminPanel.administratorsPanelPage.title', "Administrator's Panel")}</h2>
            <p>${T('adminPanel.administratorsPanelPage.intro', 'Manage administrator settings and configurations.')}</p>
            <div class="admin-panel-grid">
                <div class="admin-panel-card">
                    <i class="fas fa-user-shield"></i>
                    <div class="card-info">
                        <h3>${T('adminPanel.administratorsPanelPage.adminSettings', 'Administrator Settings')}</h3>
                        <p>${T('adminPanel.administratorsPanelPage.adminSettingsHint', 'Configure administrator permissions and access levels')}</p>
                    </div>
                </div>
                <div class="admin-panel-card">
                    <i class="fas fa-cogs"></i>
                    <div class="card-info">
                        <h3>${T('adminPanel.administratorsPanelPage.systemConfiguration', 'System Configuration')}</h3>
                        <p>${T('adminPanel.administratorsPanelPage.systemConfigurationHint', 'Manage system-wide administrator configurations')}</p>
                    </div>
                </div>
                <div class="admin-panel-card">
                    <i class="fas fa-key"></i>
                    <div class="card-info">
                        <h3>${T('adminPanel.administratorsPanelPage.accessControl', 'Access Control')}</h3>
                        <p>${T('adminPanel.administratorsPanelPage.accessControlHint', 'Control administrator access and privileges')}</p>
                    </div>
                </div>
                <div class="admin-panel-card" onclick="showLdapSyncPanel(document.querySelector('.content-area'))" style="cursor: pointer;">
                    <i class="fas fa-sync-alt"></i>
                    <div class="card-info">
                        <h3>${T('adminPanel.administratorsPanelPage.ldapSyncCardTitle', 'Synchronize With LDAP Server')}</h3>
                        <p>${T('adminPanel.administratorsPanelPage.ldapSyncCardHint', 'Synchronize LDAP users with BUDG database')}</p>
                    </div>
                </div>
            </div>
        </div>
    `;
}
