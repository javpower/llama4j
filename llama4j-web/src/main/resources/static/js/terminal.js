// Terminal management with xterm.js
// Named 'Term' to avoid collision with xterm.js's window.Terminal
const Term = {
    term: null,
    ws: null,
    fitAddon: null,

    init() {
        if (typeof window.Terminal === 'undefined') {
            console.warn('xterm.js not loaded');
            return;
        }

        this.term = new window.Terminal({
            theme: {
                background: '#1e1e1e',
                foreground: '#cccccc',
                cursor: '#aeafad',
                selectionBackground: '#264f78',
                black: '#1e1e1e',
                red: '#f14c4c',
                green: '#4ec9b0',
                yellow: '#dcdcaa',
                blue: '#3794ff',
                magenta: '#c586c0',
                cyan: '#9cdcfe',
                white: '#cccccc',
            },
            fontSize: 13,
            fontFamily: "'Fira Code', 'Cascadia Code', 'JetBrains Mono', Consolas, monospace",
            cursorBlink: true,
            scrollback: 5000,
        });

        this.fitAddon = new (window.FitAddon?.FitAddon || window.FitAddon)();
        this.term.loadAddon(this.fitAddon);
        this.term.loadAddon(new (window.WebLinksAddon?.WebLinksAddon || window.WebLinksAddon)());

        this.term.open(document.getElementById('terminal-container'));

        // Fit on resize
        window.addEventListener('resize', () => this.fit());
        setTimeout(() => this.fit(), 100);

        // Input handler
        this.term.onData(data => {
            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                this.ws.send(JSON.stringify({ type: 'input', data }));
            }
        });
    },

    fit() {
        if (this.fitAddon) {
            this.fitAddon.fit();
            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                this.ws.send(JSON.stringify({
                    type: 'resize',
                    cols: this.term.cols,
                    rows: this.term.rows
                }));
            }
        }
    },

    connect() {
        if (!AppState.workspace) return;

        const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const url = `${protocol}//${location.host}/ws/terminal`;

        this.ws = new WebSocket(url);

        this.ws.onopen = () => {
            console.log('Terminal connected');
            this.fit();
        };

        this.ws.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);
                if (msg.type === 'output') {
                    this.term.write(msg.data);
                } else if (msg.type === 'exit') {
                    this.term.write('\r\n[Process exited]\r\n');
                }
            } catch (e) {
                console.error('Terminal message error:', e);
            }
        };

        this.ws.onclose = () => {
            console.log('Terminal disconnected');
        };

        this.ws.onerror = (e) => {
            console.error('Terminal error:', e);
        };
    },

    disconnect() {
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
    },

    clear() {
        if (this.term) this.term.clear();
    },

    toggle() {
        const area = document.getElementById('terminal-area');
        AppState.terminalVisible = !AppState.terminalVisible;
        area.style.display = AppState.terminalVisible ? 'flex' : 'none';
        if (AppState.terminalVisible) {
            setTimeout(() => this.fit(), 50);
        }
    },

    initEvents() {
        document.getElementById('btn-close-terminal').addEventListener('click', () => this.toggle());
        document.getElementById('btn-new-terminal').addEventListener('click', () => {
            this.disconnect();
            this.clear();
            this.connect();
        });
    }
};
