let socket = null;
let sliceh = 0;
let autoSaveTimer = null; // 自动保存防抖计时器

function applyHoursFilter(state, prefix) {
    var hoursVal = $(prefix + '-filter-hours').val();
    var hours = (hoursVal !== '' && hoursVal !== null) ? parseInt(hoursVal) : 3;
    if (isNaN(hours) || hours < 0) hours = 3;
    var now = new Date();
    var start = new Date(now.getTime() - hours * 3600000);
    start.setMinutes(0, 0, 0);
    var end = new Date(now.getTime() + 3600000);
    end.setMinutes(0, 0, 0);
    var pad = function(n) { return ('0' + n).slice(-2); };
    var fmt = function(d) {
        return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
            + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
    };
    state.startTime = fmt(start);
    state.endTime = fmt(end);
    $(prefix + '-filter-start').val(fmt(start).substring(0, 10));
    $(prefix + '-filter-end').val(fmt(end).substring(0, 10));
}

// 观众管理 state
let vstState = {
    currentFile: '',
    startTime: '',
    endTime: '',
    fileFirstTime: '',
    fileLastTime: '',
    search: '',
    page: 1,
    pageSize: 10,
    totalPages: 1,
    totalRows: 0,
    sortField: '最近',
    sortOrder: 'desc',
    rankLimit: 15,
    headers: ['最近', 'id', '观众', '打分类型', '打分', '次数', '判定表', '场次'],
    columnOrder: [0, 1, 2, 3, 4, 5, 6, 7],
    chartInstances: {}
};
// 匹配管理 state
let mtchState = {
    currentFile: '',
    startTime: '',
    endTime: '',
    fileFirstTime: '',
    fileLastTime: '',
    search: '',
    page: 1,
    pageSize: 10,
    totalPages: 1,
    totalRows: 0,
    sortField: '最近匹配',
    sortOrder: 'desc',
    rankLimit: 15,
    headers: ['最近匹配', '匹配id', '匹配名', '匹配分', '匹配次数'],
    columnOrder: [0, 1, 2, 3, 4],
    chartInstances: {}
};
// 关注人管理 state
let flwState = {
    currentFile: '',
    startTime: '',
    endTime: '',
    fileFirstTime: '',
    fileLastTime: '',
    search: '',
    page: 1,
    pageSize: 10,
    totalPages: 1,
    totalRows: 0,
    sortField: '最新时间',
    sortOrder: 'desc',
    rankLimit: 15,
    headers: ['最新时间', 'id', '名字', '次数'],
    columnOrder: [0, 1, 2, 3],
    chartInstances: {}
};
// 礼物管理 state
let gftState = {
    currentFile: '',
    startTime: '',
    endTime: '',
    fileFirstTime: '',
    fileLastTime: '',
    search: '',
    page: 1,
    pageSize: 10,
    totalPages: 1,
    totalRows: 0,
    sortField: '最新时间',
    sortOrder: 'desc',
    rankLimit: 15,
    headers: ['最新时间', 'id', '名字', '赠送礼物名字', '电池', '赠礼次数'],
    columnOrder: [0, 1, 2, 3, 4, 5],
    chartInstances: {}
};
// 弹幕管理 state
let dmgrState = {
    currentFile: '',
    startTime: '',
    endTime: '',
    fileFirstTime: '',
    fileLastTime: '',
    search: '',
    page: 1,
    pageSize: 10,
    totalPages: 1,
    totalRows: 0,
    sortField: '发送时间',
    sortOrder: 'desc',
    rankLimit: 15,
    headers: ['发送时间', 'id', '名字', '弹幕'],
    columnOrder: [0, 1, 2, 3],
    chartInstances: {}
};
// 直播间管理 state
let lrmState = {
    currentFile: '',
    startTime: '',
    endTime: '',
    fileFirstTime: '',
    fileLastTime: '',
    search: '',
    page: 1,
    pageSize: 10,
    totalPages: 1,
    totalRows: 0,
    sortField: '时间',
    sortOrder: 'desc',
    headers: ['时间', '观看数', '在线数', '点赞数'],
    columnOrder: [0, 1, 2, 3, 4],
    chartInstances: {}
};
// 陌生观众看板 state
let svState = {
    records: [],
    page: 1,
    pageSize: 20,
    search: '',
    totalPages: 1,
    totalRecords: 0,
    defaultToLast: true,
    sortField: 'time',
    sortOrder: 'asc',
    startTime: '',
    endTime: '',
    currentFile: ''
};

$(function () {
    "use strict";
    let time;
    time = setInterval(heartBeat, 5000);
    // 恢复上次的tab页
    try {
        var savedTab = localStorage.getItem('activeTab');
        if (savedTab) {
            var $link = $('.sidebar-link[data-tab="' + savedTab + '"]');
            if ($link.length) {
                switchTab(savedTab, $link[0]);
            }
        }
    } catch(e) {}
    function heartBeat() {
        "use strict";
        $.ajax({
            url: '../heartBeat',
            async: false,
            cache: false,
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200") {
                    // 兼容新旧格式：新格式为对象{popu, live_status, room_like}，旧格式为数字
                    var result = data.result;
                    if (typeof result === 'object' && result !== null) {
                        // 新格式
                        if ($(".popu").length > 0 && result.popu != null) {
                            $(".popu").html(result.popu);
                        }
                        if ($(".status-live").length > 0 && result.live_status != null) {
                            var statusText = result.live_status == 1 ? '<span style="color:#4eff4e;">●直播中</span>' : '<span style="color:#ff6b6b;">●未开播</span>';
                            $(".status-live").html(statusText);
                        }
                        if ($(".status-like").length > 0 && result.room_like != null) {
                            $(".status-like").html(method.fmtNum(result.room_like));
                        }
                        if ($(".room-online").length > 0 && result.room_online != null) {
                            $(".room-online").html(method.fmtNum(result.room_online));
                        }
                        if ($(".room_watcher").length > 0 && result.room_watcher != null) {
                            $(".room_watcher").html(method.fmtNum(result.room_watcher));
                        }
                    } else {
                        // 旧格式兼容
                        if ($(".popu").length > 0) {
                            if (result != null) {
                                $(".popu").html(result);
                            } else {
                                clearInterval(time);
                            }
                        } else {
                            clearInterval(time);
                        }
                    }
                }
            }
        });
    };
    $(document).on('change', '.import-file-input', function () {
        method.importDfFile(this);
    });

    // ========== 直播间管理 event bindings ==========
    $(document).on('change', '#lrm-csv-select', function () {
        lrmState.currentFile = $(this).val();
        lrmState.startTime = ''; lrmState.endTime = ''; lrmState.search = '';
        $('#lrm-search-input').val('');
        lrmState.sortField = '时间'; lrmState.sortOrder = 'desc';
        lrmState.page = 1;
        if (lrmState.currentFile) method.loadCsvData();
    });
    // 日期/小时 互斥：修改日期清空小时，修改小时清空日期
    $('#lrm-filter-start, #lrm-filter-end').on('change', function() { if ($(this).val()) $('#lrm-filter-hours').val(''); });
    $('#lrm-filter-hours').on('change input', function() { if ($(this).val()) { $('#lrm-filter-start').val(''); $('#lrm-filter-end').val(''); } });

    $(document).on('click', '#lrm-btn-apply', function () {
        var start = $('#lrm-filter-start').val() || '';
        var end = $('#lrm-filter-end').val() || '';
        if (start || end) { lrmState.startTime = start ? start + ' 00:00:01' : ''; lrmState.endTime = end ? end + ' 23:59:59' : ''; }
        else { applyHoursFilter(lrmState, '#lrm'); }
        lrmState.search = $('#lrm-search-input').val() || '';
        lrmState.page = 1;
        method.loadCsvData();
    });
    $(document).on('click', '#lrm-btn-reset', function () {
        lrmState.startTime = ''; lrmState.endTime = '';
        lrmState.search = ''; $('#lrm-search-input').val(''); $('#lrm-filter-hours').val('3');
        lrmState.sortField = '时间'; lrmState.sortOrder = 'desc';
        lrmState.page = 1; method.loadCsvData();
    });
    $(document).on('keypress', '#lrm-search-input', function (e) {
        if (e.which === 13) { lrmState.search = $(this).val() || ''; lrmState.page = 1; method.loadCsvData(); }
    });
    $(document).on('click', '#lrm-table-head th[data-sort]', function () {
        method.sortCsvColumn($(this).data('sort'));
    });
    $(document).on('click', '#lrm-btn-export', function () {
        method.exportCsv();
    });
    $(document).on('click', '#lrm-first-btn', function () {
        if (lrmState.page > 1) { lrmState.page = 1; method.loadCsvData(); }
    });
    $(document).on('click', '#lrm-prev-btn', function () {
        if (lrmState.page > 1) { lrmState.page--; method.loadCsvData(); }
    });
    $(document).on('click', '#lrm-next-btn', function () {
        if (lrmState.page < lrmState.totalPages) { lrmState.page++; method.loadCsvData(); }
    });
    $(document).on('click', '#lrm-last-btn', function () {
        if (lrmState.page < lrmState.totalPages) { lrmState.page = lrmState.totalPages; method.loadCsvData(); }
    });
    $(document).on('click', '#lrm-btn-go', function () {
        method.gotoPage($('#lrm-page-jump').val());
    });
    $(document).on('keypress', '#lrm-page-jump', function (e) {
        if (e.which === 13) { method.gotoPage($(this).val()); }
    });
    $(document).on('click', '.lrm-row-del', function () {
        var $btn = $(this);
        if ($btn.hasClass('confirming')) {
            $btn.removeClass('confirming').text('删除').removeClass('btn-danger').addClass('btn-outline-danger');
            var timeKey = $btn.data('time');
            method.deleteCsvRow(timeKey);
        } else {
            // 取消其他正在确认的按钮
            $('.lrm-row-del.confirming').removeClass('confirming').text('删除').removeClass('btn-danger').addClass('btn-outline-danger');
            $btn.addClass('confirming').text('确认删除?').removeClass('btn-outline-danger').addClass('btn-danger');
            // 3秒后自动恢复
            setTimeout(function () {
                if ($btn.hasClass('confirming')) {
                    $btn.removeClass('confirming').text('删除').removeClass('btn-danger').addClass('btn-outline-danger');
                }
            }, 3000);
        }
    });

    // ========== 弹幕管理 event bindings ==========
    $(document).on('change', '#dmgr-csv-select', function () {
        dmgrState.currentFile = $(this).val();
        dmgrState.startTime = ''; dmgrState.endTime = ''; dmgrState.search = '';
        $('#dmgr-search-input').val('');
        dmgrState.sortField = '发送时间'; dmgrState.sortOrder = 'desc';
        dmgrState.page = 1;
        if (dmgrState.currentFile) method.loadDmgrData();
    });
    $('#dmgr-filter-start, #dmgr-filter-end').on('change', function() { if ($(this).val()) $('#dmgr-filter-hours').val(''); });
    $('#dmgr-filter-hours').on('change input', function() { if ($(this).val()) { $('#dmgr-filter-start').val(''); $('#dmgr-filter-end').val(''); } });

    $(document).on('click', '#dmgr-btn-apply', function () {
        var start = $('#dmgr-filter-start').val() || '';
        var end = $('#dmgr-filter-end').val() || '';
        if (start || end) { dmgrState.startTime = start ? start + ' 00:00:01' : ''; dmgrState.endTime = end ? end + ' 23:59:59' : ''; }
        else { applyHoursFilter(dmgrState, '#dmgr'); }
        dmgrState.search = $('#dmgr-search-input').val() || '';
        dmgrState.page = 1;
        method.loadDmgrData();
    });
    $(document).on('click', '#dmgr-btn-reset', function () {
        dmgrState.startTime = ''; dmgrState.endTime = '';
        dmgrState.search = ''; $('#dmgr-search-input').val(''); $('#dmgr-filter-hours').val('3');
        dmgrState.sortField = '发送时间'; dmgrState.sortOrder = 'desc';
        dmgrState.page = 1; method.loadDmgrData();
    });
    $(document).on('keypress', '#dmgr-search-input', function (e) {
        if (e.which === 13) { dmgrState.search = $(this).val() || ''; dmgrState.page = 1; method.loadDmgrData(); }
    });
    $(document).on('click', '#dmgr-table-head th[data-sort]', function () {
        method.sortDmgrColumn($(this).data('sort'));
    });
    $(document).on('click', '#dmgr-btn-export', function () {
        method.exportDmgrCsv();
    });
    $(document).on('click', '#dmgr-first-btn', function () {
        if (dmgrState.page > 1) { dmgrState.page = 1; method.loadDmgrData(); }
    });
    $(document).on('click', '#dmgr-prev-btn', function () {
        if (dmgrState.page > 1) { dmgrState.page--; method.loadDmgrData(); }
    });
    $(document).on('click', '#dmgr-next-btn', function () {
        if (dmgrState.page < dmgrState.totalPages) { dmgrState.page++; method.loadDmgrData(); }
    });
    $(document).on('click', '#dmgr-last-btn', function () {
        if (dmgrState.page < dmgrState.totalPages) { dmgrState.page = dmgrState.totalPages; method.loadDmgrData(); }
    });
    $(document).on('click', '#dmgr-btn-go', function () {
        method.gotoDmgrPage($('#dmgr-page-jump').val());
    });
    $(document).on('keypress', '#dmgr-page-jump', function (e) {
        if (e.which === 13) { method.gotoDmgrPage($(this).val()); }
    });
    $(document).on('click', '#dmgr-btn-rank-apply', function () {
        var v = parseInt($('#dmgr-rank-limit').val());
        if (isNaN(v) || v < 1) v = 1;
        dmgrState.rankLimit = v;
        $('#dmgr-rank-limit').val(v);
        method.renderDmgrCharts();
    });
    // ========== 观众管理 event bindings ==========
    $(document).on('change', '#vst-csv-select', function () {
        vstState.currentFile = $(this).val();
        vstState.startTime = ''; vstState.endTime = ''; vstState.search = '';
        $('#vst-search-input').val('');
        vstState.sortField = '最近'; vstState.sortOrder = 'desc';
        vstState.page = 1;
        if (vstState.currentFile) method.loadVstData();
    });
    $('#vst-filter-start, #vst-filter-end').on('change', function() { if ($(this).val()) $('#vst-filter-hours').val(''); });
    $('#vst-filter-hours').on('change input', function() { if ($(this).val()) { $('#vst-filter-start').val(''); $('#vst-filter-end').val(''); } });

    $(document).on('click', '#vst-btn-apply', function () {
        var start = $('#vst-filter-start').val() || '';
        var end = $('#vst-filter-end').val() || '';
        if (start || end) { vstState.startTime = start ? start + ' 00:00:01' : ''; vstState.endTime = end ? end + ' 23:59:59' : ''; }
        else { applyHoursFilter(vstState, '#vst'); }
        vstState.search = $('#vst-search-input').val() || '';
        vstState.page = 1;
        method.loadVstData();
    });
    $(document).on('click', '#vst-btn-reset', function () {
        vstState.startTime = ''; vstState.endTime = '';
        vstState.search = ''; $('#vst-search-input').val(''); $('#vst-filter-hours').val('3');
        vstState.sortField = '最近'; vstState.sortOrder = 'desc';
        vstState.page = 1; method.loadVstData();
    });
    $(document).on('keypress', '#vst-search-input', function (e) {
        if (e.which === 13) { vstState.search = $(this).val() || ''; vstState.page = 1; method.loadVstData(); }
    });
    $(document).on('click', '#vst-btn-export', function () { method.exportVstCsv(); });
    $(document).on('click', '#vst-first-btn', function () { if (vstState.page > 1) { vstState.page = 1; method.loadVstData(); } });
    $(document).on('click', '#vst-prev-btn', function () { if (vstState.page > 1) { vstState.page--; method.loadVstData(); } });
    $(document).on('click', '#vst-next-btn', function () { if (vstState.page < vstState.totalPages) { vstState.page++; method.loadVstData(); } });
    $(document).on('click', '#vst-last-btn', function () { if (vstState.page < vstState.totalPages) { vstState.page = vstState.totalPages; method.loadVstData(); } });
    $(document).on('click', '#vst-btn-go', function () { method.gotoVstPage($('#vst-page-jump').val()); });
    $(document).on('keypress', '#vst-page-jump', function (e) { if (e.which === 13) method.gotoVstPage($(this).val()); });
    $(document).on('click', '#vst-btn-rank-apply', function () {
        var v = parseInt($('#vst-rank-limit').val());
        if (isNaN(v) || v < 1) v = 1;
        vstState.rankLimit = v;
        $('#vst-rank-limit').val(v);
        method.renderVstCharts();
    });
    $(document).on('click', '#vst-table-head th[data-sort]', function () {
        method.sortVstColumn($(this).data('sort'));
    });
    // ========== 匹配管理 event bindings ==========
    $(document).on('change', '#mtch-csv-select', function () {
        mtchState.currentFile = $(this).val();
        mtchState.startTime = ''; mtchState.endTime = ''; mtchState.search = '';
        $('#mtch-search-input').val('');
        mtchState.sortField = '最近匹配'; mtchState.sortOrder = 'desc';
        mtchState.page = 1;
        if (mtchState.currentFile) method.loadMtchData();
    });
    $('#mtch-filter-start, #mtch-filter-end').on('change', function() { if ($(this).val()) $('#mtch-filter-hours').val(''); });
    $('#mtch-filter-hours').on('change input', function() { if ($(this).val()) { $('#mtch-filter-start').val(''); $('#mtch-filter-end').val(''); } });

    $(document).on('click', '#mtch-btn-apply', function () {
        var start = $('#mtch-filter-start').val() || '';
        var end = $('#mtch-filter-end').val() || '';
        if (start || end) { mtchState.startTime = start ? start + ' 00:00:01' : ''; mtchState.endTime = end ? end + ' 23:59:59' : ''; }
        else { applyHoursFilter(mtchState, '#mtch'); }
        mtchState.search = $('#mtch-search-input').val() || '';
        mtchState.page = 1;
        method.loadMtchData();
    });
    $(document).on('click', '#mtch-btn-reset', function () {
        mtchState.startTime = ''; mtchState.endTime = '';
        mtchState.search = ''; $('#mtch-search-input').val(''); $('#mtch-filter-hours').val('3');
        mtchState.sortField = '最近匹配'; mtchState.sortOrder = 'desc';
        mtchState.page = 1; method.loadMtchData();
    });
    $(document).on('keypress', '#mtch-search-input', function (e) {
        if (e.which === 13) { mtchState.search = $(this).val() || ''; mtchState.page = 1; method.loadMtchData(); }
    });
    $(document).on('click', '#mtch-btn-export', function () { method.exportMtchCsv(); });
    $(document).on('click', '#mtch-first-btn', function () { if (mtchState.page > 1) { mtchState.page = 1; method.loadMtchData(); } });
    $(document).on('click', '#mtch-prev-btn', function () { if (mtchState.page > 1) { mtchState.page--; method.loadMtchData(); } });
    $(document).on('click', '#mtch-next-btn', function () { if (mtchState.page < mtchState.totalPages) { mtchState.page++; method.loadMtchData(); } });
    $(document).on('click', '#mtch-last-btn', function () { if (mtchState.page < mtchState.totalPages) { mtchState.page = mtchState.totalPages; method.loadMtchData(); } });
    $(document).on('click', '#mtch-btn-go', function () { method.gotoMtchPage($('#mtch-page-jump').val()); });
    $(document).on('keypress', '#mtch-page-jump', function (e) { if (e.which === 13) method.gotoMtchPage($(this).val()); });
    $(document).on('click', '#mtch-btn-rank-apply', function () {
        var v = parseInt($('#mtch-rank-limit').val());
        if (isNaN(v) || v < 1) v = 1;
        mtchState.rankLimit = v;
        $('#mtch-rank-limit').val(v);
        method.renderMtchCharts();
    });
    $(document).on('click', '#mtch-table-head th[data-sort]', function () {
        method.sortMtchColumn($(this).data('sort'));
    });
    // ========== 关注人管理 event bindings ==========
    $(document).on('change', '#flw-csv-select', function () {
        flwState.currentFile = $(this).val();
        flwState.startTime = ''; flwState.endTime = ''; flwState.search = '';
        $('#flw-search-input').val('');
        flwState.sortField = '最新时间'; flwState.sortOrder = 'desc';
        flwState.page = 1;
        if (flwState.currentFile) method.loadFlwData();
    });
    $('#flw-filter-start, #flw-filter-end').on('change', function() { if ($(this).val()) $('#flw-filter-hours').val(''); });
    $('#flw-filter-hours').on('change input', function() { if ($(this).val()) { $('#flw-filter-start').val(''); $('#flw-filter-end').val(''); } });

    $(document).on('click', '#flw-btn-apply', function () {
        var start = $('#flw-filter-start').val() || '';
        var end = $('#flw-filter-end').val() || '';
        if (start || end) { flwState.startTime = start ? start + ' 00:00:01' : ''; flwState.endTime = end ? end + ' 23:59:59' : ''; }
        else { applyHoursFilter(flwState, '#flw'); }
        flwState.search = $('#flw-search-input').val() || '';
        flwState.page = 1;
        method.loadFlwData();
    });
    $(document).on('click', '#flw-btn-reset', function () {
        flwState.startTime = ''; flwState.endTime = '';
        flwState.search = ''; $('#flw-search-input').val(''); $('#flw-filter-hours').val('3');
        flwState.sortField = '最新时间'; flwState.sortOrder = 'desc';
        flwState.page = 1; method.loadFlwData();
    });
    $(document).on('keypress', '#flw-search-input', function (e) {
        if (e.which === 13) { flwState.search = $(this).val() || ''; flwState.page = 1; method.loadFlwData(); }
    });
    $(document).on('click', '#flw-btn-export', function () { method.exportFlwCsv(); });
    $(document).on('click', '#flw-first-btn', function () { if (flwState.page > 1) { flwState.page = 1; method.loadFlwData(); } });
    $(document).on('click', '#flw-prev-btn', function () { if (flwState.page > 1) { flwState.page--; method.loadFlwData(); } });
    $(document).on('click', '#flw-next-btn', function () { if (flwState.page < flwState.totalPages) { flwState.page++; method.loadFlwData(); } });
    $(document).on('click', '#flw-last-btn', function () { if (flwState.page < flwState.totalPages) { flwState.page = flwState.totalPages; method.loadFlwData(); } });
    $(document).on('click', '#flw-btn-go', function () { method.gotoFlwPage($('#flw-page-jump').val()); });
    $(document).on('keypress', '#flw-page-jump', function (e) { if (e.which === 13) method.gotoFlwPage($(this).val()); });
    $(document).on('click', '#flw-btn-rank-apply', function () {
        var v = parseInt($('#flw-rank-limit').val());
        if (isNaN(v) || v < 1) v = 1;
        flwState.rankLimit = v;
        $('#flw-rank-limit').val(v);
        method.renderFlwCharts();
    });
    $(document).on('click', '#flw-table-head th[data-sort]', function () {
        method.sortFlwColumn($(this).data('sort'));
    });
    // ========== 礼物管理 event bindings ==========
    $(document).on('change', '#gft-csv-select', function () {
        gftState.currentFile = $(this).val();
        gftState.startTime = ''; gftState.endTime = ''; gftState.search = '';
        $('#gft-search-input').val('');
        gftState.sortField = '最新时间'; gftState.sortOrder = 'desc';
        gftState.page = 1;
        if (gftState.currentFile) method.loadGftData();
    });
    $('#gft-filter-start, #gft-filter-end').on('change', function() { if ($(this).val()) $('#gft-filter-hours').val(''); });
    $('#gft-filter-hours').on('change input', function() { if ($(this).val()) { $('#gft-filter-start').val(''); $('#gft-filter-end').val(''); } });

    $(document).on('click', '#gft-btn-apply', function () {
        var start = $('#gft-filter-start').val() || '';
        var end = $('#gft-filter-end').val() || '';
        if (start || end) { gftState.startTime = start ? start + ' 00:00:01' : ''; gftState.endTime = end ? end + ' 23:59:59' : ''; }
        else { applyHoursFilter(gftState, '#gft'); }
        gftState.search = $('#gft-search-input').val() || '';
        gftState.page = 1;
        method.loadGftData();
    });
    $(document).on('click', '#gft-btn-reset', function () {
        gftState.startTime = ''; gftState.endTime = '';
        gftState.search = ''; $('#gft-search-input').val(''); $('#gft-filter-hours').val('3');
        gftState.sortField = '最新时间'; gftState.sortOrder = 'desc';
        gftState.page = 1; method.loadGftData();
    });
    $(document).on('keypress', '#gft-search-input', function (e) {
        if (e.which === 13) { gftState.search = $(this).val() || ''; gftState.page = 1; method.loadGftData(); }
    });
    $(document).on('click', '#gft-btn-export', function () { method.exportGftCsv(); });
    $(document).on('click', '#gft-first-btn', function () { if (gftState.page > 1) { gftState.page = 1; method.loadGftData(); } });
    $(document).on('click', '#gft-prev-btn', function () { if (gftState.page > 1) { gftState.page--; method.loadGftData(); } });
    $(document).on('click', '#gft-next-btn', function () { if (gftState.page < gftState.totalPages) { gftState.page++; method.loadGftData(); } });
    $(document).on('click', '#gft-last-btn', function () { if (gftState.page < gftState.totalPages) { gftState.page = gftState.totalPages; method.loadGftData(); } });
    $(document).on('click', '#gft-btn-go', function () { method.gotoGftPage($('#gft-page-jump').val()); });
    $(document).on('keypress', '#gft-page-jump', function (e) { if (e.which === 13) method.gotoGftPage($(this).val()); });
    $(document).on('click', '#gft-btn-rank-apply', function () {
        var v = parseInt($('#gft-rank-limit').val());
        if (isNaN(v) || v < 1) v = 1;
        gftState.rankLimit = v;
        $('#gft-rank-limit').val(v);
        method.renderGftCharts();
    });
    $(document).on('click', '#gft-table-head th[data-sort]', function () {
        method.sortGftColumn($(this).data('sort'));
    });

    publicData.set = method.initSet(method.getSet());
    method.loadPNList();
    method.loadAutoBlockList();
    method.loadDanmakuStoreList();
    // 负黑自动拉黑姬：建立WebSocket连接接收实时推送
    method._connectAutoBlockWs();
    $('.thankgift_thank_status')
        .change(
            function () {
                let num = Number($(".thankgift_thank_status").children(
                    "option:selected").val());
                switch (num) {
                    case 1:
                        $(".thankgift_thank").val(
                            method.replaceThanko(method.getSet().thank_gift.thank));
                        $(".thankgift_thank").attr('placeholder',
                            "感谢%uName%%Type%的%GiftName% x%Num%~");
                        $(".thankgift_thank")
                            .attr(
                                'title',
                                '模式:单人单种<br/>多条语句时候注意以回车为分割每条语句,多条语句会随机发送其中一条<br/>感谢语，可选参数<br/><span class=\'red-font\'>%uName%</span>送礼人名称<br/><span class=\'red-font\'>%Type%</span>赠送类型<br/><span class=\'red-font\'>%GiftName%</span>礼物名称<br/><span class=\'red-font\'>%Num%</span>礼物数量');
                        break;
                    case 2:
                        $(".thankgift_thank").val(method.replaceThankt(method.getSet().thank_gift.thank));
                        $(".thankgift_thank").attr('placeholder',
                            "感謝%uName%贈送的%Gifts%~");
                        $(".thankgift_thank")
                            .attr('title',
                                '模式:单人多种<br/>多条语句时候注意以回车为分割每条语句,多条语句会随机发送其中一条<br/>感谢语，可选参数<br/> <span class=\'red-font\'>%uName%</span>送礼人名称<br/><span class=\'red-font\'>%Gifts%</span>礼物和数量的集合以逗号隔开');
                        break
                    case 3:
                        $(".thankgift_thank").val(method.replaceThankts(method.getSet().thank_gift.thank));
                        $(".thankgift_thank").attr('title', '模式:多人多种<br/>多条语句时候注意以回车为分割每条语句,多条语句会随机发送其中一条<br/>感谢语，可选参数<br/> <span class=\'red-font\'>%uNames%</span>送礼人名称集合<br/><span class=\'red-font\'>%Gifts%</span>礼物和数量的集合以逗号隔开');
                        $(".thankgift_thank").attr('placeholder',
                            "感謝%uNames%贈送的%Gifts%~");
                        break
                    default:
                        break;
                }
                let exampleTriggerEl2 = document.getElementById("thankgift_thank")
                let tooltip2 = bootstrap.Tooltip.getInstance(exampleTriggerEl2)
                tooltip2 = new bootstrap.Tooltip(exampleTriggerEl2)
                let exampleTriggerEl = document.getElementById('thankgift_thank_status')
                let tooltip = bootstrap.Tooltip.getInstance(exampleTriggerEl)
                tooltip.hide();
            });
    $('.thankgift_shield_status').change(
        function () {
            if (Number($(".thankgift_shield_status").children(
                "option:selected").val()) !== 1) {
                $(".thankgift_shield").hide();
                $(".thankgift_list_gift_shield_status").hide();
            } else {
                $(".thankgift_shield").show();
                $(".thankgift_list_gift_shield_status").show();
            }
            if (Number($(".thankgift_shield_status").children(
                "option:selected").val()) !== 4) {
                $("#gift-shield-btn").hide();
            } else {
                $("#gift-shield-btn").show();
            }
        });
    $('.thankgift_list_gift_shield_status').change(
        function () {
            if (Number($(".thankgift_list_gift_shield_status").children(
                "option:selected").val()) !== 1) {
                //白名单
                $(".thankgift_shield").attr('placeholder',
                    "白名单模式：自定义通过礼物名字，以 中文逗号(，)为分割；示例：\n辣条，亿圆，友谊的小船\n注意：为空那时候是什么都不屏蔽，仅在自定义模式下有用\n默认黑名单，相反白名单（仅感谢填写的）");
                $(".thankgift_shield")
                    .attr('title',
                        '白名单模式：这里填写自定义通过礼物名字，以 中文逗号(，)为分割；示例：<br/>辣条，亿圆，友谊的小船<br/><span class=\'red-font\'>注意：为空那时候是什么都不屏蔽，仅在自定义模式下有用<br/>默认黑名单，相反白名单（仅感谢填写的）</span>');

            } else {
                //黑名单
                $(".thankgift_shield").attr('placeholder',
                    "黑名单模式：自定义屏蔽礼物名字，以 中文逗号(，)为分割；示例：\n辣条，亿圆，友谊的小船\n注意：为空那时候是什么都不屏蔽，仅在自定义模式下有用\n默认黑名单，相反白名单（仅感谢填写的）");
                $(".thankgift_shield")
                    .attr('title',
                        '黑名单模式：这里填写自定义屏蔽礼物名字，以 中文逗号(，)为分割；示例：<br/>辣条，亿圆，友谊的小船<br/><span class=\'red-font\'>注意：为空那时候是什么都不屏蔽，仅在自定义模式下有用<br/>默认黑名单，相反白名单（仅感谢填写的）</span>');

            }
        });
    $(document).on('click', '.btn-connect-d', function () {
        let a = $(".connect-docket").val();
        if (a === "" || a === null) {
            $(".connect-docket").val("不能为空");
            return;
        }
        // 在新窗口打开弹幕框, 并连接websocket.主页面不管理弹幕`socket`
        openDanmuWindow(a)
    });
    $(document).on('click', '#danmu_open', function () {
        let url_start = window.location.host;
        let protocol = window.location.protocol;
        $(".connect-docket").val((protocol === 'http:' ? "ws://" : "wss://") + url_start + "/danmu/sub");
    });
    // 页面加载时自动填充弹幕WebSocket地址（适配非localhost/IPv6访问）
    $(function() {
        var currentVal = $(".connect-docket").val();
        if (!currentVal || currentVal.indexOf('localhost') !== -1) {
            var protocol = window.location.protocol;
            $(".connect-docket").val((protocol === 'http:' ? "ws://" : "wss://") + window.location.host + "/danmu/sub");
        }
    });
    $('body').click(function (e) {
        let target = $(e.target);
        if (!target.is('.danmu-child-li')) {
            if ($('.danmu-tips').is(':visible')) {
                $('.danmu-tips').hide();
                $('.danmu-child').removeClass('danmu-child-z');
            }
        }
    });
    $('.auto_save_set').on('change', function () {
        publicData.set.auto_save_set = $(this).is(':checked');
        // 立即持久化自动保存开关状态，走静默保存流程
        method.saveSet(true);
    });
});
//为弹幕看板打开一个新窗口
function openDanmuWindow(sub_url) {
    let url = new URL("/danmu_widget?sub="+sub_url,window.location.href ).href
    let windowName = "SmallWindow";
    let windowFeatures = "width=400,height=450";

    window.open(url, windowName, windowFeatures);
}
//实时保存 (input用于文本输入, change用于复选框和下拉框)
// 带防抖：连续修改只保存最后一次，避免逐字保存
$(document).on('input change', '.live-save', function () {
    if (publicData.set && publicData.set.auto_save_set) {
        clearTimeout(autoSaveTimer);
        autoSaveTimer = setTimeout(function() {
            method.saveSet(true);
        }, 800);
    }
});
//按钮保存
$(document).on(
    'click',
    '.set-hold', function () {
        method.saveSet();
    }
);
$(document).on('click', '.is_guard_report_click', function () {
    if ($(".thankgift_is_guard_report").is(':checked')) {
        $(".thankgift_report").show();
        $(".thankgift_barrageReport").show();
        $(".thankgift_is_gift_code").attr("disabled", false);
        if ($(".thankgift_is_gift_code").is(':checked')) {
            $(".thankgift_codeStrings").show();
        }
    } else {
        $(".thankgift_is_gift_code").attr("disabled", true);
        $(".thankgift_is_gift_code").prop('checked', false);
        $(".thankgift_report").hide();
        $(".thankgift_barrageReport").hide();
        $(".thankgift_codeStrings").hide();
    }
});
$(document).on('click', '.is_guard_code_click', function () {
    if ($(".thankgift_is_gift_code").is(':checked') && $(".thankgift_is_guard_report").is(':checked')) {
        $(".thankgift_codeStrings").show();
    } else {
        $(".thankgift_codeStrings").hide();
    }
});
$(document).on('click', '.import-set', function () {
    var setControl = $(this).closest('.set-control');
    setControl.find('.import-file-input').data('fileType', setControl.attr('data-file') || null).click();
});
$(document).on('click', '.export-set', function () {
    var fileType = $(this).closest('.set-control').attr('data-file');
    if (fileType) {
        method.fileExport(fileType);
    } else {
        method.setExprot();
    }
});
$(document).on('click', '.export-set-web', function () {
    var fileType = $(this).closest('.set-control').attr('data-file');
    if (fileType) {
        method.fileExportWeb(fileType);
    } else {
        method.setExprotWeb();
    }
});
$(document).on('click', '#gift-shield-btn', function () {
    // if (!$(".shieldgifts-mask").is(":visible")) {
    //     $(".shieldgifts-mask").show();
    // }


});
// 原replys-btn弹窗按钮已移除，改为内联显示
$(document).on('click', '.btn-close', function () {
    if ($(".shieldgifts-mask").is(":visible")) {
        $(".shieldgifts-mask").hide();
    }
});
$(document).on('click', '.btn-close-block', function () {
    // if ($(".block-mask").is(":visible")) {
        $("#block-model").modal('hide');
    // }
});

