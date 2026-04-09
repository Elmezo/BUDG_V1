// Change Logo functionality for Customize & Configure

function escapeHtmlChangeLogo(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showChangeLogoContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.changeLogo');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const h = escapeHtmlChangeLogo;
    contentArea.innerHTML = `
        <div class="change-logo-content">
            <h2>${h(T('adminPanel.changeLogo.title', 'Change Logo'))}</h2>
            <p>${h(T('adminPanel.changeLogo.intro', 'Upload and manage system logos and branding.'))}</p>
            <div class="logo-options">
                <div class="logo-item">
                    <i class="fas fa-image"></i>
                    <div class="logo-info">
                        <h3>${h(T('adminPanel.changeLogo.mainLogo', 'Main Logo'))}</h3>
                        <p>${h(T('adminPanel.changeLogo.mainLogoHint', 'Upload the main system logo'))}</p>
                    </div>
                </div>
                <div class="logo-item">
                    <i class="fas fa-favicon"></i>
                    <div class="logo-info">
                        <h3>${h(T('adminPanel.changeLogo.favicon', 'Favicon'))}</h3>
                        <p>${h(T('adminPanel.changeLogo.faviconHint', 'Upload browser favicon'))}</p>
                    </div>
                </div>
            </div>
        </div>
    `;
}
