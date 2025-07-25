import { fetchAvailableAgents, switchToAgent } from './chat.js';

document.addEventListener('DOMContentLoaded', function () {
    const agentSelect = document.getElementById('agent-select');
    const startChatBtn = document.getElementById('start-chat-btn');

    if (!agentSelect || !startChatBtn) {
        console.error('Agent select or start chat button not found in DOM');
        return;
    }

    function updateAgentList() {
        console.log('Updating agent list...');
        fetchAvailableAgents().then(agents => {
            const existingOptions = Array.from(agentSelect.options).reduce((map, opt) => {
                map.set(opt.dataset.agentId, opt);
                return map;
            }, new Map());

            const currentAgentIds = new Set(agents.map(a => a.agentId));

            // Add new agents
            agents.forEach(agent => {
                if (!existingOptions.has(agent.agentId)) {
                    const option = document.createElement('option');
                    option.value = agent.agentName;
                    option.textContent = agent.agentName || agent.agentId;
                    option.dataset.agentHost = agent.agentCallback;
                    option.dataset.agentId = agent.agentId;
                    agentSelect.appendChild(option);
                }
            });

            // Remove stale agents
            for (let [agentId, option] of existingOptions.entries()) {
                if (!currentAgentIds.has(agentId)) {
                    agentSelect.removeChild(option);
                }
            }

            startChatBtn.disabled = !agentSelect.value;
        }).catch(err => {
            console.error('Failed to update agent list:', err);
        });
    }

    agentSelect.addEventListener('change', function () {
        startChatBtn.disabled = !agentSelect.value;
    });

    startChatBtn.addEventListener('click', function () {
        const selected = agentSelect.options[agentSelect.selectedIndex];
        if (selected && selected.value) {
            const agentName = selected.value;
            const agentHost = selected.dataset.agentHost;
            const agentId = selected.dataset.agentId;
            const sessionId = crypto.randomUUID();
            switchToAgent(agentName, agentId, sessionId, agentHost);
        }
    });

    // Initial load
    updateAgentList();

    // Refresh every 10 seconds
    setInterval(updateAgentList, 10000);
});