// 原btn-closer模态关闭按钮已移除，改为内联显示
$(document).on('click', '.btn-block', function () {
    const uid = $(".block-input").attr("uid");
    const time = $(".block-input").val();
    if (time !== "" && time !== null && time.indexOf(".") < 0 && Number(time) > 0 && Number(time) <= 720) {
        if (Number(time) > 720 && Number(time) < 1) {
            // alert("禁言时间错误")
            showMessage("禁言时间错误", "danger","3");
        } else {
            const code = method.block(uid, time);
            if (code === 0) {
                showMessage("禁言成功", "success","2");
                $("#block-model").modal('hide');
            } else {
                showMessage("禁言失败，纠错码:"+code, "danger","3");
                console.log("禁言纠错码:" + code)
            }
        }
    } else {
        showMessage("禁言时间错误", "danger","3");
    }
});
$(document)
    .on(
        'click',
        '.shieldgift_add',
        function () {
            $(".shieldgifts-tbody")
                .append(
                    `<tr>
									<td><input type='checkbox' class='shieldgifts_open live-save' data-bs-toggle='tooltip' data-bs-placement='top' title='是否开启' data-original-title='是否开启'></td>
									<td><input class='small-input shieldgifts_name live-save' placeholder='礼物名称' data-bs-toggle='tooltip' data-bs-placement='top' title='礼物名称' data-bs-html='true' data-original-title='礼物名称'></td>
									<td>
									<select class='custom-select-sm shieldgifts_status live-save' data-bs-toggle='tooltip' data-bs-placement='top' title='选择类型<br/>1:屏蔽对应数量<br/>2:屏蔽一坨礼物对应电池' data-bs-html='true' data-original-title='选择类型'>
									<option value='1' selected='selected'>数量</option>
									<option value='2'>电池</option></select>
									</td>
									<td>
									<input type='number' min='0' class='small-input shieldgifts_num live-save' placeholder='num' value='0' data-bs-toggle='tooltip' data-bs-placement='top' title='数量(电池)小于多少触发屏蔽(不能小于多少)' data-bs-html='true' data-original-title='大于多少(不得小于)'>
									</td>
									<td><button type='button' class='btn btn-danger btn-sm shieldgift_delete live-save'>删除</button></td>
									</tr>`);
            let exampleTriggerEl2 = document.getElementById("shieldgift_add")
            let tooltip2 = bootstrap.Tooltip.getInstance(exampleTriggerEl2)
            tooltip2.hide()
            let tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'))
            let tooltipList = tooltipTriggerList.map(function (tooltipTriggerEl) {
                // this.addEventListener('hide.bs.tooltip', function () {
                //     new bootstrap.Tooltip(tooltipTriggerEl)
                // })
                return new bootstrap.Tooltip(tooltipTriggerEl)
            });
            if (publicData.set && publicData.set.auto_save_set) {
                method.saveSet(true);
            }
        });
$(document)
    .on(
        'click',
        '.replys_add',
        function () {
            $(".replys-ul")
                .append(
                    `<li><input type='checkbox' class='reply_open live-save'
						data-bs-toggle='tooltip' data-bs-placement='top' title='是否开启'
						data-bs-html='true' data-original-title='是否开启'>
						<input type='checkbox' class='reply_oc live-save'
						data-bs-toggle='tooltip' tabindex="0" data-bs-placement='top' title='是否精确匹配<br/>更多信息点进去编辑查看'
						data-bs-html='true' data-original-title='是否精确匹配'>
						<textarea class='small-input reply_keywords live-save' placeholder='关键字'
						data-bs-toggle='tooltip' data-bs-placement='top' title='不能编辑:多个关键字,以中文逗号隔开'
						data-bs-html='true' data-original-title='关键字' readonly='readonly' style='height: 2rem' disabled></textarea>
						<textarea class='small-input reply_shields live-save' placeholder='屏蔽词'
						data-bs-toggle='tooltip' data-bs-placement='top' title='不能编辑:多个屏蔽词,以中文逗号隔开'
						data-bs-html='true' data-original-title='关键字' readonly='readonly' style='height: 2rem' disabled></textarea>
						<textarea class='big-input reply_rs live-save' placeholder='回复语句'
						data-bs-toggle='tooltip' data-bs-placement='top' title='不能编辑:回复语句,提供%AT%参数,以打印:@提问问题人名称'
						data-bs-html='true' data-original-title='回复语句' readonly='readonly' style='height: 2rem' disabled></textarea>
						<span class='reply-btns'>
						<button type='button' class='btn btn-success btn-sm reply_edit'  data-bs-toggle='modal' data-bs-target='#reply-model-edit'>编辑</button>
						<button type='button' class='btn btn-danger btn-sm reply_delete live-save'>删除</button>
						</span>
					</li>`);
            let exampleTriggerEl2 = document.getElementById("replys_add")
            let tooltip2 = bootstrap.Tooltip.getInstance(exampleTriggerEl2)
            tooltip2.hide()
            let tooltipTriggerList = [].slice.call(document.querySelectorAll('[data-bs-toggle="tooltip"]'))
            let tooltipList = tooltipTriggerList.map(function (tooltipTriggerEl) {
                // this.addEventListener('hide.bs.tooltip', function () {
                //     new bootstrap.Tooltip(tooltipTriggerEl)
                // })
                return new bootstrap.Tooltip(tooltipTriggerEl)
            });
            if (publicData.set && publicData.set.auto_save_set) {
                method.saveSet(true);
            }

        });
$(document).on('click', '.reply_delete', function () {
    $(this).parent().parent().remove();
    if (publicData.set && publicData.set.auto_save_set) {
        method.saveSet(true);
    }
});
$(document).on('click', '.shieldgift_delete', function () {
    $(this).parent().parent().remove();
    if (publicData.set && publicData.set.auto_save_set) {
        method.saveSet(true);
    }
});

// $('#reply-btns').delegate('.reply_edit','click', function () {
$(document).on('click', '.reply_edit', function () {
    let index = $(this).parent().parent().index();
    let is_open = $(this).parent().parent().find(".reply_open").is(':checked');
    let is_oc = $(this).parent().parent().find(".reply_oc").is(':checked');
    let keywords = $(this).parent().parent().find(".reply_keywords").val();
    let shields = $(this).parent().parent().find(".reply_shields").val();
    let rs = $(this).parent().parent().find(".reply_rs").val();
    /*    $(".radd-mask").show();*/
    $(".radd-body").find(".reply_open_i").prop('checked', is_open);
    $(".radd-body").find(".reply_open_i").attr("z-index", index);
    $(".radd-body").find(".reply_open_i").attr("z-name", "reply_open_" + index);

    $(".radd-body").find(".reply_oc_i").prop('checked', is_oc);
    $(".radd-body").find(".reply_oc_i").attr("z-index", index);
    $(".radd-body").find(".reply_oc_i").attr("z-name", "reply_oc_" + index);

    $(".radd-body").find(".reply_keywords_i").val(keywords);
    $(".radd-body").find(".reply_keywords_i").attr("z-index", index);
    $(".radd-body").find(".reply_keywords_i").attr("z-name", "reply_keywords_" + index);

    $(".radd-body").find(".reply_shields_i").val(shields);
    $(".radd-body").find(".reply_shields_i").attr("z-index", index);
    $(".radd-body").find(".reply_shields_i").attr("z-name", "reply_shields_" + index);

    $(".radd-body").find(".reply_rs_i").val(rs);
    $(".radd-body").find(".reply_rs_i").attr("z-index", index);
    $(".radd-body").find(".reply_rs_i").attr("z-name", "reply_rs_" + index);

    $(".radd-body").find(".reply_delete_i").attr("z-index", index);
});
$(document).on('input change', '.reply-sync', function (e) {
    let index = $(this).attr("z-index");
    let z_name = $(this).attr("z-name");
    if (z_name.startsWith("reply_open_")) {
        $(".replys-ul").children("li").eq(index).find(".reply_open").prop('checked', $(this).is(':checked'));
    } else if (z_name.startsWith("reply_oc_")) {
        $(".replys-ul").children("li").eq(index).find(".reply_oc").prop('checked', $(this).is(':checked'));
    } else if (z_name.startsWith("reply_keywords_")) {
        $(".replys-ul").children("li").eq(index).find(".reply_keywords").val($(this).val());
    } else if (z_name.startsWith("reply_shields_")) {
        $(".replys-ul").children("li").eq(index).find(".reply_shields").val($(this).val());
    } else if (z_name.startsWith("reply_rs_")) {
        $(".replys-ul").children("li").eq(index).find(".reply_rs").val($(this).val());
    }
    if (publicData.set && publicData.set.auto_save_set) {
        clearTimeout(autoSaveTimer);
        autoSaveTimer = setTimeout(function() {
            method.saveSet(true);
        }, 800);
    }
});
$(document).on('click', '.reply_delete_i', function (e) {
    let index = $(this).attr("z-index");
    $(".replys-ul").children("li").eq(index).remove();
    e.stopPropagation();
    $('#reply-model-edit').modal('hide');
    if (publicData.set && publicData.set.auto_save_set) {
        method.saveSet(true);
    }
});
$(document).on('click', '.btn-closeri', function () {
    /*    alert("1")*/
    /*    if ($("#reply-model-edit").is(":visible")) {*/
    let index = $(this).parent().parent().find(".reply_delete_i").attr("z-index");
    let is_open = $(this).parent().parent().find(".reply_open_i").is(':checked');
    let is_oc = $(this).parent().parent().find(".reply_oc_i").is(':checked');
    let keywords = $(this).parent().parent().find(".reply_keywords_i").val();
    let shields = $(this).parent().parent().find(".reply_shields_i").val();
    let rs = $(this).parent().parent().find(".reply_rs_i").val();
    $(".replys-ul").children("li").eq(index).find(".reply_open").prop('checked', is_open);
    $(".replys-ul").children("li").eq(index).find(".reply_oc").prop('checked', is_oc);
    $(".replys-ul").children("li").eq(index).find(".reply_keywords").val(keywords);
    $(".replys-ul").children("li").eq(index).find(".reply_shields").val(shields);
    $(".replys-ul").children("li").eq(index).find(".reply_rs").val(rs);
    /*       if (keywords === null || keywords === "" || rs === null || rs === "") {
               alert("关键字和回复语句都不能为空！！！");
               return;
           }*/
    $('#reply-model-edit').modal('hide');
    if (publicData.set && publicData.set.auto_save_set) {
        method.saveSet(true);
    }
    /*        $(".radd-mask").hide();*/
    /*    }*/
});
$(document).on('click', '.danmu-child', function (e) {
    $(this).children(".danmu-tips").css("left", e.pageX - $(this).offset().left);
    $(this).addClass("danmu-child-z");
    $(this).children(".danmu-tips").show();
    $(this).siblings().children(".danmu-tips").hide();
    $(this).siblings().removeClass("danmu-child-z");
});
$(document).on('click', '.danmu-tips-li', function (e) {
    e.stopPropagation();
    const text = $(this).text();
    const uname = $(this).parent().parent().parent().children().find(".danmu-name").text();
    const uid = $(this).parent().parent().attr("uid");
    if (text.trim() === "关闭") {
        $(this).parents().children(".danmu-tips").hide();
        $(".danmu-tips").hide();
        $(this).parents().children(".danmu-child").removeClass("danmu-child-z");
    } else if (text.trim() === "查看") {
        window.open("https://space.bilibili.com/" + uid, 'width=1000,height=800', '_blank');
    } else if (text.trim() === "禁言") {
        $(".block-input").attr("uid", uid);
        $(".block-input").val("");
        $(".block-input").attr("placeholder", "禁言(" + uname + "-" + uid + ")" + "-禁言时间为1-720小时");
        // $(".block-model").modal('show');
    } else {

    }
});
$(document).on('click', '.black_flag_parent', function () {
    if ($(this).is(':checked')) {
        $(".black_flag_child").prop('checked', false);
    } else {
        $(".black_flag_child").prop('checked', true);
    }
});
$(document).on('click', '.white_flag_parent', function () {
    if ($(this).is(':checked')) {
        $(".white_flag_child").prop('checked', false);
    } else {
        $(".white_flag_child").prop('checked', true);
    }
});
$(document).on('click', '.bili-badlist-load', function () {
    var $btn = $(this);
    $btn.prop('disabled', true).text('加载中...');
    method.loadBiliBadList(1, function () {
        $btn.prop('disabled', false).text('刷新列表');
    });
});
$(document).on('click', '.bili-badlist-prev', function () {
    method.loadBiliBadList(biliBadListState.page - 1);
});
$(document).on('click', '.bili-badlist-next', function () {
    method.loadBiliBadList(biliBadListState.page + 1);
});
$(document).on('click', '.bili-avatar-click', function () {
    var faceUrl = $(this).data('face');
    $('#avatar-modal-img').attr('src', faceUrl);
    var avatarModal = new bootstrap.Modal(document.getElementById('avatar-modal'));
    avatarModal.show();
});

