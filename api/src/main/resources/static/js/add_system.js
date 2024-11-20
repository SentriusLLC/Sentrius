import {countAssignedSystems} from "./functions.js";
console.log("Script loaded"); // This should fire immediately if the script loads


document.addEventListener('DOMContentLoaded', function () {
    console.log("DOMContentLoaded event fired");

    const disableSSHButton = document.getElementById('disable-ssh-button');
    if (disableSSHButton) {


            fetch(`/api/v1/system/settings/sshEnabled`)
                .then(response => response.json())
                .then(data => {
                    if (data.sshEnabled) {
                        disableSSHButton.innerText = 'Disable SSH';
                    }
                    else {
                        disableSSHButton.innerText = 'Enable SSH';
                    }
                })
                .catch(error => {

                });
        document.getElementById('disable-ssh-button').addEventListener('click', function(event) {
            event.preventDefault(); // Prevent the default anchor behavior
            const csrfToken = document.getElementById('csrf-token').value; // Get CSRF token value
            fetch('/api/v1/system/settings/ssh/toggle', {
                method: 'PUT', // Specify PUT request
                headers: {
                    'Content-Type': 'application/json', // Optional, adjust based on your API
                    'X-CSRF-TOKEN': csrfToken // Include the CSRF token in the header
                }
            }).then(response => response.json())
                .then(data => {
                    if (data.sshEnabled) {
                        disableSSHButton.innerText = 'Disable SSH';
                    }
                    else {
                        disableSSHButton.innerText = 'Enable SSH';
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
            fetch('/api/v1/ssh/servers/add', {
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
