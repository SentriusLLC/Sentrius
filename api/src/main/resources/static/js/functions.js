export function countUsers(){
    fetch('/api/v1/users/list')
        .then(response => response.json())
        .then(data => {
            if (null != data) {
                if (document.getElementById('total-user-count'))
                document.getElementById('total-user-count').innerHTML = data.length;
            }
        });
}

export function countTypes(){
    fetch('/api/v1/users/groups/list')
        .then(response => response.json())
        .then(data => {
            if (null != data) {
                if (document.getElementById('total-group-count'))
                document.getElementById('total-group-count').innerHTML = data.length;
            }
        });
}

export function countRules(){
    fetch('/api/v1/zerotrust/rules/list')
        .then(response => response.json())
        .then(data => {
            if (null != data) {
                if (document.getElementById('total-rule-count'))
                    document.getElementById('total-rule-count').innerHTML = data.length;
            }
        });
}

export function countAssignedSystems(){
    fetch('/api/v1/ssh/servers/list')
        .then(response => response.json())
        .then(data => {
            if (null != data) {
                if (document.getElementById('assigned-server-count'))
                document.getElementById('assigned-server-count').innerHTML = data.length;
            }
        });
}

export async function fetchRule(ruleId) {
    try {
        const response = await fetch("/api/v1/zerotrust/rules/" + ruleId); // Assuming `get` is a wrapper for fetch
        const data = await response.json();
        return data;
    } catch (error) {
        console.log("Error:", error);
        return [];
    }
}

export async function fetchHostGroups() {
    try {
        const response = await fetch("/api/v1/enclaves/search"); // Assuming `get` is a wrapper for fetch
        const data = await response.json();
        return data;
    } catch (error) {
        console.log("Error:", error);
        return [];
    }
}

export async function countOpenConnections()
{
    fetch('/api/v1/sessions/list')
        .then(response => response.json())
        .then(data => {
            if (null != data) {
                if (document.getElementById('open-cxn-count'))
                    document.getElementById('open-cxn-count').innerHTML = data.length;
            }
        });
}

export function countAssignedGroups() {
    fetchHostGroups().then(data => {
            if (null != data) {
                if (document.getElementById('assigned-sgs'))
                    document.getElementById('assigned-sgs').innerHTML = data.length;
            }
        }
    );

}

export function getSettings() {
    // Inject Thymeleaf model attribute as a JSON object
    const settings = /*[[${systemSettings != null ? systemSettings : {}}]]*/ {};

    console.log("Settings is ", settings);

    // Cache DOM elements
    const userAssignmentElement = document.getElementById('require-user-assignment');
    const auditEnabledElement = document.getElementById('audit-enabled');

    // Update user assignment status
    if (userAssignmentElement) {
        userAssignmentElement.innerHTML = `
            <button class="btn-dark">${settings.requireProfileForLogin ? "Yes" : "No"}</button>
        `;
    }

    // Update internal audit status
    if (auditEnabledElement) {
        auditEnabledElement.innerHTML = `
            <button class="btn-dark">${settings.enableInternalAudit ? "Yes" : "No"}</button>
        `;
    }
}