$(document).on('click', '.pn-add-btn', function () {
    method.addPNRow();
});
$(document).on('click', '.pn-save-btn', function () {
    method.savePNList();
});
$(document).on('click', '.pn-delete-btn', function () {
    method._syncPNPage();
    var rowIdx = $(this).closest('tr').index();
    var listIdx = (pnData.page - 1) * pnData.pageSize + rowIdx;
    if (listIdx < pnData.list.length) {
        pnData.list.splice(listIdx, 1);
    }
    // adjust page if we deleted the last item on the last page
    var totalPages = Math.max(1, Math.ceil(pnData.list.length / pnData.pageSize));
    if (pnData.page > totalPages) {
        pnData.page = totalPages;
    }
    method.renderPNTable();
});
$(document).on('click', '.pn-name-link', function (e) {
    e.preventDefault();
    var uid = $(this).closest('tr').data('uid');
    if (uid) {
        window.open('https://space.bilibili.com/' + uid, '_blank');
    }
});
$(document).on('dblclick', '.pn-name-link', function () {
    var cell = $(this).parent('.pn-col-name');
    var link = $(this);
    var input = cell.find('.pn-name');
    link.hide();
    input.show().focus().select();
});
$(document).on('blur', '.pn-col-name .pn-name', function () {
    var cell = $(this).parent('.pn-col-name');
    var input = $(this);
    var link = cell.find('.pn-name-link');
    var val = input.val().trim();
    if (link.length) {
        if (val) {
            link.text(val).show();
        } else {
            link.text('(未命名)').show();
        }
        input.hide();
    }
});
$(document).on('keydown', '.pn-col-name .pn-name', function (e) {
    if (e.key === 'Enter') {
        $(this).blur();
    }
});
$(document).on('click', '.pn-prev', function () {
    if (pnData.page > 1) {
        method._syncPNPage();
        pnData.page--;
        method.renderPNTable();
    }
});
$(document).on('click', '.pn-next', function () {
    var totalPages = Math.max(1, Math.ceil(pnData.list.length / pnData.pageSize));
    if (pnData.page < totalPages) {
        method._syncPNPage();
        pnData.page++;
        method.renderPNTable();
    }
});
// 正白负黑姬表头排序
$(document).on('click', '.pn-sortable', function () {
    method._syncPNPage();
    var col = $(this).data('col');
    method._togglePNSort(col);
    method._applyPNSort();
    pnData.page = 1;
    method.renderPNTable();
});
$(document).on('input', '.pn-search-input', function () {
    pnData.page = 1;
    method.renderPNTable();
});
// 负黑自动拉黑姬 - 上一页
$(document).on('click', '.ab-prev', function () {
    if (autoBlockData.page > 1) {
        autoBlockData.page--;
        method.renderAutoBlockTable();
    }
});
// 负黑自动拉黑姬 - 下一页
$(document).on('click', '.ab-next', function () {
    var totalPages = Math.max(1, Math.ceil(autoBlockData.list.length / autoBlockData.pageSize));
    if (autoBlockData.page < totalPages) {
        autoBlockData.page++;
        method.renderAutoBlockTable();
    }
});
// 负黑自动拉黑姬 - 搜索
$(document).on('input', '.ab-search-input', function () {
    autoBlockData.page = 1;
    method.renderAutoBlockTable();
});
// 负黑自动拉黑姬 - 解除拉黑
$(document).on('click', '.ab-unblock-btn', function () {
    var uid = Number($(this).data('uid'));
    if (!uid) return;
    var $btn = $(this);
    $btn.prop('disabled', true).text('...');
    $.ajax({
        url: '../unblockAutoBlockUser',
        type: 'GET',
        data: {uid: uid},
        dataType: 'json',
        success: function (data) {
            if (data.code == "200" && data.result == 0) {
                autoBlockData.list = autoBlockData.list.filter(function (item) {
                    return item.uid !== uid;
                });
                var totalPages = Math.max(1, Math.ceil(autoBlockData.list.length / autoBlockData.pageSize));
                if (autoBlockData.page > totalPages) {
                    autoBlockData.page = totalPages;
                }
                method.renderAutoBlockTable();
            }
        },
        complete: function () {
            $btn.prop('disabled', false).text('解除拉黑');
        }
    });
});
// 负黑自动拉黑姬 - 删除显示
$(document).on('click', '.ab-delete-btn', function () {
    var uid = Number($(this).data('uid'));
    if (!uid) return;
    var $btn = $(this);
    $btn.prop('disabled', true).text('...');
    $.ajax({
        url: '../deleteAutoBlockRecord',
        type: 'POST',
        data: {uid: uid},
        dataType: 'json',
        success: function (data) {
            if (data.code == "200" && data.result == 0) {
                autoBlockData.list = autoBlockData.list.filter(function (item) {
                    return item.uid !== uid;
                });
                var totalPages = Math.max(1, Math.ceil(autoBlockData.list.length / autoBlockData.pageSize));
                if (autoBlockData.page > totalPages) {
                    autoBlockData.page = totalPages;
                }
                method.renderAutoBlockTable();
            }
        },
        complete: function () {
            $btn.prop('disabled', false).text('删除显示');
        }
    });
});
// 负黑自动拉黑姬 - 拉黑分数变更保存
$(document).on('change', '.auto-block-score', function () {
    if (publicData.set && publicData.set.auto_save_set) {
        method.saveSet(true);
    } else {
        method.saveSet();
    }
});
// 负黑自动拉黑姬 - 拉黑间隔时间变更保存
$(document).on('change', '.auto-block-interval', function () {
    if (publicData.set && publicData.set.auto_save_set) {
        method.saveSet(true);
    } else {
        method.saveSet();
    }
});
// 负黑自动拉黑姬 - 面板展开时重新加载数据（已改为tab切换触发，见switchTab函数）
// 直播状态姬发送按钮
$(document).on('click', '.livestatus-live-send', function () {
    method.sendLiveStatusBarrage($(".livestatus_live_text").val());
});
$(document).on('click', '.livestatus-preparing-send', function () {
    method.sendLiveStatusBarrage($(".livestatus_preparing_text").val());
});
$(document).on('click', '.livestatus-warning-send', function () {
    method.sendLiveStatusBarrage($(".livestatus_warning_text").val());
});
$(document).on('click', '.livestatus-cut-off-send', function () {
    method.sendLiveStatusBarrage($(".livestatus_cut_off_text").val());
});
$(document).on('click', '.livestatus-room-lock-send', function () {
    method.sendLiveStatusBarrage($(".livestatus_room_lock_text").val());
});
// 定时姬
$(document).on('click', '.timer-add-btn', function () {
    method.addTimerRow();
    if (publicData.set && publicData.set.auto_save_set) {
        method.saveSet(true);
    }
});
$(document).on('click', '.timer-delete-btn', function () {
    $(this).closest('li').remove();
    if (publicData.set && publicData.set.auto_save_set) {
        method.saveSet(true);
    }
});
$(document).on('click', '.timer-send-btn', function () {
    var text = $(this).closest('li').find('.timer-text').val();
    method.sendLiveStatusBarrage(text);
});
$(document).on('click', '.timer-copy-btn', function () {
    var text = $(this).closest('li').find('.timer-text').val();
    if (!text) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function () {
            showMessage("已复制到剪贴板!", "success", 2);
        });
    } else {
        var $temp = $('<textarea>');
        $('body').append($temp);
        $temp.val(text).select();
        document.execCommand('copy');
        $temp.remove();
        showMessage("已复制到剪贴板!", "success", 2);
    }
});
// 欢迎凝视姬
$(document).on('click', '.gazeWelcome-add-btn', function () {
    method.addGazeWelcomeRow();
    if (publicData.set && publicData.set.auto_save_set) {
        method.saveSet(true);
    }
});
$(document).on('click', '.gazeWelcome-delete-btn', function () {
    $(this).closest('li').remove();
    if (publicData.set && publicData.set.auto_save_set) {
        method.saveSet(true);
    }
});
$(document).on('click', '.gazeWelcome-send-btn', function () {
    var text = $(this).closest('li').find('.gazeWelcome-text').val();
    method.sendLiveStatusBarrage(text);
});
// 弹幕话术姬
$(document).on('click', '.danmakuStore-add-btn', function () {
    method.addDanmakuStoreRow();
    // 不在此触发自动保存：saveDanmakuStoreList 会过滤空文本行并重新渲染，
    // 导致新添加的空行被移除。用户在输入框中输入内容后会通过 live-save 触发自动保存。
});
$(document).on('click', '.danmakuStore-sort-btn', function () {
    method.sortDanmakuStoreRows();
});
$(document).on('click', '.danmakuStore-delete-btn', function () {
    $(this).closest('li').remove();
    if (publicData.set && publicData.set.auto_save_set) {
        method.saveDanmakuStoreList(true);
    }
});
$(document).on('click', '.danmakuStore-copy-btn', function () {
    var text = $(this).closest('li').find('.danmakuStore-text').val();
    if (!text) return;
    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(text).then(function () {
            showMessage("已复制到剪贴板!", "success", 2);
        });
    } else {
        var $temp = $('<textarea>');
        $('body').append($temp);
        $temp.val(text).select();
        document.execCommand('copy');
        $temp.remove();
        showMessage("已复制到剪贴板!", "success", 2);
    }
});
$(document).on('click', '.danmakuStore-send-btn', function () {
    var text = $(this).closest('li').find('.danmakuStore-text').val();
    if (!text || text.trim() === '') {
        showMessage("弹幕内容不能为空！", "warning", 3);
        return;
    }
    method.sendDanmakuStoreBarrage(text);
});
$(document).on('input', '.danmakuStore-text', function () {
    var len = $(this).val().length;
    var $count = $(this).closest('li').find('.danmakuStore-count');
    $count.text(len);
    if (len > 40) {
        $count.addClass('text-danger');
    } else {
        $count.removeClass('text-danger');
    }
});
const danmuku = {
    // 0弹幕 1礼物 2消息
    type: function (t) {
        if (t === 0) {
            return `<span class="danmu-type">弹幕</span>`;
        } else if (t === 1) {
            return `<span class="danmu-type danmu-type-gift">礼物</span>`;
        } else if (t === 2) {
            return `<span class="danmu-type danmu-type-superchat">留言</span>`;
        } else {
            return `<span class="danmu-type danmu-type-msg">消息</span>`;
        }
    },
    time: function (d,t) {
        if (String(d.timestamp).length == 10) d.timestamp = d.timestamp * 1000;
        if(t===0) {
            return `<span class="danmu-time">` + format(d.timestamp, false) + `</span>`;
        }else if(t===1){
            return `<span class="danmu-time danmu-time-gift">` + format(d.timestamp, false) + `</span>`;
        }else if(t===2){
            return `<span class="danmu-time danmu-time-superchat">` + format(d.timestamp, false) + `</span>`;
        }else{
            return `<span class="danmu-time danmu-time-msg">` + format(d.timestamp, false) + `</span>`;
        }
    },
    only_time: function (d,t) {
        if (String(d.timestamp).length == 10) d.timestamp = d.timestamp * 1000;
        if(t===0) {
            return `<span class="danmu-time">` + format(d, false) + `</span>`;
        }else if(t===1){
            return `<span class="danmu-time danmu-time-gift">` + format(d, false) + `</span>`;
        }else if(t===2){
            return `<span class="danmu-time danmu-time-superchat">` + format(d, false) + `</span>`;
        }else{
            return `<span class="danmu-time danmu-time-msg">` + format(d, false) + `</span>`;
        }
    },
    medal: function (d) {
        if (d.medal_name !== null && d.medal_name !== '') {
            return `<span class="danmu-medal">` + d.medal_name + addSpace(d.medal_level) + `</span>`;
        }
        return '';
    },
    guard: function (d) {
        if (d.uguard > 0) {
            return `<span class="danmu-guard">舰</span>`;
        } else {
            return '';
        }
    },
    vip: function (d) {
        if (d.vip === 1 || d.svip === 1) {
            return `<span class="danmu-vip">爷</span>`;
        } else {
            return '';
        }
    },
    manager: function (d) {
        if (d.manager > 0) {
            if (d.manager > 1) {
                return `<span class="danmu-manager">播</span>`;
            } else {
                return `<span class="danmu-manager">管</span>`;
            }
        } else {
            return '';
        }
    },
    ul: function (d) {
        if (d.ulevel != null) {
            return `<span class="danmu-ul">UL` + addSpace(d.ulevel) + `</span>`;
        }
        return '';
    },
    dname: function (d) {
        let clazz = "";
        if (d.uguard > 0) clazz = "name-guard";
        if (d.manager > 0) clazz = "name-manager";
        return `<a href="javascript:;"><span class="danmu-name` + (clazz === "" ? "" : (" " + clazz)) + `">` + d.uname + `:</span></a>`;
    },
    dmessage: function (d) {
        return `<span class="danmu-text">` + d.msg + `</span>`;
    },
    gname: function (d) {
        let clazz = "";
        if (d.uguard > 0) clazz = "name-guard";
        return `<a href="javascript:;"><span class="danmu-name` + (clazz === "" ? "" : (" " + clazz)) + `">` + d.uname + `</span></a>`;
    },
    gguard: function (d) {
        if (d.guard_level) {
            return `<span class="danmu-guard">舰</span>`;
        } else {
            return '';
        }
    },
    gmessage: function (d) {
        return `<span class="danmu-text">` + d.action + `了 ` + `<span class="danmu-text-gift">`+d.giftName+`</span>` + ` x ` + d.num + `</span>`;
    },
    stext: function (d) {
        return `<span class="danmu-text">留言了` + d.time + `秒说:` + `<span class="danmu-text-superchat">`+d.message+`</span>` + `</span>`;
    },
    block_type: function (d) {
        if (d.operator === 1) {
            return "房管";
        } else {
            return "主播";
        }
    },
    tips: function (d) {
        return `<div class="danmu-tips" uid="` + d.uid + `"><ul class="danmu-tips-ul"><li class="danmu-tips-li" data-bs-toggle="modal" data-bs-target="#block-model">禁言</li><li class="danmu-tips-li">查看</li><li class="danmu-tips-li">关闭</li></ul></div>`;
    },
    danmu: function (type, d) {
        var type_index = 0;
        switch (type) {
            case "danmu":
                return `<div class="danmu-child" uid="` + d.uid + `">` + danmuku.type(type_index) + danmuku.time(d,type_index) + danmuku.medal(d) + danmuku.guard(d) + danmuku.vip(d) + danmuku.manager(d) + danmuku.ul(d) + danmuku.dname(d) + danmuku.dmessage(d) + danmuku.tips(d) + `</div>`;
            case "gift":
                type_index=1;
                d.timestamp = d.timestamp * 1000;
                return `<div class="danmu-child" uid="` + d.uid + `">` + danmuku.type(type_index) + danmuku.time(d,type_index) + danmuku.gguard(d) + danmuku.gname(d) + danmuku.gmessage(d) + danmuku.tips(d) + `</div>`;
            case "superchat":
                type_index=2;
                d.start_time = d.start_time * 1000;
                d.timestamp = d.start_time;
                d.uguard = d.user_info.guard_level;
                d.manager = d.user_info.manager;
                d.uname = d.user_info.uname;
                return `<div class="danmu-child" uid="` + d.uid + `">` + danmuku.type(type_index) + danmuku.time(d,type_index) + danmuku.dname(d) + danmuku.stext(d) + danmuku.tips(d) + `</div>`;
            case "welcomeVip":
                type_index=4;
                return `<div class="danmu-child" uid="` + d.uid + `">` + danmuku.type(type_index) + danmuku.only_time(getTimestamp(),type_index) + `<span class="danmu-text">欢迎</span><a href="javascript:;"><span class="danmu-name">` + d.uname + `</span></a><span class="danmu-text">老爷进入直播间</span>` + danmuku.tips(d) + `</div>`;
            case "welcomeGuard":
                type_index=4;
                return `<div class="danmu-child" uid="` + d.uid + `">` + danmuku.type(type_index) + danmuku.only_time(getTimestamp(),type_index) + `<span class="danmu-text">欢迎</span><a href="javascript:;"><span class="danmu-name">` + d.username + `</span></a><span class="danmu-text">舰长进入直播间</span>` + danmuku.tips(d) + `</div>`;
            case "block":
                type_index=4;
                return `<div class="danmu-child" uid="` + d.uid + `">` + danmuku.type(type_index) + danmuku.only_time(getTimestamp(),type_index) + `<a href="javascript:;"><span class="danmu-name">` + d.uname + `</span></a><span class="danmu-text">已被` + danmuku.block_type(d) + `禁言</span>` + danmuku.tips(d) + `</div>`;
            case "follow":
                type_index=4;
                return `<div class="danmu-child" uid="` + d.uid + `">` + danmuku.type(type_index) + danmuku.time(d,type_index) + `<a href="javascript:;"><span class="danmu-name">` + d.uname + `</span></a><span class="danmu-text">关注了直播间</span>` + danmuku.tips(d) + `</div>`;
            case "welcome":
                type_index=4;
                return `<div class="danmu-child" uid="` + d.uid + `">` + danmuku.type(type_index) + danmuku.time(d,type_index) + `<a href="javascript:;"><span class="danmu-name">` + d.uname + `</span></a><span class="danmu-text"> 进入了直播间</span>` + danmuku.tips(d) + `</div>`;
            default:
                return "";
        }
    },
}
const publicData = {
    set: {},
}
const biliBadListState = {
    page: 1,
    pageSize: 10,
    total: 0,
    list: []
};
const pnData = {
    list: [],
    page: 1,
    pageSize: 10,
    // 排序状态：最近点击的列排在数组最前面，优先级最高
    // [{col:'score', dir:'asc'}, {col:'uid', dir:'asc'}, {col:'name', dir:'asc'}]
    sortState: [
        {col: 'score', dir: 'asc'},
        {col: 'uid', dir: 'asc'},
        {col: 'name', dir: 'asc'}
    ],
}
const autoBlockData = {
    list: [],
    page: 1,
    pageSize: 10,
}
const danmakuStoreData = {
    list: [],
    sortAsc: true
}
const method = {
    saveSet: function (silent) {
        let c1 = false;
        let c2 = false;
        let c3 = false;
        let c4 = false;
        let c5 = false;
        let c6 = false;
        let c7 = false;
        let c8 = false;
        let c9 = false;
        let c10 = false;
        let set = {
            "thank_gift": {
                "giftStrings": [],
                "thankGiftRuleSets": [],
                "codeStrings": [],
            },
            "advert": {},
            "follow": {},
            "reply": {"autoReplySets": []},
            "welcome": {},
            "black": {
                "names": [],
                "uids": []
            },
            "live_status": {},
            "timer": {},
            "auto_block": {}
        };
        set.is_auto = $(".is_autoStart").is(
            ':checked');
        set.win_auto_openSet = $(".win_auto_openSet").is(':checked');
        set.auto_save_set = $(".auto_save_set").is(':checked');
        set.is_barrage = $(".is_barrage").is(
            ':checked');
        set.is_barrage_guard = $(".is_barrage_guard").is(
            ':checked');
        set.is_barrage_vip = $(".is_barrage_vip").is(
            ':checked');
        set.is_barrage_manager = $(".is_barrage_manager").is(':checked');
        set.is_barrage_medal = $(".is_barrage_medal").is(':checked');
        set.is_barrage_ul = $(".is_barrage_ul").is(':checked');
        set.is_barrage_anchor_shield = $(".is_barrage_anchor_shield").is(':checked');
        set.is_block = $(".is_block").is(':checked');
        set.is_cmd = $(".is_cmd").is(':checked');
        set.is_gift = $(".is_gift").is(':checked');
        set.is_gift_free = $(".is_gift_free").is(':checked');
        set.is_welcome_ye = $(".is_welcome").is(':checked');
        set.is_welcome_all = $(".is_welcome_all").is(':checked');
        set.is_follow_dm = $(".is_follow").is(':checked');
        set.log = $(".is_log").is(':checked');
        set.is_watcher_log = $(".is_watcher_log").is(':checked');
        set.is_footprint_record = $(".is_footprint_record").is(':checked');
        set.connect_docket = $(".connect-docket").val();
        set.thank_gift.is_open = $(".thankgift_is_open").is(':checked');
        set.thank_gift.is_live_open = $(".thankgift_is_live_open").is(
            ':checked');
        set.thank_gift.is_open_self = $(".thankgift_is_open_self").is(
            ':checked');
        set.thank_gift.is_tx_shield = $(".thankgift_is_tx_shield").is(
            ':checked');
        set.thank_gift.is_num = $(".thankgift_is_num").is(':checked');
        set.thank_gift.shield_status = Number($(".thankgift_shield_status")
            .find("option:selected").val()) - 1;
        set.thank_gift.list_gift_shield_status = Number($(".thankgift_list_gift_shield_status")
            .find("option:selected").val()) - 1;
        set.thank_gift.list_people_shield_status = Number($(".thankgift_list_people_shield_status")
            .find("option:selected").val()) - 1;
        set.thank_gift.giftStrings = method.giftStrings_handle(set.thank_gift.giftStrings, $(".thankgift_shield").val());
        if ($(".shieldgifts-tbody tr").length > 0) {
            let thankGiftRuleSet = {};
            $(".shieldgifts-tbody tr").each(function (i, v) {
                thankGiftRuleSet.is_open = $(".shieldgifts_open").eq(i).is(':checked');
                thankGiftRuleSet.gift_name = $(".shieldgifts_name").eq(i).val();
                thankGiftRuleSet.status = Number($(".shieldgifts_status").eq(i).find("option:selected").val()) - 1;
                thankGiftRuleSet.num = Number($(".shieldgifts_num").eq(i).val());
                set.thank_gift.thankGiftRuleSets.push(thankGiftRuleSet);
                thankGiftRuleSet = {};
            });
        }
        if ($(".replys-ul li").length > 0) {
            let autoReplySet = {};
            $(".replys-ul li").each(function (i, v) {
                autoReplySet.is_open = $(".reply_open").eq(i).is(':checked');
                autoReplySet.is_accurate = $(".reply_oc").eq(i).is(':checked');
                let keywords = [];
                let shields = [];
                var keyword = $(".reply_keywords").eq(i).val();
                var shield = $(".reply_shields").eq(i).val();
                var reply = $(".reply_rs").eq(i).val();
                if (keyword === null) {
                    keyword="";
                }
                autoReplySet.keywords = method.giftStrings_handle(keywords, keyword);
                autoReplySet.shields = method.giftStrings_handle(shields, shield);
                autoReplySet.reply = reply;
                set.reply.autoReplySets.push(autoReplySet);
                autoReplySet = {};
            });

        }
        set.thank_gift.thank_status = Number($(".thankgift_thank_status")
            .find("option:selected").val()) - 1;
        set.thank_gift.num = Number($(".thankgift_num").val());
        set.thank_gift.delaytime = Number($(".thankgift_delaytime").val());
        set.thank_gift.thank = $(".thankgift_thank").val();
        set.thank_gift.is_guard_report = $(".thankgift_is_guard_report")
            .is(':checked');
        set.thank_gift.is_guard_local = $(".thankgift_is_guard_local")
            .is(':checked');
        set.thank_gift.is_gift_code = $(".thankgift_is_gift_code")
            .is(':checked');
        set.thank_gift.codeStrings = method.codeStrings_handle(set.thank_gift.codeStrings, $(".thankgift_codeStrings").val());
        set.thank_gift.report = $(".thankgift_report").val();
        set.thank_gift.report_barrage = $(".thankgift_barrageReport").val();
        set.advert.is_open = $(".advert_is_open").is(':checked');
        set.advert.is_live_open = $(".advert_is_live_open").is(':checked');
        set.advert.status = Number($(".advert_status").find(
            "option:selected").val()) - 1;
        set.advert.time = Number($(".advert_time").val());
        set.advert.time2 = Number($(".advert_time2").val());
        set.advert.adverts = $(".advert_adverts").val();
        set.follow.is_open = $(".follow_is_open").is(':checked');
        set.follow.is_live_open = $(".follow_is_live_open").is(':checked');
        set.follow.is_tx_shield = $(".follow_tx_shield").is(':checked');
        set.follow.is_rd_shield = $(".follow_rd_shield").is(':checked');
        set.follow.num = Number($(".follow_num").val());
        set.follow.follows = $(".follow_follows").val();
        set.follow.delaytime = Number($(".thankfollow_delaytime").val());
        set.welcome.is_open = $(".welcome_is_open").is(':checked');
        set.welcome.is_open_self = $(".welcome_is_open_self").is(':checked');
        set.welcome.is_live_open = $(".welcome_is_live_open").is(':checked');
        set.welcome.is_tx_shield = $(".welcome_tx_shield").is(':checked');
        set.welcome.is_rd_shield = $(".welcome_rd_shield").is(':checked');
        set.welcome.num = Number($(".welcome_num").val());
        set.welcome.welcomes = $(".welcome_welcomes").val();
        set.welcome.delaytime = Number($(".thankwelcome_delaytime").val());
        set.welcome.list_people_shield_status = Number($(".welcome_list_people_shield_status")
            .find("option:selected").val()) - 1;
        set.reply.is_open = $(".replys_is_open").is(':checked');
        set.reply.is_live_open = $(".replys_is_live_open").is(':checked');
        set.reply.is_open_self = $(".replys_is_open_self").is(':checked');
        set.reply.time = Number($(".replys_time").val());
        set.reply.list_people_shield_status = Number($(".replys_list_people_shield_status")
            .find("option:selected").val()) - 1;
        set.live_status.is_live_open = $(".livestatus_live_open").is(':checked');
        set.live_status.live_text = $(".livestatus_live_text").val();
        set.live_status.is_preparing_open = $(".livestatus_preparing_open").is(':checked');
        set.live_status.preparing_text = $(".livestatus_preparing_text").val();
        set.live_status.is_warning_open = $(".livestatus_warning_open").is(':checked');
        set.live_status.warning_text = $(".livestatus_warning_text").val();
        set.live_status.is_cut_off_open = $(".livestatus_cut_off_open").is(':checked');
        set.live_status.cut_off_text = $(".livestatus_cut_off_text").val();
        set.live_status.is_room_lock_open = $(".livestatus_room_lock_open").is(':checked');
        set.live_status.room_lock_text = $(".livestatus_room_lock_text").val();
        set.timer.is_open = $(".timer_is_open").is(':checked');
        set.timer.timerSets = [];
        if ($("#timer-ul li").length > 0) {
            var timerSet = {};
            $("#timer-ul li").each(function (i, v) {
                timerSet.is_open = $(this).find(".timer-row-open").is(':checked');
                timerSet.time = $(this).find(".timer-row-time").val();
                timerSet.text = $(this).find(".timer-text").val();
                set.timer.timerSets.push(timerSet);
                timerSet = {};
            });
            set.timer.timerSets.sort(function(a, b) {
                return (a.time || "00:00").localeCompare(b.time || "00:00");
            });
        }
        set.gaze_welcome = {};
        set.gaze_welcome.is_open = $(".gazeWelcome_is_open").is(':checked');
        set.gaze_welcome.cooldown_time = parseInt($(".gazeWelcome-cooldown-time").val()) || 3;
        set.gaze_welcome.gazeWelcomeSets = [];
        if ($("#gazeWelcome-ul li").length > 0) {
            var gazeSet = {};
            $("#gazeWelcome-ul li").each(function (i, v) {
                gazeSet.is_open = $(this).find(".gazeWelcome-row-open").is(':checked');
                gazeSet.username = $(this).find(".gazeWelcome-username").val();
                gazeSet.text = $(this).find(".gazeWelcome-text").val();
                set.gaze_welcome.gazeWelcomeSets.push(gazeSet);
                gazeSet = {};
            });
        }
        set.auto_block.is_auto_block = $(".is_auto_block").is(':checked');
        set.auto_block.block_score = parseInt($(".auto-block-score").val()) || -1;
        set.auto_block.block_interval = parseInt($(".auto-block-interval").val()) || 120;
        if ($(".follow_is_open").is(':checked')) {
            if ($(".follow_follows").val().trim() !== null
                && $(".follow_follows").val().trim() !== "") {
            } else {
                c1 = true;
                if (!silent) showMessage("感谢关注语不能为空！配置保存失败!", "danger",3);
            }
            if (Number($(".follow_num").val()) > 0) {

            } else {
                c5 = true;
                if (!silent) showMessage("感谢关注必须大于0！配置保存失败!", "danger",3);
            }
        }
        if ($(".welcome_is_open").is(':checked')) {
            if ($(".welcome_welcomes").val().trim() !== null
                && $(".welcome_welcomes").val().trim() !== "") {
            } else {
                c9 = true;
                if (!silent) showMessage("感谢欢迎语不能为空！配置保存失败!", "danger",3);
            }
            if (Number($(".welcome_num").val()) > 0) {

            } else {
                c10 = true;
                if (!silent) showMessage("感谢欢迎必须大于0！配置保存失败!", "danger",3);
            }
        }
        if ($(".thankgift_is_open").is(':checked')) {
            if ($(".thankgift_thank").val().trim() !== null
                && $(".thankgift_thank").val().trim() !== "") {

            } else {
                c2 = true;
                if (!silent) showMessage("感谢礼物语不能为空！配置保存失败!", "danger",3);
            }
            if ($(".thankgift_is_guard_report").is(':checked')) {
                if ($(".thankgift_report").val().trim() !== null
                    && $(".thankgift_report").val().trim() !== "") {
                    if ($(".thankgift_report").val().length >= 500) {
                        c6 = true;
                        if (!silent) showMessage("上舰回复语不能超过500字！配置保存失败!", "danger",3);
                    }
                } else {
                    c3 = true;
                    if (!silent) showMessage("上舰回复语不能为空！配置保存失败!", "danger",3);
                }
            }
        }
        if ($(".advert_is_open").is(':checked')) {
            if ($(".advert_adverts").val().trim() !== null
                && $(".advert_adverts").val().trim() !== "") {
            } else {
                c4 = true;
                if (!silent) showMessage("广告语不能为空！配置保存失败!", "danger",3);
            }

        }
        $(".shieldgifts-tbody").children("tr").each(function (i, v) {
            if ($(".shieldgifts_name").eq(i).val().trim() == "") {
                c7 = true;
            }
        })
        if (c7) {
            if (!silent) showMessage("自定义规则不能为空！配置保存失败!", "danger",3);
        }
        $(".replys-ul").children("li").each(function (i, v) {
            if ($(".reply_keywords").eq(i).val() === "") {
                // c8 = true;
                // v.remove();
                showMessage("自动回复姬的关键字不能为空！", "warning",3);
            } else {

            }
        });
        if ($(".card-body").find(".logined").length > 0) {
            if (!c1 && !c2 && !c3 && !c4 && !c5 && !c6 && !c7 && !c8 && !c9 && !c10) {
                if (!silent) {
                    publicData.set = method.initSet(set);
                }
                var edition = $("#app-version").attr("data-version");
                set.edition = edition;
                var result = method.sendSet(set);
                if (result==1) {
                    method.saveDanmakuStoreList(true);
                    if (!silent) {
                        if (!publicData.set.auto_save_set) {
                            showMessage("保存配置成功!", "success",3);
                        }else{
                            showMessage("保存配置成功!", "success",2);
                        }
                    }
                }else if(result==2){
                    location.reload();
                }else {
                    if (!silent) showMessage("修改配置失败!", "danger",3);
                }
            } else {
                if (!silent) showMessage("修改配置失败!", "danger",3);
            }
        } else {
            if (!silent) method.initSet(set);
            var edition = $("#app-version").attr("data-version");
            set.edition = edition;
            var result = method.sendSet(set);
            if (result == 1) {
                method.saveDanmakuStoreList(true);
                if (!silent) showMessage("保存配置成功!", "success",3);
            }else if(result==2){
                location.reload();
            }else {
                if (!silent) showMessage("修改配置失败!", "danger",3);
            }
        }
    },
    getSet: function () {
        "use strict";
        let json = null;
        $.ajax({
            url: '../getSet',
            async: false,
            cache: false,
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200") {
                    json = data.result;
                }
            }
        });
        return json;
    },
    sendSet: function (set) {
        "use strict";
        let flag = 0;
        $.ajax({
            url: '../sendSet',
            data: {
                set: JSON.stringify(set)
            },
            async: false,
            cache: false,
            type: 'POST',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200") {
                    flag = data.result
                }
            }
        });
        return flag;
    },
    sendLiveStatusBarrage: function (text) {
        "use strict";
        if (!text || text.trim() === '') {
            showMessage("弹幕内容不能为空！", "warning", 3);
            return;
        }
        $.ajax({
            url: '../sendBarrage',
            async: false,
            cache: false,
            type: 'POST',
            data: { text: text },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result == 1) {
                    showMessage("发送成功!", "success", 3);
                } else {
                    showMessage("发送失败!", "danger", 3);
                }
            }
        });
    },
    addTimerRow: function (time, text, isOpen) {
        var defaultTime = time;
        if (!defaultTime) {
            var d = new Date();
            d.setMinutes(d.getMinutes() + 3);
            defaultTime = ('0' + d.getHours()).slice(-2) + ':' + ('0' + d.getMinutes()).slice(-2);
        }
        var t = defaultTime;
        var txt = text || '';
        var checked = isOpen ? 'checked' : '';
        var li = '<li style="margin-bottom:5px">' +
            '<div class="row align-items-center" style="--bs-gutter-x:5px">' +
            '<div class="col-auto">' +
            '<label class="form-check-label">' +
            '<input class="form-check-input timer-row-open live-save" type="checkbox" ' + checked + '>' +
            '</label>' +
            '</div>' +
            '<div class="col-auto">' +
            '<input class="form-control form-control-sm timer-row-time live-save" type="time" value="' + t + '" step="60" style="width:110px">' +
            '</div>' +
            '<div class="col">' +
            '<input class="form-control form-control-sm timer-text live-save" placeholder="定时发送的弹幕内容" value="' + method._escapeHtml(txt) + '">' +
            '</div>' +
            '<div class="col-auto">' +
            '<button class="btn btn-sm btn-secondary timer-copy-btn">复制</button>' +
            '</div>' +
            '<div class="col-auto">' +
            '<button class="btn btn-sm btn-primary timer-send-btn">发送</button>' +
            '</div>' +
            '<div class="col-auto">' +
            '<button class="btn btn-sm btn-danger timer-delete-btn">删除</button>' +
            '</div>' +
            '</div>' +
            '</li>';
        $("#timer-ul").append(li);
    },
    renderTimerRows: function (timerSets) {
        $("#timer-ul").empty();
        if (!timerSets) return;
        for (var i = 0; i < timerSets.length; i++) {
            var item = timerSets[i];
            method.addTimerRow(item.time, item.text, item.is_open);
        }
    },
    _sortTimerRows: function () {
        var $ul = $("#timer-ul");
        var $lis = $ul.children("li").get();
        $lis.sort(function (a, b) {
            var ta = $(a).find(".timer-row-time").val() || "00:00";
            var tb = $(b).find(".timer-row-time").val() || "00:00";
            return ta.localeCompare(tb);
        });
        $.each($lis, function (i, li) {
            $ul.append(li);
        });
    },
    addGazeWelcomeRow: function (username, text, isOpen) {
        var uname = username || '';
        var txt = text || '';
        var checked = isOpen ? 'checked' : '';
        var li = '<li style="margin-bottom:5px">' +
            '<div class="row align-items-center" style="--bs-gutter-x:5px">' +
            '<div class="col-auto">' +
            '<label class="form-check-label">' +
            '<input class="form-check-input gazeWelcome-row-open live-save" type="checkbox" ' + checked + '>' +
            '</label>' +
            '</div>' +
            '<div class="col-auto">' +
            '<input class="form-control form-control-sm gazeWelcome-username live-save" type="text" placeholder="用户名(模糊匹配, #包裹#为正则)" value="' + method._escapeHtml(uname) + '" style="width:200px">' +
            '</div>' +
            '<div class="col">' +
            '<input class="form-control form-control-sm gazeWelcome-text live-save" placeholder="欢迎弹幕内容，支持%uNames%等变量" value="' + method._escapeHtml(txt) + '">' +
            '</div>' +
            '<div class="col-auto">' +
            '<button class="btn btn-sm btn-primary gazeWelcome-send-btn">发送</button>' +
            '</div>' +
            '<div class="col-auto">' +
            '<button class="btn btn-sm btn-danger gazeWelcome-delete-btn">删除</button>' +
            '</div>' +
            '</div>' +
            '</li>';
        $("#gazeWelcome-ul").append(li);
    },
    renderGazeWelcomeRows: function (gazeWelcomeSets) {
        $("#gazeWelcome-ul").empty();
        if (!gazeWelcomeSets) return;
        for (var i = 0; i < gazeWelcomeSets.length; i++) {
            var item = gazeWelcomeSets[i];
            method.addGazeWelcomeRow(item.username, item.text, item.is_open);
        }
    },
    _escapeHtml: function (str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    },
    addDanmakuStoreRow: function (text, type) {
        var txt = text || '';
        // 兼容旧格式：如果 text 是对象则解包
        if (typeof txt === 'object' && txt !== null) {
            type = txt.type || '';
            txt = txt.text || '';
        }
        var typ = type || '';
        var len = txt.length;
        var dangerClass = len > 40 ? ' text-danger' : '';
        var li = '<li style="margin-bottom:5px">' +
            '<div class="row align-items-center" style="--bs-gutter-x:5px">' +
            '<div class="col-auto">' +
            '<input class="form-control form-control-sm danmakuStore-type live-save" placeholder="类型" value="' + method._escapeHtml(typ) + '" style="width:80px">' +
            '</div>' +
            '<div class="col">' +
            '<input class="form-control form-control-sm danmakuStore-text live-save" placeholder="暂存的话术弹幕内容" value="' + method._escapeHtml(txt) + '">' +
            '</div>' +
            '<div class="col-auto">' +
            '<span class="danmakuStore-count' + dangerClass + '" style="min-width:40px;display:inline-block;text-align:center">' + len + '</span>' +
            '</div>' +
            '<div class="col-auto">' +
            '<button class="btn btn-sm btn-secondary danmakuStore-copy-btn">复制</button>' +
            '</div>' +
            '<div class="col-auto">' +
            '<button class="btn btn-sm btn-primary danmakuStore-send-btn">发送</button>' +
            '</div>' +
            '<div class="col-auto">' +
            '<button class="btn btn-sm btn-danger danmakuStore-delete-btn">删除</button>' +
            '</div>' +
            '</div>' +
            '</li>';
        $("#danmakuStore-ul").append(li);
    },
    renderDanmakuStoreRows: function (items) {
        $("#danmakuStore-ul").empty();
        if (!items) return;
        for (var i = 0; i < items.length; i++) {
            method.addDanmakuStoreRow(items[i]);
        }
    },
    sortDanmakuStoreRows: function () {
        danmakuStoreData.sortAsc = !danmakuStoreData.sortAsc;
        var $btn = $(".danmakuStore-sort-btn");
        $btn.text(danmakuStoreData.sortAsc ? '按类型排序 ▲' : '按类型排序 ▼');
        var $ul = $("#danmakuStore-ul");
        var $lis = $ul.find("li").get();
        $lis.sort(function (a, b) {
            var ta = $(a).find(".danmakuStore-type").val() || '';
            var tb = $(b).find(".danmakuStore-type").val() || '';
            var cmp = ta.localeCompare(tb);
            return danmakuStoreData.sortAsc ? cmp : -cmp;
        });
        $.each($lis, function (i, v) {
            $ul.append(v);
        });
        if (publicData.set && publicData.set.auto_save_set) {
            method.saveDanmakuStoreList(true);
        }
    },
    loadDanmakuStoreList: function () {
        $.ajax({
            url: '../getDanmakuStore',
            async: false,
            cache: false,
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result && data.result.items && data.result.items.length > 0) {
                    danmakuStoreData.list = data.result.items;
                    // 兼容旧版字符串格式
                    for (var i = 0; i < danmakuStoreData.list.length; i++) {
                        if (typeof danmakuStoreData.list[i] === 'string') {
                            danmakuStoreData.list[i] = {type: '', text: danmakuStoreData.list[i]};
                        }
                    }
                } else {
                    // 迁移：检查set.json中的旧数据
                    if (publicData.set && publicData.set.danmaku_store && publicData.set.danmaku_store.items && publicData.set.danmaku_store.items.length > 0) {
                        danmakuStoreData.list = publicData.set.danmaku_store.items;
                        for (var i = 0; i < danmakuStoreData.list.length; i++) {
                            if (typeof danmakuStoreData.list[i] === 'string') {
                                danmakuStoreData.list[i] = {type: '', text: danmakuStoreData.list[i]};
                            }
                        }
                        // 自动保存到独立文件（静默迁移，不弹提示）
                        method.saveDanmakuStoreList(true);
                    } else {
                        danmakuStoreData.list = [];
                    }
                }
            }
        });
        method.renderDanmakuStoreRows(danmakuStoreData.list);
    },
    saveDanmakuStoreList: function (silent) {
        var list = [];
        $("#danmakuStore-ul li").each(function (i, v) {
            var text = $(this).find(".danmakuStore-text").val();
            var type = $(this).find(".danmakuStore-type").val() || '';
            if (text && text.trim() !== '') {
                list.push({type: type, text: text});
            }
        });
        list.sort(function(a, b) {
            return (a.type || '').localeCompare(b.type || '') || (a.text || '').localeCompare(b.text || '');
        });
        danmakuStoreData.list = list;
        var payload = { type: "话术", items: list };
        var result = 0;
        $.ajax({
            url: '../saveDanmakuStore',
            async: false,
            cache: false,
            type: 'POST',
            data: { data: JSON.stringify(payload) },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200") {
                    result = data.result;
                }
            }
        });
        if (!silent) {
            if (result === 0) {
                showMessage("话术列表保存成功!", "success", 3);
            } else {
                showMessage("话术列表保存失败!", "danger", 3);
            }
        }
        // 静默模式下跳过重新渲染，避免中断用户正在进行的编辑
        if (!silent) {
            method.renderDanmakuStoreRows(danmakuStoreData.list);
        }
    },
    sendDanmakuStoreBarrage: function (text) {
        if (text.length > 40) {
            var chunks = [];
            for (var i = 0; i < text.length; i += 40) {
                chunks.push(text.substring(i, i + 40));
            }
            for (var j = 0; j < chunks.length; j++) {
                method._sendBarrageChunk(chunks[j], j * 1500);
            }
        } else {
            method.sendLiveStatusBarrage(text);
        }
    },
    _sendBarrageChunk: function (text, delay) {
        setTimeout(function () {
            $.ajax({
                url: '../sendBarrage',
                async: true,
                cache: false,
                type: 'POST',
                data: { text: text },
                dataType: 'json',
            });
        }, delay);
    },
    initSet: function (set) {
        "use strict";
        if (set != null) {
            $(".is_autoStart").prop('checked',
                set.is_auto);
            $(".auto_save_set").prop('checked',
                set.auto_save_set);
            $(".win_auto_openSet").prop('checked', set.win_auto_openSet);
            $(".is_barrage").prop('checked',
                set.is_barrage);
            $(".is_barrage_guard").prop('checked',
                set.is_barrage_guard);
            $(".is_barrage_vip").prop('checked',
                set.is_barrage_vip);
            $(".is_barrage_manager").prop('checked', set.is_barrage_manager);
            $(".is_cmd").prop('checked', set.is_cmd);
            $(".is_barrage_medal").prop('checked', set.is_barrage_medal);
            $(".is_barrage_ul").prop('checked', set.is_barrage_ul);
            $(".is_barrage_anchor_shield").prop('checked', set.is_barrage_anchor_shield);
            $(".is_block").prop('checked', set.is_block);
            $(".is_gift").prop('checked', set.is_gift);
            $(".is_gift_free").prop('checked', set.is_gift_free);
            $(".is_welcome").prop('checked', set.is_welcome_ye);
            $(".is_welcome_all").prop('checked', set.is_welcome_all);
            $(".is_follow").prop('checked', set.is_follow_dm);
            $(".is_log").prop('checked', set.log);
            $(".is_watcher_log").prop('checked', set.is_watcher_log);
            $(".is_footprint_record").prop('checked', set.is_footprint_record);
            $(".connect-docket").val(set.connect_docket);
            $(".thankgift_is_open").prop('checked', set.thank_gift.is_open);
            $(".thankgift_is_live_open").prop('checked',
                set.thank_gift.is_live_open);
            $(".thankgift_is_open_self").prop('checked',
                set.thank_gift.is_open_self);
            $(".thankgift_is_tx_shield").prop('checked',
                set.thank_gift.is_tx_shield);
            $(".thankgift_is_num").prop('checked',
                set.thank_gift.is_num);
            $(".thankgift_shield_status").find("option").eq(
                set.thank_gift.shield_status).prop('selected', true);
            $(".thankgift_list_gift_shield_status").find("option").eq(
                set.thank_gift.list_gift_shield_status).prop('selected', true);
            $(".thankgift_list_people_shield_status").find("option").eq(
                set.thank_gift.list_people_shield_status).prop('selected', true);
            $(".thankgift_shield").val(method.giftStrings_method(set.thank_gift.giftStrings));
            $(".thankgift_codeStrings").val(method.codeStrings_method(set.thank_gift.codeStrings));
            method.shieldgifts_each(set.thank_gift.thankGiftRuleSets);
            method.replys_each(set.reply.autoReplySets);
            // $(".thankgift_thankGiftRuleSets").val(
            // set.thank_gift.thankGiftRuleSets);// test
            $(".thankgift_thank_status").find("option").eq(
                set.thank_gift.thank_status).prop('selected', true);
            $(".thankgift_num").val(set.thank_gift.num);
            $(".thankgift_delaytime").val(set.thank_gift.delaytime);
            $(".thankgift_thank").val(set.thank_gift.thank);
            $(".thankgift_is_guard_report").prop('checked',
                set.thank_gift.is_guard_report);
            $(".thankgift_is_gift_code").prop('checked',
                set.thank_gift.is_gift_code);
            $(".thankgift_is_guard_local").prop('checked',
                set.thank_gift.is_guard_local);
            $(".thankgift_report").val(set.thank_gift.report);
            $(".thankgift_barrageReport").val(set.thank_gift.report_barrage);
            $(".advert_is_open").prop('checked', set.advert.is_open);
            $(".advert_is_live_open").prop('checked', set.advert.is_live_open);
            $(".advert_status").find("option").eq(set.advert.status).prop(
                'selected', true)
            $(".advert_time").val(set.advert.time);
            $(".advert_time2").val(set.advert.time2);
            $(".advert_adverts").val(set.advert.adverts);
            $(".follow_is_open").prop('checked', set.follow.is_open);
            $(".follow_is_live_open").prop('checked', set.follow.is_live_open);
            $(".follow_tx_shield").prop('checked', set.follow.is_tx_shield);
            $(".follow_rd_shield").prop('checked', set.follow.is_rd_shield);
            $(".follow_num").val(set.follow.num);
            $(".follow_follows").val(set.follow.follows);
            $(".thankfollow_delaytime").val(set.follow.delaytime);
            $(".welcome_is_open").prop('checked', set.welcome.is_open);
            $(".welcome_is_open_self").prop('checked', set.welcome.is_open_self);
            $(".welcome_is_live_open").prop('checked', set.welcome.is_live_open);
            $(".welcome_tx_shield").prop('checked', set.welcome.is_tx_shield);
            $(".welcome_rd_shield").prop('checked', set.welcome.is_rd_shield);
            $(".welcome_num").val(set.welcome.num);
            $(".welcome_welcomes").val(set.welcome.welcomes);
            $(".thankwelcome_delaytime").val(set.welcome.delaytime);
            $(".welcome_list_people_shield_status").find("option").eq(
                set.welcome.list_people_shield_status).prop('selected', true);
            $(".replys_is_open").prop('checked',
                set.reply.is_open);
            $(".replys_is_live_open").prop('checked',
                set.reply.is_live_open);
            $(".replys_is_open_self").prop('checked',
                set.reply.is_open_self);
            $(".replys_time").val(set.reply.time);
            $(".replys_list_people_shield_status").find("option").eq(
                set.reply.list_people_shield_status).prop('selected', true);
            if (set.live_status) {
                $(".livestatus_live_open").prop('checked', set.live_status.is_live_open);
                $(".livestatus_live_text").val(set.live_status.live_text);
                $(".livestatus_preparing_open").prop('checked', set.live_status.is_preparing_open);
                $(".livestatus_preparing_text").val(set.live_status.preparing_text);
                $(".livestatus_warning_open").prop('checked', set.live_status.is_warning_open);
                $(".livestatus_warning_text").val(set.live_status.warning_text);
                $(".livestatus_cut_off_open").prop('checked', set.live_status.is_cut_off_open);
                $(".livestatus_cut_off_text").val(set.live_status.cut_off_text);
                $(".livestatus_room_lock_open").prop('checked', set.live_status.is_room_lock_open);
                $(".livestatus_room_lock_text").val(set.live_status.room_lock_text);
            }
            if (set.timer) {
                $(".timer_is_open").prop('checked', set.timer.is_open);
                if (set.timer.timerSets) {
                    // sort by time ascending
                    set.timer.timerSets.sort(function(a, b) {
                        return (a.time || "00:00").localeCompare(b.time || "00:00");
                    });
                    method.renderTimerRows(set.timer.timerSets);
                }
            }
            if (set.gaze_welcome) {
                $(".gazeWelcome_is_open").prop('checked', set.gaze_welcome.is_open);
                $(".gazeWelcome-cooldown-time").val(set.gaze_welcome.cooldown_time != null ? set.gaze_welcome.cooldown_time : 3);
                if (set.gaze_welcome.gazeWelcomeSets) {
                    method.renderGazeWelcomeRows(set.gaze_welcome.gazeWelcomeSets);
                }
            }
            if (set.auto_block) {
                $(".is_auto_block").prop('checked', set.auto_block.is_auto_block);
                $(".auto-block-score").val(set.auto_block.block_score != null ? set.auto_block.block_score : -1);
                $(".auto-block-interval").val(set.auto_block.block_interval != null ? set.auto_block.block_interval : 3);
            }
            if (set.local_black_white_list) {
                $(".is_bwlist_open").prop('checked', set.local_black_white_list.is_open);
            }


            /* 处理？ */
            if (Number($(".thankgift_shield_status")
                .children("option:selected").val()) !== 1) {
                $(".thankgift_shield").hide();
                $(".thankgift_list_gift_shield_status").hide();
            } else {
                $(".thankgift_shield").show();
                $(".thankgift_list_gift_shield_status").show();
            }
            if (Number($(".thankgift_shield_status")
                .children("option:selected").val()) !== 4) {
                $("#gift-shield-btn").hide();
            } else {
                $("#gift-shield-btn").show();
            }
            if (Number($(".thankgift_list_gift_shield_status").children(
                "option:selected").val()) !== 1) {
                //白名单
                $(".thankgift_shield").attr('placeholder',
                    "白名单模式：自定义通过礼物名字，以 中文逗号(，)为分割；示例：\n辣条，亿圆，友谊的小船\n注意：为空那时候是什么都不屏蔽，仅在自定义模式下有用\n默认黑名单，相反白名单（仅感谢填写的）");
                $(".thankgift_shield")
                    .attr('title',
                        '白名单模式：这里填写自定义通过礼物名字，以 中文逗号(，)为分割；示例：<br/>辣条，亿圆，友谊的小船<br/><span class=\'red-font\'>注意：为空那时候是什么都不屏蔽，仅在自定义模式下有用<br/>默认黑名单，相反白名单（仅感谢填写的）</span>');

            } else {
                //黑名单
                $(".thankgift_shield").attr('placeholder',
                    "黑名单模式：自定义屏蔽礼物名字，以 中文逗号(，)为分割；示例：\n辣条，亿圆，友谊的小船\n注意：为空那时候是什么都不屏蔽，仅在自定义模式下有用\n默认黑名单，相反白名单（仅感谢填写的）");
                $(".thankgift_shield")
                    .attr('title',
                        '黑名单模式：这里填写自定义屏蔽礼物名字，以 中文逗号(，)为分割；示例：<br/>辣条，亿圆，友谊的小船<br/><span class=\'red-font\'>注意：为空那时候是什么都不屏蔽，仅在自定义模式下有用<br/>默认黑名单，相反白名单（仅感谢填写的）</span>');

            }
            switch (Number($(".thankgift_thank_status").children(
                "option:selected").val())) {
                case 1:
                    $(".thankgift_thank").attr('placeholder',
                        "感谢%uName%%Type%的%GiftName% x%Num%~");
                    $(".thankgift_thank")
                        .attr(
                            'title',
                            '模式:单人单种<br/>多条语句时候注意以回车为分割每条语句,多条语句会随机发送其中一条<br/>感谢语，可选参数<br/> <span class=\'red-font\'>%uName%</span>送礼人名称<br/><span class=\'red-font\'>%Type%</span>赠送类型<br/><span class=\'red-font\'>%GiftName%</span>礼物名称<br/><span class=\'red-font\'>%Num%</span>礼物数量');
                    break;
                case 2:
                    $(".thankgift_thank").attr('placeholder',
                        "感謝%uName%贈送的%Gifts%~");
                    $(".thankgift_thank")
                        .attr('title',
                            '模式:单人多种<br/>多条语句时候注意以回车为分割每条语句,多条语句会随机发送其中一条<br/>感谢语，可选参数<br/> <span class=\'red-font\'>%uName%</span>送礼人名称<br/><span class=\'red-font\'>%Gifts%</span>礼物和数量的集合以逗号隔开');
                    break
                case 3:
                    $(".thankgift_thank").attr('placeholder',
                        "感謝%uNames%贈送的%Gifts%~");
                    $(".thankgift_thank")
                        .attr('title',
                            '模式:多人多种<br/>多条语句时候注意以回车为分割每条语句,多条语句会随机发送其中一条<br/>感谢语，可选参数<br/> <span class=\'red-font\'>%uNames%</span>送礼人名称集合<br/><span class=\'red-font\'>%Gifts%</span>礼物和数量的集合以逗号隔开');
                    break
                default:
                    break;
            }
            if ($(".thankgift_is_guard_report").is(':checked')) {
                $(".thankgift_report").show();
                $(".thankgift_barrageReport").show();
                if ($(".thankgift_is_gift_code").is(':checked')) {
                    $(".thankgift_codeStrings").show();
                }
            } else {
                $(".thankgift_report").hide();
                $(".thankgift_barrageReport").hide();
                $(".thankgift_codeStrings").hide();
            }
            if ($(".thankgift_is_guard_report").is(':checked')) {
                $(".thankgift_is_gift_code").attr("disabled", false);
            } else {
                $(".thankgift_is_gift_code").attr("disabled", true);
                $(".thankgift_is_gift_code").prop('checked', false);
            }
            if ($(".thankgift_is_gift_code").is(':checked') && $(".thankgift_is_guard_report").is(':checked')) {
                $(".thankgift_codeStrings").show();
            } else {
                $(".thankgift_codeStrings").hide();
            }
        }
        return set;
    },
    time_parse: function (t) {
        if (t == null || t.trim() == "") return "00:30:00";
        let ts = t.split(":");
        if (ts.length == 2) {
            t = t + ":00";
        }
        return t;
    },
    wrap_replace: function (d) {
        "use strict";
        if (d.trim() !== null && d.trim() !== "") {
            let rc = d.replace(/\n/g, '').replace(/\r/g, '');
            // rc = rc.replace(/_#_@/g, '<br/>');
            // rc = rc.replace(/_@/g, '<br/>');
            // rc = rc.replace(/\s/g, '&nbsp;');
            return rc;
        } else {
            return d;
        }
    },
    delay_method: function (e, s) {
        "use strict";
        if (!$(e).is(":visible")) {
            $(e).show();
            $(e).html(s);
            setTimeout(function () {
                $(e).hide();
            }, 5000);
        }
    },
    giftStrings_method: function (lists) {
        let s = "";
        if (lists != null) {
            s = lists.join("，");
        }
        return s;
    },
    codeStrings_method: function (lists) {
        let s = "";
        if (lists != null) {
            s = lists.join("\n");
        }
        return s;
    },
    giftStrings_handle: function (lists, s) {
        if (s !=null && s != "") {
            if (s.indexOf("，") >= 0) {
                let ss = s.split("，");
                for (let gs in ss) {
                    if (ss[gs].trim() != "") {
                        lists.push(ss[gs]);
                    }
                }
            } else {
                lists.push(s);
            }
        }
        return lists;
    },
    codeStrings_handle: function (lists, s) {
        if (s !=null && s != "") {
            if (s.indexOf("\n") >= 0) {
                let ss = s.split("\n");
                for (let gs in ss) {
                    if (ss[gs].trim() != "") {
                        lists.push(ss[gs]);
                    }
                }
            } else {
                lists.push(s);
            }
        }
        ;
        return lists;
    },
    shieldgifts_each: function (lists) {
        if (lists != null) {
            $(".shieldgifts-tbody").children('tr').remove();
            for (let i in lists) {
                $(".shieldgifts-tbody")
                    .append(
                        "<tr><td><input type='checkbox' class='shieldgifts_open live-save' data-bs-toggle='tooltip' data-bs-placement='top' title='是否开启' data-original-title='是否开启'></td><td><input class='small-input shieldgifts_name live-save' value='"
                        + lists[i].gift_name
                        + "' placeholder='礼物名称' data-bs-toggle='tooltip' data-bs-placement='top' title='礼物名称' data-bs-html='true' data-original-title='礼物名称'></td><td><select class='custom-select-sm shieldgifts_status live-save' data-bs-toggle='tooltip' data-bs-placement='top' title='选择类型' data-bs-html='true' data-original-title='选择类型'><option value='1' selected='selected'>数量</option><option value='2'>瓜子</option></select></td><td><input type='number' min='0' value='"
                        + lists[i].num
                        + "' class='small-input shieldgifts_num live-save' placeholder='num' value='0' data-bs-toggle='tooltip' data-bs-placement='top' title='大于多少(不得小于' data-bs-html='true' data-original-title='大于多少(不得小于)'></td><td><button type='button' class='btn btn-danger btn-sm shieldgift_delete live-save'>删除</button></td></tr>");
                $(".shieldgifts_open").eq(i).prop('checked', lists[i].is_open);
                $(".shieldgifts_status").eq(i).find("option").eq(
                    lists[i].status).prop('selected', true);
            }
        }
    },
    replys_each: function (lists) {
        if (lists != null) {
            $(".replys-ul").children("li").remove();
            for (let i in lists) {
                $(".replys-ul")
                    .append(
                        `<li><input type='checkbox' class='reply_open live-save'
					data-bs-toggle='tooltip' data-bs-placement='top' title='是否开启'
					data-original-title='是否开启'> 
					<input type='checkbox' class='reply_oc live-save'
						data-bs-toggle='tooltip' data-bs-placement='top' title='是否精确匹配'
						data-original-title='是否精确匹配'> 
					<textarea class='small-input reply_keywords live-save' placeholder='关键字'
					data-bs-toggle='tooltip' data-bs-placement='top' title='不能编辑:多个关键字,以中文逗号隔开'
					data-bs-html='true' data-original-title='关键字' readonly='readonly' style='height: 2rem' disabled></textarea>
					<textarea class='small-input reply_shields live-save' placeholder='屏蔽词'
					data-bs-toggle='tooltip' data-bs-placement='top' title='不能编辑:多个屏蔽词,以中文逗号隔开'
					data-bs-html='true' data-original-title='关键字' readonly='readonly' style='height: 2rem' disabled></textarea>
					<textarea class='big-input reply_rs live-save' placeholder='回复语句'
					data-bs-toggle='tooltip' data-bs-placement='top' title='不能编辑:回复语句,提供%AT%参数,以打印:@提问问题人名称'
					data-bs-html='true' data-original-title='回复语句' readonly='readonly' style='height: 2rem' disabled></textarea>
					<span class='reply-btns'>
					<button type='button' class='btn btn-success btn-sm reply_edit' data-bs-toggle='modal' data-bs-target='#reply-model-edit'>编辑</button>
					<button type='button' class='btn btn-danger btn-sm reply_delete live-save'>删除</button>
					</span>
				</li>`);
// $("#replys-ul").append(
// "<li><input type='checkbox' class='reply_open' data-bs-toggle='tooltip'
// data-bs-placement='top' title='是否开启' data-original-title='是否开启'> "
// +"<input class='small-input reply_keywords' placeholder='关键字'
// data-bs-toggle='tooltip' data-bs-placement='top' title='不能编辑:多个关键字,以中文逗号隔开'
// data-original-title='关键字' readonly='readonly' value='"
// +method.giftStrings_method(lists[i].keywords)+"' disabled/>"
// +"<input class='small-input reply_shields' placeholder='屏蔽词'
// data-bs-toggle='tooltip' data-bs-placement='top' title='不能编辑:多个屏蔽词,以中文逗号隔开'
// data-original-title='屏蔽词' readonly='readonly' value='"
// +method.giftStrings_method(lists[i].shields)+"' disabled/>"
// +"<input class='big-input reply_rs' placeholder='回复语句' readonly='readonly'
// value='"
// +lists[i].reply+"' disabled/>"
// +"<span class='reply-btns'><button type='button' class='btn btn-success
// btn-sm reply_edit'>编辑</button><button type='button' class='btn btn-danger
// btn-sm reply_delete'>删除</button></span></li>");
                $(".reply_open").eq(i).prop('checked', lists[i].is_open);
                $(".reply_oc").eq(i).prop('checked', lists[i].is_accurate);
                $(".reply_keywords").eq(i).val(method.giftStrings_method(lists[i].keywords));
                $(".reply_shields").eq(i).val(method.giftStrings_method(lists[i].shields));
                $(".reply_rs").eq(i).val(lists[i].reply);
            }
        }
    },
    //隐私模式版本后移除
    // getIp: function () {
    //     let ip = null;
    //     $.ajax({
    //         url: '../getIp',
    //         async: false,
    //         cache: false,
    //         type: 'GET',
    //         dataType: 'json',
    //         success: function (data) {
    //             if (data.code == "200") {
    //                 ip = data.result
    //             }
    //         }
    //     });
    //     return ip;
    // },
    setExprot: function () {
        $.ajax({
            url: '../setExport',
            async: false,
            cache: false,
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.result == 0) {
                    showMessage("配置导出成功!位置位于弹幕姬目录set文件夹下", "success",2);
                } else {
                    showMessage("配置导出失败!", "danger",3);
                }
            }
        });
    },
    setExprotWeb: function () {
        window.open(window.location.origin + "/setExportWeb");
    },
    fileExport: function (fileType) {
        var url = fileType === 'pn' ? '../pnExport' : fileType === 'ab' ? '../abExport' : '../dsExport';
        $.ajax({
            url: url,
            async: false,
            cache: false,
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.result == 0) {
                    showMessage("导出成功!位置位于弹幕姬目录set文件夹下", "success",2);
                } else {
                    showMessage("导出失败!", "danger",3);
                }
            }
        });
    },
    fileExportWeb: function (fileType) {
        var url = fileType === 'pn' ? '/pnExportWeb' : fileType === 'ab' ? '/abExportWeb' : '/dsExportWeb';
        window.open(window.location.origin + url);
    },
