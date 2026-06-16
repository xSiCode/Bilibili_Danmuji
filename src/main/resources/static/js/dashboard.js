// dashboard.js - 看板页面（陌生观众看板 + 足迹留印 + 足迹还原）
window.currentPageId = 'dashboard';

var REPLAY_QUEUE_KEY = 'footprint_replay_queue';

$(function() {
    initDashboardTabs();
    initFootprintFileManagement();
    initFootprintReplayControls();

    // 页面刷新后恢复队列 + 检测活跃重放
    loadQueueFromStorage();
    checkAndResumeReplayPolling();

    // 注册保存回调，支持足迹留印开关的持久化
    registerPageSave('dashboard', function(set) {
        set.is_footprint_record = $(".is_footprint_record").is(':checked');
    });
});

// ===== 标签页切换 =====
function initDashboardTabs() {
    // 根据 URL 参数激活对应标签页
    var params = new URLSearchParams(window.location.search);
    var tabParam = params.get('tab');
    if (tabParam === 'footprint-record') {
        $('#tab-btn-footprint-record').tab('show');
    } else if (tabParam === 'footprint-replay') {
        $('#tab-btn-footprint-replay').tab('show');
    } else if (tabParam === 'user-query') {
        $('#tab-btn-user-query').tab('show');
    }

    // 标签页切换时更新 URL
    $('button[data-bs-toggle="tab"]').on('shown.bs.tab', function(e) {
        var tabId = $(e.target).attr('data-bs-target').replace('#tab-', '');
        var url = new URL(window.location);
        if (tabId === 'stranger-board') {
            url.searchParams.delete('tab');
        } else {
            url.searchParams.set('tab', tabId);
        }
        window.history.replaceState({}, '', url);
    });

    // 切换到足迹留印标签页时自动刷新文件列表
    $('#tab-btn-footprint-record').on('shown.bs.tab', refreshFootprintFiles);
    // 切换到足迹还原标签页时自动刷新文件下拉框
    $('#tab-btn-footprint-replay').on('shown.bs.tab', refreshFootprintFileSelect);
}

// ===== 足迹留印：文件管理 =====
function initFootprintFileManagement() {
    $('#fp-refresh-files').on('click', refreshFootprintFiles);
}

function refreshFootprintFiles() {
    $.ajax({
        url: '../listFootprintFiles',
        type: 'GET',
        dataType: 'json',
        success: function(data) {
            if (data.code == '200') {
                var files = data.result;
                var tbody = $('#fp-file-tbody').empty();
                if (files && files.length > 0) {
                    $('#fp-file-table').show();
                    $('#fp-no-files').hide();
                    files.forEach(function(f) {
                        var sizeKB = (f.size / 1024).toFixed(1);
                        tbody.append(
                            '<tr>' +
                            '<td>' + f.fileName + '</td>' +
                            '<td>' + sizeKB + ' KB</td>' +
                            '<td>' +
                            '<button class="btn btn-sm btn-outline-primary fp-download-btn" data-path="' + f.filePath + '">下载</button> ' +
                            '<button class="btn btn-sm btn-outline-danger fp-delete-btn" data-path="' + f.filePath + '">删除</button>' +
                            '</td>' +
                            '</tr>');
                    });
                } else {
                    $('#fp-file-table').hide();
                    $('#fp-no-files').show();
                }
                // 绑定按钮事件
                $('.fp-download-btn').off('click').on('click', function() {
                    window.open('../downloadFootprintFile?filePath=' + encodeURIComponent($(this).data('path')));
                });
                $('.fp-delete-btn').off('click').on('click', function() {
                    var btn = $(this);
                    if (confirm('确认删除此文件？')) {
                        $.ajax({
                            url: '../deleteFootprintFile',
                            type: 'POST',
                            data: { filePath: btn.data('path') },
                            dataType: 'json',
                            success: function() {
                                refreshFootprintFiles();
                                refreshFootprintFileSelect();
                            }
                        });
                    }
                });
            }
        }
    });
}

