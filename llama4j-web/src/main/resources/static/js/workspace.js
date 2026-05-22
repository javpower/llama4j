// Workspace management
const Workspace = {
    RECENT_KEY: 'llama4j-recent-workspaces',

    async open(path) {
        try {
            const result = await API.post('/api/workspace/open', { path });
            AppState.workspace = result;
            document.getElementById('workspace-name').textContent = result.name;
            this.saveRecent(result.path);
            EventBus.emit('workspace:opened', result);
            return result;
        } catch (e) {
            console.error('Failed to open workspace:', e);
            alert('Failed to open workspace: ' + e.message);
        }
    },

    async getCurrent() {
        try {
            const result = await API.get('/api/workspace/current');
            if (result.active) {
                AppState.workspace = result.workspace;
                document.getElementById('workspace-name').textContent = result.workspace.name;
                return result.workspace;
            }
        } catch (e) {
            console.error('Failed to get workspace:', e);
        }
        return null;
    },

    async close() {
        await API.del('/api/workspace/close');
        AppState.workspace = null;
        document.getElementById('workspace-name').textContent = 'No workspace open';
        EventBus.emit('workspace:closed');
    },

    getRecent() {
        try {
            return JSON.parse(localStorage.getItem(this.RECENT_KEY) || '[]');
        } catch {
            return [];
        }
    },

    saveRecent(path) {
        let recent = this.getRecent().filter(p => p !== path);
        recent.unshift(path);
        if (recent.length > 10) recent = recent.slice(0, 10);
        localStorage.setItem(this.RECENT_KEY, JSON.stringify(recent));
    },

    showDialog() {
        const dialog = document.getElementById('workspace-dialog');
        const input = document.getElementById('workspace-path-input');
        const recentList = document.getElementById('recent-workspaces');

        dialog.style.display = 'flex';
        input.value = '';
        input.focus();

        // Show recent workspaces
        const recent = this.getRecent();
        recentList.innerHTML = recent.map(p => {
            const name = p.split('/').pop() || p;
            return `<div class="recent-item" data-path="${escapeHtml(p)}">
                <span>${Icons.folder}</span>
                <span>${escapeHtml(name)}</span>
                <span style="color:var(--text-muted);margin-left:auto;font-size:11px">${escapeHtml(p)}</span>
            </div>`;
        }).join('');

        recentList.querySelectorAll('.recent-item').forEach(item => {
            item.addEventListener('click', () => {
                input.value = item.dataset.path;
            });
        });
    },

    hideDialog() {
        document.getElementById('workspace-dialog').style.display = 'none';
    },

    init() {
        document.getElementById('btn-open-workspace').addEventListener('click', () => this.showDialog());
        document.getElementById('btn-confirm-workspace').addEventListener('click', () => {
            const path = document.getElementById('workspace-path-input').value.trim();
            if (path) {
                this.hideDialog();
                this.open(path);
            }
        });
        document.getElementById('btn-cancel-dialog').addEventListener('click', () => this.hideDialog());
        document.getElementById('btn-close-dialog').addEventListener('click', () => this.hideDialog());

        document.getElementById('workspace-path-input').addEventListener('keydown', (e) => {
            if (e.key === 'Enter') {
                const path = e.target.value.trim();
                if (path) {
                    this.hideDialog();
                    this.open(path);
                }
            }
            if (e.key === 'Escape') this.hideDialog();
        });
    }
};
