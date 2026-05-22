// Simple event bus for cross-component communication
const EventBus = {
    _listeners: {},

    on(event, callback) {
        if (!this._listeners[event]) this._listeners[event] = [];
        this._listeners[event].push(callback);
    },

    off(event, callback) {
        if (!this._listeners[event]) return;
        this._listeners[event] = this._listeners[event].filter(cb => cb !== callback);
    },

    emit(event, data) {
        if (!this._listeners[event]) return;
        this._listeners[event].forEach(cb => {
            try { cb(data); } catch (e) { console.error('EventBus error:', e); }
        });
    }
};

// Global state
const AppState = {
    workspace: null,
    currentFile: null,
    sessionId: null,
    models: [],
    currentModel: null,
    terminalVisible: true,
    sidebarVisible: true,
    agentPanelVisible: true,
    activePanel: 'explorer',
    openTabs: [],
};
