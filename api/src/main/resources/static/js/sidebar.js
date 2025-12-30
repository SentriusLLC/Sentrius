/**
 * Mobile Sidebar Toggle Functionality
 * Handles collapsible sidebar for mobile devices
 */

function toggleSidebar() {
    const sidebar = document.getElementById('sidebarContainer');
    const backdrop = document.getElementById('sidebarBackdrop');
    const toggle = document.getElementById('sidebarToggle');

    if (sidebar && backdrop && toggle) {
        const isShown = sidebar.classList.contains('show');

        if (isShown) {
            // Close sidebar
            sidebar.classList.remove('show');
            backdrop.classList.remove('show');
            toggle.innerHTML = '<i class="fas fa-bars"></i>';
            document.body.style.overflow = '';
        } else {
            // Open sidebar
            sidebar.classList.add('show');
            backdrop.classList.add('show');
            toggle.innerHTML = '<i class="fas fa-times"></i>';
            if (window.innerWidth <= 768) {
                document.body.style.overflow = 'hidden';
            }
        }
    }
}

// Make the toggle function available for inline HTML onclick handlers.
window.toggleSidebar = toggleSidebar;

// Close sidebar when clicking a link on mobile
function closeSidebarOnNavigate() {
    if (window.innerWidth <= 768) {
        const sidebar = document.getElementById('sidebarContainer');
        const backdrop = document.getElementById('sidebarBackdrop');
        const toggle = document.getElementById('sidebarToggle');

        if (sidebar && sidebar.classList.contains('show')) {
            sidebar.classList.remove('show');
            backdrop?.classList.remove('show');
            if (toggle) {
                toggle.innerHTML = '<i class="fas fa-bars"></i>';
            }
            document.body.style.overflow = '';
        }
    }
}

// Attach click handlers to all navigation links
document.addEventListener('DOMContentLoaded', function() {
    // Ensure the sidebar is collapsed by default on mobile.
    closeSidebarOnNavigate();

    const navLinks = document.querySelectorAll('#sidebarContainer .nav-link');
    navLinks.forEach(link => {
        // Only close sidebar for direct navigation links, not collapse toggles
        if (!link.hasAttribute('data-bs-toggle')) {
            link.addEventListener('click', closeSidebarOnNavigate);
        }
    });

    // Handle window resize - close sidebar and reset body overflow if resizing to desktop
    let resizeTimer;
    window.addEventListener('resize', function() {
        clearTimeout(resizeTimer);
        resizeTimer = setTimeout(function() {
            if (window.innerWidth > 768) {
                closeSidebarOnNavigate();
            } else {
                // If going back to mobile, keep it collapsed by default.
                closeSidebarOnNavigate();
            }
        }, 250);
    });
});

// Handle escape key to close sidebar on mobile
document.addEventListener('keydown', function(event) {
    if (event.key === 'Escape' && window.innerWidth <= 768) {
        closeSidebarOnNavigate();
    }
});
