// Change Request Systems functionality for Admin Panel

function showChangeRequestSystemsContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.changeRequestSystems');

    const T = typeof adminT === 'function' ? adminT : function (k, fallback) { return fallback; };
    const escapeHtml = typeof escapeHtmlAdmin === 'function' ? escapeHtmlAdmin : function (s) {
        if (s == null) return '';
        return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    };
    const prefix = 'adminPanel.operatingModel.changeRequestSystems';

    const pageTitle = escapeHtml(T(prefix + '.pageTitle', 'Change Request Systems'));
    const pageDescription = escapeHtml(T(prefix + '.pageDescription', 'Configure and manage change request systems and workflows.'));
    const itTitle = escapeHtml(T(prefix + '.itChangeManagement.title', 'IT Change Management'));
    const itDesc = escapeHtml(T(prefix + '.itChangeManagement.description', 'System for managing IT infrastructure changes'));
    const bpTitle = escapeHtml(T(prefix + '.businessProcessChanges.title', 'Business Process Changes'));
    const bpDesc = escapeHtml(T(prefix + '.businessProcessChanges.description', 'System for managing business process modifications'));

    contentArea.innerHTML = `
        <div class="change-systems-content">
            <h2>${pageTitle}</h2>
            <p>${pageDescription}</p>
            <div class="systems-list">
                <div class="system-item">
                    <i class="fas fa-cogs"></i>
                    <div class="system-info">
                        <h3>${itTitle}</h3>
                        <p>${itDesc}</p>
                    </div>
                </div>
                <div class="system-item">
                    <i class="fas fa-cogs"></i>
                    <div class="system-info">
                        <h3>${bpTitle}</h3>
                        <p>${bpDesc}</p>
                    </div>
                </div>
            </div>
        </div>
    `;
}
