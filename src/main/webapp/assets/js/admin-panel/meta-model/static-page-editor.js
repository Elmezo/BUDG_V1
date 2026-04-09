// Static Page Editor functionality for Meta-Model Administration

function spT(key, fallback) {
    return typeof adminT === 'function' ? adminT(key, fallback) : fallback;
}

function escapeHtmlSp(text) {
    if (text == null) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function showStaticPageEditorContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.staticPageEditor');

    // CSS Styles
    const styles = `
    <style>
        .static-page-container {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background-color: #f8f9fa;
            min-height: 100%;
        }
        .static-page-header {
            background-color: #2c3e50;
            color: white;
            padding: 15px 25px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .static-page-header .header-left {
            display: flex;
            align-items: center;
        }
        .static-page-header .back-btn {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            border: 1px solid rgba(255,255,255,0.4);
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            margin-right: 15px;
            cursor: pointer;
            text-decoration: none;
            transition: all 0.2s;
            font-size: 14px;
        }
        .static-page-header .back-btn:hover {
            background: rgba(255,255,255,0.1);
            border-color: white;
        }
        .static-page-header h2 {
            margin: 0;
            font-size: 16px;
            font-weight: 600;
            line-height: 1.2;
            color: white;
        }
        .static-page-header .subtitle {
            margin: 0;
            font-size: 12px;
            opacity: 0.8;
            font-weight: 400;
            color: #bdc3c7;
        }
        .static-page-header .btn-action {
            background-color: #009688;
            color: white;
            border: none;
            padding: 6px 15px;
            font-size: 13px;
            border-radius: 3px;
            display: flex;
            align-items: center;
            gap: 8px;
            font-weight: 500;
            box-shadow: 0 1px 3px rgba(0,0,0,0.2);
        }
        .static-page-header .btn-action:hover {
            background-color: #00796b;
            text-decoration: none;
            color: white;
        }
        .static-page-header .btn-secondary-custom {
            background-color: #546e7a;
            color: white;
            border: none;
            padding: 6px 15px;
            font-size: 13px;
            border-radius: 3px;
            font-weight: 500;
            box-shadow: 0 1px 3px rgba(0,0,0,0.2);
        }
        .static-page-header .btn-secondary-custom:hover {
            background-color: #455a64;
            text-decoration: none;
            color: white;
        }
        .static-page-content-wrapper {
            padding: 20px;
        }
        .static-page-card {
            background: white;
            border: 1px solid #e0e0e0;
            border-radius: 4px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
            overflow: hidden;
        }
        .static-page-card-header {
            background-color: #e0f7fa;
            padding: 12px 20px;
            border-bottom: 1px solid #b2ebf2;
            color: #006064;
            font-size: 12px;
            font-weight: 700;
            text-transform: uppercase;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }
        .static-page-table {
            width: 100%;
            margin-bottom: 0;
            border-collapse: collapse;
        }
        .static-page-table th {
            border-top: none;
            border-bottom: 1px solid #e0e0e0;
            font-size: 12px;
            font-weight: 700;
            color: #455a64;
            padding: 12px 20px;
            text-align: left;
            background-color: #fff;
        }
        .static-page-table td {
            padding: 12px 20px;
            vertical-align: middle;
            font-size: 13px;
            color: #37474f;
            border-top: 1px solid #f0f0f0;
        }
        .static-page-table tr:hover {
            background-color: #f5f5f5;
        }
        .static-page-table tr.selected-row {
            background-color: #e0f7fa;
        }
        .page-icon {
            color: #90a4ae;
            margin-right: 10px;
            font-size: 14px;
        }
        .card-footer-custom {
            padding: 10px 20px;
            text-align: right;
            font-size: 12px;
            color: #78909c;
            border-top: 1px solid #e0e0e0;
            background-color: #fff;
        }
        
        /* Edit Form Styles */
        .edit-tabs-container {
            background: white;
            padding: 0 20px;
            border-bottom: 1px solid #e0e0e0;
        }
        .nav-tabs-custom {
            border-bottom: none;
            margin-bottom: 0;
        }
        .nav-tabs-custom .nav-item {
            margin-bottom: -1px;
        }
        .nav-tabs-custom .nav-link {
            border: none;
            border-bottom: 3px solid transparent;
            color: #546e7a;
            padding: 15px 5px;
            margin-right: 20px;
            font-weight: 600;
            font-size: 13px;
            text-transform: uppercase;
            background: transparent;
        }
        .nav-tabs-custom .nav-link:hover {
            color: #263238;
        }
        .nav-tabs-custom .nav-link.active {
            color: #263238;
            border-bottom: 3px solid #009688;
            background: transparent;
        }
        
        .form-definition-header {
            background-color: #e0f7fa;
            padding: 10px 20px;
            color: #006064;
            font-size: 12px;
            font-weight: 700;
            text-transform: uppercase;
            margin-bottom: 25px;
            border-top: 1px solid #b2ebf2;
            border-bottom: 1px solid #b2ebf2;
        }
        .form-group-custom {
            margin-bottom: 20px;
        }
        .form-group-custom label {
            font-weight: 500;
            font-size: 13px;
            color: #455a64;
            padding-top: 7px;
        }
        .form-control-custom {
            border: 1px solid #cfd8dc;
            border-radius: 4px;
            font-size: 13px;
            padding: 8px 12px;
            color: #37474f;
            width: 100%;
            transition: border-color 0.15s ease-in-out, box-shadow 0.15s ease-in-out;
        }
        .form-control-custom:focus {
            border-color: #009688;
            box-shadow: 0 0 0 0.2rem rgba(0, 150, 136, 0.25);
            outline: 0;
        }
        .optional-fields-header {
            margin-top: 30px;
            margin-bottom: 20px;
            font-size: 12px;
            font-weight: 700;
            color: #78909c;
            text-transform: uppercase;
            padding-left: 0;
            border-bottom: 1px solid #eee;
            padding-bottom: 10px;
        }
        
        /* Scrollbar for table if needed */
        .table-responsive-custom {
            max-height: calc(100vh - 250px);
            overflow-y: auto;
        }
        .table-responsive-custom::-webkit-scrollbar {
            width: 6px;
        }
        .table-responsive-custom::-webkit-scrollbar-thumb {
            background-color: #cfd8dc;
            border-radius: 3px;
        }
    </style>
    `;

    const T = spT;
    const loading = escapeHtmlSp(T('adminPanel.common.loading', 'Loading...'));
    contentArea.innerHTML = styles + `<div class="loading-spinner">${loading}</div>`;

    // Fetch pages
    fetch('/admin/api/static-pages')
        .then(response => response.json())
        .then(data => {
            // Check if the response is an array
            if (Array.isArray(data)) {
                renderStaticPageList(contentArea, data, styles);
            } else if (data.error) {
                // API returned an error object
                console.error('API error:', data.error);
                const errMsg = escapeHtmlSp(data.error);
                contentArea.innerHTML = styles + `<div class="alert alert-danger m-3">${escapeHtmlSp(T('adminPanel.staticPageEditor.errorPrefix', 'Error:'))} ${errMsg}</div>`;
            } else {
                // Unexpected response format
                console.error('Unexpected response format:', data);
                contentArea.innerHTML = styles + '<div class="alert alert-danger m-3">' + escapeHtmlSp(T('adminPanel.staticPageEditor.unexpectedResponse', 'Unexpected response...')) + '</div>';
            }
        })
        .catch(error => {
            console.error('Error fetching static pages:', error);
            contentArea.innerHTML = styles + '<div class="alert alert-danger m-3">' + escapeHtmlSp(T('adminPanel.staticPageEditor.errorLoadingPages', 'Error loading pages...')) + '</div>';
        });
}

