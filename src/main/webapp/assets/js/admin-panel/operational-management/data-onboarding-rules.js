// Data Onboarding Rules functionality for Operational Management

function showDataOnboardingRulesContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.dataOnboardingRules');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    contentArea.innerHTML = `
        <div class="data-onboarding-rules-content">
            <h2>${T('adminPanel.dataOnboarding.title', 'Data Onboarding Rules')}</h2>
            <p>${T('adminPanel.dataOnboarding.intro', 'Configure rules and processes for data onboarding.')}</p>
            <div class="rules-list">
                <div class="rule-item">
                    <i class="fas fa-shield-alt"></i>
                    <div class="rule-info">
                        <h3>${T('adminPanel.dataOnboarding.validationRules', 'Validation Rules')}</h3>
                        <p>${T('adminPanel.dataOnboarding.validationHint', 'Define data validation criteria')}</p>
                    </div>
                </div>
                <div class="rule-item">
                    <i class="fas fa-check-circle"></i>
                    <div class="rule-info">
                        <h3>${T('adminPanel.dataOnboarding.approvalWorkflows', 'Approval Workflows')}</h3>
                        <p>${T('adminPanel.dataOnboarding.approvalHint', 'Configure approval processes for data')}</p>
                    </div>
                </div>
            </div>
        </div>
    `;
}
