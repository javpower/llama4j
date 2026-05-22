// Global search
const Search = {
    init() {
        const input = document.getElementById('search-input');
        const globInput = document.getElementById('search-glob');

        const doSearch = debounce(async () => {
            const pattern = input.value.trim();
            if (!pattern || !AppState.workspace) {
                document.getElementById('search-results').innerHTML = '';
                return;
            }

            try {
                const result = await API.post('/api/file/search', {
                    pattern,
                    glob: globInput.value.trim() || null,
                    path: '.'
                });
                this.renderResults(result.results);
            } catch (e) {
                console.error('Search failed:', e);
            }
        }, 300);

        input.addEventListener('input', doSearch);
        globInput.addEventListener('input', doSearch);

        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter') doSearch();
        });
    },

    renderResults(results) {
        const container = document.getElementById('search-results');
        if (!results || results.length === 0) {
            container.innerHTML = '<div style="padding:8px 12px;color:var(--text-muted);">No results</div>';
            return;
        }

        container.innerHTML = results.map(r => `
            <div class="search-result-item" data-file="${escapeHtml(r.file)}" data-line="${r.line}">
                <span class="search-result-file">${escapeHtml(r.file)}</span>
                <span class="search-result-line">:${r.line}</span>
                <span class="search-result-content">${escapeHtml(r.content)}</span>
            </div>
        `).join('');

        container.querySelectorAll('.search-result-item').forEach(el => {
            el.addEventListener('click', async () => {
                const file = el.dataset.file;
                const line = parseInt(el.dataset.line);
                const name = file.split('/').pop();
                await Editor.openFile(file, name);
                // Jump to line
                if (Editor.editor) {
                    Editor.editor.revealLineInCenter(line);
                    Editor.editor.setPosition({ lineNumber: line, column: 1 });
                }
            });
        });
    }
};
