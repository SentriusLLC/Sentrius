import {countUsers} from './functions.js';
document.addEventListener('DOMContentLoaded', function () {

    // Handle form submission
    const form = document.getElementById("securityGroupForm");
    if (form) {
        form.addEventListener("submit", function(event) {
            event.preventDefault(); // Prevent default form submission

            const formData = new FormData(this);
            fetch('/api/v1/users/add', {
                method: 'POST',
                body: formData
            })
                .then(response => response.json())
                .then(data => {
                    console.log("Success:", data);
                    // Optionally close the modal
                    const modalElement = document.getElementById('userFormModal');
                    const modal = bootstrap.Modal.getInstance(modalElement);
                    $("#alertTop").text("User added successfully").show().delay(3000).fadeOut();
                    if (modal) {
                        modal.hide();
                    }
                    countUsers();
                })
                .catch((error) => {
                    $("#alertTopError").text("User Not Added").show().delay(3000).fadeOut();
                    console.error("Error:", error);
                });
        });
    }
});