//导入附件
    importDfFile: function (fileInput) {
        var fileType = $(fileInput).data('fileType');
        var url;
        if (fileType === 'pn') {
            url = "../pnImport";
        } else if (fileType === 'ab') {
            url = "../abImport";
        } else if (fileType === 'ds') {
            url = "../dsImport";
        } else if (fileType === 'lrm') {
            url = "../importCsvFile";
        } else if (fileType === 'dmgr') {
            url = "../importBarrageCsvFile";
        } else if (fileType === 'vst') {
            url = "../importVisitorCsvFile";
        } else if (fileType === 'mtch') {
            url = "../importMatchCsvFile";
        } else if (fileType === 'flw') {
            url = "../importFollowCsvFile";
        } else if (fileType === 'gft') {
            url = "../importGiftCsvFile";
        } else {
            url = "../setImport";
        }
        let formData = new FormData();
        formData.append('file', fileInput.files[0]);
        $.ajax({
            url: url,
            type: 'post',
            async: false,
            processData: false,
            contentType: false,
            data: formData,
            success: function (data) {
                if (data.result == 0) {
                    if (fileType === 'pn') {
                        method.loadPNList();
                    } else if (fileType === 'ab') {
                        method.loadAutoBlockList();
                    } else if (fileType === 'ds') {
                        method.loadDanmakuStoreList();
                    } else if (fileType === 'lrm') {
                        method.loadCsvFileList();
                    } else if (fileType === 'dmgr') {
                        method.loadDmgrCsvFileList();
                    } else if (fileType === 'vst') {
                        method.loadVstCsvFileList();
                    } else if (fileType === 'mtch') {
                        method.loadMtchCsvFileList();
                    } else if (fileType === 'flw') {
                        method.loadFlwCsvFileList();
                    } else if (fileType === 'gft') {
                        method.loadGftCsvFileList();
                    } else {
                        setTimeout(function () { location.reload(); }, 1200);
                    }
                    showMessage("导入成功!", "success",2);
                } else if (data.result == 2) {
                    showMessage("导入失败文件名称应为.json结尾!", "danger",3);
                } else {
                    showMessage("导入失败!请检查文件是否正确", "danger",3);
                }
            },
            error: function (data) {
            }
        })
        $(fileInput).val("");
        $(fileInput).removeData('fileType');
    },

    replaceThanko: function (s) {
        s = s.replace(/uNames/g, "uName");
        s = s.replace(/\%Gifts\%/g, "%GiftName% x%Num%");
        return s;
    },
    replaceThankt: function (s) {
        s = s.replace(/uNames/g, "uName");
        s = s.replace(/\%GiftName\% x\%Num\%/g, "%Gifts%");
        return s;
    },
    replaceThankts: function (s) {
        if (s.indexOf("uNames") === -1) {
            s = s.replace(/uName/g, "uNames");
        }
        s = s.replace(/\%GiftName\% x\%Num\%/g, "%Gifts%");
        return s;
    },
    block: function (uid, time) {
        let code = null;
        $.ajax({
            url: '../block',
            data: {
                uid: uid,
                time: time
            },
            async: false,
            cache: false,
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200") {
                    code = data.result
                }
            }
        });
        return code;
    },
    loadPNList: function () {
        $.ajax({
            url: '../getNegativeBlackPositiveWhite',
            async: false,
            cache: false,
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result && data.result.followings_list) {
                    pnData.list = data.result.followings_list;
                } else {
                    pnData.list = [];
                }
            }
        });
        pnData.page = 1;
        method.renderPNTable();
    },
    _syncPNPage: function () {
        // sync current page DOM inputs back to pnData.list
        var start = (pnData.page - 1) * pnData.pageSize;
        $(".pn-tbody tr").each(function (i) {
            var idx = start + i;
            if (idx >= pnData.list.length) return;
            var uidVal = $(this).find(".pn-uid").val();
            if (uidVal !== undefined && uidVal.trim() !== '') {
                pnData.list[idx].uid = parseInt(uidVal);
            }
            var nameVal = $(this).find(".pn-name").val();
            if (nameVal !== undefined) {
                pnData.list[idx].name = nameVal.trim();
            }
            var scoreVal = $(this).find(".pn-score").val();
            if (scoreVal !== undefined && scoreVal.trim() !== '') {
                pnData.list[idx].score = parseInt(scoreVal);
            }
        });
    },
    renderPNTable: function () {
        var tbody = $(".pn-tbody");
        tbody.empty();
        method._applyPNSort();
        method._updatePNSortIcons();
        var query = ($(".pn-search-input").val() || '').trim().toLowerCase();
        var filtered = pnData.list;
        if (query) {
            filtered = pnData.list.filter(function(item) {
                return (String(item.uid || '').toLowerCase().indexOf(query) !== -1) ||
                       (String(item.name || '').toLowerCase().indexOf(query) !== -1) ||
                       (String(item.score || 0).toLowerCase().indexOf(query) !== -1);
            });
        }
        var totalFiltered = filtered.length;
        var totalPages = Math.max(1, Math.ceil(totalFiltered / pnData.pageSize));
        if (pnData.page > totalPages) pnData.page = totalPages;
        var start = (pnData.page - 1) * pnData.pageSize;
        var end = Math.min(start + pnData.pageSize, totalFiltered);
        var pageItems = filtered.slice(start, end);
        for (var i = 0; i < pageItems.length; i++) {
            var item = pageItems[i];
            var nameHtml = (item.uid && item.name) ?
                '<a href="javascript:;" class="pn-name-link" title="点击打开B站空间，双击可编辑">' + method._escHtml(item.name) + '</a><input class="form-control form-control-sm pn-name" type="text" value="' + method._escHtml(item.name || '') + '" style="display:none;">' :
                '<input class="form-control form-control-sm pn-name" type="text" value="' + (item.name || '') + '">';
            var row = '<tr data-uid="' + (item.uid || '') + '">' +
                '<td class="pn-col-uid"><input class="form-control form-control-sm pn-uid" type="number" value="' + (item.uid || '') + '"></td>' +
                '<td class="pn-col-name">' + nameHtml + '</td>' +
                '<td class="pn-col-score"><input class="form-control form-control-sm pn-score" type="number" value="' + (item.score || 0) + '"></td>' +
                '<td class="pn-col-action"><button class="btn btn-sm btn-danger pn-delete-btn">删除</button></td>' +
                '</tr>';
            tbody.append(row);
        }
        if (query) {
            $(".pn-page-info").text("第" + pnData.page + "页/共" + totalPages + "页 (匹配" + totalFiltered + "/共" + pnData.list.length + "条)");
        } else {
            $(".pn-page-info").text("第" + pnData.page + "页/共" + totalPages + "页");
        }
        $(".pn-prev").prop('disabled', pnData.page <= 1);
        $(".pn-next").prop('disabled', pnData.page >= totalPages);
    },
    _escHtml: function (str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    },
    savePNList: function () {
        method._syncPNPage();
        method._applyPNSort();
        // deduplicate by uid, latest wins
        var seen = {};
        var list = [];
        for (var i = pnData.list.length - 1; i >= 0; i--) {
            var entry = pnData.list[i];
            var key = entry.uid;
            if (key != null && key !== '' && !seen.hasOwnProperty(key)) {
                seen[key] = true;
                list.unshift(entry);
            }
        }
        pnData.list = list;
        var payload = { type: "负黑正白判定表", followings_list: list };
        var result = 0;
        $.ajax({
            url: '../saveNegativeBlackPositiveWhite',
            async: false,
            cache: false,
            type: 'POST',
            data: { data: JSON.stringify(payload) },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200") {
                    result = data.result;
                }
            }
        });
        if (result === 0) {
            showMessage("正白负黑列表保存成功!", "success", 3);
        } else {
            showMessage("正白负黑列表保存失败!", "danger", 3);
        }
        method.renderPNTable();
    },
    addPNRow: function () {
        pnData.list.unshift({ uid: '', name: '', score: 0 });
        pnData.page = 1;
        pnData.sortState = [];
        method.renderPNTable();
    },
    // 切换排序列的升降状态：无→升→降→移除
    _togglePNSort: function (col) {
        var existing = null;
        for (var i = 0; i < pnData.sortState.length; i++) {
            if (pnData.sortState[i].col === col) {
                existing = pnData.sortState[i];
                pnData.sortState.splice(i, 1);
                break;
            }
        }
        if (existing === null) {
            pnData.sortState.unshift({col: col, dir: 'asc'});
        } else if (existing.dir === 'asc') {
            pnData.sortState.unshift({col: col, dir: 'desc'});
        }
        // else: was 'desc', removed → no sort on this column, don't re-add
    },
    // 根据 sortState 对 pnData.list 进行稳定多列排序
    _applyPNSort: function () {
        if (pnData.sortState.length === 0) return;
        pnData.list.sort(function (a, b) {
            for (var i = 0; i < pnData.sortState.length; i++) {
                var col = pnData.sortState[i].col;
                var dir = pnData.sortState[i].dir;
                var va, vb;
                if (col === 'score') {
                    va = parseInt(a.score) || 0;
                    vb = parseInt(b.score) || 0;
                } else if (col === 'uid') {
                    va = parseInt(a.uid) || 0;
                    vb = parseInt(b.uid) || 0;
                } else {
                    va = (a.name || '').toLowerCase();
                    vb = (b.name || '').toLowerCase();
                }
                if (va < vb) return dir === 'asc' ? -1 : 1;
                if (va > vb) return dir === 'asc' ? 1 : -1;
            }
            return 0;
        });
    },
    // 更新表头排序图标
    _updatePNSortIcons: function () {
        $('.pn-sortable .pn-sort-icon').text('');
        for (var i = 0; i < pnData.sortState.length; i++) {
            var col = pnData.sortState[i].col;
            var dir = pnData.sortState[i].dir;
            var icon = dir === 'asc' ? ' ▲' : ' ▼';
            var $th = $('.pn-sortable[data-col="' + col + '"]');
            var $icon = $th.find('.pn-sort-icon');
            if (i === 0) {
                $icon.text(icon);
            }
            // 用不同透明度表示优先级：第一优先不透明，后续半透明
            $icon.css('opacity', 1 - i * 0.35);
        }
    },
    // 负黑自动拉黑姬WebSocket连接
    _autoBlockWs: null,
    _connectAutoBlockWs: function () {
        var self = this;
        // 自动拉黑WebSocket始终连接当前服务器地址，不受弹幕显示地址输入框影响
        var wsUrl = 'ws://' + window.location.host + '/danmu/sub';
        if (self._autoBlockWs) {
            try { self._autoBlockWs.close(); } catch (e) {}
        }
        try {
            self._autoBlockWs = new WebSocket(wsUrl);
            self._autoBlockWs.onmessage = function (msg) {
                var data = JSON.parse(msg.data);
                if (data.cmd === 'auto_block' && data.result) {
                    autoBlockData.list.unshift(data.result);
                    if (autoBlockData.list.length > 100) {
                        autoBlockData.list = autoBlockData.list.slice(0, 100);
                    }
                    if ($("#tab-autoBlock-set").hasClass("active")) {
                        if (autoBlockData.page === 1) {
                            method.renderAutoBlockTable();
                        } else {
                            var totalPages = Math.max(1, Math.min(10, Math.ceil(autoBlockData.list.length / autoBlockData.pageSize)));
                            $(".ab-page-info").text("第" + autoBlockData.page + "页/共" + totalPages + "页");
                            $(".ab-prev").prop('disabled', autoBlockData.page <= 1);
                            $(".ab-next").prop('disabled', autoBlockData.page >= totalPages);
                        }
                    }
                }
                if (data.cmd === 'stranger_viewer' && data.result) {
                    method._handleStrangerViewer(data.result);
                }
                if (data.cmd === 'stranger_block' && data.result) {
                    method._handleStrangerBlock(data.result);
                }
                if (data.cmd === 'bwlist_update' && data.result) {
                    method._handleBwlistUpdate(data.result);
                }
            };
            self._autoBlockWs.onclose = function () {
                // 断线3秒后重连
                setTimeout(function () { method._connectAutoBlockWs(); }, 3000);
            };
        } catch (e) {
            // 连接失败，3秒后重试
            setTimeout(function () { method._connectAutoBlockWs(); }, 3000);
        }
    },
    loadAutoBlockList: function () {
        $.ajax({
            url: '../getAutoBlockRecords',
            async: false,
            cache: false,
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result && data.result.records) {
                    autoBlockData.list = data.result.records;
                } else {
                    autoBlockData.list = [];
                }
            }
        });
        autoBlockData.page = 1;
        method.renderAutoBlockTable();
    },
    renderAutoBlockTable: function () {
        var tbody = $(".auto-block-tbody");
        tbody.empty();
        var query = ($(".ab-search-input").val() || '').trim().toLowerCase();
        var filtered = autoBlockData.list;
        if (query) {
            filtered = autoBlockData.list.filter(function(item) {
                return (String(item.uid || '').toLowerCase().indexOf(query) !== -1) ||
                       (String(item.uname || '').toLowerCase().indexOf(query) !== -1) ||
                       (String(item.score || 0).toLowerCase().indexOf(query) !== -1);
            });
        }
        var totalFiltered = filtered.length;
        var totalPages = Math.max(1, Math.min(10, Math.ceil(totalFiltered / autoBlockData.pageSize)));
        if (autoBlockData.page > totalPages) autoBlockData.page = totalPages;
        var start = (autoBlockData.page - 1) * autoBlockData.pageSize;
        var end = Math.min(start + autoBlockData.pageSize, totalFiltered);
        var pageItems = filtered.slice(start, end);
        for (var i = 0; i < pageItems.length; i++) {
            var item = pageItems[i];
            var uname = item.uname || '';
            var uid = item.uid || '';
            var score = item.score || 0;
            var time = item.time || '';
            var row = '<tr data-uid="' + uid + '">' +
                '<td class="ab-col-time">' + method._escHtml(time) + '</td>' +
                '<td class="ab-col-uname"><a class="ab-uname-link" href="https://space.bilibili.com/' + uid + '" target="_blank">' + method._escHtml(uname) + '</a></td>' +
                '<td class="ab-col-score">' + score + '</td>' +
                '<td class="ab-col-unblock"><button class="btn btn-sm btn-warning ab-unblock-btn" data-uid="' + uid + '">解除拉黑</button></td>' +
                '<td class="ab-col-delete"><button class="btn btn-sm btn-danger ab-delete-btn" data-uid="' + uid + '">删除显示</button></td>' +
                '</tr>';
            tbody.append(row);
        }
        if (query) {
            $(".ab-page-info").text("第" + autoBlockData.page + "页/共" + totalPages + "页 (匹配" + totalFiltered + "/共" + autoBlockData.list.length + "条)");
        } else {
            $(".ab-page-info").text("第" + autoBlockData.page + "页/共" + totalPages + "页");
        }
        $(".ab-prev").prop('disabled', autoBlockData.page <= 1);
        $(".ab-next").prop('disabled', autoBlockData.page >= totalPages);
    },
    _escHtml: function (str) {
        if (!str) return '';
        return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    },
    fmtNum: function (n) {
        if (n == null) return '0';
        if (n >= 100000000) return (n / 100000000).toFixed(1) + '亿';
        if (n >= 10000) return (n / 10000).toFixed(1) + '万';
        return n.toString();
    },
    fmtDate: function (ts) {
        if (!ts) return '-';
        var d = new Date(ts * 1000);
        return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2);
    },
    loadBiliBadList: function (page, callback) {
        if (!page || page < 1) page = 1;
        $.ajax({
            url: '../getBiliBadList',
            type: 'GET',
            data: {pn: page, ps: biliBadListState.pageSize},
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    biliBadListState.total = data.result.total || 0;
                    biliBadListState.list = data.result.list || [];
                    biliBadListState.page = page;
                    method.renderBiliBadList();
                } else {
                    var errMsg = (data.result && data.result.error) ? data.result.error : '获取失败，请确认已登录B站账号';
                    showMessage(errMsg, "warning", 3);
                }
            },
            error: function () {
                showMessage("网络异常，获取B站拉黑列表失败", "danger", 3);
            },
            complete: function () {
                if (callback) callback();
            }
        });
    },
    renderBiliBadList: function () {
        var $tbody = $(".bili-badlist-tbody");
        var $table = $(".bili-badlist-table");
        var $total = $(".bili-badlist-total");
        var $pagination = $(".bili-badlist-pagination");
        var list = biliBadListState.list;
        var total = biliBadListState.total;
        var page = biliBadListState.page;
        var pageSize = biliBadListState.pageSize;
        var totalPages = Math.ceil(total / pageSize) || 1;
        if (page > totalPages) page = totalPages;
        $total.text("共" + total + "人");
        if (!list || list.length === 0) {
            $table.hide();
            $pagination.hide();
            if (total === 0) $total.text("暂无拉黑记录");
            return;
        }
        $table.show();
        $tbody.empty();
        for (var i = 0; i < list.length; i++) {
            var user = list[i];
            var $tr = $("<tr>");
            var faceHtml = user.face
                ? '<img src="' + user.face + '" class="bili-avatar-click" data-face="' + user.face + '" style="width:24px;height:24px;border-radius:50%;cursor:pointer;" title="点击查看原图" onerror="this.style.display=\'none\'">'
                : '';
            $tr.append($("<td>").html(faceHtml));
            $tr.append($("<td>").text(user.mid || ''));
            var nameHtml = user.mid
                ? '<a href="https://space.bilibili.com/' + user.mid + '" target="_blank" title="查看用户主页">' + (user.uname || '') + '</a>'
                : (user.uname || '');
            $tr.append($("<td>").html(nameHtml));
            $tr.append($("<td>").text(user.sign || '').addClass('truncate-expandable').css({'max-width':'200px','overflow':'hidden','text-overflow':'ellipsis','white-space':'nowrap'}));
            var timeStr = user.mtime ? new Date(user.mtime * 1000).toLocaleString() : '';
            $tr.append($("<td>").text(timeStr));
            $tbody.append($tr);
        }
        if (totalPages > 1) {
            $pagination.show();
            $(".bili-badlist-page-info").text("第" + page + "页/共" + totalPages + "页 (共" + total + "条)");
            $(".bili-badlist-prev").prop("disabled", page <= 1);
            $(".bili-badlist-next").prop("disabled", page >= totalPages);
        } else {
            $pagination.hide();
        }
    },

    // ========== 直播间管理 ==========

    loadCsvFileList: function () {
        $.ajax({
            url: '../listCsvFiles',
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var $select = $('#lrm-csv-select');
                    $select.find('option[value!=""]').remove();
                    var firstOpt = null;
                    $.each(data.result, function (i, item) {
                        var label = item.fileName;
                        if (item.anchorName) label = item.anchorName + ' - ' + item.fileName;
                        var opt = $('<option>').val(item.filePath).text(label);
                        if (item.isCurrent === '1') { firstOpt = opt; opt.text(opt.text() + ' *'); }
                        $select.append(opt);
                    });
                    if (firstOpt) {
                        $select.val(firstOpt.val());
                        lrmState.currentFile = firstOpt.val();
                    } else {
                        var fallback = $select.find('option[value!=""]').first();
                        if (fallback.length) {
                            $select.val(fallback.val());
                            lrmState.currentFile = fallback.val();
                        }
                    }
                    if (lrmState.currentFile) method.loadCsvData();
                }
            }
        });
    },

    loadCsvData: function () {
        if (!lrmState.currentFile) return;
        if (!lrmState.startTime && !lrmState.endTime) {
            applyHoursFilter(lrmState, '#lrm');
        }
        $.ajax({
            url: '../readCsvData',
            type: 'GET',
            data: {
                filePath: lrmState.currentFile,
                page: lrmState.page,
                pageSize: lrmState.pageSize,
                startTime: lrmState.startTime || '',
                endTime: lrmState.endTime || '',
                search: lrmState.search || '',
                sortField: lrmState.sortField,
                sortOrder: lrmState.sortOrder
            },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var r = data.result;
                    lrmState.page = r.currentPage || 1;
                    lrmState.totalPages = r.totalPages || 0;
                    lrmState.totalRows = r.total || 0;
                    lrmState.fileFirstTime = r.firstTime || '';
                    lrmState.fileLastTime = r.lastTime || '';
                    method.renderCsvTable(r.rows || []);
                    method.renderCsvPagination();
                    if (r.total > 0) {
                        method.renderCsvCharts();
                        method.renderCsvStats();
                        $('#lrm-stats-row').show();
                    } else {
                        $('#lrm-stats-row').hide();
                        $('#lrm-charts-row').hide();
                    }
                }
            }
        });
    },

    renderCsvTable: function (rows) {
        var $tbody = $('#lrm-table-body');
        var $table = $('#lrm-data-table');
        var $empty = $('#lrm-empty-msg');
        $tbody.empty();
        if (!rows || rows.length === 0) {
            $table.hide();
            $('#lrm-pagination').hide();
            $empty.show();
            return;
        }
        $empty.hide();
        $table.show();
        var cols = ['时间', '观看数', '在线数', '点赞数'];
        $.each(rows, function (i, row) {
            var tr = '<tr>';
            $.each(lrmState.columnOrder, function (j, colIdx) {
                if (colIdx === 4) {
                    tr += '<td><button class="btn btn-sm btn-outline-danger lrm-row-del" data-time="' + (row['时间'] || '') + '">删除</button></td>';
                } else if (colIdx < cols.length) {
                    tr += '<td>' + (row[cols[colIdx]] || '') + '</td>';
                }
            });
            tr += '</tr>';
            $tbody.append(tr);
        });
        $('#lrm-table-head th[data-sort]').each(function () {
            var f = $(this).data('sort');
            $(this).text(f);
            if (f === lrmState.sortField) {
                $(this).text(f + ' ' + (lrmState.sortOrder === 'asc' ? '▲' : '▼'));
            }
        });
        setTimeout(function () { method.initColumnDrag(); }, 100);
    },

    renderCsvPagination: function () {
        var $pagination = $('#lrm-pagination');
        if (lrmState.totalPages <= 0) {
            $pagination.hide();
            return;
        }
        $pagination.show();
        $('#lrm-page-info').text('第' + lrmState.page + '页 / 共' + lrmState.totalPages + '页');
        $('#lrm-page-jump').attr('max', lrmState.totalPages).val('');
        var isFirst = lrmState.page <= 1;
        var isLast = lrmState.page >= lrmState.totalPages;
        $('#lrm-first-btn, #lrm-prev-btn').prop('disabled', isFirst);
        $('#lrm-next-btn, #lrm-last-btn').prop('disabled', isLast);
    },

    gotoPage: function (targetPage) {
        var p = parseInt(targetPage);
        if (isNaN(p) || p < 1) p = 1;
        if (p > lrmState.totalPages) p = lrmState.totalPages;
        if (p === lrmState.page) return;
        lrmState.page = p;
        method.loadCsvData();
    },

    sortCsvColumn: function (field) {
        if (lrmState.sortField === field) {
            lrmState.sortOrder = lrmState.sortOrder === 'asc' ? 'desc' : 'asc';
        } else {
            lrmState.sortField = field;
            lrmState.sortOrder = 'desc';
        }
        lrmState.page = 1;
        method.loadCsvData();
    },

    renderCsvCharts: function () {
        $.each(lrmState.chartInstances, function (key, chart) {
            if (chart) chart.destroy();
        });
        lrmState.chartInstances = {};

        if (!lrmState.currentFile) return;

        $.ajax({
            url: '../readCsvData',
            type: 'GET',
            data: {
                filePath: lrmState.currentFile,
                page: 1,
                pageSize: 999999,
                startTime: lrmState.startTime || '',
                endTime: lrmState.endTime || ''
            },
            dataType: 'json',
            async: false,
            success: function (data) {
                if (data.code == "200" && data.result && data.result.rows) {
                    var rows = data.result.rows;
                    if (rows.length < 2) {
                        $('#lrm-charts-row').hide();
                        return;
                    }
                    $('#lrm-charts-row').show();

                    var labels = [];
                    var watcherData = [];
                    var onlineData = [];
                    var likeData = [];
                    $.each(rows, function (i, row) {
                        var t = row['时间'] || '';
                        labels.push(t);
                        watcherData.push(parseInt(row['观看数']) || 0);
                        onlineData.push(parseInt(row['在线数']) || 0);
                        likeData.push(parseInt(row['点赞数']) || 0);
                    });

                    var chartOptions = {
                        responsive: true,
                        maintainAspectRatio: false,
                        interaction: { mode: 'index', intersect: false },
                        plugins: {
                            legend: { display: false },
                            tooltip: {
                                callbacks: {
                                    title: function (ctx) { return ctx[0].label; },
                                    label: function (ctx) { return ' ' + ctx.raw.toLocaleString(); }
                                }
                            }
                        },
                        scales: {
                            x: {
                                ticks: {
                                    maxTicksLimit: 12,
                                    autoSkip: true,
                                    font: { size: 10 },
                                    callback: function (val, index) {
                                        var t = this.getLabelForValue(val);
                                        if (!t) return '';
                                        var labels = this.chart.data.labels;
                                        var sameDay = labels.length > 0 && labels[0].substring(0, 10) === labels[labels.length - 1].substring(0, 10);
                                        if (sameDay) { var m = t.match(/(\d{2}:\d{2})/); return m ? m[1] : t; }
                                        return t.substring(5, 16);
                                    }
                                }
                            },
                            y: { ticks: { font: { size: 10 } } }
                        }
                    };

                    var datasetBase = { borderWidth: 1.5, pointRadius: 0, pointHoverRadius: 4, pointHoverBackgroundColor: '#fff', tension: 0.1 };

                    var ctxW = document.getElementById('lrm-chart-watcher').getContext('2d');
                    lrmState.chartInstances.watcher = new Chart(ctxW, {
                        type: 'line',
                        data: {
                            labels: labels,
                            datasets: [$.extend({ data: watcherData, borderColor: '#0d6efd' }, datasetBase)]
                        },
                        options: chartOptions
                    });

                    var ctxO = document.getElementById('lrm-chart-online').getContext('2d');
                    lrmState.chartInstances.online = new Chart(ctxO, {
                        type: 'line',
                        data: {
                            labels: labels,
                            datasets: [$.extend({ data: onlineData, borderColor: '#198754' }, datasetBase)]
                        },
                        options: chartOptions
                    });

                    var ctxL = document.getElementById('lrm-chart-like').getContext('2d');
                    lrmState.chartInstances.like = new Chart(ctxL, {
                        type: 'line',
                        data: {
                            labels: labels,
                            datasets: [$.extend({ data: likeData, borderColor: '#fd7e14' }, datasetBase)]
                        },
                        options: chartOptions
                    });

                }
            }
        });
    },

    renderCsvStats: function () {
        if (!lrmState.currentFile) return;
        $.ajax({
            url: '../getCsvStatistics',
            type: 'GET',
            data: {
                filePath: lrmState.currentFile,
                startTime: lrmState.startTime || '',
                endTime: lrmState.endTime || ''
            },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var s = data.result;
                    $('#lrm-stat-cum-watcher').text((s.cumulativeWatcher || 0).toLocaleString());
                    $('#lrm-stat-cum-like').text((s.cumulativeLike || 0).toLocaleString());
                    var onlineText = (s.avgOnlineCount || 0).toLocaleString();
                    if (s.maxOnlineCount && s.maxOnlineCount.time) {
                        var m = s.maxOnlineCount.time.match(/(\d{2}:\d{2})/);
                        onlineText += ' / ' + (m ? m[1] : s.maxOnlineCount.time) + ' ' + (s.maxOnlineCount.count || 0).toLocaleString();
                    }
                    $('#lrm-stat-online').text(onlineText);
                    var totalSec = s.totalWatchSeconds || 0;
                    var avgSec = s.avgWatchSeconds || 0;
                    var totalText = method.formatDuration(totalSec);
                    var avgText = method.formatDuration(avgSec);
                    $('#lrm-stat-watch-time').text(totalText + ' / ' + avgText);
                    $('#lrm-stats-row').show();
                }
            }
        });
    },

    deleteCsvRow: function (timeKey) {
        $.ajax({
            url: '../deleteCsvRow',
            type: 'POST',
            data: {
                filePath: lrmState.currentFile,
                timeKey: timeKey
            },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    showMessage("删除成功!", "success", 2);
                    method.loadCsvData();
                } else {
                    showMessage("删除失败!", "danger", 3);
                }
            },
            error: function () {
                showMessage("删除失败!", "danger", 3);
            }
        });
    },

    formatDuration: function (totalSeconds) {
        if (!totalSeconds || totalSeconds <= 0) return '--';
        var h = Math.floor(totalSeconds / 3600);
        var m = Math.floor((totalSeconds % 3600) / 60);
        var s = totalSeconds % 60;
        if (h > 0) return h + '时' + m + '分' + s + '秒';
        if (m > 0) return m + '分' + s + '秒';
        return s + '秒';
    },

    exportCsv: function () {
        if (!lrmState.currentFile) return;
        var url = '../exportFilteredCsv?filePath=' + encodeURIComponent(lrmState.currentFile);
        if (lrmState.startTime) url += '&startTime=' + encodeURIComponent(lrmState.startTime);
        if (lrmState.endTime) url += '&endTime=' + encodeURIComponent(lrmState.endTime);
        if (lrmState.search) url += '&search=' + encodeURIComponent(lrmState.search);
        window.open(url, '_blank');
    },

    initColumnDrag: function () {
        var thead = document.getElementById('lrm-table-head');
        if (!thead || !thead.querySelector('tr')) return;
        if (thead._sortable) {
            thead._sortable.destroy();
        }
        thead._sortable = new Sortable(thead.querySelector('tr'), {
            animation: 150,
            filter: 'th[data-sortable="false"]',
            onEnd: function () {
                var order = [];
                $('#lrm-table-head th').each(function (idx) {
                    var text = $(this).text().trim();
                    if (text === '时间') order.push(0);
                    else if (text === '观看数') order.push(1);
                    else if (text === '在线数') order.push(2);
                    else if (text === '点赞数') order.push(3);
                    else if (text === '操作') order.push(4);
                });
                lrmState.columnOrder = order;
                method.loadCsvData();
            }
        });
    },

    // ========== 弹幕管理 ==========

    loadDmgrCsvFileList: function () {
        $.ajax({
            url: '../listBarrageCsvFiles',
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var $select = $('#dmgr-csv-select');
                    $select.find('option[value!=""]').remove();
                    var firstOpt = null;
                    $.each(data.result, function (i, item) {
                        var label = item.fileName;
                        if (item.anchorName) label = item.anchorName + ' - ' + item.fileName;
                        var opt = $('<option>').val(item.filePath).text(label);
                        if (item.isCurrent === '1') { firstOpt = opt; opt.text(opt.text() + ' *'); }
                        $select.append(opt);
                    });
                    if (firstOpt) {
                        $select.val(firstOpt.val());
                        dmgrState.currentFile = firstOpt.val();
                    } else {
                        var fallback = $select.find('option[value!=""]').first();
                        if (fallback.length) {
                            $select.val(fallback.val());
                            dmgrState.currentFile = fallback.val();
                        }
                    }
                    if (dmgrState.currentFile) method.loadDmgrData();
                }
            }
        });
    },

    loadDmgrData: function () {
        if (!dmgrState.currentFile) return;
        if (!dmgrState.startTime && !dmgrState.endTime) {
            applyHoursFilter(dmgrState, '#dmgr');
        }
        $.ajax({
            url: '../readBarrageCsvData',
            type: 'GET',
            data: {
                filePath: dmgrState.currentFile,
                page: dmgrState.page,
                pageSize: dmgrState.pageSize,
                startTime: dmgrState.startTime || '',
                endTime: dmgrState.endTime || '',
                search: dmgrState.search || '',
                sortField: dmgrState.sortField,
                sortOrder: dmgrState.sortOrder
            },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var r = data.result;
                    dmgrState.totalPages = r.totalPages || 0;
                    dmgrState.totalRows = r.total || 0;
                    dmgrState.fileFirstTime = r.firstTime || '';
                    dmgrState.fileLastTime = r.lastTime || '';
                    method.renderDmgrTable(r.rows || []);
                    method.renderDmgrPagination();
                    if (r.total > 0) {
                        method.renderDmgrCharts();
                        method.renderDmgrStats();
                        $('#dmgr-stats-row, #dmgr-rank-limit-row').show();
                    } else {
                        $('#dmgr-stats-row, #dmgr-rank-limit-row').hide();
                        $('#dmgr-charts-row, #dmgr-scatter-row').hide();
                    }
                }
            }
        });
    },

    renderDmgrTable: function (rows) {
        var $tbody = $('#dmgr-table-body');
        var $table = $('#dmgr-data-table');
        var $empty = $('#dmgr-empty-msg');
        $tbody.empty();
        if (!rows || rows.length === 0) {
            $table.hide();
            $('#dmgr-pagination').hide();
            $empty.show();
            return;
        }
        $empty.hide();
        $table.show();
        var cols = dmgrState.headers;
        $.each(rows, function (i, row) {
            var tr = '<tr>';
            var uid = row['id'] || '';
            $.each(dmgrState.columnOrder, function (j, colIdx) {
                if (colIdx < cols.length) {
                    var val = row[cols[colIdx]] || '';
                    if (cols[colIdx] === '名字' && uid) {
                        tr += '<td><a href="https://space.bilibili.com/' + uid + '" target="_blank" style="color:#0d6efd;">' + val + '</a></td>';
                    } else {
                        tr += '<td>' + val + '</td>';
                    }
                }
            });
            tr += '</tr>';
            $tbody.append(tr);
        });
        // update sort indicators
        $('#dmgr-table-head th[data-sort]').each(function () {
            var f = $(this).data('sort');
            $(this).text(f);
            if (f === dmgrState.sortField) {
                $(this).text(f + ' ' + (dmgrState.sortOrder === 'asc' ? '▲' : '▼'));
            }
        });
        setTimeout(function () { method.initDmgrColumnDrag(); }, 100);
    },

    renderDmgrPagination: function () {
        var $pagination = $('#dmgr-pagination');
        if (dmgrState.totalPages <= 0) {
            $pagination.hide();
            return;
        }
        $pagination.show();
        $('#dmgr-page-info').text('第' + dmgrState.page + '页 / 共' + dmgrState.totalPages + '页');
        $('#dmgr-page-jump').attr('max', dmgrState.totalPages).val('');
        var isFirst = dmgrState.page <= 1;
        var isLast = dmgrState.page >= dmgrState.totalPages;
        $('#dmgr-first-btn, #dmgr-prev-btn').prop('disabled', isFirst);
        $('#dmgr-next-btn, #dmgr-last-btn').prop('disabled', isLast);
    },

    gotoDmgrPage: function (targetPage) {
        var p = parseInt(targetPage);
        if (isNaN(p) || p < 1) p = 1;
        if (p > dmgrState.totalPages) p = dmgrState.totalPages;
        if (p === dmgrState.page) return;
        dmgrState.page = p;
        method.loadDmgrData();
    },

    sortDmgrColumn: function (field) {
        if (dmgrState.sortField === field) {
            dmgrState.sortOrder = dmgrState.sortOrder === 'asc' ? 'desc' : 'asc';
        } else {
            dmgrState.sortField = field;
            dmgrState.sortOrder = 'desc';
        }
        dmgrState.page = 1;
        method.loadDmgrData();
    },

    renderDmgrCharts: function () {
        $.each(dmgrState.chartInstances, function (key, chart) {
            if (chart) chart.destroy();
        });
        dmgrState.chartInstances = {};

        if (!dmgrState.currentFile) return;

        $.ajax({
            url: '../getBarrageStatistics',
            type: 'GET',
            data: {
                filePath: dmgrState.currentFile,
                startTime: dmgrState.startTime || '',
                endTime: dmgrState.endTime || '',
                limit: dmgrState.rankLimit
            },
            dataType: 'json',
            async: false,
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var s = data.result;
                    var perInt = s.perIntervalData || [];
                    var top5 = s.top5Senders || [];
                    var wordFreq = s.wordFrequency || [];
                    var hasData = perInt.length > 0 || top5.length > 0 || wordFreq.length > 0;
                    if (!hasData) {
                        $('#dmgr-charts-row, #dmgr-scatter-row').hide();
                        return;
                    }
                    $('#dmgr-charts-row').show();

                    var chartOpts = {
                        responsive: true,
                        maintainAspectRatio: false,
                        interaction: { mode: 'index', intersect: false },
                        plugins: {
                            legend: { display: true, position: 'top', labels: { font: { size: 10 } } },
                            tooltip: {
                                titleFont: { size: 12 },
                                bodyFont: { size: 12 },
                                callbacks: {
                                    title: function (ctxs) { return '时间: ' + ctxs[0].label; },
                                    label: function (ctx) { return ' ' + ctx.dataset.label + ': ' + ctx.raw.toLocaleString(); }
                                }
                            }
                        },
                        scales: {
                            x: { ticks: { font: { size: 10 } } },
                            y: { ticks: { font: { size: 10 } } }
                        }
                    };

                    // Chart 1: 弹幕数量趋势 (line)
                    if (perInt.length > 0) {
                        var ctx1 = document.getElementById('dmgr-chart-count').getContext('2d');
                        dmgrState.chartInstances.count = new Chart(ctx1, {
                            type: 'line',
                            data: {
                                labels: perInt.map(function (d) { return d.time; }),
                                datasets: [{
                                    label: '弹幕数',
                                    data: perInt.map(function (d) { return d.count; }),
                                    borderColor: '#0d6efd', borderWidth: 1.5, pointRadius: 0, pointHoverRadius: 4, tension: 0.1
                                }]
                            },
                            options: chartOpts
                        });
                    }

                    // Chart 2: 发送排行榜 (horizontal bar)
                    if (top5.length > 0) {
                        var ctx2 = document.getElementById('dmgr-chart-top5').getContext('2d');
                        dmgrState.chartInstances.top5 = new Chart(ctx2, {
                            type: 'bar',
                            data: {
                                labels: top5.map(function (d) { return d.name; }),
                                datasets: [{
                                    label: '发送数',
                                    data: top5.map(function (d) { return d.count; }),
                                    backgroundColor: ['#0d6efd', '#198754', '#fd7e14', '#dc3545', '#6f42c1']
                                }]
                            },
                            options: {
                                responsive: true,
                                maintainAspectRatio: false,
                                plugins: {
                                    legend: { display: false },
                                    tooltip: {
                                        callbacks: {
                                            label: function (ctx) { return ' 发送数: ' + ctx.raw.toLocaleString(); }
                                        }
                                    }
                                },
                                scales: {
                                    x: {
                                        ticks: { font: { size: 10 } },
                                        title: { display: true, text: '名字', font: { size: 10 } }
                                    },
                                    y: {
                                        ticks: { font: { size: 10 }, stepSize: 1 },
                                        title: { display: true, text: '发送数', font: { size: 10 } }
                                    }
                                }
                            }
                        });
                    }

                    // Chart 3: 弹幕质量趋势 (质量 = 总长度/弹幕数)
                    if (perInt.length > 0) {
                        var ctx3 = document.getElementById('dmgr-chart-quality').getContext('2d');
                        dmgrState.chartInstances.quality = new Chart(ctx3, {
                            type: 'line',
                            data: {
                                labels: perInt.map(function (d) { return d.time; }),
                                datasets: [{
                                    label: '弹幕质量',
                                    data: perInt.map(function (d) { return d.avgLength; }),
                                    borderColor: '#198754', borderWidth: 1.5, pointRadius: 0, pointHoverRadius: 4, tension: 0.1,
                                    fill: true, backgroundColor: 'rgba(25,135,84,0.08)'
                                }]
                            },
                            options: {
                                responsive: true,
                                maintainAspectRatio: false,
                                interaction: { mode: 'index', intersect: false },
                                plugins: {
                                    legend: { display: true, position: 'top', labels: { font: { size: 10 } } },
                                    tooltip: {
                                        titleFont: { size: 12 },
                                        bodyFont: { size: 12 },
                                        callbacks: {
                                            title: function (ctxs) { return '时间: ' + ctxs[0].label; },
                                            label: function (ctx) { return ' 平均长度: ' + ctx.raw.toLocaleString() + ' 字'; }
                                        }
                                    }
                                },
                                scales: {
                                    x: { ticks: { font: { size: 10 } } },
                                    y: {
                                        title: { display: true, text: '字数', font: { size: 10 } },
                                        ticks: { font: { size: 10 } }
                                    }
                                }
                            }
                        });
                    }

                    // Chart 4: 词频 (vertical bar, top 15)
                    var top15 = wordFreq.slice(0, 15);
                    if (top15.length > 0) {
                        var ctx4 = document.getElementById('dmgr-chart-wordfreq').getContext('2d');
                        dmgrState.chartInstances.wordfreq = new Chart(ctx4, {
                            type: 'bar',
                            data: {
                                labels: top15.map(function (d) { return d.word; }),
                                datasets: [{
                                    label: '出现次数',
                                    data: top15.map(function (d) { return d.count; }),
                                    backgroundColor: '#6f42c1'
                                }]
                            },
                            options: {
                                responsive: true,
                                maintainAspectRatio: false,
                                plugins: {
                                    legend: { display: false },
                                    tooltip: { callbacks: { label: function (ctx) { return ' 出现次数: ' + ctx.raw.toLocaleString(); } } }
                                },
                                scales: {
                                    x: { ticks: { font: { size: 10 }, maxRotation: 60 } },
                                    y: { ticks: { font: { size: 10 } }, title: { display: true, text: '次数', font: { size: 10 } } }
                                }
                            }
                        });
                    }

                    // Chart 5: 弹幕长度散点
                    var scatterSeries = s.danmakuScatter || [];
                    if (scatterSeries.length > 0) {
                        $('#dmgr-scatter-row').show();
                        var ctx5 = document.getElementById('dmgr-chart-scatter').getContext('2d');
                        // 收集所有点，同uid同色，不同uid不同色（用uid hash生成颜色）
                        var uidColorCache = {};
                        function uidToColor(uid) {
                            if (uidColorCache[uid]) return uidColorCache[uid];
                            var h = 0;
                            for (var i = 0; i < uid.length; i++) { h = uid.charCodeAt(i) + ((h << 5) - h); }
                            h = Math.abs(h) % 360;
                            uidColorCache[uid] = 'hsla(' + h + ',70%,45%,0.8)';
                            return uidColorCache[uid];
                        }
                        var allPoints = [];
                        $.each(scatterSeries, function (si, series) {
                            $.each(series.points || [], function (pi, p) {
                                var ms = new Date(p.time.replace(' ', 'T')).getTime();
                                allPoints.push({ x: ms, y: p.length, rawLen: p.rawLength, uid: series.uid, name: series.name });
                            });
                        });
                        allPoints.sort(function (a, b) { return a.x - b.x; });
                        if (allPoints.length > 0) {
                            dmgrState.chartInstances.dmScatter = new Chart(ctx5, {
                                type: 'scatter', data: {
                                    datasets: [{
                                        data: allPoints,
                                        pointRadius: 3, pointHoverRadius: 6,
                                        pointBackgroundColor: function (ctx) { return uidToColor(ctx.raw.uid); },
                                        pointBorderColor: function (ctx) { return uidToColor(ctx.raw.uid).replace('0.8','1'); }
                                    }]
                                },
                                options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' ' + (ctx.raw.name || '') + ' 长度:' + (ctx.raw.rawLen || ctx.raw.y * 5); } } } }, scales: { x: { ticks: { font: { size: 10 }, callback: function (v) { var d = new Date(v); var M = d.getMonth() + 1, D = d.getDate(), h = d.getHours(), m = d.getMinutes(); return M + '/' + D + ' ' + (h < 10 ? '0' : '') + h + ':' + (m < 10 ? '0' : '') + m; } }, title: { display: true, text: '时间', font: { size: 10 } } }, y: { ticks: { font: { size: 10 }, stepSize: 1 }, title: { display: true, text: '弹幕内容长度/5', font: { size: 10 } } } } }
                            });
                        } else { $('#dmgr-scatter-row').hide(); }
                    } else { $('#dmgr-scatter-row').hide(); }
                }
            }
        });
    },

    renderDmgrStats: function () {
        if (!dmgrState.currentFile) return;
        $.ajax({
            url: '../getBarrageStatistics',
            type: 'GET',
            data: {
                filePath: dmgrState.currentFile,
                startTime: dmgrState.startTime || '',
                endTime: dmgrState.endTime || '',
                limit: dmgrState.rankLimit
            },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var s = data.result;
                    $('#dmgr-stat-users').text((s.userCount || 0).toLocaleString());
                    $('#dmgr-stat-count').text((s.barrageCount || 0).toLocaleString());
                    $('#dmgr-stat-chars').text((s.totalChars || 0).toLocaleString());
                    var avgLen = s.barrageCount > 0 ? Math.round(s.totalChars / s.barrageCount) : 0;
                    $('#dmgr-stat-avg-len').text(avgLen.toLocaleString() + ' 字');
                    $('#dmgr-stats-row').show();
                }
            }
        });
    },

    exportDmgrCsv: function () {
        if (!dmgrState.currentFile) return;
        var url = '../exportBarrageFilteredCsv?filePath=' + encodeURIComponent(dmgrState.currentFile);
        if (dmgrState.startTime) url += '&startTime=' + encodeURIComponent(dmgrState.startTime);
        if (dmgrState.endTime) url += '&endTime=' + encodeURIComponent(dmgrState.endTime);
        if (dmgrState.search) url += '&search=' + encodeURIComponent(dmgrState.search);
        window.open(window.location.origin + url);
    },

    initDmgrColumnDrag: function () {
        var thead = document.getElementById('dmgr-table-head');
        if (!thead || !thead.querySelector('tr')) return;
        if (thead._sortable) { thead._sortable.destroy(); }
        thead._sortable = new Sortable(thead.querySelector('tr'), {
            animation: 150,
            filter: 'th[data-sortable="false"]',
            onEnd: function () {
                var order = [];
                $('#dmgr-table-head th').each(function (idx) {
                    var text = $(this).text().trim();
                    if (text === '发送时间') order.push(0);
                    else if (text === 'id') order.push(1);
                    else if (text === '名字') order.push(2);
                    else if (text === '弹幕') order.push(3);
                });
                dmgrState.columnOrder = order;
                method.loadDmgrData();
            }
        });
    },

    // ========== 观众管理 ==========

    loadVstCsvFileList: function () {
        $.ajax({
            url: '../listVisitorCsvFiles',
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var $select = $('#vst-csv-select');
                    $select.find('option[value!=""]').remove();
                    var firstOpt = null;
                    $.each(data.result, function (i, item) {
                        var label = item.fileName;
                        if (item.anchorName) label = item.anchorName + ' - ' + item.fileName;
                        var opt = $('<option>').val(item.filePath).text(label);
                        if (item.isCurrent === '1') { firstOpt = opt; opt.text(opt.text() + ' *'); }
                        $select.append(opt);
                    });
                    if (firstOpt) {
                        $select.val(firstOpt.val());
                        vstState.currentFile = firstOpt.val();
                    } else {
                        var fallback = $select.find('option[value!=""]').first();
                        if (fallback.length) {
                            $select.val(fallback.val());
                            vstState.currentFile = fallback.val();
                        }
                    }
                    if (vstState.currentFile) method.loadVstData();
                }
            }
        });
    },

    loadVstData: function () {
        if (!vstState.currentFile) return;
        if (!vstState.startTime && !vstState.endTime) {
            applyHoursFilter(vstState, '#vst');
        }
        $.ajax({
            url: '../readVisitorCsvData',
            type: 'GET',
            data: {
                filePath: vstState.currentFile,
                page: vstState.page,
                pageSize: vstState.pageSize,
                startTime: vstState.startTime || '',
                endTime: vstState.endTime || '',
                search: vstState.search || '',
                sortField: vstState.sortField,
                sortOrder: vstState.sortOrder
            },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var r = data.result;
                    vstState.page = r.currentPage || 1;
                    vstState.totalPages = r.totalPages || 0;
                    vstState.totalRows = r.total || 0;
                    vstState.fileFirstTime = r.firstTime || '';
                    vstState.fileLastTime = r.lastTime || '';
                    if (!vstState.startTime && !vstState.endTime) {
                        applyHoursFilter(vstState, '#vst');
                    }
                    method.renderVstTable(r.rows || []);
                    method.renderVstPagination();
                    if (r.total > 0) {
                        method.renderVstCharts();
                        method.renderVstStats();
                        $('#vst-stats-row, #vst-rank-limit-row').show();
                    } else {
                        $('#vst-stats-row, #vst-rank-limit-row').hide();
                        $('#vst-charts-row, #vst-freq-charts-row, #vst-scatter-row').hide();
                    }
                }
            }
        });
    },

    renderVstTable: function (rows) {
        var $tbody = $('#vst-table-body');
        var $table = $('#vst-data-table');
        var $empty = $('#vst-empty-msg');
        $tbody.empty();
        if (!rows || rows.length === 0) {
            $table.hide(); $('#vst-pagination').hide(); $empty.show(); return;
        }
        $empty.hide(); $table.show();
        var cols = vstState.headers;
        $.each(rows, function (i, row) {
            var tr = '<tr>';
            var uid = row['id'] || '';
            $.each(vstState.columnOrder, function (j, colIdx) {
                if (colIdx < cols.length) {
                    var val = row[cols[colIdx]] || '';
                    var colName = cols[colIdx];
                    var tdStyle = '';
                    if (colName === '最近' || colName === 'id') {
                        tdStyle = ' style="width:180px;text-align:left;"';
                    } else if (colName === '观众') {
                        tdStyle = ' style="width:40%;text-align:left;"';
                    } else if (colName === '打分类型') {
                        tdStyle = ' style="width:60%;text-align:left;"';
                    } else if (colName === '打分' || colName === '次数' || colName === '判定表' || colName === '场次') {
                        tdStyle = ' style="width:60px;text-align:right;"';
                    }
                    if (colName === '观众' && uid) {
                        tr += '<td' + tdStyle + '><a href="https://space.bilibili.com/' + uid + '" target="_blank" style="color:#0d6efd;">' + val + '</a></td>';
                    } else {
                        tr += '<td' + tdStyle + '>' + val + '</td>';
                    }
                }
            });
            tr += '</tr>';
            $tbody.append(tr);
        });
        // update sort indicators on headers
        $('#vst-table-head th[data-sort]').each(function () {
            var f = $(this).data('sort');
            $(this).html(f + '<span class="sort-arrow"></span>');
            var css = {cursor:'pointer'};
            if (f === '最近' || f === 'id') {
                css.width = '180px'; css.textAlign = 'left';
            } else if (f === '观众') {
                css.width = '40%'; css.textAlign = 'left';
            } else if (f === '打分类型') {
                css.width = '60%'; css.textAlign = 'left';
            } else if (f === '打分' || f === '次数' || f === '判定表' || f === '场次') {
                css.width = '60px'; css.textAlign = 'right';
            }
            $(this).css(css);
            if (f === vstState.sortField) {
                $(this).find('.sort-arrow').text(vstState.sortOrder === 'asc' ? ' ▲' : ' ▼');
            }
        });
        setTimeout(function () { method.initVstColumnDrag(); }, 100);
    },

    renderVstPagination: function () {
        var $p = $('#vst-pagination');
        if (vstState.totalPages <= 0) { $p.hide(); return; }
        $p.show();
        $('#vst-page-info').text('第' + vstState.page + '页 / 共' + vstState.totalPages + '页');
        $('#vst-page-jump').attr('max', vstState.totalPages).val('');
        var isFirst = vstState.page <= 1, isLast = vstState.page >= vstState.totalPages;
        $('#vst-first-btn, #vst-prev-btn').prop('disabled', isFirst);
        $('#vst-next-btn, #vst-last-btn').prop('disabled', isLast);
    },

    gotoVstPage: function (p) {
        p = parseInt(p);
        if (isNaN(p) || p < 1) p = 1;
        if (p > vstState.totalPages) p = vstState.totalPages;
        if (p === vstState.page) return;
        vstState.page = p;
        method.loadVstData();
    },

    sortVstColumn: function (field) {
        if (vstState.sortField === field) {
            vstState.sortOrder = vstState.sortOrder === 'asc' ? 'desc' : 'asc';
        } else {
            vstState.sortField = field;
            vstState.sortOrder = 'desc';
        }
        vstState.page = 1;
        $('#vst-stat-sort').text(field + (vstState.sortOrder === 'asc' ? '▲' : '▼'));
        method.loadVstData();
    },

    renderVstCharts: function () {
        $.each(vstState.chartInstances, function (k, c) { if (c) c.destroy(); });
        vstState.chartInstances = {};
        if (!vstState.currentFile) return;

        $.ajax({
            url: '../getVisitorStatistics',
            type: 'GET',
            data: { filePath: vstState.currentFile, startTime: vstState.startTime || '', endTime: vstState.endTime || '', limit: vstState.rankLimit },
            dataType: 'json', async: false,
            success: function (data) {
                if (data.code != "200" || !data.result) { $('#vst-charts-row, #vst-freq-charts-row, #vst-scatter-row').hide(); return; }
                var s = data.result;
                var perInt = s.perIntervalData || [];
                var top5 = s.top15Visitors || [];
                var fieldRank = s.fieldRanking || [];
                var scoreDist = s.scoreDistribution || [];
                var visitFreq = s.visitCountDist || [];
                var fieldFreq = s.fieldCountDist || [];
                var scatterData = s.scatterData || [];
                if (perInt.length === 0 && top5.length === 0 && fieldRank.length === 0 && scoreDist.length === 0) { $('#vst-charts-row,#vst-scatter-row').hide(); return; }
                $('#vst-charts-row').show();

                // Chart 1: 观众数量 line
                if (perInt.length > 0) {
                    var ctx1 = document.getElementById('vst-chart-count').getContext('2d');
                    vstState.chartInstances.count = new Chart(ctx1, {
                        type: 'line', data: {
                            labels: perInt.map(function (d) { return d.time; }),
                            datasets: [{ label: '人数', data: perInt.map(function (d) { return d.count; }), borderColor: '#0d6efd', borderWidth: 1.5, pointRadius: 0, pointHoverRadius: 4, tension: 0.1, fill: true, backgroundColor: 'rgba(13,110,253,0.06)' }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false }, plugins: { legend: { display: true, position: 'top', labels: { font: { size: 10 } } }, tooltip: { callbacks: { label: function (ctx) { return ' 人数: ' + ctx.raw.toLocaleString(); } } } }, scales: { x: { ticks: { font: { size: 10 } } }, y: { ticks: { font: { size: 10 } } } } }
                    });
                }

                // Chart 2: 观众打分分布 bar
                if (scoreDist.length > 0) {
                    var ctx2 = document.getElementById('vst-chart-score').getContext('2d');
                    vstState.chartInstances.score = new Chart(ctx2, {
                        type: 'bar', data: {
                            labels: scoreDist.map(function (d) { return d.score; }),
                            datasets: [{ label: '人数', data: scoreDist.map(function (d) { return d.count; }), backgroundColor: '#198754' }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 分数 ' + ctx.label + ': ' + ctx.raw.toLocaleString() + ' 人'; } } } }, scales: { x: { ticks: { font: { size: 10 } }, title: { display: true, text: '分数', font: { size: 10 } } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '人数', font: { size: 10 } } } } }
                    });
                }

                // Chart 3: 进出榜 top5 vertical bar
                if (top5.length > 0) {
                    var ctx3 = document.getElementById('vst-chart-top5').getContext('2d');
                    vstState.chartInstances.top5 = new Chart(ctx3, {
                        type: 'bar', data: {
                            labels: top5.map(function (d) { return d.name; }),
                            datasets: [{ label: '次数', data: top5.map(function (d) { return d.count; }), backgroundColor: ['#0d6efd','#198754','#fd7e14','#dc3545','#6f42c1'] }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 次数: ' + ctx.raw.toLocaleString(); } } } }, scales: { x: { ticks: { font: { size: 10 }, maxRotation: 45 } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '次数', font: { size: 10 } } } } }
                    });
                }

                // Chart 4: 场次榜 top15 vertical bar
                if (fieldRank.length > 0) {
                    var ctx4 = document.getElementById('vst-chart-wordfreq').getContext('2d');
                    vstState.chartInstances.field = new Chart(ctx4, {
                        type: 'bar', data: {
                            labels: fieldRank.map(function (d) { return d.name; }),
                            datasets: [{ label: '场次', data: fieldRank.map(function (d) { return d.count; }), backgroundColor: '#6f42c1' }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 场次: ' + ctx.raw.toLocaleString(); } } } }, scales: { x: { ticks: { font: { size: 10 }, maxRotation: 45 } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '场次', font: { size: 10 } } } } }
                    });
                }

                // Chart 5: 进出频次分布 bar
                if (visitFreq.length > 0) {
                    $('#vst-freq-charts-row').show();
                    var ctx5 = document.getElementById('vst-chart-visitfreq').getContext('2d');
                    vstState.chartInstances.visitfreq = new Chart(ctx5, {
                        type: 'bar', data: {
                            labels: visitFreq.map(function (d) { return d.count; }),
                            datasets: [{ label: '频次', data: visitFreq.map(function (d) { return d.freq; }), backgroundColor: '#0d6efd' }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 次数 ' + ctx.label + ': ' + ctx.raw.toLocaleString() + ' 人'; } } } }, scales: { x: { ticks: { font: { size: 10 } }, title: { display: true, text: '次数', font: { size: 10 } } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '频次(人数)', font: { size: 10 } } } } }
                    });
                } else { $('#vst-freq-charts-row').hide(); }

                // Chart 6: 场次频次分布 bar
                if (fieldFreq.length > 0) {
                    $('#vst-freq-charts-row').show();
                    var ctx6 = document.getElementById('vst-chart-fieldfreq').getContext('2d');
                    vstState.chartInstances.fieldfreq = new Chart(ctx6, {
                        type: 'bar', data: {
                            labels: fieldFreq.map(function (d) { return d.count; }),
                            datasets: [{ label: '频次', data: fieldFreq.map(function (d) { return d.freq; }), backgroundColor: '#6f42c1' }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 场次 ' + ctx.label + ': ' + ctx.raw.toLocaleString() + ' 人'; } } } }, scales: { x: { ticks: { font: { size: 10 } }, title: { display: true, text: '场次', font: { size: 10 } } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '频次(人数)', font: { size: 10 } } } } }
                    });
                }

                // Chart 7: 观众成份 scatter
                if (scatterData.length > 0) {
                    $('#vst-scatter-row').show();
                    var ctx7 = document.getElementById('vst-chart-scatter').getContext('2d');
                    // 收集所有唯一分值排序，每个分值一行，等距排列
                    var yVals = []; var ySet = {};
                    scatterData.forEach(function (d) { if (!ySet[d.score]) { ySet[d.score] = true; yVals.push(d.score); } });
                    yVals.sort(function (a, b) { return a - b; });
                    var scoreIdx = {}; yVals.forEach(function (v, i) { scoreIdx[v] = i; });
                    var scatterPoints = scatterData.map(function (d) {
                        var ms = new Date(d.time.replace(' ', 'T')).getTime();
                        return { x: ms, y: scoreIdx[d.score], score: d.score, name: d.name };
                    });
                    vstState.chartInstances.scatter = new Chart(ctx7, {
                        type: 'scatter', data: {
                            datasets: [{ label: '观众', data: scatterPoints, pointRadius: 4, pointHoverRadius: 7,
                                pointBackgroundColor: function (ctx) { var v = ctx.raw.score; return v > 0 ? 'rgba(25,135,84,0.6)' : v < 0 ? 'rgba(220,53,69,0.6)' : 'rgba(13,110,253,0.6)'; },
                                pointBorderColor: function (ctx) { var v = ctx.raw.score; return v > 0 ? '#198754' : v < 0 ? '#dc3545' : '#0d6efd'; }
                            }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' ' + (ctx.raw.name || '') + ' 分数:' + ctx.raw.score; } } } }, scales: { x: { ticks: { font: { size: 10 }, callback: function (v) { var d = new Date(v); var M = d.getMonth() + 1, D = d.getDate(), h = d.getHours(), m = d.getMinutes(); return M + '/' + D + ' ' + (h < 10 ? '0' : '') + h + ':' + (m < 10 ? '0' : '') + m; } }, title: { display: true, text: '时间', font: { size: 10 } } }, y: { ticks: { font: { size: 10 }, stepSize: 1, callback: function (v, i) { return yVals[i] !== undefined ? yVals[i] : ''; } }, title: { display: true, text: '打分', font: { size: 10 } } } } }
                    });
                } else { $('#vst-scatter-row').hide(); }
            }
        });
    },

    renderVstStats: function () {
        if (!vstState.currentFile) return;
        $.ajax({
            url: '../getVisitorStatistics',
            type: 'GET',
            data: { filePath: vstState.currentFile, startTime: vstState.startTime || '', endTime: vstState.endTime || '', limit: vstState.rankLimit },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var s = data.result;
                    // update rank limit max
                    var maxRank = Math.max(1, Math.floor((s.actualPeople || 0) / 2));
                    $('#vst-rank-limit').attr('max', maxRank);
                    if (vstState.rankLimit > maxRank) { vstState.rankLimit = maxRank; $('#vst-rank-limit').val(maxRank); }
                    $('#vst-stat-visits').text((s.totalVisits || 0).toLocaleString());
                    $('#vst-stat-actual').text((s.actualPeople || 0).toLocaleString());
                    $('#vst-stat-avgpm').text((s.avgPerMin || 0).toLocaleString());
                    $('#vst-stat-score').text((s.scoreSum || 0).toLocaleString() + ' / ' + (s.scoreAvg || 0).toLocaleString());
                    $('#vst-stat-pn').text((s.pnYes || 0) + ' 是 / ' + (s.pnNo || 0) + ' 否');
                    $('#vst-stats-row').show();
                }
            }
        });
    },

    exportVstCsv: function () {
        if (!vstState.currentFile) return;
        var url = '../exportVisitorFilteredCsv?filePath=' + encodeURIComponent(vstState.currentFile);
        if (vstState.startTime) url += '&startTime=' + encodeURIComponent(vstState.startTime);
        if (vstState.endTime) url += '&endTime=' + encodeURIComponent(vstState.endTime);
        if (vstState.search) url += '&search=' + encodeURIComponent(vstState.search);
        window.open(window.location.origin + url);
    },

    initVstColumnDrag: function () {
        var thead = document.getElementById('vst-table-head');
        if (!thead || !thead.querySelector('tr')) return;
        if (thead._sortable) thead._sortable.destroy();
        thead._sortable = new Sortable(thead.querySelector('tr'), {
            animation: 150, filter: 'th[data-sortable="false"]',
            onEnd: function () {
                var order = [];
                $('#vst-table-head th').each(function (idx) {
                    var text = $(this).text().trim().replace(/ [▲▼]/, '');
                    var fi = vstState.headers.indexOf(text);
                    if (fi >= 0) order.push(fi);
                });
                vstState.columnOrder = order;
                method.loadVstData();
            }
        });
    },

    // ========== 匹配管理 ==========

    loadMtchCsvFileList: function () {
        $.ajax({
            url: '../listMatchCsvFiles',
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var $select = $('#mtch-csv-select');
                    $select.find('option[value!=""]').remove();
                    var firstOpt = null;
                    $.each(data.result, function (i, f) {
                        var label = f.fileName + (f.anchorName ? ' (' + f.anchorName + ')' : '');
                        var opt = $('<option>').val(f.filePath).text(label);
                        if (f.isCurrent === '1') { firstOpt = opt; opt.text(opt.text() + ' *'); }
                        $select.append(opt);
                    });
                    if (firstOpt) {
                        $select.val(firstOpt.val());
                        mtchState.currentFile = firstOpt.val();
                    } else {
                        // 降级：没有当前房间时选第一个
                        var fallback = $select.find('option[value!=""]').first();
                        if (fallback.length) {
                            $select.val(fallback.val());
                            mtchState.currentFile = fallback.val();
                        }
                    }
                    if (mtchState.currentFile) method.loadMtchData();
                }
            }
        });
    },

    loadMtchData: function () {
        if (!mtchState.currentFile) return;
        if (!mtchState.startTime && !mtchState.endTime) {
            applyHoursFilter(mtchState, '#mtch');
        }
        $.ajax({
            url: '../readMatchCsvData',
            type: 'GET',
            data: {
                filePath: mtchState.currentFile,
                page: mtchState.page,
                pageSize: mtchState.pageSize,
                startTime: mtchState.startTime || '',
                endTime: mtchState.endTime || '',
                search: mtchState.search || '',
                sortField: mtchState.sortField,
                sortOrder: mtchState.sortOrder
            },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var r = data.result;
                    mtchState.page = r.currentPage || 1;
                    mtchState.totalPages = r.totalPages || 0;
                    mtchState.totalRows = r.total || 0;
                    var st1 = r.firstTime || '';
                    var st2 = r.firstTime || '';
                    if (st1 && st2) { mtchState.fileFirstTime = st1 < st2 ? st1 : st2; }
                    else { mtchState.fileFirstTime = st1 || st2; }
                    mtchState.fileLastTime = r.lastTime || '';
                    if (!mtchState.startTime && !mtchState.endTime) {
                        applyHoursFilter(mtchState, '#mtch');
                    }
                    method.renderMtchTable(r.rows || []);
                    method.renderMtchPagination();
                    if (r.total > 0) {
                        method.renderMtchCharts();
                        $('#mtch-rank-limit-row').show();
                    } else {
                        $('#mtch-rank-limit-row').hide();
                        $('#mtch-charts-row').hide();
                    }
                }
            }
        });
    },

    renderMtchTable: function (rows) {
        var $tbody = $('#mtch-table-body');
        var $table = $('#mtch-data-table');
        var $empty = $('#mtch-empty-msg');
        $tbody.empty();
        if (!rows || rows.length === 0) {
            $table.hide(); $('#mtch-pagination').hide(); $empty.show(); return;
        }
        $empty.hide(); $table.show();
        var cols = mtchState.headers;
        $.each(rows, function (i, row) {
            var tr = '<tr>';
            var uid = row['匹配id'] || '';
            $.each(mtchState.columnOrder, function (j, colIdx) {
                if (colIdx < cols.length) {
                    var val = row[cols[colIdx]] || '';
                    if (cols[colIdx] === '匹配名' && uid) {
                        tr += '<td><a href="https://space.bilibili.com/' + uid + '" target="_blank" style="color:#0d6efd;">' + val + '</a></td>';
                    } else {
                        tr += '<td>' + val + '</td>';
                    }
                }
            });
            tr += '</tr>';
            $tbody.append(tr);
        });
        $('#mtch-table-head th[data-sort]').each(function () {
            var f = $(this).data('sort');
            $(this).text(f);
            if (f === mtchState.sortField) {
                $(this).text(f + ' ' + (mtchState.sortOrder === 'asc' ? '▲' : '▼'));
            }
        });
        setTimeout(function () { method.initMtchColumnDrag(); }, 100);
    },

    renderMtchPagination: function () {
        var $p = $('#mtch-pagination');
        if (mtchState.totalPages <= 0) { $p.hide(); return; }
        $p.show();
        $('#mtch-page-info').text('第' + mtchState.page + '页 / 共' + mtchState.totalPages + '页');
        $('#mtch-page-jump').attr('max', mtchState.totalPages).val('');
        var isFirst = mtchState.page <= 1, isLast = mtchState.page >= mtchState.totalPages;
        $('#mtch-first-btn, #mtch-prev-btn').prop('disabled', isFirst);
        $('#mtch-next-btn, #mtch-last-btn').prop('disabled', isLast);
    },

    gotoMtchPage: function (p) {
        p = parseInt(p);
        if (isNaN(p) || p < 1) p = 1;
        if (p > mtchState.totalPages) p = mtchState.totalPages;
        if (p === mtchState.page) return;
        mtchState.page = p;
        method.loadMtchData();
    },

    sortMtchColumn: function (field) {
        if (mtchState.sortField === field) {
            mtchState.sortOrder = mtchState.sortOrder === 'asc' ? 'desc' : 'asc';
        } else {
            mtchState.sortField = field;
            mtchState.sortOrder = 'desc';
        }
        mtchState.page = 1;
        method.loadMtchData();
    },

    renderMtchCharts: function () {
        $.each(mtchState.chartInstances, function (k, c) { if (c) c.destroy(); });
        mtchState.chartInstances = {};
        if (!mtchState.currentFile) return;

        $.ajax({
            url: '../getMatchStatistics',
            type: 'GET',
            data: { filePath: mtchState.currentFile, startTime: mtchState.startTime || '', endTime: mtchState.endTime || '', limit: mtchState.rankLimit },
            dataType: 'json',
            success: function (data) {
                if (data.code != "200" || !data.result) { $('#mtch-charts-row').hide(); return; }
                var s = data.result;
                var scoreDist = s.scoreDistribution || [];
                var freqDist = s.matchCountDist || [];
                var topMatches = s.topMatches || [];
                if (scoreDist.length === 0 && freqDist.length === 0 && topMatches.length === 0) { $('#mtch-charts-row').hide(); return; }
                $('#mtch-charts-row').show();

                setTimeout(function () {
                // Chart 1: 匹配分分布
                if (scoreDist.length > 0) {
                    var ctx1 = document.getElementById('mtch-chart-score').getContext('2d');
                    mtchState.chartInstances.score = new Chart(ctx1, {
                        type: 'bar', data: {
                            labels: scoreDist.map(function (d) { return d.score; }),
                            datasets: [{ label: '人数', data: scoreDist.map(function (d) { return d.count; }), backgroundColor: '#198754' }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 匹配分 ' + ctx.label + ': ' + ctx.raw.toLocaleString() + ' 人'; } } } }, scales: { x: { ticks: { font: { size: 10 } }, title: { display: true, text: '匹配分', font: { size: 10 } } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '人数', font: { size: 10 } } } } }
                    });
                }

                // Chart 2: 匹配频次分布
                if (freqDist.length > 0) {
                    var ctx2 = document.getElementById('mtch-chart-freq').getContext('2d');
                    mtchState.chartInstances.freq = new Chart(ctx2, {
                        type: 'bar', data: {
                            labels: freqDist.map(function (d) { return d.count; }),
                            datasets: [{ label: '频次', data: freqDist.map(function (d) { return d.freq; }), backgroundColor: '#0d6efd' }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 匹配次数 ' + ctx.label + ': ' + ctx.raw.toLocaleString() + ' 人'; } } } }, scales: { x: { ticks: { font: { size: 10 } }, title: { display: true, text: '匹配次数', font: { size: 10 } } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '频次(人数)', font: { size: 10 } } } } }
                    });
                }

                // Chart 3: 匹配次数排行
                if (topMatches.length > 0) {
                    var ctx3 = document.getElementById('mtch-chart-rank').getContext('2d');
                    mtchState.chartInstances.rank = new Chart(ctx3, {
                        type: 'bar', data: {
                            labels: topMatches.map(function (d) { return d.name; }),
                            datasets: [{ label: '匹配次数', data: topMatches.map(function (d) { return d.count; }), backgroundColor: ['#0d6efd', '#198754', '#fd7e14', '#dc3545', '#6f42c1'] }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 匹配次数: ' + ctx.raw.toLocaleString(); } } } }, scales: { x: { ticks: { font: { size: 10 } }, title: { display: true, text: '名字', font: { size: 10 } } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '匹配次数', font: { size: 10 } } } } }
                    });
                }
                }, 50);
            }
        });
    },

    exportMtchCsv: function () {
        if (!mtchState.currentFile) return;
        var url = '../exportMatchFilteredCsv?filePath=' + encodeURIComponent(mtchState.currentFile);
        if (mtchState.startTime) url += '&startTime=' + encodeURIComponent(mtchState.startTime);
        if (mtchState.endTime) url += '&endTime=' + encodeURIComponent(mtchState.endTime);
        if (mtchState.search) url += '&search=' + encodeURIComponent(mtchState.search);
        window.open(url, '_blank');
    },

    initMtchColumnDrag: function () {
        var thead = document.getElementById('mtch-table-head');
        if (!thead || thead.sortableInstance) return;
        thead.sortableInstance = new Sortable(thead, {
            animation: 150, ghostClass: 'sortable-ghost', chosenClass: 'sortable-chosen',
            onEnd: function () {
                var order = [];
                $('#mtch-table-head th').each(function () {
                    var txt = $(this).text().replace(/ [▲▼]/, '');
                    var idx = mtchState.headers.indexOf(txt);
                    if (idx >= 0) order.push(idx);
                });
                if (order.length === mtchState.headers.length) {
                    mtchState.columnOrder = order;
                    method.loadMtchData();
                }
            }
        });
    },

    // ========== 关注人管理 ==========

    loadFlwCsvFileList: function () {
        $.ajax({
            url: '../listFollowCsvFiles',
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var $select = $('#flw-csv-select');
                    $select.find('option[value!=""]').remove();
                    var firstOpt = null;
                    $.each(data.result, function (i, f) {
                        var label = f.fileName + (f.anchorName ? ' (' + f.anchorName + ')' : '');
                        var opt = $('<option>').val(f.filePath).text(label);
                        if (f.isCurrent === '1') { firstOpt = opt; opt.text(opt.text() + ' *'); }
                        $select.append(opt);
                    });
                    if (firstOpt) {
                        $select.val(firstOpt.val());
                        flwState.currentFile = firstOpt.val();
                    } else {
                        // 降级：没有当前房间时选第一个
                        var fallback = $select.find('option[value!=""]').first();
                        if (fallback.length) {
                            $select.val(fallback.val());
                            flwState.currentFile = fallback.val();
                        }
                    }
                    if (flwState.currentFile) method.loadFlwData();
                }
            }
        });
    },

    loadFlwData: function () {
        if (!flwState.currentFile) return;
        if (!flwState.startTime && !flwState.endTime) {
            applyHoursFilter(flwState, '#flw');
        }
        $.ajax({
            url: '../readFollowCsvData',
            type: 'GET',
            data: {
                filePath: flwState.currentFile,
                page: flwState.page,
                pageSize: flwState.pageSize,
                startTime: flwState.startTime || '',
                endTime: flwState.endTime || '',
                search: flwState.search || '',
                sortField: flwState.sortField,
                sortOrder: flwState.sortOrder
            },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var r = data.result;
                    flwState.page = r.currentPage || 1;
                    flwState.totalPages = r.totalPages || 0;
                    flwState.totalRows = r.total || 0;
                    flwState.fileFirstTime = r.firstTime || '';
                    flwState.fileLastTime = r.lastTime || '';
                    if (!flwState.startTime && !flwState.endTime) {
                        applyHoursFilter(flwState, '#flw');
                    }
                    method.renderFlwTable(r.rows || []);
                    method.renderFlwPagination();
                    if (r.total > 0) {
                        method.renderFlwCharts();
                        $('#flw-rank-limit-row').show();
                    } else {
                        $('#flw-rank-limit-row').hide();
                        $('#flw-charts-row').hide();
                    }
                }
            }
        });
    },

    renderFlwTable: function (rows) {
        var $tbody = $('#flw-table-body');
        var $table = $('#flw-data-table');
        var $empty = $('#flw-empty-msg');
        $tbody.empty();
        if (!rows || rows.length === 0) {
            $table.hide(); $('#flw-pagination').hide(); $empty.show(); return;
        }
        $empty.hide(); $table.show();
        var cols = flwState.headers;
        $.each(rows, function (i, row) {
            var tr = '<tr>';
            var uid = row['id'] || '';
            $.each(flwState.columnOrder, function (j, colIdx) {
                if (colIdx < cols.length) {
                    var val = row[cols[colIdx]] || '';
                    if (cols[colIdx] === '名字' && uid) {
                        tr += '<td><a href="https://space.bilibili.com/' + uid + '" target="_blank" style="color:#0d6efd;">' + val + '</a></td>';
                    } else {
                        tr += '<td>' + val + '</td>';
                    }
                }
            });
            tr += '</tr>';
            $tbody.append(tr);
        });
        $('#flw-table-head th[data-sort]').each(function () {
            var f = $(this).data('sort');
            $(this).text(f);
            if (f === flwState.sortField) {
                $(this).text(f + ' ' + (flwState.sortOrder === 'asc' ? '▲' : '▼'));
            }
        });
        setTimeout(function () { method.initFlwColumnDrag(); }, 100);
    },

    renderFlwPagination: function () {
        var $p = $('#flw-pagination');
        if (flwState.totalPages <= 0) { $p.hide(); return; }
        $p.show();
        $('#flw-page-info').text('第' + flwState.page + '页 / 共' + flwState.totalPages + '页');
        $('#flw-page-jump').attr('max', flwState.totalPages).val('');
        var isFirst = flwState.page <= 1, isLast = flwState.page >= flwState.totalPages;
        $('#flw-first-btn, #flw-prev-btn').prop('disabled', isFirst);
        $('#flw-next-btn, #flw-last-btn').prop('disabled', isLast);
    },

    gotoFlwPage: function (p) {
        p = parseInt(p);
        if (isNaN(p) || p < 1) p = 1;
        if (p > flwState.totalPages) p = flwState.totalPages;
        if (p === flwState.page) return;
        flwState.page = p;
        method.loadFlwData();
    },

    sortFlwColumn: function (field) {
        if (flwState.sortField === field) {
            flwState.sortOrder = flwState.sortOrder === 'asc' ? 'desc' : 'asc';
        } else {
            flwState.sortField = field;
            flwState.sortOrder = 'desc';
        }
        flwState.page = 1;
        method.loadFlwData();
    },

    renderFlwCharts: function () {
        $.each(flwState.chartInstances, function (k, c) { if (c) c.destroy(); });
        flwState.chartInstances = {};
        if (!flwState.currentFile) return;

        $.ajax({
            url: '../getFollowStatistics',
            type: 'GET',
            data: { filePath: flwState.currentFile, startTime: flwState.startTime || '', endTime: flwState.endTime || '', limit: flwState.rankLimit },
            dataType: 'json',
            success: function (data) {
                if (data.code != "200" || !data.result) { $('#flw-charts-row').hide(); return; }
                var s = data.result;
                var freqDist = s.countDist || [];
                var topFollows = s.topFollows || [];
                if (freqDist.length === 0 && topFollows.length === 0) { $('#flw-charts-row').hide(); return; }
                $('#flw-charts-row').show();

                setTimeout(function () {
                // Chart 1: 关注频次分布
                if (freqDist.length > 0) {
                    var ctx1 = document.getElementById('flw-chart-freq').getContext('2d');
                    flwState.chartInstances.freq = new Chart(ctx1, {
                        type: 'bar', data: {
                            labels: freqDist.map(function (d) { return d.count; }),
                            datasets: [{ label: '频次', data: freqDist.map(function (d) { return d.freq; }), backgroundColor: '#0d6efd' }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 次数 ' + ctx.label + ': ' + ctx.raw.toLocaleString() + ' 人'; } } } }, scales: { x: { ticks: { font: { size: 10 } }, title: { display: true, text: '次数', font: { size: 10 } } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '频次(人数)', font: { size: 10 } } } } }
                    });
                }

                // Chart 2: 关注次数排行
                if (topFollows.length > 0) {
                    var ctx2 = document.getElementById('flw-chart-rank').getContext('2d');
                    flwState.chartInstances.rank = new Chart(ctx2, {
                        type: 'bar', data: {
                            labels: topFollows.map(function (d) { return d.name; }),
                            datasets: [{ label: '次数', data: topFollows.map(function (d) { return d.count; }), backgroundColor: ['#0d6efd', '#198754', '#fd7e14', '#dc3545', '#6f42c1'] }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 次数: ' + ctx.raw.toLocaleString(); } } } }, scales: { x: { ticks: { font: { size: 10 } }, title: { display: true, text: '名字', font: { size: 10 } } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '次数', font: { size: 10 } } } } }
                    });
                }
                }, 50);
            }
        });
    },

    exportFlwCsv: function () {
        if (!flwState.currentFile) return;
        var url = '../exportFollowFilteredCsv?filePath=' + encodeURIComponent(flwState.currentFile);
        if (flwState.startTime) url += '&startTime=' + encodeURIComponent(flwState.startTime);
        if (flwState.endTime) url += '&endTime=' + encodeURIComponent(flwState.endTime);
        if (flwState.search) url += '&search=' + encodeURIComponent(flwState.search);
        window.open(url, '_blank');
    },

    initFlwColumnDrag: function () {
        var thead = document.getElementById('flw-table-head');
        if (!thead || thead.sortableInstance) return;
        thead.sortableInstance = new Sortable(thead, {
            animation: 150, ghostClass: 'sortable-ghost', chosenClass: 'sortable-chosen',
            onEnd: function () {
                var order = [];
                $('#flw-table-head th').each(function () {
                    var txt = $(this).text().replace(/ [▲▼]/, '');
                    var idx = flwState.headers.indexOf(txt);
                    if (idx >= 0) order.push(idx);
                });
                if (order.length === flwState.headers.length) {
                    flwState.columnOrder = order;
                    method.loadFlwData();
                }
            }
        });
    },

    // ========== 礼物管理 ==========

    loadGftCsvFileList: function () {
        $.ajax({
            url: '../listGiftCsvFiles',
            type: 'GET', dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var $select = $('#gft-csv-select');
                    $select.find('option[value!=""]').remove();
                    var firstOpt = null;
                    $.each(data.result, function (i, f) {
                        var label = f.fileName + (f.anchorName ? ' (' + f.anchorName + ')' : '');
                        var opt = $('<option>').val(f.filePath).text(label);
                        if (f.isCurrent === '1') { firstOpt = opt; opt.text(opt.text() + ' *'); }
                        $select.append(opt);
                    });
                    if (firstOpt) { $select.val(firstOpt.val()); gftState.currentFile = firstOpt.val(); }
                    else {
                        var fallback = $select.find('option[value!=""]').first();
                        if (fallback.length) { $select.val(fallback.val()); gftState.currentFile = fallback.val(); }
                    }
                    if (gftState.currentFile) method.loadGftData();
                }
            }
        });
    },

    loadGftData: function () {
        if (!gftState.currentFile) return;
        if (!gftState.startTime && !gftState.endTime) {
            applyHoursFilter(gftState, '#gft');
        }
        $.ajax({
            url: '../readGiftCsvData', type: 'GET',
            data: { filePath: gftState.currentFile, page: gftState.page, pageSize: gftState.pageSize, startTime: gftState.startTime || '', endTime: gftState.endTime || '', search: gftState.search || '', sortField: gftState.sortField, sortOrder: gftState.sortOrder },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var r = data.result;
                    gftState.page = r.currentPage || 1;
                    gftState.totalPages = r.totalPages || 0;
                    gftState.totalRows = r.total || 0;
                    gftState.fileFirstTime = r.firstTime || '';
                    gftState.fileLastTime = r.lastTime || '';
                    if (!gftState.startTime && !gftState.endTime) {
                        applyHoursFilter(gftState, '#gft');
                    }
                    method.renderGftTable(r.rows || []);
                    method.renderGftPagination();
                    if (r.total > 0) { method.renderGftStats(); method.renderGftCharts(); $('#gft-stats-row, #gft-rank-limit-row').show(); }
                    else { $('#gft-stats-row, #gft-rank-limit-row').hide(); $('#gft-charts-row').hide(); }
                }
            }
        });
    },

    renderGftTable: function (rows) {
        var $tbody = $('#gft-table-body'), $table = $('#gft-data-table'), $empty = $('#gft-empty-msg');
        $tbody.empty();
        if (!rows || rows.length === 0) { $table.hide(); $('#gft-pagination').hide(); $empty.show(); return; }
        $empty.hide(); $table.show();
        var cols = gftState.headers;
        $.each(rows, function (i, row) {
            var tr = '<tr>';
            var uid = row['id'] || '';
            $.each(gftState.columnOrder, function (j, colIdx) {
                if (colIdx < cols.length) {
                    var val = row[cols[colIdx]] || '';
                    if (cols[colIdx] === '名字' && uid) {
                        tr += '<td><a href="https://space.bilibili.com/' + uid + '" target="_blank" style="color:#0d6efd;">' + val + '</a></td>';
                    } else {
                        tr += '<td>' + val + '</td>';
                    }
                }
            });
            tr += '</tr>'; $tbody.append(tr);
        });
        $('#gft-table-head th[data-sort]').each(function () {
            var f = $(this).data('sort'); $(this).text(f);
            if (f === gftState.sortField) $(this).text(f + ' ' + (gftState.sortOrder === 'asc' ? '▲' : '▼'));
        });
        setTimeout(function () { method.initGftColumnDrag(); }, 100);
    },

    renderGftPagination: function () {
        var $p = $('#gft-pagination');
        if (gftState.totalPages <= 0) { $p.hide(); return; }
        $p.show();
        $('#gft-page-info').text('第' + gftState.page + '页 / 共' + gftState.totalPages + '页');
        $('#gft-page-jump').attr('max', gftState.totalPages).val('');
        var isFirst = gftState.page <= 1, isLast = gftState.page >= gftState.totalPages;
        $('#gft-first-btn, #gft-prev-btn').prop('disabled', isFirst);
        $('#gft-next-btn, #gft-last-btn').prop('disabled', isLast);
    },

    gotoGftPage: function (p) {
        p = parseInt(p);
        if (isNaN(p) || p < 1) p = 1;
        if (p > gftState.totalPages) p = gftState.totalPages;
        if (p === gftState.page) return;
        gftState.page = p; method.loadGftData();
    },

    sortGftColumn: function (field) {
        if (gftState.sortField === field) { gftState.sortOrder = gftState.sortOrder === 'asc' ? 'desc' : 'asc'; }
        else { gftState.sortField = field; gftState.sortOrder = 'desc'; }
        gftState.page = 1; method.loadGftData();
    },

    renderGftStats: function () {
        if (!gftState.currentFile) return;
        $.ajax({
            url: '../getGiftStatistics', type: 'GET',
            data: { filePath: gftState.currentFile, startTime: gftState.startTime || '', endTime: gftState.endTime || '', limit: 1 },
            dataType: 'json',
            success: function (data) {
                if (data.code == "200" && data.result) {
                    var s = data.result;
                    $('#gft-stat-amount').text((s.totalAmount || 0).toLocaleString());
                    $('#gft-stat-users').text((s.uniqueUsers || 0).toLocaleString());
                    $('#gft-stat-records').text((s.totalRecords || 0).toLocaleString());
                }
            }
        });
    },

    renderGftCharts: function () {
        $.each(gftState.chartInstances, function (k, c) { if (c) c.destroy(); });
        gftState.chartInstances = {};
        if (!gftState.currentFile) return;
        $.ajax({
            url: '../getGiftStatistics', type: 'GET',
            data: { filePath: gftState.currentFile, startTime: gftState.startTime || '', endTime: gftState.endTime || '', limit: gftState.rankLimit },
            dataType: 'json',
            success: function (data) {
                if (data.code != "200" || !data.result) { $('#gft-charts-row').hide(); return; }
                var s = data.result;
                var perInt = s.perIntervalData || [];
                var amountRanking = s.amountRanking || [];
                var giftNameFreq = s.giftNameFreq || [];
                if (perInt.length === 0 && amountRanking.length === 0 && giftNameFreq.length === 0) { $('#gft-charts-row').hide(); return; }
                $('#gft-charts-row').show();
                setTimeout(function () {
                if (perInt.length > 0) {
                    var ctx0 = document.getElementById('gft-chart-trend').getContext('2d');
                    gftState.chartInstances.trend = new Chart(ctx0, {
                        type: 'line', data: {
                            labels: perInt.map(function (d) { return d.time; }),
                            datasets: [{ label: '电池', data: perInt.map(function (d) { return d.amount; }), borderColor: '#fd7e14', borderWidth: 1.5, pointRadius: 0, pointHoverRadius: 4, tension: 0.1, fill: true, backgroundColor: 'rgba(253,126,20,0.06)' }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, interaction: { mode: 'index', intersect: false }, plugins: { legend: { display: true, position: 'top', labels: { font: { size: 10 } } }, tooltip: { titleFont: { size: 12 }, bodyFont: { size: 12 }, callbacks: { title: function (ctxs) { return '时间: ' + ctxs[0].label; }, label: function (ctx) { return ' ' + ctx.dataset.label + ': ' + ctx.raw.toLocaleString(); } } } }, scales: { x: { ticks: { font: { size: 10 } } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '电池', font: { size: 10 } } } } }
                    });
                }
                if (amountRanking.length > 0) {
                    var ctx1 = document.getElementById('gft-chart-rank').getContext('2d');
                    gftState.chartInstances.rank = new Chart(ctx1, {
                        type: 'bar', data: {
                            labels: amountRanking.map(function (d) { return d.name; }),
                            datasets: [{ label: '金额', data: amountRanking.map(function (d) { return d.amount; }), backgroundColor: ['#0d6efd','#198754','#fd7e14','#dc3545','#6f42c1'] }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 金额: ' + ctx.raw.toLocaleString(); } } } }, scales: { x: { ticks: { font: { size: 10 } }, title: { display: true, text: '名字', font: { size: 10 } } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '金额', font: { size: 10 } } } } }
                    });
                }
                if (giftNameFreq.length > 0) {
                    var ctx2 = document.getElementById('gft-chart-freq').getContext('2d');
                    gftState.chartInstances.freq = new Chart(ctx2, {
                        type: 'bar', data: {
                            labels: giftNameFreq.map(function (d) { return d.name; }),
                            datasets: [{ label: '次数', data: giftNameFreq.map(function (d) { return d.count; }), backgroundColor: '#198754' }]
                        },
                        options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { callbacks: { label: function (ctx) { return ' 次数: ' + ctx.raw.toLocaleString(); } } } }, scales: { x: { ticks: { font: { size: 10 } }, title: { display: true, text: '礼物名', font: { size: 10 } } }, y: { ticks: { font: { size: 10 } }, title: { display: true, text: '总次数', font: { size: 10 } } } } }
                    });
                }
                }, 50);
            }
        });
    },

    exportGftCsv: function () {
        if (!gftState.currentFile) return;
        var url = '../exportGiftFilteredCsv?filePath=' + encodeURIComponent(gftState.currentFile);
        if (gftState.startTime) url += '&startTime=' + encodeURIComponent(gftState.startTime);
        if (gftState.endTime) url += '&endTime=' + encodeURIComponent(gftState.endTime);
        if (gftState.search) url += '&search=' + encodeURIComponent(gftState.search);
        window.open(url, '_blank');
    },

    initGftColumnDrag: function () {
        var thead = document.getElementById('gft-table-head');
        if (!thead || thead.sortableInstance) return;
        thead.sortableInstance = new Sortable(thead, {
            animation: 150, ghostClass: 'sortable-ghost', chosenClass: 'sortable-chosen',
            onEnd: function () {
                var order = [];
                $('#gft-table-head th').each(function () {
                    var txt = $(this).text().replace(/ [▲▼]/, '');
                    var idx = gftState.headers.indexOf(txt);
                    if (idx >= 0) order.push(idx);
                });
                if (order.length === gftState.headers.length) { gftState.columnOrder = order; method.loadGftData(); }
            }
        });
    },

    // ========== 陌生观众看板 ==========
    _svInitialized: false,
    _initSv: function () {
        if (method._svInitialized) return;
        method._svInitialized = true;

        // Sort header click
        $('#sv-data-table').on('click', 'th.sv-sort', function () {
            var col = $(this).attr('data-sort');
            if (!col) return;
            if (svState.sortField === col) {
                svState.sortOrder = svState.sortOrder === 'asc' ? 'desc' : 'asc';
            } else {
                svState.sortField = col;
                svState.sortOrder = 'asc';
            }
            svState.defaultToLast = false;
            svState.page = 1;
            method.loadSvData();
        });

        // File selector
        $('#sv-csv-select').on('change', function () {
            svState.currentFile = $(this).val();
            svState.startTime = ''; svState.endTime = ''; svState.search = '';
            $('#sv-filter-hours').val('3');
            $('#sv-filter-start').val(''); $('#sv-filter-end').val('');
            $('#sv-search-input').val('');
            svState.page = 1;
            svState.defaultToLast = true;
            if (svState.currentFile) method.loadSvData();
        });

        // 日期/小时 互斥
        $('#sv-filter-start, #sv-filter-end').on('change', function() { if ($(this).val()) $('#sv-filter-hours').val(''); });
        $('#sv-filter-hours').on('change input', function() { if ($(this).val()) { $('#sv-filter-start').val(''); $('#sv-filter-end').val(''); } });

        // Apply: time filter + search
        $('#sv-btn-apply').on('click', function () {
            var start = $('#sv-filter-start').val() || '';
            var end = $('#sv-filter-end').val() || '';
            if (start || end) { svState.startTime = start ? start + ' 00:00:01' : ''; svState.endTime = end ? end + ' 23:59:59' : ''; }
            else { applyHoursFilter(svState, '#sv'); }
            svState.search = $('#sv-search-input').val().trim();
            svState.page = 1;
            svState.defaultToLast = false;
            method.loadSvData();
        });
        $('#sv-btn-reset').on('click', function () {
            $('#sv-filter-hours').val('3');
            $('#sv-filter-start').val('');
            $('#sv-filter-end').val('');
            $('#sv-search-input').val('');
            svState.startTime = '';
            svState.endTime = '';
            svState.search = '';
            svState.page = 1;
            svState.defaultToLast = true;
            method.loadSvData();
        });
        $('#sv-search-input').on('keypress', function (e) {
            if (e.which === 13) $('#sv-btn-apply').click();
        });

        // Export CSV
        $('#sv-btn-export').on('click', function () {
            var url = '/strangerViewerExport?';
            if (svState.startTime) url += 'startTime=' + encodeURIComponent(svState.startTime) + '&';
            if (svState.endTime) url += 'endTime=' + encodeURIComponent(svState.endTime) + '&';
            if (svState.search) url += 'search=' + encodeURIComponent(svState.search);
            window.open(url, '_blank');
        });

        // Import CSV
        $('#sv-import-file').on('change', function () {
            var file = this.files[0];
            if (!file) return;
            var fd = new FormData();
            fd.append('file', file);
            $.ajax({ url: '/strangerViewerImport', type: 'POST', data: fd, processData: false, contentType: false,
                success: function (resp) {
                    if (resp && resp.code == "200") {
                        alert('导入完成：' + (resp.result || 0) + ' 条记录');
                        svState.page = 1;
                        method.loadSvData();
                    }
                }
            });
            $(this).val('');
        });

        // Pagination
        $('#sv-first-btn').on('click', function () { svState.defaultToLast = false; method._svGoPage(1); });
        $('#sv-prev-btn').on('click', function () { svState.defaultToLast = false; method._svGoPage(svState.page - 1); });
        $('#sv-next-btn').on('click', function () { svState.defaultToLast = false; method._svGoPage(svState.page + 1); });
        $('#sv-last-btn').on('click', function () { svState.defaultToLast = true; method._svGoPage(svState.totalPages); });
        $('#sv-btn-go').on('click', function () {
            var p = parseInt($('#sv-page-jump').val());
            if (p >= 1 && p <= svState.totalPages) { svState.defaultToLast = false; method._svGoPage(p); }
        });
    },

    loadSvFileList: function () {
        $.get('/listStrangerCsvFiles', function (resp) {
            if (resp && resp.code == "200" && resp.result) {
                var $sel = $('#sv-csv-select');
                $sel.find('option[value!=""]').remove();
                var files = resp.result;
                var firstOpt = null;
                for (var i = 0; i < files.length; i++) {
                    var f = files[i];
                    var label = (f.anchorName || '?') + ' - ' + f.fileName;
                    var opt = $('<option>').val(f.filePath).text(label);
                    if (f.isCurrent === '1') { firstOpt = opt; opt.text(opt.text() + ' *'); }
                    $sel.append(opt);
                }
                if (firstOpt) {
                    $sel.val(firstOpt.val());
                    svState.currentFile = firstOpt.val();
                } else {
                    var fallback = $sel.find('option[value!=""]').first();
                    if (fallback.length) {
                        $sel.val(fallback.val());
                        svState.currentFile = fallback.val();
                    }
                }
                if (svState.currentFile) {
                    svState.defaultToLast = true;
                    method.loadSvData();
                }
            }
        });
    },

    loadSvData: function () {
        method._initSv();
        // 默认筛选：首次加载无时间参数时应用"几小时前"
        if (!svState.startTime && !svState.endTime) {
            var hours = parseInt($('#sv-filter-hours').val()) || 3;
            var now = new Date();
            var start = new Date(now.getTime() - hours * 3600000);
            start.setMinutes(0, 0, 0);
            var end = new Date(now.getTime() + 3600000);
            end.setMinutes(0, 0, 0);
            var pad = function(n) { return ('0' + n).slice(-2); };
            var fmt = function(d) {
                return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate())
                    + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes());
            };
            svState.startTime = fmt(start);
            svState.endTime = fmt(end);
        }
        var params = { page: svState.page, pageSize: svState.pageSize, sortField: svState.sortField, sortOrder: svState.sortOrder };
        if (svState.search) params.search = svState.search;
        if (svState.currentFile) params.filePath = svState.currentFile;
        if (svState.startTime) params.startTime = svState.startTime;
        if (svState.endTime) params.endTime = svState.endTime;
        $.get('/strangerViewerData', params, function (resp) {
            if (resp && resp.code == "200" && resp.result) {
                svState.records = resp.result.rows || [];
                svState.totalRecords = resp.result.total || 0;
                svState.totalPages = resp.result.totalPages || 0;
                if (svState.defaultToLast && svState.totalPages > 0 && svState.search === '' && svState.page !== svState.totalPages) {
                    svState.page = svState.totalPages;
                    method.loadSvData();
                    return;
                }
                method._renderSvTable();
            }
        });
    },

    _renderSvTable: function () {
        var $table = $('#sv-data-table');
        $table.css('visibility', 'hidden');
        var $tbody = $('#sv-table-body');
        $tbody.empty();
        var records = svState.records;

        if (!records || records.length === 0) {
            $table.hide();
            $('#sv-pagination').hide();
            $('#sv-empty-msg').show();
            $('#sv-total-info').text('');
            $table.css('visibility', '');
            return;
        }

        $table.show();
        $('#sv-pagination').show();
        $('#sv-empty-msg').hide();
        $('#sv-total-info').text('共 ' + svState.totalRecords + ' 条记录');

        // Sort indicator on headers
        var sf = svState.sortField || 'time';
        var asc = svState.sortOrder === 'asc';
        $('#sv-data-table th.sv-sort').each(function () {
            var col = $(this).attr('data-sort');
            $(this).text(function (i, txt) { return txt.replace(/ [▲▼]/, ''); });
            if (col === sf) $(this).append(asc ? ' ▲' : ' ▼');
        });

        for (let i = 0; i < svState.pageSize; i++) {
            var $tr = $('<tr></tr>');
            if (i < records.length) {
                let r = records[i];
                // 时间
                $tr.append($('<td class="sv-td truncate-expandable"></td>').text(r.time || ''));
                // 头像 - hover 显示原图，点击跳转主页
                var $avatarTd = $('<td class="sv-td truncate-expandable"></td>');
                var $avatar = $('<img src="' + (r.face || '') + '" style="width:32px;height:32px;border-radius:50%;cursor:pointer;" data-full-src="' + (r.face || '') + '">');
                $avatar.on('mouseenter', function () {
                    var fullSrc = $(this).attr('data-full-src');
                    if (!fullSrc) return;
                    var $ov = $('#sv-avatar-overlay');
                    if (!$ov.length) {
                        $ov = $('<div id="sv-avatar-overlay" style="display:none;position:fixed;top:60px;left:20px;z-index:9999;border:2px solid #fff;box-shadow:0 2px 12px rgba(0,0,0,0.4);background:#fff;padding:4px;"></div>');
                        $('body').append($ov);
                    }
                    $ov.empty().append('<img src="' + fullSrc + '" style="max-width:300px;max-height:300px;display:block;" onerror="this.style.display=\'none\'">').show();
                }).on('mouseleave', function () {
                    var $ov = $('#sv-avatar-overlay');
                    if ($ov.length) $ov.hide();
                }).on('click', function () {
                    window.open('https://space.bilibili.com/' + r.uid, '_blank');
                });
                $avatarTd.append($avatar);
                $tr.append($avatarTd);
                // 观众名
                $tr.append($('<td class="sv-td truncate-expandable"></td>').text(r.name || ''));
                // 签名
                $tr.append($('<td class="sv-td truncate-expandable"></td>').text(r.scoreTypes || '').attr('title', r.scoreTypes || ''));
                // 打分
                $tr.append($('<td class="sv-td truncate-expandable"></td>').text(r.score != null ? r.score : ''));
                // 次数
                $tr.append($('<td class="sv-td truncate-expandable"></td>').text(r.count || 0));
                // 场次
                $tr.append($('<td class="sv-td truncate-expandable"></td>').text(r.session || 0));
                // 拉黑
                var $blockTd = $('<td class="sv-td truncate-expandable"></td>');
                var isBlocked = r.blocked === true || r.blocked === 'true' || r.blocked === 1 || r.blocked === '1';
                var blockLabel = isBlocked ? '解除拉黑' : '拉黑';
                var $blockBtn = $('<button class="btn btn-sm ' + (isBlocked ? 'btn-warning' : 'btn-danger') + '"></button>')
                    .text(blockLabel)
                    .attr('data-uid', r.uid)
                    .attr('data-blocked', r.blocked ? '1' : '0')
                    .on('click', function () {
                    var uid = $(this).attr('data-uid');
                    method._svToggleBlock(uid);
                });
                $blockTd.append($blockBtn);
                $tr.append($blockTd);
            } else {
                $tr.append($('<td colspan="8" style="height:42px;">&nbsp;</td>'));
            }
            $tbody.append($tr);
        }

        // Pagination
        $('#sv-page-info').text('第' + svState.page + '页 / 共' + svState.totalPages + '页');
        $('#sv-first-btn, #sv-prev-btn').prop('disabled', svState.page <= 1);
        $('#sv-next-btn, #sv-last-btn').prop('disabled', svState.page >= svState.totalPages);
        $('#sv-page-jump').attr('max', svState.totalPages);
        $table.css('visibility', '');
    },

    _svGoPage: function (p) {
        if (p < 1 || p > svState.totalPages) return;
        svState.page = p;
        method.loadSvData();
    },

    _svToggleBlock: function (uid) {
        $.post('/strangerViewerBlock', { uid: uid }, function (resp) {
            if (resp && resp.code == "200") {
                // Update local records
                for (var i = 0; i < svState.records.length; i++) {
                    if (svState.records[i].uid === uid) {
                        svState.records[i].blocked = !svState.records[i].blocked;
                        break;
                    }
                }
                method._renderSvTable();
            }
        });
    },

    // WebSocket handler for stranger viewer updates
    _handleStrangerViewer: function (data) {
        if (!data || !data.uid) return;
        if ($('#tab-stranger-board').hasClass('active')) {
            method.loadSvData();
        }
    },

    _handleStrangerBlock: function (data) {
        if (!data || !data.uid) return;
        for (var i = 0; i < svState.records.length; i++) {
            if (svState.records[i].uid === data.uid) {
                svState.records[i].blocked = data.blocked;
                break;
            }
        }
        if ($('#tab-stranger-board').hasClass('active')) {
            method._renderSvTable();
        }
    },

    _handleBwlistUpdate: function (data) {
        if (!data || !data.type) return;
        if ($('#tab-bwlist-set').hasClass('active')) {
            method.renderBlackWhiteTable(data.type);
        }
    }


};

