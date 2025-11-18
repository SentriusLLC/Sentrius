/**
 * Script to fetch and display agents in the Host Enclaves card
 */

document.addEventListener('DOMContentLoaded', function () {
    loadAgents();
    
    // Refresh every 30 seconds
    setInterval(loadAgents, 30000);
});

async function loadAgents() {
    try {
        const response = await fetch('/api/v1/agent/list');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        const agents = await response.json();
        
        const agentList = document.getElementById('agent-list');
        const agentCountBadge = document.getElementById('agent-count-badge');
        
        if (!agentList || !agentCountBadge) {
            return;
        }
        
        agentCountBadge.textContent = agents.length;
        
        if (agents.length === 0) {
            agentList.innerHTML = '<p style="color: #888; margin: 0; text-align: center;">No agents running</p>';
            return;
        }
        
        let html = '<div style="display: flex; flex-direction: column; gap: 8px;">';
        
        agents.forEach(agent => {
            const statusColor = agent.isRegistered ? '#82ca9d' : '#ffc658';
            const statusIcon = agent.isRegistered ? 'check-circle' : 'clock';
            const agentEnabled = agent.isRegistered; // Assuming registered agents are enabled
            
            html += `
                <div style="display: flex; justify-content: space-between; align-items: center; padding: 8px; background-color: #2d2d2d; border-radius: 4px; border-left: 3px solid ${statusColor};">
                    <div style="display: flex; align-items: center; gap: 10px; flex: 1;">
                        <i class="fas fa-${statusIcon}" style="color: ${statusColor};"></i>
                        <div style="flex: 1;">
                            <span style="color: #FFFFFF; font-weight: 500;">${escapeHtml(agent.agentName || agent.agentId)}</span>
                            <div style="color: #888; font-size: 0.85em;">${agent.lastHeartbeat || 'Unknown'}</div>
                        </div>
                    </div>
                    <div style="display: flex; gap: 8px; align-items: center;">
                        <div class="form-check form-switch" style="margin: 0;">
                            <input class="form-check-input" type="checkbox" ${agentEnabled ? 'checked' : ''} 
                                   onchange="toggleAgentStatus('${escapeHtml(agent.agentId)}', this.checked)" 
                                   title="${agentEnabled ? 'Disable' : 'Enable'} Agent">
                        </div>
                        <button class="btn btn-sm btn-outline-danger" onclick="terminateAgent('${escapeHtml(agent.agentId)}', '${escapeHtml(agent.agentName)}')" title="Terminate Agent">
                            <i class="fas fa-stop"></i>
                        </button>
                    </div>
                </div>
            `;
        });
        
        html += '</div>';
        agentList.innerHTML = html;
        
    } catch (error) {
        console.error('Failed to load agents:', error);
        const agentList = document.getElementById('agent-list');
        if (agentList) {
            agentList.innerHTML = '<p style="color: #ff6b6b; margin: 0; text-align: center;">Failed to load agents</p>';
        }
    }
}

function terminateAgent(agentId, agentName) {
    if (!confirm(`Are you sure you want to terminate agent ${agentName || agentId}?`)) {
        return;
    }
    
    console.log('Terminating agent:', agentId);
    alert('Agent termination is managed through the agent launcher service.\nUse the agent launcher UI to terminate agents.');
}

function toggleAgentStatus(agentId, enabled) {
    console.log(`${enabled ? 'Enabling' : 'Disabling'} agent:`, agentId);
    
    alert(`Agent ${enabled ? 'enable' : 'disable'} is managed through pod lifecycle.\nUse the agent launcher service to control agent status.`);
    
    setTimeout(() => loadAgents(), 100);
}

function escapeHtml(text) {
    if (!text) return '';
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#039;'
    };
    return text.replace(/[&<>"']/g, m => map[m]);
}
