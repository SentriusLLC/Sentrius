
export function clearLogs() {
    var csrf = document.getElementById("csrf-token").textContent;
        fetch('/api/v1/notification/errors/clear', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-TOKEN': csrf
            },
        })
            .then(response => {
                console.log("Fetch response status:", response.status);
                if (!response.ok) throw new Error("Network response was not ok");
                return response.json();
            })
            .then(data => {
                console.log("Success:", data);
                window.location.href = "/sso/v1/notifications/error/log/get";
            })
            .catch((error) => {
                window.location.href = "/sso/v1/notifications/error/log/get";
            });

    }
