document.addEventListener('DOMContentLoaded', function () {

    // Handle form submission
    const agentForm = document.getElementById("agentForm");
    if (agentForm) {
        agentForm.addEventListener("submit", function(event) {
            event.preventDefault(); // Prevent default form submission
            console.log("User form submitted");
            const csrfToken = document.getElementById("csrf-token").value;
            const formJson = Object.fromEntries(new FormData(this).entries());
            fetch('/api/v1/agent/bootstrap/launcher/create', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    "X-CSRF-TOKEN": csrfToken
                },
                body: JSON.stringify(formJson)
            })
                .then(response => {
                    console.log("Fetch response status:", response.status);
                    if (!response.ok) throw new Error("Network response was not ok");
                    return response.json();
                })
                .then(data => {
                    console.log("Success:", data);
                    // Optionally close the modal
                    const modalElement = document.getElementById('agentFormModal');
                    const modal = bootstrap.Modal.getInstance(modalElement);
                    $("#alertTop").text("Agent created").show().delay(3000).fadeOut();
                    $("#alertTopError").hide();
                    if (modal) {
                        modal.hide();
                    }
                })
                .catch((error) => {
                    $("#alertTop").hide();
                    $("#alertTopError").text("Agent not created").show().delay(3000).fadeOut();
             });
        });
    }
});