function openSocket(ip, sliceh) {
    if (typeof (WebSocket) == "undefined") {
        showMessage("您的浏览器不支持WebSocket，显示弹幕功能异常，请升级你的浏览器版本，推荐谷歌，网页显示弹幕失败 但不影响其他功能使用!", "warning",2);
    } else {
        console.log("弹幕服务器正在连接");
        let socketUrl = ip;
        if (socket != null) {
            socket.close();
            socket = null;
        }
        try {
            socket = new WebSocket(socketUrl);
        } catch (err) {
            console.log(err);
        }
        // 打开事件
        socket.onopen = function () {
            $("#danmu").append("<div class='danmu-child'>连接成功<div/>");
            console.log("连接已打开");
        };
        // 获得消息事件
        socket.onmessage = function (msg) {
            // console.log($("#danmu").scrollTop()+":"+$("div[class='danmu-child']:last").offset().top +":"+$("#danmu").height()+":"+$("#danmu")[0].scrollHeight);
            // 发现消息进入 开始处理前端触发逻辑
            let data = JSON.parse(msg.data);
            if (data.cmd === "data_update") {
                // 管理页面数据更新通知 — 触发对应板块刷新
                $(document).trigger('danmuji:dataUpdate', [data.result]);
                return;
            }
            if (data.cmd === "cmdp") {
                $("#danmu").append("<div class='danmu-child'>" + data.result + "</div>");
            } else {
                $("#danmu").append(danmuku.danmu(data.cmd, data.result));

            }
            var find_z = $("#danmu").find(".danmu-child-z").length;
            if (!find_z) {
                //置底代码
                var h = $("div[class='danmu-child']:last").height();
                var top = $("div[class='danmu-child']:last").position().top;
                var lh = $("#danmu").height() + h;
                if (lh >= top) {
                    $("#danmu").scrollTop($("#danmu").prop("scrollHeight"));
                }
            }
            if ($("#danmu").children().length > 300) {
                $("#danmu").children().first().remove();
            }
        };
        // 关闭事件
        socket.onclose = function () {
            $("#danmu").append("<div class='danmu-child'>连接已关闭，网页显示弹幕失败 但不影响其他功能使用<div/>");
            console.log("连接已关闭，网页显示弹幕失败 但不影响其他功能使用");
        };
        // 发生了错误事件
        socket.onerror = function () {
            $("#danmu").append("<div class='danmu-child'>连接到弹幕服务器发生了错误,请刷新网页并确认地址正确无误后再次连接尝试<div/>");
            console.log("连接到弹幕服务器发生了错误，网页显示弹幕失败 但不影响其他功能使用");
        }
    }

}

