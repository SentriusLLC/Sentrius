/**
 * Script to fetch and display agents, NPEs, and MCP servers in the Host Enclaves card
 */

document.addEventListener('DOMContentLoaded', function () {
    loadAgents();
    loadMCPServers();
    
    // Refresh every 30 seconds
    setInterval(loadAgents, 30000);
    setInterval(loadMCPServers, 30000);
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

async function loadMCPServers() {
    try {
        // Load both GitHub and Coding MCP servers
        const [githubResponse, codingResponse] = await Promise.all([
            fetch('/api/v1/github/mcp/list').catch(() => ({ ok: false })),
            fetch('/api/v1/coding/mcp/list').catch(() => ({ ok: false }))
        ]);
        
        const mcpList = document.getElementById('mcp-list');
        const mcpCountBadge = document.getElementById('mcp-count-badge');
        
        if (!mcpList || !mcpCountBadge) {
            return;
        }
        
        let allServers = [];
        
        if (githubResponse.ok) {
            const githubData = await githubResponse.json();
            if (githubData.servers && Array.isArray(githubData.servers)) {
                allServers = allServers.concat(
                    githubData.servers.map(s => ({ 
                        ...s, 
                        type: 'GitHub',
                        instanceId: s.tokenId || s.podName || 'Unknown'
                    }))
                );
            }
        }
        
        if (codingResponse.ok) {
            const codingData = await codingResponse.json();
            if (codingData.servers && Array.isArray(codingData.servers)) {
                allServers = allServers.concat(
                    codingData.servers.map(s => ({ 
                        ...s, 
                        type: 'Coding',
                        instanceId: s.instanceId || s.podName || 'Unknown'
                    }))
                );
            }
        }
        
        mcpCountBadge.textContent = allServers.length;
        
        if (allServers.length === 0) {
            mcpList.innerHTML = '<p style="color: #888; margin: 0; text-align: center;">No MCP servers running</p>';
            return;
        }
        
        let html = '<div style="display: flex; flex-direction: column; gap: 8px;">';
        
        allServers.forEach(server => {
            const isRunning = server.status && server.status.toLowerCase() === 'running';
            const statusColor = isRunning ? '#82ca9d' : '#ffc658';
            const statusIcon = isRunning ? 'play-circle' : 'pause-circle';
            const typeColor = server.type === 'GitHub' ? '#4078c0' : '#8884d8';
            
            html += `
                <div style="display: flex; justify-content: space-between; align-items: center; padding: 8px; background-color: #2d2d2d; border-radius: 4px; border-left: 3px solid ${statusColor};">
                    <div style="display: flex; align-items: center; gap: 10px; flex: 1;">
                        <i class="fas fa-${statusIcon}" style="color: ${statusColor};"></i>
                        <div style="flex: 1;">
                            <div style="color: #FFFFFF; font-weight: 500;">${escapeHtml(server.instanceId)}</div>
                            <div style="color: ${typeColor}; font-size: 0.8em;">${server.type} MCP - ${server.status || 'Unknown'}</div>
                        </div>
                    </div>
                    <div style="display: flex; gap: 8px; align-items: center;">
                        <div class="form-check form-switch" style="margin: 0;">
                            <input class="form-check-input" type="checkbox" ${isRunning ? 'checked' : ''} 
                                   onchange="toggleMCPStatus('${escapeHtml(server.type)}', '${escapeHtml(server.instanceId)}', this.checked)" 
                                   title="${isRunning ? 'Stop' : 'Start'} MCP Server">
                        </div>
                        <button class="btn btn-sm btn-outline-danger" onclick="terminateMCPServer('${escapeHtml(server.type)}', '${escapeHtml(server.instanceId)}')" title="Delete MCP Server">
                            <i class="fas fa-trash"></i>
                        </button>
                    </div>
                </div>
            `;
        });
        
        html += '</div>';
        mcpList.innerHTML = html;
        
    } catch (error) {
        console.error('Failed to load MCP servers:', error);
        const mcpList = document.getElementById('mcp-list');
        if (mcpList) {
            mcpList.innerHTML = '<p style="color: #ff6b6b; margin: 0; text-align: center;">Failed to load MCP servers</p>';
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

function terminateMCPServer(type, instanceId) {
    if (!confirm(`Are you sure you want to delete the ${type} MCP server ${instanceId}?`)) {
        return;
    }
    
    const endpoint = type === 'GitHub' 
        ? `/api/v1/github/mcp/delete?tokenId=${encodeURIComponent(instanceId)}`
        : `/api/v1/coding/mcp/delete?instanceId=${encodeURIComponent(instanceId)}`;
    
    const csrfToken = document.getElementById('csrf-token') ? document.getElementById('csrf-token').value : '';
    
    fetch(endpoint, {
        method: 'DELETE',
        headers: {
            'X-CSRF-TOKEN': csrfToken
        }
    })
    .then(response => {
        if (response.ok) {
            alert('MCP server deleted successfully');
            loadMCPServers(); // Reload the list
        } else {
            alert('Failed to delete MCP server');
        }
    })
    .catch(error => {
        console.error('Error deleting MCP server:', error);
        alert('Error deleting MCP server');
    });
}

function toggleMCPStatus(type, instanceId, enabled) {
    console.log(`${enabled ? 'Starting' : 'Stopping'} ${type} MCP server:`, instanceId);
    
    if (!enabled) {
        if (confirm(`Stop and delete the ${type} MCP server ${instanceId}?`)) {
            terminateMCPServer(type, instanceId);
        } else {
            setTimeout(() => loadMCPServers(), 100);
        }
    } else {
        alert(`To start a ${type} MCP server, please use the "Launch Agent" button and select the ${type} MCP option.`);
        setTimeout(() => loadMCPServers(), 100);
    }
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
