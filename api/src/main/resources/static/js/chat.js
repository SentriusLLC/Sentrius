// =========================
// Section: Globals & Setup
// =========================
const chatSessions = new Map(); // key: agentId, value: ChatSession

window.addEventListener("beforeunload", persistChatSessions);

// Restore on page load
(function restoreSessions() {
    const saved = localStorage.getItem("openChats");
    if (!saved) return;

    const chatData = JSON.parse(saved);
    for (const [agentId, data] of Object.entries(chatData)) {
        const session = new ChatSession(data.agentId, data.sessionId, data.agentHost, data.messages);
        chatSessions.set(agentId, session);
    }
})();

// =========================
// Section: ChatSession Class
// =========================
class ChatSession {
    constructor(agentId, sessionId, agentHost, preloadMessages = []) {
        this.agentId = agentId;
        this.sessionId = sessionId;
        this.agentHost = agentHost;
        this.chatGroupId = `${agentId}-${sessionId}`;
        this.messages = preloadMessages || [];
        this.connection = null;

        this.connect();
    }

    connect() {
        const protocol = location.protocol === "https:" ? "https" : "http";
        const uri = `${protocol}://${this.agentHost}/api/v1/chat/attach/subscribe?sessionId=${encodeURIComponent(this.sessionId)}&chatGroupId=${this.chatGroupId}`;
        this.connection = new SockJS(uri);
        this.connection.onmessage = (e) => this.handleMessage(e);
        this.connection.onopen = () => this.heartbeat();
    }

    handleMessage(e) {
        try {
            const binary = Uint8Array.from(atob(e.data), c => c.charCodeAt(0));
            const msg = proto.io.sentrius.protobuf.ChatMessage.deserializeBinary(binary);
            const sender = msg.getSender();
            const message = msg.getMessage();
            this.messages.push({ sender, message });

            const activeAgentId = document.getElementById("chat-container").dataset.agentId;
            if (activeAgentId === this.agentId) {
                appendToChatWindow(sender, message);
            }

            persistChatSessions();
        } catch (err) {
            console.error("Failed to handle chat message", err);
        }
    }

    send(text) {
        const msg = new proto.io.sentrius.protobuf.ChatMessage();
        msg.setSender("user");
        msg.setMessage(text);

        this.connection.send(btoa(String.fromCharCode(...msg.serializeBinary())));
        this.messages.push({ sender: "You", message: text });

        const activeAgentId = document.getElementById("chat-container").dataset.agentId;
        if (activeAgentId === this.agentId) {
            appendToChatWindow("You", text);
        }

        persistChatSessions();
    }

    heartbeat() {
        if (!this.connection || this.connection.readyState !== 1) return;

        const msg = new proto.io.sentrius.protobuf.ChatMessage();
        msg.setSender("system");
        msg.setMessage("heartbeat");

        this.connection.send(btoa(String.fromCharCode(...msg.serializeBinary())));
        setTimeout(() => this.heartbeat(), 5000);
    }

    toJSON() {
        return {
            agentId: this.agentId,
            sessionId: this.sessionId,
            agentHost: this.agentHost,
            messages: this.messages
        };
    }
}

// =========================
// Section: UI Interaction
// =========================

function switchToAgent(agentId, sessionId, agentHost) {
    let session = chatSessions.get(agentId);
    if (!session) {
        session = new ChatSession(agentId, sessionId, agentHost);
        chatSessions.set(agentId, session);
    }

    const container = document.getElementById("chat-container");
    container.dataset.agentId = agentId;
    container.dataset.agentHost = agentHost;
    document.getElementById("chat-agent-name").textContent = agentId;

    const messagesDiv = document.getElementById("chat-messages");
    messagesDiv.innerHTML = "";
    session.messages.forEach(msg => {
        appendToChatWindow(msg.sender, msg.message);
    });

    container.style.display = "block";
}

function sendMessage(event) {
    if (event.key !== "Enter") return;

    const input = document.getElementById("chat-input");
    const messageText = input.value.trim();
    if (!messageText) return;

    const container = document.getElementById("chat-container");
    const agentId = container.dataset.agentId;
    const session = chatSessions.get(agentId);
    if (session) {
        session.send(messageText);
    }

    input.value = "";
}

function appendToChatWindow(sender, message) {
    const chatBox = document.getElementById("chat-messages");
    const div = document.createElement("div");
    div.classList.add("chat-message");
    div.innerHTML = `<strong>${sender}:</strong> ${message}`;
    chatBox.appendChild(div);
    chatBox.scrollTop = chatBox.scrollHeight;
}

function toggleChat() {
    const container = document.getElementById("chat-container");
    container.classList.toggle("hidden");
}

// =========================
// Section: Persistence
// =========================
function persistChatSessions() {
    const obj = {};
    chatSessions.forEach((session, agentId) => {
        obj[agentId] = session.toJSON();
    });
    localStorage.setItem("openChats", JSON.stringify(obj));
}
