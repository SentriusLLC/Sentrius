import {fetchHostGroups, countTypes, countUsers, fetchRule} from './functions.js';

async function saveRuleConfiguration(url, payload, csrf) {
    try {
        const response = await fetch(url, {
            method: "POST",
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': csrf // Include the CSRF token in the headers
            },
            body: JSON.stringify(payload) // Convert the payload to a JSON string
        });

        console.log("Response:", response);
        if (!response.ok) {
            throw new Error("Failed to save the rule configuration");
        }

    } catch (error) {
        console.log("Error occurred:", error);
        alert("An error occurred while saving the rule configuration.");
    }
}

function assignRule(ruleId) {
    console.log("assign");

    // Fetch rule details dynamically or provide them statically
    const ruleName = "Example Rule " + ruleId; // Replace with dynamic fetching logic if needed
    fetchRule(ruleId).then((rule) => {
        console.log("Fetched ", rule);
        // Populate modal fields
        document.getElementById("assignRuleName").value = rule.ruleName;

        document.getElementById("assignRuleId").value = rule.id;

        const hostGroupsSelect = document.getElementById("hostGroups");
        hostGroupsSelect.innerHTML = ""; // Clear previous options
        fetchHostGroups().then((hostGroups) => {
                hostGroups.forEach((group) => {

                    const option = document.createElement("option");
                    option.value = group.groupId;
                    option.textContent = group.displayName;

                    if (rule.hostGroups.find((ruleGroup) => ruleGroup.groupId === group.groupId)) {
                        option.selected = true;
                    }
                    hostGroupsSelect.appendChild(option);
                });

            });
        });

        // Show the modal
        const modal = new bootstrap.Modal(document.getElementById("assignHostGroupsModal"));
        modal.show();
    }

function deleteRule(ruleId) {
        const csrfToken = document.getElementById("assignCsrf").value;
        var url = '/api/v1/zerotrust/rules/delete/' + ruleId
        fetch(url, {
            method: "DELETE",
            headers: {
                "X-CSRF-TOKEN": csrfToken
            },

        })
            .then((response) => {
                if (response.ok) {
                    $('#rule-table').DataTable().ajax.reload(null, false);
                } else {
                    alert("Failed to delete rule(s).");
                }
            })
            .catch((error) => {
                console.error("Error:", error);
                alert("An error occurred.");
            });
}

$(document).ready(function () {



    $('#rule-table').DataTable({
        ajax: {
            url: '/api/v1/zerotrust/rules/list', // list
            dataSrc: '', // Specify the property where the data is located (e.g. use 'data' if response has a "data" field)
        },
        columns: [
            {data: 'ruleName'},
            {data: 'hostGroups',
                render: function (data) {
                    if (data) {
                        return data.map(value => `<span class="badge badge-info">${value.displayName}</span>`).join(' ');
                    }
                    else {
                        return [];
                    }
                }
            },
            {data: 'ruleClass'},

            {
                data: 'id',
                render: function (data, type, row) {
                    let buttons = '';

                    if (row.canEdit) {
                        buttons +=
                            `<button class="btn btn-secondary spacer spacer-middle" data-bs-toggle="modal" data-bs-target="#edit_dialog_${data}" onclick="editRule(${data})">Edit</button><button id="role_btn_${data}" onclick="assignRule(${data})" class="btn btn-secondary assign_btn spacer spacer-right">Assign Host Groups</button>
         `;
                    }

                    if (row.canDelete) {
                        buttons += `<button id="delete_class_${data}" class="btn btn-secondary delete_class spacer spacer-right" onclick="deleteRule(${data})">Delete</button>
            `;
                    }

                    return buttons;
                }
            }
        ]
    });

    //call delete action



    // CSRF Token




    // Handle form submission
    document.getElementById("assignHostGroupsForm").addEventListener("submit", function (event) {
        event.preventDefault();
        const csrfToken = document.getElementById("assignCsrf").value;

        const ruleName = document.getElementById("assignRuleName").value;
        const ruleId = document.getElementById("assignRuleId").value;
        const selectedHostGroups = Array.from(document.getElementById("hostGroups").selectedOptions).map(
            (option) => option.value
        );

        // Create payload
        const payload = {
            ruleId: ruleId,
            ruleName: ruleName,
            hostGroups: selectedHostGroups,
        };

        // Send POST request
        fetch("/api/v1/zerotrust/rules/assign", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "X-CSRF-TOKEN": csrfToken, // Include CSRF token
            },
            body: JSON.stringify(payload),
        })
            .then((response) => {
                if (response.ok) {
                    $('#rule-table').DataTable().ajax.reload(null, false);
                } else {
                    alert("Failed to assign host groups.");
                }
                const modal = bootstrap.Modal.getInstance(document.getElementById("assignHostGroupsModal"));
                modal.hide();

            })
            .catch((error) => {
                console.error("Error:", error);
                alert("An error occurred.");
            });
    });

    document.getElementById('redirectButton').addEventListener('click', function () {
        // Get the selected value from the dropdown
        const ruleClass = document.getElementById('ruleClassDropdown').value;

        // Get the value from the ruleName input field
        const ruleName = document.getElementById('ruleName').value;


        // Construct the URL based on the selected rule class and rule name
        let url = "";
        if (ruleClass.includes("ForbiddenCommandsRule")) {
            url = "/sso/v1/zerotrust/rules/config/forbidden_commands_rule?ruleName=" + encodeURIComponent(ruleName);
        } else if (ruleClass.includes("AllowedCommandsRule")) {
            url = "/sso/v1/zerotrust/rules/config/allowed_commands_rule?ruleName=" + encodeURIComponent(ruleName);
        } else if (ruleClass.includes("DeletePrevention")) {


            (async () => {
                url = "/api/v1/zerotrust/rules/save";
                const csrfToken = document.getElementById('csrf-token').value; // Get CSRF token value
                const payload = {
                    ruleName: ruleName,
                    ruleClass: ruleClass
                };



                try {
                    await saveRuleConfiguration(url, payload, csrfToken);
                    console.log("Rule configuration saved successfully.");
                    console.log("Saved");
                    url = "/sso/v1/zerotrust/rules/list";
                    window.location.href = url;
                } catch (error) {
                    console.error("Error while saving:", error);
                }
            })();

            return;
        } else {
            (async () => {
                url = "/api/v1/zerotrust/rules/save";
                const csrfToken = document.getElementById('csrf-token').value; // Get CSRF token value
                const payload = {
                    ruleName: ruleName,
                    ruleClass: ruleClass
                };



                try {
                    await saveRuleConfiguration(url, payload, csrfToken);
                    console.log("Rule configuration saved successfully.");
                    console.log("Saved");
                    url = "/sso/v1/zerotrust/rules/list";
                    window.location.href = url;
                } catch (error) {
                    console.error("Error while saving:", error);
                }
            })();
            return;
        }

        // Redirect to the constructed URL if ruleName and ruleClass are valid
        if (ruleClass && ruleName) {
            window.location.href = url;
        } else {
            alert("Please select a Rule Class and enter a Rule Name.");
        }
    });
});

window.assignRule = assignRule;
window.deleteRule = deleteRule;