// Dashboard functionality for Admin Panel

async function showDashboardContent(contentArea) {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return (window.I18n && window.I18n.t(k) !== k) ? window.I18n.t(k) : f; };
    const dashboardTitle = T('adminPanel.dashboard.title', 'Admin Dashboard');

    // Labels
    const systemsLabel   = T('adminPanel.dashboard.systems', 'Systems');
    const datasetsLabel  = T('adminPanel.dashboard.dataSets', 'Data Sets');
    const glossariesLabel = T('adminPanel.dashboard.glossaries', 'Glossaries');
    const pendingCRLabel = T('adminPanel.dashboard.pendingCR', 'Pending Change Requests');
    const quickActionsTitle = T('adminPanel.dashboard.quickActions', 'Quick Actions');
    const goTo = (name) => T(`adminPanel.navigation.${name}`, name);

    // Render skeleton (loading state)
    contentArea.innerHTML = `
        <div class="dashboard-content admin-dashboard-kpi admin-dashboard-modern">
            <div class="dashboard-hero">
                <div>
                    <h2>${dashboardTitle}</h2>
                    <p>${T('adminPanel.dashboard.heroSub', 'Your governance control center with live operational metrics')}</p>
                </div>
                <div class="hero-badge">
                    <i class="fas fa-shield-alt"></i>
                    <span>${T('adminPanel.dashboard.liveStatus', 'Live Status')}</span>
                </div>
            </div>
            <div class="dashboard-grid dashboard-grid--kpi">
                <div class="dashboard-card dashboard-card--kpi" id="kpi-systems">
                    <div class="kpi-icon"><i class="fas fa-server"></i></div>
                    <div class="card-content">
                        <h3>${systemsLabel}</h3>
                        <p class="card-number">—</p>
                    </div>
                </div>
                <div class="dashboard-card dashboard-card--kpi" id="kpi-datasets">
                    <div class="kpi-icon"><i class="fas fa-database"></i></div>
                    <div class="card-content">
                        <h3>${datasetsLabel}</h3>
                        <p class="card-number">—</p>
                    </div>
                </div>
                <div class="dashboard-card dashboard-card--kpi" id="kpi-glossaries">
                    <div class="kpi-icon"><i class="fas fa-book"></i></div>
                    <div class="card-content">
                        <h3>${glossariesLabel}</h3>
                        <p class="card-number">—</p>
                    </div>
                </div>
                <div class="dashboard-card dashboard-card--kpi" id="kpi-pending-cr">
                    <div class="kpi-icon kpi-icon--warn"><i class="fas fa-exchange-alt"></i></div>
                    <div class="card-content">
                        <h3>${pendingCRLabel}</h3>
                        <p class="card-number">—</p>
                    </div>
                </div>
            </div>

            <div class="dashboard-section">
                <div class="section-header">
                    <h3 class="section-title">${quickActionsTitle}</h3>
                </div>
                <div class="quick-actions">
                    <button class="qa-btn" data-jump="DG Operating Model">
                        <i class="fas fa-project-diagram"></i>
                        <span>${goTo('dgOperatingModel') || 'DG Operating Model'}</span>
                    </button>
                    <button class="qa-btn" data-jump="Meta-Model Administration">
                        <i class="fas fa-sitemap"></i>
                        <span>${goTo('metaModelAdministration') || 'Meta-Model Administration'}</span>
                    </button>
                    <button class="qa-btn" data-jump="Operational Management">
                        <i class="fas fa-tasks"></i>
                        <span>${goTo('operationalManagement') || 'Operational Management'}</span>
                    </button>
                    <button class="qa-btn" data-jump="Admin Activity Logs">
                        <i class="fas fa-clipboard-list"></i>
                        <span>${goTo('adminActivityLogs') || 'Admin Activity Logs'}</span>
                    </button>
                </div>
            </div>
        </div>
    `;

    // Fetch real counts in parallel
    const baseUrl = (typeof getBaseUrl === 'function') ? getBaseUrl() : '/api';

    const fetchCount = async (url) => {
        try {
            const resp = await fetch(url, { credentials: 'include' });
            if (!resp.ok) return 0;
            const data = await resp.json();
            if (Array.isArray(data)) return data.length;
            if (data && Array.isArray(data.data)) return data.data.length;
            return 0;
        } catch (e) {
            console.warn('Dashboard KPI fetch error:', url, e);
            return 0;
        }
    };

    const fetchPendingCRCount = async () => {
        try {
            const resp = await fetch(baseUrl + '/changerequests', { credentials: 'include' });
            if (!resp.ok) return 0;
            const data = await resp.json();
            const list = Array.isArray(data) ? data : (data && Array.isArray(data.data) ? data.data : []);
            // Filter for incomplete CRs: status is NOT "Completed" or "Closed" or "Cancelled"
            const completedStatuses = ['completed', 'closed', 'cancelled', 'rejected'];
            return list.filter(cr => {
                const status = (cr.statusName || cr.status || cr.Status || '').toLowerCase().trim();
                return !completedStatuses.includes(status);
            }).length;
        } catch (e) {
            console.warn('Dashboard KPI fetch error (CR):', e);
            return 0;
        }
    };

    const [systemsCount, datasetsCount, glossariesCount, pendingCRCount] = await Promise.all([
        fetchCount(baseUrl + '/system/list'),
        fetchCount(baseUrl + '/dataset/list'),
        fetchCount(baseUrl + '/glossary/list'),
        fetchPendingCRCount()
    ]);

    // Update numbers
    const setKpi = (id, value) => {
        const el = document.querySelector(`#${id} .card-number`);
        if (el) el.textContent = Number(value).toLocaleString();
    };

    setKpi('kpi-systems', systemsCount);
    setKpi('kpi-datasets', datasetsCount);
    setKpi('kpi-glossaries', glossariesCount);
    setKpi('kpi-pending-cr', pendingCRCount);

    // Wire up quick actions and explore cards to existing navigation
    const jump = (label) => {
        const navItems = Array.from(document.querySelectorAll('.sidebar-navigation .nav-item'));
        const target = navItems.find(n => (n.textContent || '').trim().toLowerCase().includes((label || '').toLowerCase()));
        if (target) {
            target.click();
        }
    };
    document.querySelectorAll('[data-jump]').forEach(btn => {
        btn.addEventListener('click', () => jump(btn.getAttribute('data-jump') || ''));
    });
}