function renderStaticPageList(contentArea, pages, styles) {
    const T = spT;
    const h = escapeHtmlSp;
    const yes = h(T('adminPanel.common.yes', 'Yes'));
    let tableRows = pages.map(page => `
        <tr>
            <td>
                <a href="#" class="text-dark font-weight-normal" style="text-decoration: none;" onclick="openStaticPageEditor(${page.id}); return false;">
                    <i class="far fa-file-alt page-icon"></i> ${h(page.title)}
                </a>
            </td>
            <td>${h(page.link)}</td>
            <td>${h(page.context || '')}</td>
            <td>${page.is_homepage ? yes : ''}</td>
        </tr>
    `).join('');

    const recordsLabel = h(T('adminPanel.staticPageEditor.recordsCount', '{count} records').replace('{count}', String(pages.length)));
    contentArea.innerHTML = styles + `
        <div class="static-page-container">
            <div class="static-page-header">
                <div class="header-left">
                    <a href="#" class="back-btn" onclick="window.history.back(); return false;">
                        <i class="fas fa-arrow-left"></i>
                    </a>
                    <div>
                        <h2>${h(T('adminPanel.staticPageEditor.pageEditorTitle', 'Static Page Editor'))}</h2>
                        <p class="subtitle">${h(T('adminPanel.staticPageEditor.subtitleBudg', 'BUDG Management'))}</p>
                    </div>
                </div>
                <div class="header-right">
                    <button class="btn-action">
                        <i class="fas fa-bars"></i> ${h(T('adminPanel.staticPageEditor.actions', 'Actions'))} <i class="fas fa-chevron-down ml-1"></i>
                    </button>
                </div>
            </div>

            <div class="static-page-content-wrapper">
                <div class="static-page-card">
                    <div class="static-page-card-header">
                        <span>${T('adminPanel.staticPageEditor.staticPagesHeader', 'STATIC PAGES')}</span>
                        <i class="fas fa-cog text-muted" style="cursor: pointer;"></i>
                    </div>
                    <div class="table-responsive-custom">
                        <table class="static-page-table">
                            <thead>
                                <tr>
                                    <th style="width: 30%;">${h(T('adminPanel.staticPageEditor.tableTitle', 'Title'))}</th>
                                    <th style="width: 30%;">${h(T('adminPanel.staticPageEditor.tableLink', 'Link'))}</th>
                                    <th style="width: 20%;">${h(T('adminPanel.staticPageEditor.tableContext', 'Context'))}</th>
                                    <th style="width: 20%;">${h(T('adminPanel.staticPageEditor.tableHomepage', 'Homepage'))}</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${tableRows}
                            </tbody>
                        </table>
                    </div>
                    <div class="card-footer-custom">
                        ${recordsLabel}
                    </div>
                </div>
            </div>
        </div>
    `;
}

