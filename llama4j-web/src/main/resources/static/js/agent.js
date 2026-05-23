// Agent chat panel
const Agent = {
    messages: [],
    isStreaming: false,
    currentController: null,
    pendingApprovals: {},

    init() {
        const input = document.getElementById('agent-input');
        const sendBtn = document.getElementById('btn-send');

        // Auto-resize textarea
        input.addEventListener('input', () => {
            input.style.height = 'auto';
            input.style.height = Math.min(input.scrollHeight, 150) + 'px';
        });

        // Send on Enter
        input.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                this.send();
            }
        });

        sendBtn.addEventListener('click', () => this.send());

        document.getElementById('btn-new-chat').addEventListener('click', () => this.newSession());
        document.getElementById('btn-toggle-agent').addEventListener('click', () => this.togglePanel());

        // Permission dialog buttons
        document.getElementById('btn-deny').addEventListener('click', () => this.resolvePermission(false, false));
        document.getElementById('btn-allow-once').addEventListener('click', () => this.resolvePermission(true, false));
        document.getElementById('btn-allow-always').addEventListener('click', () => this.resolvePermission(true, true));

        // Configure marked once
        if (typeof marked !== 'undefined') {
            const renderer = new marked.Renderer();
            renderer.code = function(code, lang) {
                const codeText = typeof code === 'object' ? code.text : code;
                const codeLang = typeof code === 'object' ? code.lang : lang;
                if (typeof hljs !== 'undefined' && codeLang && hljs.getLanguage(codeLang)) {
                    const highlighted = hljs.highlight(codeText, { language: codeLang }).value;
                    return `<pre><code class="hljs language-${escapeHtml(codeLang)}">${highlighted}</code></pre>`;
                }
                return `<pre><code>${escapeHtml(codeText)}</code></pre>`;
            };
            marked.setOptions({ renderer, breaks: true });
        }

        // Load sessions when workspace opens
        EventBus.on('workspace:opened', () => this.loadSessions());
    },

    async send() {
        const input = document.getElementById('agent-input');
        const message = input.value.trim();
        if (!message || this.isStreaming) return;

        if (!AppState.workspace) {
            Workspace.showDialog();
            return;
        }

        input.value = '';
        input.style.height = 'auto';

        // Add user message
        this.addMessage('user', message);

        // Start streaming
        this.isStreaming = true;
        this.showLoading();

        const assistantMsg = this.addMessage('assistant', '');

        this.currentController = API.stream('/api/agent/chat', {
            message,
            sessionId: AppState.sessionId,
        }, {
            session: (data) => {
                AppState.sessionId = data.id;
            },
            content_delta: (data) => {
                this.appendToMessage(assistantMsg, data.token);
            },
            tool_call: (data) => {
                this.showToolCall(assistantMsg, data);
            },
            tool_result: (data) => {
                this.updateToolResult(data);
            },
            tool_approval_required: (data) => {
                this.showPermissionDialog(data);
            },
            thinking: (data) => {
                this.updateThinkingStatus(data.content || 'Thinking...');
            },
            done: (data) => {
                this.hideLoading();
                this.isStreaming = false;
                this.addStats(data);
            },
            error: (data) => {
                this.hideLoading();
                this.isStreaming = false;
                if (this.currentController) {
                    this.currentController.abort();
                    this.currentController = null;
                }
                this.appendToMessage(assistantMsg, `\n\n**Error:** ${data.message}`);
            },
        });
    },

    addMessage(role, content) {
        const container = document.getElementById('agent-messages');
        const msgDiv = document.createElement('div');
        msgDiv.className = `agent-message agent-msg-${role}`;
        msgDiv.innerHTML = `
            <div class="agent-msg-header">
                ${role === 'user' ? Icons.user + ' You' : Icons.agent + ' Agent'}
            </div>
            <div class="agent-msg-body">${content ? this.renderMarkdown(content) : ''}</div>
        `;
        container.appendChild(msgDiv);
        container.scrollTop = container.scrollHeight;

        const msg = { role, element: msgDiv, bodyEl: msgDiv.querySelector('.agent-msg-body'), content: '' };
        this.messages.push(msg);
        return msg;
    },

    appendToMessage(msg, token) {
        msg.content += token;
        // Strip <think>...</think> blocks (model reasoning) before display
        const cleaned = msg.content.replace(/<think>[\s\S]*?<\/think>/gi, '').trim();
        msg.bodyEl.innerHTML = this.renderMarkdown(cleaned);
        const container = document.getElementById('agent-messages');
        container.scrollTop = container.scrollHeight;
    },

    showToolCall(msg, data) {
        const toolDiv = document.createElement('div');
        toolDiv.className = 'tool-call-block';
        toolDiv.id = `tool-${data.id}`;
        toolDiv.innerHTML = `
            <div class="tool-call-header">
                <span class="tool-call-icon">${Icons.wrench}</span>
                <span class="tool-call-name">${escapeHtml(data.name)}</span>
                <span class="tool-call-status pending">${Icons.pending}</span>
            </div>
            <div class="tool-call-body">
                <div class="tool-call-args">${escapeHtml(truncate(data.arguments, 500))}</div>
                <div class="tool-call-result" id="tool-result-${data.id}">Executing...</div>
            </div>
        `;

        toolDiv.querySelector('.tool-call-header').addEventListener('click', () => {
            toolDiv.querySelector('.tool-call-body').classList.toggle('expanded');
        });

        msg.bodyEl.appendChild(toolDiv);
        const container = document.getElementById('agent-messages');
        container.scrollTop = container.scrollHeight;
    },

    updateToolResult(data) {
        const resultEl = document.getElementById(`tool-result-${data.id}`);
        const toolBlock = document.getElementById(`tool-${data.id}`);
        if (!resultEl || !toolBlock) return;

        const statusEl = toolBlock.querySelector('.tool-call-status');
        if (data.success) {
            statusEl.className = 'tool-call-status success';
            statusEl.innerHTML = Icons.check;
        } else {
            statusEl.className = 'tool-call-status error';
            statusEl.innerHTML = Icons.cross;
        }
        resultEl.textContent = data.content || '(empty)';
    },

    showPermissionDialog(data) {
        this.pendingApprovals[data.id] = data;
        document.getElementById('permission-tool-name').textContent = `Tool: ${data.name}`;
        document.getElementById('permission-args').textContent = typeof data.arguments === 'string'
            ? data.arguments : JSON.stringify(data.arguments, null, 2);
        document.getElementById('permission-dialog').style.display = 'flex';
    },

    resolvePermission(approved, alwaysAllow) {
        document.getElementById('permission-dialog').style.display = 'none';
        const keys = Object.keys(this.pendingApprovals);
        if (keys.length === 0) return;

        const id = keys[0];
        const data = this.pendingApprovals[id];
        delete this.pendingApprovals[id];

        if (alwaysAllow && data.name) {
            API.post('/api/agent/tools/approve', {
                toolName: data.name,
                alwaysAllow: true
            }).catch(e => console.error('Failed to save approval:', e));
        }

        // The approval is handled client-side; the agent will retry
        // For now, show a message in the tool call
        const resultEl = document.getElementById(`tool-result-${id}`);
        if (resultEl) {
            if (approved) {
                resultEl.textContent = 'Approved, retrying...';
            } else {
                resultEl.textContent = 'Denied by user';
                const toolBlock = document.getElementById(`tool-${id}`);
                if (toolBlock) {
                    const statusEl = toolBlock.querySelector('.tool-call-status');
                    statusEl.className = 'tool-call-status error';
                    statusEl.innerHTML = Icons.cross;
                }
            }
        }
    },

    addStats(data) {
        const container = document.getElementById('agent-messages');
        const statsDiv = document.createElement('div');
        statsDiv.style.cssText = 'padding:4px 0;font-size:11px;color:var(--text-muted);';
        statsDiv.textContent = `${data.completionTokens} tokens · ${data.tokensPerSecond?.toFixed(1) || '?'} tok/s · ${((data.latencyMs || 0) / 1000).toFixed(1)}s`;
        container.appendChild(statsDiv);
        container.scrollTop = container.scrollHeight;
    },

    showLoading() {
        const container = document.getElementById('agent-messages');
        const loading = document.createElement('div');
        loading.id = 'agent-loading';
        loading.className = 'agent-loading';
        loading.innerHTML = '<div class="loading-dots"><span></span><span></span><span></span></div> Thinking...';
        container.appendChild(loading);
        container.scrollTop = container.scrollHeight;
    },

    hideLoading() {
        const loading = document.getElementById('agent-loading');
        if (loading) loading.remove();
    },

    updateThinkingStatus(text) {
        const loading = document.getElementById('agent-loading');
        if (loading) {
            loading.innerHTML = '<div class="loading-dots"><span></span><span></span><span></span></div> ' + escapeHtml(text);
        }
    },

    async newSession() {
        try {
            const result = await API.post('/api/agent/session/new');
            AppState.sessionId = result.id;
            document.getElementById('agent-messages').innerHTML = '';
            this.messages = [];
            this.loadSessions();
        } catch (e) {
            console.error('Failed to create session:', e);
        }
    },

    async loadSessions() {
        try {
            const result = await API.get('/api/agent/sessions');
            const list = document.getElementById('agent-sessions-list');
            if (!list) return;

            const sessions = result.sessions || [];
            if (sessions.length === 0) {
                list.innerHTML = '<div style="padding:8px;color:var(--text-muted);font-size:12px;">No sessions</div>';
                return;
            }

            list.innerHTML = sessions.map(s => `
                <div class="session-item ${s.id === AppState.sessionId ? 'active' : ''}" data-id="${escapeHtml(s.id)}">
                    <div class="session-info">
                        <span class="session-title">Session ${s.messageCount} msgs</span>
                        <span class="session-time">${new Date(s.createdAt).toLocaleTimeString()}</span>
                    </div>
                    <button class="session-delete" data-id="${escapeHtml(s.id)}" title="Delete">${Icons.close}</button>
                </div>
            `).join('');

            // Click to switch session
            list.querySelectorAll('.session-item').forEach(el => {
                el.addEventListener('click', (e) => {
                    if (e.target.classList.contains('session-delete')) return;
                    AppState.sessionId = el.dataset.id;
                    document.getElementById('agent-messages').innerHTML = '';
                    this.messages = [];
                    this.loadSessions();
                });
            });

            // Delete buttons
            list.querySelectorAll('.session-delete').forEach(btn => {
                btn.addEventListener('click', async (e) => {
                    e.stopPropagation();
                    try {
                        await API.del(`/api/agent/session/${btn.dataset.id}`);
                        if (AppState.sessionId === btn.dataset.id) {
                            AppState.sessionId = null;
                            document.getElementById('agent-messages').innerHTML = '';
                            this.messages = [];
                        }
                        this.loadSessions();
                    } catch (e) {
                        console.error('Failed to delete session:', e);
                    }
                });
            });
        } catch (e) {
            console.error('Failed to load sessions:', e);
        }
    },

    togglePanel() {
        const panel = document.getElementById('agent-panel');
        AppState.agentPanelVisible = !AppState.agentPanelVisible;
        panel.style.display = AppState.agentPanelVisible ? 'flex' : 'none';
    },

    renderMarkdown(text) {
        if (typeof marked === 'undefined') return escapeHtml(text);
        try {
            const html = marked.parse(text);
            return typeof DOMPurify !== 'undefined' ? DOMPurify.sanitize(html) : escapeHtml(text);
        } catch (e) {
            return escapeHtml(text);
        }
    },

    insertFilePath(path) {
        const input = document.getElementById('agent-input');
        input.value += ` ${path} `;
        input.focus();
    }
};
