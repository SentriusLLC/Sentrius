// api/src/main/resources/static/js/dashboard-agents.js
import { fetchAvailableAgents, switchToAgent } from './chat.js';

document.addEventListener('DOMContentLoaded', function () {
    console.log("Fetching agents 1...");
    const agentSelect = document.getElementById('agent-select');
    const startChatBtn = document.getElementById('start-chat-btn');

    if (!agentSelect || !startChatBtn) {
        console.error('Agent select or start chat button not found in DOM');
        return;
    }

    console.log("Fetching agents 2...");
    fetchAvailableAgents().then(agents => {
        agents.forEach(agent => {
            const option = document.createElement('option');
            option.value = agent.agentName;
            option.textContent = agent.agentName || agent.agentId;
            option.dataset.agentHost = agent.agentCallback;
            option.dataset.agentId = agent.agentId;
            agentSelect.appendChild(option);
        });
    });

    agentSelect.addEventListener('change', function () {
        startChatBtn.disabled = !agentSelect.value;
    });

    startChatBtn.addEventListener('click', function () {
        const selected = agentSelect.options[agentSelect.selectedIndex];
        if (selected && selected.value) {
            console.log("Switching to agent: ", selected.value);
            const agentName = selected.value;
            const agentHost = selected.dataset.agentHost;
            const agentId = selected.dataset.agentId;
            const sessionId = Date.now().toString();
            switchToAgent(agentName, agentId, sessionId, agentHost);
        }
    });
});