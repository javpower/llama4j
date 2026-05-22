// File Explorer panel
const FileExplorer = {
    treeData: null,
    selectedEl: null,

    async loadTree() {
        if (!AppState.workspace) return;
        try {
            const tree = await API.get('/api/workspace/files/tree?depth=5');
            this.treeData = tree;
            this.render(tree, document.getElementById('file-tree'), 0);
        } catch (e) {
            console.error('Failed to load file tree:', e);
        }
    },

    render(node, container, depth) {
        container.innerHTML = '';
        if (node.children) {
            this.renderNode(node, container, depth);
        }
    },

    renderNode(node, container, depth) {
        if (node.children) {
            // Directory
            const item = document.createElement('div');
            item.className = 'file-tree-item';
            item.style.paddingLeft = (depth * 16 + 4) + 'px';
            item.innerHTML = `
                <span class="tree-toggle">▶</span>
                <span class="tree-icon">${Icons.folder}</span>
                <span class="tree-name">${escapeHtml(node.name)}</span>
            `;

            const childrenDiv = document.createElement('div');
            childrenDiv.className = 'tree-children';

            item.addEventListener('click', (e) => {
                e.stopPropagation();
                const toggle = item.querySelector('.tree-toggle');
                const isExpanded = childrenDiv.classList.contains('expanded');
                if (isExpanded) {
                    childrenDiv.classList.remove('expanded');
                    toggle.classList.remove('expanded');
                } else {
                    childrenDiv.classList.add('expanded');
                    toggle.classList.add('expanded');
                }
            });

            container.appendChild(item);
            container.appendChild(childrenDiv);

            if (node.children) {
                node.children.forEach(child => this.renderNode(child, childrenDiv, depth + 1));
            }
        } else {
            // File
            const item = document.createElement('div');
            item.className = 'file-tree-item';
            item.style.paddingLeft = (depth * 16 + 20) + 'px';
            item.innerHTML = `
                <span class="tree-icon">${getFileIcon(node.name)}</span>
                <span class="tree-name">${escapeHtml(node.name)}</span>
            `;
            item.addEventListener('click', (e) => {
                e.stopPropagation();
                this.selectFile(item, node);
            });
            item.addEventListener('dblclick', (e) => {
                e.stopPropagation();
                Editor.openFile(node.path, node.name);
            });
            container.appendChild(item);
        }
    },

    selectFile(el, node) {
        if (this.selectedEl) this.selectedEl.classList.remove('selected');
        el.classList.add('selected');
        this.selectedEl = el;
        AppState.currentFile = node;
    },

    init() {
        EventBus.on('workspace:opened', () => this.loadTree());
        EventBus.on('workspace:closed', () => {
            document.getElementById('file-tree').innerHTML = '';
            this.treeData = null;
        });

        document.getElementById('btn-refresh-tree').addEventListener('click', () => this.loadTree());
    }
};
