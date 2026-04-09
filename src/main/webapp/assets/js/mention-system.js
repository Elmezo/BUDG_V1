/**
 * MentionSystem - Handles @mentions in textareas
 * Uses /api/people/search to fetch users
 */
class MentionSystem {
    constructor(textarea, options = {}) {
        this.textarea = textarea;
        this.options = options;
        this.mentionTrigger = '@';
        this.isMenuOpen = false;
        this.menu = null;
        this.selectedIndex = 0;
        this.suggestions = [];
        this.cursorPos = 0;
        this.filterText = '';

        this.init();
    }

    init() {
        // Create suggestion menu safely
        this.menu = document.createElement('ul');
        this.menu.className = 'mention-suggestions-menu';
        this.menu.style.display = 'none';
        this.menu.style.position = 'absolute';
        this.menu.style.zIndex = '9999';
        this.menu.style.backgroundColor = 'var(--bg-surface, #fff)';
        this.menu.style.border = '1px solid var(--border-color, #ccc)';
        this.menu.style.borderRadius = '4px';
        this.menu.style.boxShadow = '0 4px 6px rgba(0,0,0,0.1)';
        this.menu.style.maxHeight = '200px';
        this.menu.style.overflowY = 'auto';
        this.menu.style.padding = '0';
        this.menu.style.margin = '0';
        this.menu.style.listStyle = 'none';
        this.menu.style.width = '200px';

        document.body.appendChild(this.menu); // Append to body to avoid positioning issues

        // Event listeners
        this.textarea.addEventListener('input', (e) => this.onInput(e));
        this.textarea.addEventListener('keydown', (e) => this.onKeyDown(e));
        this.textarea.addEventListener('blur', () => {
            setTimeout(() => this.closeMenu(), 200); // Delay to allow click
        });

        // Add basic CSS for menu items
        const style = document.createElement('style');
        style.textContent = `
            .mention-suggestions-menu li {
                padding: 8px 12px;
                cursor: pointer;
                font-family: inherit;
                font-size: 0.9rem;
                display: flex;
                align-items: center;
                gap: 8px;
            }
            .mention-suggestions-menu li:hover, .mention-suggestions-menu li.selected {
                background-color: var(--primary-light, #e3f2fd);
                color: var(--primary-color, #0d47a1);
            }
            .mention-avatar {
                width: 24px;
                height: 24px;
                border-radius: 50%;
                background-color: #ccc;
                display: flex;
                align-items: center;
                justify-content: center;
                font-size: 0.7rem;
                color: #fff;
            }
        `;
        document.head.appendChild(style);
    }

    onInput(e) {
        const text = this.textarea.value;
        this.cursorPos = this.textarea.selectionStart;

        // Find the text before cursor
        const textBeforeCursor = text.substring(0, this.cursorPos);

        // Find last @ before cursor
        const lastAt = textBeforeCursor.lastIndexOf(this.mentionTrigger);

        if (lastAt !== -1) {
            // Check if there are spaces between @ and cursor (allow spaces for names like "First Last")
            // But limit search length to avoid false positives (e.g. 20 chars)
            const textAfterAt = textBeforeCursor.substring(lastAt + 1);

            // Regex: match if we are typing a name (letters, spaces) but not newline
            if (/^[a-zA-Z0-9\s]{0,20}$/.test(textAfterAt)) {
                this.filterText = textAfterAt;
                this.fetchSuggestions(this.filterText);
                this.openMenu(lastAt);
                return;
            }
        }

        this.closeMenu();
    }

    onKeyDown(e) {
        if (!this.isMenuOpen) return;

        if (e.key === 'ArrowDown') {
            e.preventDefault();
            this.selectedIndex = (this.selectedIndex + 1) % this.suggestions.length;
            this.renderMenu();
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            this.selectedIndex = (this.selectedIndex - 1 + this.suggestions.length) % this.suggestions.length;
            this.renderMenu();
        } else if (e.key === 'Enter' || e.key === 'Tab') {
            e.preventDefault();
            if (this.suggestions.length > 0) {
                this.selectItem(this.suggestions[this.selectedIndex]);
            }
        } else if (e.key === 'Escape') {
            this.closeMenu();
        }
    }

