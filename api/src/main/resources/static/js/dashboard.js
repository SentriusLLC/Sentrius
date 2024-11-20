import {
    countAssignedGroups,
    countAssignedSystems,
    countTypes,
    countUsers,
    getSettings,
    countOpenConnections,
    countRules
} from './functions.js';

document.addEventListener('DOMContentLoaded', function () {
    console.log("*** count users');");
    countUsers();
    countTypes();
    countAssignedSystems();
    countAssignedGroups();
    getSettings();
    countOpenConnections();
    countRules();
});

