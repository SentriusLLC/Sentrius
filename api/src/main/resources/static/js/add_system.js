// Handle import failure gracefully by using dynamic import with fallback
let countAssignedSystems = function() {
    console.log("Fallback countAssignedSystems function");
};

// Function to toggle RDP configuration fields
function toggleRdpFields() {
    console.log("toggleRdpFields called");
    const rdpEnabled = document.getElementById('rdpEnabled');
    const rdpFields = document.getElementById('rdpFields');
    
    // Add safety checks
    if (!rdpEnabled || !rdpFields) {
        console.error('RDP elements not found:', {
            rdpEnabled: !!rdpEnabled,
            rdpFields: !!rdpFields
        });
        return;
    }
    
    const rdpInputs = rdpFields.querySelectorAll('input[type="text"], input[type="password"], input[type="number"]');
    
    // SSH-specific fields that should be hidden when RDP is enabled
    const authorizedKeysField = document.getElementById('authorizedKeysField');
    const portField = document.getElementById('portField');
    const portInput = document.getElementById('port');
    
    if (rdpEnabled.checked) {
        rdpFields.style.display = 'block';
        
        // Hide SSH-specific fields
        if (authorizedKeysField) {
            authorizedKeysField.style.display = 'none';
            // Remove required attribute from authorized keys when RDP is enabled
            const authorizedKeysInput = document.getElementById('authorizedKeys');
            if (authorizedKeysInput) {
                authorizedKeysInput.required = false;
            }
        }
        if (portField && portInput) {
            portField.style.display = 'none';
            // Set port to RDP default and remove required
            portInput.value = '3389';
            portInput.required = false;
        }
        
        // Make RDP fields conditionally required when enabled
        rdpInputs.forEach(input => {
            if (input.id === 'rdpPassword') {
                input.required = true;
            }
        });
    } else {
        rdpFields.style.display = 'none';
        
        // Show SSH-specific fields
        if (authorizedKeysField) {
            authorizedKeysField.style.display = 'block';
            const authorizedKeysInput = document.getElementById('authorizedKeys');
            if (authorizedKeysInput) {
                authorizedKeysInput.required = true;
            }
        }
        if (portField && portInput) {
            portField.style.display = 'block';
            // Reset port to SSH default and make required
            portInput.value = '22';
            portInput.required = true;
        }
        
        // Remove required attribute when disabled
        rdpInputs.forEach(input => {
            input.required = false;
        });
    }
}

// Immediately assign to global scope - don't wait for DOMContentLoaded
window.toggleRdpFields = toggleRdpFields;
console.log("toggleRdpFields assigned to window immediately");

// Try to load the functions module dynamically (non-blocking)
async function loadFunctionsModule() {
    try {
        const module = await import("./functions.js");
        countAssignedSystems = module.countAssignedSystems;
        console.log("Successfully loaded functions.js dynamically");
    } catch (error) {
        console.warn("Could not load functions.js:", error);
        // Keep using fallback function
    }
}

