/**
 * AI Helper Library
 * Enables right-click context menu to get AI-powered descriptions and chat assistance
 */

(function(window, document) {
  'use strict';

  // Default configuration constants
  const DEFAULT_MAX_TEXT_LENGTH = 500;
  const DEFAULT_MAX_HTML_LENGTH = 1000;
  const NOTIFICATION_AUTO_CLOSE_MS = 10000;

  class AIHelper {
    constructor(options = {}) {
      this.options = {
        apiEndpoint: options.apiEndpoint || '/api/v1/tooltip',
        enableDescriptions: options.enableDescriptions !== false,
        enableChat: options.enableChat !== false,
        contextMenuId: 'ai-helper-context-menu',
        chatModalId: 'ai-helper-chat-modal',
        maxTextContentLength: options.maxTextContentLength || DEFAULT_MAX_TEXT_LENGTH,
        maxInnerHTMLLength: options.maxInnerHTMLLength || DEFAULT_MAX_HTML_LENGTH,
        notificationAutoCloseMs: options.notificationAutoCloseMs || NOTIFICATION_AUTO_CLOSE_MS,
        ...options
      };

      this.selectedElement = null;
      this.contextMenu = null;
      this.chatModal = null;
      this.eventHandlers = {
        contextMenu: null,
        click: null,
        keydown: null
      };
      this.init();
    }

    init() {
      this.createContextMenu();
      this.createChatModal();
      this.attachEventListeners();
    }

    createContextMenu() {
      if (document.getElementById(this.options.contextMenuId)) return;

      const menu = document.createElement('div');
      menu.id = this.options.contextMenuId;
      menu.className = 'ai-helper-context-menu';
      menu.style.display = 'none';

      const menuItems = [];

      if (this.options.enableDescriptions) {
        menuItems.push({
          text: 'Get AI Description',
          icon: '🤖',
          action: () => this.getElementDescription()
        });
      }

      if (this.options.enableChat) {
        menuItems.push({
          text: 'Open AI Chat',
          icon: '💬',
          action: () => this.openChatModal()
        });
      }

      menuItems.forEach(item => {
        const menuItem = document.createElement('div');
        menuItem.className = 'ai-helper-menu-item';
        menuItem.innerHTML = `<span class="ai-helper-icon">${item.icon}</span> ${item.text}`;
        menuItem.addEventListener('click', (e) => {
          e.stopPropagation();
          item.action();
          this.hideContextMenu();
        });
        menu.appendChild(menuItem);
      });

      document.body.appendChild(menu);
      this.contextMenu = menu;
    }

    createChatModal() {
      if (document.getElementById(this.options.chatModalId)) return;

      const modal = document.createElement('div');
      modal.id = this.options.chatModalId;
      modal.className = 'ai-helper-chat-modal';
      modal.style.display = 'none';

      modal.innerHTML = `
        <div class="ai-helper-modal-overlay"></div>
        <div class="ai-helper-modal-content">
          <div class="ai-helper-modal-header">
            <h3>AI Assistant</h3>
            <button class="ai-helper-close-btn" aria-label="Close">&times;</button>
          </div>
          <div class="ai-helper-chat-messages" id="ai-helper-chat-messages"></div>
          <div class="ai-helper-chat-input-container">
            <textarea 
              class="ai-helper-chat-input" 
              id="ai-helper-chat-input" 
              placeholder="Ask a question about this element..."
              rows="3"
            ></textarea>
            <button class="ai-helper-send-btn" id="ai-helper-send-btn">Send</button>
          </div>
        </div>
      `;

      document.body.appendChild(modal);
      this.chatModal = modal;

      // Attach modal-specific event listeners
      const closeBtn = modal.querySelector('.ai-helper-close-btn');
      const overlay = modal.querySelector('.ai-helper-modal-overlay');
      const sendBtn = modal.querySelector('#ai-helper-send-btn');
      const input = modal.querySelector('#ai-helper-chat-input');

      closeBtn.addEventListener('click', () => this.closeChatModal());
      overlay.addEventListener('click', () => this.closeChatModal());
      sendBtn.addEventListener('click', () => this.sendChatMessage());
      
      input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
          e.preventDefault();
          this.sendChatMessage();
        }
      });
    }

    attachEventListeners() {
      // Right-click event listener
      this.eventHandlers.contextMenu = (e) => {
        // Allow default context menu if AI Helper menu is disabled
        if (!this.options.enableDescriptions && !this.options.enableChat) {
          return;
        }

        // Check if we should show our menu (not on our own UI elements)
        if (e.target.closest('.ai-helper-context-menu') || 
            e.target.closest('.ai-helper-chat-modal')) {
          return;
        }

        e.preventDefault();
        this.selectedElement = e.target;
        this.showContextMenu(e.pageX, e.pageY);
      };

      // Click anywhere to hide context menu
      this.eventHandlers.click = () => {
        this.hideContextMenu();
      };

      // Escape key to close modal
      this.eventHandlers.keydown = (e) => {
        if (e.key === 'Escape') {
          this.closeChatModal();
        }
      };

      document.addEventListener('contextmenu', this.eventHandlers.contextMenu);
      document.addEventListener('click', this.eventHandlers.click);
      document.addEventListener('keydown', this.eventHandlers.keydown);
    }

    showContextMenu(x, y) {
      if (!this.contextMenu) return;

      this.contextMenu.style.display = 'block';
      this.contextMenu.style.left = x + 'px';
      this.contextMenu.style.top = y + 'px';

      // Adjust position if menu goes off-screen
      const rect = this.contextMenu.getBoundingClientRect();
      const windowWidth = window.innerWidth;
      const windowHeight = window.innerHeight;

      if (rect.right > windowWidth) {
        this.contextMenu.style.left = (x - rect.width) + 'px';
      }

      if (rect.bottom > windowHeight) {
        this.contextMenu.style.top = (y - rect.height) + 'px';
      }
    }

    hideContextMenu() {
      if (this.contextMenu) {
        this.contextMenu.style.display = 'none';
      }
    }

    async getElementDescription() {
      if (!this.selectedElement) return;

      const context = this.extractElementContext(this.selectedElement);
      
    const token = document.querySelector('meta[name="_csrf"]')?.content;

      try {
        const response = await fetch(`${this.options.apiEndpoint}/describe`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': token
          },
          body: JSON.stringify({
            context: context,
            timestamp: Date.now()
          })
        });

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();
        this.displayDescription(data.description || data.message);
      } catch (error) {
        console.error('Error fetching description:', error);
        this.displayDescription('Error: Unable to get AI description. ' + error.message);
      }
    }

    extractElementContext(element) {
      const context = {
        tagName: element.tagName,
        id: element.id,
        className: element.className,
        textContent: element.textContent?.substring(0, this.options.maxTextContentLength), // Limit text length
        attributes: {},
        innerHTML: element.innerHTML?.substring(0, this.options.maxInnerHTMLLength), // Limit HTML length
        path: this.getElementPath(element)
      };

      // Get relevant attributes
      const relevantAttrs = ['type', 'name', 'value', 'placeholder', 'href', 'src', 'alt', 'title', 'aria-label'];
      relevantAttrs.forEach(attr => {
        if (element.hasAttribute(attr)) {
          context.attributes[attr] = element.getAttribute(attr);
        }
      });

      return context;
    }

    getElementPath(element) {
      const path = [];
      let current = element;
      
      while (current && current !== document.body) {
        let selector = current.tagName.toLowerCase();
        if (current.id) {
          selector += '#' + current.id;
        } else if (current.className) {
          selector += '.' + current.className.split(' ').filter(c => c).join('.');
        }
        path.unshift(selector);
        current = current.parentElement;
      }
      
      return path.join(' > ');
    }

    displayDescription(description) {
      // Create a temporary notification
      const notification = document.createElement('div');
      notification.className = 'ai-helper-notification';
      notification.innerHTML = `
        <div class="ai-helper-notification-content">
          <strong>AI Description:</strong>
          <p>${this.escapeHtml(description)}</p>
          <button class="ai-helper-notification-close">Close</button>
        </div>
      `;

      document.body.appendChild(notification);

      const closeBtn = notification.querySelector('.ai-helper-notification-close');
      closeBtn.addEventListener('click', () => {
        notification.remove();
      });

      // Auto-remove after configured timeout
      setTimeout(() => {
        if (notification.parentElement) {
          notification.remove();
        }
      }, this.options.notificationAutoCloseMs);
    }

    openChatModal() {
      if (!this.chatModal) return;

      this.chatModal.style.display = 'block';
      
      // Add initial context message if an element was selected
      if (this.selectedElement) {
        const context = this.extractElementContext(this.selectedElement);
        // Escape potentially dangerous values
        const tagName = this.escapeHtml(context.tagName);
        const id = context.id ? `(#${this.escapeHtml(context.id)})` : '';
        this.addChatMessage(
          `I can help you understand this element: <strong>${tagName}</strong> ${id}`,
          'system'
        );
      }

      // Focus on input
      const input = this.chatModal.querySelector('#ai-helper-chat-input');
      if (input) input.focus();
    }

    closeChatModal() {
      if (!this.chatModal) return;
      this.chatModal.style.display = 'none';
    }

    addChatMessage(message, type = 'user') {
      const messagesContainer = document.getElementById('ai-helper-chat-messages');
      if (!messagesContainer) return;

      const messageDiv = document.createElement('div');
      messageDiv.className = `ai-helper-chat-message ai-helper-chat-message-${type}`;
      
      const messageContent = document.createElement('div');
      messageContent.className = 'ai-helper-message-content';
      messageContent.innerHTML = type === 'system' ? message : this.escapeHtml(message);
      
      messageDiv.appendChild(messageContent);
      messagesContainer.appendChild(messageDiv);

      // Scroll to bottom
      messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    async sendChatMessage() {
      const input = document.getElementById('ai-helper-chat-input');
      if (!input) return;

      const token = document.querySelector('meta[name="_csrf"]')?.content;

      const message = input.value.trim();
      if (!message) return;

      // Display user message
      this.addChatMessage(message, 'user');
      input.value = '';

      // Show loading indicator
      const loadingId = 'loading-' + Date.now();
      this.addChatMessage('Thinking...', 'assistant');
      const messagesContainer = document.getElementById('ai-helper-chat-messages');
      const loadingMessage = messagesContainer.lastChild;
      loadingMessage.id = loadingId;

      try {
        const context = this.selectedElement ? this.extractElementContext(this.selectedElement) : null;

        const response = await fetch(`${this.options.apiEndpoint}/chat`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-CSRF-TOKEN': token
          },
          body: JSON.stringify({
            message: message,
            context: context,
            timestamp: Date.now()
          })
        });

        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();
        
        // Remove loading message
        if (loadingMessage && loadingMessage.parentElement) {
          loadingMessage.remove();
        }

        // Display AI response
        this.addChatMessage(data.response || data.message, 'assistant');
      } catch (error) {
        console.error('Error sending chat message:', error);
        
        // Remove loading message
        if (loadingMessage && loadingMessage.parentElement) {
          loadingMessage.remove();
        }

        this.addChatMessage('Error: Unable to get response. ' + error.message, 'error');
      }
    }

    escapeHtml(text) {
      const div = document.createElement('div');
      div.textContent = text;
      return div.innerHTML;
    }

    destroy() {
      // Remove all created elements
      if (this.contextMenu && this.contextMenu.parentElement) {
        this.contextMenu.remove();
      }
      if (this.chatModal && this.chatModal.parentElement) {
        this.chatModal.remove();
      }
      
      // Remove event listeners
      if (this.eventHandlers.contextMenu) {
        document.removeEventListener('contextmenu', this.eventHandlers.contextMenu);
      }
      if (this.eventHandlers.click) {
        document.removeEventListener('click', this.eventHandlers.click);
      }
      if (this.eventHandlers.keydown) {
        document.removeEventListener('keydown', this.eventHandlers.keydown);
      }
      
      // Clear references
      this.contextMenu = null;
      this.chatModal = null;
      this.selectedElement = null;
      this.eventHandlers = {};
    }
  }

  // Expose to global scope
  window.AIHelper = AIHelper;

  // Auto-initialize if data-ai-helper-auto attribute is present
  if (document.currentScript && document.currentScript.hasAttribute('data-ai-helper-auto')) {
    const apiEndpoint = document.currentScript.getAttribute('data-ai-helper-endpoint') || '/api/ai-helper';
    window.addEventListener('DOMContentLoaded', () => {
      window.aiHelperInstance = new AIHelper({ apiEndpoint });
    });
  }

})(window, document);