    async fetchSuggestions(query) {
        if (!query) {
            this.suggestions = [];
            this.renderMenu();
            return;
        }

        try {
            // Updated endpoint to use /api/people/search?q=
            const response = await fetch(`/api/people/search?q=${encodeURIComponent(query)}`);
            const data = await response.json();

            if (data && data.success && Array.isArray(data.data)) {
                this.suggestions = data.data.slice(0, 5); // Limit to top 5
            } else {
                this.suggestions = [];
            }
            this.selectedIndex = 0;
            this.renderMenu();
        } catch (error) {
            console.error('Error fetching suggestions:', error);
        }
    }

    renderMenu() {
        this.menu.innerHTML = '';
        if (this.suggestions.length === 0) {
            this.closeMenu();
            return;
        }

        this.suggestions.forEach((user, index) => {
            const li = document.createElement('li');
            if (index === this.selectedIndex) {
                li.className = 'selected';
            }

            // Create avatar
            const avatar = document.createElement('div');
            avatar.className = 'mention-avatar';
            avatar.textContent = (user.first_name?.[0] || '') + (user.last_name?.[0] || '');
            avatar.style.backgroundColor = this.stringToColor(user.email || 'user');

            // Text
            const text = document.createElement('span');
            text.textContent = `${user.first_name} ${user.last_name}`;

            li.appendChild(avatar);
            li.appendChild(text);

            li.addEventListener('click', () => this.selectItem(user));
            this.menu.appendChild(li);
        });

        this.updateMenuPosition();
        this.menu.style.display = 'block';
    }

    updateMenuPosition() {
        const coords = this.getCaretCoordinates(this.textarea, this.textarea.selectionEnd);
        const rect = this.textarea.getBoundingClientRect();

        const top = rect.top + window.scrollY + coords.top + 20;
        const left = rect.left + window.scrollX + coords.left;

        this.menu.style.top = `${top}px`;
        this.menu.style.left = `${left}px`;
    }

    selectItem(user) {
        const text = this.textarea.value;
        const beforeAt = text.substring(0, text.lastIndexOf(this.mentionTrigger, this.cursorPos - 1));
        const afterCursor = text.substring(this.cursorPos);

        const insertion = `${this.mentionTrigger}${user.first_name} ${user.last_name} `;

        this.textarea.value = beforeAt + insertion + afterCursor;
        this.closeMenu();
        this.textarea.focus();

        // Move cursor to end of insertion
        const newPos = beforeAt.length + insertion.length;
        this.textarea.setSelectionRange(newPos, newPos);
    }

    openMenu(startIndex) {
        this.isMenuOpen = true;
    }

    closeMenu() {
        this.isMenuOpen = false;
        this.menu.style.display = 'none';
    }

    // Utility to get caret coordinates (simplified)
    getCaretCoordinates(element, position) {
        const div = document.createElement('div');
        const style = getComputedStyle(element);

        for (const prop of style) {
            div.style[prop] = style[prop];
        }

        div.style.position = 'absolute';
        div.style.visibility = 'hidden';
        div.style.whiteSpace = 'pre-wrap';
        div.textContent = element.value.substring(0, position);

        const span = document.createElement('span');
        span.textContent = element.value.substring(position) || '.';
        div.appendChild(span);

        document.body.appendChild(div);
        const coords = {
            top: span.offsetTop + parseInt(style.borderTopWidth),
            left: span.offsetLeft + parseInt(style.borderLeftWidth),
            height: parseInt(style.lineHeight)
        };
        document.body.removeChild(div);

        return coords;
    }

    stringToColor(str) {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            hash = str.charCodeAt(i) + ((hash << 5) - hash);
        }
        let color = '#';
        for (let i = 0; i < 3; i++) {
            let value = (hash >> (i * 8)) & 0xFF;
            color += ('00' + value.toString(16)).substr(-2);
        }
        return color;
    }
}

// Export globally if needed or just use via script tag
window.MentionSystem = MentionSystem;
