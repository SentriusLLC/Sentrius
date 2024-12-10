document.addEventListener('DOMContentLoaded', function () {

    // Handle form submission
    const userForm = document.getElementById("uploadConfigForm");
    if (userForm) {
        userForm.addEventListener("submit", function(event) {
            event.preventDefault(); // Prevent default form submission
            const formData = new FormData(this);
            fetch('/api/v1/system/settings/upload', {
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
                    const modalElement = document.getElementById('userTypeFormModal');
                    const modal = bootstrap.Modal.getInstance(modalElement);
                    $("#alertTop").text("User Type Added successfully").show().delay(3000).fadeOut();
                    $("#alertTopError").hide();
                    if (modal) {
                        modal.hide();
                    }
                    var id = encodeURIComponent(data['id']);
                    window.location.href = "/sso/v1/system/settings/validate?id=" + id ;
                })
                .catch((error) => {
                    $("#alertTop").hide();
                    $("#alertTopError").text("Upload Not Successful").show().delay(3000).fadeOut();
                });
        });
    }
    const applySettingsForm = document.getElementById("applySettingsForm");
    if (applySettingsForm) {
        applySettingsForm.addEventListener("submit", function(event) {
            event.preventDefault(); // Prevent default form submission
            const formData = new FormData(this);
            fetch('/api/v1/system/settings/apply', {
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
                    const modalElement = document.getElementById('userTypeFormModal');
                    const modal = bootstrap.Modal.getInstance(modalElement);
                    if (modal) {
                        modal.hide();
                    }
                    var id = encodeURIComponent(data['id']);
                    window.location.href = "/sso/v1/system/settings/validate/" + id ;
                })
                .catch((error) => {
                    $("#alertTop").hide();
                    $("#alertTopError").text("Upload Not Successful").show().delay(3000).fadeOut();
                    });
        });
    }
});