// Load functions module when DOM is ready, but don't block other functionality
document.addEventListener('DOMContentLoaded', function() {
    console.log("DOMContentLoaded event fired");
    
    // Load the functions module asynchronously
    loadFunctionsModule();
    
    // Debug info for troubleshooting
    window.rdpToggleDebug = {
        functionExists: typeof toggleRdpFields === 'function',
        windowAssignment: typeof window.toggleRdpFields === 'function',
        globalAssignment: typeof globalThis.toggleRdpFields === 'function'
    };
    console.log("Debug info:", window.rdpToggleDebug);

    const disableSSHButton = document.getElementById('disable-systems-button');
    if (disableSSHButton) {


            fetch(`/api/v1/system/settings/lockdownEnabled`)
                .then(response => response.json())
                .then(data => {
                    if (!data.lockdownEnabled) {
                        disableSSHButton.innerText = 'LockDown Systems';
                    }
                    else {
                        disableSSHButton.innerText = 'Re-enable Systems';
                    }
                })
                .catch(error => {

                });
        document.getElementById('disable-systems-button').addEventListener('click', function(event) {
            event.preventDefault(); // Prevent the default anchor behavior
            const csrfToken = document.getElementById('csrf-token').value; // Get CSRF token value
            fetch('/api/v1/system/settings/lockdown/toggle', {
                method: 'PUT', // Specify PUT request
                headers: {
                    'Content-Type': 'application/json', // Optional, adjust based on your API
                    'X-CSRF-TOKEN': csrfToken // Include the CSRF token in the header
                }
            }).then(response => response.json())
                .then(data => {
                    if (!data.lockdownEnabled) {
                        disableSSHButton.innerText = 'LockDown Systems';
                    }
                    else {
                        disableSSHButton.innerText = 'Re-enable Systems';
                    }
                })
                .catch(error => {

                });
        });
    } else {
        console.error("Enclave input not found");
    }
    // Enclave autocomplete
    const enclaveInput = document.getElementById('enclave');
    if (enclaveInput) {
        console.log("Enclave input found");
        enclaveInput.addEventListener('input', function () {
            const query = enclaveInput.value;

            if (query.length < 2) {
                // Do not trigger autocomplete for very short inputs
                return;
            }

            fetch(`/api/v1/enclaves/search?query=${encodeURIComponent(query)}`)
                .then(response => response.json())
                .then(data => {
                    let dataList = document.getElementById('enclaveSuggestions');
                    if (!dataList) {
                        dataList = document.createElement('datalist');
                        dataList.id = 'enclaveSuggestions';
                        document.body.appendChild(dataList);
                    }
                    dataList.innerHTML = '';

                    data.forEach(item => {
                        const option = document.createElement('option');
                        option.value = item.name;
                        dataList.appendChild(option);
                    });

                    enclaveInput.setAttribute('list', 'enclaveSuggestions');
                })
                .catch(error => {
                    console.error('Error fetching enclaves:', error);
                });
        });
    } else {
        console.error("Enclave input not found");
    }

    // Handle form submission
    const form = document.getElementById("hostForm");
    console.log("Form element found:", form);

    if (form) {
        console.log("Host form found, attaching submit event listener");
        form.addEventListener("submit", function (event) {
            event.preventDefault(); // Prevent default form submission
            console.log("Host form submitted");

            // Disable the save button for the current form
            const saveButton = form.querySelector('button[type="submit"]');
            if (saveButton) {
                saveButton.disabled = true;
            }

            const formData = new FormData(this);
            fetch('/api/v1/enclaves/hosts/add', {
                method: 'POST',
                body: formData
            })
                .then(response => {
                    if (response.ok) {
                        return response.json();
                    } else {
                        throw new Error("Failed to submit form. Status: " + response.status);
                    }
                })
                .then(data => {
                    console.log("Success:", data);

                    // Optionally close the modal
                    const modalElement = document.getElementById('hostFormModal');
                    if (modalElement) {
                        const modal = bootstrap.Modal.getInstance(modalElement);
                        if (modal) {
                            modal.hide();
                        }
                    }
                    $("#alertTop").text("Host added successfully").show().delay(3000).fadeOut();
                    countAssignedSystems();
                    const grpTable = document.getElementById("group-table");
                    if (grpTable) {
                        $('#group-table').DataTable().ajax.reload(null, false);
                    }
                    const sshTable = document.getElementById("ssh-table");
                    if (sshTable) {
                        $('#ssh-table').DataTable().ajax.reload(null, false);
                    }
                })
                .catch((error) => {
                    console.error("Error submitting the form:", error);
                })
                .finally(() => {
                    // Re-enable the save button after the request is done
                    if (saveButton) {
                        saveButton.disabled = false;
                    }
                });
        });
    } else {
        console.error("Host form not found");
    }
});
