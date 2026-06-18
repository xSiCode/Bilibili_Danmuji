// blacklist.js - 小黑屋页面
window.currentPageId = 'blacklist';

registerPageSave('blacklist', function(set) {
    // 自动拉黑
    if (!set.auto_block) set.auto_block = {};
    set.auto_block.is_auto_block = $(".is_auto_block").is(':checked');
    set.auto_block.block_score = parseInt($(".auto-block-score").val()) || -1;
    set.auto_block.block_interval = parseInt($(".auto-block-interval").val()) || 120;

    // 本地黑白名单姬
    if (!set.local_black_white_list) set.local_black_white_list = {};
    set.local_black_white_list.is_open = $(".is_bwlist_open").is(':checked');

    // 关键词检测姬
    method.kwSyncToSet(set);
});

// ========== 本地黑白名单姬 ==========
var bwlistState = {
    black: { page: 1, pageSize: 10, sortField: 'updateTime', sortOrder: 'desc', search: '' },
    white: { page: 1, pageSize: 10, sortField: 'updateTime', sortOrder: 'desc', search: '' }
};

method.renderBlackWhiteTable = function(type, page) {
    var st = bwlistState[type];
    if (page) st.page = page;
    $.ajax({
        url: '../getBlackWhiteList',
        type: 'GET',
        data: {
            type: type,
            page: st.page,
            pageSize: st.pageSize,
            search: st.search || '',
            sortField: st.sortField,
            sortOrder: st.sortOrder
        },
        dataType: 'json',
        success: function(data) {
            if (data.code == "200" && data.result) {
                var r = data.result;
                var $tbody = $(".bwlist-" + type + "-tbody");
                var $pagination = $(".bwlist-" + type + "-pagination");
                var $total = $(".bwlist-" + type + "-total");
                $tbody.empty();
                $total.text('(共' + (r.total || 0) + '条)');
                if (!r.rows || r.rows.length === 0) {
                    $pagination.hide();
                    $tbody.append('<tr><td colspan="7" class="text-muted">暂无数据</td></tr>');
                    return;
                }
                try {
                    for (var i = 0; i < r.rows.length; i++) {
                        var e = r.rows[i];
                        var $tr = $("<tr>");
                        // name: 点击跳转用户空间
                        var $nameTd = $("<td>").addClass("bw-col-name truncate-expandable").attr("title", e.name || '');
                        if (e.name) {
                            $("<a>").attr("href", "https://space.bilibili.com/" + e.uid).attr("target", "_blank")
                                .text(e.name).appendTo($nameTd);
                        }
                        $tr.append($nameTd);
                        $tr.append($("<td>").addClass("bw-col-count truncate-expandable").text(e.count));
                        $tr.append($("<td>").addClass("bw-col-scoreType truncate-expandable").text(e.scoreType || '').attr("title", e.scoreType || ''));
                        $tr.append($("<td>").addClass("bw-col-score truncate-expandable").text(e.score));
                        // roomId: 点击跳转直播间
                        var $roomTd = $("<td>").addClass("bw-col-roomId truncate-expandable");
                        if (e.roomId && e.roomId > 0) {
                            $("<a>").attr("href", "https://live.bilibili.com/" + e.roomId).attr("target", "_blank")
                                .text(e.roomId).appendTo($roomTd);
                        }
                        $tr.append($roomTd);
                        $tr.append($("<td>").addClass("bw-col-updateTime truncate-expandable").text(e.updateTime ? bwlistFmtTs(e.updateTime) : '').attr("title", e.updateTime ? bwlistFmtTs(e.updateTime) : ''));
                        var $delBtn = $("<button>").addClass("btn btn-sm btn-outline-danger bwlist-del-btn").attr("data-uid", e.uid).attr("data-type", type).attr("title", "从名单删除").text("删除");
                        var $moveLabel = type === 'black' ? '→白' : '→黑';
                        var $moveTitle = type === 'black' ? '移到白名单' : '移到黑名单';
                        var $moveBtnClass = type === 'black' ? 'btn-outline-success' : 'btn-outline-dark';
                        var $moveBtn = $("<button>").addClass("btn btn-sm " + $moveBtnClass + " bwlist-move-btn ms-1").attr("data-uid", e.uid).attr("data-type", type).attr("title", $moveTitle).text($moveLabel);
                        $tr.append($("<td>").addClass("bw-col-action truncate-expandable").append($delBtn).append($moveBtn));
                        $tbody.append($tr);
                    }
                    if (r.totalPages > 1) {
                        $pagination.show();
                        $(".bw-" + type + "-page-info").text("第" + r.currentPage + "页/共" + r.totalPages + "页");
                        $(".bw-" + type + "-prev").prop("disabled", r.currentPage <= 1);
                        $(".bw-" + type + "-next").prop("disabled", r.currentPage >= r.totalPages);
                    } else {
                        $pagination.hide();
                    }
                } catch (ex) {
                    console.error('bwlist render error:', ex);
                }
            }
        },
        error: function(xhr, status, err) {
            console.error('bwlist fetch error:', type, status, err);
        }
    });
};

method.refreshBothTables = function() {
    method.renderBlackWhiteTable('black');
    method.renderBlackWhiteTable('white');
};