// ===== 足迹还原：多文件队列 + 批次回放 =====
var replayPollInterval = null;
var replayQueue = [];  // [{filePath, fileName}]

// ----- 队列持久化（localStorage，页面刷新不丢失）-----
function saveQueueToStorage() {
    try { localStorage.setItem(REPLAY_QUEUE_KEY, JSON.stringify(replayQueue)); } catch(e) {}
}
function loadQueueFromStorage() {
    try {
        var saved = localStorage.getItem(REPLAY_QUEUE_KEY);
        if (saved) { replayQueue = JSON.parse(saved); renderQueue(); }
    } catch(e) {}
}

// 页面加载时检测是否有活跃重放，有则恢复轮询
function checkAndResumeReplayPolling() {
    $.ajax({
        url: '../getFootprintReplayStatus',
        type: 'GET',
        dataType: 'json',
        success: function(data) {
            if (data.code == '200' && data.result.running) {
                startReplayPolling();
            } else {
                // 重放已完成，清除残留队列
                replayQueue = [];
                saveQueueToStorage();
                renderQueue();
            }
        }
    });
}

function initFootprintReplayControls() {
    // 上传按钮（支持多文件）
    $('#fpr-upload-btn').on('click', function() {
        $('#fpr-upload-input').click();
    });
    $('#fpr-upload-input').on('change', function() {
        var files = this.files;
        if (!files || files.length === 0) return;
        uploadFilesSequentially(files, 0);
        this.value = '';
    });

    // 加入队列
    $('#fpr-enqueue-btn').on('click', function() {
        var selected = $('#fpr-file-select').val();
        if (!selected || selected.length === 0) return;
        var added = 0;
        selected.forEach(function(fp) {
            if (!fp) return;
            var name = $('#fpr-file-select option[value="' + fp + '"]').text();
            if (!name || name === '-- 选择已有文件 --') name = fp.split('/').pop().split('\\').pop();
            var exists = replayQueue.some(function(item) { return item.filePath === fp; });
            if (!exists) {
                replayQueue.push({filePath: fp, fileName: name});
                added++;
            }
        });
        if (added > 0) showMessage('已添加 ' + added + ' 个文件到队列', 'success');
        renderQueue();
        $('#fpr-file-select').val([]);
    });

    // 开始批次回放
    $('#fpr-start-btn').on('click', startBatchReplay);
    $('#fpr-pause-btn').on('click', pauseReplay);
    $('#fpr-resume-btn').on('click', resumeReplay);
    $('#fpr-stop-btn').on('click', stopReplay);

    // 速度模式切换
    $('input[name="fpr-speed-mode"]').on('change', function() {
        var mode = $(this).val();
        if (mode === 'time') {
            $('#fpr-speed-time-panel').show();
            $('#fpr-speed-fixed-panel').hide();
        } else {
            $('#fpr-speed-time-panel').hide();
            $('#fpr-speed-fixed-panel').show();
        }
        updateReplaySpeed();
    });

    // 倍数播放滑块
    $('#fpr-speed-slider').on('input', function() {
        var speed = parseFloat($(this).val());
        $('#fpr-speed-label').text(speed.toFixed(1) + 'x');
        updateReplaySpeed();
    });

    // 定速播放输入
    $('#fpr-fixed-rate').on('change', function() {
        updateReplaySpeed();
    });
}

function uploadFilesSequentially(files, index) {
    if (index >= files.length) {
        refreshFootprintFileSelect();
        return;
    }
    var file = files[index];
    var formData = new FormData();
    formData.append('file', file);
    $.ajax({
        url: '../uploadFootprintFile',
        type: 'POST',
        data: formData,
        processData: false,
        contentType: false,
        dataType: 'json',
        success: function(data) {
            if (data.code == '200') {
                showMessage('上传成功 (' + (index + 1) + '/' + files.length + '): ' + file.name, 'success');
            } else {
                showMessage('上传失败: ' + file.name, 'danger');
            }
        },
        complete: function() {
            uploadFilesSequentially(files, index + 1);
        }
    });
}

