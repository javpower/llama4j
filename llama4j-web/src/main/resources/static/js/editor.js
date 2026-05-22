// Monaco Editor management
const Editor = {
    editor: null,
    diffEditor: null,
    diffOriginalModel: null,
    diffModifiedModel: null,
    openTabs: [],
    activeTab: null,
    monacoReady: false,

    init() {
        // Load Monaco
        require(['vs/editor/editor.main'], () => {
            this.monacoReady = true;
            // Define dark theme
            monaco.editor.defineTheme('llama4j-dark', {
                base: 'vs-dark',
                inherit: true,
                rules: [],
                colors: {
                    'editor.background': '#1e1e1e',
                    'editor.foreground': '#cccccc',
                    'editor.lineHighlightBackground': '#2a2d2e',
                    'editor.selectionBackground': '#264f78',
                    'editor.inactiveSelectionBackground': '#3a3d41',
                }
            });

            this.editor = monaco.editor.create(document.getElementById('monaco-container'), {
                theme: 'llama4j-dark',
                automaticLayout: true,
                minimap: { enabled: true },
                fontSize: 13,
                fontFamily: "'Fira Code', 'Cascadia Code', 'JetBrains Mono', Consolas, monospace",
                lineNumbers: 'on',
                renderWhitespace: 'selection',
                scrollBeyondLastLine: false,
                wordWrap: 'off',
                tabSize: 4,
                insertSpaces: true,
                bracketPairColorization: { enabled: true },
            });

            // Track cursor position
            this.editor.onDidChangeCursorPosition((e) => {
                document.getElementById('status-cursor').textContent =
                    `Ln ${e.position.lineNumber}, Col ${e.position.column}`;
            });

            // Track modifications
            this.editor.onDidChangeModelContent(() => {
                if (this.activeTab) {
                    this.activeTab.modified = true;
                    this.updateTabUI();
                }
            });

            // Ctrl+S to save
            this.editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => {
                this.saveCurrentFile();
            });

            // Cleanup on page unload
            window.addEventListener('beforeunload', () => this.dispose());

            EventBus.emit('editor:ready');
        });
    },

    async openFile(path, name) {
        if (!this.monacoReady) return;

        // Check if already open
        const existing = this.openTabs.find(t => t.path === path);
        if (existing) {
            this.switchToTab(existing);
            return;
        }

        try {
            const data = await API.get(`/api/file/read?path=${encodeURIComponent(path)}`);
            const language = data.language || getMonacoLanguage(name);

            // Create Monaco model
            const uri = monaco.Uri.parse('file:///' + path);
            let model = monaco.editor.createModel(data.content, language, uri);

            const tab = {
                path: path,
                name: name,
                model: model,
                modified: false,
                originalContent: data.content,
                language: language,
            };

            this.openTabs.push(tab);
            this.switchToTab(tab);
            this.renderTabs();

            this.updateStatusBar(tab);
        } catch (e) {
            console.error('Failed to open file:', e);
            alert('Failed to open file: ' + e.message);
        }
    },

    switchToTab(tab) {
        this.activeTab = tab;
        this.editor.setModel(tab.model);

        // Show editor, hide welcome
        document.getElementById('welcome-view').style.display = 'none';
        document.getElementById('monaco-container').style.display = 'block';
        document.getElementById('diff-container').style.display = 'none';

        this.renderTabs();
        this.updateStatusBar(tab);
    },

    updateStatusBar(tab) {
        if (!tab) return;
        document.getElementById('status-language').textContent = tab.language;
        const opts = tab.model.getOptions();
        document.getElementById('status-indent').textContent = `Spaces: ${opts.tabSize}`;
        document.getElementById('status-encoding').textContent = tab.model.getEncoding?.() || 'UTF-8';
    },

    async closeTab(tab) {
        const idx = this.openTabs.indexOf(tab);
        if (idx === -1) return;

        if (tab.modified) {
            if (!confirm(`Save changes to ${tab.name}?`)) {
                // Don't save, just close
            } else {
                await this.saveFile(tab);
            }
        }

        tab.model.dispose();
        this.openTabs.splice(idx, 1);

        if (this.openTabs.length === 0) {
            this.activeTab = null;
            document.getElementById('welcome-view').style.display = 'flex';
            document.getElementById('monaco-container').style.display = 'none';
            document.getElementById('diff-container').style.display = 'none';
        } else if (this.activeTab === tab) {
            this.switchToTab(this.openTabs[Math.min(idx, this.openTabs.length - 1)]);
        }

        this.renderTabs();
    },

    renderTabs() {
        const container = document.getElementById('editor-tabs');
        container.innerHTML = this.openTabs.map(tab => {
            const isActive = tab === this.activeTab;
            const icon = getFileIcon(tab.name);
            return `<div class="editor-tab ${isActive ? 'active' : ''}" data-path="${escapeHtml(tab.path)}">
                <span>${icon}</span>
                <span>${escapeHtml(tab.name)}</span>
                ${tab.modified ? '<span class="tab-modified"></span>' : ''}
                <button class="tab-close" data-path="${escapeHtml(tab.path)}">${Icons.close}</button>
            </div>`;
        }).join('');

        // Tab click handlers
        container.querySelectorAll('.editor-tab').forEach(el => {
            el.addEventListener('click', (e) => {
                if (e.target.classList.contains('tab-close')) return;
                const tab = this.openTabs.find(t => t.path === el.dataset.path);
                if (tab) this.switchToTab(tab);
            });
        });

        container.querySelectorAll('.tab-close').forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.stopPropagation();
                const tab = this.openTabs.find(t => t.path === btn.dataset.path);
                if (tab) this.closeTab(tab);
            });
        });
    },

    updateTabUI() {
        this.renderTabs();
    },

    async saveCurrentFile() {
        if (this.activeTab) await this.saveFile(this.activeTab);
    },

    async saveFile(tab) {
        try {
            const content = tab.model.getValue();
            await API.post('/api/file/write', { path: tab.path, content });
            tab.modified = false;
            tab.originalContent = content;
            this.renderTabs();
        } catch (e) {
            console.error('Failed to save file:', e);
            alert('Failed to save: ' + e.message);
        }
    },

    async showDiff(path, name) {
        if (!this.monacoReady) return;

        try {
            const data = await API.get(`/api/file/read?path=${encodeURIComponent(path)}`);
            const diffData = await API.get(`/api/git/diff?file=${encodeURIComponent(path)}`);

            document.getElementById('welcome-view').style.display = 'none';
            document.getElementById('monaco-container').style.display = 'none';
            document.getElementById('diff-container').style.display = 'block';

            if (!this.diffEditor) {
                this.diffEditor = monaco.editor.createDiffEditor(document.getElementById('diff-container'), {
                    theme: 'llama4j-dark',
                    automaticLayout: true,
                    readOnly: true,
                });
            }

            const language = getMonacoLanguage(name);

            // Dispose previous diff models to avoid memory leaks
            if (this.diffOriginalModel) this.diffOriginalModel.dispose();
            if (this.diffModifiedModel) this.diffModifiedModel.dispose();

            // Use the tab's original content if available, otherwise use current content as original
            const existingTab = this.openTabs.find(t => t.path === path);
            const originalContent = existingTab?.originalContent || data.content;

            this.diffOriginalModel = monaco.editor.createModel(originalContent, language);
            this.diffModifiedModel = monaco.editor.createModel(data.content, language);

            this.diffEditor.setModel({
                original: this.diffOriginalModel,
                modified: this.diffModifiedModel,
            });
        } catch (e) {
            console.error('Failed to show diff:', e);
        }
    },

    getContent() {
        return this.activeTab ? this.activeTab.model.getValue() : '';
    },

    getActivePath() {
        return this.activeTab ? this.activeTab.path : null;
    },

    dispose() {
        this.openTabs.forEach(tab => tab.model.dispose());
        this.openTabs = [];
        if (this.diffOriginalModel) this.diffOriginalModel.dispose();
        if (this.diffModifiedModel) this.diffModifiedModel.dispose();
        if (this.diffEditor) this.diffEditor.dispose();
        if (this.editor) this.editor.dispose();
        this.activeTab = null;
    }
};
