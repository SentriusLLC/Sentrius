import {countAssignedGroups, countAssignedSystems, countTypes, countUsers, getSettings, countOpenConnections} from './functions.js';

document.addEventListener('DOMContentLoaded', function () {
    console.log("*** count users');");
    countUsers();
    countTypes();
    countAssignedSystems();
    countAssignedGroups();
    getSettings();
    countOpenConnections();

});