function refreshFootprintFileSelect() {
    $.ajax({
        url: '../listFootprintFiles',
        type: 'GET',
        dataType: 'json',
        success: function(data) {
            if (data.code == '200') {
                var select = $('#fpr-file-select').empty();
                (data.result || []).forEach(function(f) {
                    select.append('<option value="' + f.filePath + '">' + f.fileName + '</option>');
                });
            }
        }
    });
}

function renderQueue() {
    saveQueueToStorage();
    var list = $('#fpr-queue-list');
    if (replayQueue.length === 0) {
        list.html('<div class="text-muted text-center py-2" id="fpr-queue-empty">队列为空，请先添加文件</div>');
        $('#fpr-queue-count').text('');
        return;
    }
    var html = '';
    replayQueue.forEach(function(item, i) {
        html += '<div class="d-flex align-items-center py-1 border-bottom">' +
            '<span class="text-muted me-2" style="font-size:12px;">' + (i + 1) + '.</span>' +
            '<span class="flex-grow-1" style="font-size:13px;">' + item.fileName + '</span>' +
            '<button class="btn btn-sm btn-outline-danger fpr-queue-remove" data-index="' + i + '" style="font-size:11px;padding:1px 6px;">移除</button>' +
            '</div>';
    });
    list.html(html);
    $('#fpr-queue-count').text('(共 ' + replayQueue.length + ' 个文件)');

    $('.fpr-queue-remove').off('click').on('click', function() {
        var idx = parseInt($(this).data('index'));
        replayQueue.splice(idx, 1);
        renderQueue();
    });
}

function updateReplaySpeed() {
    var mode = $('input[name="fpr-speed-mode"]:checked').val() || 'time';
    var value;
    if (mode === 'fixed') {
        value = parseFloat($('#fpr-fixed-rate').val()) || 1.0;
    } else {
        value = parseFloat($('#fpr-speed-slider').val()) || 1.0;
    }
    $.ajax({
        url: '../setFootprintReplaySpeed',
        type: 'POST',
        data: { speedMode: mode, speedValue: value },
        dataType: 'json'
    });
}

function startBatchReplay() {
    if (replayQueue.length === 0) {
        showMessage('队列为空，请先添加文件', 'warning');
        return;
    }
    var mode = $('input[name="fpr-speed-mode"]:checked').val() || 'time';
    var value;
    if (mode === 'fixed') {
        value = parseFloat($('#fpr-fixed-rate').val()) || 1.0;
    } else {
        value = parseFloat($('#fpr-speed-slider').val()) || 1.0;
    }
    var filePaths = replayQueue.map(function(item) { return item.filePath; });
    $.ajax({
        url: '../startBatchFootprintReplay',
        type: 'POST',
        data: { 'filePaths[]': filePaths, speedMode: mode, speedValue: value },
        traditional: true,
        dataType: 'json',
        success: function(data) {
            if (data.code == '200') {
                $('#fpr-status-text').text('批次回放中...');
                $('#fpr-count-text').text('共 ' + data.result.total + ' 条记录 / ' + data.result.totalBatches + ' 个文件');
                $('#fpr-batch-text').text('');
                startReplayPolling();
            } else if (data.code == '1') {
                showMessage('没有有效文件', 'danger');
            } else if (data.code == '2') {
                showMessage('所有文件为空', 'warning');
            } else {
                showMessage('启动失败', 'danger');
            }
        }
    });
}

function pauseReplay() {
    $.ajax({
        url: '../pauseFootprintReplay',
        type: 'POST',
        dataType: 'json',
        success: function() { $('#fpr-status-text').text('已暂停'); }
    });
}

function resumeReplay() {
    $.ajax({
        url: '../resumeFootprintReplay',
        type: 'POST',
        dataType: 'json',
        success: function() { $('#fpr-status-text').text('批次回放中...'); }
    });
}

