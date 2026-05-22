// Main application initialization
const App = {
    initIcons() {
        // Title bar
        document.getElementById('titlebar-icon').innerHTML = Icons.bolt;
        document.getElementById('btn-settings').innerHTML = Icons.settings;
        document.getElementById('btn-help').innerHTML = Icons.help;

        // Activity bar
        document.querySelector('[data-icon="explorer"]').innerHTML = Icons.folder;
        document.querySelector('[data-icon="search"]').innerHTML = Icons.search;
        document.querySelector('[data-icon="git"]').innerHTML = Icons.git;
        document.querySelector('[data-icon="agent"]').innerHTML = Icons.agent;

        // Refresh buttons
        document.getElementById('btn-refresh-tree').innerHTML = Icons.refresh;
        document.getElementById('btn-refresh-git').innerHTML = Icons.refresh;

        // Terminal buttons
        document.getElementById('btn-new-terminal').innerHTML = Icons.plus;
        document.getElementById('btn-close-terminal').innerHTML = Icons.close;

        // Agent panel
        document.querySelector('.agent-title').innerHTML = Icons.agent + ' Agent';
        document.getElementById('btn-new-chat').innerHTML = Icons.plus;
        document.getElementById('btn-toggle-agent').innerHTML = Icons.close;
        document.getElementById('btn-send').innerHTML = Icons.send;

        // Dialog close buttons
        document.getElementById('btn-close-dialog').innerHTML = Icons.close;
        document.querySelector('#settings-dialog .dialog-close').innerHTML = Icons.close;
        document.querySelector('#help-dialog .dialog-close').innerHTML = Icons.close;
    },

    async init() {
        console.log('Llama4j Agent initializing...');

        // Set icons first
        this.initIcons();

        // Initialize all modules
        Workspace.init();
        FileExplorer.init();
        Editor.init();
        Term.init();
        Term.initEvents();
        Agent.init();
        Git.init();
        Search.init();
        Models.init();
        Shortcuts.init();

        // Try to restore workspace
        const ws = await Workspace.getCurrent();
        if (ws) {
            console.log('Restored workspace:', ws.name);
            FileExplorer.loadTree();
            Term.connect();
            Models.load();
        }

        // Connect terminal when workspace opens
        EventBus.on('workspace:opened', () => {
            Term.connect();
        });

        EventBus.on('workspace:closed', () => {
            Term.disconnect();
            Editor.openTabs.forEach(t => t.model.dispose());
            Editor.openTabs = [];
            Editor.activeTab = null;
            document.getElementById('welcome-view').style.display = 'flex';
            document.getElementById('monaco-container').style.display = 'none';
            document.getElementById('editor-tabs').innerHTML = '';
        });

        console.log('Llama4j Agent ready');
    }
};

// Start the application
document.addEventListener('DOMContentLoaded', () => App.init());
