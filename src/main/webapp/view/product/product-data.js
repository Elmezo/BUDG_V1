// Product Data View JavaScript - Read-only display for Data Sets linked to this product
(function() {
    let currentProductId = null;
    let datasetsData = [];

    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function createEntityLink(entityType, id, name) {
        if (!id || !name || name === 'N/A') return escapeHtml(name || 'N/A');
        let url;
        switch (entityType) {
            case 'dataset':
                url = `/view/dataset/${encodeURIComponent(id)}`;
                break;
            default:
                return escapeHtml(name);
        }
        return `<a href="${url}" class="relationship-link" title="View ${escapeHtml(name)}">${escapeHtml(name)}</a>`;
    }

    function loadProductData(productId) {
        currentProductId = productId;
        console.log('Loading Product Data for product:', productId);
        const container = document.getElementById('productDataContainer');
        if (container) {
            container.innerHTML = '<div class="view-section" style="grid-column:1/-1;"><div class="section-title">PRODUCT DATA</div><p style="color:var(--text-muted,#6b7280);">Loading...</p></div>';
        }
        loadDataViewData();
    }

    async function loadDataViewData() {
        try {
            await loadDatasetsData();
            renderDataView();
        } catch (error) {
            console.error('Error loading Product Data view:', error);
            const container = document.getElementById('productDataContainer');
            if (container) {
                container.innerHTML = '<div class="view-section" style="grid-column:1/-1;color:var(--danger,#b91c1c);">Error loading data: ' + (error.message || 'Unknown error') + '</div>';
            }
        }
    }

    async function loadDatasetsData() {
        try {
            const response = await fetch(`/api/dataset-impact/products/${currentProductId}/datasets`, {
                method: 'GET',
                credentials: 'include',
                headers: { 'Content-Type': 'application/json' }
            });
            if (!response.ok) {
                datasetsData = [];
                return;
            }
            const data = await response.json();
            datasetsData = Array.isArray(data) ? data : (data.data && Array.isArray(data.data) ? data.data : []);
        } catch (error) {
            console.error('Error loading product datasets:', error);
            datasetsData = [];
        }
    }

    function renderDataView() {
        const container = document.getElementById('productDataContainer');
        if (!container) return;

        const html = `
            <div class="view-section" style="grid-column:1/-1;">
                <div class="section-title">DATA SETS</div>
                <p class="section-subtitle" style="margin-bottom:1rem;">Data sets linked to this product (from Impact).</p>
                <div class="data-table-wrapper">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>Relationship Type</th>
                                <th>Data Set</th>
                                <th>Owner</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${renderDatasetsTableBody()}
                        </tbody>
                    </table>
                </div>
                <div class="hierarchy-footer">
                    ${datasetsData.length} record${datasetsData.length !== 1 ? 's' : ''}
                </div>
            </div>
        `;
        container.innerHTML = html;
    }

    function renderDatasetsTableBody() {
        if (!datasetsData.length) {
            return '<tr><td colspan="3" style="text-align:center;padding:2rem;color:var(--text-muted,#6b7280);">No data sets found for this product</td></tr>';
        }
        return datasetsData.map(function(rel) {
            const relationType = escapeHtml(rel.relationTypeReverseName || rel.relationTypeName || '-');
            const datasetName = rel.datasetName || 'N/A';
            const datasetId = rel.datasetId;
            const datasetLink = createEntityLink('dataset', datasetId, datasetName);
            const ownerName = escapeHtml(rel.datasetOwnerName || 'No owner');
            return `<tr><td>${relationType}</td><td>${datasetLink}</td><td>${ownerName}</td></tr>`;
        }).join('');
    }

    window.loadProductData = loadProductData;
})();
