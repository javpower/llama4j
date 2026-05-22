// API utility functions
const API = {
    async get(url) {
        const res = await fetch(url);
        if (!res.ok) throw new Error(`GET ${url}: ${res.status}`);
        return res.json();
    },

    async post(url, body) {
        const res = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        if (!res.ok) {
            const err = await res.json().catch(() => ({ error: res.statusText }));
            throw new Error(err.error || `POST ${url}: ${res.status}`);
        }
        return res.json();
    },

    async del(url) {
        const res = await fetch(url, { method: 'DELETE' });
        if (!res.ok) throw new Error(`DELETE ${url}: ${res.status}`);
        return res.json();
    },

    // SSE streaming
    stream(url, body, handlers) {
        const controller = new AbortController();

        fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body),
            signal: controller.signal
        }).then(response => {
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let eventType = '';

            function processLine(line) {
                if (line.startsWith('event:')) {
                    eventType = line.substring(6).trim();
                } else if (line.startsWith('data:')) {
                    const data = line.substring(5).trim();
                    if (data && eventType) {
                        try {
                            const parsed = JSON.parse(data);
                            const handler = handlers[eventType];
                            if (handler) handler(parsed);
                        } catch (e) {
                            console.warn('SSE parse error:', eventType, data, e);
                        }
                    }
                    eventType = '';
                }
            }

            function read() {
                reader.read().then(({ done, value }) => {
                    if (done) {
                        if (handlers.onDone) handlers.onDone();
                        return;
                    }

                    buffer += decoder.decode(value, { stream: true });
                    const lines = buffer.split('\n');
                    buffer = lines.pop();

                    for (const line of lines) {
                        processLine(line);
                    }
                    read();
                }).catch(err => {
                    if (err.name !== 'AbortError') {
                        console.error('SSE stream error:', err);
                        if (handlers.onError) handlers.onError(err);
                        if (handlers.onDone) handlers.onDone({});
                    }
                });
            }
            read();
        }).catch(err => {
            if (err.name !== 'AbortError') {
                console.error('SSE fetch error:', err);
                if (handlers.onError) handlers.onError(err);
                if (handlers.onDone) handlers.onDone({});
            }
        });

        return controller;
    }
};

// Debounce utility
function debounce(fn, delay) {
    let timer;
    return function (...args) {
        clearTimeout(timer);
        timer = setTimeout(() => fn.apply(this, args), delay);
    };
}

// Format file size
function formatSize(bytes) {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

// Get file extension
function getExtension(filename) {
    const dot = filename.lastIndexOf('.');
    return dot >= 0 ? filename.substring(dot) : '';
}

// Get file icon based on extension (SVG)
function getFileIcon(filename) {
    return getFileIconSvg(filename);
}

// Get language for Monaco editor
function getMonacoLanguage(filename) {
    const ext = getExtension(filename).toLowerCase();
    const langs = {
        '.java': 'java', '.py': 'python', '.js': 'javascript', '.ts': 'typescript',
        '.jsx': 'javascript', '.tsx': 'typescript', '.html': 'html', '.css': 'css',
        '.json': 'json', '.xml': 'xml', '.yaml': 'yaml', '.yml': 'yaml',
        '.md': 'markdown', '.sql': 'sql', '.sh': 'shell', '.bash': 'shell',
        '.go': 'go', '.rs': 'rust', '.c': 'c', '.cpp': 'cpp',
        '.h': 'c', '.hpp': 'cpp', '.rb': 'ruby', '.php': 'php',
        '.swift': 'swift', '.kt': 'kotlin', '.scala': 'scala',
        '.toml': 'ini', '.ini': 'ini', '.properties': 'ini',
        '.vue': 'html', '.svelte': 'html',
    };
    return langs[ext] || 'plaintext';
}

// Truncate string
function truncate(str, maxLen) {
    if (!str) return '';
    if (str.length <= maxLen) return str;
    return str.substring(0, maxLen) + '...';
}

// Escape HTML
function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