$(function() {
    initPageTabs();

    // 初始加载
    method.renderBlackWhiteTable('black');
    method.renderBlackWhiteTable('white');
    // 初始排序图标
    setTimeout(function() {
        $('.bw-sortable[data-col="updateTime"] .bw-sort-icon').text(' ▼');
    }, 100);

    // 搜索
    var searchTimer;
    $(document).on('input', '.bwlist-search-input', function() {
        clearTimeout(searchTimer);
        var val = $.trim($(this).val());
        searchTimer = setTimeout(function() {
            bwlistState.black.search = val;
            bwlistState.white.search = val;
            bwlistState.black.page = 1;
            bwlistState.white.page = 1;
            method.refreshBothTables();
        }, 300);
    });

    // 排序
    $(document).on('click', '.bw-sortable', function() {
        var col = $(this).data('col');
        var $tab = $(this).closest('.bwlist-table');
        var type = $tab.hasClass('bwlist-black-table') ? 'black' : 'white';
        if (bwlistState[type].sortField === col) {
            bwlistState[type].sortOrder = bwlistState[type].sortOrder === 'asc' ? 'desc' : 'asc';
        } else {
            bwlistState[type].sortField = col;
            bwlistState[type].sortOrder = 'asc';
        }
        bwlistState[type].page = 1;
        // 更新排序图标
        $tab.find('.bw-sort-icon').text('');
        var icon = bwlistState[type].sortOrder === 'asc' ? ' ▲' : ' ▼';
        $(this).find('.bw-sort-icon').text(icon);
        method.renderBlackWhiteTable(type);
    });

    // 删除
    $(document).on('click', '.bwlist-del-btn', function() {
        var uid = $(this).data('uid');
        var type = $(this).data('type');
        var $btn = $(this).prop('disabled', true);
        $.ajax({
            url: '../deleteBlackWhiteEntry',
            type: 'POST',
            data: { type: type, uid: uid },
            dataType: 'json',
            success: function(data) {
                if (data.code == "200" && data.result == 0) {
                    method.renderBlackWhiteTable(type);
                }
            },
            error: function() { console.error('bwlist delete failed:', uid, type); },
            complete: function() { $btn.prop('disabled', false); }
        });
    });

    // 移动
    $(document).on('click', '.bwlist-move-btn', function() {
        var uid = $(this).data('uid');
        var from = $(this).data('type');
        var $btn = $(this).prop('disabled', true);
        $.ajax({
            url: '../moveBlackWhiteEntry',
            type: 'POST',
            data: { from: from, uid: uid },
            dataType: 'json',
            success: function(data) {
                if (data.code == "200" && data.result == 0) {
                    method.refreshBothTables();
                }
            },
            error: function() { console.error('bwlist move failed:', uid, from); },
            complete: function() { $btn.prop('disabled', false); }
        });
    });

    // 分页 - 黑
    $(document).on('click', '.bw-black-prev', function() {
        method.renderBlackWhiteTable('black', bwlistState.black.page - 1);
    });
    $(document).on('click', '.bw-black-next', function() {
        method.renderBlackWhiteTable('black', bwlistState.black.page + 1);
    });

    // 分页 - 白
    $(document).on('click', '.bw-white-prev', function() {
        method.renderBlackWhiteTable('white', bwlistState.white.page - 1);
    });
    $(document).on('click', '.bw-white-next', function() {
        method.renderBlackWhiteTable('white', bwlistState.white.page + 1);
    });

    // ===== 本地黑白名单 导出/导入 =====
    $(document).on('click', '.bw-export-btn', function() {
        var type = $(this).closest('.set-control').attr('data-file').split('-')[1]; // black or white
        window.location.href = '../exportBlackWhiteCsv?type=' + type;
    });

    $(document).on('click', '.bw-import-btn', function() {
        var $setCtrl = $(this).closest('.set-control');
        var type = $setCtrl.attr('data-file').split('-')[1]; // black or white
        var $input = $setCtrl.find('.bw-import-file-input');
        $input.off('change.bwlist').on('change.bwlist', function(ev) {
            var file = ev.target.files[0];
            if (!file) return;
            var formData = new FormData();
            formData.append('file', file);
            formData.append('type', type);
            $.ajax({
                url: '../importBlackWhiteCsv',
                type: 'POST',
                data: formData,
                processData: false,
                contentType: false,
                dataType: 'json',
                success: function(data) {
                    if (data.code == "200") {
                        showMessage('导入完成: ' + (data.result || 0) + ' 条', 'success', 3);
                        method.refreshBothTables();
                    } else {
                        showMessage('导入失败', 'danger', 3);
                    }
                },
                error: function() { showMessage('导入失败', 'danger', 3); }
            });
        });
        $input.click();
    });
});

// 时间戳格式化: 毫秒 → yyyy-MM-dd HH:mm:ss
function bwlistFmtTs(ts) {
    if (!ts || ts <= 0) return '';
    var d = new Date(ts);
    var yyyy = d.getFullYear();
    var MM = ('0' + (d.getMonth() + 1)).slice(-2);
    var dd = ('0' + d.getDate()).slice(-2);
    var HH = ('0' + d.getHours()).slice(-2);
    var mm = ('0' + d.getMinutes()).slice(-2);
    var ss = ('0' + d.getSeconds()).slice(-2);
    return yyyy + '-' + MM + '-' + dd + ' ' + HH + ':' + mm + ':' + ss;
}