function openStaticPageEditor(pageId) {
    // Fetch specific page details
    fetch(`/admin/api/static-pages/${pageId}`)
        .then(response => response.json())
        .then(page => {
            renderEditForm(page);
        })
        .catch(error => {
            console.error('Error fetching page details:', error);
            if (window.showAdminNotification) window.showAdminNotification(spT('adminPanel.staticPageEditor.errorLoadingDetails', 'Error loading page details.'), 'error');
        });
}

function renderEditForm(page) {
    const T = spT;
    const h = escapeHtmlSp;
    const contentArea = document.querySelector('.content-area'); // Assuming this is the container

    // Re-inject styles if needed, though they should persist if we don't clear everything or if we re-add them
    // For safety, let's grab the styles from the previous render or just define them again/assume they are there.
    // Since we are replacing innerHTML, we need to include styles again.

    const styles = `
    <style>
        .static-page-container {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            background-color: #f8f9fa;
            min-height: 100%;
        }
        .static-page-header {
            background-color: #2c3e50;
            color: white;
            padding: 15px 25px;
            display: flex;
            justify-content: space-between;
            align-items: center;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .static-page-header .header-left {
            display: flex;
            align-items: center;
        }
        .static-page-header .back-btn {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            border: 1px solid rgba(255,255,255,0.4);
            display: flex;
            align-items: center;
            justify-content: center;
            color: white;
            margin-right: 15px;
            cursor: pointer;
            text-decoration: none;
            transition: all 0.2s;
            font-size: 14px;
        }
        .static-page-header .back-btn:hover {
            background: rgba(255,255,255,0.1);
            border-color: white;
        }
        .static-page-header h2 {
            margin: 0;
            font-size: 16px;
            font-weight: 600;
            line-height: 1.2;
            color: white;
        }
        .static-page-header .subtitle {
            margin: 0;
            font-size: 12px;
            opacity: 0.8;
            font-weight: 400;
            color: #bdc3c7;
        }
        .static-page-header .btn-action {
            background-color: #009688;
            color: white;
            border: none;
            padding: 6px 15px;
            font-size: 13px;
            border-radius: 3px;
            display: flex;
            align-items: center;
            gap: 8px;
            font-weight: 500;
            box-shadow: 0 1px 3px rgba(0,0,0,0.2);
            cursor: pointer;
        }
        .static-page-header .btn-action:hover {
            background-color: #00796b;
            text-decoration: none;
            color: white;
        }
        .static-page-header .btn-secondary-custom {
            background-color: #546e7a;
            color: white;
            border: none;
            padding: 6px 15px;
            font-size: 13px;
            border-radius: 3px;
            font-weight: 500;
            box-shadow: 0 1px 3px rgba(0,0,0,0.2);
            cursor: pointer;
        }
        .static-page-header .btn-secondary-custom:hover {
            background-color: #455a64;
            text-decoration: none;
            color: white;
        }
        .static-page-content-wrapper {
            padding: 20px;
        }
        .static-page-card {
            background: white;
            border: 1px solid #e0e0e0;
            border-radius: 4px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.05);
            overflow: hidden;
        }
        .edit-tabs-container {
            background: white;
            padding: 0 20px;
            border-bottom: 1px solid #e0e0e0;
        }
        .nav-tabs-custom {
            border-bottom: none;
            margin-bottom: 0;
            padding-left: 0;
            list-style: none;
            display: flex;
        }
        .nav-tabs-custom .nav-item {
            margin-bottom: -1px;
        }
        .nav-tabs-custom .nav-link {
            border: none;
            border-bottom: 3px solid transparent;
            color: #546e7a;
            padding: 15px 5px;
            margin-right: 20px;
            font-weight: 600;
            font-size: 13px;
            text-transform: uppercase;
            background: transparent;
            display: block;
            text-decoration: none;
        }
        .nav-tabs-custom .nav-link:hover {
            color: #263238;
        }
        .nav-tabs-custom .nav-link.active {
            color: #263238;
            border-bottom: 3px solid #009688;
            background: transparent;
        }
        
        .form-definition-header {
            background-color: #e0f7fa;
            padding: 10px 20px;
            color: #006064;
            font-size: 12px;
            font-weight: 700;
            text-transform: uppercase;
            margin-bottom: 25px;
            border-top: 1px solid #b2ebf2;
            border-bottom: 1px solid #b2ebf2;
        }
        .form-group-custom {
            margin-bottom: 20px;
        }
        .form-group-custom label {
            font-weight: 500;
            font-size: 13px;
            color: #455a64;
            padding-top: 7px;
        }
        .form-control-custom {
            border: 1px solid #cfd8dc;
            border-radius: 4px;
            font-size: 13px;
            padding: 8px 12px;
            color: #37474f;
            width: 100%;
            transition: border-color 0.15s ease-in-out, box-shadow 0.15s ease-in-out;
            display: block;
        }
        .form-control-custom:focus {
            border-color: #009688;
            box-shadow: 0 0 0 0.2rem rgba(0, 150, 136, 0.25);
            outline: 0;
        }
        .optional-fields-header {
            margin-top: 30px;
            margin-bottom: 20px;
            font-size: 12px;
            font-weight: 700;
            color: #78909c;
            text-transform: uppercase;
            padding-left: 0;
            border-bottom: 1px solid #eee;
            padding-bottom: 10px;
        }
    </style>
    `;

    contentArea.innerHTML = styles + `
        <div class="static-page-container">
            <div class="static-page-header">
                <div class="header-left">
                    <button class="back-btn" onclick="showStaticPageEditorContent(document.querySelector('.content-area'))">
                        <i class="fas fa-arrow-left"></i>
                    </button>
                    <div>
                        <h2>${h(page.title)}</h2>
                        <p class="subtitle">${h(T('adminPanel.staticPageEditor.staticPage', 'Static Page'))}</p>
                    </div>
                </div>
                <div class="header-right" style="display: flex; gap: 10px;">
                    <button class="btn-action" onclick="saveStaticPage(${page.id})">${h(T('adminPanel.staticPageEditor.save', 'Save'))}</button>
                    <button class="btn-action" onclick="saveStaticPage(${page.id}, true)">${h(T('adminPanel.staticPageEditor.saveAndClose', 'Save & Close'))}</button>
                    <button class="btn-secondary-custom" onclick="showStaticPageEditorContent(document.querySelector('.content-area'))">${h(T('adminPanel.staticPageEditor.close', 'Close'))} <i class="fas fa-chevron-down ml-1"></i></button>
                </div>
            </div>

            <div class="static-page-content-wrapper">
                <div class="static-page-card">
                    <div class="edit-tabs-container">
                        <ul class="nav-tabs-custom">
                            <li class="nav-item">
                                <a class="nav-link active" href="#">${h(T('adminPanel.staticPageEditor.pagesTab', 'PAGES'))}</a>
                            </li>
                        </ul>
                    </div>
                    
                    <div class="form-definition-header">
                        ${h(T('adminPanel.staticPageEditor.definition', 'DEFINITION'))}
                    </div>
                    
                    <div class="card-body" style="padding: 0 20px 20px;">
                        <form id="staticPageForm">
                            <div class="form-group-custom row">
                                <label class="col-sm-2 col-form-label">${h(T('adminPanel.staticPageEditor.labelTitle', 'Title'))} <span class="text-danger">*</span></label>
                                <div class="col-sm-10">
                                    <input type="text" class="form-control-custom" id="pageTitle" value="${h(page.title)}">
                                </div>
                            </div>
                            
                            <div class="form-group-custom row">
                                <label class="col-sm-2 col-form-label">${h(T('adminPanel.staticPageEditor.labelLink', 'Link'))} <span class="text-danger">*</span></label>
                                <div class="col-sm-10">
                                    <input type="text" class="form-control-custom" id="pageLink" value="${h(page.link)}">
                                </div>
                            </div>
                            
                            <div class="form-group-custom row">
                                <label class="col-sm-2 col-form-label">${h(T('adminPanel.staticPageEditor.labelContext', 'Context'))}</label>
                                <div class="col-sm-10">
                                    <input type="text" class="form-control-custom" id="pageContext" value="${h(page.context || '')}" placeholder="${h(T('adminPanel.staticPageEditor.placeholderContext', 'Enter Context'))}">
                                </div>
                            </div>
                            
                            <div class="form-group-custom row">
                                <label class="col-sm-2 col-form-label">${h(T('adminPanel.staticPageEditor.labelContent', 'Content'))} <span class="text-danger">*</span></label>
                                <div class="col-sm-10">
                                    <textarea class="form-control-custom" id="pageContent" rows="10" style="font-family: monospace;">${h(page.content || '')}</textarea>
                                </div>
                            </div>
                            
                            <div class="optional-fields-header">
                                ${h(T('adminPanel.staticPageEditor.optionalFieldsUpper', 'OPTIONAL FIELDS'))}
                            </div>
                            
                            <div class="form-group-custom row">
                                <label class="col-sm-2 col-form-label">${h(T('adminPanel.staticPageEditor.tableHomepage', 'Homepage'))}</label>
                                <div class="col-sm-10">
                                    <select class="form-control-custom" id="pageHomepage">
                                        <option value="false" ${!page.is_homepage ? 'selected' : ''}>${h(T('adminPanel.common.no', 'No'))}</option>
                                        <option value="true" ${page.is_homepage ? 'selected' : ''}>${h(T('adminPanel.common.yes', 'Yes'))}</option>
                                    </select>
                                </div>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    `;
}