// 设置面板侧边栏导航切换（全局函数，onclick直接调用）
function switchTab(tabId, el) {
    if (!tabId) return;
    // 仅在 el 非空时操作侧边栏高亮（index.html 模式）
    // 分页模式下侧边栏由服务端 th:classappend 管理
    if (el) {
        $('.sidebar-link').removeClass('active');
        $(el).addClass('active');
    }
    // 切换内容面板
    var targetPane = $('#tab-' + tabId);
    // 如果目标标签页不存在（如旧版tab已合并），回退到第一个可见标签页
    if (!targetPane.length) {
        targetPane = $('.settings-content .tab-pane').first();
        if (!targetPane.length) return;
        tabId = targetPane.attr('id').replace('tab-', '');
    }
    $('.settings-content .tab-pane').removeClass('active').hide();
    targetPane.addClass('active').show();
    // 记住当前tab，页面刷新后恢复
    try { localStorage.setItem('activeTab', tabId); } catch(e) {}
    // 自动调整textarea高度
    targetPane.find('textarea.form-control').each(function () {
        $(this).css('height', this.scrollHeight + 'px');
    });
    // 切换到负黑自动拉黑姬时重新加载数据
    if (tabId === 'autoBlock-set') {
        method.loadAutoBlockList();
    }
    // 切换到直播间管理时加载CSV文件列表
    if (tabId === 'live-room-mgr') {
        if (!$('#lrm-csv-select option[value!=""]').length) {
            method.loadCsvFileList();
        }
    }
    // 切换到弹幕管理时加载CSV文件列表
    if (tabId === 'danmaku-mgr') {
        if (!$('#dmgr-csv-select option[value!=""]').length) {
            method.loadDmgrCsvFileList();
        }
    }
    // 切换到观众管理时加载CSV文件列表
    if (tabId === 'audience-mgr') {
        if (!$('#vst-csv-select option[value!=""]').length) {
            method.loadVstCsvFileList();
        }
    }
    // 切换到匹配管理时加载CSV文件列表
    if (tabId === 'match-mgr') {
        if (!$('#mtch-csv-select option[value!=""]').length) {
            method.loadMtchCsvFileList();
        }
    }
    // 切换到关注人管理时加载CSV文件列表
    if (tabId === 'follow-mgr') {
        if (!$('#flw-csv-select option[value!=""]').length) {
            method.loadFlwCsvFileList();
        }
    }
    // 切换到礼物管理时加载CSV文件列表
    if (tabId === 'gift-mgr') {
        if (!$('#gft-csv-select option[value!=""]').length) {
            method.loadGftCsvFileList();
        }
    }
    // 切换到陌生观众看板时加载CSV文件列表
    if (tabId === 'stranger-board') {
        svState.defaultToLast = true;
        if (!$('#sv-csv-select option[value!=""]').length) {
            method.loadSvFileList();
        } else {
            method._initSv();
            method.loadSvData();
        }
    }
    // 管理页面自动刷新（每60秒）
    if (window._mgrRefreshTimer) { clearInterval(window._mgrRefreshTimer); window._mgrRefreshTimer = null; }
    var mgrLoadFn = null;
    if (tabId === 'live-room-mgr') mgrLoadFn = method.loadCsvData;
    else if (tabId === 'danmaku-mgr') mgrLoadFn = method.loadDmgrData;
    else if (tabId === 'audience-mgr') mgrLoadFn = method.loadVstData;
    else if (tabId === 'match-mgr') mgrLoadFn = method.loadMtchData;
    else if (tabId === 'follow-mgr') mgrLoadFn = method.loadFlwData;
    else if (tabId === 'gift-mgr') mgrLoadFn = method.loadGftData;
    else if (tabId === 'stranger-board') mgrLoadFn = method.loadSvData;
    // 保存当前管理页面的刷新函数引用，供 WebSocket 推送触发
    window._activeMgrLoadFn = mgrLoadFn;
    if (mgrLoadFn) {
        window._mgrRefreshTimer = setInterval(function () {
            // 页面不可见时跳过自动刷新，降低消耗
            if (document.hidden) return;
            if (typeof mgrLoadFn === 'function') mgrLoadFn();
        }, 60000);
    }
    // WebSocket 数据更新 → 触发当前管理页面刷新（节流 3 秒内最多一次）
    $(document).off('danmuji:refreshMgr').on('danmuji:refreshMgr', function() {
        if (document.hidden) return;
        var now = Date.now();
        if (window._lastMgrRefresh && now - window._lastMgrRefresh < 3000) return;
        window._lastMgrRefresh = now;
        if (typeof window._activeMgrLoadFn === 'function') window._activeMgrLoadFn();
    });
    // 侧边栏滚动到当前激活项
    if (el) {
        var $container = $(el).closest('.settings-sidebar');
        if ($container.length) {
            var containerTop = $container.offset().top;
            var itemTop = $(el).offset().top;
            var scrollTarget = $container.scrollTop() + (itemTop - containerTop) - $container.height() / 2 + $(el).height() / 2;
            $container.animate({ scrollTop: scrollTarget }, 200);
        }
    }
}