function stopReplay() {
    $.ajax({
        url: '../stopFootprintReplay',
        type: 'POST',
        dataType: 'json',
        success: function() {
            stopReplayPolling();
            replayQueue = [];
            saveQueueToStorage();
            renderQueue();
            $('#fpr-status-text').text('已停止');
            $('#fpr-progress-bar').css('width', '0%').text('0%');
            $('#fpr-current-text').text('');
            $('#fpr-batch-text').text('');
        }
    });
}

function startReplayPolling() {
    stopReplayPolling();
    replayPollInterval = setInterval(function() {
        $.ajax({
            url: '../getFootprintReplayStatus',
            type: 'GET',
            dataType: 'json',
            success: function(data) {
                if (data.code == '200') {
                    var s = data.result;
                    if (!s.running || s.stopped) {
                        stopReplayPolling();
                        replayQueue = [];
                        saveQueueToStorage();
                        renderQueue();
                        var pct = s.totalCount > 0 ? 100 : 0;
                        $('#fpr-progress-bar').css('width', pct + '%').text(pct + '%');
                        $('#fpr-status-text').text('回放完成');
                        $('#fpr-batch-text').text('');
                        return;
                    }

                    // 文件级进度
                    if (s.totalBatchCount > 1) {
                        var batchPct = Math.round((s.currentBatchIndex + 1) / s.totalBatchCount * 100);
                        $('#fpr-batch-text').text('文件 ' + (s.currentBatchIndex + 1) + '/' + s.totalBatchCount + ' — ' + s.currentFileName);
                    }

                    // 记录级进度
                    var pct = s.totalCount > 0 ? Math.round((s.currentIndex + 1) / s.totalCount * 100) : 0;
                    $('#fpr-progress-bar').css('width', pct + '%').text(pct + '%');
                    $('#fpr-current-text').text('当前: [' + (s.currentIndex + 1) + '/' + s.totalCount + '] ' + s.currentUname);

                    if (s.paused) {
                        $('#fpr-status-text').text('已暂停');
                    } else {
                        var modeLabel = s.speedMode === 'fixed' ? s.speedValue.toFixed(1) + ' 人/秒' : s.speedValue.toFixed(1) + 'x';
                        $('#fpr-status-text').text('批次回放中... (' + modeLabel + ')');
                    }
                }
            }
        });
    }, 500);
}

function stopReplayPolling() {
    if (replayPollInterval) {
        clearInterval(replayPollInterval);
        replayPollInterval = null;
    }
}

// ===== 查阅用户 =====
var timelineData = [];
var sortColumn = 'eventTime';
var sortAsc = false;
var livePollTimer = null;
var displayQueue = [];
var displayTimer = null;
var lastMaxTs = 0;       // 上次拉取到的最大时间戳（毫秒），用于增量拉取
var liveMode = true;     // true=实时模式(默认), false=搜索模式

$(function() {
    // 搜索按钮
    $('#uq-btn-search').on('click', function() {
        var keyword = $('#uq-search-input').val().trim();
        if (!keyword) {
            showMessage('请输入UID、用户名或弹幕关键词', 'warning');
            return;
        }
        stopLiveFeed();
        liveMode = false;
        $.ajax({
            url: '../queryUserTimeline',
            type: 'GET',
            data: { keyword: keyword, limit: 500 },
            dataType: 'json',
            success: function(data) {
                if (data.code == '200' && data.result && data.result.length > 0) {
                    timelineData = data.result;
                    sortColumn = 'eventTime';
                    sortAsc = false;
                    sortAndRender();
                } else {
                    timelineData = [];
                    $('#uq-result-area').hide();
                    $('#uq-empty-msg').show();
                    $('#uq-result-info').text('未找到相关记录');
                }
            },
            error: function() {
                showMessage('查询失败', 'danger');
            }
        });
    });

    // 重置按钮 — 清空搜索，切回实时模式
    $('#uq-btn-reset').on('click', function() {
        $('#uq-search-input').val('');
        $('#uq-empty-msg').hide();
        timelineData = [];
        displayQueue = [];
        lastMaxTs = 0;
        liveMode = true;
        loadLatestAndPoll();
    });

    // 回车搜索
    $('#uq-search-input').on('keypress', function(e) {
        if (e.which == 13) {
            $('#uq-btn-search').click();
        }
    });

    // 表头点击排序
    $(document).on('click', '.uq-sortable', function() {
        var col = $(this).data('sort');
        if (sortColumn === col) {
            sortAsc = !sortAsc;
        } else {
            sortColumn = col;
            sortAsc = true;
        }
        sortAndRender();
    });

    // 切换到查阅用户标签页时，自动开始实时模式
    $('#tab-btn-user-query').on('shown.bs.tab', function() {
        if (liveMode && timelineData.length === 0) {
            loadLatestAndPoll();
        }
    });
});