function saveStaticPage(pageId, closeAfterSave = false) {
    const data = {
        title: document.getElementById('pageTitle').value,
        link: document.getElementById('pageLink').value,
        context: document.getElementById('pageContext').value,
        content: document.getElementById('pageContent').value,
        is_homepage: document.getElementById('pageHomepage').value === 'true'
    };

    fetch(`/admin/api/static-pages/${pageId}`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(data)
    })
        .then(response => {
            if (response.ok) {
                if (typeof showNotification === 'function') {
                    showNotification(spT('adminPanel.staticPageEditor.saveSuccess', 'Page saved successfully.'), 'success');
                }
                if (closeAfterSave) {
                    showStaticPageEditorContent(document.querySelector('.content-area'));
                }
            } else {
                response.json().then(err => {
                    const unk = spT('adminPanel.staticPageEditor.unknownError', 'Unknown error');
                    const template = spT('adminPanel.staticPageEditor.errorSavingPageWithMsg', 'Error saving page: {msg}');
                    if (window.showAdminNotification) window.showAdminNotification(template.replace('{msg}', (err && err.error) || unk), 'error');
                });
            }
        })
        .catch(error => {
            console.error('Error saving page:', error);
            if (window.showAdminNotification) window.showAdminNotification(spT('adminPanel.staticPageEditor.errorSavingPage', 'Error saving page.'), 'error');
        });
}

