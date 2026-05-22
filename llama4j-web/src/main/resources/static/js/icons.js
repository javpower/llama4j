// SVG Icon library - replaces emoji icons with clean SVGs
const Icons = {
    folder: `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M1.5 2.5h4.382l1.5 2H14.5v9H1.5v-11z" fill="#dcb67a" stroke="#dcb67a" stroke-width="0.5"/></svg>`,

    folderOpen: `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M1.5 2.5h4.382l1.5 2H14.5L13 13.5H3L1.5 2.5z" fill="#dcb67a" stroke="#dcb67a" stroke-width="0.5"/></svg>`,

    file: `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M4 1.5h5.586L13 4.914V14.5H4V1.5z" fill="#6d8086" stroke="#6d8086" stroke-width="0.5"/><path d="M9.5 1.5v3.414H13" fill="none" stroke="#8ab4f3" stroke-width="0.5"/></svg>`,

    search: `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><circle cx="6.5" cy="6.5" r="4" stroke="#cccccc" stroke-width="1.5" fill="none"/><path d="M9.5 9.5L13 13" stroke="#cccccc" stroke-width="1.5" stroke-linecap="round"/></svg>`,

    git: `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><circle cx="4" cy="4" r="1.5" fill="#f14c4c"/><circle cx="4" cy="12" r="1.5" fill="#4ec9b0"/><circle cx="12" cy="8" r="1.5" fill="#3794ff"/><path d="M4 5.5V10.5" stroke="#cccccc" stroke-width="1"/><path d="M4 8L10.5 8" stroke="#cccccc" stroke-width="1"/></svg>`,

    agent: `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><rect x="3" y="2" width="10" height="10" rx="2" stroke="#3794ff" stroke-width="1.2" fill="none"/><circle cx="6" cy="6" r="1" fill="#3794ff"/><circle cx="10" cy="6" r="1" fill="#3794ff"/><path d="M5.5 9.5h5" stroke="#3794ff" stroke-width="1" stroke-linecap="round"/><path d="M8 12v2" stroke="#3794ff" stroke-width="1"/><path d="M5 14h6" stroke="#3794ff" stroke-width="1" stroke-linecap="round"/></svg>`,

    bolt: `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M9 1L4 9h4l-1 6 5-8H8l1-6z" fill="#dcdcaa" stroke="#dcdcaa" stroke-width="0.5"/></svg>`,

    close: `<svg width="10" height="10" viewBox="0 0 10 10" fill="none"><path d="M1 1L9 9M9 1L1 9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,

    refresh: `<svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M12 7A5 5 0 1 1 7 2" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" fill="none"/><path d="M7 0l3 2-3 2" fill="currentColor"/></svg>`,

    send: `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M2 8l12-5-5 12-2-5-5-2z" fill="#007acc" stroke="#007acc" stroke-width="0.5"/><path d="M7 10l2 5" stroke="#007acc" stroke-width="0.5"/></svg>`,

    plus: `<svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M6 1v10M1 6h10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>`,

    warning: `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M8 1L15 14H1L8 1z" fill="none" stroke="#dcdcaa" stroke-width="1.2"/><path d="M8 6v4" stroke="#dcdcaa" stroke-width="1.5" stroke-linecap="round"/><circle cx="8" cy="12" r="0.8" fill="#dcdcaa"/></svg>`,

    settings: `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><circle cx="8" cy="8" r="2" stroke="currentColor" stroke-width="1.2" fill="none"/><path d="M8 1v2M8 13v2M1 8h2M13 8h2M3.05 3.05l1.41 1.41M11.54 11.54l1.41 1.41M3.05 12.95l1.41-1.41M11.54 4.46l1.41-1.41" stroke="currentColor" stroke-width="1" stroke-linecap="round"/></svg>`,

    help: `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><circle cx="8" cy="8" r="6" stroke="currentColor" stroke-width="1.2" fill="none"/><path d="M6 6.5a2 2 0 1 1 2.5 1.9c-.5.2-.5.6-.5 1.1" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" fill="none"/><circle cx="8" cy="12" r="0.8" fill="currentColor"/></svg>`,

    check: `<svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M2 6l3 3 5-5" stroke="#4ec9b0" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>`,

    cross: `<svg width="12" height="12" viewBox="0 0 12 12" fill="none"><path d="M2 2l8 8M10 2l-8 8" stroke="#f14c4c" stroke-width="1.5" stroke-linecap="round"/></svg>`,

    pending: `<svg width="12" height="12" viewBox="0 0 12 12" fill="none"><circle cx="6" cy="6" r="4" stroke="#dcdcaa" stroke-width="1.2" fill="none"/><path d="M6 3v3l2 1.5" stroke="#dcdcaa" stroke-width="1" stroke-linecap="round"/></svg>`,

    user: `<svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="4.5" r="2.5" stroke="#007acc" stroke-width="1.2" fill="none"/><path d="M2 13c0-2.76 2.24-5 5-5s5 2.24 5 5" stroke="#007acc" stroke-width="1.2" fill="none"/></svg>`,

    terminal: `<svg width="14" height="14" viewBox="0 0 14 14" fill="none"><rect x="1" y="1" width="12" height="12" rx="2" stroke="currentColor" stroke-width="1.2" fill="none"/><path d="M4 5l2.5 2L4 9" stroke="#4ec9b0" stroke-width="1.2" stroke-linecap="round" stroke-linejoin="round"/><path d="M7.5 9H10" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/></svg>`,

    wrench: `<svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M10.5 1.5a3 3 0 0 0-4 4L8 7l-1 1-3.5-1.5a3 3 0 1 0 1 1L7 10l-3.5 3.5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round" fill="none"/></svg>`,
};

