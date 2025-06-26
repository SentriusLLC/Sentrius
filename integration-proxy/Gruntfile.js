module.exports = function(grunt) {
    grunt.initConfig({
        node: './node_modules',
        dest: './src/main/resources/static/node',
        destJs: '<%= dest %>/js',
        destCss: '<%= dest %>/css',
        destFonts: '<%= dest %>/fonts',
        clean: {
            build: {
                src: ['<%= dest %>']
            }
        },
        mkdir: {
            all: {
              options: {
                create: ['<%= destCss %>/jquery-ui/images', '<%= destJs %>/jquery-ui/widgets']
              },
            },
        },
        copy: {
            main: {
                files: [
                    {
                        expand: true,
                        flatten: true,
                        src: [
                            '<%= node %>/jointjs/dist/joint.js',
                        ],
                        dest: '<%= destJs %>/jointjs/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: [
                            '<%= node %>/jointjs/dist/joint.css',
                        ],
                        dest: '<%= destCss %>/jointjs/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: [
                            '<%= node %>/chart.js/dist/chart.js',
                            '<%= node %>/chart.js/dist/chart.umd.js',
                        ],
                        dest: '<%= destJs %>/chart.js/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: [
                            '<%= node %>/sockjs-client/dist/sockjs.js',
                        ],
                        dest: '<%= destJs %>/sockjs-client/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: [
                            '<%= node %>/google-protobuf/google-protobuf.js',
                        ],
                        dest: '<%= destJs %>/google-protobuf/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: [
                            '<%= node %>/datatables/media/css/jquery.dataTables.css',
                        ],
                        dest: '<%= destCss %>/datatables/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: [
                            '<%= node %>/datatables/media/images/*',
                        ],
                        dest: '<%= destCss %>/images/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: [
                            '<%= node %>/datatables/media/js/jquery.dataTables.js',
                        ],
                        dest: '<%= destJs %>/datatables/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: [
                            '<%= node %>/gridstack/dist/gridstack.css',
                            '<%= node %>/gridstack/dist/gridstack-extra.min.css'
                        ],
                        dest: '<%= destCss %>/gridstack/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: [
                            '<%= node %>/gridstack/dist/gridstack-all.js'
                        ],
                        dest: '<%= destJs %>/gridstack/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/bootstrap/dist/css/bootstrap*min*',
                              '<%= node %>/xterm/css/xterm.*',
                              '<%= node %>/jquery-cron/dist/jquery-cron.css'
                             ],
                        dest: '<%= destCss %>/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: [
                            '<%= node %>/@fortawesome/fontawesome-free/css/*.css'
                        ],
                        dest: '<%= destCss %>/font-awesome/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/@fortawesome/fontawesome-free/webfonts/*',
                        ],
                        dest: '<%= dest %>/css/webfonts/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/jquery-ui/themes/base/*'],
                        dest: '<%= destCss %>/jquery-ui/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/jquery-ui/themes/base/images/*'],
                        dest: '<%= destCss %>/jquery-ui/images',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/jquery/dist/jquery.min.*',
                              '<%= node %>/@popperjs/core/dist/umd/popper.min.js',
                              '<%= node %>/@popperjs/core/dist/umd/popper.min.js.map',
                              '<%= node %>/bootstrap/dist/js/bootstrap.min.*',
                              '<%= node %>/bootstrap5-tags/tags.js',
                              '<%= node %>/floatthead/dist/jquery.floatThead.min.*',
                              '<%= node %>/xterm/lib/xterm.*',
                              '<%= node %>/jquery-cron/dist/jquery-cron-min.*',
                              '<%= node %>/xterm-addon-fit/lib/xterm-addon-fit.*',
                              '<%= node %>/xterm-addon-search/lib/xterm-addon-search.*',
                              '<%= node %>/tooltip/dist/Tooltip.min.js',
                              '<%= node %>/rrule/dist/es5/rrule.js',
                             ],
                        dest: '<%= destJs %>/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/@fullcalendar/rrule/index.global.min.js'],
                        dest: '<%= destJs %>/fullcalendar/rrule/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/@fullcalendar/interaction/index.global.min.js'],
                        dest: '<%= destJs %>/fullcalendar/interaction/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/@fullcalendar/daygrid/index.global.min.js'],
                        dest: '<%= destJs %>/fullcalendar/daygrid/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/@fullcalendar/core/index.global.min.js'],
                        dest: '<%= destJs %>/fullcalendar/core/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/@fullcalendar/timegrid/index.global.min.js'],
                        dest: '<%= destJs %>/fullcalendar/timegrid/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/jquery-ui/ui/*.*'],
                        dest: '<%= destJs %>/jquery-ui/',
                        filter: 'isFile'
                    },
                    {
                        expand: true,
                        flatten: true,
                        src: ['<%= node %>/jquery-ui/ui/widgets/draggable.*',
                              '<%= node %>/jquery-ui/ui/widgets/droppable.*',
                              '<%= node %>/jquery-ui/ui/widgets/datepicker.*',
                              '<%= node %>/jquery-ui/ui/widgets/resizable.*',
                              '<%= node %>/jquery-ui/ui/widgets/selectable.*',
                              '<%= node %>/jquery-ui/ui/widgets/sortable.*',
                              '<%= node %>/jquery-ui/ui/widgets/mouse.*'
                             ],
                        dest: '<%= destJs %>/jquery-ui/widgets',
                        filter: 'isFile'
                    }
                ]
            }
        }
    });

    grunt.loadNpmTasks('grunt-contrib-clean');
    grunt.loadNpmTasks('grunt-mkdir');
    grunt.loadNpmTasks('grunt-contrib-copy');

    grunt.registerTask('default', ['clean','mkdir','copy']);
};