// 侧边栏分组折叠/展开
function toggleSidebarSection(el) {
    $(el).closest('.sidebar-section').toggleClass('collapsed');
}

function sendMessage() {
    if (typeof (WebSocket) == "undefined") {
        console.log("您的浏览器不支持WebSocket，网页显示弹幕失败 但不影响其他功能使用");
    } else {
        console.log("您的浏览器支持WebSocket");
        // socket.send('{"toUserId":"' + $("#toUserId").val()
        //     + '","contentText":"' + $("#contentText").val() + '"}');
        if (socket != null){
            let contentText = $("#contentText")
            console.log("发送弹幕中...,内容: "+contentText.val())
            let code = socket.send(contentText.val())
            contentText.val('')

            if(code === 0){
                console.log("消息发送成功")
            } else{
                console.log("消息发送失败")
            }
        }
    }
}

function add0(m) {
    return m < 10 ? '0' + m : m
}

function addSpace(m) {
    return m < 10 ? ' ' + m : m
}

function format(timestamp, flag) {
    let time = new Date(parseInt(timestamp));
    let y = time.getFullYear();
    let m = time.getMonth() + 1;
    let d = time.getDate();
    let h = time.getHours();
    let mm = time.getMinutes();
    let s = time.getSeconds();
    if (flag) {
        return y + '-' + add0(m) + '-' + add0(d) + ' ' + add0(h) + ':' + add0(mm) + ':' + add0(s);
    } else {
        return add0(h) + ':' + add0(mm) + ':' + add0(s);
    }
}

function getTimestamp() {
    return (new Date()).getTime();
}

function showMessage(message, type,timeout) {
    var id = 'message-' + Date.now(); // 使用当前时间戳创建一个独特的ID
    var countdownId = 'countdown-' + Date.now();

    var div = $('<div id="'+ id +'" class="alert alert-'+ type +'" style="position:relative;">'+ message +
        '<span id="'+ countdownId +'" style="position:absolute; right:10px; top:50%; transform: translateY(-50%);"></span> </div>');// 创建一个新的div元素

    $('#top-message').append(div); // 将新消息添加到容器中

    var countdown = timeout; // 倒计时开始

    var intervalId = setInterval(function() {
        $('#' + countdownId).text(countdown + 's');
        if (--countdown < 0) {
            clearInterval(intervalId); // 在倒计时结束时清除计时器
            $('#' + id).fadeOut().remove(); // 在倒计时结束时移除这条消息
        }
    }, 1000);
}
// ========== 页面拆分：页面感知函数 ==========

// 当前页面ID，由各页面JS文件设置
window.currentPageId = 'settings';

// 各页面注册的保存字段函数映射
window._pageSaveFields = {};

// 注册页面保存函数
function registerPageSave(pageId, fn) {
    window._pageSaveFields[pageId] = fn;
}

