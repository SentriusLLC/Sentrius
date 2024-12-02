document.addEventListener('DOMContentLoaded', function () {

    // Handle form submission
    const userForm = document.getElementById("userForm");
    if (userForm) {
        userForm.addEventListener("submit", function(event) {
            event.preventDefault(); // Prevent default form submission
            console.log("User form submitted");
            const formData = new FormData(this);
            fetch('/api/v1/users/add', {
                method: 'POST',
                body: formData
            })
                .then(response => {
                    console.log("Fetch response status:", response.status);
                    if (!response.ok) throw new Error("Network response was not ok");
                    return response.json();
                })
                .then(data => {
                    console.log("Success:", data);
                    // Optionally close the modal
                    const modalElement = document.getElementById('userFormModal');
                    const modal = bootstrap.Modal.getInstance(modalElement);
                    $("#alertTop").text("User added successfully").show().delay(3000).fadeOut();
                    $("#alertTopError").hide();
                    if (modal) {
                        modal.hide();
                    }
                    countUsers();
                    const userTable = document.getElementById("user-table");
                    if (userTable){
                        $('#user-table').DataTable().ajax.reload(null, false);
                    }
                })
                .catch((error) => {
                    $("#alertTop").hide();
                    $("#alertTopError").text("User Not Added").show().delay(3000).fadeOut();
cd                });
        });
    }
});
