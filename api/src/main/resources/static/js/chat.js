// =========================
// Section: Globals & Setup
// =========================
const chatSessions = new Map(); // key: agentId, value: ChatSession

//window.addEventListener("beforeunload", persistChatSessions);

window.addEventListener("beforeunload", () => {
    chatSessions.forEach(session => session.endChat());
});

// Restore on page load
(function restoreSesions() {
    const saved = localStorage.getItem("openChats");
    if (!saved) return;

    /*const chatData = JSON.parse(saved);
    for (const [agentId, data] of Object.entries(chatData)) {
        const session = new ChatSession(data.agentName, data.agentId, data.sessionId, data.agentHost, data.messages);
        chatSessions.set(agentId, session);
    }
    */

})();

// =========================
// Section: ChatSession Class
// =========================
class ChatSession {
    constructor(agentName, agentId, sessionId, agentHost, preloadMessages = []) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.sessionId = sessionId;
        this.agentHost = agentHost;
        this.userId = document.querySelector('meta[name="sentrius-user-id"]')?.content;
        this.chatGroupId = `${agentId}-${sessionId}`;
        this.messages = preloadMessages || [];
        this.connection = null;
    }
    

    async connect() {

        const config = await fetch("/api/v1/chat/config").then(r => r.json());

        console.log("Connecting to chat server at:", this.agentHost);
        const protocol = location.protocol === "https:" ? "wss" : "ws";
        const phost = this.agentHost.replace(/^(https?:\/\/)?/, `${protocol}://`);
        console.log("Connecting to chat server at:", phost);
        // Step 1: Generate ephemeral keypair
        const keyPair = await window.crypto.subtle.generateKey(
            { name: "ECDSA", namedCurve: "P-256" },
            true,
            ["sign", "verify"]
        );
        this.ephemeralKeyPair = keyPair;

        const exportedPublicKey = await window.crypto.subtle.exportKey("spki", keyPair.publicKey);
        const publicKeyBase64 = btoa(String.fromCharCode(...new Uint8Array(exportedPublicKey)));

        // Step 2: Request ZTAT token from backend
        const csrfToken = document.getElementById("csrf-token").value;

        const ztatResponse = await fetch("/api/v1/zerotrust/accesstoken/jwt/issue", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "X-CSRF-TOKEN": csrfToken
            },
            credentials: "same-origin", // Required to include the cookie
            body: JSON.stringify({
                sessionId: this.sessionId,
                agentId: this.agentId,
                publicKey: publicKeyBase64
            })
        });

        if (!ztatResponse.ok) {
            console.error("Failed to retrieve ZTAT");
            return;
        }

        const { jwt } = await ztatResponse.json();

        let ztatForChat = {};
        try {
            const response = await fetch(
                "/api/v1/agent/connect?session_id=" + this.sessionId + "&userId=" + this.userId,
                {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        "X-CSRF-TOKEN": csrfToken,
                        "ZTAT_TOKEN": jwt,
                    },
                    credentials: "same-origin"
                }
            );

            if (!response.ok) {
                const body = await response.text();
                throw new Error(`Request failed: ${response.status} ${response.statusText}\n${body}`);
            }

            ztatForChat = await response.json(); // or .text() depending on response
            console.log("ZTAT received:", ztatForChat);
            if (!ztatForChat.ztat_token) {
                console.error("Failed to retrieve ZTAT");
                return;
            }


        } catch (error) {
            console.error("Failed to fetch ZTAT:", error);
        }




        // Step 3: Open WebSocket with ZTAT token
        //const uri = `${phost}/api/v1/chat/attach/subscribe?sessionId=${encodeURIComponent(this.sessionId)}&chatGroupId=${this.chatGroupId}&ztat=${encodeURIComponent(jwt)}`;
        //const uri = `/api/v1/agents/ws/${encodeURIComponent(phost)}/${encodeURIComponent(this.sessionId)}/${encodeURIComponent(this.chatGroupId)}/${encodeURIComponent(jwt)}`;

        const uri = config.agentProxyWsUrl + `/api/v1/agents/ws?phost=${encodeURIComponent(phost)}&sessionId=${encodeURIComponent(this.sessionId)}&userId=${encodeURIComponent(this.userId)}&chatGroupId=${encodeURIComponent(this.chatGroupId)}&ztat=${encodeURIComponent(jwt)}&jwt=${encodeURIComponent(ztatForChat.ztat_token)}`;

        console.log("Connecting to chat server with ZTAT at:", uri);
        this.connection = new WebSocket(uri);

        this.connection.onmessage = async (e) => {
            try {
                console.log("got message:", e.data);
                const binary = Uint8Array.from(atob(e.data), c => c.charCodeAt(0));
                const chatMsg = proto.io.sentrius.protobuf.ChatMessage.deserializeBinary(binary);

                const sender = chatMsg.getSender();
                const rawMessage = chatMsg.getMessage();

                let parsed;
                try {
                    console.log("Parsing message:", rawMessage);
                    parsed = JSON.parse(rawMessage);
                } catch {
                    console.warn("Received non-JSON message, treating as raw text");
                    parsed = { type: "user-message", message: rawMessage };
                }

                if (parsed.type === "challenge") {
                    const encoder = new TextEncoder();
                    const nonceData = encoder.encode(parsed.nonce);

                    const signature = await window.crypto.subtle.sign(
                        { name: "ECDSA", hash: "SHA-256" },
                        this.ephemeralKeyPair.privateKey,
                        nonceData
                    );

                    const signatureBase64 = btoa(String.fromCharCode(...new Uint8Array(signature)));

                    const responseMsg = new proto.io.sentrius.protobuf.ChatMessage();
                    responseMsg.setSender("user");
                    responseMsg.setMessage(JSON.stringify({
                        type: "challenge-response",
                        signature: signatureBase64,
                        publicKey: publicKeyBase64
                    }));

                    this.connection.send(btoa(String.fromCharCode(...responseMsg.serializeBinary())));
                    return;
                }

                // Display user-message type
                if (parsed.type === "user-message") {
                    this.messages.push({ sender, message: parsed.message });

                    const activeAgentId = document.getElementById("chat-container").dataset.agentId;
                    if (activeAgentId === this.agentId) {
                        appendToChatWindow(sender, parsed.message);
                    }

                    persistChatSessions();
                }

            } catch (err) {
                console.error("Failed to handle protobuf chat message", err);
            }
        };


        this.connection.onopen = () => this.heartbeat();
    }

    endChat() {
    if (!this.connection || this.connection.readyState !== WebSocket.OPEN) {
        return;
    }

    const msg = new proto.io.sentrius.protobuf.ChatMessage();
    msg.setSender("user");
    msg.setMessage(JSON.stringify({
        type: "end-chat"
    }));

    this.connection.send(
        btoa(String.fromCharCode(...msg.serializeBinary()))
    );

    // Optional: close transport AFTER intent is sent
    this.connection.close();
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
        msg.setMessage(JSON.stringify({
            type: "user-message",
            message: text
        }));

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

export function switchToAgent(agentName,agentId, sessionId, agentHost) {
    console.log("Switching to agent:", agentName, sessionId, agentHost);
    let session = chatSessions.get(agentId);
    if (!session) {
        console.log("New session creating:");
        session = new ChatSession(agentName, agentId, sessionId,  agentHost);
        session.connect().then(() => {
            console.log("Connected to chat server");
        });
        console.log("New session created:", session);

        chatSessions.set(agentId, session);
    }

    const container = document.getElementById("chat-container");
    container.dataset.agentId = agentId;
    container.dataset.agentName = agentName;
    container.dataset.agentHost = agentHost;
    document.getElementById("chat-agent-name").textContent = agentName;

    const messagesDiv = document.getElementById("chat-messages");
    messagesDiv.innerHTML = "";
    session.messages.forEach(msg => {
        appendToChatWindow(msg.sender, msg.message);
    });

    // Show container with animation
    container.style.display = "flex";
    setTimeout(() => {
        container.classList.remove("hidden");
    }, 10);
    
    console.log("Chat container displayed");
}

export function sendMessage(event) {

    if (event && event.key && event.key !== 'Enter') return;

    console.log("Send message event:", event);

    const input = document.getElementById("chat-input");
    const messageText = input.value.trim();
    if (!messageText) {
        console.log("Empty message, ignoring.");
        return;
    }

    const container = document.getElementById("chat-container");
    const agentId = container.dataset.agentId;
    const session = chatSessions.get(agentId);
    if (session) {
        session.send(messageText);
    } else {
        console.error("No active chat session found for agent:", agentId);
        return;
    }

    input.value = "";
}

export function appendToChatWindow(sender, message) {
    const chatBox = document.getElementById("chat-messages");
    const div = document.createElement("div");
    div.classList.add("chat-message");
    
    // Determine if message is from user or agent
    const isUser = sender === "You" || sender === "user";
    div.classList.add(isUser ? "user" : "agent");
    
    // Create message content with modern styling
    const messageContent = document.createElement("div");
    messageContent.classList.add("chat-message-content");
    messageContent.textContent = message;
    
    // Add sender name (smaller, less prominent)
    const senderElement = document.createElement("strong");
    senderElement.textContent = sender;
    
    div.appendChild(senderElement);
    div.appendChild(messageContent);
    
    chatBox.appendChild(div);
    chatBox.scrollTop = chatBox.scrollHeight;
}

export function toggleChat() {
    const container = document.getElementById("chat-container");
    
    if (container.classList.contains("hidden")) {
        container.style.display = "flex";
        // Small delay to ensure display change is processed
        setTimeout(() => {
            container.classList.remove("hidden");
        }, 10);
        
        // Focus input when opening
        setTimeout(() => {
            const input = document.getElementById("chat-input");
            if (input) input.focus();
        }, 300);
    } else {
        container.classList.add("hidden");
        // Hide after animation completes
        setTimeout(() => {
            container.style.display = "none";
        }, 300);
    }
}

// =========================
// Section: Persistence
// =========================
export function persistChatSessions() {
    const obj = {};
    chatSessions.forEach((session, agentId) => {
        obj[agentId] = session.toJSON();
    });
    localStorage.setItem("openChats", JSON.stringify(obj));
}


// Fetches the list of available agents from the chat API
export async function fetchAvailableAgents() {
    try {
        console.log("Fetching available agents...");
        const response = await fetch("/api/v1/chat/agent/list");
        if (!response.ok) {
            throw new Error(`Failed to fetch agents: ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error("Error fetching available agents:", error);
        return [];
    }
}

window.sendMessage = sendMessage;
window.toggleChat = toggleChat;
window.fetchAvailableAgents = fetchAvailableAgents;
window.chatSessions = chatSessions;
window.ChatSession = ChatSession;