// ----- 实时模式：拉取最新 + 轮询 -----
function loadLatestAndPoll() {
    stopLiveFeed();
    $.ajax({
        url: '../latestEvents',
        type: 'GET',
        data: { limit: 10 },
        dataType: 'json',
        success: function(data) {
            if (data.code == '200' && data.result && data.result.length > 0) {
                timelineData = data.result;
                // 记录最大时间戳用于增量拉取
                for (var i = 0; i < timelineData.length; i++) {
                    var t = parseTimestamp(timelineData[i].eventTime);
                    if (t > lastMaxTs) lastMaxTs = t;
                }
                sortColumn = 'eventTime';
                sortAsc = false;
                sortAndRender();
                startLivePolling();
            } else {
                $('#uq-empty-msg').show();
                $('#uq-result-info').text('暂无事件');
                // 无数据也继续轮询
                startLivePolling();
            }
        }
    });
}

function startLivePolling() {
    stopLivePolling();
    livePollTimer = setInterval(function() {
        $.ajax({
            url: '../latestEvents',
            type: 'GET',
            data: { limit: 30 },
            dataType: 'json',
            success: function(data) {
                if (data.code == '200' && data.result && data.result.length > 0) {
                    var newEvents = [];
                    for (var i = 0; i < data.result.length; i++) {
                        var t = parseTimestamp(data.result[i].eventTime);
                        if (t > lastMaxTs) {
                            newEvents.push(data.result[i]);
                            if (t > lastMaxTs) lastMaxTs = t;
                        }
                    }
                    // 新事件加入展示队列
                    if (newEvents.length > 0) {
                        for (var j = newEvents.length - 1; j >= 0; j--) {
                            displayQueue.push(newEvents[j]);
                        }
                        if (!displayTimer) startDisplayTimer();
                    }
                }
            }
        });
    }, 2000);
}

function startDisplayTimer() {
    if (displayTimer) return;
    displayTimer = setInterval(function() {
        if (displayQueue.length === 0) {
            stopDisplayTimer();
            return;
        }
        var evt = displayQueue.shift();
        // 插入到列表顶部
        timelineData.unshift(evt);
        // 保持最多 50 条（列表固定 20 行高度）
        if (timelineData.length > 50) timelineData = timelineData.slice(0, 50);
        sortAndRender();
        $('#uq-empty-msg').hide();
    }, 500);  // 每 0.5 秒 1 条
}

function stopDisplayTimer() {
    if (displayTimer) { clearInterval(displayTimer); displayTimer = null; }
}

function stopLivePolling() {
    if (livePollTimer) { clearInterval(livePollTimer); livePollTimer = null; }
}

function stopLiveFeed() {
    stopLivePolling();
    stopDisplayTimer();
    displayQueue = [];
}

