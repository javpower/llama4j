// Keyboard shortcuts
const Shortcuts = {
    init() {
        document.addEventListener('keydown', (e) => {
            // Ctrl+P - Quick open
            if (e.ctrlKey && e.key === 'p') {
                e.preventDefault();
                Workspace.showDialog();
            }

            // Ctrl+Shift+F - Global search
            if (e.ctrlKey && e.shiftKey && e.key === 'F') {
                e.preventDefault();
                this.switchPanel('search');
                document.getElementById('search-input').focus();
            }

            // Ctrl+` - Toggle terminal
            if (e.ctrlKey && e.key === '`') {
                e.preventDefault();
                Term.toggle();
            }

            // Ctrl+B - Toggle sidebar
            if (e.ctrlKey && e.key === 'b') {
                e.preventDefault();
                this.toggleSidebar();
            }

            // Ctrl+Shift+A - Focus agent
            if (e.ctrlKey && e.shiftKey && e.key === 'A') {
                e.preventDefault();
                document.getElementById('agent-input').focus();
            }

            // Ctrl+Shift+E - Explorer
            if (e.ctrlKey && e.shiftKey && e.key === 'E') {
                e.preventDefault();
                this.switchPanel('explorer');
            }

            // Ctrl+Shift+G - Git
            if (e.ctrlKey && e.shiftKey && e.key === 'G') {
                e.preventDefault();
                this.switchPanel('git');
            }

            // Escape - Close dialogs
            if (e.key === 'Escape') {
                Workspace.hideDialog();
                this.hideSettings();
                this.hideHelp();
            }
        });

        // Activity bar button clicks
        document.querySelectorAll('.activity-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                this.switchPanel(btn.dataset.panel);
            });
        });

        // Settings button
        document.getElementById('btn-settings').addEventListener('click', () => this.showSettings());

        // Help button
        document.getElementById('btn-help').addEventListener('click', () => this.showHelp());
    },

    switchPanel(panelName) {
        // Update activity bar
        document.querySelectorAll('.activity-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.panel === panelName);
        });

        // Update sidebar panels
        document.querySelectorAll('.sidebar-panel').forEach(panel => {
            panel.classList.toggle('active', panel.id === `panel-${panelName}`);
        });

        AppState.activePanel = panelName;

        // Show sidebar if hidden
        if (!AppState.sidebarVisible) {
            this.toggleSidebar();
        }
    },

    toggleSidebar() {
        const sidebar = document.getElementById('sidebar');
        AppState.sidebarVisible = !AppState.sidebarVisible;
        sidebar.style.display = AppState.sidebarVisible ? 'flex' : 'none';
    },

    showSettings() {
        const dialog = document.getElementById('settings-dialog');
        if (!dialog) return;
        document.getElementById('settings-model').textContent = AppState.currentModel || 'Not loaded';
        document.getElementById('settings-workspace').textContent = AppState.workspace?.path || 'None';
        dialog.style.display = 'flex';
    },

    hideSettings() {
        const dialog = document.getElementById('settings-dialog');
        if (dialog) dialog.style.display = 'none';
    },

    showHelp() {
        const dialog = document.getElementById('help-dialog');
        if (dialog) dialog.style.display = 'flex';
    },

    hideHelp() {
        const dialog = document.getElementById('help-dialog');
        if (dialog) dialog.style.display = 'none';
    }
};
