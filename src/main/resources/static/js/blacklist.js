// blacklist.js - 小黑屋页面
window.currentPageId = 'blacklist';

registerPageSave('blacklist', function(set) {
    // 自动拉黑
    if (!set.auto_block) set.auto_block = {};
    set.auto_block.is_auto_block = $(".is_auto_block").is(':checked');
    set.auto_block.block_score = parseInt($(".auto-block-score").val()) || -1;
    set.auto_block.block_interval = parseInt($(".auto-block-interval").val()) || 3;

    // badlist数据在全局badListData中，由原始saveSet传递
    if (!set.bad_list) set.bad_list = { bad_users: [] };
    if (typeof badListData !== 'undefined') {
        set.bad_list.bad_users = badListData;
    }

    // 关键词检测姬 — 同步最新数据到保存对象
    kwMethod.syncFromDOM();
    if (!set.key_word) set.key_word = { keywords: [] };
    set.key_word.keywords = kwData.list.filter(function(e) { return e.keyword !== ''; });
});

// ---- 关键词检测姬 数据管理 ----
var kwData = { list: [], page: 1, pageSize: 10, sortCol: null, sortAsc: true };

var kwMethod = {
    _sortList: function() {
        if (!kwData.sortCol) return;
        var col = kwData.sortCol;
        var asc = kwData.sortAsc;
        kwData.list.sort(function(a, b) {
            var va = (a[col] != null ? a[col] : '');
            var vb = (b[col] != null ? b[col] : '');
            if (col === 'score') {
                va = parseInt(va) || 0;
                vb = parseInt(vb) || 0;
                return asc ? va - vb : vb - va;
            }
            va = String(va).toLowerCase();
            vb = String(vb).toLowerCase();
            if (va < vb) return asc ? -1 : 1;
            if (va > vb) return asc ? 1 : -1;
            return 0;
        });
    },
    renderTable: function() {
        kwMethod._sortList();
        var tbody = $(".kw-tbody");
        tbody.empty();
        var total = kwData.list.length;
        var totalPages = Math.max(1, Math.ceil(total / kwData.pageSize));
        if (kwData.page > totalPages) kwData.page = totalPages;
        var start = (kwData.page - 1) * kwData.pageSize;
        var end = Math.min(start + kwData.pageSize, total);
        // sort icons
        $(".kw-sort-icon").text('');
        if (kwData.sortCol) {
            $(".kw-sort-" + kwData.sortCol + " .kw-sort-icon").text(kwData.sortAsc ? '▲' : '▼');
        }
        for (var i = start; i < end; i++) {
            var item = kwData.list[i];
            var tr = $('<tr>');
            tr.append($('<td>').append($('<input class="form-control form-control-sm kw-keyword" type="text" style="width:100%">').val(item.keyword || '')));
            tr.append($('<td style="text-align:right">').append($('<input class="form-control form-control-sm kw-score" type="number" style="width:60px;text-align:right">').val(item.score || 0)));
            tr.append($('<td style="text-align:center">').append($('<button class="btn btn-sm btn-danger kw-delete-btn" style="width:60px">删除</button>')));
            tbody.append(tr);
        }
        $(".kw-page-info").text("第" + kwData.page + "页/共" + totalPages + "页 (共" + total + "条)");
        $(".kw-pagination").toggle(total > kwData.pageSize);
        $(".kw-prev").prop('disabled', kwData.page <= 1);
        $(".kw-next").prop('disabled', kwData.page >= totalPages);
    },
    syncFromDOM: function() {
        $(".kw-tbody tr").each(function(i) {
            var idx = (kwData.page - 1) * kwData.pageSize + i;
            if (idx >= kwData.list.length) return;
            kwData.list[idx].keyword = ($(this).find(".kw-keyword").val() || '').trim();
            kwData.list[idx].score = parseInt($(this).find(".kw-score").val()) || 0;
        });
    },
    loadFromPublicData: function() {
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
        kwMethod.renderTable();
    },
    autoSave: function() {
        kwMethod.syncFromDOM();
        if (!publicData.set.key_word) publicData.set.key_word = {};
        publicData.set.key_word.keywords = kwData.list.filter(function(e) { return e.keyword !== ''; });
        publicData.set.edition = $("#app-version").attr("data-version") || '';
        return method.sendSet(publicData.set);
    }
};

$(function() {
    initPageTabs();
    kwMethod.loadFromPublicData();

    $(document).on('click', '.kw-add-btn', function() {
        kwData.list.push({ keyword: '', score: 0 });
        kwData.page = Math.max(1, Math.ceil(kwData.list.length / kwData.pageSize));
        kwMethod.renderTable();
        kwMethod.autoSave();
    });
    $(document).on('click', '.kw-delete-btn', function() {
        var rowIdx = $(this).closest('tr').index();
        kwMethod.syncFromDOM();
        var listIdx = (kwData.page - 1) * kwData.pageSize + rowIdx;
        if (listIdx < kwData.list.length) kwData.list.splice(listIdx, 1);
        kwMethod.renderTable();
        kwMethod.autoSave();
    });
    $(document).on('input change', '.kw-keyword, .kw-score', function() {
        kwMethod.autoSave();
    });
    $(document).on('click', '.kw-prev', function() {
        kwMethod.syncFromDOM();
        if (kwData.page > 1) { kwData.page--; kwMethod.renderTable(); }
    });
    $(document).on('click', '.kw-next', function() {
        kwMethod.syncFromDOM();
        var totalPages = Math.max(1, Math.ceil(kwData.list.length / kwData.pageSize));
        if (kwData.page < totalPages) { kwData.page++; kwMethod.renderTable(); }
    });
    $(document).on('click', '.kw-sort-keyword', function() {
        kwMethod.syncFromDOM();
        if (kwData.sortCol === 'keyword') {
            kwData.sortAsc = !kwData.sortAsc;
        } else {
            kwData.sortCol = 'keyword';
            kwData.sortAsc = true;
        }
        kwData.page = 1;
        kwMethod.renderTable();
    });
    $(document).on('click', '.kw-sort-score', function() {
        kwMethod.syncFromDOM();
        if (kwData.sortCol === 'score') {
            kwData.sortAsc = !kwData.sortAsc;
        } else {
            kwData.sortCol = 'score';
            kwData.sortAsc = true;
        }
        kwData.page = 1;
        kwMethod.renderTable();
    });
});
