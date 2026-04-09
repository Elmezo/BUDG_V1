/**
 * Bulk Import ENV: upload encrypted .bsnap or legacy ZIP to TableSnapshotServlet (/api/table-snapshot/import), merge or replace.
 */

function escapeBulkEnvHtml(s) {
    if (s == null || s === '') return '';
    return String(s)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function showBulkImportEnvContent(contentArea) {
    highlightSubmenuItem('adminPanel.submenu.bulkImportEnv');
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    updateSystemTitle(
        T('adminPanel.bulkImportEnv.title', 'Bulk Import ENV'),
        T('adminPanel.bulkImportEnv.subtitle', 'Import a table snapshot ZIP (metadata.json + JSONL per table)'));

    contentArea.innerHTML = `
        <div class="bulk-import-env-container" style="padding: 0;">
            <div style="background-color: #2c3e50; padding: 20px 30px; display: flex; justify-content: space-between; align-items: center; margin-bottom: 0;">
                <div style="display: flex; align-items: center; gap: 15px;">
                    <button id="envBackButton" style="background: none; border: none; color: white; font-size: 24px; cursor: pointer; padding: 5px 10px; border-radius: 50%; width: 40px; height: 40px; display: flex; align-items: center; justify-content: center;">
                        <i class="fas fa-arrow-left"></i>
                    </button>
                    <div>
                        <h1 style="margin: 0; color: white; font-size: 28px; font-weight: 600;">${T('adminPanel.bulkImportEnv.title', 'Bulk Import ENV')}</h1>
                        <p style="margin: 5px 0 0 0; color: rgba(255, 255, 255, 0.8); font-size: 14px;">${T('adminPanel.bulkImportEnv.subtitle', 'Import a table snapshot ZIP (metadata.json + JSONL per table)')}</p>
                    </div>
                </div>
                <div style="display: flex; gap: 10px;">
                    <button id="envImportButton" style="padding: 10px 25px; background-color: #95a5a6; color: #2c3e50; border: none; border-radius: 4px; cursor: not-allowed; font-weight: 500; font-size: 14px;" disabled>
                        ${T('adminPanel.bulkImportEnv.import', 'Import')}
                    </button>
                    <button id="envCloseButton" style="padding: 10px 25px; background-color: #34495e; color: white; border: none; border-radius: 4px; cursor: pointer; font-weight: 500; font-size: 14px;">
                        ${T('adminPanel.bulkImportEnv.close', 'Close')}
                    </button>
                </div>
            </div>

            <div style="background-color: #ecf0f1; padding: 0; border-bottom: 2px solid #3498db;">
                <div style="display: flex; padding-left: 30px;">
                    <div style="padding: 15px 20px; background-color: #bdc3c7; color: white; font-weight: 600; font-size: 14px; border-bottom: 3px solid #3498db;">
                        ${T('adminPanel.bulkImportEnv.tabLabel', 'IMPORT ENV')}
                    </div>
                </div>
            </div>

            <div style="background-color: white; padding: 40px; min-height: 400px;">
                <div style="background-color: #fff3cd; border: 1px solid #ffc107; padding: 15px; border-radius: 4px; margin-bottom: 24px; color: #856404;">
                    <strong>${T('adminPanel.bulkImportEnv.warningTitle', 'Warning')}</strong>
                    <p style="margin: 8px 0 0 0; font-size: 14px;">${T('adminPanel.bulkImportEnv.replaceWarning', 'Replace mode truncates every table included in the snapshot ZIP before import. This is destructive.')}</p>
                </div>

                <div style="margin-bottom: 24px;">
                    <h3 style="margin: 0 0 12px 0; color: #2c3e50; font-size: 16px;">${T('adminPanel.bulkImportEnv.modeLabel', 'Import mode')}</h3>
                    <label style="display: block; margin: 8px 0; cursor: pointer;">
                        <input type="radio" name="envImportMode" value="merge" checked style="margin-right: 8px;">
                        ${T('adminPanel.bulkImportEnv.modeMerge', 'Merge — INSERT IGNORE: new rows only; existing primary/unique keys are not duplicated')}
                    </label>
                    <label style="display: block; margin: 8px 0; cursor: pointer;">
                        <input type="radio" name="envImportMode" value="replace" style="margin-right: 8px;">
                        ${T('adminPanel.bulkImportEnv.modeReplace', 'Replace — truncate snapshot tables, then import all rows from the ZIP')}
                    </label>
                </div>

                <div id="envDropZone" class="env-snapshot-dropzone" style="background-color: #e8f4f8; padding: 24px; border-radius: 8px; margin-bottom: 30px; border: 2px dashed #bdc3c7; transition: border-color 0.15s ease, background-color 0.15s ease;">
                    <h3 style="margin: 0 0 8px 0; color: #2c3e50; font-size: 16px; font-weight: 600;">${T('adminPanel.bulkImportEnv.uploadFile', 'Upload snapshot')}</h3>
                    <p style="margin: 0 0 16px 0; color: #546e7a; font-size: 13px;">${T('adminPanel.bulkImportEnv.dropHint', 'Drag and drop a file here, or use Choose File.')}</p>
                    <label style="display: block; font-weight: 500; color: #2c3e50; font-size: 14px; margin-bottom: 10px;">
                        ${T('adminPanel.bulkImportEnv.uploadLabel', 'Encrypted snapshot (.bsnap) or legacy .zip')} <span style="color: #e74c3c;">*</span>
                    </label>
                    <div style="display: flex; align-items: center; gap: 10px; flex-wrap: wrap;">
                        <input type="text" id="envFileNameDisplay" readonly
                               style="flex: 1; min-width: 240px; padding: 10px 12px; border: 1px solid #cfd8dc; border-radius: 4px; background-color: white; color: #95a5a6; font-size: 14px;"
                               value="${T('adminPanel.bulkImportEnv.noFile', 'No file selected')}" />
                        <input type="file" id="envFileInput" accept=".bsnap,.zip,application/octet-stream" style="display: none;" />
                        <button type="button" id="envChooseFileButton"
                                style="padding: 10px 20px; background-color: #e74c3c; color: white; border: none; border-radius: 4px; cursor: pointer; font-weight: 500; font-size: 14px; white-space: nowrap;">
                            ${T('adminPanel.bulkImportEnv.chooseFile', 'Choose File')}
                        </button>
                    </div>
                </div>

                <div id="envProgressArea" style="display: none; margin-bottom: 16px;">
                    <div style="height: 8px; background: #ecf0f1; border-radius: 4px; overflow: hidden;">
                        <div id="envProgressBar" style="height: 100%; width: 30%; background: #3498db; animation: envIndeterminate 1.2s ease-in-out infinite;"></div>
                    </div>
                    <p id="envProgressText" style="margin: 8px 0 0 0; font-size: 13px; color: #555;"></p>
                </div>
                <style>@keyframes envIndeterminate { 0% { margin-left: -30%; width: 30%; } 100% { margin-left: 100%; width: 30%; } }</style>

                <div id="envStatusMessage" style="display: none; padding: 15px; border-radius: 4px; margin-top: 20px;"></div>

                <div id="envResultsArea" style="display: none; margin-top: 24px;">
                    <h3 style="margin: 0 0 12px 0; color: #2c3e50; font-size: 16px;">${T('adminPanel.bulkImportEnv.resultsTitle', 'Import results')}</h3>
                    <div id="envResultsSummary" style="font-size: 14px; color: #333; line-height: 1.5; margin-bottom: 12px;"></div>
                    <div id="envResultsJobHint" style="display: none; font-size: 14px; color: #34495e; padding: 14px; background: #ecf0f1; border-radius: 4px; border: 1px solid #bdc3c7;">
                        <p id="envResultsJobHintText" style="margin: 0 0 10px 0;"></p>
                        <a href="#" id="envOpenMyJobsLink" style="color: #2980b9; font-weight: 600;">${T('adminPanel.bulkImportEnv.openMyJobs', 'Open My Jobs')}</a>
                    </div>
                </div>
            </div>
        </div>
        <style>
            #envBackButton:hover { background-color: rgba(255, 255, 255, 0.1) !important; }
            #envImportButton:not(:disabled):hover { background-color: #1e6b52 !important; }
            #envCloseButton:hover { background-color: #2c3e50 !important; }
            #envChooseFileButton:hover { background-color: #c0392b !important; }
            .env-snapshot-dropzone.env-snapshot-dropzone--active {
                border-color: #3498db !important;
                background-color: #d6eaf8 !important;
            }
        </style>
    `;

    initBulkImportEnv();
}

function renderSnapshotImportResults(data) {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const summaryEl = document.getElementById('envResultsSummary');
    const jobHint = document.getElementById('envResultsJobHint');
    const jobHintText = document.getElementById('envResultsJobHintText');
    const myJobsLink = document.getElementById('envOpenMyJobsLink');
    const resultsArea = document.getElementById('envResultsArea');
    if (!summaryEl || !resultsArea) return;

    const mode = data.mode || '';
    let tableCount = typeof data.tableCount === 'number' ? data.tableCount : null;
    let totalAtt = typeof data.totalRowsAttempted === 'number' ? data.totalRowsAttempted : null;
    let totalIns = typeof data.totalRowsInserted === 'number' ? data.totalRowsInserted : null;
    let totalSkip = typeof data.totalRowsSkippedDuplicate === 'number' ? data.totalRowsSkippedDuplicate : null;
    if (tableCount == null && Array.isArray(data.tables)) {
        const tables = data.tables;
        tableCount = tables.length;
        totalAtt = 0;
        totalIns = 0;
        totalSkip = 0;
        tables.forEach(function (t) {
            if (t.status === 'skipped') return;
            totalAtt += Number(t.rowsAttempted) || 0;
            totalIns += Number(t.rowsInserted) || 0;
            totalSkip += Number(t.rowsSkippedDuplicate) || 0;
        });
    }

    const lm = T('adminPanel.bulkImportEnv.summaryMode', 'Mode');
    const lt = T('adminPanel.bulkImportEnv.summaryTables', 'Tables');
    const la = T('adminPanel.bulkImportEnv.summaryAttempted', 'Σ attempted');
    const li = T('adminPanel.bulkImportEnv.summaryInserted', 'Σ inserted');
    const ls = T('adminPanel.bulkImportEnv.summarySkippedDup', 'Σ skipped (dup)');
    summaryEl.innerHTML = '<strong>' + escapeBulkEnvHtml(lm) + ':</strong> ' + escapeBulkEnvHtml(mode)
        + ' &nbsp;|&nbsp; <strong>' + escapeBulkEnvHtml(lt) + ':</strong> ' + (tableCount != null ? tableCount : '—')
        + ' &nbsp;|&nbsp; <strong>' + escapeBulkEnvHtml(la) + ':</strong> ' + (totalAtt != null ? totalAtt : '—')
        + ' &nbsp;|&nbsp; <strong>' + escapeBulkEnvHtml(li) + ':</strong> ' + (totalIns != null ? totalIns : '—')
        + ' &nbsp;|&nbsp; <strong>' + escapeBulkEnvHtml(ls) + ':</strong> ' + (totalSkip != null ? totalSkip : '—');

    if (jobHint && jobHintText && myJobsLink) {
        const jid = data.job_id != null ? data.job_id : data.jobId;
        const ref = data.reference_name || data.referenceName || '';
        if (jid != null && String(jid) !== '') {
            jobHint.style.display = 'block';
            const tmpl = T('adminPanel.bulkImportEnv.myJobsDetailHint',
                'Per-table details and merge warnings are in the JSON report on this job (download from My Jobs). Job ID: {jobId}{ref}.');
            const refPart = ref ? (' · ' + ref) : '';
            jobHintText.textContent = tmpl.replace(/\{jobId\}/g, String(jid)).replace(/\{ref\}/g, refPart);
            myJobsLink.onclick = function (e) {
                e.preventDefault();
                fetch('/api/me', { credentials: 'include' })
                    .then(function (r) { return r.ok ? r.json() : null; })
                    .then(function (u) {
                        const id = u && (u.id != null ? u.id : (u.ID != null ? u.ID : u.userId));
                        if (id != null && id !== '') {
                            window.location.href = '/view/people/' + id + '?tab=activity&subtab=my-jobs';
                        } else {
                            window.location.href = '/view/people';
                        }
                    })
                    .catch(function () { window.location.href = '/view/people'; });
            };
        } else {
            jobHint.style.display = 'none';
            jobHintText.textContent = '';
            myJobsLink.onclick = null;
        }
    }

    resultsArea.style.display = 'block';
}

function initBulkImportEnv() {
    const T = typeof adminT === 'function' ? adminT : function (k, f) { return f; };
    const noFile = T('adminPanel.bulkImportEnv.noFile', 'No file selected');
    const badTypeMsg = T('adminPanel.bulkImportEnv.invalidFileType', 'Please select a .bsnap or .zip file.');
    const fileInput = document.getElementById('envFileInput');
    const chooseBtn = document.getElementById('envChooseFileButton');
    const dropZone = document.getElementById('envDropZone');
    const nameDisplay = document.getElementById('envFileNameDisplay');
    const importBtn = document.getElementById('envImportButton');
    const closeBtn = document.getElementById('envCloseButton');
    const backBtn = document.getElementById('envBackButton');
    const statusEl = document.getElementById('envStatusMessage');
    const progressArea = document.getElementById('envProgressArea');
    const progressText = document.getElementById('envProgressText');
    const resultsArea = document.getElementById('envResultsArea');

    function goBack() {
        if (typeof handleNavigation === 'function') {
            handleNavigation('Operational Management');
        } else {
            window.history.back();
        }
    }

    function setDropActive(on) {
        if (!dropZone) return;
        dropZone.classList.toggle('env-snapshot-dropzone--active', !!on);
    }

    function clearSelectedFile() {
        fileInput.value = '';
        nameDisplay.value = noFile;
        nameDisplay.style.color = '#95a5a6';
        importBtn.disabled = true;
        importBtn.style.backgroundColor = '#95a5a6';
        importBtn.style.cursor = 'not-allowed';
        importBtn.style.color = '#2c3e50';
    }

    function applyValidFile(file) {
        nameDisplay.value = file.name;
        nameDisplay.style.color = '#2c3e50';
        importBtn.disabled = false;
        importBtn.style.backgroundColor = '#248567';
        importBtn.style.cursor = 'pointer';
        importBtn.style.color = 'white';
        statusEl.style.display = 'none';
        if (resultsArea) resultsArea.style.display = 'none';
    }

    /**
     * @returns {boolean} true if file accepted
     */
    function tryAcceptSnapshotFile(file) {
        if (!file) {
            clearSelectedFile();
            return false;
        }
        const low = file.name.toLowerCase();
        if (!low.endsWith('.zip') && !low.endsWith('.bsnap')) {
            if (typeof showToastNotification === 'function') {
                showToastNotification(badTypeMsg, 'error');
            }
            clearSelectedFile();
            return false;
        }
        try {
            const dt = new DataTransfer();
            dt.items.add(file);
            fileInput.files = dt.files;
        } catch (e) {
            console.warn('DataTransfer not supported; use Choose File.', e);
            if (typeof showToastNotification === 'function') {
                showToastNotification(badTypeMsg, 'error');
            }
            return false;
        }
        applyValidFile(file);
        return true;
    }

    chooseBtn.addEventListener('click', () => fileInput.click());

    if (dropZone) {
        let dragDepth = 0;
        dropZone.addEventListener('dragenter', function (e) {
            e.preventDefault();
            e.stopPropagation();
            dragDepth++;
            setDropActive(true);
        });
        dropZone.addEventListener('dragover', function (e) {
            e.preventDefault();
            e.stopPropagation();
            if (e.dataTransfer) e.dataTransfer.dropEffect = 'copy';
        });
        dropZone.addEventListener('dragleave', function (e) {
            e.preventDefault();
            e.stopPropagation();
            dragDepth--;
            if (dragDepth <= 0) {
                dragDepth = 0;
                setDropActive(false);
            }
        });
        dropZone.addEventListener('drop', function (e) {
            e.preventDefault();
            e.stopPropagation();
            dragDepth = 0;
            setDropActive(false);
            const f = e.dataTransfer && e.dataTransfer.files && e.dataTransfer.files[0];
            tryAcceptSnapshotFile(f || null);
        });
    }

    fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) {
            const low = file.name.toLowerCase();
            if (!low.endsWith('.zip') && !low.endsWith('.bsnap')) {
                if (typeof showToastNotification === 'function') {
                    showToastNotification(badTypeMsg, 'error');
                }
                clearSelectedFile();
                return;
            }
            applyValidFile(file);
        } else {
            clearSelectedFile();
        }
    });

    importBtn.addEventListener('click', async () => {
        const file = fileInput.files[0];
        if (!file) {
            if (typeof showToastNotification === 'function') {
                showToastNotification(T('adminPanel.bulkImportEnv.selectFileFirst', 'Please select a file first.'), 'error');
            }
            return;
        }
        const modeRadio = document.querySelector('input[name="envImportMode"]:checked');
        const mode = modeRadio ? modeRadio.value : 'merge';

        const originalHtml = importBtn.innerHTML;
        importBtn.disabled = true;
        importBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> ' + T('adminPanel.bulkImportEnv.importing', 'Importing...');
        progressArea.style.display = 'block';
        progressText.textContent = T('adminPanel.bulkImportEnv.progressUpload', 'Uploading and importing...');
        statusEl.style.display = 'none';
        if (resultsArea) resultsArea.style.display = 'none';

        if (typeof showToastNotification === 'function') {
            showToastNotification(T('adminPanel.bulkImportEnv.uploadStarted', 'Upload started...'), 'info');
        }

        try {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('mode', mode);

            const response = await fetch('/api/table-snapshot/import', {
                method: 'POST',
                body: formData,
                credentials: 'include'
            });
            const text = await response.text();
            let data = {};
            try {
                data = JSON.parse(text);
            } catch (e) {
                data = { status: 'error', error: text || response.statusText };
            }

            if (response.ok && data.status === 'success') {
                if (typeof showToastNotification === 'function') {
                    showToastNotification(
                        T('adminPanel.bulkImportEnv.successToast', 'Table snapshot import completed.'),
                        'success');
                }
                statusEl.innerHTML = '<div style="background:#d4edda;border:1px solid #c3e6cb;color:#155724;padding:15px;border-radius:4px;">'
                    + '<strong>' + T('adminPanel.bulkImportEnv.successTitle', 'Success') + '</strong>'
                    + '<p style="margin:8px 0 0 0;">' + T('adminPanel.bulkImportEnv.successBodySnapshot', 'Import finished. Summary is shown below; open My Jobs for the full JSON report.') + '</p></div>';
                statusEl.style.display = 'block';
                renderSnapshotImportResults(data);
                fileInput.value = '';
                nameDisplay.value = noFile;
                nameDisplay.style.color = '#95a5a6';
            } else {
                const err = data.error || data.message || response.statusText || 'Error';
                if (typeof showToastNotification === 'function') {
                    showToastNotification(err, 'error');
                }
                statusEl.innerHTML = '<div style="background:#f8d7da;border:1px solid #f5c6cb;color:#721c24;padding:15px;border-radius:4px;white-space:pre-wrap;">'
                    + escapeBulkEnvHtml(err) + '</div>';
                statusEl.style.display = 'block';
            }
        } catch (err) {
            console.error(err);
            const msg = err && err.message ? err.message : T('adminPanel.bulkImportEnv.errorGeneric', 'Import failed.');
            if (typeof showToastNotification === 'function') {
                showToastNotification(msg, 'error');
            }
            statusEl.innerHTML = '<div style="background:#f8d7da;border:1px solid #f5c6cb;color:#721c24;padding:15px;border-radius:4px;">' + escapeBulkEnvHtml(msg) + '</div>';
            statusEl.style.display = 'block';
        } finally {
            importBtn.innerHTML = originalHtml;
            const hasFile = fileInput.files && fileInput.files[0];
            importBtn.disabled = !hasFile;
            if (!hasFile) {
                importBtn.style.backgroundColor = '#95a5a6';
                importBtn.style.cursor = 'not-allowed';
                importBtn.style.color = '#2c3e50';
            } else {
                importBtn.style.backgroundColor = '#248567';
                importBtn.style.cursor = 'pointer';
                importBtn.style.color = 'white';
            }
            progressArea.style.display = 'none';
        }
    });

    closeBtn.addEventListener('click', goBack);
    backBtn.addEventListener('click', goBack);
}
