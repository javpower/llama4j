// Git panel
const Git = {
    status: null,

    async refresh() {
        if (!AppState.workspace) return;
        try {
            const result = await API.get('/api/git/status');
            if (!result.isGitRepo) {
                document.getElementById('git-panel').innerHTML =
                    '<div style="padding:12px;color:var(--text-muted);">Not a git repository</div>';
                return;
            }

            this.status = result.status;
            this.render();

            // Update status bar
            document.getElementById('status-branch').textContent = result.status.branch;
        } catch (e) {
            console.error('Failed to get git status:', e);
        }
    },

    render() {
        const panel = document.getElementById('git-panel');
        if (!this.status) return;

        const { branch, modified, added, deleted, untracked } = this.status;
        const changes = [...modified.map(f => ({ f, type: 'M' })),
                        ...added.map(f => ({ f, type: 'A' })),
                        ...deleted.map(f => ({ f, type: 'D' })),
                        ...untracked.map(f => ({ f, type: 'U' }))];

        panel.innerHTML = `
            <div class="git-branch-section">
                <select class="git-branch-select" id="git-branch-select">
                    <option selected>${escapeHtml(branch)}</option>
                </select>
            </div>
            ${changes.length > 0 ? `
                <div class="git-section-header">Changes (${changes.length})</div>
                ${changes.map(({ f, type }) => `
                    <div class="git-file-item" data-path="${escapeHtml(f)}" data-type="${type}">
                        <span class="git-file-icon git-${type === 'M' ? 'modified' : type === 'A' ? 'added' : type === 'D' ? 'deleted' : 'untracked'}">${type}</span>
                        <span>${escapeHtml(f)}</span>
                    </div>
                `).join('')}
            ` : '<div style="padding:12px;color:var(--text-muted);">No changes</div>'}
            <div class="git-commit-section">
                <input class="git-commit-input" id="git-commit-msg" placeholder="Commit message...">
                <button class="git-commit-btn" id="git-commit-btn" ${changes.length === 0 ? 'disabled' : ''}>Commit</button>
            </div>
        `;

        // File click to show diff
        panel.querySelectorAll('.git-file-item').forEach(el => {
            el.addEventListener('click', () => {
                const path = el.dataset.path;
                const name = path.split('/').pop();
                Editor.showDiff(path, name);
            });
        });

        // Commit button handler
        const commitBtn = document.getElementById('git-commit-btn');
        const commitMsg = document.getElementById('git-commit-msg');
        if (commitBtn) {
            commitBtn.addEventListener('click', () => this.commit());
        }
        if (commitMsg) {
            commitMsg.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') this.commit();
            });
        }

        // Load branches
        this.loadBranches();
    },

    async commit() {
        const msgInput = document.getElementById('git-commit-msg');
        const message = msgInput?.value?.trim();
        if (!message) {
            alert('Please enter a commit message');
            return;
        }

        const btn = document.getElementById('git-commit-btn');
        if (btn) {
            btn.disabled = true;
            btn.textContent = 'Committing...';
        }

        try {
            const result = await API.post('/api/git/commit', { message });
            msgInput.value = '';
            alert(`Committed: ${result.hash}`);
            this.refresh();
        } catch (e) {
            console.error('Commit failed:', e);
            alert('Commit failed: ' + e.message);
        } finally {
            if (btn) {
                btn.disabled = false;
                btn.textContent = 'Commit';
            }
        }
    },

    async loadBranches() {
        try {
            const result = await API.get('/api/git/branches');
            const select = document.getElementById('git-branch-select');
            if (select) {
                select.innerHTML = result.branches.map(b =>
                    `<option ${b === result.current ? 'selected' : ''}>${escapeHtml(b)}</option>`
                ).join('');
            }
        } catch (e) {
            console.error('Failed to load branches:', e);
        }
    },

    init() {
        EventBus.on('workspace:opened', () => this.refresh());
        document.getElementById('btn-refresh-git').addEventListener('click', () => this.refresh());
    }
};