// File type icons by extension
const FileIcons = {
    '.java': { color: '#b07219', label: 'J' },
    '.py': { color: '#3572a5', label: 'P' },
    '.js': { color: '#f1e05a', label: 'JS' },
    '.ts': { color: '#3178c6', label: 'TS' },
    '.html': { color: '#e34c26', label: '<>' },
    '.css': { color: '#563d7c', label: '#' },
    '.json': { color: '#cbcb41', label: '{}' },
    '.xml': { color: '#f1502f', label: '<>' },
    '.yaml': { color: '#cb171e', label: 'Y' },
    '.yml': { color: '#cb171e', label: 'Y' },
    '.md': { color: '#083fa1', label: 'M' },
    '.sql': { color: '#e38c00', label: 'S' },
    '.sh': { color: '#89e051', label: '$_' },
    '.bash': { color: '#89e051', label: '$_' },
    '.go': { color: '#00add8', label: 'Go' },
    '.rs': { color: '#dea584', label: 'Rs' },
    '.c': { color: '#555555', label: 'C' },
    '.cpp': { color: '#f34b7d', label: 'C+' },
    '.h': { color: '#555555', label: 'H' },
    '.rb': { color: '#cc342d', label: 'Rb' },
    '.php': { color: '#4f5d95', label: 'Ph' },
    '.swift': { color: '#f05138', label: 'Sw' },
    '.kt': { color: '#a97bff', label: 'K' },
    '.txt': { color: '#6d8086', label: 'T' },
    '.log': { color: '#6d8086', label: 'L' },
    '.toml': { color: '#9c4221', label: 'T' },
    '.ini': { color: '#6d8086', label: 'I' },
    '.properties': { color: '#6d8086', label: 'P' },
    '.vue': { color: '#41b883', label: 'V' },
    '.svelte': { color: '#ff3e00', label: 'S' },
    '.gitignore': { color: '#6d8086', label: 'G' },
};

function getFileIconSvg(filename) {
    const ext = getExtension(filename).toLowerCase();
    const info = FileIcons[ext];
    if (info) {
        return `<svg width="16" height="16" viewBox="0 0 16 16" fill="none"><rect x="3" y="1" width="10" height="14" rx="1.5" fill="${info.color}" opacity="0.15" stroke="${info.color}" stroke-width="0.8"/><text x="8" y="10" text-anchor="middle" font-size="7" font-weight="600" fill="${info.color}" font-family="var(--font-mono)">${info.label}</text></svg>`;
    }
    return Icons.file;
}
