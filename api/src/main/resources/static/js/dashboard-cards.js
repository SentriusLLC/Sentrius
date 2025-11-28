// Dashboard cards section data loading
document.addEventListener('DOMContentLoaded', function () {
    // Load embedded agents
    loadEmbeddedAgents();
    
    // Load security metrics
    loadSecurityMetrics();
    
    // Load system health
    loadSystemHealth();
    
    // Load Zero Trust data
    loadZeroTrustData();
    
    // Refresh data periodically
    setInterval(loadSecurityMetrics, 30000); // Every 30 seconds
    setInterval(loadSystemHealth, 60000); // Every 60 seconds
    setInterval(loadZeroTrustData, 30000); // Every 30 seconds
});

async function loadZeroTrustData() {
    try {
        // Fetch rules count
        const rulesResponse = await fetch('/api/v1/zerotrust/rules/list');
        if (rulesResponse.ok) {
            const rules = await rulesResponse.json();
            const rulesElement = document.getElementById('zt-system-rules');
            if (rulesElement) rulesElement.textContent = rules.length || 0;
        }
        
        // Fetch open connections count
        const sessionsResponse = await fetch('/api/v1/sessions/list');
        if (sessionsResponse.ok) {
            const sessions = await sessionsResponse.json();
            const connectionsElement = document.getElementById('zt-open-connections');
            if (connectionsElement) connectionsElement.textContent = sessions.length || 0;
        }
    } catch (error) {
        console.error('Error loading Zero Trust data:', error);
    }
}

async function loadEmbeddedAgents() {
    try {
        const response = await fetch('/api/v1/agent/embedded');
        if (!response.ok) {
            throw new Error('Failed to fetch embedded agents');
        }
        const agents = await response.json();
        
        const listContainer = document.getElementById('embedded-agents-list');
        const activeCount = document.getElementById('active-agents-count');
        const totalCount = document.getElementById('total-agents-count');
        
        if (!listContainer) return;
        
        let activeAgents = 0;
        let html = '';
        
        // Helper function to escape HTML to prevent XSS
        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
        
        agents.forEach(agent => {
            const statusClass = agent.status === 'active' ? 'text-success' : 'text-warning';
            const statusIcon = agent.status === 'active' ? 'fa-check-circle' : 'fa-exclamation-circle';
            
            if (agent.status === 'active') activeAgents++;
            
            // Escape user-controllable data to prevent XSS
            const safeName = escapeHtml(agent.name || '');
            const safeDescription = escapeHtml(agent.description || '');
            const safeIcon = escapeHtml(agent.icon || 'fa-robot');
            
            html += `
                <div class="agent-item d-flex justify-content-between align-items-center py-2 border-bottom border-secondary">
                    <div class="d-flex align-items-center">
                        <i class="fas ${safeIcon} me-2 text-primary" style="width: 20px;"></i>
                        <div>
                            <div class="fw-bold" style="font-size: 0.9rem;">${safeName}</div>
                            <div class="text-muted" style="font-size: 0.7rem;">${safeDescription}</div>
                        </div>
                    </div>
                    <i class="fas ${statusIcon} ${statusClass}"></i>
                </div>
            `;
        });
        
        listContainer.innerHTML = html;
        if (activeCount) activeCount.textContent = activeAgents;
        if (totalCount) totalCount.textContent = agents.length;
        
    } catch (error) {
        console.error('Error loading embedded agents:', error);
        const listContainer = document.getElementById('embedded-agents-list');
        if (listContainer) {
            listContainer.innerHTML = '<div class="text-center text-muted py-3">Unable to load agents</div>';
        }
    }
}

async function loadSecurityMetrics() {
    try {
        const response = await fetch('/api/v1/agent/security-metrics');
        if (!response.ok) {
            throw new Error('Failed to fetch security metrics');
        }
        const metrics = await response.json();
        
        const activeSessions = document.getElementById('metric-active-sessions');
        const trustScore = document.getElementById('metric-trust-score');
        const ztatTokens = document.getElementById('metric-ztat-tokens');
        const policyViolations = document.getElementById('metric-policy-violations');
        
        if (activeSessions) activeSessions.textContent = metrics.activeSessions || 0;
        if (trustScore) trustScore.textContent = (metrics.averageTrustScore || 0) + '%';
        if (ztatTokens) ztatTokens.textContent = metrics.ztatTokensIssued || 0;
        if (policyViolations) {
            policyViolations.textContent = metrics.policyViolations || 0;
            policyViolations.style.color = metrics.policyViolations > 0 ? '#ff4444' : '#82ca9d';
        }
        
    } catch (error) {
        console.error('Error loading security metrics:', error);
    }
}

async function loadSystemHealth() {
    try {
        const response = await fetch('/api/v1/agent/system-health');
        if (!response.ok) {
            throw new Error('Failed to fetch system health');
        }
        const health = await response.json();
        
        const overallStatus = document.getElementById('system-overall-status');
        const lastCheck = document.getElementById('health-last-check');
        const componentsList = document.getElementById('system-components-list');
        
        // Helper function to escape HTML to prevent XSS
        function escapeHtml(text) {
            const div = document.createElement('div');
            div.textContent = text;
            return div.innerHTML;
        }
        
        // Update overall status
        if (overallStatus) {
            if (health.status === 'healthy') {
                overallStatus.className = 'badge bg-success fs-6 px-3 py-2';
                overallStatus.innerHTML = '<i class="fas fa-check-circle me-1"></i> All Systems Operational';
            } else {
                overallStatus.className = 'badge bg-warning fs-6 px-3 py-2';
                overallStatus.innerHTML = '<i class="fas fa-exclamation-triangle me-1"></i> Some Issues Detected';
            }
        }
        
        // Update last check time
        if (lastCheck && health.lastCheck) {
            const checkTime = new Date(health.lastCheck);
            lastCheck.textContent = checkTime.toLocaleTimeString();
        }
        
        // Update components list
        if (componentsList && health.components) {
            let html = '';
            health.components.forEach((component, index) => {
                const isLast = index === health.components.length - 1;
                const borderClass = isLast ? '' : 'border-bottom border-secondary';
                const statusClass = component.status === 'healthy' ? 'bg-success' : 'bg-warning';
                const statusText = component.status === 'healthy' ? 'Healthy' : 'Warning';
                
                const icons = {
                    'API Server': 'fa-server',
                    'Database': 'fa-database',
                    'Authentication': 'fa-key',
                    'SSH Proxy': 'fa-terminal',
                    'RDP Proxy': 'fa-desktop'
                };
                const icon = icons[component.name] || 'fa-circle';
                const safeName = escapeHtml(component.name || '');
                
                html += `
                    <div class="d-flex justify-content-between align-items-center py-2 ${borderClass}">
                        <span><i class="fas ${icon} me-2 text-info"></i>${safeName}</span>
                        <span class="badge ${statusClass}">${statusText}</span>
                    </div>
                `;
            });
            componentsList.innerHTML = html;
        }
        
    } catch (error) {
        console.error('Error loading system health:', error);
    }
}
