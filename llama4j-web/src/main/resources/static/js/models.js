// Model management
const Models = {
    async load() {
        try {
            const result = await API.get('/api/models');
            AppState.models = result.models;
            AppState.currentModel = result.current;
            this.renderSelect();
        } catch (e) {
            console.error('Failed to load models:', e);
        }
    },

    renderSelect() {
        const select = document.getElementById('model-select');
        select.innerHTML = AppState.models.map(m =>
            `<option value="${escapeHtml(m.name)}" ${m.name === AppState.currentModel ? 'selected' : ''}>
                ${escapeHtml(m.modelName || m.name)}
            </option>`
        ).join('');
    },

    async switch(modelName) {
        try {
            const result = await API.post('/api/models/switch', { modelName });
            AppState.currentModel = result.current;
            this.renderSelect();
        } catch (e) {
            console.error('Failed to switch model:', e);
            alert('Failed to switch model: ' + e.message);
        }
    },

    init() {
        document.getElementById('model-select').addEventListener('change', (e) => {
            this.switch(e.target.value);
        });

        EventBus.on('workspace:opened', () => this.load());
    }
};
