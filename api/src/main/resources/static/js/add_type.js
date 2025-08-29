document.addEventListener('DOMContentLoaded', function () {

    // Handle form submission
    const userForm = document.getElementById("userTypeForm");
    if (userForm) {
        userForm.addEventListener("submit", function(event) {
            event.preventDefault(); // Prevent default form submission
            const formData = new FormData(this);
            fetch('/api/v1/users/types/add', {
                method: 'POST',
                body: formData
            })
                .then(response => {
                    console.log("Fetch response status:", response.status);
                    if (!response.ok) throw new Error("Network response was not ok");
                    return response;
                })
                .then(data => {
                    console.log("Success:", data);
                    // Optionally close the modal
                    const modalElement = document.getElementById('userTypeFormModal');
                    const modal = bootstrap.Modal.getInstance(modalElement);
                    $("#alertTop").text("User Type Added successfully").show().delay(3000).fadeOut();
                    $("#alertTopError").hide();
                    if (modal) {
                        modal.hide();
                    }
                    const userTable = document.getElementById("user-types-table");
                    if (userTable){
                        $('#user-types-table').DataTable().ajax.reload(null, false);
                    }
                })
                .catch((error) => {
                    $("#alertTop").hide();
                    $("#alertTopError").text("User Type Not Added").show().delay(3000).fadeOut();
cd                });
        });
    }
});