// 点击事件类型 → 显示该时间前后最近的同类事件 20 条（上下文）
function filterByEventType(type, ts) {
    stopLiveFeed();
    liveMode = false;
    $('#uq-search-input').val('');
    $.ajax({
        url: '../latestEvents',
        type: 'GET',
        data: { limit: 20, type: type, ts: ts },
        dataType: 'json',
        success: function(data) {
            if (data.code == '200' && data.result && data.result.length > 0) {
                timelineData = data.result;
                sortColumn = 'eventTime';
                sortAsc = false;
                sortAndRender();
                $('#uq-result-info').text('事件类型: ' + type + ' — 时间前后最近 20 条上下文');
            } else {
                timelineData = [];
                $('#uq-result-area').hide();
                $('#uq-empty-msg').show();
                $('#uq-result-info').text('事件类型: ' + type + ' — 暂无记录');
            }
        }
    });
}

// 解析时间字符串为毫秒时间戳
function parseTimestamp(timeStr) {
    if (!timeStr) return 0;
    var d = new Date(timeStr.replace(/-/g, '/'));
    return d.getTime();
}

function sortAndRender() {
    if (!timelineData.length) return;
    var col = sortColumn;
    timelineData.sort(function(a, b) {
        var va = a[col] != null ? a[col] : '';
        var vb = b[col] != null ? b[col] : '';
        if (col === 'roomId') {
            va = parseInt(va) || 0;
            vb = parseInt(vb) || 0;
        }
        if (va < vb) return sortAsc ? -1 : 1;
        if (va > vb) return sortAsc ? 1 : -1;
        return 0;
    });
    renderUserTimeline(timelineData);
    updateSortIndicators();
}

function updateSortIndicators() {
    $('.uq-sortable').each(function() {
        var col = $(this).data('sort');
        var text = $(this).text().replace(/ [▲▼]$/, '');
        if (col === sortColumn) {
            text += ' ' + (sortAsc ? '▲' : '▼');
        }
        $(this).text(text);
    });
}

function renderUserTimeline(events) {
    var tbody = $('#uq-timeline-body').empty();
    var iconMap = {
        '弹幕': '💬',
        '进入': '🚪',
        '关注': '❤️',
        '礼物': '🎁',
        '上舰': '⛵',
        '醒目留言': '📢',
        '老爷进入': '👑',
        '舰长进入': '🛡️',
        '禁言': '🚫'
    };
    events.forEach(function(e) {
        var roomDisplay = e.anchorName ? e.anchorName : '房间' + e.roomId;
        tbody.append(
            '<tr>' +
            '<td class="truncate-expandable" style="width:180px;min-width:180px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" title="' + e.eventTime + '">' + e.eventTime + '</td>' +
            '<td class="truncate-expandable" style="width:180px;min-width:180px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" title="' + e.uname + '"><a href="https://space.bilibili.com/' + e.uid + '" target="_blank" class="uq-link">' + e.uname + '</a></td>' +
            '<td class="truncate-expandable" style="width:189px;min-width:189px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" title="' + roomDisplay + '"><a href="https://live.bilibili.com/' + e.roomId + '" target="_blank" class="uq-link">' + roomDisplay + '</a></td>' +
            '<td class="truncate-expandable" style="width:80px;min-width:80px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" title="查看该时间前后同类事件上下文"><a href="javascript:;" class="uq-type-link" data-type="' + e.eventType + '" data-ts="' + parseTimestamp(e.eventTime) + '">' + (iconMap[e.eventType] || '') + ' ' + e.eventType + '</a></td>' +
            '<td class="truncate-expandable" style="white-space:nowrap;overflow:hidden;text-overflow:ellipsis;" title="' + e.detail + '">' + e.detail + '</td>' +
            '</tr>');
    });
    // 绑定事件类型点击 → 按时间距离找同类事件上下文
    $('.uq-type-link').off('click').on('click', function() {
        var type = $(this).data('type');
        var ts = $(this).data('ts');
        filterByEventType(type, ts);
    });
    $('#uq-result-area').show();
    $('#uq-empty-msg').hide();
    $('#uq-result-info').text('共 ' + events.length + ' 条记录');
}
