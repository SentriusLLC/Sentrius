/**
 * ABAC UI Permission Manager
 * 
 * Provides dynamic UI control based on ABAC policies.
 * This script fetches user permissions from the server and dynamically
 * shows/hides menu items based on ABAC policies.
 * 
 * Usage:
 * 1. Include this script in your page header
 * 2. Add data-ui-resource="resource.key" attributes to menu items
 * 3. Call AbacUI.init() when document is ready
 */
var AbacUI = (function() {
    'use strict';
    
    var permissions = {};
    var abacEnabled = false;
    var initialized = false;
    
    /**
     * Initialize the ABAC UI system
     */
    function init() {
        if (initialized) {
            console.log('AbacUI already initialized');
            return;
        }
        
        console.log('Initializing ABAC UI permission manager...');
        
        // Fetch permissions from server
        fetchPermissions().then(function(data) {
            if (data) {
                permissions = data.permissions || {};
                abacEnabled = data.abacEnabled || false;
                initialized = true;
                
                console.log('ABAC UI initialized. ABAC Enabled:', abacEnabled);
                console.log('Permissions loaded:', permissions);
                
                // Apply permissions to current page
                applyPermissions();
            }
        }).catch(function(error) {
            console.error('Failed to initialize ABAC UI:', error);
            // Fallback to standard access set behavior
            initialized = false;
        });
    }
    
    /**
     * Fetch permissions from the server
     */
    function fetchPermissions() {
        return $.ajax({
            url: '/api/v1/ui/permissions',
            type: 'GET',
            dataType: 'json',
            cache: false
        });
    }
    
    /**
     * Check if user has permission for a specific resource
     */
    function hasPermission(resourceKey) {
        if (!initialized || !abacEnabled) {
            // If not initialized or ABAC not enabled, fall back to standard behavior
            // (let the server-side th:if handle it)
            return null;
        }
        
        return permissions[resourceKey] === true;
    }
    
    /**
     * Apply permissions to all UI elements with data-ui-resource attribute
     */
    function applyPermissions() {
        if (!abacEnabled) {
            console.log('ABAC UI control is disabled, using standard access set checks');
            return;
        }
        
        // Find all elements with data-ui-resource attribute

        $('[data-ui-resource]').each(function() {
            var $element = $(this);
            var resourceKey = $element.data('ui-resource');
            
            if (resourceKey) {
                var hasAccess = hasPermission(resourceKey);
                
                if (hasAccess === false) {
                    // User doesn't have access, hide the element
                    $element.hide();
                    console.log('Hiding UI element:', resourceKey);
                } else if (hasAccess === true) {
                    // User has access, ensure element is visible
                    $element.show();
                    console.log('Showing UI element:', resourceKey);
                }
                // If hasAccess is null, let the server-side rendering decide
            }
        });
    }
    
    /**
     * Refresh permissions from server
     */
    function refresh() {
        console.log('Refreshing ABAC UI permissions...');
        initialized = false;
        init();
    }
    
    /**
     * Check if ABAC UI control is enabled
     */
    function isEnabled() {
        return abacEnabled;
    }
    
    /**
     * Get all loaded permissions
     */
    function getAllPermissions() {
        return permissions;
    }
    
    // Public API
    return {
        init: init,
        hasPermission: hasPermission,
        refresh: refresh,
        isEnabled: isEnabled,
        getAllPermissions: getAllPermissions,
        applyPermissions: applyPermissions
    };
})();

// Auto-initialize when document is ready
$(document).ready(function() {
    if (typeof isAuthenticated !== 'undefined' && isAuthenticated) {
        AbacUI.init();
    }
});
