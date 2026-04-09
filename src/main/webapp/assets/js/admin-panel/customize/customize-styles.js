// Customize Styles functionality for Customize & Configure

function escapeHtmlCustomizeStyles(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showCustomizeStylesContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.customizeStyles');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const h = escapeHtmlCustomizeStyles;
    contentArea.innerHTML = `
        <div class="customize-styles-content">
            <h2>${h(T('adminPanel.customizeStyles.title', 'Customize Styles'))}</h2>
            <p>${h(T('adminPanel.customizeStyles.intro', 'Customize the visual appearance and styling of the system.'))}</p>
            <div class="style-options">
                <div class="style-item">
                    <i class="fas fa-palette"></i>
                    <div class="style-info">
                        <h3>${h(T('adminPanel.customizeStyles.colorThemes', 'Color Themes'))}</h3>
                        <p>${h(T('adminPanel.customizeStyles.colorThemesHint', 'Configure color schemes and themes'))}</p>
                    </div>
                </div>
                <div class="style-item">
                    <i class="fas fa-font"></i>
                    <div class="style-info">
                        <h3>${h(T('adminPanel.customizeStyles.typography', 'Typography'))}</h3>
                        <p>${h(T('adminPanel.customizeStyles.typographyHint', 'Customize fonts and text styling'))}</p>
                    </div>
                </div>
            </div>
        </div>
    `;
}
