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