// 页面感知的保存：先加载完整配置 → 页面函数只修改本页字段 → 合并保存
method.saveCurrentPage = function(pageId, silent) {
    var that = this;
    pageId = pageId || window.currentPageId;
    // 1. 从服务器加载完整配置
    $.ajax({
        url: '../getSet',
        async: false,
        cache: false,
        type: 'GET',
        dataType: 'json',
        success: function (data) {
            // data 是 Response 包装: {code, msg, result(即CenterSetConf), timestamp}
            if (data && data.result) {
                // 更新publicData.set（publicData是const，不能整体赋值）
                publicData.set = data.result;
                var set = data.result;  // CenterSetConf 在 result 字段里
                // 2. 调用页面特定的字段读取函数，只修改本页字段
                var saveFn = window._pageSaveFields[pageId];
                if (saveFn) {
                    saveFn(set);
                }
                // 3. 静默模式下跳过本地UI刷新（不清空用户正在编辑的内容）
                if (!silent) {
                    that.initSet(set);
                }
                // 4. 发送完整配置到服务器
                var edition = $("#app-version").attr("data-version");
                set.edition = edition;
                var result = that.sendSet(set);
                if (result == 1) {
                    if (pageId === 'danmaku') {
                        try { that.saveDanmakuStoreList(true); } catch(e) {}
                    }
                    if (!silent) {
                        showMessage("保存配置成功!", "success", 3);
                    }
                } else if (result == 2) {
                    location.reload();
                } else {
                    if (!silent) {
                        showMessage("修改配置失败!", "danger", 3);
                    }
                }
            }
        },
        error: function() {
            if (!silent) {
                showMessage("加载配置失败，请刷新页面重试", "danger", 5);
            }
        }
    });
};

// 从URL参数恢复子标签页
function initPageTabs() {
    var params = new URLSearchParams(window.location.search);
    var tabParam = params.get('tab');
    if (tabParam) {
        var $link = $('.sidebar-link[data-tab="' + tabParam + '"]');
        var $pane = $('#tab-' + tabParam);
        if ($pane.length) {
            $('.settings-content .tab-pane').removeClass('active').hide();
            $pane.addClass('active').show();
            // 仅在 index.html 模式（侧边栏链接有 data-tab 属性）下操作侧边栏高亮
            // 分页模式（layout.html）下服务端已通过 th:classappend 设置正确的 activePage
            var $sidebarTabs = $('.sidebar-link[data-tab]');
            if ($sidebarTabs.length > 0) {
                $('.sidebar-link').removeClass('active');
                if ($link.length) $link.addClass('active');
            }
            if (typeof switchTab === 'function') {
                switchTab(tabParam, $link[0] || null);
            }
        }
    }
    // 侧边栏立即滚动到激活项（无延迟、无动画）
    var $active = $('.sidebar-link.active');
    if ($active.length) {
        var container = $active.closest('.settings-sidebar');
        if (container.length) {
            var containerTop = container.offset().top;
            var itemTop = $active.offset().top;
            container.scrollTop(container.scrollTop() + (itemTop - containerTop) - container.height() / 2 + $active.height() / 2);
        }
    }
}

// 覆盖switchTab以更新URL参数
var _originalSwitchTab = switchTab;
switchTab = function(tabId, el) {
    try {
        var url = new URL(window.location);
        url.searchParams.set('tab', tabId);
        window.history.replaceState({}, '', url);
    } catch(e) {}
    return _originalSwitchTab(tabId, el);
};

// 页面加载完成后初始化
$(function() {
    // 浮动保存按钮点击事件
    $(document).off('click', '#floating-button.page-save');
    $(document).on('click', '#floating-button', function() {
        var pageId = $(this).attr('data-page') || window.currentPageId || 'settings';
        method.saveCurrentPage(pageId);
    });
    // 侧边栏立即滚动到激活项（无延迟、无动画，避免先闪顶部再下滑）
    (function scrollSidebarToActive() {
        var $active = $('.sidebar-link.active');
        if ($active.length) {
            var container = $active.closest('.settings-sidebar');
            if (container.length) {
                var containerTop = container.offset().top;
                var itemTop = $active.offset().top;
                var scrollTarget = container.scrollTop() + (itemTop - containerTop) - container.height() / 2 + $active.height() / 2;
                container.scrollTop(scrollTarget);
            }
        }
    })();
});

// ========== 覆盖saveSet为页面感知的安全保存（防止跨页字段丢失） ==========
var _originalSaveSet = method.saveSet;
method.saveSet = function(silent) {
    var pageId = window.currentPageId || 'settings';
    // 如果当前页面注册了saveFields，走安全路径
    if (window._pageSaveFields[pageId]) {
        return method.saveCurrentPage(pageId, silent);
    }
    // 没有注册的页面（如原index.html），走原始保存逻辑
    return _originalSaveSet.call(this, silent);
};

// ========== 关注直播间列表 ==========
var watchedRoomsData = [];
var WATCHED_REFRESH_COOLDOWN = 3 * 60 * 1000; // 3分钟冷却时间

// 加载关注直播间列表（优先从localStorage读取，后台静默更新）
method.loadWatchedRooms = function() {
    // 先从本地缓存读取，立即渲染
    try {
        var cached = localStorage.getItem('watchedRoomsData');
        if (cached) {
            watchedRoomsData = JSON.parse(cached);
            method.renderWatchedRoomTable();
        }
    } catch(e) {}
    // 再从服务器拉取最新数据更新
    $.ajax({
        url: '../getWatchedRooms',
        async: true,
        cache: false,
        type: 'GET',
        dataType: 'json',
        success: function(data) {
            if (data && data.result) {
                var fresh = JSON.stringify(data.result);
                var old = localStorage.getItem('watchedRoomsData');
                // 数据有变化才更新DOM和缓存
                if (fresh !== old) {
                    watchedRoomsData = data.result;
                    localStorage.setItem('watchedRoomsData', fresh);
                    method.renderWatchedRoomTable();
                }
                // 自动刷新房间状态（3分钟冷却），应用启动和进入设置页面时触发
                method.refreshWatchedRooms();
            }
        },
        error: function() {
            // 静默失败，使用缓存数据
        }
    });
};

// 关注列表排序状态
var watchedSort = { field: null, asc: true };
var watchedPage = 0;
var watchedPageSize = 10;

// 渲染关注直播间表格
method.renderWatchedRoomTable = function() {
    var $table = $('#watched-rooms-table');
    var $tbody = $('#watched-rooms-tbody');
    var $empty = $('#watched-rooms-empty');
    var $pagination = $('#watched-rooms-pagination');
    var $pageInfo = $('#watched-page-info');
    $tbody.empty();

    // 获取当前连接的房间ID
    var currentRoomId = null;
    var $section = $('.watched-rooms-section');
    if ($section.length) {
        var attrVal = $section.attr('data-current-roomid');
        if (attrVal && attrVal !== '' && attrVal !== 'null') currentRoomId = parseInt(attrVal);
    }

    // 合并当前连接房间到列表（如果不在列表中则自动添加）
    var data = watchedRoomsData.slice();
    if (currentRoomId && !data.some(function(r) { return r.roomId == currentRoomId; })) {
        // 当前房间不在关注列表中，从页面元素读取信息
        var currentRoom = {
            anchorUid: parseInt($('.ar-name').attr('href') ? ($('.ar-name').attr('href').split('/').pop() || 0) : 0),
            anchorName: $('.ar-name').text().trim() || '-',
            roomId: currentRoomId,
            roomName: $('.status-bar a.window-open-x').first().text().trim() || '-',
            areaName: '',
            parentAreaName: '',
            liveStatus: $('.status-live').text().indexOf('直播中') >= 0 ? 1 : 0,
            _isCurrent: true
        };
        data.unshift(currentRoom);
    }

    if (!data || data.length === 0) {
        $table.hide();
        $pagination.hide();
        $empty.show();
        return;
    }
    // 排序（当前连接的房间始终在最前面）
    if (watchedSort.field) {
        data.sort(function(a, b) {
            if (a._isCurrent) return -1;
            if (b._isCurrent) return 1;
            var va = a[watchedSort.field] || '', vb = b[watchedSort.field] || '';
            if (typeof va === 'number') return watchedSort.asc ? va - vb : vb - va;
            return watchedSort.asc ? String(va).localeCompare(String(vb)) : String(vb).localeCompare(String(va));
        });
    }

    // 分页
    var totalPages = Math.max(1, Math.ceil(data.length / watchedPageSize));
    if (watchedPage >= totalPages) watchedPage = totalPages - 1;
    if (watchedPage < 0) watchedPage = 0;
    var start = watchedPage * watchedPageSize;
    var pageData = data.slice(start, start + watchedPageSize);

    $table.show();
    $empty.hide();
    // 更新表头排序箭头
    $('#watched-rooms-table th[data-sort]').each(function() {
        var f = $(this).attr('data-sort');
        $(this).find('.sort-arrow').html(f === watchedSort.field ? (watchedSort.asc ? ' ▲' : ' ▼') : '');
    });
    pageData.forEach(function(room) {
        var isCurrent = room._isCurrent || (currentRoomId && room.roomId == currentRoomId);
        var statusHtml = (room.liveStatus == 1)
            ? '<span style="color:#4eff4e;">●直播中</span>'
            : '<span style="color:#ff6b6b;">●未开播</span>';
        // 分区链接
        var areaHtml = '-';
        if (room.areaName) {
            var areaUrl = 'https://live.bilibili.com/p/eden/area-tags?parentAreaId=' + (room.parentAreaId || 0) + '&area_id=' + (room.areaId || 0);
            areaHtml = '<a href="' + areaUrl + '" target="_blank" style="text-decoration:none;" title="查看分区">' + (room.parentAreaName && room.parentAreaName !== room.areaName ? room.parentAreaName + '·' : '') + room.areaName + '</a>';
        }
        // 主播名链接
        var anchorHtml = room.anchorUid
            ? '<a href="https://space.bilibili.com/' + room.anchorUid + '" target="_blank" style="text-decoration:none;" title="查看主播空间">' + (room.anchorName || '-') + '</a>'
            : (room.anchorName || '-');
        // 房间名链接
        var roomNameHtml = room.roomId
            ? '<a href="https://live.bilibili.com/' + room.roomId + '" target="_blank" style="text-decoration:none;" title="进入直播间">' + (room.roomName || '-') + '</a>'
            : (room.roomName || '-');
        // 连接/断开按钮
        var actionHtml;
        if (isCurrent) {
            actionHtml = '<button class="btn btn-sm btn-outline-warning watched-disconnect-btn">断开</button>';
        } else {
            actionHtml = '<button class="btn btn-sm btn-outline-primary watched-connect-btn" data-roomid="' + room.roomId + '">连接</button>';
        }
        var onlineHtml = (room.online > 0) ? room.online : '<span style="color:#ccc;">-</span>';
        var row = '<tr' + (isCurrent ? ' class="table-active"' : '') + '>' +
            '<td>' + anchorHtml + '</td>' +
            '<td class="truncate-expandable" style="max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">' + roomNameHtml + '</td>' +
            '<td>' + areaHtml + '</td>' +
            '<td style="width:80px;text-align:right;">' + onlineHtml + '</td>' +
            '<td style="width:80px;text-align:right;">' + statusHtml + '</td>' +
            '<td style="width:80px;text-align:right;">' + actionHtml + '</td>' +
            '<td style="width:80px;text-align:right;">' + (!isCurrent ? '<button class="btn btn-sm btn-outline-danger watched-delete-btn" data-roomid="' + room.roomId + '">删除</button>' : '') + '</td>' +
            '</tr>';
        $tbody.append(row);
    });

    // 渲染分页控件
    if (totalPages > 1) {
        $pagination.show();
        $pageInfo.text('第 ' + (watchedPage + 1) + ' / ' + totalPages + ' 页（共 ' + data.length + ' 个）');
        $('#watched-prev-btn').prop('disabled', watchedPage <= 0);
        $('#watched-next-btn').prop('disabled', watchedPage >= totalPages - 1);
    } else {
        $pagination.hide();
    }
};

// 刷新关注直播间（顺序获取未连接房间的状态和在线人数）
// force: 强制刷新，跳过3分钟冷却（手动点击刷新按钮时使用）
method.refreshWatchedRooms = function(force) {
    var $btn = $('#watched-refresh-btn');
    if (!$btn.length || $btn.hasClass('disabled')) return;

    // 3分钟冷却检查（手动强制刷新可跳过）
    var now = Date.now();
    var lastRefresh = 0;
    try {
        lastRefresh = parseInt(localStorage.getItem('watchedRoomsLastRefresh') || '0');
    } catch(e) {}
    if (!force && (now - lastRefresh) < WATCHED_REFRESH_COOLDOWN) {
        return;
    }
    localStorage.setItem('watchedRoomsLastRefresh', String(now));

    $btn.addClass('disabled').text('刷新中...');

    // 当前连接房间：直接从状态栏读取在线人数
    var currentRoomId = null;
    var $section = $('.watched-rooms-section');
    if ($section.length) {
        var attrVal = $section.attr('data-current-roomid');
        if (attrVal && attrVal !== '' && attrVal !== 'null') currentRoomId = parseInt(attrVal);
    }
    if (currentRoomId) {
        var currentOnline = parseInt($('.room-online').text()) || 0;
        watchedRoomsData.forEach(function(r) {
            if (r.roomId == currentRoomId) {
                r.online = currentOnline;
                r.liveStatus = $('.status-live').text().indexOf('直播中') >= 0 ? 1 : 0;
            }
        });
    }

    // 筛选未连接的房间
    var toRefresh = watchedRoomsData.filter(function(r) { return r.roomId != currentRoomId; });
    if (toRefresh.length === 0) {
        method.renderWatchedRoomTable();
        $btn.removeClass('disabled').text('刷新');
        return;
    }

    var idx = 0;
    function fetchNext() {
        if (idx >= toRefresh.length) {
            method.saveWatchedRoomsData();
            method.renderWatchedRoomTable();
            $btn.removeClass('disabled').text('刷新');
            return;
        }
        var room = toRefresh[idx];
        var attempts = 0, maxAttempts = 4;
        var firstLiveStatus = null; // 保留第一次获取的liveStatus，避免重试时被限流返回的0覆盖
        function tryFetch() {
            $.ajax({
                url: '../getRoomStatus',
                async: true, cache: false, type: 'GET',
                data: { roomid: room.roomId },
                dataType: 'json',
                success: function(resp) {
                    if (resp && resp.code == '200' && resp.result) {
                        var liveStatus = resp.result.liveStatus || 0;
                        var online = resp.result.online || 0;
                        // 记录第一次获取的liveStatus（room_init API的live_status更可靠）
                        if (attempts === 0) {
                            firstLiveStatus = liveStatus;
                        }
                        // online>0 说明API正常返回；达到最大重试次数则接受当前结果
                        if (online > 0 || attempts >= maxAttempts - 1) {
                            // 优先使用第一次获取的liveStatus（避免重试时被限流返回的0覆盖）
                            room.liveStatus = (firstLiveStatus !== null) ? firstLiveStatus : liveStatus;
                            room.online = online;
                            idx++; setTimeout(fetchNext, 200);
                        } else {
                            attempts++; setTimeout(tryFetch, 600);
                        }
                    } else {
                        // 非200响应：保留第一次的liveStatus，online置0
                        if (firstLiveStatus !== null) {
                            room.liveStatus = firstLiveStatus;
                        }
                        room.online = 0;
                        idx++; setTimeout(fetchNext, 200);
                    }
                },
                error: function() {
                    // 网络错误：保留第一次的liveStatus
                    if (firstLiveStatus !== null) {
                        room.liveStatus = firstLiveStatus;
                    }
                    idx++; setTimeout(fetchNext, 200);
                }
            });
        }
        tryFetch();
    }
    fetchNext();
};

// 点击表头排序
method.sortWatchedRooms = function(field) {
    if (watchedSort.field === field) {
        watchedSort.asc = !watchedSort.asc;
    } else {
        watchedSort.field = field;
        watchedSort.asc = true;
    }
    watchedPage = 0;
    method.renderWatchedRoomTable();
};

// 添加关注直播间
method.addWatchedRoom = function() {
    var roomid = $('#watched-room-input').val();
    if (!roomid || roomid === '') {
        showMessage('请输入房间号', 'warning', 3);
        return;
    }
    // 检查是否已存在
    var exists = watchedRoomsData.some(function(r) { return r.roomId == roomid; });
    if (exists) {
        showMessage('该直播间已在列表中', 'warning', 3);
        return;
    }
    var $msg = $('#watched-room-add-msg');
    $msg.html('<span style="color:#17a2b8;">获取房间信息中...</span>');
    $('#watched-room-add-btn').prop('disabled', true);
    var that = this;
    $.ajax({
        url: '../getRoomInfo',
        async: true,
        cache: false,
        type: 'GET',
        data: { roomid: roomid },
        dataType: 'json',
        success: function(data) {
            $('#watched-room-add-btn').prop('disabled', false);
            if (data && data.code == '200' && data.result) {
                var info = data.result;
                watchedRoomsData.push({
                    anchorUid: info.anchorUid || 0,
                    anchorName: info.anchorName || '未知',
                    roomId: info.roomId || parseInt(roomid),
                    roomName: info.roomName || ('房间' + roomid),
                    areaName: info.areaName || '',
                    parentAreaName: info.parentAreaName || '',
                    areaId: info.areaId || 0,
                    parentAreaId: info.parentAreaId || 0,
                    liveStatus: info.liveStatus || 0,
                    online: 0
                });
                method.saveWatchedRoomsData();
                watchedPage = 0;
                method.renderWatchedRoomTable();
                $('#watched-room-input').val('');
                $msg.html('<span style="color:green;">添加成功</span>');
                setTimeout(function() { $msg.html(''); }, 2000);
            } else {
                $msg.html('<span style="color:red;">获取失败</span>');
                setTimeout(function() { $msg.html(''); }, 3000);
            }
        },
        error: function() {
            $('#watched-room-add-btn').prop('disabled', false);
            $msg.html('<span style="color:red;">请求失败</span>');
            setTimeout(function() { $msg.html(''); }, 3000);
        }
    });
};

// 保存关注直播间数据到服务器 + 同步localStorage
method.saveWatchedRoomsData = function() {
    // 去除临时字段 _isCurrent
    var clean = watchedRoomsData.map(function(r) {
        var o = {};
        Object.keys(r).forEach(function(k) { if (k !== '_isCurrent') o[k] = r[k]; });
        return o;
    });
    var json = JSON.stringify(clean);
    try { localStorage.setItem('watchedRoomsData', json); } catch(e) {}
    $.ajax({
        url: '../saveWatchedRooms',
        async: true,
        cache: false,
        type: 'POST',
        data: { data: json },
        dataType: 'json',
        error: function() {
            showMessage('保存关注列表失败', 'danger', 3);
        }
    });
};

// 删除关注直播间
method.deleteWatchedRoom = function(roomId) {
    watchedRoomsData = watchedRoomsData.filter(function(r) { return r.roomId != roomId; });
    method.saveWatchedRoomsData();
    watchedPage = 0;
    method.renderWatchedRoomTable();
};

// 连接到关注直播间（先断开当前，再连接）
method.connectToWatchedRoom = function(roomId) {
    // 先断开当前连接，再连接新房间
    $.ajax({
        url: '../disconnectRoom',
        async: true,
        cache: false,
        type: 'GET',
        dataType: 'json',
        success: function() {
            $.ajax({
                url: '../connectRoom',
                async: true,
                cache: false,
                type: 'GET',
                data: { roomid: roomId },
                dataType: 'json',
                success: function(data) {
                    if (data && data.code == '200' && data.result) {
                        showMessage('连接成功，页面即将刷新', 'success', 2);
                        setTimeout(function() { window.location.reload(); }, 1000);
                    } else {
                        showMessage('连接失败，请重试', 'danger', 3);
                    }
                },
                error: function() {
                    showMessage('连接请求失败', 'danger', 3);
                }
            });
        },
        error: function() {
            showMessage('断开连接失败', 'danger', 3);
        }
    });
};

// 绑定关注直播间按钮事件
$(function() {
    // 添加按钮
    $(document).on('click', '#watched-room-add-btn', function() {
        method.addWatchedRoom();
    });
    // 刷新按钮（手动点击强制刷新，跳过冷却）
    $(document).on('click', '#watched-refresh-btn', function() {
        method.refreshWatchedRooms(true);
    });
    // 输入框回车添加
    $(document).on('keypress', '#watched-room-input', function(e) {
        if (e.which === 13) method.addWatchedRoom();
    });
    // 连接按钮
    $(document).on('click', '.watched-connect-btn', function() {
        var roomId = $(this).attr('data-roomid');
        method.connectToWatchedRoom(roomId);
    });
    // 删除按钮
    $(document).on('click', '.watched-delete-btn', function() {
        var roomId = $(this).attr('data-roomid');
        method.deleteWatchedRoom(roomId);
    });
    // 表头排序
    $(document).on('click', '#watched-rooms-table th[data-sort]', function() {
        var field = $(this).attr('data-sort');
        method.sortWatchedRooms(field);
    });
    // 断开当前连接
    $(document).on('click', '.watched-disconnect-btn', function() {
        $.ajax({
            url: '../disconnectRoom',
            async: true,
            cache: false,
            type: 'GET',
            dataType: 'json',
            success: function() {
                showMessage('已断开连接，页面即将刷新', 'success', 2);
                setTimeout(function() { window.location.reload(); }, 1000);
            }
        });
    });
    // 分页-上一页
    $(document).on('click', '#watched-prev-btn', function() {
        if (watchedPage > 0) {
            watchedPage--;
            method.renderWatchedRoomTable();
        }
    });
    // 分页-下一页
    $(document).on('click', '#watched-next-btn', function() {
        var totalPages = Math.max(1, Math.ceil(watchedRoomsData.length / watchedPageSize));
        if (watchedPage < totalPages - 1) {
            watchedPage++;
            method.renderWatchedRoomTable();
        }
    });
});

// 页面初始化时加载关注直播间列表
$(function() {
    setTimeout(function() {
        method.loadWatchedRooms();
    }, 300);
});

// ====== 管理页面实时数据刷新辅助 ======
// 页面可见性 API：隐藏时暂停轮询，节省资源
(function() {
    var pageVisible = true;
    var visibilityCallbacks = [];

    document.addEventListener('visibilitychange', function() {
        pageVisible = !document.hidden;
        if (pageVisible) {
            // 页面恢复可见时，通知所有注册的回调立即刷新
            visibilityCallbacks.forEach(function(cb) { try { cb(true); } catch(e) {} });
        }
    });

    /** 注册页面可见性回调：cb(isNowVisible) — 页面变为可见时调用 */
    window.onPageVisible = function(cb) {
        visibilityCallbacks.push(cb);
    };

    /** 当前页面是否可见 */
    window.isPageVisible = function() {
        return !document.hidden;
    };
})();

// 管理页面数据更新辅助：WebSocket 推送 → 自动刷新对应板块
(function() {
    // 节流映射：每个板块在 N 毫秒内最多刷新一次
    var throttles = {};

    $(document).on('danmuji:dataUpdate', function(e, type) {
        // 页面不可见时跳过刷新
        if (document.hidden) return;
        // 节流：每个类型 2 秒内最多触发一次
        var now = Date.now();
        if (throttles[type] && now - throttles[type] < 2000) return;
        throttles[type] = now;
        // 通知当前活动管理页面刷新
        $(document).trigger('danmuji:refreshMgr');
        // 同时触发对应类型的自定义事件，供各管理页面监听
        $(document).trigger('danmuji:refresh_' + type);
    });

    // ---- 关键词检测姬 ----
    var kwData = { list: [], page: 1, pageSize: 10, sortCol: null, sortAsc: true };
    var kwSaveTimer = null;

    method._kwSortList = function() {
        if (!kwData.sortCol) return;
        var col = kwData.sortCol, asc = kwData.sortAsc;
        kwData.list.sort(function(a, b) {
            var va = (a[col] != null ? a[col] : ''), vb = (b[col] != null ? b[col] : '');
            if (col === 'score') {
                va = parseInt(va) || 0; vb = parseInt(vb) || 0;
                return asc ? va - vb : vb - va;
            }
            va = String(va).toLowerCase(); vb = String(vb).toLowerCase();
            if (va < vb) return asc ? -1 : 1;
            if (va > vb) return asc ? 1 : -1;
            return 0;
        });
    };
    method.kwRenderTable = function() {
        method._kwSortList();
        var tbody = $(".kw-tbody");
        tbody.empty();
        var total = kwData.list.length;
        var totalPages = Math.max(1, Math.ceil(total / kwData.pageSize));
        if (kwData.page > totalPages) kwData.page = totalPages;
        var start = (kwData.page - 1) * kwData.pageSize;
        var end = Math.min(start + kwData.pageSize, total);
        $(".kw-sort-icon").text('');
        if (kwData.sortCol) {
            $(".kw-sort-" + kwData.sortCol + " .kw-sort-icon").text(kwData.sortAsc ? '▲' : '▼');
        }
        for (var i = start; i < end; i++) {
            var item = kwData.list[i];
            var tr = $('<tr>');
            tr.append($('<td>').append($('<input class="form-control form-control-sm kw-keyword" type="text" style="width:100%">').val(item.keyword || '')));
            tr.append($('<td style="text-align:right">').append($('<input class="form-control form-control-sm kw-score" type="number" style="width:100%;text-align:right">').val(item.score || 0)));
            tr.append($('<td style="text-align:center">').append($('<button class="btn btn-sm btn-danger kw-delete-btn" style="width:100%">删除</button>')));
            tbody.append(tr);
        }
        $(".kw-page-info").text("第" + kwData.page + "页/共" + totalPages + "页 (共" + total + "条)");
        $(".kw-pagination").toggle(total > kwData.pageSize);
        $(".kw-prev").prop('disabled', kwData.page <= 1);
        $(".kw-next").prop('disabled', kwData.page >= totalPages);
    };
    method._kwSyncFromDOM = function() {
        $(".kw-tbody tr").each(function(i) {
            var idx = (kwData.page - 1) * kwData.pageSize + i;
            if (idx >= kwData.list.length) return;
            kwData.list[idx].keyword = ($(this).find(".kw-keyword").val() || '').trim();
            kwData.list[idx].score = parseInt($(this).find(".kw-score").val()) || 0;
        });
    };
    method.kwLoadFromSet = function() {
        if (publicData.set && publicData.set.key_word && publicData.set.key_word.keywords) {
            kwData.list = publicData.set.key_word.keywords.map(function(e) {
                return { keyword: e.keyword || '', score: e.score || 0 };
            });
        } else {
            kwData.list = [];
        }
        kwData.page = 1;
        kwData.sortCol = null;
        kwData.sortAsc = true;
        method.kwRenderTable();
    };
    method.kwDebouncedSave = function() {
        if (kwSaveTimer) clearTimeout(kwSaveTimer);
        kwSaveTimer = setTimeout(function() {
            method._kwSyncFromDOM();
            if (!publicData.set.key_word) publicData.set.key_word = {};
            publicData.set.key_word.keywords = kwData.list.filter(function(e) { return e.keyword !== ''; });
            publicData.set.edition = $("#app-version").attr("data-version") || '';
            method.sendSet(publicData.set);
        }, 400);
    };
    method.kwSyncToSet = function(set) {
        method._kwSyncFromDOM();
        if (!set.key_word) set.key_word = { keywords: [] };
        set.key_word.keywords = kwData.list.filter(function(e) { return e.keyword !== ''; });
    };

    $(function() {
        method.kwLoadFromSet();
        $(document).on('click', '.kw-add-btn', function() {
            kwData.list.push({ keyword: '', score: 0 });
            kwData.page = Math.max(1, Math.ceil(kwData.list.length / kwData.pageSize));
            method.kwRenderTable();
        });
        $(document).on('click', '.kw-delete-btn', function() {
            method._kwSyncFromDOM();
            var rowIdx = $(this).closest('tr').index();
            var listIdx = (kwData.page - 1) * kwData.pageSize + rowIdx;
            if (listIdx < kwData.list.length) kwData.list.splice(listIdx, 1);
            method.kwRenderTable();
            method.kwDebouncedSave();
        });
        $(document).on('input change', '.kw-keyword, .kw-score', function() {
            method.kwDebouncedSave();
        });
        $(document).on('click', '.kw-prev', function() {
            method._kwSyncFromDOM();
            if (kwData.page > 1) { kwData.page--; method.kwRenderTable(); }
        });
        $(document).on('click', '.kw-next', function() {
            method._kwSyncFromDOM();
            var totalPages = Math.max(1, Math.ceil(kwData.list.length / kwData.pageSize));
            if (kwData.page < totalPages) { kwData.page++; method.kwRenderTable(); }
        });
        $(document).on('click', '.kw-sort-keyword', function() {
            method._kwSyncFromDOM();
            if (kwData.sortCol === 'keyword') { kwData.sortAsc = !kwData.sortAsc; }
            else { kwData.sortCol = 'keyword'; kwData.sortAsc = true; }
            kwData.page = 1;
            method.kwRenderTable();
        });
        $(document).on('click', '.kw-sort-score', function() {
            method._kwSyncFromDOM();
            if (kwData.sortCol === 'score') { kwData.sortAsc = !kwData.sortAsc; }
            else { kwData.sortCol = 'score'; kwData.sortAsc = true; }
            kwData.page = 1;
            method.kwRenderTable();
        });
    });

    // ==== Tap-to-expand truncated text via floating popup (mobile friendly) ====
    $(function() {
        var $popup = null;
        var _activeEl = null;

        function _hidePopup() {
            if ($popup) { $popup.remove(); $popup = null; }
            _activeEl = null;
        }

        function _showPopup($el) {
            _hidePopup();
            var text = ($el.attr('title') || $el.text() || '').trim();
            if (!text) return;

            $popup = $('<div class="truncate-popup">')
                .append('<span class="truncate-popup-close">&times;</span>')
                .append($('<span>').text(text))
                .appendTo('body');

            $popup.on('click', '.truncate-popup-close', function(e) {
                e.stopPropagation();
                _hidePopup();
            });

            // Position near the element
            var offset = $el.offset();
            var elH = $el.outerHeight();
            var elW = $el.outerWidth();
            var popupW = $popup.outerWidth();
            var popupH = $popup.outerHeight();
            var winW = $(window).width();
            var winH = $(window).height();
            var scrollTop = $(window).scrollTop();
            var scrollLeft = $(window).scrollLeft();

            // Horizontal: align left edge with element, clamp to viewport
            var left = Math.min(offset.left, winW + scrollLeft - popupW - 10);
            left = Math.max(left, scrollLeft + 10);

            // Vertical: show below if room, else above
            var top;
            var spaceBelow = winH + scrollTop - (offset.top + elH);
            if (spaceBelow >= popupH + 8 || spaceBelow >= offset.top - scrollTop) {
                top = offset.top + elH + 4;
            } else {
                top = offset.top - popupH - 4;
            }

            $popup.css({ left: left + 'px', top: top + 'px' });
            _activeEl = $el[0];
        }

        $(document).on('click', '.truncate-expandable', function(e) {
            var $el = $(this);

            // If clicking the same element that already has popup, dismiss
            if (_activeEl === $el[0] && $popup) {
                _hidePopup();
                return;
            }

            // Only show popup if text is actually truncated
            if (this.scrollWidth > this.clientWidth) {
                _showPopup($el);
            } else if ($popup) {
                _hidePopup();
            }

            e.stopPropagation();
        });

        // Reposition on scroll/resize while popup is visible
        $(window).on('scroll.truncatePopup resize.truncatePopup', function() {
            if ($popup && _activeEl) {
                var $el = $(_activeEl);
                if (!$el.length) { _hidePopup(); return; }
                var offset = $el.offset();
                var elH = $el.outerHeight();
                var popupW = $popup.outerWidth();
                var winW = $(window).width();
                var scrollLeft = $(window).scrollLeft();
                var left = Math.min(offset.left, winW + scrollLeft - popupW - 10);
                left = Math.max(left, scrollLeft + 10);
                var top = offset.top + elH + 4;
                $popup.css({ left: left + 'px', top: top + 'px' });
            }
        });
    });
})();
